# Persistência

## Entidades JPA (`adapter/out/persistence/entity/`)

### UserEntity — tabela `users`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | UNIQUE NOT NULL |
| password | VARCHAR | nullable (BCrypt hash; null para usuários Google sem senha local) |
| enabled | BOOLEAN | NOT NULL |
| email | VARCHAR | UNIQUE (armazenado em lowercase) |
| email_verified | BOOLEAN | NOT NULL |
| pending_email | VARCHAR | nullable (email aguardando confirmação) |
| avatar_filename | VARCHAR | nullable |
| created_at | TIMESTAMP | nullable |
| auth_provider | VARCHAR(20) | NOT NULL, default `LOCAL` (`LOCAL` ou `GOOGLE`) |
| google_id | VARCHAR(255) | nullable, índice parcial único `idx_users_google_id WHERE google_id IS NOT NULL` |
| deleted_at | TIMESTAMP | nullable (soft delete) |

Relacionamento: M2M com `RoleEntity` via tabela `user_roles (user_id, role_id)`

---

### RoleEntity — tabela `roles`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| name | VARCHAR | UNIQUE NOT NULL |

Relacionamento: M2M com `PermissionEntity` via tabela `role_permissions (role_id, permission_id)`

---

### PermissionEntity — tabela `permissions`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| name | VARCHAR | UNIQUE NOT NULL |

---

### RefreshTokenEntity — tabela `refresh_tokens`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| user_id | BIGINT | FK → users(id) ON DELETE CASCADE |
| token_hash | VARCHAR(128) | UNIQUE NOT NULL (SHA-256 do token) |
| expires_at | TIMESTAMP | NOT NULL |
| revoked | BOOLEAN | NOT NULL, default false |
| created_at | TIMESTAMP | NOT NULL default now() |
| rotated_at | TIMESTAMP | nullable |
| ip_address | VARCHAR(45) | nullable — IPv4 ou IPv6 do cliente no login |
| user_agent | VARCHAR(512) | nullable — truncado a 512 chars |

Índices: `uk_refresh_token_hash`, `idx_refresh_user (user_id)`, `idx_refresh_expires (expires_at)`

O token plaintext **nunca** é persistido. `TokenHashUtils.sha256()` é aplicado antes de salvar e antes de buscar.

Queries relevantes em `RefreshTokenJpaRepository`:
- `findActiveByUsername(username, now)` — sessões ativas de um usuário (não expiradas, não revogadas)
- `countActiveByUsername(username, now)` — contagem para enforçar limite por usuário
- `countAllActive(now)` — **contagem global** usada pelo `ActiveSessionsMetric` (Gauge Prometheus)

---

### EmailVerificationCodeEntity — tabela `email_verification_codes`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | NOT NULL |
| code | VARCHAR(64) | UNIQUE NOT NULL (SHA-256 do código) |
| expires_at | TIMESTAMP | NOT NULL |
| used | BOOLEAN | NOT NULL, default false |
| sent_at | TIMESTAMP | NOT NULL |

Índices: `(code)`, `idx_email_verification_codes_expires_at (expires_at)` (para cleanup scheduler), `idx_email_verification_codes_username (username)` (C010 — usado por `findFirstByUsernameOrderByExpiresAtDescIdDesc`/`deleteByUsername`)

O código plaintext **nunca** é persistido.

---

### PasswordResetTokenEntity — tabela `password_reset_tokens`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | NOT NULL |
| token_hash | VARCHAR(64) | UNIQUE NOT NULL (SHA-256 do token) |
| expires_at | TIMESTAMPTZ | NOT NULL |
| requested_at | TIMESTAMPTZ | NOT NULL default now() |
| used_at | TIMESTAMPTZ | nullable — null = não usado |

Índices: `uk_prt_token_hash (token_hash)`, `idx_prt_token_hash (token_hash)`, `idx_password_reset_tokens_expires_at (expires_at)`, `idx_password_reset_tokens_username (username)` (C010 — usado por `deleteByUsername`)

O token plaintext **nunca** é persistido.

---

### TotpConfigEntity — tabela `totp_config`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | UNIQUE NOT NULL |
| secret_enc | TEXT | NOT NULL (secret AES-256 cifrado) |
| enabled | BOOLEAN | NOT NULL, default false |
| confirmed_at | TIMESTAMPTZ | nullable — null = setup pendente, não-null = 2FA ativo |
| created_at | TIMESTAMPTZ | NOT NULL default now() |

`enabled=false` até que o usuário confirme o primeiro código via `POST /auth/2fa/confirm`.  
Setups não confirmados são removidos pelo `TotpPendingSetupCleanupService` após `totp.pending-setup.ttl-hours` (padrão 24h).

---

### TotpBackupCodeEntity — tabela `totp_backup_codes`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | NOT NULL |
| code_hash | VARCHAR(64) | UNIQUE NOT NULL (SHA-256 do código) |
| used_at | TIMESTAMPTZ | nullable — null = disponível |

Índices: `uk_totp_backup_code (code_hash)`, `idx_totp_backup_username (username)`

Cada usuário tem 8 backup codes. Todos são deletados e recriados ao chamar `POST /auth/2fa/backup-codes/regenerate`.

---

### TotpChallengeTokenEntity — tabela `totp_challenge_tokens`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | NOT NULL |
| token_hash | VARCHAR(64) | UNIQUE NOT NULL (SHA-256 do token) |
| expires_at | TIMESTAMPTZ | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL default now() |
| used_at | TIMESTAMPTZ | nullable — null = não usado |

Índices: `uk_totp_challenge_token (token_hash)`, `idx_totp_challenge_hash (token_hash)`, `idx_totp_challenge_tokens_expires_at (expires_at)`

TTL padrão: 5 minutos. Tokens expirados são removidos pelo `TotpChallengeCleanupService`.

---

### AuditLogEntity — tabela `audit_logs`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | NOT NULL |
| action | VARCHAR(80) | NOT NULL (EventType como string) |
| target | VARCHAR(255) | nullable — "user:x", "role:y", "permission:z" |
| details | TEXT | nullable — JSON string com detalhes extras |
| ip_address | VARCHAR(45) | nullable |
| timestamp | TIMESTAMPTZ | NOT NULL default now() |

Índices: `idx_audit_logs_username (username)`, `idx_audit_logs_action (action)`, `idx_audit_logs_timestamp (timestamp DESC)`, `idx_audit_logs_username_timestamp (username, timestamp DESC)`

Retenção configurável via `audit.retention-days` (padrão 365 dias). Limpeza pelo `AuditLogCleanupService`.

---

### NotificationEntity — tabela `notifications`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | NOT NULL |
| type | VARCHAR(50) | NOT NULL (valor do enum `NotificationType`) |
| title | VARCHAR(255) | NOT NULL |
| body | TEXT | nullable |
| read_at | TIMESTAMPTZ | nullable — null = não lida |
| created_at | TIMESTAMPTZ | NOT NULL |

Índices: `idx_notifications_username (username)`, `idx_notifications_username_read_at (username, read_at)` para queries de não-lidas por usuário, `idx_notifications_created_at (created_at)` para ordenação paginada.

FK: `fk_notifications_username → users(username) ON DELETE CASCADE` (V42) — garante limpeza automática ao deletar usuário.

Limpeza automática via `NotificationCleanupService` (cron 04:00 AM): deleta notificações lidas com `read_at < cutoff`. Threshold configurável via `notification.read.retention-days` (padrão 90 dias).

---

### NotificationPreferenceEntity — tabela `notification_preferences`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| username | VARCHAR(80) | PK (composta com type) |
| type | VARCHAR(50) | PK (composta com username) — valor do enum `NotificationType` |
| in_app_enabled | BOOLEAN | NOT NULL DEFAULT TRUE |
| email_enabled | BOOLEAN | NOT NULL DEFAULT TRUE |

Chave primária composta `(username, type)` garante no máximo uma preferência por usuário/tipo. Linha ausente = ambos habilitados (default). O `NotificationEventListener` consulta preferências antes de persistir notificação ou enviar email.

Implementado com `@IdClass(NotificationPreferenceEntity.PreferenceId)` no JPA.

Índices: `idx_notification_preferences_username (username)` (V42) — evita full table scan em `findByUsername`.  
FK: `fk_notification_prefs_username → users(username) ON DELETE CASCADE` (V42).

`upsert()` é implementado com `INSERT ... ON CONFLICT (username, type) DO UPDATE SET ...` (SQL nativo) em vez do padrão check-then-act, garantindo atomicidade sob requisições concorrentes.

---

### SystemConfigEntity — tabela `system_config`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| config_key | VARCHAR(255) | UNIQUE NOT NULL |
| config_value | TEXT | nullable |
| updated_by | VARCHAR(255) | nullable — username de quem atualizou por último |
| updated_at | TIMESTAMP | nullable |

Chaves pré-populadas pela `V34__system_config.sql` e `V38__add_system_config_new_flags.sql`:

| Chave | Valor padrão | Descrição |
|-------|-------------|-----------|
| `auth.registration.enabled` | `true` | Habilita/desabilita auto-registro público |
| `auth.google.enabled` | `true` | Habilita/desabilita login via Google OAuth |
| `auth.google.register.enabled` | `true` | Habilita/desabilita criação de conta via Google |
| `auth.forgot-password.enabled` | `true` | Habilita/desabilita fluxo de recuperação de senha |
| `security.maintenance.enabled` | `false` | Modo manutenção — retorna 503 a todos exceto `/actuator/health/**` e `/system/config/public` |
| `security.2fa.required` | `false` | Força 2FA obrigatório: usuários sem TOTP ativo recebem 403 `TOTP_SETUP_REQUIRED` ao tentar logar |
| `module.audit-logs.enabled` | `true` | Quando `false`, o `AuditEventListener` não persiste eventos — útil para ambientes de teste ou modo degradado |
| `module.roles.enabled` | `true` | Quando `false`, os endpoints `/roles/**` e `/permissions/**` retornam 503 `MODULE_DISABLED` |

As chaves listadas acima são **públicas** (acessíveis via `GET /system/config/public` sem autenticação). Alterações requerem `DEV_ELEVATED` e são persistidas imediatamente no banco.

**Cache:** `SystemConfigAdapter` usa `@Cacheable` (cache `systemConfig`) em `findByKey()` e `getBoolean()`. O `save()` invalida todo o cache via `@CacheEvict(allEntries=true)`. O TTL é controlado por `spring.cache.caffeine.spec` (dev) ou `spring.cache.redis.time-to-live` (hml/prod). Leituras pelo `MaintenanceModeFilter` (a cada request) beneficiam-se deste cache.

---

### ProductEntity — tabela `product`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| sku | VARCHAR(50) | UNIQUE NOT NULL |
| name | VARCHAR(255) | NOT NULL |
| category | VARCHAR(100) | nullable |
| active | BOOLEAN | NOT NULL |

Relacionamento: 1:N com `ProductVariantEntity` (`cascade = ALL`, `orphanRemoval = true`) — variações são apagadas junto do produto pai.

---

### ProductVariantEntity — tabela `product_variant`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| product_id | BIGINT | FK → product(id) ON DELETE CASCADE |
| sku | VARCHAR(50) | UNIQUE NOT NULL |
| active | BOOLEAN | NOT NULL |

Relacionamento: `@ElementCollection` de `ProductAttributeEmbeddable` (tabela `product_attribute`) — atributos não têm identidade própria, são sempre carregados/apagados junto da variação.

Índice: `idx_product_variant_product_id (product_id)`.

---

### product_attribute (`@ElementCollection`, sem entidade própria)

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| variant_id | BIGINT | FK → product_variant(id) ON DELETE CASCADE |
| attr_type | VARCHAR(50) | NOT NULL |
| attr_value | VARCHAR(100) | NOT NULL |

Índice: `idx_product_attribute_variant_id (variant_id)`.

---

### WarehouseEntity — tabela `warehouse`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| code | VARCHAR(50) | UNIQUE NOT NULL |
| name | VARCHAR(255) | NOT NULL |
| type | VARCHAR(20) | NOT NULL — `LOJA_FISICA` \| `ECOMMERCE` (`@Enumerated(STRING)`) |
| active | BOOLEAN | NOT NULL |

---

### StockBalanceEntity — tabela `stock_balance`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| sku | VARCHAR(50) | NOT NULL |
| warehouse_id | BIGINT | FK → warehouse(id) ON DELETE CASCADE |
| quantity | NUMERIC(14,3) | NOT NULL DEFAULT 0 |
| version | BIGINT | NOT NULL DEFAULT 0 — `@Version`, locking otimista para as escritas concorrentes de `StockMovement` (`POST /estoque/movements`) |

Constraint única: `uk_stock_balance_sku_warehouse (sku, warehouse_id)` — um único registro de saldo por par SKU/depósito.
Índice: `idx_stock_balance_warehouse_id (warehouse_id)`.

`getStockBalance(sku, warehouseCode)` retorna saldo zero (sem persistir linha) quando ainda não existe registro para o par — inventário sem nenhuma movimentação começa em zero. `EstoqueService.adjustStock` faz o primeiro `save()` real (via `StockBalance.apply`), sob proteção do `@Version` acima: um conflito de escrita concorrente lança `ObjectOptimisticLockingFailureException`, traduzida pelo `GlobalExceptionHandler` para `409 STOCK_UPDATE_CONFLICT`.

---

### StockMovementEntity — tabela `stock_movement`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| sku | VARCHAR(50) | NOT NULL |
| warehouse_id | BIGINT | FK → warehouse(id) ON DELETE CASCADE |
| type | VARCHAR(10) | NOT NULL — `ENTRADA` \| `SAIDA` \| `AJUSTE` (`@Enumerated(STRING)`) |
| quantity | NUMERIC(14,3) | NOT NULL |
| reason | VARCHAR(255) | NOT NULL |
| username | VARCHAR(80) | NOT NULL — sempre o usuário autenticado (JWT), nunca informado pelo cliente da API |
| created_at | TIMESTAMP | NOT NULL |

Índice composto: `idx_stock_movement_sku_warehouse_created (sku, warehouse_id, created_at)` — suporta o
histórico paginado (`StockMovementRepository.findBySkuAndWarehouseId`, mais recentes primeiro),
ainda sem endpoint HTTP consumindo-o nesta sprint.

`EstoqueService.adjustStock` grava a `StockMovementEntity` e atualiza `StockBalanceEntity` na
mesma transação (`@Transactional`) — a movimentação nunca é persistida sem o saldo refletir a
mudança, e vice-versa.

---

### CustomerEntity — tabela `customers`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| nome | VARCHAR(255) | NOT NULL |
| contato | VARCHAR(30) | NOT NULL |
| email | VARCHAR(255) | UNIQUE NOT NULL |
| cpf | VARCHAR(11) | nullable |
| origem | VARCHAR(100) | nullable |
| cadastrado_em | TIMESTAMP | NOT NULL |
| estagio | VARCHAR(20) | NOT NULL DEFAULT 'NOVO_LEAD' — `@Enumerated(STRING)`, estágio manual do Kanban de atendimento (`crm/kanban-segmentacao`), independente do segmento RFM |

Fundação do módulo CRM (`crm/cadastro-cliente`). Histórico de pedidos e cashback ainda não têm domínio de origem implementado no backend (endpoints correspondentes retornam placeholder vazio).

---

### CustomerNoteEntity — tabela `customer_notes`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| customer_id | BIGINT | FK → customers(id) ON DELETE CASCADE |
| autor | VARCHAR(80) | NOT NULL |
| texto | TEXT | NOT NULL |
| criado_em | TIMESTAMP | NOT NULL |

Índice: `idx_customer_notes_customer_id (customer_id)`. Sem relação JPA `@ManyToOne` com `CustomerEntity` — `customer_id` é uma coluna simples com FK apenas no schema, seguindo o mesmo padrão de `StockBalanceEntity.warehouseId` (evita carregar o cliente inteiro só para gravar uma nota).

---

### StageTransitionEntity — tabela `customer_stage_transitions`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| customer_id | BIGINT | FK → customers(id) ON DELETE CASCADE |
| de | VARCHAR(20) | NOT NULL — `@Enumerated(STRING)` |
| para | VARCHAR(20) | NOT NULL — `@Enumerated(STRING)` |
| autor | VARCHAR(80) | NOT NULL |
| transicionado_em | TIMESTAMP | NOT NULL |

Índice: `idx_customer_stage_transitions_customer_id (customer_id)`. Trilha de auditoria imutável — cada `PATCH /crm/customers/{id}/estagio` grava uma linha nova, nunca atualiza. `de == para` é rejeitado no domínio (`StageTransition`), então toda linha representa uma mudança real de estágio.

---

### TagEntity — tabela `tags`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| nome | VARCHAR(50) | UNIQUE NOT NULL |

---

### CustomerTagEntity — tabela `customer_tags`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| customer_id | BIGINT | PK (composta), FK → customers(id) ON DELETE CASCADE |
| tag_id | BIGINT | PK (composta), FK → tags(id) ON DELETE CASCADE |

Tabela de junção pura (muitos-para-muitos), sem coluna própria além das duas FKs — chave primária composta `(customer_id, tag_id)` evita duplicidade de associação no próprio schema. `CustomerTagRepositoryImpl.associate()` também verifica `existsByCustomerIdAndTagId` antes de inserir, tornando a operação idempotente mesmo sem depender só da constraint do banco. Índice: `idx_customer_tags_tag_id (tag_id)` — cobre a contagem de clientes por tag (`GET /crm/tags`) e a busca reversa. `TagJpaRepository.findAllWithCustomerCount()` e `CustomerTagJpaRepository.findTagsByCustomerId()` usam JOIN JPQL explícito entre `TagEntity`/`CustomerTagEntity` (entidades sem relação `@ManyToMany` mapeada — mesma escolha de simplicidade de `CustomerNoteEntity.customerId`).

---

### CampaignAutomationEntity — tabela `campaign_automations`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| nome | VARCHAR(100) | NOT NULL |
| gatilho | VARCHAR(20) | NOT NULL — `@Enumerated(STRING)`, `MANUAL` \| `ENTRADA_ESTAGIO` |
| segmento_alvo | VARCHAR(20) | NOT NULL — `@Enumerated(STRING)`, um `CustomerStage` (Kanban) |
| canal | VARCHAR(10) | NOT NULL — `@Enumerated(STRING)`, `WHATSAPP` \| `EMAIL` \| `AMBOS` |
| template | TEXT | NOT NULL |
| ativa | BOOLEAN | NOT NULL DEFAULT TRUE |
| criado_em | TIMESTAMP | NOT NULL |

`segmento_alvo` referencia `CustomerStage`, não o segmento RFM (placeholder) — decisão de escopo tomada com o usuário (ver `crm/automacoes-campanhas`), já que o segmento RFM é sempre `"NOVO"` e seria inútil como filtro de público-alvo.

---

### CampaignLogEntryEntity — tabela `campaign_log`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| automation_id | BIGINT | FK → campaign_automations(id) ON DELETE CASCADE |
| customer_id | BIGINT | FK → customers(id) ON DELETE CASCADE |
| status | VARCHAR(30) | NOT NULL — `@Enumerated(STRING)`, único valor hoje: `PENDENTE_INTEGRACAO` |
| disparado_em | TIMESTAMP | NOT NULL |
| convertido_em | TIMESTAMP | nullable — sempre `NULL` nesta versão, reservado para quando o domínio de pedidos existir |

Índice: `idx_campaign_log_automation_id (automation_id)`. Uma linha por cliente-alvo a cada `POST /crm/automacoes/{id}/disparar` — nenhum envio real ocorre; `crm/integracao-canal-envio` (F008) entregou apenas o status de conexão dos canais (`GET /crm/canais/status`), não o envio de mensagens em si.

---

### OpenPackageEntity — tabela `open_package` (V124, EST-F027)

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| sku | VARCHAR(50) | NOT NULL — **sem FK** para `product`, mesma decisão de `stock_balance`/`stock_movement` (EST-C011): pode ser SKU de variação, que vive em outra tabela |
| warehouse_id | BIGINT | NOT NULL, FK → `warehouse (id)` |
| uses | INTEGER | NOT NULL DEFAULT 0 — `CHECK (uses >= 0 AND uses <= sessions_per_unit)` |
| sessions_per_unit | INTEGER | NOT NULL, `CHECK (> 0)` — **cópia** de `product.sessions_per_unit` no momento da abertura, não leitura viva |
| opened_at | TIMESTAMP | NOT NULL |
| opened_by | VARCHAR(100) | NOT NULL — sempre do JWT |
| closed_at | TIMESTAMP | nullable |
| close_reason | VARCHAR(20) | nullable — `EXHAUSTED` \| `REPLACED` |

Índice único **parcial** `uk_open_package_sku_warehouse_open (sku, warehouse_id) WHERE closed_at IS NULL`:
uma lata em uso por par, e não `UNIQUE` simples porque a tabela é **histórico** e guarda todas as
latas já fechadas do mesmo par — uma constraint simples proibiria a segunda. Molde dos índices
parciais da V75. Mais `idx_open_package_warehouse_open (warehouse_id) WHERE closed_at IS NULL` para a
leitura da tela.

`CHECK ((closed_at IS NULL) = (close_reason IS NULL))` — fechada e sem motivo, ou o inverso, é meio
estado. O compact constructor de `OpenPackage` recusa os dois; o banco recusa junto, para carga
direta não abrir a exceção.

**A unidade sai de `stock_balance` na abertura da lata**, não a cada sessão: o saldo passa a
significar *latas lacradas na prateleira*, que é o que o operador conta no balanço, e o consumo de
dentro da lata é o contador desta tabela. `comanda_item.package_uses`/`package_sessions_per_unit`
(mesma migration) guardam **qual uso** cada linha da comanda foi — snapshot, não FK para
`open_package.id`, para o histórico sobreviver à reposição e para o cancelamento saber, meses
depois, que a linha consumiu uso e não unidade.

Sem `@Version`: a escrita é sempre dentro da transação de `addItem`/`replaceOpenPackage`, que já
serializa a mesa por trava pessimista da comanda (PDV-C008), e o índice único parcial barra a
segunda lata aberta do mesmo par.

---

### OrderPaymentCorrectionEntity — tabela `order_payment_correction` (V136, PDV-F030)

Correção da forma de pagamento de um pedido: quem, quando, por quê e em que caixa. Nada é apagado —
as linhas `CAPTURED` vigentes de `order_payment` passam a `CORRECTED` e as novas nascem `CAPTURED`.

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGSERIAL | PK |
| order_id | BIGINT | NOT NULL, FK → `sales_order (id)` |
| reason | VARCHAR(500) | NOT NULL, `CHECK (length(trim(reason)) > 0)` |
| corrected_by | VARCHAR(80) | NOT NULL |
| corrected_at | TIMESTAMPTZ | NOT NULL |
| cash_session_id | BIGINT | NOT NULL, FK → `cash_register_session (id)` — o caixa do pedido |
| session_was_closed | BOOLEAN | NOT NULL — a correção aconteceu depois do fechamento |

Índice `idx_order_payment_correction_order (order_id, id)`.

**Colunas novas em `order_payment` (V136):** `correction_id` (a correção que **aposentou** a linha,
só em `CORRECTED`), `origin_correction_id` (a correção que **criou** a linha; nulo nas linhas da
venda), `corrected_at`, `corrected_by`. São duas referências porque uma linha pode nascer de uma
correção e ser aposentada por outra. CHECKs: `ck_order_payment_status` aceita `CORRECTED`;
`ck_order_payment_captured` exige `captured_at` em `CAPTURED` **e** `CORRECTED` (a captura original
continua sendo história); `ck_order_payment_corrected` amarra `CORRECTED` ⇔ `correction_id`,
`corrected_at` e `corrected_by` preenchidos.

### CashSessionAdjustmentEntity — tabela `cash_session_adjustment` (V136, PDV-F030)

Divergência por método deixada num caixa **já fechado** por uma correção posterior. O esperado
gravado no fechamento não é reescrito.

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGSERIAL | PK |
| session_id | BIGINT | NOT NULL, FK → `cash_register_session (id)` |
| order_id | BIGINT | NOT NULL, FK → `sales_order (id)` |
| correction_id | BIGINT | NOT NULL, FK → `order_payment_correction (id)` |
| method | VARCHAR(30) | NOT NULL |
| delta_amount | NUMERIC(14,2) | NOT NULL, `CHECK (delta_amount <> 0)` |
| created_by | VARCHAR(80) | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL |

Índice `idx_cash_session_adjustment_session (session_id)`. Ainda não há endpoint de leitura.

### CustomerCreditLimitEntity — tabela `customer_credit_limit` (V137, CRM-F010)

Limite de crédito do "Marcar" por cliente. **Tabela própria, e não coluna em `customers`**: o `PUT`
do cadastro regrava a ficha inteira e zeraria o limite. Sem linha = limite padrão
(`system_config` `pdv.on-account.default-credit-limit`, semeado `0`).

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| customer_id | BIGINT | PK, FK → `customers (id)` ON DELETE CASCADE |
| credit_limit | NUMERIC(14,2) | NOT NULL, `CHECK (credit_limit >= 0)` |
| updated_by | VARCHAR(80) | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

### CustomerReceivableEntity — tabela `customer_receivable` (V137, CRM-F010)

O marcado: a parte de uma venda que o cliente VIP paga depois.

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGSERIAL | PK |
| customer_id | BIGINT | NOT NULL, FK → `customers (id)` |
| order_id | BIGINT | NOT NULL, FK → `sales_order (id)`, **único** (`uk_customer_receivable_order`) — no máximo um marcado por pedido |
| comanda_id | BIGINT | nullable, FK → `comanda (id)` — quando marcado na mesa |
| session_id | BIGINT | NOT NULL, FK → `cash_register_session (id)` |
| amount | NUMERIC(14,2) | NOT NULL, `CHECK (amount > 0)` |
| amount_paid | NUMERIC(14,2) | NOT NULL DEFAULT 0, `CHECK (0 <= amount_paid <= amount)` |
| due_date | DATE | NOT NULL |
| status | VARCHAR(20) | NOT NULL, `ABERTO`/`PARCIAL`/`QUITADO`/`VENCIDO`/`CANCELADO` |
| created_at / created_by | TIMESTAMPTZ / VARCHAR(80) | NOT NULL |
| settled_at | TIMESTAMPTZ | `CHECK ((status = 'QUITADO') = (settled_at IS NOT NULL))` |
| cancel_reason / cancelled_by / cancelled_at | | `CHECK ((status = 'CANCELADO') = (cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL))` |
| version | BIGINT | NOT NULL DEFAULT 0 — `@Version` |

Índices `idx_customer_receivable_customer_status (customer_id, status)` e
`idx_customer_receivable_status_due (status, due_date)`.

### ReceivableItemEntity — tabela `receivable_item` (V137, CRM-F010)

Snapshot dos itens do pedido **inteiro** no momento do marcar, mesmo quando só parte foi marcada
("R$ 30 de R$ 80 marcados"): `receivable_id` (FK, ON DELETE CASCADE), `order_item_id` (sem FK),
`sku` (NOT NULL — entra em `ProductRepositoryImpl.SKU_COLUMNS` para a troca de SKU),
`product_name`, `quantity`, `subtotal`, `mode`. Índice `idx_receivable_item_receivable`.

### ReceivablePaymentBatchEntity / ReceivablePaymentEntity — tabelas `receivable_payment_batch` e `receivable_payment` (V137, CRM-F010)

A quitação entra no caixa de **quem recebe**, por `receivable_payment`, e **não** por
`order_payment`: ela não tem pedido próprio. Um recebimento no balcão (`batch`) pode abater vários
marcados; o troco é do lote (`change_amount ≥ 0`), não do marcado.

| `receivable_payment` | Tipo | Constraint |
|--------|------|-----------|
| receivable_id | BIGINT | NOT NULL, FK → `customer_receivable` |
| batch_id | BIGINT | NOT NULL, FK → `receivable_payment_batch` |
| customer_id | BIGINT | NOT NULL, FK → `customers` |
| amount | NUMERIC(14,2) | NOT NULL, `> 0` — já o abatido, líquido do troco |
| method | VARCHAR(30) | NOT NULL, `DINHEIRO`/`DEBITO`/`CREDITO`/`PIX` |
| installments | INTEGER | só `CREDITO`, 1–24 |
| channel / provider | VARCHAR(20) | como em `order_payment` (V134) |
| cash_session_id | BIGINT | NOT NULL, FK → `cash_register_session` |
| received_by / received_at | | NOT NULL |

Índices `idx_receivable_payment_receivable` e `idx_receivable_payment_session_method
(cash_session_id, method)` — este último é o que `payment-totals` lê (`receivableReceived`).

**Mudanças em `order_payment` (V137):** coluna `due_date DATE`; `method` aceita `MARCADO`; `status`
aceita `ON_ACCOUNT`; `ck_order_payment_on_account` amarra `MARCADO` ⇔ `ON_ACCOUNT` ⇔ `due_date`
preenchido. A linha `ON_ACCOUNT` nunca é capturada e fica fora de toda soma de caixa.

### Colunas novas em `comanda` (V138, PDV-F029)

`closed_by VARCHAR(80)` (quem fechou, finalizou ou cancelou; `system` na varredura automática) e
`cancel_reason VARCHAR(500)`. Índice `idx_comanda_status_closed_at (status, closed_at DESC)` para o
histórico. Ficam em `ComandaEntity`, **fora** do record de domínio `Comanda`: só o histórico os lê,
e são gravados por `ComandaRepository.recordClosing` na entidade gerenciada (não por `UPDATE` em
lote, que deixaria a cópia já carregada na transação sem o valor).

---

## Repositórios

Cada port OUT tem uma implementação `*RepositoryImpl` que:
1. Injeta a interface Spring Data JPA (`*JpaRepository`)
2. Injeta o converter de domínio ↔ entidade
3. Implementa os métodos do port

```
core/ports/out/*Repository
    ↑ implementado por
adapter/out/persistence/repository/*RepositoryImpl
    ↑ usa
adapter/out/persistence/repository/*JpaRepository  (Spring Data)
adapter/out/persistence/entity/*Entity
adapter/out/persistence/converter/*EntityConverter  (domínio ↔ entidade)
```

`UserEntityConverter`: converte entre `User` (domínio) e `UserEntity` (JPA), incluindo roles/permissions aninhados.

---

## Banco de dados por perfil

| Perfil | Banco | Migrations |
|--------|-------|-----------|
| `dev` | H2 in-memory | Schema criado automaticamente pelo JPA (sem Flyway) |
| `hml` | PostgreSQL | Flyway (`V1__init.sql` … `V43__`) |
| `prod` | PostgreSQL | Flyway (`V1__init.sql` … `V43__`) |

Em hml/prod não há seed automático — usuário admin deve ser criado via CLI (`create-admin`).

## Dimensionamento HikariCP x Tomcat (C012)

`spring.datasource.hikari.maximum-pool-size` (`application.properties`) tinha um único valor default (`10`, via `HIKARI_MAX_POOL_SIZE`) compartilhado por dev/hml/prod, e nenhum arquivo de propriedades definia `server.tomcat.threads.max` — ou seja, o Tomcat embutido rodava com o default do Spring Boot de **200 threads** contra um pool de **10 conexões** de banco: uma proporção de 20:1. Como a maioria dos endpoints é I/O-bound no banco (toda operação CRUD passa por uma query), sob pico de tráfego muito mais requisições HTTP concorrentes do que conexões disponíveis significa fila crescente esperando conexão em vez de rejeição rápida — degradação em cascata (latência sobe para todos, não só para quem excede a capacidade real).

### Fórmula de dimensionamento do HikariCP

A [fórmula usual da própria HikariCP](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing) para uma conexão por requisição (sem pooling de statements em cascata):

```
connections = ((core_count * 2) + effective_spindle_count)
```

- `core_count` — núcleos de CPU disponíveis para o **banco** (não para a aplicação).
- `effective_spindle_count` — número de discos físicos independentes; **0** para SSD/NVMe (praticamente todo banco gerenciado hoje — RDS, Cloud SQL, etc. — usa SSD), já que não há penalidade de seek.

Para um Postgres gerenciado em SSD com, por exemplo, 2 vCPUs: `(2 * 2) + 0 = 4` conexões já seriam suficientes para saturar a capacidade de processamento paralelo do banco — pools muito maiores que isso não aumentam throughput, só competem por CPU/locks no lado do banco e consomem memória (cada conexão Postgres tem overhead de processo/memória próprio).

**Este projeto não tem, no código, visibilidade sobre o dimensionamento real da instância de banco em produção** (`docker-compose.prod.yml` só define o container `postgres`, sem requests/limits de CPU explícitos) — por isso o default `HIKARI_MAX_POOL_SIZE=10` permanece como ponto de partida conservador (cobre até ~3 vCPUs pela fórmula acima com folga), não uma calibração real. **Ação recomendada:** assim que o dimensionamento real da instância de produção for conhecido (vCPUs do banco gerenciado), recalcular via a fórmula acima e definir `HIKARI_MAX_POOL_SIZE` explicitamente no ambiente de deploy.

### Tomcat — `server.tomcat.threads.max`

Diferente do HikariCP, o pool de threads HTTP do Tomcat não precisa ser dimensionado só em função do banco — outros endpoints não tocam o banco (ex.: `/actuator/health`, `/system/config/public`) e podem ser atendidos mesmo com o pool de conexões saturado. Ainda assim, manter o default implícito de 200 threads sem nenhuma relação documentada com os 10 do HikariCP deixava a proporção 20:1 acidental, não uma decisão.

`server.tomcat.threads.max=${TOMCAT_MAX_THREADS:50}` foi definido explicitamente em `application-hml.properties` e `application-prod.properties` (C012) — proporção 5:1 com o pool HikariCP padrão, uma folga que ainda absorve picos de requisições não-DB-bound sem deixar a proporção tão desalinhada quanto o default anterior. Ajustável via `TOMCAT_MAX_THREADS` sem rebuild.

### Resumo — valores atuais

| Variável | Property | Default atual | Onde |
|----------|----------|----------------|------|
| `HIKARI_MAX_POOL_SIZE` | `spring.datasource.hikari.maximum-pool-size` | `10` (todos os perfis) | `application.properties` |
| `TOMCAT_MAX_THREADS` | `server.tomcat.threads.max` | `50` (hml/prod apenas — dev usa H2 embutido, sem necessidade de tuning) | `application-hml.properties`, `application-prod.properties` |

### Histórico completo de migrations (V1–V42)

#### V1–V12 — Schema base e segurança inicial

| Migration | Descrição |
|-----------|-----------|
| `V1__init.sql` | Cria tabelas `users` (id, username, password), `roles` e `user_roles`. Índices em `users(username)` e `roles(name)`. |
| `V2__refresh_tokens.sql` | Cria tabela `refresh_tokens` com `token_hash` (SHA-256), `expires_at`, `revoked`, `rotated_at`. Índices em `token_hash`, `user_id` e `expires_at`. |
| `V3__seed_admin_hml.sql` | Insere `ROLE_ADMIN` e `ROLE_USER` como dados base. **Não cria usuário admin** — o admin deve ser criado via CLI `create-admin`. O nome do arquivo é enganoso; as roles são pré-requisito para as migrations seguintes. |
| `V4__permissions.sql` | Cria tabelas `permissions` e `role_permissions`. Insere permissões base (`USER_CREATE`, `USER_READ`, `USER_DELETE`, `USER_ROLE_ASSIGN`). `ROLE_ADMIN` recebe todas; `ROLE_USER` recebe apenas `USER_READ`. |
| `V5__remove_unused_user_update_permission.sql` | Remove `USER_UPDATE` (sem endpoint correspondente naquele momento). Revertida em V8. |
| `V6__add_user_enabled.sql` | Adiciona coluna `enabled BOOLEAN NOT NULL DEFAULT TRUE` em `users`. |
| `V7__add_user_status_permission.sql` | Insere permissão `USER_STATUS` para controle de enable/disable de conta, separando semanticamente de `USER_DELETE`. |
| `V8__add_user_update_permission.sql` | Readiciona `USER_UPDATE` após `PATCH /users/{id}` ser implementado. |
| `V9__add_role_and_permission_management_permissions.sql` | Insere permissões de gestão de RBAC: `ROLE_READ/CREATE/DELETE/MANAGE_PERMISSIONS` e `PERMISSION_READ/CREATE/DELETE`. Todas atribuídas ao `ROLE_ADMIN`. |
| `V10__email_verification.sql` | Adiciona `email` (VARCHAR 254) e `email_verified` em `users`. Cria tabela `email_verification_codes`. Índice parcial único em `users(email) WHERE email IS NOT NULL`. |
| `V11__shedlock.sql` | Cria tabela `shedlock` — exigida pelo ShedLock para coordenação de schedulers em múltiplas instâncias (k8s, ECS). |
| `V12__hash_email_verification_code.sql` | Aumenta coluna `code` para VARCHAR(64) para acomodar hash SHA-256 em base64url. Adiciona `sent_at` para cooldown de reenvio. |

#### V13–V29 — Features incrementais

| Migration | Descrição |
|-----------|-----------|
| `V13__refresh_token_device_info.sql` | Adiciona `ip_address` (VARCHAR 45) e `user_agent` (VARCHAR 512) em `refresh_tokens` — rastreia origem das sessões. |
| `V14__password_reset_tokens.sql` | Cria tabela `password_reset_tokens` com hash SHA-256, TTL e flag `used_at`. |
| `V15__pending_email.sql` | Adiciona `pending_email` em `users` — suporta o fluxo de troca de email com confirmação no novo endereço. |
| `V16__totp.sql` | Cria `totp_config`, `totp_backup_codes` e `totp_challenge_tokens`. |
| `V17__add_junction_indexes.sql` | Índices reversos nas tabelas de junção: `idx_rp_permission_id ON role_permissions(permission_id)` e `idx_ur_role_id ON user_roles(role_id)`. A PK composta cobre buscas pelo primeiro campo; estes índices cobrem buscas pelo segundo ("quais roles têm a permission X?", "quais users têm a role Y?"). |
| `V18__audit_logs.sql` | Cria tabela `audit_logs` com índices em username, action e timestamp. |
| `V19__audit_read_permission.sql` | Insere permissão `AUDIT_READ` e a atribui ao `ROLE_ADMIN`. ⚠️ Sem `ON CONFLICT DO NOTHING` (C018) — ver seção "Migrations de seed sem ON CONFLICT DO NOTHING". |
| `V20__audit_logs_range_index.sql` | Índice composto `(username, timestamp DESC)` para queries com filtro de usuário + período. |
| `V21__add_totp_config_created_at.sql` | Adiciona `created_at` em `totp_config` (para cleanup de setups pendentes por data). |
| `V22__add_user_avatar.sql` | Adiciona coluna `avatar_filename` em `users`. |
| `V23__add_user_created_at.sql` | Adiciona coluna `created_at` em `users`. |
| `V24__soft_delete_users.sql` | Adiciona coluna `deleted_at` em `users` (soft delete). |
| `V25__add_oauth_google.sql` | Adiciona `auth_provider` e `google_id` em `users`; remove NOT NULL de `password`. |
| `V26__normalize_email_lowercase.sql` | Normaliza emails existentes para `lowercase + strip`. **Não reversível** (transformação com perda de informação) e **sem batching real** — ver seção [Migrations de UPDATE em massa — batching e rollback](#migrations-de-update-em-massa--batching-e-rollback-c011) para o procedimento seguro de reexecução em bases grandes e a limitação de rollback (C011). |
| `V27__add_ttl_indexes.sql` | Índices em `expires_at` de `refresh_tokens`, `email_verification_codes` e `password_reset_tokens` — melhora performance dos schedulers de cleanup. |
| `V28__add_totp_challenge_ttl_index.sql` | Índice em `totp_challenge_tokens(expires_at)` para o scheduler de limpeza de challenges. |
| `V29__add_google_id_index.sql` | Índice parcial único `idx_users_google_id ON users(google_id) WHERE google_id IS NOT NULL` — otimiza lookup por `sub` do token Google. |
| `V30__dev_challenge_tokens.sql` | Cria tabela `dev_challenge_tokens` para os tokens de desafio DEV (duplo TOTP). Colunas: `id`, `username`, `token_hash` (SHA-256, único), `period_t` (período TOTP T), `expires_at`, `created_at`, `used_at`. Índices em `token_hash` (lookup) e `expires_at` (scheduler de cleanup). |
| `V31__add_users_enabled_index.sql` | Índice parcial `idx_users_enabled ON users(enabled) WHERE deleted_at IS NULL` — melhora queries de `GET /users?enabled=false` em tabelas grandes. |
| `V32__remove_orphaned_permissions_from_role_admin.sql` | Remove as permissões `ROLE_CREATE`, `ROLE_DELETE`, `PERMISSION_CREATE` e `PERMISSION_DELETE` de `ROLE_ADMIN`. Essas permissões foram atribuídas ao ROLE_ADMIN em V9, mas os endpoints correspondentes (`POST /roles`, `DELETE /roles`, `POST /permissions`, `DELETE /permissions`) exigem `DEV_ROLE_MANAGE`/`DEV_PERMISSION_MANAGE` — exclusivas de ROLE_DEV. Ter essas permissões em ROLE_ADMIN criava uma falsa expectativa (admin as via no banco mas recebia 403 ao tentar usá-las). |
| `V33__remove_unused_dev_permissions.sql` | Remove as permissões `DEV_LOGS_TECHNICAL`, `DEV_SYSTEM_CONFIG` e `DEV_DEBUG_ENDPOINTS` do banco. Criadas pelo `DevRoleBootstrapConfig` para uso futuro, mas nenhum endpoint as referenciava. Removidas também do bootstrap para não serem recriadas. |
| `V34__system_config.sql` | Cria tabela `system_config` com colunas `config_key` (UNIQUE NOT NULL), `config_value`, `updated_by` e `updated_at`. Insere 4 chaves iniciais (`auth.registration.enabled`, `auth.google.enabled`, `auth.google.register.enabled`, `auth.forgot-password.enabled`), todas com valor `true`. Usada pelo `SystemConfigController` (`GET/PUT /system/config`) para controle de feature flags em runtime sem restart. |
| `V35__add_totp_config_cleanup_index.sql` | Índice parcial `idx_totp_config_cleanup ON totp_config(enabled, created_at) WHERE enabled = false` — melhora o DELETE do `TotpPendingSetupCleanupService`, que filtra exatamente por `enabled=false AND created_at < :before`. Sem este índice o DELETE faz full scan conforme a tabela cresce. |
| `V36__add_dev_role_permissions.sql` | Insere no banco as permissões exclusivas de `ROLE_DEV`: `ROLE_CREATE`, `ROLE_DELETE`, `PERMISSION_CREATE`, `PERMISSION_DELETE`, `DEV_ROLE_MANAGE`, `DEV_PERMISSION_MANAGE` (via `ON CONFLICT DO NOTHING`). O `DevRoleBootstrapConfig` ainda as cria em código de forma idempotente; esta migration torna o schema explícito e rastreável no histórico Flyway. |
| `V37__cleanup_dev_role.sql` | Garante `ROLE_DEV` no schema independente do bootstrap (`INSERT … ON CONFLICT DO NOTHING`). Remove os grants de `ROLE_CREATE`, `ROLE_DELETE`, `PERMISSION_CREATE` e `PERMISSION_DELETE` de `ROLE_DEV` — essas permissões não são verificadas por nenhum `@PreAuthorize`; os endpoints usam `DEV_ROLE_MANAGE` e `DEV_PERMISSION_MANAGE`. |
| `V38__add_system_config_new_flags.sql` | Insere 4 novas chaves em `system_config` (idempotente via `ON CONFLICT DO NOTHING`): `security.maintenance.enabled` (modo manutenção — retorna 503 a todos exceto `/actuator/health/**`), `security.2fa.required` (força 2FA para todos os usuários no login), `module.audit-logs.enabled` (habilita/desabilita persistência de logs de auditoria), `module.roles.enabled` (habilita/desabilita os endpoints de RBAC `/roles` e `/permissions`). Valor padrão: `false` para maintenance e 2fa; `true` para os módulos. |
| `V39__remove_orphaned_permissions.sql` | Remove do banco as permissões `ROLE_CREATE`, `ROLE_DELETE`, `PERMISSION_CREATE` e `PERMISSION_DELETE`. Foram inseridas pela V36 como "dados de referência" mas nenhum `@PreAuthorize` as usa — os endpoints de RBAC verificam `DEV_ROLE_MANAGE` e `DEV_PERMISSION_MANAGE`. Mantê-las visíveis via `GET /permissions` criava falsa expectativa. V37 já havia removido os grants de `ROLE_DEV`; esta migration remove as próprias permissões. Remove também os grants residuais de `role_permissions` antes de deletar. |
| `V40__notifications.sql` | Cria a tabela `notifications` (módulo de notificações in-app). Campos: `id BIGSERIAL`, `username VARCHAR(80)`, `type VARCHAR(50)`, `title VARCHAR(255)`, `body TEXT`, `read_at TIMESTAMPTZ` (nullable — `NULL` = não lida), `created_at TIMESTAMPTZ`. Três índices: por `username` (listagem por usuário), por `(username, read_at)` (contagem de não-lidas e markAllAsRead), por `created_at` (cleanup por TTL via `NotificationCleanupService`). |
| `V41__notification_preferences.sql` | Cria a tabela `notification_preferences` com PK composta `(username, type)` e flags `in_app_enabled`/`email_enabled` (ambas `DEFAULT TRUE`). Linha ausente = preferências padrão (ambas ativas). Permite desativar notificação in-app e/ou email por tipo individualmente via `PUT /notifications/preferences/{type}`. |
| `V42__notification_indexes_and_fk.sql` | Adiciona índice `idx_notification_preferences_username ON notification_preferences(username)` — evita full table scan em `findByUsername`. Adiciona FK `fk_notifications_username → users(username) ON DELETE CASCADE` e `fk_notification_prefs_username → users(username) ON DELETE CASCADE` — garante integridade referencial e limpeza automática de notificações ao deletar usuário. |
| `V43__add_notification_read_at_index.sql` | Índice parcial `idx_notifications_read_at ON notifications(read_at) WHERE read_at IS NOT NULL` — melhora o DELETE do `NotificationCleanupService`, que filtra por `read_at IS NOT NULL AND read_at < :cutoff`. Sem este índice a query faz full scan conforme a tabela cresce. |
| `V44__estoque_product.sql` | Cria `product` (SKU pai), `product_variant` (SKU filho, FK → product ON DELETE CASCADE) e `product_attribute` (sem PK própria — `@ElementCollection`, FK → product_variant ON DELETE CASCADE). Índices em `product_variant(product_id)` e `product_attribute(variant_id)`. |
| `V45__estoque_product_permissions.sql` | Insere `ESTOQUE_PRODUCT_READ` e `ESTOQUE_PRODUCT_MANAGE`, atribuídas ao `ROLE_ADMIN`. Também adicionadas ao array `ADMIN_PERMISSIONS` do `DevRoleBootstrapConfig` para que `ROLE_DEV` as herde. ⚠️ Sem `ON CONFLICT DO NOTHING` (C018). |
| `V46__estoque_warehouse_stock_balance.sql` | Cria `warehouse` (código único, tipo `LOJA_FISICA`/`ECOMMERCE`) e `stock_balance` (saldo por SKU/depósito, FK → warehouse ON DELETE CASCADE, coluna `version` para locking otimista). Constraint única `(sku, warehouse_id)` — um único registro de saldo por par SKU/depósito. Índice em `stock_balance(warehouse_id)`. |
| `V47__estoque_warehouse_permissions.sql` | Insere `ESTOQUE_WAREHOUSE_READ` e `ESTOQUE_WAREHOUSE_MANAGE`, atribuídas ao `ROLE_ADMIN`. Também adicionadas ao array `ADMIN_PERMISSIONS` do `DevRoleBootstrapConfig` para que `ROLE_DEV` as herde. ⚠️ Sem `ON CONFLICT DO NOTHING` (C018). |
| `V48__crm_customer.sql` | Cria a tabela `customers` (nome, contato, email único, cpf opcional, origem opcional, cadastrado_em) — fundação do módulo CRM. Insere `CRM_CUSTOMER_READ` e `CRM_CUSTOMER_MANAGE`, atribuídas ao `ROLE_ADMIN` (`ON CONFLICT (name) DO NOTHING`). Também adicionadas ao array `ADMIN_PERMISSIONS` de `SeedConfig` e `DevRoleBootstrapConfig`. |
| `V49__crm_customer_notes.sql` | Cria a tabela `customer_notes` (customer_id FK → customers ON DELETE CASCADE, autor, texto, criado_em). Índice em `customer_id` para a consulta de notas por cliente. |
| `V50__crm_customer_stage.sql` | Adiciona coluna `estagio` em `customers` (default `NOVO_LEAD`) e cria a tabela `customer_stage_transitions` (trilha de auditoria de mudança de estágio). |
| `V51__crm_tags.sql` | Cria a tabela `tags` (nome único) e a tabela de junção `customer_tags` (customer_id + tag_id, PK composta, FK ON DELETE CASCADE para ambos). |
| `V52__crm_campaign_automations.sql` | Cria as tabelas `campaign_automations` (regra de automação/campanha) e `campaign_log` (log de disparos por cliente-alvo, FK ON DELETE CASCADE para ambos). |
| `V53__stub_controllers_read_permissions.sql` | Insere `COMPRAS_READ`, `ECOMMERCE_READ`, `FINANCEIRO_READ`, `LOGISTICA_READ`, `PDV_READ`, atribuídas ao `ROLE_ADMIN` (C004 — `@PreAuthorize` nos 5 controllers stub). Também adicionadas ao array `ADMIN_PERMISSIONS` de `SeedConfig` e `DevRoleBootstrapConfig`. |
| `V54__add_username_indices.sql` | Índices em `email_verification_codes(username)` e `password_reset_tokens(username)` — elimina full table scan em `findFirstByUsernameOrderBy...`/`deleteByUsername` (C010). |
| `V55__estoque_movement.sql` | Cria a tabela `stock_movement` (ledger auditável de entradas/saídas/ajustes, FK → warehouse ON DELETE CASCADE). Índice composto `(sku, warehouse_id, created_at)`. |
| `V56__estoque_movement_permissions.sql` | Insere `ESTOQUE_STOCK_MANAGE`, atribuída ao `ROLE_ADMIN` (com `ON CONFLICT DO NOTHING` — ao contrário de V45/V47, não repete o débito técnico C018). Também adicionada ao array `ADMIN_PERMISSIONS` de `SeedConfig` e `DevRoleBootstrapConfig`. |

---

## Migrations de UPDATE em massa — batching e rollback (C011)

`V26__normalize_email_lowercase.sql` já foi aplicada em todos os ambientes (Flyway não permite reeditar uma migration histórica), então esta seção documenta o procedimento correto — para reexecutar esse padrão numa base grande no futuro, e como checklist para as próximas migrations de UPDATE em massa.

### Batching seguro para UPDATE em massa em produção

Um `UPDATE` sem `WHERE` restritivo (ou com `WHERE` que ainda toca uma fração grande da tabela) mantém locks de linha durante toda a transação da migration — em uma tabela com muitos registros isso pode significar minutos de lock, bloqueando outras transações que tentem escrever nas mesmas linhas (ex.: login, troca de senha, criação de conta, todos escrevem em `users`).

Padrão recomendado — lotes por faixa de id, cada lote em sua própria transação, com um intervalo entre lotes para não saturar I/O:

```sql
-- Fora do Flyway (rodar manualmente antes de aplicar a migration em bases grandes),
-- ou como script de apoio referenciado pela migration:
DO $$
DECLARE
    batch_size INT := 5000;
    max_id BIGINT;
    current_id BIGINT := 0;
BEGIN
    SELECT MAX(id) INTO max_id FROM users;
    WHILE current_id < max_id LOOP
        UPDATE users
        SET email = LOWER(TRIM(email))
        WHERE id > current_id AND id <= current_id + batch_size
          AND email IS NOT NULL
          AND email <> LOWER(TRIM(email)); -- pula linhas já normalizadas (idempotente, permite retomar após falha)
        current_id := current_id + batch_size;
        COMMIT; -- cada lote é sua própria transação — libera os locks entre lotes
        PERFORM pg_sleep(0.1); -- alivia I/O sob carga de produção real
    END LOOP;
END $$;
```

Pontos-chave do padrão:
- **Lotes por faixa de `id`** (não `OFFSET`/`LIMIT` — `OFFSET` em tabela mutável durante o loop pode pular ou repetir linhas).
- **Cada lote commita separadamente** — um `UPDATE` de 5k linhas prende locks por muito menos tempo que um de 5M.
- **Filtro de idempotência** (`email <> LOWER(TRIM(email))`) — permite interromper e retomar o script sem reprocessar linhas já normalizadas.
- **`pg_sleep` entre lotes** — evita saturar I/O/replicação em produção; ajustar o valor conforme o tamanho real da tabela e a janela de manutenção disponível.

### Plano de rollback de V26

**Normalizar para lowercase não é uma operação reversível sem um backup do valor original.** `LOWER(TRIM(email))` é uma transformação com perda de informação (não há como recuperar a capitalização original de `Joao@Example.com` a partir de `joao@example.com`) — isso deve ser documentado explicitamente em qualquer migration de normalização de dados, não assumido como "reversível porque é só um UPDATE".

Se for necessário reverter V26 (cenário hipotético: um bug downstream que dependia de emails case-sensitive):
1. **Não existe rollback via SQL a partir do estado pós-migration** — a informação original já foi perdida.
2. A única forma real de reverter é restaurar os valores de `users.email`/`users.pending_email` a partir de um **backup ou snapshot tirado antes da migration** (backup completo do banco, ou point-in-time recovery se o provedor de banco suportar).
3. Se nenhum backup pré-migration existir, a reversão é **impossível** — os dados originais estão perdidos permanentemente.

### Checklist para futuras migrations de UPDATE em massa

Antes de commitar uma migration Flyway que faz `UPDATE`/`DELETE` sem `WHERE id = :id` (ou equivalente pontual):

- [ ] A migration toca potencialmente **toda** a tabela, ou uma fração grande dela? Se sim, documentar o batching (ver padrão acima) — mesmo que a migration em si rode o UPDATE direto (aceitável em tabelas pequenas/nova feature), o comentário deve dizer explicitamente a partir de qual volume de linhas o padrão de batching precisa ser aplicado manualmente antes do deploy.
- [ ] A transformação é **reversível**? Se não (normalização, hashing, deleção), documentar essa limitação explicitamente no comentário da migration — não deixar implícito.
- [ ] Se reversível, existe uma migration de rollback ou um script documentado para reverter?
- [ ] O `WHERE` da query de UPDATE/DELETE é idempotente (pode rodar de novo sem duplicar efeito) para permitir retomar após falha no meio de um batch?

---

## Migrations de seed sem `ON CONFLICT DO NOTHING` (C018)

`V19__audit_read_permission.sql`, `V45__estoque_product_permissions.sql` e `V47__estoque_warehouse_permissions.sql` fazem `INSERT INTO permissions`/`INSERT INTO role_permissions` sem `ON CONFLICT DO NOTHING` — inconsistente com o padrão já adotado em `V3`, `V4`, `V9`, `V36`, `V37`, `V38` (todas usam `ON CONFLICT (name) DO NOTHING` no insert de permissions, e `ON CONFLICT DO NOTHING` no insert de role_permissions).

**Por que não foram editadas diretamente:** as três já foram aplicadas (Flyway valida checksum dos arquivos já aplicados contra o registrado em `flyway_schema_history` — editar um arquivo histórico quebra essa validação e trava o próximo boot com `spring.flyway.enabled=true`, a menos que `flyway repair` seja rodado antes, recalculando os checksums). Mesma situação do `V26` (ver seção acima, C011) — o perfil `dev` não é afetado (usa H2 com schema JPA automático, sem Flyway), mas hml/prod usam Flyway real.

**Risco concreto:** sem `ON CONFLICT DO NOTHING`, um `INSERT` que tente reinserir uma permissão já existente falha com violação de constraint única. Isso não acontece em uma sequência normal de deploy (cada migration roda uma única vez, em ordem), mas quebraria em um cenário de `flyway repair`/rebaseline manual onde essas migrations precisassem ser re-executadas contra um schema que já tem os dados (ex.: recuperação de um `flyway_schema_history` corrompido, ou um baseline manual mal calibrado).

**Se algum dia for necessário corrigir de fato:** confirmar primeiro que V19/V45/V47 nunca rodaram em nenhum ambiente real (só então é seguro editá-las diretamente); caso já tenham rodado em hml/prod, a correção exigiria coordenar um `flyway repair` como parte do deploy — fora do escopo de uma correção de código isolada.

### Checklist para futuras migrations de seed (INSERT de dados de referência)

- [ ] `INSERT INTO permissions (name) VALUES (...)` sempre com `ON CONFLICT (name) DO NOTHING`
- [ ] `INSERT INTO role_permissions (...) SELECT ...` sempre com `ON CONFLICT DO NOTHING`
- [ ] Isso vale mesmo quando a migration é "nova" e o dado "obviamente" ainda não existe — o ponto não é a primeira execução, é permitir retry seguro depois de uma falha parcial ou um rebaseline

---

## Estratégia de paginação

### Problema: N+1 em relações M2M com paginação

`UserEntity` e `RoleEntity` têm relações `@ManyToMany(fetch = FetchType.LAZY)`. Numa paginação ingênua com `findAll(Pageable)`, o Spring Data executa:

1. `SELECT * FROM users LIMIT ?` — retorna 20 usuários
2. Para cada usuário: `SELECT * FROM user_roles WHERE user_id = ?` — 20 queries extras
3. Para cada role: `SELECT * FROM role_permissions WHERE role_id = ?` — N queries adicionais

Resultado: 1 query visível no log, mas dezenas executadas em background.

### Padrão ID-first + JOIN FETCH

Todos os repositórios com relações M2M usam o mesmo padrão de duas fases:

**Fase 1 — Buscar apenas IDs com paginação**

```sql
SELECT u.id FROM users u ORDER BY u.id LIMIT ? OFFSET ?
```

Essa query retorna apenas `Long` — sem joins, sem lazy loading, sem overhead de coluna.

**Fase 2 — Carregar entidades completas com JOIN FETCH pelos IDs**

```sql
SELECT DISTINCT u FROM UserEntity u
  LEFT JOIN FETCH u.roles r
  LEFT JOIN FETCH r.permissions
  WHERE u.id IN (:ids)
  ORDER BY u.id
```

Um único `JOIN FETCH` traz usuários, roles e permissões em uma só query. O `DISTINCT` evita duplicatas do produto cartesiano.

**Resultado:** 2 queries no total, independente de quantos usuários ou relações existam na página.

### Onde é aplicado

| Repositório | Método |
|-------------|--------|
| `UserRepositoryImpl.findAll()` | `findAllIds()` → `findAllWithRolesByIdIn()` |
| `UserRepositoryImpl.findFiltered()` | `findFilteredIds()` (Criteria API) → `findAllWithRolesByIdIn()` |
| `RoleRepositoryImpl.findAll()` | `findAllIds()` → `findAllWithPermissionsByIdIn()` |
| `RoleRepositoryImpl.findByNameContaining()` | `findIdsByNameContaining()` → `findAllWithPermissionsByIdIn()` |
| `ProductRepositoryImpl.findAll()` | `findAllIds()` → `findAllByIdsWithVariants()` (JOIN FETCH em `variants` e `variants.attributes`) |
| `OrderRepositoryImpl.findAll()` / `findBySessionId()` | página resolvida sem fetch → `findAllByIdsWithItems()` (JOIN FETCH em `items`) — PED-C002. A fase 1 aqui é a própria consulta paginada (`Specification` ou método derivado), que já não tocava a coleção: só a fase 2 foi acrescentada |
| `ComandaRepositoryImpl.findOpen()` | `findOpenIds()` → `findAllByIdsWithItems()` (JOIN FETCH em `items`) — PDV-C009 |
| `ComandaRepositoryImpl.moveOpenItems()` | `UPDATE` de `comanda_item.comanda_id` (PDV-F016) — **não** passa pelo `save` do agregado. Reatribuir a FK preserva os ids das linhas; movê-las dentro do agregado destino criaria ids novos e quebraria `linked_item_id`, que é FK auto-referente (V114) |
| `StockCountRepositoryImpl.findByWarehouseId()` | `findIdsByWarehouseId()` → `findAllByIdsWithItems()` |
| `ComandaRepositoryImpl.findHistory()` | página por `Specification` (`closedAt desc, id desc`), sem tocar em `items` → `findAllByIdsWithItems()` — PDV-F029. Specification e não JPQL com `:from IS NULL`: `Instant` nulo como parâmetro vira `bytea` no Postgres |
| `OrderRepositoryImpl.findByComandaIds()` | `findIdsByComandaIdIn()` → `findAllByIdsWithItems()` — os pedidos MESA de uma página de comandas numa consulta (PDV-F029) |

### `findFiltered` e a Criteria API

`UserRepositoryImpl.findFiltered()` usa a Criteria API do JPA em vez de JPQL porque os filtros (`search`, `enabled`) são opcionais e o JPQL com `WHERE :param IS NULL OR campo = :param` causa erro de tipo no PostgreSQL quando o parâmetro é `null`. A Criteria API constrói os predicados dinamicamente apenas quando os filtros estão presentes.

`CustomerRepositoryImpl.findAll(search, page, size)` evita o mesmo problema de forma mais simples, sem Criteria API: quando `search` é nulo/vazio, delega a `customerJpaRepository.findAll(pageable)`; caso contrário, usa o método derivado `findByNomeContainingIgnoreCaseOrContatoContaining`. `Customer` não tem coleções filhas, então não há necessidade do padrão ID-first — a paginação direta de `CustomerEntity` não sofre do bug de `LIMIT`/`OFFSET` junto de `JOIN FETCH`.

`CustomerRepositoryImpl` também expõe agregações para o dashboard (`crm/dashboard-overview`, sem migration nova): `countAll()` delega a `JpaRepository.count()`; `countActive()` usa o método derivado `countByEstagioNot(INATIVO)`; `countByStage()` usa uma query JPQL `SELECT c.estagio, COUNT(c) FROM CustomerEntity c GROUP BY c.estagio`, convertendo o `List<Object[]>` retornado em `Map<CustomerStage, Long>` — estágios sem nenhum cliente simplesmente não aparecem no mapa.

`CrmService.getChannelStatus()` (`crm/integracao-canal-envio`, F008) não tem persistência própria — não há tabela nem migration nova. O status do canal `EMAIL` é derivado do bean `EmailPort` ativo no profile (`LoggingEmailAdapter`/`MailpitEmailAdapter`/`ResendEmailAdapter`, selecionados via `email.provider` em `EmailAdapterConfig`), e o canal `WHATSAPP` é uma constante desconectada em código — não uma consulta ao banco.

### Campos de ordenação permitidos

`GET /users?sortBy=createdAt&sortDir=desc` aceita somente campos da whitelist para evitar injeção de nome de coluna:

```
id | username | email | enabled | createdAt
```

Qualquer valor fora da lista faz o sort cair para `id ASC` (default seguro).

### Contratos de paginação nos domínios stub

Os ports de saída `SupplierRepository` (compras), `CashRegisterRepository` (PDV), `CartRepository` (ecommerce), `LedgerRepository` (financeiro) e `ShipmentRepository` (logística) expõem `PageResult<T> findAll(int page, int size)`, seguindo o mesmo contrato já usado por `ProductRepository`. Os use cases correspondentes (`ComprasUseCase.listSuppliers`, `PdvUseCase.listSessions`, `EcommerceUseCase.listCarts`, `FinanceiroUseCase.listCashFlow`, `LogisticaUseCase.listShipments`) e os controllers já aceitam `page`/`size` (`@Min(0)` e `@Min(1) @Max(100)` respectivamente).

Nenhum desses domínios tem adapter de persistência ainda — os services stub retornam `new PageResult<>(List.of(), page, size, 0, 0)`, ecoando `page`/`size` recebidos com conteúdo vazio. Quando os adapters JPA forem implementados, o contrato de API já paginado se mantém: basta seguir o padrão ID-first (ver acima) na implementação de `findAll`.

`WarehouseRepository.findAll()` é exceção deliberada ao contrato paginado: depósitos formam uma lista pequena e limitada (loja física × e-commerce, eventuais novas lojas) — paginar seria over-engineering. `StockBalanceRepository` não expõe `findAll` — a consulta é sempre pontual por `(sku, warehouseId)`.

---

## Schedulers de cleanup (`infra/scheduler/`)

Todos usam **ShedLock** para garantir execução em apenas uma instância.

| Scheduler | Cron padrão (property) | O que remove | ShedLock |
|-----------|------------------------|--------------|----------|
| `RefreshTokenCleanupService` | `0 0 3 * * *` (`refresh-token.cleanup.cron`) | Refresh tokens expirados ou revogados | lockAtMostFor PT55M |
| `PasswordResetTokenCleanupService` | `0 15 3 * * *` (`password-reset.cleanup.cron`) | Tokens de reset expirados | lockAtMostFor PT30M |
| `EmailVerificationCodeCleanupService` | `0 30 3 * * *` (`email-verification.cleanup.cron`) | Códigos de verificação expirados | lockAtMostFor PT30M |
| `TotpChallengeCleanupService` | `0 30 3 * * *` (`totp.challenge.cleanup.cron`) | Challenge tokens expirados | lockAtMostFor PT15M |
| `TotpPendingSetupCleanupService` | `0 45 3 * * *` (`totp.pending-setup.cleanup.cron`) | Configs TOTP não confirmadas > TTL | lockAtMostFor PT30M |
| `AuditLogCleanupService` | `0 45 3 * * *` (`audit.cleanup.cron`) | Audit logs mais antigos que `audit.retention-days` | lockAtMostFor PT55M |
| `DevChallengeCleanupService` | `0 45 3 * * *` (`dev.challenge.cleanup.cron`) | Dev challenge tokens (duplo TOTP) expirados | lockAtMostFor PT15M |
| `NotificationCleanupService` | `0 0 4 * * *` (`notification.cleanup.cron`) | Notificações lidas mais antigas que `notification.read.retention-days` | lockAtMostFor PT15M |
| `ReceivableOverdueJob` | `0 5 0 * * *` America/Sao_Paulo (`pdv.on-account.overdue.cron`) | Não remove: marca `VENCIDO` o marcado em aberto com prazo vencido (CRM-F010). A regra "quem tem vencido não marca" calcula o vencido na leitura e não depende dele | lockAtMostFor PT10M |

### Pool de threads dos jobs (C019)

Os 8 schedulers acima concentram-se entre 03:00–04:00, com **3 jobs no mesmo horário às 03:45** (`TotpPendingSetupCleanupService`, `AuditLogCleanupService`, `DevChallengeCleanupService`) e **2 às 03:30** (`EmailVerificationCodeCleanupService`, `TotpChallengeCleanupService`). Sem `spring.task.scheduling.pool.size` configurado, o Spring Boot usa o default de **1 thread** para todos os `@Scheduled` — os jobs do mesmo horário executam em fila no mesmo thread em vez de em paralelo, atrasando uns aos outros (o atraso de um DELETE lento em `audit_logs`, por exemplo, empurraria `devChallengeCleanup` e `totpPendingSetupCleanup` para depois do horário programado).

`spring.task.scheduling.pool.size=${SCHEDULER_POOL_SIZE:4}` (`application.properties`) resolve isso — 4 threads cobre o maior grupo concorrente (3 jobs às 03:45) com uma folga. ShedLock continua garantindo que cada job individual rode em **apenas uma instância** da aplicação (múltiplas instâncias, mesmo cron) — o pool de threads é sobre paralelismo **dentro** de uma instância entre jobs *diferentes* no mesmo horário, um problema ortogonal ao que o ShedLock resolve.
