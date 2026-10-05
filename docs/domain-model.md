# Modelo de Domínio

## Entidades de domínio (`core/domain/model/`)

### AuthProvider (enum)

```java
enum AuthProvider {
    LOCAL,   // autenticação por usuário/senha
    GOOGLE   // autenticação via OAuth2 Google
}
```

### User

```java
class User {
    Long id
    String username
    String password          // hash BCrypt — null para usuários Google sem senha local
    boolean enabled          // false até verificar email (registro público)
    String email
    boolean emailVerified
    String pendingEmail      // email aguardando confirmação (troca de email em andamento)
    String avatarFilename
    Instant createdAt
    AuthProvider authProvider  // LOCAL ou GOOGLE (padrão: LOCAL)
    String googleId          // sub do ID token Google; null para usuários locais

    Set<Role> roles

    // Factory methods
    static User of(username, hashedPassword, roles)                              // criação admin
    static User ofPendingVerification(username, hashedPassword, email, roles)   // auto-registro (enabled=false)
    static User fromGoogle(googleId, username, email, roles)                    // criação via OAuth (emailVerified=true, sem senha)
    static User fromPersisted(id, username, password, enabled, email,
                              emailVerified, pendingEmail, avatarFilename,
                              createdAt, roles, googleId, authProvider)

    // Operações de domínio
    void assignEmail(email)             // define email sem marcar como não-verificado (criação admin)
    void changeEmail(newEmail)          // muda email + emailVerified=false
    void setPendingEmail(email)         // guarda pendingEmail aguardando confirmação
    void applyPendingEmail()            // move pendingEmail → email, limpa pendingEmail
    void changePassword(hashedPassword) // muda hash; chame após validar complexidade
    void rename(newUsername)
    void enable() / void disable()
    void confirmEmail()                 // emailVerified=true + enabled=true
    void setAvatar(avatarFilename)
    void clearAvatar()                  // avatarFilename = null
    void addRole(role)
    void removeRole(role)               // remove por nome
    void linkGoogle(googleId)           // vincula Google ID a conta local existente
}
```

### Role

```java
class Role {
    Long id
    String name              // convenção: prefixado com ROLE_ (ex: ROLE_ADMIN)

    Set<Permission> permissions

    static Role of(id, name, permissions)
    void addPermission(permission)
}
```

### Permission

```java
class Permission {
    Long id
    String name              // sem prefixo (ex: USER_CREATE, ROLE_READ)

    static Permission of(id, name)
}
```

### LoginResponse (record)

Retornado por `AuthUseCase.login()`. Encapsula o resultado do login que pode ser um par de tokens (sucesso) ou um challenge de 2FA.

```java
record LoginResponse(TokenPair tokenPair, String challengeToken, boolean twoFactorRequired) {
    static LoginResponse success(TokenPair pair)
    static LoginResponse twoFactorChallenge(String challengeToken)
}
```

### TokenPair (record)

```java
record TokenPair(String accessToken, String refreshToken)
```

### SessionInfo (record)

Representa uma sessão ativa (refresh token não expirado e não revogado).

```java
record SessionInfo(Long id, Instant createdAt, Instant expiresAt, String ipAddress, String userAgent)
```

### OAuthLoginResult (record)

Retornado por `OAuthLoginUseCase.loginWithGoogle()`. Carrega o par de tokens e o username resolvido para publicação do audit event.

```java
record OAuthLoginResult(TokenPair tokenPair, String username)
```

### GoogleUserInfo (record)

Resultado da verificação do ID token Google. Mapeado a partir das claims `sub`, `email` e `name`.

```java
record GoogleUserInfo(String googleId, String email, String name)
```

### UpdateProfileResult (record)

Retornado por `UserUseCase.updateOwnProfile()` e `UserUseCase.updateUser()`. Indica se uma troca de email foi iniciada (para o controller decidir qual evento de auditoria publicar).

```java
record UpdateProfileResult(User user, boolean emailChangePending)
```

### EmailVerificationCode (record)

```java
record EmailVerificationCode(
    Long id,
    String username,
    String code,           // SHA-256 hash no banco; plaintext apenas no email
    Instant expiresAt,
    Instant sentAt,
    boolean used
) {
    boolean isExpired()                         // expiresAt.isBefore(now)
    boolean isOnCooldown(long cooldownSeconds)  // sentAt + cooldown > now
}
```

### PasswordResetToken (record)

```java
record PasswordResetToken(
    Long id,
    String username,
    String tokenHash,    // SHA-256 do token; plaintext apenas no email
    Instant expiresAt,
    Instant requestedAt,
    Instant usedAt       // null = não usado
) {
    boolean isExpired()  // Instant.now().isAfter(expiresAt)
    boolean isUsed()     // usedAt != null
}
```

### TotpConfig (record)

Configuração de 2FA de um usuário. O `secretEncrypted` é cifrado com AES-256 antes de persistir (via `TotpEncryptionPort`).

```java
record TotpConfig(
    Long id,
    String username,
    String secretEncrypted,  // AES-256 cifrado
    boolean enabled,
    Instant confirmedAt      // null = setup pendente, != null = 2FA ativo
)
```

### TotpChallengeToken (record)

Token de curta duração emitido após login com senha quando o usuário tem 2FA ativo. Válido por 5 minutos.

```java
record TotpChallengeToken(
    Long id,
    String username,
    String tokenHash,    // SHA-256; plaintext retornado ao frontend
    Instant expiresAt,
    Instant createdAt,
    Instant usedAt
) {
    boolean isExpired()  // Instant.now().isAfter(expiresAt)
    boolean isUsed()     // usedAt != null
}
```

### DevChallengeToken (record)

Token emitido na etapa 1 do duplo TOTP DEV. Armazena o período T do primeiro código para validar consecutividade na etapa 2. TTL: 90 segundos.

```java
record DevChallengeToken(
    Long id,
    String username,
    String tokenHash,  // SHA-256; plaintext retornado ao frontend como devToken
    long periodT,      // epoch / 30 — período TOTP do primeiro código
    Instant expiresAt,
    Instant createdAt,
    Instant usedAt
) {
    boolean isExpired()  // Instant.now().isAfter(expiresAt)
    boolean isUsed()     // usedAt != null
}
```

### DevElevationResult (record)

Retornado por `AuthUseCase.completeDevElevation()`. Carrega o access token DEV-elevado e o username para publicação do audit event.

```java
record DevElevationResult(String username, String devAccessToken)
```

### TotpBackupCode (record)

Um dos 8 backup codes de recuperação de 2FA. Armazenado como hash SHA-256.

```java
record TotpBackupCode(
    Long id,
    String username,
    String codeHash,   // SHA-256; plaintext exibido ao usuário uma única vez
    Instant usedAt     // null = não usado
) {
    boolean isUsed()   // usedAt != null
}
```

### AuditLogEntry (record)

```java
record AuditLogEntry(
    Long id,
    String username,   // serializado como "who" na resposta HTTP
    String action,     // EventType como string
    String target,     // "user:x", "role:y", "permission:z" (pode ser null)
    String details,    // JSON string com detalhes extras (pode ser null)
    String ipAddress,
    Instant timestamp
)
```

### AvatarServeResult (sealed interface)

Resultado de `AvatarUseCase.serve()`. O controller decide como responder com base no subtipo.

```java
sealed interface AvatarServeResult {
    record Redirect(String url) implements AvatarServeResult {}     // armazenamento S3/CDN — retornar 308
    record LocalFile(byte[] bytes, String extension) implements AvatarServeResult {}  // armazenamento local — servir bytes
    record NotFound() implements AvatarServeResult {}
}
```

### StatsResult (record)

```java
record StatsResult(
    long totalUsers,
    long activeUsers,
    long disabledUsers,
    long totalRoles,
    long totalPermissions
)
```

### PageResult\<T\> (record)

```java
record PageResult<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
)
```

### NotificationType (enum)

```java
enum NotificationType {
    PASSWORD_CHANGED,       // senha alterada
    ACCOUNT_LOCKED,         // conta bloqueada por excesso de tentativas
    TOTP_ENABLED,           // 2FA ativado
    TOTP_DISABLED,          // 2FA desativado
    TOKEN_THEFT_DETECTED,   // uso suspeito de token detectado
    EMAIL_CHANGED,          // email da conta alterado
    ROLE_ASSIGNED,          // papel atribuído ao usuário
    ROLE_REMOVED,           // papel removido do usuário
    ACCOUNT_DISABLED,       // conta desativada por administrador
    SYSTEM,                 // uso programático genérico
    ESTOQUE,                // ponto de reposição e kits (ESTOQUE_STOCK_MANAGE)
    CAIXA,                  // abertura, sangria/suprimento, fechamento (FINANCEIRO_READ)
    OPERACAO,               // cancelamento, reembolso, correção de pagamento, preço (FINANCEIRO_READ)
    RESUMO,                 // resumo diário às 8h (FINANCEIRO_READ)
    DEV                     // alertas técnicos para a ROLE_DEV
}
```

### Notification (record)

```java
record Notification(
    Long id,
    String username,
    NotificationType type,
    String title,
    String body,
    Instant readAt,     // null = não lida
    Instant createdAt
) {
    boolean isRead()    // readAt != null
}
```

### NotificationPreference (record)

```java
record NotificationPreference(
    String username,
    NotificationType type,
    boolean inAppEnabled,   // se false, notificação in-app não é persistida
    boolean emailEnabled    // se false, email não é enviado para este evento
) {
    static NotificationPreference defaultFor(username, type)  // ambos = true
}
```

### Product (record) — domínio `estoque`

SKU pai da grade de produtos. Agrega as variações (SKU filhos).

> ⚠️ **Este bloco descrevia 6 componentes quando o record já tinha 30** (corrigido em 2026-08-31,
> EST-C017). `Product` cresceu por acumulação — pricing, kit, lote, marketing, marketplace, catálogo,
> mesa — e a lista abaixo é a atual. As fábricas mantêm sobrecargas curtas por compatibilidade, então
> chamadas antigas continuam compilando; a fonte da verdade é
> `core/domain/model/estoque/Product.java`.

```java
record Product(
    Long id, String sku, String name, String category, boolean active,
    List<ProductVariant> variants,
    Pricing pricing, ProductType type, boolean lotTracked,          // preço, kit (EST-F015), lote (EST-F008)
    String brand, String imageUrl, boolean onSale, boolean superPromo,
    String description, String videoUrl, List<String> images,       // marketing / vitrine
    List<ProductAttribute> attributes, Long categoryId, String barcode,
    MeasurementUnit unit, boolean sampleProduct, boolean kitComponentEligible,
    boolean visibleInPos, boolean visibleInMarketplace,             // canal (V91)
    ProductStatus status, Long brandId,
    boolean availableForTable, boolean sessionProduct,              // mesa (V112, PDV-F010)
    Integer sessionsPerUnit, BigDecimal openRoshPrice
) {
    static Product create(...)   // criação (id=null, active=true) — várias sobrecargas
    static Product of(...)       // reconstituição a partir de persistência
}
```

**Os quatro campos de mesa** (V112) moram aqui porque `product` é tabela do domínio `estoque`, ainda
que quem os use seja o PDV: `availableForTable` decide se o SKU pode ser lançado numa comanda,
`sessionProduct` marca o que é vendido por sessão de narguilé, `sessionsPerUnit` é **sugestão de
tela** para a conversão (não movimenta saldo — para isso existe `POST /estoque/conversions`), e
`openRoshPrice` mora no SKU **pai**: a linha da comanda chega com o SKU da variação, para saber qual
essência sai do estoque, mas cobra este valor.

### ProductVariant (record) — domínio `estoque`

SKU filho, distinguido por seus `ProductAttribute` (sabor, tamanho, cor).

```java
record ProductVariant(Long id, String sku, List<ProductAttribute> attributes, boolean active) {
    static ProductVariant create(sku, attributes)               // criação (id=null, active=true)
    static ProductVariant of(id, sku, attributes, active)       // reconstituição a partir de persistência
}
```

### ProductAttribute (record) — domínio `estoque`

Valor sem identidade própria — sempre filho de um `ProductVariant`.

```java
record ProductAttribute(String type, String value)
```

### Warehouse (record) — domínio `estoque`

Depósito de estoque (loja física ou e-commerce). Cada `StockBalance` é mantido por SKU
dentro de um depósito.

```java
record Warehouse(Long id, String code, String name, WarehouseType type, boolean active) {
    static Warehouse create(code, name, type)                    // criação (id=null, active=true)
    static Warehouse of(id, code, name, type, active)             // reconstituição a partir de persistência
}

enum WarehouseType { LOJA_FISICA, ECOMMERCE }
```

### StockBalance (record) — domínio `estoque`

Saldo de um SKU em um `Warehouse`. `version` suporta locking otimista para as escritas
concorrentes de `StockMovement`.

```java
record StockBalance(Long id, String sku, Long warehouseId, BigDecimal quantity, long version) {
    static StockBalance zero(sku, warehouseId)                    // saldo inicial (zero), sem persistir
    static StockBalance of(id, sku, warehouseId, quantity, version) // reconstituição a partir de persistência
    StockBalance apply(MovementType type, BigDecimal quantity)     // aplica movimentação; ENTRADA/AJUSTE somam,
                                                                     // SAIDA subtrai; lança InsufficientStockException
                                                                     // se o resultado ficaria negativo
}
```

### StockMovement (record) — domínio `estoque`

Registro auditável de uma movimentação manual de estoque (entrada, saída ou ajuste) que
altera um `StockBalance`.

```java
record StockMovement(Long id, String sku, Long warehouseId, MovementType type, BigDecimal quantity,
        String reason, String username, Instant createdAt) {
    static StockMovement create(sku, warehouseId, type, quantity, reason, username) // criação (id=null, createdAt=now)
    static StockMovement of(id, sku, warehouseId, type, quantity, reason, username, createdAt) // reconstituição
}

enum MovementType { ENTRADA, SAIDA, AJUSTE }
```

`username` é sempre o usuário autenticado (JWT) — nunca informado pelo cliente da API.

---

## Ports IN — contratos de use case

### AuthUseCase

| Método | Descrição |
|--------|-----------|
| `LoginResponse login(username, password)` | Autentica; retorna tokens ou challenge de 2FA |
| `TokenPair completeTwoFactorLogin(challengeToken, totpCode)` | Conclui o login após validação do código TOTP ou backup code |
| `TokenPair refresh(oldRefreshToken)` | Rotaciona tokens; detecta reutilização como evento de segurança |
| `void logout(refreshToken)` | Revoga uma sessão |
| `void logoutAll(username)` | Revoga todas as sessões do usuário |
| `List<SessionInfo> listActiveSessions(username)` | Retorna sessões ativas (refresh tokens não expirados e não revogados) |
| `void revokeSession(sessionId, username)` | Revoga uma sessão específica do usuário |
| `DevElevationResult completeDevElevation(rawDevToken, secondTotpCode)` | Etapa 2 DEV: valida par consecutivo, emite access token com authority `DEV_ELEVATED`. Sem refresh token — TTL 1h. |

### OAuthLoginUseCase

| Método | Descrição |
|--------|-----------|
| `OAuthLoginResult loginWithGoogle(idToken)` | Verifica ID token Google, cria ou vincula conta, emite par de tokens |

### TotpUseCase

| Método | Descrição |
|--------|-----------|
| `TotpSetupResult setup(username)` | Gera secret TOTP. Retorna `{secret, otpauthUri}` |
| `List<String> confirm(username, totpCode)` | Confirma código após escanear QR; ativa 2FA; retorna 8 backup codes |
| `void disable(username, currentPassword, totpCode)` | Desativa 2FA — exige senha atual e código TOTP/backup code |
| `List<String> regenerateBackupCodes(username, currentPassword)` | Gera novos backup codes (invalida anteriores) — exige senha atual |
| `boolean isEnabled(username)` | Retorna se o 2FA está ativo para o usuário |
| `int countBackupCodesRemaining(username)` | Conta backup codes ainda não usados (retorna 0 se 2FA inativo) |
| `TotpSetupResult replaceTotp(username, currentTotpCode)` | Troca dispositivo: valida código atual, apaga configuração vigente e inicia novo setup |
| `String issueDevFirstCode(username, firstTotpCode)` | Etapa 1 do duplo TOTP DEV: valida primeiro código e retorna `rawDevToken` (TTL 90s) |
| `String completeDevChallenge(rawDevToken, secondTotpCode)` | Etapa 2 do duplo TOTP DEV: valida que o segundo código pertence ao período T+1; retorna username |

`TotpSetupResult` é um record interno: `record TotpSetupResult(String secret, String otpauthUri)`

### AvatarUseCase

| Método | Descrição |
|--------|-----------|
| `String upload(username, bytes, originalFilename)` | Valida, armazena e associa o avatar ao usuário; retorna a URL pública |
| `void delete(username)` | Remove o avatar do usuário; no-op se não tiver avatar |
| `AvatarServeResult serve(filename)` | Resolve como servir o arquivo: redirect (S3/CDN) ou bytes locais |

### UserUseCase

| Método | Descrição |
|--------|-----------|
| `User createUser(username, rawPassword, roles)` | Criação por admin (enabled=true, sem email) |
| `User createUser(username, rawPassword, email, roles)` | Criação por admin com email (sem trigger de verificação) |
| `User registerUser(username, rawPassword, email, roles)` | Auto-registro (enabled=false, dispara verificação por email) |
| `User getUserById(id)` | Busca por ID |
| `Optional<User> findByUsername(username)` | Busca por username |
| `void assignRole(username, roleName)` | Atribui role + evict cache |
| `void removeRole(username, roleName)` | Remove role + evict cache |
| `PageResult<User> listAll(page, size)` | Listagem paginada sem filtros |
| `PageResult<User> findFiltered(search, enabled, sortBy, sortDir, page, size, excludeRoles)` | Listagem filtrada com busca, status, ordenação e exclusão de roles (ex: `ROLE_DEV` oculto para não-DEV) |
| `String deleteUser(id)` | Soft delete; revoga sessões; retorna username para auditoria |
| `void changeOwnPassword(username, currentPassword, newPassword, totpCode, revokeOtherSessions)` | Troca senha; valida TOTP quando 2FA está ativo; revoga sessões apenas se `revokeOtherSessions=true` |
| `String setUserEnabled(id, enabled)` | Habilita/desabilita conta; retorna username para auditoria |
| `UpdateProfileResult updateUser(id, newUsername, newEmail)` | Atualização administrativa de identidade |
| `UpdateProfileResult updateOwnProfile(username, newUsername, newEmail, currentPassword)` | Auto-atualização — exige `currentPassword` ao trocar email |
| `String verifyEmail(code)` | Confirma email (CAS atômico); retorna username para auditoria |
| `void resendVerification(email)` | Reenvio silencioso (sem enumeração de email) |
| `void requestPasswordReset(email)` | Inicia fluxo de recuperação de senha; sempre silencioso |
| `String resetPassword(token, newPassword)` | Conclui recuperação; revoga sessões; retorna username para auditoria |
| `String confirmEmailChange(code)` | Confirma troca de email via código; retorna username para auditoria |

### RoleUseCase

| Método | Descrição |
|--------|-----------|
| `Role createRole(name)` | Cria role |
| `PageResult<Role> listAll(page, size)` | Listagem paginada |
| `PageResult<Role> findByNameContaining(search, page, size)` | Listagem filtrada por nome (parcial, case-insensitive) |
| `Role findByName(name)` | Busca por nome |
| `void deleteRole(name)` | Remove role |
| `void assignPermission(roleName, permissionName)` | Adiciona permissão à role |
| `void removePermission(roleName, permissionName)` | Remove permissão da role |

### PermissionUseCase

| Método | Descrição |
|--------|-----------|
| `Permission createPermission(name)` | Cria permissão |
| `PageResult<Permission> listAll(page, size)` | Listagem paginada |
| `Permission findByName(name)` | Busca por nome |
| `void deletePermission(name)` | Remove permissão |

### AuditLogsUseCase

| Método | Descrição |
|--------|-----------|
| `PageResult<AuditLogEntry> list(username, action, from, to, page, size)` | Listagem paginada com filtros opcionais |

### StatsUseCase

| Método | Descrição |
|--------|-----------|
| `StatsResult getStats()` | Retorna totais agregados: usuários, roles, permissões |

### NotificationUseCase

| Método | Descrição |
|--------|-----------|
| `Notification notify(username, type, title, body)` | Persiste uma notificação in-app e retorna o objeto salvo (usado para push SSE) |
| `PageResult<Notification> getNotifications(username, unreadOnly, page, size)` | Lista paginada, opcionalmente apenas não-lidas |
| `void markAsRead(username, notificationId)` | Marca uma notificação como lida — ownership verificado na query (`WHERE id=? AND username=?`); silencioso se não pertencer ao usuário |
| `void markAllAsRead(username)` | Marca todas as notificações do usuário como lidas |
| `long countUnread(username)` | Conta notificações não lidas |
| `void delete(username, notificationId)` | Remove uma notificação — ownership verificado na query (`WHERE id=? AND username=?`); silencioso se não pertencer ao usuário |

### NotificationPreferenceUseCase

| Método | Descrição |
|--------|-----------|
| `List<NotificationPreference> getPreferences(username)` | Retorna preferências para todos os `NotificationType`. Tipos sem registro retornam com `inAppEnabled=true, emailEnabled=true` (default) |
| `void updatePreference(username, type, inAppEnabled, emailEnabled)` | Persiste (upsert) a preferência para o tipo especificado |

### EstoqueUseCase

| Método | Descrição |
|--------|-----------|
| `Product createProduct(sku, name, category, variants)` | Cria produto (SKU pai) com variações; lança `DuplicateSkuException` se o SKU já existir |
| `PageResult<Product> listProducts(page, size)` | Lista produtos paginados |
| `Warehouse createWarehouse(code, name, type)` | Cria um depósito; lança `DuplicateWarehouseCodeException` se o código já existir |
| `List<Warehouse> listWarehouses()` | Lista todos os depósitos cadastrados |
| `StockBalance getStockBalance(sku, warehouseCode)` | Consulta o saldo de um SKU em um depósito; retorna saldo zero se nunca movimentado; lança `WarehouseNotFoundException` se o depósito não existir |
| `StockBalance adjustStock(sku, warehouseCode, type, quantity, reason, username)` | Registra uma movimentação manual (entrada/saída/ajuste) e atualiza o `StockBalance` na mesma transação; lança `WarehouseNotFoundException` ou `InsufficientStockException` (SAIDA que deixaria o saldo negativo) |

---

## Ports OUT — contratos de infraestrutura

### user/

**UserRepository**
```
User save(User)
Optional<User> findById(Long)
Optional<User> findByUsername(String)
Optional<User> findByEmail(String)
Optional<User> findByGoogleId(String)
PageResult<User> findAll(page, size)
PageResult<User> findFiltered(search, enabled, sortBy, sortDir, page, size, excludeRoles)
void deleteById(Long)
```

**UserCachePort** — `void evict(String username)`

**UserAuthoritiesPort** — `Set<String> loadAuthoritiesByUsername(String username)`

### token/

**AccessTokenPort** — `String generateFor(String username, Set<String> authorities)`

**RefreshTokenPort**
```
String issue(String username)
RotationResult rotate(String oldToken)    // RotationResult: username + newToken
Optional<String> revoke(String token)     // retorna username se encontrado
void revokeAll(String username)
void deleteExpiredAndRevoked()            // chamado pelo scheduler
List<SessionInfo> findActiveSessions(String username)
void revokeByIdAndUsername(Long id, String username)   // revoga sessão específica validando owner
```

**TokenBlocklistPort**
```
void blockAllBefore(String username, Instant instant)
boolean isBlockedAt(String username, Instant tokenIssuedAt)
```

### credential/

**PasswordHashPort** — `String hash(raw)` / `boolean matches(raw, encoded)`

**CredentialVerifierPort** — `VerifiedUser verify(username, password)` → `{username, authorities}`

### role/

**RoleRepository**
```
Role save(Role)
Optional<Role> findByName(String)
Optional<Role> findById(Long)
PageResult<Role> findAll(page, size)
PageResult<Role> findByNameContaining(search, page, size)
void addPermissions(roleName, Set<String> permissionNames)
void deleteByName(String)
void removePermission(roleName, permissionName)
```

**PermissionRepository**
```
Permission save(Permission)
Optional<Permission> findByName(String)
PageResult<Permission> findAll(page, size)
void deleteByName(String)
```

### notification/

**NotificationRepository**
```
Notification save(Notification)
PageResult<Notification> findByUsername(username, unreadOnly, page, size)
void markAsRead(Long id, String username)      // UPDATE WHERE id=? AND username=? — ownership na query
void markAllAsRead(String username)
long countUnread(String username)
void delete(Long id, String username)          // DELETE WHERE id=? AND username=? — ownership na query
void deleteReadBefore(Instant before)          // usado pelo NotificationCleanupService
```

**NotificationPreferenceRepository**
```
Map<NotificationType, NotificationPreference> findByUsername(String username)
void upsert(NotificationPreference preference)    // INSERT ON CONFLICT DO UPDATE — atômico, sem race condition
```

**NotificationSsePort**
```
void send(String username, Notification notification)   // push SSE para todos os emitters ativos do usuário
int activeConnections(String username)                  // número de conexões SSE abertas
```
Implementado por `SseEmitterRegistry` em `adapter/in/sse/`. Injetado em `NotificationEventListener` para desacoplar infra de componentes de adapter HTTP.

### estoque/

**ProductRepository**
```
PageResult<Product> findAll(page, size)
Optional<Product> findBySku(String sku)
Product save(Product product)
```

**WarehouseRepository**
```
Warehouse save(Warehouse warehouse)
Optional<Warehouse> findByCode(String code)
List<Warehouse> findAll()
```

**StockBalanceRepository**
```
Optional<StockBalance> findBySkuAndWarehouseId(String sku, Long warehouseId)
StockBalance save(StockBalance stockBalance)
```

**StockMovementRepository**
```
StockMovement save(StockMovement movement)
PageResult<StockMovement> findBySkuAndWarehouseId(String sku, Long warehouseId, int page, int size) // histórico, mais recentes primeiro
```

**EmailPort**
```
void sendVerificationCode(to, username, code)
void sendPasswordResetLink(to, username, resetLink)
void sendEmailChangeNotification(oldEmail, username, newEmail)
```

**EmailVerificationCodeRepository**
```
EmailVerificationCode save(username, code, expiresAt)
Optional<EmailVerificationCode> findByCode(String code)     // busca por hash
Optional<EmailVerificationCode> findByUsername(String)
boolean markAsUsed(String code)                             // CAS atômico — UPDATE ... WHERE used=false
void deleteByUsername(String)
void deleteExpiredBefore(Instant)                           // chamado pelo scheduler de limpeza
```

**PasswordResetTokenRepository**
```
PasswordResetToken save(username, rawToken, expiresAt)
Optional<PasswordResetToken> findByToken(String rawToken)
boolean markAsUsed(String rawToken)                         // CAS atômico — UPDATE ... WHERE usedAt IS NULL
void deleteByUsername(String)
void deleteExpiredBefore(Instant)                           // chamado pelo scheduler de limpeza
```

### oauth/

**GoogleTokenVerifierPort** — `GoogleUserInfo verify(String idToken)`

Implementado por `GoogleTokenVerifierAdapter` (valida via JWKS Google + issuer + audience).

### ratelimit/

**LoginAttemptPort**
```
void recordFailure(String username)
void recordSuccess(String username)
boolean isLocked(String username)
```

**LoginRateLimiterPort** — `boolean tryConsume(String ip)`

### twofa/

**TwoFactorAuthPort** — orquestra challenge token + validação TOTP no fluxo de login
```
boolean isEnabled(String username)
String issueChallengeToken(String username)                              // gera e persiste challenge; retorna plaintext
String completeChallengeLogin(String challengeToken, String totpCode)   // valida; retorna username
String issueDevFirstCode(String username, String firstTotpCode)         // etapa 1 duplo TOTP DEV; retorna rawDevToken
String completeDevChallenge(String rawDevToken, String secondTotpCode)  // etapa 2 duplo TOTP DEV; retorna username
boolean validateTotpCode(String username, String code)                  // valida TOTP ou backup code sem lançar exceção; usado em changeOwnPassword
```

**TotpConfigRepository** — persiste a configuração de 2FA (secret cifrado, enabled, confirmedAt)
```
TotpConfig save(username, secretEncrypted)
Optional<TotpConfig> findByUsername(String)
void enable(username, confirmedAt)
void deleteByUsername(String)
void deleteUnconfirmedBefore(Instant)   // limpeza de setups pendentes não confirmados
```

**TotpBackupCodeRepository** — persiste os 8 backup codes de recuperação (hashes SHA-256)
```
void saveAll(username, List<String> rawCodes)
Optional<TotpBackupCode> findByCode(String rawCode)
boolean markAsUsed(String rawCode)                   // CAS atômico — UPDATE ... WHERE usedAt IS NULL
void deleteByUsername(String)
int countRemainingByUsername(String username)         // conta códigos com usedAt IS NULL
```

**TotpChallengeTokenRepository** — persiste challenge tokens de curta duração (5 min)
```
TotpChallengeToken save(username, rawToken, expiresAt)
Optional<TotpChallengeToken> findByToken(String rawToken)
boolean markAsUsed(String rawToken)
void deleteByUsername(String)
void deleteExpiredBefore(Instant)        // chamado pelo scheduler de limpeza
```

**DevChallengeRepository** — persiste os tokens de desafio DEV (TTL 90s, hash SHA-256)
```
DevChallengeToken save(username, rawToken, periodT, expiresAt)
Optional<DevChallengeToken> findByToken(String rawToken)
boolean consume(String rawToken)          // CAS atômico — marca como usado; retorna false se já foi consumido
void deleteExpiredBefore(Instant before)  // chamado pelo scheduler de limpeza
```

**TotpEncryptionPort** — cifra/decifra o secret TOTP com AES-256
```
String encrypt(String plaintext)
String decrypt(String ciphertext)
```
Implementado por `AesEncryptionAdapter`. Chave via property `totp.encryption.key` (mínimo 32 chars em prod).

### storage/

**AvatarStoragePort** — abstrai armazenamento local vs. S3/CDN
```
String save(byte[] bytes, String extension)          // retorna filename gerado (UUID + extensão)
Optional<InputStream> load(String filename)
void delete(String filename)
Optional<String> getPublicUrl(String filename)       // empty para local; URL pública para S3/CDN
```

Implementações:
- `@ConditionalOnProperty(avatar.storage.type=local)` (padrão): `LocalAvatarStorageAdapter` — salva em `avatar.storage-dir` e serve via `/avatars/{filename}`
- `@ConditionalOnProperty(avatar.storage.type=s3)`: `S3AvatarStorageAdapter` — salva no S3 e retorna `getPublicUrl` com a URL pública; `GET /avatars/{filename}` retorna `308 Redirect`

### audit/

**AuditLogRepository**
```
void save(AuditEvent event, String ipAddress)
PageResult<AuditLogEntry> findFiltered(username, action, from, to, page, size)
long deleteOlderThan(Instant cutoff)    // retorna número de entradas removidas
```

---

## Regras de negócio nos Services

### UserService — complexidade de senha

Mínimo 8 caracteres com todos:
- 1 letra maiúscula
- 1 letra minúscula
- 1 dígito
- 1 caractere especial

### UserService — códigos de verificação de email

- 12 caracteres alfanuméricos (~62 bits de entropia)
- Armazenado como SHA-256 hash no banco
- TTL padrão: 15 minutos
- Cooldown de reenvio: 60 segundos

### UserService — tokens de reset de senha

- 512 bits (64 bytes) aleatórios, encoded em Base64 URL-safe (igual ao refresh token)
- Armazenado como SHA-256 hash no banco
- TTL padrão: 30 minutos (configurável via `password-reset.ttl-minutes`)
- Ao usar o token: revoga todas as sessões + bloqueia todos os JWTs

### AuthService — 2FA obrigatório (`security.2fa.required`)

Quando a flag `security.2fa.required=true` (configurável via `PUT /system/config`), o `AuthService.login()` bloqueia usuários que ainda não ativaram o 2FA:
- Usuário sem TOTP ativo → `TotpSetupRequiredException` (403 `TOTP_SETUP_REQUIRED`)
- Usuário com TOTP ativo → fluxo normal de challenge (200 `PENDING_2FA`)

O frontend deve capturar `403 TOTP_SETUP_REQUIRED` e redirecionar para o fluxo de configuração de 2FA (`POST /auth/2fa/setup`).

### AuthService — detecção de reutilização de refresh token

Se `rotate()` encontrar token já revogado (revoked=true):
1. Considera comprometimento de sessão
2. Chama `revokeAll(username)` — encerra todas as sessões
3. Chama `blockAllBefore(username, now)` — invalida todos os JWTs ativos
4. Lança `RefreshTokenAlreadyUsedException`

### UserService — mutações que invalidam sessões

`resetPassword`, `deleteUser` → sempre revogam todos os refresh tokens + bloqueiam todos os JWTs antes de agora.

`changeOwnPassword` → revoga sessões **somente** se `revokeOtherSessions=true`. Se `false` (padrão), a senha é trocada mas as sessões existentes continuam válidas.

### UserService — validação 2FA em changeOwnPassword

Quando o usuário tem 2FA ativo, `changeOwnPassword` exige `totpCode` (código TOTP de 6 dígitos ou backup code):
- Se `totpCode` for `null` ou vazio → `TotpCodeRequiredException` (400 `TOTP_CODE_REQUIRED`)
- Se `totpCode` for inválido → `InvalidTotpCodeException` (400 `INVALID_TOTP_CODE`)

### UserService — evict de cache

Qualquer mutação em User (save, assignRole, removeRole, changePassword, etc.) chama `UserCachePort.evict(username)`.

### TotpService — criptografia do secret

O secret TOTP é gerado em texto puro, enviado ao frontend via `POST /auth/2fa/setup`, e então criptografado com AES-256 via `TotpEncryptionPort` antes de persistir. Nunca é armazenado em texto puro no banco.

### TotpService — cleanup de setup pendentes

Setups não confirmados (secret gerado mas `POST /auth/2fa/confirm` nunca chamado) são removidos automaticamente após `totp.pending-setup.ttl-hours` (padrão: 24h).

### TotpService / AuthService — duplo TOTP DEV

O acesso à área DEV exige dois códigos TOTP de períodos **consecutivos** (T e T+1):

1. `issueDevFirstCode(username, code)` — valida o código atual; persiste `(username, token, periodT)` com TTL 90s; retorna `rawDevToken`.
2. `completeDevChallenge(rawDevToken, code2)` — valida que `currentPeriod == periodT + 1`; verifica `code2`; consome o token atomicamente.
3. `AuthService.completeDevElevation(rawDevToken, code2)` — chama a etapa 2 e emite access token com authority `DEV_ELEVATED`. Sem refresh token.

O período TOTP é calculado como `epoch / 30`. A consecutividade é validada no instante da chamada — se o usuário chamar a etapa 2 no mesmo período do primeiro código, recebe `400 TOTP_NOT_CONSECUTIVE`.

### Permissões e roles — hierarquia

**`ROLE_USER`** (subset de `ROLE_ADMIN`, subset de `ROLE_DEV`):

```
ROLE_USER  ⊂  ROLE_ADMIN  ⊂  ROLE_DEV
```

**Permissões de `ROLE_ADMIN`** (gerenciadas pelo `SeedConfig` em dev + `DevRoleBootstrapConfig` em todos os perfis):
```
USER_CREATE  USER_READ  USER_UPDATE  USER_DELETE  USER_ROLE_ASSIGN  USER_STATUS
ROLE_READ    ROLE_MANAGE_PERMISSIONS
PERMISSION_READ
AUDIT_READ
```

**Permissões exclusivas de `ROLE_DEV`** (gerenciadas pelo `DevRoleBootstrapConfig` em todos os perfis):
```
DEV_ROLE_MANAGE      — criar e deletar roles via POST/DELETE /roles (ADMIN apenas atribui)
DEV_PERMISSION_MANAGE — criar e deletar permissões via POST/DELETE /permissions (ADMIN não tem)
```

> `ROLE_CREATE`, `ROLE_DELETE`, `PERMISSION_CREATE` e `PERMISSION_DELETE` existem no banco (V36) mas **não são verificadas** por nenhum `@PreAuthorize` — os endpoints usam `DEV_ROLE_MANAGE` e `DEV_PERMISSION_MANAGE` (V37).
> `DEV_LOGS_TECHNICAL`, `DEV_SYSTEM_CONFIG` e `DEV_DEBUG_ENDPOINTS` foram **removidas** pela migration V33 e não são recriadas pelo bootstrap.

> `ROLE_DEV` recebe **todas** as permissões do ADMIN + as exclusivas DEV acima.

**Proteção de endpoint DEV:** endpoints que exigem elevação DEV (duplo TOTP) verificam a authority `DEV_ELEVATED` no JWT — não apenas `ROLE_DEV`. O token DEV-elevado é emitido pelo `POST /auth/dev/complete` e não possui refresh token.

### Seed de roles e usuários (todos os perfis)

`DevRoleBootstrapConfig` (`@Profile("dev|hml|prod")`) garante na inicialização:
- Todas as permissões ADMIN e DEV existem
- `ROLE_DEV` existe e tem todas as permissões
- Se `seed.dev.email` (env `DEV_EMAIL`) estiver definido: cria o usuário DEV com o prefixo do email como username

```properties
# application.properties (padrão dev)
seed.dev.email=${DEV_EMAIL:}           # vazio = não cria usuário DEV
seed.dev.password=${DEV_PASSWORD:Dev@secure1!}
```

`SeedConfig` (`@Profile("dev")`) cria os usuários de teste:
- `admin` / `Admin@dev1` com `ROLE_ADMIN`
- `user` / `User@dev1` com `ROLE_USER`
