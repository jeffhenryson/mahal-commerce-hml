-- Pedidos de clientes (V208): MANAGE cuida de tudo (status, vínculo de SKU, aviso); CREATE só registra
-- e junta solicitações, para o atendente no balcão. ROLE_DEV recebe tudo pelo DevRoleBootstrapConfig.
INSERT INTO permissions (name) VALUES ('ESTOQUE_PRODUCT_REQUEST_MANAGE'), ('ESTOQUE_PRODUCT_REQUEST_CREATE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_DEV')
  AND p.name IN ('ESTOQUE_PRODUCT_REQUEST_MANAGE', 'ESTOQUE_PRODUCT_REQUEST_CREATE')
ON CONFLICT DO NOTHING;

-- Quem já gerencia a lista de reposição (perfis criados pela tela, ex.: gerente) também gerencia pedidos.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions rep ON rep.id = rp.permission_id AND rep.name = 'ESTOQUE_REPLENISHMENT_MANAGE'
CROSS JOIN permissions p
WHERE p.name IN ('ESTOQUE_PRODUCT_REQUEST_MANAGE', 'ESTOQUE_PRODUCT_REQUEST_CREATE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ATENDENTE' AND p.name = 'ESTOQUE_PRODUCT_REQUEST_CREATE'
ON CONFLICT DO NOTHING;
