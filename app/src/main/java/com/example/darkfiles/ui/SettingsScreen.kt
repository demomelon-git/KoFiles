package com.example.darkfiles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.darkfiles.MainViewModel
import java.io.File

@Composable
fun SettingsTab(vm: MainViewModel) {
    val p = vm.prefs
    var dlg by remember { mutableStateOf("") }
    val sortNames = listOf("По имени", "По размеру", "По дате")
    val themeNames = listOf("Тёмная", "Как в системе", "Светлая")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))

        Group("Внешний вид")
        ClickRow("Тема", themeNames[p.theme]) { dlg = "theme" }

        Group("Файлы")
        SwitchRow("Показывать скрытые файлы", "Имена, начинающиеся с точки", p.showHidden) { p.saveShowHidden(it) }
        SwitchRow("Папки сверху", null, p.foldersFirst) { p.saveFoldersFirst(it) }
        ClickRow("Сортировка", sortNames[p.sortBy]) { dlg = "sort" }
        SwitchRow("Сортировать по убыванию", null, p.sortDesc) { p.saveSortDesc(it) }
        SwitchRow("Подтверждать удаление", null, p.confirmDelete) { p.saveConfirmDelete(it) }

        Group("Подключение")
        SwitchRow("Подключаться автоматически", "При запуске и возврате в приложение", p.autoConnect) { p.saveAutoConnect(it) }
        ClickRow("Порт ADB-сервера", p.adbPort.toString()) { dlg = "port" }

        Group("Видео")
        SwitchRow("Автозапуск воспроизведения", null, p.autoplay) { p.saveAutoplay(it) }

        Group("Быстрый доступ")
        if (p.shortcuts.isEmpty()) {
            Text(
                "Адреса пока не добавлены. Добавьте их на вкладке «Приложения» (выберите приложение → значок закладки) или вручную.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        for (s in p.shortcuts.toList()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(s.title)
                    Text(
                        s.path,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { p.removeShortcut(s) }) { Icon(Icons.Default.Delete, "Удалить адрес") }
            }
        }
        ClickRow("Добавить адрес вручную", "Например, ${vm.root}/Android/data/…") { dlg = "addpath" }

        Group("О приложении")
        Text(
            "Dark Files 1.0 · режимы: Shizuku, ADB через ПК, без запуска",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }

    when (dlg) {
        "theme" -> RadioDialog("Тема", themeNames, p.theme, { dlg = "" }) { p.saveTheme(it); dlg = "" }
        "sort" -> RadioDialog("Сортировка", sortNames, p.sortBy, { dlg = "" }) { p.saveSortBy(it); dlg = "" }
        "port" -> TextDialog("Порт ADB-сервера", p.adbPort.toString(), "OK", { dlg = "" }) {
            p.saveAdbPort(it.toIntOrNull()?.coerceIn(1024, 65535) ?: 38517)
            dlg = ""
        }
        "addpath" -> TextDialog("Адрес папки", "${vm.root}/Android/data/", "Добавить", { dlg = "" }) { path ->
            val clean = path.trimEnd('/').ifEmpty { "/" }
            val name = File(clean).name.ifEmpty { "/" }
            vm.message = if (p.addShortcut(name, clean)) "Адрес добавлен" else "Этот адрес уже есть"
            dlg = ""
        }
    }
}

@Composable
private fun Group(text: String) {
    SectionLabel(text, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
private fun ClickRow(title: String, sub: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title)
        if (sub != null) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
