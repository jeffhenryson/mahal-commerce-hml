package com.cernecommerce.infra.config;

import com.cernecommerce.core.ports.out.pedido.OrderItemRefundRepository;
import com.cernecommerce.core.ports.out.pedido.OrderItemExchangeRepository;
import com.cernecommerce.core.service.CashSessionActivityService;
import com.cernecommerce.core.ports.out.pdv.CashSessionActivityRepository;
import com.cernecommerce.core.ports.in.CashSessionActivityUseCase;
import com.cernecommerce.core.ports.in.ReceiptEmailUseCase;
import com.cernecommerce.core.ports.out.recebivel.CustomerCreditLimitRepository;
import com.cernecommerce.core.ports.out.recebivel.CustomerReceivableRepository;
import com.cernecommerce.core.service.ReceiptEmailService;
import com.cernecommerce.core.service.ReceivableService;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.in.AutomationDispatchUseCase;
import com.cernecommerce.core.ports.in.AutomationPlatformIntegrationUseCase;
import com.cernecommerce.core.ports.in.WhatsappIntegrationUseCase;
import com.cernecommerce.core.ports.out.event.AuditEventPublisherPort;
import com.cernecommerce.core.ports.out.notification.WhatsappPort;
import com.cernecommerce.core.service.AutomationDispatchService;
import com.cernecommerce.core.service.AutomationPlatformIntegrationService;
import com.cernecommerce.core.service.WhatsappIntegrationService;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.cernecommerce.core.ports.in.AuditLogsUseCase;
import com.cernecommerce.core.ports.in.AuthUseCase;
import com.cernecommerce.core.ports.in.AvatarUseCase;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.ProductImageUseCase;
import com.cernecommerce.core.ports.in.BugReportUseCase;
import com.cernecommerce.core.ports.in.NotificationPreferenceUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.OAuthLoginUseCase;
import com.cernecommerce.core.ports.in.PermissionUseCase;
import com.cernecommerce.core.ports.in.RoleUseCase;
import com.cernecommerce.core.ports.in.StatsUseCase;
import com.cernecommerce.core.ports.in.SystemConfigUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.core.ports.in.ShopUseCase;
import com.cernecommerce.core.ports.in.PaymentWebhookUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.token.AccessTokenPort;
import com.cernecommerce.core.ports.out.credential.CredentialVerifierPort;
import com.cernecommerce.core.ports.out.notification.ManagerNotificationPort;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.notification.EmailVerificationCodeRepository;
import com.cernecommerce.core.ports.out.notification.PasswordResetTokenRepository;
import com.cernecommerce.core.ports.out.ratelimit.LoginAttemptPort;
import com.cernecommerce.core.ports.out.credential.PasswordHashPort;
import com.cernecommerce.core.ports.out.role.PermissionRepository;
import com.cernecommerce.core.ports.out.token.RefreshTokenPort;
import com.cernecommerce.core.ports.out.role.RoleRepository;
import com.cernecommerce.core.ports.out.token.TokenBlocklistPort;
import com.cernecommerce.core.ports.out.twofa.DevChallengeRepository;
import com.cernecommerce.core.ports.out.twofa.TotpBackupCodeRepository;
import com.cernecommerce.core.ports.out.twofa.TotpChallengeTokenRepository;
import com.cernecommerce.core.ports.out.twofa.TotpConfigRepository;
import com.cernecommerce.core.ports.out.twofa.TotpEncryptionPort;
import com.cernecommerce.core.ports.out.twofa.TwoFactorAuthPort;
import com.cernecommerce.core.ports.in.TotpUseCase;
import com.cernecommerce.core.ports.out.oauth.GoogleTokenVerifierPort;
import com.cernecommerce.core.ports.out.user.UserAuthoritiesPort;
import com.cernecommerce.core.ports.out.user.UserCachePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import com.cernecommerce.core.ports.out.audit.AuditLogRepository;
import com.cernecommerce.core.ports.out.notification.NotificationPreferenceRepository;
import com.cernecommerce.core.ports.out.notification.NotificationRepository;
import com.cernecommerce.core.ports.out.support.BugReportRepository;
import com.cernecommerce.adapter.out.storage.LocalAvatarStorageAdapter;
import com.cernecommerce.adapter.out.storage.LocalProductImageStorageAdapter;
import com.cernecommerce.core.ports.out.storage.AvatarStoragePort;
import com.cernecommerce.core.ports.out.storage.ProductImageStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.cernecommerce.core.service.AuditLogsService;
import com.cernecommerce.core.service.AuthService;
import com.cernecommerce.core.service.AvatarService;
import com.cernecommerce.core.service.ProductImageService;
import com.cernecommerce.core.service.BugReportService;
import com.cernecommerce.core.service.NotificationPreferenceService;
import com.cernecommerce.core.service.NotificationService;
import com.cernecommerce.core.service.OAuthLoginService;
import com.cernecommerce.core.service.PermissionService;
import com.cernecommerce.core.service.RoleService;
import com.cernecommerce.core.service.StatsService;
import com.cernecommerce.core.service.SystemConfigService;
import com.cernecommerce.core.service.StoreProfileService;
import com.cernecommerce.core.service.EmailIntegrationService;
import com.cernecommerce.core.ports.in.EmailIntegrationUseCase;
import com.cernecommerce.core.ports.out.SecretCipherPort;
import com.cernecommerce.core.ports.out.notification.EmailSampleSenderPort;
import com.cernecommerce.core.ports.in.StoreProfileUseCase;
import com.cernecommerce.core.service.TotpService;
import com.cernecommerce.core.service.UserService;

// Domínios de negócio (esqueletos — sem dependências de port out ainda)
import com.cernecommerce.core.ports.in.OrderReportUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.in.AvisoOperadoresUseCase;
import com.cernecommerce.core.ports.in.PedidoAppUseCase;
import com.cernecommerce.core.ports.out.ecommerce.AtendimentoPedidoAppRepository;
import com.cernecommerce.core.service.AvisoOperadores;
import com.cernecommerce.core.service.AtendimentoPedidoAppRepositoryComEventos;
import com.cernecommerce.core.service.CentralPedidosEventos;
import com.cernecommerce.core.service.OrderRepositoryComEventos;
import com.cernecommerce.core.ports.in.CentralPedidosEventosUseCase;
import com.cernecommerce.adapter.out.persistence.repository.OrderRepositoryImpl;
import com.cernecommerce.adapter.out.persistence.repository.PedidoAppAtendimentoRepositoryImpl;
import com.cernecommerce.core.service.PedidoAppService;
import com.cernecommerce.core.service.AvisoClientePedidoApp;
import com.cernecommerce.core.service.CentralPedidosService;
import com.cernecommerce.core.ports.in.CentralPedidosUseCase;
import com.cernecommerce.core.ports.in.AvisoClientePedidoAppUseCase;
import com.cernecommerce.core.ports.out.ecommerce.CentralPedidosConfigRepository;
import com.cernecommerce.core.ports.out.ecommerce.CentralPedidosIndicadoresQuery;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.KitBuilderUseCase;
import com.cernecommerce.core.ports.out.estoque.AttributeTypeRepository;
import com.cernecommerce.core.ports.out.estoque.BrandRepository;
import com.cernecommerce.core.ports.out.estoque.CategoryRepository;
import com.cernecommerce.core.ports.out.estoque.KitTemplateRepository;
import com.cernecommerce.core.ports.out.estoque.KitComponentRepository;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import com.cernecommerce.core.ports.out.estoque.ReorderPointRepository;
import com.cernecommerce.core.ports.out.estoque.ReplenishmentListRepository;
import com.cernecommerce.core.ports.out.estoque.StockBalanceRepository;
import com.cernecommerce.core.ports.out.estoque.StockCountRepository;
import com.cernecommerce.core.ports.out.estoque.StockIntegrityRepository;
import com.cernecommerce.core.ports.out.estoque.StockLotRepository;
import com.cernecommerce.core.ports.out.estoque.OpenPackageRepository;
import com.cernecommerce.core.ports.out.estoque.SkuPackagingRepository;
import com.cernecommerce.core.ports.out.pdv.OfflineSaleRejectionRepository;
import com.cernecommerce.core.ports.in.OfflineSaleUseCase;
import com.cernecommerce.core.service.OfflineSaleService;
import com.cernecommerce.core.ports.out.estoque.StockMovementRepository;
import com.cernecommerce.core.ports.out.estoque.StockReservationRepository;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentCorrectionRepository;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.CashMovementRepository;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.pedido.OrderReportRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import com.cernecommerce.core.ports.in.ComprasUseCase;
import com.cernecommerce.core.ports.in.NfeImportUseCase;
import com.cernecommerce.core.ports.out.compras.GoodsReceiptRepository;
import com.cernecommerce.core.ports.out.compras.NfeImportRepository;
import com.cernecommerce.core.ports.out.compras.SupplierRepository;
import com.cernecommerce.core.ports.out.estoque.NfeXmlImportPort;
import com.cernecommerce.core.ports.out.storage.NfeImportStoragePort;
import com.cernecommerce.adapter.out.nfe.JdkDomNfeXmlImportAdapter;
import com.cernecommerce.adapter.out.storage.LocalNfeImportStorageAdapter;
import com.cernecommerce.core.ports.in.FinanceiroUseCase;
import com.cernecommerce.core.ports.in.EcommerceUseCase;
import com.cernecommerce.core.ports.out.ecommerce.CartRepository;
import com.cernecommerce.core.ports.out.ecommerce.PaymentGatewayPort;
import com.cernecommerce.core.ports.out.financeiro.LedgerRepository;
import com.cernecommerce.core.ports.in.LogisticaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.out.cashback.CashbackEntryRepository;
import com.cernecommerce.core.ports.out.cashback.CashbackRateRepository;
import com.cernecommerce.core.ports.out.crm.CampaignAutomationRepository;
import com.cernecommerce.core.ports.out.crm.CampaignLogRepository;
import com.cernecommerce.core.ports.out.crm.CampaignWebhookPort;
import com.cernecommerce.core.ports.out.crm.CustomerNoteRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.crm.CustomerTagRepository;
import com.cernecommerce.core.ports.out.crm.StageTransitionRepository;
import com.cernecommerce.core.ports.out.crm.TagRepository;
import com.cernecommerce.core.service.ComandaService;
import com.cernecommerce.core.service.SessionMenuService;
import com.cernecommerce.core.ports.out.pdv.SessionMenuRepository;
import com.cernecommerce.core.service.OrderReportService;
import com.cernecommerce.core.service.OrderService;
import com.cernecommerce.core.service.PdvService;
import com.cernecommerce.core.service.EstoqueService;
import com.cernecommerce.core.service.KitBuilderService;
import com.cernecommerce.core.service.ComprasService;
import com.cernecommerce.core.service.NfeImportService;
import com.cernecommerce.core.service.FinanceiroService;
import com.cernecommerce.core.service.EcommerceService;
import com.cernecommerce.core.service.ShopService;
import com.cernecommerce.core.service.PaymentWebhookService;
import com.cernecommerce.core.service.LogisticaService;
import com.cernecommerce.core.service.CampaignTemplateRenderer;
import com.cernecommerce.core.service.CrmService;
import com.cernecommerce.core.service.CashbackService;

import java.math.BigDecimal;
import java.time.Clock;
import java.nio.file.Path;
import java.time.Duration;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;

@Configuration
@EnableScheduling
@EnableAsync
@EnableCaching
class CoreBeanConfig {

    @Bean
    UserUseCase userUseCase(UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordHashPort passwordHashPort,
            RefreshTokenPort refreshTokenPort,
            TokenBlocklistPort tokenBlocklistPort,
            EmailPort emailPort,
            EmailVerificationCodeRepository verificationCodeRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            UserCachePort userCachePort,
            TotpConfigRepository totpConfigRepository,
            TotpBackupCodeRepository totpBackupCodeRepository,
            TotpChallengeTokenRepository totpChallengeTokenRepository,
            TwoFactorAuthPort twoFactorAuthPort,
            AvatarStoragePort avatarStoragePort,
            CustomerRepository customerRepository,
            @Value("${email.verification.ttl-minutes:15}") long verificationCodeTtlMinutes,
            @Value("${email.verification.resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${password-reset.ttl-minutes:15}") long passwordResetTtlMinutes,
            @Value("${password-reset.frontend-url:http://localhost:3000/reset-password}") String passwordResetFrontendUrl,
            @Value("${user.invite.ttl-hours:48}") long inviteTtlHours) {
        return new UserService(userRepository, roleRepository, passwordHashPort,
                refreshTokenPort, tokenBlocklistPort, emailPort,
                verificationCodeRepository, passwordResetTokenRepository, userCachePort,
                totpConfigRepository, totpBackupCodeRepository, totpChallengeTokenRepository,
                twoFactorAuthPort, avatarStoragePort, customerRepository, verificationCodeTtlMinutes,
                resendCooldownSeconds, passwordResetTtlMinutes, passwordResetFrontendUrl, inviteTtlHours);
    }

    @Bean
    TotpService totpService(TotpConfigRepository totpConfigRepository,
            TotpBackupCodeRepository totpBackupCodeRepository,
            TotpChallengeTokenRepository totpChallengeTokenRepository,
            DevChallengeRepository devChallengeRepository,
            TotpEncryptionPort totpEncryptionPort,
            PasswordHashPort passwordHashPort,
            UserRepository userRepository,
            @Value("${totp.app-name:Cerne Commerce}") String appName) {
        SecretGenerator secretGenerator = new DefaultSecretGenerator();
        CodeVerifier codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        return new TotpService(totpConfigRepository, totpBackupCodeRepository, totpChallengeTokenRepository,
                devChallengeRepository, totpEncryptionPort, passwordHashPort, userRepository,
                secretGenerator, codeVerifier, appName);
    }

    @Bean
    TotpUseCase totpUseCase(TotpService totpService) {
        return totpService;
    }

    @Bean
    AuthUseCase authUseCase(CredentialVerifierPort credentialVerifier,
            AccessTokenPort accessToken,
            RefreshTokenPort refreshToken,
            UserAuthoritiesPort userAuthorities,
            TokenBlocklistPort tokenBlocklist,
            LoginAttemptPort loginAttempt,
            TotpService totpService,
            SystemConfigPort systemConfigPort) {
        return new AuthService(credentialVerifier, accessToken, refreshToken,
                userAuthorities, tokenBlocklist, loginAttempt, totpService, systemConfigPort);
    }

    @Bean
    RoleUseCase roleUseCase(RoleRepository roleRepository, PermissionRepository permissionRepository,
            UserRepository userRepository, UserCachePort userCachePort) {
        return new RoleService(roleRepository, permissionRepository, userRepository, userCachePort);
    }

    @Bean
    PermissionUseCase permissionUseCase(PermissionRepository permissionRepository) {
        return new PermissionService(permissionRepository);
    }

    @Bean
    StatsUseCase statsUseCase(UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository) {
        return new StatsService(userRepository, roleRepository, permissionRepository);
    }

    // ── Domínios de negócio ─────────────────────────────────────────────────────
    // Estoque, compras, vendas-balcao e financeiro já têm adapters de saída próprios. Ecommerce
    // e logistica seguem como esqueletos, com wiring sem dependências até que os ports/out
    // correspondentes ganhem implementação.

    @Bean
    OrderUseCase orderUseCase(OrderRepository orderRepository, EstoqueUseCase estoqueUseCase,
            OrderPaymentRepository orderPaymentRepository, CashbackUseCase cashbackUseCase,
            CashRegisterRepository cashRegisterRepository,
            OrderPaymentCorrectionRepository orderPaymentCorrectionRepository,
            ReceivableUseCase receivableUseCase,
            // PED-F004 — as unidades já estornadas, que limitam o próximo estorno.
            OrderItemRefundRepository orderItemRefundRepository,
            // PED-F006 — histórico das trocas de item.
            OrderItemExchangeRepository orderItemExchangeRepository) {
        return new OrderService(orderRepository, estoqueUseCase, orderPaymentRepository, cashbackUseCase,
                cashRegisterRepository, orderPaymentCorrectionRepository, receivableUseCase,
                orderItemRefundRepository, orderItemExchangeRepository);
    }

    @Bean
    OrderReportUseCase orderReportUseCase(OrderReportRepository orderReportRepository) {
        return new OrderReportService(orderReportRepository);
    }

    // PdvService é exposto como bean CONCRETO (não só via a interface PdvUseCase) porque
    // ComandaService (PDV-F009) precisa chamar os métodos package-private de posse de sessão e
    // validação de pagamento sem duplicá-los — ver o javadoc de ComandaService. pdvUseCase() só
    // devolve a mesma instância sob a interface, para o resto do wiring continuar programando
    // contra o port como sempre.
    @Bean
    PdvService pdvService(CashRegisterRepository cashRegisterRepository,
            CashMovementRepository cashMovementRepository, OrderRepository orderRepository,
            OrderPaymentRepository orderPaymentRepository, EstoqueUseCase estoqueUseCase,
            CashbackUseCase cashbackUseCase,
            // PDV-C005 — só para barrar o fechamento de caixa com mesa aberta. É o port, e não o
            // ComandaService: aquele já depende deste, e a seta contrária fecharia um ciclo.
            ComandaRepository comandaRepository,
            // Teto de desconto por pedido (PDV-F004). Acima dele, 409 em vez de um segundo nível de
            // permissão — dois níveis só criariam a tentação de distribuir o maior. Migra para
            // system_config junto com o painel de configuração.
            @Value("${pdv.sale.max-discount-percent:10}") BigDecimal maxDiscountPercent,
            // CRM-F010 — o marcar na venda e a quitação no caixa de quem recebe.
            ReceivableUseCase receivableUseCase,
            // PDV-F043 — o fechamento recusa caixa com venda offline recusada esperando revisão.
            OfflineSaleRejectionRepository offlineSaleRejectionRepository,
            // ECM-F010 — a liquidação do pedido do app exige o aceite no caixa.
            AtendimentoPedidoAppRepository atendimentoPedidoAppRepository,
            // ECM-F013 — "pagamento confirmado" ao cliente do app na liquidação.
            AvisoClientePedidoAppUseCase avisoClientePedidoApp) {
        return new PdvService(cashRegisterRepository, cashMovementRepository, orderRepository,
                orderPaymentRepository, estoqueUseCase, cashbackUseCase, comandaRepository,
                maxDiscountPercent, Clock.systemUTC(), receivableUseCase, offlineSaleRejectionRepository,
                atendimentoPedidoAppRepository, avisoClientePedidoApp);
    }

    /** ECM-F013 — avisa o cliente do app de cada etapa do pedido: e-mail e notificação no app. */
    @Bean
    AvisoClientePedidoAppUseCase avisoClientePedidoApp(CrmUseCase crmUseCase, UserRepository userRepository,
            NotificationUseCase notificationUseCase,
            com.cernecommerce.core.ports.out.notification.NotificationSsePort ssePort, EmailPort emailPort,
            AfterCommitExecutor afterCommitExecutor) {
        return new AvisoClientePedidoApp(crmUseCase, userRepository, notificationUseCase, ssePort, emailPort,
                afterCommitExecutor);
    }

    /** ECM-F013 — configuração do recebimento e indicadores da Central de Pedidos. */
    @Bean
    CentralPedidosUseCase centralPedidosUseCase(CentralPedidosConfigRepository centralPedidosConfigRepository,
            CentralPedidosIndicadoresQuery centralPedidosIndicadoresQuery) {
        return new CentralPedidosService(centralPedidosConfigRepository, centralPedidosIndicadoresQuery,
                Clock.systemUTC());
    }

    /** ECM-F010 — aviso em tempo real a quem opera o PDV (notificação + SSE, depois do commit). */
    @Bean
    AvisoOperadoresUseCase avisoOperadores(NotificationUseCase notificationUseCase, UserRepository userRepository,
            com.cernecommerce.core.ports.out.notification.NotificationSsePort ssePort,
            AfterCommitExecutor afterCommitExecutor) {
        return new AvisoOperadores(notificationUseCase, userRepository, ssePort, afterCommitExecutor);
    }

    /** ECM-F012 — eventos em tempo real da Central de Pedidos (SSE, depois do commit). */
    @Bean
    CentralPedidosEventosUseCase centralPedidosEventos(UserRepository userRepository,
            com.cernecommerce.core.ports.out.notification.NotificationSsePort ssePort,
            AfterCommitExecutor afterCommitExecutor) {
        return new CentralPedidosEventos(userRepository, ssePort, afterCommitExecutor);
    }

    /**
     * ECM-F012 — todo save de pedido passa pela Central de Pedidos. {@code @Primary}: quem injeta
     * {@link OrderRepository} recebe o decorador; só ele conhece o adapter concreto.
     */
    @Bean
    @Primary
    OrderRepository orderRepositoryComEventos(OrderRepositoryImpl orderRepositoryImpl,
            CentralPedidosEventosUseCase centralPedidosEventos) {
        return new OrderRepositoryComEventos(orderRepositoryImpl, centralPedidosEventos);
    }

    /** ECM-F012 — idem para a etapa do atendimento do pedido do app. */
    @Bean
    @Primary
    AtendimentoPedidoAppRepository atendimentoPedidoAppRepositoryComEventos(
            PedidoAppAtendimentoRepositoryImpl atendimentoRepositoryImpl,
            CentralPedidosEventosUseCase centralPedidosEventos) {
        return new AtendimentoPedidoAppRepositoryComEventos(atendimentoRepositoryImpl, centralPedidosEventos);
    }

    /** ECM-F010/F011 — fila "Pedidos do app" do caixa e o prazo do dia. */
    @Bean
    PedidoAppUseCase pedidoAppUseCase(AtendimentoPedidoAppRepository atendimentoPedidoAppRepository,
            OrderRepository orderRepository, OrderUseCase orderUseCase, PdvService pdvService,
            CrmUseCase crmUseCase, AvisoClientePedidoAppUseCase avisoClientePedidoApp,
            CentralPedidosConfigRepository centralPedidosConfigRepository) {
        return new PedidoAppService(atendimentoPedidoAppRepository, orderRepository, orderUseCase, pdvService,
                crmUseCase, avisoClientePedidoApp, centralPedidosConfigRepository, Clock.systemUTC());
    }

    /** PDV-F047 — atividades do operador na sessão de caixa. */
    @Bean
    CashSessionActivityUseCase cashSessionActivityUseCase(CashRegisterRepository cashRegisterRepository,
            CashSessionActivityRepository cashSessionActivityRepository) {
        return new CashSessionActivityService(cashRegisterRepository, cashSessionActivityRepository);
    }

    /**
     * PDV-F043 — sincronização da fila offline e revisão das recusadas. Recebe o {@code PdvUseCase}
     * (o proxy transacional), e não o {@code PdvService}: cada venda do lote precisa da sua transação.
     */
    @Bean
    OfflineSaleUseCase offlineSaleUseCase(PdvUseCase pdvUseCase,
            OfflineSaleRejectionRepository offlineSaleRejectionRepository,
            AuditEventPublisherPort auditEventPublisherPort) {
        return new OfflineSaleService(pdvUseCase, offlineSaleRejectionRepository, auditEventPublisherPort);
    }

    @Bean
    ReceiptEmailUseCase receiptEmailUseCase(OrderRepository orderRepository, CustomerRepository customerRepository,
            EmailPort emailPort) {
        return new ReceiptEmailService(orderRepository, customerRepository, emailPort);
    }

    /** CRM-F010 — "Marcar": venda a prazo para cliente VIP. Não depende do PdvService (sem ciclo). */
    @Bean
    ReceivableUseCase receivableUseCase(CustomerReceivableRepository customerReceivableRepository,
            CustomerCreditLimitRepository customerCreditLimitRepository, CustomerRepository customerRepository,
            CustomerTagRepository customerTagRepository, OrderRepository orderRepository,
            CashRegisterRepository cashRegisterRepository, CashbackUseCase cashbackUseCase,
            SystemConfigPort systemConfigPort) {
        return new ReceivableService(customerReceivableRepository, customerCreditLimitRepository,
                customerRepository, customerTagRepository, orderRepository, cashRegisterRepository,
                cashbackUseCase, systemConfigPort, Clock.systemUTC());
    }

    @Bean
    PdvUseCase pdvUseCase(PdvService pdvService) {
        return pdvService;
    }

    @Bean
    ComandaUseCase comandaUseCase(ComandaRepository comandaRepository, EstoqueUseCase estoqueUseCase,
            OrderRepository orderRepository, OrderPaymentRepository orderPaymentRepository,
            CashbackUseCase cashbackUseCase, PdvService pdvService,
            // PDV-F013 — a varredura de mesa esquecida avisa quem pode agir sobre ela. Mesmo par
            // (canal + destinatários por permissão) que EstoqueService usa para o alerta de lote.
            NotificationUseCase notificationUseCase, UserRepository userRepository,
            // PDV-F015 — os 10% do garçom. Configuração e não constante porque é o costume do
            // salão, não uma lei; zero desliga a cobrança sem mexer em código. Migra para
            // system_config junto com o teto de desconto, quando o painel existir.
            @Value("${pdv.comanda.service-fee-percent:10}") BigDecimal serviceFeePercent,
            KitBuilderUseCase kitBuilderUseCase,
            // PDV-F021 — cardápio de sessão; a sessão por produto fica desligada por padrão e pode
            // ser religada por propriedade durante a transição, sem deploy de código.
            SessionMenuService sessionMenuService,
            @Value("${pdv.sessao.legacy-enabled:false}") boolean legacySessionEnabled,
            // PDV-F024 — a mesa é só sessão do cardápio; produto do catálogo vai pelo balcão.
            // Religável por propriedade, sem deploy, se a casa voltar a lançar produto na mesa.
            @Value("${pdv.mesa.catalog-items-enabled:false}") boolean catalogItemsEnabled,
            // PDV-F049 — essência só em texto na sessão. Ligada durante o rollout do catálogo do
            // Salão; desligar exige o SKU do sabor em toda sessão nova.
            @Value("${pdv.sessao.essencia-livre-permitida:true}") boolean freeTextEssenceAllowed) {
        return new ComandaService(comandaRepository, estoqueUseCase, orderRepository,
                orderPaymentRepository, cashbackUseCase, pdvService, notificationUseCase,
                userRepository, serviceFeePercent, kitBuilderUseCase, sessionMenuService, legacySessionEnabled,
                catalogItemsEnabled, freeTextEssenceAllowed);
    }

    /** Autoatendimento do Salão: QR por mesa, destaques do dia, mesa ocupada e a fila de solicitações do PDV. */
    @Bean
    com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase salaoAutoatendimentoUseCase(
            com.cernecommerce.core.ports.out.salao.SalaoAutoatendimentoRepository repository,
            EstoqueUseCase estoqueUseCase, WarehouseRepository warehouseRepository,
            SessionMenuService sessionMenuService,
            com.cernecommerce.core.ports.in.CrmUseCase crmUseCase, ComandaUseCase comandaUseCase,
            com.cernecommerce.core.ports.in.NotificationUseCase notificationUseCase, UserRepository userRepository,
            com.cernecommerce.core.ports.out.notification.NotificationSsePort ssePort,
            AfterCommitExecutor afterCommitExecutor, AuditEventPublisherPort auditEventPublisherPort,
            AuditLogRepository auditLogRepository,
            CentralPedidosConfigRepository centralPedidosConfigRepository) {
        return new com.cernecommerce.core.service.SalaoAutoatendimentoService(repository, estoqueUseCase,
                warehouseRepository, sessionMenuService, crmUseCase, comandaUseCase,
                notificationUseCase, userRepository, ssePort, afterCommitExecutor, auditEventPublisherPort,
                auditLogRepository, centralPedidosConfigRepository, Clock.systemUTC());
    }

    /** PDV-F021 — bean concreto: o ComandaService usa as regras de lançamento, como faz com o PdvService. */
    @Bean
    SessionMenuService sessionMenuService(SessionMenuRepository sessionMenuRepository) {
        return new SessionMenuService(sessionMenuRepository, Clock.systemUTC());
    }

    @Bean
    EstoqueUseCase estoqueUseCase(ProductRepository productRepository, WarehouseRepository warehouseRepository,
            StockBalanceRepository stockBalanceRepository, StockMovementRepository stockMovementRepository,
            ReorderPointRepository reorderPointRepository, StockIntegrityRepository stockIntegrityRepository,
            StockCountRepository stockCountRepository, StockReservationRepository stockReservationRepository,
            NotificationUseCase notificationUseCase, UserRepository userRepository,
            AfterCommitExecutor afterCommitExecutor,
            // 30 min cobre com folga a confirmação de PIX (segundos) e de cartão, sem deixar saldo
            // travado por engano quando o cliente abandona o checkout na tela de pagamento.
            @Value("${estoque.reservation.default-ttl:PT30M}") Duration defaultReservationTtl,
            KitComponentRepository kitComponentRepository, StockLotRepository stockLotRepository,
            SystemConfigPort systemConfigPort, CategoryRepository categoryRepository,
            BrandRepository brandRepository, AttributeTypeRepository attributeTypeRepository,
            ReplenishmentListRepository replenishmentListRepository,
            OpenPackageRepository openPackageRepository, ManagerNotificationPort managerNotificationPort,
            AuditEventPublisherPort auditEventPublisherPort, SkuPackagingRepository skuPackagingRepository,
            SessionMenuRepository sessionMenuRepository,
            // PDV-F052 — essência de sessão sem faixa recusada na ficha. Desligada até corrigir os
            // produtos antigos (SELECT sku FROM product WHERE session_product AND session_tier_id IS NULL).
            @Value("${estoque.produto.faixa-sessao-obrigatoria:false}") boolean sessionTierRequired) {
        return new EstoqueService(productRepository, warehouseRepository, stockBalanceRepository,
                stockMovementRepository, reorderPointRepository, stockIntegrityRepository,
                stockCountRepository, stockReservationRepository, notificationUseCase, userRepository,
                afterCommitExecutor, defaultReservationTtl, kitComponentRepository, stockLotRepository,
                systemConfigPort, categoryRepository, brandRepository, attributeTypeRepository,
                replenishmentListRepository, openPackageRepository, managerNotificationPort, auditEventPublisherPort,
                skuPackagingRepository, sessionMenuRepository, sessionTierRequired);
    }

    @Bean
    ComprasUseCase comprasUseCase(SupplierRepository supplierRepository, GoodsReceiptRepository goodsReceiptRepository,
            EstoqueUseCase estoqueUseCase) {
        return new ComprasService(supplierRepository, goodsReceiptRepository, estoqueUseCase);
    }

    // EST-F005 — importação de NF-e. Sem toggle S3 nesta primeira entrega (diferente de avatar/
    // imagem de produto): o XML não é ativo de vitrine, só precisa sobreviver em disco/volume
    // compartilhado para auditoria; o mesmo padrão de ProductImageS3StorageConfig pode ser
    // replicado aqui se algum dia isso importar.
    @Bean
    NfeImportStoragePort nfeImportStoragePort(
            @Value("${nfe-import.storage.dir:./data/nfe-imports}") String storageDir) {
        return new LocalNfeImportStorageAdapter(Path.of(storageDir));
    }

    @Bean
    NfeXmlImportPort nfeXmlImportPort(EstoqueUseCase estoqueUseCase) {
        return new JdkDomNfeXmlImportAdapter(estoqueUseCase);
    }

    @Bean
    NfeImportUseCase nfeImportUseCase(NfeXmlImportPort nfeXmlImportPort, SupplierRepository supplierRepository,
            NfeImportStoragePort nfeImportStoragePort, NfeImportRepository nfeImportRepository,
            ComprasUseCase comprasUseCase) {
        return new NfeImportService(nfeXmlImportPort, supplierRepository, nfeImportStoragePort,
                nfeImportRepository, comprasUseCase);
    }

    @Bean
    FinanceiroUseCase financeiroUseCase(LedgerRepository ledgerRepository) {
        return new FinanceiroService(ledgerRepository);
    }

    @Bean
    EcommerceUseCase ecommerceUseCase() {
        return new EcommerceService();
    }

    // EST-F031 — kit montável. Bean próprio, e não mais um método em EstoqueService: a regra do
    // pacote atravessa carrinho (ShopService) e comanda (ComandaService), e os dois dependem só
    // desta interface.
    @Bean
    KitBuilderUseCase kitBuilderUseCase(KitTemplateRepository kitTemplateRepository,
            CategoryRepository categoryRepository, EstoqueUseCase estoqueUseCase) {
        return new KitBuilderService(kitTemplateRepository, categoryRepository, estoqueUseCase);
    }

    @Bean
    ShopUseCase shopUseCase(CrmUseCase crmUseCase, UserUseCase userUseCase, EstoqueUseCase estoqueUseCase,
            CartRepository cartRepository, OrderRepository orderRepository, OrderUseCase orderUseCase,
            CashbackUseCase cashbackUseCase, PaymentGatewayPort paymentGatewayPort,
            OrderPaymentRepository orderPaymentRepository, EmailPort emailPort,
            KitBuilderUseCase kitBuilderUseCase, AfterCommitExecutor afterCommitExecutor,
            AtendimentoPedidoAppRepository atendimentoPedidoAppRepository, SystemConfigPort systemConfigPort,
            AvisoOperadoresUseCase avisoOperadores, CentralPedidosConfigRepository centralPedidosConfigRepository) {
        return new ShopService(crmUseCase, userUseCase, estoqueUseCase, cartRepository, orderRepository,
                orderUseCase, cashbackUseCase, paymentGatewayPort, orderPaymentRepository, emailPort,
                kitBuilderUseCase, afterCommitExecutor, atendimentoPedidoAppRepository, systemConfigPort,
                avisoOperadores, centralPedidosConfigRepository, Clock.systemUTC());
    }

    @Bean
    PaymentWebhookUseCase paymentWebhookUseCase(OrderRepository orderRepository,
            OrderPaymentRepository orderPaymentRepository, PaymentGatewayPort paymentGatewayPort,
            EstoqueUseCase estoqueUseCase, CashbackUseCase cashbackUseCase) {
        return new PaymentWebhookService(orderRepository, orderPaymentRepository, paymentGatewayPort,
                estoqueUseCase, cashbackUseCase);
    }

    @Bean
    LogisticaUseCase logisticaUseCase() {
        return new LogisticaService();
    }

    @Bean
    CashbackUseCase cashbackUseCase(CashbackRateRepository cashbackRateRepository,
            CashbackEntryRepository cashbackEntryRepository, EstoqueUseCase estoqueUseCase,
            CustomerRepository customerRepository, SystemConfigPort systemConfigPort,
            OrderPaymentRepository orderPaymentRepository) {
        return new CashbackService(cashbackRateRepository, cashbackEntryRepository, estoqueUseCase,
                customerRepository, systemConfigPort, orderPaymentRepository);
    }

    @Bean
    CrmUseCase crmUseCase(CustomerRepository customerRepository, CustomerNoteRepository customerNoteRepository,
            StageTransitionRepository stageTransitionRepository, TagRepository tagRepository,
            CustomerTagRepository customerTagRepository, CampaignAutomationRepository campaignAutomationRepository,
            CampaignLogRepository campaignLogRepository, EmailPort emailPort,
            AutomationDispatchUseCase automationDispatchUseCase, WhatsappIntegrationUseCase whatsappIntegrationUseCase) {
        return new CrmService(customerRepository, customerNoteRepository, stageTransitionRepository, tagRepository,
                customerTagRepository, campaignAutomationRepository, campaignLogRepository, emailPort,
                automationDispatchUseCase, whatsappIntegrationUseCase);
    }

    @Bean
    AutomationDispatchUseCase automationDispatchUseCase(CampaignAutomationRepository campaignAutomationRepository,
            CampaignLogRepository campaignLogRepository, CustomerRepository customerRepository,
            CustomerTagRepository customerTagRepository, CashbackUseCase cashbackUseCase,
            OrderRepository orderRepository, CampaignWebhookPort campaignWebhookPort, WhatsappPort whatsappPort,
            WhatsappIntegrationUseCase whatsappIntegrationUseCase,
            AutomationPlatformIntegrationUseCase automationPlatformIntegrationUseCase) {
        return new AutomationDispatchService(campaignAutomationRepository, campaignLogRepository, customerRepository,
                customerTagRepository, cashbackUseCase, orderRepository, campaignWebhookPort, whatsappPort,
                whatsappIntegrationUseCase, automationPlatformIntegrationUseCase, new CampaignTemplateRenderer());
    }

    @Bean
    OAuthLoginUseCase oAuthLoginUseCase(GoogleTokenVerifierPort tokenVerifier,
            UserRepository userRepository,
            AccessTokenPort accessToken,
            RefreshTokenPort refreshToken,
            UserAuthoritiesPort userAuthorities,
            UserCachePort userCachePort) {
        return new OAuthLoginService(tokenVerifier, userRepository,
                accessToken, refreshToken, userAuthorities, userCachePort);
    }

    @Bean
    AuditLogsUseCase auditLogsUseCase(AuditLogRepository auditLogRepository) {
        return new AuditLogsService(auditLogRepository);
    }

    @Bean
    NotificationUseCase notificationUseCase(NotificationRepository notificationRepository) {
        return new NotificationService(notificationRepository);
    }

    @Bean
    NotificationPreferenceUseCase notificationPreferenceUseCase(NotificationPreferenceRepository preferenceRepository) {
        return new NotificationPreferenceService(preferenceRepository);
    }

    @Bean
    BugReportUseCase bugReportUseCase(BugReportRepository bugReportRepository) {
        return new BugReportService(bugReportRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "avatar.storage.type", havingValue = "local", matchIfMissing = true)
    AvatarStoragePort avatarStoragePort(AvatarProperties avatarProps) {
        return new LocalAvatarStorageAdapter(Path.of(avatarProps.getStorageDir()));
    }

    @Bean
    SystemConfigUseCase systemConfigUseCase(SystemConfigPort configPort) {
        return new SystemConfigService(configPort);
    }

    @Bean
    StoreProfileUseCase storeProfileUseCase(SystemConfigPort configPort) {
        return new StoreProfileService(configPort);
    }

    @Bean
    EmailIntegrationUseCase emailIntegrationUseCase(SystemConfigPort configPort, SecretCipherPort secretCipherPort,
            EmailSampleSenderPort emailSampleSenderPort, ObjectProvider<EmailPort> emailPort) {
        return new EmailIntegrationService(configPort, secretCipherPort, emailSampleSenderPort,
                () -> emailPort.getObject().channelStatus());
    }

    @Bean
    WhatsappIntegrationUseCase whatsappIntegrationUseCase(SystemConfigPort configPort,
            SecretCipherPort secretCipherPort, WhatsappPort whatsappPort,
            @Value("${whatsapp.status-cache-ttl:5m}") Duration statusCacheTtl) {
        return new WhatsappIntegrationService(configPort, secretCipherPort, whatsappPort, Clock.systemUTC(),
                statusCacheTtl);
    }

    @Bean
    AutomationPlatformIntegrationUseCase automationPlatformIntegrationUseCase(SystemConfigPort configPort,
            SecretCipherPort secretCipherPort, CampaignWebhookPort campaignWebhookPort, Environment environment) {
        // http:// só fora de prod — em prod o token da plataforma iria em texto puro pela rede.
        boolean allowInsecureHttp = !environment.acceptsProfiles(Profiles.of("prod"));
        return new AutomationPlatformIntegrationService(configPort, secretCipherPort, campaignWebhookPort,
                allowInsecureHttp);
    }

    @Bean
    AvatarUseCase avatarUseCase(UserRepository userRepository,
            AvatarStoragePort storagePort,
            UserCachePort userCachePort,
            AvatarProperties avatarProps) {
        return new AvatarService(userRepository, storagePort, userCachePort,
                avatarProps.getMaxSizeBytes(), avatarProps.getBaseUrl());
    }

    // Imagem de produto tem configuração independente da de avatar — um pode estar em S3 e o
    // outro em disco. O bean S3 correspondente mora em ProductImageS3StorageConfig.
    @Bean
    @ConditionalOnProperty(name = "product.image.storage.type", havingValue = "local", matchIfMissing = true)
    ProductImageStoragePort productImageStoragePort(ProductImageProperties productImageProps) {
        return new LocalProductImageStorageAdapter(Path.of(productImageProps.getStorage().getDir()));
    }

    @Bean
    ProductImageUseCase productImageUseCase(ProductImageStoragePort productImageStoragePort,
            ProductImageProperties productImageProps) {
        return new ProductImageService(productImageStoragePort,
                productImageProps.getMaxSizeBytes(), productImageProps.getBaseUrl());
    }
}
