package com.twinster.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.twinster.app.ui.components.TwinsterLoadingScreen
import com.twinster.app.ui.screens.ConnectScreen
import com.twinster.app.ui.screens.HomeScreen
import com.twinster.app.ui.screens.IntroScreen
import com.twinster.app.ui.screens.OnboardingScreen
import com.twinster.app.ui.screens.SettingsScreen
import com.twinster.app.ui.screens.TasteTwinScreen
import com.twinster.app.util.UiState

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val CONNECT = "connect"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val TASTE_TWIN = "taste_twin"
    const val LOCAL_LIBRARY = "local_library"
}

@Composable
fun TwinsterNavHost(viewModel: TwinsterViewModel, modifier: Modifier = Modifier) {
    val navController: NavHostController = rememberNavController()
    val profileState by viewModel.profileState.collectAsState()
    val sharpenedArchetype by viewModel.sharpenedArchetype.collectAsState()
    val twinResultState by viewModel.twinResultState.collectAsState()
    val localLibraryState by viewModel.localLibraryState.collectAsState()
    val localLibraryScanProgress by viewModel.localLibraryScanProgress.collectAsState()
    val hasCachedLocalLibrary by viewModel.hasCachedLocalLibrary.collectAsState(initial = false)

    NavHost(navController = navController, startDestination = Routes.SPLASH, modifier = modifier) {
        composable(Routes.SPLASH) {
            IntroScreen(
                onFinished = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onContinue = { navController.navigate(Routes.CONNECT) })
        }
        composable(Routes.CONNECT) {
            ConnectScreen(
                onTryDemo = {
                    viewModel.loadDemoProfile()
                    navController.navigate(Routes.HOME)
                },
                onScanLocalLibrary = {
                    viewModel.scanLocalLibrary()
                    navController.navigate(Routes.LOCAL_LIBRARY)
                },
                hasCachedLocalLibrary = hasCachedLocalLibrary,
                onOpenMyLibrary = {
                    viewModel.loadCachedLocalLibrary()
                    navController.navigate(Routes.LOCAL_LIBRARY)
                }
            )
        }
        composable(Routes.HOME) {
            when (val state = profileState) {
                is UiState.Success -> HomeScreen(
                    profile = state.data,
                    sharpenedArchetype = sharpenedArchetype,
                    onOpenTasteTwin = { navController.navigate(Routes.TASTE_TWIN) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
                is UiState.Loading -> TwinsterLoadingScreen(label = "Loading your music personality…")
                is UiState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.message, color = Color.White)
                }
                else -> {}
            }
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                initialByokKey = viewModel.currentByokKey(),
                onSaveByokKey = { viewModel.saveByokKey(it) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.TASTE_TWIN) {
            TasteTwinScreen(
                myShareLink = viewModel.buildTasteTwinLink(),
                myProfile = (profileState as? UiState.Success)?.data ?: (localLibraryState as? UiState.Success)?.data,
                twinResultState = twinResultState,
                onCompareLink = { viewModel.compareWithPayload(it) },
                onCompareDemoFriend = { viewModel.compareWithDemoFriend() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.LOCAL_LIBRARY) {
            when (val state = localLibraryState) {
                // Reuses the exact same swipeable card pager Demo uses, instead of a separate
                // stripped-down summary screen.
                is UiState.Success -> HomeScreen(
                    profile = state.data,
                    sharpenedArchetype = null,
                    onOpenTasteTwin = { navController.navigate(Routes.TASTE_TWIN) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
                // Real per-track progress (see TwinsterViewModel.scanLocalLibrary) — genre detection now
                // involves decoding+classifying audio per track, which is slow enough on a large
                // library that a static label would look frozen without this.
                is UiState.Loading -> TwinsterLoadingScreen(label = localLibraryScanProgress ?: "Scanning your local library…")
                is UiState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.message, color = Color.White)
                }
                else -> {}
            }
        }
    }
}
