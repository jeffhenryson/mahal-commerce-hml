-- Configurações › Automações: CRUD, teste, disparo e log de /crm/automacoes passam a exigir
-- AUTOMATION_MANAGE (a automação guarda segredo de webhook e dispara mensagem para clientes).
-- A lista (GET) continua aceitando CRM_CUSTOMER_READ, por causa da Visão Geral do CRM.
INSERT INTO permissions (name) VALUES ('AUTOMATION_MANAGE') ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_DEV')
  AND p.name = 'AUTOMATION_MANAGE'
ON CONFLICT DO NOTHING;

-- Fim do auto-cadastro: só dev/admin criam usuários e o login Google não cria contas.
-- As flags deixam de existir (o front não as lê mais).
DELETE FROM system_config WHERE config_key IN ('auth.registration.enabled', 'auth.google.register.enabled');
