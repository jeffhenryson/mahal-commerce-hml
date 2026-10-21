-- Autoatendimento do Salão, rodada 2: pedido sem CPF de quem já está na mesa e trava da confirmação.
--
-- Decisões:
--  * O aparelho com acesso à ocupação pede sem CPF: o cliente é o titular da ocupação. Para o PDV
--    saber quem pediu, o acesso guarda se veio do código de 4 dígitos (acompanhante) ou do titular
--    (aceite do pedido dele, ou entrada pelo CPF/celular confirmado). Acesso do titular prevalece:
--    o aparelho que já era dele não vira acompanhante por digitar o código.
--  * A solicitação feita assim grava acompanhante = TRUE e aparece no PDV como "Acompanhante de
--    <titular>" — a cobrança segue na comanda do titular.
--  * A trava da confirmação (5 erros em 15 min por cliente) conta as recusas já gravadas na
--    auditoria, sem tabela própria. O índice parcial cobre só essa contagem.

ALTER TABLE salao_mesa_acesso ADD COLUMN acompanhante BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE salao_solicitacao ADD COLUMN acompanhante BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_audit_logs_salao_confirmacao_recusada ON audit_logs (target, timestamp DESC)
    WHERE action = 'SALAO_CONFIRMACAO_RECUSADA';
