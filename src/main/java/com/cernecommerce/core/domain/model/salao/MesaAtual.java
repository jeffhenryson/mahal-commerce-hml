package com.cernecommerce.core.domain.model.salao;

import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.pdv.ProporcaoMistura;
import com.cernecommerce.core.domain.model.pdv.SessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A mesa ocupada como o lead com acesso a vê: o código para passar a quem chegar, o que já foi
 * lançado, quanto falta pagar, os chamados à equipe e a última sessão (para "repetir"). Sem id de
 * comanda nem dado pessoal além do nome mascarado.
 *
 * <p>Com o pedido ainda pendente (mesa ocupada no envio, V171) não há comanda: sem itens, nada em
 * aberto, sem chamados nem última sessão — só o código.</p>
 *
 * @param chamados os abertos e os atendidos há pouco
 * @param ultimaSessao a última sessão lançada na comanda — pelo QR ou pelo PDV —, ou nula
 * @param comprasLoja as vendas da loja ligadas à mesa (PDV-F036)
 */
public record MesaAtual(String mesa, String cliente, String codigo, Instant abertaEm, List<Item> itens,
        BigDecimal emAberto, List<ChamadoMesa> chamados, UltimaSessao ultimaSessao, List<CompraLoja> comprasLoja) {

    public MesaAtual {
        itens = List.copyOf(itens);
        chamados = chamados == null ? List.of() : List.copyOf(chamados);
        comprasLoja = comprasLoja == null ? List.of() : List.copyOf(comprasLoja);
    }

    public MesaAtual(String mesa, String cliente, String codigo, Instant abertaEm, List<Item> itens,
            BigDecimal emAberto, List<ChamadoMesa> chamados, UltimaSessao ultimaSessao) {
        this(mesa, cliente, codigo, abertaEm, itens, emAberto, chamados, ultimaSessao, List.of());
    }

    public MesaAtual(String mesa, String cliente, String codigo, Instant abertaEm, List<Item> itens,
            BigDecimal emAberto) {
        this(mesa, cliente, codigo, abertaEm, itens, emAberto, List.of(), null);
    }

    /** @param sessao a linha do tempo, nas linhas de sessão ({@code SESSAO}/{@code ROSH_EXTRA}); senão nula */
    public record Item(String nome, BigDecimal quantidade, BigDecimal valor, boolean pago, Instant lancadoEm,
            Sessao sessao) {

        public Item(String nome, BigDecimal quantidade, BigDecimal valor, boolean pago, Instant lancadoEm) {
            this(nome, quantidade, valor, pago, lancadoEm, null);
        }
    }

    /**
     * Venda da loja ligada à mesa, como o cliente a vê.
     *
     * @param id o número do pedido — nunca o id sequencial
     * @param em a conclusão da venda, ou a criação se ainda não concluiu
     */
    public record CompraLoja(String id, Instant em, List<ItemCompra> itens, BigDecimal total) {

        public CompraLoja {
            itens = itens == null ? List.of() : List.copyOf(itens);
        }
    }

    public record ItemCompra(String nome, BigDecimal quantidade, BigDecimal valor) {
    }

    /**
     * Onde a sessão está, para o cliente acompanhar (PDV-F035, sem custo, operador nem id da linha).
     *
     * @param status nulo na sessão anterior à V132, que não registrava o andamento
     * @param essencia o texto da linha, com a proporção da mescla; nulo no 2º rosh com sabor pendente
     */
    public record Sessao(SessionStatus status, String essencia, boolean pagarNoFinal, Instant lancadaEm,
            Instant inicioEm, Instant entregueEm, Instant recolhidaEm, Long preparoMin, Long naMesaMin) {
    }

    /** O bastante para o QR remontar o pedido: os sabores, a proporção e os extras. */
    public record UltimaSessao(String essenciaSku, String essenciaSku2, ProporcaoMistura proporcao,
            boolean vasoGrande, Charcoal carvao, List<Long> adicionalIds) {

        public UltimaSessao {
            adicionalIds = adicionalIds == null ? List.of() : List.copyOf(adicionalIds);
        }
    }
}
