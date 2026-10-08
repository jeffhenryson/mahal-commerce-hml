package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.config.EmailSample;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationEmail.Row;
import com.cernecommerce.core.domain.model.notification.NotificationEmail.Tone;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.SecurityEventContext;
import com.cernecommerce.core.ports.out.notification.EmailPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Dados fictícios de cada e-mail do sistema, para os disparos de teste da tela de Integrações. */
final class EmailSamples {

    private EmailSamples() {
    }

    /** Chama em {@code port} o método do e-mail {@code sample}; síncrono se {@code port} não for um proxy {@code @Async}. */
    static void send(EmailPort port, String to, EmailSample sample) {
        String user = "cliente.teste";
        SecurityEventContext context = new SecurityEventContext(Instant.now(), "203.0.113.10",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0");
        switch (sample) {
            case VERIFICATION_CODE -> port.sendVerificationCode(to, user, "TESTE1234ABC");
            case PASSWORD_RESET -> port.sendPasswordResetLink(to, user, "https://exemplo.com/auth/reset-password?token=TESTE", 15);
            case USER_INVITE -> port.sendUserInvite(to, user, "https://exemplo.com/auth/reset-password?token=TESTE", 48);
            case EMAIL_CHANGE -> port.sendEmailChangeNotification(to, user, "novo.email@exemplo.com");
            case WELCOME -> port.sendWelcome(to, user);
            case ACCOUNT_CHANGE -> port.sendAccountChange(to, user, "Papel atribuído",
                    "Teste de envio — o papel ROLE_ATENDENTE seria atribuído à sua conta.");
            case PASSWORD_CHANGED -> port.sendPasswordChangedAlert(to, user, context);
            case ACCOUNT_LOCKED -> port.sendAccountLockedAlert(to, user, context);
            case TOTP_STATUS -> port.sendTotpStatusAlert(to, user, true, context);
            case TOKEN_THEFT -> port.sendTokenTheftAlert(to, user, context);
            case ORDER_CONFIRMATION -> port.sendOrderConfirmation(to, order(), null);
            case ORDER_STATUS_UPDATE -> port.sendOrderStatusUpdate(to, order(), "Pedido enviado");
            case ORDER_CANCELLATION -> port.sendOrderCancellation(to, order(),
                    "Teste de envio — nenhum pedido foi cancelado", false);
            case PURCHASE_RECEIPT -> port.sendPurchaseReceipt(to, order());
            case RECEIVABLE_REMINDER -> port.sendCustomerNotice(to, NotificationEmail
                    .builder("fiado.lembrete", "Seu marcado de R$ 129,90 vence em 3 dias")
                    .title("Lembrete: seu marcado vence em 3 dias")
                    .intro("Teste de envio — nenhuma compra foi marcada.")
                    .section("Vencimento", List.of(Row.of("Compra de 01/10/2026", "R$ 129,90"),
                            Row.highlighted("Total", "R$ 129,90")))
                    .build());
            case CASH_SESSION_CLOSED -> port.sendNotification(to, NotificationEmail
                    .builder("caixa.fechamento", "Caixa #0001 fechado com diferença")
                    .intro("Teste de envio — nenhum caixa foi fechado.")
                    .tone(Tone.WARNING)
                    .section("Conferência", List.of(Row.of("Esperado", "R$ 850,00"), Row.of("Contado", "R$ 840,00"),
                            Row.highlighted("Diferença", "-R$ 10,00")))
                    .section("Vendas por forma de pagamento", List.of(Row.of("Dinheiro", "R$ 350,00"),
                            Row.of("PIX", "R$ 420,00"), Row.of("Crédito", "R$ 180,00")))
                    .action("Ver caixa", "/app/pdv")
                    .build());
            case CASH_SESSION_STALE -> port.sendNotification(to, NotificationEmail
                    .builder("caixa.esquecido", "Caixa #0001 aberto há 12h por atendente")
                    .intro("Teste de envio — nenhum caixa ficou aberto.")
                    .tone(Tone.WARNING)
                    .section(null, List.of(Row.of("Operador", "atendente"), Row.of("Depósito", "LOJA-01")))
                    .action("Abrir PDV", "/app/pdv")
                    .build());
            case DAILY_DIGEST -> port.sendNotification(to, NotificationEmail
                    .builder("gestao.resumo-diario", "Resumo de 04/10/2026")
                    .intro("Teste de envio — 42 pedidos, R$ 3.210,00 vendidos.")
                    .section("Vendas", List.of(Row.highlighted("Total vendido (líquido)", "R$ 3.210,00"),
                            Row.of("Pedidos", 42), Row.of("Ticket médio", "R$ 76,43")))
                    .section("Fiado vencido", List.of(Row.of("Cliente Teste", "R$ 80,00"),
                            Row.highlighted("Total vencido", "R$ 80,00")))
                    .build());
            case STOCK_REORDER_ALERT -> port.sendNotification(to, NotificationEmail
                    .builder("estoque.reposicao", "2 produtos no ponto de reposição")
                    .intro("Teste de envio — os produtos abaixo são fictícios.")
                    .tone(Tone.WARNING)
                    .section("Repor", List.of(Row.of("Essência Menta 50g (ESS-MENTA-50)", "disponível 3 · mínimo 10"),
                            Row.of("Carvão de coco 1kg (CARVAO-1KG)", "disponível 1 · mínimo 5")))
                    .action("Abrir estoque", "/app/estoque")
                    .build());
            case DEV_ALERT -> port.sendNotification(to, NotificationEmail
                    .builder("dev.erro-500", "Erro 500 em POST /pdv/sales")
                    .intro("Teste de envio — nenhum erro aconteceu.")
                    .tone(Tone.DANGER)
                    .section("Detalhes", List.of(Row.of("Exceção", "java.lang.IllegalStateException"),
                            Row.of("Mensagem", "exemplo"), Row.of("Usuário", "atendente")))
                    .build());
        }
    }

    private static OrderEmailView order() {
        return new OrderEmailView("Cliente Teste", "#0001", List.of(
                new OrderEmailView.Line("Essência Menta 50g", new BigDecimal("2"), new BigDecimal("39.90"), new BigDecimal("79.80")),
                new OrderEmailView.Line("Carvão de coco 1kg", BigDecimal.ONE, new BigDecimal("50.10"), new BigDecimal("50.10"))),
                new BigDecimal("129.90"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("129.90"));
    }
}
