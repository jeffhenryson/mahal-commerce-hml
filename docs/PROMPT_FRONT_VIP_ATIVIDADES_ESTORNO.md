# Prompt para o frontend-admin: VIP e limite pelo vendedor, atividades do caixa, estorno por item e aniversário (13/10/2026)

Backend na branch `feat/pdv-caixa-sessao-ajustes`. É a resposta a `Docs/PROMPT_BACKEND_PDV_SESSAO_VIP_LIMITE.md`
(§1, §2 e §3) e ao `Docs/PROMPT_BACKEND_SALAO.md` §3. Itens **PDV-F046**, **PDV-F047**, **PED-F004** e **CRM-F012**.
Migrations **V153 a V156** (o prompt pedia V152/V153, mas a V152 já estava ocupada). Os erros vêm no formato de
sempre: `{ errorCode, message }`.

**Primeiro passo:** `npm run fetch:spec && npm run generate:api`. Depois, trocar os tipos manuais:
- `ItemEstornoRequest` / `DestinoItemEstorno` em `features/vendas/vendas.service.ts`;
- `AtividadeSessao` em `features/pdv/pdv-atividades.service.ts`;
- `crm/cliente-nascimento.ts` (o validador de idade pode ficar);
- as permissões novas em `core/rbac/permissions.constants.ts`.

**Ordem de deploy:** o backend pode subir antes do front. Nenhum contrato antigo mudou.

---

## 1. Vendedor marca VIP e define limite (PDV-F046)

Saiu como pedido. A V154 dá ao `ROLE_ATENDENTE` estas permissões:
- `CRM_CUSTOMER_VIP_MANAGE` (também para ADMIN e DEV);
- `RECEIVABLE_LIMIT_SET_CAPPED` (também para ADMIN e DEV);
- `PDV_SALE_ON_ACCOUNT`;
- `ORDER_REFUND_OWN_SESSION` (ver §3).

- **Tag VIP:** com só `CRM_CUSTOMER_VIP_MANAGE`, `POST /crm/tags`, `POST /crm/customers/{id}/tags` e
  `DELETE /crm/customers/{id}/tags/{tagId}` aceitam **apenas a tag `VIP`** (sem diferenciar maiúsculas). Outra tag →
  **`403 TAG_NOT_ALLOWED`**. `GET /crm/tags` o vendedor já tinha (`CRM_CUSTOMER_READ`).
- **Limite:** `PUT /crm/customers/{id}/credit-limit` com só `RECEIVABLE_LIMIT_SET_CAPPED`:
  - acima do teto → **`422 CREDIT_LIMIT_ABOVE_CAP`**, com a mensagem "Seu limite máximo é R$ X. Valores maiores só com um gerente.";
  - `creditLimit: null` (voltar ao padrão) sempre passa;
  - o teto vale por canal e para o total.
- **`GET /pdv/config/credit-limit-cap`** → `{ "maxCreditLimit": 100.00 }`. O gerente (`RECEIVABLE_MANAGE`) recebe
  `null`: sem teto. Permissão para chamar: `PDV_SALE_MANAGE`, `RECEIVABLE_LIMIT_SET_CAPPED` ou `RECEIVABLE_MANAGE`.

**Novo, não estava no pedido:** **`PUT /pdv/config/credit-limit-cap`** `{ "maxCreditLimit": 150.00 }`, só com
`RECEIVABLE_MANAGE`, devolve o teto novo. Não havia tela nem rota para trocar os padrões do "Marcar" (o
`/system/config` só aceita chaves `auth.*`). Se a tela de configurações quiser o campo, é esta rota.

## 2. Atividades da sessão de caixa (PDV-F047)

- `POST /pdv/sessions/{id}/activities` `{ type, note? }` → `201 { id, type, note, username, createdAt }`.
  - Permissão: `PDV_SALE_MANAGE`.
  - Só na sessão **aberta** do próprio operador. Com `PDV_SESSION_CLOSE_ANY` (ADMIN/DEV), em qualquer sessão.
  - `OUTRO` sem `note`, ou `note` com mais de 280 caracteres → 400.
- `GET /pdv/sessions/{id}/activities`: mais recentes primeiro, sem paginação. Permissão: `PDV_READ` ou `ORDER_READ`.

⚠️ **Códigos diferentes do pedido:**
- sessão fechada → **`409 CASH_REGISTER_SESSION_CLOSED`**, e não `SESSION_CLOSED`. É o código que todas as escritas na sessão já usam;
- sessão de outro operador → **`403 SESSION_NOT_OWNED`**;
- sessão inexistente → `404 CASH_REGISTER_SESSION_NOT_FOUND`.

Com a rota no ar, a aba "Atividades" deixa de cair no "ainda não disponível". Isso acontece porque um 404 com `errorCode` é sessão inexistente, não rota ausente.

## 3. Estorno por item com destino (PED-F004)

`POST /orders/{id}/refund` aceita o contrato do pedido:

```json
{ "reason": "...", "restock": true, "itemLots": [],
  "items": [ { "sku": "SEDA-001", "quantity": 1, "destination": "DISCARD" } ] }
```

- **Sem `items`:** estorna tudo o que ainda não foi estornado. `restock: false` devolve só o dinheiro.
- **Com `items`:** estorna só essas unidades. O pedido continua `PAGO`/`CONCLUIDO` e vira `REEMBOLSADO` quando a última sai.
  Acima do que resta → **`422 REFUND_QUANTITY_EXCEEDED`**.
- **`refundedQuantity`** em cada item de `GET /orders/{id}` e da resposta do estorno. Nas listagens vem nulo. O
  próximo estorno aceita até `quantity − refundedQuantity`.
- **O estorno conta no caixa de quem estorna:** `payment-totals.refundedAmount` e esperado da gaveta em dinheiro.
  Sem caixa aberto, conta no caixa do pedido.
- **O vendedor** (`ORDER_REFUND_OWN_SESSION`) estorna só pedido da própria sessão de caixa **aberta**. Fora disso →
  **`403 REFUND_NOT_OWN_SESSION`**. O `allowedTransitions` do pedido parcialmente estornado continua com
  `REEMBOLSADO`, então o botão segue disponível.

⚠️ **Decisões que mudam o que a tela pode prometer:**
- **`DISCARD` não gera saída de estoque.** A unidade já saiu na venda; outra saída baixaria duas vezes. O saldo
  fica como está, e a perda é registrada no estorno, com o custo. Para a tela, `DISCARD` e `CUSTOMER_KEEPS` têm o
  mesmo efeito no estoque. A diferença é o registro de perda.
- **Pedido de mesa nunca volta ao estoque** (PED-C007). `RESTOCK` é gravado como `CUSTOMER_KEEPS`. Vale ocultar o
  `RESTOCK` em pedido de canal `MESA`.
- **Dinheiro:** é o líquido proporcional das unidades, rateado entre as formas de pagamento pelo peso de cada uma
  na venda. A parte paga no `MARCADO` **abate o marcado** em vez de devolver dinheiro. O último estorno devolve tudo
  o que restava, incluindo as taxas de serviço e de entrega.
- **Cashback:** o ganho de um item só é revertido quando a **última unidade** dele é estornada. O ledger não reverte
  parte de um lançamento.

## 4. Data de nascimento do cliente (CRM-F012)

- `dataNascimento` (`yyyy-MM-dd`) entra em três rotas e volta em `CustomerResponseDTO.dataNascimento`:
  - `POST /crm/customers`;
  - `PUT /crm/customers/{id}`: omitida, mantém a data gravada;
  - `POST /crm/customers/lead`: só completa cliente sem data e nunca sobrescreve.
- Menor de 18 anos → **`400 CUSTOMER_UNDERAGE`**. Data no futuro ou acima de 120 anos → 400.
- O lead de abrir mesa (`POST /pdv/comandas` com `lead`) **ainda não** aceita a data. Para gravar, a mesa usa o
  `POST /crm/customers/lead`, como o PDV já faz.

## Tabela de códigos novos

| HTTP | `errorCode` | Onde |
|---|---|---|
| 403 | `TAG_NOT_ALLOWED` | Tag que não é VIP, com só `CRM_CUSTOMER_VIP_MANAGE` |
| 422 | `CREDIT_LIMIT_ABOVE_CAP` | `PUT …/credit-limit` acima do teto do vendedor |
| 422 | `REFUND_QUANTITY_EXCEEDED` | `POST /orders/{id}/refund` com mais unidades do que restam |
| 403 | `REFUND_NOT_OWN_SESSION` | Estorno pelo vendedor de pedido de outra sessão, ou sem caixa aberto |
| 400 | `CUSTOMER_UNDERAGE` | Cadastro com menos de 18 anos |
