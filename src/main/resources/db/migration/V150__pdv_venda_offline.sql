-- PDV-F043 — venda offline no balcão, fase 1 (backend).
--
-- 1. Idempotência da venda. POST /pdv/sessions/{id}/sales criava um pedido novo a cada chamada: um
--    timeout seguido de reenvio duplicava a venda, mesmo online. Agora o caixa manda uma chave por
--    venda (UUID) e o reenvio devolve o pedido já gravado. UNIQUE simples (várias linhas NULL são
--    permitidas no Postgres) — as vendas sem chave, inclusive todo o histórico, continuam valendo.
--    client_sold_at guarda a hora em que a venda aconteceu no balcão quando ela chega pela fila
--    offline; sold_at continua sendo a gravação no servidor.
ALTER TABLE sales_order ADD COLUMN client_sale_id VARCHAR(36);
ALTER TABLE sales_order ADD COLUMN client_sold_at TIMESTAMP;
ALTER TABLE sales_order ADD CONSTRAINT uk_sales_order_client_sale_id UNIQUE (client_sale_id);

-- 2. Revisão das recusadas. Decisão do dono (2026-10-08): venda offline que não entra na
--    sincronização (falta de estoque, SKU que sumiu, sem preço) não entra sozinha nem some — fica
--    aqui, como chegou, e o caixa não fecha até ela ser reenviada (RETRIED) ou descartada com motivo
--    (DISCARDED).
CREATE TABLE offline_sale_rejection (
    id              BIGSERIAL    PRIMARY KEY,
    session_id      BIGINT       NOT NULL REFERENCES cash_register_session (id),
    client_sale_id  VARCHAR(36)  NOT NULL,
    client_sold_at  TIMESTAMP,
    customer_id     BIGINT,
    payload         TEXT         NOT NULL,
    error_code      VARCHAR(60)  NOT NULL,
    message         VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    resolved_at     TIMESTAMP,
    resolved_by     VARCHAR(100),
    resolution      VARCHAR(20),
    resolution_note VARCHAR(255),
    order_id        BIGINT,
    CONSTRAINT uk_offline_sale_rejection_client_sale_id UNIQUE (client_sale_id),
    CONSTRAINT ck_offline_sale_rejection_resolution CHECK (resolution IS NULL OR resolution IN ('RETRIED', 'DISCARDED')),
    -- Resolvida pela metade é meio estado: o domínio recusa, o banco recusa junto.
    CONSTRAINT ck_offline_sale_rejection_resolved CHECK ((resolved_at IS NULL) = (resolution IS NULL))
);

-- O que barra o fechamento do caixa: as pendentes de uma sessão.
CREATE INDEX idx_offline_sale_rejection_pending ON offline_sale_rejection (session_id) WHERE resolved_at IS NULL;

-- 3. Quem revisa: reenviar ou descartar mexe em dinheiro e estoque — decisão de gerente.
INSERT INTO permissions (name) VALUES ('PDV_OFFLINE_REVIEW') ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_DEV')
  AND p.name = 'PDV_OFFLINE_REVIEW'
ON CONFLICT DO NOTHING;
