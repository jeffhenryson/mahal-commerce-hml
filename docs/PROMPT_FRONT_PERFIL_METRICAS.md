# Prompt para o frontend-admin: métricas do perfil e da ficha do usuário (07/11/2026)

> Resposta do backend ao `frontend-admin/Docs/PROMPT_BACKEND_PERFIL_METRICAS.md`. Este arquivo é
> autocontido. Os formatos de resposta seguem aquele documento, com as diferenças marcadas com ⚠️.

Branch do backend: `feat/pdv-caixa-sessao-ajustes`. Migration: **V190**. Erros no formato de
sempre: `{ message, errorCode, timestamp, path, traceId }`.

## 1. O que liga a flag

Com o backend no ar:
1. `npm run generate:api`
2. Trocar os modelos escritos à mão de `usuario-metricas.models.ts` pelos gerados. No spec, os
   schemas vêm como `Performance`, `CashSessionItem`, `AccessLogItem`, `Attendance`, `CustomerOrders`
   e as respectivas páginas `PageResult…`.
3. `PERFIL_METRICAS_ENABLED = true`.

## 2. Endpoints e permissões

| Endpoint | Forma `me` | Forma `{id}` (ficha do admin) |
| --- | --- | --- |
| `GET /users/{me\|id}/performance?from&to` | autenticado | `USER_READ` + (`ORDER_READ` ou `PDV_READ`) |
| `GET /users/{me\|id}/cash-sessions?page&size` | autenticado | `USER_READ` + **`PDV_SESSION_READ_ANY`** ⚠️ |
| `GET /users/{me\|id}/access-log?page&size&from&to` | autenticado | `USER_READ` + `AUDIT_READ` |
| `GET /users/{me\|id}/attendance?from&to` | autenticado | `USER_READ` + `AUDIT_READ` |
| `GET /users/{me\|id}/customer-orders?from&to&page&size` | autenticado | `USER_READ` + `ORDER_READ` |

⚠️ **`cash-sessions` na ficha exige `PDV_SESSION_READ_ANY`, não `PDV_READ`.** É a mesma regra de
`GET /pdv/sessions/{id}` desde a V163: o atendente tem `PDV_READ` e não deve ver o caixa dos
colegas. Sem a permissão, a resposta é 403, e o front já mostra "sem permissão" nesse bloco.

**Erros:**
- `403`: falta permissão. Vem antes do 404.
- `403 TARGET_PROTECTED`: conta de dev vista por quem não é dev. Tratar como "sem permissão".
- `404 USER_NOT_FOUND`: o `{id}` não existe.
- `404 USER_CUSTOMER_NOT_LINKED`: só em `customer-orders`, quando o usuário não tem cliente
  vinculado. Mostrar "nenhum cadastro de cliente vinculado".
- `400 INVALID_REPORT_PERIOD`: `from`/`to` ausentes onde são obrigatórios, `from > to` ou período
  acima de 366 dias.
- `400`: `page < 0`, `size < 1` ou `size > 100`.

**Período:**
- `from` e `to` são obrigatórios em `performance`, `attendance` e `customer-orders`.
- Em `access-log` são opcionais.

## 3. Diferenças e regras que o front precisa saber

- ⚠️ **Paginação:** além de `content` e `totalElements`, a resposta traz `page`, `size` e
  `totalPages` (o `PageResult` padrão). Os campos extras podem ser ignorados.
- **Quem é o usuário em cada número.**
  - A venda (`pdv`) é atribuída ao **dono do caixa** em que ela caiu, com a mesma regra de
    `GET /orders/analytics/operators`. Uma mesa fechada no caixa de A conta para A, mesmo que outra
    pessoa tenha atendido.
  - `tables.itemsLaunched` conta as linhas que o usuário lançou em **qualquer** comanda.
- ⚠️ **`itemsLaunched` só conta a partir do deploy da V190.** O autor do item não era gravado antes,
  então períodos anteriores vêm com `0`. Na comparação com o mês anterior, isso aparece como
  crescimento. Até virar o mês, vale mostrar "sem base de comparação" nesse indicador.
- ⚠️ **`userAgent` é `null` nos acessos anteriores ao deploy.** Mostrar "—".
- **`cash.totalSold`** é o total recebido das sessões fechadas no período, igual ao resumo de
  fechamento. Na lista de `cash-sessions`, o caixa aberto vem com `totalSold` parcial e
  `difference: null`.
- **Frequência (`attendance`):**
  - `date` é o dia em São Paulo, então um login às 23h30 conta no próprio dia.
  - `lastLogoutAt` é o último logout **do mesmo dia local**. Num turno que vira a noite (por exemplo,
    das 18h às 02h), o dia aparece com `lastLogoutAt: null`, e o logout das 02h não aparece. Isso é
    o comportamento pedido na especificação. Sugestão de texto: "sem logout no dia".
- **`access-log`:** inclui `LOGIN_FAILED` com o username **digitado**, então tentativas de outra
  pessoa com o login do usuário também aparecem. Ordem: mais recentes primeiro.
- ⚠️ **`customer-orders`:**
  - `total` e `summary.totalSpent` são o **líquido** do pedido (sem taxa de serviço e entrega), o
    mesmo número da ficha do cliente no CRM.
  - `summary.orders` e `totalSpent` contam só pedidos pagos.
  - A página lista **todos** os status, inclusive cancelados e aguardando pagamento.
  - `lastOrderAt` é o último pedido em qualquer data.
- **`GET /users/me`** (e a resposta de `PATCH /users/me`) passa a trazer `customerId` e
  `customerName`. Os dois são `null` sem vínculo. É isso que mostra a aba Compras.

## 4. Checklist de teste no front

- Atendente sem permissão nenhuma: as três abas do próprio perfil carregam.
- Ficha de outro usuário com um admin sem `PDV_SESSION_READ_ANY`: só o bloco de caixas mostra
  "sem permissão". O resto carrega.
- Usuário sem cliente vinculado: a aba Compras não aparece no perfil. Na ficha, aparece a mensagem do 404.
- Período de mais de 366 dias: a mensagem de erro do período aparece, sem tela quebrada.
