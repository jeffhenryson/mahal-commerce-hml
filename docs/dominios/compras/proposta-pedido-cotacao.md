# Proposta de contrato: pedido de compra e cotação (05/11/2026)

**Status:** proposta para aprovação. Nada aqui está implementado. Pedido do front: item 13 de
`frontend-admin/Docs/PROMPT_BACKEND_PENDENCIAS.md`. Base: `frontend-admin/Docs/MODULO_COMPRAS.md`.

Hoje existem fornecedores (`/compras/suppliers`), recebimento (`POST /compras/goods-receipts`) e importação de NF-e. As
telas de pedido e cotação do admin rodam com mocks.

## Decisões a confirmar

| # | Decisão | Proposta |
|---|---|---|
| 1 | O pedido tem itens por SKU? | Sim. A tela hoje pede só quantidade e valor total, mas sem itens não dá para ligar o recebimento ao pedido |
| 2 | Recebimento parcial | Sim: o recebimento informa o pedido, e o saldo de cada item cai |
| 3 | Pedido enviado ao fornecedor | Só muda o status. Enviar por e-mail ou WhatsApp fica para depois |
| 4 | Cotação | Preço digitado pelo operador. A resposta automática da tela é só demonstração |
| 5 | Depósito | Cada pedido nasce para um depósito, que é onde o recebimento dá entrada |

## Pedido de compra

```
RASCUNHO → ENVIADO → PARCIALMENTE_RECEBIDO → RECEBIDO
RASCUNHO | ENVIADO → CANCELADO
```

`PARCIALMENTE_RECEBIDO` não cancela: o que já entrou no estoque não volta por aqui. Para encerrar um pedido que não vai
chegar inteiro, a proposta é `POST .../close`, que leva a `RECEBIDO` com o saldo em aberto registrado.

| Método e rota | Permissão | Nota |
|---|---|---|
| `GET /compras/purchase-orders?status=&supplierId=&from=&to=&page=&size=` | `COMPRAS_READ` | Paginado |
| `GET /compras/purchase-orders/{id}` | `COMPRAS_READ` | Com itens e recebimentos |
| `POST /compras/purchase-orders` | `COMPRAS_ORDER_MANAGE` (nova) | Nasce `RASCUNHO` |
| `PUT /compras/purchase-orders/{id}` | `COMPRAS_ORDER_MANAGE` | Só em `RASCUNHO` |
| `POST /compras/purchase-orders/{id}/send` | `COMPRAS_ORDER_MANAGE` | |
| `POST /compras/purchase-orders/{id}/cancel` | `COMPRAS_ORDER_MANAGE` | `{ reason }` |
| `POST /compras/purchase-orders/{id}/close` | `COMPRAS_ORDER_MANAGE` | `{ reason }` |

```json
{
  "supplierId": 3,
  "warehouseCode": "LOJA-01",
  "expectedAt": "2026-11-20",
  "notes": "Entregar pela manhã",
  "items": [{ "sku": "ZOMO-50G-MENTA", "quantity": 24, "unitCost": 18.50 }]
}
```

A resposta acrescenta `id`, `number`, `status`, `totalAmount`, `items[].receivedQuantity`, `createdBy` e `createdAt`.

**Ligação com o recebimento:** `POST /compras/goods-receipts` e o `nfe-confirm` aceitam `purchaseOrderId` opcional. O
recebimento sobe `receivedQuantity` e move o status. Receber acima do pedido é aceito, com aviso na resposta, porque a
nota do fornecedor manda.

**Erros:** `404 PURCHASE_ORDER_NOT_FOUND`, `409 PURCHASE_ORDER_INVALID_STATUS`, `400 PURCHASE_ORDER_EMPTY`.

## Cotação

```
ABERTA → RESPONDIDA → CONVERTIDA
ABERTA | RESPONDIDA → CANCELADA
```

| Método e rota | Permissão | Nota |
|---|---|---|
| `GET /compras/quotations?status=&sku=&page=&size=` | `COMPRAS_READ` | |
| `POST /compras/quotations` | `COMPRAS_ORDER_MANAGE` | `{ sku, quantity, supplierIds[] }` |
| `PUT /compras/quotations/{id}/responses/{supplierId}` | `COMPRAS_ORDER_MANAGE` | `{ unitCost, leadTimeDays, notes }` |
| `POST /compras/quotations/{id}/convert` | `COMPRAS_ORDER_MANAGE` | `{ supplierId }`: cria o pedido em `RASCUNHO` |
| `POST /compras/quotations/{id}/cancel` | `COMPRAS_ORDER_MANAGE` | |

A primeira resposta leva a `RESPONDIDA`. `GET` devolve `bestResponse`, a de menor `unitCost`.

## Tabelas

`purchase_order`, `purchase_order_item`, `quotation`, `quotation_supplier` (uma linha por fornecedor consultado, com a
resposta) e `purchase_order_id` em `goods_receipt`.
