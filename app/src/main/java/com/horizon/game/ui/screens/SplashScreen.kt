package com.horizon.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.horizon.game.ui.theme.HorizonBlue
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    // Le contrôle de version (F-014) sera branché sur GET /v1/config au Lot 3.
    LaunchedEffect(Unit) {
        delay(1200)
        onFinished()
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("HORIZON", style = MaterialTheme.typography.headlineLarge, color = HorizonBlue)
        Text("GAME", style = MaterialTheme.typography.headlineMedium)
        LinearProgressIndicator(
            modifier = Modifier
                .padding(top = 24.dp)
                .width(160.dp),
            color = HorizonBlue
        )
    }
}
