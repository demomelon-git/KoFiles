package com.example.kofiles.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.kofiles.data.SourceProvider
import com.example.kofiles.data.ByteSource
import java.io.IOException

private const val CHUNK = 256 * 1024

/** Читает видео по кускам через Shizuku/ADB — так можно смотреть файлы из Android/data. */
@OptIn(UnstableApi::class)
private class RemoteDataSource(private val provider: SourceProvider) : BaseDataSource(true) {
    private var source: ByteSource? = null
    private var currentUri: Uri? = null
    private var path = ""
    private var position = 0L
    private var remaining = 0L
    private var buf = ByteArray(0)
    private var bufPos = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        path = dataSpec.uri.path ?: ""
        transferInitializing(dataSpec)
        source = provider(path)
        val size = source!!.size()
        position = dataSpec.position
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else size - position
        if (remaining < 0) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        buf = ByteArray(0)
        bufPos = 0
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (bufPos >= buf.size) {
            val want = minOf(CHUNK.toLong(), remaining).toInt()
            buf = source?.read(position, want) ?: throw IOException("Источник не открыт")
            bufPos = 0
            if (buf.isEmpty()) return C.RESULT_END_OF_INPUT
        }
        val n = minOf(length, buf.size - bufPos)
        System.arraycopy(buf, bufPos, target, offset, n)
        bufPos += n
        position += n
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        buf = ByteArray(0)
        bufPos = 0
        source?.close()
        source = null
        currentUri = null
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoScreen(provider: SourceProvider, path: String, autoplay: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onClose)
    var err by remember(path) { mutableStateOf<String?>(null) }

    val exo = remember(path) {
        ExoPlayer.Builder(ctx).build().also { p ->
            val factory = DataSource.Factory { RemoteDataSource(provider) }
            val src = ProgressiveMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri(Uri.fromFile(File(path))))
            p.setMediaSource(src)
            p.prepare()
            p.playWhenReady = autoplay
        }
    }
    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                err = error.message ?: "Не удалось воспроизвести видео"
            }
        }
        exo.addListener(l)
        onDispose {
            exo.removeListener(l)
            exo.release()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    player = exo
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Закрыть", tint = Color.White)
        }
        val e = err
        if (e != null) {
            Text(e, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }
    }
}
