package com.debiandroid.desktop

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.debiandroid.desktop.data.SessionManager
import com.debiandroid.desktop.proot.ProotRunner
import com.debiandroid.desktop.proot.RootfsManager
import com.debiandroid.desktop.service.DesktopService
import com.debiandroid.desktop.ui.screens.*
import com.debiandroid.desktop.ui.theme.DebianDroidTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    private lateinit var sessionManager: SessionManager
    private lateinit var rootfsManager: RootfsManager
    private lateinit var prootRunner: ProotRunner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        rootfsManager = RootfsManager(this)
        prootRunner = ProotRunner(this)

        val isSetupComplete = try {
            runBlocking { sessionManager.isSetupComplete.first() }
        } catch (e: Exception) {
            false
        }

        setContent {
            DebianDroidTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DebianDroidNavHost(
                        isSetupComplete = isSetupComplete,
                        sessionManager = sessionManager,
                        rootfsManager = rootfsManager,
                        prootRunner = prootRunner
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        prootRunner.cleanup()
        super.onDestroy()
    }
}

@Composable
fun DebianDroidNavHost(
    isSetupComplete: Boolean,
    sessionManager: SessionManager,
    rootfsManager: RootfsManager,
    prootRunner: ProotRunner
) {
    val navController = rememberNavController()
    val startDestination = if (isSetupComplete) "home" else "onboarding"
    var isDesktopRunning by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(padding)
            ) {
                composable("onboarding") {
                    OnboardingScreen(
                        onGetStarted = { navController.navigate("setup") { popUpTo("onboarding") { inclusive = true } } }
                    )
                }
                composable("setup") {
                    SetupScreen(
                        rootfsManager = rootfsManager,
                        onComplete = {
                            scope.launch {
                                sessionManager.setSetupComplete(true)
                                navController.navigate("home") { popUpTo("setup") { inclusive = true } }
                            }
                        },
                        onError = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }
                composable("home") {
                    val context = LocalContext.current
                    val desktopResolution by sessionManager.desktopResolution.collectAsState(initial = "1280x720")
                    HomeScreen(
                        navController = navController,
                        isDesktopRunning = isDesktopRunning,
                        onToggleDesktop = {
                            val intent = Intent(context, DesktopService::class.java)
                            intent.action = if (isDesktopRunning) DesktopService.ACTION_STOP else DesktopService.ACTION_START
                            intent.putExtra(DesktopService.EXTRA_RESOLUTION, desktopResolution)
                            context.startService(intent)
                            isDesktopRunning = !isDesktopRunning
                        }
                    )
                }
                composable("desktop") {
                    val vncPassword by sessionManager.vncPassword.collectAsState(initial = "debian")
                    val desktopResolution by sessionManager.desktopResolution.collectAsState(initial = "1280x720")
                    DesktopScreen(
                        onBack = { navController.popBackStack() },
                        vncPassword = vncPassword,
                        resolution = desktopResolution
                    )
                }
                composable("terminal") {
                    TerminalScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("files") {
                    FileManagerScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onReinstall = {
                            scope.launch {
                                sessionManager.setSetupComplete(false)
                                navController.navigate("setup") { popUpTo("home") { inclusive = true } }
                            }
                        },
                        rootfsManager = rootfsManager,
                        onPatchStatus = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }
            }
        }
    }
}
