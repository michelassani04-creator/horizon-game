# Déployer HORIZON gratuitement pour tester (Render + Neon)

Pour des **essais** seulement : le serveur se met en veille après 15 min sans visite (≈1 min pour se réveiller) et la base gratuite a des limites. Pour de vrais joueurs, passer à un serveur payant.
Tout se fait depuis le navigateur du téléphone.

## 1. La base PostgreSQL — Neon
1. Va sur **neon.com** → *Sign up* (compte GitHub possible).
2. *Create project* : nom `horizon`, région la plus proche (ex. Europe/Francfort).
3. Sur la page du projet, ouvre **Connect** / *Connection details* et **copie la « connection string »** (elle commence par `postgresql://…`).
   Garde-la secrète : elle contient le mot de passe de la base.

## 2. Le serveur — Render
1. Va sur **render.com** → *Get started* → connecte-toi avec GitHub et autorise l'accès au dépôt `horizon-game`.
2. **New +** → **Web Service** → choisis le dépôt `horizon-game`.
3. Renseigne :
   - **Language** : Docker
   - **Root Directory** : `backend`
   - **Instance type** : **Free**
   - **Health Check Path** (Advanced) : `/health`
4. **Environment Variables** :

   | Nom | Valeur |
   |---|---|
   | `DATABASE_URL` | la connection string de Neon |
   | `ADMIN_TOKEN` | une longue valeur aléatoire (≥ 24 caractères, voir ci-dessous) |
   | `SEED_DEMO` | `true` la première fois pour avoir 6 jeux de démonstration, puis `false` |
   | `TRUST_PROXY` | `true` |
   | `LOG_CODES` | `true` (les codes e-mail s'affichent dans les *Logs* Render) |

   Pour fabriquer `ADMIN_TOKEN` dans Termux : `tr -dc A-Za-z0-9 </dev/urandom | head -c 40; echo`
5. **Create Web Service**. Le premier démarrage compile le serveur : compte 5 à 10 minutes. Suis l'avancement dans l'onglet *Logs* ; tu dois y voir « HORIZON backend démarré ».

(Option : le fichier `render.yaml` à la racine du dépôt permet aussi un déploiement « Blueprint ». Si Render te demande une carte bancaire pour cela, utilise la méthode manuelle ci-dessus.)

## 3. Vérifier
Render affiche l'adresse du service, du type `https://horizon-backend-xxxx.onrender.com`.
```
curl https://horizon-backend-xxxx.onrender.com/health
curl https://horizon-backend-xxxx.onrender.com/v1/config
```
Le premier appel après une pause peut prendre ~1 minute.

## 4. Brancher l'application
HORIZON GAME → **Réglages › Serveur** → colle l'adresse `https://horizon-backend-xxxx.onrender.com` → *Enregistrer* → *Tester la connexion* (« Serveur joignable »). Puis **Profil › S'inscrire**.

## 5. Publier tes vrais jeux
Voir `backend/README.md` (section « Publier un jeu », en-tête `X-Admin-Token`). Mets `SEED_DEMO` à `false` et supprime les jeux de démonstration :
```
curl -X DELETE https://…/v1/admin/games/horizon-runner -H "X-Admin-Token: TON_JETON"
```
(idem pour `cite-neon`, `course-des-rapides`, `orbite-zero`, `forge-stellaire`, `tir-de-precision`).

## Sécurité
- Ne partage jamais `DATABASE_URL` ni `ADMIN_TOKEN` (ni dans le chat, ni dans GitHub). Si l'un fuit : change-le dans Neon/Render.
- Les comptes créés en test sont de vrais comptes sur cette base : n'y mets pas de données sensibles.
