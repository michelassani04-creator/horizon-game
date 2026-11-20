package com.horizon.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.horizon.game.ui.AppState
import com.horizon.game.ui.components.EmptyState
import com.horizon.game.ui.components.GameTile
import com.horizon.game.ui.components.SectionTitle

/**
 * Bibliothèque : les jeux du HORIZON Store installés sur l'appareil, puis les favoris
 * pas encore installés. Aucune autre application n'est listée.
 */
@Composable
fun LibraryScreen(
    appState: AppState,
    onOpenGame: (String) -> Unit,
    onOpenStore: () -> Unit,
    onRedeem: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }

    val installed = appState.installedGames.filter { it.title.contains(query, ignoreCase = true) }
    val toDownload = appState.ownedGames.filter {
        !appState.isInstalled(it) && it.title.contains(query, ignoreCase = true)
    }
    val wishlist = appState.catalog.filter {
        appState.isFavorite(it.id) && !appState.isInstalled(it) && it.title.contains(query, ignoreCase = true)
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.width(260.dp),
                singleLine = true,
                placeholder = { Text("Rechercher dans mes jeux") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Recherche") }
            )
            OutlinedButton(onClick = onRedeem, modifier = Modifier.padding(start = 12.dp)) { Text("Saisir un code") }
        }
        if (installed.isEmpty() && wishlist.isEmpty() && toDownload.isEmpty()) {
            EmptyState(
                title = "Bibliothèque vide",
                message = "Les jeux installés depuis le HORIZON Store apparaissent ici, avec le bouton Jouer. " +
                    "Tes favoris du Store sont aussi regroupés ici."
            )
            Button(onClick = onOpenStore, modifier = Modifier.padding(horizontal = 24.dp)) { Text("Ouvrir le Store") }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(118.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (installed.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Installés") }
                    items(installed, key = { "in:" + it.id }) { game ->
                        GameTile(
                            title = game.title,
                            seed = game.id,
                            badge = "INSTALLÉ",
                            favorite = appState.isFavorite(game.id),
                            reduce = appState.reduceAnimations.value,
                            onClick = { onOpenGame(game.id) }
                        )
                    }
                }
                if (toDownload.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("À télécharger") }
                    items(toDownload, key = { "dl:" + it.id }) { game ->
                        GameTile(
                            title = game.title,
                            seed = game.id,
                            badge = "POSSÉDÉ",
                            favorite = appState.isFavorite(game.id),
                            reduce = appState.reduceAnimations.value,
                            onClick = { onOpenGame(game.id) }
                        )
                    }
                }
                if (wishlist.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Favoris du Store") }
                    items(wishlist, key = { "fav:" + it.id }) { game ->
                        GameTile(
                            title = game.title,
                            seed = game.id,
                            favorite = true,
                            reduce = appState.reduceAnimations.value,
                            onClick = { onOpenGame(game.id) }
                        )
                    }
                }
            }
        }
    }
}
