package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.estoque.MeasurementUnit;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Alteração parcial de produto (EST-F018). Campo ausente ou nulo significa <b>não mexer</b>;
 * por isso nenhum é {@code @NotBlank} — mas se vier, precisa ser válido.
 *
 * <p>{@code sku} e {@code variants} não estão aqui de propósito: o SKU é a identidade
 * referenciada como texto livre pelas tabelas de estoque, e mexer na grade de variações precisa
 * da validação de duplicidade de {@code POST /estoque/products}.</p>
 *
 * <p>{@code onSale} é {@code Boolean} (wrapper), não {@code boolean} — é um toggle puro (mesma
 * natureza de {@code active}/{@code lotTracked}, que têm PATCH dedicado), sem valor "vazio"
 * natural para representar "não mexer" como o {@code null} de String faz para os demais campos
 * deste DTO. Aqui nulo mantém, e {@code true}/{@code false} explícitos trocam.</p>
 */
@Data
public class ProductPatchRequest {

    @Size(min = 1, max = 255)
    private String name;

    @Size(max = 100)
    private String category;

    /**
     * Vínculo direto com uma categoria existente. Quando informado, vence sobre {@code category}.
     * Nulos nos dois campos mantêm a categoria atual.
     */
    private Long categoryId;

    @Size(max = 100)
    private String brand;

    /**
     * Vínculo direto com uma marca existente. Quando informado, vence sobre {@code brand}.
     * Nulos nos dois campos mantêm a marca atual.
     */
    private Long brandId;

    /** Estágio 01 do admin — link de imagem cadastrado manualmente. Nulo mantém a atual. */
    @Size(max = 2048)
    private String imageUrl;

    /** Estágio 01 do admin — produto em promoção. Nulo mantém; {@code true}/{@code false} troca. */
    private Boolean onSale;

    /** Selo de destaque distinto de {@code onSale}. Nulo mantém; {@code true}/{@code false} troca. */
    private Boolean superPromo;

    /** Descrição longa do produto. Nulo mantém a atual. */
    @Size(max = 5000)
    private String description;

    /** Link de vídeo cadastrado manualmente. Nulo mantém o atual. */
    @Size(max = 2048)
    private String videoUrl;

    /**
     * Galeria de até 5 imagens ordenadas. Nulo mantém a galeria atual; uma lista (mesmo vazia)
     * substitui a galeria inteira — sem edição parcial de item individual.
     */
    @Size(max = 5)
    private List<@Size(max = 2048) String> images;

    /**
     * Atributos descritivos do próprio produto. Nulo mantém os atuais; uma lista (mesmo vazia)
     * substitui o conjunto inteiro — mesma semântica de {@code images}, e pelo mesmo motivo: não
     * há identidade estável por atributo que permitisse edição item a item.
     */
    @Valid
    @Size(max = 20)
    private List<ProductAttributeRequest> attributes;

    /**
     * Precificação (EST-F019). Ausente, mantém a atual; presente, cada um dos seus campos segue
     * a mesma regra de "nulo mantém". Exige {@code ESTOQUE_PRODUCT_PRICE_MANAGE} — mandar este
     * bloco sem a permissão é 403, e nada do PATCH é aplicado.
     */
    @Valid
    private PricingRequest pricing;

    /** Código de barras/EAN. Nulo mantém o atual; vazio ({@code ""}) apaga (PDV-F045). */
    @Pattern(regexp = "^$|^[0-9]{8,14}$", message = "barcode deve ter entre 8 e 14 dígitos numéricos, ou vazio para apagar")
    private String barcode;

    /** Unidade de medida. Nulo mantém a atual. */
    private MeasurementUnit unit;

    /** Testador/amostra. Nulo mantém; {@code true}/{@code false} troca. */
    private Boolean sampleProduct;

    /** Elegível a entrar como componente de kit. Nulo mantém; {@code true}/{@code false} troca. */
    private Boolean kitComponentEligible;

    /** Aparece no PDV. Nulo mantém; {@code true}/{@code false} troca. */
    private Boolean visibleInPos;

    /** Aparece no marketplace/app. Nulo mantém; {@code true}/{@code false} troca. */
    private Boolean visibleInMarketplace;

    /** Pode ser lançado numa comanda de mesa (PDV-F010). Nulo mantém; {@code true}/{@code false} troca. */
    private Boolean availableForTable;

    /** Vendido por sessão de mesa, não por unidade (PDV-F010). Nulo mantém; {@code true}/{@code false} troca. */
    private Boolean sessionProduct;

    /**
     * Quantas sessões saem de uma unidade da origem. Nulo mantém.
     *
     * <p>Consequência da semântica de PATCH deste DTO ("nulo mantém"): não há como <b>limpar</b> o
     * campo por aqui, só trocá-lo — mesmo custo já documentado para {@code category}/{@code brand}
     * em {@code Product.patched}.</p>
     */
    @Positive(message = "sessionsPerUnit deve ser maior que zero")
    private Integer sessionsPerUnit;

    /** Preço do consumo livre (open rosh). Nulo mantém — mesma limitação de {@code sessionsPerUnit}. */
    @PositiveOrZero(message = "openRoshPrice não pode ser negativo")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal openRoshPrice;

    /** PDV-F048 — faixa da sessão sugerida no PDV. Nulo mantém; para apagar, {@code clearSessionTier}. */
    private Long sessionTierId;

    /** PDV-F048 — {@code true} apaga a faixa da sessão (vence {@code sessionTierId}). */
    private Boolean clearSessionTier;

    /**
     * Status de publicação (EST-F023). Nulo mantém o atual. Promover {@code RASCUNHO → ATIVO} não
     * exige nenhum campo adicional — a validação de {@code ATIVO} é a mesma de hoje (só
     * {@code sku}/{@code name}). Rebaixar para {@code RASCUNHO} conta contra o teto de 5, exceto
     * quando o produto já era rascunho (editar um rascunho não conta duas vezes).
     */
    private ProductStatus status;

    /**
     * Contraparte de {@code ProductRequest.touchesPricing()}, usada pelo mesmo
     * {@code @PreAuthorize}. Aqui só existe o bloco da raiz, porque o PATCH não mexe em
     * {@code variants} — mas o método existe para as duas rotas compartilharem literalmente a
     * mesma expressão de permissão, em vez de duas parecidas que podem divergir.
     */
    public boolean touchesPricing() {
        return pricing != null;
    }
}
