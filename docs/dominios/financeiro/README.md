# Domínio: financeiro

**Status:** 🟡 Fluxo de caixa (`FIN-F004`) e relatório de margem (`FIN-F003`) implementados — DRE e conciliação de gateway pendentes
**Pacote Java:** `com.cernecommerce...financeiro`
**Rota HTTP base:** `/financeiro`
**Última atualização deste doc:** 2026-08-18 (`FIN-F003` implementado: `GET /financeiro/margem`, relatório de margem agregado do período)

## Objetivo

Gestão financeira integrada do lounge físico e do e-commerce.

## Escopo planejado

- **DRE simplificado:** apuração de resultado por período (`DreLine`).
- **Fluxo de caixa:** lançamentos de entrada/saída (`CashFlowEntry`).
- **Conciliação de taxas de gateways:** repasse × venda, taxas por gateway
  (`GatewayFee` + `Reconciliation`).

## Modelo de Domínio

`FIN-F004` deu persistência real ao fluxo de caixa e `FIN-F003` trouxe o relatório de margem; DRE
e conciliação de gateway seguem esqueleto:

| Camada | Artefato |
|---|---|
| domain/model | `core/domain/model/financeiro/CashFlowEntry` — record imutável com `id`, `date` (lançamento, atribuída pelo servidor), `description`, `entityName`, `category` (`CashFlowCategory`, 10 valores), `direction`, `amount`, `status` (`CashFlowStatus`), `dueDate`, `paymentDate`, `linkedEntityType`/`linkedEntityId` (`LinkedEntityType`), `deletedAt` |
| domain/model (margem) | `core/domain/model/pedido/MarginSummary` — record com `itemsConsidered`, `totalRevenueNet`, `totalCost`, `totalMargin`, `marginPercent`, `topProductsByMargin` (`MarginByProduct`); vive no pacote `pedido` (mesma base de `OrderSummary`), não em `financeiro` |
| ports/in | `core/ports/in/FinanceiroUseCase` — `listCashFlow`, `createCashFlowEntry`, `updateCashFlowEntry` (patch), `deleteCashFlowEntry`, `getCashFlowSummary`. Margem fica em `core/ports/in/OrderReportUseCase#getMarginReport`, reaproveitado por `FinanceiroController` |
| ports/out | `core/ports/out/financeiro/LedgerRepository` — `findAll`, `findByPeriod`, `findById`, `save`, `softDelete`, `hardDelete`, `summarize`. Margem: `core/ports/out/pedido/OrderReportRepository#summarizeMargin` |
| service | `core/service/FinanceiroService` — injeta `LedgerRepository`; regra de "PAGO sem paymentDate grava hoje" e validação de período do summary (mesmo teto de `OrderReportService`). `OrderReportService#getMarginReport` reusa `validatePeriod`/`MAX_RANGE_DAYS` já existentes |
| adapter/in | `adapter/in/controller/FinanceiroController` → `GET`/`POST /financeiro/cash-flow`, `PATCH`/`DELETE /financeiro/cash-flow/{id}`, `GET /financeiro/cash-flow/summary`, `GET /financeiro/margem` (injeta `OrderReportUseCase` além de `FinanceiroUseCase`) |
| DI | `CoreBeanConfig.java` — `financeiroUseCase(LedgerRepository)` injeta o adapter real; margem não precisou de bean novo (`OrderReportUseCase`/`OrderReportRepository` já existiam) |
| persistência | `LedgerEntity` (tabela `cash_flow_entry`, V103) + `LedgerJpaRepository` + `LedgerRepositoryImpl` — enums gravados como `String` (convenção do projeto, sem `@Enumerated`). Margem: `OrderItemJpaRepository#findMarginTotals`/`findTopProductsByMargin` (JPQL, sem tabela nova) + `OrderReportRepositoryImpl#summarizeMargin` |
| testes | `FinanceiroServiceTest` (unit, Mockito), `FinanceiroControllerTest` (MockMvc standalone, inclui `GET /financeiro/margem`), `FinanceiroControllerSecurityTest` (RBAC, banco real), `FinanceiroFlowIT` (create→patch→summary→delete, banco real). Margem: `OrderReportServiceTest` (unit), `OrderReportRepositoryIT` (banco real, exclusão de itens sem custo e ranking por margem) |

## Regras de Negócio Implementadas

- **Criação nasce `PREVISTO`**, com `date` (lançamento) atribuída pelo servidor como hoje —
  distinta de `dueDate` (vencimento, obrigatória) e `paymentDate` (pagamento efetivo).
- **Marcar `status: PAGO` sem `paymentDate`** grava a data corrente no servidor
  (`FinanceiroService.updateCashFlowEntry`) — pedido explícito do contrato do front.
- **PATCH parcial**: campo ausente ou nulo mantém o valor atual, mesma semântica de
  `CategoryPatchRequest`.
- **DELETE**: soft-delete (`deleted_at`) quando há `linkedEntityId`, preservando o vínculo com o
  pedido/compra de origem; remoção definitiva quando não há vínculo.
- **Summary agregado no backend** (`GET /financeiro/cash-flow/summary?from&to`, por `due_date`),
  não somado no cliente — fecha a pergunta em aberto do contrato, no molde de
  `GET /estoque/summary`/`GET /orders/summary`.
- **`status` é coluna mutável, não ledger append-only** — decisão consciente para seguir o
  contrato do front (`PATCH` direto); imutabilidade no espírito de `cashback_entry`/`stock_movement`
  fica para depois (ver "Regras Pendentes").

## Regras Pendentes

- DRE simplificado por período (`buildDre`).
- Conciliação de taxas de gateway (`reconcileGatewayFees`).
- Imutabilidade dos lançamentos (estorno como novo lançamento, nunca `UPDATE`/`DELETE` direto) —
  deliberadamente não implementada em `FIN-F004`; ver "Contrato de escrita" abaixo.
- Geração automática de lançamento a partir de venda (`VENDA_PDV`/`VENDA_ECOMMERCE` no fechamento
  de caixa) — hoje `category` aceita esses valores, mas nada os grava automaticamente; a criação é
  sempre manual via `POST`.
- `FIN-F001` — cashback reconhecido como provisão de passivo no ganho, não despesa no resgate.
- `FIN-F002` — NFC-e via emissor terceiro.

## API — Endpoints

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| GET | `/financeiro/cash-flow?page&size` | `FINANCEIRO_READ` | Lista lançamentos (exclui removidos) |
| GET | `/financeiro/cash-flow/summary?from&to` | `FINANCEIRO_READ` | Saldo agregado (entradas, saídas, líquido) no período |
| POST | `/financeiro/cash-flow` | `FINANCEIRO_CASH_FLOW_MANAGE` | Cria lançamento, nasce `PREVISTO` |
| PATCH | `/financeiro/cash-flow/{id}` | `FINANCEIRO_CASH_FLOW_MANAGE` | Edita parcialmente e/ou marca como pago |
| DELETE | `/financeiro/cash-flow/{id}` | `FINANCEIRO_CASH_FLOW_MANAGE` | Remove (soft-delete se houver vínculo) |
| GET | `/financeiro/margem?channel&warehouseCode&from&to` | `FINANCEIRO_READ` | Relatório de margem agregado do período (`FIN-F003`) — `from`/`to` obrigatórios (máx. 366 dias) |

Duas permissões no domínio: `FINANCEIRO_READ` (V53) e `FINANCEIRO_CASH_FLOW_MANAGE` (V103,
concedida a `ROLE_ADMIN`) — separa leitura de escrita, mas ainda não separa DRE/conciliação
(pendente, sem consumidor ainda).

## Segurança e Infraestrutura

> Transversal em [`docs/security.md`](../../security.md) e
> [`docs/infrastructure.md`](../../infrastructure.md); modelo RBAC completo em
> [`plataforma`](../plataforma/README.md#segurança-e-infraestrutura).

**O que já existe.** `FINANCEIRO_READ` (V53, concedida a `ROLE_ADMIN`) protege os `GET`;
`FINANCEIRO_CASH_FLOW_MANAGE` (V103, concedida a `ROLE_ADMIN`; ambas semeadas em `dev` por
`SeedConfig`/`DevRoleBootstrapConfig`) protege `POST`/`PATCH`/`DELETE /financeiro/cash-flow`.
Criação, edição e remoção de lançamento publicam `AuditEvent` (`CASH_FLOW_ENTRY_CREATED`/
`_UPDATED`/`_DELETED`). DRE e conciliação seguem sem tabela, auditoria ou infra própria.

**O que este módulo vai precisar quando sair do esqueleto (DRE/conciliação).** É o domínio de
maior sensibilidade do sistema: consolida resultado, custo e repasse de gateway.

- [x] Permissão de escrita separada de leitura para fluxo de caixa: **resolvido em `FIN-F004`**
      (`FINANCEIRO_CASH_FLOW_MANAGE`). Ainda falta granularidade própria para DRE e conciliação,
      que continuam sem endpoint.
- [x] `AuditEvent` na origem do dado financeiro: **resolvido em 2026-07-27** (`EST-C004`, fecha
      `COM-C003`/`PDV-C003`) — `PdvController` e `ComprasController` publicam
      `STOCK_MOVEMENT_REGISTERED` a cada operação; o próprio `FinanceiroController` publica os
      eventos de fluxo de caixa desde `FIN-F004`.
- [ ] Imutabilidade dos lançamentos: estorno como novo lançamento, nunca `UPDATE`/`DELETE`, no
      mesmo espírito do ledger `stock_movement` do [`estoque`](../estoque/README.md). **Decisão
      consciente em `FIN-F004`**: o contrato do front pede `PATCH` direto (`status` mutável), e a
      migração para ledger append-only fica para quando houver necessidade de auditoria mais
      forte que `AuditEvent` — não é um "esqueci", é adiar uma migração de schema que hoje não
      tem consumidor pedindo.
- [ ] Rate limit nos relatórios agregados, que serão as consultas mais caras do backend
      (PLAT-C030) — `GET /financeiro/cash-flow/summary` já valida período (mesmo teto de
      `OrderReportService`, 366 dias) mas não tem rate limit de verdade, só essa defesa.
- [ ] Credenciais de gateway no fluxo de `.env` + validadores de startup
      ([`docs/infrastructure.md`](../../infrastructure.md#variáveis-de-ambiente-e-segredos)) e
      importação de repasses com verificação de origem.
- [ ] Retenção fiscal própria: os `audit_logs` expiram em 365 dias
      (`AuditLogCleanupService`), prazo insuficiente para obrigação contábil.

## Schema de Banco (Migrations)

`V103__financeiro_cash_flow.sql` — tabela `cash_flow_entry` (colunas do contrato de escrita
abaixo + `deleted_at` para soft-delete), `CHECK`s nomeados para `category`/`direction`/`status`/
`linked_entity_type` (mesma convenção de `V65__pedido_sales_order.sql`), índices parciais
(`WHERE deleted_at IS NULL`) em `due_date` e `status`, e a permissão
`FINANCEIRO_CASH_FLOW_MANAGE`. `dre_line`, `gateway_fee` e `reconciliation` continuam sem tabela.
Próximo número de migration livre no projeto: **V104**.

Dados que o financeiro vai consumir de outros domínios, já persistidos e prontos:
- `sales_order`/`order_item` (V65, V98–V100) — `order_number` de sequência própria
  (`order_number_seq`), `cost_price`/`unit_price`/`cashback_percent` congelados por item,
  `change_amount` no pedido (nunca linha de pagamento).
- `order_payment` (V68) — uma linha por forma de pagamento, com `gateway_ref` já indexado para
  conciliação futura.
- `cashback_entry` (V70, V80) — ledger append-only (`EARNED`/`REDEEMED`/`REVERSED`/`EXPIRED`),
  base do `FIN-F001`.
- `cash_register_session`/`cash_movement` (V66) — `expected`/`counted`/`difference` por sessão.

## Cobertura de Testes

`FIN-F004` trouxe as três camadas, no padrão usado em [`cashback`](../crm/README.md):
- `FinanceiroServiceTest` (unit, Mockito) — criação, PATCH parcial, `PAGO` sem `paymentDate`
  gravando hoje, soft vs. hard delete, validação de período do summary.
- `FinanceiroControllerTest` (MockMvc standalone) — contrato HTTP de cada endpoint, validação de
  request (`@Valid`), evento de auditoria publicado.
- `FinanceiroControllerSecurityTest` (`@SpringBootTest`, banco real) — 401/403/200 de
  `FINANCEIRO_READ` e `FINANCEIRO_CASH_FLOW_MANAGE` em todos os endpoints.
- `FinanceiroFlowIT` (`@SpringBootTest`, banco real) — fluxo completo create → patch (pago) →
  summary → delete, e o caminho soft-delete com `linkedEntityId`.

DRE e conciliação continuam sem teste — sem regra de negócio implementada ainda.

## Contrato de escrita — `FIN-F004`

**Implementado em 2026-08-18.** Recebido do time de front (`mahal-admin`,
`Docs/BACKEND_TODO.md`, 2026-08-18) — reproduzido abaixo como referência do que foi construído.

Schema anterior (`{ id, date, description, amount, direction }`) cresceu com `category` (enum de
10 valores), `entityName`, `status` (`PREVISTO`/`PAGO`/`ATRASADO`), `dueDate` separado de
`paymentDate`, e `linkedEntityType`/`linkedEntityId` (vínculo opcional com `ORDER` ou `PURCHASE`
— ver nota de nomenclatura abaixo).

**Endpoints implementados:**
- `POST /financeiro/cash-flow` — cria lançamento, `status: 'PREVISTO'` por default, `date`
  atribuída pelo servidor.
- `PATCH /financeiro/cash-flow/{id}` — edita campos e/ou marca como pago; `status: 'PAGO'` sem
  `paymentDate` grava a data corrente no servidor.
- `DELETE /financeiro/cash-flow/{id}` — soft-delete quando há `linkedEntityId`, remoção
  definitiva caso contrário.
- `GET /financeiro/cash-flow/summary?from&to` — **decisão fechada:** o backend agrega (não o
  cliente), no molde de `GET /estoque/summary`/`GET /orders/summary`. Fecha a pergunta que o
  front tinha deixado em aberto.

**Nota de nomenclatura — `linkedEntityType`.** O contrato original falava em vínculo com
"pedido, compra ou venda de origem" (3 conceitos). Neste backend, venda de balcão e pedido de
e-commerce são o mesmo agregado (`sales_order`, distinguido por `SalesChannel`) — por isso
`LinkedEntityType` tem só dois valores, `ORDER` (cobre pedido e venda) e `PURCHASE` (cobre
compra/`GoodsReceipt`), em vez de três. Se o front esperava um terceiro valor distinto para
venda de balcão, alinhar antes de consumir o campo.

**Não implementado nesta fatia** (fora do contrato de API, registrado em "Regras Pendentes"):
geração automática de lançamento a partir de fechamento de caixa (`VENDA_PDV`) ou pedido
concluído (`VENDA_ECOMMERCE`) — hoje todo lançamento é criado manualmente via `POST`; e
imutabilidade ledger-style (o `PATCH` de `status` é edição direta, não um novo lançamento).

Ver `Docs/MODULO_GESTAO_EMPRESARIAL.md` no `mahal-admin` para o detalhamento de tela esperado.

## Testes no Postman

Coleção do módulo: [`financeiro.postman_collection.json`](financeiro.postman_collection.json) — importe no Postman, rode a pasta
`00 — Autenticação` (que faz login e guarda o `accessToken`) e siga as pastas na ordem, ou
rode tudo de uma vez no Collection Runner.

```bash
npx newman run docs/dominios/financeiro/financeiro.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

A coleção valida o que já existe (`GET`/`POST`/`PATCH`/`DELETE /financeiro/cash-flow` e
`GET /financeiro/cash-flow/summary`): o contrato de `PageResult`, a validação de `page`/`size`,
os campos do lançamento e a proteção por `FINANCEIRO_READ`/`FINANCEIRO_CASH_FLOW_MANAGE`.

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| FIN-F003 | 🔴 Alta | Feature | relatorio-margem-por-periodo | `GET /financeiro/margem?channel&warehouseCode&from&to` — agrega `OrderItem.marginAmount()` (`netAmount - costPrice*quantity`) dos pedidos com pagamento confirmado, filtrável por canal e depósito (`Order.warehouseCode`). Itens sem `costPrice` congelado (pedidos legados) são excluídos do agregado inteiro (`itemsConsidered` expõe quantos entraram), não contados como margem zero. Sem migration — reusa `OrderReportUseCase`/`OrderReportRepository` (mesmo teto de 366 dias) e a permissão `FINANCEIRO_READ` já existente. | **Concluído em 2026-08-18** |
| FIN-F004 | 🔴 Alta | Feature | fluxo-de-caixa-com-escrita | Persistência real de `CashFlowEntry` (V103, `cash_flow_entry`) + `POST`/`PATCH`/`DELETE /financeiro/cash-flow` + `GET /financeiro/cash-flow/summary` (agregação no backend). Contrato de escrita do front (`Docs/BACKEND_TODO.md` do `mahal-admin`, recebido em 2026-08-18) implementado conforme especificado, com uma ressalva de nomenclatura em `linkedEntityType` — ver **Contrato de escrita — `FIN-F004`** abaixo. Geração automática de lançamento a partir de venda (`VENDA_PDV`/`VENDA_ECOMMERCE`) e imutabilidade ledger-style ficaram fora desta fatia. | **Concluído em 2026-08-18** |
| FIN-F001 | 🟡 Média | Feature | cashback-como-provisao-no-dre | Cashback reconhecido como **provisão de passivo no ganho**, não como despesa no resgate — o crédito já existe contra a empresa no momento em que é gerado, e reconhecê-lo só no resgate infla o resultado dos meses em que os clientes acumulam. Consome o ledger `cashback_entry` (CRM-F003). Depende de um DRE mínimo existir (`FIN-F004` primeiro). Ver [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.4. | Pendente |
| FIN-F002 | 🟡 Média | Feature | nfce-via-emissor-terceiro | Port fiscal + adapter para Focus NFe ou PlugNotas, campos fiscais (NCM/CEST/CFOP) no produto, emissão na conclusão do pedido e cancelamento dentro da janela legal. **Nunca construir emissão própria** (§2.8/§8.1): schemas XML por UF, certificado A1/A3, contingência, homologação SEFAZ e manutenção perpétua, contra ~R$100–300/mês de prateleira. Para tabacaria o argumento é mais forte ainda — fumo tem ICMS-ST e IPI, e errar CST 60/NCM/CEST não gera bug, gera autuação. **Não é bloqueante para o PDV rodar**; é o portão para desligar o processo fiscal atual. Fatia 11 — calendário (certificado, cadastro fiscal, homologação) maior que o esforço. | Pendente |
| FIN-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | README no molde de esqueleto: faltavam Modelo de Domínio, Regras de Negócio, API, Schema e Cobertura de Testes. | **Concluído em 2026-08-18** via `/analyze-domain financeiro` |
| FIN-C002 | 🟢 Melhoria | Correção | corrigir-nota-desatualizada-auditoria | README afirmava que `compras`/`vendas-balcao` não publicavam `AuditEvent` (`COM-C003`/`PDV-C003`), bloqueando o financeiro. Resolvido desde `EST-C004` (2026-07-27); nota corrigida. | **Concluído em 2026-08-18** |
| FIN-F005 | 🟡 Média | Feature | marcados-como-a-receber | Mostrar os marcados do CRM-F010 (`customer_receivable` em aberto, por vencimento) como "a receber" no Financeiro. Hoje o cash-flow é ledger manual e não lê `order_payment` nem `customer_receivable`, então nada entra errado — só não aparece. A quitação já tem dado por método e sessão em `receivable_payment` | ⏳ Pendente (registrado 2026-10-01) |

> `EST-F007` (`valorizacao-custo-medio`) fechou em 2026-08-16 — ver
> [Histórico de Implementações do estoque](../estoque/README.md#histórico-de-implementações).
> `StockBalance.averageCost` (custo médio ponderado móvel por SKU/depósito) e `GET
> /estoque/summary` (`valorEstoqueCusto`, com fallback para `costPrice` manual) já estão
> disponíveis para o financeiro consumir quando o DRE for construído — nenhum endpoint dedicado
> de valorização foi criado nesta entrega (decisão: estender o que já existia em vez de expor
> superfície nova).

> O que o financeiro precisa que seja decidido **agora**, mesmo sem construir nada de fiscal
> ([`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.8): o pedido nasce **imutável
> depois de concluído** e com **numeração estável e sem buracos** (`order_number` de sequência
> própria, emitido na conclusão — `BIGSERIAL` deixa buracos em rollback, e buraco em numeração de
> documento fiscal é problema com o fisco). Adicionar NCM/CEST depois é migration trivial;
> retrofitar imutabilidade e numeração num modelo já em produção, não.

## Histórico de Implementações

- **2026-08-18** — `FIN-F003` (relatório de margem por período): `GET /financeiro/margem`
  agrega `OrderItem.marginAmount()` dos pedidos com pagamento confirmado, filtrável por
  `channel`/`warehouseCode`, mesmo teto de 366 dias de `GET /financeiro/cash-flow/summary`/
  `GET /orders/summary`. Sem migration — nova query em `OrderItemJpaRepository`
  (`findMarginTotals`/`findTopProductsByMargin`) e novo método `summarizeMargin` em
  `OrderReportRepository`/`OrderReportUseCase` (não em `FinanceiroUseCase`: o dado nasce em
  `order_item`, então `FinanceiroController` reaproveita `OrderReportUseCase` diretamente, mesmo
  padrão de composição cross-domain de `OrdersController`/`CrmUseCase`). Itens sem `costPrice`
  congelado (pedidos legados pré-migração) são excluídos do agregado inteiro, não só do custo —
  `itemsConsidered` deixa a exclusão visível. Reusa a permissão `FINANCEIRO_READ` já existente,
  sem RBAC novo.
- **2026-08-18** — `FIN-F004` (contrato de escrita do fluxo de caixa): persistência real de
  `CashFlowEntry` (`LedgerEntity`/`cash_flow_entry`, V103), `POST`/`PATCH`/`DELETE
  /financeiro/cash-flow` e `GET /financeiro/cash-flow/summary` (agregação no backend, fechando a
  pergunta em aberto do contrato). Nova permissão `FINANCEIRO_CASH_FLOW_MANAGE`. Ver seção
  "Contrato de escrita — `FIN-F004`" para a ressalva de nomenclatura em `linkedEntityType`
  (`ORDER`/`PURCHASE`, dois valores em vez dos três do texto original do front).
- **2026-08-18** — `FIN-C001`/`FIN-C002` (auditoria via `/analyze-domain financeiro`): README
  reescrito no padrão do estoque (Modelo de Domínio, Regras Implementadas/Pendentes, API, Schema,
  Testes). Confirmado que as três decisões de `vendas-balcao` das quais o financeiro depende
  (`order_number` de sequência própria, `cost_price` congelado em `order_item`, `change_amount`
  como campo do pedido) **já estão em produção**. Confirmado que a trilha de auditoria
  (`STOCK_MOVEMENT_REGISTERED`) já existe desde `EST-C004` (2026-07-27), corrigindo nota
  desatualizada deste README. Identificados `FIN-F003` (relatório de margem, quick win) e
  `FIN-F004` (fluxo de caixa real) como próximos passos antes de `FIN-F001`/DRE. Nenhum código
  alterado nesta entrega — só documentação.

## Próximos passos

Roteiro completo em [`proximos-passos.md`](proximos-passos.md) — inclui **as três decisões de
outros módulos que precisam ser cobradas agora** para o financeiro não nascer sem margem
histórica nem numeração fiscal confiável (já confirmadas entregues, ver Histórico acima).

- [x] **FIN-F003** — relatório de margem por período: **concluído em 2026-08-18**, ver
      "Histórico de Implementações" acima.
- [x] **FIN-F004** — fluxo de caixa com escrita: **concluído em 2026-08-18**, ver "Contrato de
      escrita" e "Histórico de Implementações" acima.
- [ ] **FIN-F001** — cashback como provisão de passivo, reconhecida no ganho. Depende de um DRE
      mínimo existir.
- [ ] **FIN-F002** — NFC-e via emissor terceiro; nunca construir emissão própria. Não bloqueante
      para o PDV.
- [ ] Modelos: `DreLine`, `GatewayFee`, `Reconciliation`.
- [ ] Casos de uso: `buildDre`, `reconcileGatewayFees`.
- [ ] Ports out: `GatewayFeeReconciliationPort` (importação de repasses/taxas dos gateways).
- [ ] Adapter de persistência para DRE/conciliação (fluxo de caixa já tem o seu, `LedgerRepositoryImpl`).
- [ ] Permissões RBAC granulares para DRE / conciliação (fluxo de caixa já separa leitura de
      escrita: `FINANCEIRO_READ` / `FINANCEIRO_CASH_FLOW_MANAGE`).
