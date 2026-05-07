CREATE TABLE app_users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name       VARCHAR(200) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'GUEST',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login      TIMESTAMPTZ,
    failed_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_app_users_email ON app_users(email);
