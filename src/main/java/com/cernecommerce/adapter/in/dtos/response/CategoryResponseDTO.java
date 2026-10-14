package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CategoryResponseDTO {
    private Long id;
    private String name;

    /** Categoria em destaque — a vitrine a coloca na primeira linha. */
    private boolean featured;

    /** Posição relativa dentro do grupo; destacadas e não destacadas ordenam separadamente. */
    private int displayOrder;

    private boolean active;

    /** PDV-F045 — categoria de cigarros: vendida pela Central de cigarros, fora da grade do PDV. */
    private boolean cigarette;

    /** Produtos vinculados a esta categoria (Bloco 2.1) — evita a varredura de catálogo no admin. */
    private long productCount;

    /**
     * Margem média (sobre a venda) dos produtos vinculados, ou nula se nenhum produto vinculado
     * tem precificação suficiente para calcular margem. Mesma fórmula de
     * {@code Pricing#marginPercent()}, promediada por produto.
     */
    private BigDecimal averageMarginPercent;
}
