package com.cernecommerce.core.domain.model.salao;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * A mesa com dono: nasce no envio da 1ª solicitação de uma mesa livre (V171) e vale enquanto a
 * solicitação estiver pendente e, depois do aceite, enquanto a comanda estiver aberta. Só os
 * aparelhos com acesso pedem ou veem a mesa; outro aparelho entra digitando o {@link #codigo}, que o
 * lead recebe já com o pedido pendente e o atendente vê no detalhe da mesa.
 *
 * @param comandaId nula até o aceite
 * @param solicitacaoId a solicitação que ocupou a mesa; nula nas ocupações anteriores à V171
 */
public record MesaOcupacao(Long id, Long mesaId, Long comandaId, Long solicitacaoId, Long customerId,
        String codigo, Instant abertaEm) {

    private static final SecureRandom RANDOM = new SecureRandom();

    public MesaOcupacao {
        if (codigo == null || !codigo.matches("\\d{4}")) {
            throw new IllegalArgumentException("código da mesa tem 4 dígitos");
        }
        if (comandaId == null && solicitacaoId == null) {
            throw new IllegalArgumentException("ocupação sem comanda precisa da solicitação de origem");
        }
    }

    public MesaOcupacao(Long id, Long mesaId, Long comandaId, Long customerId, String codigo, Instant abertaEm) {
        this(id, mesaId, comandaId, null, customerId, codigo, abertaEm);
    }

    public static MesaOcupacao nova(Long mesaId, Long comandaId, Long customerId, Instant agora) {
        return new MesaOcupacao(null, mesaId, comandaId, null, customerId, novoCodigo(), agora);
    }

    /** Ocupada no envio do pedido, antes de haver comanda. */
    public static MesaOcupacao pendente(Long mesaId, Long solicitacaoId, Long customerId, Instant agora) {
        return new MesaOcupacao(null, mesaId, null, solicitacaoId, customerId, novoCodigo(), agora);
    }

    /** Ainda sem comanda: o pedido que ocupou a mesa não foi aceito. */
    public boolean pendente() {
        return comandaId == null;
    }

    public MesaOcupacao withComandaId(Long comandaId) {
        return new MesaOcupacao(id, mesaId, comandaId, solicitacaoId, customerId, codigo, abertaEm);
    }

    private static String novoCodigo() {
        return String.format("%04d", RANDOM.nextInt(10_000));
    }
}
