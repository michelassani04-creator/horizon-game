package com.horizon.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.horizon.game.ui.components.osClickable
import com.horizon.game.ui.theme.HorizonBlue
import com.horizon.game.ui.theme.HorizonOnSurface
import com.horizon.game.ui.theme.HorizonOnSurfaceMuted
import com.horizon.game.ui.theme.HorizonSurface

/**
 * Profil. Mode visiteur uniquement pour l'instant : HORIZON ID (inscription, connexion,
 * récupération) arrive avec le backend au Lot 3 (F-003, F-004).
 */
@Composable
fun ProfileScreen(
    onOpenController: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            Modifier
                .weight(0.45f)
                .fillMaxHeight()
                .background(HorizonSurface, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = "Avatar", tint = HorizonBlue, modifier = Modifier.size(44.dp))
            }
            Text("Visiteur", style = MaterialTheme.typography.titleLarge, color = HorizonOnSurface, modifier = Modifier.padding(top = 10.dp))
            Text(
                "Crée un HORIZON ID pour synchroniser ton profil, tes favoris et tes préférences (Lot 3).",
                style = MaterialTheme.typography.bodyMedium,
                color = HorizonOnSurfaceMuted,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            Button(onClick = {}, enabled = false) { Text("Se connecter (bientôt)") }
        }
        Column(
            Modifier
                .weight(0.55f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            ProfileAction("Manette", "Détecter et tester tes contrôleurs", Icons.Filled.SportsEsports, onOpenController)
            ProfileAction("Aide", "FAQ et premiers pas", Icons.AutoMirrored.Filled.HelpOutline, onOpenHelp)
        }
    }
}

@Composable
private fun ProfileAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .osClickable(RoundedCornerShape(16.dp), onClick)
            .background(HorizonSurface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = HorizonBlue, modifier = Modifier.size(28.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = HorizonOnSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = HorizonOnSurfaceMuted)
        }
    }
}
