-- ROLE_DEV tem todas as permissões do sistema, exceto as de cliente do marketplace (SHOP_*).
-- Permissões novas vinham sendo concedidas só ao ROLE_ADMIN por migration (RECEIVABLE_* e
-- PDV_SALE_ON_ACCOUNT em V137, ORDER_PAYMENT_CORRECT* em V136, ESTOQUE_KIT_TEMPLATE_MANAGE em V126),
-- e o DEV tomava 403 em marcados e limite de crédito. O DevRoleBootstrapConfig mantém isso a cada
-- startup; esta migration corrige os ambientes já existentes e deixa o rastro no histórico.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_DEV' AND p.name NOT LIKE 'SHOP\_%'
ON CONFLICT DO NOTHING;
