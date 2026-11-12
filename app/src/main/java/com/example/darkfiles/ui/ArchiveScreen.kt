@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.kofiles.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kofiles.MainViewModel
import com.example.kofiles.data.FileKinds
import com.example.kofiles.data.Kind

@Composable
fun ArchiveScreen(vm: MainViewModel, path: String, local: Boolean, onClose: () -> Unit) {
    val st = remember(path, local) { vm.archiveState(path, local) }
    LaunchedEffect(st) { vm.ensureLoaded(st) }
    val cs = MaterialTheme.colorScheme
    val children = remember(st.entries, st.dir) { st.children() }
    var menu by remember { mutableStateOf(false) }

    fun up() {
        if (st.selected.isNotEmpty()) st.selected.clear()
        else if (st.dir.isNotEmpty()) st.dir = st.dir.substringBeforeLast('/', "")
        else onClose()
    }
    BackHandler { up() }

    Column(Modifier.fillMaxSize().background(cs.background)) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            navigationIcon = {
                IconButton(onClick = { up() }) {
                    Icon(
                        if (st.selected.isNotEmpty() || st.dir.isEmpty()) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                        "Назад"
                    )
                }
            },
            title = {
                Column {
                    Text(st.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (st.selected.isNotEmpty()) "Выбрано: ${st.selected.size}" else "/" + st.dir,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            },
            actions = {
                if (!st.loading && st.error == null && !st.local) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Ещё") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Распаковать всё рядом с архивом") },
                                onClick = { menu = false; vm.extractArchive(st, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Распаковать выбранное") },
                                enabled = st.selected.isNotEmpty(),
                                onClick = {
                                    menu = false
                                    vm.extractArchive(st, st.selected.toList())
                                    st.selected.clear()
                                }
                            )
                        }
                    }
                }
            }
        )

        val busy = st.busy
        if (busy != null) {
            if (st.busyProgress > 0f) LinearProgressIndicator(progress = { st.busyProgress }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(busy, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val err = st.error
            when {
                st.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    val p = vm.copyProgress
                    if (p != null) {
                        CircularProgressIndicator(progress = { p })
                        Spacer(Modifier.height(12.dp))
                        Text("Копирование архива: ${(p * 100).toInt()}%")
                    } else {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Чтение архива…")
                    }
                }
                err != null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Warning, null, tint = cs.error, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(err, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Если архив зашифрован или формат не поддерживается, откройте его в другом приложении.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                children.isEmpty() -> Text("Папка пуста", modifier = Modifier.align(Alignment.Center), color = cs.onSurfaceVariant)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(children, key = { it.fullPath }) { c ->
                        val selected = c.fullPath in st.selected
                        val kind = if (c.isDir) Kind.DIR else FileKinds.kind(c.name)
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (selected) cs.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                                .combinedClickable(
                                    onClick = {
                                        when {
                                            st.selected.isNotEmpty() -> if (!st.selected.remove(c.fullPath)) st.selected.add(c.fullPath)
                                            c.isDir -> st.dir = c.fullPath
                                            else -> vm.openArchiveEntry(st, c)
                                        }
                                    },
                                    onLongClick = { if (!st.selected.remove(c.fullPath)) st.selected.add(c.fullPath) }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(kindColor(kind).copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(if (selected) Icons.Default.Check else kindIcon(kind), null, tint = kindColor(kind))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!c.isDir && c.size >= 0) {
                                    Text(Fmt.size(c.size), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
