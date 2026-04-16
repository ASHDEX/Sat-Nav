package com.jayesh.satnav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jayesh.satnav.navigation.DebugCockpitNavHost
import com.jayesh.satnav.ui.theme.CockpitTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DebugMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CockpitTheme {
                DebugCockpitNavHost()
            }
        }
    }
}