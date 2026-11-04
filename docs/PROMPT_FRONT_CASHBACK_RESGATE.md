# Prompt para o frontend-admin e o market: resgate de cashback (03/11/2026)

O backend está no branch `feat/pdv-caixa-sessao-ajustes` do `mahal-backend` (migration V186).
Até aqui o cliente só **ganhava** cashback (3% do líquido pago, só com CPF). Agora ele também
**usa** o saldo no balcão, na comanda e no app, e o admin ajusta as regras.

## Regras do programa (valem nos três canais)

- Só usa cashback quem tem **CPF** no cadastro.
- O saldo precisa estar **disponível**. Cashback novo fica em carência (padrão: 7 dias) antes de liberar.
- O saldo disponível tem que chegar ao **mínimo** configurado (padrão: R$ 50,00).
- Por compra, o cliente usa no máximo o **teto**, em % da mercadoria (padrão: 50%). A conta é feita
  depois de desconto e promoção, sem taxa de serviço e sem entrega.
- O valor usado **abate o total a pagar**. Os pagamentos devem somar o total já abatido.
- A parte paga com cashback **não gera cashback novo**.
- Se o pedido for cancelado ou estornado, o cashback usado **volta ao saldo**. A parte que tiver
  vencido nesse meio-tempo não volta.

Quando o resgate quebra alguma regra, a API responde **422** com um `code`:

| code | Mensagem sugerida |
|---|---|
| `CASHBACK_CUSTOMER_REQUIRED` | Identifique o cliente para usar cashback |
| `CASHBACK_CUSTOMER_NOT_ELIGIBLE` | Cliente sem CPF não participa do cashback |
| `CASHBACK_BELOW_MINIMUM_BALANCE` | Saldo abaixo do mínimo para usar |
| `CASHBACK_INSUFFICIENT_BALANCE` | Saldo insuficiente |
| `CASHBACK_ABOVE_CAP` | Acima do limite por compra |
| `CASHBACK_REDEMPTION_DISABLED` | Uso de cashback desativado |

---

## frontend-admin

### 1. CRM › Cashback › Configurações (nova tela)

- `GET /cashback/settings` (permissão `CASHBACK_READ`)
- `PUT /cashback/settings` (permissão `CASHBACK_RATE_MANAGE`)

```json
{ "carenciaDias": 7, "expiracaoDias": 180, "resgateTetoPercent": 50, "resgateMinimoSaldo": 50.00 }
```

| Campo | Faixa |
|---|---|
| carência | 0–365 dias |
| validade | 1–3650 dias |
| teto | 0–100% (**0 desliga o uso**) |
| mínimo | ≥ 0 |

Avisar na tela que carência e validade novas só valem para o cashback ganho **daqui em diante**.

### 2. PDV › Venda de balcão

Com cliente com CPF selecionado:

1. Chamar `GET /cashback/customers/{id}/redemption-quote?amount={mercadoria}` (`PDV_SALE_MANAGE`).
   - Resposta: `{available, minimumBalance, capPercent, capAmount, maxRedeemable, reason}`.
   - Mostrar "Saldo de cashback: R$ X" e um campo "Usar R$ ___" com o máximo em `maxRedeemable`.
   - Se `reason` vier preenchido, mostrar o motivo e desabilitar o campo.
2. Na venda, `POST /pdv/sessions/{id}/sales` recebe o campo novo `cashbackToRedeem`.
   - O total a cobrar e os pagamentos passam a ser `total − cashbackToRedeem`.
   - A resposta traz `cashbackRedeemed` e `netAmount` já abatido.
3. Cupom/recibo: linha "Cashback usado − R$ X" quando `cashbackRedeemed > 0`.

A venda **offline** não aceita resgate, porque o saldo só é conferido no servidor. Esconder o campo
quando o caixa estiver offline.

### 3. Mesas › Fechar conta

- `POST /pdv/comandas/{id}/close` recebe `cashbackToRedeem`, que usa o cliente vinculado à comanda.
- O resgate entra **depois** da taxa de serviço. A taxa não diminui, mas o total a pagar sim.
- Para a prévia, usar o mesmo endpoint da venda de balcão (`PDV_COMANDA_MANAGE`), com
  `amount` = mercadoria do fechamento.
- Conta dividida: cada fechamento tem o seu teto.

### 4. Pedidos e extrato do cliente

- `OrderResponseDTO.cashbackRedeemed` agora pode vir maior que zero. Mostrar no detalhe do pedido.
- O extrato do cliente (`GET /crm/customers/{id}/cashback`) ganha dois tipos novos:
  - `REDEEMED`: valor negativo, "Usado no pedido #N".
  - `REDEMPTION_REVERSED`: valor positivo, "Devolvido (pedido cancelado/estornado)".

---

## market (app)

### 5. Carrinho / checkout

- `GET /shop/cashback?amount={total da mercadoria do carrinho}` (permissão `SHOP_CASHBACK_OWN`,
  que o cliente já tem). Resposta:

```json
{ "available": 62.40, "pending": 3.10, "expiringSoon": 0, "minimumBalance": 50.00,
  "capPercent": 50, "capAmount": 45.00, "maxRedeemable": 45.00, "reason": null }
```

- No checkout, mostrar "Usar meu cashback (até R$ X)" com o valor de `maxRedeemable`.
- Se `reason` vier preenchido, mostrar o motivo (ex.: "faltam R$ Y para poder usar").
- `POST /shop/checkout` recebe `cashbackToRedeem`. O pedido nasce com o total já abatido, e o
  caixa cobra esse valor no balcão.
- O saldo é debitado **no checkout**. Se a loja recusar, o pedido expirar ou o cliente cancelar,
  o valor volta sozinho.

### 6. Minha conta

Card "Meu cashback" usando `GET /shop/cashback` sem `amount`:

- disponível
- liberando em breve (`pending`)
- vencendo em 30 dias (`expiringSoon`)
