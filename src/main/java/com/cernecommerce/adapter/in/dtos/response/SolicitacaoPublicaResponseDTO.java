package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.salao.SolicitacaoStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * O que a página do lead vê do próprio pedido: o código público, o status, o motivo da recusa e —
 * aceito, e se o aparelho tem acesso à mesa — o código de 4 dígitos da mesa.
 */
public record SolicitacaoPublicaResponseDTO(UUID codigo, SolicitacaoStatus status, String motivo,
        Instant criadoEm, String codigoMesa) {
}
