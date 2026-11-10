-- Avaliação (👍/👎) das respostas do assistente, para comparar provedores e modelos.
-- Só as mensagens do assistente recebem; nota nula = não avaliada.
ALTER TABLE assistente_mensagem ADD COLUMN feedback_nota   VARCHAR(10);
ALTER TABLE assistente_mensagem ADD COLUMN feedback_motivo VARCHAR(500);
ALTER TABLE assistente_mensagem ADD COLUMN feedback_em     TIMESTAMPTZ;
