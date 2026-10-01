# Domínio: pedido

**Status:** 🟢 Operacional — visão do administrador entregue; o pedido nasce em **três** origens: `vendas-balcao` (canal `BALCAO`, venda pontual, e canal `MESA`, fechamento de comanda) e `ecommerce` (canal `MARKETPLACE`, checkout + webhook InfinitePay, Fatia 10, entregue em 2026-08-03)
**Pacote Java:** `com.cernecommerce.core.domain.model.pedido`
**Rota HTTP base:** `/orders`
**Última atualização deste doc:** 2026-08-30 — **PED-C002 + C003 + C001**. O N+1 de `GET /orders`
foi corrigido (ID-first + `JOIN FETCH`), e este doc, que estava congelado antes do canal `MESA`,
absorveu tudo que quatro entregas do PDV mudaram aqui sem passar por esta página: `MESA`,
`comandaId`/`tableLabel`, `serviceFeeAmount`, os quatro campos novos de `OrderItem` e oito
migrations. Ganhou também as seções **Regras de Negócio** e **Cobertura de Testes**, que nunca
existiram.

## Objetivo

O **documento de venda**, comum a todos os canais, e a superfície pelo qual o administrador o
consulta e o gerencia.

Existe um `Order` só, discriminado por `SalesChannel`, porque tudo que consome venda consome
"vendas, independente de canal": o extrato do cliente, o ledger de cashback, a devolução, o
faturamento, o documento fiscal e o relatório de margem. Duas tabelas fariam cada um desses
consumidores pagar um `UNION` ou duplicar lógica — e nenhuma interface em Java ajuda um `SELECT`.
Ver [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.1.

## Modelo de Domínio

| Tipo | Papel |
|---|---|
| `Order` | Cabeçalho: canal, status, cliente, sessão de caixa, depósito, totais, taxa de serviço, origem de mesa e carimbos de tempo |
| `OrderItem` | Item com **três valores congelados** (`unitPrice`, `costPrice`, `cashbackPercent`) mais o que a mesa trouxe: `mode`, `courtesy`, `notes`, `surchargeAmount` |
| `SalesChannel` | `BALCAO` \| `MARKETPLACE` \| `MESA` — a **origem**, imutável |
| `OrderStatus` | Máquina de estados, com as transições declaradas no próprio enum |
| `ConsumptionMode` | Por que a linha existe: `NORMAL` \| `OPEN_ROSH` \| `SABOR_EXTRA` \| `TROCA`. Ortogonal a `courtesy` |
| `DiscountProration` | Função pura que rateia um desconto de **conta** entre as linhas (PDV-F014) |

### O canal `MESA` e a origem de comanda

`MESA` entrou em PDV-F010 e é **imutável como os outros**: o pedido da mesa tem que **nascer**
`MESA` (`Order.openMesa`), não virar depois — por isso existe uma fábrica própria em vez de um
`withChannel`. Ele carrega dois campos que só existem nele, garantidos pelo compact constructor e
pelo `CHECK ck_sales_order_mesa_origin`:

- `comandaId` — redundante com `comanda.order_id`, que aponta de volta. A redundância evita um join
  reverso em toda página de Vendas &gt; Pedidos.
- `tableLabel` — **congelado no fechamento**, não lido da comanda: renomear a mesa depois não pode
  reescrever o histórico. Mesma razão de `order_item.product_name`.

### `netAmount` é receita; `totalPayable` é o que o cliente pagou

`netAmount = grossAmount − discountAmount − cashbackRedeemed` — o valor da **mercadoria** depois dos
abatimentos, e é ele que quatro agregações somam como receita.

A **taxa de serviço** (`serviceFeeAmount`, PDV-F015, só em `MESA`) fica **deliberadamente fora**
dele: os 10% são do garçom, e a loja apenas os repassa. Somá-la ao líquido inflaria receita e margem
com dinheiro que não é da casa, e obrigaria toda agregação futura a lembrar de subtraí-la. O que o
cliente paga é `Order.totalPayable()` = líquido + taxa, e é contra ele que o pagamento é validado e
o troco calculado. Fora da mesa os dois números coincidem sempre, porque a taxa é zero.

A conferência da gaveta não precisou de nada disso: `closeSession` soma `order_payment`, não
`netAmount` — o dinheiro da taxa passa pela gaveta como qualquer outro.

### `channel` é origem; `sessionId` é liquidação

São dimensões independentes, e confundi-las custaria caro. `channel` diz **onde o pedido nasceu** e
nunca muda. `sessionId` diz **qual caixa o liquidou**.

O caso que separa os dois: o cliente monta o pedido no aplicativo, vem à loja e paga no balcão. Esse
pedido continua `MARKETPLACE` — foi o site que o gerou, e é assim que ele tem que aparecer no
relatório de conversão — mas o dinheiro entrou numa gaveta específica, e o fechamento daquele caixa
precisa contabilizá-lo.

### Por que três valores são congelados no item

- `unitPrice` — sem ele, mudar o preço amanhã reescreveria o faturamento de ontem.
- `costPrice` — **o mais caro de retrofitar.** Sem ele, a próxima compra que alterar o custo do
  produto reescreve a margem histórica de **todos** os pedidos passados, e não há como reconstruir:
  o custo antigo não fica em lugar nenhum. Como o cashback sai da margem, isso não é detalhe
  contábil.
- `cashbackPercent` — sem ele, mudar a taxa amanhã reescreveria o valor gerado por pedidos de ontem.

Os três são **anuláveis** apenas nos pedidos anteriores à V65. Um default zero mentiria sobre a
margem; nulo diz a verdade, que é "não se sabe".

### O que a mesa acrescentou ao item

`mode` e `courtesy` (V114) e `notes`/`surchargeAmount` (V116) atravessam o fechamento da comanda e
chegam ao pedido. Não é duplicação: a pergunta *"qual pinça saiu com aquela mesa"* é feita **depois**
de a mesa ter fechado, e se o registro morresse na comanda a tela de Vendas &gt; Pedidos não teria
como respondê-la. `courtesy` é campo próprio e **não inferido de preço zero** — um desconto de 100%
dá o mesmo zero, e a margem precisa distinguir os dois. `surchargeAmount` é a parcela de `unitPrice`
que veio de acréscimo manual: `unitPrice` já é a soma, então sem o campo não há como separar
preço-base de "cobramos a mais". Todos são nulos/`NORMAL`/`false` em toda venda que não veio de mesa.

### Desconto de conta é rateado, não solto

`Order.discountAmount` é **derivado** da soma dos `OrderItem.discountAmount`. Quando o desconto é
pedido sobre a conta inteira — o abatimento de fim de noite no fechamento de mesa —, ele é rateado
proporcionalmente entre as linhas por `DiscountProration.distribute` antes de virar pedido. Um campo
de desconto solto no cabeçalho seria mais simples e faria a casa **pagar cashback sobre dinheiro que
não recebeu**, além de mostrar margem cheia numa venda abatida.

### Numeração

`order_number` vem de sequência própria (`order_number_seq`) e é emitido na **conclusão**, não na
criação: o `BIGSERIAL` do id deixa buracos quando uma transação faz rollback, e buraco em numeração
de documento fiscal é problema com o fisco. Pedidos anteriores à V65 têm prefixo `LEG-`.

## Estados e transições

```
   CRIADO ──────────────────────────────────────────► CONCLUIDO ──┐
      │  (balcão: nasce e termina na mesma transação)      │      │
      │                                                    │      │
  AGUARDANDO_PAGAMENTO ──┬── pagamento ──► PAGO ──► SEPARADO ──► ENVIADO ──► ENTREGUE
      │                  │                                                     │
      │                  └── retirada e pagamento no balcão ───────────────────┤
      ▼                                                                        ▼
  CANCELADO (só pré-pagamento)                              REEMBOLSADO (só pós-pagamento)
```

`CANCELADO` e `REEMBOLSADO` são os dois estados **terminais de verdade**, e são mutuamente
exclusivos por construção: estados pré-pagamento (`CRIADO`, `AGUARDANDO_PAGAMENTO`) só aceitam
`CANCELADO`; estados pós-pagamento (`PAGO`, `SEPARADO`, `ENVIADO`, `ENTREGUE`, `CONCLUIDO`) só
aceitam `REEMBOLSADO` — cancelar depois de pago devolveria mercadoria sem estornar o dinheiro já
recebido. `Order.refunded(reason, refundedAt)` garante essa invariante no próprio construtor
compacto.

`AGUARDANDO_PAGAMENTO → CONCLUIDO` é a retirada no balcão: terminou exatamente como uma venda de
balcão termina. Mandá-lo por `SEPARADO`/`ENVIADO` descreveria uma separação e um envio que não
aconteceram.

`RESERVADO` (venda de balcão paga, mercadoria ainda na loja — PDV-F008) sai por `CONCLUIDO`
(retirada, `Order.pickedUp`) ou `REEMBOLSADO`. Desde **PDV-F022** a venda com `delivery.type =
ENTREGA` também pode ir para `SEPARADO` e seguir a esteira de envio. A regra é por pedido,
`Order.allowedTransitions()`: o enum permite `RESERVADO → SEPARADO`, e o pedido o retira quando a
reserva não é uma ENTREGA. O `allowedTransitions` do DTO de administrador já vem dessa versão.

### `AGUARDANDO_PAGAMENTO → PAGO`: quem chama `Order.paid(...)`

Dois caminhos levam a `PAGO`, e nenhum dos dois é o cliente afirmando "eu paguei":

- **Liquidação no balcão** (`PdvService.settleOnlineOrder`) — o operador confirma o recebimento
  presencialmente, informando as formas de pagamento (`payments`, obrigatório desde **PDV-C015**);
  o pagamento vira linha `CAPTURED` no ledger e a cobrança de gateway aberta no checkout é
  encerrada como `CANCELLED`. ⚠️ **Correção de imprecisão deste doc:** ela não chama
  `Order.paid(...)` — chama `.concluded(...)`, que carimba `paidAt` por dentro
  (`Order.java:324`). O efeito sobre o pedido é o mesmo; a descrição não era.
- **Webhook do gateway** (`PaymentWebhookService.handleNotification`, ECM-F004/Fatia 10) — o
  InfinitePay notifica, e o service **reconsulta o gateway** (`PaymentGatewayPort.checkPayment`)
  antes de confiar em qualquer coisa; só chama `.paid(...)` depois de `OrderPayment` já estar
  `CAPTURED` e o valor pago bater com `netAmount()`. Ver
  [`ecommerce/README.md`](../ecommerce/README.md#gateway-de-pagamento-infinitepay--webhook-ecm-f004-fatia-10--entregue-2026-08-03)
  para o desenho completo do webhook — este README só documenta o efeito sobre o pedido.

## API — Endpoints

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `GET` | `/orders` | `ORDER_READ` | Filtros por `channel`, `status`, `customerId`, `from`, `to` e — PDV-F026 — `sessionId`, `comandaId`, `orderNumber` (exato); paginado. Cada linha traz `paymentMethods` (métodos `CAPTURED`, uma consulta por página) e — PED-F003 — `operatorName`, o usuário do caixa que liquidou o pedido |
| `GET` | `/orders/{id}` | `ORDER_READ` | Detalhe **com custo e margem**, e — PDV-F026 — `payments` (todas as linhas, com `channel`/`provider` de PDV-F025) |
| `POST` | `/orders/{id}/status` | `ORDER_FULFILL` | `SEPARADO`/`ENVIADO`/`ENTREGUE` |
| `PATCH` | `/orders/{id}/delivery` | `ORDER_FULFILL` | PDV-F022 — edita a entrega depois da venda (códigos da 99, rastreio, entregador, endereço); `type`/`fee` congelados |
| `POST` | `/orders/{id}/cancel` | `ORDER_CANCEL` | Cancela (só pré-pagamento) e **devolve a mercadoria ao estoque** |
| `POST` | `/orders/{id}/refund` | `ORDER_REFUND` | Reembolsa (só pós-pagamento): devolve estoque (com suporte a lote via `itemLots`), estorna cada pagamento `CAPTURED` com uma linha `REFUNDED` nova e reverte o cashback ganho — tudo em uma transação |
| `POST` | `/orders/bulk-status` | `ORDER_FULFILL` | `{orderIds (≤200), status}` — o "Liberar selecionados/todos". Cada pedido na própria transação; recusados em `failed` com `code` |
| `POST` | `/orders/{id}/payments/correction` | `ORDER_PAYMENT_CORRECT` ou `ORDER_PAYMENT_CORRECT_CLOSED` | PDV-F030 — corrige a forma de pagamento: `CAPTURED` → `CORRECTED` (lastro) + linhas novas `CAPTURED`; soma exata, sem troco; caixa fechado exige a segunda permissão e grava o delta em `cash_session_adjustment` |
| `GET` | `/orders/{id}/payment-history` | `ORDER_READ` | PDV-F030 — correções do pedido (`correctionId`, `at`, `by`, `reason`, `before`, `after`); `[]` sem correção |

Detalhes em [`docs/api-reference.md`](../../api-reference.md#pedidos-visão-do-administrador--orders).

### Quatro permissões, não uma

As consequências são muito diferentes: ler é inócuo, avançar estágio é operação de expedição,
cancelar **mexe no estoque**, e reembolsar mexe em estoque **+ pagamento + cashback** de um pedido
já pago. Uma permissão única obrigaria a conceder o reembolso para quem só precisa despachar
pedido — ou pior, para quem só cancela pedidos pré-pagamento.

### Custo e margem só aparecem aqui

O DTO do PDV omite os dois de propósito: `PDV_READ` é a permissão mais distribuída daquele módulo, e
o operador de caixa não precisa ver quanto a loja ganha por item.

`marginAmount` do pedido é **nulo, não parcial**, quando algum item não tem custo congelado. Somar
só os itens conhecidos produziria um número que *parece* a margem do pedido e não é — pior do que
não ter número.

## Integrações entre domínios

| Domínio | Relação |
|---|---|
| `vendas-balcao` | Cria o pedido de canal `BALCAO` e o conclui na mesma transação. **Desde PDV-F010, também a terceira origem:** o fechamento de comanda (`POST /pdv/comandas/{id}/close`) gera um pedido de canal `MESA` — é a única origem em que o pedido nasce da conversão de um **agregado de outro domínio** (`Comanda`), com os itens reconstituídos (`OrderItem.of`), nunca repreçados pelo catálogo |
| `estoque` | O cancelamento e o reembolso devolvem mercadoria com `adjustStock(ENTRADA)` (reembolso com suporte a lote); a liquidação de pedido online consome reserva |
| `ecommerce` | Cria o pedido de canal `MARKETPLACE` no checkout (`Order.openMarketplace(...)`, Fatia 9) e o leva a `PAGO` via webhook do gateway (`PaymentWebhookService`, Fatia 10) |
| `crm` | `customerId` alimenta o extrato do cliente (`CRM-F001`); o reembolso reverte o cashback ganho via `cashbackUseCase.reverseEarningsForOrder` |
| `financeiro` | Consome o pedido para DRE e provisão de cashback (`FIN-F001`) |

## Regras de Negócio Implementadas

> Preenchida em **PED-C001**, a partir do código — não da entrega. As regras de fechamento de
> comanda que *produzem* o pedido de mesa (rateio, taxa, cortesia) estão em
> [`vendas-balcao`](../vendas-balcao/README.md#regras-de-negócio-implementadas); aqui ficam as do
> documento em si.

| Regra | Onde | Teste |
|---|---|---|
| **Invariantes do cabeçalho (`Order`)** | | |
| Canal determina campo obrigatório: `MARKETPLACE` exige `customerId`; `BALCAO` e `MESA` exigem `sessionId` | `Order` (compact constructor) | `OrderTest.marketplaceRequiresCustomer`, `balcaoRequiresSession` |
| `MARKETPLACE` **pode** ter `sessionId` — é o pedido do app pago no balcão. Canal é origem, sessão é liquidação | `Order` (compact constructor, sem proibição) | `OrderTest`, `PdvServiceTest.settleOnlineOrder_keepsTheChannelAndAttachesTheCashSession` |
| `comandaId`/`tableLabel` só existem em `MESA`, e são **obrigatórios** nele | `Order` (compact constructor), `CHECK ck_sales_order_mesa_origin` | `OrderTest.openMesa_startsInCriadoCarryingTheComandaOrigin`, `OrderTest.mesaRequiresComandaAndTableLabel` |
| `serviceFeeAmount` só existe em `MESA` — no balcão não há serviço a cobrar | `Order` (compact constructor), `CHECK ck_sales_order_service_fee_only_mesa` | `OrderTest.serviceFeeOnlyExistsInMesa` |
| `netAmount = grossAmount − discountAmount − cashbackRedeemed`, sempre recalculado, nunca aceito do cliente | `Order` (compact constructor) | `OrderTest.rejectsNetAmountThatDoesNotMatchTheOtherTotals` |
| A taxa de serviço **não entra** no `netAmount`: o líquido é receita da casa, a taxa é repasse. O que o cliente paga é `totalPayable()` | `Order.totalPayable`, `withServiceFeeOf` | `OrderTest.withServiceFeeOf_leavesNetAmountUntouchedAndOnlyMovesTotalPayable`, `totalPayable_equalsNetAmountWhenThereIsNoServiceFee` |
| `changeAmount` não existe em `MARKETPLACE` — troco só onde há dinheiro em espécie | `Order` (compact constructor) | `OrderTest.changeAmountOnlyExistsInBalcao` |
| `CANCELADO`⇔`cancelledAt` e `REEMBOLSADO`⇔`refundedAt` são consistência obrigatória, espelhada por `CHECK` | `Order` (compact constructor) | `OrderTest.cancelledStatusAndTimestampMustAgree`, `refundedStatusAndTimestampMustAgree` |
| A lista de itens é copiada defensivamente e nunca pode ser vazia | `Order` (compact constructor, `List.copyOf`) | `OrderTest` |
| **Numeração fiscal** | | |
| `orderNumber` vem de sequência **própria**, não do `BIGSERIAL`: rollback deixa buraco, e buraco em numeração fiscal é problema com o fisco | `order_number_seq` (V65), `OrderRepositoryImpl.nextOrderNumber` | `PedidoRepositoryIT.nextOrderNumber_worksOnTheConfiguredDialect`, `nextOrderNumber_neverRepeats` |
| É emitido só na **conclusão** ou na reserva — nunca na criação em memória | `Order.concluded`/`Order.reserved` | `PdvCashCycleIT.orderNumbersAreUniqueAcrossSales` |
| **Máquina de estados (`OrderStatus`)** | | |
| Transições seguem estritamente a tabela do enum; fora dela é `InvalidOrderStatusTransitionException` | `OrderStatus.canTransitionTo` | `OrderStatusTest.everyStatusDeclaresItsTransitions`, `nullTargetIsNeverAllowed` |
| Máquina estritamente partida: pré-pagamento só alcança `CANCELADO`; pós-pagamento só alcança `REEMBOLSADO` | `OrderStatus` (tabela de transições) | `OrderStatusTest.prePaymentStatesCanBeCancelledButNotRefunded`, `postPaymentStatesCanBeRefundedButNotCancelled` |
| `CANCELADO` e `REEMBOLSADO` são os únicos terminais | `OrderStatus` | `OrderStatusTest.cancelledAndRefundedAreTheOnlyTerminalStates` |
| `RESERVADO` é inalcançável pelo caminho do marketplace — por construção, sem checagem de canal em lugar nenhum | `OrderStatus` (só a partir de `CRIADO`) | `OrderStatusTest.reservadoIsUnreachableFromTheMarketplacePath`, `OrderTest.reserved_isUnreachableAfterTheOrderIsAlreadyConcluded` |
| `RESERVADO → CONCLUIDO` (retirada) carimba `concludedAt`, diferente do `withStatus` genérico | `Order.pickedUp` | `OrderTest.pickedUp_stampsConcludedAtAndKeepsReservedAtAsHistory`, `OrderServiceTest.changeStatus_reservadoParaConcluido_usaPickedUpEStampaConcludedAt` |
| **Item (`OrderItem`)** | | |
| `unitPrice`/`costPrice`/`cashbackPercent` são snapshots congelados; mudança no catálogo não reescreve pedido passado | `OrderItem.fromCatalog` | `OrderItemTest.fromCatalog_freezesPriceAndCostFromPricing`, `PedidoRepositoryIT.save_persistsEveryFrozenValueOfTheItem` |
| Custo e cashback são **nulos**, não zero, em pedido legado — zero mentiria sobre a margem | `OrderItem` | `PedidoRepositoryIT.save_keepsCostAndCashbackNullWhenTheyWereNeverKnown` |
| `discountAmount ≤ quantity × unitPrice` — desconto que zera o item é devolução, não venda | `OrderItem` (compact constructor) | `OrderItemTest.rejectsDiscountGreaterThanGross`, `acceptsDiscountEqualToGross` |
| Venda abaixo do custo é **sinalizada**, nunca bloqueada — queima de estoque é decisão comercial | `OrderItem.marginAmount()` | `OrderItemTest.marginAmount_isNegativeWhenSellingBelowCost` |
| `cashbackPercent` em `[0,100]`, com escala 4 (é input de fórmula, não valor de exibição) | `OrderItem` (compact constructor) | `OrderItemTest.rejectsCashbackPercentOutOfRange`, `PedidoRepositoryIT.save_persistsCashbackPercentWhenStamped` |
| **Rateio de desconto de conta (`DiscountProration`)** | | |
| A soma do rateio bate **no centavo** com o desconto pedido | `DiscountProration.distribute` | `DiscountProrationTest.distribute_alwaysSumsExactlyToTheRequestedDiscount` |
| Nenhuma linha recebe mais desconto que o próprio valor — nem no ajuste da sobra | `DiscountProration.distribute` | `DiscountProrationTest.distribute_neverGivesALineMoreDiscountThanItsOwnAmount` |
| Linha de valor zero (cortesia) absorve zero **por construção** | `DiscountProration.distribute` | `DiscountProrationTest.distribute_givesZeroToCourtesyLinesByConstruction` |
| **Cancelamento e reembolso (`OrderService`)** | | |
| Cancelar (pré-pagamento) libera a **reserva**, nunca ajusta saldo real — venda pré-paga nunca teve baixa de verdade | `OrderService.cancelOrder` | `OrderServiceTest.cancelOrder_releasesTheReservationInsteadOfTouchingRealStock` |
| Reembolsar (pós-pagamento) devolve estoque, estorna cada pagamento `CAPTURED` e reverte cashback `EARNED` — tudo na mesma transação | `OrderService.refundOrder` | `OrderServiceTest.refundOrder_returnsTheGoodsToStock`, `refundOrder_reversesEachCapturedPaymentWithMatchingMethodAndAmount`, `refundOrder_invokesCashbackReversalForTheOrder` |
| Reembolso funciona em pedido já entregue — é devolução, não desfazer venda | `OrderService.refundOrder` | `OrderServiceTest.refundOrder_worksOnADeliveredOrderBecauseThatIsAReturn` |
| Duplo cancelamento/reembolso é barrado pela própria máquina de estados, não por flag | propagação de `InvalidOrderStatusTransitionException` | `OrderServiceTest.cancelOrder_refusesToCancelTwiceAndDoesNotReleaseAgain`, `refundOrder_refusesToRefundTwiceAndDoesNotTouchStock` |
| Reembolso concorrente: só um sucede | `@Version` em `sales_order` | `OrderRefundConcurrencyIT.concurrentRefunds_onlyOneSucceedsAndEffectsAreNotDuplicated` |
| **Correção da forma de pagamento (PDV-F030)** | | |
| Nada é apagado: as `CAPTURED` vigentes viram `CORRECTED` (com `correction_id`) e as novas nascem `CAPTURED` (com `origin_correction_id`) | `OrderService.correctPayments`, `OrderPayment` | `OrderServiceTest.correctPayments_retiresCapturedLinesAndCapturesTheNewOnes`; `PagamentoPostgresIT.correction_writesCorrectedAndNewRowsThatSatisfyTheV136Checks` |
| Soma exata (`totalPayable − marcado`), sem troco nem em dinheiro; o troco antigo vai a zero | `OrderService.correctPayments` | `correctPayments_refusesASumDifferentFromTotalPayable_evenInCash`, `correctPayments_cashWithChangeBecomesExactAndZeroesTheChange` |
| Motivo obrigatório; pedido cancelado/reembolsado ou pago pelo app não se corrige; `GATEWAY_PIX`/`MARCADO` não entram | `OrderService.correctPayments` | `correctPayments_requiresAReason`, `correctPayments_refusesRefundedOrder`, `correctPayments_refusesGatewayPaidOrder` |
| Caixa fechado exige `ORDER_PAYMENT_CORRECT_CLOSED` e grava o delta por método, sem reescrever o esperado | `OrderService.correctPayments` | `correctPayments_closedSessionWithoutManagerPermission_isRefused`, `correctPayments_closedSessionWithManagerPermission_recordsTheDivergencePerMethod`; `PdvCashCycleIT.correctPayments_afterClose_recordsTheAdjustmentOnTheClosedSession` |
| **Liberação em lote** | | |
| Um pedido recusado não desfaz os outros; ids repetidos contam uma vez | `OrdersController.changeStatusInBulk` (sem `@Transactional`; cada `changeStatus` é a sua transação) | `OrdersControllerTest.changeStatusInBulk_cadaPedidoFalhaSozinho`, `changeStatusInBulk_listaVazia_retorna400` |
| **Leitura (`GET /orders`)** | | |
| A listagem carrega os itens em **duas fases** (página sem fetch → `JOIN FETCH` por ids): o número de consultas não cresce com o tamanho da página (PED-C002) | `OrderRepositoryImpl.withItems`, `OrderJpaRepository.findAllByIdsWithItems` | `PedidoRepositoryIT.findAll_doesNotScaleQueriesWithThePageSize`, `findBySessionId_doesNotScaleQueriesWithThePageSize` |
| Filtro opcional usa `Specification`, não `(:param IS NULL OR ...)` — o padrão antigo fazia o Postgres recusar inferir o tipo do bind de `Instant` nulo | `OrderRepositoryImpl.findAll` | `PedidoRepositoryPostgresIT.findAll_withoutFilters_doesNotThrowOnRealPostgres` e os dois irmãos |
| Ordem é `id DESC` (mais recentes primeiro), preservada depois do fetch em lote | `OrderRepositoryImpl` (as duas fases ordenam igual) | `PedidoRepositoryIT.findAll_keepsTheMostRecentFirstAfterTheBatchFetch`, `findBySessionId_returnsMostRecentFirst` |
| Os **itens dentro** de cada pedido saem na ordem de lançamento (`id ASC`). A ordem era o que o banco quisesse — invisível enquanto cada pedido vinha de uma consulta própria, e dependente da intercalação do join ao trazer vários de uma vez | `@OrderBy("id ASC")` em `OrderEntity.items`, mesmo padrão de `ComandaEntity.items` — e não um `ORDER BY` na consulta, que valeria só para ela | `PedidoRepositoryIT.findAll_keepsItemsInLaunchOrderWithinEachOrder` |

## Cobertura de Testes

| Arquivo | Tipo | O que cobre |
|---|---|---|
| `OrderTest` | Unit (domínio) | `openBalcao`/`openMarketplace`/`openMesa`, todas as transições (incl. `reserved`/`pickedUp`/reembolso pós-reserva), invariantes do compact constructor, taxa de serviço (`withServiceFeeOf`/`totalPayable`), cópia defensiva de itens |
| `OrderItemTest` | Unit (domínio) | `fromCatalog` (resolução de preço/custo), derivação de margem e cashback, violações de invariante |
| `OrderStatusTest` | Unit (domínio) | Completude da tabela de transições, estados terminais, caminhos por canal, alcance de `RESERVADO` |
| `DiscountProrationTest` | Unit (domínio) | O rateio do desconto de conta: proporcionalidade, soma exata ao centavo em 16 combinações, teto por linha, cortesia, desconto de 100% e as quatro recusas |
| `OrderServiceTest` | Unit (Mockito) | `changeStatus` (incl. o caso especial `pickedUp`), `cancelOrder` (libera reserva), `refundOrder` (fan-out de estoque/pagamento/cashback, com e sem lote), guardas de duplo cancelamento/reembolso |
| `OrderReportServiceTest` | Unit (Mockito) | Agregações de `GET /orders/summary` e validação de período |
| `OrdersControllerTest` | MockMvc standalone | Contrato HTTP das rotas de leitura e de mudança de status |
| `OrdersControllerSecurityTest` | MockMvc + Security | 401/403 por autoridade (`ORDER_READ`/`ORDER_FULFILL`/`ORDER_CANCEL`/`ORDER_REFUND`) e os 404s |
| `PedidoRepositoryIT` | `@SpringBootTest` + `@Transactional` | Numeração por sequência, round-trip de todo campo congelado, nulos legítimos do pedido legado, timestamps da esteira, filtros de `findAll`, e (PED-C002) a **contagem de consultas** que prova o fim do N+1, mais ordem, página vazia e página além do fim |
| `PedidoRepositoryPostgresIT` | `@SpringBootTest` + Testcontainers (`ENABLE_TC=true`) | As particularidades do dialeto Postgres que o H2 em `MODE=PostgreSQL` não reproduz: os dois bugs de bind de `Instant` nulo, e o fetch em lote de PED-C002 com dados reais |
| `OrderReportRepositoryIT` | `@SpringBootTest` | As queries de agregação de `GET /orders/summary` |
| `OrderRefundIT` | `@SpringBootTest` | Reembolso e cancelamento fim a fim |
| `OrderRefundConcurrencyIT` | `@SpringBootTest`, sem `@Transactional` | Reembolso concorrente: só um sucede, e os efeitos não são duplicados |

**Lacunas conhecidas** (registradas para não maquiar como "tudo coberto"):

1. **`PedidoRepositoryPostgresIT` é opt-in** (`ENABLE_TC=true`) e não roda no CI padrão. As
   particularidades de dialeto que ele cobre — incluindo o fetch em lote de PED-C002 — não têm
   guarda de regressão automática.
2. **O contador de consultas de PED-C002 compara cardinalidades, não um número absoluto.** É a
   afirmação honesta possível (os itens estariam acessíveis nos dois desenhos, porque a leitura
   acontece dentro da transação), mas significa que uma consulta extra *constante* passaria batido.
3. **`OrderReportService`/`summarize` não têm teste de contagem de consultas.** O N+1 de PED-C002
   estava na listagem; ninguém auditou as agregações do mesmo jeito.
4. A **liquidação de pedido online** (`settleOnlineOrder`) é testada em `PdvServiceTest`, do lado do
   PDV — este módulo não tem teste próprio do caminho, embora o pedido seja o agregado alterado.

## Schema de Banco (Migrations)

| Migration | O que faz |
|---|---|
| V57 | Criou `cash_register_sale` e `sale_item` |
| **V65** | Renomeia para `sales_order` / `order_item`, acrescenta canal, status, numeração, cliente, desconto, cashback resgatado, troco, carimbos e `@Version`; cria `order_number_seq` |
| **V67** | Permissões `ORDER_READ`, `ORDER_FULFILL`, `ORDER_CANCEL` |
| **V71** | `pedido_reembolso` — coluna `refunded_at` e expansão do `CHECK` de status para aceitar `REEMBOLSADO`, com constraint garantindo `(status = REEMBOLSADO) == (refunded_at IS NOT NULL)` |
| **V72** | Permissão `ORDER_REFUND`, concedida a `ROLE_ADMIN` |
| **V93** | Índice em `sales_order.sold_at` (a coluna que guarda `createdAt`, nome herdado da V57) |
| **V98** | `pedido_reservado` — status `RESERVADO` e coluna `reserved_at` (PDV-F008): venda de balcão paga e baixada do estoque, aguardando retirada |
| **V99** | `order_item.product_name` — nome congelado na venda, para o histórico não ser reescrito por um rename no catálogo |
| **V100** | `separated_at`/`shipped_at`/`delivered_at` — os carimbos por etapa da esteira de fulfillment |
| **V113** | `pedido_canal_mesa` — canal `MESA`, `comanda_id`, `table_label`, `CHECK ck_sales_order_mesa_origin`, e os quatro `CHECK`s da V65 reescritos para admitir o canal novo |
| **V114** | `order_item.mode` e `order_item.courtesy` — o modo de consumo e a cortesia atravessam o fechamento da comanda |
| **V116** | `order_item.notes` e `order_item.surcharge_amount` (PDV-F011) — o setup da mesa e a parcela de acréscimo manual, herdados no fechamento |
| **V118** | `sales_order.service_fee_amount` (PDV-F015), `NOT NULL DEFAULT 0` + `CHECK ck_sales_order_service_fee_only_mesa`. **Coluna própria, fora de `net_amount`** — ver "netAmount é receita" acima |
| **V136** | PDV-F030 — `order_payment_correction`, `cash_session_adjustment`, colunas de correção em `order_payment`, status `CORRECTED`, permissões `ORDER_PAYMENT_CORRECT`/`_CLOSED`. O comentário da migration diz "PDV-F027", número trocado depois para não colidir com as sessões paralelas |
| **V137** | CRM-F010 — `order_payment.due_date`, método `MARCADO`, status `ON_ACCOUNT`; o recebível em si está no README do [CRM](../crm/README.md) |

> As migrations de permissão do fechamento de mesa (V115 `PDV_COMANDA_COURTESY`, V117
> `PDV_COMANDA_SURCHARGE`, V119 `PDV_COMANDA_DISCOUNT`) pertencem a
> [`vendas-balcao`](../vendas-balcao/README.md#schema-de-banco-migrations): protegem a operação de
> comanda, não a leitura de pedido.

## Testes no Postman

Coleção: [`pedido.postman_collection.json`](pedido.postman_collection.json).

```bash
npx newman run docs/dominios/pedido/pedido.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

**Pré-requisito:** rode antes a coleção de [`vendas-balcao`](../vendas-balcao/README.md) para existir
ao menos um pedido.

> ⚠️ A pasta `03` **cancela um pedido de verdade** e devolve os itens ao estoque. Rode em base de
> desenvolvimento.

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| PED-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | Este README foi escrito junto com a entrega, não a partir de auditoria do código. Faltam Regras de Negócio e Cobertura de Testes no padrão de [`estoque`](../estoque/README.md). | ✅ Fechado (2026-08-30) — seções Regras de Negócio e Cobertura de Testes escritas a partir do código, com quatro lacunas registradas |
| PED-C002 | 🔴 Alta | Correção | n-mais-1-em-get-orders | **N+1 confirmado** em `OrderRepositoryImpl.findAll`/`findBySessionId` (`adapter/out/persistence/repository/OrderRepositoryImpl.java:90-121`), usado por `GET /orders` (endpoint admin mais provável de rodar sob carga, `size` máx. 100 — `OrdersController.java:113`). `OrderEntity.items` é `@OneToMany(fetch = FetchType.LAZY)` (`OrderEntity.java:108`) e `toDomain(OrderEntity e)` (linha 142) itera `e.getItems()` por entidade da página, sem JOIN FETCH nem `@EntityGraph` — até **101 queries** por página de 100 pedidos. Aplicar o mesmo padrão de duas fases (IDs paginados → `SELECT DISTINCT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.id IN (:ids)`) já usado em `ProductRepositoryImpl.findAllByIdsWithVariants`. Achado em auditoria `analyze-domain`/performance de 2026-08-18. | ✅ Fechado (2026-08-30) — ID-first + `JOIN FETCH`, com contagem de consultas provando que não cresce com a página. Ver Histórico abaixo |
| PED-F001 | 🟢 Baixa | Feature | filtro-por-numero-do-pedido | `GET /orders?orderNumber=` — hoje só dá para achar um pedido pelo id interno, e o número é o que o cliente tem em mãos. | ✅ Fechado (2026-09-28) — entregue junto com PDV-F026 (`GET /orders?orderNumber=`, comparação exata com o número de 9 dígitos; a busca por número parcial ficou em PED-C012). Status corrigido na análise de 2026-10-01. |
| PED-C003 | 🟡 Importante | Correção | readme-de-pedido-congelado-antes-do-canal-mesa | PDV-F010 mudou este domínio em 2026-08-27 e a documentação não acompanhou. (1) A tabela de Modelo de Domínio descreve `SalesChannel` como `BALCAO` \| `MARKETPLACE`, mas `MESA` existe no enum (`core/domain/model/pedido/SalesChannel.java`) e no schema desde a V113 — junto com `Order.openMesa`, os campos `comandaId`/`tableLabel` e o CHECK `ck_sales_order_mesa_origin`. (2) A seção Integrações entre Domínios não menciona `Comanda`: o fechamento de mesa (`POST /pdv/comandas/{id}/close`) é hoje uma terceira origem de pedido, ao lado de balcão e marketplace, e é a única em que o canal nasce de um agregado de outro domínio. (3) A tabela de Schema para na V72 — faltam V98 (`RESERVADO`), V99 (`product_name`), V100 (timestamps da esteira), V113 (canal `MESA`, `comanda_id`, `table_label`, os quatro CHECKs da V65 reescritos) e V114 (`mode`/`courtesy` em `order_item`). Mesma dívida que PDV-C006 fechou para `vendas-balcao`: a entrega atravessou dois domínios e só um foi documentado. | ✅ Fechado (2026-08-30) — Modelo de Domínio, Integrações e Schema absorveram `MESA`, a taxa de serviço, os campos de mesa no item e oito migrations |
| PED-F004 | 🟢 Baixa | Feature | paginacao-server-side-por-cliente | `GET /orders?customerId=` já existe e é o caminho oficial para o histórico de pedidos do cliente (substituiu o placeholder `GET /crm/customers/{id}/orders`, ver `CRM-F001`), mas o front (`mahal-admin`) hoje busca 50 de uma vez e pagina em memória (5/10/15/25) porque `page`/`size` não é usado nesse caminho — vira pesado em cliente com histórico grande. `GET /orders` já aceita `page`/`size`; falta só o front trocar a chamada — item de coordenação, não de código novo no backend. Sinalizado pelo front em `Docs/BACKEND_TODO.md`, 2026-08-18. | Pendente — era PED-F003, renumerado em 2026-10-01 porque o ID foi reusado em `operador-na-lista-de-pedidos` (Histórico). |
| PED-C004 | 🟡 Importante | Correção | estorno-devolve-o-valor-entregue-nao-o-recebido | `OrderService.refundOrder` estorna `original.amount()` de cada linha `CAPTURED` (`OrderPayment.refunded`, "o que voltou é exatamente o que entrou"). Em `DINHEIRO` isso não é o que entrou: `order_payment.amount` é o valor **entregue** pelo cliente, e o troco já saiu da gaveta no ato da venda (`Order.changeAmount`). Numa venda de R$22 paga com R$25, o estorno devolve R$25 — R$3 a mais do que a loja recebeu, e a diferença é exatamente o troco que o cliente já levou. Achado colateral de **PDV-C017**, que corrigiu a fórmula do fechamento de caixa: ela agora é fiel ao que este código faz (subtrai o entregue no estorno e o troco na venda, e por isso fecha), mas o valor estornado em si continua errado — o defeito ficou correto na conferência e errado no bolso. O caminho provável é estornar `amount − rateio do changeAmount da linha`, ou gravar o retido em vez do entregue; as duas mexem em contrato, e **quanto dinheiro volta para o cliente é decisão do dono**, não da correção. | Pendente |
| PED-C005 | 🔴 Alta | Correção | save-regrava-itens-do-pedido | `OrderRepositoryImpl.save` (`:74-93`) faz `entity.getItems().clear()` e reinsere os itens em **todo** save — o comentário diz que o caminho só roda antes da conclusão, mas `changeStatus`, `refundOrder`, `updateDelivery` e `correctPayments` passam por ele. Com `orphanRemoval`, os `order_item` antigos são apagados, e `cashback_entry.order_item_id REFERENCES order_item(id)` (V70:52) nunca foi removida: reembolso ou RESERVADO→CONCLUIDO de pedido com cashback EARNED deve falhar no Postgres com 23503. As ITs rodam em H2, onde a FK não existe. Análise de 2026-10-01; a confirmar com IT em Postgres antes de corrigir. | Pendente |
| PED-C006 | 🔴 Alta | Correção | mudanca-de-status-generica-pula-pagamento | `OrderStatus` permite `AGUARDANDO_PAGAMENTO → PAGO/CONCLUIDO`, e `OrderService.changeStatus` usa o `withStatus` genérico: `POST /orders/{id}/status` e `/orders/bulk-status` marcam pedido do app como pago ou concluído **sem pagamento, sem número, sem `concludedAt` e sem consumir a reserva** — que depois expira e devolve o saldo de mercadoria contada como vendida. Os caminhos reais (webhook, `settleOnlineOrder`) fazem tudo isso. Correção: o caminho genérico aceita só a esteira (SEPARADO/ENVIADO/ENTREGUE, CONCLUIDO a partir de RESERVADO). Análise de 2026-10-01. | Pendente |
| PED-C007 | 🟡 Importante | Correção | reembolso-de-mesa-devolve-estoque | `refundOrder` faz `adjustStock(ENTRADA, quantity)` em toda linha de catálogo. No pedido `MESA` isso devolve unidade inteira de essência que consumiu **uso de lata** (o `OrderItem` não carrega `packageUses`), e devolve cortesia e `TROCA` — contradiz `ComandaService.undoStock`, que recusa exatamente essa `ENTRADA`. Decisão do dono (2026-10-01): reembolso de MESA é só financeiro, não toca estoque. Análise de 2026-10-01. | Pendente |
| PED-C008 | 🟡 Importante | Correção | bulk-status-para-no-meio | `OrdersController.bulkStatus` captura só `OrderNotFoundException`, `InvalidOrderStatusTransitionException` e `ObjectOptimisticLockingFailureException`. Alvo REEMBOLSADO/CANCELADO passa a tabela de transição e estoura `IllegalArgumentException` no construtor de `Order`: o laço para, os pedidos anteriores já foram gravados e auditados, e o cliente recebe 400 "Requisição inválida" sem `ok`/`failed`. Análise de 2026-10-01. | Pendente |
| PED-C009 | 🟡 Importante | Correção | estorno-contado-na-sessao-de-origem | `sumRefundedAmountBySessionIdAndMethod` agrupa pelo `sessionId` do pedido — a sessão que vendeu —, e `refundOrder` não exige nem registra a sessão que paga. Estornar hoje, em dinheiro, a venda de uma sessão já fechada: a cédula sai da gaveta de B, o esperado de B não sabe, e o `/payment-totals` da sessão A muda depois de fechada. Análise de 2026-10-01. | Pendente |
| PED-C010 | 🟡 Importante | Correção | reembolso-de-marcado-ignora-o-quitado | `refundOrder` só estorna linhas `CAPTURED`, e `ReceivableService.cancelOpenForOrder` cancela o recebível aberto. O que o cliente já pagou do marcado (quitação parcial, ou recebível QUITADO) nunca volta. Análise de 2026-10-01. | Pendente |
| PED-C011 | 🟡 Importante | Correção | correcao-de-pagamento-sem-trava | `correctPayments` não trava o pedido e só salva o cabeçalho quando há troco, então o `@Version` normalmente não sobe. Correção e reembolso concorrentes: o reembolso estorna P1, a correção aposenta P1 e cria P2 CAPTURED — o pedido fica REEMBOLSADO com P2 nunca estornado. Análise de 2026-10-01. | Pendente |
| PED-C012 | 🟢 Melhoria | Correção | validacoes-e-pontas-do-pedido | (1) `OrderRefundRequest.itemLots` sem `@Valid`/`@Size`; listas de `SaleRequest`, `SettleOnlineOrderRequest`, `OrderPaymentCorrectionRequest` e `CloseComandaRequest.itemIds` sem `@Size`. (2) `?orderNumber=` compara exato com o número de 9 dígitos com zeros — `123` não acha. (3) `cancelOrder` deixa a cobrança PIX `PENDING` aberta. (4) `operatorName` não vem nas respostas de correção, status, cancelamento, reembolso e entrega. Análise de 2026-10-01. | Pendente |

`PDV-F007` (status `REEMBOLSADO`, distinto de `CANCELADO`) foi entregue em 2026-07-29 — ver
[Histórico de Implementações](#histórico-de-implementações) abaixo e o registro em
[`vendas-balcao`](../vendas-balcao/README.md).

## Histórico de Implementações

- **2026-10-01** — `operador-na-lista-de-pedidos` (**PED-F003**): `operatorName` em `GET /orders`
  e `GET /orders/{id}`, vindo de `cash_register_session.operator` (uma consulta por página). Coberto
  por `OrdersControllerTest` e `PdvDeliveryPostgresIT`.

- **2026-09-30** — `correcao-da-forma-de-pagamento` (**PDV-F030**, **V136**): `POST
  /orders/{id}/payments/correction` e `GET /orders/{id}/payment-history`, com lastro (`CORRECTED`) em
  vez de update e ajuste em caixa fechado. `GET /orders/{id}` ganhou `paymentStatus`/`receivableId`
  (CRM-F010). Coberto por `OrderServiceTest`, `OrdersControllerSecurityTest`, `PdvCashCycleIT` e
  `PagamentoPostgresIT`. Commits `b1bdc90` e `32e0c87` (renumeração).
- **2026-09-30** — `liberar-reservas-em-lote`: `POST /orders/bulk-status`. Commit `dbee52a`.
- **2026-08-30** — `n-mais-1-e-divida-de-documentacao` (**PED-C002 + C003 + C001**).
  **C002 era o único 🔴 acionável do projeto**, e não era uma rota só: `OrderRepositoryImpl.findAll`
  e `findBySessionId` alimentam `GET /orders`, `GET /pdv/sessions/{id}/sales` e
  `GET /pdv/pending-online-orders`. `OrderEntity.items` é `LAZY` e `toDomain` tocava a coleção de
  cada pedido da página — até **101 consultas numa página de 100**, no endpoint de pedidos do
  administrador. Corrigido com o ID-first de [`persistence.md`](../../persistence.md), o mesmo par
  que `ProductRepositoryImpl` usa e que PDV-C009 acabou de aplicar à listagem de mesas.
  **A `Specification` de `findAll` ficou intacta**, e isso importa: é ela que evita o bind ambíguo
  de `Instant` nulo no Postgres, um bug que já quebrou duas vezes por caminhos diferentes (ver o
  javadoc de `PedidoRepositoryPostgresIT`). A fase 1 já rodava sem tocar a coleção; só a fase 2 é
  nova. Como as duas chamadoras fazem exatamente a mesma coisa depois de obter a `Page`, a segunda
  fase virou um `withItems` compartilhado. Resultado: **três consultas fixas** (count, página,
  fetch) em vez de 2 + N.
  **A ordem dos itens virou `@OrderBy("id ASC")` na entidade**, não um `ORDER BY` na consulta:
  `OrderEntity.items` nunca teve ordenação declarada, e trazendo vários pedidos num join só a ordem
  passaria a depender de como o banco intercala as linhas. A primeira tentativa foi ordenar pelo
  alias do fetch join na própria consulta — **HQL inválido**, que derrubou o contexto inteiro do
  Spring. O `@OrderBy` é o padrão que `ComandaEntity` já usava, e ainda vale para todo caminho de
  leitura da coleção, não só para a listagem.
  **A prova é por contagem, não por inspeção:** `PedidoRepositoryIT` liga o `Statistics` do
  Hibernate e afirma que sextuplicar os pedidos da página **não muda** o número de consultas — a
  única afirmação honesta, porque os itens estariam acessíveis nos dois desenhos (a leitura acontece
  dentro da transação). É a mesma técnica introduzida na rodada passada. E como
  `PedidoRepositoryPostgresIT` roda contra base **vazia**, onde a guarda de página vazia impede a
  segunda consulta de sequer ser emitida, ganhou um caso com dados: é o único que exercita
  `SELECT DISTINCT … LEFT JOIN FETCH … WHERE id IN :ids ORDER BY` no dialeto real.
  **C003 estava pior do que o card descrevia, e a culpa é das entregas recentes.** O card foi aberto
  quando PDV-F010 trouxe o canal `MESA` sem atualizar esta página; desde então mais três entregas
  mexeram neste domínio e nenhuma chegou aqui. A tabela de Schema parava na **V72** e faltavam
  **oito** migrations (V93, V98, V99, V100, V113, V114, V116, V118); o Modelo de Domínio não
  conhecia `MESA`, `comandaId`/`tableLabel`, `serviceFeeAmount`, nem os quatro campos que a mesa
  acrescentou ao item; e Integrações não mencionava que o fechamento de comanda é hoje a **terceira
  origem** de pedido — a única em que ele nasce da conversão de um agregado de outro domínio.
  **C001 era a ausência das duas seções que todo módulo maduro deste projeto tem.** A cobertura
  existia e é boa (13 classes); o que não existia era o mapa dela, nem a tabela de Regras de Negócio
  extraída do código. As duas foram escritas, com **quatro lacunas registradas em vez de maquiadas**
  — entre elas o fato de o IT de Postgres ser opt-in e não rodar no CI, e o de a contagem de
  consultas comparar cardinalidades em vez de um absoluto (uma consulta extra *constante* passaria
  batido). Sem migration, sem permissão nova, sem mudança de contrato HTTP.

- **2026-07-28** — `fundacao-do-pedido` (PDV-F003/F004/F005): `Order`/`OrderItem` substituem
  `Sale`/`SaleItem`; V65.
- **2026-07-28** — `orders-visao-do-administrador`: `OrderUseCase`/`OrderService`,
  `OrdersController`, quatro endpoints, V67. Coberto por `OrderServiceTest` e
  `OrdersControllerSecurityTest`.
- **2026-07-29** — `cancelamento-e-reembolso-do-pedido` (`PDV-F007`, Fatia 5): `OrderStatus` ganha
  `REEMBOLSADO`, mutuamente exclusivo de `CANCELADO` por transição; `POST /orders/{id}/refund`
  (`ORDER_REFUND`, V72) devolve estoque (com suporte a lote), estorna cada pagamento `CAPTURED`
  com uma linha `REFUNDED` nova e reverte o cashback ganho, tudo em uma transação; V71 adiciona
  `refunded_at` e o `CHECK` de consistência do status. Coberto por `OrderServiceTest`,
  `OrderTest`, `OrderStatusTest` e o IT de ponta a ponta `OrderRefundIT`.
- **2026-08-03** — gateway InfinitePay + webhook (`ECM-F004`, Fatia 10, detalhado em
  [`ecommerce/README.md`](../ecommerce/README.md)): `Order.paid(...)` ganha seu primeiro caller
  fora de teste — `PaymentWebhookService`, chamado por `POST /webhooks/payments/{provider}`.
  Nenhuma mudança de schema neste domínio; V79 (em `ecommerce`) só amplia o `CHECK` de
  `order_payment.method` para aceitar `GATEWAY_PIX`.
- **2026-08-17** — `reserva-para-retirada` (**PDV-F008**): novo status `RESERVADO`, entre
  `CRIADO` e `CONCLUIDO`/`REEMBOLSADO` — venda de balcão paga e baixada do estoque, aguardando o
  cliente retirar depois. Detalhes completos em
  [`vendas-balcao/README.md`](../vendas-balcao/README.md#histórico-de-implementações). Migration
  V98.
- **2026-08-17** — `product-name-e-timestamps-da-esteira` (**PED-F002**): `OrderItem` ganha
  `productName`, congelado no instante da venda (mesma razão de `costPrice` — produto renomeado
  depois não pode reescrever o histórico), exposto em `OrderItemAdminResponseDto`,
  `OrderItemResponseDto` e `SaleReceiptItemResponseDto`. Novo `EstoqueUseCase.resolveSaleInfo`
  resolve nome e precificação numa consulta só (evita repetir o `findByAnySku` que
  `findPricingBySku` já faz) — `PdvService.registerSale` e `ShopService` (checkout e
  `settleOnlineOrder`) passaram a usá-lo. `Order` ganha `separatedAt`/`shippedAt`/`deliveredAt`,
  carimbados por `withStatus` quando o pedido alcança `SEPARADO`/`ENVIADO`/`ENTREGUE`
  respectivamente — histórico, sem `CHECK` de coexistência com o status atual (mesma régua de
  `reservedAt`/`paidAt`): um pedido que já foi separado mantém `separatedAt` preenchido mesmo
  depois de `ENTREGUE` ou reembolsado. Pedido do `BACKEND_TODO.md` do `mahal-admin`
  §"Vendas: nome do cliente, ranking de produtos e recibo de marketplace" — os outros três itens
  dessa seção (`customerName`, ranking de produtos, recibo de marketplace) já estavam
  implementados antes desta entrega. Migrations V99 (`order_item.product_name`) e V100
  (`sales_order.separated_at`/`shipped_at`/`delivered_at`).

## Próximos passos

- [x] **PED-C001** — auditar o código e completar este README. Fechado em 2026-08-30.
- [x] **PED-C002** — o N+1 de `GET /orders`. Fechado em 2026-08-30.
- [x] **PED-C003** — o README congelado antes do canal `MESA`. Fechado em 2026-08-30.
- [x] Teste de concorrência para reembolso: existe desde a Fatia 5 — `OrderRefundConcurrencyIT`
      prova que duas chamadas simultâneas ao mesmo pedido só deixam uma passar, e que os efeitos
      (estoque, pagamento, cashback) não são duplicados. Esta linha dizia o contrário até
      2026-08-30, quando PED-C001 auditou a cobertura de verdade.
- [x] Criação de pedido `MARKETPLACE`: entregue (Fatias 8-10, `ECM-F001`-`F004`). `checkout` cria
      via `Order.openMarketplace(...)`; o webhook do gateway leva a `PAGO` via `Order.paid(...)`.
- [ ] Gap conhecido, não deste domínio: se um pedido `MARKETPLACE` é cancelado com um link de
      checkout ainda pendente no InfinitePay, ninguém avisa o gateway — ver "Conhecido, fora desta
      entrega" em [`ecommerce/README.md`](../ecommerce/README.md).
