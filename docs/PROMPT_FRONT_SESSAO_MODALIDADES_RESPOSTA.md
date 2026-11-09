# Resposta do backend: modalidades da sessão e adicional após a entrega (PDV-F061)

> Retorno ao `frontend-admin/Docs/PROMPT_BACKEND_SESSAO_MODALIDADES.md`. O contrato pedido foi implementado
> como está, com a migration **V191**. Abaixo, só o que difere do prompt ou o que ele deixava em aberto.

## Códigos de erro novos, além dos do prompt

- **409 `SESSION_PACKAGE_PAID`** — na edição de uma sessão `COMPRA_ESSENCIA`, trocar o sabor com o pacote
  já pago. A linha paga não é reescrita. Para trocar, o operador remove e lança de novo.
- **409 `SESSION_LINKED_LINE`** — `DELETE /pdv/comandas/{id}/items/{itemId}` no pacote (`vinculoSessao:
  PACOTE_ESSENCIA`). O pacote sai junto com a sessão, nunca sozinho. Sugestão: esconder "Remover" na linha do
  pacote.

## Regras confirmadas

- **Desistência:** o pacote **fica** na conta, como sugerido.
- **Remover a sessão** ainda aguardando pagamento leva junto o pacote em aberto e devolve a unidade ao
  depósito de onde ela saiu.
- **Pagamento:** a sessão e o pacote fecham juntos. Mandar só a sessão em `itemIds` dá 409
  `LINKED_ITEM_MUST_CLOSE_TOGETHER`, a regra de sempre para pai e filha.
- **Adicional pós-entrega numa sessão já paga:** a linha nova vem com `mode: "ADICIONAL_SESSAO"`, SKU
  `ADIC-{id}` e `vinculoSessao: "ADICIONAL"`. Ela é cobrada sozinha porque a sessão já está fechada.
- **`POST …/adicionais` responde 200** (não 201), com a `ComandaResponseDto` inteira.
- **`/items` não aceita `mode: ADICIONAL_SESSAO`:** responde 400 `MENU_SESSION_NOT_ALLOWED_ON_ITEMS`.

## Flags da essência

- As flags ficam **na variação** quando o SKU é de variação. Se a variação não marcou nada, vale a marcação
  do produto.
- `PATCH /pdv/sessao/essencias/{sku}` com o SKU do próprio produto grava no produto.
- Campo omitido no PATCH mantém o valor atual.
- `ProductResponseDto` traz `sessionCompraEssencia`/`sessionItensPessoais` no produto. Cada item de
  `variants[]` traz os mesmos campos, já resolvidos.
- Produto que não é de sessão: o `PATCH` responde 400 `NOT_A_PACKAGED_SESSION_PRODUCT`.

## Config

- `PUT /pdv/sessao/config`: com `itensPessoaisPreco` ou `compraEssenciaDesconto` omitidos, o valor atual é
  mantido.

## Ajuste (`PROMPT_BACKEND_SESSAO_MODALIDADES_AJUSTE.md`)

- **V192:** `sessionCompraEssencia` passa a ter padrão `true`, e os produtos existentes passam a `true`.
  - Produto novo também nasce com `true`.
  - Na variação, `null` herda do produto e `false` exclui só aquele sabor.
  - `sessionItensPessoais` continua com padrão `false`.
- **Baixa do pacote:** sai do depósito da comanda (o caixa), como venda de balcão.
  - Sem saldo, dá o mesmo erro de estoque do `POST /pdv/comandas/{id}/items`, e não `SALAO_SEM_ESTOQUE`.
  - Remover a sessão (ou trocar o sabor na edição) devolve o pacote a esse mesmo depósito.
- **Um pacote só:** `COMPRA_ESSENCIA` com `essenciaSku2` dá **409 `SESSION_MIX_NOT_ALLOWED`**, no lançamento
  e na edição.

## Pacotes (`PROMPT_BACKEND_SESSAO_PACOTES.md`)

O pedido foi implementado como descrito, com a migration **V193** (`sessionItensPessoais` com padrão `true`,
por exclusão). `SESSION_MIX_NOT_ALLOWED` deixou de existir. Abaixo, as decisões que o prompt deixava em aberto.

- **Pacote incluso:** linha à parte com `unitPrice 0`, `quantity 1` e `incluso: true`.
  - Ela também vem com `vinculoSessao: "PACOTE_ESSENCIA"`, mas não é cortesia: não pede alçada.
  - Removê-la sozinha dá 409 `SESSION_LINKED_LINE`, como para os outros pacotes.
- **Pacote precisa ser essência de sessão** vendável, a mesma checagem do sabor. Se não for, dá 400
  `NOT_A_SESSION_PRODUCT` ou `ESSENCE_MUST_BE_FLAVOR`.
- **Itens pessoais sem faixa:** a sessão sai com o SKU `SESS-PESSOAL` e `tierId: null`.
- **Edição:**
  - `pacotes` omitido mantém os pacotes atuais;
  - se vier, substitui o conjunto inteiro, e o estoque se move só pela diferença;
  - nos itens pessoais, trocar o sabor principal refaz qual pacote é o incluso;
  - qualquer refação com pacote já pago dá 409 `SESSION_PACKAGE_PAID`.
- **Rosh extra** (`POST …/rosh`) em sessão das modalidades novas dá 409 `SESSION_MODALITY_DUPLO`.
- **Repetir:** `RepeatSessionRequest.repeticao` aceita dois valores.
  - **`ESSENCIAS_COMPRADAS`** (o padrão quando a origem é de modalidade nova): mesma modalidade e mesmo
    preço, sem pacote novo e sem baixa de estoque. O sabor (`essenciaSku`/`essenciaSku2`, ou o da origem)
    tem de estar entre os pacotes ou os sabores da origem. Se não estiver, dá 400
    `SESSION_FLAVOR_NOT_IN_PACKAGES`.
  - **`PADRAO`:** sessão da casa, pela faixa do sabor e com uso de lata.
