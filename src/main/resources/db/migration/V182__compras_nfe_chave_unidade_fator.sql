-- NF-e repetida e unidade do fornecedor (pendência 5 do admin).
--
-- 1) Chave de acesso (44 dígitos). Com ela o confirm recusa a mesma nota já recebida
--    (409 NFE_ALREADY_RECEIVED). O índice único é parcial em CONFIRMED: rever o preview da mesma nota
--    várias vezes é normal, receber duas vezes não é. Imports antigos ficam com a chave nula.
ALTER TABLE nfe_import ADD COLUMN access_key VARCHAR(44);
CREATE UNIQUE INDEX uk_nfe_import_access_key_confirmed ON nfe_import (access_key)
    WHERE status = 'CONFIRMED' AND access_key IS NOT NULL;
CREATE INDEX idx_nfe_import_access_key ON nfe_import (access_key);

-- 2) Unidade comercial (uCom) e tributável (uTrib/qTrib) da linha, e o fator de embalagem usado na
--    entrada: a nota diz "2 CX" e o estoque precisa de "24 UN".
ALTER TABLE nfe_import_line
    ADD COLUMN unit              VARCHAR(10),
    ADD COLUMN tax_unit          VARCHAR(10),
    ADD COLUMN tax_quantity      NUMERIC(14,4),
    ADD COLUMN units_per_package INTEGER,
    ADD CONSTRAINT ck_nfe_import_line_units_per_package CHECK (units_per_package IS NULL OR units_per_package >= 1);

-- 3) O que o operador ensinou por fornecedor + código do produto dele (cProd): o fator e, quando
--    a nota não traz EAN, o SKU. A próxima nota do mesmo fornecedor já chega preenchida no preview.
CREATE TABLE supplier_product_mapping (
    supplier_id           BIGINT       NOT NULL REFERENCES supplier (id),
    supplier_product_code VARCHAR(60)  NOT NULL,
    sku                   VARCHAR(50),
    units_per_package     INTEGER      NOT NULL DEFAULT 1,
    updated_at            TIMESTAMP    NOT NULL,
    updated_by            VARCHAR(80)  NOT NULL,
    PRIMARY KEY (supplier_id, supplier_product_code),
    CONSTRAINT ck_supplier_product_mapping_units CHECK (units_per_package >= 1)
);
