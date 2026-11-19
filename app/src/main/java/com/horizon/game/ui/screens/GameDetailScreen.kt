package com.horizon.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.horizon.game.ui.AppState
import com.horizon.game.ui.components.EmptyState
import com.horizon.game.ui.components.GameCover
import com.horizon.game.ui.components.OsPage
import com.horizon.game.ui.components.StatusPill
import com.horizon.game.ui.theme.HorizonBlue
import com.horizon.game.ui.theme.HorizonOnSurface
import com.horizon.game.ui.theme.HorizonOnSurfaceMuted

/** Fiche d'un jeu du Store : visuel à gauche, informations et actions à droite. */
@Composable
fun GameDetailScreen(gameId: String, appState: AppState, onBack: () -> Unit) {
    val game = appState.gameById(gameId)
    val context = LocalContext.current

    OsPage(title = game?.title ?: "Jeu introuvable", onBack = onBack) {
        if (game == null) {
            EmptyState("Jeu introuvable", "Cette fiche n'existe pas ou n'est plus disponible sur le Store.")
            return@OsPage
        }
        val installed = appState.isInstalled(game)
        val favorite = appState.isFavorite(game.id)

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            GameCover(
                title = game.title,
                seed = game.id,
                modifier = Modifier
                    .width(170.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (installed) StatusPill("INSTALLÉ", HorizonBlue)
                Text(
                    game.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = HorizonOnSurface
                )
                Text(
                    "${game.category.label} · ${game.publisher} · ${game.ageRating} · ${game.sizeMb} Mo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HorizonOnSurfaceMuted
                )
                Text("Compatibilité : ${game.compatibility.label}", style = MaterialTheme.typography.bodyMedium, color = HorizonOnSurfaceMuted)
                Text(game.description, style = MaterialTheme.typography.bodyLarge, color = HorizonOnSurface)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (installed) {
                        Button(onClick = { appState.launchGame(context, game) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text("Jouer")
                        }
                    } else {
                        // Le téléchargement depuis le Store arrive avec le backend (Lot 3).
                        Button(onClick = {}, enabled = false) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Text("Installer")
                        }
                    }
                    OutlinedButton(onClick = { appState.toggleFavorite(game.id) }) {
                        Icon(
                            if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = null
                        )
                        Text(if (favorite) "Retirer des favoris" else "Ajouter aux favoris")
                    }
                }
                if (!installed) {
                    Text(
                        "L'installation depuis le HORIZON Store sera disponible avec le backend (Lot 3).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HorizonOnSurfaceMuted
                    )
                }
            }
        }
    }
}
