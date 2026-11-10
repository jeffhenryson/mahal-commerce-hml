package com.cernecommerce.core.domain.exception.estoque;

/**
 * Kit pré-montado não tem receita: o estoque é do kit inteiro. Código {@code KIT_PRE_MONTADO_SEM_RECEITA}:
 * 422 quando a criação manda {@code components} junto com {@code kitMode = PRE_MONTADO}; 409 quando
 * {@code PUT/DELETE .../kit} mira um kit pré-montado existente ({@link #isStateConflict()}).
 */
public class KitPreAssembledNoRecipeException extends RuntimeException {

    private final boolean stateConflict;

    private KitPreAssembledNoRecipeException(String message, boolean stateConflict) {
        super(message);
        this.stateConflict = stateConflict;
    }

    /** Criação de kit pré-montado com {@code components} (422). */
    public static KitPreAssembledNoRecipeException onCreate(String sku) {
        return new KitPreAssembledNoRecipeException(
                "Kit pré-montado não aceita receita (components): " + sku, false);
    }

    /** {@code PUT/DELETE .../kit} num kit pré-montado (409). */
    public static KitPreAssembledNoRecipeException onExisting(String sku) {
        return new KitPreAssembledNoRecipeException(
                "Kit pré-montado não tem receita; o estoque é do kit inteiro: " + sku, true);
    }

    public boolean isStateConflict() {
        return stateConflict;
    }
}
