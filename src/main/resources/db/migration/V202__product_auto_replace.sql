-- "Repor sozinha" — reposição automática da lata aberta de essência (Salão › Essências).
--
-- Mora no produto base, como sessions_per_unit: as variações (os sabores) herdam. Ao lançar uma
-- sessão que faz a lata atingir o limite, o servidor fecha a lata com AUTO_REPLACED e abre a
-- próxima com baixa de 1 do depósito das latas, na mesma transação da sessão.
-- auto_replace_limit nulo = usar o sessions_per_unit gravado na própria lata.
ALTER TABLE product ADD COLUMN auto_replace BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE product ADD COLUMN auto_replace_limit SMALLINT NULL;
ALTER TABLE product ADD CONSTRAINT ck_product_auto_replace_limit
    CHECK (auto_replace_limit IS NULL OR auto_replace_limit BETWEEN 1 AND 99);

-- Novo motivo de fechamento: AUTO_REPLACED (a troca automática acima).
ALTER TABLE open_package DROP CONSTRAINT ck_open_package_close_reason;
ALTER TABLE open_package ADD CONSTRAINT ck_open_package_close_reason
    CHECK (close_reason IS NULL OR close_reason IN ('EXHAUSTED', 'REPLACED', 'FINISHED', 'AUTO_REPLACED'));
