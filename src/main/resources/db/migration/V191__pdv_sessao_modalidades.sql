-- PDV-F061 — modalidades da sessão de mesa (compra de essência, itens pessoais) e adicional
-- incluído depois da entrega.

-- 1. Preços configuráveis das modalidades novas.
ALTER TABLE session_settings
    ADD COLUMN itens_pessoais_preco     NUMERIC(12,2) NOT NULL DEFAULT 60.00,
    ADD COLUMN compra_essencia_desconto NUMERIC(12,2) NOT NULL DEFAULT 5.00;
ALTER TABLE session_settings ADD CONSTRAINT ck_session_settings_itens_pessoais_preco
    CHECK (itens_pessoais_preco >= 0);
ALTER TABLE session_settings ADD CONSTRAINT ck_session_settings_compra_essencia_desconto
    CHECK (compra_essencia_desconto >= 0);

-- 2. Adicional que pode entrar com a sessão já na mesa (filtro de gelo, piteira).
ALTER TABLE session_addon ADD COLUMN permite_pos_entrega BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Em quais modalidades a essência vale. No produto, o padrão; na variação, nulo herda do pai.
ALTER TABLE product
    ADD COLUMN session_compra_essencia BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN session_itens_pessoais  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE product_variant
    ADD COLUMN session_compra_essencia BOOLEAN,
    ADD COLUMN session_itens_pessoais  BOOLEAN;

-- 4. A modalidade da linha de sessão e o vínculo das linhas que nascem dela.
ALTER TABLE comanda_item
    ADD COLUMN session_modality VARCHAR(20),
    ADD COLUMN session_link     VARCHAR(20);
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_session_modality
    CHECK (session_modality IS NULL
        OR (session_modality IN ('PADRAO','COMPRA_ESSENCIA','ITENS_PESSOAIS') AND mode = 'SESSAO'));
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_session_link
    CHECK (session_link IS NULL
        OR (session_link = 'PACOTE_ESSENCIA' AND mode = 'NORMAL' AND linked_item_id IS NOT NULL)
        OR (session_link = 'ADICIONAL' AND mode = 'ADICIONAL_SESSAO' AND linked_item_id IS NOT NULL));

-- 5. Modo novo: o adicional cobrado depois que a sessão já foi paga. Não é produto do catálogo.
ALTER TABLE comanda_item DROP CONSTRAINT ck_comanda_item_mode;
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_mode
    CHECK (mode IN ('NORMAL','OPEN_ROSH','SABOR_EXTRA','TROCA','SESSAO','ROSH_EXTRA','ADICIONAL_SESSAO'));
ALTER TABLE order_item DROP CONSTRAINT ck_order_item_mode;
ALTER TABLE order_item ADD CONSTRAINT ck_order_item_mode
    CHECK (mode IN ('NORMAL','OPEN_ROSH','SABOR_EXTRA','TROCA','SESSAO','ROSH_EXTRA','ADICIONAL_SESSAO'));

-- 6. Linha ligada à sessão sem ser rosh: o pacote (NORMAL) e o adicional pós-entrega.
ALTER TABLE comanda_item DROP CONSTRAINT ck_comanda_item_linked_by_mode;
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_linked_by_mode
    CHECK (linked_item_id IS NULL
        OR mode IN ('SABOR_EXTRA','TROCA','ROSH_EXTRA','ADICIONAL_SESSAO')
        OR (mode = 'NORMAL' AND session_link = 'PACOTE_ESSENCIA'));
