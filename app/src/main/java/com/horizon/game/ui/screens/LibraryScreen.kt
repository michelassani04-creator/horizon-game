package com.horizon.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.horizon.game.data.SampleCatalog
import com.horizon.game.ui.AppState
import com.horizon.game.ui.components.EmptyState
import com.horizon.game.ui.components.GameCard
import com.horizon.game.ui.components.InstalledGameCard
import com.horizon.game.ui.components.SectionTitle

/**
 * Bibliothèque personnelle : jeux Android détectés sur l'appareil (F-005)
 * et favoris du catalogue. Les jeux détectés se lancent directement (F-008).
 */
@Composable
fun LibraryScreen(
    appState: AppState,
    onOpenGame: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }

    val installed = appState.visibleInstalled.filter { it.label.contains(query, ignoreCase = true) }
    val favoriteGames = SampleCatalog.games.filter {
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
        if (installed.isEmpty() && favoriteGames.isEmpty()) {
            EmptyState(
                title = "Bibliothèque vide",
                message = "Aucun jeu détecté. Installe un jeu Android, ou active « Afficher toutes les applications » " +
                    "dans Paramètres pour tester. Les favoris du catalogue apparaissent ici."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (installed.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Jeux installés") }
                    items(installed, key = { "app:" + it.packageName }) { game ->
                        InstalledGameCard(
                            game = game,
                            isFavorite = appState.isFavorite(game.packageName),
                            onPlay = { appState.launchGame(context, game.packageName) },
                            onToggleFavorite = { appState.toggleFavorite(game.packageName) }
                        )
                    }
                }
                if (favoriteGames.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Favoris du catalogue") }
                    items(favoriteGames, key = { "game:" + it.id }) { game ->
                        GameCard(game, appState.isFavorite(game.id), onClick = { onOpenGame(game.id) })
                    }
                }
            }
        }
    }
}
