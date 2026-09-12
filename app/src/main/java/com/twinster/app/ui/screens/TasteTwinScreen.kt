package com.twinster.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.twinster.app.domain.MusicProfile
import com.twinster.app.twin.CompatibilityResult
import com.twinster.app.twin.QrCodeGenerator
import com.twinster.app.ui.components.CircularArtworkThumb
import com.twinster.app.ui.components.CountUpNumber
import com.twinster.app.ui.components.entranceModifier
import com.twinster.app.ui.theme.TwinsterAmber
import com.twinster.app.ui.theme.TwinsterCyan
import com.twinster.app.ui.theme.TwinsterError
import com.twinster.app.ui.theme.TwinsterGreen
import com.twinster.app.util.ShareCard
import com.twinster.app.util.UiState

@Composable
fun TasteTwinScreen(
    myShareLink: String?,
    myProfile: MusicProfile?,
    twinResultState: UiState<Pair<CompatibilityResult, String>>,
    onCompareLink: (String) -> Unit,
    onCompareDemoFriend: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pastedLink by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Taste Twin", style = MaterialTheme.typography.headlineLarge, color = Color.White, modifier = entranceModifier(index = 0))
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Button(onClick = onBack) { Text("Back") }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))

        Text(
            "Share your link or QR code with a friend who has their own Twinster profile loaded. " +
                "You can't compare against a celebrity or someone who hasn't opened their own link — that data doesn't exist.",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = entranceModifier(index = 1)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))

        if (myShareLink != null) {
            val qrBitmap = remember(myShareLink) { QrCodeGenerator.generate(myShareLink) }
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "QR code",
                modifier = Modifier.size(220.dp).then(entranceModifier(index = 2))
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
            Button(onClick = { ShareCard.copyToClipboard(context, myShareLink) }) {
                Text("Copy My Link")
            }
        } else {
            Text("Load your Music Personality first to generate your Taste Twin link.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(20.dp))
        Text("Have a friend's link? Paste it below:", style = MaterialTheme.typography.bodyMedium, color = Color.White)
        OutlinedTextField(
            value = pastedLink,
            onValueChange = { pastedLink = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("twinster://compare?d=...") }
        )
        Button(
            onClick = {
                val query = pastedLink.substringAfter("d=", "")
                if (query.isNotBlank()) onCompareLink(query)
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Compare")
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
        Button(onClick = onCompareDemoFriend, modifier = Modifier.fillMaxWidth()) {
            Text("Compare with Demo Friend")
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp))
        when (twinResultState) {
            is UiState.Loading -> com.twinster.app.ui.components.TwinsterLoadingRing()
            is UiState.Error -> Text(twinResultState.message, style = MaterialTheme.typography.bodyMedium, color = TwinsterError)
            is UiState.Success -> {
                val (result, verdict) = twinResultState.data
                // The compatibility % is the payoff moment of the whole screen — pair the existing
                // count-up with a scale-in and a brief glow pulse behind it instead of a number
                // that just counts up with nothing else happening. Color tiers by match strength.
                val matchColor = when {
                    result.overallPercent >= 70 -> TwinsterGreen
                    result.overallPercent >= 40 -> TwinsterCyan
                    else -> TwinsterAmber
                }
                var revealed by remember(result) { mutableStateOf(false) }
                LaunchedEffect(result) { revealed = true }
                val revealScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (revealed) 1f else 0.8f,
                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                    label = "matchRevealScale"
                )
                val glowTransition = rememberInfiniteTransition(label = "matchGlow")
                val glowAlpha by glowTransition.animateFloat(
                    initialValue = 0.15f,
                    targetValue = 0.4f,
                    animationSpec = infiniteRepeatable(animation = tween(1400, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                    label = "matchGlowAlpha"
                )
                Box(contentAlignment = Alignment.Center, modifier = Modifier.graphicsLayer { scaleX = revealScale; scaleY = revealScale }) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(matchColor.copy(alpha = glowAlpha), matchColor.copy(alpha = 0f))
                                ),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CountUpNumber(
                            targetValue = result.overallPercent,
                            suffix = "%",
                            style = MaterialTheme.typography.displaySmall.copy(color = matchColor)
                        )
                    }
                }
                Text("Match", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
                androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                Text(verdict, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                if (result.sharedArtists.isNotEmpty()) {
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                    Text("Shared artists", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                    LazyRow {
                        itemsIndexed(result.sharedArtists) { index, artistName: String ->
                            val imageUrl = myProfile?.topArtists?.firstOrNull {
                                it.name.equals(artistName, ignoreCase = true)
                            }?.imageUrl
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(end = 12.dp).then(entranceModifier(index = index, resetKey = result))
                            ) {
                                CircularArtworkThumb(imageUrl = imageUrl, size = 56.dp)
                                androidx.compose.foundation.layout.Spacer(Modifier.padding(2.dp))
                                Text(artistName, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
                if (result.sharedGenres.isNotEmpty()) {
                    Text("Shared genres: ${result.sharedGenres.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                }
                if (result.biggestDifferences.isNotEmpty()) {
                    Text("Biggest differences: ${result.biggestDifferences.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                }
            }
            else -> {}
        }
    }
}

