# Prompt para o mahal-admin: caixa sem corte de meia-noite, total vendido, mesa com nome do lead e as 3 formas de cobrar a sessão (05/10/2026)

Backend na branch `feat/pdv-caixa-sessao-ajustes` (PDV-F037..F040). Nenhuma migration. Erros no formato
de sempre: `{ errorCode, message }`.

## 1. VENDAS › Mesas: histórico das mesas que saíram

O backend já entrega tudo. O passo a passo está em `docs/PROMPT_FRONT_MESAS_HISTORICO.md` §1. Resumo:

- Lista: `GET /pdv/comandas/history?from&to&status&customerId&openedBy&closedBy&tableLabel&warehouseCode&boughtInStore&page&size`
  (`PDV_READ` ou `ORDER_READ`). Traz mesas `FECHADA` e `CANCELADA`.
- **Cliente cadastrado × avulsa:** se `customerId` vier preenchido, mostrar o nome (`customerName`) com
  link para o cliente no CRM. Se vier nulo, mostrar o selo "Avulsa".
- Detalhe: `GET /pdv/comandas/{id}`. Mostrar a abertura (`openedAt`/`openedBy`), o encerramento
  (`closedAt`/`closedBy`) e, para cada sessão em `sessions[]`, a linha do tempo: `lancadaEm` → `inicioEm` →
  **entregue** (`entregueEm`) → **retirada** (`recolhidaEm`), com `esperaMin`/`preparoMin`/`naMesaMin`/`totalMin`.
  Mostrar também os pedidos (`orders`) que cobraram a mesa.

## 2. PDV › Caixa: sem corte de meia-noite, com aviso de 12h (PDV-F037)

- O caixa **não fecha mais à meia-noite**. O 409 `SESSION_STALE` deixou de existir: remover o tratamento
  e qualquer fechamento ou bloqueio automático no front. Quem fecha o caixa é o usuário que o abriu.
- A sessão (`GET /pdv/sessions/current`, `GET /pdv/sessions/{id}`) traz:
  - `horasAberto` (número): mostrar no cabeçalho do caixa, por exemplo "Aberto há 13h".
  - `fechamentoSugerido` (boolean): quando `true`, mostrar um banner que dá para dispensar: "Seu caixa está
    aberto há mais de 12h. Para um controle melhor por turno, feche este caixa e abra outro." Com botão
    "Fechar caixa". **Não bloquear nada.** Reconsultar a sessão periodicamente (ex.: a cada 5 min) ou ao
    voltar para a tela.

## 3. PDV › Fechamento de caixa: total vendido (PDV-F038)

Antes de pedir o valor contado, chamar `GET /pdv/sessions/{id}/summary` (`PDV_READ`):

```json
{ "totals": [ { "method": "DINHEIRO", "amount": 300.00, "refundedAmount": 0, "changeAmount": 12.00,
                "receivableReceived": 0, "netAmount": 288.00 }, ... ],
  "totalReceived": 1250.00,
  "totalOnAccount": 90.00 }
```

- Destaque: **"Total vendido: R$ totalReceived"** (soma de todas as formas de pagamento).
- Abaixo: uma linha por forma com `netAmount` (Dinheiro, Débito, Crédito, PIX), escondendo as zeradas.
- À parte: "Marcado (a receber): R$ totalOnAccount", **fora** do total.
- Continua igual: só o dinheiro é conferido contra o contado (`expectedAmount` no retorno do close).
- O mesmo resumo pode aparecer no detalhe de um caixa já fechado.

## 4. PDV › Nova mesa com lead (PDV-F039)

`POST /pdv/comandas?sessionId=` com `customerId` ou `lead` agora aceita `tableOrCustomerLabel` vazio: a
mesa é criada com o **nome do cliente/lead**.
- Ao selecionar o lead/cliente no modal de nova mesa, preencher o campo "Mesa" com o nome dele. O
  operador pode editar, e o que ele digitar vale.
- Mesa avulsa (sem cliente) continua exigindo o rótulo (400).

## 5. PDV › Resumo da mesa: as 3 formas de cobrar a sessão (PDV-F040)

No resumo da mesa e ao lançar a sessão, oferecer três botões:

| Botão | O que o front faz | Efeito |
|---|---|---|
| **Pagar no início** | `POST /pdv/comandas/{id}/sessoes` (sem `pagarNoFinal`) e depois `POST /pdv/comandas/{id}/close` com `itemIds: [sessão (+ roshs ligados)]` e os pagamentos | Como hoje: a sessão vai ao preparo depois de paga. |
| **Pagar no final** | `POST .../sessoes` com `pagarNoFinal: true` (exige `PDV_SESSION_PAY_LATER`: admin e dev; sem ela, esconder o botão — a API responde 403 `SESSION_PAY_LATER_NOT_ALLOWED`) | A sessão vai direto ao preparo, a receber. **Ao recolher, o pagamento tem que ser registrado.** |
| **Marcar sessão** | Lança a sessão e fecha a linha com `itemIds` e um pagamento `{ "method": "MARCADO", "amount": <valor>, "dueDate": "AAAA-MM-DD" }` | Fica no fiado do cliente para pagar outro dia (CRM-F010). A sessão vai ao preparo na hora. |

Regras de "Marcar":
- A mesa precisa de cliente vinculado (400 `CUSTOMER_REQUIRED_FOR_ON_ACCOUNT`). Se não tiver, abrir o
  vínculo de cliente antes.
- Exige `PDV_SALE_ON_ACCOUNT` (403 `ON_ACCOUNT_NOT_ALLOWED`), cliente VIP (403 `CUSTOMER_NOT_ELIGIBLE`),
  nenhum vencido (409 `CUSTOMER_HAS_OVERDUE`) e limite disponível (409 `CREDIT_LIMIT_EXCEEDED`). Mostrar
  as mensagens.
- Pedir o vencimento (sugestão: hoje + prazo padrão do CRM).

Regras de "Pagar no final" ao **recolher**:
- Se a linha tem `pagarNoFinal: true` e ainda está aberta (sem `closedInOrderId`), o botão "Recolher" deve
  abrir **o modal de pagamento da sessão** (fechamento parcial com `itemIds` da sessão e dos roshs
  ligados, incluindo a opção MARCADO). Só depois de pago chamar `PATCH /pdv/comandas/{id}/sessoes/{itemId}/status`
  com `RECOLHIDO`.
- Se o front tentar recolher sem pagar, a API responde **409 `SESSION_NOT_PAID_FOR_COLLECT`**. Tratar
  abrindo o modal de pagamento.
- Desistência sem pagamento: remover a linha (`DELETE /pdv/comandas/{id}/items/{itemId}`), não recolher.
- Depois que todas as sessões foram recolhidas e pagas, encerrar a mesa com `POST /pdv/comandas/{id}/finish`.
