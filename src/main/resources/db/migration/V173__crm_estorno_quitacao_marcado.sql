-- CRM-F010 — estorno de quitação de marcado lançada por engano (caixa 62: R$ 103,00 gravados em
-- DINHEIRO pelo "Receber marcado" sem o dinheiro ter entrado na gaveta).
--
-- Decisões:
--  * Estorna o LOTE inteiro (receivable_payment_batch): é a unidade que o operador lançou, com o troco.
--  * Append-only, como o ledger: nada em receivable_payment é apagado ou reescrito. A reversão é uma
--    linha nova; as somas do caixa (esperado, payment-totals) ignoram o lote que tiver reversão.
--  * Caixa já fechado não é recalculado: expected_amount/difference_amount ficam como foram fechados.
--    session_open_at_reversal anota se o estorno mexeu no esperado ao vivo ou veio depois do fechamento.
--  * RECEIVABLE_PAYMENT_REVERSE só para ROLE_ADMIN e ROLE_DEV, como PDV_SESSION_READ_ANY (V163).
CREATE TABLE receivable_payment_batch_reversal (
    id                        BIGSERIAL    PRIMARY KEY,
    batch_id                  BIGINT       NOT NULL UNIQUE REFERENCES receivable_payment_batch (id),
    reason                    VARCHAR(500) NOT NULL,
    reversed_by               VARCHAR(80)  NOT NULL,
    reversed_at               TIMESTAMPTZ  NOT NULL,
    session_open_at_reversal  BOOLEAN      NOT NULL,
    CONSTRAINT ck_receivable_payment_batch_reversal_reason CHECK (length(trim(reason)) > 0)
);

INSERT INTO permissions (name) VALUES ('RECEIVABLE_PAYMENT_REVERSE') ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_DEV')
  AND p.name = 'RECEIVABLE_PAYMENT_REVERSE'
ON CONFLICT DO NOTHING;
