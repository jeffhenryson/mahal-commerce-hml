-- PDV-F061 (pacotes) — nas duas modalidades a essência vira pacotes comprados na loja.
-- 1. Itens pessoais também por exclusão: todas valem, desmarca-se só o que fica de fora.
ALTER TABLE product ALTER COLUMN session_itens_pessoais SET DEFAULT TRUE;
UPDATE product SET session_itens_pessoais = TRUE;

-- 2. O pacote incluso dos itens pessoais (R$ 0): linha NORMAL ligada à sessão, com vínculo próprio.
ALTER TABLE comanda_item DROP CONSTRAINT ck_comanda_item_session_link;
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_session_link
    CHECK (session_link IS NULL
        OR (session_link IN ('PACOTE_ESSENCIA','PACOTE_INCLUSO') AND mode = 'NORMAL' AND linked_item_id IS NOT NULL)
        OR (session_link = 'ADICIONAL' AND mode = 'ADICIONAL_SESSAO' AND linked_item_id IS NOT NULL));

ALTER TABLE comanda_item DROP CONSTRAINT ck_comanda_item_linked_by_mode;
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_linked_by_mode
    CHECK (linked_item_id IS NULL
        OR mode IN ('SABOR_EXTRA','TROCA','ROSH_EXTRA','ADICIONAL_SESSAO')
        OR (mode = 'NORMAL' AND session_link IN ('PACOTE_ESSENCIA','PACOTE_INCLUSO')));
