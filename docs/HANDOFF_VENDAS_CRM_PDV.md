# Continuação: Vendas, CRM e PDV (pedido do mahal-admin de 30/09/2026)

Documento de passagem para retomar o trabalho. Escrito em 01/10/2026, no meio da entrega 4.

- **Pedido original:** `../mahal-admin/Docs/PROMPT_BACKEND_VENDAS_CRM_PDV.md` (as 4 entregas).
  A entrega 3 tem spec própria em `../mahal-admin/Docs/PROMPT_BACKEND_MARCAR.md`.
- **Plano aprovado:** `~/.claude/plans/quero-que-veja-o-velvet-moler.md`.
- **Branch:** `feat/vendas-crm-pdv`, saído de `main`. Nada foi enviado ao remoto e não há PR.

---

## 1. Situação

| # | Entrega | Estado | Commit |
|---|---|---|---|
| 1 | Retirada no balcão conclui a venda + `POST /orders/bulk-status` | ✅ pronta, testada | `dbee52a` |
| 2 | Correção da forma de pagamento com lastro | ✅ pronta, testada (H2 + Postgres) | `b1bdc90` |
| 3 | "Marcar": venda a prazo para VIP | ✅ pronta, testada (H2 + Postgres) | `05d5ec8` |
| 4 | Histórico e indicadores de mesas | ✅ pronta, testada (H2 + Postgres) | `f6b45a4` |
| — | Correção: troca de SKU cobre `receivable_item.sku` (falha da entrega 3) | ✅ | `4445b69` |
| — | Renumeração: correção de pagamento PDV-F027 → **PDV-F030** (colidia com sessões paralelas) | ✅ | `32e0c87` |

Suíte completa verde em `f6b45a4`: 260 classes, 3380 testes, 0 falhas. Postgres ITs: só as 4 falhas ambientais conhecidas (`AuthFlowPostgresIT` ×3, `PedidoRepositoryPostgresIT` ×1).

**Próximo:** passos 6–7 da §2.2 (o 5, documentação, está feito) (documentação, aviso ao front, push/PR). Os passos 1–4 estão feitos.

---

## 2. O que falta para fechar a entrega 4

### 2.1 Arquivos já alterados e não commitados (`git status`)

**Novos:**
- `src/main/resources/db/migration/V138__pdv_comanda_historico.sql`: colunas `comanda.closed_by`, `comanda.cancel_reason` e o índice `(status, closed_at)`.
- `core/domain/model/pdv/ClosedComanda.java`: a comanda mais `closedBy` e `cancelReason`.
- `core/domain/model/pdv/ComandaHistoryFilter.java`
- `adapter/in/dtos/request/ComandaCancelRequest.java`: corpo opcional `{reason}` do cancelamento.

**Alterados:**
- `ComandaEntity`: campos `closedBy` e `cancelReason`. Ficam fora do record de domínio `Comanda`, que tem 37 pontos de construção.
- `ComandaJpaRepository`:
  - passa a estender `JpaSpecificationExecutor`;
  - ganhou `recordClosing(...)`, um UPDATE.
- `ComandaRepository` (port) e `ComandaRepositoryImpl`, com os métodos novos:
  - `recordClosing`;
  - `findHistory`, por Specification, ID-first, ordem `closedAt DESC`;
  - `findWithClosing`;
  - `findClosedBetween`.
- `OrderRepository` / `OrderRepositoryImpl` / `OrderJpaRepository`: `findByComandaIds`, com os pedidos MESA e seus itens.
- `ComandaUseCase`:
  - `cancelComanda(id, user, reason)`;
  - `listHistory`, `getHistoryEntry`, `analytics`;
  - os records `ComandaHistoryEntry` e `ComandaAnalytics` (+ `SessoesNarguile`, `PorAtendente`, `PorMesa`, `PorHora`), com nomes de campo iguais ao contrato do front.
- `ComandaService`:
  - grava `closedBy` (e o motivo) em todos os encerramentos:

    | Caminho | `closedBy` | Motivo |
    |---|---|---|
    | Fechamento total | operador | — |
    | `finish` | operador | — |
    | `cancel` | operador | o informado |
    | `merge` (comanda de origem) | operador | "Juntada à comanda #X" |
    | Varredura automática | `system` | "Mesa vazia esquecida" |

  - implementa histórico, detalhe e indicadores. A agregação é em memória; o período máximo é de 366 dias, com `InvalidReportPeriodException` → 400.
- `PdvComandaController`:
  - `GET /pdv/comandas/history`;
  - `GET /pdv/comandas/analytics`;
  - `GET /pdv/comandas/{id}` agora usa `getHistoryEntry` e devolve `orders[]` com pagamentos. Permissão: `PDV_READ` **ou** `ORDER_READ`;
  - `POST /{id}/cancel` aceita `{reason}` opcional.
- `ComandaResponseDTO`: `closedBy`, `durationMinutes`, `cancelReason`, `orderIds`, `totalPaid`, `serviceFeeTotal`, `discountTotal`, `courtesyTotal`, `sessionsCount` e `orders[]` (classe interna `ComandaOrder`).

### 2.2 Próximos passos, em ordem

1. **Ajustar os testes que vão quebrar:**
   - `PdvComandaControllerTest.getComanda_returns_200` e `getComanda_notFound_returns_404` mockam `getComanda`; agora o controller chama `getHistoryEntry`. Trocar o stub, montando um `ComandaHistoryEntry` com `orders` vazio e `paymentsByOrder = Map.of()`.
   - `PdvComandaControllerTest` do cancel: o controller chama `cancelComanda(id, user, reason)` com 3 argumentos. Ajustar stubs e verifies que usam 2.
   - `ComandaServiceTest` / `ComandaSessaoServiceTest`: se algum teste usa `verifyNoMoreInteractions(comandaRepository)` depois de fechar ou cancelar, agora existe a chamada extra `recordClosing`.
   - Rodar: `./mvnw -q clean test -Dtest='PdvComandaController*,ComandaService*,ComandaSessaoServiceTest,PdvServiceTest'`.
2. **Testes novos:**
   - `ComandaServiceTest`:
     - `recordClosing` chamado no close total (não no parcial), no finish, no cancel com motivo, no merge e na varredura;
     - `listHistory` monta os totais: reembolsado fora de `totalPaid`, cortesia em `courtesyTotal`, `sessionsCount` das linhas SESSAO;
     - `analytics` agrupa `porMesa` pelo label normalizado, `porHora` pela hora de abertura em São Paulo, e recusa período invertido ou acima de 366 dias.
   - IT em H2 (por exemplo `ComandaHistoryIT`, no molde de `ComandaCashCycleIT`, que usa `@TestPropertySource("pdv.sessao.legacy-enabled=true")`): fechar uma mesa com fechamento parcial + total, cancelar outra com motivo, e conferir `history`, o detalhe com `orders` e `analytics`.
   - Segurança (`PdvComandaControllerSecurityTest`):
     - `history` e `analytics` com `ORDER_READ` → 200;
     - sem permissão → 403;
     - `analytics` sem `from`/`to` → 400.
   - Postgres (Testcontainers): um teste aplicando a V138 e rodando `findHistory` com filtro de data. É ali que `Instant` nulo daria problema se não fosse Specification.
3. **Suíte completa:** `./mvnw -q clean test`. Depois os Postgres ITs: `ENABLE_TC=true ./mvnw -q test -Dapi.version=1.44 -Dtest='*PostgresIT'`.
4. **Commit da entrega 4.** Mensagem sugerida:
   `feat(pdv): historico e indicadores de mesas (closedBy, cancelReason, history, analytics)`,
   com a linha `Co-Authored-By` usada nos commits anteriores.
5. ✅ **Documentação** (2026-10-01, sem commit ainda): rodar o skill `/document` para atualizar `docs/feature-registry.md` e `docs/dominios/*`.
   Códigos usados: PDV-F030 (correção; era PDV-F027, renumerada em `32e0c87`), CRM-F010 / PDV-F028 (Marcar), PDV-F029 (mesas).
6. **Avisar o front:** ver a seção 4.
7. **Push e PR**, quando o dono quiser. A cópia para `mahal-backend-admin-prod` segue a memória "Prod repo sync".

---

## 3. Armadilhas descobertas nesta sessão

- **A compilação incremental do Maven mente.** Um `./mvnw compile` deu BUILD SUCCESS com uma classe que não implementava métodos novos da interface. **Sempre usar `./mvnw clean compile` ou `clean test`.**
- **Postgres ITs:** precisam de `ENABLE_TC=true` e `-Dapi.version=1.44`.
- **Testes H2:** o schema sai das entidades (`ddl-auto`), não das migrations. Os CHECKs só são exercitados nos `*PostgresIT`.
- **`AuditEvent.of` usa `Map.copyOf`:** valor nulo no payload derruba a requisição com 500. Remova os nulos antes, como no `OnAccountGuard`.
- **Quitação do marcado:** `receivable_payment.amount` já é o abatido, líquido do troco. Não subtraia o troco do lote de novo; esse bug foi pego e corrigido no IT.

---

## 4. Decisões que divergem da spec (avisar o front / dono)

1. **`RECEBIMENTO_MARCADO` não é `CashMovement`.**
   - O `CashMovement` só existe para dinheiro e já entra no esperado, então contaria a quitação duas vezes.
   - A quitação aparece em `GET /pdv/sessions/{id}/payment-totals` como o campo novo `receivableReceived`, por método. Ele já entra em `netAmount` e no esperado do fechamento.
2. **Limite de crédito em tabela própria** (`customer_credit_limit`), e não em `customers.credit_limit`.
   - O `save` do cliente regrava a ficha inteira e zeraria a coluna.
   - A API não muda: `PUT /crm/customers/{id}/credit-limit`, e `creditLimit`/`openBalance`/`overdueBalance` no `GET /crm/customers/{id}`.
3. **`creditLimit` nunca é nulo na elegibilidade.** É o limite efetivo: o individual ou o padrão `pdv.on-account.default-credit-limit` (padrão 0, que exige limite individual). O front trata `null` como "sem limite".
4. **Configurações em `system_config`**, sem tela: `pdv.on-account.default-due-days=30` e `pdv.on-account.default-credit-limit=0`. O endpoint `/system/config` só aceita chaves `auth.*`.
5. **Caixa fechado e correção:** a divergência vai para a tabela nova `cash_session_adjustment`, com o delta por método. Ainda **não há endpoint de leitura** desses ajustes para o relatório do caixa.
6. **Financeiro "a receber":** não foi feito. O cash-flow é só ledger manual e não lê `order_payment`, então nada entra errado. Mostrar os marcados como "a receber" fica como pendência.
7. **`paymentStatus` no pedido:**
   - no `GET /orders/{id}` vem do recebível (PAGO/PENDENTE/PARCIAL), junto com `receivableId`;
   - nas respostas de venda do PDV vem das linhas (PENDENTE se houver MARCADO);
   - nas listagens fica nulo.
8. **O cashback da quitação** entra como um lançamento EARNED sem `orderItemId`. O índice único é por (pedido, item) e aceita item nulo. O reembolso continua revertendo tudo pelo `orderId`.
9. **Marcar não vale em:**
   - liquidação de pedido do app (`settleOnlineOrder`);
   - correção de pagamento;
   - a própria quitação.

   Nos três casos a resposta é 400 `INVALID_PAYMENT_METHOD`.
10. **A correção de pagamento** de um pedido com parte marcada exige soma = `totalPayable − marcado`.
11. **Permissões novas:**

    | Permissão | Perfis |
    |---|---|
    | `ORDER_PAYMENT_CORRECT` | ADMIN, ATENDENTE |
    | `ORDER_PAYMENT_CORRECT_CLOSED` | ADMIN |
    | `PDV_SALE_ON_ACCOUNT` | ADMIN |
    | `RECEIVABLE_READ` | ADMIN, ATENDENTE |
    | `RECEIVABLE_MANAGE` | ADMIN |

    Não existe perfil "gerente" no banco.
12. **`courtesyTotal` da mesa é custo, não preço de venda.** A linha de cortesia é gravada a preço
    zero (`ck_comanda_item_courtesy_is_free`) e o preço de venda não fica guardado. O campo traz
    quantidade × `costPrice` congelado no item, ou seja, quanto a casa deu.

---

## 5. Mapa rápido do que foi entregue (entregas 1–3)

- **Entrega 1:** `PdvService.registerSale` reserva só com ENTREGA ou `reserveForPickup=true`. `POST /orders/bulk-status` está em `OrdersController`.
- **Entrega 2:**
  - lógica em `OrderService.correctPayments` / `getPaymentHistory`;
  - V136;
  - na linha de pagamento, `OrderPayment.correctionId` (correção que aposentou) e `originCorrectionId` (correção que criou);
  - endpoints em `OrdersController`.
- **Entrega 3:**
  - serviço: `ReceivableService` (port `ReceivableUseCase`);
  - controller: `ReceivableController`;
  - borda do PDV e da mesa: `OnAccountGuard`;
  - job diário: `ReceivableOverdueJob` (00:05, horário de São Paulo);
  - migration: V137;
  - integração com a venda: `PdvService.toPaymentLine` / `validateOnAccount` / `recordReceivableIfOnAccount`, que o `ComandaService` reaproveita;
  - cashback proporcional em `CashbackService`;
  - testes: `ReceivableServiceTest`, `ReceivableCycleIT`, `ReceivableControllerSecurityTest` e `PagamentoPostgresIT`, que também cobre a entrega 2.

## 6. Como retomar com o Claude Code

Abra uma sessão em `mahal-backend` e peça:

> Leia `docs/HANDOFF_VENDAS_CRM_PDV.md` e continue a entrega 4 a partir da seção 2.2.

---

## 5. Rodada de 01/10/2026 (tarde): ajustes de Vendas e PDV, não commitados

- **CRM-C008**: a busca de cliente sem dígitos dava 500 no Postgres (`cpfDigits` nulo virava `bytea`). Agora são duas consultas; coberto por `CustomerSearchPostgresIT`.
- **PED-F003**: `operatorName` em `GET /orders` e `GET /orders/{id}`, vindo de `cash_register_session.operator`.
- O que é do front (nomes dos módulos, cards de reserva, Marcados movido para Vendas, furo da venda anônima) está em `docs/PROMPT_FRONT_VENDAS_AJUSTES.md`, que não é rastreado.
- Suíte completa verde: 3432 testes.
