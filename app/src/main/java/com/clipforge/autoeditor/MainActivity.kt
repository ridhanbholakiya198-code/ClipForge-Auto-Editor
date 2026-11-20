package com.clipforge.autoeditor

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.clipforge.autoeditor.ui.screens.AiScriptScreen
import com.clipforge.autoeditor.ui.screens.RenderExportScreen
import com.clipforge.autoeditor.ui.screens.VisualBuilderScreen
import com.clipforge.autoeditor.ui.theme.ClipForgeColors
import com.clipforge.autoeditor.ui.theme.ClipForgeTheme
import com.clipforge.autoeditor.viewmodel.ClipForgeViewModel

private enum class ClipForgeTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    AI_SCRIPT("ai_script", "AI Script", Icons.Rounded.AutoAwesome),
    VISUAL_BUILDER("visual_builder", "Pair Builder", Icons.Rounded.Tune),
    RENDER_EXPORT("render_export", "Render", Icons.Rounded.MovieCreation)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClipForgeTheme {
                ClipForgeApp()
            }
        }
    }
}

@Composable
fun ClipForgeApp(viewModel: ClipForgeViewModel = viewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ClipForgeTab.AI_SCRIPT.route

    val pairs by viewModel.pairs.collectAsStateWithLifecycle()
    val settings by viewModel.globalSettings.collectAsStateWithLifecycle()
    val scriptInput by viewModel.scriptInput.collectAsStateWithLifecycle()
    val parseMessage by viewModel.parseMessage.collectAsStateWithLifecycle()
    val renderState by viewModel.renderState.collectAsStateWithLifecycle()
    val renderStats by viewModel.renderStats.collectAsStateWithLifecycle()
    val showCommandDebug by viewModel.showCommandDebug.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(perms)
    }

    Scaffold(
        containerColor = ClipForgeColors.AmoledBlack,
        bottomBar = {
            NavigationBar(
                containerColor = ClipForgeColors.CardSurface,
                contentColor = ClipForgeColors.TextPrimary
            ) {
                ClipForgeTab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ClipForgeColors.CrimsonAccent,
                            selectedTextColor = ClipForgeColors.CrimsonAccent,
                            indicatorColor = ClipForgeColors.CrimsonContainer,
                            unselectedIconColor = ClipForgeColors.TextSecondary,
                            unselectedTextColor = ClipForgeColors.TextSecondary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ClipForgeTab.AI_SCRIPT.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ClipForgeTab.AI_SCRIPT.route) {
                AiScriptScreen(
                    scriptInput = scriptInput,
                    parseMessage = parseMessage,
                    pairs = pairs,
                    viewModel = viewModel,
                    onNavigateToRender = {
                        navController.navigate(ClipForgeTab.RENDER_EXPORT.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(ClipForgeTab.VISUAL_BUILDER.route) {
                VisualBuilderScreen(
                    pairs = pairs,
                    settings = settings,
                    viewModel = viewModel
                )
            }
            composable(ClipForgeTab.RENDER_EXPORT.route) {
                RenderExportScreen(
                    pairs = pairs,
                    renderState = renderState,
                    renderStats = renderStats,
                    showCommandDebug = showCommandDebug,
                    statusBanner = statusBanner,
                    viewModel = viewModel
                )
            }
        }
    }
}
