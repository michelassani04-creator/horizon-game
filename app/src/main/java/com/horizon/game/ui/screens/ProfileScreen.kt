package com.horizon.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.horizon.game.ui.components.SectionTitle
import com.horizon.game.ui.theme.HorizonBlue

/**
 * Profil. Lot 1 : mode visiteur uniquement.
 * HORIZON ID (inscription, connexion, récupération) arrive avec le backend au Lot 3 (F-003, F-004).
 */
@Composable
fun ProfileScreen(
    onOpenController: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Profil")
        Box(
            Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Person, contentDescription = "Avatar", tint = HorizonBlue, modifier = Modifier.size(48.dp))
        }
        Text(
            "Mode visiteur",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            "Crée un HORIZON ID pour synchroniser ton profil, tes favoris et tes préférences. " +
                "Cette fonction sera disponible avec le backend (Lot 3).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("Créer un compte / Se connecter (bientôt)")
        }
        OutlinedButton(onClick = onOpenController, modifier = Modifier.fillMaxWidth()) { Text("Manette") }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Paramètres") }
        OutlinedButton(onClick = onOpenHelp, modifier = Modifier.fillMaxWidth()) { Text("Aide") }
    }
}
