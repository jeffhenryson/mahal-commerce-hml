package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.pdv.ProporcaoMistura;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * PDV-F058 — edição da sessão ainda não entregue: a configuração completa (é PUT), no formato do
 * {@link AddSessionRequest} sem modo, duplo e pagar no final. O preço é sempre resolvido pelo servidor.
 */
@Data
public class EditSessionRequest {

    @Schema(description = "Faixa da sessão. Ignorada quando a essência do catálogo tem faixa na ficha "
            + "(PDV-F050).", example = "1")
    private Long tierId;

    @Size(max = 150)
    @Schema(description = "Marca e sabor da essência, texto livre. Obrigatório se não vier essenciaSku.",
            example = "Zomo Blueberry")
    private String essencia;

    @Size(max = 50)
    @Schema(description = "SKU do sabor no catálogo. Mudando, devolve o uso da lata antiga e consome a nova.",
            example = "ZOMO-BLUE")
    private String essenciaSku;

    @Size(max = 50)
    @Schema(description = "2º sabor da mescla (PDV-F051).")
    private String essenciaSku2;

    @Schema(description = "Quanto de cada sabor vai na mescla; só vale com essenciaSku2.", example = "MEIO_A_MEIO")
    private ProporcaoMistura proporcao;

    @Schema(description = "Vaso grande. Mudando, libera um vaso e aloca o outro (409 SESSION_ASSET_UNAVAILABLE).")
    private boolean vasoGrande;

    @Schema(description = "Carvão da sessão.")
    private Charcoal carvao;

    @Schema(description = "PDV-F057 — jumbo de cortesia. Exige PDV_COMANDA_COURTESY quando true.")
    private boolean carvaoCortesia;

    @Schema(description = "Adicionais pagos; id repetido cobra duas vezes.")
    private List<Long> adicionalIds;
}
