package com.horizon.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.horizon.game.domain.model.priceLabel
import com.horizon.game.ui.AppState
import com.horizon.game.ui.catalog.CatalogViewModel
import com.horizon.game.ui.downloads.DownloadsViewModel
import com.horizon.game.ui.theme.HorizonRed
import com.horizon.game.ui.components.EmptyState
import com.horizon.game.ui.components.GameCover
import com.horizon.game.ui.components.OsPage
import com.horizon.game.ui.components.StatusPill
import com.horizon.game.ui.theme.HorizonBlue
import com.horizon.game.ui.theme.HorizonOnSurface
import com.horizon.game.ui.theme.HorizonOnSurfaceMuted

/** Fiche d'un jeu du Store : visuel à gauche, informations et actions à droite. */
@Composable
fun GameDetailScreen(
    gameId: String,
    appState: AppState,
    downloads: DownloadsViewModel,
    catalog: CatalogViewModel,
    loggedIn: Boolean,
    onLogin: () -> Unit,
    onRedeem: () -> Unit,
    onBack: () -> Unit
) {
    val game = appState.gameById(gameId)
    val context = LocalContext.current

    OsPage(title = game?.title ?: "Jeu introuvable", onBack = onBack) {
        if (game == null) {
            EmptyState("Jeu introuvable", "Cette fiche n'existe pas ou n'est plus disponible sur le Store.")
            return@OsPage
        }
        val installed = appState.isInstalled(game)
        val favorite = appState.isFavorite(game.id)
        val owned = appState.ownedGame(game.id)
        val packVersion = downloads.installedVersion(game.id)
        val needsUpdate = owned != null && packVersion in 1 until owned.versionCode
        val progress = downloads.progress[game.id]
        val error = downloads.errors[game.id]
        val order = catalog.orders[game.id]
        var showBuy by rememberSaveable { mutableStateOf(false) }

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

                if (progress != null) {
                    if (progress >= 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        if (progress >= 0f) "Téléchargement… ${(progress * 100).toInt()} %" else "Téléchargement…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HorizonOnSurfaceMuted
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (progress != null) {
                        OutlinedButton(onClick = { downloads.cancel(game.id) }) { Text("Annuler") }
                    } else if (needsUpdate && owned != null) {
                        Button(onClick = { downloads.start(owned) }) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Text("Mettre à jour (v${owned.versionName})")
                        }
                    } else if (installed) {
                        Button(onClick = { appState.launchGame(context, game) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text("Jouer")
                        }
                        if (packVersion > 0) {
                            OutlinedButton(onClick = { downloads.delete(game.id) }) { Text("Supprimer du téléphone") }
                        }
                    } else if (owned != null && owned.packUrl != null) {
                        Button(onClick = { downloads.start(owned) }) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Text("Télécharger (${owned.sizeMb} Mo)")
                        }
                    } else if (owned == null) {
                        val price = game.priceCents
                        if (order != null && (order.status == "creating" || order.status == "pending")) {
                            OutlinedButton(onClick = { catalog.dismissOrder(game.id) }) { Text("Annuler l'attente") }
                        } else {
                            if (price != null && price > 0) {
                                Button(onClick = { if (loggedIn) showBuy = true else onLogin() }) {
                                    Text("Acheter · ${game.priceLabel()}")
                                }
                            } else if (price == 0) {
                                Button(onClick = { if (loggedIn) catalog.claimFree(game) else onLogin() }) {
                                    Text("Obtenir gratuitement")
                                }
                            }
                            OutlinedButton(onClick = onRedeem) { Text("Saisir un code") }
                        }
                    } else {
                        Button(onClick = {}, enabled = false) { Text("Bientôt disponible") }
                    }
                    OutlinedButton(onClick = { appState.toggleFavorite(game.id) }) {
                        Icon(
                            if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = null
                        )
                        Text(if (favorite) "Retirer des favoris" else "Ajouter aux favoris")
                    }
                }
                error?.let { Text(it, color = HorizonRed, style = MaterialTheme.typography.bodyMedium) }
                if (order != null) {
                    val text = when (order.status) {
                        "creating" -> "Création de la commande…"
                        "pending" -> (order.message ?: "En attente de confirmation du paiement.") + " Cet écran se met à jour tout seul."
                        "failed" -> "Le paiement a échoué ou a été refusé."
                        else -> order.message ?: "Une erreur est survenue."
                    }
                    val bad = order.status == "failed" || order.status == "error" || order.status == "timeout"
                    Text(text, color = if (bad) HorizonRed else HorizonBlue, style = MaterialTheme.typography.bodyMedium)
                }
                if (owned == null && !installed) {
                    Text(
                        if (game.priceCents == null) "Ce jeu s'obtient avec un code de téléchargement, puis se télécharge dans HORIZON GAME."
                        else "Achète ce jeu ou saisis un code, puis télécharge-le dans HORIZON GAME.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HorizonOnSurfaceMuted
                    )
                }
                if (showBuy) {
                    AlertDialog(
                        onDismissRequest = { showBuy = false },
                        title = { Text("Acheter « ${game.title} »") },
                        text = {
                            Text(
                                "Prix : ${game.priceLabel()}.\n\nPAIEMENT DE TEST : aucun argent réel n'est prélevé. " +
                                    "La commande est confirmée par le serveur HORIZON, pas par l'application."
                            )
                        },
                        confirmButton = { TextButton(onClick = { showBuy = false; catalog.buy(game) }) { Text("Confirmer") } },
                        dismissButton = { TextButton(onClick = { showBuy = false }) { Text("Annuler") } }
                    )
                }
            }
        }
    }
}
