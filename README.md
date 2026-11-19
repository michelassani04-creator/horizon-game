# HORIZON GAME : Lot 2 (version 0.2.1)

Application Android (Kotlin + Jetpack Compose) du cahier des charges HORIZON GAME V1 (18 nov 2026).

## Principes
- **Paysage uniquement** (`sensorLandscape`), mode immersif.
- **Les jeux viennent du HORIZON Store.** La bibliothèque ne contient que les jeux du Store installés sur l'appareil. L'application ne lit, ne liste et ne conserve aucune autre application.

## Interface de type système
- **Splash animé** « lever d'horizon »
- **Barre d'état** : logo, section courante, heure, batterie, centre de contrôle
- **Dock latéral** : Accueil, Store, Jeux, Profil, Réglages
- **Centre de contrôle** (panneau à droite) : notifications, animations, raccourcis
- **Accueil de console** : héros du jeu sélectionné à gauche, étagères de tuiles à droite
- **Store** : bannière à la une, recherche, catégories, grille
- **Bibliothèque** : jeux du Store installés + favoris
- **Fiche jeu**, **Profil** (visiteur), **Réglages à deux volets**, **Manette** (test des boutons), **Aide**
- Navigation à la manette : le D-pad déplace le focus (anneau bleu), OK valide

## Pour tester « Installé » et « Jouer »
Dans `app/src/main/java/com/horizon/game/data/SampleCatalog.kt`, remplace le `packageName` d'une entrée par celui d'une application réellement installée sur ton téléphone. Elle apparaîtra comme installée et le bouton Jouer la lancera.

## Pas encore fait
| Lot | Contenu |
| --- | --- |
| 3 | Backend (HORIZON ID, catalogue API, publication des jeux, téléchargement et installation depuis le Store, synchronisation), Room |
| 4 | Sécurité, tests, performance, accessibilité |

## Compiler l'APK avec GitHub Actions
1. Envoie le contenu de ce dossier sur le dépôt GitHub (`git add . && git commit && git push`).
2. Onglet **Actions**, workflow **Build APK**.
3. Télécharge l'artefact **HorizonGame-debug-apk** et installe `app-debug.apk`.

## Compiler en local
Android Studio : ouvrir le dossier, laisser Gradle synchroniser, lancer `app`.
En ligne de commande : `gradle :app:assembleDebug` (Gradle 8.7 ou plus, JDK 17, SDK Android 34).

Identifiant d'application : `com.horizon.game` · minSdk 26 (à confirmer après tests, cahier §5).
