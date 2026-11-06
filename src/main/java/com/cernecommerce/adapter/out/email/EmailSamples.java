package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.config.EmailSample;
import com.cernecommerce.core.domain.model.ecommerce.EmailEncerramentoPedido;
import com.cernecommerce.core.domain.model.notification.AccountChange;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationEmail.Row;
import com.cernecommerce.core.domain.model.notification.NotificationEmail.Tone;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.OrderStageEmail;
import com.cernecommerce.core.domain.model.notification.PurchaseReceiptEmail;
import com.cernecommerce.core.domain.model.notification.SecurityEventContext;
import com.cernecommerce.core.domain.model.recebivel.ReceivableEmailText;
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
            case USER_INVITE -> port.sendUserInvite(to, user, "Atendente", "https://exemplo.com/auth/reset-password?token=TESTE", 48);
            case EMAIL_CHANGE_REQUEST -> port.sendEmailChangeCode(to, user, "TESTE1234ABC");
            case EMAIL_CHANGE -> port.sendEmailChangeNotification(to, user, "novo.email@exemplo.com");
            case EMAIL_CHANGE_CONFIRMED -> port.sendEmailChangeConfirmed(to, user);
            case WELCOME -> port.sendWelcome(to, user);
            case CUSTOMER_WELCOME -> port.sendCustomerWelcome(to, "Maria");
            case ACCOUNT_CHANGE -> port.sendAccountChange(to, user, AccountChange.ROLE_ASSIGNED, "Atendente");
            case PASSWORD_CHANGED -> port.sendPasswordChangedAlert(to, user, context);
            case ACCOUNT_LOCKED -> port.sendAccountLockedAlert(to, user, context);
            case TOTP_STATUS -> port.sendTotpStatusAlert(to, user, true, context);
            case TOKEN_THEFT -> port.sendTokenTheftAlert(to, user, context);
            case PASSWORD_RESET_ALERT -> port.sendPasswordResetAlert(to, user, context);
            case GOOGLE_LINKED -> port.sendGoogleLinkedAlert(to, user, context);
            case TWO_FACTOR_REQUIRED -> port.sendTwoFactorRequired(to, user);
            case EMAIL_CHANGED_BY_ADMIN -> port.sendEmailChangedByAdmin(to, user, context);
            case ORDER_CONFIRMATION -> port.sendOrderConfirmation(to, order(false), null);
            case ORDER_CONFIRMATION_DELIVERY -> port.sendOrderConfirmation(to, order(true), null);
            case ORDER_STATUS_UPDATE -> port.sendOrderStatusUpdate(to, order(true), OrderStageEmail
                    .builder("Pedido #0001 aceito: falta o Pix", "O total com o frete ficou em R$ 129,90.",
                            "Pedido aceito! Falta só o Pix", "Teste de envio — nenhum pedido foi aceito.")
                    .detail("Total a pagar:", "R$ 129,90")
                    .detail("Chave Pix:", "pix@exemplo.com")
                    .items().tracking().build());
            case ORDER_CANCELLATION -> port.sendOrderCancellation(to, order(false),
                    EmailEncerramentoPedido.recusado(order(false), "Teste de envio — nenhum pedido foi cancelado"));
            case ORDER_REFUND -> port.sendOrderCancellation(to, order(false), EmailEncerramentoPedido.reembolso(
                    order(false), new BigDecimal("129.90"), new BigDecimal("129.90"), false, false));
            case PURCHASE_RECEIPT -> port.sendPurchaseReceipt(to, order(false), new PurchaseReceiptEmail("nº 000001",
                    "R$ 129,90 em 04/10/2026 às 14:32. Valeu pela compra!",
                    List.of(new OrderStageEmail.Detail("Compra:", "nº 000001"),
                            new OrderStageEmail.Detail("Data:", "04/10/2026 às 14:32")),
                    List.of(new OrderStageEmail.Detail("Pix", "R$ 80,00"), new OrderStageEmail.Detail("Dinheiro", "R$ 60,00"),
                            new OrderStageEmail.Detail("Troco", "R$ 10,10")),
                    "você ganhou R$ 3,90 nesta compra. Fica disponível a partir de 11/10/2026.", false));
            case RECEIVABLE_CREATED -> port.sendCustomerNotice(to, NotificationEmail
                    .builder("fiado.criado", "Compra marcada: R$ 129,90 até 10/10/2026")
                    .title("Sua compra foi marcada")
                    .intro("Teste de envio — nenhuma compra foi marcada.")
                    .preheader(ReceivableEmailText.PREHEADER)
                    .section("Pagamento", List.of(Row.highlighted("Valor marcado", "R$ 129,90"),
                            Row.of("Vencimento", "10/10/2026"), Row.of("Cupom", "nº 000123")))
                    .text(ReceivableEmailText.HOW_TO_PAY)
                    .section("Itens", List.of(Row.of("2× Essência Menta 50g", "R$ 79,80"),
                            Row.of("1× Carvão de coco 1kg", "R$ 50,10")))
                    .whatsapp(ReceivableEmailText.ACTION, ReceivableEmailText.whatsappMessage("R$ 129,90"))
                    .footnote("Não reconhece esta compra? Fale com a loja.")
                    .build());
            case RECEIVABLE_REMINDER -> port.sendCustomerNotice(to, NotificationEmail
                    .builder("fiado.lembrete", "Seu marcado de R$ 129,90 vence em 10/10/2026")
                    .title("Seu marcado vence em 3 dias")
                    .intro("Teste de envio — nenhuma compra foi marcada.")
                    .preheader(ReceivableEmailText.PREHEADER)
                    .section("Vencimento 10/10/2026", List.of(Row.of("Compra de 01/10/2026", "R$ 129,90"),
                            Row.highlighted("Total", "R$ 129,90")))
                    .text(ReceivableEmailText.HOW_TO_PAY)
                    .whatsapp(ReceivableEmailText.ACTION, ReceivableEmailText.whatsappMessage("R$ 129,90"))
                    .footnote("Já pagou? Pode desconsiderar este aviso.")
                    .build());
            case RECEIVABLE_PAID -> port.sendCustomerNotice(to, NotificationEmail
                    .builder("fiado.quitado", "Pagamento recebido: R$ 129,90")
                    .title("Recebemos seu pagamento")
                    .intro("Teste de envio — nenhum pagamento foi registrado.")
                    .preheader("Seu marcado está quitado.")
                    .tone(Tone.SUCCESS)
                    .section(null, List.of(Row.highlighted("Valor pago", "R$ 129,90"),
                            Row.of("Saldo em aberto", "Nenhum"),
                            Row.of("Quando", "04/10/2026 às 14:32")))
                    .footnote("Não reconhece este pagamento? Fale com a loja.")
                    .build());
            case CASH_SESSION_OPENED -> port.sendNotification(to, NotificationEmail
                    .builder("caixa.abertura", "Caixa #0001 aberto por atendente")
                    .intro("Teste de envio — nenhum caixa foi aberto.")
                    .section(null, List.of(Row.of("Operador", "atendente"), Row.of("Aberto em", "04/10/2026 às 09:00"),
                            Row.of("Fundo de troco", "R$ 200,00"), Row.of("Depósito", "LOJA-01")))
                    .action("Abrir PDV", "/app/pdv")
                    .build());
            case CASH_MOVEMENT -> port.sendNotification(to, NotificationEmail
                    .builder("caixa.movimento", "Sangria de R$ 300,00 no caixa #0001")
                    .intro("Teste de envio — nenhuma sangria foi feita.")
                    .tone(Tone.WARNING)
                    .section(null, List.of(Row.of("Tipo", "Sangria"), Row.highlighted("Valor", "R$ 300,00"),
                            Row.of("Motivo", "Depósito no banco"), Row.of("Operador", "atendente"),
                            Row.of("Quando", "04/10/2026 às 15:10")))
                    .action("Abrir PDV", "/app/pdv")
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
            case ORDER_CANCELLED_ALERT -> port.sendNotification(to, NotificationEmail
                    .builder("operacao.cancelamento", "Pedido #0001 cancelado por atendente")
                    .intro("Teste de envio — nenhum pedido foi cancelado.")
                    .tone(Tone.WARNING)
                    .section(null, List.of(Row.of("Pedido", "#0001"), Row.of("Status anterior", "PAGO"),
                            Row.of("Motivo", "Cliente desistiu"), Row.of("Itens", "ESS-MENTA-50, CARVAO-1KG"),
                            Row.of("Feito por", "atendente"), Row.of("Quando", "04/10/2026 às 16:20")))
                    .build());
            case PAYMENT_CORRECTED_ALERT -> port.sendNotification(to, NotificationEmail
                    .builder("operacao.correcao-pagamento", "Pagamento do pedido #0001 corrigido por atendente")
                    .intro("Teste de envio — nenhum pagamento foi corrigido.")
                    .tone(Tone.WARNING)
                    .section(null, List.of(Row.of("Pedido", "#0001"), Row.of("Antes", "Dinheiro"), Row.of("Depois", "PIX"),
                            Row.of("Motivo", "Lançado errado"), Row.of("Feito por", "atendente"),
                            Row.of("Quando", "04/10/2026 às 16:25")))
                    .build());
            case PRICE_CHANGED_ALERT -> port.sendNotification(to, NotificationEmail
                    .builder("operacao.preco", "Preço de ESS-MENTA-50 alterado para R$ 42,90")
                    .intro("Teste de envio — nenhum preço foi alterado.")
                    .section(null, List.of(Row.of("SKU", "ESS-MENTA-50"), Row.highlighted("Novo preço", "R$ 42,90"),
                            Row.of("Feito por", "gerente"), Row.of("Quando", "04/10/2026 às 11:05")))
                    .action("Abrir estoque", "/app/estoque")
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

    private static OrderEmailView order(boolean delivery) {
        return new OrderEmailView("Cliente Teste", "#0001", List.of(
                new OrderEmailView.Line("Essência Menta 50g", new BigDecimal("2"), new BigDecimal("39.90"), new BigDecimal("79.80")),
                new OrderEmailView.Line("Carvão de coco 1kg", BigDecimal.ONE, new BigDecimal("50.10"), new BigDecimal("50.10"))),
                new BigDecimal("129.90"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("129.90"), 1L, delivery);
    }
}
