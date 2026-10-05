package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.config.EmailSample;

/** Resultado de um e-mail de teste; {@code error} traz a resposta do provedor quando falha. */
public record EmailTestResultDTO(EmailSample sample, boolean success, String error) { }
