# Prompt para o frontend-admin: fila "Pedidos do app" no caixa (23/10/2026)

Backend: ECM-F010/F011, migration V174. Erros no formato de sempre: `{ errorCode, message }`.
Contrato completo em `docs/api-reference.md` § "Pedidos do app pagos no caixa".

O cliente faz o pedido no `frontend-market` e **paga no caixa**. O pedido entra numa fila; um operador aceita
no próprio caixa aberto, separa ou despacha, e recebe pela liquidação que já existe.

## 1. Aba "Pedidos do app" na tela do caixa

- Lista: `GET /pdv/app-orders` (`PDV_READ`). Sem `status`, vêm `PENDENTE`, `ACEITO`, `PRONTO` e
  `SAIU_PARA_ENTREGA`, do mais antigo ao mais novo. Cada item:
  `{ pedido: OrderResponseDTO, atendimento, clienteId, clienteNome, clienteContato, clienteEmail }`.
- Separar em duas seções: **Novos** (`PENDENTE`, de qualquer caixa) e **Meus** (`GET
  /pdv/sessions/{sessionId}/app-orders`, os aceitos pelo caixa aberto do operador).
- Cartão: `#pedido.id`, cliente e telefone (botão WhatsApp com `clienteContato`), itens, Retirada/Entrega
  (endereço e método `MOTOBOY_LOJA`/`APP_99`/`UBER_FLASH`), preferência de pagamento, observação,
  `pedido.totalPayable` e o prazo (`atendimento.prazo`, "reservado até 22:00").
- Badge com a contagem de `PENDENTE` no menu do PDV.

## 2. Tempo real

- O SSE de notificações (`/notifications/stream`) entrega `type: "PEDIDO_APP"` (pedido novo; e cliente
  cancelou um pedido já aceito). Ao receber: tocar um som, mostrar toast e **reler a fila**. Também reler
  ao voltar para a aba e a cada 60 s, porque o aviso pode não chegar.

## 3. Ações

| Botão | Chamada | Quando |
|---|---|---|
| Aceitar | `POST /pdv/app-orders/{id}/aceitar` `{ sessionId, deliveryFee }` | `PENDENTE`. Na **entrega** abrir modal pedindo a taxa (obrigatória, 0 vale); na retirada sem taxa |
| Recusar | `POST /pdv/app-orders/{id}/recusar` `{ motivo }` | `PENDENTE`/`ACEITO`/`PRONTO`, antes do pagamento. Motivo obrigatório (vai ao cliente) |
| Pronto | `POST /pdv/app-orders/{id}/pronto` | Retirada `ACEITO` |
| Receber | `POST /pdv/sessions/{sessionId}/orders/{id}/settle` `{ payments }` | Retirada `ACEITO`/`PRONTO` (cliente no balcão) ou entrega `ACEITO` (Pix conferido). **Valor exato de `totalPayable`**, sem troco: usar a mesma tela de pagamento do balcão, com troco bloqueado |
| Saiu p/ entrega | `POST /pdv/app-orders/{id}/saiu` | Entrega `ACEITO` **já recebida** (pedido `CONCLUIDO`) |
| Entregue | `POST /pdv/app-orders/{id}/entregue` | `SAIU_PARA_ENTREGA` |
| Assumir | `POST /pdv/app-orders/{id}/assumir` `{ sessionId }` | Pedido `ACEITO`/`PRONTO` de um caixa que já fechou |

- Depois de aceitar uma entrega: atalho "Mandar Pix pelo WhatsApp" com texto pronto: "Pedido #id: R$
  totalPayable (com taxa de entrega R$ fee). Pix: <chave da loja>".
- Erros:
  - `409 PEDIDO_APP_TRANSICAO_INVALIDA` (etapa fora de ordem, ou outro operador aceitou primeiro): reler a fila.
  - `409 PEDIDO_APP_OUTRO_CAIXA`: o pedido está com outro caixa aberto.
  - `409 PEDIDO_APP_NAO_PAGO`: receber antes de despachar.
  - `400 INVALID_DELIVERY`: taxa ausente na entrega ou presente na retirada.
  - `403 SESSION_NOT_OWNED`, `409 CASH_REGISTER_SESSION_CLOSED`: abrir o caixa.
- Sem caixa aberto, a aba é só leitura e mostra "Abra o caixa para aceitar pedidos".

## 4. Compatibilidade

`GET /pdv/pending-online-orders` continua existindo, mas a tela nova o substitui. O pedido recebido aparece em
`/pdv/sessions/{id}/sales` e no fechamento do caixa como qualquer venda (canal `MARKETPLACE`).
