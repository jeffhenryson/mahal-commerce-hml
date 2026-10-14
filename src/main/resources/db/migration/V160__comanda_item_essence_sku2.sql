-- PDV-F051 — sessão mesclada: até 2 sabores no mesmo rosh.
--
-- A linha continua sendo UMA sessão (um preço, um narguilé); o 2º sabor só precisa ser lembrado para
-- o desfazimento devolver o uso da lata certa. essence2_package diz se o 2º sabor saiu de lata aberta
-- (true) ou como unidade do saldo (false) — a mesma decisão que package_uses guarda para o 1º, e que
-- reler o catálogo meses depois não daria. Sem FK para product, como essence_sku: a troca de SKU
-- (EST-F030) atualiza a coluna por ProductRepositoryImpl.SKU_COLUMNS.
ALTER TABLE comanda_item ADD COLUMN essence_sku2 VARCHAR(50);
ALTER TABLE comanda_item ADD COLUMN essence2_package BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_essence_sku2
    CHECK (essence_sku2 IS NULL OR (essence_sku IS NOT NULL AND essence_sku2 <> essence_sku));
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_essence2_package
    CHECK (essence_sku2 IS NOT NULL OR NOT essence2_package);
