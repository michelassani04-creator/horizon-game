package com.horizon.game.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf

/**
 * État d'application du Lot 1, en mémoire uniquement.
 * Les favoris seront persistés (Room) au Lot 2 puis synchronisés au Lot 3.
 */
class AppState {
    val favorites: SnapshotStateList<String> = mutableStateListOf()
    var notificationsEnabled = mutableStateOf(true)
    var reduceAnimations = mutableStateOf(false)

    fun isFavorite(gameId: String): Boolean = favorites.contains(gameId)

    fun toggleFavorite(gameId: String) {
        if (!favorites.remove(gameId)) favorites.add(gameId)
    }
}
