package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;
import java.util.List;

/**
 * O que o cliente escolheu no montador (EST-F031): um SKU por escolha, cada um dentro de um passo.
 * Mais de uma escolha no mesmo passo é permitida até o {@code maxItems} do passo.
 *
 * <p>SKU pode ser o do pai ou o de uma variação — a seda sabor menta é uma variação, e é ela que
 * sai do estoque.</p>
 */
public record KitSelection(Long templateId, List<Pick> picks) {

    public KitSelection {
        if (templateId == null) {
            throw new IllegalArgumentException("templateId é obrigatório");
        }
        picks = picks == null ? List.of() : List.copyOf(picks);
    }

    /**
     * @param note           observação da linha; só a venda de balcão preenche
     * @param discountAmount desconto concedido além do desconto do kit; só a venda de balcão preenche, e ele
     *                       exige a permissão de desconto e conta no teto. Nunca vem do carrinho do cliente.
     */
    public record Pick(Long stepId, String sku, String note, BigDecimal discountAmount) {

        public Pick(Long stepId, String sku) {
            this(stepId, sku, null, null);
        }

        public Pick {
            if (stepId == null) {
                throw new IllegalArgumentException("stepId é obrigatório");
            }
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku é obrigatório");
            }
            sku = sku.trim();
        }
    }
}
