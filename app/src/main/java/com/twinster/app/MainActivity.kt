package com.twinster.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.twinster.app.domain.MusicProfile
import com.twinster.app.ui.TwinsterNavHost
import com.twinster.app.ui.TwinsterViewModel
import com.twinster.app.ui.theme.AnimatedMoodBackground
import com.twinster.app.ui.theme.TwinsterTheme
import com.twinster.app.ui.theme.hueForGenre
import com.twinster.app.util.UiState

class MainActivity : ComponentActivity() {

    private val viewModel: TwinsterViewModel by viewModels { TwinsterViewModel.Factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleIntentUri(intent?.data)

        setContent {
            TwinsterTheme {
                val profileState by viewModel.profileState.collectAsState()
                Box(modifier = Modifier.fillMaxSize()) {
                    val successProfile = (profileState as? UiState.Success<MusicProfile>)?.data
                    val hue = successProfile?.let { hueForGenre(it.topGenre) } ?: 260f
                    val saturation = successProfile?.let { (it.energyScore + it.valenceScore) / 2f } ?: 0.4f
                    AnimatedMoodBackground(baseHue = hue, saturation = saturation.coerceIn(0.25f, 0.85f))
                    TwinsterNavHost(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentUri(intent.data)
    }

    private fun handleIntentUri(uri: Uri?) {
        if (uri == null) return
        when (uri.host) {
            "compare" -> viewModel.onTasteTwinLink(uri)
        }
    }
}
