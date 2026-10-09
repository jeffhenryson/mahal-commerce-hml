# Prompt para o frontend-admin: central de cigarros, essências abertas na mesa e produto base com variações (08/10/2026)

Backend na branch `feat/pdv-caixa-sessao-ajustes`. Itens: EST-F033, EST-C025, PDV-F042, EST-F032,
PDV-F041 e EST-F036. Migrations **V147, V148 e V149**. Os erros vêm no formato de sempre:
`{ errorCode, message }`.

**Primeiro passo:** sincronizar o OpenAPI para regenerar o client. Há endpoints, campos e códigos de erro
novos (tabela no fim).

---

## 1. Mesa › Essências abertas: cadastrar as latas que já estão na bancada (EST-F033)

As essências da mesa saem da prateleira da loja. A lata aberta tem um contador de sessões ("3 de 5"), e
quando ela acaba o sistema abre a próxima e dá baixa de 1 unidade da loja. Esta tela serve para o
**inventário inicial**: registrar as latas que já estavam abertas antes do sistema saber delas.

- Lista: `GET /estoque/open-packages?warehouseCode=` (`ESTOQUE_PRODUCT_READ` ou `PDV_COMANDA_MANAGE`).
  Cada lata traz `{ sku, productName, uses, sessionsPerUnit, remaining, exhausted, openedAt, openedBy }`.
  Mostrar como "Zomo Blueberry — 3 de 5 (restam 2)".
- **Cadastrar lata já aberta:** `POST /estoque/open-packages/{sku}` com `{ warehouseCode, usesRemaining }`
  (`ESTOQUE_STOCK_MANAGE` ou `PDV_COMANDA_MANAGE`), responde `201`.
  - O operador informa **quantas sessões a lata ainda rende** (de 1 até o `sessionsPerUnit` do produto).
  - **Não baixa estoque**: a lata já saiu da prateleira.
  - Erros:
    - `409 OPEN_PACKAGE_ALREADY_OPEN`: já existe lata aberta desse sabor. Oferecer "Repor essência".
    - `400 OPEN_PACKAGE_INVALID_USES`: restante fora da faixa.
    - `400 NOT_A_PACKAGED_SESSION_PRODUCT`: o produto não tem `sessionsPerUnit` no cadastro.
- "Repor essência" continua igual: `POST /estoque/open-packages/{sku}/replace` descarta a lata atual e
  abre outra, com baixa de 1.
- Só aparecem para cadastro os produtos de sessão (`sessionProduct: true`) **com** `sessionsPerUnit`.
  Escolher o **sabor**, que é a variação, e não o produto base.

## 2. Mesa › Lançar sessão: essência escolhida do catálogo (PDV-F042)

Hoje a essência é texto livre e **não controla estoque**. Agora ela pode vir do catálogo.

- `POST /pdv/comandas/{id}/sessoes` aceita **`essenciaSku`** e, no duplo, **`essenciaRoshSku`**, os dois
  **opcionais**.
  - O mesmo vale para `POST .../sessoes/{itemId}/rosh` (`essenciaSku`) e
    `POST .../sessoes/{itemId}/repetir` (`essenciaSku`, `essenciaRoshSku`).
  - `essencia` (texto) deixou de ser obrigatória **quando vem o SKU**. Sem texto, o nome do produto vira a
    essência da linha.
  - Sem texto **e** sem SKU continua `400 SESSION_ESSENCE_REQUIRED`.
- **Seletor de essência** com duas listas:
  1. **Latas abertas** (`GET /estoque/open-packages`): consomem um uso da lata, sem baixa da loja.
  2. **Essências à venda**: variações de produtos com `sessionProduct: true`. Se não houver lata aberta
     daquele sabor, a sessão abre uma (baixa de 1 unidade da loja). Se o produto não tiver
     `sessionsPerUnit`, sai 1 unidade como "uso da loja".
- A resposta da comanda traz, por linha: `essenciaSku` (nulo na sessão só em texto), `packageUses` e
  `packageSessionsPerUnit`. Mostrar o "3 de 5" na linha.
- Erros novos ao escolher a essência:
  - `400 NOT_A_SESSION_PRODUCT`: o produto não é de sessão.
  - `400 ESSENCE_MUST_BE_FLAVOR`: foi escolhido o produto base, e é preciso escolher o sabor.
  - `400 PARENT_NOT_SELLABLE`: ver §5.
- **Remover ou cancelar** uma sessão que ainda está aguardando pagamento ou na fila devolve o uso à lata.
  Depois de ir ao preparo, a essência foi queimada e **não volta**. A desistência também não devolve.
  Nada muda na tela, é só para o operador saber.
- **Repetir sessão** sem sabor novo repete texto **e** SKU da origem. Sabor novo só em texto gera uma
  sessão sem controle de estoque. Ao trocar o sabor, mandar o `essenciaSku` novo.

## 3. Estoque › Produto: embalagem "contém N" (EST-F032)

Cigarro com cor e embalagem é cadastrado assim:
- **Um produto** (ex.: "LM").
- **Variações cor × embalagem** (ex.: `LM-AZUL-CART`, `LM-AZUL-MACO`, `LM-AZUL-UN`), cada uma com preço e
  código de barras próprios.

Depois, cada variação é ligada à embalagem que a contém:

- `PUT /estoque/products/{sku}/packaging` com `{ parentSku, unitsPerParent }` (`ESTOQUE_PRODUCT_MANAGE`).
  Exemplos: `LM-AZUL-UN` contém 20 dentro de `LM-AZUL-MACO`; `LM-AZUL-MACO` contém 10 dentro de
  `LM-AZUL-CART`.
- `DELETE /estoque/products/{sku}/packaging` desliga. `404 PACKAGING_NOT_FOUND` se não houver ligação.
- `GET /estoque/products/{sku}/packaging?warehouseCode=` (`ESTOQUE_PRODUCT_READ` ou `PDV_READ`) devolve a
  cadeia da embalagem mais externa à mais interna:
  `[{ sku, containsSku, containsUnits, available }]`.
- `400 INVALID_PACKAGING`: mostrar a `message`. Os motivos são kit, produto base com variações, produto
  com lote, ciclo e mais de 4 níveis. `unitsPerParent` precisa ser ≥ 2.
- **Sugestão de tela:** na grade de variações, uma coluna "Contém" com um seletor da variação de dentro e
  a quantidade.
- **Efeito automático:** a venda de um solto ou maço sem saldo **abre a embalagem de fora sozinha**, em
  cascata. A tela de movimentações vai mostrar movimentos com motivo "Quebra automática de embalagem →/←".
  A compra continua sendo uma entrada no SKU da carteira.

## 4. PDV › Central de cigarros (PDV-F041)

Botão próprio no PDV, no molde do **Kit Mahal** (que fica em `kit-mahal.config.ts`). Sugestão de atalho:
F7.

- `GET /pdv/cigarros?warehouseCode=` (`PDV_READ`):

```json
[ { "productSku": "LM", "productName": "LM",
    "lines": [ { "levels": [
      { "sku": "LM-AZUL-CART", "label": "azul · carteira", "price": 110.00, "available": 1,  "containsSku": "LM-AZUL-MACO", "containsUnits": 10 },
      { "sku": "LM-AZUL-MACO", "label": "azul · maço",     "price": 12.00,  "available": 8,  "containsSku": "LM-AZUL-UN",   "containsUnits": 20 },
      { "sku": "LM-AZUL-UN",   "label": "azul · unidade",  "price": 1.00,   "available": 15, "containsSku": null,           "containsUnits": null } ] } ] } ]
```

- Fluxo sugerido: escolher a **marca** (`productName`) → a **cor** (uma `line`) → o nível (carteira,
  maço ou solto) e a quantidade. Isso adiciona ao carrinho do PDV um item com o `sku` do nível.
  - A venda é a de sempre (`POST /pdv/sessions/{id}/sales`). Não há endpoint de venda novo.
- Aparece na central todo produto ativo que tem embalagem ligada (§3). Não depende da categoria.
- **Saldo:** `available` é o que está solto/fechado em cada nível. Vender além dele é permitido enquanto
  a cadeia cobrir, porque o sistema abre maço ou carteira. Para mostrar "dá para vender até X soltos",
  some os níveis convertidos (ex.: 15 + 8×20 + 1×10×20). Se a cadeia inteira não cobrir, a venda
  responde `400 INSUFFICIENT_STOCK`.
- Depois de vender, recarregar a central: os saldos mudam também nos níveis de cima.

## 5. Produto base com variações não vendável (EST-F036)

"LM" com azul e vermelho aparecia como **três** itens no PDV. Agora o SKU **base** de um produto com
variações **não se vende e não recebe entrada de estoque**, a menos que o dono libere.

- O produto traz **`parentSellable`** em toda resposta (`GET /estoque/products`, `/{sku}` etc.).
- **Busca do PDV, mesa e seletor de essência:** quando o produto tem variações e `parentSellable` é
  `false`, **não oferecer o SKU base**, só as variações.
- **Cadastro do produto:** toggle "Vender o produto base" em
  `PATCH /estoque/products/{sku}/parent-sellable` com `{ parentSellable }` (`ESTOQUE_PRODUCT_MANAGE`).
  Mostrar só quando há variações.
- `400 PARENT_NOT_SELLABLE` aparece em:
  - Venda, mesa, sessão e checkout do site com o SKU base.
  - **Entrada de estoque** no SKU base: compra, NF-e, entrada manual e estoque inicial de produto com
    variações. Orientar a dar entrada nas variações.
  - Saída e ajuste (balanço) continuam aceitos, para escoar o que ficou na base.
- ⚠️ A migration V149 deixa **todos** os produtos com variações com o base desligado. Se algum produto
  vende de propósito pelo base, é preciso ligar o toggle dele.

---

## Códigos de erro novos

| errorCode | Status | Onde |
|---|---|---|
| `OPEN_PACKAGE_ALREADY_OPEN` | 409 | cadastrar lata já aberta |
| `OPEN_PACKAGE_INVALID_USES` | 400 | cadastrar lata já aberta |
| `ESSENCE_MUST_BE_FLAVOR` | 400 | sessão / rosh / repetir com produto base |
| `INVALID_PACKAGING` | 400 | ligar embalagem |
| `PACKAGING_NOT_FOUND` | 404 | desligar embalagem |
| `PARENT_NOT_SELLABLE` | 400 | venda ou entrada no SKU base com variações |

## Checklist

- [ ] Sincronizar o OpenAPI e regenerar o client.
- [ ] Tela de latas abertas com "Cadastrar lata já aberta" (§1).
- [ ] Seletor de essência da sessão com latas abertas e essências à venda, mandando `essenciaSku` (§2).
- [ ] Mostrar "3 de 5" (`packageUses`/`packageSessionsPerUnit`) na linha da sessão (§2).
- [ ] Coluna "Contém" na grade de variações do produto (§3).
- [ ] Botão "Central de cigarros" no PDV, no molde do Kit Mahal (§4).
- [ ] Esconder o SKU base quando `parentSellable` é `false` e há variações; toggle no cadastro (§5).
- [ ] Tratar os códigos de erro novos.
