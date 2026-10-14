-- PED-F004 — estorno por item, com o destino da mercadoria.
--
-- O operador estorna o pedido inteiro ou só algumas unidades, e escolhe para cada uma:
--   RESTOCK        — voltou inteira: ENTRADA no estoque;
--   DISCARD        — voltou estragada: perda. O saldo NÃO muda (a unidade já saiu na venda; uma
--                    SAIDA nova baixaria duas vezes) — a perda fica aqui, com o custo congelado;
--   CUSTOMER_KEEPS — só o dinheiro volta; a mercadoria fica com o cliente.
-- Append-only: a soma de quantity por item é o que já foi estornado e limita o próximo estorno.
CREATE TABLE order_item_refund (
    id             BIGSERIAL     PRIMARY KEY,
    order_id       BIGINT        NOT NULL REFERENCES sales_order (id),
    order_item_id  BIGINT        NOT NULL REFERENCES order_item (id),
    sku            VARCHAR(100)  NOT NULL,
    quantity       NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
    destination    VARCHAR(20)   NOT NULL,
    amount         NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    cost_amount    NUMERIC(14,2),
    reason         VARCHAR(255),
    session_id     BIGINT        REFERENCES cash_register_session (id),
    created_by     VARCHAR(80)   NOT NULL,
    created_at     TIMESTAMP     NOT NULL,
    CONSTRAINT ck_order_item_refund_destination CHECK (destination IN ('RESTOCK','DISCARD','CUSTOMER_KEEPS'))
);
CREATE INDEX ix_order_item_refund_order ON order_item_refund (order_id, order_item_id);

-- A sessão de caixa ONDE o estorno foi feito: é dessa gaveta que o dinheiro sai. Só nas linhas
-- REFUNDED; nula nas antigas e quando quem estornou não tinha caixa aberto — aí a soma do caixa cai
-- na sessão do pedido, como sempre foi.
ALTER TABLE order_payment ADD COLUMN refund_session_id BIGINT REFERENCES cash_register_session (id);
ALTER TABLE order_payment ADD CONSTRAINT ck_order_payment_refund_session
    CHECK (refund_session_id IS NULL OR status = 'REFUNDED');
