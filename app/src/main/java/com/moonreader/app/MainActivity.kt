package com.moonreader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moonreader.app.ui.navigation.MoonNavGraph
import com.moonreader.app.ui.state.MainViewModel
import com.moonreader.app.ui.theme.MoonReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MoonReaderApp).container
        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(container)
            )
            val preferences by viewModel.preferences.collectAsState()

            MoonReaderTheme(themeMode = preferences.themeMode) {
                MoonNavGraph(viewModel = viewModel)
            }
        }
    }
}
