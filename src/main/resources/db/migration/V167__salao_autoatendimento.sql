-- Autoatendimento do Salão: o lead escaneia o QR da mesa, se identifica (ou se cadastra), escolhe a
-- essência entre as do dia e pede a sessão; o atendente aceita no PDV e a mesa abre com a sessão em
-- preparo.
--
-- Decisões:
--  * A mesa ganha cadastro próprio só para o QR. A comanda continua com o rótulo livre
--    (tableOrCustomerLabel = nome da mesa): nada do PDV passa a depender desta tabela.
--  * O token do QR é aleatório e regenerável — quem fotografou o QR antigo perde o acesso.
--  * "Essências do dia" valem para a data (dia no salão). Sem linha na data, o QR oferece todas as
--    disponíveis.
--  * A solicitação tem um código público (UUID) para a página do lead acompanhar o status sem
--    expor o id sequencial. EXPIRADA é derivada na leitura (pendente há mais de 30 min), sem job.

CREATE TABLE salao_mesa (
    id        BIGSERIAL   PRIMARY KEY,
    nome      VARCHAR(60) NOT NULL,
    token     VARCHAR(64) NOT NULL,
    ativo     BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_salao_mesa_token UNIQUE (token)
);
CREATE UNIQUE INDEX uk_salao_mesa_nome_lower ON salao_mesa (LOWER(nome));

CREATE TABLE salao_essencia_dia (
    data DATE        NOT NULL,
    sku  VARCHAR(50) NOT NULL,
    PRIMARY KEY (data, sku)
);

CREATE TABLE salao_solicitacao (
    id             BIGSERIAL    PRIMARY KEY,
    codigo         UUID         NOT NULL,
    mesa_id        BIGINT       NOT NULL REFERENCES salao_mesa (id),
    customer_id    BIGINT       NOT NULL REFERENCES customers (id),
    tier_id        BIGINT       REFERENCES session_tier (id),
    essencia_sku   VARCHAR(50)  NOT NULL,
    essencia_sku_2 VARCHAR(50),
    observacao     VARCHAR(200),
    status         VARCHAR(20)  NOT NULL,
    motivo         VARCHAR(200),
    comanda_id     BIGINT       REFERENCES comanda (id),
    criado_em      TIMESTAMPTZ  NOT NULL,
    decidido_em    TIMESTAMPTZ,
    decidido_por   VARCHAR(80),
    CONSTRAINT uk_salao_solicitacao_codigo UNIQUE (codigo),
    CONSTRAINT ck_salao_solicitacao_status CHECK (status IN ('PENDENTE', 'ACEITA', 'RECUSADA'))
);
CREATE INDEX idx_salao_solicitacao_status ON salao_solicitacao (status, criado_em);
CREATE INDEX idx_salao_solicitacao_mesa ON salao_solicitacao (mesa_id, status);
