package com.example.darkfiles.data

import com.example.darkfiles.IFileService
import com.example.darkfiles.server.FileOps
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

data class FileItem(val name: String, val path: String, val isDir: Boolean, val size: Long, val modified: Long)
data class Shortcut(val title: String, val path: String)
data class Clip(val paths: List<String>, val cut: Boolean)
data class AppEntry(val label: String, val pkg: String, val system: Boolean, val dataDir: String)

fun joinPath(dir: String, name: String) = if (dir.endsWith("/")) dir + name else "$dir/$name"

interface Transport {
    fun call(method: String, args: Array<String>): ByteArray
}

/** Shizuku: вызов через Binder (AIDL). */
class BinderTransport(private val s: IFileService) : Transport {
    override fun call(method: String, args: Array<String>): ByteArray =
        try {
            s.call(method, args)
        } catch (e: android.os.RemoteException) {
            throw IOException("Служба Shizuku недоступна")
        }
}

/** ADB: вызов через локальный сокет к серверу, запущенному с ПК. */
class AdbTransport(private val port: Int, private val token: String) : Transport {
    override fun call(method: String, args: Array<String>): ByteArray =
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", port), 2000)
                s.soTimeout = 20000
                val out = DataOutputStream(s.getOutputStream().buffered())
                out.writeUTF(token)
                out.writeUTF(method)
                out.writeInt(args.size)
                for (a in args) out.writeUTF(a)
                out.flush()
                val inp = DataInputStream(s.getInputStream().buffered())
                val n = inp.readInt()
                val buf = ByteArray(n)
                inp.readFully(buf)
                buf
            }
        } catch (e: IOException) {
            throw IOException("Сервер ADB недоступен: ${e.message}")
        }
}

/** Одинаковый API поверх любого транспорта. Вызывать только не из главного потока. */
class RemoteFs(private val t: Transport) {
    private fun call(m: String, vararg a: String): ByteArray {
        val r = t.call(m, arrayOf(*a))
        if (r.isEmpty()) throw IOException("Пустой ответ сервера")
        if (r[0].toInt() != 0) throw IOException(String(r, 1, r.size - 1, Charsets.UTF_8))
        return r.copyOfRange(1, r.size)
    }

    fun ping(): String = String(call("ping"))
    fun uid(): Int = String(call("uid")).trim().toInt()
    fun size(path: String): Long = String(call("size", path)).trim().toLong()
    fun read(path: String, off: Long, len: Int): ByteArray = call("read", path, off.toString(), len.toString())
    fun mkdir(p: String) { call("mkdir", p) }
    fun touch(p: String) { call("touch", p) }
    fun delete(p: String) { call("delete", p) }
    fun rename(a: String, b: String) { call("rename", a, b) }
    fun copy(a: String, b: String) { call("copy", a, b) }
    fun move(a: String, b: String) { call("move", a, b) }
    fun stop() { call("stop") }

    fun list(dir: String): List<FileItem> {
        val s = String(call("list", dir), Charsets.UTF_8)
        if (s.isEmpty()) return emptyList()
        val res = ArrayList<FileItem>()
        for (rec in s.split(FileOps.RS)) {
            if (rec.isEmpty()) continue
            val p = rec.split(FileOps.FS)
            if (p.size < 4) continue
            res.add(FileItem(p[3], joinPath(dir, p[3]), p[0] == "d", p[1].toLongOrNull() ?: 0L, p[2].toLongOrNull() ?: 0L))
        }
        return res
    }
}
