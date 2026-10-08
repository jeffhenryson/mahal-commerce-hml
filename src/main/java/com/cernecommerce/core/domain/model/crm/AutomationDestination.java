package com.cernecommerce.core.domain.model.crm;

/** Para onde a automação entrega o disparo. */
public enum AutomationDestination {
    /** POST no {@code webhookUrl} da própria automação, com os headers de autenticação dela. */
    WEBHOOK,
    /** POST em {@code {baseUrl}/{workflowPath}} da plataforma de automação (n8n/Make) da tela de Integrações. */
    PLATAFORMA,
    /** Template aprovado na WhatsApp Cloud API, para o WhatsApp do cliente. */
    WHATSAPP_META
}
