# Prompt para o frontend-admin: Central de Pedidos, fatias 1 e 2 (24/10/2026)

Backend: ECM-F012 (V176) + ECM-F013 (V177). Erros no formato de sempre: `{ errorCode, message }`.
Contrato completo em `docs/api-reference.md`, seções "Central de Pedidos (ECM-F013)", "Pedidos do app pagos no caixa",
"Pedidos (visão do administrador)" e `GET /notifications/stream`.
Depois de subir o backend, rode `npm run generate:api` e troque os tipos escritos à mão de `central-pedidos.contrato.ts`
pelos gerados. As seções 1 a 6 abaixo dizem o que muda em cada flag. A seção 7 lista o que conferi no código do front e
precisa de ajuste antes de ligar as flags.

Tudo de `Docs/PROMPT_BACKEND_CENTRAL_PEDIDOS.md` foi entregue, com três diferenças:
- **(a)** O limite de streams responde 429 (não fecha a conexão mais antiga).
- **(b)** `aceiteAutomatico` e `raioKm` são só gravados.
- **(c)** O aviso de pedido novo continua com o tipo `PEDIDO_APP` (o front já aceita `PEDIDO` e `PEDIDO_APP`).

## 0. Ler antes: o aceite é o do caixa

O aceite e a recusa da Central são **fachadas do atendimento do ECM-F010** (a fila "Pedidos do app" do PDV), e não uma
segunda máquina de estado:

1. **O Kanban lê `fulfillmentStatus`, não `status`.** O market paga no caixa: o pedido fica `AGUARDANDO_PAGAMENTO` até ser
   liquidado e vai direto a `CONCLUIDO`. Ele nunca passa por `PAGO`, `SEPARADO` nem `ENVIADO`. O front já faz isso
   (`fulfillmentDoPedido`).
2. **Aceitar exige caixa aberto.** O pedido fica com o caixa de quem aceitou, e só esse caixa o liquida. Sem caixa aberto,
   a resposta é `404 NO_OPEN_SESSION`. O painel já bloqueia ("Abra o caixa para aceitar").
3. **Entrega pede taxa no aceite.** Zero vale. Na retirada, não mande taxa.

## 1. Aceite e recusa: `ACEITE_PEDIDO_APP_ENABLED = true`

| Botão | Chamada | Quando |
|---|---|---|
| Aceitar | `POST /orders/{id}/accept` `{ deliveryFee?, sessionId? }` | `PENDENTE`. Sem `sessionId`, vale o caixa aberto do operador |
| Recusar | `POST /orders/{id}/reject` `{ reasonCode, note? }` | `PENDENTE`/`ACEITO`/`PRONTO`, antes do pagamento. `OUTRO` exige `note` (até 200) |

- A resposta é o `OrderAdminResponseDto`, com `fulfillmentStatus`, `acceptedAt`, `acceptedBy`, `rejectedAt` e
  `rejectionReasonCode`. O `aplicarDto` do store já lê esses campos.
- O prefixo `Recusado pela loja: ` agora é responsabilidade do backend. Não o monte no front quando a flag estiver ligada.

Erros com mensagem própria (já estão em `mensagemErroFluxo`). Falta um:

| Código | Mensagem sugerida |
|---|---|
| `400 ORDER_NOT_MARKETPLACE` | "Só pedido do app pode ser aceito aqui". **Novo no mapa** |

## 2. Tempo real: `CENTRAL_PEDIDOS_SSE_ENABLED = true`

O backend segue o §2 do pedido. **O nome do evento SSE é o próprio tipo**, e o JSON repete o tipo em `type`:

```
event: ORDER_STATUS_CHANGED
data: {"type":"ORDER_STATUS_CHANGED","orderId":123,"channel":"MARKETPLACE","from":"AGUARDANDO_PAGAMENTO","to":"CONCLUIDO"}
```

| Evento | Campos | Reação |
|---|---|---|
| `ORDER_CREATED` | `orderId`, `channel`, `orderNumber` (nulo no app), `createdAt` | Reler |
| `ORDER_STATUS_CHANGED` | `orderId`, `channel`, `from`, `to` | Reler |
| **`ORDER_FULFILLMENT_CHANGED`** | `orderId`, `channel` (sempre `MARKETPLACE`), `from`, `to` | Reler. **Novo**: é o único evento de aceite, separação, saída, entrega, recusa e expiração, porque nenhum deles muda `status` |
| `SOLICITACAO_CREATED` / `_UPDATED` | `solicitacaoId`, `mesa` / `status` | Reler as solicitações do QR |
| `CHAMADO_CREATED` / `_UPDATED` | `chamadoId`, `mesa`, `tipo` / `status` | Reler os chamados |

A notificação `PEDIDO_APP` de pedido novo passou a ir também para quem tem `ONLINE_ORDER_READ`, além de `PDV_SALE_MANAGE`.
O `targetUrl` dela continua `/pdv`.

O evento é efêmero: quem estava desconectado não o recebe. Releia tudo ao reconectar e ao voltar para a aba. A expiração
das solicitações e dos chamados do QR não gera evento; esconda-os pelo `criadoEm`.

### 2.1 Limite de streams: agora é 429

- O limite subiu de 5 para 10 streams por usuário.
- A 11ª conexão recebe `429` com `errorCode: "SSE_CONNECTION_LIMIT"` e `Retry-After: 30`. Não recebe mais 401.
- **A sessão continua válida.** Não renove o token e não conte essa recusa em `MAX_AUTH_RETRIES`.
- **A conexão mais antiga não é fechada.** Fechá-la geraria um pingue-pongue: a aba derrubada reconecta e derruba outra.

## 3. Permissões: `CENTRAL_PEDIDOS_PERMISSOES_ENABLED = true`

| Permissão | O que libera | Perfis |
|---|---|---|
| `ONLINE_ORDER_READ` | `GET /orders` (exige `channel=MARKETPLACE` ou `MESA` para quem não tem `ORDER_READ`; sem o filtro, `403`), `GET /pdv/app-orders*`, `/salao/mesas`, `/salao/ocupacoes`, `/pdv/solicitacoes-sessao`, `/pdv/chamados-mesa`, os eventos SSE, `GET /central-pedidos/config` e `/indicators` | admin, dev, atendente |
| `ONLINE_ORDER_MANAGE` | Aceitar, recusar e avançar pedido do app (`/orders/{id}/accept\|reject\|status`, `PATCH /orders/{id}/delivery` e `POST /pdv/app-orders/{id}/*`), recusar solicitação do QR e atender chamado | admin, dev, atendente |
| `ONLINE_CONFIG_MANAGE` | `PUT /central-pedidos/config` e `PATCH /estoque/products/visibility` | admin, dev |

- O perfil de gerente não vem seedado. Ele recebe as três permissões pela tela de perfis.
- Aceitar solicitação do QR continua exigindo `PDV_COMANDA_MANAGE`, porque abre comanda.
- A liquidação (`/settle`) continua exigindo a permissão de venda do PDV.
- O histórico do cliente com `GET /orders?customerId=` continua exigindo `ORDER_READ`.

## 4. Configuração do recebimento: `CENTRAL_PEDIDOS_CONFIG_ENABLED = true`

`GET /central-pedidos/config` e `PUT /central-pedidos/config` seguem o mesmo formato do `CentralPedidosConfig` do front,
**mais `pixChave`** (até 120 caracteres).

- O `GET` sempre responde `200`. Antes do primeiro salvamento, devolve o padrão. O ramo de 404 do
  `recebimento-config` deixa de ser usado; pode ficar como proteção.
- O `PUT` substitui tudo e devolve o que gravou.
- Se uma regra entre campos for violada, a resposta é `400 INVALID_CENTRAL_CONFIG`, com todos os problemas na mensagem.
  As regras são as mesmas de `errosDaConfig`, mais o limite de 120 caracteres da chave Pix.

O que cada campo passa a governar no servidor:

| Campo | Efeito |
|---|---|
| `appRecebendo` + `horarios` | Checkout do app: `409 LOJA_FECHADA` |
| `entrega.habilitada` / `retiradaHabilitada` | Checkout: `409 MODALIDADE_INDISPONIVEL` |
| `entrega.pedidoMinimo` | Checkout de entrega abaixo do mínimo (pelo valor dos itens): `409 PEDIDO_MINIMO` |
| `entrega.bairros` | Com bairros na lista, entrega em outro bairro: `409 FORA_DA_AREA`. Também define a `taxaSugerida` do aceite |
| `entrega.taxaFixa` | `taxaSugerida` quando o bairro não está na lista |
| `qrRecebendo` | Nova solicitação pelo QR: `409 QR_PAUSADO` |
| `expiracaoSolicitacaoMin` | Validade da solicitação do QR. Era fixa em 30 |
| `pixChave` | Vai no aviso de aceite da entrega (e-mail e app) e no `atendimento.pixChave` do app |
| `tempoPreparoMin` / `tempoEntregaMin` | Só estimativa, exposta em `GET /shop/store-status` |
| `aceiteAutomatico`, `entrega.raioKm` | **Só gravados**. Mostre "em breve" ou um aviso ao lado |

**Visibilidade em massa:** `PATCH /estoque/products/visibility { skus, visibleInMarketplace }` responde `204`.

- Aceita até 500 SKUs.
- Se algum SKU não existir, a resposta é `404 PRODUCT_NOT_FOUND` e nenhum produto muda.
- O catálogo público (`/shop/catalog` e o detalhe) **passou a esconder** produto com `visibleInMarketplace = false`.
  Antes ignorava esse campo, e o toggle não tinha efeito.

## 5. Indicadores: `CENTRAL_PEDIDOS_INDICADORES_ENABLED = true`

`GET /central-pedidos/indicators?date=AAAA-MM-DD` usa o formato de `CentralPedidosIndicadores` do front.

- `porCanal` vem sempre com `MARKETPLACE` e `MESA`, nesta ordem.
- `taxaRecusa` é uma fração com 4 casas e pode passar de 1 num dia com recusa de pedido da véspera. Limite a exibição a 100%.
- Recusa antiga, sem código, conta como `OUTRO`.
- `solicitacoes[].motivo` pode vir nulo. O front já trata isso ("Sem motivo").
- `cancelamentos[].valor` e `pendentesAgora` agora vêm preenchidos. Se quiser, mostre-os: hoje estão tipados, mas não são lidos.

## 6. Fluxo do card (§6)

### 6.1 `SEPARACAO_ENTREGA_ENABLED = true`

- `POST /pdv/app-orders/{id}/pronto` na entrega **já liquidada** leva o pedido a `PRONTO` e preenche `prontoEm`.
- Na entrega ainda não paga, a resposta é `409 PEDIDO_APP_NAO_PAGO`.
- `saiu` agora sai de `ACEITO` ou de `PRONTO`.
- O cliente recebe "Pedido separado, aguardando o entregador".
- O `acoesDoPedido` do front já está pronto para isso.

### 6.2 `LINK_VIAGEM_ENABLED = true`

- `POST /pdv/app-orders/{id}/saiu` aceita `{ "linkViagem": "https://…" }`: até 500 caracteres, só http/https.
  Fora disso, a resposta é `400 INVALID_TRACKING_URL`.
- O link fica em **`delivery.trackingUrl`**, campo novo no `DeliveryResponseDto` e também aceito no
  `PATCH /orders/{id}/delivery`.
- O link vai no aviso ao cliente.

### 6.3 Avisos no app do cliente

Cada etapa vira e-mail **e** notificação `PEDIDO_CLIENTE` para a conta do cliente, com `targetUrl` `/pedidos/{id}`. As
etapas são:

- aceite (na entrega, com o total com frete e a chave Pix);
- separado;
- saiu (com o link da viagem);
- entregue;
- recusa;
- cancelamento ou expiração;
- **"Pagamento confirmado"** na liquidação.

Na fila do PDV e da Central, o `atendimento` traz:

- `mensagem`: o texto que o cliente recebeu na etapa. Serve de base para a mensagem pronta do WhatsApp.
- `taxaSugerida`: só na entrega `PENDENTE`, para pré-preencher o modal da taxa.

### 6.4 Chave Pix

A chave é o `pixChave` de `/central-pedidos/config` (seção 4).

## 7. Ajustes no frontend-admin (conferidos no código)

1. **`central-pedidos.contrato.ts`**:
   - Adicionar `ORDER_FULFILLMENT_CHANGED` ao `TIPOS_EVENTO_CENTRAL` e ao `EventoCentralPedidos`. O campo do JSON é
     `type`, não `tipo`; como o avisos filtra por `e.evento`, isso só afeta a tipagem.
   - Sem o novo evento, com a flag de SSE ligada, aceite, separação e saída feitos em outra aba só aparecem no polling.
2. **`central-pedidos-avisos.service.ts`**: assinar também `SOLICITACAO_*` e `CHAMADO_*` em `eventoNomeado$`. Hoje estão
   no conjunto, mas ninguém as consome. O QR depende só das notificações `SOLICITACAO_SESSAO`/`CHAMADO_MESA`, que não
   avisam quando outra pessoa aceita ou recusa.
3. **`notification.service.ts`**: criar um ramo próprio para `resp.status === 429` antes do `!resp.ok`.
   - Esperar `Retry-After` (30 s) e reconectar.
   - Não chamar `initSession()` nem contar em `MAX_AUTH_RETRIES`.
   - Hoje o 429 cai no backoff genérico. Funciona, mas sem respeitar o `Retry-After`.
4. **`pedido-app-painel`** e **`central-pedidos-fluxo.models.ts`**:
   - `linkDaViagem` e o prefill de `linkTexto` devem ler `delivery.trackingUrl ?? delivery.trackingCode`. Hoje leem só
     `trackingCode`, e com `LINK_VIAGEM_ENABLED` o link "sumiria" do painel.
   - Com a flag ligada, não faça mais o `PATCH /orders/{id}/delivery` com `trackingCode` antes do `/saiu`.
5. **`recebimento-config.component.ts`**:
   - Incluir `pixChave` no formulário (`maxlength` 120) e em `configPadrao()`.
   - Em `errosDaConfig`, acrescentar "Chave Pix com mais de 120 caracteres".
6. **`whatsapp-mensagem.dialog.ts`**: com `CENTRAL_PEDIDOS_CONFIG_ENABLED`, ler a chave do `GET /central-pedidos/config` e
   deixar o `localStorage` (`mahal.central-pedidos.pix`) só como fallback. Para a mensagem pronta, considerar usar
   `atendimento.mensagem`.
7. **Modal da taxa no aceite**: pré-preencher com `atendimento.taxaSugerida` quando vier.
8. **`mensagemErroFluxo`**: adicionar `ORDER_NOT_MARKETPLACE` e `INVALID_TRACKING_URL` ("O link precisa começar com
   https://"). Na tela de configuração, `INVALID_CENTRAL_CONFIG` mostra a `message` como veio.
9. **`central-pedidos.flag.ts`**:
   - O comentário de `CENTRAL_PEDIDOS_SSE_ENABLED` ("Desligada: polling") não está exato: o polling de 30 s com a aba
     aberta roda nos dois casos.
   - Com o SSE ligado, dá para espaçar esse polling (ex.: 2 min), porque `ORDER_FULFILLMENT_CHANGED` cobre as etapas.
10. **`Docs/PROMPT_BACKEND_CENTRAL_PEDIDOS.md`**: o cabeçalho ainda descreve o fallback antigo (Aceitar →
    `/status SEPARADO`, Recusar → `/cancel`), mas o código usa `/pdv/app-orders/{id}/aceitar|recusar`. Marque o
    documento como entregue e aponte para este.

Ordem sugerida para ligar as flags, depois do `generate:api`:

| Ordem | Flags | Depende de |
|---|---|---|
| 1 | `CENTRAL_PEDIDOS_PERMISSOES_ENABLED` | Dar as permissões ao perfil de gerente |
| 2 | `ACEITE_PEDIDO_APP_ENABLED` | — |
| 3 | `CENTRAL_PEDIDOS_SSE_ENABLED` | Itens 1 a 3 da seção 7 |
| 4 | `SEPARACAO_ENTREGA_ENABLED` | — |
| 4 | `LINK_VIAGEM_ENABLED` | Item 4 da seção 7 |
| 5 | `CENTRAL_PEDIDOS_CONFIG_ENABLED` | Itens 5 e 6 da seção 7 |
| 5 | `CENTRAL_PEDIDOS_INDICADORES_ENABLED` | — |

## 8. Para o frontend-market (repassar)

- Antes do checkout, chamar `GET /shop/store-status` (público). Ele responde `{ aberto, appRecebendo, entregaHabilitada,
  retiradaHabilitada, tempoPreparoMin, tempoEntregaMin, pedidoMinimo, taxaFixa, bairros[] }`. Com `aberto = false`,
  mostre "loja fechada" e esconda as modalidades desligadas.
- O checkout pode responder `409` com estes códigos: `LOJA_FECHADA`, `MODALIDADE_INDISPONIVEL`, `PEDIDO_MINIMO` e
  `FORA_DA_AREA`. O bairro é o `district` do endereço.
- `GET /shop/orders/{id}` traz:
  - `atendimento.mensagem`: o texto da etapa;
  - `atendimento.pixChave`: na entrega aceita que falta pagar;
  - `delivery.trackingUrl`: depois da saída.
- As notificações `PEDIDO_CLIENTE` chegam pelo `/notifications` (e pelo stream, se o app o abrir), com `targetUrl`
  `/pedidos/{id}`.
- O QR da mesa pode responder `409 QR_PAUSADO` na solicitação: mostre "chame um atendente".
