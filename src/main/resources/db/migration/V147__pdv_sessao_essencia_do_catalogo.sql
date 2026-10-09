-- PDV-F042 — essência do catálogo na sessão do cardápio.
--
-- Até aqui a sessão vendida pelo cardápio (SKU sintético SESS-{faixa}) guardava a essência só como
-- texto em notes e não tocava estoque: o uso de essência da loja pela mesa era invisível, e a lata
-- aberta (EST-F027) nunca rodava no caminho de produção. Com o sabor do catálogo escolhido, a linha
-- consome um uso da lata aberta (ou uma unidade como uso da loja, sem lata configurada) — e precisa
-- guardar QUAL sabor queimou, porque é ele, e não a faixa, que remover/cancelar devolve.
--
-- Nulo = sessão só em texto (toda linha anterior a esta migration, e as lançadas sem o SKU, que segue
-- opcional por decisão do dono). Sem FK para product, como as demais colunas de SKU: a troca de SKU
-- (EST-F030) atualiza esta coluna por ProductRepositoryImpl.SKU_COLUMNS.
ALTER TABLE comanda_item ADD COLUMN essence_sku VARCHAR(50);

-- A linha de catálogo já é o próprio produto: só a sessão do cardápio carrega a essência à parte.
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_essence_sku_menu_session
    CHECK (essence_sku IS NULL OR mode IN ('SESSAO','ROSH_EXTRA'));
