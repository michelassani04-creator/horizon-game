package com.horizon.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.horizon.game.ui.auth.AuthViewModel
import com.horizon.game.ui.catalog.CatalogViewModel
import com.horizon.game.ui.components.OsPage
import com.horizon.game.ui.theme.HorizonBlue
import com.horizon.game.ui.theme.HorizonOnSurface
import com.horizon.game.ui.theme.HorizonOnSurfaceMuted
import com.horizon.game.ui.theme.HorizonRed
import com.horizon.game.ui.theme.HorizonSurface

/** Saisie d'un code de téléchargement : ajoute le jeu à la bibliothèque du joueur. */
@Composable
fun RedeemScreen(
    auth: AuthViewModel,
    catalog: CatalogViewModel,
    onOpenAuth: () -> Unit,
    onOpenGame: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var code by rememberSaveable { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { catalog.clearRedeem() } }

    OsPage(title = "Code de téléchargement", onBack = onBack, modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .background(HorizonSurface, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!auth.isLoggedIn) {
                Text(
                    "Connecte-toi à ton HORIZON ID pour utiliser un code : le jeu sera ajouté à ta bibliothèque et te suivra sur tes appareils.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = HorizonOnSurface
                )
                Button(onClick = onOpenAuth) { Text("Se connecter / S'inscrire") }
            } else {
                Text(
                    "Saisis le code reçu (16 caractères, ex. ABCD-EFGH-JKLM-NPQR).",
                    style = MaterialTheme.typography.bodyLarge,
                    color = HorizonOnSurface
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(19) },
                    label = { Text("Code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
                catalog.redeemError?.let { Text(it, color = HorizonRed, style = MaterialTheme.typography.bodyMedium) }
                val won = catalog.redeemedGame
                if (won != null) {
                    Text("« ${won.title} » a été ajouté à ta bibliothèque !", color = HorizonBlue, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { onOpenGame(won.id) }) { Text("Ouvrir la fiche et télécharger") }
                } else {
                    Button(
                        enabled = !catalog.redeemBusy && code.count { it.isLetterOrDigit() } == 16,
                        onClick = { catalog.redeem(code) }
                    ) { Text(if (catalog.redeemBusy) "Vérification…" else "Valider le code") }
                }
                OutlinedButton(onClick = onBack) { Text("Retour") }
                Text(
                    "Un code ne s'utilise qu'une seule fois.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HorizonOnSurfaceMuted
                )
            }
        }
    }
}
