package com.horizon.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.horizon.game.data.SampleCatalog
import com.horizon.game.ui.AppState
import com.horizon.game.ui.components.EmptyState
import com.horizon.game.ui.components.GameCard
import com.horizon.game.ui.components.SectionTitle

/**
 * Bibliothèque personnelle. Lot 1 : affiche les favoris.
 * Lot 2 : détection des jeux Android installés via PackageManager (F-005, F-008).
 */
@Composable
fun LibraryScreen(
    appState: AppState,
    onOpenGame: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    val games = SampleCatalog.games.filter {
        appState.isFavorite(it.id) && it.title.contains(query, ignoreCase = true)
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        SectionTitle("Bibliothèque")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Rechercher un jeu") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Recherche") }
        )
        if (games.isEmpty()) {
            EmptyState(
                title = "Bibliothèque vide",
                message = "Ajoute des jeux en favoris depuis le catalogue. " +
                    "La détection des jeux Android installés arrive au Lot 2."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(games, key = { it.id }) { game ->
                    GameCard(game, appState.isFavorite(game.id), onClick = { onOpenGame(game.id) })
                }
            }
        }
    }
}
