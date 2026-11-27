-- Pedidos de clientes: produtos que a loja NÃO vende e que clientes pediram no balcão
-- (frontend-admin/Docs/PROMPT_BACKEND_PEDIDOS_CLIENTES.md). Um item (o produto solicitado, com status)
-- agrupa N solicitações, uma por cliente, e guarda o histórico em product_request_events.
--
-- Divergências do prompt:
--  * Sem pg_trgm/unaccent: em dev o Flyway está desligado (ddl-auto), então a similaridade e a
--    normalização de normalized_key rodam no Java (ProductRequestKeys). Os itens abertos são poucos.
--  * A categoria é product_category (não "categories").
--  * payload é TEXT com JSON (mesma convenção de campaign_automation.webhook_headers).
--  * linked_sku sem FK, mesma convenção do resto do estoque (EST-C011).
--  * customer_id: a exclusão de cliente (V206) só anonimiza, então a FK não atrapalha a LGPD.

CREATE TABLE product_requests (
    id               BIGSERIAL    PRIMARY KEY,
    description      VARCHAR(160) NOT NULL,
    brand            VARCHAR(80),
    variation        VARCHAR(80),
    category_id      BIGINT       REFERENCES product_category (id),
    category_other   VARCHAR(80),
    normalized_key   VARCHAR(400) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    linked_sku       VARCHAR(100),
    expected_at      DATE,
    supplier_note    VARCHAR(500),
    created_by       VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at        TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_product_requests_status CHECK (status IN ('PENDING', 'ORDERED', 'ARRIVED', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_product_requests_category CHECK (category_id IS NULL OR category_other IS NULL)
);
CREATE INDEX ix_product_requests_status ON product_requests (status);
CREATE INDEX ix_product_requests_linked_sku ON product_requests (linked_sku);

CREATE TABLE product_request_entries (
    id               BIGSERIAL     PRIMARY KEY,
    request_id       BIGINT        NOT NULL REFERENCES product_requests (id) ON DELETE CASCADE,
    customer_id      BIGINT        REFERENCES customers (id),
    customer_name    VARCHAR(120),
    customer_phone   VARCHAR(20),
    quantity         INT           NOT NULL DEFAULT 1,
    max_price        NUMERIC(12, 2),
    urgency          VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM',
    needed_by        DATE,
    note             VARCHAR(500),
    status           VARCHAR(12)   NOT NULL DEFAULT 'ACTIVE',
    notified_at      TIMESTAMPTZ,
    notified_channel VARCHAR(12),
    created_by       VARCHAR(255)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_pre_customer CHECK (customer_id IS NOT NULL OR (customer_name IS NOT NULL AND customer_phone IS NOT NULL)),
    CONSTRAINT ck_pre_quantity CHECK (quantity >= 1),
    CONSTRAINT ck_pre_max_price CHECK (max_price IS NULL OR max_price >= 0),
    CONSTRAINT ck_pre_urgency CHECK (urgency IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_pre_status CHECK (status IN ('ACTIVE', 'CANCELLED', 'FULFILLED')),
    CONSTRAINT ck_pre_channel CHECK (notified_channel IS NULL OR notified_channel IN ('AUTOMATION', 'MANUAL'))
);
CREATE INDEX ix_pre_request ON product_request_entries (request_id);
CREATE INDEX ix_pre_customer ON product_request_entries (customer_id);

CREATE TABLE product_request_events (
    id           BIGSERIAL    PRIMARY KEY,
    request_id   BIGINT       NOT NULL REFERENCES product_requests (id) ON DELETE CASCADE,
    entry_id     BIGINT       REFERENCES product_request_entries (id) ON DELETE SET NULL,
    type         VARCHAR(30)  NOT NULL,
    from_status  VARCHAR(20),
    to_status    VARCHAR(20),
    payload      TEXT,
    actor        VARCHAR(255) NOT NULL,
    at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_pr_events_request ON product_request_events (request_id, at);
