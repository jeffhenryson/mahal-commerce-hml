-- CRM › Clientes: cidade, avatar e segmento real (pendências 7 e 8 do admin).

-- Cidade (filtro e coluna da lista) e foto do cliente (antes no localStorage de cada navegador).
ALTER TABLE customers
    ADD COLUMN cidade                 VARCHAR(100),
    ADD COLUMN avatar_url             VARCHAR(500),
    -- Segmento materializado: Novo, Recorrente, VIP, Em Risco ou Inativo. É gravado (e não calculado
    -- na leitura) para a lista poder filtrar e paginar por ele no servidor. Recalculado pelo job
    -- diário (CustomerSegmentJob) e ao mexer na tag VIP. segmento_manual = true congela o valor
    -- escolhido pelo operador (PATCH /crm/customers/{id}/segmento): o job não sobrescreve.
    ADD COLUMN segmento               VARCHAR(20) NOT NULL DEFAULT 'Novo',
    ADD COLUMN segmento_manual        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN segmento_calculado_em  TIMESTAMP,
    ADD CONSTRAINT ck_customers_segmento CHECK (segmento IN ('Novo','Recorrente','VIP','Em Risco','Inativo'));

CREATE INDEX idx_customers_segmento ON customers (segmento);
CREATE INDEX idx_customers_origem ON customers (origem);
CREATE INDEX idx_customers_cidade ON customers (cidade);
-- O cálculo do segmento e do LTV agrega pedidos por cliente.
CREATE INDEX IF NOT EXISTS idx_sales_order_customer_status ON sales_order (customer_id, status);

-- Mesma regra do job (CustomerSegmentRule), para a lista já nascer com o segmento certo.
UPDATE customers c SET
    segmento = CASE
        WHEN EXISTS (SELECT 1 FROM customer_tags ct JOIN tags t ON t.id = ct.tag_id
                      WHERE ct.customer_id = c.id AND UPPER(t.nome) = 'VIP') THEN 'VIP'
        WHEN (SELECT MAX(o.sold_at) FROM sales_order o
               WHERE o.customer_id = c.id
                 AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')) IS NULL THEN 'Novo'
        WHEN (SELECT MAX(o.sold_at) FROM sales_order o
               WHERE o.customer_id = c.id
                 AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO'))
             < NOW() - INTERVAL '90 days' THEN 'Inativo'
        WHEN (SELECT MAX(o.sold_at) FROM sales_order o
               WHERE o.customer_id = c.id
                 AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO'))
             < NOW() - INTERVAL '45 days' THEN 'Em Risco'
        WHEN (SELECT COUNT(*) FROM sales_order o
               WHERE o.customer_id = c.id
                 AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')) >= 3 THEN 'Recorrente'
        ELSE 'Novo'
    END,
    segmento_calculado_em = NOW();

-- Anexos das notas do cliente (documento ou imagem). O arquivo fica no storage (S3/local, o mesmo
-- das imagens de produto); aqui só a referência.
CREATE TABLE customer_note_attachment (
    id            BIGSERIAL    PRIMARY KEY,
    note_id       BIGINT       NOT NULL REFERENCES customer_notes (id) ON DELETE CASCADE,
    nome_arquivo  VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    tamanho       BIGINT       NOT NULL,
    storage_key   VARCHAR(500) NOT NULL,
    criado_em     TIMESTAMP    NOT NULL
);
CREATE INDEX idx_customer_note_attachment_note ON customer_note_attachment (note_id);
CREATE INDEX idx_customer_notes_customer_criado ON customer_notes (customer_id, criado_em DESC);

-- Ajustes administrativos de cashback (revogar um ganho, liberar o que está em carência): permissão
-- própria, dada a quem já administra as taxas de cashback.
INSERT INTO permissions (name) VALUES ('CASHBACK_ADJUST') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, novo.id
FROM role_permissions rp
JOIN permissions base ON base.id = rp.permission_id AND base.name = 'CASHBACK_RATE_MANAGE'
CROSS JOIN permissions novo
WHERE novo.name = 'CASHBACK_ADJUST'
ON CONFLICT DO NOTHING;
