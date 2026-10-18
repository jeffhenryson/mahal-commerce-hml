package com.cernecommerce.core.domain.exception.pdv;

/**
 * Tentativa de operar uma sessão de caixa que pertence a outro operador (PDV-C004).
 *
 * <p>Antes desta regra, qualquer usuário com {@code PDV_SALE_MANAGE} registrava venda em
 * <b>qualquer</b> sessão aberta, inclusive na de outro operador — e o fechamento daquele caixa
 * acusava uma diferença que o dono não tinha como explicar.</p>
 *
 * <p>É este escopo, e não permissão fina de estoque, que fecha o buraco de isolamento do módulo: um
 * PDV que exigisse permissão de estoque para vender seria inoperante, mas um PDV em que o operador
 * só mexe no próprio caixa é simplesmente correto.</p>
 */
public class CashRegisterSessionNotOwnedException extends RuntimeException {

    public CashRegisterSessionNotOwnedException(Long sessionId, String operator) {
        super("A sessão de caixa " + sessionId + " não pertence a " + operator + ".");
    }

    private CashRegisterSessionNotOwnedException(String message) {
        super(message);
    }

    /** PDV-F056 — os caixas de outro operador, lidos em conjunto (desempenho), sem a permissão. */
    public static CashRegisterSessionNotOwnedException ofOperator(String operator, String requester) {
        return new CashRegisterSessionNotOwnedException(
                "Os caixas de " + operator + " não podem ser lidos por " + requester + ".");
    }
}
