# Prompt para o mahal-admin: ajustes de Vendas, Marcados e PDV (01/10/2026)

O backend desta rodada está no branch `feat/vendas-crm-pdv` do `mahal-backend`. O que segue é
o que falta no front. Cada item diz o que o backend já entrega.

---

## 1. Nomes dos módulos (menu)

Em `src/app/core/layout/app-modules.ts`, renomear os rótulos, em caixa alta:

| Hoje | Novo |
|---|---|
| CRM & Clientes | **CRM** |
| Estoque & Inventário | **ESTOQUE** |
| Vendas & E-commerce | **VENDAS** |
| Compras & Fornecedores | **COMPRAS** |
| Financeiro | **FINANCEIRO** |

Só muda o rótulo. Rotas e permissões ficam como estão. Atualizar os testes e tours que procuram
os nomes antigos (`grep -rn "CRM & Clientes\|Estoque & Inventário\|Vendas & E-commerce\|Compras & Fornecedores" src e2e`).

## 2. VENDAS › Pedidos: quem fez a venda

**Backend pronto (PED-F003).** `GET /orders` e `GET /orders/{id}` trazem `operatorName`, o
usuário (funcionário ou administrador) que operava o caixa do pedido: quem vendeu no PDV ou
fechou a mesa.

- É o `username`, porque usuário não tem nome completo no sistema.
- Vem `null` em pedido do app ainda não pago na loja. Mostrar "—" nesse caso.

No front:
- adicionar a coluna **"Vendido por"** na lista (tabela no desktop, linha secundária no card mobile);
- mostrar o mesmo dado no detalhe do pedido;
- regenerar o cliente OpenAPI (`ng-openapi-gen`) para o campo entrar em `OrderAdminResponseDto`.

## 3. VENDAS › Reservas: cards no mobile

Só front. Em telas estreitas, trocar a tabela por cards com:
- número do pedido;
- cliente;
- itens (resumo);
- total;
- data da reserva (`reservedAt`);
- forma de entrega/retirada;
- botão **"Finalizar reserva"**.

O botão usa o endpoint que já existe: `POST /orders/{id}/status {status: "CONCLUIDO"}`
(`RESERVADO → CONCLUIDO`, PDV-F008). Para vários de uma vez existe `POST /orders/bulk-status`.

## 4. Nova aba VENDAS › Marcados (sai do CRM)

Mover a tela `features/crm/marcados` (lista de marcados) do CRM para **VENDAS › Marcados**:
- rota `/app/vendas/marcados`, mesmo `MARCAR_ENABLED` e permissão `RECEIVABLE_READ`;
- remover a entrada "Marcados" do CRM;
- redirecionar `/app/crm/marcados` para a rota nova, para não quebrar links salvos;
- atualizar o texto do PDV que diz "aparecem em CRM › Marcados" (`pdv-pagamento.component.ts`).

**Backend pronto:** `GET /receivables` traz, por pedido marcado:
- cliente: `customerName`;
- pedido: `orderNumber`, ou `tableLabel` quando veio de mesa;
- produtos: `items[]`, com `productName`, `quantity` e `subtotal`;
- valores e situação: `amount`, `amountOpen`, `dueDate` (data de pagamento), `status` e `daysOverdue`;
- quem lançou: `createdBy`.

Filtros: `status`, `overdue`, `dueFrom`, `dueTo`, `customerId`. A ordem é pelo vencimento mais
próximo.

A regra de quem pode marcar **não muda**: só cliente cadastrado, VIP, sem atraso e dentro do limite.

## 5. PDV: busca de cliente com "Erro no servidor"

**Corrigido no backend (CRM-C008).** Toda busca por nome ou e-mail (sem dígitos) em
`GET /crm/customers?search=` dava 500 no Postgres. Não há mudança de contrato.

Depois do deploy, conferir no PDV a busca da etapa 1 e a do "Abrir comanda".

## 6. PDV: venda anônima não pode ser marcada

O backend recusa marcar sem cliente (`CUSTOMER_REQUIRED_FOR_ON_ACCOUNT`). O furo está no front:
em `pdv.component.ts`, no envio da venda (por volta da linha 4296):

```ts
let customerId = this.selectedCrmClienteId() ?? undefined;
```

Isso **ignora `vendaAnonima()`**. Se o operador escolhe um cliente e depois liga "venda anônima",
a venda sai vinculada ao cliente, e com ele pode sair marcada.

Corrigir assim:
- com `vendaAnonima()` ligada, mandar `customerId` indefinido;
- ao ligar "venda anônima", limpar `selectedCrmClienteId` e o cliente selecionado. Assim o
  `contextoMarcar` e o método "Marcar" somem também no pagamento;
- se o método escolhido era `marcar`, soltá-lo (o efeito em `pdv-pagamento` já faz isso quando
  `marcar` vira `null`);
- teste: escolher cliente VIP → ligar venda anônima → ir ao pagamento → "Marcar" não aparece e o
  `POST` sai sem `customerId`.

Fazer o mesmo na mesa: comanda sem cliente não oferece "Marcar" no fechamento.

## 7. Mesas e pedidos: mudanças de contrato do backend (branch `fix/pdv-mesas-pedido`)

Nenhum campo saiu nem mudou de tipo. O que muda são recusas novas e dois comportamentos.

**Mesa (`/pdv/comandas`)**

| Ação | Antes | Agora | O que a tela faz |
|---|---|---|---|
| `POST /{id}/cancel` em mesa com linha já paga (sessão paga no lançamento) | cancelava | `409 COMANDA_PARTIALLY_CLOSED` | Esconder/desabilitar "Cancelar mesa" quando alguma linha tem `closedInOrderId`; oferecer remover as abertas e "Encerrar" (`/finish`) |
| `DELETE /{id}/items/{itemId}` de linha já paga | removia | `400 ITEM_NOT_OPEN_IN_COMANDA` | Sem botão de remover em linha paga; desfazer venda paga é reembolso do pedido |
| `POST /{id}/merge-into/{targetId}` com origem já paga | `409 COMANDA_PARTIALLY_CLOSED` | **junta**: vão as linhas abertas e as sessões ainda no salão (com os utensílios); a origem fica `FECHADA` | Liberar "Juntar mesas" para mesa paga; tirar o tratamento do 409 |
| `PATCH .../sessoes/{itemId}/status` `NA_FILA → PREPARANDO` sem pagamento | passava | `409 SESSION_NOT_PAID` | Botão "Preparar" do 2º rosh só com a sessão paga; rosh cobrado só depois de cobrado |
| `POST /{id}/sessoes` e `/repetir` com `modo: "DUPLO"` | `PDV_COMANDA_MANAGE` | também `PDV_COMANDA_COURTESY` → senão `403 COURTESY_NOT_ALLOWED` | Esconder "Duplo" sem a permissão. ⚠️ Hoje só ADMIN tem `PDV_COMANDA_COURTESY`: o atendente para de lançar duplo até o dono conceder a permissão ao perfil |
| Essência em branco / duplo sem `essenciaRosh` | `400 BAD_REQUEST` | `400 SESSION_ESSENCE_REQUIRED` | Mensagem própria no formulário |
| Modo de sessão do cardápio em `/items` | `400 BAD_REQUEST` | `400 MENU_SESSION_NOT_ALLOWED_ON_ITEMS` | — (erro de programação) |
| `PUT /pdv/sessao/utensilios/{id}` (e faixas/adicionais) sem `quantidadeTotal`/`incluso`/`ordem` | zerava o campo | mantém o valor atual | Pode mandar só o que mudou |

Comportamento novo sem ação obrigatória: remover o último rosh ativo de uma sessão já recolhida libera o
vaso; a varredura das 5h (agora no horário de São Paulo) encerra sozinha a mesa toda paga que ninguém
encerrou (`closedBy = "system"`, motivo "Mesa paga encerrada (varredura automática)").

**Pedidos (`/orders`)**

- `POST /orders/{id}/status` e `/orders/bulk-status` só aceitam `SEPARADO`/`ENVIADO`/`ENTREGUE` e a
  retirada `RESERVADO → CONCLUIDO`. Outro destino é `409 INVALID_STATUS_TRANSITION` (no bulk, vai para
  `failed[]`). Novo `code` possível em `failed[]`: `INVALID_ORDER_STATE`.
- Reembolso de pedido de mesa não devolve nada ao estoque (só pagamento e cashback).
