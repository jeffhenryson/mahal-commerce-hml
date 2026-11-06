-- Ações administrativas sobre usuários e pendências de login (senha temporária e 2FA exigido).
ALTER TABLE users ADD COLUMN full_name VARCHAR(160);
ALTER TABLE users ADD COLUMN phone VARCHAR(32);
ALTER TABLE users ADD COLUMN two_factor_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN last_login_ip VARCHAR(64);

-- Token de uso restrito: só serve para concluir a pendência, nunca dá acesso a outra rota.
CREATE TABLE pending_login_tokens (
    id         BIGSERIAL   PRIMARY KEY,
    username   VARCHAR(80) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    purpose    VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    used_at    TIMESTAMPTZ,
    CONSTRAINT uk_pending_login_token UNIQUE (token_hash)
);

CREATE INDEX idx_pending_login_expires ON pending_login_tokens (expires_at);

INSERT INTO permissions (name) VALUES ('USER_SECURITY_MANAGE') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_DEV') AND p.name = 'USER_SECURITY_MANAGE'
ON CONFLICT DO NOTHING;
