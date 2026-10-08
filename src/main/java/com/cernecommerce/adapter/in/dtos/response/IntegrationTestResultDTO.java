package com.cernecommerce.adapter.in.dtos.response;

/**
 * Resultado do teste de uma integração. Recusa do provedor volta com {@code success = false} e a
 * mensagem dele em {@code error}; {@code detail} traz o wamid (WhatsApp) ou o status HTTP (n8n/Make).
 */
public record IntegrationTestResultDTO(boolean success, String error, String detail) { }
