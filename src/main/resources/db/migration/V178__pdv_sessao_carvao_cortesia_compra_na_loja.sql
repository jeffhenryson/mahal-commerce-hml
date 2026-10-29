-- PDV-F057 — carvão jumbo de cortesia: o acréscimo do jumbo não entra no subtotal da sessão.
ALTER TABLE comanda_item ADD COLUMN carvao_cortesia BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_carvao_cortesia CHECK (
    NOT carvao_cortesia OR charcoal = 'JUMBO');

-- PDV-F060 — compra na loja lançada na conta da mesa: cobrada junto com a sessão paga no final,
-- fora da base da taxa de serviço. O desconto (do kit montável do caixa) é da linha, como na venda
-- do caixa, e não usa as colunas de kit (ck_comanda_item_kit_fields exige o pacote).
ALTER TABLE comanda_item ADD COLUMN compra_na_loja BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE comanda_item ADD COLUMN store_discount_amount NUMERIC(14,2);

ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_store_purchase CHECK (
    (NOT compra_na_loja OR mode = 'NORMAL')
    AND (store_discount_amount IS NULL OR (compra_na_loja AND store_discount_amount >= 0)));
