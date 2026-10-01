-- PDV-F027 — Correção da forma de pagamento com lastro.
--
-- O operador lança PIX quando foi débito (ou dinheiro quando foi crédito) e o fechamento de caixa
-- fica errado. A correção não apaga nada: as linhas CAPTURED vigentes passam a CORRECTED (e saem de
-- toda soma de caixa, que só conta CAPTURED) e as linhas novas nascem CAPTURED.
--
-- Uma linha pode ser criada por uma correção e aposentada por outra, então são duas referências:
--   origin_correction_id — a correção que CRIOU a linha (null nas linhas da venda original)
--   correction_id        — a correção que APOSENTOU a linha (só em CORRECTED)

CREATE TABLE order_payment_correction (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT       NOT NULL REFERENCES sales_order (id),
    reason              VARCHAR(500) NOT NULL,
    corrected_by        VARCHAR(80)  NOT NULL,
    corrected_at        TIMESTAMPTZ  NOT NULL,
    cash_session_id     BIGINT       NOT NULL REFERENCES cash_register_session (id),
    session_was_closed  BOOLEAN      NOT NULL,
    CONSTRAINT ck_order_payment_correction_reason CHECK (length(trim(reason)) > 0)
);
CREATE INDEX idx_order_payment_correction_order ON order_payment_correction (order_id, id);

ALTER TABLE order_payment ADD COLUMN correction_id        BIGINT REFERENCES order_payment_correction (id);
ALTER TABLE order_payment ADD COLUMN origin_correction_id BIGINT REFERENCES order_payment_correction (id);
ALTER TABLE order_payment ADD COLUMN corrected_at         TIMESTAMPTZ;
ALTER TABLE order_payment ADD COLUMN corrected_by         VARCHAR(80);

ALTER TABLE order_payment DROP CONSTRAINT ck_order_payment_status;
ALTER TABLE order_payment ADD CONSTRAINT ck_order_payment_status
    CHECK (status IN ('PENDING','AUTHORIZED','CAPTURED','REFUNDED','FAILED','CANCELLED','CORRECTED'));

-- CORRECTED vem sempre de CAPTURED: o instante da captura original continua sendo história.
ALTER TABLE order_payment DROP CONSTRAINT ck_order_payment_captured;
ALTER TABLE order_payment ADD CONSTRAINT ck_order_payment_captured
    CHECK ((status IN ('CAPTURED','CORRECTED')) = (captured_at IS NOT NULL));

ALTER TABLE order_payment ADD CONSTRAINT ck_order_payment_corrected
    CHECK ((status = 'CORRECTED') = (correction_id IS NOT NULL AND corrected_at IS NOT NULL
                                     AND corrected_by IS NOT NULL));

-- Divergência registrada num caixa JÁ FECHADO quando a correção acontece depois do fechamento.
-- O esperado gravado no fechamento não é reescrito; o relatório soma estes deltas por método.
CREATE TABLE cash_session_adjustment (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT        NOT NULL REFERENCES cash_register_session (id),
    order_id        BIGINT        NOT NULL REFERENCES sales_order (id),
    correction_id   BIGINT        NOT NULL REFERENCES order_payment_correction (id),
    method          VARCHAR(30)   NOT NULL,
    delta_amount    NUMERIC(14,2) NOT NULL,
    created_by      VARCHAR(80)   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_cash_session_adjustment_delta CHECK (delta_amount <> 0)
);
CREATE INDEX idx_cash_session_adjustment_session ON cash_session_adjustment (session_id);

INSERT INTO permissions (name) VALUES ('ORDER_PAYMENT_CORRECT')
ON CONFLICT (name) DO NOTHING;
INSERT INTO permissions (name) VALUES ('ORDER_PAYMENT_CORRECT_CLOSED')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_ATENDENTE') AND p.name = 'ORDER_PAYMENT_CORRECT'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'ORDER_PAYMENT_CORRECT_CLOSED'
ON CONFLICT DO NOTHING;
