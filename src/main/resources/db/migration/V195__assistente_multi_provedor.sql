-- Assistente de IA com vários provedores (Claude, ChatGPT, Gemini): uma chave por provedor em
-- integration.assistant.<provedor>.*. A chave que já existia era do Claude e vai para anthropic.*.

INSERT INTO system_config (config_key, config_value, updated_at, updated_by)
SELECT 'integration.assistant.anthropic.' || substring(config_key FROM length('integration.assistant.') + 1),
       config_value, updated_at, updated_by
FROM system_config
WHERE config_key IN ('integration.assistant.api-key', 'integration.assistant.api-key-last4',
                     'integration.assistant.model', 'integration.assistant.last-test-detail')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO system_config (config_key, config_value, updated_at, updated_by)
SELECT 'integration.assistant.provider', 'ANTHROPIC', updated_at, updated_by
FROM system_config
WHERE config_key = 'integration.assistant.api-key' AND config_value <> ''
ON CONFLICT (config_key) DO NOTHING;

DELETE FROM system_config
WHERE config_key IN ('integration.assistant.api-key', 'integration.assistant.api-key-last4',
                     'integration.assistant.model', 'integration.assistant.last-test-detail');

-- Quem respondeu cada mensagem do assistente (o provedor reserva pode ter entrado).
ALTER TABLE assistente_mensagem ADD COLUMN provedor VARCHAR(20);
ALTER TABLE assistente_mensagem ADD COLUMN modelo VARCHAR(100);
