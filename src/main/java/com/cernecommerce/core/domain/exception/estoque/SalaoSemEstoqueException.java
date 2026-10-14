package com.cernecommerce.core.domain.exception.estoque;

/**
 * A sessão precisa abrir uma lata do sabor no Salão e ele não tem lata fechada. Não cai para o
 * estoque da loja de propósito: o Salão "compra" da loja por transferência, e baixar direto do
 * caixa tiraria a essência do saldo da loja sem passar pelo custo do Salão.
 */
public class SalaoSemEstoqueException extends RuntimeException {
    private final String sku;

    public SalaoSemEstoqueException(String sku) {
        super("Sem lata fechada deste sabor no Salão — traga da loja em Salão › Essências.");
        this.sku = sku;
    }

    public String sku() {
        return sku;
    }
}
