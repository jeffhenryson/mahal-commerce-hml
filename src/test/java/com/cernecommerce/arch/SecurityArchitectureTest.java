package com.cernecommerce.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.Set;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * PLAT-C034 (docs/dominios/plataforma/README.md): {@code SecurityConfig} termina em
 * {@code anyRequest().authenticated()}, então qualquer método de controller sem
 * {@code @PreAuthorize} explícito é hoje alcançável por qualquer usuário autenticado — inócuo
 * enquanto "autenticado" só significa "funcionário". A partir da Fatia 8 (ROLE_CUSTOMER,
 * plano-pdv-marketplace.md §2.9), "autenticado" passa a incluir cliente final, e um endpoint de
 * operador que esqueça a anotação vira exposição real.
 *
 * As exceções abaixo são as mesmas já documentadas em
 * plataforma/README.md#endpoints-do-módulo--permissão: a família {@code /auth/**} (pública ou
 * autenticada-sem-permissão-de-negócio por natureza) e os endpoints "self-service", gateados por
 * identidade — o token já garante que só se vê o próprio recurso, não uma permissão de negócio.
 * O ramo {@code /shop/**} (Fatia 8) segue o mesmo raciocínio do {@code /auth}: {@code ShopController}
 * (cadastro, e desde ECM-F002, catálogo) é público por natureza. Carrinho/checkout (Fatia 9) usam
 * um controller PRÓPRIO e autenticado, {@code ShopAccountController} — SHOP_CART_OWN/SHOP_ORDER_OWN
 * via {@code @PreAuthorize} de verdade, propriedade resolvida no service — que deliberadamente NÃO
 * está nesta lista. {@code PaymentWebhookController} (ECM-F004, Fatia 10) é a única exceção nova
 * desde então: é o gateway de pagamento chamando, não um usuário — a defesa mora em
 * {@code PaymentWebhookService} reconsultando o gateway pela verdade, não em permissão.
 */
class SecurityArchitectureTest {

    private static final Set<String> PUBLIC_ROUTE_CONTROLLERS = Set.of(
            "AuthController", "OAuthController", "RegistrationController", "DevAuthController",
            "TotpController", "ShopController",
            // ECM-F008: montador de kit da vitrine, público como o catálogo (rotas em SecurityConfig).
            "ShopKitController",
            // ECM-F004 (Fatia 10): é o gateway de pagamento chamando, sem sessão de usuário. A
            // defesa mora dentro de PaymentWebhookService (payment_check sempre reconsulta a
            // verdade no gateway), não em @PreAuthorize — o InfinitePay nem assina o webhook.
            "PaymentWebhookController",
            // WhatsApp Cloud API: é a Meta chamando. GET confere hub.verify_token; POST exige a
            // assinatura X-Hub-Signature-256 com o app secret — a defesa mora no próprio controller.
            "WhatsappWebhookController",
            // Só serve o arquivo de imagem já publicado no catálogo, pelo mesmo motivo de
            // GET /shop/catalog ser público: a vitrine do marketplace renderiza a foto sem token.
            // O UPLOAD não está aqui — mora em EstoqueController#uploadProductImage, sob
            // ESTOQUE_PRODUCT_MANAGE, e continua coberto por esta regra.
            "ProductImageController");

    private static final Set<String> SELF_SERVICE_CONTROLLERS = Set.of(
            "NotificationController", "NotificationPreferenceController", "AvatarController",
            // POST /support/bug-reports: qualquer usuário autenticado pode reportar um bug sobre a
            // própria sessão — não há permissão de negócio a checar, mesmo raciocínio do
            // NotificationController (reportedBy vem de auth.getName(), não de input do cliente).
            "SupportController");

    // Exceções pontuais, método a método, dentro de um controller que por outro lado exige
    // permissão normalmente — "ClasseSimples#metodo". UserController#me/changeOwnPassword/
    // updateOwnProfile são self-service (identidade, não permissão); SystemConfigController#
    // getPublicConfig é GET /system/config/public, permitAll() em SecurityConfig.
    private static final Set<String> SELF_SERVICE_METHODS = Set.of(
            "UserController#me", "UserController#changeOwnPassword", "UserController#updateOwnProfile",
            "SystemConfigController#getPublicConfig");

    static JavaClasses classes;

    @BeforeAll
    static void loadClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.cernecommerce.adapter.in.controller");
    }

    @Test
    void every_operator_endpoint_declares_preauthorize() {
        // CanBeAnnotated.Predicates.annotatedWith(...) devolve DescribedPredicate<CanBeAnnotated>;
        // generics em Java não aceitam isso onde um DescribedPredicate<JavaMethod> é exigido,
        // mesmo JavaMethod implementando CanBeAnnotated — forSubtype() existe exatamente para
        // esse caso (JavaMethod só usa a parte de CanBeAnnotated do predicado).
        DescribedPredicate<JavaMethod> isHttpEndpoint =
                CanBeAnnotated.Predicates.annotatedWith(GetMapping.class)
                        .or(CanBeAnnotated.Predicates.annotatedWith(PostMapping.class))
                        .or(CanBeAnnotated.Predicates.annotatedWith(PutMapping.class))
                        .or(CanBeAnnotated.Predicates.annotatedWith(DeleteMapping.class))
                        .or(CanBeAnnotated.Predicates.annotatedWith(PatchMapping.class))
                        .forSubtype();

        DescribedPredicate<JavaMethod> isDocumentedException = new DescribedPredicate<>(
                "faz parte de uma exceção documentada (rota pública ou self-service)") {
            @Override
            public boolean test(JavaMethod method) {
                String owner = method.getOwner().getSimpleName();
                if (PUBLIC_ROUTE_CONTROLLERS.contains(owner) || SELF_SERVICE_CONTROLLERS.contains(owner)) {
                    return true;
                }
                return SELF_SERVICE_METHODS.contains(owner + "#" + method.getName());
            }
        };

        ArchRule rule = methods()
                .that(isHttpEndpoint).and(not(isDocumentedException))
                .should().beAnnotatedWith(PreAuthorize.class)
                .because("PLAT-C034: fora de /auth, /shop e dos endpoints self-service documentados, "
                        + "todo endpoint precisa declarar a permissão exigida explicitamente");

        rule.check(classes);
    }
}
