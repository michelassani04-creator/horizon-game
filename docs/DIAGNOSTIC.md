# HORIZON GAME — Diagnostic technique (19 nov. 2026, v0.3.0)

## 1. Ce qui existe et fonctionne
**Application Android** (Kotlin 2.0.20, Compose/Material 3, Navigation Compose, minSdk 26) — compile en CI, APK généré.
- Coque « système » : barre d'état, dock, centre de contrôle, mode immersif, paysage uniquement.
- Écrans : démarrage animé, accueil console, Store (recherche, catégories, fiche jeu), bibliothèque, profil (mode visiteur), réglages, manette (détection), aide.
- Fond animé commun (vagues + « HORIZON GAME » lumineux), réduit via « Réduire les animations ».
- Détection des jeux installés limitée aux paquets du Store (pas de `QUERY_ALL_PACKAGES`).
- Favoris / récents / réglages en SharedPreferences.

**Backend** (Kotlin, Ktor 2.3.12, PostgreSQL, Docker) — écrit, tests unitaires, CI dédiée (`build-backend.yml`).
- HORIZON ID : inscription/connexion e-mail ou téléphone, sessions révocables, PBKDF2, limitation de débit.
- Store : catalogue, détail, favoris synchronisés, publication par jeton administrateur.
- **Jamais exécuté contre une vraie base** : seule la compilation/tests unitaires sont vérifiés en CI.

## 2. Écarts avec le cahier v4.0
| Sujet | État |
|---|---|
| Données fictives dans l'app (`SampleCatalog`, 6 jeux démo) | **Présentes** → à retirer quand l'API est branchée |
| Données démo côté serveur | Isolées derrière `SEED_DEMO=true` (désactivé par défaut en production) |
| Architecture MVVM / Clean / Hilt / Room | **Absente** : état dans `AppState`, accès données direct dans les écrans |
| Client réseau | **Absent** (aucun appel API dans l'app) |
| Écrans connexion / inscription / HORIZON ID | **Absents** |
| Téléchargement / installation / mise à jour de jeux locaux | **Absent** |
| Paiements, Memory (S3), Social, Cloud Gaming, Admin web | **Absents** (aucune ligne de code) |
| Qualité graphique adaptative (haute/standard/économie) | Seulement « réduire les animations » (tout ou rien) |
| Police Inter | Police système actuellement |

## 3. Risques / erreurs identifiés
1. Aucune couche réseau → l'app ne peut pas encore être « réellement fonctionnelle ».
2. Pas de tests côté Android ; vérification par compilation CI seulement.
3. Backend non éprouvé en exécution : prévoir un test d'intégration (docker compose) avant d'y brancher l'app.
4. Jetons de session stockés plus tard dans l'app : utiliser `EncryptedSharedPreferences`/Keystore, pas des préférences en clair.
5. Téléchargement d'APK : vérifier le SHA-256 (déjà prévu côté API) et utiliser `PackageInstaller` (permission « installer des applis » à demander à l'utilisateur).

## 4. Ce qui ne peut PAS être livré par du code seul
- **HORIZON CLOUD** : exige des serveurs GPU, un moteur de streaming faible latence, et les **licences des éditeurs** (DRM, anti-triche).
- **Paiements M-Pesa / Orange / Airtel / cartes** : exigent des contrats et comptes marchands avec chaque fournisseur ; le code sera prêt, les transactions réelles non.
- **HORIZON MEMORY** : exige un stockage objet S3 déployé (ex. MinIO sur ton serveur ou un fournisseur S3) et un serveur.
- **SMS** : exige un fournisseur SMS.
- **Jeux commerciaux** : seulement avec autorisation écrite des éditeurs.
