# Testes

## Visão geral

| Categoria | Quantidade | Tecnologia principal |
|-----------|-----------|----------------------|
| Unit tests | ~59 | JUnit 5 + Mockito |
| Integration tests (`*IT`) | 20 | JUnit 5 + Spring Boot Test + MockMvc |
| Testcontainers (PostgreSQL real) | 2 | Testcontainers + `@EnabledIfEnvironmentVariable` |
| ArchUnit (regras arquiteturais) | 1 | ArchUnit |
| Coleções Postman (API rodando) | 1 por módulo | Postman / newman |

**Não há slice tests (`@DataJpaTest`, `@WebMvcTest`) neste projeto, e não é por descuido.** No
Spring Boot 4 as slices saíram do `spring-boot-test-autoconfigure` — que ficou com 22 classes —
para módulos por tecnologia, e o artefato que traz `@DataJpaTest`/`@AutoConfigureTestDatabase`
não está no classpath. Teste de repositório aqui se escreve como `@SpringBootTest` +
`@Transactional`, como em `EstoqueRepositoryIT` e `RefreshTokenRepositoryImplIT`. Tentar usar a
anotação de slice falha no `testCompile` com `cannot find symbol: class DataJpaTest`.

As coleções Postman são a única categoria que exercita a aplicação **em execução**, de ponta a
ponta pelo HTTP real (incluindo login, RBAC e integração entre domínios). Elas vivem junto da
documentação de cada domínio — ver [Testes de API com Postman](#testes-de-api-com-postman).

---

## Como rodar

```bash
# Todos os testes (unit + ITs com H2)
./mvnw test

# Somente o IT com PostgreSQL real (requer Docker)
ENABLE_TC=true ./mvnw test -Dtest=AuthFlowPostgresIT

# Classe específica
./mvnw test -Dtest=AuthServiceTest

# Coleção Postman de um módulo (com a aplicação no ar)
npx newman run docs/dominios/estoque/estoque.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

> Os ITs (exceto `AuthFlowPostgresIT`) rodam com perfil `dev` — banco H2 in-memory, sem Redis, sem envio de email real.

---

## Testes de API com Postman

Cada módulo tem uma coleção própria, guardada ao lado do README do domínio, que faz login e
exercita os endpoints daquele módulo com asserções de status, corpo e regra de negócio:

| Módulo | Coleção |
|---|---|
| Estoque | [`docs/dominios/estoque/estoque.postman_collection.json`](dominios/estoque/estoque.postman_collection.json) |
| Compras | [`docs/dominios/compras/compras.postman_collection.json`](dominios/compras/compras.postman_collection.json) |
| Vendas Balcão (PDV) | [`docs/dominios/vendas-balcao/vendas-balcao.postman_collection.json`](dominios/vendas-balcao/vendas-balcao.postman_collection.json) |
| CRM | [`docs/dominios/crm/crm.postman_collection.json`](dominios/crm/crm.postman_collection.json) |
| Plataforma | [`docs/dominios/plataforma/plataforma.postman_collection.json`](dominios/plataforma/plataforma.postman_collection.json) |
| E-commerce | [`docs/dominios/ecommerce/ecommerce.postman_collection.json`](dominios/ecommerce/ecommerce.postman_collection.json) |
| Financeiro | [`docs/dominios/financeiro/financeiro.postman_collection.json`](dominios/financeiro/financeiro.postman_collection.json) |
| Logística | [`docs/dominios/logistica/logistica.postman_collection.json`](dominios/logistica/logistica.postman_collection.json) |

Como importar, quais variáveis existem, o environment compartilhado e os pré-requisitos
manuais de Compras e PDV estão em [`docs/postman/README.md`](postman/README.md).

---

## Categorias

### Unit tests

Testam uma classe isolada com dependências mockadas via Mockito. Não sobem o contexto Spring.

| Arquivo | O que cobre |
|---------|-------------|
| `AuthServiceTest` | Login, refresh, logout, rotação de token, detecção de reutilização (token theft); 2 casos para `security.2fa.required=true` (usuário sem TOTP → `TotpSetupRequiredException`; usuário com TOTP → challenge normal) |
| `UserServiceTest` | CRUD, troca de senha, fluxo pendingEmail, soft delete |
| `OAuthLoginServiceTest` | Resolução de usuário Google: novo / vincular conta existente / login direto |
| `TotpServiceTest` | Setup, confirm, disable, backup codes, fluxo challenge |
| `AvatarServiceTest` | Upload, delete, serve (local e S3) |
| `RoleServiceTest` | CRUD de roles, atribuição/remoção de permissions; `assignPermission`/`removePermission` evictam o cache de authorities (`UserCachePort.evict`) de todo usuário retornado por `UserRepository.findUsernamesByRole` (C003), e não evictam nada quando role/permission não existe |
| `PermissionServiceTest` | CRUD de permissions |
| `WarehouseTest` | Factory `create`/`of`, invariantes de domínio: `code`/`name` obrigatórios, `type` obrigatório |
| `StockBalanceTest` | Factory `zero`/`of`, invariantes de domínio: `sku` obrigatório, `warehouseId` obrigatório, `quantity` não pode ser negativa; `apply()`: ENTRADA soma, SAIDA subtrai, SAIDA drena exatamente até zero é permitido, SAIDA insuficiente lança `InsufficientStockException`, AJUSTE soma |
| `StockMovementTest` | Factory `create`/`of`, invariantes de domínio: `sku`/`warehouseId`/`type`/`reason`/`username` obrigatórios, `quantity` deve ser maior que zero |
| `EstoqueServiceTest` | `createProduct` salva e retorna, lança `DuplicateSkuException` em SKU duplicado, permite produto sem variações; `listProducts` delega ao repositório; `createWarehouse` salva e retorna, lança `DuplicateWarehouseCodeException` em código duplicado; `listWarehouses` delega ao repositório; `getStockBalance` retorna saldo existente, retorna saldo zero quando ainda não há registro, lança `WarehouseNotFoundException` para código de depósito inexistente; `adjustStock` ENTRADA sem saldo prévio parte de zero e persiste ledger+saldo, SAIDA decrementa saldo existente, SAIDA com saldo insuficiente lança `InsufficientStockException` sem persistir nada, lança `WarehouseNotFoundException` para depósito inexistente; `listMovements` resolve o depósito por código e delega a paginação ao repositório, devolve página vazia para par SKU/depósito nunca movimentado, lança `WarehouseNotFoundException` sem tocar no `StockMovementRepository` |
| `CustomerTest` | Factory `create`/`of`, invariantes de domínio: `nome`/`contato`/`email` obrigatórios, formato de email inválido rejeitado, `cpf`/`origem` opcionais |
| `CustomerNoteTest` | Factory `create`/`of`, invariantes de domínio: `customerId`/`autor`/`texto` obrigatórios |
| `StageTransitionTest` | Factory `create`/`of`, invariantes de domínio: `customerId`/`de`/`para`/`autor` obrigatórios, `de` não pode ser igual a `para` |
| `TagTest` | Factory `create`/`of`, invariante de domínio: `nome` obrigatório |
| `CampaignAutomationTest` | Factory `create`/`of` (ativa por padrão), `withAtiva` (wither imutável), invariantes de domínio: `nome`/`gatilho`/`segmentoAlvo`/`canal`/`template` obrigatórios |
| `CampaignLogEntryTest` | Factory `create` (status `PENDENTE_INTEGRACAO`, `convertidoEm` nulo por padrão) `/of`, invariantes de domínio: `automationId`/`customerId` obrigatórios |
| `ChannelStatusTest` | Factory `of` constrói status conectado (com provedor) e desconectado (sem provedor); invariante de domínio: `canal` obrigatório |
| `EmailChannelStatusTest` | Factory `of` constrói status com `conectado`/`provedor`/`detalhe` |
| `CustomerCsvConverterTest` | Header row sempre presente (mesmo lista vazia); uma linha por cliente; escapa campos com vírgula entre aspas; escapa aspas internas dobrando-as (RFC 4180); campos opcionais nulos ficam em branco |
| `CrmServiceTest` | `createCustomer` salva e retorna, lança `DuplicateCustomerEmailException` em email duplicado; `findCustomerById` retorna cliente, lança `CustomerNotFoundException` para id inexistente; `listCustomers` delega ao repositório com e sem `search`; `addNote` salva quando cliente existe e lança `CustomerNotFoundException` quando não existe; `listNotes` retorna notas quando cliente existe e lança `CustomerNotFoundException` quando não existe; `moveStage` atualiza cliente e registra transição, lança `CustomerNotFoundException` quando cliente não existe, lança `IllegalArgumentException` ao mover para o mesmo estágio; `listStageHistory` retorna histórico quando cliente existe e lança `CustomerNotFoundException` quando não existe; `getDashboardOverview` agrega contagens reais do repositório (total, ativos, por estágio) com os placeholders de LTV/WhatsApp/segmento; `createTag` salva e retorna, lança `DuplicateTagNameException` em nome duplicado; `listTags` delega ao repositório; `deleteTag` remove quando existe, lança `TagNotFoundException` quando não existe; `addTagToCustomer`/`removeTagFromCustomer` validam cliente e tag antes de (des)associar, lançam `CustomerNotFoundException`/`TagNotFoundException` conforme o caso; `listCustomerTags` retorna tags quando cliente existe e lança `CustomerNotFoundException` quando não existe; `listCustomersForExport` delega ao repositório (sem paginação); `createAutomation` salva e retorna; `listAutomations` delega ao repositório; `setAutomationActive` atualiza a flag quando existe, lança `CampaignAutomationNotFoundException` quando não existe; `deleteAutomation` remove quando existe, lança `CampaignAutomationNotFoundException` quando não existe; `dispatchAutomation` cria uma `CampaignLogEntry` por cliente do `segmentoAlvo` (status `PENDENTE_INTEGRACAO`, `convertidoEm` nulo), lança `CampaignAutomationNotFoundException` quando a automação não existe; `listAutomationLog` retorna o log quando a automação existe e lança `CampaignAutomationNotFoundException` quando não existe; `getChannelStatus` reflete o `EmailChannelStatus` retornado por `EmailPort.channelStatus()` para o canal EMAIL e sempre reporta WHATSAPP desconectado |
| `StatsServiceTest` | Totais do dashboard |
| `AuditLogsServiceTest` | Delegação com filtros, sem filtros, página com entradas, página além do total |
| `SystemConfigServiceTest` | Leitura de feature flags, atualização, chave inexistente |
| `NotificationServiceTest` | `notify()` (retorna Notification salva), `getNotifications()`, `markAsRead()` com verificação de ownership (próprio/outro usuário/não encontrado), `markAllAsRead()`, `countUnread()`, `delete()` com ownership check (próprio/outro/não encontrado) |
| `NotificationPreferenceServiceTest` | `getPreferences()` retorna todos os tipos com defaults quando nada armazenado; preferência armazenada sobrepõe default; tipos sem preferência recebem default; `updatePreference()` persiste campos corretos |
| `JwtServiceTest` | Geração, validação, extração de claims, token expirado, assinatura inválida |
| `RefreshTokenServiceTest` | Hash, rotação, expiração, revogação |
| `JwtAuthenticationFilterTest` | Extração do Bearer, validação, blocklist check |
| `LoginRateLimitingFilterTest` | Sliding window por IP, endpoints cobertos, bypass de rotas não protegidas |
| `CustomUserDetailsServiceTest` | Cache de UserDetails, eviction |
| `GlobalExceptionHandlerTest` | Mapeamento de exceções de domínio para status HTTP |
| `RestHandlersTest` | 401 / 403 JSON responses |
| `PasswordPolicyTest` | Validação de complexidade de senha |
| `TraceIdFilterTest` | Injeção de traceId no MDC, extração de IP e User-Agent |
| `AesEncryptionAdapterTest` | Cifra/decifra AES-256-GCM do secret TOTP |
| `InMemoryTokenBlocklistAdapterTest` | Blocklist in-memory (perfil dev) |
| `InMemoryLoginRateLimiterAdapterTest` | Rate limiter in-memory (perfil dev) |
| `InMemoryLoginAttemptAdapterTest` | Lockout in-memory (perfil dev) |
| `RedisTokenBlocklistAdapterTest` | Blocklist Redis (hml/prod) com TTL |
| `RedisLoginRateLimiterAdapterTest` | Rate limiter Redis |
| `RedisLoginAttemptAdapterTest` | Lockout Redis |
| `RefreshTokenCleanupServiceTest` | Cron de limpeza de tokens expirados/revogados |
| `AuditLogCleanupServiceTest` | Cron de retenção de audit logs |
| `EmailVerificationCodeCleanupServiceTest` | Cron de limpeza de códigos expirados |
| `PasswordResetTokenCleanupServiceTest` | Cron de limpeza de tokens de reset expirados |
| `TotpChallengeCleanupServiceTest` | Cron de limpeza de challenge tokens |
| `TotpPendingSetupCleanupServiceTest` | Cron de limpeza de setups TOTP não confirmados |
| `DevChallengeCleanupServiceTest` | Cron de limpeza de dev_challenge_tokens expirados |
| `SchedulerPoolSizeTest` | Sobe o contexto Spring real e confirma que `ThreadPoolTaskScheduler.getPoolSize()` reflete `spring.task.scheduling.pool.size` (≥4) — sem essa property, o default de 1 thread serializaria os 3 jobs concorrentes às 03:45 (C019) |
| `NotificationCleanupServiceTest` | Cron passa cutoff correto (90 dias por padrão), respeita retention-days configurável, delega ao repositório |
| `ThymeleafEmailRendererTest` | Renderiza cada um dos 5 templates com campos esperados; XSS escaping automático via `th:text` em valores maliciosos |
| `ResendEmailAdapterTest` | `sendVerificationCode` envia POST com from/to/subject/html corretos via `MockRestServiceServer`; `sendPasswordResetLink` verifica template e resetLink; falha HTTP lança `EmailDeliveryException`; `sendPasswordChangedAlert` e `sendTokenTheftAlert` verificam subject e template; `channelStatus` reporta conectado ao provedor RESEND |
| `LoggingEmailAdapterTest` | `channelStatus` reporta desconectado, provedor LOG |
| `MailpitEmailAdapterTest` | `channelStatus` reporta conectado ao provedor MAILPIT |
| `NotificationEventListenerTest` | Dispatch completo (persist + SSE + email) para PASSWORD_CHANGED e ACCOUNT_LOCKED; in-app desabilitado pula persistência e SSE mas envia email; email desabilitado persiste e faz push SSE mas pula email; falha na lookup de preferência faz fallback para defaults (todos habilitados); role_assigned inclui nome do papel no corpo; tipo de evento não mapeado é ignorado |
| `SseEmitterRegistryTest` | Register adiciona emitter; múltiplos emitters até o limite de 5; conexão além do limite é recusada; send para usuário sem emitters é no-op; remove diminui contagem; remove usuário inexistente é no-op; activeConnections retorna zero para usuário sem emitters |
| `GoogleTokenVerifierAdapterTest` | Validação de id_token Google (assinatura, issuer, audience) |
| `SeedConfigTest` | O `CommandLineRunner` de seed dev concede `ESTOQUE_PRODUCT_READ`/`MANAGE` e `ESTOQUE_WAREHOUSE_READ`/`MANAGE` ao `ROLE_ADMIN` — alinhado com `DevRoleBootstrapConfig` (C006), evita 403 inesperado em `/estoque/**` para o usuário `admin` de teste; concede também `PDV_SALE_MANAGE` (EST-C001) |
| `DevRoleBootstrapConfigTest` | O `CommandLineRunner` de bootstrap concede a `ROLE_DEV` as permissões de negócio (`ESTOQUE_*`, `PDV_READ` e `PDV_SALE_MANAGE`) e as `DEV_ONLY_*` (`DEV_ROLE_MANAGE`, `DEV_PERMISSION_MANAGE`); não cria usuário DEV quando `DEV_EMAIL` está em branco. O caso do `PDV_SALE_MANAGE` (EST-C001) é o que quebrou: a permissão existia só no controller e na migration V57, então o DEV tomava 403 em `POST /pdv/sessions/{id}/sales` |
| `HmlStartupValidatorTest` | Boot fail em hml com variáveis ausentes; rejeita `seed.dev.email` (DEV_EMAIL) definido sem `seed.dev.password` (DEV_PASSWORD) real — vazio ou igual ao default `Dev@secure1!` (C005); aceita quando DEV_EMAIL está ausente, mesmo com a senha default; rejeita `avatar.base-url` ausente, `localhost` ou `example.com` (C016 — campo não existia antes) |
| `ProdStartupValidatorTest` | Boot fail em prod com variáveis ausentes ou com valores padrão; rejeita explicitamente o valor default de `TOTP_ENCRYPTION_KEY` que esteve hardcoded em `docker-compose.prod.yml` (C002); mesma validação de `seed.dev.password` do C005; `avatar.base-url` agora também rejeita `example.com`, além de `localhost` (C016) |
| `ActuatorSecurityTest` | Endpoints de actuator acessíveis apenas com auth |

Controladores (MockMvc com contexto parcial):

| Arquivo | O que cobre |
|---------|-------------|
| `AuthControllerTest` | Serialização/deserialização de requests e responses de auth |
| `RegistrationControllerTest` | Register, verify-email, resend-verification |
| `TotpControllerTest` | Endpoints `/auth/2fa/*` |
| `DevAuthControllerTest` | Endpoints `/auth/dev/*` (elevação de privilégio DEV, duplo TOTP) |
| `OAuthControllerTest` | Endpoints `/auth/oauth2/google` — happy path, cookie HttpOnly, OAuth desabilitado |
| `UserControllerTest` | CRUD de usuários, atribuição de roles |
| `RoleControllerTest` | CRUD de roles |
| `PermissionControllerTest` | CRUD de permissions |
| `EstoqueControllerTest` | Lista produtos paginados, cria produto (201), validação de campo obrigatório (400), SKU duplicado (409 `SKU_ALREADY_EXISTS`), criação sem variações; cria depósito (201), código duplicado (409 `WAREHOUSE_CODE_ALREADY_EXISTS`), campo obrigatório ausente (400), `type` inválido (400), lista depósitos, consulta saldo (200), depósito inexistente (404 `WAREHOUSE_NOT_FOUND`); registra movimentação (201 com saldo atualizado), `type` inválido (400), `quantity` ausente/negativa (400), depósito inexistente (404 `WAREHOUSE_NOT_FOUND`), saldo insuficiente (400 `INSUFFICIENT_STOCK`); lista histórico de movimentações (200 com ledger ordenado, página vazia para SKU nunca movimentado, teto de 100 por página, 400 `MISSING_PARAMETER` sem `sku` ou sem `warehouseCode`, 404 `WAREHOUSE_NOT_FOUND`) |
| `CrmControllerTest` | Cria cliente (201, com placeholders `ltv`/`cashback`/`segmento`/`tags`), campo obrigatório ausente (400), email inválido (400), email duplicado (409 `CUSTOMER_EMAIL_ALREADY_EXISTS`); busca por id (200, tags reais do use case), id inexistente (404 `CUSTOMER_NOT_FOUND`); lista paginada (200), filtro `search` repassado ao use case, `size` capado em 100; cria nota (201), sem `texto` (400), cliente inexistente (404); lista notas (200), cliente inexistente (404); histórico de pedidos e extrato de cashback retornam `[]` (200) com cliente existente, 404 com cliente inexistente; move estágio (200), sem `estagio` (400), valor de enum inválido (400), cliente inexistente (404), mesmo estágio (400); lista histórico de transições (200), cliente inexistente (404); dashboard overview retorna totais/ativos/porEstagio reais e placeholders de ltv/whatsapp/segmento (200); cria tag (201), sem `nome` (400), nome duplicado (409 `TAG_ALREADY_EXISTS`); lista tags com contagem (200); remove tag (204), tag inexistente (404 `TAG_NOT_FOUND`); associa/remove tag de cliente (204), sem `tagId` (400), cliente ou tag inexistentes (404); lista tags do cliente (200), cliente inexistente (404); exporta CSV com header `Content-Type: text/csv;charset=UTF-8` e `Content-Disposition: attachment` (200), filtro `search` repassado ao use case; cria automação (201), sem `nome` (400); lista automações (200); ativa/desativa automação (200), automação inexistente (404 `CAMPAIGN_AUTOMATION_NOT_FOUND`); remove automação (204), automação inexistente (404); dispara automação retornando 1 `CampaignLogResponse` por cliente-alvo com `convertidoEm` ausente (200), automação inexistente (404); lista log de disparos (200), automação inexistente (404); status dos canais retorna EMAIL e WHATSAPP com `canal`/`conectado`/`provedor` (200) |
| `AuditLogControllerTest` | Listagem filtrada de audit logs |
| `StatsControllerTest` | Endpoint de stats |
| `AvatarControllerTest` | Upload, delete, serve de avatar |
| `SystemConfigControllerTest` | GET /system/config/public, GET/PUT /system/config |
| `SystemInfoControllerTest` | GET /system/info — DEV_ELEVATED obrigatório |
| `NotificationControllerTest` | Lista paginada (+ `unreadOnly`), unread-count, markAsRead, markAllAsRead, delete, SSE stream (verifica registro no `SseEmitterRegistry`) |
| `ReceivableServiceTest` | CRM-F010 — validação do "Marcar" (sem linha, VIP no limite, duas linhas, vencimento ausente/passado, venda anônima, não-VIP, vencido, acima do limite com os números da tela, limite padrão zero), elegibilidade, quitação (FIFO sem ids, troco só em dinheiro, ids na ordem dada, sessão de outro dia), renegociação, cancelamento, limite nulo volta ao padrão, `markOverdue` pela data da loja |
| `OrderServiceTest` (correção) | PDV-F030 — linhas `CAPTURED` viram `CORRECTED`, dinheiro com troco vira valor exato e zera o troco, soma ≠ `totalPayable` recusada, motivo obrigatório, pedido reembolsado ou pago pelo app recusado, caixa fechado com e sem a permissão de gerente, histórico separando quem aposentou e quem criou cada linha |
| `ComandaServiceTest` (histórico) | PDV-F029 — `recordClosing` no fechamento total (não no parcial), cancelamento com motivo aparado, junção e varredura; totais do histórico (reembolsado fora, cortesia pelo custo, sessões); detalhe com pagamentos por pedido; indicadores por mesa normalizada e por hora de abertura em São Paulo; período invertido ou acima de 366 dias recusado |
| `NotificationPreferenceControllerTest` | GET preferências (lista completa e vazia), PUT com type válido (verifica delegação ao use case) e com type inválido (→ 400 `INVALID_ENUM_VALUE` com lista de valores) |

Adapters com contexto Spring parcial (cache/AOP):

| Arquivo | O que cobre |
|---------|-------------|
| `NotificationPreferenceRepositoryImplTest` | Comportamento real de `@Cacheable` (segunda chamada retorna do cache sem tocar o DB) e `@CacheEvict` (upsert invalida o cache, próxima leitura vai ao DB). Usa `ConcurrentMapCacheManager` em contexto minimal via `@ExtendWith(SpringExtension.class)` + `@EnableCaching`. `@BeforeEach` limpa o cache para evitar poluição entre testes no mesmo contexto. |
| `SystemConfigAdapterTest` | Comportamento real de `@Cacheable` em `findByKey()` e `getBoolean()`; `save()` evicta todo o cache (`allEntries=true`) — próxima leitura vai ao DB; caches são independentes por chave; `getBoolean` retorna `defaultValue` quando chave ausente. Mesmo padrão de contexto minimal que `NotificationPreferenceRepositoryImplTest`. |
| `S3AvatarStorageAdapterTest` | `save()` envia `PutObjectRequest` com bucket, key (`avatars/{filename}`), `contentType` correto (jpg→image/jpeg, png, webp, default→application/octet-stream) e `cacheControl`; retorna filename com extensão. `load()` retorna `Optional` com stream em caso de sucesso, `empty()` em `NoSuchKeyException` e em `S3Exception` genérica. `delete()` suprime `S3Exception` sem propagar. `save()` lança `IllegalStateException` em falha S3. `getPublicUrl()` monta URL com prefixo `avatars/`. |

---

### Integration tests (ITs)

Sobem o contexto Spring completo com MockMvc contra H2 in-memory (perfil `dev`), exceto onde indicado.

| Arquivo | O que cobre |
|---------|-------------|
| `AuthRegistrationFlowIT` | Fluxo completo: registro → verificação de email → login → refresh → logout |
| `AuthFlowSecurityIT` | Segurança de tokens: JWT expirado, refresh revogado, token theft detection (incluindo race condition concorrente), logout invalida sessions |
| `OAuthLoginFlowIT` | Login Google: novo usuário, vincular conta existente, login direto por google_id |
| `PasswordResetFlowIT` | Fluxo completo: forgot-password → reset-password → novas sessions revogadas |
| `AuditEventsIT` | Eventos de auditoria persistidos corretamente para login, logout, registro, RBAC |
| `UserProfileAndSessionsTest` | GET /users/me, PATCH /users/me, GET /users/me/sessions, DELETE session; troca de senha com backup code TOTP real (formato `XXXX-XXXX-XXXX`, 14 chars) retorna 204 — regressão do C022 (`ChangePasswordRequest.totpCode` rejeitava todo backup code real por `@Size(max=8)`) |
| `VerifyEmailConcurrencyIT` | Race condition: duas requisições simultâneas com o mesmo código ativam a conta exatamente uma vez (valida o `markAsUsed()` atômico via CAS) |
| `RoleCacheEvictionIT` | Sobe o `CacheManager` real: popula o cache de `userDetails` via `CustomUserDetailsService.loadUserByUsername`, chama `RoleService.assignPermission`/`removePermission` e confirma que o próximo load já reflete a nova/removida authority sem esperar o TTL (C003) |
| `TotpBackupCodeConcurrencyIT` | Race condition (C008): 5 threads completam `POST /auth/2fa/verify` com o mesmo backup code, cada uma com seu próprio challenge token (evita que o CAS do challenge token serialize a corrida antes dela chegar no backup code) — exatamente 1 sucesso (200), as demais falham (backup code já usado) |
| `PasswordResetConcurrencyIT` | Race condition (C008): 5 threads chamam `POST /auth/reset-password` com o mesmo token — exatamente 1 sucesso (204), as demais 400 (token já usado) |
| `TotpChallengeConcurrencyIT` | Race condition (C008): 5 threads chamam `POST /auth/2fa/verify` com o mesmo challenge token e o mesmo código — exatamente 1 sucesso (200), as demais 401 (challenge token já usado, CAS acontece antes da validação do código) |
| `DevChallengeConcurrencyIT` | Race condition (C008): 5 threads chamam `POST /auth/dev/complete` com o mesmo devToken (semeado diretamente via `DevChallengeRepository` para evitar depender de dois períodos TOTP consecutivos reais) — exatamente 1 sucesso (200), as demais 410 (devToken já usado) |
| `TotpFlowIT` | End-to-end do fluxo TOTP: ativar 2FA → login com challenge → completar com código TOTP real → receber tokens |
| `NotificationFlowIT` | Fluxo completo de notificações: register → verify-email → login → troca de senha (dispara `PASSWORD_CHANGED` async) → GET /notifications → mark-as-read → DELETE. Segundo teste cobre markAllAsRead zerando o unread-count. |
| `SystemConfigFlowIT` | GET /system/config/public (sem auth → 200); GET /system/config sem auth → 401, com DEV_ELEVATED → 200; PUT chave inválida → 400, sem auth → 401; PUT chave pública persiste e aparece no getPublicConfig; toggle maintenance mode via `SystemConfigPort` direto (bypassa whitelist de PUBLIC_KEYS) evicta `@CacheEvict(allEntries=true)` → `MaintenanceModeFilter` retorna 503 em paths não-allowlistados; `/system/config/public` permanece acessível durante manutenção. |
| `RbacEndToEndIT` | RBAC de ponta a ponta pelo pipeline real (C007): `POST /users` (USER_CREATE) → `POST /roles/{role}/permissions/{perm}` (ROLE_MANAGE_PERMISSIONS) → `POST /users/{username}/roles/{role}` (USER_ROLE_ASSIGN) → `POST /auth/login` real (sem authorities injetadas) → JWT emitido usado via header `Authorization` em `GET /pdv/sessions` (`PDV_READ`) retorna 200; usuário sem a role/permissão retorna 403 com o mesmo JWT real |
| `RefreshTokenRepositoryImplIT` | Primeiro teste dedicado a uma classe `*RepositoryImpl` do projeto (C009), contra o banco real: `revokeByIdForUser` — usuário A tentando revogar sessão de B lança `SessionNotFoundException` e a sessão de B continua ativa (prova o isolamento IDOR de `findActiveByIdAndUsername`); dono revogando a própria sessão tem sucesso e ela some de `findActiveSessions`; id inexistente lança `SessionNotFoundException` |
| `EstoqueRepositoryIT` | Os 5 `*RepositoryImpl` de estoque contra banco real (EST-C007): round-trip de produto com variações/atributos, `existsBySku` em SKU pai e de variação, paginação ID-first, propagação do `version`, ordem do ledger e upsert do ponto de reposição. Usa `flush()` + `clear()` explícitos para não ler do cache de primeiro nível |
| `ReceivableCycleIT` | CRM-F010 de ponta a ponta: venda com parte marcada e quitação em dinheiro no caixa de **outro** operador (entra em `receivableReceived` e no esperado), recusa de não-VIP e de quem passa do limite antes da venda, reembolso do pedido cancelando o marcado em aberto |
| `ComandaHistoryIT` | PDV-F029: mesa fechada em duas vezes (parcial com taxa + total) e mesa cancelada com motivo; `closedBy`/`cancelReason` gravados, filtros do histórico (status, mesa sem caixa, quem fechou, período), detalhe com os pagamentos de cada pedido, indicadores contando só a FECHADA |
| `StockBalanceConcurrencyIT` | Optimistic locking do saldo de estoque (EST-C007): 8 threads dando `SAIDA` no mesmo par SKU/depósito — o saldo final tem que bater exatamente com as baixas confirmadas (sem lost update) e os perdedores da corrida têm que falhar como conflito tratado, não 500. Segundo teste cobre a corrida na *primeira* movimentação do par, onde não existe `version` ainda e quem protege é a unique constraint |

---

### Testcontainers (PostgreSQL real)

Duas classes sobem um container PostgreSQL real via Testcontainers: `AuthFlowPostgresIT` (gated por `@EnabledIfEnvironmentVariable`) e `RefreshTokenServiceTest` (`@Testcontainers` + `@Container` com `postgres:16-alpine`).

`AuthFlowPostgresIT` valida que:
- As migrations Flyway executam sem erro contra PostgreSQL (não apenas H2)
- O fluxo de login/refresh/logout funciona com o banco de produção

**Por que está desabilitado por padrão:** sobe um container Docker e é mais lento. Precisa do daemon Docker disponível.

```bash
# Ativar explicitamente
ENABLE_TC=true ./mvnw test -Dtest=AuthFlowPostgresIT
```

A anotação `@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")` garante que não seja executado em pipelines de CI padrão.

Depois disso vieram os `*PostgresIT` de domínio, no mesmo molde (container próprio, Flyway ligado,
`ddl-auto=none`, `spring.sql.init.mode=never`). Existem porque **o H2 do perfil `dev` monta o schema
pelas entidades**: CHECKs, índices e seeds das migrations só são exercitados aqui. Entre eles:

| Arquivo | O que prova contra o DDL real |
|---|---|
| `PagamentoPostgresIT` | V136 e V137: correção grava `CORRECTED` + linhas novas que passam nos CHECKs; `CORRECTED` sem referência é recusado; permissões novas semeadas nos perfis certos; venda marcada, quitação e job de vencidos; `MARCADO` sem `due_date` recusado |
| `ComandaHistoryPostgresIT` | V138: `closed_by`/`cancel_reason` gravados, recorte por data com um lado só (o caso em que `Instant` nulo em JPQL viraria `bytea`), ordem `closed_at desc`, paginação, indicadores lendo pedido e item pelo `comanda_id`, índice criado |

```bash
# Todos os Postgres ITs (nesta máquina o Docker exige -Dapi.version=1.44)
ENABLE_TC=true ./mvnw test -Dapi.version=1.44 -Dtest='*PostgresIT'
```

---

### Segurança específica

Testes que validam comportamento de autorização independentemente do fluxo de negócio:

| Arquivo | O que cobre |
|---------|-------------|
| `PermissionControllerSecurityTest` | 401 sem auth, 403 com role insuficiente, 200/201 com permission correta |
| `EstoqueControllerSecurityTest` | 401 sem auth, 403 com `ROLE_USER` sem permissão, 403 sem `ESTOQUE_PRODUCT_MANAGE` no POST, 200/201 com `ESTOQUE_PRODUCT_READ`/`ESTOQUE_PRODUCT_MANAGE`; mesmos casos para `ESTOQUE_WAREHOUSE_READ`/`ESTOQUE_WAREHOUSE_MANAGE` nos endpoints de depósito e saldo; 401/403 em `POST /estoque/movements`, 201 com `ESTOQUE_STOCK_MANAGE` (cria um depósito no próprio teste e registra uma ENTRADA, que nunca falha por saldo insuficiente); 401/403 em `GET /estoque/movements`, incluindo 403 para quem tem apenas `ESTOQUE_WAREHOUSE_READ` — ler o ledger expõe o `username` de cada movimentação e exige `ESTOQUE_STOCK_MANAGE` |
| `CrmControllerSecurityTest` | 401 sem auth em POST/GET/PATCH (item, listagem, notas, estágio, dashboard, tags, exportação CSV, automações, status dos canais), 403 sem `CRM_CUSTOMER_MANAGE` no POST/PATCH/DELETE de cliente/nota/estágio/tags/automações, 403 sem `CRM_CUSTOMER_READ` no GET (item, listagem, notas, pedidos, cashback, histórico de estágio, dashboard, tags, exportação CSV, automações, log de disparos, status dos canais), 201 com `CRM_CUSTOMER_MANAGE`, 200/404 com `CRM_CUSTOMER_READ`/`CRM_CUSTOMER_MANAGE` |
| `RoleControllerSecurityTest` | 401 sem auth, 403 sem permissão, guard DEV_ELEVATED em assign/removePermission para `DEV_ROLE_MANAGE`/`DEV_PERMISSION_MANAGE` |
| `AuditLogControllerSecurityTest` | 401 sem auth, 403 sem `AUDIT_READ`, 200 com permissão correta |
| `StatsControllerSecurityTest` | 401 sem auth, 403 com apenas uma das permissões exigidas (`USER_READ` + `ROLE_READ`), 200 com ambas |
| `UserControllerSecurityTest` | Proteção dos endpoints de usuário por permission |
| `ExpiredJwtTest` | JWT expirado retorna 401 |
| `InvalidSignatureJwtTest` | JWT com assinatura inválida retorna 401 |
| `AuthRateLimitingTest` | Rate limit por IP bloqueia após N tentativas |
| `MaintenanceModeFilterTest` | Modo manutenção retorna 503; `/actuator/health/**` e `/system/config/public` são passados; filtro respeita `security.maintenance.enabled=false` |
| `NotificationControllerSecurityTest` | GET /notifications, GET /notifications/unread-count, PATCH /{id}/read, PATCH /read-all, DELETE /{id}, GET /stream — todos retornam 401 sem autenticação |
| `NotificationPreferenceControllerSecurityTest` | GET /notifications/preferences e PUT /notifications/preferences/{type} retornam 401 sem autenticação |
| `ComprasControllerSecurityTest` | GET /compras/suppliers — 401 sem auth, 403 sem `COMPRAS_READ`, 200 com `COMPRAS_READ` (C004) |
| `EcommerceControllerSecurityTest` | GET /ecommerce/carts — 401 sem auth, 403 sem `ECOMMERCE_READ`, 200 com `ECOMMERCE_READ` (C004) |
| `FinanceiroControllerSecurityTest` | GET /financeiro/cash-flow — 401 sem auth, 403 sem `FINANCEIRO_READ`, 200 com `FINANCEIRO_READ` (C004) |
| `LogisticaControllerSecurityTest` | GET /logistica/shipments — 401 sem auth, 403 sem `LOGISTICA_READ`, 200 com `LOGISTICA_READ` (C004) |
| `PdvControllerSecurityTest` | GET /pdv/sessions — 401 sem auth, 403 sem `PDV_READ`, 200 com `PDV_READ` (C004) |
| `ReceivableControllerSecurityTest` | CRM-F010 — 401/403 em `/receivables`, 200 com `RECEIVABLE_READ` (lista e resumo), 404 `RECEIVABLE_NOT_FOUND`, limite e cancelamento exigem `RECEIVABLE_MANAGE`, limite negativo 400, quitação exige `PDV_SALE_MANAGE`, elegibilidade de cliente inexistente 404 |
| `PdvComandaControllerSecurityTest` (histórico) | PDV-F029 — `history` e `analytics` com `ORDER_READ` → 200, sem permissão → 403, `analytics` sem período ou invertido → 400, `history?status=ABERTA` e `size` acima do teto → 400, detalhe com `ORDER_READ` → 404 para comanda inexistente |
| `OrdersControllerSecurityTest` (correção) | PDV-F030 — `POST /orders/{id}/payments/correction`: 403 só com `ORDER_FULFILL`, 404 com `ORDER_PAYMENT_CORRECT` para pedido inexistente |

---

### ArchUnit

`HexagonalArchitectureTest` verifica em tempo de teste que as regras de dependência da arquitetura hexagonal não foram violadas:

- `core/domain` e `core/ports` — proibido qualquer `org.springframework.*`
- `core/service` — permite apenas `org.springframework.transaction.*` (exceção consciente: `@Transactional` no use-case boundary); todo o resto do Spring é barrado
- `adapter/in.controller` não acessa `adapter/out` (e vice-versa)
- `adapter/in.controller` não acessa `core/ports/out` diretamente (rule scoped a controllers — `adapter/in/sse/` pode implementar ports de saída)
- `adapter/` não acessa `core/service/` — sempre via interfaces dos ports
- Services implementam apenas interfaces de `core/ports/in` ou `core/ports/out`
- Classes de `adapter/in/dtos` não podem ser referenciadas em `core/service` (isolamento de DTO)
- `infra/notification` não importa `adapter/in/dtos` — listeners não usam DTOs de resposta HTTP
- `adapter/in/controller` não importa `infra/notification` — controllers usam `NotificationSsePort` (port), não o registry concreto

Se um desenvolvedor importar Spring MVC, Spring Data ou Spring Security no core, este teste falha imediatamente no `./mvnw test`.

---

## Helpers de teste

Disponíveis apenas com `@Profile("dev")` — não são incluídos no build de produção.

### `EmailVerificationTestHelper`

Recupera o código de verificação em texto puro do `LoggingEmailAdapter`. Necessário porque o banco armazena apenas o SHA-256, não o código original.

```java
@Autowired EmailVerificationTestHelper verificationHelper;

String code = verificationHelper.getCodeForUsername("testuser");
// usar `code` na chamada ao endpoint POST /auth/verify-email
```

### `RefreshTokenTestHelper`

Manipula refresh tokens via JDBC para simular cenários de expiração sem esperar o TTL real.

```java
@Autowired RefreshTokenTestHelper refreshTokenTestHelper;

refreshTokenTestHelper.expireTokenByHash(tokenHash);
// próximo POST /auth/refresh com esse hash retorna 400 REFRESH_TOKEN_EXPIRED
```

### `SeedCredentials`

Constantes de credenciais do seed de desenvolvimento (`SeedConfig`). Centralizadas aqui para evitar literais espalhados nos testes.

### `TestHashUtils`

Utilitário de teste para computar SHA-256 de tokens e comparar com o que foi persistido, sem depender de `TokenHashUtils` de produção diretamente nos asserts dos testes.

---

## Convenções

- Nomes de métodos em snake_case descrevendo comportamento: `login_com_senha_incorreta_retorna_401`
- ITs com `@DirtiesContext` quando o estado do H2 pode contaminar outros testes (ex: `VerifyEmailConcurrencyIT`)
- ⚠️ O `@DirtiesContext` fecha o contexto, e o `create-drop` derruba o schema do H2 `demo` **compartilhado** — os
  outros contextos em cache ficam sem tabelas (PLAT-C052). IT com `@TestPropertySource` próprio deve usar um
  banco próprio, como `ComandaMergeIT` (`jdbc:h2:mem:comanda-prod-flags`)
- ITs de mesa novas rodam com as flags de **produção** (`pdv.mesa.catalog-items-enabled=false`,
  `pdv.sessao.legacy-enabled=false`) — o perfil `dev` liga as duas, e boa parte da suíte de mesa exercita
  caminhos desligados em produção (PDV-C032). Exemplo: `ComandaMergeIT`
- `OrderCashbackPostgresIT` (Testcontainers): a FK `cashback_entry.order_item_id` só existe no Flyway; prova que
  reembolso e retirada de pedido com cashback não regravam `order_item` (PED-C005)
- Mocks de `EmailPort` para evitar tentativas reais de envio em unit tests
- `@ActiveProfiles("dev")` em todos os ITs — garante H2, Caffeine e LoggingEmailAdapter
