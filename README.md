# HORIZON GAME : Lot 1 (socle mobile)

Application Android (Kotlin + Jetpack Compose) du cahier des charges HORIZON GAME V1 (18 nov 2026).

## Contenu du Lot 1
- Thème HORIZON (#0A0E17, #1683FF, #FF2E55, #161F30, #26354A)
- Navigation : barre inférieure (Accueil, Bibliothèque, Catalogue, Profil) + écrans Fiche jeu, Manette, Paramètres, Aide
- Accueil : Hero, Reprendre, accès rapides, et modules de la vision (Store, Game Pass, Social Hub, Game Center, HORIZON AI, HORIZON CLOUD) en « Bientôt disponible »
- Catalogue de démonstration (titres fictifs, sans visuels sous licence), recherche et filtre par catégorie
- Favoris (en mémoire pour l'instant)
- Écran Manette : détection des contrôleurs et test des boutons (F-010)
- États vide / erreur prévus, aucune permission sensible

## Pas encore fait
| Lot | Contenu |
| --- | --- |
| 2 | Détection des jeux installés, bouton « Jouer », favoris Room, manettes avancées |
| 3 | Backend (HORIZON ID, catalogue API, synchronisation) |

## Compiler l'APK avec GitHub Actions
1. Crée un dépôt GitHub et envoie le contenu de ce dossier.
2. Onglet **Actions**, workflow **Build APK** (se lance au push, ou « Run workflow »).
3. Télécharge l'artefact **HorizonGame-debug-apk** et installe `app-debug.apk`.

## Compiler en local
Android Studio : ouvrir le dossier, laisser Gradle synchroniser, lancer `app`.
En ligne de commande : `gradle :app:assembleDebug` (Gradle 8.7 ou plus, JDK 17, SDK Android 34).

Identifiant d'application : `com.horizon.game` · minSdk 26 (à confirmer après tests, cahier §5).
