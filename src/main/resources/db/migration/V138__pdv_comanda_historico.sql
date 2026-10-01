-- PDV-F029 — Histórico e indicadores de mesas.
--
-- GET /pdv/comandas listava só as ABERTAS: encerrada, a mesa sumia com quem a atendeu, quanto durou e
-- os pedidos que gerou. Os pedidos já apontam para a comanda (sales_order.comanda_id, V113); faltava
-- registrar QUEM encerrou e, no cancelamento, POR QUÊ.

ALTER TABLE comanda ADD COLUMN closed_by     VARCHAR(80);
ALTER TABLE comanda ADD COLUMN cancel_reason VARCHAR(500);

-- O histórico filtra por status != ABERTA e ordena/recorta por closed_at.
CREATE INDEX idx_comanda_status_closed_at ON comanda (status, closed_at DESC);
