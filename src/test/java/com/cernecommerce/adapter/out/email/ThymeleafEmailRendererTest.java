package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.config.StoreProfile;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.ports.in.StoreProfileUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThymeleafEmailRendererTest {

    private static final StoreProfile PROFILE = new StoreProfile("Mahal Tabacaria", null, null, null,
            "Rua das Flores, 10", null, "Curitiba", "PR", null, "(41) 99999-0000", "mahaltabacaria", null,
            null, null);

    private static final OrderEmailView ORDER = new OrderEmailView("Maria", "#7", List.of(
            new OrderEmailView.Line("Essência Menta", new BigDecimal("2.000"), new BigDecimal("39.90"), new BigDecimal("79.80")),
            new OrderEmailView.Line("Carvão 1kg", new BigDecimal("0.500"), new BigDecimal("40.20"), new BigDecimal("20.10"))),
            new BigDecimal("99.90"), new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("94.90"));

    private StoreProfileUseCase storeProfile;
    private ThymeleafEmailRenderer renderer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        storeProfile = mock(StoreProfileUseCase.class);
        when(storeProfile.get()).thenReturn(PROFILE);
        ObjectProvider<StoreProfileUseCase> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(storeProfile);

        renderer = new ThymeleafEmailRenderer(engine, new EmailBranding(provider, "https://painel.mahal.com/"));
    }

    @Test
    void layout_usa_a_marca_de_dados_da_loja() {
        String html = renderer.render("verification-code", vars(
                "username", "alice", "code", "ABC123", "verifyUrl", "http://localhost/verify?code=ABC123", "ttlMinutes", 15L));

        assertThat(html).contains("Mahal Tabacaria");
        assertThat(html).contains("Rua das Flores, 10, Curitiba - PR");
        assertThat(html).contains("(41) 99999-0000");
        assertThat(html).contains("@mahaltabacaria");
        assertThat(html).doesNotContain("Cerne Commerce");
        assertThat(html).contains("alice").contains("ABC123").contains("http://localhost/verify?code=ABC123");
    }

    @Test
    void loja_sem_perfil_cai_no_nome_padrao() {
        when(storeProfile.get()).thenReturn(StoreProfile.empty());

        String html = renderer.render("welcome", vars("username", "bob", "appUrl", "https://painel.mahal.com/"));

        assertThat(html).contains(EmailBranding.DEFAULT_NAME);
        assertThat(html).contains("bob");
    }

    @Test
    void password_reset_mostra_o_ttl_recebido() {
        String html = renderer.render("password-reset", vars(
                "username", "bob", "resetLink", "http://localhost/reset?token=xyz", "ttlMinutes", 30L));

        assertThat(html).contains("http://localhost/reset?token=xyz");
        assertThat(html).contains("expira em <span>30</span> minutos");
    }

    @Test
    void security_alert_mostra_quando_ip_e_dispositivo() {
        String html = renderer.render("security-alert", vars(
                "username", "dave", "title", "Conta bloqueada", "message", "Múltiplas tentativas detectadas.",
                "footerMessage", "Entre em contato com o suporte.",
                "occurredAt", "05/10/2026 às 14:32", "ip", "203.0.113.10", "device", "Firefox"));

        assertThat(html).contains("Conta bloqueada").contains("Múltiplas tentativas detectadas.");
        assertThat(html).contains("05/10/2026 às 14:32").contains("203.0.113.10").contains("Firefox");
    }

    @Test
    void security_alert_sem_contexto_omite_o_bloco() {
        String html = renderer.render("security-alert", vars(
                "username", "dave", "title", "t", "message", "m", "footerMessage", "f"));

        assertThat(html).doesNotContain("Dispositivo").doesNotContain(">IP<");
    }

    @Test
    void security_alert_escapa_html_em_valores_maliciosos() {
        String html = renderer.render("security-alert", vars(
                "username", "<script>alert('xss')</script>", "title", "Test", "message", "Test", "footerMessage", "Test"));

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void order_confirmation_lista_itens_e_valores_em_reais() {
        String html = renderer.render("order-confirmation", vars("order", ORDER, "checkoutUrl", "http://checkout-url"));

        assertThat(html).contains("Maria").contains("#7");
        assertThat(html).contains("Essência Menta").contains("Carvão 1kg");
        assertThat(html).contains(">2<").contains(">0,5<");
        assertThat(html).containsPattern("R\\$[\\s\\u00a0]94,90");
        assertThat(html).containsPattern("- R\\$[\\s\\u00a0]5,00");
        assertThat(html).doesNotContain("Cashback usado");
        assertThat(html).contains("http://checkout-url");
    }

    @Test
    void order_status_update_traz_o_status_e_os_itens() {
        String html = renderer.render("order-status-update", vars("order", ORDER, "newStatusLabel", "Pagamento confirmado"));

        assertThat(html).contains("Pagamento confirmado").contains("Essência Menta");
    }

    @Test
    void order_cancellation_usa_texto_de_reembolso_quando_refunded() {
        String html = renderer.render("order-cancellation", vars(
                "order", ORDER, "reason", "Produto com defeito", "refunded", true));

        assertThat(html).contains("reembolsado").contains("Produto com defeito");
    }

    @Test
    void order_cancellation_sem_motivo_omite_o_bloco() {
        String html = renderer.render("order-cancellation", vars("order", ORDER, "reason", null, "refunded", false));

        assertThat(html).contains("cancelado");
        assertThat(html).doesNotContain("reembolsado").doesNotContain("Motivo").doesNotContain("null");
    }

    @Test
    void purchase_receipt_avisa_que_nao_e_fiscal() {
        String html = renderer.render("purchase-receipt", vars("order", ORDER));

        assertThat(html).contains("Obrigado pela sua compra").contains("não tem valor fiscal").contains("Essência Menta");
    }

    @Test
    void notification_renderiza_secoes_destaque_e_botao() {
        NotificationEmail email = NotificationEmail.builder("caixa.fechamento", "Caixa #3 fechado com diferença")
                .intro("O valor contado não bate com o esperado.")
                .tone(NotificationEmail.Tone.WARNING)
                .section("Conferência", List.of(NotificationEmail.Row.of("Esperado", "R$ 850,00"),
                        NotificationEmail.Row.highlighted("Diferença", "-R$ 10,00")))
                .action("Abrir PDV", "/app/pdv")
                .build();

        String html = renderer.render("notification", vars(
                "email", email, "accent", "#b45309", "actionUrl", renderer.appUrl(email.actionPath())));

        assertThat(html).contains("Caixa #3 fechado com diferença").contains("O valor contado não bate");
        assertThat(html).contains("Conferência").contains("R$ 850,00").contains("-R$ 10,00");
        assertThat(html).contains("font-weight:bold;color:#b45309");
        assertThat(html).contains("https://painel.mahal.com/app/pdv").contains("Abrir PDV");
    }

    @Test
    void email_change_confirmado_fala_com_o_novo_endereco() {
        String html = renderer.render("email-change", vars("username", "carol", "newEmail", "carol@novo.com", "confirmed", true));

        assertThat(html).contains("Este é o novo email da sua conta").contains("carol@novo.com");
        assertThat(html).doesNotContain("Foi solicitada a troca");
    }

    @Test
    void email_change_pedido_avisa_o_endereco_antigo() {
        String html = renderer.render("email-change", vars("username", "carol", "newEmail", "carol@novo.com"));

        assertThat(html).contains("Foi solicitada a troca").doesNotContain("Este é o novo email");
    }

    @Test
    void role_changed_template_renderiza_com_campos_corretos() {
        String html = renderer.render("role-changed", vars(
                "username", "eve", "title", "Papel atribuído", "message", "O papel ROLE_ADMIN foi atribuído à sua conta."));

        assertThat(html).contains("eve").contains("Papel atribuído").contains("ROLE_ADMIN");
    }

    private static Map<String, Object> vars(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
