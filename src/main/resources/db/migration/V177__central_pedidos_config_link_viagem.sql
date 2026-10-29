-- ECM-F013 — Central de Pedidos (fatia 2): configuração do recebimento, separação na entrega, link da
-- viagem e avisos ao cliente no app.
--
-- Decisões:
--  * A configuração (app/QR recebendo, horários, entrega, retirada, chave Pix…) é um JSON numa chave só de
--    system_config ('central-pedidos.config'): o PUT substitui tudo, e as listas de horários e bairros não
--    cabem em uma chave por campo. Sem a chave, vale o padrão (sempre aberto, entrega e retirada ligadas).
--  * O link da corrida (Uber, 99…) ganha coluna própria: tracking_code tem 60 caracteres e é o código dos
--    Correios, que continua existindo.
--  * ONLINE_CONFIG_MANAGE para admin e dev. O perfil de gerente recebe pela tela de perfis, como na V176.
--  * PEDIDO_CLIENTE (notificação ao cliente do app) não precisa de nada aqui: notification.type não tem CHECK.
ALTER TABLE order_delivery ADD COLUMN tracking_url VARCHAR(500);

COMMENT ON COLUMN order_delivery.tracking_url IS
    'Link para acompanhar a corrida (Uber, 99…), gravado na saída do pedido do app para entrega (ECM-F013).';

INSERT INTO permissions (name) VALUES ('ONLINE_CONFIG_MANAGE') ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_DEV')
  AND p.name = 'ONLINE_CONFIG_MANAGE'
ON CONFLICT DO NOTHING;
