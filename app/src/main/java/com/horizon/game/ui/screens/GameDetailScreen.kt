package com.horizon.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.horizon.game.data.SampleCatalog
import com.horizon.game.ui.AppState
import com.horizon.game.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(gameId: String, appState: AppState, onBack: () -> Unit) {
    val game = SampleCatalog.byId(gameId)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(game?.title ?: "Jeu introuvable") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (game == null) {
            EmptyState("Jeu introuvable", "Cette fiche n'existe pas ou n'est plus disponible.", Modifier.padding(padding))
            return@Scaffold
        }
        val favorite = appState.isFavorite(game.id)
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(game.description, style = MaterialTheme.typography.bodyLarge)
            InfoRow("Éditeur", game.publisher)
            InfoRow("Catégorie", game.category.label)
            InfoRow("Classification", game.ageRating)
            InfoRow("Taille indicative", "${game.sizeMb} Mo")
            InfoRow("Compatibilité", game.compatibility.label)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Règle §4.1 : « Jouer » seulement si l'application est détectée et lançable (Lot 2).
                Button(onClick = {}, enabled = false) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Jouer")
                }
                OutlinedButton(onClick = { appState.toggleFavorite(game.id) }) {
                    Icon(
                        if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = null
                    )
                    Text(if (favorite) "Retirer des favoris" else "Ajouter aux favoris")
                }
            }
            Text(
                "Le bouton « Jouer » s'active quand le jeu installé est détecté (Lot 2).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label :", style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
