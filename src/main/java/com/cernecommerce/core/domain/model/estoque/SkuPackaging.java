package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Embalagem (EST-F032): o SKU {@code parentSku} contém {@code unitsPerParent} do SKU
 * {@code childSku} — a carteira contém 10 maços, o maço contém 20 cigarros.
 *
 * <p>Cada nível é um SKU próprio, com saldo, preço e código de barras próprios — o saldo de cada um
 * é o que está fisicamente na prateleira (carteiras lacradas, maços lacrados, cigarros soltos). A
 * ligação é o que permite à saída "abrir" uma embalagem pai quando o filho acaba, sem o operador
 * converter à mão.</p>
 *
 * <p>Um filho tem um único pai; a cadeia é limitada e sem ciclo — regras do service, que enxerga a
 * cadeia inteira. Aqui ficam só as invariantes da ligação isolada.</p>
 */
public record SkuPackaging(String childSku, String parentSku, int unitsPerParent) {

    public SkuPackaging {
        if (childSku == null || childSku.isBlank()) {
            throw new IllegalArgumentException("childSku é obrigatório");
        }
        if (parentSku == null || parentSku.isBlank()) {
            throw new IllegalArgumentException("parentSku é obrigatório");
        }
        if (childSku.equals(parentSku)) {
            throw new IllegalArgumentException("uma embalagem não contém a si mesmo: " + childSku);
        }
        // Fator 1 não é embalagem, é o mesmo item com outro nome — e ainda faria a quebra abrir uma
        // embalagem por unidade vendida.
        if (unitsPerParent <= 1) {
            throw new IllegalArgumentException("unitsPerParent deve ser maior que 1: " + unitsPerParent);
        }
    }

    /** Quantas embalagens pai abrir para cobrir {@code missingChildUnits}: para cima — não se abre meia. */
    public BigDecimal parentsToOpen(BigDecimal missingChildUnits) {
        return missingChildUnits.divide(BigDecimal.valueOf(unitsPerParent), 0, RoundingMode.CEILING);
    }

    /** Quantas unidades do filho saem de {@code parents} embalagens pai abertas. */
    public BigDecimal childUnits(BigDecimal parents) {
        return parents.multiply(BigDecimal.valueOf(unitsPerParent));
    }
}
