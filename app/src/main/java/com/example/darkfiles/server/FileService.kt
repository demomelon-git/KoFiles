package com.example.darkfiles.server

import com.example.darkfiles.IFileService
import kotlin.system.exitProcess

/** Shizuku UserService: запускается Shizuku в своём процессе (shell или root). */
class FileService : IFileService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun call(method: String, args: Array<String>): ByteArray = FileOps.call(method, args)
}
