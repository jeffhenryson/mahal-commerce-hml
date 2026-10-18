-- PLAT-F005 — vínculo usuário↔cliente. Quem opera o PDV (atendente/admin/dev) também compra na loja,
-- e a conta de operador passa a poder apontar para o seu cadastro de cliente do CRM.
--
-- A V76 amarrava customer_id ao tipo nos dois sentidos: (CUSTOMER) = (customer_id IS NOT NULL). O
-- sentido que importa continua: conta de cliente da loja sempre tem cliente. O operador agora pode ter
-- ou não. O índice único parcial uk_users_customer_id (V76) segue garantindo um usuário por cliente.
ALTER TABLE users DROP CONSTRAINT ck_users_customer_link;
ALTER TABLE users ADD CONSTRAINT ck_users_customer_link
    CHECK (user_type <> 'CUSTOMER' OR customer_id IS NOT NULL);
