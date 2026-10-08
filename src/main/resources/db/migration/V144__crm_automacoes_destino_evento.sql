-- Automações (Configurações › Automações): gatilho por EVENTO, destino (webhook próprio,
-- plataforma n8n/Make ou WhatsApp Meta), autenticação do webhook e blocos do payload.

-- Gatilho EVENTO: o segmento vira filtro opcional (NULL = qualquer estágio).
ALTER TABLE campaign_automations ALTER COLUMN segmento_alvo DROP NOT NULL;

ALTER TABLE campaign_automations ADD COLUMN evento            VARCHAR(40);
ALTER TABLE campaign_automations ADD COLUMN destino           VARCHAR(20)  NOT NULL DEFAULT 'WEBHOOK';
ALTER TABLE campaign_automations ADD COLUMN workflow_path     VARCHAR(200);
ALTER TABLE campaign_automations ADD COLUMN whatsapp_template VARCHAR(512);
ALTER TABLE campaign_automations ADD COLUMN whatsapp_idioma   VARCHAR(10);
ALTER TABLE campaign_automations ADD COLUMN auth_tipo         VARCHAR(10)  NOT NULL DEFAULT 'NONE';
ALTER TABLE campaign_automations ADD COLUMN auth_header_nome  VARCHAR(100);
ALTER TABLE campaign_automations ADD COLUMN auth_last4        VARCHAR(4);
-- AutomationMetadata separados por vírgula; NULL = padrão (CLIENTE,LOJA,DATA,AUTOMACAO).
ALTER TABLE campaign_automations ADD COLUMN metadados         VARCHAR(100);

-- webhook_headers das linhas antigas está em JSON puro (a partir daqui é cifrado pela aplicação,
-- que recifra a linha no próximo save). Inferir auth_tipo/auth_header_nome/auth_last4 agora evita
-- que a tela mostre "sem autenticação" — e, ao salvar assim, apague um segredo que existe.
UPDATE campaign_automations
SET auth_tipo  = 'BEARER',
    auth_last4 = RIGHT(webhook_headers::jsonb ->> 'Authorization', 4)
WHERE webhook_headers IS NOT NULL
  AND webhook_headers LIKE '{%'
  AND (SELECT COUNT(*) FROM jsonb_object_keys(webhook_headers::jsonb)) = 1
  AND webhook_headers::jsonb ->> 'Authorization' LIKE 'Bearer %';

UPDATE campaign_automations
SET auth_tipo        = 'HEADER',
    auth_header_nome = (SELECT k FROM jsonb_object_keys(webhook_headers::jsonb) k LIMIT 1),
    auth_last4       = RIGHT((SELECT v FROM jsonb_each_text(webhook_headers::jsonb) AS e(k, v) LIMIT 1), 4)
WHERE webhook_headers IS NOT NULL
  AND webhook_headers LIKE '{%'
  AND webhook_headers::jsonb <> '{}'::jsonb
  AND auth_tipo = 'NONE';

-- Eventos da loja (caixa fechado, estoque baixo) não têm cliente destinatário.
ALTER TABLE campaign_log ALTER COLUMN customer_id DROP NOT NULL;

-- Idempotência dos gatilhos automáticos: no máximo um disparo por automação + ocorrência.
-- event_key é NULL no disparo manual (NULLs não colidem no índice único).
ALTER TABLE campaign_log ADD COLUMN event_key VARCHAR(160);
ALTER TABLE campaign_log
    ADD CONSTRAINT uk_campaign_log_automation_event UNIQUE (automation_id, event_key);
