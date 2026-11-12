@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.kofiles.ui

import android.os.StatFs
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kofiles.Conn
import com.example.kofiles.MainViewModel
import com.example.kofiles.data.FileItem
import java.io.File

private data class Tile(val title: String, val path: String, val icon: ImageVector)

private sealed interface Dlg {
    object NewFolder : Dlg
    data class Rename(val path: String) : Dlg
    data class Delete(val paths: List<String>) : Dlg
}

@Composable
fun FilesTab(vm: MainViewModel, goLaunch: () -> Unit) {
    if (vm.path == null) Home(vm, goLaunch) else Browser(vm, goLaunch)
}

// ---------------------------------------------------------------- Главная

@Composable
private fun Home(vm: MainViewModel, goLaunch: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tiles = remember(vm.root) {
        listOf(
            Tile("Загрузки", "${vm.root}/Download", Icons.Default.Download),
            Tile("Фото", "${vm.root}/DCIM", Icons.Default.Image),
            Tile("Видео", "${vm.root}/Movies", Icons.Default.Movie),
            Tile("Музыка", "${vm.root}/Music", Icons.Default.MusicNote),
            Tile("Android/data", "${vm.root}/Android/data", Icons.Default.Android),
            Tile("Android/obb", "${vm.root}/Android/obb", Icons.Default.Folder)
        )
    }
    val st = remember { try { StatFs(vm.root) } catch (e: Exception) { null } }
    val ok = vm.conn is Conn.On

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = cs.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                    .clickable { vm.open(vm.root); vm.searchMode = true }
            ) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("Поиск в файлах", color = cs.onSurfaceVariant)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (ok) cs.primaryContainer else cs.surfaceVariant,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = goLaunch)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Info, null, tint = cs.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Доступ к файлам", fontWeight = FontWeight.Bold)
                        Text(vm.statusText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Быстрый доступ") }
        items(tiles) { t -> TileCard(t) { vm.open(t.path) } }
        if (vm.prefs.shortcuts.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Адреса приложений") }
            items(vm.prefs.shortcuts.toList()) { s ->
                TileCard(Tile(s.title, s.path, Icons.Default.Android)) { vm.open(s.path) }
            }
        }
        if (st != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val total = st.totalBytes
                val free = st.availableBytes
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cs.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { vm.open(vm.root) }
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Память устройства", fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = { if (total > 0) (total - free).toFloat() / total else 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Свободно ${Fmt.size(free)} из ${Fmt.size(total)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TileCard(t: Tile, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(t.icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- Обзор папки

@Composable
private fun Browser(vm: MainViewModel, goLaunch: () -> Unit) {
    val path = vm.path ?: return
    val list = vm.visible()
    val cs = MaterialTheme.colorScheme
    val selecting = vm.selected.isNotEmpty()
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val title = if (path == vm.root) "Память устройства" else File(path).name.ifEmpty { "/" }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            navigationIcon = {
                IconButton(onClick = { vm.back() }) {
                    Icon(
                        if (selecting || vm.searchMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                        "Назад"
                    )
                }
            },
            title = {
                if (vm.searchMode && !selecting) SearchField(vm)
                else Text(
                    if (selecting) "${vm.selected.size} выбрано" else title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            actions = {
                if (selecting) {
                    IconButton(onClick = { vm.copySelected() }) { Icon(Icons.Default.ContentCopy, "Копировать") }
                    IconButton(onClick = { vm.cutSelected() }) { Icon(Icons.Default.ContentCut, "Вырезать") }
                    if (vm.selected.size == 1) {
                        IconButton(onClick = { dlg = Dlg.Rename(vm.selected[0]) }) {
                            Icon(Icons.Default.DriveFileRenameOutline, "Переименовать")
                        }
                    }
                    IconButton(onClick = {
                        val l = vm.selected.toList()
                        if (vm.prefs.confirmDelete) dlg = Dlg.Delete(l) else vm.delete(l)
                    }) { Icon(Icons.Default.Delete, "Удалить") }
                    IconButton(onClick = { vm.selectAll(list) }) { Icon(Icons.Default.SelectAll, "Выбрать все") }
                } else {
                    if (!vm.searchMode) {
                        IconButton(onClick = { vm.searchMode = true }) { Icon(Icons.Default.Search, "Поиск") }
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Сортировка") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("По имени", "По размеру", "По дате").forEachIndexed { i, s ->
                                DropdownMenuItem(
                                    text = { Text(s) },
                                    onClick = { vm.prefs.setSortBy(i); sortMenu = false },
                                    leadingIcon = { if (vm.prefs.sortBy == i) Icon(Icons.Default.Check, null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("По убыванию") },
                                onClick = { vm.prefs.setSortDesc(!vm.prefs.sortDesc); sortMenu = false },
                                leadingIcon = { if (vm.prefs.sortDesc) Icon(Icons.Default.Check, null) }
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Ещё") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Новая папка") }, onClick = { menu = false; dlg = Dlg.NewFolder })
                            DropdownMenuItem(text = { Text("Обновить") }, onClick = { menu = false; vm.reload() })
                            DropdownMenuItem(
                                text = { Text("Скрытые файлы") },
                                onClick = { vm.prefs.setShowHidden(!vm.prefs.showHidden); menu = false },
                                leadingIcon = { if (vm.prefs.showHidden) Icon(Icons.Default.Check, null) }
                            )
                        }
                    }
                }
            }
        )

        Crumbs(vm, path)
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val err = vm.error
            when {
                err != null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Warning, null, tint = cs.error, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(err, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    if (vm.fs == null) Button(onClick = goLaunch) { Text("Способ запуска") }
                    else OutlinedButton(onClick = { vm.reload() }) { Text("Повторить") }
                }
                list.isEmpty() && !vm.loading -> Text(
                    "Папка пуста",
                    modifier = Modifier.align(Alignment.Center),
                    color = cs.onSurfaceVariant
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.path }) { f ->
                        FileRow(
                            f = f,
                            selected = f.path in vm.selected,
                            onClick = {
                                when {
                                    selecting -> vm.toggle(f.path)
                                    f.isDir -> vm.open(f.path)
                                    kindOf(f) == Kind.VIDEO -> vm.onFileClick(f)
                                    else -> vm.message = "${f.name} · ${Fmt.size(f.size)}"
                                }
                            },
                            onLong = { vm.toggle(f.path) }
                        )
                    }
                }
            }
        }

        val c = vm.clip
        if (c != null) {
            Surface(color = cs.surfaceContainerHigh) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${c.paths.size} элем. · " + if (c.cut) "перемещение" else "копирование",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.clearClip() }) { Text("Отмена") }
                    Button(onClick = { vm.paste() }) { Text("Вставить") }
                }
            }
        }
    }

    when (val d = dlg) {
        null -> {}
        is Dlg.NewFolder -> TextDialog("Новая папка", "", "Создать", { dlg = null }) { vm.mkdir(it); dlg = null }
        is Dlg.Rename -> TextDialog("Переименовать", File(d.path).name, "OK", { dlg = null }) {
            vm.rename(d.path, it)
            dlg = null
        }
        is Dlg.Delete -> AlertDialog(
            onDismissRequest = { dlg = null },
            title = { Text("Удалить?") },
            text = { Text("Будет удалено элементов: ${d.paths.size}. Это действие нельзя отменить.") },
            confirmButton = { TextButton(onClick = { vm.delete(d.paths); dlg = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { dlg = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun SearchField(vm: MainViewModel) {
    val fr = remember { FocusRequester() }
    TextField(
        value = vm.query,
        onValueChange = { vm.query = it },
        placeholder = { Text("Поиск в папке") },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.focusRequester(fr)
    )
    LaunchedEffect(Unit) { fr.requestFocus() }
}

@Composable
private fun Crumbs(vm: MainViewModel, p: String) {
    val base = if (p == vm.root || p.startsWith(vm.root + "/")) vm.root else ""
    val segs = ArrayList<Pair<String, String>>()
    segs.add((if (base.isEmpty()) "/" else "Память") to base.ifEmpty { "/" })
    var acc = base
    for (part in p.removePrefix(base).split("/").filter { it.isNotEmpty() }) {
        acc = "$acc/$part"
        segs.add(part to acc)
    }
    val scroll = rememberScrollState()
    LaunchedEffect(p) { scroll.scrollTo(scroll.maxValue) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segs.forEachIndexed { i, (label, target) ->
            if (i > 0) {
                Icon(
                    Icons.Default.ChevronRight, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.open(target) }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun FileRow(f: FileItem, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val kind = kindOf(f)
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) cs.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
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
            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (f.isDir) Fmt.date(f.modified) else "${Fmt.size(f.size)} · ${Fmt.date(f.modified)}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}
