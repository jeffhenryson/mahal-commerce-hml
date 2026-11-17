-- Cor da categoria no PDV (2026-11-17): o PDV pinta a borda do card e a etiqueta da categoria.
--
-- Só visual: não mexe em preço, estoque, cashback nem desconto. Nula = card neutro. O backend grava
-- sempre "#RRGGBB" em maiúsculo; o CHECK garante isso também para quem escrever direto no banco.
-- Sem backfill: as categorias existentes ficam sem cor.
ALTER TABLE product_category ADD COLUMN color VARCHAR(7) NULL;

ALTER TABLE product_category
    ADD CONSTRAINT ck_product_category_color CHECK (color IS NULL OR color ~ '^#[0-9A-F]{6}$');
