# Resposta do backend: pendências do admin (05/11/2026)

Migrations novas: **V188** e **V189**. Depois de subir o backend, rode `npm run fetch:spec && npm run generate:api`.
O código foi escrito sem compilar nem rodar testes neste ambiente.

## Resumo

| # | Item | Situação |
|---|---|---|
| 1 | Segurança de usuários | **Implementado** |
| 2 | Frete na análise de vendas | **Implementado**, com frete e taxa separados |
| 3 | Kit no balcão | **Implementado** |
| 4 | Cashback retroativo | **Implementado** |
| 5 | Integrações por tela | Pendente, rodada própria |
| 6 | Notificações | **Implementado**, seguindo o `failed-email.service.ts` |
| 7 | Compras | Proposta em `docs/dominios/compras/proposta-pedido-cotacao.md` |
| 8 | Schema 200 em `/orders/analytics/*` | **Corrigido** |
| 9 | Schema `Item` | **Corrigido**, junto com `Step`, `Line` e `Payment` |
| 10 | Content type `*/*` | **Corrigido** |
| 11 | Erro da chave Pix | **Corrigido**: agora é `INVALID_CENTRAL_CONFIG` |
| 12 | Filtro `visibleInMarketplace` | **Implementado** |
| 13 | Apagar meta | **Implementado** |
| 14 | Aviso de kit travado | **Já existia**, ver abaixo |
| 15 | Opcionais | Fora desta rodada |

## 1. Segurança de usuários

O contrato é `Docs/PROMPT_BACKEND_USUARIOS_ADMIN.md`. Para ligar: regere o client, ponha `USUARIO_ADMIN_ENABLED = true`.
`USER_SECURITY_MANAGE` já vem em ADMIN e DEV. Outros perfis recebem pela tela.

**Diferenças em relação ao contrato:**

- `security.2fa.required` vale só para operadores. O app do cliente não tem tela de cadastro de 2FA.
- `GET /users/{id}` de conta de dev segue o critério da listagem: exige `ROLE_DEV` ou `DEV_ELEVATED`, sem pedir token
  elevado para ler. As rotas de escrita exigem `DEV_ELEVATED`.
- Token de pendência inválido responde `400 PENDING_LOGIN_TOKEN_INVALID`.
- O e-mail de 2FA exigido cita o Google e o Microsoft Authenticator, mas sem os links das lojas.
- Quem entra pelo Google com 2FA ativo e tem senha temporária recebe a troca de senha depois do código.
- `PATCH /users/{id}`: `fullName` e `phone` ausentes mantêm o valor, e `null` limpa.
- Amostras de e-mail: agora são **36**, com `TWO_FACTOR_REQUIRED` e `EMAIL_CHANGED_BY_ADMIN`.

## 2. Frete na análise

`GET /orders/summary` ganha `deliveryFeeTotal` e `serviceFeeTotal`. A soma de `payment-methods` é
`totalRevenueNet + deliveryFeeTotal + serviceFeeTotal`. A diferença vinha também da taxa de serviço da mesa, não só do
frete. `payment-methods` continua devolvendo lista, e `netAmount` não mudou.

## 3. Kit no balcão

`SaleRequest.kits[]` passou a ser `SaleKitSelectionRequest`. Cada escolha aceita `note` e `discountAmount`. Esse desconto
exige `PDV_SALE_DISCOUNT` e conta no teto, enquanto o desconto do próprio kit segue fora. Com isso, a venda com desconto
do chefe pode voltar a mandar `kits[]`: reparta o desconto nos `picks` (`pdv.component.ts`, `kitsPorSelecao`).

`POST /pdv/kits/quote`, a comanda e o carrinho seguem com `KitSelectionRequest`, sem esses campos.

## 4. Cashback retroativo

Contrato de `Docs/PROMPT_BACKEND_CASHBACK_RETROATIVO.md`, sem diferenças. Decisão sobre o "para avaliar": fiado quitado
depois do crédito não gera cashback na parte quitada, porque o percentual gravado no item é zero.

## 6. Notificações

- `GET /notifications/{id}`: só do dono, senão `404 NOTIFICATION_NOT_FOUND`.
- `NotificationResponseDto` ganha `details` (pares do alerta) e `metadata`, com `failedEmailId` na falha de e-mail.
  Só os alertas técnicos preenchem `details`. Notificação antiga vem com os dois nulos.
- `GET /failed-emails/{id}` e `POST /failed-emails/{id}/resend`, no formato de `FailedEmail` do front:
  - exigem `ROLE_DEV`;
  - reenvio a menos de 30 s responde `429`;
  - recusa do provedor responde `200` com `lastResendStatus = FAILED`;
  - a falha expira em 30 dias.

## 8 a 10. Contrato da API

O spec muda nomes, então o `generate:api` vai quebrar imports:

| Antes | Depois |
|---|---|
| `Item` | `EssenciasPerfisItem`, `EssenciaDoDiaItem`, `MesaAtualItem`, `OfflineRejectionItem`, `ReceivableItem`, `TransferSummaryItem` |
| `Step` | `KitTemplateStepRequest`, `KitTemplateStepResponse` |
| `Line` | `CigarroLine`, `KitQuoteLine` |
| `Payment` | `OfflineRejectionPayment`, `ReceivablePayment` |

`kit-montavel.models.ts` e `kit-montavel.dialog.ts` importam `Step`. O `customizedResponseType` do `ng-openapi-gen.json`
pode sair. `POST /auth/register` segue sem resposta 2xx, porque a rota sempre responde 403.

## 12 e 13

- `GET /estoque/products?visibleInMarketplace=true|false`.
- `DELETE /vendas/metas/{yyyy-MM}`: `SALES_GOAL_MANAGE`, `204`, `404 SALES_GOAL_NOT_FOUND`.

## 14. Aviso de kit travado

Já existe. O `EstoqueService` detecta a transição e manda a notificação `ESTOQUE` "Kit sem componente disponível" para
quem tem `ESTOQUE_STOCK_MANAGE`, com `targetUrl` para o kit. O texto do pedido chegou cortado: se o caso for outro, diga
qual.

## Para conferir

- **slow-movers:** o teste `encalhadosPorValorParado` já cobre produto com saldo e sem venda. Vazio indica depósito sem
  saldo positivo ou sem produto ativo e publicado.
- **Perfil de gerente:** não vem do seed (comentário da V176). Se existir em produção, dê a ele as três permissões
  `ONLINE_*` antes do deploy.
