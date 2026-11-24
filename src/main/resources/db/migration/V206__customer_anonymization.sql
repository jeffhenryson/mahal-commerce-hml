-- Exclusão do perfil do cliente (LGPD) = anonimização. A linha de customers fica — pedidos, comandas,
-- movimentações de estoque, extrato de cashback e auditoria continuam apontando para ela —, mas tudo o
-- que identifica a pessoa vira NULL e o nome passa a "Cliente excluído". Não tem volta.

ALTER TABLE customers ADD COLUMN anonymized_at TIMESTAMPTZ;

-- Listagem, busca, lookup, segmentos e disparos filtram "anonymized_at IS NULL".
CREATE INDEX idx_customers_ativos ON customers (id) WHERE anonymized_at IS NULL;

-- Telefone, e-mail e CPF do anonimizado ficam NULL: uk_customers_email/uk_customers_cpf já ignoram
-- NULL e o telefone não é único, então o mesmo dado pode ser cadastrado de novo. O que muda é a
-- exigência de ao menos um identificador (V69), que não vale para o cliente anonimizado.
ALTER TABLE customers DROP CONSTRAINT ck_customers_has_identifier;
ALTER TABLE customers ADD CONSTRAINT ck_customers_has_identifier
    CHECK (anonymized_at IS NOT NULL OR cpf IS NOT NULL OR email IS NOT NULL OR contato IS NOT NULL);

-- Motivo de um lançamento de cashback fora do fluxo normal. Hoje só CUSTOMER_DELETED: o saldo do
-- cliente excluído é zerado por lançamento (EXPIRED/REVERSED), sem apagar nada do extrato.
ALTER TABLE cashback_entry ADD COLUMN reason VARCHAR(40);

INSERT INTO permissions (name) VALUES ('CRM_CUSTOMER_DELETE') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'CRM_CUSTOMER_DELETE'
ON CONFLICT DO NOTHING;
