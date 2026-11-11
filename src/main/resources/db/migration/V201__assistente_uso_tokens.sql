-- Assistente de IA, rodada 3: tokens gastos por resposta (somados nas iterações do turno), para o
-- relatório de uso (GET /assistente/uso) e o limite mensal da loja. Nulos nas respostas antigas.
--   tokens_entrada: entrada processada sem cache (no Claude, inclui a escrita no cache);
--   tokens_saida:   saída, com o raciocínio;
--   tokens_cache:   entrada lida do cache (mais barata; não conta no limite mensal).
ALTER TABLE assistente_mensagem ADD COLUMN tokens_entrada INTEGER;
ALTER TABLE assistente_mensagem ADD COLUMN tokens_saida   INTEGER;
ALTER TABLE assistente_mensagem ADD COLUMN tokens_cache   INTEGER;

CREATE INDEX idx_assistente_mensagem_criada_em ON assistente_mensagem (criada_em);
