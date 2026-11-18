package com.horizon.game.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val faq = listOf(
    "Quels jeux puis-je lancer ?" to
        "HORIZON GAME V1 est un lanceur de jeux Android : il ouvre les applications installées sur ton appareil et compatibles. " +
        "Il ne lance pas de jeux PC ou console.",
    "Le Cloud Gaming est-il disponible ?" to
        "Non. Le Cloud Gaming est une phase ultérieure, soumise à une étude technique, financière et juridique.",
    "Pourquoi le bouton « Jouer » est-il grisé ?" to
        "Il s'active uniquement quand le jeu est détecté comme installé et lançable. La détection arrive au Lot 2.",
    "Ma manette n'est pas détectée." to
        "Connecte-la d'abord en Bluetooth dans les réglages Android, puis ouvre l'écran Manette et appuie sur un bouton.",
    "Comment contacter le support ?" to
        "Le contact support et le signalement seront ajoutés avec le backend (Lot 3)."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Aide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            faq.forEach { (question, answer) -> FaqItem(question, answer) }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by rememberSaveable(question) { mutableStateOf(false) }
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(question, style = MaterialTheme.typography.titleMedium)
            if (expanded) {
                Text(answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
