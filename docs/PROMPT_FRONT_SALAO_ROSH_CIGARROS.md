# Prompt para o frontend-admin: rosh duplo com sabor depois, cigarros e latas de essência (09/10/2026)

Backend na branch `feat/pdv-caixa-sessao-ajustes`. É a resposta a `Docs/PROMPT_BACKEND_ROSH_DUPLO.md` e a
`Docs/PROMPT_BACKEND_SALAO.md` §1, §2, §4 e §5. Itens: **PDV-F044**, **PDV-F045**, **EST-F037** e
**EST-C026**. Migrations **V151 e V152**. Os erros vêm no formato de sempre: `{ errorCode, message }`.

**Ficou fora deste ciclo:** a data de nascimento do cliente (`PROMPT_BACKEND_SALAO.md` §3, `customers.data_nascimento`).
O tipo manual `crm/cliente-nascimento.ts` continua valendo, e o Jackson segue ignorando o campo.

**Primeiro passo:** `npm run fetch:spec && npm run generate:api`. Depois, trocar os tipos manuais:
- `SaborRoshRequest` e `ItemDeSessao.essenciaPendente` em `features/pdv/sessao-mesa.models.ts`;
- `estoque/categoria-cigarro.ts`: o fallback pelo nome pode ficar, mas `category.cigarette` passa a ser a fonte;
- `RendimentoEssencia` em `estoque/estoque.service.ts`.

**Ordem de deploy:** o backend pode subir antes do front. O cliente antigo, que manda `essenciaRosh` no
duplo, continua funcionando.

---

## 1. Rosh duplo: sabor do 2º rosh na hora de consumir (PDV-F044)

O contrato saiu como pedido em `PROMPT_BACKEND_ROSH_DUPLO.md`:

- `POST /pdv/comandas/{id}/sessoes` com `modo: DUPLO` e `…/repetir` com `duplo: true` **aceitam** lançar sem
  `essenciaRosh`/`essenciaRoshSku`/`tierIdRosh`. O `ROSH_EXTRA` nasce cortesia, `NA_FILA`, sem nota e sem
  baixa, com **`essenciaPendente: true`** no item.
- Recolher a sessão **não** promove esse rosh: ele continua `NA_FILA`. O **vaso continua reservado**, porque
  é o mesmo narguilé.
- `PUT /pdv/comandas/{id}/itens/{itemId}/rosh-sabor` com `{ essencia?, essenciaSku?, tierId? }`, em que
  `itemId` é o **ROSH_EXTRA**. Responde `200` com a comanda inteira. Funciona nas duas ordens: antes de
  recolher (o recolhimento promove) e depois (o PUT já põe o rosh em `PREPARANDO`).
- Erros: `400 SESSION_ESSENCE_REQUIRED`, `400 NOT_A_SESSION_PRODUCT` / `ESSENCE_MUST_BE_FLAVOR`,
  `404 COMANDA_ITEM_NOT_FOUND`, `409 ROSH_FLAVOR_NOT_PENDING`, `409 COMANDA_NOT_OPEN`, `422 ITEM_NOT_ROSH_EXTRA`.

**Novo, não estava no pedido:**
- `PATCH …/sessoes/{itemId}/status` com `PREPARANDO` num rosh com `essenciaPendente` responde
  **`409 ROSH_FLAVOR_PENDING`**. Quem tira o rosh da fila é o `rosh-sabor`. Se a tela tiver o botão
  "Preparar" no card do rosh, ele deve sumir enquanto o rosh aguarda sabor.
- **Desistiu do 2º rosh** antes de escolher o sabor: use a remoção comum, `DELETE /pdv/comandas/{id}/items/{itemId}`.
  Nada volta ao estoque, porque nada saiu. Se a sessão já estava recolhida, o vaso é liberado.

## 2. Cigarros (PDV-F045)

### Categoria
- `POST/PATCH /estoque/categories` aceitam `cigarette` (boolean). No POST, omitido vale `false`; no PATCH,
  omitido não muda nada. `GET` devolve `cigarette` em toda categoria.
- A V151 já marcou como `cigarette = true` as categorias cujo nome contém "cigarr" (com e sem acento).

### Central (`GET /pdv/cigarros`)
- Agora inclui o produto **ativo** de categoria de cigarros **sem embalagem**. Cada SKU vendável dele (as
  variações ativas, ou o próprio produto sem variações) vem numa cadeia de **um nível**, com
  `containsSku`/`containsUnits` nulos.
- A montagem local `familiasSemEmbalagem` pode sair. Se ficar, é preciso deduplicar por `productSku`.

### Abrir no balcão: `POST /pdv/cigarros/abrir` (`PDV_SALE_MANAGE`)
```json
{ "sku": "LM-AZUL-CART", "quantidade": 1, "warehouseCode": "PRINCIPAL" }
// 200 { "aberto": { "sku": "LM-AZUL-CART", "available": 1 }, "dentro": { "sku": "LM-AZUL-MACO", "available": 13 } }
```
- **Exige caixa aberto** do operador, e o depósito é o do caixa. O `warehouseCode` é opcional; se vier
  diferente do depósito do caixa, a resposta é `400 WAREHOUSE_MISMATCH`.
- `404 PACKAGING_NOT_FOUND`: o SKU não contém nada.
- `409 INSUFFICIENT_STOCK`: não há embalagem fechada disponível. Abrir **nunca** abre o nível de cima.
- ⚠️ **`404 NO_OPEN_SESSION`** quando o operador não tem caixa aberto. Hoje o front trata 404/405 dessa rota
  como "aguardando o servidor". Com a rota no ar, olhe o `errorCode`:
  - `NO_OPEN_SESSION` → "abra o caixa";
  - `PACKAGING_NOT_FOUND` → nível sem conteúdo.

  Só um 404 sem `errorCode` (ou um 405) indica rota ausente.

### Código de barras
- `PATCH /estoque/products/{sku}` aceita `barcode: ""` para **apagar** o código. Nulo continua mantendo o
  atual. Na conversão do cigarro antigo, isso permite mover o EAN do produto para o maço.

### Bug das variações repetidas (EST-C026)
- `GET /estoque/products/{sku}`, `by-barcode` e as consultas internas não repetem mais a variação de 2
  atributos. A proteção `semVariacoesRepetidas` pode ficar, porque não custa nada.

## 3. Latas de essência (EST-F037)

- **`sessionsPerUnit` é estimativa.** A lata **não fecha mais sozinha**: `uses` pode passar de
  `sessionsPerUnit`. Nesse caso `remaining` fica em 0 e `exhausted` em `true`. Mostre `exhausted` como o
  selo "acima da estimativa", não como fim.
- **"Acabou":** `POST /estoque/open-packages/{sku}/close` com `{ warehouseCode }` (`PDV_COMANDA_MANAGE` ou
  `ESTOQUE_PRODUCT_MANAGE`). Responde `200` com a lata **fechada**, com `closedAt` e
  `closeReason: "FINISHED"`. Não abre outra lata e não dá baixa. Sem lata aberta: `404 OPEN_PACKAGE_NOT_FOUND`.
- **Rendimento real:** `GET /estoque/open-packages/yield?warehouseCode=&from=&to=` (`from`/`to` em ISO-8601,
  opcionais, sobre `closedAt`) devolve
  `[{ sku, productName, closedCount, avgSessions, totalSessions, estimate }]`.
  - Conta só as latas fechadas com ao menos um uso.
  - `estimate` é o `sessionsPerUnit` **atual** e pode vir nulo.
  - `from` depois de `to` responde `400`.
- `OpenPackageResponseDTO` ganhou `closedAt` e `closeReason`, nulos enquanto a lata está em uso.
- "Repor essência" (`/replace`) não mudou.

## Tabela de códigos novos

| HTTP | `errorCode` | Onde |
|---|---|---|
| 409 | `ROSH_FLAVOR_NOT_PENDING` | `PUT …/rosh-sabor`: o rosh já tem sabor |
| 409 | `ROSH_FLAVOR_PENDING` | `PATCH …/status PREPARANDO` em rosh sem sabor |
| 422 | `ITEM_NOT_ROSH_EXTRA` | `PUT …/rosh-sabor` num item que não é 2º rosh |
| 400 | `WAREHOUSE_MISMATCH` | `POST /pdv/cigarros/abrir` com depósito diferente do caixa |
| 409 | `INSUFFICIENT_STOCK` | `POST /pdv/cigarros/abrir` sem embalagem fechada (na venda continua 400) |
