# Domínio: crm

**Status:** 🟢 Operacional — roadmap inicial `F001–F009` entregue por completo, cliente
identificável no balcão sem e-mail (CRM-C005/F002) e programa de cashback — ganho, taxas e
consultas (CRM-F003), todos em 2026-07-29
**Pacote Java:** `com.cernecommerce.core.domain.model.crm` (cashback em
`com.cernecommerce.core.domain.model.cashback`, controller próprio `CashbackController` em `/cashback`)
**Rota HTTP base:** `/crm` (mais `/cashback`, ver CRM-F003)
**Última atualização deste doc:** 2026-10-08 (duas falhas de teste pré-existentes corrigidas — ver Histórico). Antes: 2026-10-01 ("Marcar", CRM-F010)

> ⚠️ **Este README ainda não passou por auditoria de código.** Ele foi criado em 2026-07-26 na
> descentralização do `docs/backlog.md` para receber os itens `F001–F009`, que estavam órfãos.
> As seções de Modelo de Domínio, Regras de Negócio, API, Schema e Cobertura de Testes ainda
> precisam ser preenchidas a partir do código — rode `/1-analise crm` ou `/analyze-domain crm`.
> O padrão de README completo está em [`estoque`](../estoque/README.md).

## Objetivo

Gestão do relacionamento com clientes da tabacaria: base de clientes com segmentação RFM,
perfil 360º (pedidos, cashback, notas), funil de atendimento em kanban, campanhas de automação
por WhatsApp/e-mail e tags.

É o domínio com mais features entregues do backend, e o que mais destrava telas do frontend
`mahal-admin-ui`.

## Escopo planejado

- **Cadastro e base de clientes:** `Customer` com contato, CPF, origem e LTV. ✅ Implementado (F001, F002).
- **Segmentação RFM:** VIP / Recorrente / Novo / Inativo / Em Risco. ✅ Implementado (F002).
- **Perfil 360º:** histórico de pedidos (placeholder, CRM-F001), extrato de cashback (real, CRM-F003), notas/interações. ✅ Implementado (F003).
- **Programa de cashback:** taxa por abrangência (SKU/categoria/global), ledger append-only,
  ganho na conclusão da venda, expiração automática, saldo/extrato e diagnóstico de margem.
  ✅ Implementado (CRM-F003, 2026-07-29). Resgate no balcão e ajuste manual ficam para uma fatia
  seguinte.
- **Funil de atendimento:** kanban com trilha de auditoria das transições de estágio. ✅ Implementado (F004).
- **Dashboard:** agregações de clientes ativos, LTV médio, disparos e contagem por segmento. ✅ Implementado (F005).
- **Automações e campanhas:** regras por gatilho/segmento/canal com template e log de envios. ✅ Implementado (F006).
- **Tags e exportação.** ✅ Implementado (F007, F009).
- **Status de canal de envio.** ✅ Implementado (F008).
- **"Marcar" — venda a prazo para cliente VIP:** recebível por pedido, limite de crédito, quitação
  no caixa, vencidos. ✅ Implementado (CRM-F010, 2026-09-30). Falta mostrar os marcados como "a
  receber" no Financeiro.

## Segurança e Infraestrutura

> Mecanismos transversais em [`docs/security.md`](../../security.md); ambientes e containers em
> [`docs/infrastructure.md`](../../infrastructure.md); o modelo RBAC completo em
> [`plataforma`](../plataforma/README.md#segurança-e-infraestrutura). Aqui fica só o recorte
> deste domínio.

Este é o domínio com **mais dado pessoal** do backend — nome, telefone, e-mail e CPF de clientes
reais. As decisões abaixo pesam mais aqui do que em qualquer outro módulo.

### Permissões RBAC

Quatro permissões cobrem os **28 endpoints** do `CrmController`: `CRM_CUSTOMER_READ` e
`CRM_CUSTOMER_MANAGE` (V48, com `ON CONFLICT DO NOTHING`), `CRM_CUSTOMER_LOOKUP` (V69,
CRM-F002) e `CRM_CUSTOMER_EXPORT` (V81, CRM-C002) — todas concedidas a `ROLE_ADMIN`, semeadas em
`dev` por `SeedConfig` e `DevRoleBootstrapConfig`.

| Permissão | Libera | Endpoints |
|---|---|---|
| `CRM_CUSTOMER_READ` | toda leitura: clientes, notas, pedidos, cashback, histórico de estágio, dashboard, tags, automações, log de disparos e status de canal | 12 |
| `CRM_CUSTOMER_MANAGE` | toda escrita: criar cliente e nota, mover estágio, CRUD de tags e associações, CRUD de automações e disparo manual | 11 |
| `CRM_CUSTOMER_LOOKUP` | `GET /crm/customers/lookup` — busca pontual por CPF/email/contato (CRM-F002); `GET /crm/customers/lookup/contact` — todos que já usam o telefone/email/CPF, com `matchedBy` (CRM-C007) | 2 |
| `CRM_CUSTOMER_EXPORT` | `GET /crm/customers/export` — export CSV da base inteira (CRM-C002) | 1 |

O programa de cashback (CRM-F003) tem controller e permissões próprios, fora do `CrmController` e
das permissões acima: `CashbackController` em `/cashback`, sob `CASHBACK_RATE_MANAGE`
(mutação de taxa) e `CASHBACK_READ` (saldo, extrato, diagnóstico de margem). `GET
/crm/customers/{id}/cashback` continua sob `CRM_CUSTOMER_READ` — delega ao mesmo caso de uso, não
duplica a checagem.

O "Marcar" (CRM-F010) também tem controller e permissões próprios: `ReceivableController`, com
`RECEIVABLE_READ` (leituras, ADMIN e ATENDENTE) e `RECEIVABLE_MANAGE` (vencimento, cancelamento e
`PUT /crm/customers/{id}/credit-limit`, só ADMIN), ambas da V137. Marcar na venda exige
`PDV_SALE_ON_ACCOUNT` (só ADMIN) e receber exige `PDV_SALE_MANAGE`. Nenhuma das três está em
`SeedConfig`: em `dev`, com o Flyway desligado, os endpoints respondem 403 ao admin local.

**Granularidade:** `CRM_CUSTOMER_LOOKUP` resolveu o caso do balcão — quem atende ali agora pode
achar **um** cliente por CPF/email/contato sem precisar de `CRM_CUSTOMER_READ`.
`CRM_CUSTOMER_EXPORT` resolveu o caso do export (CRM-C002) — ler um cliente não implica mais
poder baixar a base inteira. O resto do módulo (ex.: "criar nota" vs. "disparar campanha")
continua com a granularidade grossa de sempre — fora do escopo de CRM-C002.

> Os nomes `CUSTOMER_CREATE`/`CUSTOMER_READ` que aparecem na descrição legada de F001 no
> [Histórico](#histórico-de-implementações) nunca existiram no código — as permissões reais
> sempre foram `CRM_CUSTOMER_*`.

### Rate limiting

✅ `GET /crm/customers/export` é limitado desde 2026-08-04: bucket `crm-export`, 5
requisições/hora por usuário autenticado, aplicado por um filtro de servlet dedicado
(`infra/security/ResourceRateLimitingFilter.java`, roda depois do `JwtAuthenticationFilter` na
cadeia — PLAT-C030), não por chamada dentro do controller. Excedente responde `429
RATE_LIMIT_EXCEEDED` com header `Retry-After`. Nenhum outro endpoint deste módulo é limitado — o
`LoginRateLimitingFilter` (`infra/security/LoginRateLimitingFilter.java:42-77`) cobre só
`/auth/**` e duas rotas de notificação. PLAT-C030 segue parcial no restante do backend (demais
endpoints de negócio fora de `crm-export`/`estoque-movements`/`shop-catalog`) — ver
[`plataforma`](../plataforma/README.md).

### Isolamento de dados e dado pessoal

Single-tenant, sem carteira por vendedor: quem tem `CRM_CUSTOMER_READ` vê **todos** os clientes.

`customers` (V48) guarda `nome`, `contato`, `email` e `cpf` — **tudo em texto claro**, sem
criptografia em repouso nem mascaramento na API ou no CSV. Não há registro de consentimento,
anonimização, exportação por titular nem exclusão de cliente (o domínio não tem endpoint de
delete). Sobre LGPD no backend como um todo, ver
[`plataforma`](../plataforma/README.md#conformidade-lgpd).

### Auditoria

O `CrmController` publica `AuditEvent` em 12 operações:

| Operação | `EventType` |
|---|---|
| `POST /crm/customers` | `CUSTOMER_CREATED` |
| `POST /crm/customers/{id}/notes` | `CUSTOMER_NOTE_ADDED` |
| `PATCH /crm/customers/{id}/estagio` | `CUSTOMER_STAGE_CHANGED` |
| `POST` / `DELETE /crm/tags` | `TAG_CREATED` / `TAG_DELETED` |
| `POST` / `DELETE /crm/customers/{id}/tags` | `CUSTOMER_TAG_ADDED` / `CUSTOMER_TAG_REMOVED` |
| `POST` / `DELETE /crm/automacoes` | `CAMPAIGN_AUTOMATION_CREATED` / `CAMPAIGN_AUTOMATION_DELETED` |
| `PATCH /crm/automacoes/{id}/ativa` | `CAMPAIGN_AUTOMATION_TOGGLED` |
| `POST /crm/automacoes/{id}/disparar` | `CAMPAIGN_AUTOMATION_DISPATCHED` |
| `GET /crm/customers/export` | `CUSTOMER_LIST_EXPORTED` |

O "Marcar" (CRM-F010) publica `RECEIVABLE_CREATED` (venda ou fechamento de mesa com linha
`MARCADO`), `RECEIVABLE_PAID` (lote, abatimentos, troco e saldo restante),
`RECEIVABLE_DUE_DATE_CHANGED` (de/para e motivo), `RECEIVABLE_CANCELLED` (saldo e motivo) e
`CUSTOMER_CREDIT_LIMIT_CHANGED` (antes/depois e se é individual).

É a melhor cobertura de auditoria entre os domínios de negócio. Nenhuma outra leitura gera
evento — só o export, por expor a base inteira de PII em um único download (CRM-C002).

A trilha de movimentação no kanban tem registro próprio de domínio em
`customer_stage_transitions` (V50), independente dos `audit_logs`.

### Infraestrutura utilizada

| Recurso | Uso neste módulo | Se cair |
|---|---|---|
| Postgres 16 (H2 em `dev`) | `customers` (V48), `customer_notes` (V49), `customer_stage_transitions` (V50), `tags` + `customer_tags` (V51), `campaign_automations` + `campaign_log` (V52) | módulo indisponível |
| Cache de authorities (Redis/Caffeine, TTL 60s) | checagem de `@PreAuthorize` | latência maior |
| `EmailPort` (Resend em `hml`/`prod`, `logging` em `dev`) | **apenas** o status do canal em `GET /crm/canais/status` | badge de canal reporta desconectado |

Sem fila, sem storage de arquivo e **sem integração de WhatsApp** — `getChannelStatus` devolve
`conectado = false` com o texto fixo "Integração de WhatsApp ainda não implementada".

⚠️ **`POST /crm/automacoes/{id}/disparar` não envia nada.** `CrmService.dispatchAutomation`
busca os clientes do segmento-alvo e grava uma linha em `campaign_log` por cliente — nenhuma
mensagem sai por e-mail ou WhatsApp. O `EmailPort` injetado no service só é usado para o status
do canal.

### Limites operacionais

- `GET /crm/customers`: paginado, com filtro por `search` e segmento.
- `GET /crm/customers/export`: **não paginado** — a base inteira em memória, com BOM UTF-8 e
  `Content-Disposition: attachment; filename="clientes.csv"`.
- `POST /crm/automacoes/{id}/disparar`: sem teto de destinatários; uma linha de log por cliente
  do segmento, tudo em uma transação.
- Validação: `@Valid` nos requests (e-mail, tamanhos, enums), com erros no formato `ApiError`.

### Riscos conhecidos

- **CRM-C001** — README ainda sem Modelo de Domínio, Regras, API, Schema e Testes.

## "Marcar" — venda a prazo para cliente VIP (CRM-F010)

O cliente VIP leva o produto (ou consome a sessão na mesa) e paga até uma data combinada. Pacotes:
`core/domain/model/recebivel`, `ReceivableService` (port `ReceivableUseCase`), `ReceivableController`,
`OnAccountGuard` (borda do PDV e da mesa) e `ReceivableOverdueJob`.

**Fluxo.** A venda **conclui** normalmente — a mercadoria saiu. A parte marcada vira uma linha
`order_payment` `MARCADO`/`ON_ACCOUNT` (fora de toda soma de caixa) e um `customer_receivable` com o
snapshot dos itens do pedido inteiro. A quitação entra no caixa de **quem recebe**, por
`receivable_payment`, e aparece em `payment-totals` como `receivableReceived`. O cashback da parte
marcada só é creditado na quitação, proporcional ao que foi pago.

**Estados:** `ABERTO → PARCIAL → QUITADO`; `VENCIDO` (gravado pelo job às 00:05 de São Paulo; a
checagem de "tem vencido" calcula na leitura e não depende dele); `CANCELADO` (manual ou pelo
reembolso do pedido).

| Regra | Onde | Teste |
|---|---|---|
| No máximo uma linha `MARCADO`, com vencimento hoje ou depois | `ReceivableService.validateOnAccount` | `ReceivableServiceTest.validate_refusesTwoOnAccountLines`, `validate_refusesMissingOrPastDueDate` |
| Exige cliente, tag `VIP` (enquanto não houver campo próprio), nenhum vencido e limite | `ReceivableService.validateOnAccount` | `validate_refusesAnonymousSale`, `validate_refusesNonVip`, `validate_refusesCustomerWithOverdue`, `validate_refusesAboveLimit_withTheNumbersForTheScreen` |
| Limite efetivo: o individual ou o padrão da loja (`pdv.on-account.default-credit-limit`, padrão 0 = sem limite individual não marca); `null` no `PUT` volta ao padrão | `ReceivableService` | `validate_defaultLimitZero_requiresAnIndividualLimit`, `setCreditLimit_nullReturnsToTheDefault` |
| Quitação: FIFO por vencimento sem ids; com ids, só neles e na ordem; parcial fica `PARCIAL` | `ReceivableService.pay` | `pay_withoutIds_appliesFifoAndLeavesThePartialOnTheLastOne`, `pay_withIds_appliesOnlyThoseInTheGivenOrder` |
| Só dinheiro passa do saldo (troco do lote); outros métodos acima do saldo → 409 | `ReceivableService.pay` | `pay_cashAboveTheBalance_givesChange`, `pay_nonCashAboveTheBalance_isRefused` |
| Sessão de quem recebe: aberta, do operador e de hoje | `ReceivableService.pay` | `pay_inASessionFromAPreviousDay_isRefused` |
| Renegociar vencido o reabre | `ReceivableService.changeDueDate` | `changeDueDate_reopensAnOverdueReceivable` |
| Reembolso do pedido cancela o marcado em aberto | `ReceivableService.cancelOpenForOrder` | `cancelOpenForOrder_cancelsOnlyWhenOpen`; `ReceivableCycleIT.refundingTheOrder_cancelsTheOpenReceivable` |
| Vencido pela data da loja (São Paulo) | `ReceivableService.markOverdue` | `markOverdue_usesTheStoreDate` |
| `MARCADO` não vale na liquidação do app, na correção de pagamento nem na quitação → 400 `INVALID_PAYMENT_METHOD` | `OnAccountNotSupportedException` | — |

**Schema (V137):** `customer_credit_limit`, `customer_receivable` (único por pedido),
`receivable_item`, `receivable_payment_batch`, `receivable_payment`; em `order_payment`, `due_date`,
`MARCADO` e `ON_ACCOUNT`; `system_config` `pdv.on-account.default-due-days=30` e
`pdv.on-account.default-credit-limit=0` (sem tela: `/system/config` só aceita `auth.*`). Colunas
em [`persistence.md`](../../persistence.md).

**API:** `/receivables` (lista, resumo por cliente, detalhe, quitação, vencimento, cancelamento),
`GET /crm/customers/{id}/receivables`, `GET /crm/customers/{id}/on-account-eligibility`,
`PUT /crm/customers/{id}/credit-limit`, e `creditLimit`/`openBalance`/`overdueBalance` no
`GET /crm/customers/{id}`. Contrato em [`api-reference.md`](../../api-reference.md).

**Testes:** `ReceivableServiceTest`, `ReceivableControllerSecurityTest`, `ReceivableCycleIT`,
`PagamentoPostgresIT` (V137 contra Postgres) e os casos de `CashbackServiceTest` do crédito
proporcional.

**Pendências:** os marcados não aparecem como "a receber" no Financeiro (o cash-flow é ledger manual
e não lê `order_payment`); as permissões novas faltam no `SeedConfig` de `dev`.

## Testes no Postman

Coleção do módulo: [`crm.postman_collection.json`](crm.postman_collection.json) — importe no Postman, rode a pasta
`00 — Autenticação` (que faz login e guarda o `accessToken`) e siga as pastas na ordem, ou
rode tudo de uma vez no Collection Runner.

```bash
npx newman run docs/dominios/crm/crm.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

**O que a coleção cobre**

| Pasta | Requisições |
|---|---|
| `01 — Clientes` | criação, busca por id, listagem com `search`, export CSV (confere BOM UTF-8 e `Content-Disposition`), 409 de e-mail duplicado, 400 de e-mail inválido e 404 |
| `02 — Notas` | criação (autor vem do token) e listagem |
| `03 — Kanban de estágios` | movimentação de estágio, trilha de transições e os 400 de estágio repetido e de valor fora do enum |
| `04 — Tags` | criação, associação ao cliente, listagem com `clientesCount`, remoção da associação, 409 de nome duplicado, 404 de tag inexistente e exclusão |
| `05 — Automações` | criação, listagem, disparo manual, log de disparos, ativar/desativar, 404 e exclusão |
| `06 — Dashboard e canais` | overview agregado e status dos canais de envio |
| `07 — Pedidos (placeholder) e extrato de cashback` | `orders` (placeholder, CRM-F001) e `cashback` (real, CRM-F003 — extrato do ledger) |
| `08 — Segurança` | 401 sem token |

Cliente, tag e automação são criados com sufixo de timestamp; tag e automação são apagadas ao
final. O cliente permanece — o domínio não tem endpoint de exclusão.

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| CRM-F001 | 🟡 Importante | Feature | perfil-360-deixa-de-ser-placeholder (orders) | ~~`GET /crm/customers/{id}/cashback` e `CustomerResponseDTO.cashback`~~ ✅ fechados por CRM-F003 (2026-07-29) — delegam ao ledger real. Falta só `GET /crm/customers/{id}/orders`, que ainda devolve `List.of()` (`CrmController.java:236`): passar a consultar `sales_order` por `customer_id`. Depende de PDV-F003 (✅ existe). | Pendente |
| ~~CRM-F003~~ | 🔴 Alta | Feature | ~~programa-de-cashback~~ ✅ Fechado 2026-07-29 (ganhar + consultas) | Taxas por escopo `GLOBAL`/`CATEGORY`/`SKU` (a mais específica ativa e vigente vence) e ledger `cashback_entry` **append-only**, sem coluna `status` (divergência deliberada do rascunho do plano — disponibilidade é sempre derivada de `available_at`; ver Histórico). Ganho lançado na conclusão da venda de balcão e na liquidação de pedido de marketplace, com carência (7d) e expiração (180d, via `CashbackExpiryCleanupService`) configuráveis em `system_config`. `GET /cashback/margin-impact` já entrega o diagnóstico de margem. Migration V70. **Resgate no balcão (`CASHBACK_REDEEM`) e ajuste manual (`CASHBACK_ADJUST`) ficam para uma fatia seguinte, isolada** — os parâmetros (teto 50%, mínimo de saldo R$50 para resgatar) já estão semeados em `system_config`, sem código que os leia ainda. Fatia 4, §2.4. | ✅ Fechado |
| CRM-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | Este README não tem Modelo de Domínio, Regras de Negócio, API, Schema nem Cobertura de Testes — as 9 features foram entregues sem que a documentação de domínio fosse criada. Auditar o código e preencher no padrão de `estoque`. | Pendente |
| ~~CRM-C002~~ | 🔴 Alta | Correção | ~~export-da-base-sem-auditoria-nem-limite~~ ✅ Fechado 2026-08-04 | `GET /crm/customers/export` tinha 3 gaps: sem rate limit, sem auditoria e sem permissão dedicada (usava a mesma `CRM_CUSTOMER_READ` de qualquer leitura). Rate limit (bucket `crm-export`, 5 req/hora por usuário, `ResourceRateLimitingFilter`) e o evento `CUSTOMER_LIST_EXPORTED` já tinham sido adicionados mais cedo no mesmo dia. Última peça: nova permissão `CRM_CUSTOMER_EXPORT` (V81), separada de `CRM_CUSTOMER_READ` — quem só lê clientes não consegue mais exportar a base inteira. Paginação do CSV foi avaliada e descartada (é download de arquivo único); streaming/limite de memória ficou fora de escopo por decisão do dono do produto. | ✅ Fechado |
| CRM-C003 | 🟡 Importante | Correção | disparo-de-campanha-nao-envia-nada | **Revisado em 2026-08-18: parcialmente resolvido, ainda não commitado.** A descrição original (`CrmService.dispatchAutomation` só grava log, não envia nada) está desatualizada — o working tree já tem `CampaignWebhookAdapter` (`adapter/out/webhook`, POST real via `RestClient` para webhook n8n/Make) e `CampaignTemplateRenderer` (variáveis `{{cliente.nome}}`/legado `{nome}`), com `CampaignDispatchStatus` ganhando `ENVIADO`/`FALHA` além do antigo `PENDENTE_INTEGRACAO`, migration `V102__crm_campaign_webhook.sql` e campos novos `webhookUrl`/`webhookHeaders` em `CampaignAutomationEntity`. **Nada disso está commitado** (confirmado via `git status` em 2026-08-18) nem registrado em `docs/feature-registry.md`. O contrato exato esperado pelo front (`mahal-admin`, `Docs/BACKEND_TODO.md`, seção "P0 — Automações do CRM", 2026-08-18) bate em grande parte com o que já foi implementado: campos `webhookUrl`/`webhookHeaders` em `CampaignAutomationRequest`/`ResponseDTO`, `POST /crm/automacoes/{id}/testar` para teste determinístico, payload de disparo com `cliente.whatsapp` normalizado (DDI 55, só dígitos) e `mensagem` já renderizada, `CampaignLogResponseDTO` com `status: ENVIADO|FALHOU`/`httpStatus`/`erro`. **Falta confirmar:** se o `WHATSAPP`/`AMBOS` do enum `canal` já é tratado como "dado no payload para a plataforma decidir" (como o front espera) e não como "como o backend envia"; se há retry/backoff em 5xx e timeout (~15s) no `CampaignWebhookAdapter`; e se o renderizador aceita o formato legado de chave simples (`{nome}`, `{saldo}`) além de `{{cliente.nome}}`. Próximo passo: revisar o diff não commitado contra o contrato do front, commitar, e atualizar `feature-registry.md`. | Pendente |
| ~~CRM-C004~~ | 🟢 Melhoria | Correção | ~~audit-event-ausente-em-ativar-desativar-automacao~~ ✅ Já resolvido | Descrição estava desatualizada: `PATCH /crm/automacoes/{id}/ativa` já publica `CAMPAIGN_AUTOMATION_TOGGLED` (`CrmController.java:459-461`). Corrigido só na documentação nesta sessão — nenhuma mudança de código foi necessária. | ✅ Fechado |
| CRM-F004 | 🟢 Baixa | Feature | resgate-de-cashback-no-balcao | Permitir abater cashback acumulado no pagamento de uma venda de balcão. O teto (50%) e o mínimo (R$ 50) já estão semeados em `system_config` **sem nenhum código lendo** (ver nota de `CRM-F003` acima: "Resgate no balcão e ajuste manual ficam para uma fatia seguinte, isolada"). Proposta: nova permissão `CASHBACK_REDEEM`, `CashbackEntryType.REDEEMED` no ledger append-only existente, consumido dentro de `PdvService.registerSale` como abatimento do saldo antes de chamar `EstoqueUseCase`. É a peça que falta para o cashback virar motivo de retorno à loja, não só extrato. Sugerido em análise de inovação de 2026-08-18. | Pendente |
| CRM-F006 | 🔴 Alta | Feature | carteirinha-vip-digital-com-qrcode | Token opaco por cliente (QR), tiers derivados da segmentação RFM já existente, check-in por QR que abre a comanda do lounge (`PDV-F009`, se implementada) já vinculada ao cliente, com notificação de boas-vindas — depende do disparo real de webhook (`CRM-C003`) já estar commitado. Diferencial "clube fechado" além do desconto puro em dinheiro. Sugerido em análise de inovação de 2026-08-18. | Pendente |

Novas features e correções do CRM seguem as séries `CRM-F001+` e `CRM-C002+`. A série legada
`F001–F009` está congelada (todos concluídos, ver histórico).

## Histórico de Implementações

- **2026-10-08** — reparos de build, achados ao fechar a suíte das correções do PDV (sem migration, sem
  mudança de comportamento):
  - `ReceivableService` tinha um `private record Position` aninhado, e o `HexagonalArchitectureTest`
    (`services_must_only_implement_use_case_ports`) barra classe em `core.service` que não implementa
    port. O record virou `core/domain/model/recebivel/CreditPosition`, ao lado de `CreditLimits`, com os
    mesmos componentes e métodos.
  - `AutomationDispatchServiceTest.ocorrenciaJaReservada_naoDisparaDeNovo` reestubava `claim(any())` com
    `when(...)`, o que chamava `claim(null)` e rodava a resposta do `setUp` (`Optional.of(null)` → NPE).
    Trocado por `doReturn(...).when(...)`; o serviço não mudou.

- **2026-09-30** — `marcar-venda-a-prazo-vip` (CRM-F010 / PDV-F028, **V137**): venda a prazo para
  cliente VIP no balcão e na mesa, recebível com snapshot dos itens, limite de crédito em tabela
  própria, quitação no caixa de quem recebe (`receivableReceived`), cashback proporcional na
  quitação, job diário de vencidos e saldo do marcado na ficha. Commit `05d5ec8`; correção da troca
  de SKU para `receivable_item` em `4445b69`.

- **2026-10-01** — `busca-de-cliente-500-no-postgres` (CRM-C008): a busca sem dígitos de
  `GET /crm/customers` (e do export) caía em 500 no Postgres (`cpfDigits` nulo virava `bytea`). Duas
  consultas, com e sem CPF; `CustomerSearchPostgresIT`.

- **2026-09-26** — `cadastro-sem-duplicado-por-telefone-ou-email` (CRM-C007): `GET
  /crm/customers/lookup/contact?phone=&email=&cpf=` devolve todos os clientes que batem, com
  `matchedBy`; `POST`/`PUT /crm/customers` respondem `409 CUSTOMER_ALREADY_EXISTS {matchedBy,
  customerId}` também para telefone e e-mail sem diferenciar caixa. **V129**: `phone_normalized`
  (dígitos sem DDI 55) e índice em `lower(email)`, ambos **não únicos** — a regra fica no serviço
  para a migração não falhar em base com repetidos.

- **2026-09-24** — `atendente-cadastra-lead-e-cpf-editavel` (CRM-C006): permissão `CRM_LEAD_CREATE`
  (V127, admin + atendente) libera `POST /crm/customers` e o novo find-or-create
  `POST /crm/customers/lead`; `PUT /crm/customers/{id}` para completar CPF/dados depois;
  `CustomerIdentifiers` normaliza CPF (máscara → 11 dígitos) e troca `""` por nulo; listagem
  ordenada por cadastro desc e busca por email/CPF. Diagnóstico na base HML: dos 501 clientes,
  todos com nome, 417 com telefone e 382 com CPF (quase todos seed); o único cliente real criado
  pelo PDV estava sem CPF porque o PDV não o enviava.

- **2026-08-04** — `export-da-base-sem-auditoria-nem-limite` (**CRM-C002**, fechado): última peça
  do maior risco de segurança aberto do módulo. Rate limit e evento de auditoria já existiam desde
  mais cedo no mesmo dia; esta rodada adicionou a permissão dedicada `CRM_CUSTOMER_EXPORT`
  (migration V81, seguindo o padrão de V48), trocou o `@PreAuthorize` do endpoint de
  `CRM_CUSTOMER_READ` para ela e semeou a nova permissão em `SeedConfig`/`DevRoleBootstrapConfig`
  ao lado de `CRM_CUSTOMER_READ`/`MANAGE`/`LOOKUP` (só `ROLE_ADMIN`/`ROLE_DEV` a possuem hoje).
  Paginação do CSV foi avaliada e descartada — é um download de arquivo único, não uma listagem.
  Também corrigido nesta sessão, só na documentação (**CRM-C004**): `PATCH
  /crm/automacoes/{id}/ativa` já publicava `CAMPAIGN_AUTOMATION_TOGGLED`, o README é que estava
  desatualizado.
- **2026-07-29** — `programa-de-cashback` (**CRM-F003**, ganhar + consultas): decisões de negócio
  fechadas com o dono nesta sessão — taxa **GLOBAL semeada em 3%** (não os 8% do rascunho do
  plano, que consumiam 44% da margem do carvão no exemplo do plano); **nenhuma taxa por categoria
  semeada** ainda, porque `Product.category` é texto livre e a loja não tem categorias reais
  cadastradas — semear nomes inventados deixaria a regra morta silenciosamente; carência **7
  dias** (igual à janela de devolução hoje praticada); expiração **180 dias**; teto de resgate
  **50%** e mínimo de saldo **R$50 para resgatar** (ambos só parâmetros semeados em
  `system_config`, sem leitor ainda — resgate é fatia seguinte).
  Domínio novo em `core/domain/model/cashback`: `CashbackRate` (abrangência `GLOBAL`/`CATEGORY`/
  `SKU`, resolução pela mais específica ativa e vigente) e `CashbackEntry`, ledger append-only
  **sem coluna `status`** — divergência deliberada do rascunho do plano: disponibilidade é sempre
  derivada de `available_at` comparado a `now()`, o que cobre `EARNED` e os futuros `REDEEMED`/
  `REVERSED`/`EXPIRED` sem precisar mutar a linha original. **`REVERSED` deixou de ser "futuro" em
  2026-07-29** — é escrito por `CashbackUseCase.reverseEarningsForOrder`, acionado por
  `OrderService.refundOrder` (Fatia 5, PDV-F007, domínio `vendas-balcao`), não por esta fatia;
  `REDEEMED` (resgate no balcão) continua pendente. `PdvService.registerSale` resolve e
  carimba a taxa por item antes de concluir a venda, e `recordEarnedForOrder` lança o `EARNED`
  logo depois de salvar — mesma transação, mesmo padrão da captura de pagamento; nada é lançado
  para venda anônima ou cliente sem CPF (`Customer.isOfficiallyRegistered()`).
  `CashbackExpiryCleanupService` (novo, `infra/scheduler`) expira ganhos vencidos uma vez por dia,
  molde exato de `StockReservationExpiryCleanupService`. Novo `CashbackController` em `/cashback`
  (`CASHBACK_RATE_MANAGE`/`CASHBACK_READ`): CRUD de taxa, `GET /cashback/rates/resolve`, `GET
  /cashback/margin-impact` (usa `Pricing.marginPercent()`, sem matemática nova; **corrigido em
  2026-07-29** para ler via `estoqueUseCase.findPricingBySku(sku)` em vez de `product.pricing()`
  cru — direto do domínio, um kit virtual (EST-F015, Fatia 6) sempre tem `costPrice` nulo, e a
  leitura crua excluiria todo kit deste relatório), saldo e extrato
  por cliente. `GET /crm/customers/{id}/cashback` e `CustomerResponseDTO.cashback` deixam de ser
  placeholder — a listagem paginada de clientes mantém o zero de propósito, para não virar um
  N+1 de saldo por linha da página; só a busca por id paga a consulta real. Migration V70 (V69 já
  fora consumida por CRM-C005/F002 no mesmo dia).
  Pequenas extensões de porta para viabilizar a taxa por categoria e os parâmetros configuráveis:
  `EstoqueUseCase.findProductBySku` (resolve categoria do SKU) e
  `SystemConfigPort.getInt`/`getDecimal` (espelham `getBoolean` já existente).
  Coberto por `CashbackRateTest`, `CashbackEntryTest`, `CashbackServiceTest`, casos novos em
  `PdvServiceTest`, `CashbackFlowIT` (ponta a ponta contra banco real: venda ganha cashback para
  cliente com CPF, nada é lançado para cliente sem CPF nem venda anônima) e
  `CashbackControllerSecurityTest`. Conhecido em aberto: nenhum teste dedicado ao scheduler de
  expiração — mesma lacuna já aceita para `StockReservationExpiryCleanupService`, que também não
  tem um.
- **2026-07-29** — `cliente-identificavel-no-balcao-sem-email` (**CRM-C005** + **CRM-F002**):
  `Customer.email` deixou de ser obrigatório. Modelo revisto com o dono, mais amplo que o §2.5
  original do plano (que só prometia "email OU cpf"): **CPF é o identificador oficial** do
  cadastro; email e contato (telefone) são identificadores alternativos, qualquer um dos três
  basta — checado no compact constructor de `Customer`, não em Bean Validation, porque é regra
  de negócio entre campos. Venda anônima de balcão continua **sem** exigir nenhum dos três; o
  gatilho para identificar é o operador querer o "CPF na nota?", não a venda em si. Cliente sem
  CPF ("cliente leve", achado só por email/contato) é válido e pesquisável, mas fica marcado como
  não elegível a cashback via `Customer.isOfficiallyRegistered()` — porta pronta para a Fatia 4
  usar, sem reimplementar a checagem. Migration V69: `email`/`contato` viram `NULLABLE`,
  `uk_customers_cpf` novo (UNIQUE padrão — SQL trata cada NULL como distinto dos demais, então não
  precisou de índice parcial, que o H2 do perfil dev não suporta), `CHECK` exigindo pelo menos um
  identificador. Novo `GET /crm/customers/lookup?cpf=&email=&contato=` sob `CRM_CUSTOMER_LOOKUP`
  (permissão própria, separada de `CRM_CUSTOMER_READ` — achar um cliente não é listar a base),
  prioridade cpf → email → contato quando mais de um critério vier preenchido; 404 quando não
  acha, 400 quando nenhum critério é informado. `POST /crm/customers` (cadastro rápido) reaproveitado
  sem mudança de rota — só `contato`/`email` deixaram de ser obrigatórios no request. Duplicidade
  de CPF passa a responder `409 CUSTOMER_CPF_ALREADY_EXISTS`, espelhando o que já existia para
  email. Coberto por casos novos em `CustomerTest`, `CrmServiceTest`, `CrmControllerTest` e
  `CrmControllerSecurityTest` (inclusive uma jornada de ponta a ponta: cadastra por CPF, acha pelo
  lookup).

Série legada `F001–F009`, geração de planejamento de 2026-07-20. Descrições técnicas detalhadas
em [`docs/feature-registry.md`](../../feature-registry.md), seção `crm`.

| ID | Prioridade | Feature | Descrição | Tela destravada no frontend |
|---|---|---|---|---|
| F001 | 🔴 Alta | `cadastro-cliente` | Entidade `Customer` (nome, contato, email, CPF, origem, cadastradoEm), casos de uso de criar/buscar, permissões RBAC (`CUSTOMER_CREATE`, `CUSTOMER_READ`, …) e migration inicial. Fundação do módulo. | Dialog "Novo Cliente"; troca do guard `placeholder: true` de `/app/crm` pela permissão real `CUSTOMER_READ` |
| F002 | 🔴 Alta | `listagem-clientes-rfm` | Endpoint paginado e filtrável (busca por nome/telefone, filtro por segmento) devolvendo LTV, cashback, tags e segmento RFM calculado. | Base de Clientes |
| F003 | 🔴 Alta | `perfil-cliente-360` | Endpoints de histórico de pedidos, extrato de cashback e notas/interações por cliente. | Detalhe do Cliente (abas Visão Geral / Pedidos / Cashback / Notas) |
| F004 | 🟡 Média | `kanban-segmentacao` | Movimentação de cliente entre estágios/segmentos com trilha de auditoria da transição. | Kanban (Funil de Atendimento) |
| F005 | 🟡 Média | `dashboard-overview` | Agregação: total de clientes, ativos (30d), LTV médio, disparos WhatsApp/mês, contagem por segmento RFM. | Overview / Dashboard do CRM |
| F006 | 🟡 Média | `automacoes-campanhas` | CRUD de regras de automação (gatilho, segmento-alvo, canal WhatsApp/E-mail/ambos, template com placeholders `{nome}`/`{saldo}`, ativa/inativa) + log de envios e conversão. | Automações |
| F007 | 🟢 Baixa | `tags-segmentos` | CRUD de tags, associação cliente↔tag e contagem de clientes por tag. | Segmentos / Tags |
| F008 | 🟢 Baixa | `integracao-canal-envio` | Status real de conexão do canal de envio, substituindo o badge fixo "API WhatsApp: Conectada" hardcoded no frontend; reaproveita `EmailAdapterConfig`/`MailpitEmailAdapter` como base do canal de e-mail. | Badge de status em Automações |
| F009 | 🟢 Baixa | `exportacao-csv-clientes` | Exportação server-side da listagem (antes o CSV era montado no cliente a partir dos dados em memória). | Botão "Exportar CSV" na Base de Clientes |

## Próximos passos

Roteiro completo, com prompt pronto para colar numa sessão nova, em
[`proximos-passos.md`](proximos-passos.md).

- [ ] **CRM-C002** — auditoria no export da base; é o maior risco aberto do módulo.
- [x] **CRM-C005 + CRM-F002** — Fatia 2 de [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md):
      cliente identificável no balcão. Fechado em 2026-07-29 — destravou a Fatia 4.
- [x] **CRM-F003** — Fatia 4: programa de cashback (ganhar + consultas). Fechado em 2026-07-29 —
      taxa global calibrada com o dono (3%, não os 8% do rascunho do plano). Resgate no balcão e
      ajuste manual ficam para uma fatia seguinte, isolada.
- [ ] **CRM-F001 (orders)** — `GET /crm/customers/{id}/orders` ainda é placeholder; a metade
      `/cashback` já foi fechada pela CRM-F003.
- [ ] **CRM-C001** — auditar o código e completar este README (Modelo de Domínio, Regras, API, Schema, Testes).
- [ ] **CRM-C003** — decidir entre implementar o envio de campanha ou explicitar que é simulação.
