-- PDV-C049 — a soma de estornos do caixa (fechamento, /payment-totals, /summary) filtrava por
-- COALESCE(refund_session_id, sales_order.session_id), que não usa índice nenhum: o Postgres varria
-- todo REFUNDED com o join. A consulta foi partida em duas metades indexáveis; esta é a da sessão
-- onde o estorno foi feito (PED-F004). A outra metade usa idx_sales_order_session_id + order_id.
-- Parcial: refund_session_id só existe em linha REFUNDED (ck da V156), e é só ela que a soma lê.
CREATE INDEX IF NOT EXISTS idx_order_payment_refund_session
    ON order_payment (refund_session_id, method)
    WHERE status = 'REFUNDED';
