-- Promoção por quantidade ("leve 4 por R$ 10"). É uma regra de preço sobre os produtos reais, e o
-- servidor a aplica sozinho no balcão, na comanda e no marketplace. Substitui o produto "promo"
-- separado ("Seda 4un"), que vendia sem baixar a seda de verdade.
--
-- Não tem SKU nem saldo: cada unidade baixa do próprio SKU, e o desconto fica congelado na linha
-- (order_item / comanda_item). Por isso as linhas guardam promotion_id SEM FK: excluir a regra não
-- pode quebrar o histórico, e promotion_name guarda o nome como estava na venda.

CREATE TABLE quantity_promotion (
    id           BIGSERIAL     PRIMARY KEY,
    name         VARCHAR(150)  NOT NULL,
    description  VARCHAR(500),
    quantity     INTEGER       NOT NULL,
    bundle_price NUMERIC(12,2) NOT NULL,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    starts_at    TIMESTAMP,
    ends_at      TIMESTAMP,
    created_at   TIMESTAMP     NOT NULL,
    created_by   VARCHAR(80),
    updated_at   TIMESTAMP     NOT NULL,
    updated_by   VARCHAR(80),
    CONSTRAINT ck_quantity_promotion_quantity CHECK (quantity >= 2),
    CONSTRAINT ck_quantity_promotion_bundle_price CHECK (bundle_price > 0),
    CONSTRAINT ck_quantity_promotion_period CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at)
);

-- Mesma regra de kit_template (V126): o nome é único sem caixa.
CREATE UNIQUE INDEX uk_quantity_promotion_name_lower ON quantity_promotion (LOWER(name));

-- SKU é texto, sem FK, como no resto do estoque. Produto inativado ou arquivado continua na lista
-- e só deixa de contar na venda. A tela mostra "+N inativo(s)".
CREATE TABLE quantity_promotion_sku (
    promotion_id BIGINT      NOT NULL REFERENCES quantity_promotion (id) ON DELETE CASCADE,
    sku          VARCHAR(50) NOT NULL,
    PRIMARY KEY (promotion_id, sku)
);
CREATE INDEX idx_quantity_promotion_sku_sku ON quantity_promotion_sku (sku);

CREATE TABLE quantity_promotion_channel (
    promotion_id BIGINT      NOT NULL REFERENCES quantity_promotion (id) ON DELETE CASCADE,
    channel      VARCHAR(20) NOT NULL,
    PRIMARY KEY (promotion_id, channel),
    CONSTRAINT ck_quantity_promotion_channel CHECK (channel IN ('PDV', 'COMANDA', 'MARKETPLACE'))
);

-- Detalhamento do desconto da linha. order_item.discount_amount continua sendo o TOTAL (kit + manual
-- + promoção), e promotion_discount_amount é a parte da promoção, para o relatório separar.
ALTER TABLE order_item ADD COLUMN promotion_id BIGINT;
ALTER TABLE order_item ADD COLUMN promotion_name VARCHAR(150);
ALTER TABLE order_item ADD COLUMN promotion_discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE order_item ADD CONSTRAINT ck_order_item_promotion_discount CHECK (
    promotion_discount_amount >= 0 AND promotion_discount_amount <= discount_amount
);
CREATE INDEX idx_order_item_promotion ON order_item (promotion_id) WHERE promotion_id IS NOT NULL;

-- Na comanda o desconto é separado, ao lado de kit_discount_amount e store_discount_amount, e
-- recalculado a cada item que entra ou sai.
ALTER TABLE comanda_item ADD COLUMN promotion_id BIGINT;
ALTER TABLE comanda_item ADD COLUMN promotion_name VARCHAR(150);
ALTER TABLE comanda_item ADD COLUMN promotion_discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_promotion_discount CHECK (promotion_discount_amount >= 0);

COMMENT ON TABLE quantity_promotion IS 'Promoção por quantidade: N unidades de qualquer combinação dos SKUs por bundle_price';
COMMENT ON COLUMN order_item.promotion_discount_amount IS 'Parte de discount_amount que veio de promoção por quantidade';
COMMENT ON COLUMN order_item.promotion_id IS 'Regra aplicada (a de maior desconto na linha). Sem FK: a regra pode ser excluída';

-- Permissão nova, concedida a quem já cuida dos kits montáveis. O ROLE_DEV recebe pelo
-- DevRoleBootstrapConfig.
INSERT INTO permissions (name) VALUES ('ESTOQUE_PROMOTION_MANAGE') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, novo.id
FROM role_permissions rp
JOIN permissions kit ON kit.id = rp.permission_id AND kit.name = 'ESTOQUE_KIT_TEMPLATE_MANAGE'
CROSS JOIN permissions novo
WHERE novo.name = 'ESTOQUE_PROMOTION_MANAGE'
ON CONFLICT DO NOTHING;
