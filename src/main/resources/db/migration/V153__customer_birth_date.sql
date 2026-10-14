-- CRM-F012 — data de nascimento do cliente: opcional, cadastrada no CRM ou no lead do PDV/Salão.
-- É a base da automação ANIVERSARIO_CLIENTE (job diário, fatia futura), que procura por mês e dia
-- — daí o índice de expressão, e não um índice simples na coluna.
ALTER TABLE customers ADD COLUMN data_nascimento DATE;

CREATE INDEX ix_customer_aniversario
    ON customers ((EXTRACT(MONTH FROM data_nascimento)), (EXTRACT(DAY FROM data_nascimento)))
    WHERE data_nascimento IS NOT NULL;
