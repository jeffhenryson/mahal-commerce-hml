-- PDV-F061 (ajuste) — na compra de essência todas as essências valem: a marcação passa a EXCLUIR
-- (as deluxe). Na variação continua NULL = herda do produto; FALSE exclui só aquele sabor.
-- session_itens_pessoais não muda: só as marcadas valem.
ALTER TABLE product ALTER COLUMN session_compra_essencia SET DEFAULT TRUE;
UPDATE product SET session_compra_essencia = TRUE;
