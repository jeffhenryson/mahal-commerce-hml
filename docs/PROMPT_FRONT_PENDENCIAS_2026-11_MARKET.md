# Prompt para o frontend-market: o que o backend entrega e o app ainda não usa (05/11/2026)

O backend está no branch `feat/pdv-caixa-sessao-ajustes` do `mahal-backend`. Front de referência: `frontend-market`,
branch `feat/integracao-backend-hml`, commit `1803be0`. Os paths são relativos a `frontend-market/src/app/`.

A lista saiu de uma busca de cada contrato no código, sem abrir tela por tela.

**Como usar:**

1. Antes de cada parte, confira no código se ela ainda falta. Se já estiver feita, diga isso e passe para a próxima.
2. Faça uma parte por vez, com os seus testes.
3. O contrato completo está no documento de origem de cada parte, em `backend/docs/`, e em `docs/api-reference.md`.

Os erros vêm no formato `{ errorCode, message }`. Só o resgate de cashback responde com `code` (ver parte 4).

## Índice

| Parte | Assunto | Prioridade | Origem |
|---|---|---|---|
| 0 | Regenerar o client | antes de tudo | — |
| 1 | Página de redefinir senha | 🔴 | `PROMPT_FRONT_MARKET_REDEFINIR_SENHA.md` |
| 2 | URL da API em produção e homologação | 🔴 | `PROMPT_FRONT_MARKET_PEDIDO_BALCAO.md` §3 |
| 3 | Status da loja e recusas do checkout | 🔴 | `PROMPT_FRONT_CENTRAL_PEDIDOS.md` §8 |
| 4 | Cashback no checkout e em Minha conta | 🔴 | `PROMPT_FRONT_CASHBACK_RESGATE.md` §5 e §6 |
| 5 | Mensagem, Pix e link da viagem no pedido | 🟡 | `PROMPT_FRONT_CENTRAL_PEDIDOS.md` §8 |
| 6 | Promoção por quantidade | 🟡 | `docs/api-reference.md` |
| 7 | Notificações do pedido | 🟡 | `PROMPT_FRONT_CENTRAL_PEDIDOS.md` §6.3 |
| 8 | Kit montável | 🟢, a decidir | `docs/api-reference.md` § "Kit montável" |

---

## Parte 0: Regenerar o client

O `src/app/api` é de 23/10 e não tem os campos novos. Atualize o `openapi.json` com o backend da branch rodando e
regenere o client. Atualize também os tipos escritos à mão em `core/models/shop-api.model.ts`.

## Parte 1: Página de redefinir senha

O app tem o botão "Esqueci minha senha", mas não tem a rota `redefinir-senha` nem o `resetPassword` em
`core/services/auth.service.ts`. Siga o documento de origem.

⚠️ Só com a página publicada, peça para definir `PASSWORD_RESET_CUSTOMER_FRONTEND_URL` no backend. Se a variável for
definida antes, o link cai na home e o token se perde.

**Aceite:**
- [ ] Token ausente, sucesso e os dois erros de link testados.

## Parte 2: URL da API

`environments/environment.prod.ts` e `environment.hml.ts` ainda têm `apiUrl: 'http://localhost:8080'`.

- Troque pela URL pública de cada ambiente. **Pergunte a URL se ela não estiver no repositório.**
- Peça para incluir o domínio do app em `CORS_ALLOWED_ORIGINS` no backend.

**Aceite:**
- [ ] O build de produção não aponta para `localhost`.

## Parte 3: Status da loja e recusas do checkout

- Chame `GET /shop/store-status` (público) antes do checkout. Com `aberto = false`, mostre "loja fechada".
- Esconda as modalidades desligadas (`entregaHabilitada`, `retiradaHabilitada`).
- Mostre o `pedidoMinimo` e os `bairros` na entrega. A taxa continua "a confirmar pela loja".
- Em `features/checkout-payment/checkout-error.ts`, trate os `409`:

| Código | Mensagem sugerida |
|---|---|
| `LOJA_FECHADA` | A loja está fechada no momento |
| `MODALIDADE_INDISPONIVEL` | Esta forma de receber o pedido não está disponível agora |
| `PEDIDO_MINIMO` | O pedido não atingiu o valor mínimo para entrega |
| `FORA_DA_AREA` | Ainda não entregamos nesse bairro |

**Aceite:**
- [ ] Loja fechada não deixa concluir o checkout.
- [ ] Cada `409` aparece com a sua mensagem.

## Parte 4: Cashback

Nada disto existe no app hoje.

- **Checkout:** `GET /shop/cashback?amount={total da mercadoria}`. Mostre "Usar meu cashback (até R$ X)" com
  `maxRedeemable`. Com `reason` preenchido, mostre o motivo.
- **Envio:** `POST /shop/checkout` recebe `cashbackToRedeem`, e o pedido nasce com o total já abatido.
- **Erros:** resposta `422` com `code`. A tabela de mensagens está no documento de origem.
- **Minha conta:** card "Meu cashback" com `GET /shop/cashback` sem `amount`: disponível, `pending` e `expiringSoon`.

**Aceite:**
- [ ] O total exibido é o mesmo que o pedido grava.
- [ ] O card aparece em Minha conta.

## Parte 5: Mensagem, Pix e link da viagem no pedido

Em `features/orders/order-detail.ts`, use o que `GET /shop/orders/{id}` já devolve:

- `atendimento.mensagem`: o texto da etapa.
- `atendimento.pixChave`: na entrega aceita que falta pagar. Mostre com um botão de copiar.
- `delivery.trackingUrl`: depois da saída, como link "Acompanhar entrega".

**Aceite:**
- [ ] Os três campos aparecem só quando vêm preenchidos.

## Parte 6: Promoção por quantidade

- **Catálogo e detalhe:** mostre `quantityPromotions` no card e na página do produto.
- **Carrinho:** mostre `promotionName` e `promotionDiscountAmount` por item, e `promotionDiscountTotal` no resumo.

**Aceite:**
- [ ] O desconto do carrinho é o mesmo que o checkout grava.

## Parte 7: Notificações do pedido

Cada etapa do pedido gera a notificação `PEDIDO_CLIENTE` em `/notifications`. O app ainda não lê essa rota, então a
parte inclui criar o serviço e um sino ou aviso no header.

⚠️ O `targetUrl` vem como `/pedidos/{id}`, e o app só tem `/pedido/:id` e `/conta/pedidos/:id`. Mapeie o caminho no front
ou crie um redirecionamento. Sem isso, a rota `**` leva para a home.

**Aceite:**
- [ ] Clicar na notificação abre o pedido certo.

## Parte 8: Kit montável (a decidir)

O backend tem `/shop/kits` e `/shop/cart/kits`. O model do app já prevê itens de kit, mas nenhuma tela faz essas
chamadas. **Pergunte antes de começar** se o kit entra no app agora.
