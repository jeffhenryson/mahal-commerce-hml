package com.cernecommerce.core.domain.model.assistente;

import java.time.Instant;

/**
 * Escrita proposta pelo modelo e guardada no servidor. {@code parametrosJson} não muda depois de
 * criado: o confirmar executa exatamente isto. {@code camposJson} é o resumo mostrado ao usuário,
 * gerado dos mesmos parâmetros.
 */
public record AcaoPendente(
        String id,
        String conversaId,
        String mensagemId,
        String username,
        String tipo,
        String parametrosJson,
        String titulo,
        String camposJson,
        EstadoAcao estado,
        Instant criadaEm,
        Instant expiraEm,
        String resultado,
        String linksJson,
        Instant executadaEm) {

    public boolean vencida(Instant agora) {
        return estado == EstadoAcao.PENDENTE && !agora.isBefore(expiraEm);
    }

    /** Estado como o front deve ver agora: pendente vencida aparece como expirada. */
    public EstadoAcao estadoEm(Instant agora) {
        return vencida(agora) ? EstadoAcao.EXPIRADA : estado;
    }

    public AcaoPendente comEstado(EstadoAcao novo, String novoResultado, String novosLinks, Instant quando) {
        return new AcaoPendente(id, conversaId, mensagemId, username, tipo, parametrosJson, titulo, camposJson,
                novo, criadaEm, expiraEm, novoResultado, novosLinks, quando);
    }
}
