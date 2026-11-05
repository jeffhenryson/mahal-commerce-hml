package com.cernecommerce.core.domain.model.recebivel;

/** Textos que a compra marcada, o lembrete e o vencimento repetem: o caminho para pagar é um só. */
public final class ReceivableEmailText {

    public static final String PREHEADER = "Para pagar, passe no caixa da loja ou fale com a gente no WhatsApp.";
    public static final String HOW_TO_PAY =
            "Para pagar, passe no caixa da loja até o vencimento ou fale com a gente no WhatsApp para combinar.";
    public static final String ACTION = "Combinar o pagamento";

    private ReceivableEmailText() {
    }

    public static String whatsappMessage(String amount) {
        return "Oi! Quero combinar o pagamento da minha compra marcada de " + amount + ".";
    }
}
