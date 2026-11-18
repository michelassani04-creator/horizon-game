package com.horizon.game.ui.screens

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.horizon.game.ui.components.EmptyState

private fun connectedControllers(): List<String> =
    InputDevice.getDeviceIds().toList()
        .mapNotNull { id: Int -> InputDevice.getDevice(id) }
        .filter { device: InputDevice ->
            val sources: Int = device.sources
            (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        }
        .map { device: InputDevice -> device.name }

/**
 * Écran de test des manettes (F-010) : détecte les manettes connectées
 * et affiche les boutons reçus. Les axes analogiques et la gestion avancée arrivent au Lot 2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var controllers by remember { mutableStateOf(connectedControllers()) }
    val pressed = remember { mutableStateListOf<String>() }
    var lastKey by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { controllers = connectedControllers() }
            override fun onInputDeviceRemoved(deviceId: Int) { controllers = connectedControllers() }
            override fun onInputDeviceChanged(deviceId: Int) { controllers = connectedControllers() }
        }
        manager.registerInputDeviceListener(listener, null)
        onDispose { manager.unregisterInputDeviceListener(listener) }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Manette") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val name = KeyEvent.keyCodeToString(event.key.nativeKeyCode)
                            .removePrefix("KEYCODE_")
                        lastKey = name
                        if (name !in pressed) pressed.add(name)
                    }
                    false
                }
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Contrôleurs détectés", style = MaterialTheme.typography.titleLarge)
            if (controllers.isEmpty()) {
                EmptyState(
                    "Aucune manette",
                    "Connecte une manette Bluetooth depuis les réglages Android, puis reviens ici."
                )
            } else {
                controllers.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
            }

            Text("Test des boutons", style = MaterialTheme.typography.titleLarge)
            Text(
                "Appuie sur les boutons de la manette ou du clavier.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Dernier bouton : ${lastKey ?: "—"}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Boutons testés : ${if (pressed.isEmpty()) "—" else pressed.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = { pressed.clear(); lastKey = null; controllers = connectedControllers() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Réinitialiser / Reconnecter") }
        }
    }
}
