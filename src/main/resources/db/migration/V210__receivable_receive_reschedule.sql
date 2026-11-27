-- CRM-F013 — "Marcados" no PDV para o atendente: receber e renegociar pelo cliente, sem a lista
-- geral de devedores nem os totais da loja.
--
--  * RECEIVABLE_RECEIVE    — marcados de UM cliente (GET /crm/customers/{id}/receivables), o detalhe
--                            (GET /receivables/{id}) e o recebimento (POST /receivables/payments).
--  * RECEIVABLE_RESCHEDULE — trocar o vencimento (PATCH /receivables/{id}/due-date).
--
-- RECEIVABLE_READ continua sendo a lista geral e o resumo; RECEIVABLE_MANAGE, cancelar e o limite.
-- ROLE_DEV recebe as duas pelo DevRoleBootstrapConfig (e pela regra de READ/MANAGE abaixo).

INSERT INTO permissions (name) VALUES ('RECEIVABLE_RECEIVE'), ('RECEIVABLE_RESCHEDULE')
ON CONFLICT (name) DO NOTHING;

-- Atendente, admin e dev, explicitamente.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('ROLE_ATENDENTE', 'ROLE_ADMIN', 'ROLE_DEV')
  AND p.name IN ('RECEIVABLE_RECEIVE', 'RECEIVABLE_RESCHEDULE')
ON CONFLICT DO NOTHING;

-- Todo perfil que já lia ou gerenciava marcados (perfis criados pela tela, ex.: gerente) recebe as duas.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions src ON src.id = rp.permission_id AND src.name IN ('RECEIVABLE_READ', 'RECEIVABLE_MANAGE')
CROSS JOIN permissions p
WHERE p.name IN ('RECEIVABLE_RECEIVE', 'RECEIVABLE_RESCHEDULE')
ON CONFLICT DO NOTHING;

-- Até aqui, POST /receivables/payments exigia PDV_SALE_MANAGE. Quem já recebia por ela continua
-- recebendo (só o RECEIVE, sem renegociar), e o admin passa a poder desligar por perfil.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions src ON src.id = rp.permission_id AND src.name = 'PDV_SALE_MANAGE'
CROSS JOIN permissions p
WHERE p.name = 'RECEIVABLE_RECEIVE'
ON CONFLICT DO NOTHING;

-- Decisão do dono: o atendente não vê a lista de devedores nem os totais. A V137 tinha dado a ele
-- RECEIVABLE_READ; ele fica com RECEIVE + RESCHEDULE. A pré-checagem do Marcar
-- (on-account-eligibility, por PDV_SALE_MANAGE) e o extrato do caixa (por PDV_READ) não dependem dela.
DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE name = 'ROLE_ATENDENTE')
  AND permission_id = (SELECT id FROM permissions WHERE name = 'RECEIVABLE_READ');
