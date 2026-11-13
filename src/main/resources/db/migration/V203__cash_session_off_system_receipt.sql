-- "Valor recebido por fora": Pix ou cartão que caiu na maquininha ou na conta sem passar pelo sistema
-- (ex.: Pix na chave de telefone do dono). Serve só para a conferência por operadora no fechamento e no
-- detalhe do caixa: não entra no esperado da gaveta nem no total recebido.
-- Pode ser lançado com o caixa aberto (operador do caixa) e depois de fechado (PDV_SESSION_CLOSE_ANY).
-- A exclusão é lógica: mexe na conferência de dinheiro, então fica o rastro de quem apagou.
CREATE TABLE cash_session_off_system_receipt (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT        NOT NULL REFERENCES cash_register_session(id),
    amount      NUMERIC(14,2) NOT NULL,
    method      VARCHAR(20)   NOT NULL,
    provider    VARCHAR(20),
    note        VARCHAR(120),
    username    VARCHAR(100)  NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(100),
    CONSTRAINT ck_off_system_receipt_amount CHECK (amount > 0),
    CONSTRAINT ck_off_system_receipt_method CHECK (method IN ('PIX','DEBITO','CREDITO')),
    CONSTRAINT ck_off_system_receipt_provider CHECK (provider IS NULL OR provider IN ('INFINITYPAY','CIELO')),
    CONSTRAINT ck_off_system_receipt_deleted CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

CREATE INDEX ix_off_system_receipt_session ON cash_session_off_system_receipt (session_id, created_at DESC)
    WHERE deleted_at IS NULL;
