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
