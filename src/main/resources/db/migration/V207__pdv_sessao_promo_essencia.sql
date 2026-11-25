-- PDV-F062 — sessão "Promo de essência": preço fechado (R$ 10) com lata já aberta no Salão.

-- 1. Preço configurável da promo.
ALTER TABLE session_settings
    ADD COLUMN promo_essencia_preco NUMERIC(12,2) NOT NULL DEFAULT 10.00;
ALTER TABLE session_settings ADD CONSTRAINT ck_session_settings_promo_essencia_preco
    CHECK (promo_essencia_preco > 0);

-- 2. Quem vale na promo: todas por padrão, a marcação EXCLUI (as deluxe). Na variação, NULL herda do
--    produto; FALSE exclui só aquele sabor.
ALTER TABLE product ADD COLUMN session_promo_essencia BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE product_variant ADD COLUMN session_promo_essencia BOOLEAN;

-- 3. A modalidade nova na linha de sessão.
ALTER TABLE comanda_item DROP CONSTRAINT ck_comanda_item_session_modality;
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_session_modality
    CHECK (session_modality IS NULL
        OR (session_modality IN ('PADRAO','COMPRA_ESSENCIA','ITENS_PESSOAIS','PROMO_ESSENCIA') AND mode = 'SESSAO'));
