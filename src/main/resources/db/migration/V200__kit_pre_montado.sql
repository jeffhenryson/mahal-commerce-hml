-- Kit pré-montado: kit com estoque próprio, cujos itens só descrevem o conteúdo.
ALTER TABLE product ADD COLUMN kit_mode VARCHAR(20) NULL;
UPDATE product SET kit_mode = 'LOJA' WHERE type = 'KIT';

CREATE TABLE kit_item (
    id             BIGSERIAL     PRIMARY KEY,
    kit_product_id BIGINT        NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    name           VARCHAR(150)  NOT NULL,
    quantity       INT           NOT NULL CHECK (quantity >= 1),
    unit_cost      NUMERIC(12,2) NULL CHECK (unit_cost >= 0),
    category_id    BIGINT        NULL REFERENCES product_category(id) ON DELETE SET NULL,
    brand_id       BIGINT        NULL REFERENCES product_brand(id) ON DELETE SET NULL,
    description    VARCHAR(500)  NULL,
    image_url      VARCHAR(2048) NULL,
    position       INT           NOT NULL
);

CREATE INDEX idx_kit_item_kit_product ON kit_item (kit_product_id, position);
