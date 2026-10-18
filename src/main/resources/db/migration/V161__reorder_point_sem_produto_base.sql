-- EST-F053 — produto base com variações e sem "vender o produto base" (EST-F036) não tem saldo
-- próprio: a entrada vai nas variações. O ponto de reposição salvo nele aparecia sempre como
-- "Crítico" no resumo e nos Alertas. O service passa a recusar o cadastro; isto limpa os antigos.
DELETE FROM stock_reorder_point rp
 WHERE EXISTS (
       SELECT 1
         FROM product p
         JOIN product_variant v ON v.product_id = p.id
        WHERE p.sku = rp.sku
          AND p.parent_sellable = FALSE);
