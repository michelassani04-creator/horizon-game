package com.horizon.game.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.horizon.game.ui.auth.AuthViewModel
import com.horizon.game.ui.theme.HorizonRed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.horizon.game.ui.AppState
import com.horizon.game.ui.components.OsSwitchRow
import com.horizon.game.ui.components.osClickable
import com.horizon.game.ui.theme.HorizonBlue
import com.horizon.game.ui.theme.HorizonOnSurface
import com.horizon.game.ui.theme.HorizonOnSurfaceMuted
import com.horizon.game.ui.theme.HorizonSurface

private val sections = listOf("Affichage", "Notifications", "Serveur", "Manette", "Confidentialité", "À propos")

/** Réglages « système » : catégories à gauche, contenu de la catégorie à droite. */
@Composable
fun SettingsScreen(
    appState: AppState,
    auth: AuthViewModel,
    onOpenController: () -> Unit,
    modifier: Modifier = Modifier
) {
    var section by rememberSaveable { mutableStateOf(0) }

    Row(
        modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            Modifier
                .weight(0.32f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sections.forEachIndexed { index, label ->
                val selected = index == section
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) HorizonOnSurface else HorizonOnSurfaceMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .osClickable(RoundedCornerShape(12.dp)) { section = index }
                        .background(if (selected) HorizonBlue.copy(alpha = 0.22f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
        Column(
            Modifier
                .weight(0.68f)
                .fillMaxHeight()
                .background(HorizonSurface, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (section) {
                0 -> {
                    OsSwitchRow("Réduire les animations", appState.reduceAnimations.value) {
                        appState.setReduceAnimations(it)
                    }
                    Info("Langue", "Français")
                    Info("Thème", "Sombre HORIZON")
                    Info("Orientation", "Paysage (verrouillée)")
                }
                1 -> {
                    OsSwitchRow("Notifications", appState.notificationsEnabled.value) {
                        appState.setNotifications(it)
                    }
                    Text(
                        "Les notifications de mises à jour et d'actualités du Store seront actives avec le Lot 3.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HorizonOnSurfaceMuted
                    )
                }
                2 -> {
                    var url by rememberSaveable { mutableStateOf(auth.serverUrl) }
                    Text(
                        "Adresse du serveur HORIZON (HTTPS). Elle est fournie par l'équipe HORIZON une fois le serveur en ligne.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HorizonOnSurfaceMuted
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("https://api.exemple.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { auth.saveServerUrl(url) }) { Text("Enregistrer") }
                        OutlinedButton(
                            enabled = !auth.busy,
                            onClick = { auth.saveServerUrl(url); auth.testServer() }
                        ) { Text("Tester la connexion") }
                    }
                    auth.error?.let { Text(it, color = HorizonRed, style = MaterialTheme.typography.bodyMedium) }
                    auth.info?.let { Text(it, color = HorizonBlue, style = MaterialTheme.typography.bodyMedium) }
                }
                3 -> {
                    Text(
                        "Connecte une manette Bluetooth dans les réglages Android, puis teste ses boutons ici.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HorizonOnSurfaceMuted
                    )
                    OutlinedButton(onClick = onOpenController) { Text("Tester la manette") }
                }
                4 -> {
                    Info(
                        "Données",
                        "Sans compte, rien n'est envoyé en ligne : favoris, jeux récents et réglages restent sur cet appareil. Avec un HORIZON ID, ton pseudo, ton e-mail ou téléphone et tes préférences sont enregistrés sur le serveur HORIZON."
                    )
                    Info(
                        "Jeux installés",
                        "L'application vérifie seulement lesquels des jeux du HORIZON Store sont installés. " +
                            "Les autres applications de ton téléphone ne sont ni lues, ni listées, ni conservées."
                    )
                    Info("Politique de confidentialité", "Elle sera publiée avant le lancement public.")
                }
                else -> {
                    Info("Application", "HORIZON GAME")
                    Info("Version", "0.3.2 (Lot 3)")
                    Info("Identifiant", "com.horizon.game")
                }
            }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = HorizonOnSurface)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = HorizonOnSurfaceMuted)
    }
}
