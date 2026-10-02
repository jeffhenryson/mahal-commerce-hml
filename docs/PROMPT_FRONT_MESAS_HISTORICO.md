# Prompt para o mahal-admin: histórico de mesas, sessão paga no final e "comprou na loja?" (02/10/2026)

O backend está no branch `fix/pdv-mesas-pedido` do `mahal-backend` (PDV-F029 e PDV-F034..F036).
Abaixo, o que falta no front. Cada item diz o que o backend já entrega.

Primeiro passo: regenerar o cliente OpenAPI (`ng-openapi-gen`). Os DTOs ganharam campos e há um
endpoint novo.

---

## 1. VENDAS › Mesas: aba "Histórico"

Hoje a tela só mostra as mesas abertas (`vendas-mesas.component.ts`, que diz no comentário que o
histórico "espera"). O backend pronto desde PDV-F029.

### Lista

`GET /pdv/comandas/history`, com `PDV_READ` ou `ORDER_READ`. Devolve mesas `FECHADA` e `CANCELADA`,
da mais recente para a mais antiga, em `PageResult<ComandaResponseDTO>`.

Filtros:

| Parâmetro | Uso na tela |
|---|---|
| `from`, `to` | Período do encerramento (ISO-8601). Padrão sugerido: hoje |
| `status` | `FECHADA` / `CANCELADA` (`ABERTA` dá 400) |
| `tableLabel` | Busca por mesa; o backend ignora caixa e espaços |
| `openedBy`, `closedBy` | Atendente |
| `customerId` | Cliente |
| `boughtInStore` | `true`/`false`: "Comprou na loja" / "Não comprou". Ausente traz todas |
| `page`, `size` | Paginação, `size` até 100 |

Colunas da tabela. No mobile, cards, como em Reservas:
- mesa (`tableOrCustomerLabel`);
- cliente (`customerName`);
- abertura e encerramento (`openedAt`, `closedAt`);
- duração (`durationMinutes`);
- status;
- quem abriu e quem encerrou (`openedBy`, `closedBy`; `system` = varredura automática, mostrar
  "Automático");
- total (`totalPaid`);
- sessões (`sessionsCount`);
- comprou na loja (`boughtInStore`: ✓, ✗ ou "—" quando nulo);
- motivo, se cancelada (`cancelReason`).

### Detalhe da mesa

Ao clicar numa linha, chamar `GET /pdv/comandas/{id}`, que serve para qualquer status. Além dos
campos da lista, traz:

- `items[]`: as linhas, com `pagarNoFinal` nas sessões;
- `orders[]`: os pedidos gerados, com `payments[]`. Linkar cada um para VENDAS › Pedidos;
- **`sessions[]` (PDV-F035)**: a linha do tempo de cada sessão. Desenhar uma barra horizontal por
  sessão com três segmentos:
  - **espera**: `esperaMin`. O rótulo vem de `esperouPor`: `PAGAMENTO` → "aguardando pagamento",
    `FILA` → "na fila"; se for nulo, o segmento não aparece;
  - **preparo**: `preparoMin`;
  - **na mesa**: `naMesaMin`.

  Mostrar também os horários `lancadaEm`, `pagaEm`, `inicioEm`, `entregueEm` e `recolhidaEm`.
  Duração nula quer dizer que a fase não terminou. `desistida = true` é a sessão recolhida sem ter
  sido entregue: mostrar um selo "desistida". Roshs (`mode = ROSH_EXTRA`) ficam agrupados sob a
  sessão de `linkedItemId`. `pagarNoFinal = true` leva o selo "pago no final";
- `aberturaAtePrimeiraSessaoMin` e `ultimoRecolhimentoAteEncerramentoMin`: dois textos curtos, como
  "1ª sessão 12 min após abrir" e "encerrada 8 min após recolher";
- `boughtInStore`, com botão para corrigir (ver item 3).

### Indicadores

`GET /pdv/comandas/analytics?from&to[&warehouseCode]`. O período máximo é de 366 dias. Os cards
acima da lista são:

- `mesas`, `ticketMedio`, `permanenciaMediaMin`, `receitaTotal`;
- `sessoesNarguile`:
  - `quantidade`, `receita`;
  - **médias de fase**: `esperaMediaMin`, `preparoMedioMin`, `naMesaMediaMin`, nulos quando não há
    dado;
  - `pagasNoFinal`, `desistidas`;
- **`compraNaLoja`**:
  - `taxaConversao` em %, com uma casa;
  - abaixo, "`compraram` de `respondidas` respondidas (`mesasComSessao` mesas com sessão)";
  - com `taxaConversao` nula, mostrar "sem respostas";
- `porAtendente`, `porMesa`, `porHora`: tabelas ou gráficos simples.

## 2. PDV › Mesa: "Pagar no final" ao lançar a sessão (PDV-F034)

**Backend pronto.** `POST /pdv/comandas/{id}/sessoes` e `POST .../sessoes/{itemId}/repetir` aceitam
`pagarNoFinal: true`. Nesse caso:

- A sessão vai **direto ao preparo** (`PREPARANDO`) e fica a receber até a conta. O 2º rosh herda
  a marca e sai da fila sem pagamento.
- Exige a permissão **`PDV_SESSION_PAY_LATER`**, hoje só do admin. Sem ela, o backend devolve
  `403 SESSION_PAY_LATER_NOT_ALLOWED`. Adicionar a permissão em `core/rbac/permissions.constants.ts`.
- "Repetir" **não herda** a marca da sessão de origem: o operador decide de novo.

No front (`pdv-comandas.component.ts`, diálogo de lançar sessão):

1. Mostrar o toggle **"Pagar no final"** só para quem tem `PDV_SESSION_PAY_LATER`.
2. Ao ligar o toggle, mostrar o **aviso de risco** antes de confirmar, por exemplo: "O narguilé vai
   para a mesa sem pagamento. Se o cliente sair sem pagar, o prejuízo é da casa. Confirmar?".
3. Na mesa, destacar a linha com `pagarNoFinal = true` e `closedInOrderId` nulo como **"a receber"**.
   Destacar também a mesa na lista do salão.
4. Fechar a conta: o fechamento total **continua recusando** mesa com narguilé no salão
   (`409 SESSION_NOT_COLLECTED`). Se o cliente pagar antes do recolhimento:
   - cobrar com `itemIds` (todas as linhas abertas);
   - recolher;
   - finalizar com `POST /finish`. A varredura também encerra a mesa toda paga.

   Se já estiver recolhido, o fechamento normal serve.

## 3. "O cliente comprou algo na loja?" (PDV-F036)

**Backend pronto.** `PUT /pdv/comandas/{id}/store-purchase` com corpo `{ "boughtInStore": true }`
e `PDV_COMANDA_MANAGE`. Comportamento:
- devolve `204`;
- vale em **qualquer status**;
- responder de novo sobrescreve;
- comanda inexistente dá `404 COMANDA_NOT_FOUND`.

Quando perguntar (um diálogo Sim/Não, com "Pular"):
1. Ao **recolher a última sessão** da mesa, quando nenhuma linha `SESSAO`/`ROSH_EXTRA` fica fora de
   `RECOLHIDO`.
2. Ao **encerrar a mesa** (`close` total ou `finish`), se ainda não houver resposta.
   `boughtInStore` vem no `GET /pdv/comandas/{id}`.
3. No **detalhe do histórico**, para responder ou corrigir.

"Pular" não chama o endpoint. "Não respondido" é diferente de "não comprou", e a taxa de conversão
só conta as respondidas.
