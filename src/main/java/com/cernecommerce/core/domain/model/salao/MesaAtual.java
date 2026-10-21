package com.cernecommerce.core.domain.model.salao;

import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.pdv.ProporcaoMistura;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A mesa ocupada como o lead com acesso a vê: o código para passar a quem chegar, o que já foi
 * lançado, quanto falta pagar, os chamados à equipe e a última sessão (para "repetir"). Sem id de
 * comanda nem dado pessoal além do nome mascarado.
 *
 * @param chamados os abertos e os atendidos há pouco
 * @param ultimaSessao a última sessão lançada na comanda — pelo QR ou pelo PDV —, ou nula
 */
public record MesaAtual(String mesa, String cliente, String codigo, Instant abertaEm, List<Item> itens,
        BigDecimal emAberto, List<ChamadoMesa> chamados, UltimaSessao ultimaSessao) {

    public MesaAtual {
        itens = List.copyOf(itens);
        chamados = chamados == null ? List.of() : List.copyOf(chamados);
    }

    public MesaAtual(String mesa, String cliente, String codigo, Instant abertaEm, List<Item> itens,
            BigDecimal emAberto) {
        this(mesa, cliente, codigo, abertaEm, itens, emAberto, List.of(), null);
    }

    public record Item(String nome, BigDecimal quantidade, BigDecimal valor, boolean pago, Instant lancadoEm) {
    }

    /** O bastante para o QR remontar o pedido: os sabores, a proporção e os extras. */
    public record UltimaSessao(String essenciaSku, String essenciaSku2, ProporcaoMistura proporcao,
            boolean vasoGrande, Charcoal carvao, List<Long> adicionalIds) {

        public UltimaSessao {
            adicionalIds = adicionalIds == null ? List.of() : List.copyOf(adicionalIds);
        }
    }
}
