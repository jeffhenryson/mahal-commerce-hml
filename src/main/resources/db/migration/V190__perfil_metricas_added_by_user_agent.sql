-- Perfil > métricas (desempenho e atividade do próprio usuário e da ficha do admin).
-- comanda_item.added_by: quem lançou a linha, base de performance.tables.itemsLaunched. Nulo nas
-- linhas anteriores a esta migration — o histórico não é reconstruído.
ALTER TABLE comanda_item ADD COLUMN added_by VARCHAR(80);
CREATE INDEX idx_comanda_item_added_by_added_at ON comanda_item (added_by, added_at);

-- audit_logs.user_agent: navegador do login/logout, exibido em /users/{me|id}/access-log.
ALTER TABLE audit_logs ADD COLUMN user_agent VARCHAR(512);

-- Consultas por usuário e período das métricas (caixa, comandas, compras do cliente).
CREATE INDEX idx_cash_register_session_operator_opened ON cash_register_session (operator, opened_at);
CREATE INDEX idx_cash_register_session_operator_closed ON cash_register_session (operator, closed_at);
CREATE INDEX idx_comanda_opened_by_opened_at ON comanda (opened_by, opened_at);
CREATE INDEX idx_sales_order_customer_sold_at ON sales_order (customer_id, sold_at DESC);
