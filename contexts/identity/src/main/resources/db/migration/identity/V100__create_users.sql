CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    display_name  VARCHAR(80)  NOT NULL,
    roles         TEXT[]       NOT NULL DEFAULT ARRAY['OBSERVER'],
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_users_email ON identity.users (email);
