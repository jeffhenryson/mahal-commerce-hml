# Domínio: ecommerce

**Status:** 🟢 Marketplace completo — Fatia 8 (ECM-F001/F002), Fatia 9 (ECM-F003/C002) e Fatia 10 (ECM-F004, gateway InfinitePay) entregues
**Pacote Java:** `com.cernecommerce...ecommerce`
**Rota HTTP base:** `/ecommerce` (admin, stub), `/shop` (cliente final — `/shop/register`+`/shop/catalog/**` públicos, `/shop/cart/**`+`/shop/checkout`+`/shop/orders/**` autenticados) e `/webhooks/payments/{provider}` (público, gateway)
**Última atualização deste doc:** 2026-10-08 (nota de EST-F034/F035, preço promocional vindo do estoque). Antes: 2026-08-18 (ECM-C003/C004, ECM-F005/F006/F007 — auditoria `/1-analise ambas`)

## Objetivo

Loja online: catálogo, carrinho, promoções e pagamentos.

## Escopo planejado

- **Carrinho online:** `Cart` + `CartItem` — entregue (ECM-F003).
- **Cupons:** `Coupon` (código, validade, regras de desconto). Fora de escopo por decisão, §8.3.
- **Motor de promoções:** regras configuráveis (ex.: **Leve 3 Pague 2**) via `Promotion`. Fora de escopo, mesma decisão.
- **Integrações de pagamentos:** checkout com gateways (`PaymentGatewayPort`) — entregue (ECM-F004, InfinitePay).

## Autenticação de cliente (ECM-F001, Fatia 8) — entregue 2026-08-03

Cliente do marketplace reaproveita `UserEntity` — não existe uma entidade `CustomerAccount`
separada, por decisão documentada em
[`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.9: refazer hash de senha,
verificação de email, TOTP, rotação de refresh token e OAuth para o cliente final seria o erro
mais caro disponível no projeto.

- `users.user_type ∈ {OPERATOR, CUSTOMER}` (V76) discrimina o dono da linha; `users.customer_id`
  liga 1:1 ao `Customer` do CRM.
- `POST /shop/register` (**público**, rate-limited): cria `Customer` + `User` `ROLE_CUSTOMER` em
  uma transação. Sem campo de username no request — username = email internamente
  (`ShopController`/`ShopService`/`UserService.createCustomerAccount`).
- **Login reaproveita `POST /auth/login`** — autenticação única para os dois canais
  (plano §2.11.1), não existe `/shop/login` separado.
- **Colisão de username entre cliente e operador — decisão registrada aqui:** o schema permite a
  coexistência via constraint composta `(user_type, username)` (rede de segurança), mas a
  aplicação **nunca deixa a colisão acontecer de fato** — `createCustomerAccount` checa o email
  contra `findByUsername` **e** `findByEmail`, cruzando os dois tipos, antes de criar a conta. Foi
  uma escolha deliberada: permitir a colisão de verdade obrigaria rate limit, lockout, refresh
  token, TOTP e blocklist — hoje todos chaveados só por `username` — a virarem cientes do tipo,
  mudança bem maior e mais arriscada em código de segurança hoje estável do que o "discriminador e
  algumas regras de `SecurityConfig`" que o plano original estimava.
- `SecurityArchitectureTest` (PLAT-C034, ver [`plataforma`](../plataforma/README.md)) já trata
  `ShopController` como rota pública — nenhum ajuste necessário ao criar `/shop/register`.
- Permissões `SHOP_CART_OWN`/`SHOP_ORDER_OWN` já semeadas (V76) para `ROLE_CUSTOMER` e, desde
  ECM-F003, exigidas de verdade por `ShopAccountController` (`SHOP_CASHBACK_OWN` segue sem
  endpoint — ver nota no fim da seção de checkout).

## Catálogo público (ECM-F002, Fatia 8) — entregue 2026-08-03

`GET /shop/catalog` (paginado) e `GET /shop/catalog/{sku}`, ambos públicos, no mesmo
`ShopController` do cadastro.

- **Só produto ativo e precificado aparece** — filtrado na consulta
  (`ProductRepository.findAllActiveAndPriced`), não em memória sobre a listagem do admin, para a
  paginação bater certo. Detalhe de um produto inativo ou sem preço responde **404
  `PRODUCT_NOT_FOUND`**, mesmo que o SKU exista — mesmo raciocínio de "não confirmar existência"
  já usado para pedido de outro cliente (plano §5.4): do ponto de vista do público, "fora de
  venda" e "não existe" são a mesma coisa.
- **`available` é booleano, não a quantidade exata** — a vitrine não expõe saldo real (sinal
  comercial de concorrente), e a checagem que vale de verdade acontece na reserva do checkout
  (ECM-F003), não aqui. Resolvido comparando `StockBalance.availableQuantity()` contra zero.
- **Depósito padrão do marketplace, novo conceito transversal.** `GET /estoque/stock-balance` e
  toda a família de reserva sempre exigiram um `warehouseCode` explícito — mas o catálogo é
  público, sem sessão de operador para informar um. Resolvido por
  `EstoqueUseCase.getDefaultWarehouse()`, configurado em
  `system_config.estoque.warehouse.default-code` (chave semeada em branco pela V77 — só para
  ficar descobrível via `GET /system/config`; nenhuma migration inventa um código real, criar
  depósito continua sendo `POST /estoque/warehouses`, ação do operador). Chave ausente ou em
  branco lança `DefaultWarehouseNotConfiguredException` → **503
  `DEFAULT_WAREHOUSE_NOT_CONFIGURED`**. Este mesmo conceito será reaproveitado pela reserva de
  estoque do checkout (ECM-F003) — não crie um segundo mecanismo de resolução lá.
- Detalhe do item aceita **qualquer SKU do catálogo** (pai ou variação — mesma resolução de
  `EstoqueUseCase#findProductBySku`) e devolve só as **variações ativas**, cada uma com sua
  própria disponibilidade (o preço é sempre herdado do pai).
- DTOs de resposta (`ShopCatalogItemResponseDTO`/`ShopCatalogItemDetailResponseDTO`) são
  deliberadamente separados dos DTOs do admin (`ProductResponseDTO`/`PricingResponseDTO`): custo,
  markup e margem nunca saem por uma rota pública.

## Carrinho e checkout (ECM-F003 + ECM-C002, Fatia 9) — entregue 2026-08-03

Controller **novo e separado**, `ShopAccountController` (não `ShopController`) — autenticado de
verdade, `@PreAuthorize("hasAuthority('SHOP_CART_OWN')")`/`SHOP_ORDER_OWN` em todo método, sem
exceção nenhuma em `SecurityArchitectureTest` (diferente de `ShopController`, que é público por
natureza). `GET/PUT/DELETE /shop/cart` + `/shop/cart/items/{sku}`, `POST /shop/checkout`,
`GET /shop/orders` + `/shop/orders/{id}`, `POST /shop/orders/{id}/cancel`.

- **`customerId` nunca vem do request** — toda operação resolve o `username` do
  `Authentication` e busca o `customerId` via `UserUseCase.findByUsername` dentro do service
  (`ShopService.requireCustomerId`), nunca de um parâmetro. Regra dura do plano §5.4.
- **Pedido de outro cliente responde 404, nunca 403** (`ShopService.requireOwnOrder`) — em
  `getMyOrder` e `cancelMyOrder`, um pedido que existe mas não pertence ao cliente autenticado
  lança o mesmo `OrderNotFoundException` de um pedido que não existe. 403 confirmaria a
  existência do recurso e transformaria a rota num oráculo de enumeração.
- **Carrinho não guarda preço** (`Cart`/`CartItem`, `cart`/`cart_item` da V78 — o plano original
  estimava V72, número mudou porque `order_refund_permission` da Fatia 5 já tinha consumido V72
  antes desta feature ser implementada, mesmo drift já visto em V76/V77). `PUT
  /shop/cart/items/{sku}` é upsert **idempotente** (define a quantidade, não incrementa) e já
  valida SKU + preço no momento de adicionar (`findPricingBySku`, mesma checagem que o checkout
  fará de novo — nunca confia no que valia quando o item foi adicionado).
- **Checkout consome o carrinho inteiro, não itens avulsos do request.** `ShopService.checkout`:
  resolve o depósito padrão do marketplace (mesmo `EstoqueUseCase.getDefaultWarehouse` do
  catálogo, ECM-F002 — nenhum segundo mecanismo foi inventado), resolve preço/custo/taxa de
  cashback de cada item **do catálogo agora** (nunca do que estava no carrinho, mesmo caminho de
  `PdvService.registerSale`), cria o pedido via `Order.openMarketplace` (já existia no domínio,
  sem caller até agora) em `AGUARDANDO_PAGAMENTO`, reserva o estoque de cada item
  (`EstoqueUseCase.reserveStock`, owner reference `Order.reservationOwnerReference(orderId)` — o
  mesmo formato que `PdvService.settleOnlineOrder`/`OrderService.cancelOrder` já esperavam desde
  a Fatia 5/EST-F021, só sem chamador até agora) e só então esvazia o carrinho — tudo em uma
  transação: se a reserva de qualquer item falhar (`InsufficientStockException`), nada é
  persistido.
- **Taxa de cashback é carimbada no checkout, não na conclusão** — `settleOnlineOrder` (PDV) só
  *lê* `item.cashbackPercent()` ao chamar `recordEarnedForOrder`, não resolve taxa nenhuma; se o
  checkout não carimbasse aqui, todo pedido de marketplace geraria cashback zero para sempre.
- **Cancelamento reaproveita `OrderUseCase.cancelOrder`** (Fatia 5) por inteiro — `ShopService`
  só acrescenta a checagem de propriedade antes de delegar; a liberação de reserva já existia e
  não precisou de nenhuma mudança.
- `GET /shop/cashback` (saldo/extrato do cliente) ficou **fora desta entrega, de propósito** — e
  continua fora mesmo após ECM-F004: cashback de marketplace só é gravado quando o webhook
  confirma pagamento (`PaymentWebhookService`), então o endpoint faria sentido a partir de agora,
  mas ainda não foi pedido. Fica para quando houver demanda de exibir isso ao cliente.
- Reaproveita `OrderResponseDTO`/`OrderDTOConverter` do PDV (sem custo/margem, já adequado para
  um cliente ver o próprio pedido) em vez de um DTO novo — só o carrinho ganhou DTOs próprios
  (`ShopCartResponseDTO`/`ShopCartItemResponseDTO`). Desde ECM-F004, `OrderResponseDTO` também
  carrega `checkoutUrl` (nulo em toda resposta exceto a de `POST /shop/checkout`).

## Gateway de pagamento InfinitePay + webhook (ECM-F004, Fatia 10) — entregue 2026-08-03

**Pivot registrado aqui: o plano original (`plano-pdv-marketplace.md` §2.6) supunha Mercado Pago
— o gateway efetivamente integrado foi o InfinitePay**, por decisão do dono do produto. A API real
do InfinitePay (levantada em `infinitepay.io/checkout-documentacao` durante o planejamento) muda
vários pontos de design em relação ao que o plano original desenhava — cada divergência está
documentada abaixo, não é omissão.

- **Groundwork já existia desde a Fatia 3/5, sem chamador até agora**: `PaymentStatus`
  (`PENDING/AUTHORIZED/CAPTURED/REFUNDED/FAILED`), `order_payment.gateway_ref` com índice único
  parcial (`uk_order_payment_gateway_ref`, V68 — comentário na própria migration já dizia "para a
  Fatia 10"), e `Order.paid(Instant)` ("disparado pelo webhook" no javadoc, sem nenhum caller até
  ECM-F004). O trabalho novo foi majoritariamente orquestração, não mecanismo.
- **Checkout hospedado, não QR code embutido.** `POST /links` do InfinitePay devolve uma URL de
  checkout própria do gateway — o cliente é redirecionado para lá, diferente do desenho original
  (QR code PIX embutido na resposta). `POST /shop/checkout` devolve `checkoutUrl` em
  `OrderResponseDTO`.
- **A criação do link NÃO devolve identificador de transação** — `transaction_nsu`/`invoice_slug`
  (os dois valores que `payment_check` exige) só chegam depois, pelo webhook. Por isso
  `OrderPayment.pending(...)` grava `gatewayRef = null` no checkout; só
  `OrderPayment.confirmCaptured(gatewayRef, capturedAt)` o preenche, quando o webhook chega. A
  correlação de volta ao pedido é sempre `orderNsu = Order.id().toString()` — nós que definimos,
  nunca o gateway.
- **`confirmCaptured` é a única exceção documentada ao ledger append-only** de `order_payment`:
  atualiza a MESMA linha (não cria uma nova), porque `uk_order_payment_gateway_ref` é único e a
  confirmação — diferente do estorno (`refunded`, que ganha uma referência própria) — não tem um
  segundo identificador para usar. `OrderPaymentRepositoryImpl.save` já faz `UPDATE` (não
  `INSERT`) quando o id não é nulo, então nenhuma mudança de persistência foi necessária.
- **Sem assinatura de webhook** — confirmado contra a documentação pública do InfinitePay em três
  buscas independentes, não uma omissão deste código. A defesa inteira do endpoint público
  `POST /webhooks/payments/{provider}` é: (1) só reconsultar o gateway (`payment_check`) quando já
  existe, no nosso banco, um pagamento `PENDING` para o `order_nsu` informado — uma notificação
  forjada para um pedido sem cobrança pendente nunca gera uma chamada externa, o que substitui a
  assinatura que o gateway não oferece; (2) nunca decidir a partir do corpo da notificação — o
  valor pago só vale o que `PaymentGatewayPort.checkPayment` confirma.
- **Contrato de resposta do webhook é do InfinitePay, não o "200 sempre" genérico do plano
  original**: sucesso ou no-op → `200`; qualquer falha ao processar → `400`, porque é
  especificamente esse código que o InfinitePay reconhece como "tente de novo" (confirmado na
  documentação deles). `PaymentWebhookController` captura `RuntimeException` amplamente por isso —
  cair no handler genérico devolveria 500/502, que o gateway não trata como retentativa.
  `PaymentGatewayException` continua mapeada para **502** no `GlobalExceptionHandler` — é o
  checkout, não o webhook, que usa esse mapeamento default.
- **Taxa de cashback já vinha carimbada no checkout** (ECM-F003) — o webhook só *lê*
  `item.cashbackPercent()` ao chamar `recordEarnedForOrder` depois de `order.paid()`, na mesma
  ordem que `PdvService.settleOnlineOrder` já usa.
- **`core/service` não publica `AuditEvent` diretamente** (`HexagonalArchitectureTest
  #core_service_may_only_use_spring_transaction` só permite `@Transactional` de Spring em
  `core/service`) — por isso `PaymentWebhookUseCase.handleNotification` devolve um `WebhookResult`
  (em vez de `void`), e é `PaymentWebhookController` quem publica `ORDER_STATUS_CHANGED` (mesmo
  `EventType` do fluxo do operador) só quando `orderPaid()` é verdadeiro.
- **`InfinitePayAdapter`** (`adapter/out/payment`, via Spring `RestClient` — mesmo padrão já
  estabelecido por `ResendEmailAdapter`, sem dependência nova) tem timeout explícito (conectar 3s
  / ler 5s): a chamada de criação do link acontece **dentro** da transação de checkout, e um
  gateway lento não pode segurar a transação presa. Selecionado por
  `payment.gateway.provider=infinitepay`; qualquer outro valor (padrão em `dev`) usa
  `StubPaymentGatewayAdapter` — nenhum ambiente precisa de credencial real para compilar, subir ou
  rodar a suíte, mesmo papel de `LoggingEmailAdapter` para e-mail.
- **Sem chave de API** nos dois endpoints usados (`/links`, `/payment_check`) — confirmado, não
  omissão. Só `infinitepay.handle` (o InfiniteTag da loja) é validado no boot de `prod`
  (`ProdStartupValidator`).
- Migration `V79` só amplia o `CHECK` de `order_payment.method` para aceitar `GATEWAY_PIX` — o
  resto do schema (status, `gateway_ref` nulável, índice único parcial) já vinha pronto da V68.
- **Conhecido, fora desta entrega:** pagamento parcial (`paid_amount < netAmount`) só é logado, não
  resolvido automaticamente; pedido que deixa de estar `AGUARDANDO_PAGAMENTO` entre o checkout e o
  webhook ainda marca o pagamento `CAPTURED` mas não mexe no pedido (reconciliação manual); não há
  job de reconciliação para webhook que nunca chega (o próprio InfinitePay recomenda um modelo
  híbrido webhook+polling); `POST /shop/orders/{id}/cancel` não avisa o InfinitePay nem toca a
  linha de pagamento pendente.

## Estrutura hexagonal (criada)

| Camada | Artefato |
|---|---|
| domain/model | `core/domain/model/ecommerce/{Cart,CartItem}` |
| ports/in | `core/ports/in/{ShopUseCase, PaymentWebhookUseCase, EcommerceUseCase}` — `ShopUseCase` é o carrinho/checkout/pedido de verdade; `PaymentWebhookUseCase` processa notificação do gateway (ECM-F004); `EcommerceUseCase` segue esqueleto (ver abaixo) |
| ports/out | `core/ports/out/ecommerce/{CartRepository, PaymentGatewayPort}` (os dois com adapter real, desde ECM-F003 e ECM-F004 respectivamente) |
| service | `core/service/ShopService` (cadastro, catálogo, carrinho, checkout, pedidos do cliente); `core/service/PaymentWebhookService` (notificação de pagamento); `core/service/EcommerceService` (stub, visão admin) |
| adapter/in | `ShopController` (público) + `ShopAccountController` (autenticado) + `PaymentWebhookController` (público, gateway); `EcommerceController` → `GET /ecommerce/carts?page&size` (stub, retorna `PageResult<Cart>` vazio — visão do **admin**, não tocada por ECM-F003/F004) |
| adapter/out | `adapter/out/payment/{InfinitePayAdapter, StubPaymentGatewayAdapter, PaymentGatewayAdapterConfig}` (ECM-F004) |

> `EcommerceUseCase`/`EcommerceService`/`GET /ecommerce/carts` continuam esqueleto de propósito:
> ECM-C002 descartou o record `Cart` antigo (sem itens, sem dono real) e o substituiu para o
> cliente final via `ShopUseCase`, mas não havia pedido para dar uma visão administrativa de
> carrinhos — se isso vier a ser necessário, é item novo de backlog, não parte desta entrega.

## Segurança e Infraestrutura

> Transversal em [`docs/security.md`](../../security.md) e
> [`docs/infrastructure.md`](../../infrastructure.md); modelo RBAC completo em
> [`plataforma`](../plataforma/README.md#segurança-e-infraestrutura).

**Visão do admin (`/ecommerce/carts`, esqueleto).** `ECOMMERCE_READ` (criada na V53 com `ON
CONFLICT DO NOTHING`, concedida a `ROLE_ADMIN`; semeada em `dev` por `SeedConfig`/
`DevRoleBootstrapConfig`) protege o único endpoint deste stub. O `@PreAuthorize` foi acrescentado
em **C004**, que tirou os controllers stub do fallback genérico `anyRequest().authenticated()`.
Não há tabela, auditoria, rate limit nem infra própria — o service devolve página vazia. Distinto
do ramo `/shop/**` (cliente final) e de `/webhooks/payments/**` (gateway), que já têm tudo isso
desde ECM-F001–F004.

**O que já existe desde ECM-F001–F004.** Ramo `/shop/**` público (`/shop/register`,
`/shop/catalog/**`) e autenticado (`/shop/cart/**`, `/shop/checkout`, `/shop/orders/**`, este
último via `SHOP_CART_OWN`/`SHOP_ORDER_OWN` de verdade, propriedade resolvida no service — não
mais um único `*_READ` genérico); `/webhooks/payments/{provider}` público, para o gateway.
Identidade do comprador resolvida (usuário↔cliente 1:1 desde ECM-F001). Reserva de estoque no
checkout (`EST-F021`, consumida por ECM-F003 — `EST-F013` como item de backlog era o cruzamento
com `ecommerce`, fechado nesta entrega). Segredo do gateway (`INFINITEPAY_HANDLE`) validado no
boot de prod (`ProdStartupValidator`, ECM-F004). `AuditEvent` para cancelamento
(`ORDER_CANCELLED`) e para pagamento confirmado por webhook (`ORDER_STATUS_CHANGED`).

**O que ainda falta:**

- [ ] **Rate limit** em `/shop/cart/**`, `/shop/checkout` e `/shop/catalog/**` — hoje o
      `LoginRateLimitingFilter` só cobre `/auth/**` e `/shop/register` (PLAT-C030, ainda aberto).
      `/webhooks/payments/**` fica **deliberadamente fora** — a defesa contra abuso ali é a
      checagem de pagamento `PENDING` dentro do service, não um limitador por IP, que penalizaria
      o próprio gateway em caso de retentativa legítima.
- [ ] Cupom e promoção — **fora de escopo por decisão** (§8.3), não pendência.
- [ ] Job de reconciliação para webhook que nunca chega (InfinitePay recomenda um modelo híbrido
      webhook+polling) — ver "Conhecido, fora desta entrega" na seção do gateway acima.
- [ ] Dado pessoal do comprador entra em escopo de LGPD — ver
      [`plataforma`](../plataforma/README.md#conformidade-lgpd).

## Testes no Postman

Coleção do módulo: [`ecommerce.postman_collection.json`](ecommerce.postman_collection.json) — importe no Postman, rode a pasta
`00 — Autenticação` (que faz login e guarda o `accessToken`) e siga as pastas na ordem, ou
rode tudo de uma vez no Collection Runner.

```bash
npx newman run docs/dominios/ecommerce/ecommerce.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

A coleção valida o que já existe (`GET /ecommerce/carts`): o contrato de `PageResult`, a
validação de `page`/`size` e a proteção por `ECOMMERCE_READ` — e serve de esqueleto para
crescer junto com o módulo.

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| ECM-F001 | 🟡 Média | Feature | autenticacao-de-cliente-final | Cliente do marketplace reaproveita `UserEntity` com `ROLE_CUSTOMER` e discriminador `user_type ∈ {OPERATOR, CUSTOMER}` (unicidade por `(user_type, username)`), ligado 1:1 ao `Customer` do CRM por `users.customer_id`. Reconstruir hash de senha, verificação de e-mail, reset, TOTP, rotação de refresh e OAuth para o cliente final seria o caminho mais curto para um bug de autenticação em superfície pública. Migration V76 (a doc original estimava V71 — número mudou porque kits/lotes/reembolso ocuparam V71–V75 antes desta feature ser implementada). Fatia 8 de [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.9. | ✅ Concluído (2026-08-03) |
| ECM-F002 | 🟡 Média | Feature | catalogo-publico | `GET /shop/catalog` e `/shop/catalog/{sku}` públicos, expondo só produto ativo e precificado, com preço e disponível (booleano). Disponibilidade resolvida contra o novo "depósito padrão do marketplace" (`EstoqueUseCase.getDefaultWarehouse`, `system_config.estoque.warehouse.default-code`, V77) — reaproveitado depois pelo checkout (ECM-F003). Detalhe aceita SKU pai ou de variação e só devolve variações ativas; produto inativo/sem preço responde 404 mesmo existindo. Ramo `/shop/**` com regras próprias em `SecurityConfig` (já existe, criado por ECM-F001). Rate limit em `/shop/register` já resolvido (PLAT-C030 segue aberto para o restante do catálogo de endpoints de negócio). Fatia 8. | ✅ Concluído (2026-08-03) |
| ECM-F003 | 🟡 Média | Feature | carrinho-e-checkout | `cart`/`cart_item` (V78 — a doc original estimava V72, número mudou porque `order_refund_permission` da Fatia 5 já tinha consumido V72) e checkout criando pedido + reserva de estoque. O carrinho **não guarda preço** — preço é resolvido do catálogo na exibição e congelado só no checkout. `customerId` **nunca** vem do request, sempre do principal autenticado (`ShopService.requireCustomerId`), e pedido de outro cliente responde **404, não 403** (`ShopService.requireOwnOrder`). Novo controller autenticado `ShopAccountController`. Reaproveita `Order.openMarketplace` (existia no domínio sem caller), `EstoqueUseCase.reserveStock`/`getDefaultWarehouse` (ECM-F002) e `OrderUseCase.cancelOrder` (Fatia 5) por inteiro. Fatia 9, §2.9. | ✅ Concluído (2026-08-03) |
| ECM-F004 | 🟡 Média | Feature | gateway-de-pagamento-e-webhook | `PaymentGatewayPort` em `core/ports/out/ecommerce` + `InfinitePayAdapter` (**InfinitePay, não Mercado Pago** — pivot do dono do produto em relação ao plano original). `POST /shop/checkout` cria a cobrança e devolve `checkoutUrl` hospedada pelo gateway; `POST /webhooks/payments/{provider}` (público) confirma o pagamento. **Sem assinatura HMAC** (o InfinitePay não oferece — confirmado na doc pública dele, não omissão): a defesa é só reconsultar o gateway quando já existe um pagamento `PENDING` no nosso banco para o `order_nsu`, e nunca decidir a partir do corpo da notificação. **Idempotente por `gateway_ref`** (`uk_order_payment_gateway_ref`, já existia desde V68) — `OrderPayment.confirmCaptured` atualiza a mesma linha (única exceção documentada ao ledger append-only, pela mesma razão do índice único). Migration V79 só amplia o `CHECK` de método para `GATEWAY_PIX`. Fatia 10, §2.6 (design divergiu do plano original — ver seção própria acima). | ✅ Concluído (2026-08-03) |
| ECM-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | README ainda no molde de esqueleto: faltam Modelo de Domínio, Regras de Negócio, API, Schema e Cobertura de Testes. Rode `/1-analise ecommerce`. Padrão: [`estoque`](../estoque/README.md). | Pendente |
| ECM-C002 | 🟢 Melhoria | Correção | descartar-o-record-cart-atual | `core/domain/model/ecommerce/Cart.java` era um record sem comportamento e **sem itens**, e `EcommerceService.listCarts` devolvia página vazia com um `// TODO`. Não havia nada a aproveitar — descartado e substituído por um `Cart`/`CartItem` reais junto com ECM-F003. `EcommerceService`/`EcommerceUseCase`/`GET /ecommerce/carts` (visão do admin) permanecem esqueleto — não faziam parte do pedido. | ✅ Concluído (2026-08-03) |
| ECM-C003 | 🟡 Importante | Correção | permissao-orfa-shop-cashback-own | `SHOP_CASHBACK_OWN` é criada e concedida a `ROLE_CUSTOMER` desde `V76__marketplace_customer_auth.sql:32,38` (ECM-F001) e semeada em `SeedConfig`, mas **nenhum endpoint a verifica** — `grep` em todo `adapter/in/controller` não encontra a permissão sendo usada. É exatamente a contrapartida de `ECM-F005` (saldo/extrato de cashback do cliente): implementar esse endpoint fecha o card; se a decisão for não implementar tão cedo, remover a permissão via migration, seguindo o precedente de `V84` em `estoque`. Achado em auditoria `analyze-domain`/RBAC de 2026-08-18. | Pendente |
| ECM-C004 | 🟢 Melhoria | Correção | infinitepayadapter-sem-teste | `InfinitePayAdapter` (`adapter/out/payment/InfinitePayAdapter.java`) — gateway de pagamento real, usado em produção para movimentar dinheiro — não tem teste dedicado, só `StubPaymentGatewayAdapterTest`. Merece o mesmo tratamento que `ResendEmailAdapterTest` deu ao Resend: mock de HTTP (`MockRestServiceServer`) cobrindo casos de falha de rede/timeout, não só o caminho feliz. Achado em auditoria `analyze-domain`/testes de 2026-08-18. | Pendente |
| ECM-F005 | 🟢 Baixa | Feature | saldo-e-extrato-de-cashback-do-cliente | `GET /shop/cashback` no marketplace — a permissão `SHOP_CASHBACK_OWN` já existe desde ECM-F001 e nunca ganhou endpoint (`ECM-C003`); o cliente do site hoje não vê o próprio saldo, reduzindo o incentivo do programa de cashback (`CRM-F003`). Implementar como novo método em `ShopAccountController`/`ShopService` delegando ao mesmo `CashbackUseCase` já usado por `CrmController`, reaproveitando `requireCustomerId`. Sugerido em análise de inovação de 2026-08-18. | Pendente |
| ECM-F006 | 🔴 Alta | Feature | assinatura-recorrente-de-consumiveis | Clube de assinatura mensal de essência/carvão com cobrança automática. Transforma receita pontual de e-commerce em recorrência previsível — o gateway InfinitePay (`ECM-F004`) já está integrado como base de cobrança. Proposta: `Subscription` (plano, periodicidade, itens) gerando `Order` automaticamente via job agendado, reaproveitando `PaymentGatewayPort`/checkout existentes; avaliar se o InfinitePay suporta token de cartão ou se o modelo precisa ser link recorrente por ciclo. Toca `financeiro` no reconhecimento de receita quando o DRE existir. Sugerido em análise de inovação de 2026-08-18. | Pendente |
| ECM-F007 | 🔴 Alta | Feature | motor-de-recomendacao-e-cross-sell | Sugestão de produtos complementares no e-commerce e no balcão ("quem levou X também levou Y"). Catálogo com variações e histórico de pedidos já existem prontos para alimentar análise de coocorrência sobre `order_item` histórico (batch offline, sem ML pesado no MVP), exposto em `GET /shop/catalog/{sku}/recommendations` e sugestão no fechamento do PDV. Aumenta ticket médio sem depender de desconto — cupom/promoção saíram de escopo por decisão (ver nota abaixo). Sugerido em análise de inovação de 2026-08-18. | Pendente |

> **EST-F034** (preço promocional com vigência) e **EST-F035** (promoção sugerida por validade de
> lote), em [`estoque`](../estoque/README.md#backlog-do-módulo), mudam o preço que o shop exibe e
> cobra, porque `ShopService` lê `Pricing.effectivePrice()`. Não é o motor de cupom e promoção que
> §Escopo deixou de fora (sem cupom, combo nem regra), mas encosta na decisão do
> [plano](../../plano-pdv-marketplace.md) §8.3 e precisa dela revista pelo dono antes da sprint.

> `EST-F013` (`reserva-estoque-checkout`) era o cruzamento `ecommerce↔estoque` rastreado em
> [`estoque`](../estoque/README.md#backlog-do-módulo) — fechado por ECM-F003, que consome
> `EstoqueUseCase.reserveStock` (já implementado desde EST-F021) sem precisar de mecanismo novo.
> A decisão de reservar no **checkout** e não no carrinho está em
> [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.2.

> **Cupom e promoção saíram do escopo** (§8.3): o cashback já é o mecanismo de fidelidade
> escolhido, e dois motores de desconto compondo entre si é exatamente onde nasce o pedido que sai
> de graça. **Frete e integração logística também** (§8.4): retirada na loja + entrega própria com
> taxa fixa por bairro cobre a operação regional de uma tabacaria.

## Próximos passos

Roteiro completo, com prompt pronto para colar numa sessão nova, em
[`proximos-passos.md`](proximos-passos.md).

O marketplace **não** é por onde começar — o balcão fatura hoje e o site não existe. A Fatia 0 do
[`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) protege o futuro do marketplace
(pedido unificado) sem construí-lo. Ordem daqui:

- [x] **ECM-F001** — autenticação de cliente concluída em 2026-08-03.
- [x] **ECM-F002** — catálogo público concluído em 2026-08-03. Fatia 8 fechada.
- [x] **ECM-F003 + ECM-C002** — carrinho e checkout com reserva concluído em 2026-08-03. Fatia 9 fechada.
- [x] **ECM-F004** — gateway InfinitePay + webhook concluído em 2026-08-03. Fatia 10 fechada — **marketplace completo**.
- [x] `PaymentGatewayPort` + `InfinitePayAdapter`.
- [x] Permissões RBAC do domínio (`SHOP_CART_OWN`, `SHOP_ORDER_OWN`) + `@PreAuthorize` nos
      endpoints — `SHOP_CASHBACK_OWN` segue semeada sem endpoint (ver nota na seção de checkout).

Não há mais fatia numerada pendente no marketplace. Candidatos abertos fora dele, do mesmo re-audit
de 2026-08-03: **CRM-C002** (export audit, maior risco de segurança aberto do CRM), o slice de
resgate/ajuste manual de cashback, e **EST-F007** (custo médio ponderado). Itens menores deste
próprio módulo: **ECM-C001** (documentar no padrão do estoque — parcialmente resolvido por este
próprio README, mas ainda falta Regras de Negócio/Schema completos), rate limit de `/shop/cart`+
`/shop/checkout`+`/shop/catalog` (PLAT-C030), e o job de reconciliação de webhook.
