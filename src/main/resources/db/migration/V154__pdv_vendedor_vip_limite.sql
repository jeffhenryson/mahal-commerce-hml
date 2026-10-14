-- PDV-F046 — o vendedor (ROLE_ATENDENTE) marca o cliente VIP e dá limite de "Marcar" até um teto.
--
-- CRM_CUSTOMER_VIP_MANAGE  — associar/remover (e criar, na 1ª vez) só a tag VIP. Outras tags seguem
--                            exigindo CRM_CUSTOMER_MANAGE (403 TAG_NOT_ALLOWED).
-- RECEIVABLE_LIMIT_SET_CAPPED — PUT /crm/customers/{id}/credit-limit até o teto
--                            pdv.on-account.attendant-max-credit-limit (422 CREDIT_LIMIT_ABOVE_CAP).
--                            Quem tem RECEIVABLE_MANAGE continua sem teto.
-- ORDER_REFUND_OWN_SESSION — PED-F004: estornar só pedidos da própria sessão de caixa aberta. Criada
--                            aqui junto das outras concessões ao vendedor.
-- PDV_SALE_ON_ACCOUNT      — já existia (V137); o vendedor passa a vender no Marcar.
INSERT INTO permissions (name) VALUES ('CRM_CUSTOMER_VIP_MANAGE')     ON CONFLICT (name) DO NOTHING;
INSERT INTO permissions (name) VALUES ('RECEIVABLE_LIMIT_SET_CAPPED') ON CONFLICT (name) DO NOTHING;
INSERT INTO permissions (name) VALUES ('ORDER_REFUND_OWN_SESSION')    ON CONFLICT (name) DO NOTHING;
INSERT INTO permissions (name) VALUES ('PDV_SALE_ON_ACCOUNT')         ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_DEV', 'ROLE_ATENDENTE')
  AND p.name IN ('CRM_CUSTOMER_VIP_MANAGE', 'RECEIVABLE_LIMIT_SET_CAPPED')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ATENDENTE'
  AND p.name IN ('PDV_SALE_ON_ACCOUNT', 'ORDER_REFUND_OWN_SESSION')
ON CONFLICT DO NOTHING;

INSERT INTO system_config (config_key, config_value, updated_at, updated_by) VALUES
    ('pdv.on-account.attendant-max-credit-limit', '100.00', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (config_key) DO NOTHING;
