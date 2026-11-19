# HORIZON GAME — Plan de développement (migration progressive)

Principe : ne rien casser. Chaque étape compile en CI, est livrée avec ses fichiers complets, ses tests et sa doc. Ce qui n'est qu'une interface est signalé comme tel dans l'app (« Bientôt disponible »), jamais simulé.

## Architecture cible Android (introduite progressivement)
```
data/      remote (client Ktor + DTO) · local (Room) · repository (implémentations)
domain/    modèles · interfaces de dépôts · cas d'usage
ui/        écrans Compose + ViewModel (MVVM) · composants · thème
di/        injection (Hilt via KSP, ajoutée à l'étape 1.3)
```
Les écrans actuels sont conservés ; on remplace leur source de données (AppState → ViewModel + dépôt), écran par écran.

## PHASE 1 — Android (en cours : v0.3.0 fait le fond animé)
- 1.1 ✅ (v0.3.1) Couche réseau (HttpURLConnection + org.json, sans dépendance ; Ktor Client plus tard si besoin), `BASE_URL` configurable, gestion hors-ligne/erreurs.
- 1.2 ✅ (v0.3.1) HORIZON ID : intro, inscription, connexion, jeton chiffré (Keystore), session, déconnexion.
- 1.3 ViewModels + Hilt ; `AppState` réduit aux préférences locales.
- 1.4 ✅ (v0.3.2) Catalogue depuis l'API ; **suppression de `SampleCatalog`** (états vides propres si serveur absent).
- 1.5 Qualité graphique : Haute / Standard / Économie (nombre de particules, vagues, FPS).
- 1.6 Écrans restants : recherche dédiée, notifications, sécurité, centre d'assistance, téléchargements.

## PHASE 2 — HORIZON ID + backend
- Déployer le backend (Docker) sur ton serveur, test d'intégration réel.
- Profil complet : photo (via Memory plus tard), ID gamer unique, appareils connectés, confidentialité.
- Bibliothèque serveur (jeux possédés), statistiques, succès (modèle + API).
- Rôles/permissions administrateur, journal d'audit.

## PHASE 3 — Store + Payments
- Éditeurs, versions de jeux, produits, prix, promotions, avis, soumission par développeurs + validation HORIZON.
- Commandes + **moteur de paiement abstrait** (interface `PaymentProvider`), fournisseur « sandbox » de test, webhooks authentifiés, confirmation **uniquement côté serveur**.
- Installation/désinstallation/mise à jour des jeux locaux (PackageInstaller + SHA-256).
- Adaptateurs M-Pesa / Orange / Airtel / cartes : écrits quand tu auras les comptes marchands et la doc de test de chaque fournisseur.

## PHASE 4 — HORIZON MEMORY
- Stockage S3 (MinIO sur ton serveur ou fournisseur S3), URLs pré-signées, quotas par offre, dossiers, renommage, suppression, restauration, sync des sauvegardes. Offres 20 Go / 100 Go / 500 Go / 1 To = **à configurer**, pas des offres actives.

## PHASE 5 — HORIZON SOCIAL
- Amis, invitations, profils publics, présence, notifications, groupes, classements, messages privés, blocage/signalement.

## PHASE 6 — HORIZON CLOUD (projet d'infrastructure indépendant)
- Étude GPU, orchestrateur de sessions, streaming faible latence (WebRTC), entrées manette/tactile, tests latence. **Bloqué tant que : serveurs GPU + licences éditeurs.** L'app n'aura pas d'écran « fictif » : le bouton n'apparaît que pour un jeu réellement activé.

## Transversal : tableau de bord admin
Application web séparée (même API, rôles) : utilisateurs, jeux, validation, prix, commandes, paiements, quotas, signalements, stats. Démarre en Phase 3.

## Livrables à chaque étape
Code complet + arborescence, config, migrations SQL, doc, instructions de compilation, résultats de tests (CI), APK de test (artefact GitHub Actions).
