# Resposta do backend: Pedidos de clientes

> Resposta ao `frontend-admin/Docs/PROMPT_BACKEND_PEDIDOS_CLIENTES.md` (25/11/2026).
> O contrato do prompt foi mantido. As diferenças estão listadas abaixo. Depois do deploy, rode `generate:api` e ligue
> `PEDIDOS_CLIENTES_ENABLED`.

## 1. O que mudou em relação ao prompt (afeta o front)

| Tema | Prompt | Implementado |
|---|---|---|
| Corpo do erro | `code` | O campo se chama **`errorCode`** (`ApiError`: `message`, `errorCode`, `path`, `traceId`), igual ao resto da API. Os valores são os da seção 9 do prompt. |
| `GET /similar` | `description`, `brand`, `variation` | Também aceita **`sku`** (opcional). Um item aberto já vinculado a esse SKU vem primeiro, com `similarity: 1`. |
| `PATCH /{id}` `expectedAt` | data | Aceita `"YYYY-MM-DD"`, ou `""` para **limpar**. Ausente/`null` não muda. |
| Categoria no `PATCH /{id}` | — | `categoryId` limpa `categoryOther`, e `categoryOther` preenchido limpa `categoryId`. Mandar os dois dá 400. Hoje não há como deixar o item sem categoria do catálogo, só trocar por "outra". |
| `skippedNoConsent` | "sem consentimento" | O CRM **não tem campo de consentimento**. Conta como pulado: cliente avulso, cliente excluído (LGPD), cliente sem contato/e-mail, ou cliente fora do segmento de todas as automações ativas. |
| `summary.awaitingNotification` | — | Conta os **itens `ARRIVED`** com pelo menos uma solicitação ativa ainda sem aviso. `pending`, `ordered` e `arrived` contam itens por status. |
| `ProductRequestDetailDTO` | `extends ProductRequestSummaryDTO` | No OpenAPI são dois schemas independentes. Os campos são os mesmos, mais os do detalhe. |
| `notify` com `entryIds` | — | Só as solicitações **ativas** da lista entram. Uma que já foi avisada recebe de novo (reenvio intencional). Um `entryId` de outro item dá 404 `PRODUCT_REQUEST_NOT_FOUND`. |
| Solicitação de cliente do CRM | — | Não guarda nome/telefone avulsos: tudo vem do cadastro na leitura. Cliente excluído aparece como `"Cliente excluído"`, com `customerPhone: null`. |
| `customerPhone` com `CRM_CUSTOMER_READ` | — | Vem só com dígitos (`"85999991234"`), sem máscara. Sem a permissão, vem `"(85) 9****-1234"`. |

O resto (rotas, permissões por rota, tipos TS, regras de transição, códigos de erro) segue o prompt.

## 2. Permissões e papéis

- `ESTOQUE_PRODUCT_REQUEST_MANAGE` e `ESTOQUE_PRODUCT_REQUEST_CREATE`, em `V209__product_request_permissions.sql`.
- **ROLE_ADMIN** e **ROLE_DEV** recebem as duas.
- **ROLE_ATENDENTE** (o papel do caixa/PDV; não existe papel separado de caixa) recebe **CREATE**.
- Todo perfil que já tinha `ESTOQUE_REPLENISHMENT_MANAGE` (ex.: gerente criado pela tela de perfis) recebe as duas.
- `SeedConfig` e `DevRoleBootstrapConfig` também foram atualizados para o ambiente de dev.

Com **CREATE**, o usuário acessa `POST /`, `POST /{id}/entries`, `GET /similar` e `GET /by-customer/{id}`. As outras rotas, incluindo `GET /`, `GET /summary` e `GET /{id}`, exigem **MANAGE**.

## 3. Automação

- Novo evento: `AutomationEvent.PEDIDO_PRODUTO_CHEGOU` (com cliente). Adicione-o ao `AutomacaoEvento` em `crm.models.ts`.
- **Variáveis prontas** para esse evento: `cliente.*` (as de sempre), `loja.nome`, `produto.nome` (nome do produto vinculado, ou a descrição do pedido), `produto.marca`, `produto.variacao` e `pedidoProduto.quantidade`. Também saem `pedidoProduto.id`, `contexto.pedidoProdutoId` e `contexto.solicitacaoId`. Token sem valor continua saindo literal. Os blocos `produto` e `pedidoProduto` também vão no topo do payload do webhook.
- **Disparo automático:** acontece ao mudar para `ARRIVED`. Vai para cada solicitação ativa, ainda não avisada e de cliente do CRM. O cliente avulso fica para o botão manual, mas entra em `pendingNotifications`.
- `notified_at` só é marcado quando existe automação ativa do evento que aceita o estágio do cliente e o cliente tem contato. O envio sai depois do commit, em segundo plano (é a mesma entrega das outras automações). Uma falha de entrega aparece no log de campanhas, não na resposta.

## 4. Gatilho da seção 7 (entrada de estoque)

Está implementado e **desligado** por padrão: `estoque.product-requests.auto-arrive-on-stock-entry=false`.
Quando ligado, qualquer ENTRADA de estoque (compra, ajuste, transferência, devolução) num SKU vinculado a um pedido `ORDERED` move o pedido para `ARRIVED`. Isso vale também para uma variação quando o pedido está vinculado ao SKU do pai. O ator registrado é `"sistema"`, e o disparo da seção 3 acontece em seguida.

## 5. Decisões internas (não mudam o contrato)

- Sem `pg_trgm` e sem `unaccent` no banco. A similaridade (trigramas + Jaccard, a mesma conta do `similarity()` do Postgres, limiar 0.35) e a normalização rodam no Java sobre os itens abertos. Em dev o Flyway fica desligado, então as extensões não existiriam lá.
- A categoria referencia `product_category` (não `categories`).
- `payload` do histórico é TEXT com JSON (não JSONB).
