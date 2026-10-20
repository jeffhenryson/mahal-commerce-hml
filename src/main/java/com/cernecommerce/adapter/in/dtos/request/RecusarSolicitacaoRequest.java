package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.Size;

/** Motivo opcional, mostrado ao lead na página do QR. */
public record RecusarSolicitacaoRequest(@Size(max = 200) String motivo) {
}
