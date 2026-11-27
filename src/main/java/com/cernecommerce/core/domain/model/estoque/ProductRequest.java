package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.exception.estoque.InvalidProductRequestTransitionException;
import com.cernecommerce.core.domain.exception.estoque.SkuRequiredForArrivedException;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Pedido de cliente: um produto que a loja <b>não vende</b> e que clientes pediram no balcão. O item agrupa
 * N solicitações ({@link ProductRequestEntry}, uma por cliente) e segue o ciclo de
 * {@link ProductRequestStatus}. É da loja inteira, sem depósito.
 *
 * <p>{@code linkedSku} é o produto do catálogo quando ele passa a existir. Fica sem FK, como o resto do
 * estoque (EST-C011). {@code version} é o {@code @Version} da linha: duas edições simultâneas não se
 * sobrescrevem (409 {@code PRODUCT_REQUEST_CONFLICT}).</p>
 */
public record ProductRequest(
        Long id,
        String description,
        String brand,
        String variation,
        Long categoryId,
        String categoryOther,
        String normalizedKey,
        ProductRequestStatus status,
        String linkedSku,
        LocalDate expectedAt,
        String supplierNote,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        long version) {

    public ProductRequest {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description é obrigatória");
        }
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório");
        }
        if (createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy é obrigatório");
        }
        description = description.trim();
        brand = blankToNull(brand);
        variation = blankToNull(variation);
        categoryOther = blankToNull(categoryOther);
        supplierNote = blankToNull(supplierNote);
        linkedSku = blankToNull(linkedSku);
        if (categoryId != null && categoryOther != null) {
            throw new IllegalArgumentException("categoryId e categoryOther são exclusivos");
        }
        normalizedKey = ProductRequestKeys.normalize(description, brand, variation);
    }

    public static ProductRequest create(String description, String brand, String variation, Long categoryId,
            String categoryOther, String createdBy, Instant now) {
        return new ProductRequest(null, description, brand, variation, categoryId, categoryOther, null,
                ProductRequestStatus.PENDING, null, null, null, createdBy, now, now, null, 0L);
    }

    public boolean isOpen() {
        return status.isOpen();
    }

    /** @throws InvalidProductRequestTransitionException item fechado */
    public void requireOpen() {
        if (!isOpen()) {
            throw new InvalidProductRequestTransitionException(status);
        }
    }

    /**
     * Edição dos dados descritivos. Já chega resolvida pelo serviço: aqui o valor é o final.
     */
    public ProductRequest withDetails(String newDescription, String newBrand, String newVariation, Long newCategoryId,
            String newCategoryOther, LocalDate newExpectedAt, String newSupplierNote, Instant now) {
        requireOpen();
        return new ProductRequest(id, newDescription, newBrand, newVariation, newCategoryId, newCategoryOther, null,
                status, linkedSku, newExpectedAt, newSupplierNote, createdBy, createdAt, now, closedAt, version);
    }

    /**
     * Muda o status validando a matriz. {@code expectedAt}/{@code supplierNote} só são aplicados quando não
     * nulos (a encomenda costuma trazê-los). Ir para {@code ARRIVED} exige o SKU vinculado ou
     * {@code withoutSku} (o produto não vai ao catálogo).
     */
    public ProductRequest transitionTo(ProductRequestStatus target, LocalDate newExpectedAt, String newSupplierNote,
            boolean withoutSku, Instant now) {
        if (target == null || !status.canMoveTo(target)) {
            throw new InvalidProductRequestTransitionException(status, target);
        }
        if (target == ProductRequestStatus.ARRIVED && linkedSku == null && !withoutSku) {
            throw new SkuRequiredForArrivedException(id);
        }
        return new ProductRequest(id, description, brand, variation, categoryId, categoryOther, null, target,
                linkedSku, newExpectedAt != null ? newExpectedAt : expectedAt,
                newSupplierNote != null ? newSupplierNote : supplierNote, createdBy, createdAt, now,
                target.isFinal() ? now : null, version);
    }

    /** Vincula (ou, com {@code null}, desvincula) o SKU do catálogo. Só item aberto; não mexe em estoque. */
    public ProductRequest withLinkedSku(String sku, Instant now) {
        requireOpen();
        return new ProductRequest(id, description, brand, variation, categoryId, categoryOther, null, status, sku,
                expectedAt, supplierNote, createdBy, createdAt, now, closedAt, version);
    }

    /** Só marca a alteração (nova solicitação, aviso): é o que faz o {@code @Version} pegar a corrida. */
    public ProductRequest touch(Instant now) {
        return new ProductRequest(id, description, brand, variation, categoryId, categoryOther, null, status,
                linkedSku, expectedAt, supplierNote, createdBy, createdAt, now, closedAt, version);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
