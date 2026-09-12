package com.twinster.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Twinster", style = MaterialTheme.typography.displayMedium, color = Color.White)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
        Text(
            "Turn your downloaded music into a Music Personality card, then compare taste with a friend " +
                "by link or QR code. No account, no server.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
        Text(
            "Taste Twin only works with friends who've opened Twinster themselves — not celebrities or strangers.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(20.dp))
        Button(onClick = onContinue, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Text("Get Started")
        }
    }
}
