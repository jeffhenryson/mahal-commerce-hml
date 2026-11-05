# Prompt para o frontend-admin: novas amostras no teste de e-mail (04/11/2026)

O backend está no branch `feat/pdv-caixa-sessao-ajustes` do `mahal-backend`. O enum `EmailSample`
passou de 20 para 34 valores, para que todo e-mail do sistema possa ser disparado em
Configurações > Dados da loja > Integrações. O endpoint e o contrato continuam os mesmos.

**O que muda para o usuário:** sem este ajuste, o "enviar todos" devolve resultados de amostras que a
tela não conhece, e elas aparecem sem rótulo.

## O que fazer

Regenerar os modelos da API (`email-integration-test-request.ts` e `email-test-result-dto.ts`) e
atualizar `src/app/core/integrations/email-integration.service.ts`: o tipo `EmailSample`,
`EMAIL_SAMPLE_LABELS` e `EMAIL_SAMPLE_GROUPS`.

O `USER_INVITE` já existia no backend e nunca entrou nesse arquivo. Ele entra agora, junto com os novos.

| Valor | Rótulo | Grupo |
|---|---|---|
| `USER_INVITE` | Convite de usuário | Conta e segurança |
| `EMAIL_CHANGE_REQUEST` | Pedido de troca de e-mail | Conta e segurança |
| `EMAIL_CHANGE_CONFIRMED` | Confirmação de novo e-mail | Conta e segurança |
| `CUSTOMER_WELCOME` | Boas-vindas (cliente) | Conta e segurança |
| `PASSWORD_RESET_ALERT` | Alerta: senha redefinida | Conta e segurança |
| `GOOGLE_LINKED` | Alerta: login com Google ativado | Conta e segurança |
| `ORDER_CONFIRMATION_DELIVERY` | Pedido recebido (entrega) | Pedidos e clientes |
| `ORDER_REFUND` | Pedido reembolsado | Pedidos e clientes |
| `RECEIVABLE_CREATED` | Compra marcada no fiado | Pedidos e clientes |
| `RECEIVABLE_PAID` | Pagamento de fiado recebido | Pedidos e clientes |
| `CASH_SESSION_OPENED` | Caixa aberto | Operação interna |
| `CASH_MOVEMENT` | Sangria ou suprimento | Operação interna |
| `ORDER_CANCELLED_ALERT` | Aviso: pedido cancelado ou reembolsado | Operação interna |
| `PAYMENT_CORRECTED_ALERT` | Aviso: pagamento corrigido | Operação interna |
| `PRICE_CHANGED_ALERT` | Aviso: preço alterado | Operação interna |

Dois rótulos atuais mudam, porque as variantes agora têm amostra própria:

- `ORDER_CANCELLATION`: de "Pedido cancelado/reembolsado" para "Pedido cancelado".
- `ORDER_CONFIRMATION`: de "Pedido recebido (loja online)" para "Pedido recebido (retirada)".
- `WELCOME`: de "Boas-vindas" para "Boas-vindas (equipe)".

## Teste

Em `email-integration.service.spec.ts`, trocar a contagem de 19 para 34 e ajustar o título do caso.
O teste "cada e-mail aparece em exatamente um grupo" já cobre os agrupamentos.
