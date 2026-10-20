-- Autoatendimento do Salão, 2ª parte: mesa travada por aparelho, sessão completa pelo QR e carvão
-- jumbo cobrado.
--
-- Decisões:
--  * "Essências do dia" viram DESTAQUES: o QR oferece todas as latas abertas do Salão e as marcadas
--    aparecem no topo. A tabela salao_essencia_dia continua a mesma; muda só o significado.
--  * Ocupação: ao aceitar a 1ª solicitação de uma mesa livre, a mesa fica com o aparelho que pediu
--    (device_token, gerado no navegador) e ganha um código de 4 dígitos. Outro aparelho só pede ou
--    vê a mesa digitando o código. A ocupação acaba quando a comanda dela deixa de estar ABERTA —
--    derivado na leitura, sem job nem gancho no fechamento da comanda.
--  * A solicitação passa a carregar o que o lead escolheu: vaso, carvão, adicionais e se paga no
--    final (só VIP). O aceite usa essas escolhas em vez de valores fixos.

CREATE TABLE salao_mesa_ocupacao (
    id          BIGSERIAL   PRIMARY KEY,
    mesa_id     BIGINT      NOT NULL REFERENCES salao_mesa (id),
    comanda_id  BIGINT      NOT NULL REFERENCES comanda (id),
    customer_id BIGINT      NOT NULL REFERENCES customers (id),
    codigo      CHAR(4)     NOT NULL,
    aberta_em   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_salao_mesa_ocupacao_mesa ON salao_mesa_ocupacao (mesa_id, aberta_em DESC);
CREATE INDEX idx_salao_mesa_ocupacao_comanda ON salao_mesa_ocupacao (comanda_id);

CREATE TABLE salao_mesa_acesso (
    ocupacao_id  BIGINT      NOT NULL REFERENCES salao_mesa_ocupacao (id) ON DELETE CASCADE,
    device_token VARCHAR(64) NOT NULL,
    criado_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (ocupacao_id, device_token)
);

ALTER TABLE salao_solicitacao
    ADD COLUMN vaso_grande    BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN carvao         VARCHAR(10),
    ADD COLUMN adicional_ids  VARCHAR(200),
    ADD COLUMN pagar_no_final BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN device_token   VARCHAR(64);

ALTER TABLE session_settings
    ADD COLUMN upgrade_carvao_jumbo_preco NUMERIC(12, 2) NOT NULL DEFAULT 2.00;
