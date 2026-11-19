package com.horizon.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.horizon.game.data.SampleCatalog
import com.horizon.game.domain.model.Game
import com.horizon.game.domain.model.GameCategory
import com.horizon.game.ui.AppState
import com.horizon.game.ui.components.EmptyState
import com.horizon.game.ui.components.GameTile
import com.horizon.game.ui.components.SectionTitle
import com.horizon.game.ui.components.StatusPill
import com.horizon.game.ui.components.osClickable
import com.horizon.game.ui.components.tileColors
import com.horizon.game.ui.theme.HorizonBlue
import com.horizon.game.ui.theme.HorizonOnSurface
import com.horizon.game.ui.theme.HorizonOnSurfaceMuted

/** HORIZON Store : bannière à la une, recherche, catégories et grille de jeux. */
@Composable
fun StoreScreen(
    appState: AppState,
    onOpenGame: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    val games = SampleCatalog.games.filter { game ->
        game.title.contains(query, ignoreCase = true) &&
            (category == null || game.category.name == category)
    }
    val featured = SampleCatalog.games.firstOrNull { it.featured }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.width(240.dp),
                singleLine = true,
                placeholder = { Text("Rechercher") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Recherche") }
            )
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GameCategory.entries.toList()) { cat ->
                    FilterChip(
                        selected = category == cat.name,
                        onClick = { category = if (category == cat.name) null else cat.name },
                        label = { Text(cat.label) }
                    )
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(118.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (featured != null && query.isEmpty() && category == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FeaturedBanner(featured, appState.isInstalled(featured)) { onOpenGame(featured.id) }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Tous les jeux") }
            if (games.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("Aucun résultat", "Essaie un autre titre ou retire le filtre de catégorie.")
                }
            } else {
                items(games, key = { it.id }) { game ->
                    GameTile(
                        title = game.title,
                        seed = game.id,
                        badge = if (appState.isInstalled(game)) "INSTALLÉ" else null,
                        favorite = appState.isFavorite(game.id),
                        reduce = appState.reduceAnimations.value,
                        onClick = { onOpenGame(game.id) }
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Catalogue de démonstration : titres fictifs, publication des vrais jeux avec le Lot 3.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HorizonOnSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun FeaturedBanner(game: Game, installed: Boolean, onClick: () -> Unit) {
    val colors = tileColors(game.id)
    Column(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .osClickable(RoundedCornerShape(20.dp), onClick)
            .background(Brush.horizontalGradient(listOf(colors.first, colors.second)))
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        StatusPill(if (installed) "INSTALLÉ" else "À LA UNE", HorizonBlue)
        Text(
            game.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = HorizonOnSurface,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            game.description,
            style = MaterialTheme.typography.bodyMedium,
            color = HorizonOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
