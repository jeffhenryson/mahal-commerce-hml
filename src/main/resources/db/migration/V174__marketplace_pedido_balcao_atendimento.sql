-- ECM-F009/F010/F011 — pedido do app pago no caixa.
--
-- Decisões:
--  * O cliente escolhe retirada ou entrega no checkout; a entrega do marketplace usa a mesma
--    order_delivery da venda de balcão (V130). A taxa nasce zero e o caixa a define no aceite.
--  * Pagamento só no caixa: retirada paga no balcão (crédito, débito, Pix, dinheiro); entrega por Pix
--    cobrado e conferido pelo operador antes de despachar. O link do gateway fica desligado por
--    configuração (shop.checkout.modo-pagamento), não removido.
--  * O atendimento (fila, aceite, separação, despacho) mora em tabela própria, ao lado do status do
--    pedido: sales_order.status continua dizendo só se foi pago.
--  * session_id daqui é o caixa que ATENDE; o que liquidou continua em sales_order.session_id.
--  * Prazo até o fim do dia: o varredor cancela o pedido não pago quando passa de prazo, e só então
--    a reserva é devolvida (ela vence um pouco depois, de propósito).

ALTER TABLE order_delivery DROP CONSTRAINT ck_order_delivery_method;
ALTER TABLE order_delivery ADD CONSTRAINT ck_order_delivery_method
    CHECK (method IS NULL OR method IN ('MOTOBOY_LOJA', 'APP_99', 'UBER_FLASH', 'CORREIOS'));

CREATE TABLE pedido_app_atendimento (
    order_id              BIGINT       PRIMARY KEY REFERENCES sales_order (id) ON DELETE CASCADE,
    status                VARCHAR(20)  NOT NULL,
    tipo                  VARCHAR(20)  NOT NULL,
    preferencia_pagamento VARCHAR(20)  NOT NULL,
    observacao            VARCHAR(500),
    prazo                 TIMESTAMPTZ  NOT NULL,
    session_id            BIGINT       REFERENCES cash_register_session (id),
    atendido_por          VARCHAR(100),
    motivo                VARCHAR(500),
    criado_em             TIMESTAMPTZ  NOT NULL,
    aceito_em             TIMESTAMPTZ,
    pronto_em             TIMESTAMPTZ,
    saiu_em               TIMESTAMPTZ,
    concluido_em          TIMESTAMPTZ,
    encerrado_em          TIMESTAMPTZ,
    CONSTRAINT ck_pedido_app_atendimento_status CHECK (status IN (
        'PENDENTE', 'ACEITO', 'PRONTO', 'SAIU_PARA_ENTREGA', 'RETIRADO', 'ENTREGUE', 'RECUSADO', 'CANCELADO',
        'EXPIRADO')),
    CONSTRAINT ck_pedido_app_atendimento_tipo CHECK (tipo IN ('RETIRADA', 'ENTREGA')),
    CONSTRAINT ck_pedido_app_atendimento_preferencia CHECK (preferencia_pagamento IN ('PIX', 'CREDITO', 'DEBITO', 'DINHEIRO')),
    -- Entrega é sempre Pix, cobrado pelo operador.
    CONSTRAINT ck_pedido_app_atendimento_entrega_pix CHECK (tipo <> 'ENTREGA' OR preferencia_pagamento = 'PIX')
);

-- A fila do PDV: o que está em andamento, do mais antigo ao mais novo.
CREATE INDEX ix_pedido_app_atendimento_status ON pedido_app_atendimento (status, criado_em);
-- A tela do caixa: os pedidos do app que ele aceitou.
CREATE INDEX ix_pedido_app_atendimento_session ON pedido_app_atendimento (session_id) WHERE session_id IS NOT NULL;
-- O varredor: em andamento com prazo vencido.
CREATE INDEX ix_pedido_app_atendimento_prazo ON pedido_app_atendimento (prazo)
    WHERE status IN ('PENDENTE', 'ACEITO', 'PRONTO');

COMMENT ON TABLE pedido_app_atendimento IS
    'Atendimento no caixa de um pedido do app (ECM-F010), 1:1 com sales_order de canal MARKETPLACE. O status do pedido diz se foi pago; este diz o que a loja já fez com ele.';
COMMENT ON COLUMN pedido_app_atendimento.session_id IS
    'Caixa que aceitou e atende o pedido — não o que liquidou (esse é sales_order.session_id).';
COMMENT ON COLUMN pedido_app_atendimento.prazo IS
    'Até quando o pedido segura o estoque sem pagamento. Passado ele, o varredor cancela e devolve a reserva.';

INSERT INTO system_config (config_key, config_value) VALUES
    ('shop.checkout.modo-pagamento', 'BALCAO'),
    ('shop.pedido.hora-limite', '22:00')
ON CONFLICT (config_key) DO NOTHING;
