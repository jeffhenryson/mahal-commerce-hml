-- PDV-F056 — troco que fica na gaveta para o próximo turno, informado no fechamento. É parte do
-- contado: não mexe no esperado nem na diferença, e só existe em caixa fechado.
ALTER TABLE cash_register_session ADD COLUMN left_in_drawer_amount NUMERIC(14, 2);

ALTER TABLE cash_register_session ADD CONSTRAINT ck_cash_session_left_in_drawer
    CHECK (left_in_drawer_amount IS NULL
        OR (status = 'CLOSED' AND left_in_drawer_amount >= 0 AND left_in_drawer_amount <= counted_amount));
