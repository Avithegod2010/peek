package com.avi.peek.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale

/**
 * A photo with pinch-to-zoom, pan while zoomed, and double-tap to zoom or reset.
 * The surrounding pager keeps horizontal drags when the image is at rest.
 */
@Composable
fun ZoomablePeekImage(
    url: String?,
    cacheKey: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var rotation by remember(url) { mutableFloatStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, rotationChange ->
        val next = (scale * zoomChange).coerceIn(1f, 5f)
        scale = next
        if (next > 1f) {
            offset += panChange
        } else {
            offset = Offset.Zero
        }
        rotation = (rotation + rotationChange).coerceIn(-30f, 30f)
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(url) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                            rotation = 0f
                        } else {
                            scale = 2.2f
                        }
                    },
                )
            }
            .transformable(transformState),
        contentAlignment = Alignment.Center,
    ) {
        PeekImage(
            url = url,
            cacheKey = cacheKey,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    rotationZ = rotation
                },
            contentScale = contentScale,
        )
    }
}
