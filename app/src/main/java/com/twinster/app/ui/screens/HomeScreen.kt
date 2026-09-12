package com.twinster.app.ui.screens

import android.graphics.Rect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.twinster.app.domain.MusicProfile
import com.twinster.app.ui.components.CircularArtworkThumb
import com.twinster.app.ui.components.CountUpNumber
import com.twinster.app.ui.components.CountUpNumberWithRing
import com.twinster.app.ui.components.GlassCard
import com.twinster.app.ui.components.entranceModifier
import com.twinster.app.ui.components.rememberPressScale
import com.twinster.app.ui.theme.TwinsterAmber
import com.twinster.app.ui.theme.TwinsterCyan
import com.twinster.app.ui.theme.TwinsterGreen
import com.twinster.app.ui.theme.TwinsterPink
import com.twinster.app.util.ShareCard
import com.twinster.app.util.captureWindowRegion
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    profile: MusicProfile,
    sharpenedArchetype: String?,
    onOpenTasteTwin: (() -> Unit)? = null,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var cardBounds by remember { mutableStateOf<Rect?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).then(entranceModifier(index = 0)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your Music Personality", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Row {
                if (onOpenTasteTwin != null) {
                    IconButton(onClick = onOpenTasteTwin) { Text("Twin", color = MaterialTheme.colorScheme.primary) }
                }
                IconButton(onClick = onOpenSettings) { Text("⚙", color = Color.White) }
            }
        }

        val pagerState = rememberPagerState(pageCount = { 6 })
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp)
        ) { page ->
            // Subtle scale/alpha depth so swiping between cards feels like flipping through a
            // stack rather than a flat slide — consistent with the app's glassmorphic depth cues.
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            val clampedOffset = abs(pageOffset).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = lerp(0.92f, 1f, 1f - clampedOffset)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(0.55f, 1f, 1f - clampedOffset)
                    }
                    .onGloballyPositioned { coords ->
                        if (page == pagerState.currentPage) {
                            val location = IntArray(2)
                            view.getLocationInWindow(location)
                            val topLeft = coords.localToWindow(androidx.compose.ui.geometry.Offset.Zero)
                            val size = coords.size
                            cardBounds = Rect(
                                topLeft.x.toInt(),
                                topLeft.y.toInt(),
                                (topLeft.x + size.width).toInt(),
                                (topLeft.y + size.height).toInt()
                            )
                        }
                    }
            ) {
                when (page) {
                    0 -> TopPicksCard(profile)
                    1 -> GenreCard(profile)
                    2 -> DecadeCard(profile)
                    3 -> ObscurityCard(profile)
                    4 -> MoodCard(profile)
                    else -> ArchetypeCard(profile, sharpenedArchetype)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val copyInteraction = remember { MutableInteractionSource() }
            val copyScale by rememberPressScale(copyInteraction)
            Button(
                interactionSource = copyInteraction,
                modifier = Modifier.graphicsLayer { scaleX = copyScale; scaleY = copyScale },
                onClick = {
                    val text = "🎧 ${profile.overallObscurityScore}% obscure / ${profile.archetypeTitle}"
                    ShareCard.copyToClipboard(context, text)
                }
            ) { Text("Copy for Notes") }

            val shareInteraction = remember { MutableInteractionSource() }
            val shareScale by rememberPressScale(shareInteraction)
            Button(
                interactionSource = shareInteraction,
                modifier = Modifier.graphicsLayer { scaleX = shareScale; scaleY = shareScale },
                onClick = {
                    val bounds = cardBounds ?: return@Button
                    if (activity == null) return@Button
                    scope.launch {
                        val bitmap = captureWindowRegion(activity, bounds) ?: return@launch
                        val uri = ShareCard.saveBitmapToCache(context, bitmap)
                        if (ShareCard.isInstagramInstalled(context)) {
                            if (!ShareCard.shareToInstagramStory(context, uri)) {
                                ShareCard.shareGeneric(context, uri, "My Twinster music personality")
                            }
                        } else {
                            ShareCard.shareGeneric(context, uri, "My Twinster music personality")
                        }
                    }
                }
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Share Card")
            }
        }
    }
}

@Composable
private fun TopPicksCard(profile: MusicProfile) {
    var selected by remember { mutableStateOf(0) }
    val tabs = listOf("Songs", "Artists", "Genres", "Playlists")
    // Each tab pulls a distinct accent from the wider palette instead of everything on screen
    // defaulting to the same green/pink split.
    val tabColors = listOf(TwinsterGreen, TwinsterPink, TwinsterCyan, TwinsterAmber)

    GlassCard(modifier = Modifier.fillMaxSize()) {
        Text("Top Picks", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabs.forEachIndexed { index, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (index == selected) tabColors[index] else Color.White.copy(alpha = 0.45f),
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .clickable { selected = index }
                )
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))

        AnimatedContent(
            targetState = selected,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "topPicksTab"
        ) { tab ->
            Column {
                when (tab) {
                    0 -> if (profile.topTracks.isEmpty()) {
                        Text("No songs found yet.", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f))
                    } else {
                        profile.topTracks.take(6).forEachIndexed { index, track ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                    .then(entranceModifier(index = index, resetKey = tab)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularArtworkThumb(imageUrl = track.imageUrl, size = 48.dp)
                                androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 6.dp))
                                Column {
                                    Text("${index + 1}. ${track.name}", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f))
                                    if (track.artistNames.isNotEmpty()) {
                                        Text(track.artistNames.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
                                    }
                                }
                            }
                        }
                    }
                    1 -> if (profile.topArtists.isEmpty()) {
                        Text("No artists found yet.", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f))
                    } else {
                        profile.topArtists.take(6).forEachIndexed { index, artist ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                    .then(entranceModifier(index = index, resetKey = tab)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularArtworkThumb(imageUrl = artist.imageUrl, size = 48.dp)
                                androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 6.dp))
                                Text("${index + 1}. ${artist.name}", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f))
                            }
                        }
                    }
                    2 -> if (!profile.genresAvailable || profile.genreBreakdown.isEmpty()) {
                        Text("Genre data isn't available for these tracks.", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f))
                    } else {
                        profile.genreBreakdown.take(6).forEachIndexed { index, share ->
                            Text(
                                "${share.genre}: ${share.percent}%",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.padding(vertical = 4.dp).then(entranceModifier(index = index, resetKey = tab))
                            )
                        }
                    }
                    else -> if (!profile.obscurity.playlists.available && profile.obscurity.playlists.items.isEmpty()) {
                        Text(
                            profile.obscurity.playlists.basisNote ?: "Playlist data isn't available from this device.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    } else {
                        profile.obscurity.playlists.items.take(6).forEachIndexed { index, item ->
                            Text(
                                item.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.padding(vertical = 4.dp).then(entranceModifier(index = index, resetKey = tab))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreCard(profile: MusicProfile) {
    GlassCard(modifier = Modifier.fillMaxSize()) {
        Text("Genre Breakdown", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        if (profile.genresAvailable) {
            profile.genreBreakdown.forEachIndexed { index, share ->
                Text(
                    "${share.genre}: ${share.percent}%",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(vertical = 4.dp).then(entranceModifier(index = index))
                )
            }
            profile.genreBasisNote?.let { note ->
                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                Text("($note)", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
            }
        } else {
            Text(
                "Genre data isn't available for these tracks.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun DecadeCard(profile: MusicProfile) {
    GlassCard(modifier = Modifier.fillMaxSize()) {
        Text("Decade Split", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        profile.decadeSplit.forEachIndexed { index, share ->
            Text(
                "${share.decade}: ${share.percent}%",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(vertical = 4.dp).then(entranceModifier(index = index))
            )
        }
        profile.decadeBasisNote?.let { note ->
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
            Text("($note)", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun ObscurityCard(profile: MusicProfile) {
    GlassCard(modifier = Modifier.fillMaxSize()) {
        Text("Obscurity", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))

        if (profile.overallObscurityAvailable) {
            // Underground-leaning scores read cooler (cyan), mainstream-leaning scores read
            // warmer (amber) — the ring color itself carries a little of the meaning.
            val ringColors = if (profile.overallObscurityScore > 60) {
                listOf(TwinsterCyan, TwinsterGreen)
            } else {
                listOf(TwinsterAmber, TwinsterGreen)
            }
            CountUpNumberWithRing(
                targetValue = profile.overallObscurityScore,
                fraction = profile.overallObscurityScore / 100f,
                suffix = "",
                ringColors = ringColors
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
            Text(
                if (profile.overallObscurityScore > 60) "More underground than most listeners." else "Mostly familiar, crowd-approved taste.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
            ObscurityColorLegend(highlighted = profile.overallObscurityScore > 60)
        } else {
            Text(
                "Not available — no Genius mainstream-footprint match yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        Text(
            "Obscurity is a fun, subjective estimate — not a real ranking, so don't take it too personally.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.45f)
        )
    }
}

/** Small legend explaining what the ObscurityCard's ring color means — mirrors the exact two-way
 *  threshold the ring itself uses (score > 60 => cyan/"Underground", otherwise amber/"Mainstream";
 *  see ObscurityCard above) rather than inventing extra tiers the actual color logic doesn't have.
 *  [highlighted] marks which of the two labels matches this profile's own score, via a slightly
 *  brighter dot/label, without hiding the other tier's meaning. */
@Composable
private fun ObscurityColorLegend(highlighted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendDot(color = TwinsterCyan, label = "Underground", emphasized = highlighted)
        androidx.compose.foundation.layout.Spacer(Modifier.width(16.dp))
        LegendDot(color = TwinsterAmber, label = "Mainstream", emphasized = !highlighted)
    }
}

@Composable
private fun LegendDot(color: Color, label: String, emphasized: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color.copy(alpha = if (emphasized) 1f else 0.5f), shape = CircleShape)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = if (emphasized) 0.75f else 0.45f)
        )
    }
}

@Composable
private fun MoodCard(profile: MusicProfile) {
    GlassCard(modifier = Modifier.fillMaxSize()) {
        Text("Mood Profile", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        Text(profile.moodSummary.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f))
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        // Energy reads warm (amber), Valence reads cool (cyan) — a small, purposeful use of the
        // wider palette rather than both stats defaulting to plain white.
        Text("Energy: ${(profile.energyScore * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge, color = TwinsterAmber.copy(alpha = 0.9f))
        Text("Valence: ${(profile.valenceScore * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge, color = TwinsterCyan.copy(alpha = 0.9f))
    }
}

@Composable
private fun ArchetypeCard(profile: MusicProfile, sharpened: String?) {
    GlassCard(modifier = Modifier.fillMaxSize(), variant = com.twinster.app.ui.components.GlassCardVariant.Primary) {
        Text("Your Archetype", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        Text(profile.archetypeTitle, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
        Text(sharpened ?: profile.archetypeDescription, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
    }
}
