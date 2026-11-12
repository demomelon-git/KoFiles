package com.example.kofiles.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile

data class ArchiveEntryInfo(val path: String, val isDir: Boolean, val size: Long)

interface ArchiveReader : Closeable {
    fun entries(): List<ArchiveEntryInfo>
    fun open(entryPath: String): InputStream
}

object Archives {
    fun normalize(n: String): String = n.replace('\\', '/').trimStart('/').removePrefix("./").trimEnd('/')

    /** Защита от zip-slip: пути с «..» не распаковываем. */
    fun isSafe(path: String): Boolean = path.isNotEmpty() && path.split('/').none { it == ".." }

    fun open(file: File, name: String): ArchiveReader {
        val l = name.lowercase()
        return when {
            l.endsWith(".7z") -> SevenZReader(file)
            l.endsWith(".tar") || l.endsWith(".tar.gz") || l.endsWith(".tgz") || l.endsWith(".tar.bz2") ||
                l.endsWith(".tbz2") || l.endsWith(".tar.xz") || l.endsWith(".txz") -> TarReader(file, l)
            l.endsWith(".gz") || l.endsWith(".bz2") || l.endsWith(".xz") -> SingleReader(file, l)
            else -> ZipReader(file)
        }
    }
}

// ---------------------------------------------------------------- ZIP (zip, jar, apk, xapk, apks, cbz…)

private class ZipReader(file: File) : ArchiveReader {
    private val zip: ZipFile = openZip(file)

    override fun entries(): List<ArchiveEntryInfo> =
        zip.entries().toList().map { ArchiveEntryInfo(Archives.normalize(it.name), it.isDirectory, it.size) }

    override fun open(entryPath: String): InputStream {
        val e = zip.entries().toList().firstOrNull { Archives.normalize(it.name) == entryPath }
            ?: throw FileNotFoundException(entryPath)
        return zip.getInputStream(e)
    }

    override fun close() = zip.close()

    private companion object {
        /** Старые архивы из Windows хранят русские имена в CP866 — пробуем несколько кодировок. */
        fun openZip(file: File): ZipFile {
            var fallback: ZipFile? = null
            var lastError: Exception? = null
            for (cs in listOf("UTF-8", "IBM866", "windows-1251", "ISO-8859-1")) {
                try {
                    val z = ZipFile(file, Charset.forName(cs))
                    val broken = z.entries().toList().any { it.name.contains('\uFFFD') }
                    if (!broken) {
                        fallback?.close()
                        return z
                    }
                    if (fallback == null) fallback = z else z.close()
                } catch (e: Exception) {
                    lastError = e
                }
            }
            return fallback ?: throw (lastError ?: IOException("Не удалось открыть архив"))
        }
    }
}

// ---------------------------------------------------------------- 7z

private class SevenZReader(file: File) : ArchiveReader {
    @Suppress("DEPRECATION")
    private val sz = SevenZFile(file)

    override fun entries(): List<ArchiveEntryInfo> =
        sz.entries.map { ArchiveEntryInfo(Archives.normalize(it.name), it.isDirectory, it.size) }

    override fun open(entryPath: String): InputStream {
        val e = sz.entries.firstOrNull { Archives.normalize(it.name) == entryPath }
            ?: throw FileNotFoundException(entryPath)
        return sz.getInputStream(e)
    }

    override fun close() = sz.close()
}

// ---------------------------------------------------------------- TAR (.tar, .tar.gz, .tgz, .tar.bz2, .tar.xz)

private class TarReader(private val file: File, private val lname: String) : ArchiveReader {
    private fun stream(): InputStream {
        val raw = BufferedInputStream(FileInputStream(file))
        return when {
            lname.endsWith(".tar.gz") || lname.endsWith(".tgz") -> GzipCompressorInputStream(raw)
            lname.endsWith(".tar.bz2") || lname.endsWith(".tbz2") -> BZip2CompressorInputStream(raw)
            lname.endsWith(".tar.xz") || lname.endsWith(".txz") -> XZCompressorInputStream(raw)
            else -> raw
        }
    }

    override fun entries(): List<ArchiveEntryInfo> {
        val res = ArrayList<ArchiveEntryInfo>()
        TarArchiveInputStream(stream()).use { tis ->
            var e = tis.nextEntry
            while (e != null) {
                res.add(ArchiveEntryInfo(Archives.normalize(e.name), e.isDirectory, e.size))
                e = tis.nextEntry
            }
        }
        return res
    }

    override fun open(entryPath: String): InputStream {
        val tis = TarArchiveInputStream(stream())
        var e = tis.nextEntry
        while (e != null) {
            if (Archives.normalize(e.name) == entryPath) return tis
            e = tis.nextEntry
        }
        tis.close()
        throw FileNotFoundException(entryPath)
    }

    override fun close() {}
}

// ---------------------------------------------------------------- одиночные .gz / .bz2 / .xz

private class SingleReader(private val file: File, private val lname: String) : ArchiveReader {
    private val inner = file.name.substringBeforeLast('.', file.name)

    override fun entries(): List<ArchiveEntryInfo> = listOf(ArchiveEntryInfo(inner, false, -1))

    override fun open(entryPath: String): InputStream {
        val raw = BufferedInputStream(FileInputStream(file))
        return when {
            lname.endsWith(".gz") -> GzipCompressorInputStream(raw)
            lname.endsWith(".bz2") -> BZip2CompressorInputStream(raw)
            else -> XZCompressorInputStream(raw)
        }
    }

    override fun close() {}
}

// ---------------------------------------------------------------- состояние открытого архива (живёт во ViewModel)

data class ArchiveChild(val name: String, val fullPath: String, val isDir: Boolean, val size: Long)

class ArchiveState(val path: String, val name: String, val local: Boolean) {
    var started = false
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var entries by mutableStateOf<List<ArchiveEntryInfo>>(emptyList())
    var dir by mutableStateOf("")
    var busy by mutableStateOf<String?>(null)
    var busyProgress by mutableFloatStateOf(0f)
    val selected = mutableStateListOf<String>()
    var reader: ArchiveReader? = null
    val lock = Mutex()

    /** Содержимое текущей папки внутри архива. */
    fun children(): List<ArchiveChild> {
        val prefix = if (dir.isEmpty()) "" else "$dir/"
        val map = LinkedHashMap<String, ArchiveChild>()
        for (e in entries) {
            if (!e.path.startsWith(prefix) || e.path == dir) continue
            val rest = e.path.removePrefix(prefix)
            if (rest.isEmpty()) continue
            val first = rest.substringBefore('/')
            val isDir = rest.contains('/') || e.isDir
            val old = map[first]
            if (old == null) {
                map[first] = ArchiveChild(first, prefix + first, isDir, if (isDir) -1 else e.size)
            } else if (isDir && !old.isDir) {
                map[first] = old.copy(isDir = true, size = -1)
            }
        }
        return map.values.sortedWith(compareByDescending<ArchiveChild> { it.isDir }.thenBy { it.name.lowercase() })
    }

    /** Выбранные пути + всё, что лежит внутри выбранных папок. */
    fun expand(paths: List<String>): List<ArchiveEntryInfo> =
        entries.filter { e -> paths.any { p -> e.path == p || e.path.startsWith("$p/") } }

    fun close() {
        try { reader?.close() } catch (e: Exception) { }
    }
}
