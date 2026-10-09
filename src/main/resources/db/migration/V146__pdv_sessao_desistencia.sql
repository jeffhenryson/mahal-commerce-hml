-- PDV-C036 — desistência da sessão servida.
--
-- Até aqui a sessão já servida (em preparo ou na mesa) e não paga — o caso de quem paga no final —
-- saía da mesa pelo DELETE da linha, só com PDV_COMANDA_MANAGE: o narguilé saía de graça sem a alçada de
-- cortesia, e a linha sumia do histórico e da conta de desistidas. Decisão do dono (2026-10-08): motivo
-- obrigatório, e a linha fica — recolhida, como cortesia a R$ 0 —, com quem registrou.
ALTER TABLE comanda_item ADD COLUMN withdrawn_reason VARCHAR(200);
ALTER TABLE comanda_item ADD COLUMN withdrawn_by VARCHAR(80);

-- A desistência é sempre uma sessão do cardápio recolhida e gratuita.
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_withdrawn
    CHECK (withdrawn_reason IS NULL
           OR (courtesy = TRUE AND session_status = 'RECOLHIDO' AND mode IN ('SESSAO','ROSH_EXTRA')));
