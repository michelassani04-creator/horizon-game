package com.horizon.game.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
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
import com.horizon.game.domain.model.GameCategory
import com.horizon.game.ui.AppState
import com.horizon.game.ui.components.EmptyState
import com.horizon.game.ui.components.GameCard
import com.horizon.game.ui.components.SectionTitle

@Composable
fun CatalogScreen(
    appState: AppState,
    onOpenGame: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    val games = SampleCatalog.games.filter { game ->
        game.title.contains(query, ignoreCase = true) &&
            (selectedCategory == null || game.category.name == selectedCategory)
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        SectionTitle("Catalogue")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Rechercher par titre") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Recherche") }
        )
        LazyRow(
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(GameCategory.entries.toList()) { category ->
                FilterChip(
                    selected = selectedCategory == category.name,
                    onClick = {
                        selectedCategory = if (selectedCategory == category.name) null else category.name
                    },
                    label = { Text(category.label) }
                )
            }
        }
        Text(
            "Données de démonstration (titres fictifs).",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (games.isEmpty()) {
            EmptyState("Aucun résultat", "Essaie un autre titre ou retire le filtre de catégorie.")
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
