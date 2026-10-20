package com.cernecommerce.core.domain.model.salao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A mesa ocupada como o lead com acesso a vê: o código para passar a quem chegar, o que já foi
 * lançado e quanto falta pagar. Sem id de comanda nem dado pessoal além do nome mascarado.
 */
public record MesaAtual(String mesa, String cliente, String codigo, Instant abertaEm, List<Item> itens,
        BigDecimal emAberto) {

    public MesaAtual {
        itens = List.copyOf(itens);
    }

    public record Item(String nome, BigDecimal quantidade, BigDecimal valor, boolean pago, Instant lancadoEm) {
    }
}
