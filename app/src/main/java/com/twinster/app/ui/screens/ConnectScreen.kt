package com.twinster.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.twinster.app.ui.components.entranceModifier
import com.twinster.app.ui.components.rememberPressScale
import com.twinster.app.ui.theme.TwinsterError

/** READ_MEDIA_AUDIO replaced READ_EXTERNAL_STORAGE for audio-only access starting API 33. */
private fun localLibraryPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun ConnectScreen(
    onTryDemo: () -> Unit,
    onScanLocalLibrary: () -> Unit,
    hasCachedLocalLibrary: Boolean = false,
    onOpenMyLibrary: () -> Unit = {}
) {
    val context = LocalContext.current
    var showLocalLibraryRationale by remember { mutableStateOf(false) }
    var localLibraryPermissionDenied by remember { mutableStateOf(false) }

    val permission = remember { localLibraryPermission() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            localLibraryPermissionDenied = false
            showLocalLibraryRationale = false
            onScanLocalLibrary()
        } else {
            localLibraryPermissionDenied = true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Your Music, Your Device",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            modifier = entranceModifier(index = 0)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Text(
            "Built entirely from the audio files on your phone. Nothing is uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = entranceModifier(index = 1)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp))
        val scanInteraction = remember { MutableInteractionSource() }
        val scanScale by rememberPressScale(scanInteraction)
        Button(
            interactionSource = scanInteraction,
            onClick = {
                val alreadyGranted = ContextCompat.checkSelfPermission(context, permission) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                if (alreadyGranted) {
                    onScanLocalLibrary()
                } else {
                    showLocalLibraryRationale = true
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
                .graphicsLayer { scaleX = scanScale; scaleY = scanScale }
                .then(entranceModifier(index = 2))
        ) {
            Text("Scan Local Library")
        }
        if (showLocalLibraryRationale) {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            Text(
                "Needed to scan your downloaded songs. Everything stays on your phone.",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.65f)
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            Button(onClick = { permissionLauncher.launch(permission) }, modifier = Modifier.fillMaxWidth()) {
                Text("Allow Access")
            }
        }
        if (localLibraryPermissionDenied) {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            Text(
                "Permission denied — grant it anytime from Settings.",
                style = MaterialTheme.typography.labelMedium,
                color = TwinsterError
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open App Settings")
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp))
        Text(
            "No local downloads? No problem.",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.5f),
            modifier = entranceModifier(index = 3)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
        val demoInteraction = remember { MutableInteractionSource() }
        val demoScale by rememberPressScale(demoInteraction)
        OutlinedButton(
            interactionSource = demoInteraction,
            onClick = onTryDemo,
            modifier = Modifier.fillMaxWidth()
                .graphicsLayer { scaleX = demoScale; scaleY = demoScale }
                .then(entranceModifier(index = 4))
        ) {
            Text("Try Demo")
        }

        if (hasCachedLocalLibrary) {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp))
            val libraryInteraction = remember { MutableInteractionSource() }
            val libraryScale by rememberPressScale(libraryInteraction)
            Button(
                interactionSource = libraryInteraction,
                onClick = onOpenMyLibrary,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
                    .graphicsLayer { scaleX = libraryScale; scaleY = libraryScale }
                    .then(entranceModifier(index = 5))
            ) {
                Text("My Library")
            }
        }
    }
}
