package com.example.kofiles.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Проверка совместимости: какие приложения на устройстве умеют открывать файл такого типа. */
object Opener {
    const val APK_MIME = "application/vnd.android.package-archive"

    private val STOCK_INSTALLERS = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.packageinstaller"
    )

    fun uriFor(ctx: Context, file: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)

    /** Приложения, которые заявили, что умеют открывать этот MIME-тип (ACTION_VIEW). */
    fun compatibleApps(ctx: Context, mime: String): List<ResolveInfo> {
        val probe = Uri.parse("content://${ctx.packageName}.files/probe")
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(probe, mime)
        return try {
            ctx.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .filter { it.activityInfo.packageName != ctx.packageName }
                .distinctBy { it.activityInfo.packageName }
                .sortedBy { it.loadLabel(ctx.packageManager).toString().lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Стандартный системный установщик пакетов (а не сторонний вроде SAI/InstallerX). */
    fun isStockInstaller(ri: ResolveInfo): Boolean {
        val p = ri.activityInfo.packageName
        if (p in STOCK_INSTALLERS) return true
        val system = (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        return system && p.contains("packageinstaller", ignoreCase = true)
    }
}
