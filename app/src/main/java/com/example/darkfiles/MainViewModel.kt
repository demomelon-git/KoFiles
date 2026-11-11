package com.example.darkfiles

import android.app.Application
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.darkfiles.data.AdbTransport
import com.example.darkfiles.data.AppEntry
import com.example.darkfiles.data.BinderTransport
import com.example.darkfiles.data.Clip
import com.example.darkfiles.data.FileItem
import com.example.darkfiles.data.Prefs
import com.example.darkfiles.data.RemoteFs
import com.example.darkfiles.data.joinPath
import com.example.darkfiles.server.AdbServer
import com.example.darkfiles.server.FileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.File

const val MODE_SHIZUKU = "shizuku"
const val MODE_ADB = "adb"
const val MODE_NONE = "none"

sealed interface Conn {
    object Off : Conn
    object Connecting : Conn
    data class On(val fs: RemoteFs, val info: String) : Conn
    data class Error(val msg: String) : Conn
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val root: String = Environment.getExternalStorageDirectory().absolutePath
    private val ctx: Application get() = getApplication()

    // ---------- подключение ----------
    var conn by mutableStateOf<Conn>(Conn.Off)
        private set
    val fs: RemoteFs? get() = (conn as? Conn.On)?.fs

    val statusText: String
        get() = when (val c = conn) {
            is Conn.On -> "Подключено · ${c.info}"
            is Conn.Connecting -> "Подключение…"
            is Conn.Error -> c.msg
            is Conn.Off -> if (prefs.mode == MODE_NONE) "Режим «Без запуска»: доступ к файлам отключён" else "Не подключено"
        }

    private val svcConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) {
                conn = Conn.Error("Служба Shizuku не отвечает")
                return
            }
            finishConnect(RemoteFs(BinderTransport(IFileService.Stub.asInterface(binder))), "Shizuku")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (prefs.mode == MODE_SHIZUKU) conn = Conn.Error("Служба Shizuku отключена")
        }
    }

    private val onBinder = Shizuku.OnBinderReceivedListener {
        if (prefs.mode == MODE_SHIZUKU && prefs.autoConnect && conn !is Conn.On && conn !is Conn.Connecting) connect()
    }
    private val onDead = Shizuku.OnBinderDeadListener {
        if (prefs.mode == MODE_SHIZUKU) conn = Conn.Error("Shizuku остановлен")
    }
    private val onPerm = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) bindShizuku()
        else conn = Conn.Error("Разрешение Shizuku не выдано. Выдайте его вручную в приложении Shizuku.")
    }

    init {
        Shizuku.addBinderReceivedListener(onBinder)
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onPerm)
        if (prefs.autoConnect) connect()
    }

    override fun onCleared() {
        Shizuku.removeBinderReceivedListener(onBinder)
        Shizuku.removeBinderDeadListener(onDead)
        Shizuku.removeRequestPermissionResultListener(onPerm)
    }

    fun onResume() {
        val c = conn
        if (prefs.autoConnect && prefs.mode != MODE_NONE && (c is Conn.Error || c is Conn.Off)) connect()
    }

    fun chooseMode(m: String) {
        prefs.setMode(m)
        connect()
    }

    fun connect() {
        when (prefs.mode) {
            MODE_SHIZUKU -> connectShizuku()
            MODE_ADB -> {
                conn = Conn.Connecting
                finishConnect(RemoteFs(AdbTransport(prefs.adbPort, prefs.token)), "ADB")
            }
            else -> conn = Conn.Off
        }
        if (path != null) reload()
    }

    private fun connectShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                conn = Conn.Error("Shizuku не запущен. Запустите Shizuku и вернитесь в приложение.")
                return
            }
            if (Shizuku.isPreV11()) {
                conn = Conn.Error("Нужен Shizuku версии 11 или новее")
                return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                conn = Conn.Connecting
                Shizuku.requestPermission(100)
                return
            }
            bindShizuku()
        } catch (e: Throwable) {
            conn = Conn.Error(e.message ?: "Ошибка Shizuku")
        }
    }

    private fun bindShizuku() {
        conn = Conn.Connecting
        try {
            val args = Shizuku.UserServiceArgs(ComponentName(ctx.packageName, FileService::class.java.name))
                .daemon(false)
                .processNameSuffix("fs")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)
            Shizuku.bindUserService(args, svcConn)
        } catch (e: Throwable) {
            conn = Conn.Error(e.message ?: "Не удалось запустить службу Shizuku")
        }
    }

    private fun finishConnect(fs: RemoteFs, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            conn = try {
                val who = when (val uid = fs.uid()) {
                    0 -> "root"
                    2000 -> "shell"
                    else -> "uid $uid"
                }
                Conn.On(fs, "$label · $who")
            } catch (e: Exception) {
                Conn.Error(e.message ?: "Ошибка подключения")
            }
            if (conn is Conn.On && path != null) reload()
        }
    }

    // ---------- ADB: скрипт запуска на телефоне ----------
    fun writeScript(): String {
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        dir.mkdirs()
        val f = File(dir, "start.sh")
        val cls = AdbServer::class.java.name
        val lines = listOf(
            "#!/system/bin/sh",
            "APK=\$(pm path ${ctx.packageName} | head -n 1 | cut -d: -f2)",
            "if [ -z \"\$APK\" ]; then echo 'APK not found'; exit 1; fi",
            "CLASSPATH=\$APK nohup app_process /system/bin --nice-name=darkfiles_server $cls ${prefs.adbPort} ${prefs.token} >/dev/null 2>&1 &",
            "sleep 1",
            "echo 'Server started'"
        )
        f.writeText(lines.joinToString("\n") + "\n")
        return f.absolutePath
    }

    fun stopAdbServer() {
        viewModelScope.launch(Dispatchers.IO) {
            message = try {
                RemoteFs(AdbTransport(prefs.adbPort, prefs.token)).stop()
                "Сервер остановлен"
            } catch (e: Exception) {
                e.message ?: "Сервер не отвечает"
            }
            if (prefs.mode == MODE_ADB) conn = Conn.Error("Сервер ADB остановлен")
        }
    }

    // ---------- обзор файлов ----------
    var path by mutableStateOf<String?>(null)
        private set
    private var raw by mutableStateOf<List<FileItem>>(emptyList())
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var query by mutableStateOf("")
    var searchMode by mutableStateOf(false)
    val selected = mutableStateListOf<String>()
    var clip by mutableStateOf<Clip?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var player by mutableStateOf<String?>(null)

    fun open(p: String?) {
        path = p
        selected.clear()
        query = ""
        searchMode = false
        error = null
        raw = emptyList()
        if (p != null) reload()
    }

    fun reload() {
        val p = path ?: return
        val f = fs
        if (f == null) {
            raw = emptyList()
            error = if (conn is Conn.Connecting) "Подключение…" else "Нет доступа к файлам. Выберите способ запуска (Shizuku или ADB)."
            return
        }
        loading = true
        error = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val l = f.list(p)
                if (path == p) raw = l
            } catch (e: Exception) {
                if (path == p) {
                    raw = emptyList()
                    error = e.message ?: "Ошибка чтения папки"
                }
            }
            loading = false
        }
    }

    fun back(): Boolean {
        if (selected.isNotEmpty()) { selected.clear(); return true }
        if (searchMode) { searchMode = false; query = ""; return true }
        val p = path ?: return false
        val parent = File(p).parent
        open(if (p == root || parent == null) null else parent)
        return true
    }

    fun visible(): List<FileItem> {
        var l = raw
        if (!prefs.showHidden) l = l.filter { !it.name.startsWith(".") }
        if (query.isNotBlank()) l = l.filter { it.name.contains(query, ignoreCase = true) }
        val by = prefs.sortBy
        var cmp = Comparator<FileItem> { a, b ->
            when (by) {
                1 -> a.size.compareTo(b.size)
                2 -> a.modified.compareTo(b.modified)
                else -> a.name.compareTo(b.name, ignoreCase = true)
            }
        }
        if (prefs.sortDesc) cmp = cmp.reversed()
        if (prefs.foldersFirst) cmp = compareByDescending<FileItem> { it.isDir }.then(cmp)
        return l.sortedWith(cmp)
    }

    fun toggle(p: String) { if (!selected.remove(p)) selected.add(p) }
    fun selectAll(l: List<FileItem>) { selected.clear(); selected.addAll(l.map { it.path }) }
    fun clearClip() { clip = null }

    fun playVideo(p: String) {
        if (fs == null) message = "Нет доступа к файлам" else player = p
    }

    private fun op(ok: String, block: (RemoteFs) -> Unit) {
        val f = fs
        if (f == null) { message = "Нет доступа к файлам"; return }
        viewModelScope.launch(Dispatchers.IO) {
            message = try {
                block(f)
                ok
            } catch (e: Exception) {
                e.message ?: "Ошибка"
            }
            selected.clear()
            reload()
        }
    }

    fun delete(paths: List<String>) = op("Удалено: ${paths.size}") { f -> paths.forEach { f.delete(it) } }

    fun mkdir(name: String) {
        val p = path ?: return
        op("Папка создана") { it.mkdir(joinPath(p, name)) }
    }

    fun rename(old: String, name: String) {
        val parent = File(old).parent ?: return
        op("Переименовано") { it.rename(old, joinPath(parent, name)) }
    }

    fun copySelected() { clip = Clip(selected.toList(), false); selected.clear(); message = "Скопировано" }
    fun cutSelected() { clip = Clip(selected.toList(), true); selected.clear(); message = "Вырезано" }

    fun paste() {
        val c = clip ?: return
        val dst = path ?: return
        val taken = raw.map { it.name }.toMutableSet()
        if (c.cut) clip = null
        op("Готово") { f ->
            for (src in c.paths) {
                if (c.cut && File(src).parent == dst) continue
                val name = unique(File(src).name, taken)
                taken.add(name)
                if (c.cut) f.move(src, joinPath(dst, name)) else f.copy(src, joinPath(dst, name))
            }
        }
    }

    private fun unique(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while ("$base ($i)$ext" in taken) i++
        return "$base ($i)$ext"
    }

    // ---------- приложения ----------
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    fun loadApps() {
        if (apps.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val pm = ctx.packageManager
            apps = pm.getInstalledApplications(0).map { ai ->
                AppEntry(
                    pm.getApplicationLabel(ai).toString(),
                    ai.packageName,
                    (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    ai.dataDir ?: "/data/data/${ai.packageName}"
                )
            }.sortedBy { it.label.lowercase() }
        }
    }
}
