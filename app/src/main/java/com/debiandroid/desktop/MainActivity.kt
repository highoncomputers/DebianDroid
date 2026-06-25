package com.debiandroid.desktop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.debiandroid.desktop.data.SessionManager
import com.debiandroid.desktop.proot.ProotRunner
import com.debiandroid.desktop.proot.RootfsManager
import com.debiandroid.desktop.service.DesktopService
import com.debiandroid.desktop.service.SetupService
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

        val isSetupComplete = runBlocking {
            sessionManager.isSetupComplete.first()
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

    val scope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = startDestination) {
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
                onError = { }
            )
        }
        composable("home") {
            HomeScreen(
                navController = navController,
                isDesktopRunning = isDesktopRunning
            )
        }
        composable("desktop") {
            val vncPassword by sessionManager.vncPassword.collectAsState(initial = "debian")
            DesktopScreen(
                onBack = { navController.popBackStack() },
                vncPassword = vncPassword,
                resolution = "1280x720"
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
                }
            )
        }
    }
}
