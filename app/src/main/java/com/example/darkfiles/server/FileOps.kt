package com.example.darkfiles.server

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Файловые операции. Выполняются внутри процесса с повышенными правами
 * (Shizuku UserService или ADB-сервер), поэтому используют только java.io.
 * Ответ: [0] + данные  или  [1] + текст ошибки.
 */
object FileOps {
    const val FS = '\u0001'
    const val RS = '\u0002'

    fun call(method: String, a: Array<String>): ByteArray = try {
        byteArrayOf(0) + exec(method, a)
    } catch (t: Throwable) {
        byteArrayOf(1) + (t.message ?: t.javaClass.simpleName).toByteArray()
    }

    private fun exec(m: String, a: Array<String>): ByteArray {
        when (m) {
            "ping" -> return "pong".toByteArray()
            "uid" -> return android.os.Process.myUid().toString().toByteArray()
            "list" -> return list(File(a[0]))
            "size" -> return File(a[0]).length().toString().toByteArray()
            "read" -> return read(a[0], a[1].toLong(), a[2].toInt())
            "mkdir" -> if (!File(a[0]).mkdirs()) throw IOException("Не удалось создать папку")
            "touch" -> if (!File(a[0]).createNewFile()) throw IOException("Файл уже существует")
            "delete" -> if (!File(a[0]).deleteRecursively()) throw IOException("Не удалось удалить: ${File(a[0]).name}")
            "rename" -> rename(File(a[0]), File(a[1]))
            "copy" -> copy(File(a[0]), File(a[1]))
            "move" -> move(File(a[0]), File(a[1]))
            else -> throw IOException("Неизвестная команда: $m")
        }
        return ByteArray(0)
    }

    private fun list(dir: File): ByteArray {
        val files = dir.listFiles() ?: throw IOException("Нет доступа или это не папка")
        val sb = StringBuilder()
        for (f in files) {
            val d = f.isDirectory
            sb.append(if (d) 'd' else 'f').append(FS)
            sb.append(if (d) 0L else f.length()).append(FS)
            sb.append(f.lastModified()).append(FS)
            sb.append(f.name.replace(FS, ' ').replace(RS, ' ')).append(RS)
        }
        return sb.toString().toByteArray()
    }

    private fun read(path: String, off: Long, len: Int): ByteArray {
        RandomAccessFile(path, "r").use { raf ->
            raf.seek(off)
            val buf = ByteArray(len)
            var n = 0
            while (n < len) {
                val r = raf.read(buf, n, len - n)
                if (r <= 0) break
                n += r
            }
            return if (n == len) buf else buf.copyOf(n)
        }
    }

    private fun rename(from: File, to: File) {
        if (to.exists()) throw IOException("Уже существует: ${to.name}")
        if (!from.renameTo(to)) throw IOException("Не удалось переименовать")
    }

    private fun copy(from: File, to: File) {
        if (to.exists()) throw IOException("Уже существует: ${to.name}")
        if (to.path.startsWith(from.path + "/")) throw IOException("Нельзя копировать папку в саму себя")
        if (!from.copyRecursively(to, false)) throw IOException("Не удалось скопировать: ${from.name}")
    }

    private fun move(from: File, to: File) {
        if (to.exists()) throw IOException("Уже существует: ${to.name}")
        if (from.renameTo(to)) return
        copy(from, to)
        from.deleteRecursively()
    }
}
