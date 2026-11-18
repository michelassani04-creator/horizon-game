package com.horizon.game.ui.screens

import android.content.Context
import android.os.BatteryManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.game.data.InstalledGame
import com.horizon.game.data.SampleCatalog
import com.horizon.game.ui.AppState
import com.horizon.game.ui.theme.HorizonBackground
import com.horizon.game.ui.theme.HorizonBlue
import com.horizon.game.ui.theme.HorizonOnSurface
import com.horizon.game.ui.theme.HorizonOnSurfaceMuted
import com.horizon.game.ui.theme.HorizonRed
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class TileKind { INSTALLED, CATALOG, SOON }

private data class Tile(
    val key: String,
    val kind: TileKind,
    val title: String,
    val subtitle: String,
    val icon: ImageBitmap? = null,
    val symbol: ImageVector? = null
)

private val soonTiles = listOf(
    Tile("soon-store", TileKind.SOON, "Store", "Boutique numérique officielle", symbol = Icons.Filled.Storefront),
    Tile("soon-pass", TileKind.SOON, "Game Pass", "Catalogue par abonnement", symbol = Icons.Filled.WorkspacePremium),
    Tile("soon-social", TileKind.SOON, "Social Hub", "Amis, groupes et communautés", symbol = Icons.Filled.Groups),
    Tile("soon-center", TileKind.SOON, "Game Center", "Statistiques et trophées", symbol = Icons.Filled.EmojiEvents),
    Tile("soon-ai", TileKind.SOON, "HORIZON AI", "Assistant gaming", symbol = Icons.Filled.SmartToy),
    Tile("soon-cloud", TileKind.SOON, "HORIZON CLOUD", "Jeux en streaming", symbol = Icons.Filled.Cloud)
)

private fun InstalledGame.toTile(): Tile = Tile(
    key = packageName,
    kind = TileKind.INSTALLED,
    title = label,
    subtitle = if (isGame) "Jeu Android installé" else "Application installée",
    icon = icon
)

private fun tileColors(seed: String): Pair<Color, Color> {
    val hue = ((seed.hashCode().toLong() and 0x7fffffffL) % 360L).toFloat()
    return Color.hsv(hue, 0.60f, 0.55f) to Color.hsv((hue + 40f) % 360f, 0.70f, 0.22f)
}

@Composable
private fun rememberStatus(): String {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val battery = try {
                (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } catch (e: Exception) {
                -1
            }
            text = if (battery in 1..100) "$time  ·  $battery %" else time
            delay(20_000)
        }
    }
    return text
}

/**
 * Accueil de type console : héros du jeu sélectionné + étagères de tuiles.
 * Le focus manette / clavier (D-pad) sélectionne les tuiles ; OK ouvre ou lance.
 * En écran large (paysage, tablette) le héros passe à gauche et les étagères à droite.
 */
@Composable
fun HomeScreen(
    appState: AppState,
    wide: Boolean,
    onOpenGame: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenController: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reduce = appState.reduceAnimations.value
    val installed = appState.visibleInstalled

    val recentTiles = appState.recent
        .mapNotNull { pkg -> installed.firstOrNull { it.packageName == pkg } }
        .map { it.toTile() }
    val installedTiles = installed.map { it.toTile() }
    val catalogTiles = SampleCatalog.games.map { g ->
        Tile(g.id, TileKind.CATALOG, g.title, "${g.category.label} · ${g.publisher}")
    }
    val allTiles = recentTiles + installedTiles + catalogTiles + soonTiles

    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = allTiles.firstOrNull { it.key == selectedKey } ?: allTiles.first()

    val accent = tileColors(selected.key).first
    val bg by animateColorAsState(accent, tween(if (reduce) 0 else 500), label = "homeBg")
    val status = rememberStatus()
    val tileWidth: Dp = if (wide) 124.dp else 140.dp

    fun activate(tile: Tile) {
        when (tile.kind) {
            TileKind.INSTALLED -> appState.launchGame(context, tile.key)
            TileKind.CATALOG -> onOpenGame(tile.key)
            TileKind.SOON -> Unit
        }
    }

    val hero: @Composable (Modifier) -> Unit = { m ->
        HeroPanel(
            tile = selected,
            wide = wide,
            isFavorite = appState.isFavorite(selected.key),
            onPrimary = { activate(selected) },
            onToggleFavorite = { appState.toggleFavorite(selected.key) },
            modifier = m
        )
    }

    val shelves: @Composable (Modifier) -> Unit = { m ->
        LazyColumn(
            modifier = m,
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (recentTiles.isNotEmpty()) {
                item(key = "shelf-recent") {
                    Shelf("Reprendre", recentTiles, selected.key, tileWidth, reduce,
                        onFocus = { selectedKey = it.key },
                        onClick = { if (it.key == selected.key) activate(it) else selectedKey = it.key })
                }
            }
            item(key = "shelf-installed") {
                if (installedTiles.isEmpty()) {
                    EmptyShelf(onOpenLibrary)
                } else {
                    Shelf("Mes jeux", installedTiles, selected.key, tileWidth, reduce,
                        onFocus = { selectedKey = it.key },
                        onClick = { if (it.key == selected.key) activate(it) else selectedKey = it.key })
                }
            }
            item(key = "shelf-catalog") {
                Shelf("À découvrir", catalogTiles, selected.key, tileWidth, reduce,
                    onFocus = { selectedKey = it.key },
                    onClick = { if (it.key == selected.key) activate(it) else selectedKey = it.key })
            }
            item(key = "shelf-soon") {
                Shelf("Prochainement", soonTiles, selected.key, tileWidth, reduce,
                    onFocus = { selectedKey = it.key },
                    onClick = { selectedKey = it.key })
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(bg.copy(alpha = 0.70f), HorizonBackground, HorizonBackground)
                )
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                wide = wide,
                status = status,
                onOpenController = onOpenController,
                onOpenSettings = onOpenSettings
            )
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    hero(Modifier.weight(0.42f).fillMaxHeight())
                    shelves(Modifier.weight(0.58f).fillMaxHeight())
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    hero(Modifier.fillMaxWidth())
                    shelves(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    wide: Boolean,
    status: String,
    onOpenController: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("HORIZON", color = HorizonOnSurface, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 3.sp)
        Text(" GAME", color = HorizonBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 3.sp)
        Spacer(Modifier.weight(1f))
        if (wide) {
            listOf("Jeux", "Store", "Social", "AI", "Cloud").forEachIndexed { index, label ->
                Text(
                    text = label,
                    color = if (index == 0) HorizonOnSurface else HorizonOnSurfaceMuted,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .then(
                            if (index == 0) Modifier.border(1.5.dp, HorizonBlue, RoundedCornerShape(50)) else Modifier
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Text(status, color = HorizonOnSurfaceMuted, style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = onOpenController) {
            Icon(Icons.Filled.SportsEsports, contentDescription = "Manette", tint = HorizonOnSurface)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Paramètres", tint = HorizonOnSurface)
        }
    }
}

@Composable
private fun HeroPanel(
    tile: Tile,
    wide: Boolean,
    isFavorite: Boolean,
    onPrimary: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.padding(horizontal = 24.dp, vertical = if (wide) 8.dp else 14.dp),
        verticalArrangement = Arrangement.Center
    ) {
        val kindLabel = when (tile.kind) {
            TileKind.INSTALLED -> "INSTALLÉ"
            TileKind.CATALOG -> "CATALOGUE"
            TileKind.SOON -> "BIENTÔT DISPONIBLE"
        }
        Text(kindLabel, color = if (tile.kind == TileKind.SOON) HorizonRed else HorizonBlue,
            style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
        Text(
            tile.title,
            style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
            color = HorizonOnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(tile.subtitle, style = MaterialTheme.typography.bodyMedium, color = HorizonOnSurfaceMuted)
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (tile.kind) {
                TileKind.INSTALLED -> Button(onClick = onPrimary) { Text("Jouer") }
                TileKind.CATALOG -> Button(onClick = onPrimary) { Text("Voir la fiche") }
                TileKind.SOON -> Button(onClick = {}, enabled = false) { Text("Bientôt") }
            }
            if (tile.kind != TileKind.SOON) {
                OutlinedButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris"
                    )
                }
            }
        }
    }
}

@Composable
private fun Shelf(
    title: String,
    tiles: List<Tile>,
    selectedKey: String,
    tileWidth: Dp,
    reduce: Boolean,
    onFocus: (Tile) -> Unit,
    onClick: (Tile) -> Unit
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = HorizonOnSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(tiles, key = { it.key }) { tile ->
                ShelfTile(
                    tile = tile,
                    selected = tile.key == selectedKey,
                    width = tileWidth,
                    reduce = reduce,
                    onFocus = { onFocus(tile) },
                    onClick = { onClick(tile) }
                )
            }
        }
    }
}

@Composable
private fun ShelfTile(
    tile: Tile,
    selected: Boolean,
    width: Dp,
    reduce: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(if (reduce) 0 else 180),
        label = "tileScale"
    )
    val colors = tileColors(tile.key)
    val shape = RoundedCornerShape(18.dp)

    Box(
        Modifier
            .width(width)
            .aspectRatio(0.78f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
            .then(if (selected) Modifier.border(2.dp, HorizonBlue, shape) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable(onClick = onClick)
    ) {
        val icon = tile.icon
        val symbol = tile.symbol
        when {
            icon != null -> Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(width * 0.5f)
            )
            symbol != null -> Icon(
                symbol,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.Center).size(width * 0.4f)
            )
            else -> Text(
                tile.title.take(1).uppercase(),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (tile.kind == TileKind.SOON) {
            Text(
                "BIENTÔT",
                color = HorizonRed,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                .padding(10.dp)
        ) {
            Text(
                tile.title,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyShelf(onOpenLibrary: () -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mes jeux", style = MaterialTheme.typography.titleMedium, color = HorizonOnSurface)
        Text(
            "Aucun jeu détecté. Installe un jeu Android, ou active « Afficher toutes les applications » dans Paramètres pour tester le lancement.",
            style = MaterialTheme.typography.bodyMedium,
            color = HorizonOnSurfaceMuted
        )
        OutlinedButton(onClick = onOpenLibrary) { Text("Ouvrir la bibliothèque") }
    }
}
