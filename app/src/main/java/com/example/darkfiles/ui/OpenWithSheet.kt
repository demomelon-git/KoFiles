@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.kofiles.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.kofiles.MainViewModel
import com.example.kofiles.data.FileKinds
import com.example.kofiles.data.Kind
import com.example.kofiles.data.Opener

/**
 * Окно, которое появляется при нажатии на файл: встроенные просмотрщики + проверка,
 * какие приложения на устройстве умеют открывать такой тип файла. Для APK — выбор установщика.
 */
@Composable
fun OpenWithSheet(vm: MainViewModel) {
    val req = vm.openRequest ?: return
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val cs = MaterialTheme.colorScheme
    val kind = remember(req) { FileKinds.kind(req.name) }
    val mime = remember(req) { FileKinds.mime(req.name) }
    val apps = remember(req) { Opener.compatibleApps(ctx, mime) }
    val isApk = kind == Kind.APK
    val stock = remember(apps) { apps.firstOrNull { Opener.isStockInstaller(it) } }
    val custom = remember(apps) { apps.filter { !Opener.isStockInstaller(it) } }
    val unknownBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !pm.canRequestPackageInstalls()

    ModalBottomSheet(
        onDismissRequest = { vm.openRequest = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)
        ) {
            // заголовок
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(kindIcon(kind), null, tint = kindColor(kind), modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(req.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(
                        "$mime · ${Fmt.size(req.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            val progress = vm.copyProgress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    "Копирование во временную папку: ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // встроенные возможности KoFiles
            SectionLabel("Открыть в KoFiles")
            when (kind) {
                Kind.VIDEO -> OptionRow("Видеоплеер", "Встроенный, читает файл напрямую", { Icon(Icons.Default.Movie, null, tint = cs.primary) }) {
                    vm.openBuiltIn(req, false)
                }
                Kind.IMAGE -> OptionRow("Просмотр изображений", "Листайте фото из этой папки, увеличение двумя пальцами", { Icon(Icons.Default.Image, null, tint = cs.primary) }) {
                    vm.openBuiltIn(req, false)
                }
                Kind.AUDIO -> OptionRow("Музыкальный плеер", "Плейлист из треков этой папки, обложка и теги", { Icon(Icons.Default.AudioFile, null, tint = cs.primary) }) {
                    vm.openBuiltIn(req, false)
                }
                else -> {}
            }
            if (FileKinds.canOpenAsArchive(req.name)) {
                OptionRow(
                    "Открыть как архив",
                    if (isApk) "Посмотреть содержимое APK и распаковать" else "Просмотр и распаковка",
                    { Icon(Icons.Default.Archive, null, tint = cs.primary) }
                ) { vm.openBuiltIn(req, true) }
            }
            if (kind != Kind.VIDEO && kind != Kind.IMAGE && kind != Kind.AUDIO && !FileKinds.canOpenAsArchive(req.name)) {
                Text(
                    "Встроенного просмотра для этого типа нет.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (isApk) {
                SectionLabel("Установка")
                OptionRow(
                    "Обычный установщик",
                    if (unknownBlocked) "Сначала нужно разрешить установку из KoFiles" else "Системный установщик пакетов",
                    {
                        if (stock != null) AppIcon(stock.activityInfo.packageName, 40.dp)
                        else Icon(Icons.Default.Android, null, tint = cs.primary)
                    }
                ) { vm.installApk(req, stock?.activityInfo?.packageName, stock?.activityInfo?.name, true) }

                for (ri in custom) {
                    OptionRow(
                        ri.loadLabel(pm).toString(),
                        "Кастомный установщик на этом устройстве",
                        { AppIcon(ri.activityInfo.packageName, 40.dp) }
                    ) { vm.installApk(req, ri.activityInfo.packageName, ri.activityInfo.name, false) }
                }
                if (vm.fs != null) {
                    OptionRow(
                        "Установить через KoFiles (Shizuku/ADB)",
                        "Установка с правами shell без системного окна",
                        { Icon(Icons.Default.Bolt, null, tint = cs.primary) }
                    ) { vm.installViaShell(req) }
                }
                if (custom.isEmpty()) {
                    Text(
                        "Сторонние установщики на устройстве не найдены.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                SectionLabel("Совместимые приложения (${apps.size})")
                if (apps.isEmpty()) {
                    Text(
                        "Приложений для типа $mime не найдено.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                for (ri in apps) {
                    OptionRow(ri.loadLabel(pm).toString(), null, { AppIcon(ri.activityInfo.packageName, 40.dp) }) {
                        vm.openExternal(req, ri.activityInfo.packageName, ri.activityInfo.name)
                    }
                }
            }

            SectionLabel("Другое")
            OptionRow("Поделиться", null, { Icon(Icons.Default.Share, null, tint = cs.primary) }) { vm.shareFile(req) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OptionRow(title: String, subtitle: String?, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
