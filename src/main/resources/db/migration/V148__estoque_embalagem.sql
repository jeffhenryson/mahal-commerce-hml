-- EST-F032 — embalagem: carteira → maço → unidade, com quebra automática na saída.
--
-- O cigarro é comprado por carteira (10 maços) e vendido em maço e solto (20 por maço). Cada nível
-- é um SKU próprio — na decisão do dono (2026-10-08), uma variação "cor × embalagem" do mesmo
-- produto —, com saldo, preço e código de barras próprios. Esta tabela diz quem contém quem: quando
-- a SAIDA do filho não cabe no disponível, EstoqueService abre o pai sozinho (SAIDA do pai + ENTRADA
-- do filho na mesma transação da venda), em cascata.
--
-- A chave é o filho: um SKU está dentro de no máximo uma embalagem. Ciclo e profundidade (até 4
-- níveis: fardo → carteira → maço → unidade) são regras do service, que enxerga a cadeia. Sem FK
-- para product, como as demais colunas de SKU; a troca de SKU (EST-F030) atualiza as duas colunas
-- por ProductRepositoryImpl.SKU_COLUMNS.
CREATE TABLE sku_packaging (
    child_sku        VARCHAR(50) PRIMARY KEY,
    parent_sku       VARCHAR(50) NOT NULL,
    units_per_parent INTEGER     NOT NULL,
    CONSTRAINT ck_sku_packaging_units CHECK (units_per_parent > 1),
    CONSTRAINT ck_sku_packaging_not_self CHECK (child_sku <> parent_sku)
);

CREATE INDEX idx_sku_packaging_parent ON sku_packaging (parent_sku);
