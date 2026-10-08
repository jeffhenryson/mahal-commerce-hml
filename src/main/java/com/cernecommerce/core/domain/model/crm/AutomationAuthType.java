package com.cernecommerce.core.domain.model.crm;

/** Como a automação se autentica no webhook próprio (destino {@link AutomationDestination#WEBHOOK}). */
public enum AutomationAuthType {
    NONE,
    /** {@code Authorization: Bearer <segredo>}. */
    BEARER,
    /** Header customizado ({@code authHeaderNome}: segredo). */
    HEADER
}
