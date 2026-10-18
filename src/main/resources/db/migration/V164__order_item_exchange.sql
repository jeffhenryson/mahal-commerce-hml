-- PED-F006 — troca de um item do pedido por outro produto, no mesmo pedido e com histórico.
--
-- A troca é um estorno por item (PED-F004) mais uma venda, amarrados:
--   * a perna devolvida é uma linha de order_item_refund (RESTOCK dá ENTRADA; DISCARD é perda, o saldo
--     não muda) marcada com exchange_id — o valor dela não volta em dinheiro, vira crédito no item novo;
--   * o item entregue entra no pedido como order_item novo (SAIDA no depósito do pedido);
--   * só a diferença move dinheiro: CAPTURED (cliente paga) ou REFUNDED (cliente recebe).
CREATE TABLE order_item_exchange (
    id                      BIGSERIAL     PRIMARY KEY,
    order_id                BIGINT        NOT NULL REFERENCES sales_order (id),
    returned_order_item_id  BIGINT        NOT NULL REFERENCES order_item (id),
    sku                     VARCHAR(100)  NOT NULL,
    quantity                NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
    destination             VARCHAR(20)   NOT NULL,
    new_order_item_id       BIGINT        NOT NULL REFERENCES order_item (id),
    new_sku                 VARCHAR(100)  NOT NULL,
    new_quantity            NUMERIC(14,3) NOT NULL CHECK (new_quantity > 0),
    returned_amount         NUMERIC(14,2) NOT NULL CHECK (returned_amount >= 0),
    new_amount              NUMERIC(14,2) NOT NULL CHECK (new_amount >= 0),
    difference              NUMERIC(14,2) NOT NULL,
    settlement_method       VARCHAR(20),
    payment_id              BIGINT        REFERENCES order_payment (id),
    session_id              BIGINT        REFERENCES cash_register_session (id),
    reason                  VARCHAR(255)  NOT NULL,
    created_by              VARCHAR(80)   NOT NULL,
    created_at              TIMESTAMP     NOT NULL,
    CONSTRAINT ck_order_item_exchange_destination CHECK (destination IN ('RESTOCK','DISCARD')),
    CONSTRAINT ck_order_item_exchange_difference CHECK (difference = new_amount - returned_amount),
    CONSTRAINT ck_order_item_exchange_settlement CHECK (difference = 0 OR settlement_method IS NOT NULL)
);
CREATE INDEX ix_order_item_exchange_order ON order_item_exchange (order_id);

ALTER TABLE order_item_refund ADD COLUMN exchange_id BIGINT REFERENCES order_item_exchange (id);

-- A diferença paga numa troca conta no caixa de quem fez a troca, não no do pedido (que pode estar
-- fechado há dias). Só em CAPTURED — e na CORRECTED, que nasce dela. Mesma divisão do estorno
-- (refund_session_id, V156/V162): as somas do caixa leem as duas metades por índice.
ALTER TABLE order_payment ADD COLUMN capture_session_id BIGINT REFERENCES cash_register_session (id);
ALTER TABLE order_payment ADD CONSTRAINT ck_order_payment_capture_session
    CHECK (capture_session_id IS NULL OR status IN ('CAPTURED', 'CORRECTED'));
CREATE INDEX IF NOT EXISTS idx_order_payment_capture_session
    ON order_payment (capture_session_id, method)
    WHERE status = 'CAPTURED';
