package com.cernecommerce.adapter.in.dtos.response;

/** {@code habilitado}: o módulo existe neste deploy. {@code configurado}: chave salva e integração ativa. */
public record AssistenteStatusDTO(boolean habilitado, boolean configurado) { }
