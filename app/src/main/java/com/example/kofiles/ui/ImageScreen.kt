@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.kofiles.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.kofiles.data.ImageLoader
import com.example.kofiles.data.SourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface ImgState {
    object Loading : ImgState
    class Done(val bmp: ImageBitmap) : ImgState
    class Fail(val msg: String) : ImgState
}

@Composable
fun ImageScreen(provider: SourceProvider, paths: List<String>, startIndex: Int, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val pager = rememberPagerState(initialPage = startIndex) { paths.size }
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { zoomed = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(provider, paths[page]) { z -> if (pager.currentPage == page) zoomed = z }
        }
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().background(Color(0x66000000)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Закрыть", tint = Color.White) }
            Text(
                File(paths[pager.currentPage]).name,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text("${pager.currentPage + 1}/${paths.size}", color = Color.White, modifier = Modifier.padding(end = 12.dp))
        }
    }
}

@Composable
private fun ZoomableImage(provider: SourceProvider, path: String, onZoom: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val maxPx = remember { ctx.resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) * 2 } }
    val state by produceState<ImgState>(ImgState.Loading, path) {
        value = withContext(Dispatchers.IO) {
            try {
                val src = provider(path)
                try {
                    ImgState.Done(ImageLoader.load(src, maxPx).asImageBitmap())
                } finally {
                    src.close()
                }
            } catch (e: Throwable) {
                ImgState.Fail(e.message ?: "Не удалось открыть изображение")
            }
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(scale) { onZoom(scale > 1.01f) }

    val tState = rememberTransformableState { zoomChange, panChange, _ ->
        val ns = (scale * zoomChange).coerceIn(1f, 6f)
        scale = ns
        offset = if (ns > 1f) {
            val maxX = box.width * (ns - 1f) / 2f
            val maxY = box.height * (ns - 1f) / 2f
            Offset((offset.x + panChange.x).coerceIn(-maxX, maxX), (offset.y + panChange.y).coerceIn(-maxY, maxY))
        } else {
            Offset.Zero
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { box = it }, contentAlignment = Alignment.Center) {
        when (val st = state) {
            is ImgState.Loading -> CircularProgressIndicator()
            is ImgState.Fail -> Text(st.msg, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
            is ImgState.Done -> Image(
                bitmap = st.bmp,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offset = Offset.Zero
                        })
                    }
                    .transformable(state = tState, canPan = { scale > 1f })
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            )
        }
    }
}
