package com.moonreader.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moonreader.app.ui.navigation.MoonNavGraph
import com.moonreader.app.ui.screens.splash.SplashOverlay
import com.moonreader.app.ui.state.MainViewModel
import com.moonreader.app.ui.theme.MoonReaderTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val pendingOAuthUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        intent?.data?.let { data ->
            if (data.scheme == "moonreader") {
                pendingOAuthUri.value = data
            }
        }

        val container = (application as MoonReaderApp).container
        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(container)
            )
            val preferences by viewModel.preferences.collectAsState()
            val oauthUri by pendingOAuthUri.collectAsState()
            var showSplash by rememberSaveable { mutableStateOf(true) }

            LaunchedEffect(oauthUri) {
                val uri = oauthUri ?: return@LaunchedEffect
                viewModel.handleOAuthRedirect(uri)
                pendingOAuthUri.value = null
            }

            MoonReaderTheme(themeMode = preferences.themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MoonNavGraph(viewModel = viewModel)
                    SplashOverlay(
                        visible = showSplash,
                        onFinished = { showSplash = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { data ->
            if (data.scheme == "moonreader") {
                pendingOAuthUri.value = data
            }
        }
    }
}
