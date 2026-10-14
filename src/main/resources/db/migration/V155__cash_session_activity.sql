-- PDV-F047 — atividades do operador na sessão de caixa: varrer a loja, passar pano, trocar dinheiro,
-- repor mercadoria, limpar o banheiro ou outra (com observação). É o diário do turno, mostrado no
-- painel da sessão; não entra em nenhum cálculo de gaveta. Append-only, como cash_movement.
CREATE TABLE cash_session_activity (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT       NOT NULL REFERENCES cash_register_session(id),
    type        VARCHAR(30)  NOT NULL,
    note        VARCHAR(280),
    username    VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cash_session_activity_type CHECK (type IN
        ('VARRER_LOJA','PASSAR_PANO','TROCAR_DINHEIRO','REPOSICAO','LIMPEZA_BANHEIRO','OUTRO')),
    CONSTRAINT ck_cash_session_activity_outro_note CHECK (type <> 'OUTRO' OR note IS NOT NULL)
);

CREATE INDEX ix_cash_session_activity_session ON cash_session_activity (session_id, created_at DESC);
