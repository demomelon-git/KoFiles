package com.example.kofiles.server

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * Запускается с ПК: adb shell sh .../start.sh  (процесс получает права shell).
 * Слушает только 127.0.0.1, каждый запрос проверяется по токену.
 */
object AdbServer {
    @JvmStatic
    fun main(args: Array<String>) {
        val port = args.getOrNull(0)?.toIntOrNull() ?: 38517
        val token = args.getOrNull(1) ?: ""
        val pool = Executors.newCachedThreadPool()
        val server = ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"))
        while (true) {
            val s = server.accept()
            pool.execute { handle(s, token) }
        }
    }

    private fun handle(s: Socket, token: String) {
        var stop = false
        try {
            s.soTimeout = 15000
            val inp = DataInputStream(s.getInputStream().buffered())
            val out = DataOutputStream(s.getOutputStream().buffered())
            val t = inp.readUTF()
            val method = inp.readUTF()
            val n = inp.readInt()
            if (n < 0 || n > 8) throw IllegalArgumentException("bad args")
            val args = Array(n) {
                val len = inp.readInt()
                if (len < 0 || len > 8 * 1024 * 1024) throw IllegalArgumentException("bad arg")
                val b = ByteArray(len)
                inp.readFully(b)
                String(b, Charsets.UTF_8)
            }
            val res: ByteArray = when {
                t != token -> byteArrayOf(1) + "Неверный токен".toByteArray()
                method == "stop" -> { stop = true; byteArrayOf(0) }
                else -> FileOps.call(method, args)
            }
            out.writeInt(res.size)
            out.write(res)
            out.flush()
        } catch (e: Throwable) {
            // игнорируем: клиент отключился или прислал мусор
        } finally {
            try { s.close() } catch (e: Throwable) { }
        }
        if (stop) exitProcess(0)
    }
}
