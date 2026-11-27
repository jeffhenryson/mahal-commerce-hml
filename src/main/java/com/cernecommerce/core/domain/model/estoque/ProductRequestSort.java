package com.cernecommerce.core.domain.model.estoque;

/** Campos de ordenação da listagem de pedidos de clientes ({@code sort=campo,asc|desc}). */
public enum ProductRequestSort {
    CREATED_AT("createdAt"),
    ENTRIES_COUNT("entriesCount"),
    TOTAL_QUANTITY("totalQuantity"),
    MAX_URGENCY("maxUrgency");

    private final String apiName;

    ProductRequestSort(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }

    /** {@code null} se o nome não é um campo aceito. */
    public static ProductRequestSort fromApiName(String name) {
        for (ProductRequestSort s : values()) {
            if (s.apiName.equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }
}
