-- PDV-C022 — diagnóstico das cópias que a junção de mesas deixou na comanda de origem.
--
-- Até a correção, todo `POST /pdv/comandas/{id}/merge-into/{targetId}` movia as linhas para o
-- destino e, em seguida, as REINSERIA como linhas novas na origem (que ficava CANCELADA). A cópia
-- carrega os mesmos sku, added_at, quantidade e preço da linha movida, mas tem id novo — é por esse
-- "gêmeo" na outra comanda, do mesmo depósito, que ela é reconhecida.
--
-- SÓ LEITURA. Rodar em produção, conferir o resultado com o dono e só então escrever a limpeza
-- (migration própria; apagar também as linhas de comanda_item_addon das cópias).

-- 1. As cópias, uma por linha, com a mesa de origem e a de destino.
SELECT copia.id            AS copia_item_id,
       origem.id           AS comanda_origem,
       origem.table_or_customer_label AS mesa_origem,
       original.id         AS item_original_id,
       destino.id          AS comanda_destino,
       destino.table_or_customer_label AS mesa_destino,
       copia.sku,
       copia.product_name,
       copia.quantity,
       copia.unit_price,
       copia.added_at,
       copia.session_status,
       copia.closed_in_order_id
FROM comanda_item copia
JOIN comanda origem    ON origem.id = copia.comanda_id AND origem.status = 'CANCELADA'
JOIN comanda_item original
     ON original.comanda_id <> copia.comanda_id
    AND original.sku        = copia.sku
    AND original.added_at   = copia.added_at
    AND original.quantity   = copia.quantity
    AND original.unit_price = copia.unit_price
    AND original.id         < copia.id          -- a cópia nasceu depois do original
JOIN comanda destino   ON destino.id = original.comanda_id
                      AND destino.warehouse_code = origem.warehouse_code
ORDER BY origem.id, copia.id;

-- 2. Resumo por mesa de origem: quantas cópias e quanto elas "valem" (não houve cobrança delas —
--    é só o que um relatório que some comanda_item contaria a mais).
SELECT origem.id AS comanda_origem,
       origem.table_or_customer_label AS mesa,
       origem.closed_at,
       COUNT(*) AS copias,
       SUM(copia.quantity * copia.unit_price) AS valor_das_copias
FROM comanda_item copia
JOIN comanda origem ON origem.id = copia.comanda_id AND origem.status = 'CANCELADA'
WHERE EXISTS (
    SELECT 1 FROM comanda_item original
    JOIN comanda destino ON destino.id = original.comanda_id
    WHERE original.comanda_id <> copia.comanda_id
      AND destino.warehouse_code = origem.warehouse_code
      AND original.sku = copia.sku
      AND original.added_at = copia.added_at
      AND original.quantity = copia.quantity
      AND original.unit_price = copia.unit_price
      AND original.id < copia.id)
GROUP BY origem.id, origem.table_or_customer_label, origem.closed_at
ORDER BY origem.closed_at DESC;
