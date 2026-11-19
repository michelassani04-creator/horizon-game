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
| `GET /v1/admin/games` · `PUT/DELETE /v1/admin/games/{id}` | Publication de jeux (en-tête `X-Admin-Token`) |

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

## Publier un jeu

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
