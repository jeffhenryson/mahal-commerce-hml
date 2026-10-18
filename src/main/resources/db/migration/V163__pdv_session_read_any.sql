-- PDV-C047 / PDV-C030 — ler o caixa de OUTRO operador (esperado, contado, diferença, totais por forma,
-- movimentos, vendas) e os indicadores de mesas deixa de vir junto com PDV_READ, que o atendente tem
-- (V86). Decisão do dono (2026-10-17): o atendente vê só o próprio caixa; a conferência dos colegas e
-- o analytics são do gerente. Concedida a ROLE_ADMIN e ROLE_DEV, como PDV_SESSION_CLOSE_ANY (V145).
INSERT INTO permissions (name) VALUES ('PDV_SESSION_READ_ANY') ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_DEV')
  AND p.name = 'PDV_SESSION_READ_ANY'
ON CONFLICT DO NOTHING;
