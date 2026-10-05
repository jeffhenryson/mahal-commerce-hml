# Prompt — Cadastro e gestão de usuários no frontend

> Cole este prompt numa sessão aberta no repositório do **frontend**. Ele é autocontido.

---

Você é um engenheiro sênior de frontend. Primeiro **investigue e diagnostique**; depois proponha
(e só então aplique, se eu aprovar) as correções.

## Sintomas
1. Ao cadastrar um usuário **totalmente novo**, a tela dizia que o usuário **já estava registrado**.
2. A **gestão de usuários** (listar, criar, editar, ativar/desativar, atribuir roles) não funciona.

## O que mudou no backend (2026-10-05)
- **PLAT-C053** — antes, se o e-mail de verificação falhasse, `POST /auth/register` gravava a conta
  (desabilitada) e respondia 503; a nova tentativa dava **409 `USERNAME_ALREADY_EXISTS`**. Agora a
  falha **desfaz o cadastro**: 503 `EMAIL_DELIVERY_FAILED` significa "nada foi gravado, pode tentar
  de novo". Contas órfãs criadas antes da correção ainda dão 409; o usuário as recupera com
  `POST /auth/resend-verification` + `POST /auth/verify-email`, e a senha que vale é a do cadastro original.
- **PLAT-C054** — `GET /users` agora lista **só operadores**: clientes da loja (`/shop/register`) saíram
  da listagem. Atribuir ou remover role de conta de cliente responde **409 `CUSTOMER_ACCOUNT_ROLES_IMMUTABLE`**.
- **PLAT-C055** — uma corrida no INSERT de usuário agora responde `USERNAME_ALREADY_EXISTS`/`EMAIL_ALREADY_EXISTS`
  em vez do genérico `DATA_INTEGRITY_VIOLATION`.

Nenhum endpoint, campo de request/response ou permissão mudou. Mudaram só o comportamento e os
códigos de erro acima.

## Contrato da API

Corpo de erro (`ApiError`): `{ message, errorCode, timestamp, path, traceId }`.
**Decida a mensagem pelo `errorCode`, nunca só pelo status HTTP.**

| Ação | Método e rota | Auth | Corpo | Sucesso | Erros (`errorCode`) |
|---|---|---|---|---|---|
| Autocadastro público | `POST /auth/register` | nenhuma | `{ username (3–80), password, email }` | **201** sem corpo — conta **desabilitada** até verificar e-mail | 409 `USERNAME_ALREADY_EXISTS` / `EMAIL_ALREADY_EXISTS`; 503 `EMAIL_DELIVERY_FAILED` (nada gravado, pode repetir); 503 **sem corpo** (autocadastro desligado); 400 validação / `INVALID_PASSWORD` |
| Verificar e-mail | `POST /auth/verify-email` | nenhuma | `{ code }` | 204 — conta ativada | 400 `VERIFICATION_CODE_INVALID` / `VERIFICATION_CODE_EXPIRED`; 409 `EMAIL_ALREADY_VERIFIED` |
| Reenviar código | `POST /auth/resend-verification` | nenhuma | `{ email }` | 204 sempre (não revela se o e-mail existe) | — |
| **Admin cria usuário** | `POST /users` | `USER_CREATE` | `{ username, password, email?, roles?: string[] }` | **201** + `UserResponseDTO`, conta **habilitada** | 409 `USERNAME_ALREADY_EXISTS` / `EMAIL_ALREADY_EXISTS`; 404 `ROLE_NOT_FOUND`; 403 |
| Listar operadores | `GET /users?search&enabled&sortBy&sortDir&page&size` | `USER_READ` | — | `{ content, page, size, totalElements, totalPages }` — `page` começa em **0**, `size` máx. 100, `sortBy ∈ id,username,email,enabled,createdAt` | 403 |
| Buscar | `GET /users/{id}` | `USER_READ` | — | `UserResponseDTO` | 404 `USER_NOT_FOUND` |
| Editar | `PATCH /users/{id}` | `USER_UPDATE` | `{ username?, email? }` | 200 `UserResponseDTO` | 409 `USERNAME_ALREADY_EXISTS` / `EMAIL_ALREADY_EXISTS` |
| Desativar / ativar | `PUT /users/{id}/disable` · `PUT /users/{id}/enable` | `USER_STATUS` | — | 204 | 404 |
| Excluir | `DELETE /users/{id}` | `USER_DELETE` | — | 204 | 403 |
| Atribuir / remover role | `POST` / `DELETE /users/{username}/roles/{roleName}`: **username**, não id; role com prefixo (`ROLE_ADMIN`) | `USER_ROLE_ASSIGN` | — | 204 | 404 `USER_NOT_FOUND` / `ROLE_NOT_FOUND`; 409 `CUSTOMER_ACCOUNT_ROLES_IMMUTABLE`; 403 para `ROLE_DEV` sem elevação |
| Perfil próprio | `GET /users/me`, `PATCH /users/me`, `PUT /users/me/password` | autenticado | — | — | — |

Login: `POST /auth/login` devolve o access token no body e o refresh token num cookie `HttpOnly` com `Path=/auth`.
Uma conta não verificada recebe **401** no login, sem revelar o motivo.

## Checklist de investigação

### Cadastro
1. **Qual endpoint cada tela usa?** Liste todas as chamadas a `/auth/register` e a `POST /users`. A tela de
   **gestão** (admin criando usuário) deve usar `POST /users`. Com `/auth/register`, o usuário nasce
   desabilitado, não consegue logar e qualquer reenvio dá 409.
2. **Duplo envio:** o botão fica desabilitado durante a requisição? Há `onSubmit` + `onClick`, `useEffect`
   sem guarda, React StrictMode disparando duas vezes, ou Enter + clique?
3. **Repetição automática** (interceptor axios/fetch, `axios-retry`, `retry` de mutation no React Query, SWR, RTK Query):
   - POST é repetido em 5xx, 503, timeout ou erro de rede?
   - no refresh de token após 401, o POST original é reenviado? `/auth/*` passa por esse interceptor?
4. **Mapeamento de erro:** onde nasce a mensagem "já registrado"? Ela depende do `errorCode` ou aparece
   para qualquer 409, qualquer erro, ou para o 503?
5. **Depois do 201 do autocadastro**, a tela leva para a verificação de e-mail (campo de código + botão
   "reenviar código")? Ou tenta logar e mostra um erro enganoso?
6. Os nomes dos campos do corpo batem com o contrato?

### Gestão de usuários
7. O token do usuário logado tem `USER_READ`, `USER_CREATE`, `USER_UPDATE`, `USER_DELETE`, `USER_STATUS`
   e `USER_ROLE_ASSIGN`? Como o front decide o que mostrar? Qual status chega na tela (401 ou 403)?
8. A listagem lê `content`/`totalElements`/`totalPages` (não `items`/`data`/`total`) e envia `page` a partir de 0?
9. Métodos e rotas: editar é `PATCH`; ativar/desativar são `PUT .../enable|disable`; roles usam **username**
   na URL e o nome completo da role. A lista de roles vem de `GET /roles`?
10. CORS em hml/prod: por padrão só são permitidos os headers `Authorization` e `Content-Type`. O front envia
    algum header extra (`X-Requested-With`, `X-Tenant`, ...) que derrube o preflight?
11. A base URL e o proxy apontam para o ambiente certo?

## Correções esperadas no front (aplicar após o diagnóstico)
- Mensagens guiadas por `errorCode`:
  - `USERNAME_ALREADY_EXISTS`: "Esse nome de usuário já está em uso";
  - `EMAIL_ALREADY_EXISTS`: "Esse e-mail já está cadastrado", com o link "Não recebeu o código? Reenviar",
    que chama `/auth/resend-verification` e leva à tela de verificação;
  - `EMAIL_DELIVERY_FAILED` no autocadastro: "Não conseguimos enviar o e-mail agora. Tente novamente",
    com o formulário preservado e **sem** dizer que a conta existe;
  - 503 sem corpo: "Cadastro indisponível";
  - `CUSTOMER_ACCOUNT_ROLES_IMMUTABLE`: "Conta de cliente da loja — roles não podem ser alteradas".
- Nenhum retry automático em POST; o botão fica desabilitado enquanto a requisição está em andamento.
- A tela de gestão usa `POST /users` (com `roles`), nunca `/auth/register`.
- Depois do autocadastro, o usuário vai para a tela de verificação de e-mail.

## Como reproduzir
No DevTools → Network, faça um cadastro novo e anote quantos requests saem por clique, com endpoint,
status e `errorCode` de cada um. O padrão `201 → 409` indica envio duplicado ou retry.

## Entrega esperada
1. Para cada item do checklist: **OK** ou **Problema**, com `arquivo:linha` e evidência.
2. A causa raiz confirmada.
3. As correções propostas, separando o que é do front e o que ainda precisaria mudar no backend.
