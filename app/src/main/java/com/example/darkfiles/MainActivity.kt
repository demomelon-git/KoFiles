package com.example.darkfiles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import com.example.darkfiles.ui.AppsTab
import com.example.darkfiles.ui.DarkFilesTheme
import com.example.darkfiles.ui.FilesTab
import com.example.darkfiles.ui.LaunchTab
import com.example.darkfiles.ui.SettingsTab
import com.example.darkfiles.ui.VideoScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DarkFilesTheme(vm.prefs.theme) { Root(vm) }
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

    val video = vm.player
    val fs = vm.fs
    if (video != null && fs != null) {
        VideoScreen(fs, video, vm.prefs.autoplay) { vm.player = null }
        return
    }

    BackHandler(enabled = tab == 0 && (vm.path != null || vm.selected.isNotEmpty())) { vm.back() }
    BackHandler(enabled = tab != 0) { tab = 0 }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
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
