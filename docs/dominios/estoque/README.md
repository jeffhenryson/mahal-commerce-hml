# Domínio: estoque

**Status:** 🟢 Operacional — grade de produtos, saldo multi-depósito, ledger de movimentações (gravação e consulta), alerta de ponto de reposição, reserva, kits, lote/validade, custo médio ponderado e importação de entrada de mercadoria por XML de NF-e em produção
**Pacote Java:** `com.cernecommerce.core.domain.model.estoque`
**Rota HTTP base:** `/estoque`
**Última atualização deste doc:** 2026-10-08 — **EST-F036 entregue** (produto base com variações não
vendável por padrão; V149). Antes, no mesmo dia — **EST-F032 entregue** (embalagem carteira → maço →
unidade com quebra automática na saída; V148). Antes, no mesmo dia — **EST-F036** aberto (produto base com variações vendável;
PDV-F042 passou a chamar `consumeSession` pela sessão do cardápio). Antes, no mesmo dia — **EST-C025 fechado** (trava pessimista no contador da
lata). Antes, no mesmo dia — **EST-F033 entregue** (`POST /estoque/open-packages/{sku}`,
cadastro de lata já aberta, sem baixa) e **EST-C025** aberto (contador da lata sem trava,
pré-requisito de PDV-F042). Antes, no mesmo dia — `/1-analise` de features vinda do PDV: **EST-F032**
(embalagem carteira→maço→unidade com quebra automática), **EST-F033** (cadastrar lata já aberta),
**EST-F034** (preço promocional com vigência) e **EST-F035** (sugestão de promoção por validade de lote).
Antes, 2026-08-31 — **o backlog do módulo ficou sem correção
pendente**: fecharam **EST-F011** (curva ABC), **EST-C006** (como decisão) e **EST-C017** (a
documentação da mesa). Resta só **EST-F016** (unidade de medida), 🟢, e **EST-F012**, despriorizado.
Antes, no mesmo dia — **EST-C015** (leitura do ledger com
`ESTOQUE_PRODUCT_READ`) e **EST-F025** (`POST /estoque/conversions`, a conversão lata→sessão numa
transação só). Antes, em 2026-08-30 — **EST-C016**: `ReservedStockException` não tinha
`@ExceptionHandler` e respondia **500** onde o contrato pedia `400 RESERVED_STOCK`; entre os caminhos que a
alcançam está `ComandaService.addItem`, o lançamento de item em **mesa**. Registrados na mesma passada
**EST-F025** (conversão atômica entre SKUs — a lata que vira N sessões) e **EST-C017** (este README não
documenta a mesa). Antes: 2026-08-18 (EST-F005, importação de NF-e; EST-C014, teste do `ProductImageController`).

## Objetivo

Gerenciamento da grade de produtos e controle de inventário multi-depósito, com trilha auditável de todas as movimentações de saldo. É o domínio transacional central do sistema: tanto o PDV (venda) quanto Compras (recebimento) escrevem saldo através dele.

## Escopo planejado

- **Grade de produtos com variações:** SKU pai (`Product`) e SKUs filhos (`ProductVariant`). ✅ Implementado (EST-F001).
- **Atributos:** sabor, tamanho, cor (`ProductAttribute`). ✅ Implementado (EST-F001).
- **Multi-depósito:** loja física × e-commerce (`Warehouse` + `StockBalance` por depósito). ✅ Implementado (EST-F002).
- **Movimentações:** entradas/saídas/ajustes com histórico auditável (`StockMovement`). ✅ Implementado (EST-F003), com consulta paginada do histórico (EST-F017).
- **Ponto de reposição:** mínimo por SKU/depósito + notificação automática. ✅ Implementado (EST-F004).
- **Entrada por XML de NF-e** (`NfeXmlImportPort`). 🟡 Pendente (EST-F005).
- **Inventário/balanço:** contagem com sessão por depósito, divergência registrada e ajuste em lote no fechamento (`StockCount`). ✅ Implementado (EST-F006).
- **Precificação:** custo de aquisição, markup desejado e preço praticado por produto, com preço sugerido, margem e markup efetivo derivados. ✅ Implementado (EST-F019).
- **Reserva de estoque:** `reservedQuantity` separado do físico, TTL configurável, expiração agendada e diagnóstico de integridade. ✅ Implementado (EST-F013/F021/EST-C013).
- **Kits virtuais:** combo de um nível, saldo e custo derivados dos componentes, sem linha própria em `stock_balance`. ✅ Implementado (EST-F015/EST-F022).
- **Lote e validade:** `StockLot` aditivo a `stock_balance`, consumo FEFO na saída, alerta de vencimento agendado e diagnóstico de integridade. ✅ Implementado (EST-F008).
- **Custo médio, transferência entre depósitos, unidade de medida.** 🟡 Pendentes — ver [Backlog do Módulo](#backlog-do-módulo).

## Modelo de Domínio

Todos os modelos são `record` imutáveis em `core/domain/model/estoque/`, com invariantes no
compact constructor e o par de fábricas `create()` (entidade nova, sem `id`) / `of()`
(reconstituição a partir da persistência).

| Modelo | Campos | Invariantes e comportamento |
|---|---|---|
| `Product` | `id, sku, name, category, active, variants, pricing` | `sku` e `name` obrigatórios; `variants` null vira `List.of()`, senão cópia defensiva; `pricing` null vira `Pricing.empty()`; `create` nasce `active = true` |
| `Product` — campos de mesa (V112) | `availableForTable, sessionProduct, sessionsPerUnit, openRoshPrice` | Acrescentados por PDV-F010 a `product`, que é tabela **deste** módulo, e por isso documentados aqui. `availableForTable` (default `true`) decide se o SKU pode ser lançado numa comanda — é a regra *"bebida e narguilé saem na mesa, cigarro e isqueiro não"* existindo no servidor. `sessionProduct` marca o que é vendido por sessão, e os sabores são as variações da grade. `sessionsPerUnit` **passou a movimentar saldo em EST-F027**: nasceu como sugestão de tela para a conversão e ficou dois meses sem nenhum leitor, o que fazia cada sessão baixar uma lata inteira. Hoje é ele que diz quantas sessões saem de uma unidade, e a lata em uso vive em `open_package`. `openRoshPrice` mora no SKU **pai** de propósito — a linha da comanda chega com o SKU da variação (para saber qual essência sai do estoque), mas cobra este valor. |
| `Product.parentSellable` (V149) | `boolean`, default `false` | EST-F036 — a base de um produto **com variações** é vendável? `isSellable(sku)`: variação sempre; base só sem variações ou com o flag ligado. Desligado, a base não se vende (PDV, mesa, site) nem recebe `ENTRADA`; `SAIDA` e `AJUSTE` passam, para escoar e corrigir no balanço. Sem variações o flag não tem efeito. |
| `OpenPackage` (V124) | `sku, warehouseId, uses, sessionsPerUnit, openedAt, openedBy, closedAt, closeReason` | EST-F027 — a lata de essência já aberta no balcão. **A unidade sai de `stock_balance` na abertura**, então o saldo passa a significar *latas lacradas na prateleira*, que é o que o operador conta no balanço; o consumo de dentro da lata é o contador daqui. `sessionsPerUnit` é **cópia** do catálogo no momento da abertura, não leitura viva — editar o produto não pode mudar o tamanho de uma lata pela metade. Uma lata em uso por par `(sku, depósito)`, garantido por índice único **parcial** (a tabela é histórico e guarda as fechadas). A lata esgotada continua aberta até a sessão seguinte, que é o que permite a tela mostrar "5 de 5". Duas fábricas: `open` (lata nova, `uses = 0`, com `SAIDA 1` no service) e `registered` (EST-F033: lata que **já estava aberta**, nasce com `uses = sessionsPerUnit − usesRemaining` e **sem** `SAIDA`; `usesRemaining` fora de `1..sessionsPerUnit` → `InvalidOpenPackageUsesException`). |
| `SkuPackaging` (V148) | `childSku, parentSku, unitsPerParent` | EST-F032 — a embalagem: `parentSku` contém `unitsPerParent` de `childSku` (a carteira contém 10 maços, o maço contém 20 cigarros). Cada nível é um SKU próprio — na decisão do dono, uma **variação cor × embalagem** do mesmo produto —, com saldo, preço e código de barras próprios; o saldo de cada um é o que está fisicamente na prateleira. Invariantes: fator > 1 e filho ≠ pai. `parentsToOpen` arredonda para cima (não se abre meio maço). Um filho tem um pai só (PK); ciclo e profundidade (até 4 níveis) são regras do service. |
| `Pricing` | `costPrice, markupPercent, salePrice` | Value object (EST-F019); os três campos são opcionais e **não negativos**; `empty()` é "não precificado". Deriva `suggestedPrice`, `effectivePrice`, `marginAmount`, `marginPercent`, `effectiveMarkupPercent` |
| `ProductVariant` | `id, sku, attributes, active` | `sku` obrigatório; cópia defensiva dos atributos |
| `ProductAttribute` | `type, value` | Ambos obrigatórios; **sem identidade própria** (persistido como `@ElementCollection`) |
| `Warehouse` | `id, code, name, type, active` | `code`, `name` e `type` obrigatórios; `create` nasce ativo |
| `WarehouseType` | enum | `LOJA_FISICA`, `ECOMMERCE` |
| `StockBalance` | `id, sku, warehouseId, quantity, version` | `quantity` **nunca negativa**; `zero(sku, warehouseId)` para saldo inicial; `version` suporta locking otimista |
| `StockMovement` | `id, sku, warehouseId, type, quantity, reason, username, createdAt` | Todos obrigatórios; `quantity > 0` em `ENTRADA`/`SAIDA` e `>= 0` em `AJUSTE`; `create()` carimba `Instant.now()` |
| `MovementType` | enum | `ENTRADA`, `SAIDA` (delta) e `AJUSTE` (**saldo-alvo**, EST-C009) |
| `StockCount` | `id, warehouseId, status, username, createdAt, closedAt, items` | Balanço de um depósito; `withCountedItem` é upsert por SKU preservando a posição; `closed()`/`cancelled()` carimbam `closedAt` |
| `StockCountStatus` | enum | `ABERTA`, `FECHADA`, `CANCELADA` |
| `StockCountItem` | `id, sku, countedQuantity, expectedQuantity, difference, lotCode` | `countedQuantity >= 0`; `expectedQuantity`/`difference` só no fechamento; `diverges()` decide se gera movimentação; `lotCode` (EST-F008) nulo para SKU não lote-rastreado — cada lote é contado e reconciliado à parte |
| `ReorderPoint` | `id, sku, warehouseId, minQuantity` | `minQuantity >= 0`; `isBelow(qty)` é comparação **estrita** (`qty < minQuantity`) |
| `StockLot` | `id, sku, warehouseId, lotCode, expiryDate, quantity, alertedAt, version` | EST-F008. Aditivo a `StockBalance` — `SUM(quantity)` por `(sku, warehouseId)` deve igualar `stock_balance.quantity`, mantido pela mesma transação de `adjustStock`. `receive`/`consume` somam/subtraem; `reconciledTo` é saldo-alvo por lote (fechamento de balanço); `expiryDate` imutável após criado |
| `LotIntegrityMismatch` | `sku, warehouseCode, balanceQuantity, lotsTotal` | Retrato de leitura (EST-F008), sem persistência própria — diagnóstico de `stock_balance.quantity` divergindo da soma de `stock_lot.quantity` |

**Ponto central do domínio — `StockBalance.apply(MovementType, BigDecimal)`:**
`ENTRADA` soma e `SAIDA` subtrai — ambas tratam a quantidade como **delta**. Se o resultado
ficaria negativo, lança `InsufficientStockException`; zerar exatamente é permitido, negativar não.

`AJUSTE` é a exceção: a quantidade é o **saldo-alvo**, não um delta (EST-C009). O saldo passa a
valer exatamente o valor informado, para cima ou para baixo, e zero é um alvo válido. Alvo
negativo é `IllegalArgumentException`, não `InsufficientStockException` — não existe "saldo
insuficiente" para uma contagem, o que há é um alvo inválido.

O `version` é preservado no record resultante nos três casos, para que o merge no JPA acione o
optimistic locking.

**Exceções** (`core/domain/exception/estoque/`) e o mapeamento HTTP em
`infra/handler/GlobalExceptionHandler.java`:

| Exceção | HTTP | Código de erro |
|---|---|---|
| `DuplicateSkuException` | 409 | `SKU_ALREADY_EXISTS` |
| `MissingServletRequestParameterException` (Spring) | 400 | `MISSING_PARAMETER` |
| `HandlerMethodValidationException` (Spring) | 400 | `VALIDATION_ERROR` |
| `DuplicateWarehouseCodeException` | 409 | `WAREHOUSE_CODE_ALREADY_EXISTS` |
| `WarehouseNotFoundException` | 404 | `WAREHOUSE_NOT_FOUND` |
| `ProductNotFoundException` | 404 | `PRODUCT_NOT_FOUND` |
| `InsufficientStockException` | 400 | `INSUFFICIENT_STOCK` |
| `InactiveProductException` | 409 | `PRODUCT_INACTIVE` |
| `InactiveWarehouseException` | 409 | `WAREHOUSE_INACTIVE` |
| `StockCountNotFoundException` | 404 | `STOCK_COUNT_NOT_FOUND` |
| `StockCountNotOpenException` | 409 | `STOCK_COUNT_NOT_OPEN` |
| `StockCountAlreadyOpenException` | 409 | `STOCK_COUNT_ALREADY_OPEN` |
| `ObjectOptimisticLockingFailureException` (Spring) | 409 | `STOCK_UPDATE_CONFLICT` |
| `DataIntegrityViolationException` (Spring) | 409 | `DATA_INTEGRITY_VIOLATION` |
| `MissingLotInfoException` (EST-F008) | 400 | `LOT_INFO_REQUIRED` |
| `UnexpectedLotInfoException` (EST-F008) | 400 | `LOT_INFO_NOT_APPLICABLE` |
| `LotExpiryDateMismatchException` (EST-F008) | 409 | `LOT_EXPIRY_MISMATCH` |
| `StockLotNotFoundException` (EST-F008) | 404 | `STOCK_LOT_NOT_FOUND` |

## Regras de Negócio Implementadas

| Regra | Onde | Teste |
|---|---|---|
| SKU é único no sistema, e o espaço de nomes é compartilhado entre SKU pai e SKUs de variação | `EstoqueService.createProduct` | `EstoqueServiceTest.createProduct_throwsWhenSkuAlreadyExists`, `createProduct_throwsWhenVariantSkuEqualsParentSku` |
| Produto pode ser criado sem variações (produto simples) | `Product.create` | `EstoqueServiceTest.createProduct_allowsProductWithoutVariants` |
| Variação exige SKU próprio; atributo exige tipo e valor | `ProductVariant`, `ProductAttribute` (compact constructors) | `@Valid` em `ProductVariantRequest` / `ProductAttributeRequest` |
| Listagem de produtos é paginada (máx. 100 por página) | `EstoqueController.listProducts` (`Math.min(size, 100)`) | `EstoqueControllerTest.list_returns_200_with_products` |
| Código de depósito (`Warehouse.code`) é único no sistema | `EstoqueService.createWarehouse` | `EstoqueServiceTest.createWarehouse_throwsWhenCodeAlreadyExists` |
| Consulta de saldo retorna zero (sem persistir linha) quando não há registro para o par SKU/depósito | `EstoqueService.getStockBalance` | `EstoqueServiceTest.getStockBalance_returnsZeroWhenNoBalanceRecordYet` |
| Operações em depósito inexistente lançam `WarehouseNotFoundException` (404) | `getStockBalance`, `adjustStock`, `setReorderPoint` | `EstoqueServiceTest.getStockBalance_throwsWhenWarehouseNotFound`, `adjustStock_throwsWhenWarehouseNotFound`, `setReorderPoint_throwsWhenWarehouseNotFound` |
| Saldo (`StockBalance.quantity`) não pode ser negativo | `StockBalance` (compact constructor) | `StockBalanceTest.throwsWhenQuantityIsNegative` |
| Saída maior que o saldo é rejeitada e **nada é persistido** (nem movimento, nem saldo) | `StockBalance.apply` → `InsufficientStockException`, antes de qualquer `save` em `EstoqueService.adjustStock` | `EstoqueServiceTest.adjustStock_saida_insufficientBalance_throwsAndDoesNotPersistAnything`, `StockBalanceTest.apply_saida_throwsInsufficientStockExceptionWhenNotEnoughBalance` |
| Saída pode drenar o saldo exatamente até zero | `StockBalance.apply` | `StockBalanceTest.apply_saida_allowsDrainingExactlyToZero` |
| Entrada sem saldo prévio parte de zero e cria a linha de saldo | `EstoqueService.adjustStock` (`orElseGet(StockBalance::zero)`) | `EstoqueServiceTest.adjustStock_entrada_withoutPriorBalance_startsFromZeroAndPersists` |
| Toda movimentação grava um `StockMovement` (ledger auditável) com motivo e usuário | `EstoqueService.adjustStock` | `EstoqueServiceTest.adjustStock_saida_decreasesExistingBalance` |
| Quantidade de movimentação deve ser estritamente maior que zero | `StockMovement` (compact constructor) + `@DecimalMin(inclusive=false)` no request | `StockMovementTest`, `EstoqueControllerTest.registerMovement_withNegativeQuantity_returns_400` |
| Escritas concorrentes no mesmo saldo resultam em 409, não em saldo corrompido | `@Version` em `StockBalanceEntity` + handler global | `StockBalanceConcurrencyIT.saidas_concorrentes_nao_perdem_baixa_de_estoque` |
| Primeira movimentação concorrente do mesmo par também resulta em 409, não em 500 | `uk_stock_balance_sku_warehouse` + `GlobalExceptionHandler.handleDataIntegrityViolation` | `StockBalanceConcurrencyIT.primeira_movimentacao_concorrente_do_mesmo_par_nao_duplica_saldo` |
| Escritas simultâneas no contador da lata aberta fazem **fila** e todas contam — nenhum uso some, e ninguém toma 409 no uso comum (EST-C025) | `OpenPackageRepository.findOpenForUpdate` (`PESSIMISTIC_WRITE`) em consumir, desfazer, repor e cadastrar | `OpenPackageConcurrencyIT.concurrentSessions_onTheSamePackage_neverLoseAUse` |
| Movimentar ou definir mínimo exige SKU existente no catálogo (pai ou variação) | `EstoqueService.requireKnownSku` → `ProductNotFoundException` | `EstoqueServiceTest.adjustStock_throwsWhenSkuNotInCatalog`, `adjustStock_acceptsVariantSkuNotJustParentSku`, `setReorderPoint_throwsWhenSkuNotInCatalog`, `EstoqueRepositoryIT.existsBySku_encontraTantoSkuPaiQuantoSkuDeVariacao` |
| SKU desconhecido vindo de venda ou recebimento reverte a operação inteira | propagação de `ProductNotFoundException` por `PdvService`/`ComprasService` | `PdvServiceTest.registerSale_propagatesUnknownSkuAndDoesNotSaveSale`, `ComprasServiceTest.receiveGoods_propagatesUnknownSkuAndDoesNotSaveReceipt` |
| SKU de variação duplicado é 409, não 500 | `EstoqueService.createProduct` valida pai e variações | `EstoqueServiceTest.createProduct_throwsWhenVariantSkuAlreadyExists`, `createProduct_throwsWhenPayloadRepeatsTheSameVariantSku`, `createProduct_throwsWhenVariantSkuEqualsParentSku` |
| Definir ponto de reposição é upsert: reaproveita o `id` existente do par SKU/depósito | `EstoqueService.setReorderPoint` | `EstoqueServiceTest.setReorderPoint_createsNewWhenNoneExists`, `setReorderPoint_updatesExisting` |
| Saldo abaixo do mínimo notifica todos os usuários com `ESTOQUE_STOCK_MANAGE` | `EstoqueService.notifyIfBelowReorderPoint` | `EstoqueServiceTest.adjustStock_saida_belowReorderPoint_notifiesUsersWithStockManagePermission`, `EstoqueAlertaIT` |
| Alerta é agregado por operação e só sai depois do commit | `AfterCommitExecutor` + `EstoqueService.dispatchReorderAlerts` | `TransactionAfterCommitExecutorTest.comTransacaoAtiva_agrega_e_despacha_uma_unica_vez_no_commit`, `em_rollback_nao_despacha_nada` |
| Saldo igual ao mínimo **não** dispara alerta (comparação estrita) | `ReorderPoint.isBelow` | `EstoqueServiceTest.adjustStock_saida_aboveReorderPoint_doesNotNotify` |
| Sem ponto de reposição configurado, nenhuma notificação é enviada | `EstoqueService.notifyIfBelowReorderPoint` | `EstoqueServiceTest.adjustStock_withoutReorderPointConfigured_doesNotNotify` |
| Histórico de movimentações é paginado e ordenado do mais recente para o mais antigo | `EstoqueController.listMovements` + `findBySkuAndWarehouseIdOrderByCreatedAtDescIdDesc` | `EstoqueControllerTest.listMovements_returns_200_with_ledger` |
| `page >= 0` e `size` entre 1 e 100 nos quatro endpoints paginados; fora da faixa é 400, não teto silencioso | `@Min`/`@Max` nos `@RequestParam` + `@Validated` no controller | `EstoqueControllerValidationTest.sizeAcimaDoTeto_returns_400`, `sizeZero_returns_400`, `pageNegativa_returns_400`, `sizeExatamenteNoTeto_returns_200` |
| `sku` (3–50) e `warehouseCode` (2–50) em query e path são validados antes de chegar ao service | `@NotBlank`/`@Size` nos `@RequestParam`/`@PathVariable` | `EstoqueControllerValidationTest.getStockBalance_comParametroInvalido_returns_400`, `getStockBalance_comSkuAcimaDe50Caracteres_returns_400` |
| Parâmetro **ausente** continua sendo 400 `MISSING_PARAMETER`, e não `VALIDATION_ERROR` | `GlobalExceptionHandler.handleMissingParam` vs. `handleHandlerMethodValidation` | `EstoqueControllerValidationTest.listMovements_semSku_continua_400_MISSING_PARAMETER` |
| Listagem de depósitos é paginada e ordenada por id (paginação estável) | `WarehouseJpaRepository.findAllOrderById(Pageable)` | `EstoqueServiceTest.listWarehouses_delegatesPagingToRepository`, `EstoqueRepositoryIT.warehouse_paginaOrdenadoPorId` |
| Edição de produto/depósito é parcial: campo ausente é mantido | `Product.withDetails`, `Warehouse.withDetails` (null = manter) | `ProductTest.withDetails_nullMantemOCampo`, `WarehouseTest.withDetails_nullMantemOCampo` |
| Edição não altera `sku`, `code` nem as variações | `withDetails` preserva esses campos | `ProductTest.withDetails_preservaIdSkuActiveEVariacoes`, `EstoqueRepositoryIT.save_aposWithDetails_preservaVariacoesEAtributos` |
| Edição parcial não burla os invariantes do modelo (nome em branco continua rejeitado) | compact constructor roda em cada `with*` | `ProductTest.withDetails_naoDeixaBurlarOsInvariantes`, `WarehouseTest.withDetails_naoDeixaBurlarOsInvariantes` |
| Produto ou depósito **desativado recusa ENTRADA** (manual e por recebimento) | `EstoqueService.requireActiveForInbound` | `EstoqueServiceTest.adjustStock_entrada_emSkuDesativado_throwsAndDoesNotPersistAnything`, `adjustStock_entrada_emDepositoDesativado_throwsAndDoesNotPersistAnything` |
| Desativado **continua aceitando SAIDA e AJUSTE** — não prende saldo nem impede correção de inventário | `requireActiveForInbound` só age em `ENTRADA` | `EstoqueServiceTest.adjustStock_saida_emSkuDesativado_continuaPermitida`, `adjustStock_ajuste_emSkuDesativado_continuaPermitido` |
| SKU de variação só é "ativo" se a variação **e** o produto pai estiverem ativos | `ProductJpaRepository.isSkuActive` | `EstoqueRepositoryIT.isSkuActive_exigeProdutoPaiAtivo_inclusiveParaSkuDeVariacao` |
| SKU inexistente (404) tem precedência sobre SKU desativado (409) | ordem de `requireKnownSku` antes de `requireActiveForInbound` | `EstoqueServiceTest.adjustStock_entrada_skuDesconhecido_temPrecedenciaSobreDesativado` |
| Desativar não apaga: o SKU segue existindo, com histórico e saldo válidos | `active` é flag, não exclusão | `EstoqueRepositoryIT.isSkuActive_exigeProdutoPaiAtivo_inclusiveParaSkuDeVariacao` |
| `AJUSTE` é saldo-alvo: substitui o saldo, para cima ou para baixo, e aceita zero | `StockBalance.apply` | `StockBalanceTest.apply_ajuste_substituiOSaldoParaBaixo`, `apply_ajuste_paraZeroEhValido` |
| Baixar por `AJUSTE` nunca é `INSUFFICIENT_STOCK` — é substituição, não subtração | `StockBalance.apply` trata `AJUSTE` antes do cálculo de delta | `StockBalanceTest.apply_ajuste_abaixoDoSaldoAtual_naoLancaInsufficientStock` |
| Movimento de quantidade zero só é aceito em `AJUSTE` | `StockMovement` (compact constructor) | `StockMovementTest.ajuste_aceitaQuantidadeZero`, `saida_continuaRecusandoQuantidadeZero` |
| Só pode haver **um balanço aberto por depósito** | `EstoqueService.openStockCount` + `findOpenByWarehouseId` | `EstoqueServiceTest.openStockCount_recusaSegundoBalancoAbertoNoMesmoDeposito`, `EstoqueInventarioIT.balanco_segundoAbertoNoMesmoDeposito_returns_409` |
| Recontar um SKU sobrescreve, não cria segunda linha | `StockCount.withCountedItem` + `uk_stock_count_item_count_sku` | `StockCountTest.withCountedItem_recontarSobrescreveEPreservaAPosicao`, `EstoqueRepositoryIT.stockCount_recontagemNaoDuplicaLinhaDoMesmoSku` |
| Fechar aplica `AJUSTE` **só nos itens divergentes** — contagem que bateu não polui o ledger | `EstoqueService.closeStockCount` + `StockCountItem.diverges` | `EstoqueServiceTest.closeStockCount_aplicaAjusteApenasNosItensDivergentes`, `EstoqueInventarioIT.balanco_ajustaApenasOsDivergentesEDeixaTrilhaNoLedger` |
| A divergência fica gravada (`expectedQuantity`/`difference`), não só o saldo corrigido | `StockCountItem.reconciledWith` persistido no fechamento | `EstoqueRepositoryIT.stockCount_fechamentoPersisteExpectedEDifference` |
| SKU nunca movimentado é confrontado contra saldo zero | `closeStockCount` (`orElse(BigDecimal.ZERO)`) | `EstoqueServiceTest.closeStockCount_skuSemSaldoRegistrado_confrontaContraZero` |
| Fechar duas vezes é 409, não ajuste em dobro | `requireOpenStockCount` | `EstoqueServiceTest.closeStockCount_balancoJaFechado_throwsAndDoesNotAdjust`, `EstoqueInventarioIT.balanco_fecharDuasVezes_returns_409` |
| Cancelar não toca em saldo e libera o depósito para novo balanço | `StockCount.cancelled()` | `EstoqueServiceTest.cancelStockCount_naoTocaEmSaldo`, `EstoqueInventarioIT.balanco_cancelado_naoAjustaSaldoELiberaODeposito` |
| Contar SKU fora do catálogo é 404 na hora, não no fechamento | `recordCountedItem` → `requireKnownSku` | `EstoqueServiceTest.recordCountedItem_throwsWhenSkuNotInCatalog`, `EstoqueInventarioIT.balanco_contarSkuForaDoCatalogo_returns_404` |
| Movimentos com o mesmo `created_at` têm ordem determinística e paginação estável | desempate por `id` (BIGSERIAL) na ordenação do ledger | `EstoqueRepositoryIT.stockMovement_paginaDoMaisRecenteParaOMaisAntigo`, `stockMovement_paginacaoNaoRepeteNemPulaLinhaComCreatedAtIgual` |
| Consultar histórico de par SKU/depósito nunca movimentado devolve página vazia (200), não 404 | `EstoqueService.listMovements` | `EstoqueServiceTest.listMovements_returnsEmptyPageWhenSkuNeverMoved`, `EstoqueControllerTest.listMovements_returns_200_withEmptyPageWhenNeverMoved` |
| Histórico em depósito inexistente lança `WarehouseNotFoundException` (404) sem tocar no repositório de movimentações | `EstoqueService.listMovements` | `EstoqueServiceTest.listMovements_throwsWhenWarehouseNotFound` |
| `sku` e `warehouseCode` ausentes na query devolvem 400 `MISSING_PARAMETER` (não 500) | `GlobalExceptionHandler.handleMissingParam` | `EstoqueControllerTest.listMovements_withoutSku_returns_400`, `listMovements_withoutWarehouseCode_returns_400`, `GlobalExceptionHandlerTest.missingRequestParameter_returns400_namingTheParameter` |
| SKU órfão é diagnosticado por par SKU/depósito, considerando SKU pai **e** de variação como conhecidos | `StockIntegrityJpaRepository.findOrphanSkus` (anti-join `NOT EXISTS` contra `product` e `product_variant`) | `EstoqueRepositoryIT.orphanSkus_naoAcusaSkuPaiNemSkuDeVariacaoCadastrados`, `orphanSkus_acusaSkuForaDoCatalogoComSaldo` |
| Par presente nas três tabelas de estoque vira **uma** linha do diagnóstico, não três | `UNION` (não `UNION ALL`) na origem da query | `EstoqueRepositoryIT.orphanSkus_naoDuplicaQuandoOParEstaNasTresTabelas` |
| O diagnóstico de integridade é somente leitura — nenhum expurgo automático | `StockIntegrityRepository` sem operação de escrita | `EstoqueServiceTest.listOrphanSkus_doesNotTouchAnyWriteRepository` |
| Cadastrar lata que já estava aberta **não baixa estoque** — ela já não está no saldo de lacradas (EST-F033) | `EstoqueService.registerOpenPackage` (sem `adjustStock`) | `EstoqueServiceTest.registerOpenPackage_criaALataSemBaixarEstoque` |
| A lata cadastrada guarda o que já foi gasto: lata de 5 com 2 restantes nasce com 3 usos | `OpenPackage.registered` | `OpenPackageTest.registered_guardaOsUsosJaGastos`, `registered_cheia_nasceZerada` |
| Restante fora de `1..sessionsPerUnit` é 400 `OPEN_PACKAGE_INVALID_USES`; o piso também é barrado no DTO (`@Min(1)`) | `OpenPackage.registered` + `RegisterOpenPackageRequest` | `OpenPackageTest.registered_recusaRestanteForaDaLata`, `EstoqueControllerTest.registerOpenPackage_withoutUsesRemaining_returns_400` |
| Só uma lata aberta por `(sku, depósito)`: cadastrar com outra em uso é 409 `OPEN_PACKAGE_ALREADY_OPEN`, inclusive na corrida (índice parcial da V124 traduzido pelo handler) | `registerOpenPackage` + `GlobalExceptionHandler.handleDataIntegrityViolation` | `EstoqueServiceTest.registerOpenPackage_comLataJaAberta_eRecusado`, `GlobalExceptionHandlerTest.dataIntegrity_onOpenPackageConstraint_returnsOpenPackageAlreadyOpen` |
| Saída que não cabe no **disponível** de um SKU dentro de embalagem abre o pai sozinha, para cima, e em cascata (unidade abre maço, maço abre carteira), na mesma transação (EST-F032) | `EstoqueService.breakPackagingIfShort` (autoinvocação de `adjustStock`) | `EstoqueServiceTest.saida_semSoltoSuficiente_abreUmMacoAutomaticamente`, `saida_semMaco_abreACarteiraEmCascata`, `PackagingBreakIT.venderSoltos_abreMacoECarteiraSozinho` |
| A cadeia inteira não cobre: `INSUFFICIENT_STOCK` de sempre, e a venda reverte junto com as quebras | `StockBalance.apply` na `SAIDA` do pai | `EstoqueServiceTest.saida_semSaldoEmNenhumNivel_falhaComoSempre`, `PackagingBreakIT.venderAlemDaCadeia_falhaSemAbrirNada` |
| Com saldo suficiente, a embalagem nem é consultada; SKU sem ligação se comporta como antes | gancho só quando falta | `EstoqueServiceTest.saida_comSaldoSuficiente_naoConsultaAEmbalagem`, `saida_semLigacao_naoQuebraNada` |
| O filho aberto entra com o custo médio do pai dividido pelo fator | `breakPackagingIfShort` (`unitCost` na `ENTRADA`) | `EstoqueServiceTest.quebra_levaOCustoDoPaiDivididoPeloFator` |
| Ligação recusada para kit, produto base com variações, produto com lote, ciclo e cadeia acima de 4 níveis | `EstoqueService.definePackaging` + `requirePackageableSku` | `EstoqueServiceTest.definePackaging_*` |
| A base de um produto com variações não se vende por padrão: `400 PARENT_NOT_SELLABLE` antes de qualquer gravação, em todo caminho de venda (EST-F036) | `EstoqueService.resolveSaleInfo` + `Product.isSellable` | `EstoqueServiceTest.resolveSaleInfo_daBaseNaoVendavel_eRecusado`, `ProductTest.baseComVariacoes_naoEVendavelPorPadrao_masAsVariacoesSao` |
| Entrada de estoque na base também é recusada; saída e ajuste continuam (o balanço não pode travar) | `EstoqueService.adjustStock` | `EstoqueServiceTest.entradaNaBaseNaoVendavel_eRecusada`, `saidaEAjusteNaBaseNaoVendavel_continuamLiberados` |

## API — Endpoints

Todos exigem `bearerAuth`. Controller: `adapter/in/controller/EstoqueController.java`.

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `GET` | `/estoque/products` | `ESTOQUE_PRODUCT_READ` | Lista produtos paginados (`page` = 0, `size` = 20, teto de 100). Filtros opcionais e combináveis: `search` (trecho em nome **ou** SKU, sem diferenciar maiúsculas), `category`, `brand` (igualdade exata, idem), `active`; ordenação por `sort` (`ID`/`NAME`/`SALE_PRICE`) e `direction` (`ASC`/`DESC`), **sempre desempatada por id**. Sem parâmetros, comportamento idêntico ao anterior. `400 INVALID_ENUM_VALUE` para `sort`/`direction` fora da lista |
| `GET` | `/estoque/products/{sku}` | `ESTOQUE_PRODUCT_READ` | Busca um produto por SKU. Aceita SKU **pai ou de variação** — nos dois casos devolve o pai. `200`; `404 PRODUCT_NOT_FOUND` |
| `POST` | `/estoque/products/images` | `ESTOQUE_PRODUCT_MANAGE` | Upload de imagem de produto (`multipart/form-data`, campo `file`; JPEG/PNG/WebP, 5 MB). Devolve `{"imageUrl": "..."}`. **Não recebe SKU** — a imagem é enviada antes de o produto existir. `400 INVALID_IMAGE_FORMAT`/`IMAGE_TOO_LARGE` |
| `GET` | `/estoque/categories` | `ESTOQUE_PRODUCT_READ` | Lista categorias paginadas, incluindo inativas, na ordem da vitrine (destaque, ordem, nome) |
| `POST` | `/estoque/categories` | `ESTOQUE_CATEGORY_MANAGE` | Cria categoria. `201` + `Location`; `409 CATEGORY_NAME_ALREADY_EXISTS` (comparação sem diferenciar maiúsculas) |
| `PATCH` | `/estoque/categories/{id}` | `ESTOQUE_CATEGORY_MANAGE` | Altera `name`, `featured` e/ou `displayOrder`. Campo ausente é mantido. Renomear **propaga** o nome para os produtos vinculados. `200`; `404 CATEGORY_NOT_FOUND`; `409 CATEGORY_NAME_ALREADY_EXISTS` |
| `PATCH` | `/estoque/categories/{id}/active` | `ESTOQUE_CATEGORY_MANAGE` | Ativa/desativa a categoria. Inativa some da vitrine, mas os produtos vinculados **continuam à venda**. `200`; `404 CATEGORY_NOT_FOUND` |
| `POST` | `/estoque/products` | `ESTOQUE_PRODUCT_MANAGE` (+ `ESTOQUE_PRODUCT_PRICE_MANAGE` se enviar `pricing`) | Cria produto (SKU pai) com variações, atributos e `pricing` opcional. `201` + `Location: /estoque/products/{sku}`; `409 SKU_ALREADY_EXISTS` |
| `PATCH` | `/estoque/products/{sku}` | `ESTOQUE_PRODUCT_MANAGE` (+ `ESTOQUE_PRODUCT_PRICE_MANAGE` se enviar `pricing`) | Altera `name`, `category` e/ou `pricing`. Campo ausente é mantido, inclusive dentro de `pricing`; não altera SKU nem variações. `200`; `404 PRODUCT_NOT_FOUND` |
| `GET` | `/estoque/products/{sku}/price` | `ESTOQUE_PRODUCT_READ` | Precificação vigente do SKU, com os derivados calculados. Aceita SKU **pai ou de variação**. `200`; `404 PRODUCT_NOT_FOUND` |
| `PATCH` | `/estoque/products/{sku}/active` | `ESTOQUE_PRODUCT_MANAGE` | Ativa/desativa o produto (`{"active": false}`). `200`; `404 PRODUCT_NOT_FOUND`; `400` se `active` ausente |
| `PUT` | `/estoque/products/{sku}/reorder-point` | `ESTOQUE_STOCK_MANAGE` | Define a quantidade mínima do SKU no depósito (upsert). `204 No Content`; `404 WAREHOUSE_NOT_FOUND` |
| `GET` | `/estoque/products/{sku}/reorder-point` | `ESTOQUE_WAREHOUSE_READ` | Consulta o ponto de reposição configurado (`minQuantity` nulo se não há configuração). `200`; `404 WAREHOUSE_NOT_FOUND` |
| `GET` | `/estoque/products/reorder-points` | `ESTOQUE_WAREHOUSE_READ` | Lista paginada dos pontos de reposição de um depósito (`warehouseCode` obrigatório). `200`; `404 WAREHOUSE_NOT_FOUND` |
| `PUT` | `/estoque/products/{sku}/kit` | `ESTOQUE_KIT_MANAGE` | Define a receita (componentes) de um kit. `200`; `404 PRODUCT_NOT_FOUND`; `400` se SKU não é kit ou violação de regra |
| `GET` | `/estoque/products/{sku}/kit` | `ESTOQUE_PRODUCT_READ` | Consulta a receita vigente de um kit (lista de componentes). `200` com lista vazia se nunca foi promovido a kit; `404 PRODUCT_NOT_FOUND` |
| `POST` | `/estoque/warehouses` | `ESTOQUE_WAREHOUSE_MANAGE` | Cria depósito (`LOJA_FISICA` ou `ECOMMERCE`). `201` + `Location`; `409 WAREHOUSE_CODE_ALREADY_EXISTS` |
| `PATCH` | `/estoque/warehouses/{code}` | `ESTOQUE_WAREHOUSE_MANAGE` | Altera `name` e/ou `type`. Campo ausente é mantido; não altera o código. `200`; `404 WAREHOUSE_NOT_FOUND` |
| `PATCH` | `/estoque/warehouses/{code}/active` | `ESTOQUE_WAREHOUSE_MANAGE` | Ativa/desativa o depósito. `200`; `404 WAREHOUSE_NOT_FOUND` |
| `GET` | `/estoque/warehouses` | `ESTOQUE_WAREHOUSE_READ` | Lista depósitos paginados, ordenados por id (`page` = 0, `size` = 20, faixa 1–100) |
| `GET` | `/estoque/stock-balance` | `ESTOQUE_WAREHOUSE_READ` | Consulta saldo por `sku` + `warehouseCode`. Retorna zero se nunca houve movimentação; `404 WAREHOUSE_NOT_FOUND`; `400 VALIDATION_ERROR` |
| `POST` | `/estoque/movements` | `ESTOQUE_STOCK_MANAGE` | Registra movimentação manual (`ENTRADA`/`SAIDA`/`AJUSTE`) e devolve o saldo atualizado. Aceita `lotCode`/`expiryDate` opcionais (EST-F008) — obrigatórios juntos numa `ENTRADA` de SKU lote-rastreado, recusados em qualquer outro caso. `201` + `Location` para o saldo; `400 INSUFFICIENT_STOCK`/`LOT_INFO_REQUIRED`/`LOT_INFO_NOT_APPLICABLE`; `404 WAREHOUSE_NOT_FOUND`; `409 STOCK_UPDATE_CONFLICT`/`LOT_EXPIRY_MISMATCH` |
| `POST` | `/estoque/conversions` | `ESTOQUE_STOCK_MANAGE` | Converte saldo de um SKU em saldo de outro **na mesma transação** (EST-F025): `SAIDA` de `fromQuantity` em `fromSku` + `ENTRADA` de `toQuantity` em `toSku`, no mesmo depósito. `201` com os **dois** saldos; `400 SAME_SKU_CONVERSION` / `INSUFFICIENT_STOCK` / `RESERVED_STOCK`; `404 PRODUCT_NOT_FOUND`/`WAREHOUSE_NOT_FOUND`; `409 STOCK_UPDATE_CONFLICT`. A saída é aplicada primeiro, então falta de saldo na origem impede a entrada do destino de existir |
| `GET` | `/estoque/open-packages` | `ESTOQUE_PRODUCT_READ` ou `PDV_COMANDA_MANAGE` | EST-F027 — as latas em uso de um depósito, com o contador de sessões de cada uma. É o "3 de 5" da tela de sessão. `404 WAREHOUSE_NOT_FOUND` |
| `GET` | `/estoque/open-packages/{sku}` | `ESTOQUE_PRODUCT_READ` ou `PDV_COMANDA_MANAGE` | EST-F027 — a lata de um SKU. `404 OPEN_PACKAGE_NOT_FOUND` quando não há nenhuma aberta, que é **estado normal**, não erro: a próxima sessão abre uma |
| `POST` | `/estoque/open-packages/{sku}/replace` | `ESTOQUE_STOCK_MANAGE` ou `PDV_COMANDA_MANAGE` | EST-F027 — "Repor essência": descarta a lata em uso e abre outra, baixando **uma** unidade. Quem repõe é o atendente, que tem `PDV_COMANDA_MANAGE` e não `STOCK_MANAGE` — exigir só a segunda deixaria o botão inalcançável para quem o aperta. `400 INSUFFICIENT_STOCK` (e a lata antiga **continua aberta**) / `NOT_A_PACKAGED_SESSION_PRODUCT`; `404 PRODUCT_NOT_FOUND`/`WAREHOUSE_NOT_FOUND` |
| `POST` | `/estoque/open-packages/{sku}` | `ESTOQUE_STOCK_MANAGE` ou `PDV_COMANDA_MANAGE` | EST-F033 — cadastra uma lata que **já estava aberta** antes do sistema, com `{warehouseCode, usesRemaining}`. **Não baixa estoque.** `201` com o contador; `409 OPEN_PACKAGE_ALREADY_OPEN` se já houver lata em uso do SKU no depósito; `400 OPEN_PACKAGE_INVALID_USES` com restante fora de `1..sessionsPerUnit`; `400 NOT_A_PACKAGED_SESSION_PRODUCT`. |
| `PUT` | `/estoque/products/{sku}/packaging` | `ESTOQUE_PRODUCT_MANAGE` | EST-F032 — liga o SKU à embalagem que o contém, `{parentSku, unitsPerParent ≥ 2}`; redefinir substitui. `400 INVALID_PACKAGING` (kit, produto base com variações, produto com lote, ciclo, cadeia acima de 4 níveis); `404` SKU desconhecido. Publica `PACKAGING_DEFINED`. |
| `DELETE` | `/estoque/products/{sku}/packaging` | `ESTOQUE_PRODUCT_MANAGE` | EST-F032 — desliga; saldos não mudam. `404 PACKAGING_NOT_FOUND`. Publica `PACKAGING_REMOVED`. |
| — | `EstoqueUseCase.listPackagedFamilies` (sem rota em `/estoque`) | — | PDV-F041 — as famílias com embalagem ligada, servidas por `GET /pdv/cigarros` em [`vendas-balcao`](../vendas-balcao/README.md#api--endpoints). |
| `GET` | `/estoque/products/{sku}/packaging?warehouseCode=` | `ESTOQUE_PRODUCT_READ` ou `PDV_READ` | EST-F032 — a cadeia que passa pelo SKU, da embalagem mais externa à mais interna (`sku`, `containsSku`, `containsUnits`, `available` no depósito quando informado). `PDV_READ` porque é a base da central de cigarros (PDV-F041). |
| `DELETE` | `/estoque/products/{sku}` | `ESTOQUE_PRODUCT_MANAGE` | EST-F026 — descarta um **rascunho**. `204`; `409 PRODUCT_NOT_DRAFT` (publicado: use `PATCH .../active`) / `PRODUCT_HAS_STOCK_HISTORY`; `404 PRODUCT_NOT_FOUND` |
| `PATCH` | `/estoque/products/{sku}/lot-tracked` | `ESTOQUE_PRODUCT_MANAGE` | Ativa/desativa o rastreamento de lote e validade do SKU (EST-F008), opt-in — kit não pode. `200`; `400` se SKU é `KIT`; `404 PRODUCT_NOT_FOUND` |
| `PATCH` | `/estoque/products/{sku}/parent-sellable` | `ESTOQUE_PRODUCT_MANAGE` | EST-F036 — `{parentSellable}` liga/desliga a venda da base de um produto com variações. `200` com o produto (que passa a trazer `parentSellable` em toda resposta); `400` sem o campo; `404` SKU pai desconhecido. Publica `PRODUCT_UPDATED` com o flag. |
| `GET` | `/estoque/movements` | `ESTOQUE_PRODUCT_READ` ou `ESTOQUE_STOCK_MANAGE` | Histórico paginado do ledger por `sku` + `warehouseCode` (`page` = 0, `size` = 20, teto de 100), mais recentes primeiro. Par nunca movimentado devolve página vazia com `200`; `404 WAREHOUSE_NOT_FOUND`; `400 MISSING_PARAMETER` |
| `POST` | `/estoque/stock-counts` | `ESTOQUE_STOCK_MANAGE` | Abre um balanço para o depósito. `201` + `Location`; `404 WAREHOUSE_NOT_FOUND`; `409 STOCK_COUNT_ALREADY_OPEN` |
| `POST` | `/estoque/stock-counts/{id}/items` | `ESTOQUE_STOCK_MANAGE` | Registra a contagem física de um SKU (upsert; zero é válido). SKU lote-rastreado (EST-F008) exige `lotCode` — upsert então é por `(sku, lotCode)`, cada lote contado à parte. `200`; `404 PRODUCT_NOT_FOUND`/`STOCK_COUNT_NOT_FOUND`/`STOCK_LOT_NOT_FOUND`; `400 LOT_INFO_REQUIRED`/`LOT_INFO_NOT_APPLICABLE`; `409 STOCK_COUNT_NOT_OPEN` |
| `POST` | `/estoque/stock-counts/{id}/close` | `ESTOQUE_STOCK_MANAGE` | Fecha e aplica os `AJUSTE` dos itens divergentes. `200`; `409 STOCK_COUNT_NOT_OPEN` |
| `POST` | `/estoque/stock-counts/{id}/cancel` | `ESTOQUE_STOCK_MANAGE` | Abandona o balanço sem tocar em saldo. `200`; `409 STOCK_COUNT_NOT_OPEN` |
| `GET` | `/estoque/stock-counts/{id}` | `ESTOQUE_STOCK_MANAGE` | Consulta o balanço e seus itens. `200`; `404 STOCK_COUNT_NOT_FOUND` |
| `GET` | `/estoque/stock-counts` | `ESTOQUE_STOCK_MANAGE` | Balanços do depósito por `warehouseCode`, mais recentes primeiro (`page`/`size` 1–100) |
| `GET` | `/estoque/integrity/orphan-skus` | `ESTOQUE_STOCK_MANAGE` | Diagnóstico de EST-C011: pares SKU/depósito com saldo, movimentações ou ponto de reposição gravados cujo SKU não existe no catálogo (`page` = 0, `size` = 20, teto de 100). Base íntegra devolve página vazia com `200` |
| `GET` | `/estoque/reservations` | `ESTOQUE_RESERVATION_READ` | Lista reservas de estoque paginadas, mais recentes primeiro. Filtros opcionais `sku`, `warehouseCode` e `status` (`ACTIVE`/`CONSUMED`/`RELEASED`/`EXPIRED`), combináveis. `404 WAREHOUSE_NOT_FOUND` se `warehouseCode` for informado e não existir |
| `GET` | `/estoque/reservations/{id}` | `ESTOQUE_RESERVATION_READ` | Consulta uma reserva específica. `200`; `404 RESERVATION_NOT_FOUND` |
| `GET` | `/estoque/integrity/reservation-mismatch` | `ESTOQUE_STOCK_MANAGE` | Diagnóstico de EST-C013: pares SKU/depósito cujo `stock_balance.reserved_quantity` diverge da soma das reservas `ACTIVE` em `stock_reservation` — estoque travado invisível, não overselling (`page` = 0, `size` = 20, teto de 100). Base íntegra devolve página vazia com `200` |
| `GET` | `/estoque/products/{sku}/lots` | `ESTOQUE_PRODUCT_READ` | Lista os lotes de um SKU num depósito (EST-F008, `warehouseCode` obrigatório), do que vence primeiro em diante. Lista vazia se não é lote-rastreado ou nunca recebeu lote — não é erro; `404 WAREHOUSE_NOT_FOUND` |
| `GET` | `/estoque/integrity/lot-mismatch` | `ESTOQUE_STOCK_MANAGE` | Diagnóstico de EST-F008: pares SKU/depósito de SKU lote-rastreado cujo `stock_balance.quantity` diverge da soma de `stock_lot.quantity` (`page` = 0, `size` = 20, teto de 100). Base íntegra devolve página vazia com `200` |

**Rotas públicas fora de `/estoque`** (sem token, liberadas em `SecurityConfig`):

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/product-images/{filename}` | Serve a imagem de produto enviada pelo upload. Redirect 308 com S3/CDN, bytes com armazenamento local; cache imutável de 365 dias. A vitrine do marketplace precisa renderizar a foto sem login |
| `GET` | `/shop/categories` | Categorias ativas na ordem da vitrine — é a lista que o app usa para montar a primeira linha de navegação |

`GET /shop/catalog` ganhou `?categoryId` e passou a ordenar por **destaque da categoria**, depois
ordem de exibição, depois id.

## Segurança e Infraestrutura

> Mecanismos transversais (JWT, filtros, CORS, headers, rate limit de login, lockout) estão em
> [`docs/security.md`](../../security.md); ambientes, containers e datastores em
> [`docs/infrastructure.md`](../../infrastructure.md); o modelo RBAC completo em
> [`plataforma`](../plataforma/README.md#segurança-e-infraestrutura). Aqui fica só o recorte
> deste domínio.

### Permissões RBAC

| Permissão | Libera | Migration | Semeada em `dev`? |
|---|---|---|---|
| `ESTOQUE_PRODUCT_READ` | `GET /estoque/products` | V45 | ✅ `SeedConfig` + `DevRoleBootstrapConfig` |
| `ESTOQUE_PRODUCT_MANAGE` | `POST /estoque/products` | V45 | ✅ |
| `ESTOQUE_PRODUCT_PRICE_MANAGE` | O bloco `pricing` no `POST`/`PATCH` de produto | V63 | ✅ `SeedConfig` + `DevRoleBootstrapConfig` |
| `ESTOQUE_WAREHOUSE_READ` | `GET /estoque/warehouses`, `GET /estoque/stock-balance`, `GET .../reorder-point`, `GET /reorder-points` | V47 | ✅ |
| `ESTOQUE_WAREHOUSE_MANAGE` | `POST /estoque/warehouses` | V47 | ✅ |
| `ESTOQUE_STOCK_MANAGE` | `POST /estoque/movements`, `POST /estoque/conversions`, `PUT .../reorder-point`, stock counts e diagnósticos | V56 | ✅ |
| `ESTOQUE_PRODUCT_READ` **ou** `ESTOQUE_STOCK_MANAGE` | `GET /estoque/movements` — leitura do ledger (EST-C015). Antes exigia a permissão de escrita, o que deixava o `ROLE_ATENDENTE` de fora do próprio histórico que o PDV consulta | — | ✅ |
| `ESTOQUE_RESERVATION_READ` | `GET /estoque/reservations`, `GET .../reservations/{id}` | V64 | ✅ `SeedConfig` + `DevRoleBootstrapConfig` |
| `ESTOQUE_KIT_MANAGE` | `PUT /estoque/products/{sku}/kit` | V71 | ✅ `SeedConfig` + `DevRoleBootstrapConfig` |
| `ESTOQUE_CATEGORY_MANAGE` | `POST`/`PATCH` de `/estoque/categories` | V90 | ✅ `SeedConfig` + `DevRoleBootstrapConfig` |

Concedidas a `ROLE_ADMIN` pelas migrations (`hml`/`prod`) e a `ROLE_ADMIN`/`ROLE_DEV` em runtime
por `SeedConfig`/`DevRoleBootstrapConfig` — necessário porque `dev` não roda Flyway. V45 e V47
inserem **sem** `ON CONFLICT DO NOTHING` (EST-C006).

**Por que o histórico exige `ESTOQUE_STOCK_MANAGE` e não `ESTOQUE_WAREHOUSE_READ`:** o ledger
carrega o `username` de quem realizou cada movimentação. Quem só precisa saber *quanto* existe
usa `GET /estoque/stock-balance` (`WAREHOUSE_READ`); ver *quem* mexeu é privilégio de quem
gerencia estoque. `EstoqueControllerSecurityTest.list_movements_with_warehouse_read_only_returns_403`
fixa essa decisão.

As escritas vindas de Compras e PDV **não passam por `@PreAuthorize` de estoque** — elas entram
por `EstoqueUseCase.adjustStock`, chamado de dentro de `ComprasService`/`PdvService`. Quem tem
`COMPRAS_RECEIPT_MANAGE` ou `PDV_SALE_MANAGE` movimenta saldo sem ter nenhuma permissão
`ESTOQUE_*`. É intencional (o port é a fronteira do domínio), mas significa que a permissão de
estoque não é o único caminho para alterar saldo.

### Rate limiting

❌ **Nenhum endpoint deste módulo é limitado.** O `LoginRateLimitingFilter`
(`infra/security/LoginRateLimitingFilter.java:42-77`) cobre apenas `/auth/**` e duas rotas de
notificação. `GET /estoque/movements` pode ser varrido em loop por qualquer token válido com
`ESTOQUE_PRODUCT_READ` ou `ESTOQUE_STOCK_MANAGE` — desde EST-C015 a superfície é maior, o que reforça
PLAT-C030 em vez de criar risco novo: a alternativa era manter o operador de PDV fora da tela.

### Isolamento de dados

Sistema single-tenant: quem tem `ESTOQUE_WAREHOUSE_READ` enxerga **todos** os depósitos, e quem
tem `ESTOQUE_STOCK_MANAGE` movimenta **qualquer** SKU em **qualquer** depósito. Não existe
vínculo usuário↔depósito — é a limitação a resolver antes de operar com mais de uma loja.

### Auditoria

Toda operação que altera saldo publica `AuditEvent`:

| Operação | Controller | `EventType` |
|---|---|---|
| `POST /estoque/products` | `EstoqueController` | `PRODUCT_CREATED` |
| `PATCH /estoque/products/{sku}` | `EstoqueController` | `PRODUCT_UPDATED` (+ `PRODUCT_PRICE_CHANGED` quando o corpo traz `pricing`) |
| `PATCH /estoque/products/{sku}/active` | `EstoqueController` | `PRODUCT_ACTIVATED` / `PRODUCT_DEACTIVATED` |
| `POST /estoque/warehouses` | `EstoqueController` | `WAREHOUSE_CREATED` |
| `PATCH /estoque/warehouses/{code}` | `EstoqueController` | `WAREHOUSE_UPDATED` |
| `PATCH /estoque/warehouses/{code}/active` | `EstoqueController` | `WAREHOUSE_ACTIVATED` / `WAREHOUSE_DEACTIVATED` |
| `POST /estoque/stock-counts` | `EstoqueController` | `STOCK_COUNT_OPENED` |
| `POST /estoque/stock-counts/{id}/close` | `EstoqueController` | `STOCK_COUNT_CLOSED` (com `itemCount` e `divergentCount`) |
| `POST /estoque/stock-counts/{id}/cancel` | `EstoqueController` | `STOCK_COUNT_CANCELLED` |
| `POST /estoque/movements` | `EstoqueController` | `STOCK_MOVEMENT_REGISTERED` |
| `PUT /estoque/products/{sku}/reorder-point` | `EstoqueController` | `REORDER_POINT_SET` |
| `PUT`/`DELETE /estoque/products/{sku}/packaging` | `EstoqueController` | `PACKAGING_DEFINED` / `PACKAGING_REMOVED` (EST-F032) |
| Quebra automática de embalagem (qualquer `SAIDA`) | `EstoqueService` via `AuditEventPublisherPort` | `STOCK_PACKAGE_BROKEN` (EST-F032, com `parentSku`, `childSku`, `warehouseCode`, `parentsOpened`, `childUnits`; usuário = quem fez a saída) — os dois movimentos ficam no ledger com o motivo "Quebra automática de embalagem" |
| `POST /estoque/open-packages/{sku}` | `EstoqueController` | `OPEN_PACKAGE_REGISTERED` (EST-F033, com `sku`, `warehouseCode`, `usesRemaining`) — única entrada no contador da lata sem `SAIDA` por trás |
| `POST /pdv/sessions/{id}/sales` | `PdvController` | `STOCK_MOVEMENT_REGISTERED` (`origin: PDV_SALE`) |
| `POST /compras/goods-receipts` | `ComprasController` | `STOCK_MOVEMENT_REGISTERED` (`origin: GOODS_RECEIPT`) |

Venda e recebimento emitem **um evento por operação**, não por item, com os campos `origin`,
`warehouseCode`, `type`, `skus` e `itemCount` — o detalhamento item a item continua no ledger
`stock_movement`. A publicação fica nos controllers (adapter), e não nos services, porque
`HexagonalArchitectureTest` só libera `org.springframework.transaction.*` dentro de `core/service`.

Leituras (incluindo `GET /estoque/movements`) não geram evento.

Retenção dos `audit_logs`: 365 dias (`AuditLogCleanupService`); leitura por `GET /audit-logs`
com `AUDIT_READ`.

### Infraestrutura utilizada

| Recurso | Uso neste módulo | Se cair |
|---|---|---|
| Postgres 16 (H2 em `dev`) | `product`, `product_variant`, `warehouse`, `stock_balance`, `stock_movement`, `stock_reorder_point` | módulo indisponível |
| Cache de authorities (Redis/Caffeine, TTL 60s) | checagem de `@PreAuthorize` | latência maior, sem perda de função |
| `UserRepository.findUsernamesByPermission` | destinatários do alerta de reposição | alerta não sai |
| `NotificationUseCase` + SSE (`SseEmitterRegistry`) | entrega do alerta de ponto de reposição | notificação fica só no banco |
| Optimistic locking (`@Version` em `stock_balance`) | protege o saldo sob escrita concorrente | — |

O alerta roda **dentro** da transação de escrita, o que prolonga a transação e gera uma
notificação por item (EST-C003). Não há fila: se a entrega falhar, não há retry.

### Limites operacionais

- Os quatro endpoints paginados (`/products`, `/warehouses`, `/movements`,
  `/integrity/orphan-skus`): `page >= 0` e `size` entre 1 e 100, ambos por Bean Validation.
  `size` fora da faixa é **400 `VALIDATION_ERROR`**, não um teto silencioso (EST-C005).
- `sku` (3–50) e `warehouseCode` (2–50) em query e path são validados com `@NotBlank`/`@Size`,
  espelhando as constraints dos DTOs de escrita.
- Sem upload de arquivo neste módulo. A importação de XML de NF-e (EST-F005) vai introduzir o
  primeiro — e vai precisar de limite de tamanho e validação de conteúdo próprios.

### Riscos conhecidos

- **PLAT-C030** — sem rate limit em nenhum endpoint do módulo, inclusive
  `GET /estoque/integrity/orphan-skus`, cuja query nativa é a mais cara do módulo.
- O passivo de SKU órfão anterior a EST-C002 **continua na base até alguém decidir o destino de
  cada SKU**. `GET /estoque/integrity/orphan-skus` e
  [`scripts/estoque-orphan-skus.sql`](../../../scripts/estoque-orphan-skus.sql) levantam a lista;
  a limpeza é manual, por decisão (ver EST-C011 no Histórico).

## Integrações entre Domínios

> **EST-F032 — a quebra de embalagem vale para todo chamador de `SAIDA` por `adjustStock`** (venda de
> balcão, mesa, estorno, conversão): quem vende o cigarro solto não sabe que um maço foi aberto. A
> exceção é a **criação de reserva** do marketplace (`reserveStock`), que não abre embalagem — reserva
> de solto sem solto disponível falha. O consumo da reserva não passa por `adjustStock` e não é afetado.

Estoque é consumido por outros domínios através do port de entrada `EstoqueUseCase`, injetado
em `infra/config/CoreBeanConfig.java`. Todas as integrações são **chamadas síncronas diretas**
— não há evento, listener, fila nem outbox.

| Origem | Onde | Tipo |
|---|---|---|
| **Compras — recebimento** | `ComprasService.receiveGoods` | `ENTRADA` por item |
| **PDV — venda no balcão** | `PdvService.registerSale` | `SAIDA` por item |
| **PDV — liquidação de pedido online** | `PdvService.settleOnlineOrder` | consome a reserva |
| **Mesa — lançamento na comanda** | `ComandaService.addItem` | `SAIDA`, **uma por lançamento** |
| **Mesa — sessão do cardápio com sabor do catálogo** (PDV-F042) | `ComandaService.addSession`/`addRoshExtra`/`repeatSession` | `consumeSession` (uso da lata do sabor) ou `SAIDA 1` como uso da loja sem `sessionsPerUnit`; desfeito por `releaseSession`/`ENTRADA` só enquanto a sessão não saiu da espera |
| **Mesa — remoção de linha** | `ComandaService.removeItem` | `ENTRADA` das linhas removidas |
| **Mesa — cancelamento** | `ComandaService.cancelComanda` | `ENTRADA` de todos os itens |
| **Pedido — cancelamento** | `OrderService.cancelOrder` | libera a reserva |
| **Pedido — estorno** | `OrderService.refundOrder` | `ENTRADA` por item |
| **Marketplace — checkout** | `ShopService.checkout` | **reserva** |
| **Marketplace — pagamento aprovado** | `PaymentWebhookService` | consome a reserva |

**A mesa é a integração que foge do padrão de todas as outras, e vale saber por quê.** Compras, PDV e
marketplace resolvem o estoque numa transação só, junto do documento que os origina. A comanda não
pode: ela fica aberta por horas, e não há como segurar uma transação de banco aberta durante o
consumo. Por isso cada `addItem` **debita e commita por conta própria**, e o fechamento
(`closeComanda`) **não toca em saldo** — o estoque já saiu, item a item. A contrapartida é que a
baixa da mesa não é atômica ao longo da vida dela: mesa esquecida deixa saldo debitado, e o que a
varredura de PDV-F013 devolve automaticamente é só o caso da comanda **vazia** — quando houve consumo
real, a essência foi queimada e devolvê-la criaria saldo que não existe.

Cortesia **baixa estoque igual**: o cliente não paga, mas a mercadoria saiu.
Junção de mesas (PDV-F016) **não move estoque**: a mercadoria não voltou à prateleira, mudou de conta.

Em ambos os casos o ajuste de estoque acontece **antes** de persistir o documento de origem
(`GoodsReceipt` / `Sale`), dentro da mesma transação. Consequência: se qualquer item falhar
— tipicamente `InsufficientStockException` na venda — a operação inteira é revertida e nem o
documento nem os movimentos anteriores do mesmo lote são gravados.

Antes de qualquer escrita, `adjustStock` e `setReorderPoint` exigem que o SKU exista no catálogo
— como SKU pai ou como SKU de variação (`ProductRepository.existsBySku`). Vale para as três
portas de entrada: movimentação manual, venda e recebimento. SKU desconhecido responde 404
`PRODUCT_NOT_FOUND` e reverte a operação inteira, em vez de criar saldo órfão (EST-C002).

O alerta de reposição (`notifyIfBelowReorderPoint`) **acumula** os SKUs que cruzaram o mínimo
durante a operação e despacha **uma notificação por destinatário depois do commit**, via o port
`AfterCommitExecutor` (implementado em `infra/transaction/TransactionAfterCommitExecutor`). Assim
uma venda com N itens abaixo do mínimo gera um aviso listando os N SKUs — não N avisos —, a
transação de venda não espera o envio, e uma venda revertida não notifica ninguém (EST-C003).

## Schema de Banco (Migrations)

> **Permissão em migration usa `ON CONFLICT DO NOTHING` — sempre** (EST-C006). V45 e V47 não usam, e
> re-executá-las numa base parcialmente populada quebra. **Não há correção possível nos arquivos:**
> migration já aplicada não se edita sem `flyway repair`, que reescreveria o checksum de um script que
> rodou em produção. Fica como regra de processo, não como dívida a pagar — V56, V57, V60, V105, V111,
> V115, V117 e V119 já a seguem, e toda nova deve seguir.


**V44 — `estoque_product`**
- `product` (id, sku UNIQUE `uk_product_sku`, name, category, active DEFAULT TRUE)
- `product_variant` (id, product_id FK → `product` ON DELETE CASCADE, sku UNIQUE `uk_product_variant_sku`, active) — índice `idx_product_variant_product_id`
- `product_attribute` (variant_id FK → `product_variant` ON DELETE CASCADE, attr_type, attr_value) — índice `idx_product_attribute_variant_id`; **sem PK própria** (`@ElementCollection`)

**V45 — `estoque_product_permissions`**
- Cria `ESTOQUE_PRODUCT_READ` e `ESTOQUE_PRODUCT_MANAGE`, concedidas a `ROLE_ADMIN` (e replicadas para `ROLE_DEV` via `DevRoleBootstrapConfig`). **Sem `ON CONFLICT DO NOTHING`** — ver EST-C006.

**V46 — `estoque_warehouse_stock_balance`**
- `warehouse` (id, code UNIQUE `uk_warehouse_code`, name, type VARCHAR(20) [`LOJA_FISICA`|`ECOMMERCE`], active)
- `stock_balance` (id, sku, warehouse_id FK → `warehouse` ON DELETE CASCADE, quantity NUMERIC(14,3) DEFAULT 0, version BIGINT DEFAULT 0) — UNIQUE `uk_stock_balance_sku_warehouse (sku, warehouse_id)`; índice `idx_stock_balance_warehouse_id`

**V47 — `estoque_warehouse_permissions`**
- Cria `ESTOQUE_WAREHOUSE_READ` e `ESTOQUE_WAREHOUSE_MANAGE` para `ROLE_ADMIN`. **Sem `ON CONFLICT DO NOTHING`** — ver EST-C006.

**V55 — `estoque_movement`**
- `stock_movement` (id, sku VARCHAR(50), warehouse_id FK → `warehouse` ON DELETE CASCADE, type VARCHAR(10), quantity NUMERIC(14,3), reason VARCHAR(255), username VARCHAR(80), created_at TIMESTAMP) — índice composto `idx_stock_movement_sku_warehouse_created (sku, warehouse_id, created_at)` para o histórico ordenado

**V56 — `estoque_movement_permissions`**
- Cria `ESTOQUE_STOCK_MANAGE` para `ROLE_ADMIN`, com `ON CONFLICT DO NOTHING`

**V62 — `estoque_stock_count`**
- `stock_count` (id, warehouse_id FK → `warehouse` ON DELETE CASCADE, status VARCHAR(20) [`ABERTA`|`FECHADA`|`CANCELADA`], username, created_at, closed_at NULL) — índice `idx_stock_count_warehouse_status`
- `stock_count_item` (id, stock_count_id FK → `stock_count` ON DELETE CASCADE, sku, counted_quantity NUMERIC(14,3), expected_quantity NULL, difference NULL) — UNIQUE `uk_stock_count_item_count_sku (stock_count_id, sku)`; índice `idx_stock_count_item_count_id`
- **Sem permissão nova:** o balanço reusa `ESTOQUE_STOCK_MANAGE`, a mesma que já autoriza movimentar saldo — fechar uma contagem é exatamente isso, em lote.

**V61 — `stock_reorder_points`**
- `stock_reorder_point` (id, sku, warehouse_id FK → `warehouse` ON DELETE CASCADE, min_quantity NUMERIC(14,3)) — UNIQUE `uk_stock_reorder_point_sku_warehouse (sku, warehouse_id)`

**V63 — `estoque_product_pricing`**
- `product` ganha `cost_price NUMERIC(14,2)`, `markup_percent NUMERIC(9,4)` e `sale_price NUMERIC(14,2)`, todas **NULLABLE** — preço desconhecido não é preço zero, e um `DEFAULT 0` faria o PDV vender de graça em vez de recusar item sem preço. Sem backfill: todo produto já cadastrado passa a existir como não precificado.
- CHECKs `ck_product_cost_price_non_negative`, `ck_product_markup_percent_non_negative` e `ck_product_sale_price_non_negative`, espelhando as invariantes de `Pricing` — redundantes por desenho, porque o schema é a barreira que sobrevive a carga direta e import de planilha.
- Escala **4** em `markup_percent` (e não 2) porque markup é input de fórmula, não valor de exibição: 33,3333% sobre custo 45,00 tem que reproduzir 60,00 no preço sugerido, e centavo errado em preço de prateleira vira divergência de caixa.
- Cria `ESTOQUE_PRODUCT_PRICE_MANAGE` para `ROLE_ADMIN`, com `ON CONFLICT DO NOTHING`.
- **`product_variant` não recebe colunas de preço** — a variação herda o preço do pai. Ver EST-F020.

**V87 — `estoque_product_search_indexes`**
- Índices funcionais `idx_product_category_lower` e `idx_product_brand_lower` sobre `LOWER(category)`/`LOWER(brand)`, servindo aos filtros de igualdade de `GET /estoque/products`.
- **Sem índice para a busca textual, de propósito:** ela é `LIKE '%termo%'`, com curinga à esquerda, e btree não serve — o planner faz seq scan de qualquer jeito. Criar um daria a falsa impressão de busca indexada. Quando o catálogo crescer a ponto de doer, a resposta é `CREATE EXTENSION pg_trgm` + GIN sobre `LOWER(name)`/`LOWER(sku)`.

**V88 — `estoque_product_root_attributes`**
- `product_root_attribute` (product_id FK → `product` ON DELETE CASCADE, attr_type, attr_value) — índice `idx_product_root_attribute_product_id`; **sem PK própria** (`@ElementCollection`, mesmo padrão de `product_attribute`)
- Tabela nova em vez de tornar `product_attribute.variant_id` anulável: aquela tabela tem FK e índice para `variant_id`, e admitir linha sem variação exigiria coluna nula em metade das linhas mais um CHECK garantindo que exatamente um dos dois donos está preenchido.
- **Sem UNIQUE em `(product_id, attr_type)`** — o mesmo tipo pode repetir com valores diferentes (dois sabores num blend), como já era em `product_attribute`.

**V89 — `estoque_variant_pricing`**
- `product_variant` ganha `cost_price NUMERIC(14,2)`, `markup_percent NUMERIC(9,4)`, `sale_price NUMERIC(14,2)` e `original_price NUMERIC(14,2)`, todas **NULLABLE** — as quatro nulas são exatamente como "esta variação herda do pai" fica representada. Sem `DEFAULT` e sem backfill, pelo mesmo motivo da V63.
- CHECKs de não-negatividade espelhando os do produto pai.
- **Revoga a nota da V63** de que `product_variant` não recebe colunas de preço — ver EST-F020 no histórico.

**V90 — `estoque_product_category`**
- `product_category` (id, name UNIQUE `uk_product_category_name`, featured DEFAULT FALSE, display_order INTEGER DEFAULT 0 com CHECK `>= 0`, active DEFAULT TRUE)
- Índice **único funcional** `uk_product_category_name_lower` sobre `LOWER(name)`: a UNIQUE comum é sensível a caixa, mas a resolução por nome (caminho de compatibilidade, em que o admin ainda manda texto livre) é case-insensitive — sem ele, "Narguilé" e "narguilé" passariam e virariam duas categorias que a aplicação trata como uma.
- `product` ganha `category_id BIGINT` NULL com FK `fk_product_category` e índice `idx_product_category_id`. **`product.category` (texto) permanece** como nome denormalizado, mantido em sincronia pela aplicação — é o que `mahal-market` e `mahal-admin` leem, e trocá-lo por FK quebraria os dois.
- **Backfill:** cada valor distinto de `product.category` vira categoria (agrupado por `LOWER(TRIM(...))` para não colidir com o índice acima, gravando a primeira grafia que entrou no catálogo), os produtos são vinculados e seus textos alinhados à grafia canônica. Sem destaque e com ordem 0 — adivinhar hierarquia aqui seria inventar.
- Cria `ESTOQUE_CATEGORY_MANAGE` para `ROLE_ADMIN`, com `ON CONFLICT DO NOTHING`.

**Nota de modelagem:** `stock_balance`, `stock_movement` e `stock_reorder_point` referenciam
`warehouse(id)` por FK, mas guardam `sku` como **texto livre** — não há FK para `product.sku`
nem para `product_variant.sku`. Ver EST-C002.

## Cobertura de Testes

| Arquivo | Tipo | O que cobre |
|---|---|---|
| `core/service/EstoqueServiceTest` | Unit (Mockito) | Todos os casos de uso, incluindo os 3 cenários de alerta de reposição, os 3 de histórico de movimentações e os 10 de precificação (EST-F019: PATCH parcial que não apaga campo, herança de preço pai→variação, SKU fora do catálogo) |
| `core/domain/model/estoque/StockBalanceTest` | Unit de domínio | `zero`/`of`, invariantes, e os 5 cenários de `apply` (entrada, saída, drenar a zero, insuficiente, ajuste) |
| `core/domain/model/estoque/StockMovementTest` | Unit de domínio | `create`/`of` e todas as invariantes |
| `core/domain/model/estoque/WarehouseTest` | Unit de domínio | `create`/`of`, obrigatoriedade de code/name/type e os `with*` de EST-F018 |
| `adapter/in/controller/EstoqueControllerTest` | MockMvc standalone | 29 casos: 200/201/204/400/404/409 dos 8 endpoints |
| `adapter/in/controller/EstoqueControllerValidationTest` | `@SpringBootTest` + MockMvc real | Bean Validation dos `@RequestParam`/`@PathVariable`: faixa de `page`/`size` nos 4 endpoints paginados, `sku`/`warehouseCode` em branco ou fora do tamanho, e a distinção entre `VALIDATION_ERROR` e `MISSING_PARAMETER`. **Não é standalone de propósito** — a validação de parâmetro de handler é aplicada pelo `RequestMappingHandlerAdapter`, não pelo controller |
| `adapter/in/controller/EstoqueControllerSecurityTest` | MockMvc + Security | 401 sem auth / 403 sem authority / sucesso com a authority correta, endpoint a endpoint — inclui o 403 de `WAREHOUSE_READ` no histórico de movimentações |
| `infra/config/DevRoleBootstrapConfigTest` | Unit (Mockito) | 4 casos: `ROLE_DEV` recebe as permissões de negócio (incl. `PDV_SALE_MANAGE`), as `DEV_ONLY_*`, e não cria usuário sem `DEV_EMAIL` |
| `infra/config/SeedConfigTest` | Unit (Mockito) | `ROLE_ADMIN` recebe as permissões `ESTOQUE_*` e `PDV_SALE_MANAGE` no seed de dev |
| `adapter/in/controller/EstoqueAlertaIT` | `@SpringBootTest` (profile `dev`) | E2E do alerta: depósito → ENTRADA 20 → mínimo 10 → SAIDA 12 → notificação em `GET /notifications` |
| `core/service/ComprasServiceTest` | Unit | Recebimento ajusta estoque por item; falha do estoque (saldo, depósito ou SKU desconhecido) propaga e não salva o receipt |
| `core/service/PdvServiceTest` | Unit | Venda dá baixa por item; `InsufficientStockException` e `ProductNotFoundException` revertem a venda inteira |
| `core/domain/model/estoque/SkuPackagingTest` | Unit de domínio | EST-F032: invariantes da ligação, `parentsToOpen` arredondando para cima, `childUnits` |
| `core/service/PackagingBreakIT` | `@SpringBootTest` (profile `dev`) | EST-F032 ponta a ponta pela venda do PDV: 2 carteiras, 25 soltos → 1 carteira, 8 maços, 15 soltos; maço sem maço fechado abre carteira; cadeia que não cobre falha; leitura da cadeia com disponível |
| `core/service/OpenPackageConcurrencyIT` | `@SpringBootTest` (profile `dev`) | EST-C025: 8 sessões simultâneas na mesma lata registram exatamente 8 usos, sem baixar estoque |
| `core/service/StockBalanceConcurrencyIT` | `@SpringBootTest` (profile `dev`) | 8 escritas simultâneas no mesmo saldo: sem lost update, conflitos tratados; idem na primeira movimentação do par |
| `adapter/out/persistence/repository/EstoqueRepositoryIT` | `@SpringBootTest` + `@Transactional` | Os 6 `*RepositoryImpl`: round-trip de produto com variações/atributos, `existsBySku` em SKU pai e de variação, paginação ID-first, propagação do `version`, ordem do ledger, upsert do ponto de reposição, e os 7 cenários da query nativa de SKU órfão |
| `infra/transaction/TransactionAfterCommitExecutorTest` | Unit | Agregação por chave, despacho único no commit, silêncio no rollback, isolamento entre transações da mesma thread, falha de um lote não derruba o próximo |
| `core/domain/model/estoque/ProductTest` | Unit de domínio | `create`/`of`, cópia defensiva das variações, e os `with*` de EST-F018: semântica de "null = manter", preservação de sku/active/variações e invariantes que continuam valendo |
| `core/domain/model/estoque/PricingTest` | Unit de domínio | EST-F019, 35 casos em 6 grupos: invariantes de não-negatividade, preço sugerido (incl. arredondamento HALF_UP e as 4 casas do markup), precedência do preço praticado sobre o sugerido, markup × margem (o caso custo 50 / venda 100 = 100% e 50%), divisões indefinidas devolvendo `null`, venda abaixo do custo, PATCH parcial e `materializeSuggestion` |
| `core/domain/model/estoque/StockCountTest` | Unit de domínio | Ciclo de vida do balanço, upsert de item preservando posição e id, contagem zero, `closed()`/`cancelled()` |
| `core/domain/model/estoque/StockCountItemTest` | Unit de domínio | `reconciledWith` (falta, sobra e contagem que bateu) e `diverges()` |
| `adapter/in/controller/EstoqueInventarioIT` | `@SpringBootTest` (profile `dev`) | E2E do balanço: abrir → contar 3 SKUs → fechar → conferir saldo e ledger; contagem zero, cancelamento, duplo fechamento, recontagem e SKU fora do catálogo |
| `core/domain/model/estoque/OrphanSkuTest` | Unit de domínio | Invariantes do retrato de diagnóstico: obrigatoriedade de `sku`/`warehouseCode`, `quantity` nula vira zero, `movementCount` não-negativo, `lastMovementAt` nulo permitido |

**Lacunas conhecidas:** o histórico de movimentações tem cobertura de service, controller e
segurança, mas ainda não tem um IT end-to-end que grave movimentações reais e as releia pelo
endpoint. `EstoqueRepositoryIT` roda contra H2 em modo PostgreSQL, como os demais ITs
do projeto — divergências específicas do Postgres continuam fora de cobertura automatizada.

## Testes no Postman

Coleção do módulo: [`estoque.postman_collection.json`](estoque.postman_collection.json) — importe no Postman, rode a pasta
`00 — Autenticação` (que faz login e guarda o `accessToken`) e siga as pastas na ordem, ou
rode tudo de uma vez no Collection Runner.

```bash
npx newman run docs/dominios/estoque/estoque.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

**O que a coleção cobre**

| Pasta | Requisições |
|---|---|
| `01 — Depósitos` | criação, listagem e o 409 de código duplicado |
| `02 — Produtos` | criação com variações e atributos, listagem paginada, 409 de SKU duplicado e 400 de validação |
| `03 — Saldo e movimentações` | saldo zerado inicial, `ENTRADA` → `SAIDA` → `AJUSTE` conferindo o saldo a cada passo (o `AJUSTE` confere o **saldo-alvo**, não a soma), entrada no SKU de variação, e os erros `INSUFFICIENT_STOCK`, quantidade negativa, depósito inexistente e `PRODUCT_NOT_FOUND` |
| `04 — Ponto de reposição e alerta` | upsert do mínimo, saída que **não** cruza o mínimo, saída que cruza, a conferência da notificação em `GET /notifications` e o `PRODUCT_NOT_FOUND` do mínimo em SKU fora do catálogo |
| `05 — Segurança` | 401 sem token e com token inválido |
| `06 — Integridade` | levantamento de SKU órfão conferindo que os SKUs cadastrados pela própria coleção **não** são acusados, o 400 de `size` acima do teto e o 401 sem token |
| `08 — Balanço de inventário` | ciclo completo num depósito próprio: abrir, o 409 do segundo balanço, contar, recontagem que sobrescreve, SKU fora do catálogo, fechar conferindo `expectedQuantity`/`difference`, o saldo ajustado, o 409 do duplo fechamento e a listagem |
| `07 — Edição e desativação` | PATCH parcial de produto e depósito (nome muda, categoria/tipo e SKU/código ficam), corpo vazio como no-op, os 400 de validação, e o ciclo desativar → `ENTRADA` 409 → `SAIDA` 201 → reativar |

O SKU e o código de depósito são gerados com timestamp a cada execução, então a coleção é
reexecutável sem limpeza manual.

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).


**V122 — `estoque_busca_sem_acento`** (EST-C020)
- `CREATE EXTENSION IF NOT EXISTS unaccent` — *trusted* desde o PG13, não exige superusuário em banco gerenciado.
- **Sem índice**, mantendo a decisão registrada na V87: a busca textual do catálogo não é indexada enquanto couber num `LIKE` sequencial, e o caminho para quando não couber continua sendo `pg_trgm` + GIN. Um índice funcional sobre `unaccent()` exigiria antes empacotá-la numa função `IMMUTABLE` própria (a nativa é `STABLE`) — trabalho que só se paga junto com a troca para `pg_trgm`.
- Contraparte no H2 do perfil `dev`: alias em `db/dev/dev-schema.sql` apontando para `H2Unaccent`, para o mesmo HQL valer nos dois bancos.

**V123 — `estoque_backfill_brand_id`** (EST-C023)
- Repete os três passos do backfill da V107 de forma idempotente, para a base semeada **depois** dela: cria as marcas que só existiam em texto, vincula `product.brand_id` casando por `LOWER(unaccent(TRIM(...)))` e alinha o texto à grafia canônica.
- Produto sem marca nenhuma continua com `brand_id` nulo — estado válido desde a V107. Inventar marca seria pior que a coluna vazia.

**V124 — `estoque_lata_aberta`** (EST-F027 / PDV-F018)
- `open_package` (id, sku, warehouse_id FK → `warehouse`, uses DEFAULT 0, sessions_per_unit, opened_at, opened_by, closed_at NULL, close_reason NULL) — CHECKs de `uses` entre 0 e `sessions_per_unit`, de `sessions_per_unit > 0`, de `close_reason ∈ {EXHAUSTED, REPLACED}` e de que `closed_at`/`close_reason` vêm **juntos** (meio estado é o que o compact constructor do domínio recusa; o banco recusa junto para carga direta não abrir a exceção).
- Índice único **parcial** `uk_open_package_sku_warehouse_open ... WHERE closed_at IS NULL` — uma lata em uso por par, e não `UNIQUE` simples porque a tabela é histórico e guarda todas as fechadas do mesmo par. Molde dos parciais da V75. Mais `idx_open_package_warehouse_open` para a leitura da tela.
- `sku` **sem FK** para `product`, mesma decisão de `stock_balance`/`stock_movement` (EST-C011): pode ser SKU de variação, que vive em outra tabela, e a checagem de existência mora no service.
- `comanda_item.package_uses` / `package_sessions_per_unit` (nullable, com os mesmos dois CHECKs) — qual uso da lata a linha foi, congelado no lançamento. Snapshot e não FK para `open_package.id`: o histórico continua verdadeiro depois da reposição, e é por aqui que o cancelamento sabe, meses depois, que a linha consumiu **uso** e não unidade. `NULL` é toda linha anterior a esta migration; **sem backfill**, porque não há como saber quantas latas de fato foram abertas no passado.

**V149 — `estoque_produto_base_vendavel`** (EST-F036)
- `product.parent_sellable BOOLEAN NOT NULL DEFAULT FALSE` — **para todos, inclusive os já cadastrados** (decisão do dono, 2026-10-08). Efeito só em produto com variações (regra do domínio). Quem precisa vender a base liga por `PATCH .../parent-sellable`.

**V148 — `estoque_embalagem`** (EST-F032)
- `sku_packaging` (`child_sku` PK, `parent_sku`, `units_per_parent`) — CHECKs `units_per_parent > 1` e `child_sku <> parent_sku`; índice `idx_sku_packaging_parent`. PK no filho: um SKU está dentro de no máximo uma embalagem. Sem FK para `product`, como as demais colunas de SKU; as duas colunas entram em `ProductRepositoryImpl.SKU_COLUMNS` (EST-F030). Ciclo e profundidade são regras do service.

**V125 — `compras_supplier_manage_permission`** (COM-F001) — documentada em [`compras`](../compras/README.md).

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| EST-F005 | 🟡 Média | Feature | importacao-nfe-xml | Entrada de mercadoria por XML de NF-e (`NfeXmlImportPort`) gerando `StockMovement` de entrada — diferencial operacional. | ✅ Fechado (2026-08-18) — ver Histórico abaixo. |
| EST-F011 | 🟢 Baixa | Feature | curva-abc-giro | Análise ABC e giro de produtos para priorização de compras (domínio `relatorios`). | ✅ Fechado (2026-08-31) — `GET /estoque/analytics/abc`, sobre `stock_movement`, sem domínio `relatorios` novo. Ver Histórico. |
| EST-F012 | 🟢 Baixa | Feature | transferencia-entre-depositos | `MovementType.TRANSFER`: saída atômica de um `Warehouse` + entrada em outro, distinto do ajuste manual. **Só faz sentido quando existir um segundo local físico de verdade** ([`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.2): o marketplace **não** vai usar `WarehouseType.ECOMMERCE` para separar canal — para uma tabacaria de uma loja, a prateleira é uma só, e partir o pool geraria rebalanceamento manual permanente e o absurdo de "o site tem 5 e a loja tem 0" com tudo no mesmo armário. A reserva (EST-F013) é o mecanismo que permite um pool servir dois canais. | ⏸️ Despriorizado por decisão (revisto em 2026-08-31) — segue sem caso de uso enquanto houver um só local físico. **EST-F025 entregou o que a operação de fato precisava**: a conversão atômica entre SKUs é o mesmo desenho (duas pontas numa transação) aplicado onde há demanda diária. |
| EST-F016 | 🟢 Baixa | Feature | unidade-medida-conversao | Múltiplas unidades por produto (compra em kg, venda em porção/g) com fator de conversão nas movimentações. | Backlog (Sprint 6) |
| EST-C006 | 🟢 Melhoria | Correção | migrations-v45-v47-sem-on-conflict | V45 e V47 inserem permissões sem `ON CONFLICT DO NOTHING`, ao contrário de V56/V57/V60. Re-execução em base parcialmente populada quebra. Herdado do antigo C018. | ✅ Fechado (2026-08-31) — **como decisão, não como código**: não há correção possível no arquivo. Ver a nota em §Schema de Banco e o Histórico. |
| EST-C014 | 🔴 Alta | Correção | productimagecontroller-sem-teste | `ProductImageController` (`adapter/in/controller/ProductImageController.java:39-45`) implementa a mesma guarda anti-path-traversal que `AvatarController`, mas não existe nenhum `ProductImageControllerTest` — só `ProductImageServiceTest`, que não passa pelo MockMvc/guard do controller. Endpoint **público** (`GET /product-images/{filename}`, sem autenticação): a defesa nunca foi exercitada via HTTP real. Criar `ProductImageControllerTest` espelhando `AvatarControllerTest` (casos `".."`/`"/"`/`"\\"`). Achado em auditoria `analyze-domain`/testes de 2026-08-18. | ✅ Fechado (2026-08-18) — `ProductImageControllerTest` criado, cópia adaptada de `AvatarControllerTest` (4 casos: `LocalFile`, `Redirect`, `NotFound`, guarda de `..`). |
| EST-C015 | 🟡 Importante | Correção | permitir-leitura-de-movements-com-product-read | `EstoqueController.listMovements` (`adapter/in/controller/EstoqueController.java:894`) exige `ESTOQUE_STOCK_MANAGE` — permissão de **escrita** — para uma leitura, enquanto o resto do módulo lê com `ESTOQUE_PRODUCT_READ`. Efeito no consumidor: o `global-error.interceptor` do `frontend-admin-prod` manda todo `GET` 403 para `/app/access-denied`, então um gerente sem `STOCK_MANAGE` é **expulso da tela** em vez de ver um aviso. Pedido formal em `frontend-admin-prod/Docs/BACKEND_TODO.md` §"Duas dívidas de permissão": trocar por `hasAnyAuthority('ESTOQUE_PRODUCT_READ','ESTOQUE_STOCK_MANAGE')` no `GET`, mantendo o `POST /estoque/movements` em `STOCK_MANAGE`, que é o correto. `EstoqueControllerSecurityTest.list_movements_with_warehouse_read_only_returns_403` (`:347`) codifica o comportamento atual e muda junto. Levantado na `/1-analise` de vendas-balcao (2026-08-28). | ✅ Fechado (2026-08-31) — trocado por `hasAnyAuthority('ESTOQUE_PRODUCT_READ','ESTOQUE_STOCK_MANAGE')`; ver Histórico abaixo. |
| EST-C016 | 🔴 Alta | Correção | reservedstockexception-sem-handler-responde-500 | `ReservedStockException` (`core/domain/exception/estoque/ReservedStockException.java`) é lançada por `StockBalance.apply` em `SAIDA` (`:166`) e no `AJUSTE` abaixo do reservado (`:152`), mas **não tinha `@ExceptionHandler`** — `grep -rn "ReservedStockException" src/main/java` devolvia só a própria classe e `StockBalance`. Caía no `@ExceptionHandler(Exception.class)` (`GlobalExceptionHandler:1103`) e virava **500 `INTERNAL_ERROR` "Erro interno inesperado"**, descartando a mensagem do domínio — justamente a que diz quanto está reservado, que é o que resolve o caso para o operador. A classe existe **para** não se confundir com `InsufficientStockException` (que tem handler desde sempre, `:403`): *"'não tem' e 'tem, mas está separado para um pedido online' pedem ações diferentes de quem está no balcão"*. O contrato `400 RESERVED_STOCK` já estava especificado em [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.2 e na tabela de endpoints do §9, e nunca foi implementado. Alcançável por `POST /estoque/movements`, `PdvService.registerSale`, o fechamento de balanço — e, com pool único, por `ComandaService.addItem:182`, o caminho da **mesa**: o atendente lança a essência que o marketplace reservou e recebe 500. Cobertura anterior: só `StockBalanceTest` (domínio), nunca via HTTP. Achado na análise de estoque×mesa de 2026-08-30. | ✅ Fechado (2026-08-30) — ver Histórico abaixo. |
| EST-F025 | 🟡 Importante | Feature | conversao-atomica-entre-skus | Converter 1 lata de essência em N sessões de narguilé — a operação diária do lounge — são hoje **dois `POST /estoque/movements` independentes** disparados pelo admin (`estoque-conversao.dialog.ts`): `SAIDA` do SKU origem e `ENTRADA` do SKU destino, cada um em sua transação. Se o segundo falhar (409 de `@Version`, 403, rede), **a lata saiu do saldo e nenhuma sessão entrou**, sem compensação nem rastro de que os dois movimentos eram um só ato. `sessions_per_unit` (V112) não fecha o buraco: a própria migration diz que ele *"NÃO movimenta saldo sozinho"*, e uma varredura confirma que o campo só aparece em DTO, converter, entity e patch de catálogo — nunca em `EstoqueService.adjustStock`. Desenho decidido com o dono em 2026-08-30: `POST /estoque/conversions` (`ESTOQUE_STOCK_MANAGE`) com `{fromSku, toSku, fromQuantity, toQuantity, warehouseCode, reason}`, **uma transação** chamando `adjustStock(SAIDA)` + `adjustStock(ENTRADA)` — validação de SKU, `@Version`, alerta de reposição, explosão de kit e FEFO vêm de graça e nenhuma regra é duplicada. Os dois `StockMovement` gravam `reason` cruzado. `toQuantity` é **explícito no request**, não derivado de `sessions_per_unit`: o campo é sugestão de UI por decisão da V112, e derivar no servidor amarraria o saldo a um número que o admin edita no catálogo. **Não** usa um `MovementType.TRANSFER` novo — acrescentar valor ao enum mexe no `CHECK` de `stock_movement` e na semântica de `AJUSTE` (saldo-alvo, não delta); dois movimentos comuns numa transação entregam a atomicidade sem tocar nele. Recusar `fromSku == toSku`. É o desenho de EST-F012 aplicado entre **SKUs** em vez de entre depósitos — e, ao contrário de F012, tem caso de uso hoje. | ✅ Fechado (2026-08-31) — `POST /estoque/conversions`; ver Histórico abaixo. |
| EST-C017 | 🟢 Melhoria | Correção | readme-de-estoque-nao-conhece-a-mesa | `grep -rn -i "comanda" docs/dominios/estoque/` volta **vazio**, embora a mesa seja hoje o consumidor do `EstoqueUseCase` com o padrão de baixa mais distinto de todos (item a item, um commit por lançamento, sem reserva). Quatro lacunas: (1) §Integrações entre Domínios declara *"as **duas** integrações"* e lista só `receiveGoods` e `registerSale`, quando há pelo menos **seis** portas de escrita — somam-se `ComandaService.addItem`/`removeItem`/`cancelComanda` e `OrderService.refundOrder`; (2) os quatro campos que a V112 acrescentou a `product`, que é tabela **deste** domínio (`available_for_table`, `session_product`, `sessions_per_unit`, `open_rosh_price`), não estão no §Modelo de Domínio nem no §Schema daqui, nem em `persistence.md`, `domain-model.md` ou `feature-registry.md` — só na migration e no README do PDV; (3) **`EST-F024`** (mutação da grade de variantes pós-criação) está implementado, com `EstoqueVariantMutationIT` e seções próprias em `EstoqueControllerTest`/`EstoqueServiceTest`, e **não aparece em nenhum arquivo de `docs/`** — como `.claude/commands/1-analise.md:79` manda extrair os IDs usados do README para achar o próximo livre, quem seguir a instrução ao pé da letra **reatribui EST-F024 e colide**; (4) há dois headings `## Próximos passos` consecutivos. É o espelho de PDV-C006, que arrumou o lado do PDV e nunca foi feito deste lado. | ✅ Fechado (2026-08-31) — §Integrações reescrita, campos da V112 documentados, EST-F024 registrado. |
| EST-C018 | 🔴 Alta | Correção | movements-responde-500-em-toda-chamada | `GET /estoque/movements` respondia **500 em qualquer chamada**, com ou sem filtro: `StockMovementJpaRepository.search` usava o padrão `:param IS NULL OR ...` com `Instant`, e o Postgres real recusa inferir o tipo do bind nulo (`could not determine data type of parameter $7`). A aba Movimentações ficava inteiramente inutilizável, e o pior é que a **escrita continuava funcionando** — o operador registrava movimento e nunca conseguia conferir. O projeto já tinha resolvido este bug em `OrderRepositoryImpl.findAll`, com Specification, e o javadoc de `ProductJpaRepository.search` já avisava que o padrão "só vale para filtros de `String`/`Boolean`"; a regra existia e não tinha sido aplicada aqui. Reportado como EST-001 no QA de 06/09/2026 do `frontend-admin-prod`. | ✅ Fechado (2026-09-08) — Specification em `StockMovementRepositoryImpl`, com o desempate de EST-C012 preservado no `Sort`. **Não** o CAST explícito que o front sugeriu: ele tem bug conhecido de Hibernate/pgjdbc que troca o tipo do parâmetro por `bytea`. |
| EST-C019 | 🔴 Alta | Correção | alertascriticos-descarta-os-sku-zerados | `ReorderPointJpaRepository.countAlertsRaw` cruzava ponto de reposição com saldo por `JOIN`, então SKU com mínimo cadastrado e **sem linha em `stock_balance`** — o que nunca recebeu entrada, portanto saldo zero — ficava fora das duas contagens. O `summary` dizia "nenhum produto crítico" enquanto a tela de Alertas listava três SKUs zerados. O QA provou lançando ENTRADA+SAIDA de 1 num deles: `alertasCriticos` saltou de 0 para 1 sem nada mudar no estoque real. EST-004 do QA de 06/09/2026. | ✅ Fechado (2026-09-08) — `LEFT JOIN` + `COALESCE(sb.quantity, 0)`. |
| EST-C020 | 🟡 Importante | Correção | busca-de-produto-ignora-caixa-mas-nao-acento | `search=Carvão` devolvia 6 e `search=carvao` devolvia 0. O efeito pior é o do narguilé: a base tem produtos cadastrados **com e sem** acento, então cada busca devolvia só o seu grupo — quem digitava "narguile" via 6 de 14 e recebia uma lista plausível, incompleta e sem nenhum sinal de que faltava metade. EST-021 do QA de 06/09/2026. | ✅ Fechado (2026-09-08) — extensão `unaccent` (V122) aplicada nos **dois** lados da comparação, com `UnaccentFunctionContributor` registrando a função no HQL e alias equivalente no H2 do perfil `dev`. Sem índice, mantendo a decisão da V87. |
| EST-C021 | 🟢 Melhoria | Correção | mensagem-de-nfe-invalida-vaza-o-parser | `MalformedNfeXmlException` concatenava `e.getMessage()` do `SAXException`: prefixo em português, substância em inglês e em jargão de parser Java, exibida direto na tela. Pior, o comentário do código dizia que a mensagem era "deliberadamente genérica para não confirmar a um atacante se foi malformado ou DOCTYPE bloqueado" — e o texto do Xerces confirma exatamente isso. Código e comentário discordavam. EST-024 do QA de 06/09/2026. | ✅ Fechado (2026-09-08) — `fromParser(Throwable)` com frase de usuário; o detalhe técnico vive na causa e no log, alcançável pelo `traceId` que o `ApiError` já devolve. |
| EST-C022 | 🟡 Importante | Correção | valorestoquecusto-sem-regra-escrita | O QA achou R$ 1.562,49 de diferença entre `summary.valorEstoqueCusto` e a soma dos saldos, e a causa não era um erro de conta: **não havia regra escrita**, então servidor e tela escolheram critérios diferentes e chegaram a três números. EST-005 do QA de 06/09/2026. | ✅ Fechado (2026-09-08) — regra decidida e documentada na `@Operation` e aqui: `RASCUNHO` fica **fora** (cadastro em construção não é mercadoria), produto e depósito **inativos ficam dentro** (desativar tira de circulação, não da prateleira, e o total precisa bater com a contagem física). |
| EST-C023 | 🟡 Importante | Correção | backfill-de-brand-id-na-base-semeada | 190 produtos com marca em texto e **zero** com `brandId`; as 73 marcas cadastradas todas com `productCount: 0`. Não era defeito de código nem falha da V107 — o catálogo de demonstração foi semeado por `scripts/` **depois** da migration, inserindo direto em `product`. Na tela: coluna MARCA em "—", filtro de marca sem nada para filtrar, "Todas as Marcas" listando 73 cards de "0 SKUs". EST-008 do QA de 06/09/2026. | ✅ Fechado (2026-09-08) — V123 repete os três passos da V107 de forma idempotente, casando por `LOWER(unaccent(...))`. **O seed continua reabrindo o buraco**: toda carga em massa que não passe pelo `EstoqueService` nasce sem vínculo. |
| EST-F026 | 🟡 Importante | Feature | excluir-rascunho-de-produto | O 409 `DRAFT_LIMIT_REACHED` orientava uma ação que o sistema não oferecia: dizia "publique ou remova um rascunho", e remover não existia. `PATCH .../active` com `false` **não** liberava a vaga (`status` e `active` são eixos independentes), então a única saída era publicar no catálogo um produto que o operador não queria publicar — cinco rascunhos abandonados desligavam o recurso para o tenant inteiro. EST-020 do QA de 06/09/2026. | ✅ Fechado (2026-09-08) — `DELETE /estoque/products/{sku}`, restrito a `RASCUNHO` (409 `PRODUCT_NOT_DRAFT` no publicado) e recusando rascunho com saldo/movimentação (409 `PRODUCT_HAS_STOCK_HISTORY`, mesma régua de EST-C011). |
| EST-F027 | 🔴 Alta | Feature | lata-de-essencia-aberta | `sessions_per_unit` existia em `product` desde a V112 e **nunca era lido por ninguém**: cada sessão de narguilé baixava uma lata inteira. Medido no QA de 06/09/2026: `ESSE-ZGY-BLUEBERRY` foi de 50 para 49 numa sessão só — com `sessionsPerUnit: 5`, o estoque some cinco vezes mais rápido que a realidade, o alerta de reposição dispara cedo e a margem do open rosh, que é o número que o dono quer olhar, sai errada. Pedido levantado com o dono. | ✅ Fechado (2026-09-08) — `open_package` (V124) com contador de usos por `(sku, depósito)`, `GET /estoque/open-packages`, `.../{sku}` e `POST .../{sku}/replace`. **A baixa acontece na abertura**: o saldo passa a significar "latas lacradas na prateleira", que é o que o operador conta no balanço. Par de PDV-F018. |
| EST-F032 | 🔴 Alta | Feature | hierarquia-de-embalagem-e-quebra-automatica | Central de cigarros: a carteira tem 10 maços e o maço tem 20 unidades, e a loja vende **solto e em maço**. Hoje não há relação entre SKUs: `ProductType` é só `SIMPLES`/`KIT`, `Product.unit` é rótulo, e abrir embalagem é um `POST /estoque/conversions` manual (EST-F025). Decisão do dono (2026-10-08): **três SKUs ligados**, cada saldo sendo o que está fisicamente na prateleira (carteiras lacradas, maços lacrados, cigarros soltos). Desenho: `product.parent_sku` + `units_per_parent` (V147). Em `adjustStock(SAIDA)` (`EstoqueService:1261`), se o saldo do filho não cobre a quantidade, chama `convertStock(pai→filho, 1, fator)` (`:1136`) **na mesma transação**, em cascata (unidade sem saldo abre maço, maço sem saldo abre carteira), quantas vezes for preciso, com `reason` "Quebra automática de embalagem (reposição)" nos dois movimentos. Só falha com `InsufficientStockException` quando a cadeia inteira não cobre. Recusar ciclo, auto-referência, profundidade > 3, `KIT` e `lotTracked` (lote atravessando embalagem fica fora desta entrega). Inclui cadastro em família (um POST cria os três SKUs já ligados). A compra ("comprei N carteiras") é ENTRADA comum no SKU carteira. Diferente de EST-F016 (unidade de medida dentro de **um** SKU). Par de **PDV-F041**. Pedido do dono, `/1-analise` de 2026-10-08. | ✅ Fechado (2026-10-08) — `sku_packaging` (V148) + quebra em `adjustStock`; cadastro em família ficou fora (ver Histórico). |
| EST-F033 | 🔴 Alta | Feature | registrar-lata-ja-aberta | As essências da mesa saem da prateleira da loja, e o dono quer cadastrar as latas **já abertas** para ter controle e, a partir daí, baixa automática. Mas `OpenPackage.open` (`:71`) sempre nasce com `uses = 0` e `EstoqueService.openPackage` (`:1235`) sempre faz `SAIDA 1`: não há como registrar uma lata aberta **antes** do sistema sem baixar outra unidade, nem dizer quantos usos ela ainda tem. Desenho: `POST /estoque/open-packages/{sku}` com `{warehouseCode, usesRemaining}` cria a lata com `uses = sessionsPerUnit − usesRemaining`, **sem SAIDA**. Só vale se não houver lata aberta para `(sku, depósito)` (o índice parcial de V124 já garante) e para produto com `sessionProduct` e `sessionsPerUnit`. Permissão de `/replace` (`ESTOQUE_STOCK_MANAGE` ou `PDV_COMANDA_MANAGE`) e evento de auditoria próprio, porque é a única entrada no contador que não passa pelo ledger. Par de **PDV-F042**. Pedido do dono, `/1-analise` de 2026-10-08. | ✅ Fechado (2026-10-08) — `POST /estoque/open-packages/{sku}`, sem `SAIDA`; ver Histórico. |
| EST-F034 | 🟡 Média | Feature | preco-promocional-com-vigencia | Não existe promoção no sistema: `onSale`/`superPromo` (V83) são flags de vitrine e `Pricing.originalPrice` é só o preço riscado (`Pricing.java:39-41`). Desenho: `product_promotion` com `sku`, `%` ou preço, `startsAt`/`endsAt`, `origin` (`MANUAL`/`VALIDADE`), `lot_id` opcional e quem criou. `Pricing.effectivePrice()` passa a considerar a promoção vigente. Como **os dois canais** resolvem preço por ali (`PdvService.registerSale:284-296` via `resolveSaleInfo`; `ShopService:138-149` e `:369`), a promoção vale no PDV e no catálogo do shop de uma vez, e `onSale` passa a ser derivado. **⚠️ Decisão do dono pendente:** o [plano](../../plano-pdv-marketplace.md) §8.3 tirou cupom e promoção do escopo para não ter dois descontos compondo com o cashback. Este item é **preço**, não motor de desconto (sem cupom, combo nem regra), e o cashback incide sobre o preço já reduzido, mas a decisão de 8.3 precisa ser revista explicitamente antes da sprint. Ver nota em [`ecommerce`](../ecommerce/README.md#backlog-do-módulo). Pedido do dono, `/1-analise` de 2026-10-08. | Pendente |
| EST-F035 | 🟡 Média | Feature | sugestao-de-promocao-por-validade | Essências de narguilé perto do vencimento. A validade já existe **por lote** (EST-F008: `stock_lot.expiry_date`, FEFO na saída), e decisão do dono (2026-10-08) é usá-la, e não uma data única no produto. Hoje o lote só gera aviso: `StockLotExpiryAlertService` notifica 7 dias antes e não há listagem dos lotes vencendo entre produtos (só `GET /estoque/products/{sku}/lots`). Desenho: `GET /estoque/lots/expiring?days=` lista os lotes com saldo e o desconto sugerido (configurável; padrão ≤ 30 dias → 20%, ≤ 15 dias → 30%), e `POST /estoque/lots/{lotId}/promotion` aceita a sugestão com um clique, criando a promoção de **EST-F034** (`origin = VALIDADE`) até a validade do lote. Ela encerra sozinha quando o lote zera. O alerta diário passa a citar a sugestão. Pré-requisito operacional: marcar as essências como `lotTracked` e informar a validade na entrada. Depende de EST-F034. Pedido do dono, `/1-analise` de 2026-10-08. | Pendente |
| EST-C025 | 🟡 Importante | Correção | lata-aberta-sem-trava-de-concorrencia | `OpenPackageEntity` **não tem `@Version`** e `OpenPackageRepositoryImpl.save` é read-modify-write (`findById`, reescreve `uses`). `consumeSession`/`releaseSession` (`EstoqueService:1159`, `:1184`) leem a lata sem trava: dois atendentes lançando sessão do mesmo sabor ao mesmo tempo leem `uses = 2` e gravam `3` os dois — **um uso some**, e a lata rende uma sessão a mais do que a realidade. O índice parcial da V124 só protege a **abertura** (duas latas), não o contador. Hoje inalcançável em produção (a sessão do cardápio não consome lata), mas **PDV-F042 liga esse caminho**: precisa entrar antes dela. Molde: o `@Version` de `StockBalanceEntity` com o 409 de `OptimisticLockingFailureException`, ou a trava pessimista de PDV-C008. Achado no Gate 1 da sprint de EST-F033 (2026-10-08). | ✅ Fechado (2026-10-08) — trava **pessimista** na leitura da lata, não `@Version`; ver Histórico. |
| EST-F036 | 🟡 Média | Feature | produto-pai-com-variacoes-nao-vendavel | O produto **base** de um produto com variações aparece como vendável e aceita movimento, embora o estoque real esteja nas variações. Exemplo do dono (2026-10-08): "LM cigarro" com azul e vermelho mostra **três** itens no PDV — a base, que não existe na prateleira, e as duas cores; o mesmo vale para "Essência Zig" com os sabores. Hoje não há guarda em `EstoqueService` (só `KitHasVariantsException` para kit). Proposta: flag por produto "a base é vendável?" — padrão **não** quando há variações —, aplicada em `registerSale`, `adjustStock`, no checkout e na listagem do PDV/catálogo. PDV-F042 já recusa a base como essência (`ESSENCE_MUST_BE_FLAVOR`, via `CatalogSaleInfo.parentWithVariants`); este item generaliza. Ligado a EST-F032/PDV-F041 (cigarros). | ✅ Fechado (2026-10-08) — `parent_sellable` (V149), padrão desligado para todos; ver Histórico. |

> **PDV-F041** (central de cigarros) e **PDV-F042** (essência do catálogo na sessão de mesa), em
> [`vendas-balcao`](../vendas-balcao/README.md#backlog-do-módulo), são o lado do PDV de **EST-F032** e
> **EST-F033**. A baixa do estoque continua sendo deste módulo.

## Histórico de Implementações

- **2026-10-08** — `produto-pai-com-variacoes-nao-vendavel` (EST-F036, **V149**). "LM" com azul e vermelho
  aparecia como **três** itens no PDV — a base, que não existe na prateleira, e as duas cores. Novo
  `product.parent_sellable` e `Product.isSellable(sku)`: com variações, a base não se vende nem recebe
  entrada até o dono ligar (`PATCH /estoque/products/{sku}/parent-sellable`). **Decisões:** (1) padrão
  **desligado para todos**, inclusive os já cadastrados — decisão do dono; (2) a guarda de venda fica em
  `resolveSaleInfo`, por onde passam PDV, mesa, essência da sessão e checkout, então é um ponto só e
  antes de qualquer gravação; (3) entrada bloqueada, **saída e ajuste liberados** — o dono escolheu
  bloquear também o ajuste para cima, mas o fechamento do balanço aplica `AJUSTE` e travaria inteiro por
  uma base com contagem acima do saldo, então o ajuste ficou livre (registrado com o dono); (4) a lista
  de latas lê o nome por `findProductBySku`, para ler não passar pela guarda de venda; (5) o produto
  devolve `parentSellable`, que é o que o PDV usa para esconder a base na busca. **Fica de fora:** o
  carrinho do site aceita a base e a recusa só aparece no checkout. Testes: `ProductTest` (+3),
  `EstoqueServiceTest` (+6), `EstoqueControllerTest` (+3, e dois testes de lata passaram a mockar
  `findProductBySku`), `EstoqueControllerSecurityTest` (+1).

- **2026-10-08** — `hierarquia-de-embalagem-e-quebra-automatica` (EST-F032, **V148**). O cigarro é comprado
  por carteira (10 maços) e vendido em maço e solto (20 por maço), e não havia relação entre SKUs: abrir
  embalagem era `POST /estoque/conversions` à mão. Nova `sku_packaging` ("o pai contém N do filho") com
  `PUT`/`DELETE`/`GET /estoque/products/{sku}/packaging`, e a **quebra automática**: toda `SAIDA` que não
  cabe no disponível de um SKU ligado abre o pai sozinha, em cascata, na mesma transação. **Decisões:**
  (1) modelo do dono — um produto ("LM") com variações **cor × embalagem**, cada uma com SKU, preço e
  código de barras próprios; o produto base não entra em embalagem; (2) as duas pontas são
  autoinvocação de `adjustStock` (mesmo idioma de `explodeKitMovement`), por isso a cascata é de graça
  e a falta na cadeia inteira reverte a venda; motivos próprios ("Quebra automática de embalagem →/←"),
  sem `MovementType` novo; (3) o filho entra com o custo médio do pai ÷ fator; (4) a conta usa o
  **disponível** (reserva não é aberta para cobrir a si mesma) e o gancho só consulta a embalagem quando
  falta — o caminho comum não paga leitura a mais; (5) teto de **4 níveis** (fardo → carteira → maço →
  unidade), ciclo recusado; kit, base com variações e produto com lote recusados — lote atravessando
  embalagem não tem regra; (6) `STOCK_PACKAGE_BROKEN` para a decisão do sistema, `PACKAGING_DEFINED/REMOVED`
  para a do operador. **Fora:** o cadastro em família (um POST que cria a grade inteira) — a grade de
  variações já existe; e a criação de reserva não quebra embalagem. Testes: `SkuPackagingTest` (6),
  `EstoqueServiceTest` (+13), `EstoqueControllerTest` (+5), `EstoqueControllerSecurityTest` (+3),
  `PackagingBreakIT` (4).

- **2026-10-08** — `lata-aberta-sem-trava-de-concorrencia` (EST-C025): o contador de `open_package`
  era read-modify-write sem trava, e dois atendentes lançando o mesmo sabor perdiam um uso. Novo
  `OpenPackageRepository.findOpenForUpdate` (`PESSIMISTIC_WRITE`), usado pelos quatro caminhos de
  escrita (`consumeSession`, `releaseSession`, `replaceOpenPackage`, `registerOpenPackage`); as
  leituras seguem sem trava. **Pessimista e não `@Version`**, como a comanda (PDV-C008): as escritas
  fazem fila e todas passam, em vez de o atendente perder a corrida com 409 no meio do salão — e sem
  migration. **Limite que fica:** sem lata aberta não há linha a travar, então duas sessões que
  encontram o par vazio (ou a lata acabando no mesmo instante) abrem cada uma a sua; a segunda colide
  no índice parcial da V124 e responde 409 com a `SAIDA` revertida. É a janela da troca de lata, rara,
  e o estoque não fica errado. Teste: `OpenPackageConcurrencyIT` (novo); `EstoqueServiceTest` passou a
  mockar a leitura travada. Pré-requisito de PDV-F042, que liga esse caminho em produção.

- **2026-10-08** — `registrar-lata-ja-aberta` (EST-F033): `POST /estoque/open-packages/{sku}` com
  `{warehouseCode, usesRemaining}` cadastra uma lata que **já estava aberta** antes de o sistema saber
  dela — o inventário inicial das essências da mesa, pedido do dono. Antes, toda lata nascia por
  `openPackage`, que sempre faz `SAIDA 1`: não havia como registrar a lata da bancada sem tirar do
  saldo uma segunda, ainda lacrada. **Decisões:** (1) **sem `SAIDA`** — a lata já não está no saldo de
  lacradas; daí em diante segue o ciclo de EST-F027 (esgotou, a próxima sessão abre outra com baixa);
  (2) o operador informa o que vê, **sessões restantes**, e o domínio guarda o gasto
  (`OpenPackage.registered`: `uses = sessionsPerUnit − usesRemaining`); (3) com lata já em uso é **409
  `OPEN_PACKAGE_ALREADY_OPEN`**, e a corrida entre dois cadastros, barrada pelo índice parcial da V124,
  responde o mesmo código (o handler lê o nome da constraint, como PLAT-C055); (4) evento próprio
  `OPEN_PACKAGE_REGISTERED`, porque é a única entrada no contador sem rastro em `stock_movement`;
  (5) mesma permissão do `/replace` — quem cadastra é o atendente. Sem migration. Testes:
  `OpenPackageTest` (+3), `EstoqueServiceTest` (+4), `EstoqueControllerTest` (+5),
  `EstoqueControllerSecurityTest` (+2), `GlobalExceptionHandlerTest` (+1). O Gate 1 desta sprint abriu
  **EST-C025** (contador da lata sem trava), pré-requisito de PDV-F042.

- **2026-09-23** — `kit-montavel` (EST-F031, com ECM-F008 e PDV-F019): o "Kit Mahal", em que o
  cliente escolhe bag, seda, piteira, tubeck, tesoura, cuia e isqueiro e paga a soma dos itens menos
  um desconto %. Novas tabelas `kit_template`/`kit_template_step` (**V126**), CRUD em
  `/estoque/kit-templates` sob a nova `ESTOQUE_KIT_TEMPLATE_MANAGE`. **Decisões que valem registro:**
  (1) não é o kit de EST-F015 — a receita muda a cada venda, então o modelo não tem SKU nem saldo, e
  cada item escolhido vira linha comum de carrinho/comanda agrupada por `kit_bundle_id`, com a baixa
  de estoque item a item pelo caminho de sempre; (2) o passo aponta para **categoria**, não para uma
  lista de SKUs, para produto novo entrar no kit sem ninguém editar o modelo; (3) toda regra mora em
  `KitBuilderService.quote`, chamado por carrinho, checkout e comanda — o checkout recota mesmo que
  o carrinho já tenha validado, porque o carrinho não guarda preço; (4) o desconto é rateado por
  linha com `DiscountProration` e gravado em `OrderItem.discountAmount`, onde cashback e margem já o
  enxergam; (5) passos são atualizados no lugar por id, porque o carrinho guarda o passo de cada item.
- **2026-09-23** — `troca-de-sku` (EST-F030): `PATCH /estoque/products/{sku}/sku`. O SKU era
  imutável porque nenhuma das ~17 colunas que o guardam tem FK para cascatear. A troca roda um
  `UPDATE` por coluna de `ProductRepositoryImpl.SKU_COLUMNS` numa transação só, **inclusive o
  histórico**. A lista é protegida por `ProductRepositoryPostgresIT.renameSku_coversEverySkuColumnInSchema`,
  que a compara com o `information_schema`: **migration nova com coluna de SKU precisa entrar na lista**.
- **2026-09-23** — `busca-livre-por-categoria-e-marca` (EST-F029): `search` passa a cobrir categoria e
  marca, além de nome e SKU, no admin e no catálogo público (`GET /shop/catalog?search=`).

- **2026-09-08** — `lata-de-essencia-aberta` (EST-F027): `sessions_per_unit` existia em `product`
  desde a V112 e **nunca era lido por ninguém** — a própria migration o declarava como "sugestão de
  tela, não movimenta saldo". A consequência foi medida no QA de 06/09/2026 do
  `frontend-admin-prod`: cada sessão de narguilé baixava uma **lata inteira**
  (`ESSE-ZGY-BLUEBERRY` foi de 50 para 49 numa sessão só). Com `sessionsPerUnit: 5`, o estoque
  sumia cinco vezes mais rápido que a realidade — o alerta de reposição disparava cedo, o custo por
  sessão saía inflado e a margem do open rosh, que é a pergunta de negócio por trás da feature
  inteira, saía errada. Nova tabela `open_package` (**V124**) com contador de usos por
  `(sku, depósito)`, mais `GET /estoque/open-packages`, `GET .../{sku}` e
  `POST .../{sku}/replace`. **Cinco decisões que valem registro:**
  (1) **A baixa acontece na ABERTURA**, não a cada sessão nem na reposição — decisão do dono do
  produto. É o que mantém o significado do saldo igual ao que o operador conta no balanço:
  `stock_balance` passa a ser *latas lacradas na prateleira*, e a lata em uso vive aqui. Nenhum
  movimento é inventado: a `SAIDA` acontece no instante físico em que alguém tira a lata da
  prateleira.
  (2) **`sessions_per_unit` é copiado para a lata na abertura**, não lido do catálogo a cada uso. O
  admin pode corrigir o cadastro no meio da noite, e uma lata pela metade não pode mudar de tamanho
  por causa disso.
  (3) **A lata esgotada continua aberta** até a sessão seguinte. É a que o atendente está
  terminando, e é o que permite a tela mostrar "5 de 5"; quem a fecha como `EXHAUSTED` é a próxima
  sessão, ao abrir a seguinte.
  (4) **Reposição antecipada não vira perda.** `POST .../replace` fecha a lata como `REPLACED` com
  a sobra registrada (`uses < sessionsPerUnit`) e não lança ajuste: a unidade já saiu do saldo na
  abertura, e transformar o resto em perda criaria movimento para medir uma quantidade que ninguém
  mediu. A lata nova é aberta **antes** de a velha ser fechada, mesma razão pela qual
  `convertStock` faz a `SAIDA` primeiro — falhar depois de fechar deixaria o atendente sem lata
  nenhuma no sistema, com uma na mão.
  (5) **Fronteira com EST-F025.** `POST /estoque/conversions` continua existindo como ferramenta
  **genérica** de reembalagem entre SKUs distintos (comprei em fardo, vendo em unidade); a
  **essência sai daquele caminho**, porque com a lata origem e sessão são o mesmo SKU — o produto
  de sessão, com `openRoshPrice` e `sessionsPerUnit` próprios. Sem essa fronteira escrita, o
  operador ficaria com duas verdades sobre a mesma lata.
  Nada de histórico é reprocessado: latas nascem zeradas a partir daqui e o saldo atual fica como
  está — recalcular comandas antigas geraria movimento retroativo sem lastro físico. Par de
  **PDV-F018**, que é o lado da mesa.
- **2026-09-08** — `excluir-rascunho-de-produto` (EST-F026): `DELETE /estoque/products/{sku}`,
  restrito a `status: RASCUNHO`. O 409 `DRAFT_LIMIT_REACHED` de EST-F023 orientava uma ação que o
  sistema não oferecia — "publique ou remova um rascunho" —, e `PATCH .../active` com `false`
  **não** liberava a vaga, porque `status` e `active` são eixos independentes: a única saída era
  publicar no catálogo um produto que o operador não queria publicar, e cinco rascunhos abandonados
  desligavam o recurso para o tenant inteiro. Produto publicado responde 409 `PRODUCT_NOT_DRAFT`
  com a mensagem apontando o `active:false` — apagar do catálogo deixaria órfão o histórico que
  referencia o SKU como texto livre, sem FK (EST-C011). Pelo mesmo motivo, rascunho com saldo ou
  movimentação, no SKU pai **ou em qualquer variação**, responde 409 `PRODUCT_HAS_STOCK_HISTORY` —
  mesma régua de `deleteVariant`. Auditoria `PRODUCT_DELETED`, gravando o **nome** junto do SKU: é o
  único evento do módulo cujo objeto não existe mais depois dele.
- **2026-09-08** — `movements-respondia-500-em-toda-chamada` (EST-C018): bloqueador em produção.
  `GET /estoque/movements` respondia 500 em **qualquer** chamada, com ou sem filtro, porque
  `StockMovementJpaRepository.search` usava `:param IS NULL OR ...` com `Instant` e o Postgres real
  recusa inferir o tipo do bind nulo. E era pior do que falhar por inteiro: a **escrita continuava
  funcionando**, então o operador registrava movimento e nunca conseguia conferir. Trocado por
  `Specification`, preservando o desempate por `id` de EST-C012. **Não** o CAST explícito que o
  frontend sugeriu — o javadoc de `OrderRepositoryImpl.findAll` já registra que ele tem bug
  conhecido de Hibernate/pgjdbc que troca o tipo do parâmetro por `bytea`. O projeto já tinha
  resolvido este mesmo bug ali e em `AuditLogRepositoryImpl`, e a regra estava escrita em
  `ProductJpaRepository.search`; faltou aplicá-la aqui.
- **2026-09-08** — `alertascriticos-descartava-os-sku-zerados` (EST-C019): `countAlertsRaw` cruzava
  ponto de reposição com saldo por `JOIN`, e SKU que nunca recebeu entrada não tem linha em
  `stock_balance` — sumia das duas contagens, justamente o caso mais grave. O `summary` dizia
  "nenhum produto crítico" enquanto a tela de Alertas listava três SKUs zerados. `LEFT JOIN` +
  `COALESCE(sb.quantity, 0)`, que é a mesma leitura que a tela de Alertas já fazia.
- **2026-09-08** — `busca-de-produto-ignorava-caixa-mas-nao-acento` (EST-C020): extensão `unaccent`
  (**V122**) aplicada nos **dois** lados da comparação, com `UnaccentFunctionContributor`
  registrando a função no HQL pelo SPI do Hibernate e alias equivalente no H2 do perfil `dev`
  (`db/dev/dev-schema.sql`, mesma paridade que já mantinha `order_number_seq`). Só na coluna
  resolveria "carvao" achar "Carvão" e deixaria "Carvão" digitado sem achar "carvao" gravado — e a
  base tem as duas grafias. **Sem índice**, mantendo a decisão da V87. `category`/`brand` ficam de
  fora: são igualdade exata contra valor escolhido em lista, não texto digitado.
- **2026-09-08** — `valorestoquecusto-sem-regra-escrita` (EST-C022): a diferença de R$ 1.562,49 que
  o QA achou entre o `summary` e a soma dos saldos não era erro de conta — era ausência de regra, e
  cada lado escolheu um critério. Decidido e escrito: `RASCUNHO` **fora** (cadastro em construção
  não é mercadoria da loja), produto e depósito **inativos dentro** (desativar tira de circulação,
  não da prateleira, e o total precisa bater com a contagem física).
- **2026-09-08** — `backfill-de-brand-id-na-base-semeada` (EST-C023): 190 produtos com marca em
  texto e zero com vínculo, 73 marcas com `productCount: 0`. Não era defeito de código nem falha da
  V107 — o catálogo de demonstração foi semeado por `scripts/` **depois** da migration, inserindo
  direto em `product`. **V123** repete os três passos da V107 de forma idempotente, casando por
  `LOWER(unaccent(...))`. O seed continua reabrindo o buraco: toda carga em massa que não passe
  pelo `EstoqueService` nasce sem vínculo.
- **2026-09-08** — `mensagem-de-nfe-invalida-vazava-o-parser` (EST-C021): `MalformedNfeXmlException`
  concatenava `e.getMessage()` do `SAXException`, e o comentário do código dizia que a mensagem era
  genérica "para não confirmar a um atacante" enquanto o texto do Xerces confirmava exatamente isso.
  Nova factory `fromParser(Throwable)`: frase de usuário no corpo, detalhe técnico na causa e no
  log, alcançável pelo `traceId`. Os detalhes redigidos por nós ("NF-e sem nenhum item", "arquivo
  vazio") continuam no corpo — são em português e dizem o que consertar na nota.

- **2026-07-15** — `cadastrar-produto` (EST-F001): grade de produtos com SKU pai, variações e atributos; listagem paginada com padrão ID-first + `JOIN FETCH` (`ProductJpaRepository.findAllIds` + `findAllByIdsWithVariants`); RBAC `ESTOQUE_PRODUCT_READ`/`MANAGE`; migrations V44/V45.
- **2026-07-15** — `controle-saldo-multi-deposito` (EST-F002): `Warehouse` (código único, loja física/e-commerce) e `StockBalance` por SKU/depósito com `@Version`; consulta de saldo retorna zero quando ainda não houve movimentação; RBAC `ESTOQUE_WAREHOUSE_READ`/`MANAGE`; migrations V46/V47.
- **2026-07-15** — `paginacao-repositorios`: `PageResult<T>` adotado nos repositórios do módulo.
- **2026-07-22** — `movimentacao-manual` (EST-F003): `StockMovement` como ledger `ENTRADA`/`SAIDA`/`AJUSTE`, `EstoqueService.adjustStock` transacional sobre `StockBalance`, `POST /estoque/movements`, `InsufficientStockException` (400) e conflito otimista (409 `STOCK_UPDATE_CONFLICT`); RBAC `ESTOQUE_STOCK_MANAGE`; migrations V55/V56.
- **2026-07-22** — `alinhar-permissoes-seed-dev` (C006): permissões `ESTOQUE_*` acrescentadas a `SeedConfig.ADMIN_PERMISSIONS`, eliminando 403 inesperado em `/estoque/**` no perfil dev.
- **2026-07-23** — `alerta-estoque-minimo` (EST-F004): `ReorderPoint`, `PUT /estoque/products/{sku}/reorder-point` com upsert, e notificação via `NotificationUseCase` a todos os usuários com `ESTOQUE_STOCK_MANAGE` (`UserRepository.findUsernamesByPermission`); migration V61; cobertura E2E em `EstoqueAlertaIT`.
- **2026-07-23** — `recebimento-movimenta-saldo` (EST-F009, domínio `compras`): `POST /compras/goods-receipts` chama `adjustStock` com `ENTRADA` por item. Detalhes em [`docs/dominios/compras/README.md`](../compras/README.md).
- **2026-07-23** — `baixa-automatica-venda` (EST-F010, domínio `vendas-balcao`): venda no PDV chama `adjustStock` com `SAIDA` por item e dispara o alerta de reposição. Detalhes em [`docs/dominios/vendas-balcao/README.md`](../vendas-balcao/README.md).
- **2026-07-27** — `permissao-pdv-sale-manage-ausente-no-seed` (EST-C001): `PDV_SALE_MANAGE` acrescentada aos arrays `ADMIN_PERMISSIONS` de `SeedConfig` e `DevRoleBootstrapConfig`, eliminando o 403 de `ROLE_DEV` em `POST /pdv/sessions/{id}/sales` que bloqueava o caminho de baixa automática de estoque em dev. Sem migration — a V57 já cria a permissão e a concede a `ROLE_ADMIN`; o furo era só no bootstrap de runtime. Cobertura nova em `DevRoleBootstrapConfigTest` e `SeedConfigTest`.
- **2026-07-27** — `historico-movimentacoes-endpoint` (EST-F017): `GET /estoque/movements?sku=&warehouseCode=&page=&size=` liga o `StockMovementRepository.findBySkuAndWarehouseId`, que estava órfão desde EST-F003. Novo `EstoqueUseCase.listMovements` (`@Transactional(readOnly = true)`, resolve o depósito por código antes de paginar), `StockMovementResponseDTO` e `StockMovementDTOConverter.toResponse`; RBAC `ESTOQUE_STOCK_MANAGE`; sem migration (o índice `idx_stock_movement_sku_warehouse_created` da V55 já servia à consulta). Junto veio a correção de `GlobalExceptionHandler`, que não tratava `MissingServletRequestParameterException` e devolvia 500 em vez de 400 `MISSING_PARAMETER` para qualquer `@RequestParam` obrigatório ausente — afetava também o `GET /estoque/stock-balance` já existente.

- **2026-07-27** — `validar-existencia-do-sku` (EST-C002): novo `ProductRepository.existsBySku`, resolvido por uma consulta só que cobre SKU pai e SKU de variação (`ProductJpaRepository.existsBySkuOrVariantSku`, apoiada nos índices únicos já existentes da V44 — sem migration). `adjustStock` e `setReorderPoint` passam a exigir SKU conhecido antes de qualquer escrita, lançando `ProductNotFoundException` → 404 `PRODUCT_NOT_FOUND`. Cobre as três portas de escrita de uma vez: movimentação manual, venda no PDV e recebimento em Compras. Optou-se por validação na aplicação em vez de FK no banco, porque `stock_movement` é histórico imutável e uma FK impediria arquivar ou renomear produto. O passivo de saldo órfão anterior à correção ficou registrado como EST-C011.
- **2026-07-27** — `violacao-de-constraint-retornava-500` (EST-C010): `createProduct` só checava o SKU pai, então SKU de variação duplicado batia em `uk_product_variant_sku` e virava 500 com a mensagem do driver no corpo — o javadoc de `EstoqueUseCase` já prometia 409 desde EST-F001. Agora valida SKU pai e de variações (inclusive repetição dentro do próprio payload). Somado a isso, handler de rede de segurança para `DataIntegrityViolationException` → 409 `DATA_INTEGRITY_VIOLATION` com mensagem genérica, que também cobre a corrida de primeira movimentação simultânea do mesmo par SKU/depósito (sem linha anterior não há `version` para conferir; quem protege é a unique constraint).
- **2026-07-27** — `notificacao-reposicao-em-loop-e-na-transacao` (EST-C003): novo port `AfterCommitExecutor` (`core/ports/out`) com implementação em `infra/transaction/TransactionAfterCommitExecutor` sobre `TransactionSynchronizationManager`. O alerta passa a ser acumulado durante a operação e despachado uma única vez após o commit: venda com N SKUs abaixo do mínimo gera um aviso listando os N, a transação de venda não espera o envio, e venda revertida não notifica ninguém. Cobertura em `TransactionAfterCommitExecutorTest` (agregação, commit, rollback, isolamento entre transações da mesma thread).
- **2026-07-27** — `audit-event-ausente-em-venda-e-recebimento` (EST-C004): `PdvController` e `ComprasController` passam a publicar `STOCK_MOVEMENT_REGISTERED`, um evento por operação com `origin`, `warehouseCode`, `type`, `skus` e `itemCount`. A publicação ficou nos controllers porque `HexagonalArchitectureTest` barra `ApplicationEventPublisher` em `core/service`. Fecha também as contrapartes COM-C003 e PDV-C003.
- **2026-07-27** — `lacunas-de-teste-persistencia-e-concorrencia` (EST-C007): `StockBalanceConcurrencyIT` prova que o `@Version` de `stock_balance` impede lost update sob 8 escritas simultâneas (saldo final == baixas confirmadas) e que o perdedor da corrida vira conflito tratado, não 500; cobre também a corrida de primeira movimentação. `EstoqueRepositoryIT` cobre os cinco `*RepositoryImpl` do módulo — round-trip de produto com variações e atributos, `existsBySku` achando SKU pai e de variação, paginação ID-first, propagação do `version`, ordem do ledger e upsert do ponto de reposição.
- **2026-07-27** — `package-info-obsoletos` (EST-C008): os `package-info` de `core/domain/model/estoque` e `core/ports/out/estoque` descreviam o módulo como "esqueleto (TODO)" e listavam como previstos modelos e adapters existentes desde EST-F001/F002; o comentário equivalente em `CoreBeanConfig` também foi corrigido. O único TODO que sobrou é o `NfeXmlImportPort` (EST-F005).

- **2026-07-27** — `inventario-contagem` + `ajuste-de-inventario-so-incrementa` (EST-F006 + EST-C009, feitos juntos porque a semântica de `AJUSTE` era a modelagem do balanço): **EST-C009** — `StockBalance.apply` tratava tudo que não era `SAIDA` como soma, então `AJUSTE` só aumentava saldo e um acerto para baixo precisava virar `SAIDA` falsa, poluindo o ledger. `AJUSTE` passou a ser **saldo-alvo**: a quantidade é o valor contado na prateleira, o saldo passa a valer exatamente aquilo, e zero é alvo válido. O ledger continua replayável — um `AJUSTE` grava o alvo, e reaplicá-lo dá o mesmo resultado. `StockMovement` agora aceita `quantity == 0` **só** em `AJUSTE`, e o `@DecimalMin` do request virou inclusivo (zero em `ENTRADA`/`SAIDA` continua barrado, pelo invariante do domínio → 400 `BAD_REQUEST`). PDV e Compras não foram afetados: usam `SAIDA` e `ENTRADA`, e nada no main consumia `AJUSTE`. **EST-F006** — `StockCount` como sessão de balanço por depósito (`ABERTA` → `FECHADA`/`CANCELADA`), com `StockCountItem` guardando o contado e, no fechamento, o `expectedQuantity` e a `difference`. Escolheu-se sessão em vez de ajuste avulso porque o balanço é o evento que a operação reconhece: dá para contar aos poucos, conferir antes de mexer no saldo, e auditar depois quem contou o quê e quanto faltava. Fechar aplica um `AJUSTE` por item **divergente** — contagem que bateu não gera movimentação —, tudo na mesma transação, e os alertas de ponto de reposição saem agregados após o commit (EST-C003). Um balanço aberto por depósito, porque dois simultâneos contariam o mesmo saldo e se sobrescreveriam. Não há estado "em contagem": uma contagem aberta já está sendo contada; `CANCELADA` resolve o caso real de abandonar o balanço sem aplicar nada. Migration V62; sem permissão nova — reusa `ESTOQUE_STOCK_MANAGE`, já que fechar um balanço é movimentar saldo em lote.
- **2026-07-27** — `atualizar-desativar-produto-deposito` (EST-F018): produto e depósito só tinham `create` e `list` — o campo `active` nascia `true` e nunca mudava. Agora há `PATCH /estoque/products/{sku}`, `PATCH /estoque/warehouses/{code}` e os respectivos `/active`. **PATCH parcial** (`null` = manter) em vez de `PUT`: não existe `GET` de produto por SKU, então um cliente não teria como ler o recurso inteiro antes de reescrevê-lo, e um `PUT` apagaria por omissão. `sku` e `code` ficaram fora da edição — são identidade, e o SKU em especial é referenciado como texto livre por `stock_balance`/`stock_movement`/`stock_reorder_point`: renomeá-lo transformaria todo o histórico do produto em órfão (EST-C011). As variações também ficaram fora, porque mexer na grade altera o espaço de nomes de SKU e exigiria a validação de duplicidade de `createProduct`. **Desativação em endpoint próprio**, não como campo do PATCH, para render `PRODUCT_DEACTIVATED`/`WAREHOUSE_DEACTIVATED` na auditoria em vez de se confundir com uma correção de nome; `active` é `Boolean` com `@NotNull`, para corpo vazio não virar um "desativar" silencioso vindo do default `false`. **Efeito no saldo:** desativado recusa `ENTRADA` (409 `PRODUCT_INACTIVE`/`WAREHOUSE_INACTIVE`) mas continua aceitando `SAIDA` — desativar quer dizer "não reponho mais", e bloquear a saída deixaria preso o saldo que ainda está na prateleira. `AJUSTE` também passa, porque é o caminho de correção de inventário. Novo `ProductRepository.isSkuActive`, que exige produto pai ativo inclusive para SKU de variação: desativar o pai tira a grade inteira de circulação de uma vez. Sem migration — as colunas `active` já existiam desde a V44/V46. Limitação conhecida: com `null` significando "manter", não há como **limpar** a `category`, só trocá-la.
- **2026-07-27** — `validacao-e-paginacao-nos-endpoints-de-leitura` (EST-C005): `EstoqueController` recebeu `@Validated` e os `@RequestParam`/`@PathVariable` ganharam constraints — `page >= 0`, `size` entre 1 e 100, `sku` 3–50 e `warehouseCode` 2–50, espelhando os DTOs de escrita. O `Math.min(size, 100)` silencioso saiu: `size` fora da faixa agora é **400 `VALIDATION_ERROR`**, alinhando `/estoque` com `/compras` e `/pdv`. `GET /estoque/warehouses` passou a ser paginado (`WarehouseRepository.findAll(page, size)` → `PageResult`, ordenado por `id`), o que é **mudança de contrato**: os depósitos saíram da raiz do JSON para `content`. Sem migration. Junto veio a correção de uma ponta de infra que valia para o projeto inteiro: desde o Spring Framework 6.1 a validação de parâmetro de handler é nativa do `RequestMappingHandlerAdapter` e lança `HandlerMethodValidationException`, não `ConstraintViolationException` — sem handler para ela, o `GlobalExceptionHandler` a jogava no catch-all de `Exception` e devolvia **500**. Era o comportamento real de `GET /compras/suppliers?size=200`, cujos `@Min`/`@Max` existiam desde COM-F001 e nunca tinham sido exercitados por teste. Nova `EstoqueControllerValidationTest` com contexto real (o standalone de `EstoqueControllerTest` não reproduz essa montagem).
- **2026-07-27** — `saldo-orfao-ja-existente-na-base` (EST-C011): EST-C002 fechou a porta para novos órfãos, mas o passivo anterior seguia invisível na base — e é ele que contaminaria os relatórios de EST-F006 e EST-F007. Entregue o **levantamento**, não a limpeza: novo port `StockIntegrityRepository` (`core/ports/out/estoque`), query nativa em `StockIntegrityJpaRepository` e `GET /estoque/integrity/orphan-skus` paginado sob `ESTOQUE_STOCK_MANAGE`, mais o script avulso [`scripts/estoque-orphan-skus.sql`](../../../scripts/estoque-orphan-skus.sql) para o caminho DBA. O retrato é o record `OrphanSku` — uma linha por par SKU/depósito, com saldo, contagem e data do último movimento e presença de ponto de reposição, que é o contexto de que a decisão humana precisa. **Nenhum expurgo automático, de propósito:** os dois destinos possíveis (cadastrar o produto que faltava × apagar a digitação errada) são incompatíveis e a consulta não os distingue, então apagar em massa destruiria histórico legítimo — o script traz o bloco de `DELETE` comentado, com lista de SKUs a preencher à mão. Query nativa porque a origem é o `UNION` de três tabelas e JPQL não tem `UNION`; sem migration, e sem permissão nova. Cobertura na `EstoqueRepositoryIT` (7 cenários, incluindo SKU de variação, órfão só com ledger e paginação estável).
- **2026-07-27** — `ordenacao-instavel-do-ledger` (EST-C012): o histórico ordenava só por `created_at DESC`, chave não-única — uma venda com N itens grava N movimentos no mesmo loop e na mesma transação, com `created_at` idêntico. Além da ordem de exibição arbitrária, a paginação de `GET /estoque/movements` ficava instável: com chave de ordenação não-única o banco não garante ordem consistente entre consultas, então a mesma linha podia voltar em duas páginas ou não aparecer em nenhuma. Corrigido com desempate por `id` (`findBySkuAndWarehouseIdOrderByCreatedAtDescIdDesc`); `id` é BIGSERIAL monotônico e dá ordem total. Sem migration — o índice `idx_stock_movement_sku_warehouse_created` continua servindo ao filtro e ao prefixo da ordenação. Achado ao escrever o `EstoqueRepositoryIT` do EST-C007, que reproduziu o cenário de venda multi-item.
- **2026-07-28** — `precificacao-de-produto` (EST-F019): o catálogo não tinha preço em lugar nenhum — `sale_item.unit_price` era o único valor monetário do estoque, **digitado no request de cada venda**, o que fazia o operador do PDV redigitar o preço a cada atendimento e impedia qualquer relatório de faturamento confiável. Entrou o value object `Pricing` (`costPrice`, `markupPercent`, `salePrice`, os três opcionais) embutido em `Product`, migration V63 e `GET /estoque/products/{sku}/price`. **Markup e margem são expostos separados de propósito:** markup é sobre o custo e é o input do lojista ("compro a 50 e quero 100% em cima"), margem é sobre a venda e é o que sobra — custo 50 e venda 100 são 100% de markup e 50% de margem, e confundi-los é o erro clássico de precificação de varejo. Por isso a margem também sai calculada: é ela, e não o faturamento, que dimensiona desconto e cashback. **`salePrice` vence sobre o sugerido** quando informado, para caber preço psicológico (R$ 49,90 em vez dos R$ 48,73 da fórmula); `effectiveMarkupPercent` revela o markup que o preço praticado realmente entrega, contra o pretendido que ficou guardado. Os derivados são calculados no backend e serializados no DTO em vez de deixados para a UI, que os recalcularia com outra regra de arredondamento e divergiria do caixa em centavos. Campos **NULLABLE sem backfill**: preço desconhecido não é preço zero, e um `DEFAULT 0` faria o PDV vender de graça em vez de recusar a venda. Derivado indefinido (custo ausente, divisão por zero) volta `null`, nunca zero — a ausência é informação. Venda abaixo do custo é **sinalizada, não bloqueada** (`belowCost`): queima de estoque e produto-isca são decisões comerciais legítimas. Nova permissão `ESTOQUE_PRODUCT_PRICE_MANAGE`, separada de `ESTOQUE_PRODUCT_MANAGE`, para que quem mantém o cadastro não ganhe de brinde o poder de mexer em preço — checada via SpEL no `@PreAuthorize` (`#request.pricing == null or hasAuthority(...)`), e só quando o corpo traz o bloco `pricing`. Auditoria própria `PRODUCT_PRICE_CHANGED`, para a pergunta "quem baixou o preço disso e quando" não se perder no meio dos `PRODUCT_UPDATED` de renomeação. **Preço mora no SKU pai** e a variação herda (`ProductRepository.findByAnySku`, o caminho do leitor de código de barras no balcão): sabores diferentes da mesma essência custam o mesmo, e preço por variação virou EST-F020. As assinaturas antigas de `createProduct`/`updateProduct` sobreviveram como `default` na interface, então nenhum chamador existente precisou mudar. Cobertura: `PricingTest` (35 casos de domínio, incluindo os arredondamentos e as divisões indefinidas), 10 casos novos em `EstoqueServiceTest`, 7 em `EstoqueControllerTest`, 4 de round-trip em `EstoqueRepositoryIT` e 7 de RBAC em `EstoqueControllerSecurityTest`.
- **2026-07-29** — `reserva-de-estoque-endpoint-scheduler-integridade` (EST-F013/EST-F021/EST-C013): a V64 e o núcleo em `EstoqueService` já existiam sem superfície HTTP nem varredor — `reserved_quantity` era uma coluna que ninguém alimentava pela API, então disponível e físico coincidiam por acidente, não por desenho. Fecha as três pontas que restavam. **Endpoint (F013):** `GET /estoque/reservations` (paginado, filtros opcionais `sku`/`warehouseCode`/`status`, com `warehouseCode` resolvido por depósito e cacheado por request) e `GET /estoque/reservations/{id}`, sob `ESTOQUE_RESERVATION_READ`. **Só leitura, de propósito:** criar, consumir e liberar reserva é orquestração interna — o checkout do marketplace (Fatia 9, ainda não existe) e a liquidação de pedido online no PDV (`consumeReservationsByOwner`, já em produção) — não uma operação que um humano dispara pelo Swagger, então não há `POST`/`{id}/release` aqui. **Scheduler (F021):** novo `StockReservationExpiryCleanupService` (`infra/scheduler`), `@Scheduled` a cada 5 minutos (não diário, como os demais `*CleanupService`) + `@SchedulerLock`, chamando `expireReservations` em lotes de 200 — o TTL padrão da reserva é 30 minutos, e uma varredura diária deixaria estoque travado por quase um dia após vencer. **Integridade (C013):** novo record `ReservationIntegrityMismatch` + `StockIntegrityRepository.findReservationMismatches`, query nativa em `StockIntegrityJpaRepository` no mesmo molde de EST-C011 (união dos candidatos de `stock_balance.reserved_quantity > 0` e `stock_reservation` `ACTIVE`, comparando os dois lados) e `GET /estoque/integrity/reservation-mismatch` sob `ESTOQUE_STOCK_MANAGE` — mesma régua de permissão do órfão de SKU, e 200 com página vazia quando a base está íntegra, não 404. `StockReservationNotFoundException`/`NotActiveException` ganharam handler em `GlobalExceptionHandler` (404 `RESERVATION_NOT_FOUND` / 409 `RESERVATION_NOT_ACTIVE`) — existiam desde a V64 sem nenhuma rota que as alcançasse. Sem migration nova; permissões `ESTOQUE_RESERVATION_READ`/`MANAGE` já vieram seedadas na V64. Cobertura: `ReservationIntegrityMismatchTest` (domínio), casos novos em `EstoqueControllerTest` e `EstoqueControllerSecurityTest`, e 4 cenários em `EstoqueRepositoryIT` para a query de integridade (contador acima do ledger, ledger acima do contador, os dois batendo, e reserva já resolvida saindo do cálculo). **Gap conhecido, não fechado aqui:** o domínio/service de reserva em si (`StockReservation`, os oito métodos de `EstoqueService`, `StockBalance.reserve/consumeReservation/releaseReservation`) segue sem suíte de teste própria — essa entrega só cobriu a query de integridade e a superfície nova.
- **2026-07-29** — `kits-virtuais-um-nivel-so` (EST-F015/EST-F022, Fatia 6): novo `ProductType`
  (`SIMPLES`/`KIT`) em `Product`; kit é virtual, explode em componentes na venda/estorno e nunca
  ganha linha própria em `stock_balance` (§2.10 do plano). Novo `product_kit_component`
  (`kit_sku`, `component_sku`, `quantity`), sem FK pelo mesmo motivo de `stock_balance`/
  `stock_movement` — `component_sku` pode ser SKU de variação. Ciclo e aninhamento são
  impossíveis por construção: `EstoqueService.defineKitRecipe`
  (`PUT /estoque/products/{sku}/kit`, nova permissão `ESTOQUE_KIT_MANAGE`) recusa componente que
  não seja `SIMPLES`, recusa promover a `KIT` um SKU já usado como componente de outro kit, e
  recusa kit com variações. `getStockBalance` deriva o saldo do kit como
  `min(floor(disponível_componente / quantidade_receita))`; `findPricingBySku` deriva o custo
  como a soma de `costPrice * quantity` dos componentes, preservando o `salePrice` próprio do kit
  — componente sem custo torna o custo do kit inteiro `null`, nunca zero. A explosão mora
  inteiramente em `EstoqueService.adjustStock` (chama a si mesmo por componente), então
  `PdvService.registerSale` e `OrderService.refundOrder` não precisaram de nenhuma mudança.
  `AJUSTE` direto num kit é rejeitado (`KitDirectAdjustmentException`); `reason` de cada
  movimento de componente ganha o sufixo `" (kit " + kitSku + ")"`. Migration V73. Bug corrigido
  de passagem, fora deste domínio: `CashbackService.findMarginImpact` excluía todo kit do
  relatório de impacto na margem por ler `product.pricing()` cru em vez de `findPricingBySku`.
  Coberto por `ProductTest`, novo `KitComponentTest`, casos novos em `EstoqueServiceTest`/
  `EstoqueControllerTest`/`EstoqueControllerSecurityTest`, e o novo `KitSaleFlowIT`.
- **2026-07-30** — `cobertura-de-teste-do-nucleo-de-reserva`: fecha o gap que a entrega de
  EST-F013/F021/C013 (2026-07-29) deixou em aberto — o domínio e o service de reserva tinham
  código em produção sem teste próprio, só a query de integridade e a superfície nova (endpoint,
  scheduler) tinham cobertura. Novo `StockReservationTest` (compact constructor de
  `StockReservation` — `expiresAt` posterior a `createdAt`, consistência `status.isActive() ==
  (resolvedAt == null)` —, `isActive`/`isExpiredAt`, e as três transições `consumed`/`released`/
  `expired`). `StockBalanceTest` ganhou os casos de `reservedQuantity` que não existiam: o
  compact constructor (não-negativo, não maior que `quantity`), `reserve`/`releaseReservation`/
  `consumeReservation`, e os ramos de `apply(SAIDA)`/`apply(AJUSTE)` que hoje lançam
  `ReservedStockException` quando o físico bastaria mas parte dele está prometida. `EstoqueServiceTest`
  ganhou os oito métodos de reserva (`reserveStock`, `consumeReservation`, `releaseReservation`,
  `releaseReservationsByOwner`, `consumeReservationsByOwner`, `getStockReservation`,
  `listReservations`, `expireReservations`) — inclusive o TTL default vs. informado, o alerta de
  reposição no caminho de reserva, e o caso de `expireReservations` achar a reserva mas não achar
  mais o saldo (`ifPresent` que não falha). De brinde, `StockReservationExpiryCleanupServiceTest`,
  que faltava para este ser o único `*CleanupService` do pacote sem teste próprio. Nenhuma mudança
  de comportamento ou de API — só a cobertura que já devia existir.
- **2026-07-30** — `lote-e-validade` (EST-F008): o núcleo (`StockLot`, migration V74, FEFO na
  saída/reserva) tinha ficado pronto sem dois pontos que quebravam assim que um SKU virasse
  `lotTracked=true` — receber ou estornar esse SKU lançava `MissingLotInfoException` e abortava a
  transação. **Desenho aditivo, não a reescrita de `StockBalance` que o roteiro antigo descrevia
  como risco** ([`proximos-passos.md`](proximos-passos.md)): `stock_lot` é uma quebra por lote ao
  lado do agregado, mantida na mesma transação de `adjustStock` — `stock_balance` e seu `@Version`
  não mudaram, nem a reserva, que continua lote-agnóstica de propósito (reservar contra o
  disponível agregado basta; só o consumo real precisa saber de qual lote sair). **Compras:**
  `GoodsReceiptItem`/`ComprasService.receiveGoods` ganharam `lotCode`/`expiryDate` opcionais,
  sempre propagados para a sobrecarga de 8 argumentos de `adjustStock` (nula para SKU não
  lote-rastreado, idêntica ao caminho antigo). **Estorno:** `OrderService.refundOrder` ganhou uma
  sobrecarga com `List<RefundItemLot>` — o operador informa em qual lote a mercadoria devolvida
  volta a ficar, casado por SKU; sem isso o sistema não tem como adivinhar. **Kit:** `adjustStock`
  recusa (`UnexpectedLotInfoException`) lote informado num SKU que é kit, em vez de descartar a
  informação em silêncio na recursão de `explodeKitMovement`. **Balanço de inventário:**
  `recordCountedItem` ganhou `lotCode` opcional e `StockCountItem`/`StockCount.withCountedItem`
  passaram a fazer upsert por `(sku, lotCode)` — SKU lote-rastreado é contado lote a lote, não
  agregado. `closeStockCount` reconcilia cada `StockLot` contado via `reconciledTo` (existia desde
  a V74, nunca era chamado) e só então lança **um** `AJUSTE` agregado pela soma dos lotes — o
  branch `AJUSTE` de `adjustStock` continua sem saber de lote, porque o lote já foi reconciliado
  por fora antes da chamada. **Alerta de vencimento:** novo `StockLotExpiryAlertService`
  (`infra/scheduler`, `@Scheduled` diário `0 0 7 * * *` + `@SchedulerLock`, diferente do
  varredor de reserva a cada 5 min — validade se mede em dias, não minutos) chama
  `EstoqueUseCase.alertExpiringLots`, que liga o `StockLotRepository.findExpiringSoon` que
  existia desde a V74 sem nenhum chamador. **Leitura e integridade:**
  `GET /estoque/products/{sku}/lots` (`ESTOQUE_PRODUCT_READ`) e novo record
  `LotIntegrityMismatch` + `StockIntegrityRepository.findLotMismatches` (query nativa no molde de
  EST-C011/C013 — união de quem já tem lote gravado com quem tem saldo físico e é lote-rastreado
  agora, SKU pai ou variação) + `GET /estoque/integrity/lot-mismatch` (`ESTOQUE_STOCK_MANAGE`) —
  fecha o que já estava citado em comentário no código como destino do drift do FEFO. Migration
  V75: `goods_receipt_item.lot_code`/`expiry_date`; `stock_count_item.lot_code` e a troca do
  `uk_stock_count_item_count_sku` por dois índices únicos parciais (mesmo molde de
  `uk_cashback_rate_active_scope`, V69) — `@UniqueConstraint` não expressa `WHERE`, então a
  entidade ficou sem a anotação e a garantia condicional é só de aplicação + migration, como
  `CashbackRateEntity`. Próxima migration livre: V76. **Fora de escopo, por decisão:** reserva por
  lote específico (reservar já garantindo sair do que vence primeiro) não existe — o FEFO só roda
  no consumo efetivo. Cobertura em todas as camadas: domínio (`GoodsReceiptTest`,
  `StockCountItemTest`, `StockCountTest`, `LotIntegrityMismatchTest`), service (`EstoqueServiceTest`,
  `ComprasServiceTest`, `OrderServiceTest`), scheduler (`StockLotExpiryAlertServiceTest`),
  persistência (`EstoqueRepositoryIT`) e controller/segurança (`EstoqueControllerTest`,
  `EstoqueControllerSecurityTest`).

- **2026-08-10** — `upload-de-imagem-de-produto` (BACKEND_TODO §P1-Estoque item 1): o mahal-admin
  já chamava `POST /estoque/products/images` desde o formulário novo de cadastro, e o botão
  "Arquivo" falhava contra o backend, que não tinha o endpoint. `AvatarService` já resolvia
  exatamente este problema (magic bytes, limite de tamanho, storage local ou S3, URL pública), e
  a entrega foi generalizar em vez de duplicar: novo `FileStoragePort` com o contrato que era de
  `AvatarStoragePort`; as duas portas concretas viraram marcadoras vazias que só existem para o
  wiring manual de `CoreBeanConfig` distinguir os beans (o projeto não usa `@Qualifier`).
  `LocalAvatarStorageAdapter`/`S3AvatarStorageAdapter` viraram subclasses finas de
  `LocalFileStorageAdapter`/`S3FileStorageAdapter`, com `keyPrefix` separando `avatars/` de
  `product-images/` no mesmo bucket. A detecção por magic bytes saiu de dentro de `AvatarService`
  para o enum `ImageFormat` (`core.domain`, sem Spring) — o formato é detectado pelo **conteúdo**,
  nunca pela extensão nem pelo `Content-Type`, que são texto controlado por quem faz o upload.
  O upload **não recebe SKU**, de propósito: o admin sobe a imagem enquanto ainda preenche o
  formulário, antes de o produto existir; a contrapartida é imagem órfã sem expurgo automático,
  pelo mesmo motivo de EST-C011. `GET /product-images/{filename}` é público, como
  `/avatars/{filename}`. **Bug pré-existente corrigido no caminho:** `avatar.storage.dir` nunca
  bindava em `AvatarProperties` — o binder do Spring Boot trata o ponto como separador de nível, e
  a chave não casava com o campo `storageDir` de nível único, caindo em silêncio no default; na
  prática `AVATAR_STORAGE_DIR` era ignorado. Passava despercebido porque o valor de hml/prod é
  igual ao default. Corrigido com classe aninhada `Storage`, com teste de regressão. Sem migration.

- **2026-08-10** — `busca-filtro-e-ordenacao-server-side-no-catalogo`: `GET /estoque/products` só
  aceitava `page`/`size`, então o admin baixava o catálogo inteiro e filtrava no cliente — com o
  teto de `size=100` (EST-C005), qualquer catálogo maior fazia busca, KPIs e exportação CSV
  mentirem em silêncio, sem erro nem aviso. Não estava na lista numerada do BACKEND_TODO; apareceu
  ao auditar o consumo real do admin. Novo `ProductFilter` (search/category/brand/active, com
  normalização — branco vira nulo, texto em minúsculas) e `ProductSortField` (lista fechada
  ID/NAME/SALE_PRICE; ordenação por nome de coluna concatenado seria injeção). A ordenação
  **sempre desempata por id**, mesmo pedindo outro campo — nome e preço não são únicos, e paginar
  por chave não-única faz o banco devolver a mesma linha em duas páginas ou em nenhuma (mesma
  lição de EST-C012). `findAllByIdsWithVariants` tem `ORDER BY p.id` fixo, então a ordem pedida é
  reimposta a partir da página de ids — sem isso, ordenar por nome devolveria a página certa na
  ordem errada. Novo `GET /estoque/products/{sku}`, reusando `findProductBySku`, fechando a
  lacuna que o próprio EST-F018 citou como motivo de o PATCH ser parcial. `findAllIds` foi
  removido (`findFilteredIds` com todos os filtros nulos é o mesmo). Migration V87 indexa só os
  filtros de igualdade — busca textual é `LIKE '%termo%'` e btree não serve, ver a migration.
  Retrocompatível: sem parâmetros novos, comportamento idêntico ao anterior.

- **2026-08-10** — `atributos-no-produto-pai` (BACKEND_TODO §P1-Estoque item 3): atributo só
  existia dentro de uma variação, onde faz parte do que **identifica** cada SKU filho. Produto
  sem grade não tinha onde carregar um atributo puramente **descritivo** ("Sabor: Menta" num item
  que não varia). São coleções separadas de propósito — fundi-las faria um dado descritivo do pai
  parecer parte da chave da variação. Nova tabela `product_root_attribute` em vez de tornar
  `product_attribute.variant_id` anulável (aquela tabela tem FK/índice para `variant_id`, e
  admitir linha sem variação exigiria CHECK garantindo exatamente um dono preenchido);
  `ProductAttributeEmbeddable` reaproveitado como está. Semântica de PATCH igual a `images`: nulo
  mantém, lista — inclusive vazia — substitui o conjunto inteiro. Sem `JOIN FETCH` simultâneo com
  `variants`: os dois são bags, e buscar os dois juntos dá `MultipleBagFetchException` — mesma
  escolha já vigente para `images`. Migration V88.

- **2026-08-10** — `preco-por-variacao` (EST-F020, BACKEND_TODO §P1-Estoque item 2): estava
  marcado como "desaconselhado por ora" (`plano-pdv-marketplace.md` §8.5 — sabores da mesma
  essência custam o mesmo, grade com preços distintos deveria virar produtos separados). Decisão
  do dono do produto: implementar, mas com **herança do pai como padrão**, preservando o argumento
  do §8.5 em vez de contrariá-lo — variação sem preço declarado continua herdando exatamente como
  antes. A herança é **por campo**, não tudo-ou-nada: cada valor preenchido na variação vence o do
  pai, cada ausente é herdado (mesma semântica de `Pricing.withPatch`). Tudo-ou-nada falharia em
  silêncio — uma variação que só declara `originalPrice` ficaria sem preço de venda nenhum, porque
  o do pai teria sido descartado junto. Bug pego por teste na primeira versão: `hasOwnPricing()`
  usava `Pricing.isPriced()` (exige preço de venda), então uma variação que só declara o próprio
  custo tinha esse custo silenciosamente ignorado no cálculo do custo de kit — entrou
  `Pricing.isEmpty()` ("nenhum dos quatro campos preenchido"), a pergunta certa. A precedência mora
  em `Product.effectivePricingFor(sku)`, no domínio (não no service): PDV e vitrine precisam da
  mesma regra, e duplicá-la garantiria divergência. **Correção de segurança que veio junto, e sem
  a qual esta fatia seria um furo:** o `@PreAuthorize` de `POST`/`PATCH /estoque/products` checava
  só `#request.pricing == null`; com preço podendo vir dentro de `variants[]`, quem tinha apenas
  `ESTOQUE_PRODUCT_MANAGE` passaria a precificar pela porta lateral, desfazendo a separação que
  EST-F019 criou de propósito. Trocado por `touchesPricing()`, varrendo raiz e variações. Migration
  V89, quatro colunas nullable em `product_variant`, sem backfill (mesmo motivo da V63).

- **2026-08-10** — `categoria-como-entidade` (BACKEND_TODO §P1-Estoque item 6, maior escopo do
  bloco): pedido concreto — mandar uma categoria para a primeira linha do app. Impossível com
  categoria como texto solto, sem registro onde pendurar destaque/ordem. **Decisão central: a
  mudança é aditiva, não substitutiva** — `product.category` (texto) permanece e continua sendo
  devolvido; `mahal-market` e o próprio admin leem aquele campo hoje, e trocá-lo por FK quebraria
  os dois de uma vez. Nova coluna `category_id` opcional; o texto vira nome denormalizado mantido
  em sincronia pelo backend. `EstoqueService.resolveCategory` aceita os dois caminhos: quem manda
  `categoryId` tem o nome resolvido a partir dele; quem manda só texto — o que o admin faz hoje —
  tem a categoria reencontrada por nome (sem diferenciar maiúsculas) ou **criada** se não existir.
  Criar em vez de recusar é deliberado: mantém o fluxo "Nova categoria..." do formulário, e o
  efeito colateral de erro de digitação criar categoria a mais já acontecia com texto puro — só
  que agora é visível e editável. Renomear **propaga** o nome para a coluna denormalizada de todos
  os produtos vinculados (update em massa, não carrega-e-salva). Desativar categoria **não** tira
  produtos de venda — categoria é organização de vitrine, não permissão de venda; sem `DELETE`,
  mesmo motivo de EST-F018. `featured` e `displayOrder` são campos separados de propósito: um
  número só faria "destacar" virar "reordenar todo mundo". `GET /shop/categories` público;
  `GET /shop/catalog` ganhou `?categoryId` e passou a ordenar por destaque → ordem → id (`LEFT
  JOIN` + `COALESCE`, para produto sem categoria não sumir nem depender de como cada banco trata
  NULL). Migration V90, com backfill que colapsa grafias divergentes ("Narguilé"/"narguilé") numa
  categoria só e normaliza o texto dos produtos para a grafia canônica — validado contra
  postgres:16 real. Permissão `ESTOQUE_CATEGORY_MANAGE`, com `ON CONFLICT DO NOTHING` (a convenção
  que V45/V47 violaram — EST-C006) e semeada em `SeedConfig`/`DevRoleBootstrapConfig` para não
  repetir o furo de EST-C001 em `dev`.
- **2026-08-16** — `valorizacao-custo-medio` (EST-F007): o backlog presumia que "o custo já entra
  por lote" desde EST-F008 — **não entrava**: nem `StockLot`, nem `StockMovement`, nem
  `GoodsReceiptItem` capturavam custo de entrada em lugar nenhum. Escopo real acabou maior que o
  descrito: instrumentar a captura de `unitCost` na entrada, e só depois calcular a média. Novo
  campo `StockBalance.averageCost` (nullable), custo médio ponderado **móvel**, recalculado dentro
  da mesma transação de `adjustStock(ENTRADA)` que já escreve saldo e ledger juntos — aditivo,
  mesmo espírito de `reservedQuantity` (EST-F021) e `StockLot` (EST-F008): não mexe em
  `@Version`/granularidade do saldo. Fórmula clássica de custo médio ponderado móvel, com
  `Money.INTERMEDIATE_SCALE`/`Money.ROUNDING` na divisão e `Money.MONEY_SCALE` no resultado final —
  motivo de o refactor de constantes `Money` (`domain/model/Money.java`, consolidando
  `MONEY_SCALE`/`PERCENT_SCALE`/`HUNDRED`/`ROUNDING` antes duplicados em `Pricing`, `OrderItem`,
  `CashbackService`, `PdvService`, e agora também `CashbackRate`/`OrderReportRepositoryImpl`) ter
  sido feito primeiro. **Distinto de `Pricing.costPrice`** (o input manual do lojista, V63, ainda o
  que o PDV usa em tempo real para margem) — os dois coexistem, sem um substituir o outro.
  `unitCost` é opcional mesmo em `ENTRADA` (nem toda entrada tem custo conhecido — balanço de
  inventário nunca tem) e é rejeitado em `SAIDA`/`AJUSTE`/kit com `UnexpectedUnitCostException`
  nova, mesmo molde de `UnexpectedLotInfoException`. `averageCost` volta a `null` quando o saldo
  chega a zero (por `SAIDA`, `AJUSTE` ou `consumeReservation`) — sem estoque, custo é "desconhecido"
  de novo, mesma convenção de ausência-não-é-zero de `Pricing`. Nova sobrecarga de 9 argumentos em
  `EstoqueUseCase.adjustStock`/`ComprasService.receiveGoods` (as anteriores continuam existindo,
  delegando com `unitCost = null` — nenhum chamador existente precisou mudar). `GET
  /estoque/summary` (`valorEstoqueCusto`) passou a usar `COALESCE(average_cost, costPrice efetivo,
  0)` em vez de só `costPrice` — sem endpoint novo, sem permissão nova, mesma precedência de
  variação → pai → produto → zero já documentada na query. Migration V94: `stock_balance
  .average_cost`, `stock_movement.unit_cost`, `goods_receipt_item.unit_cost`, todas
  `NUMERIC(14,2) NULL`. Cobertura: 12 casos novos em `StockBalanceTest` (fórmula, arredondamento,
  reset a zero em `apply`/`consumeReservation`), 5 em `EstoqueServiceTest`, round-trip e query de
  valorização em `EstoqueRepositoryIT`, propagação em `ComprasServiceTest`, aceitação/rejeição em
  `EstoqueControllerTest`. **Kit não ganhou `averageCost` próprio** — kit não tem linha em
  `stock_balance`, e `derivedKitPricing` continua derivando o custo da soma dos componentes; sem
  consumidor no escopo atual para custo por lote (`StockLot` também não ganhou o campo).
- **2026-08-17** — `rascunho-de-produto-kit` (EST-F023): novo `ProductStatus` (`RASCUNHO`/`ATIVO`)
  em `Product`, eixo independente de `active` (disponibilidade) e `type` (produto/kit) — um
  rascunho pode estar `active=true` e ainda assim ficar fora das listagens que filtram por
  `status=ATIVO`. **Sem validação nova no domínio:** a exploração confirmou que hoje só
  `sku`+`name` já são exigidos mesmo em produto "normal" (`EstoqueService.createProduct` não pede
  categoria/preço), então `RASCUNHO` não precisou relaxar nada — e a promoção `RASCUNHO → ATIVO`
  também não ganhou exigência extra, por decisão (manter o comportamento já existente em vez de
  criar uma inconsistência nova). Teto de **5 rascunhos validado no servidor**
  (`ProductRepository.countByStatus`, 409 `DRAFT_LIMIT_REACHED` no 6º) — o frontend já validava,
  mas em memória, e duas abas do mesmo operador furariam o limite sem a checagem aqui; editar um
  rascunho que já é rascunho não conta contra o próprio teto. Filtro novo `GET
  /estoque/products?status=`, mesmo idioma de parâmetro nulo de `type`/`kitComponentEligible`.
  `status` default `ATIVO` quando ausente (dado legado lê como `ATIVO`). Migration V97:
  `product.status VARCHAR(20) NOT NULL DEFAULT 'ATIVO'` + índice. Sem permissão nova — reaproveita
  `ESTOQUE_PRODUCT_MANAGE`/`READ`. Pedido do `mahal-admin` `BACKEND_TODO.md` §"Estoque: rascunho de
  produto/kit", frontend já pronto atrás de `DRAFTS_ENABLED`. Cobertura: `ProductTest`
  (`withStatus`/`isDraft`/default), `EstoqueServiceTest` (limite, edição de rascunho já-rascunho,
  criação sem categoria/preço), `EstoqueControllerTest` (filtro, 409, campo no request/response),
  `EstoqueRepositoryIT` (round-trip do filtro, `countByStatus`).
- **2026-08-18** — `teste-do-productimagecontroller` (EST-C014): `ProductImageController`
  reimplementava a guarda anti-path-traversal de `AvatarController`, mas o endpoint público
  `GET /product-images/{filename}` nunca tinha sido exercitado via HTTP real — só
  `ProductImageServiceTest`, que não passa pelo MockMvc/guard do controller. Novo
  `ProductImageControllerTest`, cópia adaptada de `AvatarControllerTest`: mocka
  `ProductImageUseCase` (não o storage port) e stuba as três variantes de `FileServeResult`. 4
  casos: `LocalFile` (200 + cache), `Redirect` (308), `NotFound` (404), guarda de `..` (404) — sem
  caso separado para `/`/`\`, porque o roteamento do Spring já barra `/` literal num único
  `{filename}` antes de o guard rodar. Sem mudança de comportamento, sem migration.
- **2026-08-18** — `importacao-nfe-xml` (EST-F005): entrada de mercadoria automática lendo o XML
  da NF-e do fornecedor. Fluxo em **duas fases — preview → confirm** — porque
  `ComprasUseCase.receiveGoods` é `@Transactional` tudo-ou-nada, e uma NF-e real com item sem EAN
  batido (comum: `cEAN` ausente ou "SEM GTIN") não pode abortar o recebimento inteiro; o operador
  resolve a pendência manualmente no fechamento, sem editar o XML do fornecedor. **Parser em JDK
  puro** (`DocumentBuilderFactory`/DOM, `adapter/out/nfe/JdkDomNfeXmlImportAdapter`) — o projeto
  não tinha nenhuma lib de XML, e só ~6 campos planos por item precisam ser extraídos, sem
  validação de schema completo da SEFAZ. **Hardening contra XXE obrigatório**
  (`disallow-doctype-decl`, entidades externas gerais/parametrizadas desabilitadas) — testado com
  fixture de ataque real (`file:///etc/passwd` e SSRF via entidade parametrizada), confirmando que
  o parser falha fechado em vez de resolver a entidade. Casamento de SKU por `cEAN` →
  `Product.barcode` via `EstoqueUseCase.findProductByBarcode` (já existia ponta a ponta); linha
  sem EAN volta `UNMATCHED` no preview, resolvida por override manual do operador no `confirm`
  (chaveado por `nItem`, estável entre as duas chamadas). **Fornecedor não cadastrado bloqueia o
  import com 404** (`SupplierNotFoundByTaxIdException`) — sem criação automática, diferente do
  precedente de Categoria: `Supplier.taxId` alimenta conta a pagar/compliance no futuro, dado
  demais para criar sem revisão humana; cadastro de fornecedor (COM-F001) segue como limitação
  conhecida, fora desta entrega. Novo agregado `NfeImport`/`NfeImportLine`
  (`core/domain/model/compras`) persiste o resultado do parsing **entre** as duas requisições
  HTTP — inclusive quando o fornecedor não é encontrado, como `REJECTED` (trilha de auditoria de
  toda tentativa de import, não só das bem-sucedidas). `matchStatus` é **derivado** de
  `matchedSku`, nunca um campo independente. XML bruto persistido via `FileStoragePort`
  (`NfeImportStoragePort`, `keyPrefix`/diretório próprio) para auditoria/disputa com fornecedor —
  **sem** endpoint de leitura pública, diferente de imagem de produto. Endpoints novos
  `POST /compras/goods-receipts/nfe-preview` e `.../nfe-confirm`, reaproveitando
  `COMPRAS_RECEIPT_MANAGE` (mesma autoridade de registrar recebimento manual, sem permissão nova).
  Migration V106 (`nfe_import`/`nfe_import_line`, com `CHECK` de coexistência status↔campos
  espelhando o compact constructor de `NfeImport`). Cobertura:
  `JdkDomNfeXmlImportAdapterTest` (EAN batido, "SEM GTIN", multi-lote, XML malformado, dois
  fixtures de XXE), `NfeImportServiceTest` (Mockito), `NfeImportRepositoryIT` (round-trip, incl.
  `REJECTED` sem fornecedor), `NfeImportIT` (ciclo completo preview→confirm→`GoodsReceipt` contra
  banco real, com produto/fornecedor reais e baixa de estoque conferida),
  `NfeImportControllerTest`/`NfeImportControllerSecurityTest`. Backend-only: feature que o
  `mahal-admin` nunca pediu — anunciada em `Docs/BACKEND_TODO.md` daquele repo.
- **2026-08-30** — `reservedstockexception-sem-handler-responde-500` (EST-C016): `ReservedStockException`
  existia desde EST-F021, era lançada nos dois ramos de `StockBalance.apply` que esbarram no reservado
  (`SAIDA` e `AJUSTE` abaixo do reservado) e **nunca teve handler**. Caía no fallback de
  `Exception.class` e saía como **500 `INTERNAL_ERROR`**, com a mensagem do domínio descartada. A
  ironia é que a classe foi criada exatamente para *não* se confundir com `InsufficientStockException`:
  "não tem" e "tem, mas está reservado para um pedido online" pedem ações diferentes do operador — a
  segunda tem solução (cancelar a reserva pelo painel e vender). Um 500 genérico apagava a distinção
  inteira, e apagava justamente o número que a resolve. Fechado com um `@ExceptionHandler` de quatro
  linhas em `GlobalExceptionHandler`, ao lado do irmão: **`400 RESERVED_STOCK`**, que não é escolha
  nova — é o código que [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) já especificava
  em §2.2 e na tabela de endpoints do §9. Sem migration, sem permissão, sem tocar domínio ou service.
  **Por que apareceu agora:** o caminho mais provável é a **mesa**. Com pool único, um pedido do
  marketplace reserva do mesmo saldo do salão, e `ComandaService.addItem` chama `adjustStock(SAIDA)`
  a cada lançamento — o atendente lança a essência e leva 500. Também alcançável por
  `POST /estoque/movements`, `PdvService.registerSale` e o fechamento de balanço. Cobertura: a
  exceção só tinha teste de domínio (`StockBalanceTest`) e nunca havia sido exercitada via HTTP;
  agora tem `EstoqueControllerTest.registerMovement_reservedStock_returns_400` (que também afirma
  que a mensagem do domínio sobrevive na resposta) e
  `PdvComandaControllerTest.addItem_reservedStock_returns_400`, o caminho da mesa. Achado varrendo a
  interseção estoque×mesa depois que o backlog documentado dos dois módulos já estava sem correções
  — o mesmo padrão que produziu PDV-C017/C018 do outro lado.

- **2026-08-31** — `permitir-leitura-de-movements-com-product-read` (EST-C015): `GET /estoque/movements`
  exigia `ESTOQUE_STOCK_MANAGE`, permissão de **escrita**, para uma leitura — a única do controller
  assim. **O argumento que fechou o caso não era o do card:** o vizinho
  `GET /estoque/products/{sku}/purchase-history` devolve `PageResult<StockMovement>` — o mesmo ledger,
  a mesma entidade — com `ESTOQUE_PRODUCT_READ` desde sempre. A justificativa antiga ("ler o ledger
  expõe quem movimentou o quê") não protegia nada, porque o dado já saía pela porta ao lado; o que ela
  fazia era quebrar uma tela. E o afetado não era hipotético: `SeedConfig.ATENDENTE_PERMISSIONS` dá ao
  `ROLE_ATENDENTE` `PRODUCT_READ` e **não** dá `STOCK_MANAGE`, então quem opera PDV e mesa era
  justamente quem não lia o histórico nem abria o diálogo de conversão — e o `global-error.interceptor`
  do admin manda todo `GET` 403 para `/access-denied`, expulsando o operador em vez de avisá-lo.
  Agora `hasAnyAuthority('ESTOQUE_PRODUCT_READ','ESTOQUE_STOCK_MANAGE')`, o primeiro `hasAnyAuthority`
  do projeto. O `POST` **não** mudou: escrever saldo continua em `STOCK_MANAGE`. Sem migration.
  Cobertura: `list_movements_with_product_read_returns_200` novo, e
  `list_movements_with_warehouse_read_only_returns_403` segue verde de propósito — `WAREHOUSE_READ` não
  é nenhuma das duas —, com o javadoc reescrito, porque a razão dele mudou mesmo sem o resultado mudar.
- **2026-08-31** — `conversao-atomica-entre-skus` (EST-F025): converter 1 lata de essência em N sessões
  de narguilé — a operação diária do lounge — eram **dois `POST /estoque/movements` independentes**
  disparados em sequência pelo admin, cada um em sua transação. Falha no segundo (conflito de
  `@Version`, rede, permissão) e a lata saía do saldo sem nenhuma sessão entrar, sem compensação nem
  rastro de que os dois movimentos eram um ato só. `POST /estoque/conversions` faz os dois numa
  transação: ou acontecem, ou nenhum. **A `SAIDA` vem primeiro de propósito** — é o lado que pode
  faltar saldo, e falhar antes de criar a entrada mantém a regra de "valida tudo antes de escrever" de
  `registerSale`/`addItem`. As duas chamadas a `adjustStock` são autoinvocação do próprio bean: não
  passam pelo proxy, seguem na mesma transação, mesmo idioma de `explodeKitMovement`. Nada foi
  reescrito — validação de SKU, `@Version`, alerta de reposição, explosão de kit e FEFO já moram dentro
  de `adjustStock`, e cada lado passa por todos. **Três decisões que valem registro:** (1) `toQuantity`
  é explícito no request e **não** derivado de `sessions_per_unit`, que a V112 declara como sugestão de
  tela — o saldo não pode depender de um número que o admin edita no catálogo; (2) **não** existe
  `MovementType.TRANSFER` novo, porque acrescentar valor ao enum mexeria no `CHECK` de `stock_movement`
  e na semântica de `AJUSTE` sem entregar nada que a transação já não entregue — são uma `SAIDA` e uma
  `ENTRADA` comuns com `reason` cruzado; (3) `EventType.STOCK_CONVERTED` próprio em vez de dois
  `STOCK_MOVEMENT_REGISTERED`, que descreveriam duas pontas sem relação aparente e perderiam o que
  importa auditar — que foram a mesma decisão (mesma lição de PDV-C014). Exceção nova
  `SameSkuConversionException` → `400 SAME_SKU_CONVERSION`. Sem migration e sem permissão nova: reusa
  `ESTOQUE_STOCK_MANAGE`, o que significa que o `ROLE_ATENDENTE` lê o ledger (EST-C015) mas não
  converte — converter altera saldo. Diferente de **EST-F012**, despriorizado por falta de caso de uso:
  este é o mesmo desenho aplicado entre **SKUs** em vez de entre depósitos, e acontece todo dia.

- **2026-08-31** — `curva-abc-giro` (EST-F011): `GET /estoque/analytics/abc?from=&to=&warehouseCode=`
  (`ESTOQUE_PRODUCT_READ`), classificando os SKUs por **valor consumido** na regra de Pareto e
  devolvendo o giro. **Duas decisões que o card não trazia.** (1) Ficou **em estoque**, não num
  domínio `relatorios` novo: haveria um domínio inteiro para uma rota, e o dado é daqui. (2) A fonte
  é `stock_movement` com `type = 'SAIDA'`, **não** `order_item` — o que precisa ser reposto é tudo
  que saiu da prateleira, e cortesia, perda e o lado de saída de uma conversão (EST-F025) saem sem
  virar venda. A valorização usa `stock_balance.average_cost` (EST-F007), com SKU sem custo entrando
  a zero e caindo em C em vez de sumir do relatório.
  **O detalhe que mudou durante a implementação:** o corte de faixa olha o acumulado **antes** da
  linha, não o de depois. Escrevendo o teste apareceu o caso que decide: um SKU que sozinho vale 90%
  do consumo sairia **B** pelo critério ingênuo — o item mais caro da loja fora da faixa de atenção,
  exatamente o oposto do que a curva existe para dizer. Olhando o acumulado anterior, o item que
  *cruza* o limiar pertence à faixa que estava cruzando, e o primeiro SKU é sempre A.
  A classificação mora em `AbcAnalysis` (domínio puro, molde de `DiscountProration`) e não em SQL,
  porque percentual acumulado, empate e saldo zero são onde este relatório erra e nenhum deles precisa
  de banco para ser provado — `AbcAnalysisTest` tem 12 casos. Giro com saldo zero devolve **null**, não
  infinito: um SKU em ruptura não é o que mais gira, é o que perdeu o denominador. Sem migration, sem
  permissão nova, sem domínio novo.
- **2026-08-31** — `migrations-v45-v47-sem-on-conflict` (EST-C006): **fechado como decisão, não como
  código.** V45 e V47 inserem permissões sem `ON CONFLICT DO NOTHING`, e migration já aplicada não se
  edita sem `flyway repair` — não há correção possível no arquivo. O que sobrava era o card
  reaparecendo em toda análise sem nunca ter uma ação. Vira nota permanente em §Schema de Banco, com a
  regra para o futuro: **toda migration de permissão nova usa `ON CONFLICT DO NOTHING`**, como V56,
  V57, V60, V105, V111, V115, V117 e V119 já fazem. Nenhuma linha de SQL foi tocada.
- **2026-08-31** — `readme-de-estoque-nao-conhece-a-mesa` (EST-C017): até aqui
  `grep -rn -i "comanda" docs/dominios/estoque/` voltava **vazio**, embora a mesa seja o consumidor do
  `EstoqueUseCase` com o padrão de baixa mais distinto de todos. §Integrações declarava *"as duas
  integrações"* e listava compras + `registerSale`; são **dez** pontos de escrita em seis services, e a
  tabela agora os lista. Os quatro campos que a V112 acrescentou a `product` — tabela **deste** módulo —
  entraram no §Modelo de Domínio e no §Schema, onde faltavam desde 26/08. **EST-F024** (mutação da
  grade de variantes) foi registrado: estava implementado, com IT próprio, e ausente de todo `docs/` —
  quem seguisse `.claude/commands/1-analise.md:79` ao pé da letra reatribuiria o ID e colidiria. O
  `## Próximos passos` duplicado saiu. Par de **PDV-C019**, do lado de vendas-balcão.

## Próximos passos

A sprint de 2026-07-27 fechou C002, C003, C004, C005, C007, C008, C009, C010, C011, C012, F006 e F018.
Em 2026-07-29 fecharam também F013/F021/C013 (reserva), F014 (estorno/devolução, via
`OrderService.refundOrder`) e F015/F022 (kits, Fatia 6) — nenhum item do marco do marketplace
segue pendente neste módulo. Em 2026-07-30 fechou também F008 (lote e validade). Em 2026-08-10
fechou o bloco da §P1-Estoque do `BACKEND_TODO.md` do `mahal-admin`: upload de imagem, busca e
filtro server-side, atributos no produto pai, preço por variação (F020) e categoria como
entidade.

Em 2026-08-18 fecharam também **EST-C014** (teste do `ProductImageController`) e **EST-F005**
(importação de NF-e por XML) — ver Histórico acima.

O roteiro completo para o que resta — ordem de execução, dependências entre os itens e os dois
que não cabem em estoque — está em [`proximos-passos.md`](proximos-passos.md). Resumo da
prioridade imediata (nenhuma bloqueia outro módulo):

1. **EST-F016** (unidade de medida) é o único item de código que resta na fila imediata.
   **EST-F007** (custo médio) fechou em 2026-08-16 e destrava o DRE do `financeiro`; **EST-F005**
   fechou em 2026-08-18.
2. **EST-F012** (transferência entre depósitos) segue despriorizado por decisão — ver `proximos-passos.md`. **EST-F020** (preço por variação) saiu do backlog: foi implementado em 2026-08-10 a pedido do dono, com herança do pai como padrão, o que preserva o argumento do §8.5 em vez de contrariá-lo.

Fora do roteiro de código, EST-C011 deixou uma **pendência operacional**: rodar
`GET /estoque/integrity/orphan-skus` (ou o script) contra a base de produção e decidir o destino
de cada SKU levantado. É trabalho de conferência humana, não de implementação.
