-- Meta mensal de faturamento (pendência 9 do admin). Uma por mês, para todos os canais. Se um dia houver
-- meta por canal, a chave ganha a coluna channel.
CREATE TABLE sales_goal (
    goal_month  VARCHAR(7)    PRIMARY KEY,   -- yyyy-MM ("month" é palavra reservada no H2 do perfil dev)
    amount      NUMERIC(14,2) NOT NULL,
    updated_by  VARCHAR(80)   NOT NULL,
    updated_at  TIMESTAMP     NOT NULL,
    CONSTRAINT ck_sales_goal_amount CHECK (amount > 0),
    CONSTRAINT ck_sales_goal_month CHECK (goal_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$')
);

-- Escrita da meta: permissão própria (gestor/administrador). Nasce no admin e no dev; o dono dá a outros
-- papéis pela tela de permissões. Leitura usa ORDER_READ, como o resto de Vendas.
INSERT INTO permissions (name) VALUES ('SALES_GOAL_MANAGE') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_DEV') AND p.name = 'SALES_GOAL_MANAGE'
ON CONFLICT DO NOTHING;
