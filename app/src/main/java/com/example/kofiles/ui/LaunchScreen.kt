package com.example.kofiles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.kofiles.MODE_ADB
import com.example.kofiles.MODE_NONE
import com.example.kofiles.MODE_SHIZUKU
import com.example.kofiles.MainViewModel

@Composable
fun LaunchTab(vm: MainViewModel) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val mode = vm.prefs.mode
    var script by remember { mutableStateOf<String?>(null) }
    val shizukuApp = remember { ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Способ запуска", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Выберите, как приложение получит доступ к файлам Android.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant
        )

        ModeCard(
            Icons.Default.Bolt, "Shizuku",
            "Полный доступ к Android/data и Android/obb через Shizuku (или совместимый форк). Служба должна быть запущена.",
            mode == MODE_SHIZUKU
        ) {
            if (mode == MODE_SHIZUKU) Text(vm.statusText, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.chooseMode(MODE_SHIZUKU) }) { Text(if (mode == MODE_SHIZUKU) "Переподключить" else "Выбрать") }
                if (shizukuApp != null) {
                    OutlinedButton(onClick = { ctx.startActivity(shizukuApp) }) { Text("Открыть Shizuku") }
                }
            }
        }

        ModeCard(
            Icons.Default.Usb, "ADB (через ПК)",
            "Доступ с правами shell: запускаете маленький сервер командой с компьютера. Root и Shizuku не нужны.",
            mode == MODE_ADB
        ) {
            Text(
                "1. Включите «Отладку по USB», подключите телефон к ПК.\n" +
                    "2. Нажмите «Создать скрипт».\n" +
                    "3. Выполните на ПК показанную команду (нужен adb).\n" +
                    "4. Нажмите «Подключиться».\n" +
                    "Порт: ${vm.prefs.adbPort} (меняется в настройках; после смены создайте скрипт заново).",
                style = MaterialTheme.typography.bodySmall
            )
            val cmd = script?.let { "adb shell sh $it" }
            if (cmd != null) {
                SelectionContainer {
                    Text(
                        cmd,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().background(cs.background, RoundedCornerShape(8.dp)).padding(10.dp)
                    )
                }
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(cmd))
                    vm.message = "Команда скопирована"
                }) { Text("Копировать команду") }
            }
            if (mode == MODE_ADB) Text(vm.statusText, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { script = vm.writeScript() }) { Text("Создать скрипт") }
                Button(onClick = { vm.chooseMode(MODE_ADB) }) { Text("Подключиться") }
            }
            TextButton(onClick = { vm.stopAdbServer() }) { Text("Остановить сервер на телефоне") }
        }

        ModeCard(
            Icons.Default.Block, "Без запуска",
            "Файловый доступ отключён. Работает только просмотр списка приложений.",
            mode == MODE_NONE
        ) {
            Button(onClick = { vm.chooseMode(MODE_NONE) }) { Text("Выбрать") }
        }
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    desc: String,
    selected: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = if (selected) cs.primaryContainer else cs.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = cs.primary)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Default.CheckCircle, "Выбрано", tint = cs.primary)
            }
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            content()
        }
    }
}
