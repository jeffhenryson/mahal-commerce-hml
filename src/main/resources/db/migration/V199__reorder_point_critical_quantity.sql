-- Corte do nível CRÍTICO do alerta de reposição. Nulo = padrão (metade do mínimo).
ALTER TABLE stock_reorder_point ADD COLUMN critical_quantity NUMERIC(14,3) NULL;
