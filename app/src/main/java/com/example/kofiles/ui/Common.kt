package com.example.kofiles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.kofiles.data.FileItem
import com.example.kofiles.data.Kind as DataKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    fun size(b: Long): String {
        if (b < 1024) return "$b Б"
        val u = arrayOf("КБ", "МБ", "ГБ", "ТБ")
        var v = b.toDouble()
        var i = -1
        while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
        return String.format(Locale.getDefault(), "%.1f %s", v, u[i])
    }

    fun date(ms: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ms))

    fun time(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
        return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
    }
}

enum class Kind { DIR, VIDEO, IMAGE, AUDIO, APK, OTHER }

private val VIDEO = setOf("mp4", "mkv", "webm", "3gp", "avi", "mov", "m4v", "ts", "flv", "wmv", "mpg", "mpeg")
private val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")
private val AUDIO = setOf("mp3", "m4a", "aac", "ogg", "wav", "flac", "opus")

fun uiKind(kind: DataKind): Kind = when (kind) {
    DataKind.DIR -> Kind.DIR
    DataKind.VIDEO -> Kind.VIDEO
    DataKind.IMAGE -> Kind.IMAGE
    DataKind.AUDIO -> Kind.AUDIO
    DataKind.APK -> Kind.APK
    DataKind.ARCHIVE, DataKind.OTHER -> Kind.OTHER
}

fun kindOf(f: FileItem): Kind {
    if (f.isDir) return Kind.DIR
    return when (f.name.substringAfterLast('.', "").lowercase()) {
        in VIDEO -> Kind.VIDEO
        in IMAGE -> Kind.IMAGE
        in AUDIO -> Kind.AUDIO
        "apk", "xapk", "apks" -> Kind.APK
        else -> Kind.OTHER
    }
}

fun kindIcon(k: Kind): ImageVector = when (k) {
    Kind.DIR -> Icons.Default.Folder
    Kind.VIDEO -> Icons.Default.Movie
    Kind.IMAGE -> Icons.Default.Image
    Kind.AUDIO -> Icons.Default.AudioFile
    Kind.APK -> Icons.Default.Android
    Kind.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

fun kindColor(k: Kind): Color = when (k) {
    Kind.DIR -> Color(0xFF8AB4F8)
    Kind.VIDEO -> Color(0xFFF28B82)
    Kind.IMAGE -> Color(0xFF81C995)
    Kind.AUDIO -> Color(0xFFFDD663)
    Kind.APK -> Color(0xFF78D9EC)
    Kind.OTHER -> Color(0xFFBDC1C6)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(top = 8.dp))
}

@Composable
fun TextDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = t, onValueChange = { t = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (t.isNotBlank()) onOk(t.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun RadioDialog(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, o ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(i) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = i == selected, onClick = { onPick(i) })
                        Text(o)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}
