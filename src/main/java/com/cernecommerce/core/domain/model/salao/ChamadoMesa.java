package com.cernecommerce.core.domain.model.salao;

import java.time.Duration;
import java.time.Instant;

/**
 * Chamado feito pelo QR: a conta, recolher o narguilé, trocar o carvão ou só chamar alguém.
 * Pertence à comanda — a da mesa do QR, com a ocupação, ou a aberta pelo PDV, sem mesa. Encerrada a
 * comanda, os chamados dela deixam de valer.
 *
 * <p>O ABERTO esquecido vence em {@link #VALIDADE}: sai da fila do PDV e libera o cliente a chamar
 * de novo. Derivado na leitura, como a solicitação expirada — sem job.</p>
 *
 * @param mesaId nulo na comanda do PDV, junto com {@code ocupacaoId}
 * @param formaPagamento só no chamado de {@link TipoChamado#CONTA}, onde é obrigatória
 */
public record ChamadoMesa(Long id, Long comandaId, Long mesaId, Long ocupacaoId, TipoChamado tipo,
        FormaPagamentoChamado formaPagamento, StatusChamado status, Instant criadoEm, Instant atendidoEm,
        String atendidoPor) {

    public static final Duration VALIDADE = Duration.ofMinutes(30);

    public ChamadoMesa {
        if (comandaId == null) {
            throw new IllegalArgumentException("chamado pertence a uma comanda");
        }
        if ((mesaId == null) != (ocupacaoId == null)) {
            throw new IllegalArgumentException("mesa e ocupação do chamado vêm juntas");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("tipo do chamado é obrigatório");
        }
        if (tipo == TipoChamado.CONTA && formaPagamento == null) {
            throw new IllegalArgumentException("chamado de conta exige a forma de pagamento");
        }
        formaPagamento = tipo == TipoChamado.CONTA ? formaPagamento : null;
        status = status == null ? StatusChamado.ABERTO : status;
    }

    public static ChamadoMesa novo(Long comandaId, MesaOcupacao ocupacao, TipoChamado tipo,
            FormaPagamentoChamado formaPagamento, Instant agora) {
        return new ChamadoMesa(null, comandaId, ocupacao == null ? null : ocupacao.mesaId(),
                ocupacao == null ? null : ocupacao.id(), tipo, formaPagamento, StatusChamado.ABERTO, agora, null, null);
    }

    /** ABERTO há mais de {@link #VALIDADE}: ninguém atendeu, e a fila o esquece. */
    public boolean expiradoEm(Instant agora) {
        return status == StatusChamado.ABERTO && criadoEm != null && criadoEm.plus(VALIDADE).isBefore(agora);
    }

    /** Aberto e ainda valendo. */
    public boolean abertoEm(Instant agora) {
        return status == StatusChamado.ABERTO && !expiradoEm(agora);
    }

    /** O que a tela do cliente mostra: os abertos que valem e os atendidos há menos de {@link #VALIDADE}. */
    public boolean recenteEm(Instant agora) {
        return abertoEm(agora) || (status == StatusChamado.ATENDIDO && atendidoEm != null
                && atendidoEm.isAfter(agora.minus(VALIDADE)));
    }

    /** Idempotente: o já atendido volta como está, com quem atendeu primeiro. */
    public ChamadoMesa atendido(String username, Instant agora) {
        if (status == StatusChamado.ATENDIDO) {
            return this;
        }
        return new ChamadoMesa(id, comandaId, mesaId, ocupacaoId, tipo, formaPagamento, StatusChamado.ATENDIDO,
                criadoEm, agora, username);
    }
}
