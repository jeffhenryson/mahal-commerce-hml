package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.pdv.ProporcaoMistura;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Lançamento de sessão do cardápio na mesa (PDV-F021). O preço é sempre resolvido pelo servidor. */
@Data
public class AddSessionRequest {

    // PDV-F050 — opcional: com essenciaSku de produto com faixa ativa, a faixa é a da ficha e este
    // campo é ignorado. Só vale para a essência sem faixa e para a sessão em texto livre.
    @Schema(description = "Faixa da sessão. Ignorada quando a essência do catálogo tem faixa na ficha "
            + "(PDV-F050); obrigatória para sessão em texto e essência sem faixa (400 SESSION_TIER_REQUIRED).",
            example = "2")
    private Long tierId;

    // Sem @NotBlank desde PDV-F042: texto OU essenciaSku — conferido no controller, com o mesmo
    // SESSION_ESSENCE_REQUIRED de sempre.
    @Size(max = 150)
    @Schema(description = "Marca e sabor da essência, texto livre. Obrigatório se não vier essenciaSku; "
            + "vazio com essenciaSku usa o nome do produto.", example = "Zomo Blueberry")
    private String essencia;

    @Size(max = 50)
    @Schema(description = "PDV-F042 — SKU do sabor no catálogo (a variação, não o produto base). Opcional: "
            + "com ele a sessão consome um uso da lata aberta do sabor, ou 1 unidade como uso da loja se o "
            + "produto não tiver sessionsPerUnit.", example = "ZOMO-BLUEBERRY")
    private String essenciaSku;

    @Size(max = 50)
    @Schema(description = "PDV-F051 — 2º sabor da sessão mesclada (exige o 1º e tem de ser outro: 400 "
            + "SAME_FLAVOR_MIX). Consome 1 uso da lata de cada sabor; a faixa é a de maior preço entre os dois.",
            example = "ZIGY-MENTA")
    private String essenciaSku2;

    @Schema(description = "Quanto de cada sabor vai na mescla; o 1º é o principal. Só vale com essenciaSku2 "
            + "(sem ele é ignorada). Não muda o preço.", example = "SETENTA_TRINTA")
    private ProporcaoMistura proporcao;

    @Schema(description = "Upgrade para o vaso grande (acréscimo da configuração).")
    private boolean vasoGrande;

    @Schema(description = "Carvão da sessão (PDV-F024). JUMBO soma upgradeCarvaoJumboPreco ao preço.")
    private Charcoal carvao;

    @Schema(description = "PDV-F057 — o jumbo sai de cortesia: o acréscimo não entra no subtotal. Só vale "
            + "com carvao=JUMBO (com outro carvão é ignorado). Exige PDV_COMANDA_COURTESY (403 "
            + "COURTESY_NOT_ALLOWED).")
    private boolean carvaoCortesia;

    @Schema(description = "Adicionais pagos (GET /pdv/sessao/cardapio → adicionais). Somados ao preço "
            + "da sessão; id repetido cobra duas vezes (PDV-F024).")
    private List<Long> adicionalIds;

    @Schema(description = "NORMAL (padrão) ou DUPLO: no duplo o servidor cria na mesma transação a "
            + "sessão e o 2º rosh a R$ 0, ligado a ela e em NA_FILA (PDV-F024).", defaultValue = "NORMAL")
    private Modo modo;

    @Size(max = 150)
    @Schema(description = "Essência do 2º rosh no DUPLO. Opcional desde PDV-F044: sem ela e sem essenciaRoshSku, o 2º rosh nasce aguardando sabor (PUT …/rosh-sabor).", example = "Nay Uva")
    private String essenciaRosh;

    @Size(max = 50)
    @Schema(description = "PDV-F042 — SKU do sabor do 2º rosh no DUPLO.", example = "NAY-UVA")
    private String essenciaRoshSku;

    @Size(max = 50)
    @Schema(description = "PDV-F051 — 2º sabor do 2º rosh no DUPLO (mescla).", example = "NAY-MENTA")
    private String essenciaRoshSku2;

    @Schema(description = "Faixa do 2º rosh no DUPLO, ignorada se o sabor do rosh tiver faixa (PDV-F050); "
            + "nula usa a da sessão.")
    private Long tierIdRosh;

    @Schema(description = "PDV-F034 — a sessão vai direto ao preparo e fica a receber até a conta. Exige "
            + "PDV_SESSION_PAY_LATER (403 SESSION_PAY_LATER_NOT_ALLOWED). O front avisa o risco antes.")
    private boolean pagarNoFinal;

    public enum Modo { NORMAL, DUPLO }

    public boolean isDuplo() {
        return modo == Modo.DUPLO;
    }
}
