package com.twinster.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

/**
 * Artist/track artwork with a glassmorphic fallback tile — used anywhere a name-only row used to
 * be shown (Top Picks, Taste Twin shared artists). Falls back cleanly on null URLs (e.g. Local Library tracks with no embedded art) and
 * on failed loads instead of a broken-image icon or empty gap.
 */
@Composable
fun ArtworkThumb(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(size)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context).data(imageUrl).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { ArtworkPlaceholder(size = size, shape = shape) },
                error = { ArtworkPlaceholder(size = size, shape = shape) }
            )
        } else {
            ArtworkPlaceholder(size = size, shape = shape)
        }
    }
}

@Composable
private fun ArtworkPlaceholder(size: Dp, shape: Shape) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.05f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(size / 2)
        )
    }
}

/** Circular variant for artist avatars where a round crop reads better than a rounded square. */
@Composable
fun CircularArtworkThumb(imageUrl: String?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    ArtworkThumb(imageUrl = imageUrl, modifier = modifier, size = size, shape = CircleShape)
}
