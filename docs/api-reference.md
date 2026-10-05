# API Reference — mahal-commerce

**Base URL (dev):** `http://localhost:8080` 
**Auth:** `Authorization: Bearer <accessToken>` em todos os endpoints, exceto os marcados como **Público**.

> Swagger UI disponível em `http://localhost:8080/swagger-ui.html` — em `dev` e `hml` apenas.
> Em `prod` o springdoc é desabilitado (PLAT-C029), e nem a UI nem `/v3/api-docs/**` são servidos.

---

## Formato de erro padrão

Todos os erros retornam `ApiError`:

```json
{
  "message": "Mensagem legível",
  "errorCode": "SNAKE_CASE_CODE",
  "timestamp": "2026-05-30T16:00:00Z",
  "path": "/auth/login",
  "traceId": "uuid-para-correlação-de-log"
}
```

### Tabela de error codes

| errorCode | HTTP | Quando ocorre |
|-----------|------|---------------|
| `INVALID_CREDENTIALS` | 401 | Username/senha errados ou conta desabilitada |
| `ACCOUNT_LOCKED` | 401 | Conta bloqueada por tentativas excessivas |
| `ACCESS_DENIED` | 403 | Token válido mas sem a permissão necessária |
| `INVALID_REFRESH_TOKEN` | 401 | Refresh token inválido |
| `REFRESH_TOKEN_EXPIRED` | 401 | Refresh token expirado — redirecionar para login |
| `REFRESH_TOKEN_REUSED` | 401 | Token já usado — possível roubo, todas as sessões encerradas |
| `TOTP_CHALLENGE_EXPIRED` | 401 | Challenge de 2FA expirou (5 min) |
| `INVALID_TOTP_CODE` | 400 | Código TOTP ou backup code inválido |
| `TOTP_CODE_REQUIRED` | 400 | Operação requer código 2FA mas ele não foi enviado (usuário tem 2FA ativo) |
| `TOTP_NOT_CONSECUTIVE` | 400 | Segundo código DEV não pertence ao período T+1 do primeiro |
| `DEV_CHALLENGE_EXPIRED` | 410 | devToken DEV expirou (TTL 90s) ou já foi consumido |
| `TOTP_ALREADY_ENABLED` | 409 | 2FA já está ativo |
| `KIT_TEMPLATE_NOT_FOUND` | 404 | Kit montável inexistente, ou inativo/invisível no canal (EST-F031) |
| `DUPLICATE_KIT_TEMPLATE_NAME` | 409 | Nome de kit montável já em uso |
| `KIT_NOT_AVAILABLE` | 422 | Kit inativo ou fora de venda no canal |
| `KIT_EMPTY` | 422 | Nenhum item escolhido |
| `KIT_REQUIRED_STEP_MISSING` | 422 | Passo obrigatório sem escolha |
| `KIT_STEP_NOT_FOUND` | 422 | Passo não pertence ao kit |
| `KIT_STEP_MAX_ITEMS_EXCEEDED` | 422 | Mais escolhas que o `maxItems` do passo |
| `KIT_ITEM_NOT_IN_STEP_CATEGORY` | 422 | Produto não é da categoria do passo |
| `KIT_ITEM_NOT_SELLABLE` | 422 | Produto inexistente, inativo, rascunho, sem preço, kit, ou invisível no canal |
| `KIT_ITEM_REMOVAL_NOT_ALLOWED` | 409 | Remoção avulsa de linha de kit na comanda — remova o kit inteiro |
| `TOTP_NOT_ENABLED` | 400 | Operação requer 2FA ativo |
| `TOTP_SETUP_REQUIRED` | 403 | Login bloqueado: `security.2fa.required=true` e o usuário ainda não ativou 2FA |
| `INVALID_PASSWORD` | 400 | Senha atual incorreta |
| `PASSWORD_RESET_TOKEN_INVALID` | 400 | Token de reset inválido |
| `PASSWORD_RESET_TOKEN_EXPIRED` | 400 | Token de reset expirado |
| `USERNAME_ALREADY_EXISTS` | 409 | Username já cadastrado |
| `EMAIL_ALREADY_EXISTS` | 409 | Email já cadastrado |
| `EMAIL_ALREADY_VERIFIED` | 409 | Email já verificado |
| `VERIFICATION_CODE_INVALID` | 400 | Código de verificação de email inválido |
| `VERIFICATION_CODE_EXPIRED` | 400 | Código de verificação expirado |
| `USER_NOT_FOUND` | 404 | Usuário não encontrado |
| `ROLE_NOT_FOUND` | 404 | Role não encontrada |
| `PERMISSION_NOT_FOUND` | 404 | Permissão não encontrada |
| `SESSION_NOT_FOUND` | 404 | Sessão não encontrada |
| `ROLE_ALREADY_EXISTS` | 409 | Role já existe |
| `PERMISSION_ALREADY_EXISTS` | 409 | Permissão já existe |
| `SKU_ALREADY_EXISTS` | 409 | SKU já cadastrado — vale para o SKU pai e para os de variação, que dividem o mesmo espaço de nomes |
| `PRODUCT_NOT_FOUND` | 404 | SKU não existe no catálogo (nem como SKU pai, nem como SKU de variação). Barra movimentação e ponto de reposição para SKU inexistente ou digitado errado |
| `DATA_INTEGRITY_VIOLATION` | 409 | Conflito com um registro já existente que escapou da validação de aplicação — tipicamente uma corrida entre duas requisições simultâneas. Refazer a operação costuma resolver |
| `OAUTH_TOKEN_INVALID` | 401 | Token Google inválido, expirado ou audience incorreto |
| `AVATAR_TOO_LARGE` | 400 | Arquivo de avatar excede 2 MB |
| `INVALID_AVATAR_FORMAT` | 400 | Formato não suportado — aceito JPEG, PNG, WebP |
| `VALIDATION_ERROR` | 400 | Campos inválidos (bean validation) |
| `UNREADABLE_BODY` | 400 | Body ausente ou JSON malformado |
| `MISSING_PARAMETER` | 400 | Parâmetro de query obrigatório ausente — a mensagem nomeia o parâmetro |
| `EMAIL_DELIVERY_FAILED` | 503 | Falha ao enviar email |
| `INTERNAL_ERROR` | 500 | Erro interno inesperado |

---

## Auth — `/auth`

### POST /auth/login — Público

```json
// Request
{ "username": "string", "password": "string" }

// Response 200 — login completo (sem 2FA)
{
  "accessToken": "eyJ...",
  "refreshToken": "opaque-token",
  "tokenType": "Bearer",
  "expiresIn": 900
}

// Response 200 — 2FA ativado (precisa de verificação)
{
  "status": "PENDING_2FA",
  "challengeToken": "string",
  "expiresInSeconds": 300
}
```

**Cookie:** `refreshToken` HttpOnly setado em `Path=/auth`, `Max-Age=604800` (7 dias), `SameSite=Strict`.  
O `refreshToken` também vem no body como fallback para clientes que não lêem cookies.  
Usar `withCredentials: true` nas chamadas a `/auth/*` para que o browser envie o cookie automaticamente.

**Erros:** `401 INVALID_CREDENTIALS`, `401 ACCOUNT_LOCKED`, `429` (rate-limit — header `Retry-After: <seg>`)

---

### POST /auth/2fa/verify — Público · Rate-limited

Completa o login quando 2FA está ativo. Usar o `challengeToken` recebido no `/login`.

```json
// Request
{
  "challengeToken": "string",
  "code": "123456"  // TOTP 6 dígitos OU backup code formato XXXX-XXXX-XXXX
}

// Response 200 → TokenPairResponse (igual ao login sem 2FA)
```

**Erros:** `400 INVALID_TOTP_CODE`, `401 TOTP_CHALLENGE_EXPIRED`, `429` rate-limit

---

### POST /auth/refresh — Público

Rotaciona o refresh token e emite novo par de tokens. Aceita cookie ou body.

```json
// Request (opcional — usa cookie automaticamente se omitido)
{ "refreshToken": "string" }

// Response 200 → TokenPairResponse
```

**Erros:** `401 INVALID_REFRESH_TOKEN`, `401 REFRESH_TOKEN_EXPIRED`, `401 REFRESH_TOKEN_REUSED`

> Ao receber `REFRESH_TOKEN_REUSED`, todas as sessões do usuário são invalidadas por suspeita de roubo de token. Redirecionar para login.

---

### POST /auth/logout — Público

```json
// Request (opcional — usa cookie se omitido)
{ "refreshToken": "string" }

// Response 204
```

Limpa o cookie `refreshToken` na resposta mesmo que o token não exista.

---

### DELETE /auth/sessions — Autenticado

Revoga **todas** as sessões do usuário logado (logout total).

```
// Response 204
```

---

### GET /auth/sessions — Autenticado

Lista as sessões ativas do usuário logado.

```json
// Response 200
[
  {
    "id": 1,
    "createdAt": "2026-05-30T10:00:00Z",
    "expiresAt": "2026-06-06T10:00:00Z",
    "ipAddress": "192.168.1.1",
    "userAgent": "Mozilla/5.0..."
  }
]
```

---

### DELETE /auth/sessions/{id} — Autenticado

Revoga uma sessão específica do usuário logado.

```
// Response 204 / 404 SESSION_NOT_FOUND
```

---

### POST /auth/forgot-password — Público

Inicia o fluxo de recuperação de senha. Sempre retorna 204 (sem disclosure de email).

```json
{ "email": "string" }
// Response 204
```

---

### POST /auth/reset-password — Público

```json
{
  "token": "string",          // token recebido por email
  "newPassword": "string"     // deve respeitar PasswordPolicy
}
// Response 204
```

**Erros:** `400 PASSWORD_RESET_TOKEN_INVALID`, `400 PASSWORD_RESET_TOKEN_EXPIRED`, `400 VALIDATION_ERROR`

---

### POST /auth/confirm-email-change — Público

Confirma a troca de email usando o código enviado ao novo endereço.

```json
{ "code": "ABC123DEF456" }   // exatamente 12 chars [A-Z0-9]
// Response 204 / 400 VERIFICATION_CODE_INVALID / 400 VERIFICATION_CODE_EXPIRED
```

---

## Registration — `/auth`

### POST /auth/register — Público

```json
// Request
{
  "username": "string",   // 3–80 chars, obrigatório
  "password": "string",   // PasswordPolicy, obrigatório
  "email": "string"       // email válido, max 254 chars, obrigatório
}
// Response 201
```

Conta criada com `enabled=false`. Email de verificação enviado automaticamente.  
**Erros:** `409 USERNAME_ALREADY_EXISTS`, `409 EMAIL_ALREADY_EXISTS`, `400 VALIDATION_ERROR`

---

### POST /auth/verify-email — Público

```json
{ "code": "ABC123DEF456" }   // 12 chars [A-Z0-9]
// Response 204 — ativa a conta (enabled=true, emailVerified=true)
```

**Erros:** `400 VERIFICATION_CODE_INVALID`, `400 VERIFICATION_CODE_EXPIRED`, `409 EMAIL_ALREADY_VERIFIED`

> `409 EMAIL_ALREADY_VERIFIED` é retornado quando o email da conta associada ao código já foi verificado. Isso permite ao frontend distinguir "reload após verificação bem-sucedida" (conta já está ativa — pode redirecionar ao login) de "código inválido" (400 — usuário digitou código errado).

---

### POST /auth/resend-verification — Público

```json
{ "email": "string" }
// Response 204 (sempre, sem disclosure). Cooldown 60s por email.
```

---

## OAuth — `/auth/oauth2`

### POST /auth/oauth2/google — Público

Login ou cadastro via conta Google. O frontend obtém um `id_token` usando o [Google Identity Services](https://developers.google.com/identity/gsi/web) e envia ao backend para validação.

```json
// Request
{ "idToken": "eyJ..." }   // id_token retornado pelo Google Sign-In

// Response 200 → TokenPairResponse (igual ao login com senha)
{
  "accessToken": "eyJ...",
  "refreshToken": "opaque-token",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Cookie:** mesmo comportamento do `POST /auth/login` — `refreshToken` HttpOnly em `Path=/auth`.  
Usar `withCredentials: true` para que o browser armazene o cookie.

**Comportamento no servidor:**

| Situação | O que acontece |
|----------|----------------|
| Google ID (`sub`) já vinculado a uma conta | Login direto na conta existente |
| Email do Google já existe em conta local | Google ID vinculado automaticamente — login na conta local |
| Email não existe | Nova conta criada com `ROLE_USER`, `emailVerified=true`, sem senha |

> Usuários criados via Google **não têm senha** e não podem usar `POST /auth/forgot-password` nem `PUT /users/me/password`. O `authProvider` deles é `GOOGLE`.

**Erro:** `401 OAUTH_TOKEN_INVALID` — token inválido, expirado ou `aud` não corresponde ao `GOOGLE_CLIENT_ID` configurado.

**No Angular (exemplo básico com Google Identity Services):**
```typescript
google.accounts.id.initialize({
  client_id: environment.googleClientId,
  callback: async ({ credential }) => {
    const res = await http.post('/auth/oauth2/google',
      { idToken: credential },
      { withCredentials: true }
    ).toPromise();
    // res = TokenPairResponse
  }
});
```

---

## 2FA TOTP — `/auth/2fa`

Todos os endpoints abaixo requerem `Authorization: Bearer <accessToken>`.

### GET /auth/2fa/status

```json
// Response 200
{
  "enabled": true,
  "backupCodesRemaining": 5   // 0 quando enabled=false
}
```

---

### POST /auth/2fa/setup

Inicia o setup de 2FA. Retorna o segredo e o URI para gerar o QR code.

```json
// Response 200
{
  "secret": "BASE32SECRET",
  "otpauthUri": "otpauth://totp/mahal-commerce:username?secret=...&issuer=mahal-commerce"
}
```

O frontend deve renderizar o `otpauthUri` como QR code (ex: biblioteca `qrcode`).  
**Erro:** `409 TOTP_ALREADY_ENABLED`

---

### POST /auth/2fa/confirm

Confirma o setup escaneando o QR e enviando o primeiro código.

```json
// Request
{ "code": "123456" }   // exatamente 6 dígitos

// Response 200
{
  "backupCodes": ["ABCD-1234-EF56", "..."]  // 8 códigos, guardar agora
}
```

**Erro:** `400 INVALID_TOTP_CODE`

> Os backup codes são exibidos **uma única vez**. O frontend deve orientar o usuário a salvá-los antes de fechar o modal.

---

### DELETE /auth/2fa

Desativa o 2FA.

```json
// Request
{
  "currentPassword": "string",
  "code": "123456"   // TOTP 6 dígitos OU backup code XXXX-XXXX-XXXX
}
// Response 204
```

**Erros:** `400 INVALID_PASSWORD`, `400 INVALID_TOTP_CODE`, `400 TOTP_NOT_ENABLED`

---

### POST /auth/2fa/replace

Troca o dispositivo 2FA: valida o código do app atual, apaga a configuração vigente e inicia um novo setup. Confirmar com `POST /auth/2fa/confirm` para ativar o novo dispositivo.

```json
// Request
{ "currentTotpCode": "123456" }   // código atual do app (6 dígitos)

// Response 200
{
  "secret": "BASE32SECRET",
  "otpauthUri": "otpauth://totp/..."
}
```

**Erros:** `400 INVALID_TOTP_CODE`, `400 TOTP_NOT_ENABLED`

---

### POST /auth/2fa/backup-codes/regenerate

Gera novos backup codes (invalida os anteriores).

```json
// Request
{ "currentPassword": "string" }

// Response 200
{ "backupCodes": ["ABCD-1234-EF56", "..."] }  // 8 novos códigos
```

**Erros:** `400 INVALID_PASSWORD`, `400 TOTP_NOT_ENABLED`

---

## DEV Elevation — `/auth/dev`

Fluxo de elevação de privilégio para a área de desenvolvedor via **duplo TOTP consecutivo**. Exige que o usuário tenha `ROLE_DEV` e 2FA ativo. O token resultante contém a authority `DEV_ELEVATED`, que protege endpoints sensíveis como `/actuator/**`.

> O access token DEV-elevado **não tem refresh token** — expira em 1h e não pode ser renovado. Novo duplo TOTP necessário após expirar.

### POST /auth/dev/first-code — Bearer + `ROLE_DEV`

Etapa 1: valida o código atual do app autenticador e reserva o período T. Retorna um `devToken` temporário (TTL 90s) para ser usado na etapa 2.

```json
// Request
{ "totpCode": "123456" }   // exatamente 6 dígitos

// Response 200
{
  "devToken": "opaque-token-base64url",
  "expiresIn": 90   // segundos até o devToken expirar
}
```

**Erros:** `400 INVALID_TOTP_CODE`, `403 ACCESS_DENIED` (sem `ROLE_DEV`)

> Após receber o `devToken`, o frontend deve aguardar o próximo ciclo de 30s do app TOTP antes de prosseguir para a etapa 2.

---

### POST /auth/dev/complete — Público

Etapa 2: valida que o segundo código pertence ao período T+1 (imediatamente consecutivo ao T registrado na etapa 1). Emite o access token DEV-elevado com TTL de 1h.

```json
// Request
{
  "devToken": "opaque-token-base64url",   // obtido na etapa 1
  "totpCode": "654321"                    // novo código do próximo período
}

// Response 200
{
  "accessToken": "eyJ...",   // JWT com DEV_ELEVATED + todas as authorities ROLE_DEV
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

**Erros:** `400 TOTP_NOT_CONSECUTIVE`, `400 INVALID_TOTP_CODE`, `410 DEV_CHALLENGE_EXPIRED`

> `DEV_CHALLENGE_EXPIRED` (410) ocorre quando o `devToken` expirou (após 90s) ou já foi usado. O frontend deve reiniciar pelo `POST /auth/dev/first-code`.

---

## Users — `/users`

> **Dois formatos de resposta de usuário:**
> - `UserProfileResponse` — retornado por `GET /users/me` e `PATCH /users/me`. Inclui `pendingEmail`.
> - `UserResponse` — retornado pelos demais endpoints (`GET /users`, `GET /users/{id}`, `POST /users`, `PATCH /users/{id}`). Inclui `avatarUrl` e `createdAt`, mas **não** inclui `pendingEmail`.

### GET /users/me — Autenticado

```json
// Response 200 → UserProfileResponse
```

---

### PATCH /users/me — Autenticado

Atualiza username e/ou email do próprio perfil.

```json
// Request
{
  "username": "string",       // 3–80 chars, obrigatório
  "email": "string",          // email válido, min 1 char, max 254, opcional (null = não altera)
  "currentPassword": "string" // obrigatório SOMENTE ao trocar email
}
// Response 200 → UserProfileResponse
```

**Fluxo de troca de email:**  
- A conta **não** é desabilitada.  
- `UserProfileResponse.pendingEmail` recebe o novo email.  
- Um código é enviado ao novo endereço — confirmar via `POST /auth/confirm-email-change`.  
- Enquanto pendente: `.email` = email atual, `.pendingEmail` = novo email.  
- Frontend pode usar `pendingEmail != null` para exibir banner "confirme seu novo e-mail".

**Erros:** `409 USERNAME_ALREADY_EXISTS`, `409 EMAIL_ALREADY_EXISTS`, `400 INVALID_PASSWORD`

---

### POST /users/me/avatar — Autenticado

Faz upload do avatar. Enviar como `multipart/form-data`, campo `file`.

```
Content-Type: multipart/form-data
file: <binary>

// Response 200
{ "avatarUrl": "http://localhost:8080/avatars/f47ac10b-uuid.jpg" }
```

**Limites de tamanho:**
- Conteúdo do arquivo: máximo **2 MB** (validado no service → `AVATAR_TOO_LARGE`)
- Boundary multipart: máximo **3 MB** (limite do servidor → `413 Payload Too Large` antes de chegar ao controller)

**Validação de formato:** feita por magic bytes (não por extensão ou `Content-Type`). Formatos aceitos: JPEG, PNG, WebP.  
**Erros:** `400 AVATAR_TOO_LARGE`, `400 INVALID_AVATAR_FORMAT`

> Ao fazer upload quando já existe um avatar, o arquivo anterior é deletado automaticamente no servidor.

---

### DELETE /users/me/avatar — Autenticado

Remove o avatar do usuário.

```
// Response 204
```

---

### GET /avatars/{filename} — **Público**

Serve o arquivo de avatar. Sem autenticação. Filename gerado pelo servidor (UUID).

```
// Response 200  Content-Type: image/jpeg | image/png | image/webp
//               Cache-Control: max-age=31536000, immutable
// Response 404  arquivo não encontrado ou filename inválido (contém ..)
```

> Como os filenames são UUIDs aleatórios, não há enumeração. Use sempre a `avatarUrl` retornada pelo perfil — nunca construa a URL manualmente.

**No Angular**, para forçar recarregamento após upload (o browser cacheia a URL antiga):
```typescript
// Adicione um query param após o upload para bustar o cache do browser:
this.avatarUrl = response.avatarUrl + '?v=' + Date.now();
```

---

### PUT /users/me/password — Autenticado

```json
// Request
{
  "currentPassword": "string",
  "newPassword": "string",        // deve respeitar PasswordPolicy
  "totpCode": "123456",           // obrigatório se o usuário tiver 2FA ativo (6 dígitos ou backup code)
  "revokeOtherSessions": false    // se true, revoga todos os refresh tokens e bloqueia JWTs anteriores
}
// Response 204
```

**Comportamento de sessão:**
- `revokeOtherSessions: false` (padrão) — apenas a senha é trocada; sessões em outros dispositivos continuam ativas.
- `revokeOtherSessions: true` — todos os refresh tokens são revogados e todos os JWTs emitidos antes deste momento são bloqueados.

**Erros:** `400 INVALID_PASSWORD`, `400 TOTP_CODE_REQUIRED`, `400 INVALID_TOTP_CODE`, `400 VALIDATION_ERROR`

---

### GET /users — Permissão: USER_READ

```
Query params:
  search:  string   (filtra username/email, parcial, case-insensitive)
  enabled: boolean
  sortBy:  "id" | "username" | "email" | "enabled" | "createdAt"  (default: "id")
  sortDir: "asc" | "desc"                           (default: "asc")
  page:    int  (default: 0)
  size:    int  (default: 20, max: 100)
```

```json
// Response 200
{
  "content": [UserResponse],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

---

### POST /users — Permissão: USER_CREATE

```json
// Request
{
  "username": "string",   // 3–80, obrigatório
  "password": "string",   // PasswordPolicy, obrigatório
  "email": "string",      // email válido, max 254, opcional
  "roles": ["ROLE_USER"]  // opcional, padrão []
}
// Response 201 + header Location: /users/{id}
```

**Erros:** `409 USERNAME_ALREADY_EXISTS`, `400 VALIDATION_ERROR`

---

### GET /users/{id} — Permissão: USER_READ

```
// Response 200 → UserResponse / 404 USER_NOT_FOUND
```

---

### PATCH /users/{id} — Permissão: USER_UPDATE

```json
// Request
{
  "username": "string",  // 3–80, obrigatório
  "email": "string"      // email válido, min 1 char, max 254, opcional (null = não altera)
}
// Response 200 → UserResponse / 404 / 409
```

> `currentPassword` é ignorado nesta rota (admin não precisa de confirmação de senha).

**Fluxo de troca de email (admin):** idêntico ao `PATCH /users/me` — a conta **não** é desabilitada, `pendingEmail` é definido e um código é enviado ao novo endereço. O usuário confirma normalmente via `POST /auth/confirm-email-change`. O evento auditado é `EMAIL_CHANGE_REQUESTED`.

---

### PUT /users/{id}/enable — Permissão: USER_STATUS

```
// Response 204 / 404 USER_NOT_FOUND
```

---

### PUT /users/{id}/disable — Permissão: USER_STATUS

```
// Response 204 / 404 USER_NOT_FOUND
```

---

### DELETE /users/{id} — Permissão: USER_DELETE

**Soft delete** — o registro é marcado como deletado (`deleted_at`) mas não removido do banco.  
Audit logs do usuário são preservados. O username e email ficam liberados para reuso.

```
// Response 204 / 404 USER_NOT_FOUND
```

---

### POST /users/{username}/roles/{roleName} — Permissão: USER_ROLE_ASSIGN

```
// Response 204 / 404 USER_NOT_FOUND
```

---

### DELETE /users/{username}/roles/{roleName} — Permissão: USER_ROLE_ASSIGN

```
// Response 204 / 404
```

---

## Roles — `/roles`

### GET /roles — Permissão: ROLE_READ

```
Query: search (string, opcional), page, size
```

```json
// Response 200
{
  "content": [RoleResponse],
  "page": 0, "size": 20, "totalElements": 5, "totalPages": 1
}
```

---

### GET /roles/{name} — Permissão: ROLE_READ

```json
// Response 200 → RoleResponse / 404 ROLE_NOT_FOUND
```

---

### POST /roles — Permissão: DEV_ROLE_MANAGE

> Exige token **DEV-elevado** (`POST /auth/dev/complete`). `ROLE_ADMIN` não tem essa permissão — apenas `ROLE_DEV` pós-elevação.

```json
{ "name": "ROLE_ANALYST" }   // 3–80 chars, prefixo ROLE_ por convenção
// Response 201 + Location / 409 ROLE_ALREADY_EXISTS
```

---

### DELETE /roles/{name} — Permissão: DEV_ROLE_MANAGE

> Exige token **DEV-elevado**.

```
// Response 204 / 404 ROLE_NOT_FOUND
```

---

### POST /roles/{roleName}/permissions/{permissionName} — Permissão: ROLE_MANAGE_PERMISSIONS

```
// Response 204 / 404
```

> **Guard DEV_ELEVATED:** atribuir `DEV_ROLE_MANAGE` ou `DEV_PERMISSION_MANAGE` a qualquer role exige, além da permissão `ROLE_MANAGE_PERMISSIONS`, que o token carregue a authority `DEV_ELEVATED` (obtida via `POST /auth/dev/complete`). Tentar sem elevação resulta em `403 ACCESS_DENIED`.

---

### DELETE /roles/{roleName}/permissions/{permissionName} — Permissão: ROLE_MANAGE_PERMISSIONS

```
// Response 204 / 404
```

> **Guard DEV_ELEVATED:** remover `DEV_ROLE_MANAGE` ou `DEV_PERMISSION_MANAGE` de qualquer role exige, além da permissão `ROLE_MANAGE_PERMISSIONS`, que o token carregue a authority `DEV_ELEVATED`. O guard é idêntico ao `assignPermission` — as permissões DEV são protegidas em ambas as direções.

---

## Permissions — `/permissions`

### GET /permissions — Permissão: PERMISSION_READ

```
Query: page, size
// Response 200 → PageResult<PermissionResponse>
```

---

### GET /permissions/{name} — Permissão: PERMISSION_READ

```json
// Response 200 → PermissionResponse / 404 PERMISSION_NOT_FOUND
```

---

### POST /permissions — Permissão: DEV_PERMISSION_MANAGE

> Exige token **DEV-elevado** (`POST /auth/dev/complete`). `ROLE_ADMIN` não tem essa permissão.

```json
{ "name": "REPORTS_READ" }   // 3–80 chars
// Response 201 + Location / 409 PERMISSION_ALREADY_EXISTS
```

---

### DELETE /permissions/{name} — Permissão: DEV_PERMISSION_MANAGE

> Exige token **DEV-elevado**.

```
// Response 204 / 404 PERMISSION_NOT_FOUND
```

---

## Estoque — `/estoque`

### GET /estoque/products — Permissão: ESTOQUE_PRODUCT_READ

```
Query: page (default 0, >= 0), size (default 20, 1..100)
       search — nome, SKU, categoria ou marca, sem caixa nem acento (EST-F029: "alfafa" traz a categoria Alfafa inteira)
// Response 200 → PageResult<ProductResponse> / 400 VALIDATION_ERROR
```

---

### POST /estoque/products — Permissão: ESTOQUE_PRODUCT_MANAGE

```json
{
  "sku": "NARG-001",        // 3–50 chars, obrigatório
  "name": "Narguile Aladin", // obrigatório
  "category": "narguile",    // opcional
  "variants": [               // opcional — produto pode não ter variações
    {
      "sku": "NARG-001-M",   // 3–50 chars, obrigatório
      "attributes": [
        { "type": "sabor", "value": "menta" }
      ]
    }
  ]
}
// Response 201 + Location → ProductResponse / 409 SKU_ALREADY_EXISTS / 400 VALIDATION_ERROR
```

> **EST-F023 (rascunho):** aceita também `"status": "RASCUNHO" | "ATIVO"` (default `ATIVO`).
> Rascunho não exige nenhum campo além de `sku`/`name`. Teto de 5 rascunhos no catálogo — o 6º
> devolve `409 DRAFT_LIMIT_REACHED`. `GET /estoque/products` aceita `status` como filtro. `PATCH
> /estoque/products/{sku}` também aceita `status` (promover/rebaixar).

```json
// ProductResponse
{
  "id": 1,
  "sku": "NARG-001",
  "name": "Narguile Aladin",
  "category": "narguile",
  "active": true,
  "variants": [
    { "id": 1, "sku": "NARG-001-M", "active": true, "attributes": [{ "type": "sabor", "value": "menta" }] }
  ]
}
```

---

### PATCH /estoque/products/{sku}/sku — Permissão: ESTOQUE_PRODUCT_MANAGE (EST-F030)

```json
{ "newSku": "SEDA-ALF-KS" }   // 3–50 chars
// 200 → ProductResponse (o produto pai) · 404 PRODUCT_NOT_FOUND · 409 DUPLICATE_SKU
```

Aceita SKU pai ou de variação. Reescreve o SKU numa única transação em **todas** as tabelas que o guardam como texto (catálogo, saldo, lotes, movimentos, reservas, ponto de reposição, balanço, lata aberta, carrinho, comanda, pedidos, recebimentos, lista de reposição, NF-e e receitas de kit) — inclusive o histórico. Auditoria `PRODUCT_SKU_CHANGED` com `oldSku`/`newSku`. Mesmo SKU atual é no-op.

---

### PATCH /estoque/products/{sku} — Permissão: ESTOQUE_PRODUCT_MANAGE

```json
{
  "name": "Narguilé Aladin 2.0",  // opcional, 1–255 — ausente ou null = manter
  "category": "narguile-premium"   // opcional, até 100 — ausente ou null = manter
}
// Response 200 → ProductResponse / 404 PRODUCT_NOT_FOUND / 400 VALIDATION_ERROR
```

Alteração **parcial**: corpo `{}` é um no-op válido. Não altera `sku` nem as variações — o SKU é
referenciado como texto livre por `stock_balance`, `stock_movement` e `stock_reorder_point`, sem
FK, então renomeá-lo tornaria órfão todo o histórico do produto. Limitação da semântica
"null = manter": não há como **limpar** a `category`, só trocá-la.

---

### PATCH /estoque/products/{sku}/active — Permissão: ESTOQUE_PRODUCT_MANAGE

```json
{ "active": false }   // obrigatório — corpo sem o campo é 400, não um "desativar" implícito
// Response 200 → ProductResponse / 404 PRODUCT_NOT_FOUND / 400 VALIDATION_ERROR
```

Desativar **não apaga**: o SKU continua existindo, com saldo e histórico válidos. O efeito é
sobre a movimentação — produto inativo recusa `ENTRADA` (manual ou por recebimento de Compras)
com `409 PRODUCT_INACTIVE`, mas continua aceitando `SAIDA` e venda no PDV, para escoar o saldo
remanescente, e `AJUSTE`, que é o caminho de correção de inventário.

Desativar um SKU **pai** tira também as variações de circulação: um SKU de variação só conta como
ativo se ele e o produto pai estiverem ativos.

---

### POST /estoque/warehouses — Permissão: ESTOQUE_WAREHOUSE_MANAGE

```json
{
  "code": "LOJA-01",         // 2–50 chars, obrigatório, único
  "name": "Loja Centro",      // obrigatório
  "type": "LOJA_FISICA"       // obrigatório — LOJA_FISICA | ECOMMERCE
}
// Response 201 + Location → WarehouseResponse / 409 WAREHOUSE_CODE_ALREADY_EXISTS / 400 VALIDATION_ERROR
```

```json
// WarehouseResponse
{
  "id": 1,
  "code": "LOJA-01",
  "name": "Loja Centro",
  "type": "LOJA_FISICA",
  "active": true
}
```

---

### PATCH /estoque/warehouses/{code} — Permissão: ESTOQUE_WAREHOUSE_MANAGE

```json
{
  "name": "Loja Centro Reformada",  // opcional — ausente ou null = manter
  "type": "ECOMMERCE"                // opcional — LOJA_FISICA | ECOMMERCE; valor desconhecido é 400
}
// Response 200 → WarehouseResponse / 404 WAREHOUSE_NOT_FOUND / 400 VALIDATION_ERROR
```

Não altera o `code`: é a identidade pública do depósito, usada como `warehouseCode` em toda a API.

---

### PATCH /estoque/warehouses/{code}/active — Permissão: ESTOQUE_WAREHOUSE_MANAGE

```json
{ "active": false }   // obrigatório
// Response 200 → WarehouseResponse / 404 WAREHOUSE_NOT_FOUND / 400 VALIDATION_ERROR
```

Mesma regra do produto: depósito inativo recusa `ENTRADA` com `409 WAREHOUSE_INACTIVE` e continua
despachando `SAIDA`.

---

### GET /estoque/warehouses — Permissão: ESTOQUE_WAREHOUSE_READ

```
Query: page (default 0, >= 0), size (default 20, 1..100)
// Response 200 → PageResult<WarehouseResponse>, ordenado por id
// 400 VALIDATION_ERROR (page ou size fora da faixa)
```

⚠️ **Mudança de contrato no EST-C005:** antes devolvia `WarehouseResponse[]` — a lista inteira,
sem paginação. Agora devolve um `PageResult`, então os depósitos estão em `content`.

---

### GET /estoque/stock-balance — Permissão: ESTOQUE_WAREHOUSE_READ

```
Query: sku (obrigatório, 3..50), warehouseCode (obrigatório, 2..50)
// Response 200 → StockBalanceResponse / 404 WAREHOUSE_NOT_FOUND
// 400 VALIDATION_ERROR (sku ou warehouseCode em branco ou fora do tamanho)
// 400 MISSING_PARAMETER (parâmetro ausente)
```

```json
// StockBalanceResponse — quantity é 0 se ainda não houve nenhuma movimentação para o par sku/depósito
{
  "sku": "NARG-001",
  "warehouseCode": "LOJA-01",
  "quantity": 0
}
```

---

### POST /estoque/movements — Permissão: ESTOQUE_STOCK_MANAGE

```json
{
  "sku": "NARG-001",           // 3–50 chars, obrigatório
  "warehouseCode": "LOJA-01",   // obrigatório
  "type": "ENTRADA",            // obrigatório — ENTRADA | SAIDA | AJUSTE
  "quantity": 5.000,             // obrigatório; > 0 em ENTRADA/SAIDA, >= 0 em AJUSTE
  "reason": "Recebimento de fornecedor", // obrigatório, máx. 255 chars
  "unitCost": 7.50                // opcional (EST-F007) — só ENTRADA; alimenta o custo médio ponderado
}
// Response 201 + Location → StockBalanceResponse (saldo já atualizado)
// 404 PRODUCT_NOT_FOUND (SKU não existe no catálogo) / 404 WAREHOUSE_NOT_FOUND
// 400 INSUFFICIENT_STOCK (SAIDA deixaria o saldo negativo) / 400 VALIDATION_ERROR
// 400 RESERVED_STOCK (o físico bastaria, mas parte dele está reservada — EST-C016)
// 400 UNIT_COST_NOT_APPLICABLE (unitCost fora de ENTRADA, ou SKU é kit)
// 409 STOCK_UPDATE_CONFLICT (conflito de concorrência otimista — tente novamente)
// 409 DATA_INTEGRITY_VIOLATION (corrida na primeira movimentação do par — refazer resolve)
```

`unitCost` é opcional mesmo em `ENTRADA` (EST-F007) — nem toda entrada tem custo conhecido (ex.:
balanço de inventário nunca tem). Quando informado, recalcula o custo médio ponderado móvel do par
SKU/depósito (`StockBalanceResponse.averageCost`), distinto de `Pricing.costPrice` (o custo manual
do produto, usado em tempo real pelo PDV). Informado fora de `ENTRADA`, ou para um SKU que é kit
(kit não tem saldo próprio), é recusado com 400 `UNIT_COST_NOT_APPLICABLE`.

`username` **não** é enviado no corpo — é sempre o usuário autenticado (JWT), nunca informado
pelo cliente da API.

⚠️ **`quantity` tem significado diferente por tipo** (EST-C009). Em `ENTRADA` e `SAIDA` é o
**delta**: soma e subtrai, respectivamente — a saída é rejeitada com 400 se o resultado ficaria
negativo, e zerar exatamente é permitido. Em `AJUSTE` é o **saldo-alvo**: o saldo passa a valer
exatamente o valor informado, para cima ou para baixo, e zero é um alvo válido (item que acabou).
É o que permite corrigir inventário para baixo sem lançar uma `SAIDA` falsa. Baixar por `AJUSTE`
nunca devolve `INSUFFICIENT_STOCK` — é substituição, não subtração.

⚠️ **`RESERVED_STOCK` não é o mesmo que `INSUFFICIENT_STOCK`** (EST-C016), e a diferença decide o
que o operador faz. `INSUFFICIENT_STOCK` diz que **nem o saldo físico bastaria** — não há o que
fazer no caixa. `RESERVED_STOCK` diz que o físico bastaria, mas parte dele está **separada para um
pedido ainda não concluído**: a solução existe, é cancelar a reserva pelo painel e vender. A
mensagem traz o físico, o reservado, o disponível e a quantidade pedida. Um `AJUSTE` que levaria o
saldo **abaixo do reservado** responde o mesmo 400 — a contagem encontrou menos unidades do que já
foram prometidas, e quais pedidos perder é decisão humana, não arredondamento do sistema. Até
2026-08-30 esta exceção não tinha handler e saía como **500**.

Além do lançamento manual, `AJUSTE` é o tipo usado pelo fechamento de um
[balanço de inventário](#balanço-de-inventário--estoquestock-counts--permissão-estoque_stock_manage).

`ENTRADA` é recusada com 409 se o produto ou o depósito estiver **desativado** (EST-F018);
`SAIDA` e `AJUSTE` continuam permitidos.

O `sku` precisa existir no catálogo, como SKU pai ou como SKU de variação; caso contrário a
movimentação é recusada com 404 `PRODUCT_NOT_FOUND` e nada é gravado. Isso vale igualmente para
as movimentações originadas de `POST /pdv/sessions/{id}/sales` e `POST /compras/goods-receipts`,
onde um SKU desconhecido reverte a venda ou o recebimento inteiro.

```json
// StockBalanceResponse (mesmo shape de GET /estoque/stock-balance)
{
  "sku": "NARG-001",
  "warehouseCode": "LOJA-01",
  "quantity": 5.000,
  "averageCost": 7.50 // custo médio ponderado móvel (EST-F007); null até a 1ª ENTRADA com unitCost
}
```

---

### GET /estoque/analytics/abc — Permissão: ESTOQUE_PRODUCT_READ ou ESTOQUE_STOCK_MANAGE

Curva ABC e giro do consumo de um período (EST-F011) — o relatório de priorização de compra.

```
Query: from (obrigatório, ISO), to (obrigatório, ISO), warehouseCode (opcional, 2..50 — omitido agrega a loja)
// Response 200 → [{ sku, productName, consumedQuantity, consumedValue, cumulativePercent, abcClass, turnover }]
// 404 WAREHOUSE_NOT_FOUND / 400 VALIDATION_ERROR (from/to ausentes)
```

**Classifica dinheiro, não movimento.** A ordenação é por `consumedValue` (quantidade que saiu ×
custo médio vigente), então a essência cara que sai duas vezes por semana pode ser **A** e o carvão
barato que sai todo dia pode ser **C**. É essa inversão que o relatório existe para mostrar.

**A fonte é o ledger de `SAIDA`, não as vendas.** Cortesia, perda e o lado de saída de uma conversão
(`POST /estoque/conversions`) tiram mercadoria da prateleira sem virar venda — e também precisam ser
repostas. Um relatório sobre `order_item` não as veria.

`abcClass` corta em 80% e 95% do acumulado, olhando o acumulado **antes** da linha: o item que cruza
o limiar pertence à faixa que estava cruzando, então o primeiro SKU é sempre `A` mesmo quando sozinho
já passa dos 80%.

`turnover` é o consumo dividido pelo saldo atual, e vem **`null`** quando o saldo é zero — um SKU em
ruptura não tem giro infinito, tem giro desconhecido. SKU sem custo médio conhecido entra valendo
zero e cai em `C`, em vez de sumir do relatório.

---

### POST /estoque/conversions — Permissão: ESTOQUE_STOCK_MANAGE

Converte saldo de um SKU em saldo de outro **numa única transação** (EST-F025) — o caso diário do
lounge: uma lata de essência vira N sessões de narguilé.

```json
{
  "fromSku": "ESS-BLUE-LATA",   // obrigatório, 3..50
  "toSku": "SESS-BLUE",         // obrigatório, 3..50, diferente de fromSku
  "fromQuantity": 1.000,        // obrigatório, > 0 — quanto sai da origem
  "toQuantity": 5.000,          // obrigatório, > 0 — quanto entra no destino
  "warehouseCode": "LOJA-01",   // obrigatório, 2..50 — o mesmo para as duas pontas
  "reason": "Fracionamento"     // obrigatório, máx. 255
}
// Response 201 + Location (saldo do destino) → { "from": StockBalanceResponse, "to": StockBalanceResponse }
// 400 SAME_SKU_CONVERSION (origem e destino iguais)
// 400 INSUFFICIENT_STOCK / 400 RESERVED_STOCK (na SAIDA da origem) / 400 VALIDATION_ERROR
// 404 PRODUCT_NOT_FOUND / 404 WAREHOUSE_NOT_FOUND
// 409 STOCK_UPDATE_CONFLICT (concorrência otimista — refazer resolve)
```

**Por que não bastam dois `POST /estoque/movements`.** É como a operação era feita: uma `SAIDA` e uma
`ENTRADA` disparadas em sequência pelo cliente, cada uma em sua transação. Se a segunda falhasse, a
lata tinha saído do saldo e nenhuma sessão entrava — sem compensação e sem rastro de que os dois
movimentos eram um ato só. Aqui ou os dois acontecem, ou nenhum.

A **`SAIDA` é aplicada primeiro**, de propósito: é o lado que pode faltar saldo, então falta na origem
impede a entrada do destino de sequer existir.

`toQuantity` é **explícito**, e não derivado de `sessionsPerUnit` do catálogo: aquele campo é sugestão
de tela por decisão da V112, e o saldo não pode depender de um número que o admin edita no cadastro.

Não há `MovementType` novo — são uma `SAIDA` e uma `ENTRADA` comuns no ledger, com `reason` cruzado
citando o SKU do outro lado. Na trilha de auditoria a operação aparece como **um** evento
`STOCK_CONVERTED`, não dois de movimentação: o que importa auditar é que as duas pontas foram a mesma
decisão.

Kit é recusado dos dois lados (`KIT_DIRECT_ADJUSTMENT`): kit não tem saldo próprio.

> **Fronteira com a lata aberta (EST-F027).** Este endpoint continua sendo a ferramenta **genérica**
> de reembalagem entre SKUs **distintos** — comprei em fardo, vendo em unidade. A **essência saiu
> deste caminho**: o dono confirmou em 06/09/2026 que a essência *é* o produto de sessão, com
> `openRoshPrice` e `sessionsPerUnit` próprios, então origem e sessão são o mesmo SKU e o consumo
> passou a ser contado em `/estoque/open-packages`. Manter os dois caminhos para a essência deixaria
> o operador com duas verdades sobre a mesma lata.

### GET /estoque/open-packages — Permissão: ESTOQUE_PRODUCT_READ ou PDV_COMANDA_MANAGE

As latas de essência **em uso** num depósito, com o contador de sessões de cada uma (EST-F027).

```
GET /estoque/open-packages?warehouseCode=LOJA-01
// Response 200 → [ OpenPackageResponse ]
// 404 WAREHOUSE_NOT_FOUND
```

```json
// OpenPackageResponse
{
  "sku": "ESSE-ZGY-BLUEBERRY",
  "productName": "Zgy Blueberry",
  "warehouseCode": "LOJA-01",
  "uses": 3,                 // sessões já lançadas nesta lata
  "sessionsPerUnit": 5,      // cópia do cadastro no momento da ABERTURA
  "remaining": 2,
  "exhausted": false,        // rendeu tudo; continua aberta até a próxima sessão
  "openedAt": "2026-09-08T20:14:03Z",
  "openedBy": "atendente"
}
```

**A unidade sai do saldo na ABERTURA da lata, não a cada sessão.** É o que mantém o significado de
`/estoque/stock-balance` igual ao que o operador conta no balanço: *latas lacradas na prateleira*. O
consumo de dentro da lata vive aqui, e nenhum movimento é inventado — a `SAIDA` de 1 acontece no
instante físico em que alguém tira a lata da prateleira.

`sessionsPerUnit` é **cópia** do catálogo, feita na abertura, e não leitura viva: o admin pode
corrigir o cadastro no meio da noite, e uma lata pela metade não pode mudar de tamanho por isso.

### GET /estoque/open-packages/{sku} — Permissão: ESTOQUE_PRODUCT_READ ou PDV_COMANDA_MANAGE

```
GET /estoque/open-packages/ESSE-ZGY-BLUEBERRY?warehouseCode=LOJA-01
// Response 200 → OpenPackageResponse
// 404 OPEN_PACKAGE_NOT_FOUND — não há lata aberta deste SKU
// 404 WAREHOUSE_NOT_FOUND
```

O `404` aqui é **estado normal**, não erro: significa que nenhuma lata está aberta, e a próxima
sessão abre uma. É distinto do `400 NOT_A_PACKAGED_SESSION_PRODUCT`, que é cadastro faltando —
`sessionsPerUnit` em branco no produto.

### POST /estoque/open-packages/{sku}/replace — Permissão: ESTOQUE_STOCK_MANAGE ou PDV_COMANDA_MANAGE

"Repor essência": descarta a lata em uso e abre outra, baixando **uma** unidade do saldo.

```json
{ "warehouseCode": "LOJA-01" }   // obrigatório, 2..50
// Response 200 → OpenPackageResponse (a lata NOVA, com uses = 0)
// 400 INSUFFICIENT_STOCK — sem saldo para abrir; a lata antiga CONTINUA aberta
// 400 NOT_A_PACKAGED_SESSION_PRODUCT — SKU não é vendido por sessão, ou sem sessionsPerUnit
// 404 PRODUCT_NOT_FOUND / 404 WAREHOUSE_NOT_FOUND
```

Existe porque a lata acaba **antes** do previsto, que é o caso comum. A sobra da lata descartada
(`uses` menor que `sessionsPerUnit`) fica registrada no histórico e **não vira ajuste de estoque**: a
unidade já saiu do saldo quando a lata foi aberta, e transformar o resto em perda criaria movimento
para medir uma quantidade que ninguém mediu.

A lata nova é aberta **antes** de a velha ser fechada, mesma razão pela qual a conversão faz a `SAIDA`
primeiro: abrir baixa estoque e pode faltar saldo, e falhar depois de fechar deixaria o atendente sem
lata nenhuma no sistema, com uma na mão.

`PDV_COMANDA_MANAGE` é aceita porque **quem repõe a essência é o atendente**, que tem essa permissão
(V111) e não `ESTOQUE_STOCK_MANAGE` — exigir só a segunda deixaria o botão inalcançável justamente
para quem o aperta.

### DELETE /estoque/products/{sku} — Permissão: ESTOQUE_PRODUCT_MANAGE

Descarta um **rascunho** de produto ou kit (EST-F026).

```
DELETE /estoque/products/SKU-DRAFT
// Response 204
// 409 PRODUCT_NOT_DRAFT — produto publicado; use PATCH /estoque/products/{sku}/active
// 409 PRODUCT_HAS_STOCK_HISTORY — há saldo ou movimentação no SKU pai ou em alguma variação
// 404 PRODUCT_NOT_FOUND
```

Existe porque o `409 DRAFT_LIMIT_REACHED` orientava uma ação que o sistema não oferecia: dizia
"publique ou remova um rascunho", e `PATCH .../active` com `active: false` **não** libera a vaga —
`status` e `active` são eixos independentes. Cinco rascunhos abandonados desligavam o recurso para o
tenant inteiro.

**Restrito a `status: RASCUNHO`, e é o que separa "descartar um cadastro que nunca foi publicado" de
"apagar um produto do catálogo".** O segundo não existe: SKU é referenciado como texto livre por
`stock_balance`, `stock_movement`, `order_item` e `comanda_item`, **sem FK** (EST-C011), e apagar o
produto deixaria esse histórico órfão. Pelo mesmo motivo, rascunho que chegou a movimentar estoque
também é recusado.

---

### GET /estoque/movements — Permissão: ESTOQUE_PRODUCT_READ **ou** ESTOQUE_STOCK_MANAGE

> **Mudou em 2026-08-31 (EST-C015).** Antes exigia `ESTOQUE_STOCK_MANAGE`, permissão de escrita, para
> uma leitura. O `POST` abaixo **não** mudou. Quem já chamava com `STOCK_MANAGE` continua funcionando —
> a mudança só amplia.

```
Query: sku (obrigatório, 3..50), warehouseCode (obrigatório, 2..50), page (default 0, >= 0), size (default 20, 1..100)
// Response 200 → PageResult<StockMovementResponse>
// 404 WAREHOUSE_NOT_FOUND / 400 MISSING_PARAMETER (sku ou warehouseCode ausente)
// 400 VALIDATION_ERROR (parâmetro presente mas fora da faixa)
```

```json
// PageResult<StockMovementResponse> — mais recentes primeiro (created_at DESC, id DESC)
// O desempate por id garante paginação estável: movimentos de uma mesma venda compartilham
// created_at, e sem ele a mesma linha poderia repetir entre páginas.
{
  "content": [
    {
      "id": 9,
      "sku": "NARG-001",
      "warehouseCode": "LOJA-01",
      "type": "SAIDA",
      "quantity": 2.000,
      "reason": "Venda balcão sessão #7",
      "username": "gerente",
      "createdAt": "2026-07-26T12:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Histórico auditável do par SKU/depósito, incluindo as movimentações geradas automaticamente por
`compras` (recebimento) e `vendas-balcao` (venda) — o `reason` identifica a origem. Par sem
nenhuma movimentação devolve `content` vazio com `200`, não `404`; o `404` é reservado ao
depósito inexistente. Exige `ESTOQUE_STOCK_MANAGE` (e não `ESTOQUE_WAREHOUSE_READ`) porque o
ledger expõe **qual usuário** realizou cada movimentação.

---

### PUT /estoque/products/{sku}/reorder-point — Permissão: ESTOQUE_STOCK_MANAGE

```json
{
  "warehouseCode": "LOJA-01",  // obrigatório
  "minQuantity": 10.000         // obrigatório, >= 0
}
// Response 204 No Content (cria ou atualiza — upsert por (sku, warehouseCode))
// 404 PRODUCT_NOT_FOUND / 404 WAREHOUSE_NOT_FOUND / 400 VALIDATION_ERROR
```

Define o ponto de reposição do par SKU/depósito. A partir daí, **toda** movimentação que reduza
o saldo abaixo de `minQuantity` — manual, recebimento de compras ou venda de PDV — notifica
todos os usuários com `ESTOQUE_STOCK_MANAGE`. Sem ponto de reposição cadastrado, nenhuma
notificação é disparada. Não há endpoint para ler ou remover um ponto de reposição.

A notificação é enviada **depois** do commit da operação e **agregada por operação**: uma venda
que derruba vários SKUs abaixo do mínimo gera um único aviso listando todos eles, não um por SKU.
Operação revertida não notifica ninguém.

---

### PUT /estoque/products/{sku}/kit — Permissão: ESTOQUE_KIT_MANAGE

```json
{
  "components": [
    { "componentSku": "CARV-001", "quantity": 2 },
    { "componentSku": "ESS-001", "quantity": 1 }
  ]
}
// Response 200 → ProductResponse (type passa a "KIT")
// 404 PRODUCT_NOT_FOUND (kitSku ou algum componentSku fora do catálogo)
// 409 KIT_RECIPE_EMPTY / KIT_HAS_VARIANTS / KIT_COMPONENT_ALREADY_IN_USE /
//     KIT_SELF_REFERENCE / DUPLICATE_KIT_COMPONENT / KIT_COMPONENT_NOT_SIMPLE
// 400 VALIDATION_ERROR
```

Define (substitui integralmente) a receita de um kit virtual (EST-F015, um nível só — §2.10 do
plano) e promove o produto a `KIT` como efeito colateral. **PUT idempotente:** chamar de novo com
um conjunto diferente de componentes substitui a receita inteira, nunca mescla. Componente
precisa ser `SIMPLES` — kit dentro de kit é proibido por construção — e o próprio SKU do kit não
pode já ser componente de outro kit nem ter variações cadastradas.

Kit nunca tem linha própria em `stock_balance`: `GET /estoque/stock-balance` para um SKU `KIT`
devolve o saldo **derivado**, `min(floor(disponível_componente / quantidade_receita))` sobre os
componentes. `GET /estoque/products/{sku}/price` devolve o custo **derivado**, soma de
`costPrice * quantity` dos componentes — o `salePrice` continua sendo o do próprio kit. Uma
venda do kit (`POST /pdv/sessions/{id}/sales`) e o reembolso correspondente
(`POST /orders/{id}/refund`) explodem transparentemente em uma movimentação de estoque por
componente, com o SKU do kit anexado ao `reason` (ex.: `"Venda balcão sessão #42 (kit
KIT-001)"`) — nenhuma mudança de contrato nesses dois endpoints.

### GET /estoque/products/{sku}/kit — Permissão: ESTOQUE_PRODUCT_READ

```json
// Response 200 → List<KitComponentResponse> (vazio se o SKU nunca foi promovido a kit)
[
  { "componentSku": "CARV-001", "quantity": 2 },
  { "componentSku": "ESS-001", "quantity": 1 }
]
// 404 PRODUCT_NOT_FOUND
```

---

### Balanço de inventário — `/estoque/stock-counts` — Permissão: ESTOQUE_STOCK_MANAGE

O balanço (EST-F006) é uma **sessão**: abre-se para um depósito, contam-se os SKUs aos poucos, e o
fechamento aplica os ajustes de uma vez. Só pode haver **um balanço aberto por depósito** — dois
simultâneos contariam o mesmo saldo e se sobrescreveriam.

```
POST   /estoque/stock-counts            { "warehouseCode": "LOJA-01" }
POST   /estoque/stock-counts/{id}/items { "sku": "NARG-001", "countedQuantity": 37.000 }
POST   /estoque/stock-counts/{id}/close
POST   /estoque/stock-counts/{id}/cancel
GET    /estoque/stock-counts/{id}
GET    /estoque/stock-counts?warehouseCode=LOJA-01&page=0&size=20
```

```json
// StockCountResponse — após o fechamento
{
  "id": 50,
  "warehouseCode": "LOJA-01",
  "status": "FECHADA",            // ABERTA | FECHADA | CANCELADA
  "username": "gerente",
  "createdAt": "2026-07-27T09:00:00Z",
  "closedAt": "2026-07-27T18:00:00Z",
  "items": [
    {
      "id": 1,
      "sku": "NARG-001",
      "countedQuantity": 8.000,   // o que se contou na prateleira
      "expectedQuantity": 10.000, // saldo do sistema no fechamento — null enquanto ABERTA
      "difference": -2.000        // negativo é falta, positivo é sobra
    }
  ]
}
```

**Registrar item** é upsert por SKU: recontar sobrescreve o valor anterior em vez de criar uma
segunda linha. `countedQuantity` aceita **zero** — é o item que acabou ou sumiu, e é justamente o
que o balanço precisa registrar. SKU fora do catálogo é `404 PRODUCT_NOT_FOUND` na hora, não no
fechamento.

**Fechar** grava um `AJUSTE` (saldo-alvo, ver EST-C009) para cada item cuja contagem **divirja** do
saldo do sistema, levando o saldo ao valor contado. Item que bateu não gera movimentação — contagem
certa não polui o ledger. Tudo na mesma transação: se um SKU falhar, nenhum ajuste é aplicado e o
balanço continua aberto. Os alertas de ponto de reposição disparados pelos ajustes saem agregados
depois do commit. Fechar duas vezes é `409 STOCK_COUNT_NOT_OPEN`, não ajuste em dobro.

**Cancelar** abandona o balanço sem tocar em saldo nenhum e libera o depósito para um novo.

Erros: `404 STOCK_COUNT_NOT_FOUND`, `404 WAREHOUSE_NOT_FOUND`, `404 PRODUCT_NOT_FOUND`,
`409 STOCK_COUNT_ALREADY_OPEN`, `409 STOCK_COUNT_NOT_OPEN`.

---

### GET /estoque/integrity/orphan-skus — Permissão: ESTOQUE_STOCK_MANAGE

```
Query: page (default 0, >= 0), size (default 20, 1..100)
// Response 200 → PageResult<OrphanSkuResponse> / 400 VALIDATION_ERROR
```

```json
// PageResult<OrphanSkuResponse> — uma linha por par SKU/depósito, ordenado por (sku, warehouseCode)
{
  "content": [
    {
      "sku": "NARG-DIGITADO-ERRADO",
      "warehouseCode": "LOJA-01",
      "quantity": 0.000,
      "movementCount": 4,
      "hasReorderPoint": false,
      "lastMovementAt": "2026-02-14T09:12:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Diagnóstico de integridade (EST-C011): lista os pares SKU/depósito que têm saldo, movimentações
ou ponto de reposição gravados mas cujo `sku` **não existe no catálogo** — nem como SKU pai nem
como SKU de variação. As três tabelas de estoque guardam `sku` como texto livre, sem FK para
`product`, então até EST-C002 um SKU digitado errado criava esses registros em silêncio.

**Somente leitura, e não há endpoint de expurgo.** O destino de cada órfão — cadastrar o produto
que faltava (`POST /estoque/products`, e o SKU deixa de ser órfão) ou apagar as linhas — é
decisão humana, porque a consulta não distingue os dois casos e apagar em massa destruiria
histórico legítimo. Para o caminho DBA há o script
[`scripts/estoque-orphan-skus.sql`](../scripts/estoque-orphan-skus.sql), com o bloco de `DELETE`
comentado e a lista de SKUs a preencher à mão.

`quantity` é zero quando o órfão só tem movimentações ou só ponto de reposição, e
`lastMovementAt` é `null` quando o par nunca foi movimentado. Base íntegra devolve `content`
vazio com `200`. Exige `ESTOQUE_STOCK_MANAGE` pela mesma régua do ledger: `ESTOQUE_WAREHOUSE_READ`
não basta.

---

### GET /estoque/reservations — Permissão: ESTOQUE_RESERVATION_READ

```
Query: sku (opcional), warehouseCode (opcional), status (opcional — ACTIVE/CONSUMED/RELEASED/EXPIRED),
       page (default 0, >= 0), size (default 20, 1..100)
// Response 200 → PageResult<StockReservationResponse> / 404 WAREHOUSE_NOT_FOUND (warehouseCode inexistente)
```

```json
// PageResult<StockReservationResponse>
{
  "content": [
    {
      "id": 10,
      "sku": "NARG-001",
      "warehouseCode": "LOJA-01",
      "quantity": 2.000,
      "ownerReference": "CHECKOUT:abc",
      "status": "ACTIVE",
      "expiresAt": "2026-07-29T12:30:00Z",
      "createdAt": "2026-07-29T12:00:00Z",
      "resolvedAt": null,
      "username": "cliente@exemplo.com"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

Listagem de reservas de estoque (EST-F013/EST-F021). `sku`, `warehouseCode` e `status` são
filtros opcionais e se combinam. **Somente leitura**: criar, consumir e liberar reserva é
orquestração interna (checkout do marketplace na Fatia 9, e a liquidação de pedido online no PDV
via `POST /pdv/sessions/{id}/orders/{orderId}/settle`), não uma operação disparada por um
operador via HTTP — daí não haver `POST`/`{id}/release` neste controller.

### GET /estoque/reservations/{id} — Permissão: ESTOQUE_RESERVATION_READ

Consulta uma reserva por id. `404 RESERVATION_NOT_FOUND` se não existir.

---

### GET /estoque/integrity/reservation-mismatch — Permissão: ESTOQUE_STOCK_MANAGE

```
Query: page (default 0, >= 0), size (default 20, 1..100)
// Response 200 → PageResult<ReservationIntegrityMismatchResponse>
```

```json
{
  "content": [
    {
      "sku": "NARG-001",
      "warehouseCode": "LOJA-01",
      "reservedQuantity": 5.000,
      "activeReservationsTotal": 3.000,
      "difference": 2.000
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

Diagnóstico de integridade (EST-C013): pares SKU/depósito cujo `stock_balance.reserved_quantity`
diverge da soma das reservas `ACTIVE` no ledger `stock_reservation` para o mesmo par. Diferente do
órfão de SKU, essa divergência não aparece em nenhuma tela — o saldo físico bate normalmente, só
o disponível é que mente. O sintoma é **estoque travado invisível**: a venda recusa por reserva e
nenhuma reserva ativa a explica (ou o inverso), mais difícil de diagnosticar que o overselling que
a reserva existe para evitar. `difference` positivo é contador acima do ledger; negativo é ledger
acima do contador. Somente leitura — a correção de cada linha é decisão humana, no mesmo espírito
de `GET /estoque/integrity/orphan-skus`. Base íntegra devolve `content` vazio com `200`.

---

## Kit montável — `/estoque/kit-templates`, `/shop/kits`, `/pdv/kits` (EST-F031 · ECM-F008 · PDV-F019)

O "Kit Mahal": o cliente escolhe um item por passo (bag, seda, piteira, tubeck, tesoura, cuia, isqueiro) e paga a **soma dos itens menos `discountPercent`**. Cada passo aponta para uma **categoria**; as opções são os produtos ativos, publicados, precificados e não-kit dessa categoria (produto com grade vira uma opção por variação ativa). Não é o kit de receita fixa (EST-F015): não tem SKU nem saldo — cada item vira uma linha comum agrupada por `kitBundleId`, e o estoque baixa item a item.

### GET /estoque/kit-templates · GET /estoque/kit-templates/{id} — Permissão: ESTOQUE_PRODUCT_READ

### POST /estoque/kit-templates · PUT /estoque/kit-templates/{id} — Permissão: ESTOQUE_KIT_TEMPLATE_MANAGE

```json
{
  "name": "Kit Mahal",              // único, sem caixa
  "description": "Monte o seu kit",
  "imageUrl": null,
  "discountPercent": 10,            // 0 ≤ x < 100
  "active": true,                   // default true
  "visibleInPos": true,             // default true
  "visibleInMarketplace": true,     // default true
  "steps": [
    { "name": "Bag",      "displayOrder": 0, "categoryId": 1, "required": true,  "maxItems": 1 },
    { "name": "Seda",     "displayOrder": 1, "categoryId": 2, "required": true,  "maxItems": 2 },
    { "name": "Piteira",  "displayOrder": 2, "categoryId": 3, "required": false, "maxItems": 1 },
    { "name": "Tubeck",   "displayOrder": 3, "categoryId": 4, "required": false, "maxItems": 1 },
    { "name": "Tesoura",  "displayOrder": 4, "categoryId": 5, "required": false, "maxItems": 1 },
    { "name": "Cuia",     "displayOrder": 5, "categoryId": 6, "required": false, "maxItems": 1 },
    { "name": "Isqueiro", "displayOrder": 6, "categoryId": 7, "required": true,  "maxItems": 1 }
  ]
}
// 201/200 → KitTemplateResponse (com ids dos passos) · 404 CATEGORY_NOT_FOUND · 409 DUPLICATE_KIT_TEMPLATE_NAME
```

`PUT` substitui o modelo inteiro: passo **com** `id` é atualizado no lugar, passo sem `id` é criado, passo ausente é removido. Mantenha o `id` dos passos — o carrinho guarda o passo de cada item, e o checkout recota por ele.

### DELETE /estoque/kit-templates/{id} — Permissão: ESTOQUE_KIT_TEMPLATE_MANAGE

`204`. Não desfaz venda nenhuma; kit que estava em carrinho passa a ser recusado no checkout (`KIT_NOT_AVAILABLE`).

### GET /shop/kits · GET /shop/kits/{id} — **Público**

Só kits ativos e com `visibleInMarketplace`. Inativo responde 404 `KIT_TEMPLATE_NOT_FOUND`.

### GET /shop/kits/{id}/steps/{stepId}/options — **Público**

```json
// 200 → [{ "sku": "SEDA-G-MENTA", "productSku": "SEDA-G", "name": "Seda Sabores",
//          "attributes": [...], "price": 7.00, "imageUrl": null, "available": true }]
```

`sku` é o que vai na escolha. `available` = saldo no depósito padrão do marketplace.

### POST /shop/kits/quote — **Público** · POST /pdv/kits/quote — Permissão: PDV_READ

```json
{ "templateId": 7, "picks": [ { "stepId": 10, "sku": "BAG-01" }, { "stepId": 20, "sku": "SEDA-01" } ] }
// 200 → { "subtotal": 50.00, "discount": 5.00, "total": 45.00,
//         "lines": [{ "sku": "BAG-01", "unitPrice": 40.00, "discountAmount": 4.00, "netAmount": 36.00, ... }] }
// 422 → code KIT_* (ver tabela de error codes)
```

O desconto é rateado por linha ao centavo (`DiscountProration`): a soma dos `discountAmount` é exatamente `discount`.

### POST /shop/cart/kits — Permissão: SHOP_CART_OWN

Mesmo corpo do quote. `201 → ShopCartResponse`. Cada chamada cria um pacote novo (`kitBundleId`). O carrinho passa a trazer `kitBundleId`, `kitTemplateId`, `kitStepId` e `discountAmount` por item, e `discountTotal` no topo; **`total` agora é líquido** (soma dos subtotais − `discountTotal`). O `POST /shop/checkout` recota cada pacote e grava o desconto de cada linha em `OrderItem.discountAmount`; pacote que não fecha mais recusa o checkout inteiro com 422.

### DELETE /shop/cart/kits/{bundleId} — Permissão: SHOP_CART_OWN

`204`. Item de kit não sai por `DELETE /shop/cart/items/{sku}` (essa rota só mexe em linha avulsa).

### GET /pdv/kits · GET /pdv/kits/{id}/steps/{stepId}/options?warehouseCode= — Permissão: PDV_READ

Kits com `visibleInPos`. Sem `warehouseCode`, `available` vem `null`.

### POST /pdv/comandas/{id}/kits — Permissão: PDV_COMANDA_MANAGE

Mesmo corpo do quote. `201 → ComandaResponse`. Uma linha por item, preço cheio em `unitPrice` e a parte do kit em `kitDiscountAmount`; `runningTotal` já é líquido. Estoque sai agora (SAIDA por item). No fechamento, o desconto do kit **soma** ao desconto de conta em cada `OrderItem.discountAmount`, o desconto de conta é rateado sobre o líquido do kit, e o desconto do kit **não conta** para o teto `pdv.sale.max-discount-percent`.

### DELETE /pdv/comandas/{id}/kits/{bundleId} — Permissão: PDV_COMANDA_MANAGE

Remove o pacote inteiro e devolve o estoque (ENTRADA por item). `DELETE /pdv/comandas/{id}/items/{itemId}` numa linha de kit responde 409 `KIT_ITEM_REMOVAL_NOT_ALLOWED`.

---

## Compras — `/compras`

### GET /compras/suppliers — Permissão: COMPRAS_READ

Lista fornecedores paginados (`page` ≥ 0, `size` entre 1 e 100 — default 0/20). Retorna
`PageResult<SupplierResponseDTO>` — até COM-C002 devolvia o record de domínio direto, o único ponto
da API assim.

```json
// SupplierResponseDTO
{
  "id": 12,
  "legalName": "Distribuidora Zomo LTDA",
  "taxId": "12345678000199",       // SÓ DÍGITOS — é como o XML da NF-e traz o emitente
  "email": "contato@zomo.com.br",  // pode ser null
  "active": true
}
```

### GET /compras/suppliers/{id} — Permissão: COMPRAS_READ

```
// Response 200 → SupplierResponseDTO
// 404 SUPPLIER_NOT_FOUND
```

### POST /compras/suppliers — Permissão: COMPRAS_SUPPLIER_MANAGE

Cadastra um fornecedor (COM-F001).

```json
{
  "legalName": "Distribuidora Zomo LTDA",  // obrigatório, máx. 150
  "taxId": "12.345.678/0001-99",           // obrigatório — com ou sem máscara
  "email": "contato@zomo.com.br"           // opcional, máx. 150
}
// Response 201 → SupplierResponseDTO
// 400 VALIDATION_ERROR — razão social ausente, ou CNPJ/CPF com número de dígitos inválido
// 409 SUPPLIER_TAX_ID_ALREADY_EXISTS
```

**Destrava uma feature já entregue.** A importação de NF-e por XML responde
`404 SUPPLIER_NOT_FOUND_BY_TAX_ID` quando o CNPJ do emitente não está cadastrado — decisão
deliberada, porque `taxId` é dado de compliance e não se cria fornecedor por dedução —, e até aqui
não havia nenhum caminho pela UI para cadastrá-lo: o único jeito era `INSERT` direto no banco.

**`taxId` é gravado só com dígitos, e a duplicidade é conferida sobre o valor normalizado.** Não é
cosmético: `findByTaxId` é comparação exata de string e é ela que a importação usa para achar o
emitente, que chega do XML **sem máscara**. Aceitar `12.345.678/0001-99` e `12345678000199` como
valores distintos criaria dois fornecedores para o mesmo CNPJ, com a `uk_supplier_tax_id` sem
enxergar a duplicidade.

CPF de 11 dígitos é aceito — produtor rural que emite nota é pessoa física. **Sem** validação de
dígito verificador, de propósito: a nota que traz esse número já foi validada pela SEFAZ, e reprovar
aqui um CNPJ que o fisco aceitou travaria o recebimento por causa de uma regra nossa.

### PATCH /compras/suppliers/{id} — Permissão: COMPRAS_SUPPLIER_MANAGE

```json
{ "legalName": "Distribuidora Zomo ME", "email": "compras@zomo.com.br" }  // campo ausente mantém
// Response 200 → SupplierResponseDTO
// 404 SUPPLIER_NOT_FOUND
```

**`taxId` não é editável**, pelo mesmo motivo que o SKU do produto também ficou fora do PATCH: é a
chave pela qual a importação de NF-e encontra o fornecedor, e trocá-lo faria os recebimentos já
registrados apontarem para um CNPJ que nunca os emitiu. Fornecedor com CNPJ errado se resolve
criando o certo e desativando o outro.

### PATCH /compras/suppliers/{id}/active — Permissão: COMPRAS_SUPPLIER_MANAGE

```json
{ "active": false }   // obrigatório
// Response 200 → SupplierResponseDTO
// 400 VALIDATION_ERROR (campo ausente) / 404 SUPPLIER_NOT_FOUND
```

Endpoint próprio, e não um campo do PATCH acima, pelo mesmo motivo de
`PATCH /estoque/products/{sku}/active` (EST-F018): desativar tem efeito operacional e merece evento
de auditoria distinto de uma correção de nome. Fornecedor inativo sai da escolha de um recebimento
novo, mas continua resolvendo os recebimentos já registrados — desativar não apaga histórico.

### POST /compras/goods-receipts — Permissão: COMPRAS_RECEIPT_MANAGE

```json
{
  "supplierId": 1,               // obrigatório
  "warehouseCode": "LOJA-01",    // obrigatório
  "items": [                      // obrigatório, não vazio
    { "sku": "NARG-001", "quantity": 12.000 }  // quantity > 0
  ]
}
// Response 201 → GoodsReceiptResponseDTO
// 404 SUPPLIER_NOT_FOUND / WAREHOUSE_NOT_FOUND / 400 VALIDATION_ERROR
```

Registra o recebimento e **dá entrada automática no estoque na mesma transação**: cada item gera
um `StockMovement` de `ENTRADA` via `EstoqueUseCase.adjustStock`, atualizando o `StockBalance`.
`username` vem do JWT, nunca do corpo.

```json
// GoodsReceiptResponseDTO
{
  "id": 1,
  "supplierId": 1,
  "warehouseCode": "LOJA-01",
  "username": "admin",
  "receivedAt": "2026-07-23T14:02:11Z",
  "items": [ { "sku": "NARG-001", "quantity": 12.000 } ]
}
```

### POST /compras/goods-receipts/nfe-preview — Permissão: COMPRAS_RECEIPT_MANAGE

EST-F005: primeira fase da importação de NF-e. Upload `multipart/form-data`, campo `file` (o XML
da NF-e). Casa o fornecedor pelo CNPJ do emitente e cada item pelo EAN (`cEAN`) contra
`Product.barcode` — **nada é persistido em `GoodsReceipt` ainda**. `400 MALFORMED_NFE_XML` (XML
malformado ou rejeitado pelo hardening contra XXE); `404 SUPPLIER_NOT_FOUND_BY_TAX_ID` (nenhum
fornecedor cadastrado com aquele CNPJ — sem criação automática, de propósito).

```json
// Response 200 → NfeImportResponseDTO
{
  "id": 1,
  "supplierId": 7,
  "emitterCnpj": "12345678000199",
  "warehouseCode": null,
  "status": "PREVIEWED",
  "goodsReceiptId": null,
  "lines": [
    {
      "id": 10, "itemNumber": 1, "supplierProductCode": "FORN-001",
      "ean": "7891234567890", "description": "Essência Menta 50g",
      "quantity": 10.000, "unitPrice": 12.50,
      "lotCode": "L2026A", "expiryDate": "2027-06-01",
      "matchStatus": "MATCHED", "matchedSku": "ESS-MENTA-50"
    },
    {
      "id": 11, "itemNumber": 2, "supplierProductCode": "FORN-002",
      "ean": null, "description": "Carvão a granel",
      "quantity": 5.000, "unitPrice": 8.00,
      "lotCode": null, "expiryDate": null,
      "matchStatus": "UNMATCHED", "matchedSku": null
    }
  ],
  "uploadedBy": "comprador1",
  "uploadedAt": "2026-08-18T14:00:00Z",
  "confirmedAt": null
}
```

Linha sem `cEAN` (ou `cEAN = "SEM GTIN"`, comum em NF-e real de item não-branded/a granel) volta
`UNMATCHED` — resolva com um override manual em `POST .../nfe-confirm` antes de confirmar.

### POST /compras/goods-receipts/nfe-confirm — Permissão: COMPRAS_RECEIPT_MANAGE

Segunda fase: confirma um import previamente aceito, cobrindo com `overrides` toda linha que
ficou `UNMATCHED` no preview, e delega para o mesmo caminho de `POST /compras/goods-receipts`. O
estoque só é debitado/creditado aqui, nunca no preview.

```json
{
  "nfeImportId": 1,          // obrigatório — id devolvido pelo preview
  "warehouseCode": "LOJA-01", // obrigatório — a NF-e não diz o depósito de destino
  "overrides": [
    { "itemNumber": 2, "sku": "CARV-GRANEL" }
  ]
}
// Response 201 → GoodsReceiptResponseDTO (mesmo shape do POST /compras/goods-receipts)
// 400 UNMATCHED_NFE_LINE (linha UNMATCHED sem override) / VALIDATION_ERROR
// 404 NFE_IMPORT_NOT_FOUND / WAREHOUSE_NOT_FOUND
// 409 NFE_IMPORT_ALREADY_PROCESSED (import já confirmado ou rejeitado)
```

---

## PDV (Vendas Balcão) — `/pdv`

### GET /pdv/sessions — Permissão: PDV_READ

Paginado (`page` ≥ 0, `size` 1–100), **das mais recentes para as mais antigas**. A ordenação entrou
em PDV-C013: antes a consulta paginava sem `ORDER BY`, e a mesma sessão podia aparecer em duas
páginas enquanto outra sumia.

Lista sessões de caixa paginadas (`page` ≥ 0, `size` entre 1 e 100 — default 0/20). Retorna
`PageResult<CashRegisterSessionResponseDTO>` (era o record de domínio até PDV-C002).

### POST /pdv/sessions — Permissão: PDV_SESSION_MANAGE

```json
{ "openingAmount": 200.00, "warehouseCode": "LOJA-01" }
// 201 + Location → CashRegisterSessionResponseDTO
// 409 SESSION_ALREADY_OPEN / 404 WAREHOUSE_NOT_FOUND / 400 VALIDATION_ERROR
```

**Uma sessão aberta por operador**, garantida por índice parcial único no schema além da checagem
de domínio. O `warehouseCode` informado aqui é o depósito de **todas** as vendas deste caixa — ele
não vai mais no corpo da venda (PDV-C004).

### GET /pdv/sessions/current — Permissão: PDV_READ

Caixa aberto do operador autenticado. `404 NO_OPEN_SESSION`.

### GET /pdv/sessions/{id} — Permissão: PDV_READ

`404 CASH_REGISTER_SESSION_NOT_FOUND`.

### POST /pdv/sessions/{id}/movements — Permissão: PDV_SESSION_MANAGE

```json
{ "type": "SANGRIA", "amount": 150.00, "reason": "depósito no cofre" }
// 201 → CashMovementResponseDTO
// 403 SESSION_NOT_OWNED / 409 CASH_REGISTER_SESSION_CLOSED / 400 VALIDATION_ERROR
```

`type ∈ {SANGRIA, SUPRIMENTO}`. `amount` é **sempre positivo** — o sentido vem do `type`, e a
resposta traz `signedAmount` com o efeito no caixa. `reason` é obrigatório: sangria sem motivo
registrado é indistinguível de desvio.

Exige sessão aberta **e do próprio operador**.

### GET /pdv/sessions/{id}/movements — Permissão: PDV_READ

`PageResult<CashMovementResponseDTO>` dos movimentos da sessão, na ordem em que aconteceram
(`id` crescente). Parâmetros `page` (≥ 0, default 0) e `size` (1–100, default 50); fora da faixa é
`400 VALIDATION_ERROR`.

> **Contrato alterado em PDV-C012** (2026-08-30): devolvia a lista na raiz do corpo e passa a
> devolver `PageResult` — os itens saíram da raiz para `content`.

### GET /pdv/sessions/{id}/payment-totals — Permissão: PDV_READ

Totais da sessão por forma de pagamento. As quatro formas (`DINHEIRO`, `DEBITO`, `CREDITO`, `PIX`)
aparecem sempre, mesmo zeradas. Só pagamento `CAPTURED` conta — `CORRECTED` (PDV-F030) e
`ON_ACCOUNT` (CRM-F010) ficam fora.

```json
[ { "method": "DINHEIRO", "amount": 300.00, "refundedAmount": 0.00, "changeAmount": 12.00,
    "receivableReceived": 50.00, "netAmount": 338.00 } ]
```

| Campo | Descrição |
|---|---|
| `amount` | Bruto das vendas `CAPTURED` |
| `refundedAmount` / `changeAmount` | Estornos e troco (troco só em `DINHEIRO`) — PDV-F026 |
| `receivableReceived` | **CRM-F010** — quitação de marcado recebida nesta sessão neste método, já líquida do troco. Separado de `amount` porque não é venda. A quitação **não** é `CashMovement` (que contaria duas vezes) |
| `netAmount` | `amount + receivableReceived − refundedAmount − changeAmount`. Em `DINHEIRO`, é o que entra no esperado do fechamento |

`MARCADO` nunca aparece aqui: é dinheiro que não entrou. `404` se a sessão não existe.

### GET /pdv/sessions/{id}/summary — Permissão: PDV_READ

PDV-F038 — o total vendido do caixa, para a tela de fechamento (prévia) e para o relatório depois.

```json
{ "totals": [ /* o mesmo de /payment-totals */ ],
  "totalReceived": 1250.00,
  "totalOnAccount": 90.00 }
```

`totalReceived` é a soma dos `netAmount` de todas as formas. `totalOnAccount` é o `MARCADO` vendido na
sessão (linhas `ON_ACCOUNT`), à parte e fora do total. `404` se a sessão não existe.

PDV-F037: a sessão (`GET /pdv/sessions/current`, `GET /pdv/sessions/{id}`) traz `horasAberto` e
`fechamentoSugerido` (aberta há 12h ou mais). É só aviso: desde então não há corte de meia-noite, e o
caixa aberto ontem continua vendendo até o dono fechar.

### POST /pdv/sessions/{id}/close — Permissão: PDV_SESSION_CLOSE

```json
{ "countedAmount": 495.00 }
// 200 → CashRegisterSessionResponseDTO com expectedAmount, countedAmount, differenceAmount, diverges
// 404 CASH_REGISTER_SESSION_NOT_FOUND / 409 CASH_REGISTER_SESSION_CLOSED
```

`expectedAmount = openingAmount + vendas concluídas − sangrias + suprimentos`.

**Divergência NÃO impede o fechamento** — é registrada, exatamente como no fechamento de um balanço
de inventário. `differenceAmount` negativo significa falta na gaveta, e é um número legítimo.

Fechar **não** exige ser o dono da sessão: a conferência costuma ser do gerente, e é por isso que
`PDV_SESSION_CLOSE` existe separada de `PDV_SESSION_MANAGE`.

> ⚠️ **`expectedAmount` é aproximado nesta fase.** A conferência da gaveta deveria considerar só o
> que entrou em **dinheiro**, mas `order_payment` só existe na Fatia 3 — por ora o esperado soma
> **todas** as vendas concluídas da sessão. Enquanto a loja só receber dinheiro, o número bate; na
> primeira venda no cartão ele vai acusar uma sobra que não existe fisicamente.

### GET /pdv/pending-online-orders — Permissão: PDV_READ

Pedidos `MARKETPLACE` em `AGUARDANDO_PAGAMENTO` — a lista que o caixa consulta quando o cliente
chega à loja para retirar e pagar um pedido montado no app.

### POST /pdv/sessions/{id}/orders/{orderId}/settle — Permissão: PDV_SALE_MANAGE

> **Contrato alterado em PDV-C015.** A rota **não tinha corpo** e passou a exigir `payments`, o
> mesmo shape de `POST /pdv/sessions/{id}/sales`. Ela concluía o pedido sem registrar como o
> dinheiro entrou — era o único caminho de recebimento do projeto fora do ledger de pagamento, e o
> fechamento daquele caixa acusava sobra sem dono.

Liquida no balcão um pedido feito no aplicativo: **consome a reserva** de estoque (não dá baixa
nova, que debitaria duas vezes), **registra o pagamento recebido**, vincula o pedido à sessão de
caixa e conclui, emitindo o `orderNumber`.

O `channel` **continua `MARKETPLACE`** — foi o site que gerou a venda, e é assim que ela tem que
aparecer no relatório de conversão. O que muda é o `sessionId`, que passa a apontar para o caixa que
recebeu o dinheiro.

**Valor exato, sem troco.** Justamente porque o canal continua `MARKETPLACE`, o pedido não admite
`changeAmount` (`ck_sales_order_change_amount_by_channel`) — a tela lança o que **fica na gaveta**,
não a cédula entregue. Excedente responde `400 CHANGE_NOT_SUPPORTED`.

A cobrança de gateway que o checkout deixou aberta (`PENDING`/`GATEWAY_PIX`) é **encerrada** como
`CANCELLED`: pago no balcão, nenhum webhook vai confirmá-la.

```json
{
  "payments": [                    // obrigatório, não vazio; várias linhas = pagamento dividido
    { "method": "DINHEIRO", "amount": 44.00 }
  ]
}
// Response 200 → OrderResponseDTO
// 400 INSUFFICIENT_PAYMENT (a soma não cobre o líquido)
// 400 CHANGE_NOT_SUPPORTED (a soma passa do líquido — aqui não há troco)
```

`403 SESSION_NOT_OWNED` / `404` / `409 INVALID_STATUS_TRANSITION` se o pedido não estiver aguardando
pagamento.

### POST /pdv/sessions/{id}/sales — Permissão: PDV_SALE_MANAGE (+ PDV_SALE_DISCOUNT se houver desconto)

> **Contrato alterado em PDV-F004 e PDV-C004.** `unitPrice` e `warehouseCode` **saíram** do request — o depósito vem da sessão de caixa. Além disso, a venda passa a exigir que a sessão pertença ao operador autenticado (`403 SESSION_NOT_OWNED`). Sobre o preço: o preço e o custo são
> resolvidos pelo servidor a partir do catálogo (`Pricing`, V63). Aceitar preço do cliente HTTP
> tornava indistinguíveis erro de digitação, desconto autorizado e fraude.

```json
{
  "customerId": 42,                // opcional — sem cliente, sem cashback
  "items": [                       // obrigatório, não vazio
    { "sku": "NARG-001", "quantity": 2.000, "discountAmount": 4.00 }
  ]
}
// Response 201 → OrderResponseDTO
// 400 INSUFFICIENT_STOCK (saldo insuficiente para algum item) / 400 VALIDATION_ERROR
// 400 RESERVED_STOCK (o físico bastaria, mas está reservado para um pedido online)
// 403 desconto > 0 sem PDV_SALE_DISCOUNT
// 404 CASH_REGISTER_SESSION_NOT_FOUND / 404 PRODUCT_NOT_FOUND
// 409 CASH_REGISTER_SESSION_CLOSED / 409 PRODUCT_NOT_PRICED / 409 DISCOUNT_LIMIT_EXCEEDED
// 409 ITEM_DISCOUNT_EXCEEDS_GROSS (desconto da linha acima do bruto dela — PDV-C016; antes era um 400 genérico)
```

Registra a venda e **dá baixa automática no estoque na mesma transação**: cada item gera um
`StockMovement` de `SAIDA`. Se qualquer item não tiver saldo, a transação inteira é revertida. A
venda nasce e termina na mesma transação (`CRIADO → CONCLUIDO`), e é na conclusão que o
`orderNumber` é emitido, de sequência própria.

`quantity` > 0; `discountAmount` ≥ 0 e não pode passar do bruto do item. O teto de desconto por
pedido é `pdv.sale.max-discount-percent` (default **10%**).

Produto sem preço no catálogo **recusa a venda** com `409 PRODUCT_NOT_PRICED` — preço zero e preço
desconhecido não são a mesma coisa.

> **PDV-F008 (reserva para retirada):** o request aceita `"reserveForPickup": true` (default
> `false`) — grava `RESERVADO` em vez de `CONCLUIDO`: mercadoria já baixada e pagamento já
> capturado, só a retirada fica pendente. Com `delivery` (PDV-F022, revisto em 2026-09-30):
> `ENTREGA` grava sempre `RESERVADO` e segue a esteira; `RETIRADA` grava `CONCLUIDO`, ou
> `RESERVADO` com `reserveForPickup=true`. O antigo `400` para `delivery` + `reserveForPickup=false`
> deixou de existir. Marcar como retirado depois é
> `POST /orders/{id}/status` com `{"status": "CONCLUIDO"}` — mesmo endpoint da esteira de
> fulfillment. `RESERVADO` só é alcançável a partir de venda de balcão (`CRIADO`); pedido de
> marketplace nunca alcança esse status.

```json
// OrderResponseDTO
{
  "id": 1,
  "orderNumber": "000001000",
  "channel": "BALCAO",
  "status": "CONCLUIDO",
  "customerId": 42,
  "sessionId": 1,
  "warehouseCode": "LOJA-01",
  "grossAmount": 179.80,
  "discountAmount": 4.00,
  "cashbackRedeemed": 0.00,
  "netAmount": 175.80,
  "changeAmount": null,
  "createdAt": "2026-07-28T18:40:02Z",
  "paidAt": "2026-07-28T18:40:02Z",
  "concludedAt": "2026-07-28T18:40:02Z",
  "items": [
    {
      "id": 1, "sku": "NARG-001", "quantity": 2.000,
      "unitPrice": 89.90, "discountAmount": 4.00,
      "grossAmount": 179.80, "netAmount": 175.80,
      "cashbackPercent": null, "cashbackAmount": null
    }
  ]
}
```

Custo e margem **não** são expostos aqui: são dado de gestão, e `PDV_READ` é a permissão mais
distribuída do módulo.

#### "Marcar" — venda a prazo para cliente VIP (CRM-F010)

Uma linha de `payments` com `"method": "MARCADO"` e `"dueDate": "AAAA-MM-DD"` deixa parte ou toda a
venda para o cliente pagar depois. A venda **conclui normalmente** (a mercadoria saiu); a linha fica
`ON_ACCOUNT`, fora do caixa, e nasce um marcado em `GET /receivables`. Vale também no fechamento de
mesa (`POST /pdv/comandas/{id}/close`).

```json
{ "customerId": 42, "items": [ ... ],
  "payments": [ { "method": "DINHEIRO", "amount": 50.00 },
                { "method": "MARCADO", "amount": 30.00, "dueDate": "2026-10-31" } ] }
```

| Situação | HTTP | Código |
|---|---|---|
| Operador sem `PDV_SALE_ON_ACCOUNT` | 403 | `ON_ACCOUNT_NOT_ALLOWED` |
| Venda sem `customerId` | 400 | `CUSTOMER_REQUIRED_FOR_ON_ACCOUNT` |
| Cliente sem a tag VIP | 403 | `CUSTOMER_NOT_ELIGIBLE` |
| Cliente com marcado vencido | 409 | `CUSTOMER_HAS_OVERDUE` (corpo traz `overdueBalance`) |
| Passa do limite de crédito | 409 | `CREDIT_LIMIT_EXCEEDED` (corpo traz `limit`, `openBalance`, `available`) |
| `dueDate` ausente ou no passado | 400 | `INVALID_DUE_DATE` |
| Mais de uma linha `MARCADO` | 400 | `DUPLICATE_ON_ACCOUNT_PAYMENT` |
| `MARCADO` onde não vale (liquidação do app, correção de pagamento, quitação) | 400 | `INVALID_PAYMENT_METHOD` |

Para a tela checar antes de vender: `GET /crm/customers/{id}/on-account-eligibility`. Nas respostas
de venda, `paymentStatus` vem `PENDENTE` quando há linha `MARCADO`.

### GET /pdv/sales/{id} — Permissão: PDV_READ

Retorna `OrderResponseDTO`. `404 ORDER_NOT_FOUND`.

### POST /pdv/sales/{id}/receipt/email — Permissão: PDV_SALE_MANAGE

Envia o comprovante da compra (balcão ou mesa) para o e-mail do **cliente vinculado** ao pedido — só quando o operador pede; venda presencial não manda e-mail sozinha. Não aceita endereço avulso. Envio assíncrono: `202` quer dizer que saiu para o provedor.

```json
// Response 202
{ "sentTo": "jo***@gmail.com" }
```

| Erro | Quando |
|------|--------|
| 404 | Pedido não encontrado |
| 422 `RECEIPT_EMAIL_UNAVAILABLE` | Pedido cancelado, sem cliente vinculado ou cliente sem e-mail |

### GET /pdv/sessions/{id}/sales — Permissão: PDV_READ

`PageResult<OrderResponseDTO>` dos pedidos da sessão, do mais recente para o mais antigo
(`page` ≥ 0, `size` 1–100). `404 CASH_REGISTER_SESSION_NOT_FOUND`.

## Comanda de mesa (PDV-F009) — `/pdv/comandas`

Pedidos incrementais numa sessão de caixa aberta por horas — o caso do lounge de narguilé, distinto
da venda pontual de `POST /pdv/sessions/{id}/sales`. **A baixa de estoque acontece a cada item
lançado**, não no fechamento — ver a nota de limitação conhecida no README do módulo.

### POST /pdv/comandas?sessionId= — Permissão: PDV_COMANDA_MANAGE

`tableOrCustomerLabel` é obrigatório e tem no máximo **100 caracteres** (PDV-C011) — acima disso é
`400`, não o `500` que a violação da coluna produzia antes.

```json
{ "tableOrCustomerLabel": "Mesa 4" }
```
`201` com `ComandaResponseDTO`, status `ABERTA`. `403 SESSION_NOT_OWNED`; `404 CASH_REGISTER_SESSION_NOT_FOUND`;
`409 CASH_REGISTER_SESSION_CLOSED`.

### POST /pdv/comandas/{id}/items — Permissão: PDV_COMANDA_MANAGE

```json
{ "sku": "ESS-MENTA-50", "quantity": 1 }
```
Preço e custo vêm do catálogo, igual à venda de balcão. `201` com a comanda atualizada
(`runningTotal` recalculado). `400 INSUFFICIENT_STOCK`; `400 RESERVED_STOCK`; `403 SESSION_NOT_OWNED`;
`404 COMANDA_NOT_FOUND`/`PRODUCT_NOT_FOUND`; `409 COMANDA_NOT_OPEN`/`PRODUCT_NOT_PRICED`.

### DELETE /pdv/comandas/{id}/items/{itemId} — Permissão: PDV_COMANDA_MANAGE

Remove uma linha da comanda aberta e devolve ao estoque o que ela debitou (`ENTRADA`), mantendo a
mesa aberta (PDV-F012). Devolve a `ComandaResponseDTO` atualizada.

**As linhas `TROCA` penduradas nesta saem junto** — são cortesia e não existem sem o consumo livre
que as originou. Já um `SABOR_EXTRA` pendurado **barra** a remoção: é linha própria e pode estar
sendo cobrada, e apagá-la em cascata tiraria valor da conta sem o operador pedir.

| Erro | HTTP | Código |
|---|---|---|
| Linha inexistente, ou de outra comanda | 404 | `COMANDA_ITEM_NOT_FOUND` |
| Há `SABOR_EXTRA` pendurado na linha | 409 | `LINKED_ITEM_IS_CHARGED` |
| Comanda já fechada ou cancelada | 409 | `COMANDA_NOT_OPEN` |
| Linha já cobrada num fechamento parcial (PDV-C023) — está num pedido pago; desfazer é reembolso | 400 | `ITEM_NOT_OPEN_IN_COMANDA` |

Removido um `ROSH_EXTRA` (PDV-C027), o grupo do narguilé se acerta como num recolhimento: se a sessão
e os roshs que sobraram estão todos recolhidos, os utensílios voltam; se o narguilé ficou livre, o
próximo rosh da fila já pago entra no preparo.

Reusa `PDV_COMANDA_MANAGE` sem permissão nova: quem já pode cancelar a mesa inteira não precisa de
alçada maior para remover uma linha dela.

### GET /pdv/comandas/{id} — Permissão: PDV_READ ou ORDER_READ

`ComandaResponseDTO` em **qualquer status** (PDV-F029), com os itens lançados, o `runningTotal`, os
campos do histórico (ver `GET /pdv/comandas/history`) e `orders[]` — os pedidos que a mesa gerou,
inclusive os parciais, cada um com os pagamentos:

```json
"orders": [ { "id": 500, "orderNumber": "000001000", "closedAt": "2026-10-01T01:30:00Z",
              "totalPayable": 27.50, "status": "CONCLUIDO", "payments": [ { "method": "DINHEIRO", "...": "..." } ] } ]
```
`404 COMANDA_NOT_FOUND`.

### GET /pdv/comandas/history — Permissão: PDV_READ ou ORDER_READ

`PageResult<ComandaResponseDTO>` das mesas **encerradas** (`FECHADA` e `CANCELADA`), da mais recente
para a mais antiga por `closedAt` (PDV-F029).

| Parâmetro | Descrição |
|---|---|
| `from`, `to` | ISO-8601, recortam pelo **encerramento** (`closedAt`). Cada um opcional |
| `status` | `FECHADA` ou `CANCELADA`; sem ele, as duas. `ABERTA` é `400` |
| `customerId`, `openedBy`, `closedBy`, `warehouseCode` | igualdade |
| `tableLabel` | compara sem caixa e sem espaços nas pontas ("Mesa 4" = " mesa 4 ") |
| `boughtInStore` | `true`/`false`: só as mesas que responderam isso a "comprou na loja?" (PDV-F036). Sem ele, todas, inclusive as não respondidas |
| `page`, `size` | ≥ 0 / 1–100, default 0 / 50 |

Campos que o DTO ganhou (também no detalhe):

| Campo | Descrição |
|---|---|
| `closedBy` | Quem fechou, finalizou ou cancelou. `system` na varredura automática |
| `durationMinutes` | `closedAt − openedAt`; nulo em mesa aberta |
| `cancelReason` | Motivo do cancelamento. Junção grava "Juntada à comanda #X"; varredura, "Mesa vazia esquecida (varredura automática)" |
| `orderIds` | Todos os pedidos MESA da comanda, inclusive os parciais |
| `totalPaid` | Σ `totalPayable` dos pedidos **não reembolsados** |
| `serviceFeeTotal`, `discountTotal` | Σ taxa de serviço e desconto dos mesmos pedidos |
| `courtesyTotal` | Σ quantidade × **custo** das linhas de cortesia. A cortesia é gravada a preço zero e o preço de venda não fica guardado |
| `sessionsCount` | Linhas `SESSAO` da comanda |
| `boughtInStore`, `boughtInStoreBy`, `boughtInStoreAt` | PDV-F036 — resposta a "comprou na loja?", quem respondeu e quando. `boughtInStore` nulo = não respondido |

Só no detalhe (`GET /pdv/comandas/{id}`), PDV-F035:

| Campo | Descrição |
|---|---|
| `sessions[]` | A linha do tempo de cada linha `SESSAO`/`ROSH_EXTRA`: `itemId`, `mode`, `linkedItemId`, `productName`, `pagarNoFinal`, `status`, `desistida` (recolhida sem entrega), `esperouPor` (`PAGAMENTO`, `FILA` ou nulo), os instantes `lancadaEm`, `pagaEm` (conclusão do pedido que cobrou a linha), `inicioEm`, `entregueEm`, `recolhidaEm` e as durações em minutos `esperaMin` (lançada → início), `preparoMin` (início → entregue, ou → recolhida se desistida), `naMesaMin` (entregue → recolhida), `totalMin` (lançada → recolhida). Fase que não terminou vem nula |
| `aberturaAtePrimeiraSessaoMin` | Abertura da mesa → 1ª sessão lançada |
| `ultimoRecolhimentoAteEncerramentoMin` | Último recolhimento → encerramento. Nulo com mesa aberta ou sessão no salão |

### GET /pdv/comandas/analytics — Permissão: PDV_READ ou ORDER_READ

Indicadores das mesas **FECHADAS** com encerramento entre `from` e `to` (obrigatórios, ISO-8601,
máximo **366 dias**), opcionalmente por `warehouseCode` (PDV-F029).

```json
{
  "mesas": 42, "ticketMedio": 118.50, "permanenciaMediaMin": 96,
  "receitaTotal": 4977.00, "taxaServicoTotal": 402.30, "descontoTotal": 35.00,
  "sessoesNarguile": { "quantidade": 61, "receita": 2135.00, "esperaMediaMin": 4, "preparoMedioMin": 7,
                       "naMesaMediaMin": 58, "pagasNoFinal": 3, "desistidas": 1 },
  "compraNaLoja": { "mesasComSessao": 38, "respondidas": 30, "compraram": 9, "taxaConversao": 30.00 },
  "porAtendente": [ { "username": "ana", "mesas": 20, "receita": 2400.00 } ],
  "porMesa":      [ { "tableLabel": "Mesa 4", "mesas": 7, "receita": 910.00, "permanenciaMediaMin": 110 } ],
  "porHora":      [ { "hora": 21, "mesasAbertas": 9, "receita": 1100.00 } ]
}
```

`receita` é o `totalPayable` dos pedidos não reembolsados (inclui a taxa de serviço). `porAtendente`
usa quem **abriu** a mesa; `porMesa` agrupa pelo rótulo sem caixa e sem espaços (exibe o primeiro
visto); `porHora` usa a hora de **abertura** em America/Sao_Paulo. `porAtendente` e `porMesa` vêm
por receita decrescente; `porHora`, por hora. Sem `from`/`to`, invertido ou acima de 366 dias:
`400`.

PDV-F035: as médias de `sessoesNarguile` são sobre as linhas `SESSAO` que passaram pela fase (nulas
sem nenhuma); `esperaMediaMin` ignora as pagas no final, que não esperam. PDV-F036: `compraNaLoja`
conta só mesas com sessão, e `taxaConversao` (percentual, duas casas) é `compraram / respondidas` —
"não respondido" não entra; nula sem nenhuma resposta.

### PUT /pdv/comandas/{id}/store-purchase — Permissão: PDV_COMANDA_MANAGE

PDV-F036 — `{ "boughtInStore": true }`. Registra se o cliente da mesa comprou algo na loja. Uma
resposta por mesa, em **qualquer status** (o front pergunta ao recolher a última sessão ou ao
encerrar, e corrige pelo histórico); responder de novo sobrescreve. `204`; `400` sem
`boughtInStore`; `404 COMANDA_NOT_FOUND`. Evento `COMANDA_STORE_PURCHASE_RECORDED`.

### GET /pdv/comandas — Permissão: PDV_READ

`PageResult<ComandaResponseDTO>` das comandas `ABERTA` — as "mesas ocupadas".

| Parâmetro | Obrigatório | Descrição |
|---|---|---|
| `sessionId` | não | Restringe a um caixa. **Sem ele a listagem é da loja inteira** (PDV-C007) |
| `warehouseCode` | não | Restringe a um depósito |
| `page` | não | ≥ 0, default 0 |
| `size` | não | 1–100, default 50 |

`sessionId` era obrigatório, e era isso que obrigava o cliente a listar as sessões, filtrar as
`OPEN` e disparar **uma chamada por sessão** para remontar o salão — a decisão do dono é *caixa por
atendente, mesas compartilhadas*. Não há filtro por status da sessão de caixa porque não é preciso:
desde PDV-C005 o caixa não fecha com mesa aberta, então comanda `ABERTA` já implica sessão `OPEN`.
Continua **sem checagem de posse**.

> **Contrato alterado em PDV-C007/C012** (2026-08-30): devolvia a lista na raiz do corpo e passa a
> devolver `PageResult` — os itens saíram da raiz para `content`. `size` fora da faixa é
> `400 VALIDATION_ERROR`.

### GET /pdv/comandas/service-fee — Permissão: PDV_READ

`{ "percent": 10 }` — a taxa de serviço vigente (PDV-F015). Existe porque a taxa é aplicada por
padrão no fechamento: sem consultá-la antes, a única forma de saber quanto será cobrado seria fechar
a conta. Zero significa que a casa não cobra.

### POST /pdv/comandas/{id}/close — Permissão: PDV_COMANDA_MANAGE (+ PDV_COMANDA_DISCOUNT se discountAmount > 0)

Três campos opcionais além dos pagamentos:

| Campo | Default | Descrição |
|---|---|---|
| `discountAmount` | `0` | PDV-F014 — abatimento sobre a **conta inteira**, rateado pelo servidor entre as linhas proporcionalmente ao valor de cada uma. Exige `PDV_COMANDA_DISCOUNT` (403 `COMANDA_DISCOUNT_NOT_ALLOWED`) e respeita o mesmo teto do balcão (409 `DISCOUNT_LIMIT_EXCEEDED`). Maior que o total da conta é 409 `DISCOUNT_EXCEEDS_BILL` (PDV-C016; antes caía no 400 genérico, **sem** chegar ao 409 do teto). Não confundir com `surchargeAmount`, que é acréscimo **por linha** no open rosh |
| `applyServiceFee` | `true` | PDV-F015 — taxa de serviço. Vem **aplicada por omissão**, porque é o padrão do salão; `false` é o cliente recusando |
| `itemIds` | todas as abertas | PDV-F017 — **conta dividida**: as linhas que ESTE fechamento cobra. Com a lista, o pedido sai só com elas, são marcadas como cobradas e a comanda **continua ABERTA** com o resto; repita até zerar, e o último fechamento encerra a mesa. Desconto, taxa e troco incidem **só sobre o escopo**. Um `OPEN_ROSH` e as `TROCA`/`SABOR_EXTRA` ligados a ele têm que sair juntos: 409 `LINKED_ITEM_MUST_CLOSE_TOGETHER`. Linha inexistente ou já cobrada é 400 `ITEM_NOT_OPEN_IN_COMANDA` |

**O pagamento é validado contra `netAmount + serviceFeeAmount`**, não contra o líquido. A resposta
traz os dois números separados: `netAmount` é o que a loja vendeu, `totalPayable` é o que o cliente
pagou. A taxa fica **fora** do líquido de propósito — o líquido é a receita da casa, a taxa é
repasse ao garçom, e somá-la ali inflaria receita e margem.

```json
{ "payments": [{ "method": "DINHEIRO", "amount": 50.00 }] }
```
Mesmo contrato de pagamento de `POST /pdv/sessions/{id}/sales`: pelo menos uma linha, só
`DINHEIRO` pode exceder o total para gerar troco. Converte os itens acumulados num `Order`
concluído — `201`~`200` com `OrderResponseDTO`. **Sem novo débito de estoque**: já saiu item a
item em cada lançamento. `400 INSUFFICIENT_PAYMENT`; `403 SESSION_NOT_OWNED`;
`404 COMANDA_NOT_FOUND`; `409 COMANDA_NOT_OPEN`/`COMANDA_EMPTY`/`PAYMENT_EXCEEDS_ORDER_TOTAL`.

> **Conta dividida muda o significado de dois campos** (PDV-F017). `runningTotal` da comanda passa a
> somar **só as linhas em aberto** — é o "falta pagar", não o total consumido. E `comanda.orderId`
> passa a ser **o pedido que encerrou a mesa**; a lista completa dos pedidos dela sai filtrando
> `GET /orders?comandaId=` pelo `sales_order.comanda_id`, que é N→1.

### PATCH /pdv/comandas/{id} — Permissão: PDV_COMANDA_MANAGE

Troca o rótulo da mesa (PDV-F016) — o cliente mudou de lugar no salão.

```json
{ "tableOrCustomerLabel": "Mesa 7" }
```
`200` com a comanda. Nada de físico acontece: itens, depósito e sessão de origem seguem os mesmos, e
**nenhum estoque se move**. Antes disto o rótulo era imutável, e trocar de mesa só era possível
cancelando a comanda — o que devolvia tudo ao estoque — e relançando item a item.
`404 COMANDA_NOT_FOUND`; `409 COMANDA_NOT_OPEN`.

### POST /pdv/comandas/{id}/merge-into/{targetId} — Permissão: PDV_COMANDA_MANAGE

Junta esta mesa em outra (PDV-F016): as linhas em aberto passam para o destino e esta é encerrada.
`200` com a comanda **de destino**.

**PDV-F031 — origem já paga em parte também junta.** Vão as linhas em aberto **e o grupo inteiro**
(sessão e roshs) de toda sessão de narguilé ainda no salão, paga ou não, com os utensílios — que
estão alocados na sessão raiz e por isso seguem com ela. A linha cobrada que já saiu do salão fica na
origem, e os pedidos pagos continuam apontando para a origem, que termina **`FECHADA`** no último
deles (é receita, e o histórico e os indicadores só contam `FECHADA`). Sem nada cobrado, a origem
termina `CANCELADA`, como antes. Nos dois casos o motivo gravado é "Juntada à comanda #X".

**Nenhum estoque se move.** A mercadoria não voltou para a prateleira nem saiu de novo — mudou de
conta. Por isso a origem não recebe a `ENTRADA` que `POST /cancel` faria; o que distingue a junção na
trilha é o evento `COMANDA_MERGED`.

> PDV-C022: até 01/10/2026 a junção deixava **cópias** das linhas movidas na origem. A consulta
> [`dominios/vendas-balcao/diagnostico-pdv-c022.sql`](dominios/vendas-balcao/diagnostico-pdv-c022.sql)
> acha as gravadas antes da correção.

Os ids das linhas são **preservados** na mudança de comanda, então um `OPEN_ROSH` e as `TROCA` dele
chegam juntos e ainda ligados.

As duas mesas precisam estar `ABERTA` e no **mesmo depósito** (o estoque de cada linha saiu de um só).
`404 COMANDA_NOT_FOUND`; `409 COMANDA_NOT_OPEN`/`COMANDA_MERGE_NOT_ALLOWED`.

### POST /pdv/comandas/{id}/cancel — Permissão: PDV_COMANDA_MANAGE

Abandona a comanda sem cobrança, devolvendo ao estoque (`ENTRADA`) cada item já lançado. `200`
com a comanda `CANCELADA`. Corpo **opcional** `{ "reason": "Cliente desistiu" }` (≤ 500): o motivo
vai, aparado, para `cancelReason` no histórico (PDV-F029); em branco vale como ausente. `403 SESSION_NOT_OWNED`; `404 COMANDA_NOT_FOUND`; `409 COMANDA_NOT_OPEN`.

**PDV-C021 — mesa com linha já cobrada não é cancelada:** `409 COMANDA_PARTIALLY_CLOSED`. Cancelar
devolveria ao estoque mercadoria vendida e tiraria do histórico uma mesa com pedido pago. A saída é
remover as linhas ainda abertas e encerrar com `POST /pdv/comandas/{id}/finish`.

---

## Pedidos (visão do administrador) — `/orders`

Atravessa canais: enxerga venda de balcão e pedido de marketplace na mesma superfície. Separada do
`/pdv`, que enxerga a operação de um caixa. **Aqui aparecem custo e margem**, que o DTO do PDV omite
de propósito — `PDV_READ` é a permissão mais distribuída daquele módulo.

Três permissões, porque as consequências são diferentes: ler é inócuo, avançar estágio é expedição, e
cancelar **devolve mercadoria ao estoque**.

### GET /orders — Permissão: ORDER_READ

Filtros, todos opcionais: `channel` (`BALCAO`/`MARKETPLACE`), `status`, `customerId`, `from`, `to`
(ISO-8601, sobre a data de criação), `page`, `size`. Ordenado do mais recente para o mais antigo.

Cada linha traz `operatorName` (PED-F003): o usuário que operava o caixa do pedido (`null` sem
caixa, como pedido do app ainda não pago). Também vem em `GET /orders/{id}`.

### GET /orders/{id} — Permissão: ORDER_READ

```json
// OrderAdminResponseDTO — além dos campos do PDV:
{
  "marginAmount": 8.00,              // soma da margem dos itens; null se algum item não tem custo
  "allowedTransitions": ["CANCELADO", "SEPARADO"],
  "items": [ { "costPrice": 18.00, "marginAmount": 8.00, "...": "..." } ]
}
// 404 ORDER_NOT_FOUND
```

`marginAmount` é **nulo, não parcial**, quando algum item não tem custo congelado (pedidos anteriores
à V65): somar só os itens conhecidos produziria um número que parece a margem do pedido e não é.

CRM-F010: `paymentStatus` (`PAGO`, `PENDENTE` = marcado sem quitação, `PARCIAL`) e `receivableId`
vêm do marcado do pedido, se houver. Nas listagens `paymentStatus` fica nulo. Em `payments`, as
linhas `CORRECTED` (PDV-F030) aparecem como lastro, fora de qualquer total.

### POST /orders/{id}/status — Permissão: ORDER_FULFILL

```json
{ "status": "SEPARADO" }
// 200 → OrderAdminResponseDTO / 404 / 409 INVALID_STATUS_TRANSITION
```

`SEPARADO → ENVIADO → ENTREGUE`, nesta ordem, mais a retirada `RESERVADO → CONCLUIDO`. Consulte
`allowedTransitions` no detalhe do pedido.

**PED-C006 — esta rota só anda a esteira.** Qualquer outro destino é `409 INVALID_STATUS_TRANSITION`,
mesmo que a tabela de estados o permita: `AGUARDANDO_PAGAMENTO → PAGO/CONCLUIDO` é do webhook e da
liquidação no balcão (que gravam o pagamento, numeram o pedido e consomem a reserva), e reembolso e
cancelamento têm rota própria. A mensagem lista os destinos válidos **por esta rota**.

### POST /orders/bulk-status — Permissão: ORDER_FULFILL

O "Liberar selecionados/todos" de Vendas › Reservas. Equivale a `POST /orders/{id}/status` para cada
id, na ordem dada (ids repetidos contam uma vez): cada pedido segue a máquina de estados e é gravado
na **própria transação**, então um recusado não desfaz os outros.

```json
{ "orderIds": [101, 102, 103], "status": "CONCLUIDO" }
// 200 → { "ok": [101, 103],
//         "failed": [ { "orderId": 102, "code": "INVALID_STATUS_TRANSITION", "message": "..." } ] }
// 400 lista vazia, mais de 200 ids, id nulo ou status ausente
```

`code` em `failed` (o mesmo `errorCode` do endpoint unitário): `ORDER_NOT_FOUND`, `INVALID_STATUS_TRANSITION` ou `CONCURRENT_UPDATE`,
mais `INVALID_ORDER_STATE` para invariante de domínio de um pedido (PED-C008 — antes ela parava o
laço no meio, com os anteriores já gravados). Cada
sucesso publica `ORDER_STATUS_CHANGED` com `"bulk": true`.

### POST /orders/{id}/payments/correction — Permissão: ORDER_PAYMENT_CORRECT ou ORDER_PAYMENT_CORRECT_CLOSED

Corrige a forma de pagamento lançada errada (PIX que foi débito, dinheiro que foi crédito) **sem
apagar o registro do erro** (PDV-F030). As linhas `CAPTURED` vigentes passam a `CORRECTED` (ficam no
pedido como lastro e saem de `payment-totals` e da conferência do caixa); as informadas nascem
`CAPTURED`.

```json
{ "payments": [ { "method": "DEBITO", "amount": 80.00 } ],
  "reason": "Cliente pagou no débito, operador marcou PIX" }
// 200 → OrderAdminResponseDTO com payments
```

- A soma tem que ser **exatamente** o `totalPayable` (ou `totalPayable − marcado`, se o pedido tem
  parte marcada) — sem troco, nem em `DINHEIRO`. Se o pedido tinha troco, `changeAmount` vai a zero.
- Caixa do pedido aberto: basta `ORDER_PAYMENT_CORRECT`. Caixa já fechado: exige
  `ORDER_PAYMENT_CORRECT_CLOSED`, e a divergência por método fica registrada em
  `cash_session_adjustment` (o esperado do fechamento não é reescrito).

| Situação | HTTP | Código |
|---|---|---|
| Soma diferente do alvo | 400 | `PAYMENT_TOTAL_MISMATCH` |
| `reason` ausente ou em branco | 400 | `REASON_REQUIRED` |
| `GATEWAY_PIX` ou `MARCADO` na correção | 400 | `INVALID_PAYMENT_METHOD` |
| Caixa fechado sem `ORDER_PAYMENT_CORRECT_CLOSED` | 409 | `CASH_SESSION_CLOSED` |
| Pedido `CANCELADO`/`REEMBOLSADO` ou sem pagamento capturado | 409 | `ORDER_NOT_CORRECTABLE` |
| Pago pelo app (sem sessão, ou `GATEWAY_PIX` capturado) | 409 | `GATEWAY_PAYMENT_NOT_CORRECTABLE` |

Publica `ORDER_PAYMENT_CORRECTED` com antes/depois, motivo e `sessionWasClosed`.

### GET /orders/{id}/payment-history — Permissão: ORDER_READ

As correções do pedido, da mais antiga para a mais recente; `[]` se nunca foi corrigido.

```json
[ { "correctionId": 3, "at": "...", "by": "ana", "reason": "...",
    "before": [ /* OrderPaymentResponseDTO */ ], "after": [ /* ... */ ] } ]
```

Campos novos em `OrderPaymentResponseDTO`: `correctionId`, `originCorrectionId`, `correctedAt`,
`correctedBy` (PDV-F030) e `dueDate` (linha `MARCADO`, CRM-F010).

### POST /orders/{id}/cancel — Permissão: ORDER_CANCEL

```json
{ "reason": "cliente desistiu na entrega" }
// 200 → OrderAdminResponseDTO / 404 / 409 INVALID_STATUS_TRANSITION (já cancelado)
```

**Devolve a mercadoria ao estoque** na mesma transação: um `StockMovement` de `ENTRADA` por item, com
o `orderNumber` no motivo. Vale inclusive para pedido já entregue — cancelar um pedido entregue *é*
uma devolução, e devolução é entrada de estoque.

`reason` é obrigatório: estorno sem justificativa registrada é indistinguível de erro.

> **Ainda não acontece aqui:** estorno do **pagamento** (depende da Fatia 3, `order_payment`) e
> `REVERSED` no **cashback** (depende da Fatia 4). Marcar como reembolsado, distinto de cancelado, é
> `PDV-F007` no backlog.

---

## CRM — `/crm`

### POST /crm/customers — Permissão: CRM_CUSTOMER_MANAGE

```json
{
  "nome": "Maria Silva",           // obrigatório, máx. 255 chars
  "contato": "11999998888",         // opcional (CRM-C005), máx. 30 chars
  "email": "maria@example.com",     // opcional (CRM-C005), formato de email quando informado, máx. 255 chars, único
  "cpf": "12345678900",             // opcional, identificador OFICIAL do cadastro, exatamente 11 chars, único
  "origem": "loja-fisica"           // opcional, máx. 100 chars
}
// Response 201 + Location → CustomerResponse
// 409 CUSTOMER_EMAIL_ALREADY_EXISTS / 409 CUSTOMER_CPF_ALREADY_EXISTS
// 400 VALIDATION_ERROR (nome ausente, ou email/cpf com formato inválido)
// 400 BAD_REQUEST (nenhum dos três identificadores — cpf, email, contato — foi informado)
```

> **CRM-C005:** pelo menos um entre `cpf`, `email` e `contato` é obrigatório — não há mais um
> campo único obrigatório. Cliente sem `cpf` ("cliente leve", achado só por email/contato) é
> válido e pesquisável, mas não é elegível a cashback quando o programa existir (Fatia 4).

```json
// CustomerResponse — estagio e tags são valores reais (Kanban de atendimento e crm/tags-segmentos).
// ltv, cashback e segmento seguem como placeholders (0 / "NOVO") até os domínios de pedidos e
// cashback existirem no backend (ver crm/listagem-clientes-rfm) — não confundir "segmento"
// (RFM auto-calculado) com "estagio" (Kanban movido manualmente) nem com "tags" (livres, F007).
// Cliente recém-criado sempre vem com tags: [] (ainda não associado a nenhuma tag).
{
  "id": 1,
  "nome": "Maria Silva",
  "contato": "11999998888",
  "email": "maria@example.com",
  "cpf": "12345678900",
  "origem": "loja-fisica",
  "cadastradoEm": "2026-07-20T18:00:00Z",
  "estagio": "NOVO_LEAD",
  "ltv": 0,
  "cashback": 0,
  "segmento": "NOVO",
  "tags": []
}
```

---

### GET /crm/customers/{id} — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → CustomerResponse (tags reais do cliente) / 404 CUSTOMER_NOT_FOUND
```

CRM-F010: só aqui (não na listagem) vêm `creditLimit` (limite **efetivo**: o individual ou o padrão
`pdv.on-account.default-credit-limit`; nunca nulo), `openBalance` e `overdueBalance` do "Marcar".

---

### GET /crm/customers/lookup — Permissão: CRM_CUSTOMER_LOOKUP

```
Query: cpf (opcional), email (opcional), contato (opcional) — informe pelo menos um
// Response 200 → CustomerResponse
// 404 CUSTOMER_NOT_FOUND / 400 BAD_REQUEST (nenhum critério informado)
```

Busca pontual de balcão (CRM-F002) — o "CPF na nota?": o operador tenta achar o cliente antes de
cadastrar um novo. Permissão própria, separada de `CRM_CUSTOMER_READ`, porque achar **um** cliente
não é a mesma coisa que listar/exportar a base inteira. Quando mais de um critério vem preenchido,
a prioridade é `cpf` (identificador oficial) → `email` → `contato`. Não achando, o fluxo normal é
seguir para `POST /crm/customers` (cadastro rápido) — venda anônima de balcão nunca passa por
aqui, ela simplesmente não informa `customerId`.

---

### GET /crm/customers — Permissão: CRM_CUSTOMER_READ

```
Query: search (opcional — filtra por nome ou contato, case-insensitive), page, size (máx. 100)
// Response 200 → PageResult<CustomerResponse> — tags sempre [] aqui (evita N+1); use
// GET /crm/customers/{id} ou GET /crm/customers/{id}/tags para as tags reais
```

---

### GET /crm/customers/export — Permissão: CRM_CUSTOMER_EXPORT

```
Query: search (opcional — mesmo filtro de GET /crm/customers, mas sem paginação: exporta todos
os registros correspondentes, não só uma página)
// Response 200 → text/csv;charset=UTF-8, Content-Disposition: attachment; filename="clientes.csv"
// Response 429 RATE_LIMIT_EXCEEDED → acima de 5 requisições/hora pelo mesmo usuário
```

```
Colunas (nessa ordem): id,nome,contato,email,cpf,origem,cadastradoEm,estagio
```

Não inclui `ltv`/`cashback`/`segmento` (placeholder) nem `tags` (exigiria query em lote extra — mesma decisão de evitar N+1 da listagem paginada). Arquivo gerado com quebra de linha `\r\n` (RFC 4180), campos com vírgula/aspas/quebra de linha escapados entre aspas duplas, e prefixo BOM UTF-8 (compatibilidade com Excel para acentos). Permissão dedicada (CRM-C002, separada de `CRM_CUSTOMER_READ` desde 2026-08-04) — ler clientes não dá direito a exportar a base inteira. Rate limit (bucket `crm-export`) e `AuditEvent.CUSTOMER_LIST_EXPORTED` já existiam antes disso.

---

### POST /crm/customers/{id}/notes — Permissão: CRM_CUSTOMER_MANAGE

```json
{
  "texto": "Cliente prefere contato por WhatsApp"   // obrigatório, máx. 2000 chars
}
// Response 201 + Location → CustomerNoteResponse / 404 CUSTOMER_NOT_FOUND / 400 VALIDATION_ERROR
```

```json
// CustomerNoteResponse — autor é preenchido automaticamente com o username autenticado
{
  "id": 10,
  "customerId": 1,
  "autor": "gerente",
  "texto": "Cliente prefere contato por WhatsApp",
  "criadoEm": "2026-07-20T20:00:00Z"
}
```

---

### GET /crm/customers/{id}/notes — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → CustomerNoteResponse[] (mais recentes primeiro) / 404 CUSTOMER_NOT_FOUND
```

---

### GET /crm/customers/{id}/orders — Permissão: CRM_CUSTOMER_READ

```
// Placeholder: sempre retorna [] até o domínio de pedidos existir no backend.
// Response 200 → [] / 404 CUSTOMER_NOT_FOUND
```

---

### GET /crm/customers/{id}/cashback — Permissão: CRM_CUSTOMER_READ

```
// CRM-F003: extrato real do ledger de cashback, até 100 entradas mais recentes.
// Delega a CashbackUseCase.listCustomerEntries — mesma fonte de GET /cashback/customers/{id}/entries.
// Response 200 → CashbackEntryResponse[] (ver seção "Cashback — /cashback") / 404 CUSTOMER_NOT_FOUND
```

---

### PATCH /crm/customers/{id}/estagio — Permissão: CRM_CUSTOMER_MANAGE

```json
{
  "estagio": "EM_ATENDIMENTO"   // obrigatório — NOVO_LEAD | EM_ATENDIMENTO | QUALIFICADO | CLIENTE_ATIVO | INATIVO
}
// Response 200 → CustomerResponse (com o estagio já atualizado) / 404 CUSTOMER_NOT_FOUND
// 400 VALIDATION_ERROR (estagio ausente/inválido) / 400 BAD_REQUEST (estagio igual ao atual)
```

Cada transição é registrada com autor (username autenticado, nunca informado no body) e timestamp — ver `GET .../estagio/historico`.

---

### GET /crm/customers/{id}/estagio/historico — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → StageTransitionResponse[] (mais recentes primeiro) / 404 CUSTOMER_NOT_FOUND
```

```json
// StageTransitionResponse
{
  "id": 20,
  "customerId": 1,
  "de": "NOVO_LEAD",
  "para": "EM_ATENDIMENTO",
  "autor": "gerente",
  "transicionadoEm": "2026-07-20T20:30:00Z"
}
```

---

### GET /crm/dashboard/overview — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → CrmDashboardResponse
```

```json
// CrmDashboardResponse — ativo = estagio != INATIVO (decisão de escopo, ver crm/dashboard-overview).
// ltvMedio, disparosWhatsappMes e porSegmento são placeholders até os domínios de pedidos/cashback
// e de campanhas existirem. totalClientes, clientesAtivos e porEstagio são dados reais.
{
  "totalClientes": 42,
  "clientesAtivos": 30,
  "ltvMedio": 0,
  "disparosWhatsappMes": 0,
  "porSegmento": { "NOVO": 42 },
  "porEstagio": { "NOVO_LEAD": 20, "EM_ATENDIMENTO": 10, "QUALIFICADO": 5, "CLIENTE_ATIVO": 5, "INATIVO": 2 }
}
```

---

### POST /crm/tags — Permissão: CRM_CUSTOMER_MANAGE

```json
{
  "nome": "VIP"   // obrigatório, máx. 50 chars, único
}
// Response 201 + Location → TagResponse / 409 TAG_ALREADY_EXISTS / 400 VALIDATION_ERROR
```

```json
// TagResponse
{ "id": 1, "nome": "VIP" }
```

---

### GET /crm/tags — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → TagSummaryResponse[]
```

```json
// TagSummaryResponse — clientesCount é dado real (contagem de associações)
{ "id": 1, "nome": "VIP", "clientesCount": 3 }
```

---

### DELETE /crm/tags/{id} — Permissão: CRM_CUSTOMER_MANAGE

```
// Remove a tag e todas as suas associações (ON DELETE CASCADE)
// Response 204 / 404 TAG_NOT_FOUND
```

---

### POST /crm/customers/{id}/tags — Permissão: CRM_CUSTOMER_MANAGE

```json
{
  "tagId": 1   // obrigatório
}
// Associa a tag ao cliente (idempotente — associar de novo não duplica)
// Response 204 / 404 CUSTOMER_NOT_FOUND ou TAG_NOT_FOUND / 400 VALIDATION_ERROR
```

---

### DELETE /crm/customers/{id}/tags/{tagId} — Permissão: CRM_CUSTOMER_MANAGE

```
// Response 204 / 404 CUSTOMER_NOT_FOUND ou TAG_NOT_FOUND
```

---

### GET /crm/customers/{id}/tags — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → TagResponse[] / 404 CUSTOMER_NOT_FOUND
```

---

### POST /crm/automacoes — Permissão: CRM_CUSTOMER_MANAGE

```json
{
  "nome": "Boas-vindas",               // obrigatório, máx. 100 chars
  "gatilho": "MANUAL",                  // obrigatório — MANUAL | ENTRADA_ESTAGIO (só MANUAL dispara nesta versão)
  "segmentoAlvo": "NOVO_LEAD",          // obrigatório — um CustomerStage (Kanban), não o segmento RFM
  "canal": "EMAIL",                     // obrigatório — WHATSAPP | EMAIL | AMBOS
  "template": "Ola {nome}, seu saldo e {saldo}"  // obrigatório, máx. 2000 chars — placeholders não são interpolados nesta versão
}
// Response 201 + Location → CampaignAutomationResponse / 400 VALIDATION_ERROR / 403
```

```json
// CampaignAutomationResponse
{
  "id": 1,
  "nome": "Boas-vindas",
  "gatilho": "MANUAL",
  "segmentoAlvo": "NOVO_LEAD",
  "canal": "EMAIL",
  "template": "Ola {nome}, seu saldo e {saldo}",
  "ativa": true,
  "criadoEm": "2026-07-21T20:00:00Z"
}
```

---

### GET /crm/automacoes — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → CampaignAutomationResponse[]
```

---

### PATCH /crm/automacoes/{id}/ativa — Permissão: CRM_CUSTOMER_MANAGE

```json
{
  "ativa": false   // obrigatório
}
// Response 200 → CampaignAutomationResponse / 404 CAMPAIGN_AUTOMATION_NOT_FOUND / 400 VALIDATION_ERROR
```

---

### DELETE /crm/automacoes/{id} — Permissão: CRM_CUSTOMER_MANAGE

```
// Remove a automação e todo o seu log de disparos (ON DELETE CASCADE)
// Response 204 / 404 CAMPAIGN_AUTOMATION_NOT_FOUND
```

---

### POST /crm/automacoes/{id}/disparar — Permissão: CRM_CUSTOMER_MANAGE

```
// Resolve os clientes cujo estagio == segmentoAlvo da automação e cria 1 CampaignLogEntry por
// cliente, status PENDENTE_INTEGRACAO. NÃO envia mensagem real — o canal de envio ainda não
// existe no backend (ver crm/integracao-canal-envio, F008).
// Response 200 → CampaignLogResponse[] (uma entrada por cliente-alvo) / 404 CAMPAIGN_AUTOMATION_NOT_FOUND
```

```json
// CampaignLogResponse — convertidoEm é sempre null nesta versão (depende do domínio de pedidos,
// inexistente — ver crm/listagem-clientes-rfm)
{
  "id": 10,
  "automationId": 1,
  "customerId": 5,
  "status": "PENDENTE_INTEGRACAO",
  "disparadoEm": "2026-07-21T20:05:00Z",
  "convertidoEm": null
}
```

---

### GET /crm/automacoes/{id}/log — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → CampaignLogResponse[] (mais recentes primeiro) / 404 CAMPAIGN_AUTOMATION_NOT_FOUND
```

---

### GET /crm/canais/status — Permissão: CRM_CUSTOMER_READ

```
// Status de conexão dos canais de envio (WhatsApp/E-mail) — substitui o badge fixo
// "API WhatsApp: Conectada" hoje hardcoded no frontend. "conectado" reflete qual adapter de
// e-mail está ativo no profile (email.provider), não é um health-check de rede ao vivo.
// WhatsApp sempre reporta desconectado — não existe integração real no backend.
// Response 200 → ChannelStatusResponse[]
```

```json
[
  {
    "canal": "EMAIL",
    "conectado": true,
    "provedor": "MAILPIT",
    "detalhe": "Conectado ao Mailpit (ambiente de homologação)"
  },
  {
    "canal": "WHATSAPP",
    "conectado": false,
    "provedor": null,
    "detalhe": "Integração de WhatsApp ainda não implementada"
  }
]
```

---

## Marcados (CRM-F010) — `/receivables` e `/crm/customers/{id}/...`

Venda a prazo para cliente VIP. O marcado nasce da linha `MARCADO` na venda de balcão ou no
fechamento de mesa (ver `POST /pdv/sessions/{id}/sales`). Estados: `ABERTO → PARCIAL → QUITADO`,
`VENCIDO` (marcado pelo `ReceivableOverdueJob` às 00:05 de São Paulo) e `CANCELADO`.

### GET /receivables — Permissão: RECEIVABLE_READ

`PageResult<ReceivableResponseDTO>`, um item por pedido marcado, com os itens do pedido. Ordem:
vencimento mais próximo primeiro. Filtros opcionais: `customerId`, `status`, `overdue` (true = só
em aberto vencidos), `dueFrom`/`dueTo` (data), `createdFrom`/`createdTo` (ISO-8601), `page` (≥ 0),
`size` (1–200, default 50).

```json
{ "id": 9, "customerId": 42, "customerName": "Ana", "orderId": 500, "orderNumber": "000001000",
  "comandaId": null, "tableLabel": null,
  "items": [ { "orderItemId": 1, "sku": "ESS-MENTA", "productName": "...", "quantity": 1,
               "subtotal": 80.00, "mode": "NORMAL" } ],
  "amount": 30.00, "amountPaid": 10.00, "amountOpen": 20.00, "dueDate": "2026-10-31",
  "status": "PARCIAL", "daysOverdue": 0, "createdAt": "...", "createdBy": "caixa1",
  "settledAt": null, "cancelReason": null, "cancelledBy": null, "cancelledAt": null,
  "payments": [ { "id": 1, "batchId": 4, "amount": 10.00, "method": "PIX", "installments": null,
                  "channel": null, "provider": null, "cashSessionId": 7, "receivedBy": "caixa2",
                  "receivedAt": "..." } ] }
```

`items` é o pedido **inteiro** no momento do marcar, mesmo quando só parte foi marcada.

### GET /receivables/summary — Permissão: RECEIVABLE_READ

Marcados agrupados por cliente: `[{ customerId, customerName, openBalance, overdueBalance,
creditLimit, nextDueDate, count }]`. Sem `status`, só os em aberto (`ABERTO`, `PARCIAL`,
`VENCIDO`); `overdue=true`, só clientes com saldo vencido. Ordem: mais vencido primeiro.

### GET /receivables/{id} — Permissão: RECEIVABLE_READ

`ReceivableResponseDTO` com as quitações. `404 RECEIVABLE_NOT_FOUND`.

### GET /crm/customers/{id}/receivables — Permissão: RECEIVABLE_READ

Marcados do cliente, mais recentes primeiro (aba "Marcados" da ficha).

### GET /crm/customers/{id}/on-account-eligibility — Permissão: PDV_SALE_MANAGE, PDV_COMANDA_MANAGE ou RECEIVABLE_READ

Pré-checagem do "Marcar" para o PDV:

```json
{ "eligible": false, "reasons": ["CUSTOMER_HAS_OVERDUE"], "creditLimit": 300.00,
  "openBalance": 120.00, "overdueBalance": 40.00, "available": 180.00, "defaultDueDate": "2026-10-31" }
```

`reasons`: `CUSTOMER_NOT_ELIGIBLE` (sem tag VIP), `ON_ACCOUNT_NOT_ALLOWED` (operador sem
`PDV_SALE_ON_ACCOUNT`), `CUSTOMER_HAS_OVERDUE`, `CREDIT_LIMIT_EXCEEDED` (nada disponível).
`creditLimit` é o limite efetivo; `defaultDueDate` vem de `pdv.on-account.default-due-days`.

### POST /receivables/payments?sessionId= — Permissão: PDV_SALE_MANAGE

Recebe marcado no caixa de **quem recebe**. Exige a sessão `OPEN`, do operador e de hoje (as mesmas
regras da venda).

```json
{ "customerId": 42, "payments": [ { "method": "DINHEIRO", "amount": 50.00 } ], "receivableIds": [9, 12] }
// 200 → { "batchId": 4, "applied": [ { "receivableId": 9, "amount": 20.00, "statusAfter": "QUITADO" } ],
//         "changeAmount": 0.00, "openBalanceAfter": 0.00 }
```

- Sem `receivableIds`, abate do mais antigo (vencimento, depois criação); com eles, só neles e na
  ordem dada.
- Parcial deixa o marcado `PARCIAL`; zerado, `QUITADO`.
- Só `DINHEIRO` pode passar do saldo e gera troco; os demais acima do saldo dão
  `409 PAYMENT_EXCEEDS_BALANCE`. `MARCADO` aqui é `400 INVALID_PAYMENT_METHOD`.
- O valor entra em `payment-totals` como `receivableReceived` e, em dinheiro, no esperado do
  fechamento. O cashback da parte marcada é creditado agora, proporcional.

`403` sessão de outro operador; `409 RECEIVABLE_NOT_OPEN`, sessão fechada ou de dia anterior.
`batchId` é numérico.

### PATCH /receivables/{id}/due-date — Permissão: RECEIVABLE_MANAGE

`{ "dueDate": "2026-11-15", "reason": "..." }` — renegocia o vencimento de um marcado em aberto
(hoje ou depois; `reason` obrigatório, ≤ 500). `200` com o marcado. Publica
`RECEIVABLE_DUE_DATE_CHANGED`.

### POST /receivables/{id}/cancel — Permissão: RECEIVABLE_MANAGE

`{ "reason": "Lançado no cliente errado" }` — perdão ou erro de lançamento. **Não mexe em estoque**:
para devolver a mercadoria, o caminho é o reembolso do pedido, que já cancela o marcado em aberto.
`409 RECEIVABLE_NOT_OPEN` se não está em aberto. Publica `RECEIVABLE_CANCELLED`.

### PUT /crm/customers/{id}/credit-limit — Permissão: RECEIVABLE_MANAGE

`{ "creditLimit": 300.00 }` (≥ 0, 2 casas). `null` volta ao limite padrão da loja. `204`. Endpoint
próprio, e não campo do `PUT /crm/customers/{id}`: o limite é decisão do gerente e o `PUT` do
cadastro regrava a ficha inteira. `400` valor negativo; `404` cliente inexistente. Publica
`CUSTOMER_CREDIT_LIMIT_CHANGED`.

**Configuração** (`system_config`, sem tela — `/system/config` só aceita chaves `auth.*`):
`pdv.on-account.default-due-days` (default `30`) e `pdv.on-account.default-credit-limit` (default
`0`, ou seja, sem limite individual o cliente não marca).

---

## Cashback — `/cashback`

CRM-F003, esta fatia cobre **ganhar** cashback (taxa por abrangência, ledger, lançamento na
conclusão da venda, expiração) e as consultas de saldo/extrato/margem. Resgate no balcão
(`CASHBACK_REDEEM`) e ajuste manual (`CASHBACK_ADJUST`) ficam para uma fatia seguinte, isolada.

### POST /cashback/rates — Permissão: CASHBACK_RATE_MANAGE

```json
{
  "scope": "CATEGORY",       // obrigatório — GLOBAL | CATEGORY | SKU
  "scopeRef": "narguile",    // obrigatório para CATEGORY/SKU; ausente para GLOBAL
  "percent": 6.5,            // obrigatório, 0–100
  "validFrom": null,         // opcional — omitido vale a partir de agora
  "validTo": null            // opcional — omitido é vigência em aberto
}
// Response 201 → CashbackRateResponse
// 409 CASHBACK_RATE_ALREADY_EXISTS (já existe taxa ativa e em aberto para a mesma abrangência)
// 400 VALIDATION_ERROR
```

```json
// CashbackRateResponse
{
  "id": 1,
  "scope": "GLOBAL",
  "scopeRef": null,
  "percent": 3.0,
  "active": true,
  "validFrom": "2026-07-29T00:00:00Z",
  "validTo": null,
  "createdAt": "2026-07-29T00:00:00Z"
}
```

---

### GET /cashback/rates — Permissão: CASHBACK_READ

```
Query params: page (default 0), size (default 20, máx 100)
// Response 200 → Page<CashbackRateResponse>
```

---

### PATCH /cashback/rates/{id} — Permissão: CASHBACK_RATE_MANAGE

```json
// Campo ausente ou nulo é mantido. Não altera scope/scopeRef.
{
  "percent": 4.0,
  "active": null,
  "validTo": null
}
// Response 200 → CashbackRateResponse / 404 CASHBACK_RATE_NOT_FOUND
```

---

### GET /cashback/rates/resolve?sku= — Permissão: CASHBACK_READ

```
// Cadeia SKU → CATEGORY → GLOBAL — devolve a regra ativa e vigente mais específica.
// Response 200 → CashbackRateResponse (body vazio se nenhuma taxa se aplica) / 404 PRODUCT_NOT_FOUND
```

---

### GET /cashback/margin-impact?maxShare= — Permissão: CASHBACK_READ

```
// Produtos cuja taxa vigente consome mais de maxShare% da margem do item (Pricing.marginPercent()).
// Response 200 → CashbackMarginImpactResponse[] / 400 VALIDATION_ERROR (maxShare ausente/fora de 0–100)
```

```json
// CashbackMarginImpactResponse
{
  "sku": "CARV-001",
  "name": "Carvão",
  "marginPercent": 18.2,
  "cashbackPercent": 3.0,
  "marginShareConsumed": 16.48   // cashbackPercent / marginPercent * 100
}
```

---

### GET /cashback/customers/{id} — Permissão: CASHBACK_READ

```
// Response 200 → CashbackBalanceResponse / 404 CUSTOMER_NOT_FOUND
```

```json
// CashbackBalanceResponse
{
  "available": "0.00",     // SUM(amount) das entradas já liberadas (available_at <= now())
  "pending": "3.00",       // ganhos EARNED ainda em carência
  "expiringSoon": "0.00"   // disponível que vence nos próximos 30 dias
}
```

---

### GET /cashback/customers/{id}/entries — Permissão: CASHBACK_READ

```
Query params: page (default 0), size (default 20, máx 100)
// Extrato paginado, mais recente primeiro.
// Response 200 → Page<CashbackEntryResponse> / 404 CUSTOMER_NOT_FOUND
```

```json
// CashbackEntryResponse
{
  "id": 10,
  "customerId": 42,
  "orderId": 100,
  "orderItemId": 9,
  "type": "EARNED",         // EARNED | REDEEMED | REVERSED | EXPIRED — só EARNED/EXPIRED são escritos nesta fatia
  "amount": "3.00",
  "availableAt": "2026-08-05T12:00:00Z",   // paidAt + carência (default 7 dias)
  "expiresAt": "2027-02-01T12:00:00Z",     // availableAt + expiração (default 180 dias)
  "reversesEntryId": null,
  "createdAt": "2026-07-29T12:00:00Z"
}
```

Ledger **append-only**: nenhuma linha é atualizada nem deletada. Saldo é sempre
`SUM(amount) WHERE available_at <= now()` — cobre `EARNED` (positivo) e os futuros `REDEEMED`/
`REVERSED`/`EXPIRED` (negativos, referenciando a entrada original via `reversesEntryId`).

---

## Audit Logs — `/audit-logs`

### GET /audit-logs — Permissão: AUDIT_READ

```
Query params:
  username: string  (opcional)
  action:   string  (opcional — ver /audit-logs/actions para valores válidos)
  from:     ISO-8601 datetime (ex: 2026-05-01T00:00:00Z)
  to:       ISO-8601 datetime (ex: 2026-05-31T23:59:59Z)
  page:     int (default: 0)
  size:     int (default: 20, max: 100)
```

```json
// Response 200
{
  "content": [
    {
      "id": 1,
      "who": "admin",
      "action": "USER_LOGGED_IN",
      "target": null,           // "user:joao", "role:ROLE_ADMIN", "permission:USER_READ"
      "details": null,          // JSON string com detalhes extras (pode ser null)
      "ipAddress": "192.168.1.1",
      "timestamp": "2026-05-30T16:00:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 500, "totalPages": 25
}
```

---

### GET /audit-logs/actions — Permissão: AUDIT_READ

Retorna todos os tipos de evento válidos para uso no filtro `?action=`.

```json
// Response 200
["ACCOUNT_LOCKED", "EMAIL_CHANGE_CONFIRMED", "LOGIN_FAILED", ...]
```

**Todos os EventType disponíveis:**

| Grupo | Eventos |
|-------|---------|
| Auth | `USER_LOGGED_IN`, `USER_LOGGED_OUT`, `USER_SESSIONS_CLEARED`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `TOKEN_THEFT_DETECTED` |
| Lifecycle | `USER_REGISTERED`, `USER_EMAIL_VERIFIED`, `USER_CREATED`, `USER_DELETED`, `USER_UPDATED`, `USER_EMAIL_CHANGED`, `USER_ROLE_ASSIGNED`, `USER_ROLE_REMOVED`, `USER_ENABLED`, `USER_DISABLED`, `USER_PASSWORD_CHANGED` |
| Password | `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED` |
| Email | `EMAIL_CHANGE_REQUESTED`, `EMAIL_CHANGE_CONFIRMED` |
| RBAC | `ROLE_CREATED`, `ROLE_DELETED`, `PERMISSION_CREATED`, `PERMISSION_DELETED`, `PERMISSION_ASSIGNED_TO_ROLE`, `PERMISSION_REMOVED_FROM_ROLE` |
| 2FA | `TOTP_ENABLED`, `TOTP_DISABLED`, `TOTP_BACKUP_CODES_REGENERATED`, `TOTP_REPLACED` |
| DEV | `DEV_ELEVATION_COMPLETED` |
| OAuth | `OAUTH_GOOGLE_LOGIN` |
| Segurança | `ACCESS_DENIED` |

---

## Notificações — `/notifications`

Todas as rotas exigem autenticação Bearer. Operações são sempre escopadas ao usuário autenticado — não é possível ler ou marcar notificações de outro usuário.

### GET /notifications — Autenticado

Lista as notificações do usuário. Suporta filtro por não-lidas e paginação.

| Parâmetro | Tipo | Default | Descrição |
|-----------|------|---------|-----------|
| `unreadOnly` | boolean | `false` | Se `true`, retorna apenas notificações não lidas |
| `page` | int | `0` | Número da página (começa em 0) |
| `size` | int | `20` | Tamanho da página (máx. 100) |

```json
// Response 200
{
  "content": [
    {
      "id": 1,
      "type": "PASSWORD_CHANGED",
      "title": "Senha alterada",
      "body": "Sua senha foi alterada. Se não foi você, contate o suporte.",
      "read": false,
      "readAt": null,
      "createdAt": "2026-06-09T03:04:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

### GET /notifications/unread-count — Autenticado

Retorna o total de notificações não lidas do usuário autenticado.

```json
// Response 200
{ "count": 3 }
```

---

### PATCH /notifications/{id}/read — Autenticado

Marca uma notificação específica como lida. Silencioso se a notificação não pertencer ao usuário autenticado (não retorna 403 — por design, para não vazar IDs).

```
// Response 204 No Content
```

---

### PATCH /notifications/read-all — Autenticado

Marca todas as notificações do usuário autenticado como lidas.

```
// Response 204 No Content
```

---

### DELETE /notifications/{id} — Autenticado

Remove permanentemente uma notificação do usuário autenticado. Silencioso se a notificação não pertencer ao usuário (não retorna 403 — por design, para não vazar IDs).

```
// Response 204 No Content
```

---

### POST /notifications/stream-ticket — Autenticado

Emite um bilhete de uso único para abrir o stream SSE (PLAT-C051).

```json
// Response 200
{ "ticket": "kK3v9c1Zx0aQ8m2s7Lb4eR6tY5uI1oP3aS0dF7gH9jK", "expiresInSeconds": 30 }
```

**Existe porque a API `EventSource` do navegador não envia headers.** Sem isto,
`GET /notifications/stream` respondia 401 para todo cliente de navegador — não por causa do token,
mas porque a requisição chegava sem autenticação nenhuma — e notificação em tempo real simplesmente
não existia. O efeito extrapolava o endpoint: o cliente reagia ao 401 refazendo a sessão, estourava
o rate limit do `/auth/refresh` e derrubava o usuário no meio de qualquer fluxo.

**Não é um token de acesso.** Vale para uma rota, um uso e 30 segundos, e é queimado na abertura —
cada reconexão precisa de um bilhete novo. A alternativa óbvia, aceitar o JWT em `?token=`, foi
recusada: query string entra em log de acesso, histórico de proxy e cabeçalho `Referer`, e o access
token vale 15 minutos em **toda** a API.

---

### GET /notifications/stream — Autenticado (Bearer **ou** `?ticket=`)

Abre uma conexão SSE (Server-Sent Events) para receber notificações em tempo real. Cada notificação persistida é enviada como evento `notification` no stream.

| Detalhe | Valor |
|---------|-------|
| Content-Type | `text/event-stream` |
| Timeout | 30 minutos |
| Nome do evento SSE | `notification` |
| Autenticação | `Authorization: Bearer` **ou** `?ticket=` de `POST /notifications/stream-ticket` |
| Rate limit | bucket `notifications-stream`, **por usuário** (30/min) — antes era por IP, e num salão atrás de um NAT um cliente em laço de reconexão consumia o balde de todos |

```
GET /notifications/stream?ticket=kK3v9c1Zx0aQ8m2s7Lb4eR6tY5uI1oP3aS0dF7gH9jK
```

O Bearer continua funcionando e tem precedência: quem consegue mandar o header (curl, integração
servidor-a-servidor) não precisa de bilhete. O caminho do bilhete só age quando não há autenticação
nenhuma no contexto.

```
// Exemplo de evento recebido
event: notification
data: {"id":42,"type":"PASSWORD_CHANGED","title":"Senha alterada","body":"...","read":false,"readAt":null,"createdAt":"2026-06-09T03:04:00Z"}
```

---

### GET /notifications/preferences — Autenticado

Retorna as preferências de notificação do usuário para todos os `NotificationType`. Tipos sem preferência explícita retornam com `inAppEnabled: true, emailEnabled: true` (padrão).

```json
// Response 200
[
  { "type": "PASSWORD_CHANGED", "inAppEnabled": true, "emailEnabled": true },
  { "type": "ACCOUNT_LOCKED",   "inAppEnabled": true, "emailEnabled": false },
  ...
]
```

---

### PUT /notifications/preferences/{type} — Autenticado

Atualiza a preferência de notificação para um tipo específico. O path `{type}` deve ser um valor válido de `NotificationType`.

```json
// Request body
{ "inAppEnabled": false, "emailEnabled": true }

// Response 200
{ "type": "PASSWORD_CHANGED", "inAppEnabled": false, "emailEnabled": true }
```

| Status | Condição |
|--------|----------|
| 200 | Preferência atualizada com sucesso |
| 400 `INVALID_ENUM_VALUE` | `{type}` inválido — não é um `NotificationType` reconhecido; a mensagem de erro lista os valores aceitos |
| 401 | Sem autenticação |
| 429 | Rate limit atingido — header `Retry-After: <seg>` |

---

### Tipos de notificação (`NotificationType`)

| Tipo | Evento que dispara | Quem recebe | E-mail |
|------|-------------------|-------------|--------|
| `PASSWORD_CHANGED` | `USER_PASSWORD_CHANGED`, `PASSWORD_RESET_COMPLETED`, `OAUTH_GOOGLE_LINKED` (primeiro login com Google) | o próprio usuário | ✅ alerta com data, IP e dispositivo |
| `ACCOUNT_LOCKED` | `ACCOUNT_LOCKED` | o próprio usuário | ✅ alerta com data, IP e dispositivo |
| `TOTP_ENABLED` | `TOTP_ENABLED` | o próprio usuário | ✅ alerta com data, IP e dispositivo |
| `TOTP_DISABLED` | `TOTP_DISABLED` | o próprio usuário | ✅ alerta com data, IP e dispositivo |
| `TOKEN_THEFT_DETECTED` | `TOKEN_THEFT_DETECTED` | o próprio usuário | ✅ alerta com data, IP e dispositivo |
| `EMAIL_CHANGED` | `USER_EMAIL_CHANGED` (só in-app), `EMAIL_CHANGE_CONFIRMED` | o próprio usuário | ✅ confirmação no endereço **novo** |
| `ROLE_ASSIGNED` | `USER_ROLE_ASSIGNED` | o próprio usuário | ✅ |
| `ROLE_REMOVED` | `USER_ROLE_REMOVED` | o próprio usuário | ✅ |
| `ACCOUNT_DISABLED` | `USER_DISABLED` | o próprio usuário | ✅ |
| `SYSTEM` | lote vencendo, kit bloqueado, ponto de reposição (in-app) | `ESTOQUE_STOCK_MANAGE` | ❌ |
| `ESTOQUE` | produto abaixo do ponto de reposição | `ESTOQUE_STOCK_MANAGE` | ✅ um e-mail por operação, com todos os SKUs |
| `CAIXA` | `CASH_SESSION_OPENED`, `CASH_MOVEMENT_REGISTERED`, `CASH_SESSION_CLOSED`, caixa aberto há 12h (job de hora em hora, também para o operador) | `FINANCEIRO_READ` (abertura e sangria/suprimento não vão para quem fez) | ✅ fechamento com conferência da gaveta, vendas por forma de pagamento, fiado e sangrias |
| `OPERACAO` | `ORDER_CANCELLED`, `ORDER_REFUNDED`, `ORDER_PAYMENT_CORRECTED`, `PRODUCT_PRICE_CHANGED` | `FINANCEIRO_READ` (menos quem fez) | ✅ |
| `RESUMO` | job diário às 8h (`email.digest.cron`) | `FINANCEIRO_READ` | ✅ vendas de ontem por canal, mais vendidos, caixas do dia e fiado vencido |
| `DEV` | `BUG_REPORT_CREATED`, `INTEGRATION_UPDATED`, `PAYMENT_WEBHOOK_FAILED`, eventos de segurança e de RBAC, erro 500, falha de envio de e-mail | `ROLE_DEV` | ✅ (falha de envio de e-mail só in-app); repetições agrupadas por 15 min |

Fora das preferências também: **fiado** para o cliente — comprovante ao marcar (`RECEIVABLE_CREATED`), lembrete 3 dias antes e no dia do vencimento (job às 9h, `pdv.on-account.reminder.cron`) e confirmação de pagamento (`RECEIVABLE_PAID`).

Fora das preferências: **boas-vindas** (`USER_EMAIL_VERIFIED`, `USER_CREATED`) e e-mails do **comprador** — confirmação, status, cancelamento/reembolso (só pedidos `MARKETPLACE`, com itens e valores) e comprovante do PDV (`POST /pdv/sales/{id}/receipt/email`). Todos os e-mails usam nome, logo e rodapé de Dados da loja; links do painel usam `email.app-base-url` (`EMAIL_APP_BASE_URL`).

> **Preferências:** o comportamento de cada coluna ("in-app" e "email") pode ser sobrescrito individualmente via `PUT /notifications/preferences/{type}`. O `NotificationEventListener` verifica as preferências antes de persistir ou enviar email.

---

## Stats — `/stats`

### GET /stats — Permissões: USER_READ **e** ROLE_READ

```json
// Response 200
{
  "totalUsers": 100,
  "activeUsers": 95,
  "disabledUsers": 5,
  "totalRoles": 3,
  "totalPermissions": 25
}
```

---

## System Config — `/system/config`

Gerenciamento de feature flags em runtime. Apenas flags da whitelist `PUBLIC_KEYS` podem ser alteradas via API (`auth.google.enabled`, `auth.google.register.enabled`, `auth.registration.enabled`, `auth.forgot-password.enabled`). Flags de sistema como `security.maintenance.enabled` e `security.2fa.required` só podem ser alteradas diretamente no banco.

### GET /system/config/public — Público

Retorna as feature flags públicas (sem autenticação). Inclui apenas as chaves da whitelist que existem no banco.

```json
// Response 200
{
  "auth.google.enabled": "true",
  "auth.google.register.enabled": "true",
  "auth.registration.enabled": "true",
  "auth.forgot-password.enabled": "true"
}
```

### GET /system/config — Autoridade: DEV_ELEVATED

Retorna todas as feature flags do banco.

```json
// Response 200
{
  "auth.google.enabled": "true",
  "auth.registration.enabled": "true",
  "security.maintenance.enabled": "false",
  "security.2fa.required": "false",
  "module.audit-logs.enabled": "true",
  "module.roles.enabled": "true"
}
```

**Erros:** `401` sem autenticação, `403` sem `DEV_ELEVATED`.

### PUT /system/config/{key} — Autoridade: DEV_ELEVATED

Atualiza uma flag da whitelist pública.

```json
// Request body
{ "value": "false" }
```

| Campo | Tipo | Validação |
|-------|------|-----------|
| `value` | string | `@NotNull`, máximo 255 caracteres |

**Responses:**
- `204 No Content` — atualizado com sucesso (evicta cache imediatamente)
- `400 INVALID_ARGUMENT` — chave não está na whitelist ou body inválido
- `401` — sem autenticação
- `403` — sem `DEV_ELEVATED`

---

## System Info — `/system/info`

### GET /system/info — Autoridade: DEV_ELEVATED

Retorna informações do ambiente ativo. Útil para diagnosticar qual perfil está rodando.

```json
// Response 200
{
  "status": "UP",
  "profile": "dev",
  "profiles": ["dev"]
}
```

**Erros:** `401` sem autenticação, `403` sem `DEV_ELEVATED`.

---

## Tipos TypeScript

```typescript
// ---- Tokens ----

interface TokenPairResponse {
  accessToken: string;
  refreshToken: string;      // também enviado como cookie HttpOnly
  tokenType: 'Bearer';
  expiresIn: number;         // segundos — 900 (15 min) em dev
}

interface TwoFactorChallengeResponse {
  status: 'PENDING_2FA';
  challengeToken: string;
  expiresInSeconds: number;  // 300 (5 min)
}

type LoginResponse = TokenPairResponse | TwoFactorChallengeResponse;

// Discriminador:
function isPending2FA(r: LoginResponse): r is TwoFactorChallengeResponse {
  return (r as TwoFactorChallengeResponse).status === 'PENDING_2FA';
}


// ---- User ----

// Retornado por GET /users, GET /users/{id}, POST /users, PATCH /users/{id}
interface UserResponse {
  id: number;
  username: string;
  enabled: boolean;
  email: string | null;
  emailVerified: boolean;
  avatarUrl: string | null;  // URL pública do avatar ou null se sem avatar
  createdAt: string;         // ISO-8601
  roles: string[];           // ex: ["ROLE_ADMIN"]
  permissions: string[];     // ex: ["USER_READ", "ROLE_READ"]
}

// Retornado por GET /users/me e PATCH /users/me
// Adiciona pendingEmail ao UserResponse (troca de email em andamento)
interface UserProfileResponse extends UserResponse {
  pendingEmail: string | null;  // não-nulo = código enviado ao novo endereço, aguardando confirmação
}

// Retornado por POST /users/me/avatar
interface AvatarUploadResponse {
  avatarUrl: string;  // nova URL — use como novo valor de UserProfileResponse.avatarUrl
}


// ---- RBAC ----

interface RoleResponse {
  id: number;
  name: string;          // sempre prefixo ROLE_
  permissions: string[];
}

interface PermissionResponse {
  id: number;
  name: string;
}


// ---- Paginação ----

interface PageResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}


// ---- Session ----

interface SessionInfo {
  id: number;
  createdAt: string;    // ISO-8601
  expiresAt: string;
  ipAddress: string | null;
  userAgent: string | null;
}


// ---- Audit ----

interface AuditLogEntry {
  id: number;
  who: string;
  action: string;           // EventType
  target: string | null;    // "user:x", "role:y", "permission:z"
  details: string | null;   // JSON string — parsear se precisar
  ipAddress: string | null;
  timestamp: string;        // ISO-8601
}


// ---- Stats ----

interface StatsResponse {
  totalUsers: number;
  activeUsers: number;
  disabledUsers: number;
  totalRoles: number;
  totalPermissions: number;
}


// ---- Erros ----

interface ApiError {
  message: string;
  errorCode: string;
  timestamp: string;   // ISO-8601
  path: string;
  traceId: string;
}

// 2FA Setup
interface TotpSetupResponse {
  secret: string;
  otpauthUri: string;   // renderizar como QR code
}

interface TotpConfirmResponse {
  backupCodes: string[];  // 8 códigos XXXX-XXXX-XXXX — exibir uma vez
}
```

---

## Permissões disponíveis

| Permissão | Descrição |
|-----------|-----------|
| `USER_CREATE` | Criar conta de usuário |
| `USER_READ` | Listar e visualizar usuários |
| `USER_UPDATE` | Atualizar dados básicos (admin) |
| `USER_DELETE` | Deletar conta |
| `USER_ROLE_ASSIGN` | Atribuir/remover roles |
| `USER_STATUS` | Ativar/desativar conta |
| `ROLE_CREATE` | Criar role |
| `ROLE_READ` | Listar roles |
| `ROLE_DELETE` | Deletar role |
| `ROLE_MANAGE_PERMISSIONS` | Associar/remover permissões de roles |
| `PERMISSION_CREATE` | Criar permissão |
| `PERMISSION_READ` | Listar permissões |
| `PERMISSION_DELETE` | Deletar permissão |
| `AUDIT_READ` | Ver audit logs |
| `ESTOQUE_PRODUCT_READ` | Listar produtos do estoque |
| `ESTOQUE_PRODUCT_READ` **ou** `ESTOQUE_STOCK_MANAGE` | `GET /estoque/movements` — leitura do ledger (EST-C015) |
| `ESTOQUE_PRODUCT_MANAGE` | Criar/gerenciar produtos do estoque |
| `ESTOQUE_WAREHOUSE_READ` | Listar depósitos e consultar saldo |
| `ESTOQUE_WAREHOUSE_MANAGE` | Criar/gerenciar depósitos |
| `ESTOQUE_STOCK_MANAGE` **ou** `PDV_COMANDA_MANAGE` | `POST /estoque/open-packages/{sku}/replace` — "Repor essência" (EST-F027). Quem repõe é o **atendente**, que tem a segunda e não a primeira |
| `ESTOQUE_PRODUCT_READ` **ou** `PDV_COMANDA_MANAGE` | `GET /estoque/open-packages` e `GET /estoque/open-packages/{sku}` — o contador da lata alimenta a tela de sessão do atendente |
| `ESTOQUE_STOCK_MANAGE` | `POST /estoque/movements`, `POST /estoque/conversions`, `PUT /estoque/products/{sku}/reorder-point`, `GET /estoque/integrity/orphan-skus`, `GET /estoque/integrity/reservation-mismatch` e todo o `/estoque/stock-counts` (balanço de inventário) |
| `ESTOQUE_RESERVATION_READ` | `GET /estoque/reservations` e `GET /estoque/reservations/{id}` |
| `ESTOQUE_KIT_MANAGE` | `PUT /estoque/products/{sku}/kit` — definir a receita de um kit |
| `ESTOQUE_PRODUCT_MANAGE` | `POST /estoque/products`, `PATCH /estoque/products/{sku}` e `.../active` |
| `ESTOQUE_WAREHOUSE_MANAGE` | `POST /estoque/warehouses`, `PATCH /estoque/warehouses/{code}` e `.../active` |
| `CRM_CUSTOMER_READ` | Leituras de `/crm/**` |
| `CRM_CUSTOMER_MANAGE` | Escritas de `/crm/**` |
| `CRM_CUSTOMER_LOOKUP` | `GET /crm/customers/lookup` — busca pontual por cpf/email/contato, separada de `CRM_CUSTOMER_READ` |
| `CRM_CUSTOMER_EXPORT` | `GET /crm/customers/export` — export CSV da base inteira, separada de `CRM_CUSTOMER_READ` |
| `CASHBACK_RATE_MANAGE` | `POST`/`PATCH /cashback/rates` — criar e alterar taxa de cashback |
| `CASHBACK_READ` | Leituras de `/cashback/**` — taxas, saldo, extrato e diagnóstico de margem |
| `COMPRAS_READ` | `GET /compras/suppliers` |
| `COMPRAS_RECEIPT_MANAGE` | `POST /compras/goods-receipts` — recebimento de mercadoria; também `POST /compras/goods-receipts/nfe-preview`/`.../nfe-confirm` — importação de NF-e (EST-F005) |
| `COMPRAS_SUPPLIER_MANAGE` | `POST /compras/suppliers`, `PATCH /compras/suppliers/{id}` e `.../active` — cadastro de fornecedor (COM-F001). Própria e não `COMPRAS_RECEIPT_MANAGE` reaproveitada: receber mercadoria é rotina de balcão, cadastrar fornecedor grava CNPJ, que é dado de compliance |
| `PDV_READ` | `GET /pdv/sessions` |
| `PDV_SALE_MANAGE` | `POST /pdv/sessions/{id}/sales` — venda com baixa de estoque |
| `PDV_COMANDA_MANAGE` | `POST /pdv/comandas` e `.../items`/`.../close`/`.../cancel` — comanda de mesa (PDV-F009) |
| `PDV_READ` **ou** `ORDER_READ` | `GET /pdv/comandas/{id}`, `/pdv/comandas/history` e `/pdv/comandas/analytics` (PDV-F029) |
| `PDV_SALE_ON_ACCOUNT` | Linha `MARCADO` na venda ou no fechamento de mesa (CRM-F010). Só ADMIN |
| `RECEIVABLE_READ` | Leituras de `/receivables/**` e `GET /crm/customers/{id}/receivables` (CRM-F010). ADMIN e ATENDENTE |
| `RECEIVABLE_MANAGE` | Renegociar e cancelar marcado, `PUT /crm/customers/{id}/credit-limit` (CRM-F010). Só ADMIN |
| `ORDER_PAYMENT_CORRECT` | `POST /orders/{id}/payments/correction` com o caixa do pedido aberto (PDV-F030). ADMIN e ATENDENTE |
| `ORDER_PAYMENT_CORRECT_CLOSED` | A mesma correção com o caixa já fechado (PDV-F030). Só ADMIN |
| `ECOMMERCE_READ` | Acesso ao endpoint stub `GET /ecommerce/carts` |
| `FINANCEIRO_READ` | `GET /financeiro/cash-flow` e `.../cash-flow/summary` |
| `FINANCEIRO_CASH_FLOW_MANAGE` | `POST`/`PATCH`/`DELETE /financeiro/cash-flow` — criar, editar e remover lançamento |
| `LOGISTICA_READ` | Acesso ao endpoint stub `GET /logistica/shipments` |

---

## PasswordPolicy

```
Mínimo: 8 caracteres
Máximo: 120 caracteres
Deve conter: 1 maiúscula, 1 minúscula, 1 dígito, 1 especial
Regexp: ^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z\d]).+$
```

---

## Cookie refreshToken

| Atributo | Valor |
|----------|-------|
| Name | `refreshToken` |
| Path | `/auth` |
| HttpOnly | `true` |
| SameSite | `Strict` |
| Max-Age | 604800 (7 dias) |
| Secure | `true` em hml/prod, `false` em dev |

O browser envia o cookie **automaticamente** apenas em requisições para `/auth/*`.  
No Angular: `withCredentials: true` apenas nas chamadas a `/auth/*` (login, refresh, logout, 2fa/verify).

---

## Store Integrations — `/store/integrations`

Tokens de integração da loja (Configurações > Dados da loja > Integrações). Hoje só e-mail via **Resend**. Configuração em `system_config` (`integration.email.*`), chave da API cifrada com AES-256-GCM (mesma chave do TOTP, `totp.encryption.key`) e **nunca devolvida** — só `apiKeyConfigured` e `apiKeyLast4`. As chaves `integration.*` não aparecem em `GET /system/config`.

**Precedência:** com `enabled=true` (exige chave + `fromEmail`), todos os e-mails do sistema saem pelo Resend da loja em qualquer ambiente (`RoutingEmailAdapter`). Desativada, vale o provedor do ambiente (`email.provider` = logging | mailpit | resend).

### GET /store/integrations/email — Permissão: INTEGRATION_MANAGE

```json
// Response 200
{
  "enabled": true,
  "provider": "RESEND",
  "fromEmail": "contato@mahaltabacaria.com.br",
  "fromName": "Mahal Tabacaria",
  "replyTo": null,
  "apiKeyConfigured": true,
  "apiKeyLast4": "x9Qa",
  "activeProvider": "RESEND",
  "activeProviderDetail": "Resend configurado em Dados da loja > Integrações (remetente contato@mahaltabacaria.com.br)",
  "environmentProvider": "MAILPIT",
  "mailpitUiUrl": "http://localhost:8025",
  "updatedAt": "2026-10-05T17:00:00Z",
  "updatedBy": "admin"
}
```

`environmentProvider` é o provedor do ambiente (`email.provider`), o que vale com a integração desativada. `mailpitUiUrl` (prop `mailpit.ui-url` / `MAILPIT_UI_URL`) só vem quando esse provedor é `MAILPIT`. É a caixa de entrada que o admin embute na aba "Caixa de teste" e fica `null` em prod.

### PUT /store/integrations/email — Permissão: INTEGRATION_MANAGE

```json
// Request body
{ "enabled": true, "provider": "RESEND", "fromEmail": "contato@mahaltabacaria.com.br",
  "fromName": "Mahal Tabacaria", "replyTo": null, "apiKey": "re_..." }
```

`apiKey` ausente/`null` mantém a chave salva; `""` remove. Responde como o GET. Publica `INTEGRATION_UPDATED` na auditoria (sem a chave).

**Erros:** `422 INVALID_EMAIL_INTEGRATION` — ativar sem chave ou remetente, e-mail de remetente/resposta inválido; `403` sem `INTEGRATION_MANAGE`.

### POST /store/integrations/email/test — Permissão: INTEGRATION_MANAGE

Envia exemplos com dados fictícios, **de forma síncrona**. Sem `sample`, envia um de cada (19).

- `target: "STORE"` (padrão): pela configuração salva da loja (mesmo desativada).
- `target: "ENVIRONMENT"`: pelo provedor do ambiente (Mailpit em dev/hml, log sem provedor, Resend do env em prod). Não exige a integração da loja.

```json
// Request body
{ "to": "voce@exemplo.com", "sample": "PASSWORD_RESET", "target": "STORE" }
// Response 200
[ { "sample": "PASSWORD_RESET", "success": false,
    "error": "Failed to send email: 403 {\"name\":\"validation_error\",\"message\":\"The domain is not verified\"}" } ]
```

`sample` ∈ `VERIFICATION_CODE`, `PASSWORD_RESET`, `EMAIL_CHANGE`, `PASSWORD_CHANGED`, `ACCOUNT_LOCKED`, `TOTP_STATUS`, `TOKEN_THEFT`, `ORDER_CONFIRMATION`, `ORDER_STATUS_UPDATE`, `ORDER_CANCELLATION`, `WELCOME`, `PURCHASE_RECEIPT`, `CASH_SESSION_CLOSED`, `STOCK_REORDER_ALERT`, `DEV_ALERT`, `ACCOUNT_CHANGE`, `RECEIVABLE_REMINDER`, `CASH_SESSION_STALE`, `DAILY_DIGEST`.

**Erros:** `400` destinatário inválido; `422 INVALID_EMAIL_INTEGRATION` sem chave/remetente salvos (só com `target=STORE`).

## Configuração CORS (dev)

Backend: `CORS_ALLOWED_ORIGINS=http://localhost:4200`, `CORS_ALLOW_CREDENTIALS=true` e `CORS_ALLOWED_METHODS=GET,POST,PUT,DELETE,OPTIONS,PATCH`.

```typescript
// Angular — interceptor de autenticação
// withCredentials: true é necessário em todos os endpoints que enviam ou recebem o cookie refreshToken
const authPaths = [
  '/auth/login',
  '/auth/refresh',
  '/auth/logout',
  '/auth/2fa/verify',
  '/auth/oauth2/google',  // recebe o cookie refreshToken na resposta
];

function needsCredentials(url: string): boolean {
  return authPaths.some(p => url.includes(p));
}
```

---

## Fluxo de autenticação resumido

```
── Login com usuário/senha ──────────────────────────────────────────
1. POST /auth/login
   ├─ status=PENDING_2FA  →  POST /auth/2fa/verify  →  TokenPair
   └─ TokenPair (accessToken + cookie refreshToken)

── Login com Google ─────────────────────────────────────────────────
1. Frontend obtém id_token via Google Identity Services
2. POST /auth/oauth2/google { idToken }
   └─ TokenPair (accessToken + cookie refreshToken)
   (cria conta ou vincula à existente automaticamente)

── Em cada request autenticado ──────────────────────────────────────
   Authorization: Bearer <accessToken>

── Ao receber 401 (access token expirado) ───────────────────────────
   POST /auth/refresh  (cookie enviado automaticamente)
   └─ novo TokenPair  →  repetir request original

── Ao receber REFRESH_TOKEN_EXPIRED ou REFRESH_TOKEN_REUSED ─────────
   Redirecionar para /login

── Logout ────────────────────────────────────────────────────────────
   POST /auth/logout  →  invalida token + limpa cookie
```

---

## Monitoramento — `/actuator`

| Endpoint | Acesso | Descrição |
|----------|--------|-----------|
| `GET /actuator/health` | Público | Status geral |
| `GET /actuator/health/liveness` | Público | Liveness probe (ECS/Kubernetes) |
| `GET /actuator/health/readiness` | Público | Readiness probe (inclui DB + Redis) |
| `GET /actuator/info` | Público | Metadados da aplicação |
| `GET /actuator/prometheus` | `ROLE_ADMIN` | Métricas no formato Prometheus (HML + Prod) |

O endpoint `/actuator/prometheus` pode ser usado por Grafana, Datadog, CloudWatch agent ou qualquer coletor Prometheus-compatível.

---

## Configuração do ambiente HML local

Para rodar o HML localmente com Docker Compose e ter Swagger + cookies funcionando:

```env
# .env (raiz do projeto — não versionar)
SPRING_PROFILES_ACTIVE=hml
DB_PASSWORD=postgres
REDIS_PASSWORD=hml_redis_2026
JWT_SECRET=<gere com: openssl rand -base64 32>
CORS_ALLOWED_ORIGINS=http://localhost:4200
CORS_ALLOW_CREDENTIALS=true
COOKIE_SECURE=false          # cookies funcionam em HTTP local
SWAGGER_ENABLED=true         # habilita Swagger UI em HML
TOTP_ENCRYPTION_KEY=<gere com: openssl rand -base64 32>
AVATAR_BASE_URL=http://localhost:8080/avatars
RESEND_API_KEY=<sua-chave-resend>
RESEND_FROM=noreply@seudominio.com
GOOGLE_CLIENT_ID=<seu-client-id>.apps.googleusercontent.com   # obrigatório para login com Google
```

Iniciar a stack:
```bash
docker compose up -d        # sobe PostgreSQL (5435) + Redis (6382)
./mvnw spring-boot:run      # sobe o Spring Boot em HML
```

Swagger UI disponível em: `http://localhost:8080/swagger-ui.html`

---

## Convenções

- Roles **sempre** com prefixo `ROLE_` (ex: `ROLE_ADMIN`, nunca `ADMIN`)
- Códigos de verificação de email: `[A-Z0-9]{12}` exatamente
- Código TOTP: 6 dígitos numéricos
- Backup codes: formato `XXXX-XXXX-XXXX`, X ∈ `[A-Z0-9]`, 8 códigos por usuário
- Timestamps: ISO-8601 UTC
- Paginação começa em `page=0`
- Rate limiting em endpoints de auth: `429` com header `Retry-After: <segundos>`
- Emails são enviados de forma **assíncrona** em HML/Prod — o HTTP response não espera a entrega
- Soft delete: `DELETE /users/{id}` não remove o registro — apenas marca `deleted_at`
