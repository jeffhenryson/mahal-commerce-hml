-- Central de Pedidos › App v2 (pendência 10 §2): coordenadas do endereço de entrega, para o mapa e a rota
-- do entregador. Nulas quando o endereço não foi geocodificado (nunca o centro da cidade inventado).
ALTER TABLE order_delivery
    ADD COLUMN latitude  NUMERIC(9,6),
    ADD COLUMN longitude NUMERIC(9,6),
    ADD CONSTRAINT ck_order_delivery_coords CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180));
