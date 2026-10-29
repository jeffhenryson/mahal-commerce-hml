-- ECM-F012 — Central de Pedidos (fatia 1): aceite/recusa pelo /orders, permissões e SSE.
--
-- Decisões:
--  * O aceite e a recusa continuam no atendimento do ECM-F010 (pedido_app_atendimento): /orders/{id}/accept
--    e /reject são fachadas dele, para não haver duas noções de "aceito" para o mesmo pedido.
--  * A recusa ganha um código padronizado (motivo_codigo), lido pelo indicador de recusas. O texto livre
--    continua em motivo, que é o que o cliente vê.
--  * ONLINE_ORDER_READ / ONLINE_ORDER_MANAGE para admin, dev e atendente. Não existe ROLE_GERENTE seedado:
--    o perfil de gerente recebe as permissões pela tela de perfis.
ALTER TABLE pedido_app_atendimento ADD COLUMN motivo_codigo VARCHAR(30);
ALTER TABLE pedido_app_atendimento ADD CONSTRAINT ck_pedido_app_atendimento_motivo_codigo CHECK (
    motivo_codigo IS NULL OR motivo_codigo IN (
        'SEM_ESTOQUE', 'FORA_DO_HORARIO', 'FORA_DA_AREA', 'LOJA_SOBRECARREGADA', 'OUTRO'));

COMMENT ON COLUMN pedido_app_atendimento.motivo_codigo IS
    'Código da recusa pela loja (ECM-F012). Nulo nas recusas anteriores e nos encerramentos que não são recusa.';

INSERT INTO permissions (name) VALUES ('ONLINE_ORDER_READ'), ('ONLINE_ORDER_MANAGE') ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_DEV', 'ROLE_ATENDENTE')
  AND p.name IN ('ONLINE_ORDER_READ', 'ONLINE_ORDER_MANAGE')
ON CONFLICT DO NOTHING;
