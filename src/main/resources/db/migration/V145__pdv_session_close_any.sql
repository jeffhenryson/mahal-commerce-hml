-- PDV-C037 — fechar o caixa de OUTRO operador deixa de ser decidido por role fixa no código
-- (PdvController.canCloseAnySession olhava ROLE_ADMIN/ROLE_DEV) e passa a ser a permissão
-- PDV_SESSION_CLOSE_ANY. Concedida às mesmas duas roles, para o comportamento de hoje não mudar; um
-- gerente pode recebê-la sem virar admin. Fechar o próprio caixa continua sendo PDV_SESSION_CLOSE.
INSERT INTO permissions (name) VALUES ('PDV_SESSION_CLOSE_ANY') ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_DEV')
  AND p.name = 'PDV_SESSION_CLOSE_ANY'
ON CONFLICT DO NOTHING;
