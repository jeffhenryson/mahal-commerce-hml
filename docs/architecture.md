# Arquitetura

## Padrão: Hexagonal (Ports & Adapters)

```
┌─────────────────────────────────────────────────────────┐
│  adapter/in           CORE                adapter/out   │
│  ┌──────────┐   ┌──────────────────┐   ┌─────────────┐ │
│  │Controllers│──▶│  ports/in (use  │   │ Persistence │ │
│  │ (HTTP)   │   │  cases / intfs) │   │ JWT         │ │
│  └──────────┘   │                  │   │ Email       │ │
│                 │  service/        │   │ Cache       │ │
│                 │  (impl dos ports)│   │ Redis       │ │
│                 │                  │◀──│ Storage     │ │
│                 │  ports/out       │   │ TOTP        │ │
│                 │  (interfaces p/  │   │             │ │
│                 │   infraestrutura)│   │ (impl dos   │ │
│                 └──────────────────┘   │  ports/out) │ │
│                                        └─────────────┘ │
│  infra/  (config, filtros, handlers, schedulers, audit) │
└─────────────────────────────────────────────────────────┘
```

## Estrutura de pacotes

```
com.cernecommerce
├── CerneCommerceApplication              — entry point
├── core/                                 — lógica de negócio pura (sem framework)
│   ├── domain/
│   │   ├── model/
│   │   │   ├── auth/
│   │   │   │   ├── User                  — entidade principal
│   │   │   │   ├── AuthProvider          — enum LOCAL / GOOGLE
│   │   │   │   ├── LoginResponse         — record (TokenPair | challengeToken)
│   │   │   │   ├── TokenPair             — record accessToken + refreshToken
│   │   │   │   ├── SessionInfo           — record de sessão ativa
│   │   │   │   ├── OAuthLoginResult      — record (TokenPair + username)
│   │   │   │   ├── GoogleUserInfo        — record (googleId, email, name)
│   │   │   │   ├── UpdateProfileResult   — record (User + emailChangePending)
│   │   │   │   ├── EmailVerificationCode — record (code hash, expiresAt, used)
│   │   │   │   ├── PasswordResetToken    — record (tokenHash, expiresAt, usedAt)
│   │   │   │   ├── TotpConfig            — record (secretEncrypted, enabled)
│   │   │   │   ├── TotpChallengeToken    — record (tokenHash, expiresAt)
│   │   │   │   └── TotpBackupCode        — record (codeHash, usedAt)
│   │   │   ├── rbac/
│   │   │   │   ├── Role                  — entidade com permissões
│   │   │   │   └── Permission            — entidade simples
│   │   │   ├── AuditLogEntry             — record de entrada de auditoria
│   │   │   ├── AvatarServeResult         — sealed interface (Redirect | LocalFile | NotFound)
│   │   │   ├── StatsResult               — record de totais do dashboard
│   │   │   └── PageResult<T>             — record de paginação genérico
│   │   ├── event/
│   │   │   └── AuditEvent               — Spring ApplicationEvent com EventType enum
│   │   ├── exception/                    — exceções de domínio (por subpacote)
│   │   │   ├── auth/    AccountDisabledException, AccountLockedException,
│   │   │   │            DevChallengeExpiredException, InvalidPasswordException,
│   │   │   │            InvalidRefreshTokenException, InvalidTotpCodeException,
│   │   │   │            OAuthTokenInvalidException, PasswordResetTokenExpiredException,
│   │   │   │            PasswordResetTokenNotFoundException, RefreshTokenAlreadyUsedException,
│   │   │   │            RefreshTokenExpiredException, SessionNotFoundException,
│   │   │   │            TotpAlreadyEnabledException, TotpChallengeExpiredException,
│   │   │   │            TotpCodeRequiredException, TotpNotConsecutiveException,
│   │   │   │            TotpNotEnabledException, TotpSetupRequiredException
│   │   │   ├── avatar/  AvatarTooLargeException, InvalidAvatarFormatException
│   │   │   ├── email/   EmailAlreadyVerifiedException, EmailDeliveryException,
│   │   │   │            EmailVerificationCodeExpiredException, EmailVerificationCodeNotFoundException
│   │   │   ├── rbac/    RoleNotFoundException
│   │   │   ├── user/    EmailAlreadyExistsException, UsernameAlreadyExistsException, UserNotFoundException
│   │   │   ├── PermissionAlreadyExistsException, PermissionNotFoundException
│   │   │   └── RoleAlreadyExistsException
│   │   └── TokenHashUtils                — utilitário SHA-256
│   ├── ports/
│   │   ├── in/                           — contratos implementados pelos services
│   │   │   ├── AuthUseCase               — login, refresh, logout, sessions
│   │   │   ├── OAuthLoginUseCase         — loginWithGoogle
│   │   │   ├── TotpUseCase               — setup, confirm, disable, backup codes
│   │   │   ├── AvatarUseCase             — upload, delete, serve
│   │   │   ├── UserUseCase               — CRUD, password, email, roles
│   │   │   ├── RoleUseCase               — CRUD, permissions
│   │   │   ├── PermissionUseCase         — CRUD
│   │   │   ├── AuditLogsUseCase          — listagem filtrada
│   │   │   ├── StatsUseCase              — totais do dashboard
│   │   │   ├── SystemConfigUseCase       — feature flags em runtime (GET/PUT /system/config)
│   │   │   └── NotificationPreferenceUseCase — preferências in-app/email por tipo
│   │   └── out/                          — contratos implementados pelos adapters
│   │       ├── user/        UserRepository, UserCachePort, UserAuthoritiesPort
│   │       ├── token/       AccessTokenPort, RefreshTokenPort, TokenBlocklistPort
│   │       ├── credential/  PasswordHashPort, CredentialVerifierPort
│   │       ├── role/        RoleRepository, PermissionRepository
│   │       ├── notification/ EmailPort, EmailVerificationCodeRepository,
│   │       │                 PasswordResetTokenRepository, NotificationRepository,
│   │       │                 NotificationPreferenceRepository, NotificationSsePort
│   │       ├── oauth/       GoogleTokenVerifierPort
│   │       ├── ratelimit/   LoginAttemptPort, LoginRateLimiterPort
│   │       ├── twofa/       TwoFactorAuthPort, TotpConfigRepository,
│   │       │                TotpBackupCodeRepository, TotpChallengeTokenRepository,
│   │       │                TotpEncryptionPort
│   │       ├── storage/     AvatarStoragePort
│   │       └── audit/       AuditLogRepository
│   └── service/                          — implementações dos use cases
│       ├── AuthService
│       ├── OAuthLoginService
│       ├── TotpService
│       ├── AvatarService
│       ├── UserService
│       ├── RoleService
│       ├── PermissionService
│       ├── AuditLogsService
│       ├── StatsService
│       ├── SystemConfigService
│       ├── NotificationService
│       └── NotificationPreferenceService
├── adapter/
│   ├── in/                               — camada HTTP
│   │   ├── controller/
│   │   │   ├── AuthController            — login, refresh, logout, sessions, 2fa/verify,
│   │   │   │                               forgot-password, reset-password, confirm-email-change
│   │   │   ├── RegistrationController    — register, verify-email, resend-verification
│   │   │   ├── TotpController            — /auth/2fa (status, setup, confirm, disable, backup-codes)
│   │   │   ├── OAuthController           — /auth/oauth2/google
│   │   │   ├── DevAuthController         — /auth/dev (duplo TOTP DEV: first-code, complete)
│   │   │   ├── UserController            — /users (CRUD, roles, profile, password)
│   │   │   ├── AvatarController          — /users/me/avatar, /avatars/{filename}
│   │   │   ├── RoleController            — /roles (CRUD [DEV_ROLE_MANAGE], permissions)
│   │   │   ├── PermissionController      — /permissions (CRUD [DEV_PERMISSION_MANAGE])
│   │   │   ├── AuditLogController        — /audit-logs
│   │   │   ├── SystemConfigController    — /system/config (feature flags runtime, requer DEV_ELEVATED)
│   │   │   ├── NotificationController    — /notifications (list, unread-count, mark-read, mark-all-read, delete, stream SSE)
│   │   │   ├── NotificationPreferenceController — /notifications/preferences (GET lista, PUT por tipo)
│   │   │   └── StatsController           — /stats
│   │   ├── sse/
│   │   │   └── SseEmitterRegistry            — gerencia conexões SSE por usuário;
│   │   │                                       implementa `NotificationSsePort`;
│   │   │                                       limite de 5 conexões por usuário (configurável)
│   │   ├── converter/    UserDTOConverter, RoleDTOConverter, PermissionDTOConverter
│   │   └── dtos/
│   │       ├── request/  LoginRequest, RefreshRequest, RegisterRequest, CreateUserRequest,
│   │       │             ChangePasswordRequest, UserUpdateRequest, VerifyEmailRequest,
│   │       │             ForgotPasswordRequest, ResetPasswordRequest,
│   │       │             GoogleLoginRequest, RoleRequest, PermissionRequest,
│   │       │             TotpConfirmRequest, TotpDisableRequest, TotpVerifyRequest,
│   │       │             RegenerateBackupCodesRequest, ResendVerificationRequest, LogoutRequest
│   │       ├── request/  LoginRequest, RefreshRequest, RegisterRequest, CreateUserRequest,
│   │       │             ChangePasswordRequest, UserUpdateRequest, VerifyEmailRequest,
│   │       │             ForgotPasswordRequest, ResetPasswordRequest,
│   │       │             GoogleLoginRequest, RoleRequest, PermissionRequest,
│   │       │             TotpConfirmRequest, TotpDisableRequest, TotpVerifyRequest,
│   │       │             RegenerateBackupCodesRequest, ResendVerificationRequest, LogoutRequest,
│   │       │             UpdateNotificationPreferenceRequest
│   │       └── response/ TokenPairResponseDTO, TwoFactorChallengeResponseDTO,
│   │                     UserResponseDTO, UserProfileDTO,
│   │                     RoleResponseDTO, PermissionResponseDTO,
│   │                     SessionInfoDTO, AuditLogResponseDTO, StatsResponseDTO,
│   │                     TotpSetupResponseDTO, TotpConfirmResponseDTO, TotpStatusResponseDTO,
│   │                     NotificationResponseDTO, NotificationPreferenceResponseDTO
│   └── out/                              — adapters de persistência e serviços externos
│       ├── persistence/
│       │   ├── entity/   UserEntity, RoleEntity, PermissionEntity,
│       │   │             RefreshTokenEntity, EmailVerificationCodeEntity,
│       │   │             PasswordResetTokenEntity, AuditLogEntity,
│       │   │             TotpConfigEntity, TotpBackupCodeEntity, TotpChallengeTokenEntity,
│       │   │             DevChallengeTokenEntity, SystemConfigEntity,
│       │   │             NotificationEntity, NotificationPreferenceEntity
│       │   ├── repository/ *RepositoryImpl (implementam os ports) + *JpaRepository
│       │   └── converter/ UserEntityConverter
│       ├── jwt/          JwtAccessTokenAdapter
│       ├── oauth/        GoogleTokenVerifierAdapter
│       ├── security/
│       │   ├── password/    BcryptPasswordHashAdapter
│       │   ├── credential/  SpringCredentialVerifierAdapter, JpaUserAuthoritiesAdapter
│       │   ├── ratelimit/   InMemoryLoginAttemptAdapter, InMemoryLoginRateLimiterAdapter (@Profile dev)
│       │   ├── blocklist/   InMemoryTokenBlocklistAdapter (@Profile dev)
│       │   └── totp/        AesEncryptionAdapter (implementa TotpEncryptionPort — AES-256)
│       ├── email/        LoggingEmailAdapter (@Profile dev) / ResendEmailAdapter (@Profile hml|prod)
│       │               + ThymeleafEmailRenderer (renderiza templates HTML em `templates/email/`)
│       ├── cache/        UserCacheAdapter
│       ├── redis/        RedisTokenBlocklistAdapter, RedisLoginRateLimiterAdapter,
│       │                 RedisLoginAttemptAdapter (@Profile hml|prod)
│       └── storage/
│           ├── LocalAvatarStorageAdapter (padrão — avatar.storage.type=local)
│           └── S3AvatarStorageAdapter (@ConditionalOnProperty avatar.storage.type=s3)
└── infra/
    ├── config/
    │   ├── CoreBeanConfig                — wiring dos services
    │   ├── ConverterBeanConfig           — wiring dos converters
    │   ├── AsyncConfig                   — dois executores assíncronos: `emailTaskExecutor` (email) e `taskExecutor` (notificações + geral)
    │   ├── AvatarProperties              — @ConfigurationProperties prefix=avatar
    │   ├── OAuthConfig                   — JwtDecoder Google (JWKS + issuer + audience)
    │   ├── S3StorageConfig               — S3Client + S3AvatarStorageAdapter
    │   │                                   (@ConditionalOnProperty avatar.storage.type=s3)
    │   ├── SeedConfig (@Profile dev)     — dados de teste
    │   ├── OpenApiConfig                 — Swagger
    │   ├── ShedLockConfig                — locks distribuídos
    │   └── security/  SecurityConfig, H2ConsoleSecurityConfig
    │   └── startup/   DevModeWarningConfig, HmlStartupValidator, ProdStartupValidator
    ├── security/
    │   ├── jwt/       JwtService, JwtAuthenticationFilter
    │   ├── CustomUserDetailsService      — UserDetailsService com cache
    │   ├── TraceIdFilter                 — 1º filtro: MDC traceId + popula DeviceInfoContext
    │   ├── MaintenanceModeFilter         — 2º filtro: verifica `security.maintenance.enabled`; retorna 503 exceto `/actuator/health/**` e `/system/config/public`
    │   ├── LoginRateLimitingFilter       — 3º filtro: rate limiting por IP em endpoints de auth
    │   ├── DeviceInfoContext             — ThreadLocal com IP e User-Agent da requisição
    │   ├── RestAuthenticationEntryPoint  — 401 JSON
    │   └── RestAccessDeniedHandler       — 403 JSON
    ├── handler/
    │   ├── GlobalExceptionHandler        — mapeamento exceção → HTTP status
    │   └── ApiError (record)             — payload de erro padrão
    ├── scheduler/
    │   ├── RefreshTokenCleanupService    — cron 3:00 AM — tokens expirados e revogados
    │   ├── AuditLogCleanupService        — cron 3:45 AM — entradas mais antigas que retention-days (padrão 365)
    │   ├── EmailVerificationCodeCleanupService — cron 3:30 AM — códigos expirados
    │   ├── PasswordResetTokenCleanupService    — cron 3:15 AM — tokens expirados
    │   ├── TotpChallengeCleanupService         — cron 3:30 AM — challenge tokens expirados
    │   ├── TotpPendingSetupCleanupService      — cron 3:45 AM — setups pendentes > pending-setup.ttl-hours (padrão 24h)
    │   ├── DevChallengeCleanupService          — cron 3:00 AM — dev_challenge_tokens expirados (TTL 90s)
    │   └── NotificationCleanupService          — cron 4:00 AM — notificações lidas mais antigas que retention-days (padrão 90 dias)
    ├── audit/
    │   ├── AuditEventListener            — @EventListener que persiste AuditEvent via AuditPersistenceService
    │   ├── AuditPersistenceService       — salva entrada no banco com IP do DeviceInfoContext
    │   └── AuthenticationEventsListener  — escuta eventos Spring Security (ex: login fail)
    ├── notification/
    │   └── NotificationEventListener     — @EventListener + @Async("taskExecutor"): executa em thread pool separado
    │                                       para não bloquear a thread da requisição; verifica preferências do usuário,
    │                                       persiste notificação in-app e/ou envia email; faz push SSE via NotificationSsePort
    ├── metrics/
    │   ├── SecurityMetricsEventListener  — 15 counters Micrometer via AuditEvent (auth, users, RBAC, TOTP, DEV)
    │   └── ActiveSessionsMetric          — Gauge em tempo real: auth.active_sessions (consulta refresh_tokens via JPA a cada scrape)
    └── cli/
        └── AdminCliRunner               — ApplicationRunner para diagnóstico em dev (substitui Spring Shell)
```

## Convenções por camada

| Pacote | Lombok | Motivo |
|--------|--------|--------|
| `core/domain/model` | Nenhum | Métodos semânticos (changePassword, confirmEmail…) são lógica de negócio |
| `adapter/in/dtos` | `@Data` | POJOs simples para transporte HTTP |
| `adapter/out/persistence/entity` | `@Getter @Setter @NoArgsConstructor` | Requisitos JPA + redução de boilerplate |

## Regras de dependência

- `core/` não importa nada de Spring, JPA, Redis, etc.
- `adapter/` importa `core/` e frameworks específicos.
- `infra/` importa `core/` e `adapter/` para configurar tudo.
- Nenhuma camada de fora acessa `core/service/` diretamente; sempre via interface do port.

### Regras ArchUnit (`HexagonalArchitectureTest`)

| Regra | Escopo | O que previne |
|-------|--------|--------------|
| `core_domain_must_not_depend_on_adapters_or_infra` | `core.domain` | Qualquer import de adapter/infra |
| `core_ports_must_not_depend_on_adapters_or_infra` | `core.ports` | Qualquer import de adapter/infra |
| `core_services_must_not_depend_on_adapters_or_infra` | `core.service` | Qualquer import de adapter/infra |
| `adapter_in_must_not_depend_on_adapter_out` | `adapter.in` | Cross-adapter direto |
| `adapter_out_must_not_depend_on_adapter_in` | `adapter.out` | Cross-adapter inverso |
| `adapter_in_controllers_must_not_depend_on_output_ports` | `adapter.in.controller` | Controller bypassando use case e acessando repo diretamente |
| `core_domain_and_ports_must_not_depend_on_spring` | `core.domain + core.ports` | Spring MVC/Data/Security no core |
| `core_service_may_only_use_spring_transaction` | `core.service` | Spring não-transacional no core |
| `adapter_dtos_must_not_enter_core_service` | `core.service` | DTOs HTTP vazando para use cases |
| `adapter_must_not_access_core_service_directly` | `adapter` | Uso direto de implementação em vez da interface |
| `services_must_only_implement_use_case_ports` | `core.service` | Service implementando interface que não é um port |
| `infra_notification_must_not_depend_on_adapter_in_dtos` | `infra.notification` | Listener de infra importando DTOs de resposta HTTP |
| `adapter_in_controllers_must_not_depend_on_infra_notification` | `adapter.in.controller` | Controller acessando componentes de infra de notificação diretamente |

## Decisões arquiteturais

### Avatar Storage: local vs. S3

O `AvatarStoragePort` abstrai o backend de armazenamento. A implementação é selecionada via property `avatar.storage.type`:

- `local` (padrão dev): arquivos em `avatar.storage-dir`; `/avatars/{filename}` serve os bytes diretamente.
- `s3`: arquivos no S3; `getPublicUrl()` retorna URL pública; `/avatars/{filename}` retorna `308 Redirect` para a URL do S3/CDN.

Trocar de `local` para `s3` não exige alteração no `core/` — apenas configuração.

### Audit via Spring Events

`GlobalExceptionHandler`, controllers e services publicam `AuditEvent` via `ApplicationEventPublisher`. O `AuditEventListener` captura e delega ao `AuditPersistenceService`, que busca o IP/UserAgent do `DeviceInfoContext` ThreadLocal. Essa separação mantém o domínio sem dependências de infraestrutura de auditoria.

### Email assíncrono

`AsyncConfig` configura um executor dedicado para o `EmailPort`. O HTTP response retorna imediatamente sem esperar a entrega de email. Falhas de entrega são logadas mas não propagadas ao caller em hml/prod.

### CORS + PATCH

O CORS inclui `PATCH` nos métodos permitidos desde que `PATCH /users/me` e `PATCH /users/{id}` foram adicionados como endpoints de atualização parcial. Configurável via `CORS_ALLOWED_METHODS`.

### SseEmitterRegistry em adapter/in (não em infra)

O `SseEmitterRegistry` foi colocado em `adapter/in/sse/` em vez de `infra/notification/` por duas razões:

1. **Responsabilidade**: gerenciar conexões `SseEmitter` é uma preocupação do adapter de entrada HTTP — emitters são criados e registrados durante requests HTTP no controller.
2. **Arquitetura**: ao implementar `NotificationSsePort` (core port), o registry pode ser injetado via port em `infra/notification/NotificationEventListener`, sem que a infra conheça `adapter.in`. O controller (na mesma camada `adapter.in`) injeta o bean concreto para chamar `register()`, evitando que a interface do port precisasse referenciar `SseEmitter` (classe Spring), o que violaria a regra de pureza de `core/ports`.

### Notificações assíncronas

`NotificationEventListener` usa `@EventListener + @Async("taskExecutor")`. Isso significa:
- A thread da requisição publica o `AuditEvent` e retorna imediatamente.
- O dispatch (consulta de preferências → persistência → SSE push → email) roda no pool `taskExecutor` definido em `AsyncConfig`.
- Falhas de dispatch são logadas mas nunca propagadas ao caller — o mesmo padrão do `AuditPersistenceService`.

### Cache de preferências de notificação

`NotificationPreferenceRepositoryImpl` anota `findByUsername()` com `@Cacheable("notificationPreferences")` e `upsert()` com `@CacheEvict`. O `NotificationEventListener` consulta as preferências para cada evento de auditoria (`@Async`) — sem cache, cada notificação dispararia um SELECT extra. A eviction acontece no upsert (não por TTL), garantindo consistência imediata após `PUT /notifications/preferences/{type}`.

Configuração do cache segue o mesmo padrão dos outros adapters: Caffeine em dev (`maximumSize=500, expireAfterWrite=60s`), Redis em hml/prod.

### SystemConfig: whitelist de chaves mutáveis via API

`SystemConfigService.set()` aceita apenas chaves presentes no conjunto `PUBLIC_KEYS` (`auth.google.enabled`, `auth.google.register.enabled`, `auth.registration.enabled`, `auth.forgot-password.enabled`). Qualquer outra chave lança `IllegalArgumentException` → 400.

Flags de sistema com alto impacto (`security.maintenance.enabled`, `security.2fa.required`, `module.*`) **não fazem parte dessa whitelist**. Para alterá-las é necessário um `UPDATE` direto no banco ou uma migration Flyway.

**Por quê:** Evitar que uma chamada acidental à API (ou um token DEV comprometido) possa ativar manutenção global ou forçar 2FA para todos os usuários. A mudança de flags de sistema é intencional e requer acesso ao banco.

**Consequência:** Em testes de integração, `security.maintenance.enabled` deve ser configurado via `SystemConfigPort` diretamente (injetado por `@Autowired`), não pelo endpoint HTTP.

### HTTP Security Headers

Além dos defaults do Spring Security (X-Content-Type-Options, X-Frame-Options, HSTS em HTTPS), a aplicação configura:

| Header | Valor |
|--------|-------|
| `Referrer-Policy` | `no-referrer` |
| `Content-Security-Policy` | Configurável via `security.content-security-policy` (vazio = desabilitado em dev) |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=(), usb=()` |

### Topologia de deploy: backend + frontends (C013)

O backend (este repositório) e os dois frontends Angular vivem em **repositórios separados**, sibling entre si no filesystem:

```
mahaltabacaria/
├── mahal-commerce/            (este repo — backend)
├── mahal-commerce-ui/         (admin UI — porta 4201)
└── mahal-commerce-ui-market/  (market UI — porta 4300)
```

`docker-compose.yml` (raiz deste repo, stack de hml/local) define **apenas** backend + infra (Postgres, Redis, Prometheus, Grafana) — nenhum frontend. Isso é intencional, não uma lacuna: cada frontend tem seu próprio `docker-compose.yml` no seu próprio repositório, com uma network externa (`cerne-commerce_default`) apontando para a criada por este compose. Fluxo local:

1. `docker compose up -d` neste repositório (cria a network `cerne-commerce_default`)
2. `docker compose up -d` em `../mahal-commerce-ui` (admin UI, porta 4201)
3. `docker compose up -d` em `../mahal-commerce-ui-market` (market UI, porta 4300)

Em **produção**, os três serviços são unificados em `docker-compose.prod.yml` (raiz deste repo) — os serviços `cerne-commerce-ui` e `mahal-commerce-ui-market` usam `build.context` relativo (`./cerne-commerce-ui`, `./mahal-commerce-ui-market`), então o host de deploy precisa ter os dois repositórios de frontend clonados como diretórios sibling do backend antes de rodar `docker compose -f docker-compose.prod.yml up -d` (tipicamente via script de deploy, não documentado neste repositório).

### "Marcar" (CRM-F010): a quitação não é pedido nem `CashMovement`

A venda marcada **conclui** — a mercadoria saiu — e a parte a prazo vira uma linha `order_payment`
`MARCADO`/`ON_ACCOUNT`, que nenhuma soma de caixa conta. A quitação, depois, entra no caixa de quem
recebe por `receivable_payment` (agrupado em `receivable_payment_batch`, que guarda o troco).

- **Não é `order_payment`:** a quitação não tem pedido próprio, e entra no caixa de quem recebe,
  que pode ser outro operador em outro dia.
- **Não é `CashMovement`:** o movimento de caixa só existe para dinheiro e já entra no esperado do
  fechamento; somado ao `payment-totals`, a quitação seria contada duas vezes. Ela aparece em
  `payment-totals` como `receivableReceived`, por método, e já entra em `netAmount`.
- **Limite de crédito em tabela própria** (`customer_credit_limit`), não em `customers`: o `save` do
  cliente regrava a ficha inteira e zeraria a coluna.

Pendência conhecida: o Financeiro (`/financeiro/cash-flow`) é ledger manual e não lê
`order_payment` nem `customer_receivable`, então os marcados ainda não aparecem como "a receber".

### Correção de pagamento (PDV-F030): lastro em vez de update

A forma errada não é apagada: vira `CORRECTED` e continua no pedido, com `correction_id` apontando
para a correção que a aposentou. Caixa já fechado não tem o esperado reescrito — a divergência por
método vai para `cash_session_adjustment`. O preço é uma tabela a mais e um endpoint de leitura dos
ajustes ainda pendente; o ganho é que o fechamento assinado continua sendo o que foi assinado.

### Campos só do histórico ficam fora do record de domínio (PDV-F029)

`comanda.closed_by` e `cancel_reason` estão em `ComandaEntity` e chegam ao serviço por
`ClosedComanda(comanda, closedBy, cancelReason)`, sem entrar no record `Comanda`, que tem dezenas de
pontos de construção e nenhum uso para eles. São gravados por `ComandaRepository.recordClosing` na
entidade gerenciada — um `UPDATE` em lote deixaria a cópia já carregada na transação desatualizada.
