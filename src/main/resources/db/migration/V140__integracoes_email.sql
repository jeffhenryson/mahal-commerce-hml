-- Integrações da loja (Configurações > Dados da loja > Integrações): por ora, envio de e-mail pelo
-- Resend. A configuração mora em system_config (chaves integration.email.*, a chave da API cifrada
-- com AES-GCM); aqui só a permissão. Separada de STORE_PROFILE_MANAGE porque dá acesso a tokens.
INSERT INTO permissions (name) VALUES ('INTEGRATION_MANAGE') ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_DEV')
  AND p.name = 'INTEGRATION_MANAGE'
ON CONFLICT DO NOTHING;
