package com.cernecommerce.core.domain.model.salao;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * A mesa com dono: nasce no aceite da 1ª solicitação de uma mesa livre e vale enquanto a comanda
 * dela estiver aberta. Só os aparelhos com acesso pedem ou veem a mesa; outro aparelho entra
 * digitando o {@link #codigo}, que o lead recebe ao ser aceito e o atendente vê no detalhe da mesa.
 */
public record MesaOcupacao(Long id, Long mesaId, Long comandaId, Long customerId, String codigo, Instant abertaEm) {

    private static final SecureRandom RANDOM = new SecureRandom();

    public MesaOcupacao {
        if (codigo == null || !codigo.matches("\\d{4}")) {
            throw new IllegalArgumentException("código da mesa tem 4 dígitos");
        }
    }

    public static MesaOcupacao nova(Long mesaId, Long comandaId, Long customerId, Instant agora) {
        return new MesaOcupacao(null, mesaId, comandaId, customerId,
                String.format("%04d", RANDOM.nextInt(10_000)), agora);
    }
}
