package com.horizon.game.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.game.data.SampleCatalog
import com.horizon.game.domain.model.Game
import com.horizon.game.ui.AppState
import com.horizon.game.ui.components.GameTile
import com.horizon.game.ui.components.StatusPill
import com.horizon.game.ui.components.tileColors
import com.horizon.game.ui.theme.HorizonBackground
import com.horizon.game.ui.theme.HorizonBlue
import com.horizon.game.ui.theme.HorizonOnSurface
import com.horizon.game.ui.theme.HorizonOnSurfaceMuted
import com.horizon.game.ui.theme.HorizonRed

private enum class Kind { INSTALLED, STORE, SOON }

private data class Tile(
    val key: String,
    val kind: Kind,
    val title: String,
    val subtitle: String,
    val symbol: ImageVector? = null
)

private val soonTiles = listOf(
    Tile("soon-pass", Kind.SOON, "Game Pass", "Catalogue par abonnement", Icons.Filled.WorkspacePremium),
    Tile("soon-social", Kind.SOON, "Social Hub", "Amis, groupes et communautés", Icons.Filled.Groups),
    Tile("soon-center", Kind.SOON, "Game Center", "Statistiques et trophées", Icons.Filled.EmojiEvents),
    Tile("soon-ai", Kind.SOON, "HORIZON AI", "Assistant gaming", Icons.Filled.SmartToy),
    Tile("soon-cloud", Kind.SOON, "HORIZON CLOUD", "Jeux en streaming", Icons.Filled.Cloud)
)

private fun Game.toTile(installed: Boolean) = Tile(
    key = id,
    kind = if (installed) Kind.INSTALLED else Kind.STORE,
    title = title,
    subtitle = "${category.label} · $publisher"
)

/**
 * Accueil de console (paysage) : à gauche le héros du jeu sélectionné,
 * à droite les étagères de tuiles. Le D-pad déplace le focus, OK ouvre ou lance.
 */
@Composable
fun HomeScreen(
    appState: AppState,
    onOpenGame: (String) -> Unit,
    onOpenStore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reduce = appState.reduceAnimations.value

    val mine = appState.installedGames
    val recentTiles = appState.recent
        .mapNotNull { id -> SampleCatalog.byId(id) }
        .filter { appState.isInstalled(it) }
        .map { it.toTile(true) }
    val mineTiles = mine.map { it.toTile(true) }
    val storeTiles = SampleCatalog.games.filter { !appState.isInstalled(it) }.map { it.toTile(false) }
    val allTiles = recentTiles + mineTiles + storeTiles + soonTiles

    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = allTiles.firstOrNull { it.key == selectedKey } ?: allTiles.first()

    val accent = tileColors(selected.key)
    val bg by animateColorAsState(accent.first, tween(if (reduce) 0 else 500), label = "homeBg")

    fun activate(tile: Tile) {
        when (tile.kind) {
            Kind.INSTALLED -> SampleCatalog.byId(tile.key)?.let { appState.launchGame(context, it) }
            Kind.STORE -> onOpenGame(tile.key)
            Kind.SOON -> Unit
        }
    }

    @Composable
    fun Shelf(title: String, tiles: List<Tile>) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = HorizonOnSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(tiles, key = { it.key }) { tile ->
                    GameTile(
                        title = tile.title,
                        seed = tile.key,
                        modifier = Modifier.width(112.dp),
                        symbol = tile.symbol,
                        badge = when (tile.kind) {
                            Kind.INSTALLED -> "INSTALLÉ"
                            Kind.SOON -> "BIENTÔT"
                            Kind.STORE -> null
                        },
                        badgeColor = if (tile.kind == Kind.SOON) HorizonRed else HorizonBlue,
                        favorite = appState.isFavorite(tile.key),
                        highlighted = tile.key == selected.key,
                        reduce = reduce,
                        onFocus = { selectedKey = tile.key },
                        onClick = { if (tile.key == selected.key) activate(tile) else selectedKey = tile.key }
                    )
                }
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0.70f), HorizonBackground, HorizonBackground)))
    ) {
        Row(Modifier.fillMaxSize()) {
            // Héros
            Column(
                Modifier
                    .weight(0.40f)
                    .fillMaxHeight()
                    .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                when (selected.kind) {
                    Kind.INSTALLED -> StatusPill("INSTALLÉ", HorizonBlue)
                    Kind.STORE -> StatusPill("SUR LE STORE", HorizonBlue)
                    Kind.SOON -> StatusPill("BIENTÔT DISPONIBLE", HorizonRed)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    selected.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = HorizonOnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(selected.subtitle, style = MaterialTheme.typography.bodyMedium, color = HorizonOnSurfaceMuted)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (selected.kind) {
                        Kind.INSTALLED -> Button(onClick = { activate(selected) }) { Text("Jouer") }
                        Kind.STORE -> Button(onClick = { activate(selected) }) { Text("Voir sur le Store") }
                        Kind.SOON -> Button(onClick = {}, enabled = false) { Text("Bientôt") }
                    }
                    if (selected.kind != Kind.SOON) {
                        val fav = appState.isFavorite(selected.key)
                        OutlinedButton(onClick = { appState.toggleFavorite(selected.key) }) {
                            Icon(
                                if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (fav) "Retirer des favoris" else "Ajouter aux favoris"
                            )
                        }
                    }
                }
            }

            // Étagères
            LazyColumn(
                Modifier
                    .weight(0.60f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (recentTiles.isNotEmpty()) item(key = "shelf-recent") { Shelf("Reprendre", recentTiles) }
                item(key = "shelf-mine") {
                    if (mineTiles.isEmpty()) {
                        Column(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Mes jeux", style = MaterialTheme.typography.titleMedium, color = HorizonOnSurface)
                            Text(
                                "Aucun jeu installé pour l'instant. Les jeux que tu télécharges depuis le HORIZON Store apparaissent ici.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HorizonOnSurfaceMuted
                            )
                            Row {
                                Icon(Icons.Filled.Storefront, contentDescription = null, tint = HorizonBlue)
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = onOpenStore) { Text("Ouvrir le Store") }
                            }
                        }
                    } else {
                        Shelf("Mes jeux", mineTiles)
                    }
                }
                if (storeTiles.isNotEmpty()) item(key = "shelf-store") { Shelf("À découvrir sur le Store", storeTiles) }
                item(key = "shelf-soon") { Shelf("Prochainement", soonTiles) }
            }
        }
    }
}
