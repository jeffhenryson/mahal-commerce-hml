package com.cernecommerce.core.domain.model.compras;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma linha (item) de uma NF-e importada (EST-F005).
 *
 * <p>{@link #matchStatus} é <b>derivado</b> de {@link #matchedSku}, não um campo independente:
 * uma linha é {@code MATCHED} exatamente quando tem SKU resolvido, seja pelo casamento automático
 * por EAN ({@link #fromXml}) seja por override manual do operador no fechamento
 * ({@link #withMatchedSku}). Não existe um terceiro estado — evita a inconsistência de uma linha
 * "UNMATCHED com matchedSku preenchido" ou vice-versa.</p>
 *
 * @param itemNumber ordinal do item na NF-e ({@code nItem}) — estável dentro do mesmo ciclo
 *        preview/confirm, usado para casar overrides manuais
 * @param supplierProductCode código do produto no catálogo do fornecedor ({@code cProd}) — só
 *        identificador de exibição, nunca usado para casar SKU
 * @param ean código de barras da NF-e ({@code cEAN}), ou {@code null} quando ausente/"SEM GTIN"
 * @param quantity quantidade do item ({@code qCom})
 * @param unitPrice preço unitário de aquisição ({@code vUnCom}) — vira {@code unitCost} do
 *        recebimento, não preço de venda
 * @param lotCode lote ({@code rastro/nLote}), quando a NF-e declara rastreabilidade
 * @param expiryDate validade ({@code rastro/dVal}), idem
 * @param matchedSku SKU interno resolvido — por EAN na importação, ou por override manual no
 *        fechamento. {@code null} enquanto a linha não foi casada com nenhum SKU
 */
public record NfeImportLine(
        Long id,
        int itemNumber,
        String supplierProductCode,
        String ean,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        String lotCode,
        LocalDate expiryDate,
        MatchStatus matchStatus,
        String matchedSku,
        String unit,
        String taxUnit,
        BigDecimal taxQuantity,
        Integer unitsPerPackage) {

    public enum MatchStatus { MATCHED, UNMATCHED }

    public NfeImportLine {
        if (itemNumber <= 0) {
            throw new IllegalArgumentException("itemNumber deve ser maior que zero");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice é obrigatório e não pode ser negativo");
        }
        if (matchStatus == null) {
            throw new IllegalArgumentException("matchStatus é obrigatório");
        }
        if (unitsPerPackage != null && unitsPerPackage < 1) {
            throw new IllegalArgumentException("unitsPerPackage deve ser pelo menos 1");
        }
        if ((matchStatus == MatchStatus.MATCHED) != (matchedSku != null)) {
            throw new IllegalArgumentException(
                    "matchStatus e matchedSku têm que coexistir: matchStatus=" + matchStatus
                            + ", matchedSku=" + matchedSku);
        }
    }

    /**
     * Monta a linha a partir do XML parseado, já com o casamento automático por EAN resolvido
     * (ou não) — {@code matchedSku} vem de {@code EstoqueUseCase.findProductByBarcode(ean)} quando
     * há EAN, {@code null} caso contrário.
     */
    public static NfeImportLine fromXml(int itemNumber, String supplierProductCode, String ean,
            String description, BigDecimal quantity, BigDecimal unitPrice, String lotCode,
            LocalDate expiryDate, String matchedSku) {
        return fromXml(itemNumber, supplierProductCode, ean, description, quantity, unitPrice, lotCode, expiryDate,
                matchedSku, null, null, null);
    }

    /** Com a unidade comercial ({@code uCom}) e a tributável ({@code uTrib}/{@code qTrib}) da nota. */
    public static NfeImportLine fromXml(int itemNumber, String supplierProductCode, String ean,
            String description, BigDecimal quantity, BigDecimal unitPrice, String lotCode,
            LocalDate expiryDate, String matchedSku, String unit, String taxUnit, BigDecimal taxQuantity) {
        return new NfeImportLine(null, itemNumber, supplierProductCode, ean, description, quantity,
                unitPrice, lotCode, expiryDate, matchedSku != null ? MatchStatus.MATCHED : MatchStatus.UNMATCHED,
                matchedSku, unit, taxUnit, taxQuantity, null);
    }

    /** Reconstitui uma linha a partir de persistência. */
    public static NfeImportLine of(Long id, int itemNumber, String supplierProductCode, String ean,
            String description, BigDecimal quantity, BigDecimal unitPrice, String lotCode,
            LocalDate expiryDate, MatchStatus matchStatus, String matchedSku) {
        return new NfeImportLine(id, itemNumber, supplierProductCode, ean, description, quantity, unitPrice,
                lotCode, expiryDate, matchStatus, matchedSku, null, null, null, null);
    }

    /** Reconstitui com unidade e fator (V182). */
    public static NfeImportLine of(Long id, int itemNumber, String supplierProductCode, String ean,
            String description, BigDecimal quantity, BigDecimal unitPrice, String lotCode,
            LocalDate expiryDate, MatchStatus matchStatus, String matchedSku, String unit, String taxUnit,
            BigDecimal taxQuantity, Integer unitsPerPackage) {
        return new NfeImportLine(id, itemNumber, supplierProductCode, ean, description, quantity, unitPrice,
                lotCode, expiryDate, matchStatus, matchedSku, unit, taxUnit, taxQuantity, unitsPerPackage);
    }

    /** Fator de embalagem: unidades de estoque por unidade comercial da nota. Nulo (não informado) vale 1. */
    public NfeImportLine withUnitsPerPackage(Integer factor) {
        return new NfeImportLine(id, itemNumber, supplierProductCode, ean, description, quantity, unitPrice,
                lotCode, expiryDate, matchStatus, matchedSku, unit, taxUnit, taxQuantity, factor);
    }

    /** Fator efetivo da linha. */
    public int effectiveUnitsPerPackage() {
        return unitsPerPackage == null ? 1 : unitsPerPackage;
    }

    /** Quantidade que entra no estoque: a da nota vezes o fator. */
    public BigDecimal stockQuantity() {
        return quantity.multiply(BigDecimal.valueOf(effectiveUnitsPerPackage()));
    }

    /**
     * Custo por unidade de estoque: {@code vUnCom} dividido pelo fator, a 2 casas (a mesma escala de
     * {@code stock_movement.unit_cost}).
     */
    public BigDecimal stockUnitCost() {
        int factor = effectiveUnitsPerPackage();
        return factor == 1 ? unitPrice
                : unitPrice.divide(BigDecimal.valueOf(factor), 2, java.math.RoundingMode.HALF_UP);
    }

    /** Aplica um override manual do operador — a linha passa a {@code MATCHED}. */
    public NfeImportLine withMatchedSku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku do override não pode ser vazio");
        }
        return new NfeImportLine(id, itemNumber, supplierProductCode, ean, description, quantity, unitPrice,
                lotCode, expiryDate, MatchStatus.MATCHED, sku, unit, taxUnit, taxQuantity, unitsPerPackage);
    }
}
