package com.example.kofiles.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Источник байтов: файл «на той стороне» (Shizuku/ADB) или обычный локальный файл. */
interface ByteSource {
    fun size(): Long
    fun read(offset: Long, len: Int): ByteArray
    fun close() {}
}

typealias SourceProvider = (String) -> ByteSource

class RemoteSource(private val fs: RemoteFs, private val path: String) : ByteSource {
    private val cachedSize: Long by lazy { fs.size(path) }
    override fun size(): Long = cachedSize
    override fun read(offset: Long, len: Int): ByteArray = fs.read(path, offset, len)
}

class LocalSource(file: File) : ByteSource {
    private val raf = RandomAccessFile(file, "r")
    override fun size(): Long = raf.length()
    override fun read(offset: Long, len: Int): ByteArray {
        raf.seek(offset)
        val buf = ByteArray(len)
        val n = raf.read(buf)
        return when {
            n <= 0 -> ByteArray(0)
            n == len -> buf
            else -> buf.copyOf(n)
        }
    }
    override fun close() = raf.close()
}

/** Временные копии файлов (для сторонних приложений, архивов, установки). Лежат во внешнем кэше — оттуда их видит и shell. */
object OpenCache {
    fun root(ctx: Context): File = File(ctx.externalCacheDir ?: ctx.cacheDir, "open").also { it.mkdirs() }

    fun safe(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }

    fun fileFor(ctx: Context, sourcePath: String, name: String): File {
        val dir = File(root(ctx), Integer.toHexString(sourcePath.hashCode()))
        dir.mkdirs()
        return File(dir, safe(name))
    }

    fun clear(ctx: Context) {
        root(ctx).deleteRecursively()
    }

    fun size(ctx: Context): Long = root(ctx).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Удаляет копии старше двух суток. */
    fun trimOld(ctx: Context) {
        val limit = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000
        root(ctx).walkTopDown().filter { it.isFile && it.lastModified() < limit }.forEach { it.delete() }
    }

    fun copy(src: ByteSource, dest: File, onProgress: (Float) -> Unit) {
        val total = src.size()
        if (dest.exists() && dest.length() == total && total > 0) {
            onProgress(1f)
            return
        }
        val tmp = File(dest.parentFile, dest.name + ".part")
        tmp.outputStream().use { out ->
            var off = 0L
            while (off < total) {
                val chunk = src.read(off, minOf(256L * 1024, total - off).toInt())
                if (chunk.isEmpty()) throw IOException("Файл прочитан не полностью")
                out.write(chunk)
                off += chunk.size
                onProgress(off.toFloat() / total)
            }
        }
        dest.delete()
        if (!tmp.renameTo(dest)) throw IOException("Не удалось сохранить временную копию")
    }
}
