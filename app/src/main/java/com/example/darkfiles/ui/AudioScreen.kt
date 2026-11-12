package com.example.kofiles.ui

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.kofiles.data.SourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private class AudioMeta(val title: String? = null, val artist: String? = null, val album: String? = null, val art: ImageBitmap? = null)

private fun loadMeta(provider: SourceProvider, path: String): AudioMeta {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(SourceMediaDataSource(provider(path)))
        val pic = r.embeddedPicture
        val art = if (pic != null) BitmapFactory.decodeByteArray(pic, 0, pic.size)?.asImageBitmap() else null
        AudioMeta(
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
            art
        )
    } catch (e: Exception) {
        AudioMeta()
    } finally {
        try { r.release() } catch (e: Exception) { }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun AudioScreen(provider: SourceProvider, paths: List<String>, startIndex: Int, autoplay: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    BackHandler(onBack = onClose)

    val exo = remember(paths) {
        ExoPlayer.Builder(ctx).build().also { p ->
            val factory = DataSource.Factory { ByteSourceDataSource(provider) }
            val pf = ProgressiveMediaSource.Factory(factory)
            val sources = paths.map { pf.createMediaSource(MediaItem.fromUri(Uri.fromFile(File(it)))) }
            p.setMediaSources(sources, startIndex, 0L)
            p.prepare()
            p.playWhenReady = autoplay
        }
    }

    var index by remember { mutableIntStateOf(startIndex) }
    var playing by remember { mutableStateOf(autoplay) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = exo.currentMediaItemIndex
                error = null
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayerError(e: PlaybackException) {
                error = e.message ?: "Не удалось воспроизвести трек"
            }
        }
        exo.addListener(l)
        onDispose {
            exo.removeListener(l)
            exo.release()
        }
    }
    LaunchedEffect(exo) {
        while (true) {
            pos = exo.currentPosition
            dur = if (exo.duration > 0) exo.duration else 0L
            delay(400)
        }
    }

    val current = paths[index.coerceIn(0, paths.size - 1)]
    val meta by produceState(AudioMeta(), current) {
        value = withContext(Dispatchers.IO) { loadMeta(provider, current) }
    }

    Column(Modifier.fillMaxSize().background(cs.background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Закрыть") }
            Text("Музыкальный плеер", style = MaterialTheme.typography.titleMedium)
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(220.dp).clip(RoundedCornerShape(24.dp)).background(cs.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val art = meta.art
                if (art != null) {
                    Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = cs.primary, modifier = Modifier.size(80.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                meta.title ?: File(current).name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            val sub = listOfNotNull(meta.artist, meta.album).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val e = error
            if (e != null) Text(e, color = cs.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)

            Slider(
                value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                onValueChange = { if (dur > 0) exo.seekTo((it * dur).toLong()) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Fmt.time(pos), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text(Fmt.time(dur), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { exo.seekToPreviousMediaItem() }, enabled = paths.size > 1) {
                    Icon(Icons.Default.SkipPrevious, "Предыдущий", modifier = Modifier.size(36.dp))
                }
                IconButton(
                    onClick = { if (playing) exo.pause() else exo.play() },
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(cs.primaryContainer)
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playing) "Пауза" else "Играть",
                        tint = cs.onPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { exo.seekToNextMediaItem() }, enabled = paths.size > 1) {
                    Icon(Icons.Default.SkipNext, "Следующий", modifier = Modifier.size(36.dp))
                }
            }
        }

        if (paths.size > 1) {
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(paths) { i, p ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (i == index) cs.primaryContainer.copy(alpha = 0.4f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { exo.seekTo(i, 0L); exo.play() }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (i == index && playing) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                            null,
                            tint = if (i == index) cs.primary else cs.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(File(p).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
