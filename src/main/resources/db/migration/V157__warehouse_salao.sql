-- EST-F038 — depósito do Salão (essências das sessões de narguilé).
--
-- warehouse.type é VARCHAR(20) sem CHECK desde a V46: o valor novo 'SALAO' só precisa do enum.
--
-- A migração NÃO cria o depósito, de propósito. A existência de um SALAO ativo muda de onde a
-- sessão consome a lata (EstoqueService.consumeSession), e um Salão vazio no deploy faria todo
-- sabor sem lata aberta falhar no PDV até alguém transferir estoque. Quem liga é o dono, criando
-- o depósito pela API (POST /estoque/warehouses, type SALAO); na mesma transação as latas abertas
-- vão para ele.

-- Um Salão ativo por vez: é ele que a sessão consome, seja qual for o caixa da comanda. A
-- checagem do service dá a mensagem certa; este índice barra a corrida entre dois cadastros.
CREATE UNIQUE INDEX uk_warehouse_salao_ativo
    ON warehouse (type) WHERE type = 'SALAO' AND active;
