-- Cashback × desconto do chefe — os snapshots que a regra precisa depois de gravado.
--
-- order_item.benefits_excluded: a linha era de categoria fora dos benefícios no instante da venda.
-- Congelada como o preço e a taxa de cashback: a revalidação do resgate (base = líquido dos itens
-- elegíveis) e o relatório precisam dela, e mudar a categoria amanhã não pode reescrever o passado.
ALTER TABLE order_item ADD COLUMN benefits_excluded BOOLEAN NOT NULL DEFAULT FALSE;

-- sales_order.boss_discount_percent: o percentual do desconto do chefe (1–5), para relatório e estorno.
-- Pedido com ele não gera cashback.
ALTER TABLE sales_order ADD COLUMN boss_discount_percent SMALLINT;
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_boss_discount_percent
    CHECK (boss_discount_percent IS NULL OR boss_discount_percent BETWEEN 1 AND 5);

-- comanda_item.benefits_excluded: carimbado no lançamento (como preço e custo). O fechamento da mesa
-- tira essas linhas da base do desconto do chefe e do resgate.
ALTER TABLE comanda_item ADD COLUMN benefits_excluded BOOLEAN NOT NULL DEFAULT FALSE;

-- Linhas já lançadas: pelo SKU do produto ou de uma variação dele.
UPDATE comanda_item ci SET benefits_excluded = TRUE
  FROM product p
  JOIN product_category pc ON pc.id = p.category_id
 WHERE pc.cashback_excluded
   AND (p.sku = ci.sku
        OR EXISTS (SELECT 1 FROM product_variant pv WHERE pv.product_id = p.id AND pv.sku = ci.sku));
