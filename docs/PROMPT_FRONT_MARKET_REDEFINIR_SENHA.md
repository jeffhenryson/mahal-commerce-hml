# Prompt para o frontend-market: página de redefinir senha (05/11/2026)

O backend está no branch `feat/pdv-caixa-sessao-ajustes` do `mahal-backend`. Hoje o app tem o botão
"Esqueci minha senha", mas nenhuma página para criar a senha nova. O link do e-mail precisa abrir uma
tela do app, e não uma tela do painel.

**O que muda para o usuário:** o cliente abre o link do e-mail, cria a senha nova no app e volta para a
própria conta.

## O que fazer

Criar a rota pública `redefinir-senha`, antes do `**` em `app.routes.ts`, com o mesmo formato de
`features/confirm-email`. Ela não usa `customerAuthGuard`.

1. Ler o `token` de `?token=`. Sem token, mostrar "Link incompleto" e um link para `/conta`.
2. Mostrar os campos de senha nova e confirmação. Usar as mesmas regras do cadastro em
   `features/account/account.ts`, que espelham a `PasswordPolicy` do backend.
3. Adicionar `resetPassword(token, newPassword)` em `core/services/auth.service.ts`, chamando
   `POST /auth/reset-password` com `{ "token": "...", "newPassword": "..." }`.
4. Na resposta 204, mostrar "Senha alterada" e um botão para `/conta`. A troca encerra todas as sessões
   do cliente, então ele precisa entrar de novo.

## Erros

| Código | Mensagem |
|---|---|
| `PASSWORD_RESET_TOKEN_INVALID` | Este link não é válido. Peça um novo na sua conta. |
| `PASSWORD_RESET_TOKEN_EXPIRED` | Este link expirou ou já foi usado. Peça um novo na sua conta. |
| `VALIDATION_ERROR` | Confira se a senha segue as regras indicadas. |

Nos dois erros de link, mostrar um botão para `/conta`, onde está o "Esqueci minha senha".

## Teste

Cobrir o token ausente, o envio com sucesso e os dois erros de link.

## Depois de publicar

Só com a página no ar, definir no backend:

```
PASSWORD_RESET_CUSTOMER_FRONTEND_URL=https://mahaltabacaria.com.br/redefinir-senha
```

Antes disso, o cliente continua indo para a página do painel. Se a variável for definida antes da
publicação, o app redireciona o link para a home e o token se perde.
