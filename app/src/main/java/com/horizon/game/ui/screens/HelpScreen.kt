package com.horizon.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.horizon.game.ui.components.OsPage
import com.horizon.game.ui.components.osClickable
import com.horizon.game.ui.theme.HorizonOnSurface
import com.horizon.game.ui.theme.HorizonOnSurfaceMuted
import com.horizon.game.ui.theme.HorizonSurface

private val faq = listOf(
    "D'où viennent les jeux ?" to
        "Les jeux sont publiés sur le HORIZON Store. Tu les trouves dans l'onglet Store, puis dans ta Bibliothèque une fois installés.",
    "Pourquoi le bouton « Installer » est-il grisé ?" to
        "Le téléchargement depuis le Store arrive avec le backend (Lot 3). Les jeux affichés aujourd'hui sont des exemples fictifs.",
    "Le Cloud Gaming est-il disponible ?" to
        "Non. Le Cloud Gaming est une phase ultérieure, soumise à une étude technique, financière et juridique.",
    "Ma manette n'est pas détectée." to
        "Connecte-la d'abord en Bluetooth dans les réglages Android, puis ouvre Manette et appuie sur un bouton.",
    "Comment contacter le support ?" to
        "Le contact support et le signalement seront ajoutés avec le backend (Lot 3)."
)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    OsPage(title = "Aide", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            faq.forEach { (question, answer) -> FaqItem(question, answer) }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by rememberSaveable(question) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .osClickable(RoundedCornerShape(16.dp)) { expanded = !expanded }
            .background(HorizonSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(question, style = MaterialTheme.typography.titleMedium, color = HorizonOnSurface)
        if (expanded) {
            Text(answer, style = MaterialTheme.typography.bodyMedium, color = HorizonOnSurfaceMuted)
        }
    }
}
