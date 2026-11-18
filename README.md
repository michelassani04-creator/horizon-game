# HORIZON GAME : Lot 2

Application Android (Kotlin + Jetpack Compose) du cahier des charges HORIZON GAME V1 (18 nov 2026).

## Lot 2 (version 0.2.0)
- **Splash animé** « lever d'horizon » : ligne d'horizon qui s'étire, halo bleu, logo qui se resserre, barre de progression
- **Accueil de type console** : barre haute (heure, batterie, accès manette et paramètres), héros du jeu sélectionné, étagères Reprendre / Mes jeux / À découvrir / Prochainement, tuiles qui s'agrandissent au focus
- **Mode paysage** : navigation latérale, accueil en deux colonnes (héros à gauche, étagères à droite), mode immersif (les barres système se révèlent d'un balayage)
- **Détection des jeux Android installés** (catégorie « jeu » déclarée par Android) et bouton **Jouer** qui lance l'application
- **Favoris et jeux récents** conservés sur l'appareil
- Bibliothèque et catalogue en grille adaptative
- Navigation à la manette : le D-pad déplace le focus entre les tuiles, OK ouvre ou lance
- Réglage de test « Afficher toutes les applications » pour essayer le lancement sans jeu installé

## Pas encore fait
| Lot | Contenu |
| --- | --- |
| 3 | Backend (HORIZON ID, catalogue API, synchronisation), Room |
| 4 | Sécurité, tests, performance, accessibilité |

## Compiler l'APK avec GitHub Actions
1. Envoie le contenu de ce dossier sur le dépôt GitHub (`git add . && git commit && git push`).
2. Onglet **Actions**, workflow **Build APK**.
3. Télécharge l'artefact **HorizonGame-debug-apk** et installe `app-debug.apk`.

## Compiler en local
Android Studio : ouvrir le dossier, laisser Gradle synchroniser, lancer `app`.
En ligne de commande : `gradle :app:assembleDebug` (Gradle 8.7 ou plus, JDK 17, SDK Android 34).

Identifiant d'application : `com.horizon.game` · minSdk 26 (à confirmer après tests, cahier §5).
