-- Assistente de IA do admin (chat com Claude). Conversas por usuário, mensagens já sem dados
-- pessoais e as ações de escrita que o modelo propõe e o usuário confirma.

INSERT INTO permissions (name) VALUES ('ASSISTENTE_USE') ON CONFLICT (name) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_DEV') AND p.name = 'ASSISTENTE_USE'
ON CONFLICT DO NOTHING;

CREATE TABLE assistente_conversa (
    id            VARCHAR(36)  PRIMARY KEY,
    username      VARCHAR(80)  NOT NULL,
    titulo        VARCHAR(120),
    criada_em     TIMESTAMPTZ  NOT NULL,
    atualizada_em TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_assistente_conversa_usuario ON assistente_conversa (username, atualizada_em DESC);
CREATE INDEX idx_assistente_conversa_atualizada ON assistente_conversa (atualizada_em);

CREATE TABLE assistente_mensagem (
    id          VARCHAR(36) PRIMARY KEY,
    conversa_id VARCHAR(36) NOT NULL REFERENCES assistente_conversa (id) ON DELETE CASCADE,
    papel       VARCHAR(20) NOT NULL,
    texto       TEXT        NOT NULL,
    blocos      TEXT,
    links       TEXT,
    criada_em   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_assistente_mensagem_conversa ON assistente_mensagem (conversa_id, criada_em);

-- parametros é imutável depois de criado: o confirmar executa exatamente o que está aqui.
CREATE TABLE assistente_acao (
    id           VARCHAR(36)  PRIMARY KEY,
    conversa_id  VARCHAR(36)  NOT NULL REFERENCES assistente_conversa (id) ON DELETE CASCADE,
    mensagem_id  VARCHAR(36)  NOT NULL,
    username     VARCHAR(80)  NOT NULL,
    tipo         VARCHAR(40)  NOT NULL,
    parametros   TEXT         NOT NULL,
    titulo       VARCHAR(200) NOT NULL,
    campos       TEXT         NOT NULL,
    estado       VARCHAR(20)  NOT NULL,
    criada_em    TIMESTAMPTZ  NOT NULL,
    expira_em    TIMESTAMPTZ  NOT NULL,
    resultado    TEXT,
    links        TEXT,
    executada_em TIMESTAMPTZ
);

CREATE INDEX idx_assistente_acao_conversa ON assistente_acao (conversa_id);
CREATE INDEX idx_assistente_acao_pendente ON assistente_acao (estado, expira_em);
