-- Guarda apenas o SHA-256 do refresh token; o valor em texto nunca é persistido.
CREATE TABLE refresh_token (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES app_user (id),
    token_hash varchar     NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);
