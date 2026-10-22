-- Autoatendimento do Salão, rodada 3: modelo semanal das essências, mesa ocupada já no pedido e as
-- compras da loja ligadas à mesa.
--
-- Decisões:
--  * Modelo semanal: a seleção por dia da semana (1 = segunda, ISO). A data sem seleção própria usa o
--    modelo do dia da semana dela — no admin e no QR. Gravar a data vazia volta para o modelo.
--  * A ocupação nasce no envio do pedido, não no aceite: o lead recebe o código de 4 dígitos já com o
--    pedido pendente. Sem comanda ainda, ela vale enquanto a solicitação de origem estiver PENDENTE;
--    recusada ou expirada sem nada lançado, a mesa fica livre de novo — derivado na leitura, sem job.
--    O aceite grava a comanda na mesma ocupação (o código não muda).
--  * Compra na loja: além do "comprou?" da V139, a venda do balcão pode ser ligada à mesa. Várias por
--    mesa; o QR mostra o que foi comprado.

CREATE TABLE salao_essencia_modelo (
    dia_semana SMALLINT    NOT NULL,
    sku        VARCHAR(50) NOT NULL,
    PRIMARY KEY (dia_semana, sku),
    CONSTRAINT ck_salao_essencia_modelo_dia CHECK (dia_semana BETWEEN 1 AND 7)
);

ALTER TABLE salao_mesa_ocupacao ALTER COLUMN comanda_id DROP NOT NULL;
ALTER TABLE salao_mesa_ocupacao ADD COLUMN solicitacao_id BIGINT REFERENCES salao_solicitacao (id);
ALTER TABLE salao_mesa_ocupacao ADD CONSTRAINT ck_salao_mesa_ocupacao_origem
    CHECK (comanda_id IS NOT NULL OR solicitacao_id IS NOT NULL);

CREATE TABLE comanda_compra_loja (
    comanda_id     BIGINT      NOT NULL REFERENCES comanda (id),
    order_id       BIGINT      NOT NULL REFERENCES sales_order (id),
    registrado_por VARCHAR(80) NOT NULL,
    registrado_em  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (comanda_id, order_id)
);
CREATE INDEX idx_comanda_compra_loja_order ON comanda_compra_loja (order_id);
