-- Extensão necessária para a EXCLUDE gist de reservas (versionada aqui pelo Flyway).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE app_user (
    id            uuid        PRIMARY KEY,
    email         varchar     NOT NULL UNIQUE,
    password_hash varchar     NOT NULL,
    role          varchar     NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);
