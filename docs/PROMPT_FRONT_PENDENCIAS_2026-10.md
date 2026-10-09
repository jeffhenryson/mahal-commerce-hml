# Prompt para o frontend-admin: tudo o que o backend entrega e o front ainda não usa (09/10/2026)

Backend na branch `feat/pdv-caixa-sessao-ajustes`, migrations até **V150**. Front de referência:
`frontend-admin` no commit `6ac5511` (07/10/2026). Este documento lista **só o que falta**. A lista
saiu de um grep no `frontend-admin/src` contra cada contrato; o que já está implementado ficou de fora.

**Como usar:** uma parte por vez, cada uma com o seu commit e os seus testes. As partes são
independentes, salvo as dependências marcadas. Quando uma parte já tem prompt detalhado, o
documento aponta para ele, e o essencial do contrato está repetido aqui.

Os erros vêm no formato de sempre, `{ errorCode, message }`. Os paths abaixo são relativos a
`frontend-admin/src/app/`.

## Índice

| Parte | Assunto | Prioridade | Depende de |
|---|---|---|---|
| 0 | Preparação: client, permissões, códigos de erro | antes de tudo | — |
| 1 | **Correções que já quebram a tela** | 🔴 urgente | 0 |
| 2 | Regras da mesa que ficaram faltando | 🔴 | 0 |
| 3 | "Comprou na loja?" e indicadores das mesas | 🟡 | 0 |
| 4 | Vendas e caixa: filtros no servidor, correção de pagamento, comprovante por e-mail | 🟡 | 0 |
| 5 | Estoque: latas abertas, embalagem, produto base | 🔴 | 0 |
| 6 | Mesa: essência escolhida do catálogo | 🔴 | 5 |
| 7 | PDV: central de cigarros | 🔴 | 5 |
| 8 | Kit montável cadastrado no backend | 🟢 | 0 |
| 9 | Convite de usuário e auth | 🟡 | 0 |
| 10 | Venda offline | 🟡 (1ª etapa) / 🟢 (2ª) | 0 |
| 11 | Ajustes visuais | 🟢 | — |

---

## Parte 0 — Preparação

1. **Regenerar o client.** O `openapi.json` local é de julho, e vários campos novos não existem nos
   models (por exemplo `essenciaSku` e `parentSellable`).
   - `npm run fetch:spec`, com o backend rodando em `BACKEND_URL` (padrão `localhost:8080`), e depois `npm run generate:api`.
   - Depois de regenerar, trocar pelos models gerados o que hoje é montado à mão (por exemplo os corpos de sessão em `features/pdv/sessao-mesa.models.ts` e `features/pdv/entrega.models.ts`).
2. **Permissões novas** em `core/rbac/permissions.constants.ts`:
   - `PDV_SESSION_CLOSE_ANY`: fechar o caixa de outro operador.
   - `PDV_OFFLINE_REVIEW`: revisar venda offline recusada.
   - `ESTOQUE_KIT_TEMPLATE_MANAGE`: cadastro de kit montável.
3. **Feature flags** no padrão `*.flag.ts` (como `features/pdv/pdv-reservas.flag.ts`) para as partes
   grandes: `CIGARROS_ENABLED` (7), `ESSENCIA_CATALOGO_ENABLED` (6), `PDV_OFFLINE_ENABLED` (10).
4. **Códigos de erro novos.** As mensagens ficam no `*-models.ts` de cada feature, como já é feito em
   `sessao-mesa.models.ts::mensagemErroLancamento`. O `core/http/api-error.util.ts` extrai o código.

| errorCode | Status | Parte | Mensagem sugerida |
|---|---|---|---|
| `SESSION_WITHDRAWAL_REASON_REQUIRED` | 400 | 1 | Informe o motivo da desistência |
| `LINKED_SESSION_STILL_ACTIVE` | 409 | 1 | Recolha ou remova o rosh desta sessão antes |
| `LINKED_ITEM_IS_CHARGED` | 409 | 1 | Remova antes o rosh ligado a esta sessão |
| `COMANDA_ONLY_COURTESY` | 409 | 1 | A mesa só tem cortesia: não há o que encerrar como venda |
| `INVALID_PAYMENT_METHOD` | 400 | 1 | Forma de pagamento inválida |
| `COMANDA_PARTIALLY_CLOSED` | 409 | 2 | A mesa já tem conta paga: remova as linhas em aberto e encerre |
| `SESSION_ESSENCE_REQUIRED` | 400 | 2 | Informe a essência |
| `INVALID_STATUS_TRANSITION` / `INVALID_ORDER_STATE` | 409 | 2 | O pedido não pode ir para esse status agora |
| `RECEIPT_EMAIL_UNAVAILABLE` | 422 | 4 | Pedido cancelado, sem cliente ou cliente sem e-mail |
| `OPEN_PACKAGE_ALREADY_OPEN` | 409 | 5 | Já existe lata aberta deste sabor — use "Repor essência" |
| `OPEN_PACKAGE_INVALID_USES` | 400 | 5 | Sessões restantes fora do que a lata rende |
| `INVALID_PACKAGING` | 400 | 5 | (mostrar a `message`) |
| `PACKAGING_NOT_FOUND` | 404 | 5 | Este item não está em nenhuma embalagem |
| `PARENT_NOT_SELLABLE` | 400 | 5/6/7 | Escolha a variação (cor/sabor), não o produto base |
| `NOT_A_SESSION_PRODUCT` | 400 | 6 | Este produto não é essência de sessão |
| `ESSENCE_MUST_BE_FLAVOR` | 400 | 6 | Escolha o sabor, não o produto base |
| `KIT_ITEM_REMOVAL_NOT_ALLOWED` | 409 | 8 | Item de kit sai só com o kit inteiro |
| `INVITE_EMAIL_REQUIRED` | 400 | 9 | Usuário sem e-mail: cadastre um antes de reenviar o convite |
| `REGISTRATION_DISABLED` | 403 | 9 | Cadastro desativado — peça um convite |
| `DUPLICATE_CLIENT_SALE` | 409 | 10 | Esta venda já foi registrada |
| `OFFLINE_PAYMENT_NOT_ALLOWED`, `OFFLINE_SOLD_AT_OUT_OF_WINDOW`, `SESSION_HAS_PENDING_OFFLINE_SALES`, `OFFLINE_REJECTION_NOT_FOUND`, `OFFLINE_REJECTION_ALREADY_RESOLVED` | — | 10 | ver `PROMPT_FRONT_PDV_OFFLINE.md` |

---

## Parte 1 — Correções que já quebram a tela (urgente)

**1.1 Desistência de sessão servida (PDV-C036).** Hoje **quebra**: ao remover uma sessão que já foi
ao preparo ou foi entregue, e ainda não foi paga (o caso do "paga no final"), o backend responde
`400 SESSION_WITHDRAWAL_REASON_REQUIRED`. O front não manda o motivo
(`features/pdv/pdv.service.ts` → `removeComandaItem(id, itemId)`, perto da linha 295).
- **Contrato:** `DELETE /pdv/comandas/{id}/items/{itemId}?reason=` (até 200 caracteres). Sem motivo, só remove sessão que **não** foi servida, e linha de catálogo.
- **O que fazer:**
  - Em sessão com status `PREPARANDO` ou `ENTREGUE`, não cortesia e não paga, o botão vira **"Desistência"** e abre um diálogo de motivo obrigatório.
  - A linha **não some**: volta recolhida, a R$ 0 e como cortesia, com `withdrawn: true`, `withdrawnReason` e `withdrawnBy` (`ComandaItemResponseDTO`). Mostrar o selo "Desistência" com o motivo em `features/pdv/components/pdv-comandas/`.
  - `409 LINKED_SESSION_STILL_ACTIVE`: o rosh ligado ainda está no salão. Pedir para recolher ou remover o rosh antes.
- **Aceite:**
  - [ ] Remover uma sessão servida e não paga abre o motivo e a linha fica como desistência.
  - [ ] Uma sessão ainda aguardando pagamento continua sendo removida sem motivo.

**1.2 Mensagens que caem no erro genérico.**
- `COMANDA_ONLY_COURTESY` (409): ao encerrar (`/finish`) uma mesa que só tem cortesia.
- `INVALID_PAYMENT_METHOD` (400): forma de pagamento desconhecida, ou `GATEWAY_PIX` lançado pelo operador. Também vale para a correção de pagamento (parte 4).
- `LINKED_ITEM_IS_CHARGED` (409): remover uma sessão com o 2º rosh do duplo ainda na fila. Hoje é preciso remover o rosh primeiro; o backend vai corrigir isso (PDV-C045).
- **Aceite:** [ ] os três aparecem com mensagem própria.

---

## Parte 2 — Regras da mesa que ficaram faltando

Origem: `PROMPT_FRONT_VENDAS_AJUSTES.md` §7.
- **Cancelar mesa com linha paga:** esconder "Cancelar mesa" quando alguma linha tem `closedInOrderId` (`pdv-comandas.component.ts`, perto das linhas 495–503) e tratar `409 COMANDA_PARTIALLY_CLOSED`. A saída é remover as linhas em aberto e encerrar.
- **Linha paga não se remove:** sem botão de remover quando `closedInOrderId != null`.
- **Duplo:** esconder o modo DUPLO sem `PDV_COMANDA_COURTESY` (`features/pdv/dialogs/nova-sessao.dialog.ts`, perto da linha 110), porque o 2º rosh é cortesia.
- **`SESSION_ESSENCE_REQUIRED`:** mensagem própria, além da checagem local que já existe.
- **Pedidos:** mensagem para `INVALID_STATUS_TRANSITION` / `INVALID_ORDER_STATE` na troca de status.
- **Aceite:**
  - [ ] Mesa com conta parcial não oferece cancelar.
  - [ ] O atendente sem cortesia não vê o DUPLO.

---

## Parte 3 — "Comprou na loja?" e indicadores das mesas

Origem: `PROMPT_FRONT_MESAS_HISTORICO.md` §1 e §3 (PDV-F029, F036).

**3.1 Comprou na loja (PDV-F036).**
- **Contrato:** `PUT /pdv/comandas/{id}/store-purchase` com `{ "boughtInStore": true|false }` (`PDV_COMANDA_MANAGE`) → `204`. É uma resposta por mesa, e responder de novo sobrescreve.
- **Quando perguntar** (diálogo Sim / Não / Pular): ao recolher a **última** sessão da mesa e ao encerrar ou fechar a mesa. Também dá para corrigir no detalhe do histórico. "Pular" não grava nada.
- **Histórico** (`features/vendas/components/vendas-mesas-historico/`):
  - Coluna "Comprou na loja" (Sim/Não/—) com `boughtInStore`, `boughtInStoreBy` e `boughtInStoreAt`.
  - Filtro `boughtInStore` em `GET /pdv/comandas/history`, a acrescentar em `features/pdv/comanda-historico.models.ts`.

**3.2 Indicadores das mesas (PDV-F029/F035/F036).**
- **Contrato:** `GET /pdv/comandas/analytics?from=&to=&warehouseCode=` (`PDV_READ` ou `ORDER_READ`). O período máximo é de 366 dias; fora disso, ou invertido, responde 400.
- **Cards:**
  - Por mesa (`porMesa`), por hora de abertura (`porHora`) e por atendente (`porAtendente`).
  - `sessoesNarguile`: quantidade, receita, médias de espera/preparo/na mesa, pagas no final e desistidas.
  - `compraNaLoja`: conversão sobre as mesas respondidas.
- **Onde:** uma aba "Indicadores" em VENDAS › Mesas.
- **Aceite:**
  - [ ] O diálogo aparece no último recolhimento.
  - [ ] A coluna e o filtro funcionam.
  - [ ] Os cards carregam para o período escolhido.

---

## Parte 4 — Vendas e caixa

**4.1 Filtros no servidor (PDV-F026).** Hoje a filtragem de caixas é feita no cliente (comentário em
`features/pdv/pdv.service.ts`, perto da linha 139).
- `GET /pdv/sessions?status=OPEN|CLOSED&operator=&from=&to=&page=&size=` (`PDV_READ`): trocar a filtragem local por esses parâmetros.
- `GET /orders?sessionId=&comandaId=&orderNumber=&channel=&status=&customerId=&from=&to=` (no `vendas.service.ts`): o "ver vendas deste caixa" e a busca por número do pedido passam a ir ao servidor.
- **Aceite:**
  - [ ] A lista de caixas e a de pedidos filtram pelo servidor.
  - [ ] A paginação fica coerente com o filtro.

**4.2 Correção da forma de pagamento (PDV-F030).** A tela **já está feita e desligada**
(`features/vendas/correcao-pagamento.flag.ts`: `CORRECAO_PAGAMENTO_ENABLED = false`).
- Contrato:
  - `POST /orders/{id}/payments/correction` (`ORDER_PAYMENT_CORRECT`; com o caixa fechado, `ORDER_PAYMENT_CORRECT_CLOSED`).
  - `GET /orders/{id}/payment-history` (`ORDER_READ`).
- Erros: `PAYMENT_TOTAL_MISMATCH`, `REASON_REQUIRED`, `INVALID_PAYMENT_METHOD` (400); `CASH_SESSION_CLOSED`, `ORDER_NOT_CORRECTABLE`, `GATEWAY_PAYMENT_NOT_CORRECTABLE` (409).
- **O que fazer:** regenerar o client (parte 0), conferir os tipos e **ligar a flag**.
- **Aceite:** [ ] corrigir um pagamento de pedido de caixa aberto e de caixa fechado, e ver o histórico.

**4.3 Comprovante por e-mail (PLAT-F003).**
- **Contrato:** `POST /pdv/sales/{id}/receipt/email` (`PDV_SALE_MANAGE`) → `202 { sentTo }` (destinatário mascarado). Envia para o e-mail do **cliente vinculado**, sem endereço avulso.
- **Erros:** `422 RECEIPT_EMAIL_UNAVAILABLE` (pedido cancelado, sem cliente ou cliente sem e-mail).
- **Onde:** botão "Enviar por e-mail" no comprovante da venda e no detalhe do pedido. Só aparece quando há cliente vinculado.
- **Aceite:** [ ] envia e mostra "enviado para j***@…".

**4.4 Fechar caixa de outro operador (PDV-C037).** Só quem tem `PDV_SESSION_CLOSE_ANY` vê
"Fechar caixa" num caixa que não é seu. Sem ela, o backend responde `403 SESSION_NOT_OWNED`.
- **Aceite:** [ ] o atendente não vê o botão no caixa do colega.

---

## Parte 5 — Estoque: latas abertas, embalagem e produto base

O detalhe está em `PROMPT_FRONT_CIGARROS_ESSENCIAS.md` §1, §3 e §5. Resumo:

**5.1 Latas abertas (EST-F033).** Tela nova (não existe hoje), na aba Essências ou em
`features/estoque/components/`.
- **Lista:** `GET /estoque/open-packages?warehouseCode=` mostra "3 de 5 (restam 2)". O client gerado já tem `list-open-packages`.
- **Cadastrar lata já aberta:** `POST /estoque/open-packages/{sku}` com `{ warehouseCode, usesRemaining }` → `201`, **sem baixa de estoque**.
- **Repor essência:** `POST /estoque/open-packages/{sku}/replace` com `{ warehouseCode }`.
- **Permissão:** `ESTOQUE_STOCK_MANAGE` ou `PDV_COMANDA_MANAGE`.

**5.2 Embalagem "contém N" (EST-F032).** Fica no cadastro de produto
(`features/estoque/pages/produto-form/produto-form.page.ts`, na grade de variações).
- Coluna **"Contém"** em cada variação: escolher a variação de dentro e a quantidade (ex.: `LM-AZUL-MACO` contém 20 de `LM-AZUL-UN`).
- Endpoints (`ESTOQUE_PRODUCT_MANAGE`; leitura também com `PDV_READ`):
  - `PUT /estoque/products/{sku}/packaging` com `{ parentSku, unitsPerParent ≥ 2 }`.
  - `DELETE /estoque/products/{sku}/packaging`.
  - `GET /estoque/products/{sku}/packaging?warehouseCode=` devolve a cadeia com o disponível de cada nível.
- **Modelo do cigarro:** um produto ("LM") com variações **cor × embalagem**, cada uma com preço e código de barras próprios.
- Avisar no formulário: "vender além do solto abre um maço sozinho; além do maço, abre uma carteira".

**5.3 Produto base com variações (EST-F036).**
- O produto passa a trazer `parentSellable`.
- **Toggle "Vender o produto base"** no formulário, só quando há variações, chamando `PATCH /estoque/products/{sku}/parent-sellable` com `{ parentSellable }`.
- **Busca do PDV, mesa e seletor de essência:** não oferecer o SKU base quando há variações e `parentSellable` é `false`.
- `400 PARENT_NOT_SELLABLE` aparece na venda e na **entrada de estoque** no SKU base (compra, NF-e, entrada manual, estoque inicial de produto com variações). Orientar a usar a variação.
- ⚠️ **Desde a V149 todo produto com variações tem o base desligado.**

- **Aceite:**
  - [ ] Cadastrar uma lata aberta.
  - [ ] Ligar a carteira, o maço e a unidade de uma cor.
  - [ ] O base não aparece no PDV.
  - [ ] O toggle liga o base de volta.

---

## Parte 6 — Mesa: essência escolhida do catálogo (depende da 5)

O detalhe está em `PROMPT_FRONT_CIGARROS_ESSENCIAS.md` §2. Hoje a essência é texto livre em
`features/pdv/dialogs/nova-sessao.dialog.ts` (perto das linhas 221 e 250) e em `repetir-sessao.dialog.ts`.
- **Campos novos e opcionais:**
  - `essenciaSku` em `POST /pdv/comandas/{id}/sessoes`, `.../sessoes/{itemId}/rosh` e `.../sessoes/{itemId}/repetir`.
  - `essenciaRoshSku` no duplo.
  - `essencia` (texto) deixa de ser obrigatória quando vem o SKU.
- **Seletor** com duas listas:
  1. **Latas abertas** (`GET /estoque/open-packages`).
  2. **Essências à venda**: variações de produtos com `sessionProduct: true`, escondendo o base (parte 5.3).
- **Na linha da sessão:** mostrar "uso 3 de 5" (`packageUses` / `packageSessionsPerUnit`) e o sabor (`essenciaSku`).
- **Erros:** `NOT_A_SESSION_PRODUCT`, `ESSENCE_MUST_BE_FLAVOR`, `PARENT_NOT_SELLABLE`. Mapear em `sessao-mesa.models.ts::mensagemErroLancamento`.
- **Repetir sessão:** sem trocar o sabor, repete também o SKU. Ao trocar o sabor, mandar o `essenciaSku` novo.
- **Aceite:**
  - [ ] Lançar uma sessão escolhendo da lata aberta faz o "uso" subir.
  - [ ] Remover a sessão antes do preparo devolve o uso.

---

## Parte 7 — PDV: central de cigarros (depende da 5)

O detalhe está em `PROMPT_FRONT_CIGARROS_ESSENCIAS.md` §4.
- **Botão** no PDV ao lado do Kit Mahal (`features/pdv/pdv.component.ts`, perto da linha 1219), atalho **F7**. O F2/F4/F6/F9 já estão em uso; atualizar `ATALHOS_TOOLTIP` e o tour `core/tour/tours/pdv-venda.tour.ts`.
- **Diálogo** no molde de `features/pdv/dialogs/kit-mahal.dialog.ts`, com o fluxo **marca → cor → carteira / maço / solto + quantidade**.
- **Contrato:** `GET /pdv/cigarros?warehouseCode=` (`PDV_READ`) devolve `[{ productSku, productName, lines: [{ levels: [{ sku, label, price, available, containsSku, containsUnits }] }] }]`.
- **Venda:** o item vai ao carrinho com o `sku` do nível, pela venda de sempre. Vender além de `available` é permitido enquanto a cadeia cobrir (o sistema abre maço ou carteira). Mostrar o total possível somando os níveis convertidos. Recarregar depois de vender.
- **Aceite:**
  - [ ] Vender 25 soltos com 1 solto e maços fechados funciona.
  - [ ] Os saldos da central atualizam depois da venda.

---

## Parte 8 — Kit montável cadastrado no backend

Hoje o Kit Mahal é montado **só no front**, por categoria (`features/pdv/kit-mahal.config.ts`). O
backend tem o modelo cadastrado desde EST-F031/PDV-F019/ECM-F008, e o front não usa.
- **Cadastro** (Estoque › Kits montáveis):
  - CRUD `/estoque/kit-templates`: `GET` e `GET /{id}` com `ESTOQUE_PRODUCT_READ`; `POST`, `PUT /{id}` e `DELETE /{id}` com `ESTOQUE_KIT_TEMPLATE_MANAGE`.
  - Cada modelo tem passos por **categoria** e um desconto %.
- **PDV:**
  - `GET /pdv/kits` e `GET /pdv/kits/{id}/steps/{stepId}/options` (`PDV_READ`).
  - `POST /pdv/kits/quote` cota sem lançar.
- **Mesa:** `POST /pdv/comandas/{id}/kits` e `DELETE /pdv/comandas/{id}/kits/{bundleId}` (`PDV_COMANDA_MANAGE`).
  - As linhas trazem `kitBundleId` e `kitDiscountAmount`. Agrupar por `kitBundleId`.
  - `409 KIT_ITEM_REMOVAL_NOT_ALLOWED`: o item sai só com o kit inteiro.
- **Decisão a tomar no front:** migrar o `kit-mahal.config.ts` para o modelo cadastrado ("Kit Mahal" vira um template) ou manter os dois. A recomendação é migrar, porque desconto e passos passam a ser editáveis sem deploy.
- **Aceite:**
  - [ ] Cadastrar um modelo.
  - [ ] Montar e lançar na mesa.
  - [ ] Remover o kit inteiro.

---

## Parte 9 — Convite de usuário e auth

- **Reenviar convite:** botão em Configurações › Usuários chamando `POST /users/{id}/invite/resend` (`USER_CREATE`) → `204`. O link anterior é invalidado e o novo vale 48h. Erro `400 INVITE_EMAIL_REQUIRED`.
- **Login:**
  - `403 REGISTRATION_DISABLED`: o cadastro aberto está desligado.
  - `403 USER_NOT_FOUND` no login com Google: conta Google não cadastrada. Mensagem: "Esta conta Google não tem acesso — peça um convite ao administrador".
- **503 sem corpo** em cadastro ou convite: "Cadastro indisponível no momento" (`PROMPT_FRONT_ANALISE_CADASTRO_USUARIO.md`).
- **Aceite:**
  - [ ] Reenviar o convite.
  - [ ] As três mensagens aparecem nos casos certos.

---

## Parte 10 — Venda offline

O detalhe está em `PROMPT_FRONT_PDV_OFFLINE.md`. São duas etapas.
- **1ª etapa (rápida, vale já online):** `clientSaleId` (UUID, `crypto.randomUUID()`) em **toda** venda, gerado uma vez por carrinho e guardado no rascunho (`features/pdv/pdv-rascunho.service.ts`). O envio é `features/pdv/pdv.service.ts::registerSale`.
  - Reenvio com a mesma chave → `200` com a venda já registrada.
  - `409 DUPLICATE_CLIENT_SALE` → tratar como "já registrada".
  - Já resolve a venda duplicada por timeout ou duplo clique.
- **2ª etapa:**
  - PWA (`@angular/service-worker`) com cache do catálogo do balcão; `features/pdv/pdv-preload.service.ts` é uma boa base.
  - Fila em IndexedDB.
  - `POST /pdv/sessions/{id}/sales/sync` em lotes de 50.
  - Tela de revisão das recusadas (`PDV_OFFLINE_REVIEW`).
  - Bloquear o fechamento do caixa com fila local.
  - Só dinheiro, débito e crédito offline.
  - ⚠️ Conferir o orçamento do `size-limit` no `package.json` (500 KB main / 2 MB total) ao incluir o service worker e o `idb`.
- **Aceite:** ver o checklist do `PROMPT_FRONT_PDV_OFFLINE.md`.

---

## Parte 11 — Ajustes visuais

Origem: `PROMPT_FRONT_VENDAS_AJUSTES.md` §1, §2 e §4.
- Menu principal em CAIXA ALTA (CRM, ESTOQUE, VENDAS, COMPRAS, FINANCEIRO) em `core/layout/app-modules.ts`.
- Coluna e detalhe do pedido: rótulo **"Vendido por"** (`operatorName`); hoje está "Atendido por". Mostrar também no detalhe do pedido.
- Textos "CRM › Marcados" que ainda aparecem no PDV (`pdv-pagamento.component.ts`, `pdv.component.ts`, `cobranca-*.ts`) passam a "VENDAS › Marcados". Conferir se é texto de tela ou só caminho de import.
