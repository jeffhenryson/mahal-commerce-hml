-- Autoatendimento do Salão, 3ª parte: proporção da mescla, perfis de sabor, chamados da mesa.
--
-- Decisões:
--  * Proporção (MEIO_A_MEIO, SETENTA_TRINTA, NOVENTA_DEZ): o 1º sabor é o principal. Não muda o
--    preço — a mescla segue cobrando a faixa mais cara (PDV-F051). Gravada na solicitação (o que o
--    lead pediu) e na linha da comanda (o que foi servido, para relatório de mistura). Só existe
--    com o 2º sabor.
--  * Perfis de sabor são por SKU, não por produto: há essência como produto simples e como
--    variação. salao_essencia_perfil_config marca o SKU que a casa já configurou — sem marca, o QR
--    sugere os perfis pelo nome (na leitura, sem gravar); com marca, vale o que a casa marcou,
--    inclusive nenhum.
--  * Chamados pertencem à comanda: a da mesa do QR (com a ocupação) ou a aberta pelo PDV, que o
--    cliente acha pela identificação confirmada. O ABERTO esquecido vence em 30 min na leitura, sem
--    job; quando a comanda fecha, os chamados dela saem da fila.

ALTER TABLE salao_solicitacao ADD COLUMN proporcao VARCHAR(20);
ALTER TABLE salao_solicitacao ADD CONSTRAINT ck_salao_solicitacao_proporcao
    CHECK (proporcao IS NULL OR (proporcao IN ('MEIO_A_MEIO', 'SETENTA_TRINTA', 'NOVENTA_DEZ')
        AND essencia_sku_2 IS NOT NULL));

ALTER TABLE comanda_item ADD COLUMN essence_ratio VARCHAR(20);
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_essence_ratio
    CHECK (essence_ratio IS NULL OR (essence_ratio IN ('MEIO_A_MEIO', 'SETENTA_TRINTA', 'NOVENTA_DEZ')
        AND essence_sku2 IS NOT NULL));

CREATE TABLE salao_essencia_perfil (
    sku    VARCHAR(50) NOT NULL,
    perfil VARCHAR(20) NOT NULL,
    PRIMARY KEY (sku, perfil),
    CONSTRAINT ck_salao_essencia_perfil
        CHECK (perfil IN ('FRUTADO', 'CITRICO', 'GELADO', 'DOCE', 'FLORAL', 'ESPECIARIAS'))
);

CREATE TABLE salao_essencia_perfil_config (
    sku           VARCHAR(50) PRIMARY KEY,
    atualizado_em TIMESTAMPTZ NOT NULL
);

CREATE TABLE salao_chamado (
    id              BIGSERIAL   PRIMARY KEY,
    comanda_id      BIGINT      NOT NULL REFERENCES comanda (id),
    mesa_id         BIGINT      REFERENCES salao_mesa (id),
    ocupacao_id     BIGINT      REFERENCES salao_mesa_ocupacao (id),
    tipo            VARCHAR(20) NOT NULL,
    forma_pagamento VARCHAR(20),
    status          VARCHAR(20) NOT NULL,
    criado_em       TIMESTAMPTZ NOT NULL,
    atendido_em     TIMESTAMPTZ,
    atendido_por    VARCHAR(80),
    CONSTRAINT ck_salao_chamado_tipo CHECK (tipo IN ('CONTA', 'RECOLHER', 'CARVAO', 'ATENDENTE')),
    CONSTRAINT ck_salao_chamado_forma CHECK (forma_pagamento IS NULL
        OR forma_pagamento IN ('PIX', 'CARTAO', 'DINHEIRO')),
    CONSTRAINT ck_salao_chamado_conta CHECK (tipo <> 'CONTA' OR forma_pagamento IS NOT NULL),
    CONSTRAINT ck_salao_chamado_status CHECK (status IN ('ABERTO', 'ATENDIDO')),
    CONSTRAINT ck_salao_chamado_atendido CHECK (status <> 'ATENDIDO' OR atendido_em IS NOT NULL),
    CONSTRAINT ck_salao_chamado_mesa CHECK ((mesa_id IS NULL) = (ocupacao_id IS NULL))
);
CREATE INDEX idx_salao_chamado_comanda ON salao_chamado (comanda_id, status);
CREATE INDEX idx_salao_chamado_status ON salao_chamado (status, criado_em);
