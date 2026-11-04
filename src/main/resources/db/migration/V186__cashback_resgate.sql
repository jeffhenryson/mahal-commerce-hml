-- Resgate de cashback (PDV balcão, comanda/mesa e app) + parâmetros configuráveis pelo admin.
--
-- O ganho (V70) não muda. Esta migration abre o ledger para o débito de resgate e para a
-- devolução desse débito quando o pedido é cancelado/estornado, e cria a tabela que registra DE
-- QUAL ganho cada resgate consumiu — sem ela, expirar um ganho já resgatado debitaria o mesmo
-- dinheiro duas vezes (saldo = SUM(amount)).

-- ---------------------------------------------------------------------------------------------
-- Novos formatos de lançamento:
--   REDEEMED            negativo, order_id = pedido onde foi usado, reverses_entry_id NULL
--                       (consome vários ganhos — o vínculo fica em cashback_redemption_allocation).
--   REDEMPTION_REVERSED positivo, devolve (total ou parcialmente) um REDEEMED; aponta para ele.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE cashback_entry DROP CONSTRAINT ck_cashback_entry_type;
ALTER TABLE cashback_entry ADD CONSTRAINT ck_cashback_entry_type
    CHECK (type IN ('EARNED','REDEEMED','REVERSED','EXPIRED','REDEMPTION_REVERSED'));

ALTER TABLE cashback_entry DROP CONSTRAINT ck_cashback_entry_sign;
ALTER TABLE cashback_entry ADD CONSTRAINT ck_cashback_entry_sign
    CHECK ((type IN ('EARNED','REDEMPTION_REVERSED')) = (amount > 0));

ALTER TABLE cashback_entry DROP CONSTRAINT ck_cashback_entry_reversal;
ALTER TABLE cashback_entry ADD CONSTRAINT ck_cashback_entry_reversal
    CHECK ((type IN ('EARNED','REDEEMED')) OR (reverses_entry_id IS NOT NULL));

-- Um resgate por pedido. Fechamento parcial de comanda já é um pedido próprio.
CREATE UNIQUE INDEX uk_cashback_entry_redeemed_per_order
    ON cashback_entry (order_id)
    WHERE type = 'REDEEMED';

-- ---------------------------------------------------------------------------------------------
-- Alocação FIFO do resgate nos ganhos. Append-only como o ledger:
--   amount > 0  — o lançamento entry_id (REDEEMED) consumiu essa parte do ganho earned_entry_id;
--   amount < 0  — o lançamento entry_id (REDEMPTION_REVERSED) devolveu essa parte ao ganho.
-- Consumido de um ganho = SUM(amount) WHERE earned_entry_id = ganho. A expiração debita só
-- (ganho - consumido).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE cashback_redemption_allocation (
    id               BIGSERIAL     PRIMARY KEY,
    entry_id         BIGINT        NOT NULL REFERENCES cashback_entry(id),
    earned_entry_id  BIGINT        NOT NULL REFERENCES cashback_entry(id),
    amount           NUMERIC(14,2) NOT NULL,
    created_at       TIMESTAMP     NOT NULL,
    CONSTRAINT ck_cashback_redemption_allocation_amount CHECK (amount <> 0)
);

CREATE INDEX idx_cashback_redemption_allocation_earned ON cashback_redemption_allocation (earned_entry_id);
CREATE INDEX idx_cashback_redemption_allocation_entry ON cashback_redemption_allocation (entry_id);

COMMENT ON TABLE cashback_redemption_allocation IS
    'De qual ganho (EARNED) cada resgate consumiu (positivo) ou devolveu (negativo). Append-only.';

-- Garantia: as 4 chaves configuráveis existem (V70 já semeou; ON CONFLICT mantém o que o admin
-- tiver ajustado).
INSERT INTO system_config (config_key, config_value) VALUES
    ('cashback.carencia.dias', '7'),
    ('cashback.expiracao.dias', '180'),
    ('cashback.resgate.teto.percent', '50'),
    ('cashback.resgate.minimo.saldo', '50.00')
ON CONFLICT (config_key) DO NOTHING;
