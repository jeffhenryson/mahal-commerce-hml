-- CRM-F010 / PDV-F028 — "Marcar": venda a prazo para cliente VIP.
--
-- O cliente VIP leva o produto (ou consome a sessão na mesa) e paga até uma data combinada. A venda
-- conclui normalmente — a mercadoria já saiu —, a parte marcada vira uma linha de pagamento
-- ON_ACCOUNT (fora do caixa) e um recebível em customer_receivable. A quitação entra no caixa de
-- QUEM RECEBE, por receivable_payment, e não por order_payment: ela não tem pedido próprio.

-- Limite de crédito por cliente. Tabela própria, e não coluna em customers: o PUT do cadastro
-- regrava a ficha inteira, e o limite é decisão do gerente (RECEIVABLE_MANAGE), não do cadastro.
-- Sem linha = limite padrão (system_config pdv.on-account.default-credit-limit).
CREATE TABLE customer_credit_limit (
    customer_id   BIGINT        PRIMARY KEY REFERENCES customers (id) ON DELETE CASCADE,
    credit_limit  NUMERIC(14,2) NOT NULL,
    updated_by    VARCHAR(80)   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_customer_credit_limit_non_negative CHECK (credit_limit >= 0)
);

CREATE TABLE customer_receivable (
    id             BIGSERIAL PRIMARY KEY,
    customer_id    BIGINT        NOT NULL REFERENCES customers (id),
    order_id       BIGINT        NOT NULL REFERENCES sales_order (id),
    comanda_id     BIGINT        REFERENCES comanda (id),
    session_id     BIGINT        NOT NULL REFERENCES cash_register_session (id),
    amount         NUMERIC(14,2) NOT NULL,
    amount_paid    NUMERIC(14,2) NOT NULL DEFAULT 0,
    due_date       DATE          NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    created_by     VARCHAR(80)   NOT NULL,
    settled_at     TIMESTAMPTZ,
    cancel_reason  VARCHAR(500),
    cancelled_by   VARCHAR(80),
    cancelled_at   TIMESTAMPTZ,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_customer_receivable_status
        CHECK (status IN ('ABERTO','PARCIAL','QUITADO','VENCIDO','CANCELADO')),
    CONSTRAINT ck_customer_receivable_amount CHECK (amount > 0),
    CONSTRAINT ck_customer_receivable_paid CHECK (amount_paid >= 0 AND amount_paid <= amount),
    CONSTRAINT ck_customer_receivable_settled CHECK ((status = 'QUITADO') = (settled_at IS NOT NULL)),
    CONSTRAINT ck_customer_receivable_cancelled
        CHECK ((status = 'CANCELADO') = (cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL))
);
-- Um pedido gera no máximo um recebível (no máximo uma linha MARCADO por venda).
CREATE UNIQUE INDEX uk_customer_receivable_order ON customer_receivable (order_id);
CREATE INDEX idx_customer_receivable_customer_status ON customer_receivable (customer_id, status);
CREATE INDEX idx_customer_receivable_status_due ON customer_receivable (status, due_date);

-- Snapshot dos itens do pedido no momento do marcar: o pedido inteiro, mesmo quando só parte dele
-- foi marcada ("R$ 30 de R$ 80 marcados").
CREATE TABLE receivable_item (
    id             BIGSERIAL PRIMARY KEY,
    receivable_id  BIGINT         NOT NULL REFERENCES customer_receivable (id) ON DELETE CASCADE,
    order_item_id  BIGINT,
    sku            VARCHAR(80)    NOT NULL,
    product_name   VARCHAR(255),
    quantity       NUMERIC(14,3)  NOT NULL,
    subtotal       NUMERIC(14,2)  NOT NULL,
    mode           VARCHAR(20)
);
CREATE INDEX idx_receivable_item_receivable ON receivable_item (receivable_id);

-- Um recebimento no balcão que pode abater vários recebíveis. O troco é do lote, não do recebível.
CREATE TABLE receivable_payment_batch (
    id               BIGSERIAL     PRIMARY KEY,
    customer_id      BIGINT        NOT NULL REFERENCES customers (id),
    cash_session_id  BIGINT        NOT NULL REFERENCES cash_register_session (id),
    change_amount    NUMERIC(14,2) NOT NULL DEFAULT 0,
    received_by      VARCHAR(80)   NOT NULL,
    received_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_receivable_payment_batch_change CHECK (change_amount >= 0)
);
CREATE INDEX idx_receivable_payment_batch_session ON receivable_payment_batch (cash_session_id);

CREATE TABLE receivable_payment (
    id               BIGSERIAL PRIMARY KEY,
    receivable_id    BIGINT        NOT NULL REFERENCES customer_receivable (id),
    batch_id         BIGINT        NOT NULL REFERENCES receivable_payment_batch (id),
    customer_id      BIGINT        NOT NULL REFERENCES customers (id),
    amount           NUMERIC(14,2) NOT NULL,
    method           VARCHAR(30)   NOT NULL,
    installments     INTEGER,
    channel          VARCHAR(20),
    provider         VARCHAR(20),
    cash_session_id  BIGINT        NOT NULL REFERENCES cash_register_session (id),
    received_by      VARCHAR(80)   NOT NULL,
    received_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_receivable_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_receivable_payment_method CHECK (method IN ('DINHEIRO','DEBITO','CREDITO','PIX')),
    CONSTRAINT ck_receivable_payment_installments
        CHECK (installments IS NULL OR (method = 'CREDITO' AND installments BETWEEN 1 AND 24))
);
CREATE INDEX idx_receivable_payment_receivable ON receivable_payment (receivable_id);
CREATE INDEX idx_receivable_payment_session_method ON receivable_payment (cash_session_id, method);

-- A linha MARCADO do pedido: ON_ACCOUNT, com vencimento, nunca capturada.
ALTER TABLE order_payment ADD COLUMN due_date DATE;

ALTER TABLE order_payment DROP CONSTRAINT ck_order_payment_method;
ALTER TABLE order_payment ADD CONSTRAINT ck_order_payment_method
    CHECK (method IN ('DINHEIRO','DEBITO','CREDITO','PIX','GATEWAY_PIX','MARCADO'));

ALTER TABLE order_payment DROP CONSTRAINT ck_order_payment_status;
ALTER TABLE order_payment ADD CONSTRAINT ck_order_payment_status
    CHECK (status IN ('PENDING','AUTHORIZED','CAPTURED','REFUNDED','FAILED','CANCELLED','CORRECTED',
                      'ON_ACCOUNT'));

ALTER TABLE order_payment ADD CONSTRAINT ck_order_payment_on_account
    CHECK ((method = 'MARCADO') = (status = 'ON_ACCOUNT')
       AND (method = 'MARCADO') = (due_date IS NOT NULL));

INSERT INTO system_config (config_key, config_value, updated_at, updated_by) VALUES
    ('pdv.on-account.default-due-days',     '30', CURRENT_TIMESTAMP, 'system'),
    ('pdv.on-account.default-credit-limit', '0',  CURRENT_TIMESTAMP, 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO permissions (name) VALUES ('PDV_SALE_ON_ACCOUNT') ON CONFLICT (name) DO NOTHING;
INSERT INTO permissions (name) VALUES ('RECEIVABLE_READ')     ON CONFLICT (name) DO NOTHING;
INSERT INTO permissions (name) VALUES ('RECEIVABLE_MANAGE')   ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('PDV_SALE_ON_ACCOUNT', 'RECEIVABLE_READ', 'RECEIVABLE_MANAGE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ATENDENTE' AND p.name = 'RECEIVABLE_READ'
ON CONFLICT DO NOTHING;
