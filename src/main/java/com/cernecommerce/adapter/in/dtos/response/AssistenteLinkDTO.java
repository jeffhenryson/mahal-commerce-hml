package com.cernecommerce.adapter.in.dtos.response;

/** Link para uma tela do admin. Só rotas {@code /app/...}; o servidor descarta o resto. */
public record AssistenteLinkDTO(String rotulo, String rota) { }
