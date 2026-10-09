# Prompt para o frontend-admin: venda offline no balcão, fase 2 (09/10/2026)

O backend da fase 1 está pronto na branch `feat/pdv-caixa-sessao-ajustes` (PDV-F043, migration **V150**).
Esta fase é toda do frontend: o PDV de balcão continua vendendo sem rede e sincroniza quando a conexão
volta. Erros no formato de sempre: `{ errorCode, message }`.

**Primeiro passo:** sincronizar o OpenAPI para regenerar o client.

## Escopo (decidido com o dono)

- **Só a venda de balcão** funciona offline.
- Mesa, Pix por gateway, venda marcada (fiado), cadastro e relatórios continuam exigindo conexão.
- **Formas de pagamento offline:** só `DINHEIRO`, `DEBITO` e `CREDITO` (maquininha). Esconder PIX e "Marcar" quando estiver sem rede.
- **O caixa precisa estar aberto antes de cair a rede.** Não se abre caixa offline.
- **O caixa não fecha enquanto houver venda na fila local.** O backend também recusa o fechamento enquanto houver venda recusada esperando revisão (§4).

## 1. Chave por venda, já, também online

Toda venda passa a levar um `clientSaleId`, que é um UUID (`crypto.randomUUID()`) **gerado quando a venda é montada**:

```json
POST /pdv/sessions/{id}/sales
{ "items": [...], "payments": [...], "clientSaleId": "3f1c9a2e-7b4d-4c1e-9a8f-2d6b5e0c1a7b" }
```

- Reenviar com a **mesma** chave (timeout, duplo clique, retry automático) responde **`200`** com a venda que já existia, e não registra outra. A venda nova continua respondendo `201`.
- `409 DUPLICATE_CLIENT_SALE` aparece se dois envios chegarem ao mesmo tempo. Tratar como "já registrada" e buscar a venda.
- **A chave é gerada uma vez por venda**: guardar junto com o carrinho e só trocar depois que a venda for confirmada.

Isso já resolve a venda duplicada por reenvio, mesmo com rede.

## 2. Funcionar sem rede (PWA)

- **Service worker** com cache do app (Angular PWA / `ngsw`).
- **Cache do catálogo do balcão**, atualizado enquanto há rede (ex.: ao abrir o caixa e a cada N minutos). O cache inclui produtos visíveis no PDV com preço, variações e o `parentSellable` (EST-F036, para não oferecer o produto base). Também entra a central de cigarros (`GET /pdv/cigarros`).
  - Mostrar "catálogo de HH:MM" quando estiver offline.
  - O **preço cobrado** na sincronização é o **atual do servidor**, não o do cache. Se mudou, o pedido sai com o preço novo; avisar isso na tela de sincronização.
- **Detecção de rede:** `navigator.onLine` mais uma falha de chamada (timeout ou erro de rede, sem resposta HTTP). Uma resposta 4xx/5xx **não** é offline.
- **Fila local em IndexedDB**: cada venda offline é gravada com `{ clientSaleId, soldAt (ISO, hora do terminal), customerId?, items, payments }` e o `sessionId` do caixa.
  - Comprovante local: mostrar "venda registrada offline, sincroniza quando a rede voltar".
  - Contador visível no cabeçalho do PDV, por exemplo "3 vendas a sincronizar".

## 3. Sincronizar

`POST /pdv/sessions/{id}/sales/sync` (`PDV_SALE_MANAGE`), com **até 50 vendas por chamada**. Filas maiores vão em lotes.

```json
{ "sales": [ { "clientSaleId": "…", "soldAt": "2026-10-09T15:00:00Z", "customerId": null,
               "items": [ { "sku": "LM-AZUL-MACO", "quantity": 1 } ],
               "payments": [ { "method": "DINHEIRO", "amount": 12.00 } ] } ] }
```

A resposta traz um resultado por venda, na ordem do lote:

| `status` | O que fazer na fila |
|---|---|
| `SYNCED` | Remover da fila. `orderId` é o pedido. |
| `DUPLICATE` | Já estava registrada (reenvio). Remover da fila. |
| `REJECTED` | **Remover da fila local**: ela está guardada no servidor para revisão (`rejectionId`, `errorCode`, `message`). Avisar o operador e o gerente (§4). |

- **Quando sincronizar:** ao voltar a rede, ao abrir a tela do caixa e antes de fechar o caixa.
- **Reenviar é seguro.** Se a chamada cair no meio, mandar o mesmo lote de novo: o que entrou volta `DUPLICATE`, o que foi recusado volta a mesma recusa.
- **Erros do lote inteiro** (nada entrou, a fila fica como está):
  - `400 OFFLINE_PAYMENT_NOT_ALLOWED`: há PIX ou marcado na fila. Não deveria acontecer se o Escopo for seguido.
  - `400 OFFLINE_SOLD_AT_OUT_OF_WINDOW`: `soldAt` é anterior à abertura do caixa ou está no futuro (relógio do terminal errado).
  - `403`: o caixa é de outro operador, ou há desconto sem `PDV_SALE_DISCOUNT`.
  - `409 CASH_REGISTER_SESSION_CLOSED`: o caixa foi fechado. Não deveria acontecer se §5 for seguido. A fila precisa de intervenção manual.

## 4. Revisão das vendas recusadas (tela do gerente)

As recusadas mais comuns são falta de estoque (`INSUFFICIENT_STOCK`), produto que sumiu ou foi desativado, produto sem preço e `PARENT_NOT_SELLABLE`.

- **Lista:** `GET /pdv/sessions/{id}/offline-rejections` (`PDV_READ`). Pendente é `resolution: null`. Mostrar os itens, os pagamentos, o motivo (`errorCode`/`message`) e a hora do balcão (`clientSoldAt`).
- **Reenviar:** `POST /pdv/offline-rejections/{id}/retry` (`PDV_OFFLINE_REVIEW`), depois de acertar a causa (ex.: dar entrada no estoque).
  - Volta `RETRIED` com `orderId` quando entra.
  - Volta pendente com o `errorCode` novo quando falha de novo.
- **Descartar:** `POST /pdv/offline-rejections/{id}/discard` com `{ "reason": "…" }` obrigatório (`PDV_OFFLINE_REVIEW`). A venda não entra, e o dinheiro é acertado fora do sistema. Pedir confirmação.
- `409 OFFLINE_REJECTION_ALREADY_RESOLVED`: outra pessoa já resolveu. Recarregar.
- **Quem vê os botões:** só quem tem `PDV_OFFLINE_REVIEW` (hoje admin). O atendente vê a lista e o aviso, mas não decide.

## 5. Fechamento do caixa

- **Bloquear no front:** com fila local não vazia, o botão "Fechar caixa" fica desabilitado, com a mensagem "sincronize as N vendas offline antes de fechar".
- **Bloqueio no servidor:** `409 SESSION_HAS_PENDING_OFFLINE_SALES` enquanto houver recusada pendente. Mostrar a lista do §4 com o caminho para resolver.

## Códigos novos

| errorCode | Status | Onde |
|---|---|---|
| `DUPLICATE_CLIENT_SALE` | 409 | venda online, envio simultâneo da mesma chave |
| `OFFLINE_PAYMENT_NOT_ALLOWED` | 400 | sync |
| `OFFLINE_SOLD_AT_OUT_OF_WINDOW` | 400 | sync |
| `SESSION_HAS_PENDING_OFFLINE_SALES` | 409 | fechar caixa |
| `OFFLINE_REJECTION_NOT_FOUND` | 404 | retry / discard |
| `OFFLINE_REJECTION_ALREADY_RESOLVED` | 409 | retry / discard |

## Checklist

- [ ] Sincronizar o OpenAPI.
- [ ] `clientSaleId` (UUID) em toda venda, gerado uma vez por carrinho, e tratar `200` no reenvio (§1).
- [ ] Service worker e cache do catálogo do balcão com "catálogo de HH:MM" (§2).
- [ ] Fila em IndexedDB, comprovante local e contador "N a sincronizar" (§2).
- [ ] Esconder PIX e "Marcar" offline (Escopo).
- [ ] Sincronização em lotes de 50, ao voltar a rede, ao abrir e antes de fechar o caixa (§3).
- [ ] Tela de revisão das recusadas com reenviar e descartar (§4).
- [ ] Fechamento bloqueado com fila local e tratamento do `409 SESSION_HAS_PENDING_OFFLINE_SALES` (§5).
