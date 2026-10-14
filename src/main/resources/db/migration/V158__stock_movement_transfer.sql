-- EST-F039 — transferência entre depósitos (ex.: loja → Salão, a preço de custo).
--
-- Sem MovementType novo, mesma justificativa da conversão (EST-F025): para o saldo de cada depósito
-- é uma SAIDA e uma ENTRADA comuns. O que distingue a transferência é o depósito do outro lado,
-- gravado nas duas pontas: o ledger mostra o vínculo e o resumo de consumo do Salão soma só as
-- entradas que vieram de outro depósito, sem depender do texto do motivo.
ALTER TABLE stock_movement
    ADD COLUMN counterpart_warehouse_id BIGINT NULL REFERENCES warehouse (id);

-- Leitura do resumo: entradas transferidas para um depósito num período.
CREATE INDEX idx_stock_movement_transfer
    ON stock_movement (warehouse_id, created_at) WHERE counterpart_warehouse_id IS NOT NULL;

COMMENT ON COLUMN stock_movement.counterpart_warehouse_id IS
    'Depósito do outro lado de uma transferência (EST-F039). Nulo em qualquer outro movimento.';
