package com.example.kofiles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kofiles.data.Kind
import com.example.kofiles.data.Viewer
import com.example.kofiles.ui.AppsTab
import com.example.kofiles.ui.ArchiveScreen
import com.example.kofiles.ui.AudioScreen
import com.example.kofiles.ui.FilesTab
import com.example.kofiles.ui.ImageScreen
import com.example.kofiles.ui.KoFilesTheme
import com.example.kofiles.ui.LaunchTab
import com.example.kofiles.ui.OpenWithSheet
import com.example.kofiles.ui.SettingsTab
import com.example.kofiles.ui.VideoScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KoFilesTheme(vm.prefs.theme) { Root(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}

@Composable
private fun Root(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        val m = vm.message
        if (m != null) {
            snack.showSnackbar(m)
            vm.message = null
        }
    }
    val viewer = vm.viewer

    BackHandler(enabled = viewer == null && tab == 0 && (vm.path != null || vm.selected.isNotEmpty())) { vm.back() }
    BackHandler(enabled = viewer == null && tab != 0) { tab = 0 }

    Box(Modifier.fillMaxSize()) {
        if (viewer != null) {
            ViewerHost(vm, viewer)
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        val items = listOf(
                            Triple("Файлы", Icons.Default.Folder, 0),
                            Triple("Приложения", Icons.Default.Apps, 1),
                            Triple("Запуск", Icons.Default.Bolt, 2),
                            Triple("Настройки", Icons.Default.Settings, 3)
                        )
                        items.forEach { (label, icon, i) ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(icon, label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad)) {
                    when (tab) {
                        0 -> FilesTab(vm) { tab = 2 }
                        1 -> AppsTab(vm) { tab = 0 }
                        2 -> LaunchTab(vm)
                        else -> SettingsTab(vm)
                    }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = if (viewer == null) 80.dp else 16.dp))
        OpenWithSheet(vm)
    }
}

@Composable
private fun ViewerHost(vm: MainViewModel, v: Viewer) {
    val provider = remember(v) { vm.provider(v.local) }
    when (v.kind) {
        Kind.VIDEO -> VideoScreen(provider, v.paths[0], vm.prefs.autoplay) { vm.closeViewer() }
        Kind.IMAGE -> ImageScreen(provider, v.paths, v.index) { vm.closeViewer() }
        Kind.AUDIO -> AudioScreen(provider, v.paths, v.index, vm.prefs.autoplay) { vm.closeViewer() }
        Kind.ARCHIVE -> ArchiveScreen(vm, v.paths[0], v.local) { vm.closeViewer() }
        else -> LaunchedEffect(Unit) { vm.closeViewer() }
    }
}
