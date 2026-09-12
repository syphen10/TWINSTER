package com.twinster.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.twinster.app.ui.components.entranceModifier
import com.twinster.app.ui.theme.TwinsterCyan

@Composable
fun SettingsScreen(
    initialByokKey: String?,
    onSaveByokKey: (String?) -> Unit,
    onBack: () -> Unit
) {
    var byokInput by remember { mutableStateOf(initialByokKey ?: "") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().then(entranceModifier(index = 0)),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text("Close", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(4.dp))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Button(onClick = onBack, modifier = entranceModifier(index = 1)) { Text("Back") }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp))
        Text(
            "Sharper Archetype (BYOK)",
            style = MaterialTheme.typography.titleLarge,
            color = TwinsterCyan,
            modifier = entranceModifier(index = 2)
        )
        Text(
            "Optionally paste your own Anthropic API key to get a funnier, AI-written archetype line. " +
                "Stored encrypted on this device only. Falls back to the built-in rule-based version if empty or on error.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = entranceModifier(index = 3)
        )
        OutlinedTextField(
            value = byokInput,
            onValueChange = { byokInput = it },
            label = { Text("Anthropic API key") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).then(entranceModifier(index = 4))
        )
        Button(onClick = { onSaveByokKey(byokInput.ifBlank { null }) }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Save Key")
        }
    }
}
