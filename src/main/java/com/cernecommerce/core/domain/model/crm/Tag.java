package com.cernecommerce.core.domain.model.crm;

/**
 * Tag de segmentação livre do CRM, associável a múltiplos clientes.
 */
public record Tag(Long id, String nome) {

    /**
     * Nome da tag que marca o cliente VIP — elegível ao "Marcar" (CRM-F010). Enquanto não houver
     * campo oficial, VIP é esta tag; a comparação ignora maiúsculas e espaços nas pontas.
     */
    public static final String VIP = "VIP";

    public Tag {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome é obrigatório");
        }
    }

    /** Cria uma nova tag (sem id). */
    public static Tag create(String nome) {
        return new Tag(null, nome);
    }

    /** PDV-F046 — o nome é o da tag VIP. */
    public static boolean isVipName(String nome) {
        return nome != null && VIP.equalsIgnoreCase(nome.trim());
    }

    /** PDV-F046 — esta é a tag VIP. */
    public boolean isVip() {
        return isVipName(nome);
    }

    /** Reconstitui uma tag a partir de persistência. */
    public static Tag of(Long id, String nome) {
        return new Tag(id, nome);
    }
}
