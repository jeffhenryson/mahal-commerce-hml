-- EST-F037 — "sessões por lata" passa a ser estimativa, e a lata fecha quando o atendente marca "Acabou".
--
-- A lata não fecha mais sozinha ao atingir sessions_per_unit: continua recebendo sessões além da
-- estimativa até alguém fechá-la. O CHECK da V124 que limitava uses ao sessions_per_unit recusaria
-- exatamente o que agora é o caso normal; fica só o piso.
ALTER TABLE open_package DROP CONSTRAINT ck_open_package_uses;
ALTER TABLE open_package ADD CONSTRAINT ck_open_package_uses CHECK (uses >= 0);

-- Novo motivo de fechamento: FINISHED ("Acabou"), sem abrir outra lata e sem baixa.
ALTER TABLE open_package DROP CONSTRAINT ck_open_package_close_reason;
ALTER TABLE open_package ADD CONSTRAINT ck_open_package_close_reason
    CHECK (close_reason IS NULL OR close_reason IN ('EXHAUSTED', 'REPLACED', 'FINISHED'));

-- A leitura do rendimento real filtra as latas fechadas de um depósito pelo momento do fechamento.
CREATE INDEX idx_open_package_warehouse_closed ON open_package (warehouse_id, closed_at)
    WHERE closed_at IS NOT NULL;
