-- Origem estruturada da movimentação de estoque e saldo antes/depois (pendência 4 do admin).
--
-- Até aqui a origem vivia só no texto livre de "reason" ("Venda balcão sessão #12", "Comanda #40"),
-- e a tela de histórico não tinha como linkar para o pedido/recebimento/comanda nem mostrar o saldo
-- resultante sem refazer a conta. origin_id não tem FK: aponta para tabelas diferentes conforme o
-- tipo, e a movimentação é trilha de auditoria — excluir o documento de origem não pode apagá-la.

ALTER TABLE stock_movement
    ADD COLUMN origin_type    VARCHAR(20),
    ADD COLUMN origin_id      BIGINT,
    ADD COLUMN balance_before NUMERIC(14,3),
    ADD COLUMN balance_after  NUMERIC(14,3);

-- Backfill do que dá para saber com certeza. O resto do histórico fica com origem nula (desconhecida),
-- e não MANUAL: chutar "manual" para uma venda antiga seria pior que admitir que não se sabe.
-- Saldo antes/depois não tem backfill: recompor exigiria reprocessar o ledger inteiro na ordem exata.
UPDATE stock_movement SET origin_type = 'GOODS_RECEIPT', origin_id = goods_receipt_id
 WHERE goods_receipt_id IS NOT NULL;
UPDATE stock_movement SET origin_type = 'TRANSFER'
 WHERE counterpart_warehouse_id IS NOT NULL AND origin_type IS NULL;

CREATE INDEX ix_stock_movement_origin ON stock_movement (origin_type, origin_id);
