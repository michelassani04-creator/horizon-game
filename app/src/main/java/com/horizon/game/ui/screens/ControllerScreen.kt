package com.horizon.game.ui.screens

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.horizon.game.ui.components.EmptyState
import com.horizon.game.ui.components.OsPage
import com.horizon.game.ui.theme.HorizonOnSurface
import com.horizon.game.ui.theme.HorizonOnSurfaceMuted
import com.horizon.game.ui.theme.HorizonSurface

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
 * Écran de test des manettes (F-010) : liste les contrôleurs connectés et affiche
 * les boutons reçus. Les axes analogiques et la configuration avancée viendront plus tard.
 */
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

    OsPage(title = "Manette", onBack = onBack) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val name = KeyEvent.keyCodeToString(event.key.nativeKeyCode).removePrefix("KEYCODE_")
                        lastKey = name
                        if (name !in pressed) pressed.add(name)
                    }
                    false
                },
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(HorizonSurface, RoundedCornerShape(20.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Contrôleurs détectés", style = MaterialTheme.typography.titleMedium, color = HorizonOnSurface)
                if (controllers.isEmpty()) {
                    EmptyState(
                        "Aucune manette",
                        "Connecte une manette Bluetooth depuis les réglages Android, puis appuie sur un bouton."
                    )
                } else {
                    controllers.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge, color = HorizonOnSurface) }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(HorizonSurface, RoundedCornerShape(20.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Test des boutons", style = MaterialTheme.typography.titleMedium, color = HorizonOnSurface)
                Text(
                    "Appuie sur les boutons de la manette ou du clavier.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HorizonOnSurfaceMuted
                )
                Text("Dernier bouton : ${lastKey ?: "—"}", style = MaterialTheme.typography.bodyLarge, color = HorizonOnSurface)
                Text(
                    "Boutons testés : ${if (pressed.isEmpty()) "—" else pressed.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HorizonOnSurfaceMuted
                )
                Button(
                    onClick = {
                        pressed.clear()
                        lastKey = null
                        controllers = connectedControllers()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Réinitialiser / Reconnecter") }
            }
        }
    }
}
