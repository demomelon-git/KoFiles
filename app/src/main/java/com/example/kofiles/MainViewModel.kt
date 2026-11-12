package com.example.kofiles

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import com.example.kofiles.data.AdbTransport
import com.example.kofiles.data.ArchiveChild
import com.example.kofiles.data.ArchiveState
import com.example.kofiles.data.Archives
import com.example.kofiles.data.FileKinds
import com.example.kofiles.data.Kind
import com.example.kofiles.data.LocalSource
import com.example.kofiles.data.OpenCache
import com.example.kofiles.data.OpenRequest
import com.example.kofiles.data.Opener
import com.example.kofiles.data.RemoteSource
import com.example.kofiles.data.SourceProvider
import com.example.kofiles.data.Viewer
import com.example.kofiles.data.AppEntry
import com.example.kofiles.data.BinderTransport
import com.example.kofiles.data.Clip
import com.example.kofiles.data.FileItem
import com.example.kofiles.data.Prefs
import com.example.kofiles.data.RemoteFs
import com.example.kofiles.data.joinPath
import com.example.kofiles.server.AdbServer
import com.example.kofiles.server.FileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.io.IOException

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

    var path by mutableStateOf<String?>(null)
        private set

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
        viewModelScope.launch(Dispatchers.IO) { try { OpenCache.trimOld(ctx) } catch (e: Exception) { } }
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
            "CLASSPATH=\$APK nohup app_process /system/bin --nice-name=kofiles_server $cls ${prefs.adbPort} ${prefs.token} >/dev/null 2>&1 &",
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

    // ---------- открытие файлов ----------
    var openRequest by mutableStateOf<OpenRequest?>(null)
    var copyProgress by mutableStateOf<Float?>(null)
        private set
    private val viewerStack = mutableStateListOf<Viewer>()
    val viewer: Viewer? get() = viewerStack.lastOrNull()
    private val archives = HashMap<String, ArchiveState>()

    private val WRITE_CHUNK = 96 * 1024

    /** Нажатие на файл: спросить, чем открыть (с проверкой совместимых приложений), либо сразу встроенный просмотр. */
    fun onFileClick(f: FileItem) {
        val req = OpenRequest(f.path, f.name, f.size, false)
        if (prefs.askOpenWith) openRequest = req else openDirect(req)
    }

    fun requestOpen(req: OpenRequest) {
        if (prefs.askOpenWith) openRequest = req else openDirect(req)
    }

    private fun openDirect(req: OpenRequest) {
        when (FileKinds.kind(req.name)) {
            Kind.VIDEO, Kind.IMAGE, Kind.AUDIO, Kind.ARCHIVE -> openBuiltIn(req, false)
            else -> openRequest = req
        }
    }

    fun provider(local: Boolean): SourceProvider {
        if (local) return { p -> LocalSource(File(p)) }
        return { p -> RemoteSource(fs ?: throw IOException("Нет доступа к файлам"), p) }
    }

    fun openBuiltIn(req: OpenRequest, asArchive: Boolean) {
        openRequest = null
        if (!req.local && fs == null) {
            message = "Нет доступа к файлам. Выберите способ запуска."
            return
        }
        val kind = if (asArchive) Kind.ARCHIVE else FileKinds.kind(req.name)
        when (kind) {
            Kind.IMAGE, Kind.AUDIO -> {
                val parent = File(req.path).parent
                val siblings: List<String> = if (req.local) emptyList<String>() else visible()
                    .filter { !it.isDir && FileKinds.kind(it.name) == kind && File(it.path).parent == parent }
                    .map { it.path }
                val list = if (siblings.contains(req.path)) siblings else listOf(req.path)
                viewerStack.add(Viewer(kind, list, list.indexOf(req.path), req.local))
            }
            Kind.VIDEO, Kind.ARCHIVE -> viewerStack.add(Viewer(kind, listOf(req.path), 0, req.local))
            else -> message = "Для этого типа файла нет встроенного просмотра"
        }
    }

    fun closeViewer() {
        val v = viewerStack.removeLastOrNull() ?: return
        if (v.kind == Kind.ARCHIVE) archives.remove("${v.paths[0]}|${v.local}")?.close()
    }

    /** Копия файла во временной папке (для сторонних приложений). Блокирующий вызов — только из IO. */
    private fun materialize(req: OpenRequest): File {
        if (req.local) return File(req.path)
        val f = fs ?: throw IOException("Нет доступа к файлам")
        val dest = OpenCache.fileFor(ctx, req.path, req.name)
        copyProgress = 0f
        try {
            OpenCache.copy(RemoteSource(f, req.path), dest) { copyProgress = it }
        } finally {
            copyProgress = null
        }
        return dest
    }

    private fun launchSafely(intent: Intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            message = "Приложение не найдено"
        } catch (e: SecurityException) {
            message = "Нет доступа к файлу для этого приложения"
        }
    }

    /** Открыть в стороннем приложении (pkg/activity выбраны из списка совместимых). */
    fun openExternal(req: OpenRequest, pkg: String?, activity: String?, mimeOverride: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = materialize(req)
                val uri = Opener.uriFor(ctx, file)
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mimeOverride ?: FileKinds.mime(req.name))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (pkg != null && activity != null) intent.setClassName(pkg, activity)
                withContext(Dispatchers.Main) {
                    openRequest = null
                    launchSafely(intent)
                }
            } catch (e: Exception) {
                message = e.message ?: "Не удалось открыть файл"
            }
        }
    }

    /** Установка через обычный (или выбранный сторонний) установщик пакетов. */
    fun installApk(req: OpenRequest, pkg: String?, activity: String?, stock: Boolean) {
        if (stock && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            message = "Разрешите KoFiles устанавливать приложения и повторите"
            launchSafely(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
            return
        }
        openExternal(req, pkg, activity, Opener.APK_MIME)
    }

    /** «Свой» установщик: pm install с правами shell/root через Shizuku или ADB. */
    fun installViaShell(req: OpenRequest) {
        val f = fs
        if (f == null) {
            message = "Нужен режим Shizuku или ADB"
            return
        }
        openRequest = null
        message = "Установка…"
        viewModelScope.launch(Dispatchers.IO) {
            message = try {
                f.pmInstall(req.path)
                "Приложение установлено"
            } catch (e: Exception) {
                e.message ?: "Ошибка установки"
            }
        }
    }

    fun shareFile(req: OpenRequest) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = materialize(req)
                val uri = Opener.uriFor(ctx, file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType(FileKinds.mime(req.name))
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = ClipData.newRawUri("", uri)
                withContext(Dispatchers.Main) {
                    openRequest = null
                    launchSafely(Intent.createChooser(send, "Поделиться"))
                }
            } catch (e: Exception) {
                message = e.message ?: "Не удалось поделиться файлом"
            }
        }
    }

    // ---------- архивы ----------
    fun archiveState(path: String, local: Boolean): ArchiveState =
        archives.getOrPut("$path|$local") { ArchiveState(path, File(path).name, local) }

    fun ensureLoaded(st: ArchiveState) {
        if (st.started) return
        st.started = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = materialize(OpenRequest(st.path, st.name, 0, st.local))
                val reader = Archives.open(file, st.name)
                st.reader = reader
                st.entries = reader.entries()
            } catch (e: Throwable) {
                st.error = e.message ?: e.toString()
            }
            st.loading = false
        }
    }

    /** Файл из архива → временная копия → то же окно «чем открыть». */
    fun openArchiveEntry(st: ArchiveState, child: ArchiveChild) {
        val reader = st.reader ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                st.busy = "Распаковка: ${child.name}"
                val dest = OpenCache.fileFor(ctx, st.path + "!" + child.fullPath, child.name)
                st.lock.withLock {
                    reader.open(child.fullPath).use { ins -> dest.outputStream().use { ins.copyTo(it) } }
                }
                requestOpen(OpenRequest(dest.absolutePath, child.name, dest.length(), true))
            } catch (e: Exception) {
                message = e.message ?: "Не удалось распаковать файл"
            } finally {
                st.busy = null
                st.busyProgress = 0f
            }
        }
    }

    /** Распаковка в папку рядом с архивом (через Shizuku/ADB, поэтому работает и в Android/data). names == null — всё. */
    fun extractArchive(st: ArchiveState, names: List<String>?) {
        val f = fs
        val reader = st.reader
        if (f == null || reader == null || st.local) {
            message = "Распаковка доступна только в режиме Shizuku или ADB"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                st.lock.withLock {
                    val parent = File(st.path).parent ?: "/"
                    val existing = f.list(parent).map { it.name }.toSet()
                    val lower = st.name.lowercase()
                    val base = listOf(".tar.gz", ".tar.bz2", ".tar.xz").firstOrNull { lower.endsWith(it) }
                        ?.let { st.name.dropLast(it.length) } ?: st.name.substringBeforeLast('.', st.name)
                    val destName = unique(base.ifBlank { "archive" }, existing)
                    val dest = joinPath(parent, destName)
                    val list = (if (names == null) st.entries else st.expand(names)).filter { Archives.isSafe(it.path) }
                    f.mkdir(dest)
                    var done = 0
                    for (e in list) {
                        st.busy = "Распаковка: ${done + 1} из ${list.size}"
                        st.busyProgress = done.toFloat() / list.size
                        val target = joinPath(dest, e.path)
                        if (e.isDir) f.mkdir(target) else writeEntry(f, reader, e.path, target)
                        done++
                    }
                    message = "Распаковано в «$destName»: $done"
                }
            } catch (e: Exception) {
                message = e.message ?: "Ошибка распаковки"
            } finally {
                st.busy = null
                st.busyProgress = 0f
            }
            reload()
        }
    }

    private fun writeEntry(f: RemoteFs, reader: com.example.kofiles.data.ArchiveReader, entryPath: String, target: String) {
        reader.open(entryPath).use { ins ->
            val buf = ByteArray(WRITE_CHUNK)
            var off = 0L
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                if (n == 0) continue
                f.write(target, off, buf, n)
                off += n
            }
            if (off == 0L) f.write(target, 0L, buf, 0) // пустой файл
        }
    }

    fun clearOpenCache() {
        viewModelScope.launch(Dispatchers.IO) {
            OpenCache.clear(ctx)
            message = "Временные файлы удалены"
        }
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
