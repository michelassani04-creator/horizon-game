# HORIZON Backend (Lot 3)

API HORIZON ID + HORIZON Store. Kotlin · Ktor · PostgreSQL · Docker.

## Ce que fait l'API (préfixe `/v1`)

| Route | Rôle |
|---|---|
| `GET /health` | Vérification de fonctionnement |
| `GET /v1/config` | Version de l'API, fonctionnalités actives |
| `POST /v1/auth/register` | Créer un compte (e-mail et/ou téléphone + mot de passe + pseudo) |
| `POST /v1/auth/login` | Connexion par e-mail **ou** téléphone |
| `POST /v1/auth/logout` | Révoque la session de cet appareil |
| `GET/PATCH/DELETE /v1/me` | Profil, préférences, suppression du compte |
| `POST /v1/me/verify/request` · `/confirm` | Vérification e-mail par code à 6 chiffres |
| `GET/PUT /v1/me/favorites` · `PUT/DELETE /v1/me/favorites/{gameId}` | Favoris synchronisés |
| `GET /v1/games` (`query`, `category`, `page`, `pageSize`) · `GET /v1/games/{id}` | Catalogue du Store |
| `POST /v1/me/redeem` · `GET /v1/me/games` | Utiliser un code de téléchargement · bibliothèque du joueur (avec liens de téléchargement) |
| `GET /admin` | **Page d'administration** (publier, modifier, supprimer des jeux depuis le téléphone ; demande le `ADMIN_TOKEN`) |
| `GET /v1/admin/games` · `PUT/DELETE /v1/admin/games/{id}` · `GET /v1/admin/stats` | API d'administration (en-tête `X-Admin-Token`) |

Authentification : en-tête `Authorization: Bearer <jeton>`. Les jetons sont opaques, révocables, et seul leur hachage est stocké. Mots de passe : PBKDF2-SHA256 (120 000 itérations). Limitation de débit sur l'inscription, la connexion et les codes.

Le SMS n'est pas actif : le téléphone est enregistré, sa vérification répond « non disponible » (`501`). Tant qu'aucun service d'e-mail n'est branché, le code e-mail est écrit dans les logs du serveur (`LOG_CODES=true`).

## Lancer sur un serveur (Linux avec Docker)

```bash
git clone https://github.com/michelassani04-creator/horizon-game.git
cd horizon-game/backend
cp .env.example .env
nano .env                      # DB_PASSWORD, ADMIN_TOKEN (openssl rand -hex 24)
docker compose up -d --build
curl http://localhost:8080/health
```

Avec un nom de domaine (HTTPS automatique) : dans `.env` mettez `DOMAIN=api.exemple.com`, `TRUST_PROXY=true`, `API_BIND=127.0.0.1:8080`, pointez le DNS vers le serveur (ports 80 et 443 ouverts), puis :

```bash
docker compose --profile https up -d --build
```

## Jeux = paquets téléchargés dans l'application
Un jeu est un **paquet** (fichier de données, ex. `.pck` Godot) hébergé en HTTPS (GitHub Releases convient). Dans `/admin`, renseigne l'adresse du paquet, son **SHA-256** (obligatoire), la version et le moteur. Les liens de téléchargement ne sont **jamais** dans le catalogue public : seuls les joueurs qui possèdent le jeu les reçoivent. Dans `/admin`, le bouton **Codes** génère des codes de téléchargement à donner aux joueurs.
Limite à connaître : un lien GitHub Releases est public pour qui le connaît ; une vraie protection viendra avec le stockage S3 à liens signés (HORIZON MEMORY).

## Publier un jeu (API)

```bash
curl -X PUT https://api.exemple.com/v1/admin/games/mon-jeu \
  -H "X-Admin-Token: VOTRE_JETON" -H "Content-Type: application/json" \
  -d '{"id":"mon-jeu","title":"Mon Jeu","publisher":"Mon Studio","category":"ACTION",
       "description":"…","ageRating":"7+","sizeMb":250,"compatibility":"VERIFIED",
       "packageName":"com.monstudio.monjeu","apkUrl":"https://…/monjeu.apk",
       "apkSha256":"<empreinte sha256 de l'APK>","featured":false}'
```

Catégories : `ACTION`, `RACING`, `PUZZLE`, `ADVENTURE`, `STRATEGY`.

## Sauvegarde

```bash
docker compose exec db pg_dump -U horizon horizon > sauvegarde.sql
```

## Achats (v0.5.0)

- `POST /v1/me/orders {gameId, provider:"sandbox"}` crée une commande ; seul le serveur la passe en `paid`.
- Fournisseur `sandbox` = paiement de TEST (aucun argent réel). Confirmation depuis `/admin` (Commandes → « Simuler : payé ») ou par webhook signé HMAC-SHA256 (`X-Horizon-Signature`) si `SANDBOX_WEBHOOK_SECRET` (≥ 24 caractères) est défini.
- `POST /v1/me/games/{id}/claim` : jeux gratuits (prix 0).

## Partenaires : éditeurs et revendeurs (v0.6)

Créés dans `/admin` (section Partenaires). Chaque partenaire reçoit une clé `hzp_…` affichée une seule fois (seul son hachage est stocké) et n'agit que sur les jeux qui lui sont autorisés.

| Méthode | Chemin | Rôle |
|---|---|---|
| GET | `/v1/partner/games` | ses jeux, avec codes créés / utilisés |
| POST | `/v1/partner/codes` | crée un lot de codes (1 à 500) |

```
curl -X POST https://horizon-game.onrender.com/v1/partner/codes \
  -H "X-Partner-Key: hzp_..." -H "Content-Type: application/json" \
  -d '{"gameId":"mon-jeu","count":10,"reference":"commande-0001"}'
```

`reference` est obligatoire et unique par partenaire : si la requête est rejouée, le serveur répond 409 `duplicate_reference` au lieu de recréer des codes. Le joueur saisit le code dans l'application (« Saisir un code »).

## Portail éditeurs (v0.7)

- Page `/portal` : inscription, contrat, jeux, codes, ventes. Page `/admin` : refonte (éditeurs, validation des jeux, reversements).
- Parcours : inscription → l'admin fixe la commission → l'éditeur signe en ligne (texte exact, empreinte, date, IP et navigateur conservés dans `contract_signatures`) → l'admin valide → l'éditeur soumet ses jeux (brouillon → en validation → publié ou refusé avec motif) et crée ses codes.
- Le modèle de contrat est `src/main/resources/contract/v1.txt` (placeholders `{{COMPANY}}`, `{{COMMISSION}}`…). À faire relire par un juriste avant usage réel ; toute modification d'article doit changer `Contract.VERSION`.
- Les ventes de test (`provider = sandbox`) ne comptent jamais dans les reversements.
