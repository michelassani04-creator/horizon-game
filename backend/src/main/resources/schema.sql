-- Schéma HORIZON GAME (cahier des charges §9.1). Idempotent : exécuté à chaque démarrage.

CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY,
    email           TEXT UNIQUE,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    phone           TEXT UNIQUE,
    phone_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    display_name    TEXT NOT NULL,
    avatar_url      TEXT,
    password_hash   TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'active',
    language        TEXT NOT NULL DEFAULT 'fr',
    theme           TEXT NOT NULL DEFAULT 'dark',
    notifications   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_contact_check CHECK (email IS NOT NULL OR phone IS NOT NULL)
);

-- Sessions révocables : seul le hachage du jeton est conservé.
CREATE TABLE IF NOT EXISTS device_sessions (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash    TEXT NOT NULL UNIQUE,
    device_label  TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked       BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON device_sessions(user_id);

CREATE TABLE IF NOT EXISTS verification_codes (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel     TEXT NOT NULL,
    code_hash   TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    INT NOT NULL DEFAULT 0,
    used        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_codes_user ON verification_codes(user_id, channel);

-- Jeux publiés sur le HORIZON Store.
CREATE TABLE IF NOT EXISTS games (
    id             TEXT PRIMARY KEY,
    title          TEXT NOT NULL,
    publisher      TEXT NOT NULL,
    category       TEXT NOT NULL,
    description    TEXT NOT NULL,
    age_rating     TEXT NOT NULL,
    size_mb        INT NOT NULL,
    compatibility  TEXT NOT NULL,
    package_name   TEXT,
    apk_url        TEXT,
    apk_sha256     TEXT,
    featured       BOOLEAN NOT NULL DEFAULT FALSE,
    published      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_favorites (
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    game_id     TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, game_id)
);

-- Paquets de jeu (v0.4) : le jeu est un fichier de données lancé par HORIZON GAME.
ALTER TABLE games ADD COLUMN IF NOT EXISTS pack_url TEXT;
ALTER TABLE games ADD COLUMN IF NOT EXISTS pack_sha256 TEXT;
ALTER TABLE games ADD COLUMN IF NOT EXISTS version_code INT NOT NULL DEFAULT 1;
ALTER TABLE games ADD COLUMN IF NOT EXISTS version_name TEXT NOT NULL DEFAULT '1.0';
ALTER TABLE games ADD COLUMN IF NOT EXISTS engine TEXT;
ALTER TABLE games ADD COLUMN IF NOT EXISTS engine_version TEXT;

-- Codes de téléchargement : seul le hachage du code est conservé.
CREATE TABLE IF NOT EXISTS redeem_codes (
    id           UUID PRIMARY KEY,
    code_hash    TEXT NOT NULL UNIQUE,
    game_id      TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    expires_at   TIMESTAMPTZ,
    redeemed_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    redeemed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_codes_game ON redeem_codes(game_id);

-- Jeux possédés par chaque joueur (bibliothèque).
CREATE TABLE IF NOT EXISTS user_games (
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    game_id      TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    acquired_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    source       TEXT NOT NULL DEFAULT 'code',
    PRIMARY KEY (user_id, game_id)
);

-- Prix et commandes (v0.5). Prix en centimes ; NULL = pas en vente (code seulement), 0 = gratuit.
ALTER TABLE games ADD COLUMN IF NOT EXISTS price_cents INT;
ALTER TABLE games ADD COLUMN IF NOT EXISTS currency TEXT NOT NULL DEFAULT 'USD';

-- L'historique des commandes est conservé même si le jeu ou le compte est supprimé (comptabilité).
CREATE TABLE IF NOT EXISTS orders (
    id            UUID PRIMARY KEY,
    user_id       UUID REFERENCES users(id) ON DELETE SET NULL,
    game_id       TEXT NOT NULL,
    game_title    TEXT NOT NULL,
    amount_cents  INT NOT NULL,
    currency      TEXT NOT NULL,
    provider      TEXT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'pending',
    provider_ref  TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at       TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_orders_user ON orders(user_id, created_at DESC);

-- Partenaires (v0.6) : éditeurs de jeux et revendeurs. Seul le hachage de la clé d'accès est conservé.
CREATE TABLE IF NOT EXISTS partners (
    id          UUID PRIMARY KEY,
    name        TEXT NOT NULL,
    kind        TEXT NOT NULL,
    key_hash    TEXT NOT NULL UNIQUE,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS partner_games (
    partner_id  UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    game_id     TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    PRIMARY KEY (partner_id, game_id)
);
ALTER TABLE redeem_codes ADD COLUMN IF NOT EXISTS partner_id UUID REFERENCES partners(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_codes_partner ON redeem_codes(partner_id);
-- Un lot = une référence unique par partenaire : empêche de créer deux fois les mêmes codes si une requête est rejouée.
CREATE TABLE IF NOT EXISTS code_batches (
    partner_id  UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    reference   TEXT NOT NULL,
    game_id     TEXT NOT NULL,
    quantity    INT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (partner_id, reference)
);

-- Portail éditeurs (v0.7) : comptes avec e-mail, contrat signé en ligne, validation des jeux, reversements.
ALTER TABLE partners ALTER COLUMN key_hash DROP NOT NULL;
ALTER TABLE partners ADD COLUMN IF NOT EXISTS email TEXT;
ALTER TABLE partners ADD COLUMN IF NOT EXISTS password_hash TEXT;
ALTER TABLE partners ADD COLUMN IF NOT EXISTS contact_name TEXT;
ALTER TABLE partners ADD COLUMN IF NOT EXISTS phone TEXT;
ALTER TABLE partners ADD COLUMN IF NOT EXISTS country TEXT;
-- registered → contract_ready → signed → active (ou suspended). Les partenaires à clé API existants restent « active ».
ALTER TABLE partners ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'active';
ALTER TABLE partners ADD COLUMN IF NOT EXISTS commission_bp INT;
ALTER TABLE partners ADD COLUMN IF NOT EXISTS contract_version TEXT;
ALTER TABLE partners ADD COLUMN IF NOT EXISTS contract_signed_name TEXT;
ALTER TABLE partners ADD COLUMN IF NOT EXISTS contract_signed_at TIMESTAMPTZ;
ALTER TABLE partners ADD COLUMN IF NOT EXISTS contract_validated_at TIMESTAMPTZ;
ALTER TABLE partners ADD COLUMN IF NOT EXISTS contract_note TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS idx_partners_email ON partners (lower(email)) WHERE email IS NOT NULL;

CREATE TABLE IF NOT EXISTS publisher_sessions (
    token_hash  TEXT PRIMARY KEY,
    partner_id  UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Preuve de signature : texte exact signé, son empreinte, la date, l'adresse IP et le navigateur.
CREATE TABLE IF NOT EXISTS contract_signatures (
    id           UUID PRIMARY KEY,
    partner_id   UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    version      TEXT NOT NULL,
    text_sha256  TEXT NOT NULL,
    contract_text TEXT NOT NULL,
    commission_bp INT NOT NULL,
    signed_name  TEXT NOT NULL,
    signed_ip    TEXT,
    signed_agent TEXT,
    signed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE games ADD COLUMN IF NOT EXISTS partner_id UUID REFERENCES partners(id) ON DELETE SET NULL;
ALTER TABLE games ADD COLUMN IF NOT EXISTS review_status TEXT NOT NULL DEFAULT 'approved';
ALTER TABLE games ADD COLUMN IF NOT EXISTS review_note TEXT;
CREATE INDEX IF NOT EXISTS idx_games_partner ON games(partner_id);

CREATE TABLE IF NOT EXISTS payouts (
    id           UUID PRIMARY KEY,
    partner_id   UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    amount_cents BIGINT NOT NULL,
    currency     TEXT NOT NULL,
    note         TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
