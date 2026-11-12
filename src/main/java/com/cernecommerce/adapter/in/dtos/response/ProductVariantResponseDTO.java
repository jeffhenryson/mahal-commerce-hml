package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.util.List;

@Data
public class ProductVariantResponseDTO {
    private Long id;
    private String sku;
    private boolean active;
    private List<ProductAttributeResponseDTO> attributes;

    /**
     * Precificação própria da variação (EST-F020). <b>Nula quando a variação herda o preço do
     * pai</b> — a ausência aqui é informação, não omissão: é assim que o cliente distingue
     * "tem preço próprio" de "usa o do pai".
     */
    private PricingResponseDTO pricing;

    /** Código de barras/EAN próprio da variação, quando cadastrado. */
    private String barcode;

    /** PDV-F061 — já resolvido: a marcação da variação ou, sem ela, a do produto. */
    private boolean sessionCompraEssencia;

    /** PDV-F061 — já resolvido: a marcação da variação ou, sem ela, a do produto. */
    private boolean sessionItensPessoais;
}
