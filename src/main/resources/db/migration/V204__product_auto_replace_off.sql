-- A lata nunca troca sozinha (2026-11-13): o PDV sempre pergunta "Essa lata acabou?".
--
-- Desliga a troca automática da V202 nos produtos que a ligaram. auto_replace_limit fica: é o
-- limite em que o PDV faz a pergunta. A coluna e o AUTO_REPLACED do CHECK de open_package ficam
-- também, pelo histórico das latas que já fecharam assim.
UPDATE product SET auto_replace = FALSE WHERE auto_replace = TRUE;
