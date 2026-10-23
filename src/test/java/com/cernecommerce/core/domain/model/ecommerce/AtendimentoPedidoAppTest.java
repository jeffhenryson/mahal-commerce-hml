package com.cernecommerce.core.domain.model.ecommerce;

import com.cernecommerce.core.domain.exception.ecommerce.AtendimentoTransicaoInvalidaException;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtendimentoPedidoAppTest {

    private static final Instant AGORA = Instant.parse("2026-10-23T15:00:00Z");
    private static final Instant PRAZO = Instant.parse("2026-10-24T01:00:00Z");

    private static AtendimentoPedidoApp retirada() {
        return AtendimentoPedidoApp.novo(7L, DeliveryType.RETIRADA, PreferenciaPagamento.DEBITO, " ", PRAZO, AGORA);
    }

    private static AtendimentoPedidoApp entrega() {
        return AtendimentoPedidoApp.novo(8L, DeliveryType.ENTREGA, PreferenciaPagamento.PIX, null, PRAZO, AGORA);
    }

    @Test
    void novo_chegaPendenteSemCaixaEComObservacaoEmBrancoComoNula() {
        AtendimentoPedidoApp a = retirada();

        assertThat(a.status()).isEqualTo(AtendimentoStatus.PENDENTE);
        assertThat(a.sessionId()).isNull();
        assertThat(a.observacao()).isNull();
        assertThat(a.emAndamento()).isTrue();
        assertThat(a.liquidavel()).isFalse();
    }

    @Test
    void retirada_aceitaSeparaELiquidaNoBalcao() {
        AtendimentoPedidoApp aceito = retirada().aceito(5L, "caixa1", AGORA);
        assertThat(aceito.sessionId()).isEqualTo(5L);
        assertThat(aceito.atendidoPor()).isEqualTo("caixa1");
        assertThat(aceito.liquidavel()).isTrue();

        AtendimentoPedidoApp pronto = aceito.pronto(AGORA.plusSeconds(600));
        assertThat(pronto.liquidavel()).isTrue();

        AtendimentoPedidoApp retirado = pronto.retirado(AGORA.plusSeconds(3600));
        assertThat(retirado.status()).isEqualTo(AtendimentoStatus.RETIRADO);
        assertThat(retirado.status().isFinal()).isTrue();
        assertThat(retirado.prontoEm()).isEqualTo(AGORA.plusSeconds(600));
        assertThat(retirado.concluidoEm()).isEqualTo(AGORA.plusSeconds(3600));
    }

    @Test
    void entrega_saiDepoisDoAceiteENaoPassaPorPronto() {
        AtendimentoPedidoApp aceito = entrega().aceito(5L, "caixa1", AGORA);

        assertThatThrownBy(() -> aceito.pronto(AGORA)).isInstanceOf(AtendimentoTransicaoInvalidaException.class);
        assertThatThrownBy(() -> aceito.retirado(AGORA)).isInstanceOf(AtendimentoTransicaoInvalidaException.class);

        AtendimentoPedidoApp entregue = aceito.saiuParaEntrega(AGORA).entregue(AGORA.plusSeconds(1800));
        assertThat(entregue.status()).isEqualTo(AtendimentoStatus.ENTREGUE);
        assertThat(entregue.saiuEm()).isEqualTo(AGORA);
    }

    @Test
    void naoAceitaDuasVezesNemPulaOAceite() {
        AtendimentoPedidoApp aceito = retirada().aceito(5L, "caixa1", AGORA);

        assertThatThrownBy(() -> aceito.aceito(6L, "caixa2", AGORA))
                .isInstanceOf(AtendimentoTransicaoInvalidaException.class)
                .hasMessageContaining("ACEITO");
        assertThatThrownBy(() -> retirada().pronto(AGORA)).isInstanceOf(AtendimentoTransicaoInvalidaException.class);
        assertThatThrownBy(() -> entrega().saiuParaEntrega(AGORA))
                .isInstanceOf(AtendimentoTransicaoInvalidaException.class);
    }

    @Test
    void recusaCancelaEExpiraSoEmAndamento() {
        assertThat(retirada().recusado("acabou", AGORA).motivo()).isEqualTo("acabou");
        assertThat(retirada().aceito(5L, "caixa1", AGORA).cancelado("desisti", AGORA).status())
                .isEqualTo(AtendimentoStatus.CANCELADO);
        assertThat(retirada().aceito(5L, "caixa1", AGORA).pronto(AGORA).expirado(PRAZO).status())
                .isEqualTo(AtendimentoStatus.EXPIRADO);

        AtendimentoPedidoApp saiu = entrega().aceito(5L, "caixa1", AGORA).saiuParaEntrega(AGORA);
        assertThatThrownBy(() -> saiu.recusado("x", AGORA)).isInstanceOf(AtendimentoTransicaoInvalidaException.class);
        assertThatThrownBy(() -> saiu.expirado(PRAZO)).isInstanceOf(AtendimentoTransicaoInvalidaException.class);
    }

    @Test
    void assumido_trocaOCaixaSemMudarAEtapa() {
        AtendimentoPedidoApp pronto = retirada().aceito(5L, "caixa1", AGORA).pronto(AGORA);

        AtendimentoPedidoApp assumido = pronto.assumido(6L, "caixa2");

        assertThat(assumido.status()).isEqualTo(AtendimentoStatus.PRONTO);
        assertThat(assumido.sessionId()).isEqualTo(6L);
        assertThat(assumido.atendidoPor()).isEqualTo("caixa2");
        assertThatThrownBy(() -> retirada().assumido(6L, "caixa2"))
                .isInstanceOf(AtendimentoTransicaoInvalidaException.class);
    }
}
