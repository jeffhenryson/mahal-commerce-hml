-- CRM-F010 — "Marcar": limite de crédito separado para balcão e mesa.
--
-- O canal de um marcado já é derivável (customer_receivable.comanda_id nulo = BALCAO, senão MESA);
-- o que muda é o limite. customer_credit_limit passa a ter uma linha por (cliente, canal):
--   TOTAL  — o limite único de antes, agora teto da SOMA dos dois canais (opcional);
--   BALCAO — o que o cliente pode marcar no balcão;
--   MESA   — o que o cliente pode marcar na mesa (sessão de narguilé e consumo no salão).
--
-- Limite efetivo de um canal: linha do cliente no canal → pdv.on-account.default-credit-limit.<canal>
-- → pdv.on-account.default-credit-limit. O teto total só existe com linha TOTAL.
--
-- ATENÇÃO NO DEPLOY: as linhas de hoje viram TOTAL e não dão limite a nenhum canal. Quem tem limite
-- individual e nenhuma linha de canal passa a ter, em cada canal, o padrão do canal. Conferir antes:
--   SELECT customer_id, credit_limit FROM customer_credit_limit;
-- e cadastrar BALCAO/MESA desses clientes (PUT /crm/customers/{id}/credit-limit com channel) ou
-- subir o padrão do canal.
ALTER TABLE customer_credit_limit ADD COLUMN channel VARCHAR(10) NOT NULL DEFAULT 'TOTAL';
ALTER TABLE customer_credit_limit
    ADD CONSTRAINT ck_customer_credit_limit_channel CHECK (channel IN ('TOTAL', 'BALCAO', 'MESA'));
ALTER TABLE customer_credit_limit DROP CONSTRAINT customer_credit_limit_pkey;
ALTER TABLE customer_credit_limit ADD CONSTRAINT customer_credit_limit_pkey PRIMARY KEY (customer_id, channel);

-- Os padrões por canal nascem com o valor do padrão geral: quem não tem limite individual não muda.
INSERT INTO system_config (config_key, config_value, updated_at, updated_by)
SELECT k.config_key, COALESCE(g.config_value, '0'), CURRENT_TIMESTAMP, 'system'
FROM (VALUES ('pdv.on-account.default-credit-limit.balcao'),
             ('pdv.on-account.default-credit-limit.mesa')) AS k (config_key)
LEFT JOIN system_config g ON g.config_key = 'pdv.on-account.default-credit-limit'
ON CONFLICT (config_key) DO NOTHING;
