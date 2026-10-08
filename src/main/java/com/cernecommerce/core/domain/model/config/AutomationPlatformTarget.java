package com.cernecommerce.core.domain.model.config;

/**
 * Para onde vão os workflows: {@code {baseUrl}/{workflowPath}}, com o token como Bearer.
 * {@code token} pode ser nulo (plataforma sem autenticação).
 */
public record AutomationPlatformTarget(String baseUrl, String token) {

    public String urlFor(String workflowPath) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = workflowPath.startsWith("/") ? workflowPath.substring(1) : workflowPath;
        return base + "/" + path;
    }

    @Override
    public String toString() {
        return "AutomationPlatformTarget[baseUrl=" + baseUrl + ", token=" + (token == null ? "null" : "***") + "]";
    }
}
