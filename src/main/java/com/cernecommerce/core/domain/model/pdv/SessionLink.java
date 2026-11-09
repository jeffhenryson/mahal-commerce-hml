package com.cernecommerce.core.domain.model.pdv;

/**
 * PDV-F061 — por que uma linha que não é rosh está ligada a uma sessão de mesa.
 *
 * <ul>
 *   <li>{@link #PACOTE_ESSENCIA}: pacote da loja comprado com a sessão, cobrado pelo preço de venda.</li>
 *   <li>{@link #PACOTE_INCLUSO}: o pacote do sabor principal que vem no kit de itens pessoais, a R$ 0.</li>
 *   <li>{@link #ADICIONAL}: o adicional incluído depois que a sessão já tinha sido paga.</li>
 * </ul>
 */
public enum SessionLink {
    PACOTE_ESSENCIA,
    PACOTE_INCLUSO,
    ADICIONAL
}
