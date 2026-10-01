# Segurança

## Filtros (ordem de execução)

1. **TraceIdFilter** — gera `traceId` aleatório, injeta no MDC e popula `DeviceInfoContext` (IP + User-Agent)
2. **MaintenanceModeFilter** — verifica `security.maintenance.enabled`; retorna 503 para todos os paths exceto `/actuator/health/**` e `/system/config/public`
3. **LoginRateLimitingFilter** — rate limiting por IP em múltiplos endpoints de auth (ver seção abaixo)
4. **JwtAuthenticationFilter** — valida Bearer token e popula `SecurityContext`

---

## Access Token (JWT)

- Algoritmo: HS256
- Chave: property `jwt.secret` (base64 ou UTF-8 fallback, 256 bits)
- TTL padrão: 15 minutos (`jwt.access-ttl-minutes`)
- Claims: `sub` = username, `roles` = lista de authorities
- Header: `Authorization: Bearer {token}`

Validação em `JwtAuthenticationFilter`:
1. Extrai token do header
2. Valida assinatura e expiração (`JwtService.isValid`)
3. Extrai username e `iat` (issued-at)
4. Verifica blocklist: `TokenBlocklistPort.isBlockedAt(username, iat)`
5. Carrega UserDetails (com cache)
6. Seta `SecurityContext`

---

## Refresh Token

- Geração: 512 bits (64 bytes) aleatórios, encoded em Base64 URL-safe
- Armazenamento: hash SHA-256 na tabela `refresh_tokens` — plaintext nunca persiste
- TTL padrão: 7 dias (`jwt.refresh-ttl-days`)

### Rotação

Cada `POST /auth/refresh`:
1. Hash do token recebido
2. Busca no banco pelo hash
3. Se `revoked=true` → **token reutilizado**: revoga todas as sessões + bloqueia todos os JWTs → `401`
4. Se expirado → `400`
5. Marca antigo como `revoked=true, rotatedAt=now`
6. Emite novo refresh token (novo hash salvo)
7. Emite novo access JWT

---

## Token Blocklist

Impede uso de JWTs válidos após logout/troca de senha sem manter lista completa.

Mecanismo: mapeia `username → Instant threshold`
- `blockAllBefore(username, now)` salva o threshold
- `isBlockedAt(username, tokenIat)` retorna `tokenIat <= threshold`

Implementações:
- `@Profile("dev")`: `InMemoryTokenBlocklistAdapter` (ConcurrentHashMap)
- `@Profile("hml|prod")`: `RedisTokenBlocklistAdapter` (TTL = access token TTL)

Entradas expiram automaticamente quando o access TTL passa (não há tokens mais novos que possam ser bloqueados).

---

## Autenticação (Spring Security)

- `DaoAuthenticationProvider` com BCrypt
- `eraseCredentialsAfterAuthentication = false` — necessário para `@Cacheable` com UserDetails (evita apagar o hash antes do cache guardar)
- `ProviderManager` configurado em `SecurityConfig`

### CustomUserDetailsService

- Implementa `UserDetailsService`
- Anotado com `@Cacheable` (cache `userDetails`, TTL 60s padrão — `spring.cache.caffeine.spec` em dev, `spring.cache.redis.time-to-live` em hml/prod)
- Cache evicted em toda mutação de usuário via `UserCachePort.evict(username)`
- Cache também evicted em `RoleService.assignPermission`/`removePermission` (C003) — como a mutação é na *role*, não no usuário, `RoleService` resolve `UserRepository.findUsernamesByRole(roleName)` e evicta o cache de cada usuário com aquela role, garantindo que uma revogação de permissão tenha efeito imediato (não espera o TTL de 60s) em todos os usuários afetados, não só no que disparou a ação

---

## Autorização

Convenção: **sempre `hasAuthority()`**, nunca `hasRole()`.

Authorities carregadas:
- Roles prefixadas com `ROLE_` (ex: `ROLE_ADMIN`, `ROLE_USER`)
- Permissões sem prefixo (ex: `USER_CREATE`, `ROLE_READ`)

Anotações nos controllers: `@PreAuthorize("hasAuthority('PERMISSION_NAME')")`

### Endpoints de notificação — `isAuthenticated()` intencional

Os endpoints `/notifications/**` e `/notifications/preferences/**` usam `isAuthenticated()` em vez de uma permissão granular. Toda conta autenticada pode gerenciar suas próprias notificações — não há RBAC fino aqui por design: o controle de acesso é por **ownership** (cada operação filtra pelo `username` do JWT), não por role/permission.

> Isso é uma decisão consciente: adicionar uma permission `NOTIFICATION_READ` seria redundante porque qualquer usuário ativo já tem o direito de ver suas próprias notificações. A granularidade de permissões é reservada para operações administrativas.

---

### Authority especial: `DEV_ELEVATED`

Adicionada ao JWT apenas após completar o **duplo TOTP DEV** (`POST /auth/dev/complete`). Não é uma permissão persistida no banco — é injetada dinamicamente no access token pelo `AuthService.completeDevElevation()`.

> **Atenção:** `DEV_ELEVATED` nunca deve ser criada manualmente como permissão no banco. É puramente dinâmica — existe apenas como claim no JWT elevado e não aparece no `GET /permissions`.

- Protege `/actuator/**` e outros endpoints sensíveis de infra
- Protege operações de atribuição e remoção das permissões `DEV_ROLE_MANAGE` e `DEV_PERMISSION_MANAGE` via guard interno no `RoleController`: mesmo que o token tenha `ROLE_MANAGE_PERMISSIONS`, assinar ou remover essas permissões de qualquer role exige adicionalmente `DEV_ELEVATED`
- O token DEV-elevado **não tem refresh token** — expira em 1h e não pode ser renovado
- O token normal do usuário com `ROLE_DEV` **não** contém `DEV_ELEVATED` — a elevação é um passo adicional explícito

---

### Permissões — Estoque

Endpoints de `EstoqueController` (`/estoque/**`), protegidos via `@PreAuthorize("hasAuthority('...')")`:

| Método | Endpoint | Permissão |
|--------|----------|-----------|
| `GET` | `/estoque/products` | `ESTOQUE_PRODUCT_READ` |
| `POST` | `/estoque/products` | `ESTOQUE_PRODUCT_MANAGE` |
| `POST` | `/estoque/warehouses` | `ESTOQUE_WAREHOUSE_MANAGE` |
| `GET` | `/estoque/warehouses` | `ESTOQUE_WAREHOUSE_READ` |
| `GET` | `/estoque/stock-balance` | `ESTOQUE_WAREHOUSE_READ` |

Permissões criadas em `V45__estoque_product_permissions.sql` (`ESTOQUE_PRODUCT_READ`, `ESTOQUE_PRODUCT_MANAGE`) e `V47__estoque_warehouse_permissions.sql` (`ESTOQUE_WAREHOUSE_READ`, `ESTOQUE_WAREHOUSE_MANAGE`). Ambas as migrations concedem as permissões apenas a `ROLE_ADMIN`.

---

### Permissões — correção de pagamento e "Marcar" (V136, V137)

| Permissão | Libera | Perfis |
|---|---|---|
| `ORDER_PAYMENT_CORRECT` | `POST /orders/{id}/payments/correction` com o caixa do pedido aberto | ADMIN, ATENDENTE |
| `ORDER_PAYMENT_CORRECT_CLOSED` | A mesma correção com o caixa já fechado. O controller confere a authority para decidir, porque o endpoint aceita as duas | ADMIN |
| `PDV_SALE_ON_ACCOUNT` | Linha `MARCADO` na venda e no fechamento de mesa. Checada no controller (`OnAccountGuard`), antes do service | ADMIN |
| `RECEIVABLE_READ` | Leituras de marcados | ADMIN, ATENDENTE |
| `RECEIVABLE_MANAGE` | Renegociar, cancelar marcado e definir limite de crédito | ADMIN |

Não existe perfil "gerente" no banco; a alçada maior fica com `ROLE_ADMIN`. O histórico e os
indicadores de mesas (PDV-F029) não criaram permissão: aceitam `PDV_READ` **ou** `ORDER_READ`.

---

## Senhas

- Hash: BCrypt via `BcryptPasswordHashAdapter`
- Complexidade exigida (validada em `UserService`):
  - ≥ 8 caracteres
  - ≥ 1 maiúscula
  - ≥ 1 minúscula
  - ≥ 1 dígito
  - ≥ 1 caractere especial

---

## Rate Limiting de Login

Baseado em **IP**, sliding window. O `LoginRateLimitingFilter` cobre:

**POST:**
- `POST /auth/login`
- `POST /auth/register`
- `POST /auth/verify-email`
- `POST /auth/resend-verification`
- `POST /auth/refresh`
- `POST /auth/forgot-password`
- `POST /auth/reset-password`
- `POST /auth/2fa/verify`
- `POST /auth/2fa/confirm`
- `POST /auth/2fa/replace`
- `POST /auth/2fa/backup-codes/regenerate`
- `POST /auth/oauth2/google`
- `POST /auth/dev/first-code`
- `POST /auth/dev/complete`

**DELETE:**
- `DELETE /auth/2fa` — desativação de 2FA exige senha + TOTP code; protegido contra brute-force

**GET:**
- `GET /notifications/stream` — protegido contra flood de abertura/fechamento de conexões SSE

**PUT:**
- `PUT /notifications/preferences/{type}` — protegido contra spam de atualizações

| Property | Padrão |
|----------|--------|
| `rate.limit.login.window-seconds` | 60 |
| `rate.limit.login.max-requests` | 10 |

Implementações:
- `@Profile("dev")`: `InMemoryLoginRateLimiterAdapter`
- `@Profile("hml|prod")`: `RedisLoginRateLimiterAdapter`

---

## Limite de conexões SSE por usuário

O endpoint `GET /notifications/stream` mantém conexões HTTP de longa duração. Para evitar esgotamento de threads por um único usuário:

| Property | Padrão |
|----------|--------|
| `notification.sse.max-connections-per-user` | 5 |

Conexões além do limite são recusadas com erro imediato pelo `SseEmitterRegistry.register()`. O log registra `sse.connection_limit_exceeded` com o username e o limite configurado.

Além disso, `GET /notifications/stream` está coberto pelo `LoginRateLimitingFilter` — limita a cadência de abertura de novas conexões por IP. O controle de conexões ativas simultâneas (cap por usuário) é feito pelo `SseEmitterRegistry` de forma complementar.

---

## Bloqueio de conta (Account Lockout)

Baseado em **username**, rastreia falhas consecutivas.

| Property | Padrão |
|----------|--------|
| `auth.lockout.max-attempts` | 5 |
| `auth.lockout.duration-minutes` | 15 |

`recordSuccess()` zera o contador de falhas.  
`isLocked()` retorna `true` se atingiu max-attempts dentro da janela.

Implementações:
- `@Profile("dev")`: `InMemoryLoginAttemptAdapter`
- `@Profile("hml|prod")`: `RedisLoginAttemptAdapter`

---

## Autenticação Multifator (2FA)

**Método suportado:** TOTP (Time-based One-Time Password, RFC 6238) via `TotpService`.

- Secret encriptado no banco (`totp_config.secret_encrypted`)
- Gerador: LibOTP (compatível com Google Authenticator, Authy, Microsoft Authenticator)
- Validação: janela de ±1 período (30s × 2 = 60s de tolerância para dessincronização de relógio)
- Backup codes: 10 códigos únicos, consumo one-time, armazenados como SHA-256 hashes

**Fora de escopo:** WebAuthn/FIDO2, Passkeys, fingerprint, face recognition. Estas não estão implementadas e não estão no roadmap.

---

## Email Verification

| Property | Padrão |
|----------|--------|
| `email.verification.ttl-minutes` | 15 |
| `email.verification.resend-cooldown-seconds` | 60 |

Segurança:
- Código de 12 chars alfanuméricos (~62 bits de entropia)
- Armazenado como SHA-256 hash — código plaintext só existe no email
- `markAsUsed()` é atômico (CAS): `UPDATE ... WHERE code=? AND used=false` — previne ativação concorrente
- Resend retorna sempre `204` (sem enumeração de email)
- TTL curto + cooldown previne spam

---

## CORS

Configurável via properties:

| Property | Padrão (dev) | Padrão (hml/prod) |
|----------|--------------|-------------------|
| `cors.allowed-origins` | `*` | `CORS_ALLOWED_ORIGINS` env var |
| `cors.allowed-methods` | — | `GET,POST,PUT,DELETE,OPTIONS,PATCH` |
| `cors.allowed-headers` | `*` | `Authorization,Content-Type` |
| `cors.allow-credentials` | `false` | `true` |

`PATCH` está incluído para suportar `PATCH /users/me` e `PATCH /users/{id}`.  
Em hml/prod: restringir `cors.allowed-origins` via variável de ambiente.

---

## Respostas de erro de auth

- `401`: `RestAuthenticationEntryPoint` → JSON com `ApiError`
- `403`: `RestAccessDeniedHandler` → JSON com `ApiError`

Sem redirecionamento para login (API stateless).

---

## HTTP Security Headers

Além dos defaults do Spring Security (X-Content-Type-Options: nosniff, X-Frame-Options: DENY, HSTS em HTTPS), a aplicação configura:

| Header | Valor |
|--------|-------|
| `Referrer-Policy` | `no-referrer` |
| `Content-Security-Policy` | Configurável via `security.content-security-policy` (string vazia = desabilitado em dev/Swagger) |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=(), usb=()` |

---

## Device Tracking (DeviceInfoContext)

`TraceIdFilter` extrai o IP da requisição e o `User-Agent` e os armazena em `DeviceInfoContext`, um ThreadLocal limpo ao final de cada request. O `AuditPersistenceService` e o `RefreshTokenPort.issue()` consomem esses dados para registrar origem da sessão nos logs de auditoria e no `SessionInfo`.

```
DeviceInfo { ipAddress, userAgent }
  ↑
TraceIdFilter.doFilter()  (início do request)
  ↓
DeviceInfoContext.clear()  (fim do request — evita vazamento entre requests no pool)
```

---

## TOTP — Criptografia do Secret

O secret TOTP (gerado como string BASE32) é cifrado com **AES-256-GCM** antes de persistir, via `TotpEncryptionPort` / `AesEncryptionAdapter`. O texto puro nunca é armazenado no banco.

| Property | Descrição |
|----------|-----------|
| `totp.encryption.key` | Chave AES-256 (mínimo 32 chars em prod — validado pelo `ProdStartupValidator`) |
| `totp.pending-setup.ttl-hours` | TTL de setup não confirmado (padrão 24h) |

Fluxo:
1. `POST /auth/2fa/setup` — gera secret em texto puro, cifra, persiste `TotpConfig(enabled=false)`
2. `POST /auth/2fa/confirm { code }` — decifra, valida TOTP, seta `enabled=true`, persiste backup codes (hashes SHA-256)
3. Uso posterior — decifra sempre na memória; plaintext nunca vai ao banco

---

## Reset de Senha

| Property | Padrão |
|----------|--------|
| `password-reset.ttl-minutes` | 30 |

Token gerado com 512 bits (64 bytes) aleatórios, encoded em Base64 URL-safe. Armazenado como SHA-256 hash. Plaintext apenas no email.  
`markAsUsed()` é atômico (CAS): `UPDATE ... WHERE usedAt IS NULL` — previne duplo uso concorrente.  
Ao concluir o reset: todas as sessões são revogadas + todos os JWTs anteriores são bloqueados.

---

## H2 Console (dev)

`H2ConsoleSecurityConfig` libera `/h2-console/**` **totalmente** (`permitAll()`, sem autenticação nem permissão — CSRF desabilitado e `frameOptions.sameOrigin()` para o console carregar em iframe) — não há nenhum controle de acesso no próprio endpoint (C021). A única mitigação é a classe inteira ser `@Profile("dev")`: o bean nunca é registrado em hml/prod, então o endpoint simplesmente não existe fora de dev.

---

## OAuth2 Google Login

O endpoint `POST /auth/oauth2/google` aceita um `id_token` emitido pelo Google Identity Services e valida no backend sem depender de sessão Spring OAuth2.

### Verificação do token

Implementada em `GoogleTokenVerifierAdapter` (port: `GoogleTokenVerifierPort`), usando `NimbusJwtDecoder` configurado em `OAuthConfig`:

| Verificação | Detalhe |
|-------------|---------|
| Assinatura | Chaves públicas do Google — JWKS URI: `https://www.googleapis.com/oauth2/v3/certs` |
| Issuer | `https://accounts.google.com` |
| Audience | `oauth2.google.client-id` (property → variável `GOOGLE_CLIENT_ID`) |
| Expiração | Validada automaticamente pelo `NimbusJwtDecoder` |

Se qualquer validação falhar, `GoogleTokenVerifierAdapter` lança `OAuthTokenInvalidException` → `401 OAUTH_TOKEN_INVALID`.

### Resolução de usuário (`OAuthLoginService`)

Ordem de resolução:
1. `googleId` (`sub` do token) já vinculado em `users.google_id` → login direto
2. Email do Google existe em conta local → `linkGoogle(googleId)` + salva + evict cache
3. Email não existe → `User.fromGoogle(...)` com `ROLE_USER`, `emailVerified=true`, sem senha

Emails são normalizados (`lowercase + strip`) antes de qualquer busca ou persistência.

### Normalização de email

Todos os emails são normalizados para `lowercase + strip` em `UserService` e `OAuthLoginService` antes de salvar ou buscar no banco. A migração `V26__normalize_email_lowercase.sql` normalizou os dados existentes.

---

## Alertas de Segurança por Email

`SecurityAlertEventListener` (`infra/audit/`) escuta eventos de auditoria e envia emails de alerta ao usuário para eventos sensíveis. O envio é assíncrono (via `@Async("emailTaskExecutor")` no `ResendEmailAdapter`) e falha silenciosamente — nunca bloqueia o fluxo principal.

| Evento | Assunto do email |
|--------|-----------------|
| `USER_PASSWORD_CHANGED` | "Alerta de segurança: senha alterada" |
| `ACCOUNT_LOCKED` | "Alerta de segurança: conta bloqueada" |
| `TOTP_ENABLED` | "Alerta de segurança: 2FA ativada" |
| `TOTP_DISABLED` | "Alerta de segurança: 2FA desativada" |
| `TOKEN_THEFT_DETECTED` | "Alerta de segurança: acesso suspeito" |

O listener busca o email do usuário via `UserRepository.findByUsername()`. Se o usuário não tiver email ou a busca falhar, o alerta é descartado com log de erro. Em dev, os alertas são logados pelo `LoggingEmailAdapter`.

---

## Eventos de Auditoria (`AuditEvent.EventType`)

Todos os eventos publicados via `ApplicationEventPublisher` e persistidos pelo `AuditEventListener`:

| Grupo | Eventos |
|-------|---------|
| Auth | `USER_LOGGED_IN`, `USER_LOGGED_OUT`, `USER_SESSIONS_CLEARED`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `TOKEN_THEFT_DETECTED`, `ACCESS_DENIED` |
| Lifecycle | `USER_REGISTERED`, `USER_EMAIL_VERIFIED`, `USER_CREATED`, `USER_DELETED`, `USER_UPDATED`, `USER_EMAIL_CHANGED`, `USER_ROLE_ASSIGNED`, `USER_ROLE_REMOVED`, `USER_ENABLED`, `USER_DISABLED`, `USER_PASSWORD_CHANGED` |
| Password | `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED` |
| Email | `EMAIL_CHANGE_REQUESTED`, `EMAIL_CHANGE_CONFIRMED` |
| RBAC | `ROLE_CREATED`, `ROLE_DELETED`, `PERMISSION_CREATED`, `PERMISSION_DELETED`, `PERMISSION_ASSIGNED_TO_ROLE`, `PERMISSION_REMOVED_FROM_ROLE` |
| 2FA | `TOTP_ENABLED`, `TOTP_DISABLED`, `TOTP_BACKUP_CODES_REGENERATED`, `TOTP_REPLACED` |
| DEV | `DEV_ELEVATION_COMPLETED` |
| OAuth | `OAUTH_GOOGLE_LOGIN` |
| Estoque | `PRODUCT_CREATED`, `WAREHOUSE_CREATED` |
| CRM | `CUSTOMER_CREATED`, `CUSTOMER_NOTE_ADDED`, `CUSTOMER_STAGE_CHANGED`, `TAG_CREATED`, `TAG_DELETED`, `CUSTOMER_TAG_ADDED`, `CUSTOMER_TAG_REMOVED`, `CAMPAIGN_AUTOMATION_CREATED`, `CAMPAIGN_AUTOMATION_DELETED`, `CAMPAIGN_AUTOMATION_DISPATCHED` |
| Pedido (PDV-F030) | `ORDER_PAYMENT_CORRECTED` — antes/depois por método, motivo, `correctionId` e `sessionWasClosed`. O `POST /orders/bulk-status` reaproveita `ORDER_STATUS_CHANGED`, um por pedido movido, com `"bulk": true` |
| Marcados (CRM-F010) | `RECEIVABLE_CREATED` (venda ou fechamento de mesa com linha `MARCADO`), `RECEIVABLE_PAID` (lote, abatimentos, troco e saldo restante), `RECEIVABLE_DUE_DATE_CHANGED`, `RECEIVABLE_CANCELLED`, `CUSTOMER_CREDIT_LIMIT_CHANGED` (antes/depois e se é individual) |

`COMANDA_CANCELLED` passou a levar o `reason` opcional do cancelamento (PDV-F029).

> **`AuditEvent.of` usa `Map.copyOf`**: valor nulo no payload derruba a requisição com 500. Quem monta
> payload com campo opcional remove os nulos antes (ver `OnAccountGuard`, `PdvComandaController.auditPayload`
> e o `HashMap` de `CUSTOMER_CREDIT_LIMIT_CHANGED`).

**`ACCESS_DENIED`:** publicado por `GlobalExceptionHandler.handleAccessDenied` sempre que uma `AccessDeniedException` for lançada. O evento inclui o `username` do contexto de segurança (ou `"anonymous"`) e o `path` da requisição no campo `details`.

---

## Métricas de Observabilidade (Prometheus / Grafana)

### Counters — `SecurityMetricsEventListener`

`SecurityMetricsEventListener` (`infra/metrics/`) escuta `AuditEvent` via `@EventListener` e incrementa counters Micrometer. Os nomes em Prometheus substituem `.` por `_`.

| Métrica Micrometer | Prometheus | Evento que dispara |
|--------------------|------------|-------------------|
| `auth.login.total{result="success"}` | `auth_login_total{result="success"}` | `USER_LOGGED_IN` |
| `auth.login.total{result="failure"}` | `auth_login_total{result="failure"}` | `LOGIN_FAILED` |
| `auth.oauth.login.total{provider="google"}` | `auth_oauth_login_total` | `OAUTH_GOOGLE_LOGIN` |
| `auth.account.locked.total` | `auth_account_locked_total` | `ACCOUNT_LOCKED` |
| `auth.token.theft.total` | `auth_token_theft_total` | `TOKEN_THEFT_DETECTED` |
| `auth.sessions.cleared.total` | `auth_sessions_cleared_total` | `USER_SESSIONS_CLEARED` |
| `auth.password_reset.completed.total` | `auth_password_reset_completed_total` | `PASSWORD_RESET_COMPLETED` |
| `auth.dev_elevation.total` | `auth_dev_elevation_total` | `DEV_ELEVATION_COMPLETED` |
| `auth.rate_limit.blocked.total` | `auth_rate_limit_blocked_total` | Bloqueio pelo `LoginRateLimitingFilter` (não via AuditEvent) |
| `users.registered.total` | `users_registered_total` | `USER_REGISTERED` |
| `users.email_verified.total` | `users_email_verified_total` | `USER_EMAIL_VERIFIED` |
| `users.totp_enabled.total` | `users_totp_enabled_total` | `TOTP_ENABLED` |
| `users.totp_disabled.total` | `users_totp_disabled_total` | `TOTP_DISABLED` |
| `rbac.role_assigned.total` | `rbac_role_assigned_total` | `USER_ROLE_ASSIGNED` |
| `rbac.permission_assigned.total` | `rbac_permission_assigned_total` | `PERMISSION_ASSIGNED_TO_ROLE` |
| `rbac.permission_removed.total` | `rbac_permission_removed_total` | `PERMISSION_REMOVED_FROM_ROLE` |

### Gauge — `ActiveSessionsMetric`

`ActiveSessionsMetric` (`infra/metrics/`) registra um Gauge Micrometer que consulta o banco a cada scrape do Prometheus:

| Métrica | Prometheus | Descrição |
|---------|------------|-----------|
| `auth.active_sessions` | `auth_active_sessions` | Refresh tokens não-expirados e não-revogados — proxy de sessões ativas em tempo real |

A query subjacente é `RefreshTokenJpaRepository.countAllActive(Instant.now())`. O valor reflete o estado do banco no momento do scrape (intervalo padrão: 15s).

### Histograma de Latência HTTP

Configurado em `application-hml.properties` e `application-prod.properties`:

```properties
management.metrics.distribution.percentiles-histogram.http.server.requests=true
```

Expõe buckets `http_server_requests_seconds_bucket` para cálculo de percentis no Grafana:

```promql
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))
```

### Dashboard Grafana (`grafana/provisioning/dashboards/mahal-commerce.json`)

Dashboard provisionado automaticamente com **22 painéis** em 4 rows colapsáveis:

| Row | Painéis |
|-----|---------|
| 🔐 Segurança | 6 stats (registros, emails verificados, contas bloqueadas, token theft, rate limit, dev elevations) + logins time series + OAuth/rate limit time series |
| 👥 Usuários | 4 stats (sessões ativas, sessões encerradas, password resets, TOTP) + funil registro→verificação + eventos TOTP/RBAC |
| ⚡ Performance HTTP | 4 stats (CPU, req/s, p95, 5xx) + latência p50/p95/p99 + erros 4xx/5xx |
| ☕ JVM / Sistema | Heap JVM + Non-Heap + CPU processo/sistema + JVM Threads |

Template variable `$job` filtra por job do Prometheus (default: `mahal-commerce`).

### Alertas Grafana (`grafana/provisioning/alerting/alerts.yml`)

4 regras de alerta provisionadas automaticamente (Grafana Unified Alerting):

| Alerta | Condição | `for` | Severity |
|--------|----------|-------|----------|
| Token Theft Detectado | `increase(auth_token_theft_total[5m]) > 0` | 0s (imediato) | critical |
| Pico de Bloqueios | `increase(auth_account_locked_total[5m]) > 5` | 0s | warning |
| Alta Taxa 5xx | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) > 0.1` | 2m | warning |
| Latência p95 Alta | `histogram_quantile(0.95, ...) > 1` (segundo) | 5m | warning |

O alerting requer `GF_UNIFIED_ALERTING_ENABLED=true` no Grafana (já configurado no `docker-compose.yml`).

---

## Validação na inicialização (Startup Validators)

`ProdStartupValidator` (`@Profile("prod")`) e `HmlStartupValidator` (`@Profile("hml")`) falham no boot se variáveis obrigatórias estiverem ausentes ou com valores de desenvolvimento.

Variáveis validadas em **prod**:

| Variável | Property | Descrição |
|----------|----------|-----------|
| `JWT_SECRET` | `jwt.secret` | Mínimo 44 chars (Base64 de 256 bits) |
| `CORS_ALLOWED_ORIGINS` | `cors.allowed-origins` | Não pode ser `*` |
| `DB_URL` | `spring.datasource.url` | Não pode apontar para localhost/H2 |
| `RESEND_API_KEY` | `resend.api-key` | Chave de produção |
| `RESEND_FROM` | `resend.from` | Domínio de envio real |
| `JWT_ISSUER` | `jwt.issuer` | Não pode ser `security-spring` (valor padrão) |
| `JWT_AUDIENCE` | `jwt.audience` | Não pode ser `api` (valor padrão) |
| `TOTP_ENCRYPTION_KEY` | `totp.encryption.key` | Mínimo 32 chars; rejeita explicitamente o valor default que esteve hardcoded como fallback em `docker-compose.prod.yml` (`${TOTP_ENCRYPTION_KEY:-Vx74...}`, removido em C002 — comprometido por estar no histórico do git) |
| `AVATAR_BASE_URL` | `avatar.base-url` | Não pode apontar para `localhost` nem para `example.com` (C016 — antes só rejeitava `localhost`, deixando `https://example.com` passar despercebido em prod) |
| `GOOGLE_CLIENT_ID` | `oauth2.google.client-id` | Obrigatório para validar tokens Google |
| `DEV_PASSWORD` | `seed.dev.password` | Só validado quando `DEV_EMAIL` (`seed.dev.email`) está definido: rejeita ausente/vazio ou igual ao default do repositório `Dev@secure1!` — sem isso, `DevRoleBootstrapConfig` criaria uma conta `ROLE_DEV` real (acesso a `/actuator/**`, gestão de roles/permissions) com senha pública hardcoded (C005) |

Em **hml** são verificados: `jwt.secret`, `DB_PASSWORD`, `REDIS_PASSWORD`, `resend.api-key`, `resend.from`, `cors.allowed-origins`, `GOOGLE_CLIENT_ID`, `AVATAR_BASE_URL` (C016 — `HmlStartupValidator` não validava esse campo antes; `application-hml.properties` tem `avatar.base-url=${AVATAR_BASE_URL:https://example.com}` como default, então sem essa validação um deploy de hml podia subir "funcional" com URLs de avatar quebradas) e, condicionalmente, `DEV_PASSWORD` (mesma regra do C005 acima).
