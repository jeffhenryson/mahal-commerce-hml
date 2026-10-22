package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.pdv.ProporcaoMistura;
import com.cernecommerce.core.domain.model.salao.EscolhasSessao;
import com.cernecommerce.core.domain.model.salao.SolicitacaoResumo;
import com.cernecommerce.core.domain.model.salao.SolicitacaoSessao;
import com.cernecommerce.core.domain.model.salao.SolicitacaoStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Solicitação de sessão como o PDV a vê, com o que o lead escolheu e o total estimado. {@code acompanhante}:
 * pedido sem CPF de quem entrou na mesa pelo código — {@code cliente} já vem "Acompanhante de &lt;titular&gt;".
 * {@code pagarNoFinal}: o que o lead pediu; {@code pagarNoFinalAceito}: o que o atendente confirmou no aceite
 * (nulo enquanto pendente e na recusada).
 */
public record SolicitacaoSessaoResponseDTO(Long id, SolicitacaoStatus status, String mesa, Long customerId,
        String cliente, String clienteContato, String tierNome, BigDecimal preco, String essenciaSku,
        String essencia, String essenciaSku2, String essencia2, ProporcaoMistura proporcao, String observacao, String motivo, Long comandaId,
        Instant criadoEm, Instant decididoEm, String decididoPor, boolean vasoGrande, Charcoal carvao,
        List<String> adicionais, boolean pagarNoFinal, BigDecimal total, boolean acompanhante,
        Boolean pagarNoFinalAceito) {

    public static SolicitacaoSessaoResponseDTO of(SolicitacaoResumo r) {
        SolicitacaoSessao s = r.solicitacao();
        EscolhasSessao x = s.escolhas();
        return new SolicitacaoSessaoResponseDTO(s.id(), r.status(), r.mesa(), s.customerId(), r.cliente(),
                r.clienteContato(), r.tierNome(), r.preco(), s.essenciaSku(), r.essencia(), s.essenciaSku2(),
                r.essencia2(), x.proporcao(), s.observacao(), s.motivo(), s.comandaId(), s.criadoEm(), s.decididoEm(),
                s.decididoPor(), x.vasoGrande(), x.carvao(), r.adicionais(), x.pagarNoFinal(), r.total(),
                s.acompanhante(), s.pagarNoFinalAceito());
    }
}
