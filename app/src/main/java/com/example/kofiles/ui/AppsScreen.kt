@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.darkfiles.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.example.darkfiles.MainViewModel
import com.example.darkfiles.data.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppPath(val kind: String, val title: String, val path: String, val rootOnly: Boolean)

private fun appPaths(root: String, a: AppEntry) = listOf(
    AppPath("data", "Данные приложения", "$root/Android/data/${a.pkg}", false),
    AppPath("obb", "Кэш/OBB (Android/obb)", "$root/Android/obb/${a.pkg}", false),
    AppPath("media", "Медиа (Android/media)", "$root/Android/media/${a.pkg}", false),
    AppPath("внутр.", "Внутренние данные (нужен root)", a.dataDir, true),
    AppPath("базы", "Базы данных (нужен root)", "${a.dataDir}/databases", true)
)

@Composable
fun AppsTab(vm: MainViewModel, goFiles: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadApps() }
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 пользовательские, 1 системные, 2 все
    var sel by remember { mutableStateOf<AppEntry?>(null) }

    val list = remember(vm.apps, q, filter) {
        vm.apps.filter {
            (filter == 2 || (filter == 1) == it.system) &&
                (q.isBlank() || it.label.contains(q, true) || it.pkg.contains(q, true))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text("Приложения", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp))
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            singleLine = true,
            placeholder = { Text("Название или пакет") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Пользовательские", "Системные", "Все").forEachIndexed { i, s ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(s) })
            }
        }
        if (vm.apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.pkg }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { sel = a }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(a.pkg, 44.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                a.pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    val app = sel
    if (app != null) {
        ModalBottomSheet(onDismissRequest = { sel = null }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.pkg, 48.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                        Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.padding(4.dp))
                SectionLabel("Адреса данных")
                for (ap in appPaths(vm.root, app)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ap.title)
                            Text(
                                ap.path,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { vm.open(ap.path); sel = null; goFiles() }) {
                            Icon(Icons.Default.FolderOpen, "Открыть")
                        }
                        IconButton(onClick = {
                            val added = vm.prefs.addShortcut("${app.label} · ${ap.kind}", ap.path)
                            vm.message = if (added) "Адрес добавлен в быстрый доступ" else "Этот адрес уже добавлен"
                        }) { Icon(Icons.Default.BookmarkAdd, "В быстрый доступ") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(pkg: String, size: Dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            try {
                ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    val b = bmp
    if (b != null) Image(b, null, Modifier.size(size)) else Box(Modifier.size(size))
}
