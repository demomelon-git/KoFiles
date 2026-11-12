package com.example.kofiles.data

import android.webkit.MimeTypeMap

enum class Kind { DIR, VIDEO, IMAGE, AUDIO, ARCHIVE, APK, OTHER }

object FileKinds {
    private val VIDEO = setOf("mp4", "mkv", "webm", "3gp", "avi", "mov", "m4v", "ts", "flv", "wmv", "mpg", "mpeg")
    private val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    private val AUDIO = setOf("mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "opus", "mka", "amr", "mid", "midi")
    private val ARCHIVE = setOf("zip", "jar", "apks", "xapk", "apkm", "cbz", "7z", "tar", "tgz", "gz", "bz2", "xz", "tbz2", "txz")

    fun ext(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun kind(name: String): Kind {
        val e = ext(name)
        return when {
            e in VIDEO -> Kind.VIDEO
            e in IMAGE -> Kind.IMAGE
            e in AUDIO -> Kind.AUDIO
            e == "apk" -> Kind.APK
            e in ARCHIVE -> Kind.ARCHIVE
            else -> Kind.OTHER
        }
    }

    /** Можно ли открыть встроенным просмотром архивов (APK — это тоже zip). */
    fun canOpenAsArchive(name: String): Boolean = ext(name) in ARCHIVE || ext(name) == "apk"

    fun mime(name: String): String = when (val e = ext(name)) {
        "apk" -> "application/vnd.android.package-archive"
        "xapk" -> "application/xapk-package-archive"
        "apkm" -> "application/vnd.apkm"
        "apks" -> "application/octet-stream"
        "mkv" -> "video/x-matroska"
        "7z" -> "application/x-7z-compressed"
        "flac" -> "audio/flac"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(e) ?: "*/*"
    }
}
