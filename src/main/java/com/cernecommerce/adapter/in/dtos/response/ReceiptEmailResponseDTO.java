package com.cernecommerce.adapter.in.dtos.response;

/** Para onde o comprovante foi enviado, mascarado (ex.: {@code jo***@gmail.com}). */
public record ReceiptEmailResponseDTO(String sentTo) { }
