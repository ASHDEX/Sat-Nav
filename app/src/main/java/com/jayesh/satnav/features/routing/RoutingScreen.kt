package com.jayesh.satnav.features.routing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun RoutingScreen(
    modifier: Modifier = Modifier,
    viewModel: RoutingViewModel = hiltViewModel(),
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Routing Screen (UI Layer Disabled)")
    }
}
