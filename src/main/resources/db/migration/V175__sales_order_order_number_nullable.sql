-- ECM-F009: pedido do app nasce sem número (emitido na liquidação pelo caixa, ver Order).
-- V65 tinha deixado a coluna NOT NULL, o que quebrava todo POST /shop/checkout.
ALTER TABLE sales_order ALTER COLUMN order_number DROP NOT NULL;
