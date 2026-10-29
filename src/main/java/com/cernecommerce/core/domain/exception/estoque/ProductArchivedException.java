package com.cernecommerce.core.domain.exception.estoque;

/**
 * Produto arquivado não vende, não recebe entrada nem entra em kit (EST-F054).
 *
 * <p>Só os caminhos que <b>originam</b> movimento lançam esta exceção: venda nova (PDV, comanda,
 * loja, troca), carrinho, {@code ENTRADA} (manual, Compras, NF-e) e receita de kit. Saída e
 * ajuste internos — consumo de reserva, estorno, contagem, abertura de embalagem — continuam
 * livres, pela mesma razão do produto inativo (EST-F018): o saldo que já existe não pode ficar
 * preso.</p>
 */
public class ProductArchivedException extends RuntimeException {
    public ProductArchivedException(String sku) {
        super("Produto " + sku + " foi excluído — restaure-o para movimentar");
    }
}
