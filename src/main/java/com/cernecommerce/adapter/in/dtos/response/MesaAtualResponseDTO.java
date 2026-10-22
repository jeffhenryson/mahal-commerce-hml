package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.pdv.ProporcaoMistura;
import com.cernecommerce.core.domain.model.pdv.SessionStatus;
import com.cernecommerce.core.domain.model.salao.MesaAtual;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A mesa ocupada como o lead com acesso a vê, com os chamados, a última sessão (para "repetir") e as
 * compras da loja ligadas à mesa.
 */
public record MesaAtualResponseDTO(String mesa, String cliente, String codigo, Instant abertaEm, List<Item> itens,
        BigDecimal emAberto, List<ChamadoDaMesaResponseDTO> chamados, UltimaSessao ultimaSessao,
        List<CompraLoja> comprasLoja) {

    /** {@code sessao} só nas linhas de sessão; nas demais, nula. */
    public record Item(String nome, BigDecimal quantidade, BigDecimal valor, boolean pago, Instant lancadoEm,
            Sessao sessao) {

        public static Item of(MesaAtual.Item i) {
            MesaAtual.Sessao s = i.sessao();
            return new Item(i.nome(), i.quantidade(), i.valor(), i.pago(), i.lancadoEm(),
                    s == null ? null : new Sessao(s.status(), s.essencia(), s.pagarNoFinal(), s.lancadaEm(),
                            s.inicioEm(), s.entregueEm(), s.recolhidaEm(), s.preparoMin(), s.naMesaMin()));
        }
    }

    /** A linha do tempo da sessão (PDV-F035), sem custo, operador nem id da linha. */
    public record Sessao(SessionStatus status, String essencia, boolean pagarNoFinal, Instant lancadaEm,
            Instant inicioEm, Instant entregueEm, Instant recolhidaEm, Long preparoMin, Long naMesaMin) {
    }

    /** Venda da loja ligada à mesa; {@code id} é o número do pedido. */
    public record CompraLoja(String id, Instant em, List<ItemCompra> itens, BigDecimal total) {

        public static CompraLoja of(MesaAtual.CompraLoja c) {
            return new CompraLoja(c.id(), c.em(),
                    c.itens().stream().map(i -> new ItemCompra(i.nome(), i.quantidade(), i.valor())).toList(),
                    c.total());
        }
    }

    public record ItemCompra(String nome, BigDecimal quantidade, BigDecimal valor) {
    }

    public record UltimaSessao(String essenciaSku, String essenciaSku2, ProporcaoMistura proporcao,
            boolean vasoGrande, Charcoal carvao, List<Long> adicionalIds) {
    }

    public static MesaAtualResponseDTO of(MesaAtual m) {
        MesaAtual.UltimaSessao u = m.ultimaSessao();
        return new MesaAtualResponseDTO(m.mesa(), m.cliente(), m.codigo(), m.abertaEm(),
                m.itens().stream().map(Item::of).toList(), m.emAberto(),
                m.chamados().stream().map(ChamadoDaMesaResponseDTO::of).toList(),
                u == null ? null : new UltimaSessao(u.essenciaSku(), u.essenciaSku2(), u.proporcao(), u.vasoGrande(),
                        u.carvao(), u.adicionalIds()),
                m.comprasLoja().stream().map(CompraLoja::of).toList());
    }
}
