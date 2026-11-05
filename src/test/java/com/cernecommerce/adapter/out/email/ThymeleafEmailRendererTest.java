package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.config.StoreProfile;
import com.cernecommerce.core.domain.model.ecommerce.EmailEncerramentoPedido;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.OrderClosureEmail;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.OrderStageEmail;
import com.cernecommerce.core.domain.model.notification.PurchaseReceiptEmail;
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
            new BigDecimal("94.90"), 7L, false);

    private static final OrderEmailView DELIVERY_ORDER = new OrderEmailView("Maria Souza", "#8", ORDER.items(),
            new BigDecimal("99.90"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("99.90"), 8L, true);

    private static final PurchaseReceiptEmail RECEIPT = new PurchaseReceiptEmail("nº 000123", "R$ 94,90 em 04/11/2026.",
            List.of(new OrderStageEmail.Detail("Compra:", "nº 000123"), new OrderStageEmail.Detail("Data:", "04/11/2026 às 14:32")),
            List.of(new OrderStageEmail.Detail("Cartão de crédito (3x)", "R$ 94,90")),
            "você ganhou R$ 3,90 nesta compra. Fica disponível a partir de 11/11/2026.", false);

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

        renderer = new ThymeleafEmailRenderer(engine, new EmailBranding(provider, "https://painel.mahal.com/", "https://mahal.com/"));
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
    void convite_mostra_a_loja_e_o_papel() {
        String html = renderer.render("user-invite", vars(
                "username", "alice", "roles", "Atendente", "inviteLink", "http://front/convite?token=T", "ttlHours", 48L));

        assertThat(html).contains("<strong>Mahal Tabacaria</strong> como <strong>Atendente</strong>.");
        assertThat(html).contains("Criar minha senha").contains("http://front/convite?token=T");
        assertThat(html).contains("<span>48</span> horas").doesNotContain("convidado");
    }

    @Test
    void convite_sem_papel_nao_deixa_o_como_solto() {
        String html = renderer.render("user-invite", vars(
                "username", "alice", "inviteLink", "http://front", "ttlHours", 48L));

        assertThat(html).contains("<strong>Mahal Tabacaria</strong>. Seu usuário é").doesNotContain(" como ");
    }

    @Test
    void recuperacao_de_senha_avisa_validade_e_uso_unico() {
        String html = renderer.render("password-reset", vars(
                "username", "alice", "resetLink", "http://front/reset?token=T", "ttlMinutes", 15L));

        assertThat(html).contains("Redefina sua senha").contains("Criar nova senha").contains("http://front/reset?token=T");
        assertThat(html).contains("<span>15</span> minutos e só funciona uma vez");
        assertThat(html).contains("sua senha continua a mesma");
    }

    @Test
    void confirmacao_de_email_nao_fala_em_cadastro() {
        String html = renderer.render("verification-code", vars(
                "username", "alice", "code", "ABC123", "verifyUrl", "http://front/verify?code=ABC123", "ttlMinutes", 15L));

        assertThat(html).contains("Confirme seu e-mail").contains("<span>15</span> minutos");
        assertThat(html).doesNotContain("cadastro").doesNotContain("ativar sua conta");
    }

    @Test
    void troca_de_email_tem_link_sem_codigo_para_digitar() {
        String html = renderer.render("email-change-request", vars(
                "username", "alice", "confirmUrl", "http://front/troca?code=ABC123", "ttlMinutes", 15L));

        assertThat(html).contains("Confirme seu novo e-mail").contains("http://front/troca?code=ABC123");
        assertThat(html).contains("a conta continua como está");
        assertThat(html).doesNotContain("página de verificação");
    }

    @Test
    void loja_sem_perfil_cai_no_nome_padrao() {
        when(storeProfile.get()).thenReturn(StoreProfile.empty());

        String html = renderer.render("welcome", vars("username", "bob", "appUrl", "https://painel.mahal.com/"));

        assertThat(html).contains(EmailBranding.DEFAULT_NAME);
        assertThat(html).contains("bob");
    }

    @Test
    void layout_aplica_a_paleta_da_marca() {
        String html = renderer.render("welcome", vars("username", "alice", "appUrl", "http://app"));

        assertThat(html).contains(EmailTheme.DEFAULT.wine()).contains(EmailTheme.DEFAULT.cream());
        assertThat(html).contains("color-scheme");
        assertThat(html).doesNotContain("rem;");
    }

    @Test
    void acento_padrao_vira_filete_caramelo_e_acento_de_estado_se_mantem() {
        String padrao = renderer.render("welcome", vars("username", "alice"));
        String alerta = renderer.render("security-alert", vars(
                "username", "dave", "title", "t", "message", "m", "footerMessage", "f",
                "accent", EmailTheme.DEFAULT.danger()));

        assertThat(padrao).contains("line-height:0;background:" + EmailTheme.DEFAULT.caramel());
        assertThat(alerta).contains("line-height:0;background:" + EmailTheme.DEFAULT.danger());
    }

    @Test
    void preheader_so_aparece_quando_informado() {
        String com = renderer.render("welcome", vars("username", "alice", "preheader", "Sua conta está pronta para uso"));
        String sem = renderer.render("welcome", vars("username", "alice"));

        assertThat(com).contains("Sua conta está pronta para uso").contains("mso-hide:all");
        assertThat(sem).doesNotContain("mso-hide:all");
    }

    @Test
    void botao_sai_em_tabela_com_a_cor_da_marca() {
        String html = renderer.render("password-reset", vars(
                "username", "bob", "resetLink", "http://localhost/reset?token=xyz", "ttlMinutes", 30L));

        assertThat(html).contains("bgcolor=\"" + EmailTheme.DEFAULT.wine() + "\"").contains("Criar nova senha");
    }

    @Test
    void welcome_sem_url_omite_o_botao() {
        String html = renderer.render("welcome", vars("username", "alice"));

        assertThat(html).doesNotContain("Entrar no painel");
    }

    @Test
    void welcome_da_equipe_explica_como_entrar() {
        String html = renderer.render("welcome", vars("username", "alice", "appUrl", "http://painel/auth/login"));

        assertThat(html).contains("Entrar no painel").contains("http://painel/auth/login");
        assertThat(html).contains("a senha que a pessoa responsável passou para você");
        assertThat(html).doesNotContain("confirmações de pedido");
    }

    @Test
    void boas_vindas_do_cliente_leva_para_a_loja() {
        String html = renderer.render("customer-welcome", vars("firstName", "Maria", "shopUrl", "http://loja/"));

        assertThat(html).contains("E aí, <strong>Maria</strong>!");
        assertThat(html).contains("Ir para a loja").contains("http://loja/");
    }

    @Test
    void boas_vindas_do_cliente_sem_url_omite_o_botao() {
        String html = renderer.render("customer-welcome", vars("firstName", "Maria"));

        assertThat(html).doesNotContain("Ir para a loja");
    }

    @Test
    void confirmacao_na_retirada_orienta_pagar_no_caixa() {
        String html = renderer.render("order-confirmation", vars(
                "order", ORDER, "trackingUrl", "https://mahal.com/conta/pedidos/7"));

        assertThat(html).contains("Pedido #7 na área!").contains("E aí, <strong>Maria</strong>!");
        assertThat(html).contains("é só pagar no caixa quando vier retirar");
        assertThat(html).contains("Acompanhar pedido").contains("https://mahal.com/conta/pedidos/7");
        assertThat(html).doesNotContain("Assim que o pagamento for confirmado").doesNotContain("sem o frete");
    }

    @Test
    void confirmacao_na_entrega_avisa_que_o_frete_vem_depois() {
        String html = renderer.render("order-confirmation", vars("order", DELIVERY_ORDER));

        assertThat(html).contains("E aí, <strong>Maria</strong>!");
        assertThat(html).contains("calcula o frete").contains("pagar por Pix").contains("Total sem o frete");
        assertThat(html).doesNotContain("pagar no caixa");
    }

    @Test
    void confirmacao_com_pagamento_online_mostra_so_o_botao_de_pagar() {
        String html = renderer.render("order-confirmation", vars(
                "order", DELIVERY_ORDER, "checkoutUrl", "http://checkout-url", "trackingUrl", "https://mahal.com/x"));

        assertThat(html).contains("Falta só o pagamento").contains("Pagar agora").contains("http://checkout-url");
        assertThat(html).doesNotContain("Acompanhar pedido").doesNotContain("sem o frete");
    }

    @Test
    void confirmacao_sem_url_do_app_omite_o_botao() {
        String html = renderer.render("order-confirmation", vars("order", ORDER));

        assertThat(html).doesNotContain("Acompanhar pedido");
    }

    @Test
    void outros_emails_de_pedido_mantem_o_rotulo_total() {
        String html = renderer.render("purchase-receipt", vars("order", DELIVERY_ORDER, "receipt", RECEIPT));

        assertThat(html).contains(">Total<").doesNotContain("sem o frete");
    }

    @Test
    @SuppressWarnings("unchecked")
    void market_url_vazia_nao_gera_link() {
        EmailBranding semApp = new EmailBranding(mock(ObjectProvider.class), "https://painel.mahal.com", "");

        assertThat(semApp.marketUrl("/conta/pedidos/7")).isNull();
        assertThat(renderer.marketUrl("/conta/pedidos/7")).isEqualTo("https://mahal.com/conta/pedidos/7");
    }

    @Test
    void password_reset_mostra_o_ttl_recebido() {
        String html = renderer.render("password-reset", vars(
                "username", "bob", "resetLink", "http://localhost/reset?token=xyz", "ttlMinutes", 30L));

        assertThat(html).contains("http://localhost/reset?token=xyz");
        assertThat(html).contains("vale por <span>30</span> minutos");
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
    void security_alert_mostra_o_botao_e_o_aviso_com_rotulo() {
        String html = renderer.render("security-alert", vars(
                "username", "dave", "title", "t", "message", "m", "calloutLabel", "Não foi você?",
                "footerMessage", "Crie uma nova senha agora.", "accent", EmailTheme.DEFAULT.warning(),
                "actionUrl", "http://painel/auth/forgot-password", "actionLabel", "Recuperar minha conta"));

        assertThat(html).contains("<strong>Não foi você?</strong>").contains("Crie uma nova senha agora.");
        assertThat(html).contains("Recuperar minha conta").contains("http://painel/auth/forgot-password");
        assertThat(html).contains("line-height:0;background:" + EmailTheme.DEFAULT.warning());
    }

    @Test
    void security_alert_sem_url_omite_o_botao() {
        String html = renderer.render("security-alert", vars(
                "username", "dave", "title", "t", "message", "m", "calloutLabel", "l", "footerMessage", "f",
                "accent", EmailTheme.DEFAULT.warning(), "actionLabel", "Recuperar minha conta"));

        assertThat(html).doesNotContain("Recuperar minha conta");
    }

    @Test
    void security_alert_sem_contexto_omite_o_bloco() {
        String html = renderer.render("security-alert", vars(
                "username", "dave", "title", "t", "message", "m", "footerMessage", "f"));

        assertThat(html).doesNotContain("Aparelho").doesNotContain(">IP<");
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
    void order_status_update_com_pix_mostra_destaque_itens_e_botao() {
        OrderStageEmail stage = OrderStageEmail.builder("s", "p", "Pedido aceito! Falta só o Pix", "Faça o Pix.")
                .detail("Total a pagar:", "R$ 94,90").detail("Chave Pix:", "pix@mahal.com").items().tracking().build();

        String html = renderer.render("order-status-update", vars("order", ORDER, "stage", stage,
                "actionUrl", "https://mahal.com/conta/pedidos/7", "actionLabel", "Acompanhar pedido"));

        assertThat(html).contains("Pedido aceito! Falta só o Pix").contains("E aí, <strong>Maria</strong>!");
        assertThat(html).contains("Chave Pix:").contains("pix@mahal.com").contains("Essência Menta");
        assertThat(html).contains("Acompanhar pedido").doesNotContain("Onde retirar");
    }

    @Test
    void order_status_update_pronto_mostra_o_endereco_sem_os_itens() {
        OrderStageEmail stage = OrderStageEmail.builder("s", "p", "Pode vir buscar!", "Está separado.")
                .pickup().detail("Pagamento:", "no caixa").build();

        String html = renderer.render("order-status-update", vars("order", ORDER, "stage", stage));

        assertThat(html).contains("Onde retirar:").contains("Rua das Flores, 10, Curitiba - PR");
        assertThat(html).doesNotContain("Essência Menta").doesNotContain("Acompanhar");
    }

    @Test
    void order_status_update_sem_destaque_omite_a_caixa() {
        OrderStageEmail stage = OrderStageEmail.builder("s", "p", "Chegou!", "Entregue.").items().build();

        String html = renderer.render("order-status-update", vars("order", ORDER, "stage", stage));

        assertThat(html).doesNotContain("border-left:4px").contains("Essência Menta");
    }

    @Test
    void recusa_na_retirada_mostra_motivo_e_diz_que_nada_foi_cobrado() {
        String html = renderer.render("order-cancellation", vars("order", ORDER,
                "email", EmailEncerramentoPedido.recusado(ORDER, "Sem estoque"),
                "actionUrl", "https://mahal.com/", "actionLabel", "Fazer novo pedido"));

        assertThat(html).contains("Não conseguimos aceitar seu pedido").contains("Olá, <strong>Maria</strong>.");
        assertThat(html).contains("Motivo:").contains("Sem estoque").contains("Nenhum valor foi cobrado.");
        assertThat(html).contains("Fazer novo pedido").contains("Fale com a loja: <span>(41) 99999-0000</span>");
        assertThat(html).contains("line-height:0;background:" + EmailTheme.DEFAULT.danger());
    }

    @Test
    void cancelamento_na_entrega_orienta_quem_ja_fez_o_pix() {
        String html = renderer.render("order-cancellation", vars("order", DELIVERY_ORDER,
                "email", EmailEncerramentoPedido.expirado(DELIVERY_ORDER)));

        assertThat(html).contains("Se você já fez o Pix").doesNotContain("Nenhum valor foi cobrado");
    }

    @Test
    void cancelamento_com_cashback_avisa_que_ele_voltou_ao_saldo() {
        OrderEmailView comCashback = new OrderEmailView("Maria", "#9", ORDER.items(), new BigDecimal("99.90"),
                BigDecimal.ZERO, new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("89.90"), 9L, false);

        String html = renderer.render("order-cancellation", vars("order", comCashback,
                "email", EmailEncerramentoPedido.pelaLoja(comCashback)));

        assertThat(html).contains("voltou para o seu saldo").contains("10,00");
    }

    @Test
    void reembolso_mostra_pix_e_whatsapp_com_a_cor_da_marca() {
        String html = renderer.render("order-cancellation", vars("order", ORDER,
                "email", EmailEncerramentoPedido.reembolso(ORDER, new BigDecimal("94.90"), new BigDecimal("94.90"), false, false)));

        assertThat(html).contains("Seu reembolso foi registrado").contains("94,90");
        assertThat(html).contains("a loja devolve por Pix.").contains("pelo WhatsApp para combinar a devolução");
        assertThat(html).contains("line-height:0;background:" + EmailTheme.DEFAULT.caramel());
    }

    @Test
    void reembolso_parcial_lista_so_o_devolvido_com_o_rotulo_proprio() {
        OrderEmailView devolvido = ORDER.withOnlyItems(List.of(ORDER.items().get(1)));

        String html = renderer.render("order-cancellation", vars("order", devolvido,
                "email", EmailEncerramentoPedido.reembolso(ORDER, new BigDecimal("20.10"), new BigDecimal("20.10"), true, false)));

        assertThat(html).contains("Reembolsamos parte do seu pedido").contains("Valor reembolsado");
        assertThat(html).contains("Carvão 1kg").doesNotContain("Essência Menta");
    }

    @Test
    void reembolso_parcial_de_marcado_abate_a_divida() {
        OrderClosureEmail email = EmailEncerramentoPedido.reembolso(ORDER, BigDecimal.ZERO, new BigDecimal("20.10"), true, true);

        assertThat(email.title()).isEqualTo("Ajustamos sua compra marcada");
        assertThat(email.body()).contains("20,10");
        assertThat(email.whatsapp()).isFalse();
    }

    @Test
    void whatsapp_usa_o_telefone_da_loja_com_a_mensagem_pronta() {
        assertThat(renderer.whatsappUrl("Oi! Pedido #7"))
                .isEqualTo("https://wa.me/5541999990000?text=Oi%21%20Pedido%20%237");
    }

    @Test
    void whatsapp_sem_telefone_valido_nao_gera_link() {
        when(storeProfile.get()).thenReturn(StoreProfile.empty());

        assertThat(renderer.whatsappUrl("Oi")).isNull();
    }

    @Test
    void purchase_receipt_mostra_compra_pagamento_e_cashback() {
        String html = renderer.render("purchase-receipt", vars("order", ORDER, "receipt", RECEIPT));

        assertThat(html).contains("Valeu pela compra!").contains("E aí, <strong>Maria</strong>!");
        assertThat(html).contains("nº 000123").contains("04/11/2026 às 14:32").contains("Essência Menta");
        assertThat(html).contains("Pagamento").contains("Cartão de crédito (3x)");
        assertThat(html).contains("Cashback:").contains("você ganhou R$ 3,90");
        assertThat(html).contains("Comprovante sem valor fiscal.").doesNotContain("Reembolso:");
    }

    @Test
    void purchase_receipt_sem_cashback_e_sem_pagamento_omite_os_blocos() {
        PurchaseReceiptEmail cortesia = new PurchaseReceiptEmail("nº 1", "p", RECEIPT.summary(), List.of(), null, false);

        String html = renderer.render("purchase-receipt", vars("order", ORDER, "receipt", cortesia));

        assertThat(html).doesNotContain("Cashback:").doesNotContain(">Pagamento<");
    }

    @Test
    void purchase_receipt_com_reembolso_parcial_avisa() {
        PurchaseReceiptEmail parcial = new PurchaseReceiptEmail("nº 1", "p", RECEIPT.summary(), RECEIPT.payments(), null, true);

        String html = renderer.render("purchase-receipt", vars("order", ORDER, "receipt", parcial));

        assertThat(html).contains("Reembolso:").contains("os da compra original");
    }

    @Test
    void purchase_receipt_mostra_os_dados_legais_e_o_rodape_do_cupom() {
        when(storeProfile.get()).thenReturn(new StoreProfile("Mahal Tabacaria", "Mahal Ltda", "00.000.000/0001-00", null,
                null, null, null, null, null, null, null, null, null, "Volte sempre!"));

        String html = renderer.render("purchase-receipt", vars("order", ORDER, "receipt", RECEIPT));

        assertThat(html).contains("Mahal Ltda · CNPJ 00.000.000/0001-00").contains("Volte sempre!");
    }

    @Test
    void purchase_receipt_sem_dados_legais_nao_quebra() {
        String html = renderer.render("purchase-receipt", vars("order", ORDER, "receipt", RECEIPT));

        assertThat(html).doesNotContain("CNPJ").doesNotContain("pre-line");
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

        assertThat(html).contains("Seu novo e-mail já está valendo").contains("carol@novo.com");
        assertThat(html).doesNotContain("Não foi você?").doesNotContain("confirmações de pedido");
    }

    @Test
    void email_change_pedido_avisa_o_endereco_antigo() {
        String html = renderer.render("email-change", vars("username", "carol", "newEmail", "carol@novo.com",
                "supportUrl", "https://wa.me/5541999990000"));

        assertThat(html).contains("O e-mail da sua conta foi alterado").contains("Não foi você?");
        assertThat(html).contains("Falar com a loja no WhatsApp").contains("https://wa.me/5541999990000");
        assertThat(html).doesNotContain("Seu novo e-mail já está valendo");
    }

    @Test
    void email_change_sem_whatsapp_mantem_o_aviso_e_omite_o_botao() {
        String html = renderer.render("email-change", vars("username", "carol", "newEmail", "carol@novo.com"));

        assertThat(html).contains("Fale com a loja agora").doesNotContain("Falar com a loja no WhatsApp");
    }

    @Test
    void notificacao_mostra_paragrafo_entre_os_blocos_e_aviso_no_fim() {
        NotificationEmail email = NotificationEmail.builder("fiado.criado", "Assunto")
                .section("Pagamento", List.of(NotificationEmail.Row.of("Valor", "R$ 10,00")))
                .text("Passe no caixa da loja.")
                .whatsapp("Combinar o pagamento", "Oi!")
                .footnote("Não reconhece esta compra?")
                .build();

        String html = renderer.render("notification", vars(
                "email", email, "accent", EmailTheme.DEFAULT.brand(), "actionUrl", "https://wa.me/5541999990000"));

        assertThat(html).contains("Passe no caixa da loja.").contains("Combinar o pagamento");
        assertThat(html).contains("Não reconhece esta compra?");
        assertThat(html.indexOf("Combinar o pagamento")).isLessThan(html.indexOf("Não reconhece esta compra?"));
    }

    @Test
    void papel_atribuido_mostra_o_nome_legivel() {
        String html = renderer.render("account-change", vars(
                "username", "eve", "change", "ROLE_ASSIGNED", "role", "Atendente"));

        assertThat(html).contains("Novo papel na sua conta").contains("<strong>Atendente</strong>");
        assertThat(html).doesNotContain("Papel removido").doesNotContain("foi desativada").doesNotContain("solicitou");
    }

    @Test
    void papel_removido_avisa_das_areas_que_somem() {
        String html = renderer.render("account-change", vars(
                "username", "eve", "change", "ROLE_REMOVED", "role", "Atendente"));

        assertThat(html).contains("Papel removido da sua conta").contains("Algumas áreas podem deixar de aparecer");
    }

    @Test
    void conta_desativada_diz_o_que_significa_e_o_que_fazer() {
        String html = renderer.render("account-change", vars("username", "eve", "change", "DISABLED"));

        assertThat(html).contains("Sua conta foi desativada").contains("você não consegue mais entrar");
        assertThat(html).contains("fale com a loja pelos contatos abaixo").doesNotContain("papel");
    }

    private static Map<String, Object> vars(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
