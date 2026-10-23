# Prompt para o frontend-market: pedido pago no caixa, entrega com taxa do caixa e acompanhamento (23/10/2026)

Backend: ECM-F009..F011, migration V174. Erros no formato de sempre: `{ errorCode, message }`.
Contrato completo em `docs/api-reference.md` § "Pedidos do app pagos no caixa".

**Mudança de regra:** o app **não cobra mais online**. O cliente escolhe retirada ou entrega e paga no caixa:
retirada → crédito, débito, Pix ou dinheiro no balcão; entrega → Pix que a loja manda pelo WhatsApp depois de
aceitar o pedido e definir a taxa.

## 1. Checkout (`src/app/features/checkout-payment/checkout-payment.ts`)

Hoje o front monta um `OrderPayload` completo e manda `POST /shop/checkout` com corpo `null`. Passar a mandar:

```json
{
  "deliveryType": "RETIRADA",      // ou "ENTREGA"
  "address": { "street": "...", "number": "...", "complement": null, "zipCode": "18600-000",
               "district": "Centro", "city": "Botucatu", "state": "SP", "reference": null },   // só na ENTREGA
  "method": "MOTOBOY_LOJA",        // ENTREGA: MOTOBOY_LOJA (local_botucatu) | APP_99 | UBER_FLASH (app_delivery)
  "paymentPreference": "DEBITO",   // RETIRADA: PIX | CREDITO | DEBITO | DINHEIRO · ENTREGA: só PIX
  "notes": "Interfone 12"
}
```

- Mapear os modos atuais: `pickup` → `RETIRADA`; `local_botucatu` → `ENTREGA` + `MOTOBOY_LOJA`;
  `app_delivery` → `ENTREGA` + `APP_99` ou `UBER_FLASH`, conforme o cliente escolher.
- **Remover:** campos de cartão, boleto e o redirecionamento para `checkoutUrl` (vem `null`). Na entrega,
  oferecer só Pix. Na retirada: Pix, crédito, débito, dinheiro.
- **Frete:** remover os preços inventados por hash de CEP (`shipping.service.ts`) e a tabela de
  `botucatu-zones.ts` como valor cobrado. Mostrar "Taxa de entrega: a confirmar pela loja". A ViaCEP pode
  continuar, só para preencher o endereço.
- Erros: `400 INVALID_DELIVERY` (endereço incompleto, método ausente, entrega com pagamento que não seja
  Pix), `409/422` de estoque e preço como hoje, `429 RATE_LIMIT_EXCEEDED` (10 checkouts em 10 min):
  "Muitas tentativas, aguarde alguns minutos".
- Tela de confirmação: "Pedido #id recebido. A loja vai confirmar em instantes." Na entrega, completar com
  "Você vai receber o valor com a taxa e o Pix pelo WhatsApp". Mostrar o `atendimento.prazo` ("Seu pedido
  fica reservado até 22h").
- O link de WhatsApp que leva os dados do pedido pode continuar como canal extra, mas deixou de ser o
  único lugar onde entrega e pagamento existem.

## 2. Meus pedidos (`/conta/pedidos`, `/conta/pedidos/:id`, `/pedido/:id`)

`GET /shop/orders` e `GET /shop/orders/{id}` agora trazem `atendimento`:

```json
"atendimento": { "status": "ACEITO", "tipo": "ENTREGA", "preferenciaPagamento": "PIX",
                 "observacao": null, "prazo": "2026-10-24T01:00:00Z", "motivo": null,
                 "criadoEm": "...", "aceitoEm": "...", "prontoEm": null, "saiuEm": null,
                 "concluidoEm": null, "encerradoEm": null }
```

Linha do tempo (o pedido pago aparece como `status: "CONCLUIDO"` do pedido):

| `atendimento.status` | Texto para o cliente |
|---|---|
| `PENDENTE` | Aguardando a loja aceitar |
| `ACEITO` (retirada) | Aceito, estamos separando |
| `ACEITO` (entrega, pedido não `CONCLUIDO`) | Aceito: total R$ `totalPayable` com a taxa. Aguardando o Pix |
| `ACEITO` (entrega, pedido `CONCLUIDO`) | Pagamento confirmado, preparando para sair |
| `PRONTO` | Pronto para retirada: pague no caixa ao buscar |
| `SAIU_PARA_ENTREGA` | Saiu para entrega |
| `RETIRADO` / `ENTREGUE` | Concluído |
| `RECUSADO` | Recusado pela loja: `motivo` |
| `CANCELADO` | Cancelado por você |
| `EXPIRADO` | Expirou: não foi pago até o fim do dia |

- `totalPayable` já inclui a taxa depois do aceite; mostrar `delivery.fee` separada quando for maior que 0.
- Atualizar o detalhe por polling (ex.: a cada 30 s com a aba visível) enquanto o status não for final.
- Cancelar (`POST /shop/orders/{id}/cancel`) só enquanto o pedido não estiver `CONCLUIDO`.

## 3. Ambiente

- `environment.prod.ts` / `environment.hml.ts`: trocar o `apiUrl` `localhost` pela URL pública da API.
- Pedir para incluir o domínio do market em `CORS_ALLOWED_ORIGINS` do backend (prod e hml).
- O catálogo responde `503 DEFAULT_WAREHOUSE_NOT_CONFIGURED` se `estoque.warehouse.default-code` não estiver
  configurado: tratar com uma mensagem de "loja em manutenção".
