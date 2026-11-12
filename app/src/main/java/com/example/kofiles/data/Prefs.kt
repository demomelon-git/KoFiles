package com.example.kofiles.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var mode by mutableStateOf(sp.getString("mode", "none") ?: "none")
        private set
    var theme by mutableIntStateOf(sp.getInt("theme", 0)) // 0 тёмная, 1 системная, 2 светлая
        private set
    var showHidden by mutableStateOf(sp.getBoolean("hidden", false))
        private set
    var foldersFirst by mutableStateOf(sp.getBoolean("foldersFirst", true))
        private set
    var confirmDelete by mutableStateOf(sp.getBoolean("confirmDelete", true))
        private set
    var autoConnect by mutableStateOf(sp.getBoolean("autoConnect", true))
        private set
    var autoplay by mutableStateOf(sp.getBoolean("autoplay", true))
        private set
    var sortBy by mutableIntStateOf(sp.getInt("sortBy", 0)) // 0 имя, 1 размер, 2 дата
        private set
    var sortDesc by mutableStateOf(sp.getBoolean("sortDesc", false))
        private set
    var adbPort by mutableIntStateOf(sp.getInt("adbPort", 38517))
        private set
    var askOpenWith by mutableStateOf(sp.getBoolean("askOpenWith", true))
        private set

    val shortcuts = mutableStateListOf<Shortcut>()

    init {
        shortcuts.addAll(loadShortcuts())
    }

    val token: String
        get() = sp.getString("token", null)
            ?: UUID.randomUUID().toString().replace("-", "").also { sp.edit().putString("token", it).apply() }

    @JvmName("updateMode")
    fun setMode(v: String) { mode = v; sp.edit().putString("mode", v).apply() }
    @JvmName("updateTheme")
    fun setTheme(v: Int) { theme = v; sp.edit().putInt("theme", v).apply() }
    @JvmName("updateShowHidden")
    fun setShowHidden(v: Boolean) { showHidden = v; sp.edit().putBoolean("hidden", v).apply() }
    @JvmName("updateFoldersFirst")
    fun setFoldersFirst(v: Boolean) { foldersFirst = v; sp.edit().putBoolean("foldersFirst", v).apply() }
    @JvmName("updateConfirmDelete")
    fun setConfirmDelete(v: Boolean) { confirmDelete = v; sp.edit().putBoolean("confirmDelete", v).apply() }
    @JvmName("updateAutoConnect")
    fun setAutoConnect(v: Boolean) { autoConnect = v; sp.edit().putBoolean("autoConnect", v).apply() }
    @JvmName("updateAutoplay")
    fun setAutoplay(v: Boolean) { autoplay = v; sp.edit().putBoolean("autoplay", v).apply() }
    @JvmName("updateSortBy")
    fun setSortBy(v: Int) { sortBy = v; sp.edit().putInt("sortBy", v).apply() }
    @JvmName("updateSortDesc")
    fun setSortDesc(v: Boolean) { sortDesc = v; sp.edit().putBoolean("sortDesc", v).apply() }
    @JvmName("updateAdbPort")
    fun setAdbPort(v: Int) { adbPort = v; sp.edit().putInt("adbPort", v).apply() }
    @JvmName("updateAskOpenWith")
    fun setAskOpenWith(v: Boolean) { askOpenWith = v; sp.edit().putBoolean("askOpenWith", v).apply() }

    private fun loadShortcuts(): List<Shortcut> = try {
        val a = JSONArray(sp.getString("shortcuts", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Shortcut(o.getString("t"), o.getString("p"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun persist() {
        val a = JSONArray()
        for (s in shortcuts) a.put(JSONObject().put("t", s.title).put("p", s.path))
        sp.edit().putString("shortcuts", a.toString()).apply()
    }

    fun addShortcut(title: String, path: String): Boolean {
        if (shortcuts.any { it.path == path }) return false
        shortcuts.add(Shortcut(title, path))
        persist()
        return true
    }

    fun removeShortcut(s: Shortcut) {
        shortcuts.remove(s)
        persist()
    }
}
