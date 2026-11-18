package com.horizon.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.horizon.game.data.SampleCatalog
import com.horizon.game.ui.components.ComingSoonCard
import com.horizon.game.ui.components.EmptyState
import com.horizon.game.ui.components.SectionTitle
import com.horizon.game.ui.components.heroBrush

@Composable
fun HomeScreen(
    onOpenGame: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenController: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val featured = SampleCatalog.games.first()
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Hero Screen
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(heroBrush())
                    .padding(20.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("À LA UNE", style = MaterialTheme.typography.labelLarge)
                    Text(featured.title, style = MaterialTheme.typography.headlineMedium)
                    Button(onClick = { onOpenGame(featured.id) }) { Text("Voir la fiche") }
                }
            }
        }

        // Continue Playing
        item { SectionTitle("Reprendre") }
        item {
            EmptyState(
                title = "Aucun jeu récent",
                message = "Les jeux lancés depuis HORIZON GAME apparaîtront ici (Lot 2)."
            )
        }

        // Accès rapides
        item { SectionTitle("Accès rapides") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenLibrary) { Text("Bibliothèque") }
                OutlinedButton(onClick = onOpenCatalog) { Text("Catalogue") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenController) { Text("Manette") }
                OutlinedButton(onClick = onOpenSettings) { Text("Paramètres") }
            }
        }

        // Modules de la vision, non activés en V1
        item { SectionTitle("Prochainement") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComingSoonCard("Store", "Boutique numérique", Icons.Filled.Storefront, Modifier.weight(1f))
                ComingSoonCard("Game Pass", "Catalogue par abonnement", Icons.Filled.WorkspacePremium, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComingSoonCard("Social Hub", "Amis, groupes, communautés", Icons.Filled.Groups, Modifier.weight(1f))
                ComingSoonCard("Game Center", "Statistiques et trophées", Icons.Filled.EmojiEvents, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComingSoonCard("HORIZON AI", "Assistant gaming", Icons.Filled.SmartToy, Modifier.weight(1f))
                ComingSoonCard("HORIZON CLOUD", "Jeux en streaming", Icons.Filled.Cloud, Modifier.weight(1f))
            }
        }
    }
}
