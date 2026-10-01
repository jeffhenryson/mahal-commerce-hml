# Domínio: plataforma (correções transversais)

**Status:** 🟢 Ativo — série `C001–C022` concluída; resíduos documentados abaixo
**Escopo:** segurança, infraestrutura, CI/CD, testes, persistência, performance e documentação
**Última atualização deste doc:** 2026-08-18 (PLAT-C036–C046 e PLAT-F001, auditoria `/1-analise ambas`)

## Objetivo

Este não é um domínio de negócio. É o módulo guarda-chuva das **correções transversais** do
backend — as que não pertencem a nenhum domínio (estoque, compras, CRM…) porque atravessam
todos: segredos, RBAC, pipeline, migrations, dimensionamento de pool, documentação.

Existe porque o `docs/backlog.md` central foi descentralizado em 2026-07-26 (cada módulo passou
a ser dono do seu próprio backlog) e a série `C001–C022` precisava de um dono.

## Convenção de IDs

- `C001–C022` — série legada, **congelada**. Toda ela está concluída; o registro vive em
  [Histórico de Implementações](#histórico-de-implementações).
- `PLAT-C023+` — novas correções transversais a partir daqui.

Correções que pertencem claramente a um domínio vão para o README daquele domínio, não para cá
(ex.: `EST-C001` em [`estoque`](../estoque/README.md)).

## Segurança e Infraestrutura

> Esta é a seção **dona** do assunto no backend. O detalhamento mecânico de cada peça está em
> [`docs/security.md`](../../security.md) (filtros, JWT, refresh, TOTP, CORS, headers, métricas)
> e [`docs/infrastructure.md`](../../infrastructure.md) (ambientes, containers, datastores,
> CI/CD, IaC). Os READMEs dos domínios de negócio trazem só o recorte deles e apontam para cá.

### Modelo de autorização

RBAC **dinâmico**: roles e permissões são linhas nas tabelas `roles`, `permissions` e
`role_permissions` — **não existe `enum Role` em Java**. `core/domain/model/rbac/Role` e
`Permission` são records de domínio sem valores fixos.

Convenção obrigatória, documentada em `infra/config/security/SecurityConfig.java:53-57`: usar
sempre `hasAuthority()`, **nunca** `hasRole()`. Roles carregam o prefixo `ROLE_`; permissões não.

| Role | Criada por | Profiles | Recebe |
|---|---|---|---|
| `ROLE_ADMIN` | `infra/config/SeedConfig.java` | **só `dev`** | os 25 nomes de `ADMIN_PERMISSIONS` |
| `ROLE_USER` | `infra/config/SeedConfig.java` | **só `dev`** | apenas `USER_READ` |
| `ROLE_DEV` | `infra/config/DevRoleBootstrapConfig.java` | `dev`, `hml`, `prod` | `ADMIN_PERMISSIONS` + `DEV_ROLE_MANAGE` + `DEV_PERMISSION_MANAGE` |
| `ROLE_CUSTOMER` | `SeedConfig` (`dev`) / `V76` (`hml`, `prod`) | todos | `SHOP_CART_OWN`, `SHOP_ORDER_OWN`, `SHOP_CASHBACK_OWN` — cliente do marketplace, nunca uma permissão de operador |

Em `hml`/`prod` as permissões vêm das migrations de seed (Flyway); em `dev` não há Flyway
(`ddl-auto=create-drop`), então `SeedConfig`/`DevRoleBootstrapConfig` são a **única** fonte —
uma permissão criada só por migration não existe em dev. Foi a causa raiz de `EST-C001`.

`POST /auth/register` não atribui role alguma por default (`auth.registration.default-roles`
vazio): o usuário autorregistrado nasce autenticável e sem nenhuma authority.

### Permissões — catálogo completo

30 permissões persistidas, mais duas authorities que não são linha de tabela.

| Grupo | Permissões | Migration |
|---|---|---|
| Usuários | `USER_CREATE`, `USER_READ`, `USER_UPDATE`, `USER_DELETE`, `USER_ROLE_ASSIGN`, `USER_STATUS` | V4, V7, V8 |
| Roles e permissões | `ROLE_READ`, `ROLE_MANAGE_PERMISSIONS`, `PERMISSION_READ` | V9 |
| Auditoria | `AUDIT_READ` | V19 |
| DEV | `DEV_ROLE_MANAGE`, `DEV_PERMISSION_MANAGE` | V36 |
| Estoque | `ESTOQUE_PRODUCT_READ/MANAGE`, `ESTOQUE_WAREHOUSE_READ/MANAGE`, `ESTOQUE_STOCK_MANAGE` | V45, V47, V56 |
| CRM | `CRM_CUSTOMER_READ`, `CRM_CUSTOMER_MANAGE` | V48 |
| Compras | `COMPRAS_READ`, `COMPRAS_RECEIPT_MANAGE` | V53, V60 |
| PDV | `PDV_READ`, `PDV_SALE_MANAGE` | V53, V57 |
| Stubs | `ECOMMERCE_READ`, `FINANCEIRO_READ`, `LOGISTICA_READ` | V53 |
| Marketplace (`ROLE_CUSTOMER`, não operador) | `SHOP_CART_OWN`, `SHOP_ORDER_OWN`, `SHOP_CASHBACK_OWN` | V76 |
| **Órfãs** | `ROLE_CREATE`, `ROLE_DELETE`, `PERMISSION_CREATE`, `PERMISSION_DELETE` | V9, V36 — ver PLAT-C033 |

Authorities que **não** existem na tabela `permissions`:

- **`DEV_ELEVATED`** — injetada no JWT por `core/service/AuthService.java:137` após o duplo TOTP
  de elevação (`POST /auth/dev/first-code` → `POST /auth/dev/complete`). Protege
  `/system/config`, `/system/info` e, em `dev`, `/actuator/**`. Vive só na sessão elevada.
- **`ROLE_DEV`** — é uma role, mas aparece em `@PreAuthorize("hasAuthority('ROLE_DEV')")` em
  `DevAuthController.java:53`, o que é válido porque roles também são authorities.

### Endpoints do módulo × permissão

| Método | Rota | Permissão | Controller |
|---|---|---|---|
| `POST` | `/users` | `USER_CREATE` | `UserController:61` |
| `GET` | `/users`, `/users/{username}` | `USER_READ` | `UserController` |
| `PUT` | `/users/{id}` | `USER_UPDATE` | `UserController` |
| `DELETE` | `/users/{id}` | `USER_DELETE` | `UserController` |
| `POST`/`DELETE` | `/users/{username}/roles/{roleName}` | `USER_ROLE_ASSIGN` | `UserController:78` |
| `PUT` | `/users/{id}/disable`, `/users/{id}/enable` | `USER_STATUS` | `UserController:216` |
| `GET` | `/roles`, `/roles/{name}` | `ROLE_READ` | `RoleController:61,89` |
| `POST`/`DELETE` | `/roles`, `/roles/{name}` | `DEV_ROLE_MANAGE` | `RoleController:102,120` |
| `POST`/`DELETE` | `/roles/{r}/permissions/{p}` | `ROLE_MANAGE_PERMISSIONS` | `RoleController:136,161` |
| `GET` | `/permissions` | `PERMISSION_READ` | `PermissionController` |
| `POST`/`DELETE` | `/permissions` | `DEV_PERMISSION_MANAGE` | `PermissionController:91` |
| `GET` | `/audit-logs`, `/audit-logs/actions` | `AUDIT_READ` | `AuditLogController:41` |
| `GET`/`PUT` | `/system/config`, `/system/config/{key}` | `DEV_ELEVATED` | `SystemConfigController:34,41` |
| `GET` | `/system/info` | `DEV_ELEVATED` | `SystemInfoController:28` |
| `POST` | `/auth/dev/first-code` | `ROLE_DEV` | `DevAuthController:53` |
| `GET` | `/stats` | `USER_READ` **e** `ROLE_READ` | `StatsController:27` |

**Endpoints protegidos apenas por `anyRequest().authenticated()`** — sem `@PreAuthorize`, por
decisão consciente (o recurso é sempre do próprio usuário autenticado):
`NotificationController`, `NotificationPreferenceController`, `TotpController`,
`AvatarController` (upload/remoção do próprio avatar) e `/users/me/**` no `UserController`.
`AuthController`, `OAuthController` e `RegistrationController` são públicos por natureza.

### Rotas públicas

Definidas em `SecurityConfig.java:74-114`. A **ordem importa**: as regras de `/auth/sessions`,
`/auth/2fa/*` e `/auth/dev/first-code` precisam vir **antes** do `permitAll` genérico de
`/auth/**`, que é mais amplo e as engoliria (há comentário fixando isso no código).

| Rota | Regra |
|---|---|
| `/auth/**` | `permitAll` (catch-all, por último) |
| `POST /shop/register` | `permitAll` (Fatia 8, ECM-F001 — 2026-08-03) |
| `GET /shop/catalog`, `GET /shop/catalog/{sku}` | `permitAll` (Fatia 8, ECM-F002 — 2026-08-03) |
| `/auth/sessions` (GET/DELETE), `/auth/sessions/*` (DELETE) | `authenticated` |
| `/auth/2fa/setup`, `/auth/2fa/confirm`, `/auth/2fa/replace`, `DELETE /auth/2fa`, `GET /auth/2fa/status`, `/auth/2fa/backup-codes/regenerate` | `authenticated` |
| `/auth/2fa/verify`, `/auth/dev/complete` | `permitAll` (o token de desafio é a prova de identidade) |
| `/system/config/public` | `permitAll` |
| `GET /avatars/*` | `permitAll` |
| `/actuator/health/**`, `/actuator/info` | `permitAll` |
| `/actuator/**` | `permitAll` em `hml`/`prod` (porta 8081 isolada por rede); `DEV_ELEVATED` em `dev` |
| `/v3/api-docs/**`, `/swagger-ui/**` | `permitAll` em `dev`/`hml`; **desabilitado em `prod`** (`springdoc.*.enabled=false`), então nenhuma regra é registrada — PLAT-C029 |

### Cadeia de filtros

Ordem em `SecurityConfig.java:118-121`, todos antes do `UsernamePasswordAuthenticationFilter`:

1. `TraceIdFilter` — `X-Trace-Id` no MDC (só aceita o header entrante se casar com o regex
   `SAFE_TRACE_ID`) e popula o `DeviceInfoContext` (IP + User-Agent truncado em 512).
2. `MaintenanceModeFilter` — 503 quando `security.maintenance.enabled`.
3. `LoginRateLimitingFilter` — ver abaixo.
4. `JwtAuthenticationFilter` — só header `Authorization: Bearer`; consulta a blocklist por `iat`
   e revalida `user.isEnabled()` como defesa em profundidade.

Sessão `STATELESS`, `httpBasic` desabilitado, CSRF desabilitado (API com Bearer token; o cookie
de refresh é mitigado por `SameSite=Strict` + `Path=/auth`). 401/403 saem como JSON via
`RestAuthenticationEntryPoint` / `RestAccessDeniedHandler`.

### Rate limiting

Sliding window **por IP**, aplicada só a um conjunto fechado de rotas
(`LoginRateLimitingFilter.shouldNotFilter`, linhas 42-77). Resposta: **429** com `Retry-After`,
corpo `ApiError`, e incremento da métrica `auth.rate_limit.blocked.total`.

| Profile | Janela | Máx. |
|---|---|---|
| `dev` | 60s | 10 |
| `hml` | 60s | 10 |
| `prod` | 60s | **5** |

Rotas cobertas: `POST` em `/auth/login`, `/auth/register`, `/auth/verify-email`,
`/auth/resend-verification`, `/auth/refresh`, `/auth/forgot-password`, `/auth/reset-password`,
`/auth/2fa/verify|confirm|replace`, `/auth/2fa/backup-codes/regenerate`, `/auth/oauth2/google`,
`/auth/dev/first-code`, `/auth/dev/complete`, **`/shop/register`** (Fatia 8, 2026-08-03 — cria
linha sem autenticação, mesmo risco de abuso que `/auth/register`); `DELETE /auth/2fa`;
`PUT /notifications/preferences/**`; `GET /notifications/stream`.

❌ **Quase nenhum endpoint de negócio é limitado** — só `/shop/register` (acima) tem freio.
`/estoque/**`, `/crm/**` (inclusive o export sem paginação), `/compras/**`, `/pdv/**` continuam
sem passar pelo filtro. **`GET /shop/catalog`/`/shop/catalog/{sku}` (ECM-F002, 2026-08-03) também
ficaram de fora** — são públicos e sem sessão, então um IP pode varrer o catálogo inteiro sem
freio; risco menor que `/shop/register` (não cria linha, só lê), mas ainda um scraping/DoS leve
sem custo nenhum hoje. Ver PLAT-C030.

Implementação: `RedisLoginRateLimiterAdapter` em `hml`/`prod` (script Lua atômico com
`ZADD`/`ZREMRANGEBYSCORE`/`ZCARD`, chave `rate:login:{ip}`, member com UUID para não colidir em
timestamps iguais) e `InMemoryLoginRateLimiterAdapter` em `dev`. O IP vem de
`request.getRemoteAddr()`, confiando em `server.forward-headers-strategy=native` atrás do Traefik.

Complementar e independente: **lockout por usuário** (`auth.lockout.max-attempts=5`,
`auth.lockout.duration-minutes=15`), que bloqueia a conta e não o IP.

### Isolamento de dados

O sistema é **single-tenant**. Não existe `tenant_id`, `company_id`, `store_id` nem RLS em
nenhuma das tabelas — nem `@Filter`/`@TenantId` do Hibernate. `warehouse` (V46) é depósito
logístico, não unidade organizacional.

O único isolamento é **por usuário**, e só em auth: `AuthService.revokeSession` delega para
`RefreshTokenRepositoryImpl.revokeByIdForUser`, que casa o id da sessão com o username — a
proteção contra IDOR entre sessões (coberta por teste em C009).

Consequência prática: **qualquer usuário com a permissão de um domínio vê todos os dados daquele
domínio.** Não há como restringir um vendedor a um depósito ou uma carteira de clientes.

### Auditoria

`AuditEvent` (`core/domain/event/AuditEvent.java`) tem ~50 `EventType` cobrindo auth, ciclo de
vida de usuário, reset de senha, RBAC, 2FA, elevação DEV, OAuth, estoque e CRM. Pipeline:

`publisher.publishEvent(AuditEvent.of(...))` → `infra/audit/AuditEventListener` (loga e resolve
o IP **na thread do request**, antes do salto async; respeita `module.audit-logs.enabled`) →
`AuditPersistenceService` (grava `audit_logs` de forma assíncrona). Os mesmos eventos alimentam
`SecurityMetricsEventListener` e `NotificationEventListener`.
`AuthenticationEventsListener` captura ainda os eventos nativos do Spring Security — o
`AuthenticationEventPublisher` é fiado manualmente em `SecurityConfig.java:141` porque o
`ProviderManager` criado por código usa `NullEventPublisher` por padrão.

Tabela `audit_logs` (V18): `username`, `action`, `target`, `details`, `ip_address`, `timestamp`,
com índices em `username`, `action`, `timestamp DESC` e o composto `username + timestamp DESC`
(V20). Retenção `audit.retention-days=365`, limpeza em `AuditLogCleanupService`
(cron `0 45 3 * * *`). Leitura por `GET /audit-logs` (`AUDIT_READ`).

**Cobertura desigual:** os domínios `compras` e `vendas-balcao` não publicam nenhum
`AuditEvent`, e o export CSV de clientes também não — ver `COM-C003`, `PDV-C003`, `EST-C004` e
`CRM-C002` nos READMEs dos respectivos domínios.

### Conformidade (LGPD)

❌ **Não há nada implementado.** Nenhuma menção a consentimento, anonimização, exportação de
dados pessoais ou direito ao esquecimento no código. A exclusão de usuário é **soft delete**
(`deleted_at` + `@SQLRestriction`, V24), o que preserva o dado em vez de removê-lo. Se LGPD
entrar em escopo, isso vira trabalho de domínio próprio, não ajuste pontual.

### Validação e uploads

- Bean Validation em 18 controllers (`@Valid`), com tratamento central em
  `infra/handler/GlobalExceptionHandler` no formato `ApiError` + `traceId`.
- Upload: `spring.servlet.multipart.max-file-size=3MB`; limite de negócio de avatar 2 MB
  (`AvatarProperties`). O formato é validado por **magic bytes** (JPEG/PNG/WebP) em
  `AvatarService`, não por `Content-Type`, e o arquivo é salvo como `UUID + extensão detectada`
   — o nome enviado pelo cliente nunca é usado.
- Senhas: BCrypt. `eraseCredentialsAfterAuthentication=false` é intencional e comentado em
  `SecurityConfig.java:126-130` (o erase zeraria o hash dentro do objeto em cache).

### Infraestrutura transversal

| Recurso | Papel | Onde |
|---|---|---|
| Postgres 16 + Flyway (V1–V61) | dado de todos os domínios; H2 em `dev` | [`docs/infrastructure.md`](../../infrastructure.md#datastores) |
| Redis 7 | rate limit, lockout, blocklist de token e cache (`hml`/`prod`) | adapters em `adapter/out/redis/` |
| Cache Spring | `userDetails`, `users`, `userAuthorities`, TTL 60s | evict ao alterar permissões de role (C003) |
| S3 + CloudFront | avatares em `prod` | `S3AvatarStorageAdapter` |
| SSE | notificações em tempo real | `SseEmitterRegistry` |
| ShedLock | impede os 8 jobs `@Scheduled` de duplicarem entre instâncias | tabela `shedlock` (V11) |
| Prometheus + Grafana | métricas, dashboards e alertas | `prometheus.yml`, `grafana/provisioning/` |
| Validadores de boot | recusam subir `hml`/`prod` com segredo ausente ou placeholder | `infra/config/startup/` |

### Riscos conhecidos

Todos rastreados no [Backlog do Módulo](#backlog-do-módulo): PLAT-C023 (segredos não
rotacionados), PLAT-C024 (compose pai fora do repo), PLAT-C030 (sem rate limit no negócio),
PLAT-C031 (Grafana anônimo), PLAT-C033 (permissões órfãs).

PLAT-C028 (chave TOTP versionada), PLAT-C029 (spec pública em prod) e PLAT-C032 (profile
default `dev`) foram resolvidos na Sprint 3 — ver o Histórico.

## Testes no Postman

Coleção do módulo: [`plataforma.postman_collection.json`](plataforma.postman_collection.json) — importe no Postman, rode a pasta
`00 — Autenticação` (que faz login e guarda o `accessToken`) e siga as pastas na ordem, ou
rode tudo de uma vez no Collection Runner.

```bash
npx newman run docs/dominios/plataforma/plataforma.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

**O que a coleção cobre**

| Pasta | Requisições |
|---|---|
| `01 — Sessões e refresh` | sessões ativas, rotação de refresh token e o 401 de refresh inválido |
| `02 — Usuários` | criação, busca, listagem com filtros e ordenação, atualização, desabilitar/reabilitar (com o 401 do login bloqueado), atribuição e remoção de role, 409 de username duplicado, 400 de senha fora da política, 404 e exclusão |
| `03 — Perfil próprio` | `PATCH /users/me` e troca de senha — **ignoradas por padrão** |
| `04 — Roles` / `05 — Permissões` | listagem, busca, concessão e revogação de permissão em role, 404, e as operações exclusivas de DEV (ignoradas se o usuário não tiver a permissão) |
| `06 — Notificações` | listagem, contador de não lidas, marcar como lida, marcar todas e preferências por tipo |
| `07 — Auditoria` | tipos de evento e listagem filtrada por usuário, ação e intervalo |
| `08 — Sistema` | configuração pública, `/stats` e os endpoints `DEV_ELEVATED` |
| `09 — 2FA` | status (sempre seguro) e o fluxo de setup/confirm/disable — **ignorado por padrão** |
| `10 — Cadastro público` | autoregistro, verificação de e-mail e recuperação de senha — **ignorados por padrão** |
| `11 — Segurança` | 401 sem token, 401 de credencial inválida e o **403** real: login com o usuário `user` e chamada a um endpoint que exige `AUDIT_READ` |

As pastas marcadas como ignoradas são liberadas pelas variáveis `runPerfilProprio`,
`runRevogarSessao`, `run2fa` e `runCadastroPublico` — elas alteram a conta logada ou dependem
de código enviado por e-mail/app autenticador.

`GET /notifications/stream` (SSE) ficou de fora: o Postman não encerra a conexão
`text/event-stream` e trava o Runner. Use `curl -N`.

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).

## Backlog do Módulo

Resíduos explicitamente deixados em aberto pelas notas de implementação de `C001–C022` — nenhum
deles tinha item de backlog próprio até agora.

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| PLAT-C023 | 🔴 Alta | Correção | rotacionar-segredos-expostos-no-historico | Os 5 segredos de C001 (`JWT_SECRET`, `TOTP_ENCRYPTION_KEY`, `RESEND_API_KEY`, `REDIS_PASSWORD`, `GOOGLE_CLIENT_ID`) foram movidos para `.env`, mas **não rotacionados** — continuam expostos no histórico do git. `RESEND_API_KEY` e `GOOGLE_CLIENT_ID` exigem ação nos consoles de terceiros; rotacionar `TOTP_ENCRYPTION_KEY` exige reencriptar os secrets TOTP já armazenados. Decisão de adiar tomada com o usuário em C001/C002. | Pendente |
| PLAT-C024 | 🔴 Alta | Correção | compose-pai-com-segredos-hardcoded | Existe um terceiro `docker-compose.yml` em `~/Documents/myprojects/mahaltabacaria/` (git separado) que é o que **efetivamente cria os containers do ambiente local** (confirmado via labels `com.docker.compose.project.config_files`). Ele ainda tem os segredos hardcoded originais e não recebeu os fixes de C001/C002/C013/C014 — enquanto não for replicado lá, essas correções não têm efeito prático. Achado registrado durante C013, fora do escopo daquele card. | Pendente |
| PLAT-C025 | 🟡 Importante | Correção | dimensionar-pool-hikaricp | C012 ajustou `server.tomcat.threads.max` para 50 (proporção 5:1), mas o pool HikariCP continua em 10 — a fórmula correta depende do número de vCPUs da instância real de Postgres em produção, dado que não existe no código (`docker-compose.prod.yml` não define requests/limits de CPU). Registrado como ação pendente em `docs/persistence.md`. | Pendente |
| PLAT-C026 | 🟡 Importante | Correção | testes-dedicados-nos-repository-impl | C009 cobriu apenas `RefreshTokenRepositoryImpl.revokeByIdForUser`. As outras ~16 classes `*RepositoryImpl` seguem sem teste dedicado — a Nota C009 registra explicitamente que não havia item de backlog para elas. Sobrepõe-se parcialmente a `EST-C007` (repositórios de estoque). | Pendente |
| PLAT-C027 | 🟢 Melhoria | Correção | validar-pipeline-ci-refatorado | A refatoração de C015 (build único + artifact entre `build-test` e `deploy-ecr`) foi validada só por parse YAML e revisão manual — nunca executada de fato no GitHub Actions. A confirmação (pipeline verde, tempo reduzido) depende do próximo push real a `main`. | Pendente |
| PLAT-C028 | 🔴 Alta | Correção | fallback-totp-key-reintroduzido-no-compose-prod | `docker-compose.prod.yml:60` voltou a ter fallback hardcoded: `${TOTP_ENCRYPTION_KEY:-zNEtKjyPPpEkAIBZBZ29nixcQCcAcA19ExgMgaQVRjg=}` (commit `ca2ee50`). É regressão de **C002**, que removeu exatamente esse padrão. Pior: `ProdStartupValidator.java:22` só conhece a chave antiga (`Vx74sQn7…`), então o boot **não** bloqueia esta — produção sobe silenciosamente com uma chave AES versionada no repositório, e é ela que cifra os secrets TOTP. Distinto de PLAT-C023, que trata da rotação dos valores já expostos. | ✅ Concluído (Sprint 3) |
| PLAT-C029 | 🟡 Importante | Correção | swagger-spec-publico-em-prod-contradiz-doc | `SecurityConfig.java:75-80` dá `permitAll()` a `/v3/api-docs/**` e `/swagger-ui/**` em todos os ambientes, com comentário afirmando que é intencional. `application-prod.properties:54` afirma o contrário: "spec (`/v3/api-docs/**`) exige ROLE_DEV". Uma das duas está errada e hoje o spec completo da API é público em produção. Decidir e alinhar código, comentário e `docs/api-reference.md`. | ✅ Concluído (Sprint 3) |
| PLAT-C030 | 🟡 Importante | Correção | sem-rate-limit-em-endpoints-de-negocio | `LoginRateLimitingFilter.shouldNotFilter` cobre `/auth/**`, 2 de notificação e, desde 2026-08-03, `POST /shop/register` (o único ponto em que este card era bloqueante para a Fatia 8 — resolvido). **Revisado em 2026-08-04:** novo `ResourceRateLimitingFilter` (`infra/security/ResourceRateLimitingFilter.java`) fechou os 3 endpoints já flagrados aqui — `GET /crm/customers/export` (bucket `crm-export`, 5 req/hora por usuário), `GET /estoque/movements` (bucket `estoque-movements`, 60 req/min) e `GET /shop/catalog`+`/shop/catalog/{sku}` (bucket `shop-catalog`, 120 req/min por IP, endpoint público). Continua faltando para o restante dos endpoints de negócio (`/pdv/**`, `/compras/**`, `/shop/cart/**`, `/shop/checkout`) — nenhum novo endpoint entrou na lista coberta desde então. | Pendente (parcial) |
| PLAT-C031 | 🟢 Melhoria | Correção | grafana-anonimo-e-embedding-no-compose-hml | `docker-compose.yml` sobe o Grafana com `GF_AUTH_ANONYMOUS_ENABLED: true` (papel Viewer) e `GF_SECURITY_ALLOW_EMBEDDING: true`, publicado em `localhost:3002`. Qualquer um com acesso à rede vê as métricas operacionais sem autenticar. | Pendente |
| PLAT-C032 | 🟢 Melhoria | Correção | profile-default-dev-sem-bloqueio | `application.properties:10` — `spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}`. Um deploy que esqueça a variável sobe em `dev`: H2 em memória, CORS `*`, CSP vazio, H2 console aberto e seed com senha do repositório. Há comentário de aviso, mas nenhum mecanismo impede. | ✅ Concluído (Sprint 3) |
| PLAT-C033 | 🟢 Melhoria | Correção | permissoes-orfas-role-e-permission-crud | `ROLE_CREATE`, `ROLE_DELETE`, `PERMISSION_CREATE` e `PERMISSION_DELETE` são criadas em V9/V36 mas **nenhum `@PreAuthorize` as usa** — o CRUD de role/permissão migrou para `DEV_ROLE_MANAGE`/`DEV_PERMISSION_MANAGE`. Também não constam de `ADMIN_PERMISSIONS` em `SeedConfig`/`DevRoleBootstrapConfig`. Ou são concedidas e não fazem nada, ou poluem a listagem de `GET /permissions`. Decidir entre remover ou voltar a usar. | Pendente |
| PLAT-C034 | 🟡 Importante | Correção | archunit-exigindo-preauthorize-em-controller | `SecurityConfig.java:117` termina em `anyRequest().authenticated()` — qualquer método de controller sem `@PreAuthorize` explícito é alcançável por **qualquer** usuário autenticado. Precisava estar pronto **antes** da Fatia 8 de [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.9, porque é aí que `ROLE_CUSTOMER` passa a existir e "autenticado" deixa de significar "funcionário". Resolvido: `arch/SecurityArchitectureTest.java` (`every_operator_endpoint_declares_preauthorize`) exige `@PreAuthorize` em todo método `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping` de `adapter.in.controller`, exceto a família `/auth/**` (`AuthController`, `OAuthController`, `RegistrationController`, `DevAuthController`, `TotpController`), os endpoints self-service já documentados acima (`NotificationController`, `NotificationPreferenceController`, `AvatarController`, e os três métodos `/users/me/**` de `UserController`) e `SystemConfigController#getPublicConfig` (`GET /system/config/public`, `permitAll()` em `SecurityConfig`) — esta última só apareceu ao rodar o teste pela primeira vez, confirmando o valor do próprio teste. `ShopController` (cadastro `/shop/register` e, desde ECM-F002, catálogo `/shop/catalog`/`/shop/catalog/{sku}`) já está em `PUBLIC_ROUTE_CONTROLLERS` — criado na mesma sessão que fechou este card. **Fatia 9 (2026-08-03) cumpriu a expectativa registrada aqui:** carrinho/checkout ganharam um controller autenticado PRÓPRIO, `ShopAccountController`, com `@PreAuthorize("hasAuthority('SHOP_CART_OWN')")`/`SHOP_ORDER_OWN` de verdade em todo método — **não foi adicionado** a `PUBLIC_ROUTE_CONTROLLERS` nem a nenhuma outra lista de exceção, exatamente como planejado. | ✅ Concluído (2026-08-03) |
| PLAT-C035 | 🟡 Importante | Correção | migrations-nunca-executadas-nos-testes | **Revisado em 2026-08-03: a alegação original ("nenhuma migration Flyway roda na suíte") estava desatualizada.** `AuthFlowPostgresIT` (existe desde 2026-05-25) sobe um Postgres real via Testcontainers com `spring.flyway.enabled=true` e `ddl-auto=none`, e o job `verify` do CI (`.github/workflows/ci.yml:25`) já roda com `ENABLE_TC=true` — ou seja, o SQL de todas as migrations **é** executado contra Postgres real a cada push. O que **de fato** continua sem cobertura é o comportamento sob concorrência da V66: seu **índice parcial único** (`uk_cash_register_session_open_operator`, que o H2 nem suporta) nunca foi exercitado por um teste que dispute a mesma sessão de caixa contra Postgres — `AuthFlowPostgresIT` só cobre login/refresh/logout/criação de usuário, nada de PDV. Correção com escopo reduzido: um IT (pode estender o mesmo padrão de `AuthFlowPostgresIT`) que abra duas sessões de caixa concorrentes para o mesmo operador e confirme que o índice barra a segunda. Levantado em 2026-07-28 durante a Fatia 1. | Pendente (escopo reduzido) |
| PLAT-C036 | 🔴 Alta | Correção | redis-ssl-default-inseguro-em-prod | `docker-compose.prod.yml:82` injeta `REDIS_SSL: ${REDIS_SSL:-false}`, sobrepondo o default seguro de `application-prod.properties:28` (`${REDIS_SSL:true}`). Como o compose sempre define a variável (mesmo vazia), o default seguro da aplicação nunca é alcançado por esse caminho: se `.env` de produção não setar `REDIS_SSL=true` explicitamente, o Redis roda sem TLS silenciosamente. Trocar o default do compose para `${REDIS_SSL:-true}`. Achado em auditoria `analyze-infra` de 2026-08-18. | Pendente |
| PLAT-C037 | 🔴 Alta | Correção | healthcheck-ausente-em-servicos-de-producao | `docker-compose.prod.yml:116-133` (`mahal-commerce-ui`) e `:135-155` (`mahal-commerce-ui-market`) não têm bloco `healthcheck` — o Traefik pode continuar roteando tráfego para um frontend travado. `docker-compose.hml.yml:67-75` (`mailpit-mahal`) também não tem, e `mahal-backend` depende dele com `condition: service_started` (não `service_healthy`). Adicionar `healthcheck` com `interval`/`timeout`/`retries` aos três. Achado em auditoria `analyze-infra` de 2026-08-18. | Pendente |
| PLAT-C038 | 🟡 Importante | Correção | perfil-hml-data-nao-formalizado | O item "hml-data: seeder completo ativo" nunca foi implementado como profile Spring — não há `application-hml-data.properties`, `@Profile("hml-data")` nem `spring.profiles.group/include`. Dados de homologação são inseridos via scripts avulsos (`scripts/rename-and-seed-users.sql`, `scripts/generate_products_seed.py`, `scripts/setup-requested-users.sql`) sem idempotência centralizada. Decidir entre formalizar como profile Spring com seeder próprio ou aceitar o processo manual como definitivo e documentar a decisão. Achado em auditoria `analyze-infra` de 2026-08-18. | Pendente |
| PLAT-C039 | 🟡 Importante | Correção | hashes-de-senha-versionados-em-script-de-seed | `scripts/rename-and-seed-users.sql:15,22,29` contém hashes bcrypt fixos para as contas de homologação `desenvolvedor`/`administrador`/`atendente`, commitados no repositório — mesma classe de risco que motivou o `ProdStartupValidator` a bloquear chaves TOTP comprometidas conhecidas (`ProdStartupValidator.java:26-28`). Gerar hashes únicos por ambiente/deploy ou mover para segredo injetado via `.env`. Achado em auditoria `analyze-infra` de 2026-08-18. | Pendente |
| PLAT-C040 | 🟡 Importante | Correção | roleservice-nao-valida-prefixo-role | `RoleService.createRole` (`core/service/RoleService.java:32`) e `RoleRequest` não validam que `name` comece com `ROLE_`, apesar da convenção documentada em `docs/security.md:82-86` ser obrigatória — `JpaUserAuthoritiesAdapter.loadAuthoritiesByUsername` (`:31-32`) adiciona `role.getName()` como authority sem prefixar, confiando cegamente que toda role no banco já tem `ROLE_`. Exige `DEV_ROLE_MANAGE` para explorar (ator já privilegiado), e o resultado é uma role "fantasma" que nunca autoriza nada (falha silenciosa, não escalação). Adicionar `@Pattern(regexp = "^ROLE_.*")` em `RoleRequest`. Achado em auditoria `analyze-domain`/RBAC de 2026-08-18. | Pendente |
| PLAT-C041 | 🟢 Melhoria | Correção | resourceratelimitingfilter-nao-documentado | `ResourceRateLimitingFilter` está implementado e ativo (`SecurityConfig.java:146`, protege `/crm/customers/export`, `/estoque/movements`, `/shop/catalog*`), mas não é mencionado em `docs/security.md` (que só documenta `LoginRateLimitingFilter`) nem na lista de filtros de `docs/architecture.md`. Junto: a tabela de rate limiting de login também não lista `POST /auth/confirm-email-change` e `POST /shop/register`, que já estão cobertos por `LoginRateLimitingFilter.java:63-80`. Atualizar as duas docs. Achado em auditoria `analyze-domain`/segurança de 2026-08-18. | Pendente |
| PLAT-C042 | 🟡 Importante | Correção | pool-scheduler-sem-folga | `spring.task.scheduling.pool.size` (default 4, `application.properties:45`) e o `SchedulerPoolSizeTest` que o protege ficaram desatualizados desde que `StockReservationExpiryCleanupService` (cron `0 */5 * * * *`) passou a rodar também nos horários já ocupados por outros 3 jobs às 03:30/03:45 — o grupo concorrente real subiu de 3 para 4, exatamente no limite do pool, sem folga. Subir `SCHEDULER_POOL_SIZE` default para 5–6 e atualizar o teste e `docs/persistence.md`. Achado em auditoria `analyze-domain`/performance de 2026-08-18. | Pendente |
| PLAT-C043 | 🟡 Importante | Correção | totpcontroller-sem-security-test | `TotpController` (`/auth/2fa/setup`, `/confirm`, `/disable`, `/backup-codes/regenerate`) é o único módulo de segurança do projeto sem `*ControllerSecurityTest` dedicado cobrindo 401 para usuário não autenticado — todos os outros módulos (`Crm`, `Estoque`, `Pdv`, `Notification` etc.) seguem esse padrão. Criar `TotpControllerSecurityTest`. Achado em auditoria `analyze-domain`/testes de 2026-08-18. | Pendente |
| PLAT-C044 | 🟡 Importante | Correção | docs-persistence-testing-desatualizadas | `docs/persistence.md` documenta migrations só até `V56` (a real vai até `V102`) e `docs/testing.md` lista ~27 services (o código real tem 28, mas módulos inteiros como PDV completo, Pedidos, Cashback, Devoluções, Reservas de estoque, Lotes e Webhooks de pagamento são descritos como stub ou nem mencionados). Faltam ~20 entidades JPA no catálogo (`OrderEntity`, `CartEntity`, `CashbackEntryEntity`, `StockLotEntity`, `StockReservationEntity`, `SupplierEntity`, `GoodsReceiptEntity`, `BugReportEntity` etc.). Risco real: qualquer decisão tomada só a partir dos docs opera com um modelo de dados de meses atrás. Rodar o skill `document` para resincronizar. Achado em duas auditorias independentes (testes/performance e infra) de 2026-08-18 — mesmo achado, consolidado aqui. | Pendente |
| PLAT-C045 | 🟢 Melhoria | Correção | nomenclatura-docker-inconsistente | Sufixo `-mahal` nos `container_name` é inconsistente entre `dev` (`mahal-backend-dev`), `hml` (`mahal-backend`) e `prod` (`app-mahal-prod`); `mahal-commerce-ui`/`mahalcommerce-marketplace` não seguem nenhum padrão de sufixo (nomes divergem de `mahal-admin-ui`/`mahal-market-ui` do checklist). Some a isso: a imagem buildada localmente no CI (`mahal-commerce:*`, `.github/workflows/ci.yml:37`) diverge do nome usado nos composes (`mahal-commerce-backend:*`) — não é bug funcional (o CI faz `docker tag` antes do push ao ECR), mas confunde inspeção manual. Baixo risco, baixa prioridade. Achado em auditoria `analyze-infra` de 2026-08-18. | Pendente |
| PLAT-C046 | 🟢 Melhoria | Correção | expose-dockerfile-e-postgres-db-desatualizados | `Dockerfile:43` declara `EXPOSE 8080`, mas a aplicação escuta `8082` (API) e `8081` (actuator) por padrão (`application.properties:55`) — `EXPOSE` é só documentação, não afeta runtime, mas está incorreto. `docker-compose.prod.yml:26,32,68` usa default `${DB_NAME:-security}`, nome legado do template antigo ("security-spring"), não `mahal`. Achado em auditoria `analyze-infra` de 2026-08-18. | Pendente |
| PLAT-C047 | 🟡 Importante | Correção | sobrecarga-default-em-port-perde-a-transacao | As interfaces de `core/ports/in` oferecem sobrecargas de conveniência como `default`, que delegam para o método completo — `ComandaUseCase.addItem`/`openComanda` e **mais de vinte** em `EstoqueUseCase` (`createProduct`, `updateProduct`, `listProducts`…). Os beans de use case são registrados em `CoreBeanConfig` **pelo tipo da interface**, então o Spring usa proxy JDK. Chamar uma dessas sobrecargas dá: o proxy não acha `@Transactional` no `default` (a anotação vive no método concreto do service, e a interface não pode recebê-la — `HexagonalArchitectureTest` barra framework em `core/ports`), invoca o default no **target**, e ali o `this.metodoCompleto(...)` é **self-invocation**: o `@Transactional` do método real **não é aplicado**. O resultado é uma operação que deveria ser atômica rodando como N transações independentes — uma por chamada de repositório, já que os `*RepositoryImpl` têm `@Transactional` de classe. **Nenhum controller é afetado hoje** (todos chamam a sobrecarga completa — conferido em `PdvComandaController:187`), então o impacto atual é em testes, seeds e qualquer chamador interno futuro. Descoberto de um jeito caro: `ComandaConcurrencyIT` usava o atalho de quatro argumentos e produziu um **lost update reprodutível** que levou a trava pessimista de PDV-C008 a parecer quebrada — o `SELECT FOR UPDATE` commitava e soltava a trava na própria chamada, antes do `save`. Saídas possíveis: mover as sobrecargas para a classe do service (onde `@Transactional` pega e o self-invocation passa a ocorrer *dentro* da transação), ou eliminá-las e obrigar todo chamador à assinatura completa. Levantado ao corrigir PDV-C008 (2026-08-29). | Pendente |
| PLAT-C048 | 🟢 Melhoria | Correção | handler-de-illegalargument-descarta-a-mensagem | `GlobalExceptionHandler:725` mapeia `IllegalArgumentException` para 400 com mensagem **fixa** ("Requisição inválida", código `BAD_REQUEST`), descartando a do domínio. Os records usam `IllegalArgumentException` para **todas** as invariantes — `OrderItem` e `Order` sozinhos passam de vinte —, então toda recusa alcançável por payload lê igual para o cliente: "desconto que zera o item é devolução, não venda" e um campo obrigatório ausente produzem a mesma resposta. Descartar a mensagem é defensável (elas citam valores e nomes de campo interno), o que torna isto uma **decisão de desenho a revisitar**, não um bug: ou as invariantes alcançáveis pela borda ganham exceção tipada uma a uma, ou o handler passa a distinguir uma subclasse de "invariante de domínio" segura de expor. Achado em PDV-C016, que tipou os dois casos do PDV (`ITEM_DISCOUNT_EXCEEDS_GROSS`, `DISCOUNT_EXCEEDS_BILL`); os demais domínios não foram varridos. | Pendente |
| PLAT-C049 | 🟡 Importante | Correção | metodo-http-nao-suportado-responde-500 | `GlobalExceptionHandler` é um `@RestControllerAdvice` puro (não estende `ResponseEntityExceptionHandler`), então `HttpRequestMethodNotSupportedException` caía no catch-all de `Exception` e virava **500 `INTERNAL_ERROR`**, com stacktrace em `log.error`. Isso conta erro de cliente como falha de servidor no monitoramento e manda quem investiga caçar defeito onde não há — foi exatamente o que levou o QA de 06/09/2026 (EST-023) a procurar stacktrace de um endpoint que não existe. Mesma família de `HandlerMethodValidationException` e `MissingServletRequestParameterException`, que ganharam handler pelo mesmo motivo. | ✅ Fechado (2026-09-08) — 405 `METHOD_NOT_ALLOWED` com header `Allow`, mais os vizinhos que caíam no mesmo buraco: 415 e 406. Log em `warn`. |
| PLAT-C050 | 🟡 Importante | Correção | corpo-de-erro-dos-filtros-sem-charset | `{"message":"Muitas tentativas ? aguarde antes de tentar novamente"}` — o travessão virava `?`. Três filtros escrevem JSON direto no `HttpServletResponse` com `setContentType(APPLICATION_JSON_VALUE)`, que é `application/json` seco, e **sem** `setCharacterEncoding`: `LoginRateLimitingFilter`, `ResourceRateLimitingFilter` e `MaintenanceModeFilter`. O `getWriter()` do Tomcat cai então no default do container. O molde certo estava no mesmo pacote — `RestAccessDeniedHandler` e `RestAuthenticationEntryPoint` já faziam o par. EST-009 do QA de 06/09/2026. | ✅ Fechado (2026-09-08) — charset nos três, com teste de regressão afirmando que o travessão sobrevive. De quebra, os corpos de 401/403 passaram a ser `ApiError` como todo o resto da API: eram `{"error","message"}` sem `errorCode`, `path` nem `traceId`. |
| PLAT-C051 | 🟡 Importante | Correção | sse-de-notificacoes-inalcancavel-pelo-navegador | `GET /notifications/stream` respondia 401 para todo cliente de navegador, e a notificação em tempo real simplesmente não existia. O 401 era real mas a causa não era o token: a API `EventSource` **não envia headers**, então a requisição chegava sem autenticação nenhuma e caía no `anyRequest().authenticated()` — não há suporte a cookie nem query param em lugar nenhum do projeto. O efeito colateral extrapolava o endpoint: o cliente reagia ao 401 refazendo a sessão, estourava o rate limit do `/auth/refresh` e **derrubava o usuário no meio de qualquer fluxo** (EST-002/EST-003 do QA de 06/09/2026). | ✅ Fechado (2026-09-08) — bilhete de uso único: `POST /notifications/stream-ticket` emite um valor opaco de 30 s guardado no Redis (`SseTicketPort`, com contraparte em memória para `dev`), e `?ticket=` autentica só naquela rota, via `SseTicketAuthenticationFilter` que roda depois do JWT e só age com o contexto vazio. **Não** `?token=` cru: query string entra em log de acesso, histórico de proxy e `Referer`, e o access token vale 15 min em toda a API. Junto, o stream saiu do rate limit por **IP** do `LoginRateLimitingFilter` — num salão atrás de um NAT, um cliente em laço de reconexão consumia o balde de todos — e passou ao `ResourceRateLimitingFilter`, com chave por usuário. |
| PLAT-C052 | 🟡 Importante | Correção | dirtiescontext-derruba-o-schema-compartilhado | O perfil `dev` dos testes usa um H2 em memória de nome fixo (`jdbc:h2:mem:demo`) com `ddl-auto=create-drop`. As ITs de concorrência marcadas com `@DirtiesContext` (`ComandaConcurrencyIT`, `PdvSaleConcurrencyIT`, `OrderRefundConcurrencyIT` e outras) **fecham** o contexto delas, e o drop derruba o schema que os outros contextos em cache (os de `@TestPropertySource` próprio, como o de `ComandaCashCycleIT`/`ComandaHistoryIT`) ainda usam: elas passam a falhar com `Table "warehouse" not found`. A suíte completa só passa pela ordem em que as classes rodam; subconjuntos com `-Dtest=` quebram. Visto em 2026-10-01 ao rodar `Comanda*,Pdv*,Session*`. Caminho: banco com nome por contexto (como fez `ComandaMergeIT`) ou `ddl-auto=create` sem drop. | Pendente |
| PLAT-F001 | 🔴 Alta | Feature | suporte-multi-loja-e-franquia | Todo domínio hoje é single-tenant: nenhum vínculo usuário↔loja, `Warehouse` já modela múltiplos depósitos mas não múltiplos negócios/CNPJs, RBAC é role global sem escopo por loja. É o requisito que separa "sistema de uma tabacaria" de "plataforma vendável para uma rede" — maior alavanca comercial de longo prazo do produto, e também o item de maior risco arquitetural do backlog (exige planejamento próprio antes de qualquer código: `tenant_id`/`store_id` transversal em `Warehouse`, `CashRegisterSession`, `Customer` e RBAC). Primeira feature registrada na série `PLAT-F` — as demais features cross-domain seguem aqui quando não couberem em um único domínio de negócio. Sugerido em análise de inovação de 2026-08-18. | Pendente |

## Próximos passos

Roteiro completo, com prompt pronto para colar numa sessão nova, em
[`proximos-passos.md`](proximos-passos.md).

- [ ] **PLAT-C035** — escopo reduzido em 2026-08-03: o Flyway completo já roda em CI via `AuthFlowPostgresIT`/`ENABLE_TC=true`. Falta só um teste de concorrência para o índice parcial único da V66 (`uk_cash_register_session_open_operator`) contra Postgres real.
- [x] **PLAT-C034** — concluído em 2026-08-03 (`SecurityArchitectureTest`), antes de começar a
      Fatia 8, como o prazo exigia.
- [ ] **PLAT-C030** — a parte bloqueante da Fatia 8 (`/shop/register`) já foi resolvida em
      2026-08-03. Em 2026-08-04, novo `ResourceRateLimitingFilter` fechou os três endpoints que
      ainda faltavam nesta lista — `/crm/customers/export`, `/estoque/movements` e
      `/shop/catalog`/`/shop/catalog/{sku}`. Resta o restante dos endpoints de negócio
      (`/pdv/**`, `/compras/**`, `/shop/cart/**`, `/shop/checkout`).
- [ ] **PLAT-C024 + PLAT-C023** — como par: rotacionar segredo sem corrigir o compose que
      efetivamente sobe os containers é rotacionar para nada.
- [ ] **PLAT-C031**, **PLAT-C026**, **PLAT-C033** — dívida que não piora com o tempo.
- [ ] **PLAT-C025** e **PLAT-C027** — não se resolvem escrevendo código.

## Histórico de Implementações

### Sprint 1 — 2026-07-20

- **C001** 🔴 `remover-segredos-hardcoded-docker-compose` — os 5 segredos movidos para `.env` (gitignored); `docker-compose.yml` referencia `${VAR}`. **Sem rotação** dos valores (ver PLAT-C023).
- **C002** 🔴 `remover-default-totp-encryption-key-prod` — removido o fallback `${TOTP_ENCRYPTION_KEY:-Vx74sQn7...}` de `docker-compose.prod.yml`; `ProdStartupValidator` reforçado para rejeitar explicitamente esse default conhecido. **Sem rotação** do valor (ver PLAT-C023).
- **C003** 🔴 `evict-cache-userdetails-ao-alterar-permissoes` — cache `userDetails` passou a ser evictado quando `RoleService.assignPermission`/`removePermission` altera as permissões de uma role, eliminando a janela de 60s em que uma authority revogada continuava válida.

### Sprint 2 — 2026-07-20 (C004–C016, 13/13)

- **C004** 🟡 `preauthorize-nos-controllers-stub` — os 5 controllers stub (`Compras`, `Ecommerce`, `Financeiro`, `Logistica`, `Pdv`) ganharam `@PreAuthorize` e testes de segurança, saindo do fallback genérico `anyRequest().authenticated()`.
- **C005** 🟡 `validar-seed-dev-password-em-prod` — `seed.dev.password` passou a ser validado no boot, fechando o caminho de criar um `ROLE_DEV` com senha pública do repositório caso `DEV_EMAIL` fosse definido em produção.
- **C006** 🟡 `alinhar-permissoes-seed-dev` — permissões `ESTOQUE_PRODUCT_*` e `ESTOQUE_WAREHOUSE_*` acrescentadas a `SeedConfig.ADMIN_PERMISSIONS`. Detalhes em [`estoque`](../estoque/README.md).
- **C007** 🟡 `teste-integracao-rbac-ponta-a-ponta` — teste que percorre o pipeline real (criar usuário → atribuir role via API → login → JWT com authorities do `CustomUserDetailsService` → endpoint protegido), em vez de injetar authorities via `SecurityMockMvcRequestPostProcessors`.
- **C008** 🟡 `teste-concorrencia-tokens-cas` — testes de race condition para backup code TOTP, token de reset de senha, TOTP challenge token e dev challenge token (todos usam o mesmo padrão CAS via UPDATE condicional).
- **C009** 🟡 `teste-dedicado-refresh-token-repository` — cobertura direta de `RefreshTokenRepositoryImpl.revokeByIdForUser` (proteção contra revogação cruzada de sessão / IDOR). Escopo limitado a essa classe — ver PLAT-C026.
- **C010** 🟡 `indices-username-verification-e-reset` — migration V54 criou índices em `email_verification_codes.username` e `password_reset_tokens.username`, eliminando full table scan nas operações de emissão/limpeza.
- **C011** 🟡 `documentar-batching-e-rollback-de-migrations` — V26 já aplicada em todos os ambientes e imutável para o Flyway; a correção virou documentação de processo em `docs/persistence.md` (padrão de batching por lotes de id + o fato de que o rollback de V26 é impossível sem backup pré-migration, por ser transformação com perda).
- **C012** 🟡 `dimensionar-tomcat-threads` — `server.tomcat.threads.max` explicitado em hml/prod (`${TOMCAT_MAX_THREADS:50}`, era 200 implícito), levando a proporção 5:1 com o pool Hikari. O pool em si não foi alterado — ver PLAT-C025.
- **C013** 🟡 `documentar-topologia-de-deploy` — confirmado que os frontends rodam via compose próprio conectando à network externa `cerne-commerce_default`, e que `docker-compose.prod.yml` já os unifica. Não havia lacuna funcional: resolvido com comentário no topo do compose e a seção "Topologia de deploy" em `docs/architecture.md`.
- **C014** 🟡 `healthchecks-prometheus-grafana` — healthchecks adicionados a `prometheus-mahal` e `grafana-mahal` (via `wget` do busybox, confirmado presente em ambas as imagens). Validado por `docker compose config`, não contra containers reais.
- **C015** 🟡 `build-docker-unico-no-ci` — `build-test` builda uma vez, salva com `docker save | gzip` e sobe como artifact; `deploy-ecr` faz `docker load` + `tag` + `push`, sem rebuild. Removida também a etapa `Build JAR` de `deploy-ecr`, que era desperdício pré-existente (o `Dockerfile` faz seu próprio `mvn package`). Ver PLAT-C027.
- **C016** 🟡 `validar-avatar-base-url-em-hml` — `avatar.base-url` acrescentado ao `HmlStartupValidator` (rejeita ausente/`localhost`/`example.com`) e `ProdStartupValidator` reforçado para também rejeitar `example.com`.

### Correções posteriores (C017–C022)

- **C017** 🟢 `rebranding-cerne-para-mahal` — renomeação `cerne-commerce` → `mahal-commerce` em 14 arquivos (pom, Dockerfile, properties, `JwtService`, README, CI, composes, prometheus, alertas e dashboard Grafana). **Não alterados** por risco: o groupId/pacote Java `com.cernecommerce` (tocaria centenas de arquivos), os volumes Docker `external: true` (renomear apontaria para volumes inexistentes) e a network/serviço de frontend em `docker-compose.prod.yml`. Build Maven e `docker build` validados de ponta a ponta; suíte com 910 testes, 0 falhas, 1 erro (flake pré-existente).
- **C018** 🟢 `documentar-migrations-seed-sem-on-conflict` — mesma situação de C011: V19/V45/V47 já aplicadas, editar quebraria o checksum do Flyway. Documentado em `docs/persistence.md` com checklist para migrations de seed futuras. A parte específica de estoque (V45/V47) segue rastreada como `EST-C006`.
- **C019** 🟢 `pool-de-threads-do-scheduler` — `spring.task.scheduling.pool.size` configurado; os 8 jobs `@Scheduled` concentrados entre 03:00–04:00 não competem mais por um único thread.
- **C020** 🟢 `renomear-servico-app-no-compose` — serviço `app` alinhado ao padrão `-mahal` dos demais; comentário de porta de scrape do Prometheus corrigido.
- **C021** 🟢 `corrigir-docs-desatualizadas` — `docs/testing.md` (contagem real de arquivos de teste) e `docs/security.md` (H2 console é `permitAll()` total, mitigado por existir só em `@Profile("dev")`).
- **C022** 🟡 `aceitar-backup-code-no-change-password` — `ChangePasswordRequest.totpCode` tinha `@Size(min=6, max=8)` enquanto os backup codes reais têm formato `XXXX-XXXX-XXXX` (14 caracteres): `PUT /users/me/password` rejeitava **todo** backup code com 400 antes de chegar na validação. Corrigido.

### Sprint 3 — 2026-07-27 (segurança de produção)

- **PLAT-C028** 🔴 `fallback-totp-key-reintroduzido-no-compose-prod` — removido o fallback de `docker-compose.prod.yml:60`; a variável agora é obrigatória. O `ProdStartupValidator` passou a manter um **conjunto** de chaves comprometidas (`KNOWN_COMPROMISED_TOTP_KEYS`) em vez de uma só, cobrindo tanto a chave de C002 quanto a reintroduzida em `ca2ee50` — remover do compose não bastava, já que o valor permanece recuperável no histórico do git. No mesmo ponto, o mínimo da chave subiu de 32 para 44 caracteres (32 aceitava Base64 de 192 bits, abaixo do exigido por AES-256) e o placeholder de zeros (`AAAA...`) passou a ser rejeitado, paridade que o `HmlStartupValidator` já tinha.
- **PLAT-C029** 🟡 `swagger-spec-publico-em-prod-contradiz-doc` — resolvido **desabilitando** o springdoc em prod (`springdoc.swagger-ui.enabled=false`, `springdoc.api-docs.enabled=false`), não restringindo por role como o card supunha: com `SessionCreationPolicy.STATELESS` e `httpBasic` desabilitado, o navegador não tem como enviar o Bearer token nem na navegação até a UI nem no `fetch` do spec — gatear por `ROLE_DEV` deixaria a página quebrada para todos. Com as flags em `false`, o bloco `if (swaggerEnabled)` do `SecurityConfig` não registra `permitAll` algum. Documentação segue disponível em dev e hml.
- **PLAT-C032** 🟢 `profile-default-dev-sem-bloqueio` — criado `DevStartupValidator` (`@Profile("dev")`), contraparte do validador de prod. Em vez de tentar provar "isto é produção", ele detecta **variáveis de infra remota que o perfil dev ignoraria em silêncio** — `DB_URL` apontando para banco não-local enquanto a datasource efetiva é outra (tipicamente H2), e `CORS_ALLOWED_ORIGINS` com origem remota. Esse é o cenário perigoso: o operador crê ter configurado o banco real e a aplicação sobe sobre um H2 vazio. Escape hatch `DEV_ALLOW_REMOTE_INFRA=true` para quem aponta o ambiente local a um banco compartilhado de propósito.

> **Nota sobre PLAT-C023 (rotação):** confirmado com o usuário que produção ainda não subiu, portanto não há secret TOTP cifrado no banco. Enquanto isso valer, `JWT_SECRET`, `TOTP_ENCRYPTION_KEY` e `REDIS_PASSWORD` podem ser rotacionados ao custo de gerar novos valores e reiniciar. Depois que existirem usuários com 2FA ativo, trocar `TOTP_ENCRYPTION_KEY` passa a exigir reencriptação. O card segue **Pendente** porque a rotação acontece no `.env` (gitignored, fora deste repositório) e `RESEND_API_KEY`/`GOOGLE_CLIENT_ID` dependem dos consoles de terceiros.

### Sprint 4 — 2026-08-03 (pré-requisito da Fatia 8)

- **PLAT-C034** 🟡 `archunit-exigindo-preauthorize-em-controller` — novo `arch/SecurityArchitectureTest.java`, no molde do `HexagonalArchitectureTest` existente. Varre todo método `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping` de `adapter.in.controller` e exige `@PreAuthorize`, com duas categorias de exceção deliberada (mapeadas 1:1 com a tabela de "Endpoints protegidos apenas por `anyRequest().authenticated()`" acima): a família `/auth/**` (classe inteira) e os self-service por identidade (`NotificationController`, `NotificationPreferenceController`, `AvatarController` inteiros; só os três métodos `/me` de `UserController`). Também revisado nesta sprint: PLAT-C035 tinha uma alegação desatualizada (ver linha da tabela) — corrigida sem mudar código.

Também revisado (não é um card novo, é correção da alegação existente): **PLAT-C035** — a suíte já roda o Flyway completo contra Postgres real via `AuthFlowPostgresIT`/CI (`ENABLE_TC=true`) desde 2026-05-25/07-22, bem antes deste card ter sido aberto em 2026-07-28. Reduzido ao gap real (concorrência da V66 sob Postgres, não coberta).
