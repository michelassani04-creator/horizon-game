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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.horizon.game.ui.auth.AuthViewModel
import com.horizon.game.ui.theme.HorizonRed
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
 * Profil : mode visiteur, ou compte HORIZON ID connecté (vérification de l'e-mail, déconnexion).
 */
@Composable
fun ProfileScreen(
    auth: AuthViewModel,
    onOpenAuth: () -> Unit,
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
            val user = auth.user
            if (user == null) {
                Text("Visiteur", style = MaterialTheme.typography.titleLarge, color = HorizonOnSurface, modifier = Modifier.padding(top = 10.dp))
                Text(
                    "Crée un HORIZON ID pour synchroniser ton profil, tes favoris et tes préférences.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HorizonOnSurfaceMuted,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
                Button(onClick = onOpenAuth) { Text("Se connecter / S'inscrire") }
            } else {
                var showVerify by rememberSaveable { mutableStateOf(false) }
                var code by rememberSaveable { mutableStateOf("") }
                Text(user.displayName, style = MaterialTheme.typography.titleLarge, color = HorizonOnSurface, modifier = Modifier.padding(top = 10.dp))
                user.email?.let {
                    Text(
                        if (user.emailVerified) "$it · vérifié" else "$it · non vérifié",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HorizonOnSurfaceMuted
                    )
                }
                user.phone?.let {
                    Text("$it · vérification SMS bientôt", style = MaterialTheme.typography.bodyMedium, color = HorizonOnSurfaceMuted)
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (user.email != null && !user.emailVerified) {
                        OutlinedButton(onClick = { auth.requestEmailCode(); showVerify = true }) { Text("Vérifier l'e-mail") }
                    }
                    OutlinedButton(onClick = { auth.logout() }) { Text("Se déconnecter") }
                }
                auth.error?.let { Text(it, color = HorizonRed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
                auth.info?.let { Text(it, color = HorizonBlue, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
                if (showVerify) {
                    AlertDialog(
                        onDismissRequest = { showVerify = false },
                        title = { Text("Code de vérification") },
                        text = {
                            OutlinedTextField(
                                value = code,
                                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                                label = { Text("Code à 6 chiffres") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(
                                enabled = code.length == 6 && !auth.busy,
                                onClick = { auth.confirmEmailCode(code) { showVerify = false; code = "" } }
                            ) { Text("Valider") }
                        },
                        dismissButton = { TextButton(onClick = { showVerify = false }) { Text("Annuler") } }
                    )
                }
            }
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
