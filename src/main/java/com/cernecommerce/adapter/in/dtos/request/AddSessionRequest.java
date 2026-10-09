package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pdv.Charcoal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Lançamento de sessão do cardápio na mesa (PDV-F021). O preço é sempre resolvido pelo servidor. */
@Data
public class AddSessionRequest {

    @NotNull
    @Schema(example = "2")
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

    @Schema(description = "Upgrade para o vaso grande (acréscimo da configuração).")
    private boolean vasoGrande;

    @Schema(description = "Carvão da sessão, só registro, sem efeito em preço (PDV-F024).")
    private Charcoal carvao;

    @Schema(description = "Adicionais pagos (GET /pdv/sessao/cardapio → adicionais). Somados ao preço "
            + "da sessão; id repetido cobra duas vezes (PDV-F024).")
    private List<Long> adicionalIds;

    @Schema(description = "NORMAL (padrão) ou DUPLO: no duplo o servidor cria na mesma transação a "
            + "sessão e o 2º rosh a R$ 0, ligado a ela e em NA_FILA (PDV-F024).", defaultValue = "NORMAL")
    private Modo modo;

    @Size(max = 150)
    @Schema(description = "Essência do 2º rosh — no DUPLO, ela ou essenciaRoshSku.", example = "Nay Uva")
    private String essenciaRosh;

    @Size(max = 50)
    @Schema(description = "PDV-F042 — SKU do sabor do 2º rosh no DUPLO.", example = "NAY-UVA")
    private String essenciaRoshSku;

    @Schema(description = "Faixa do 2º rosh no DUPLO; nula usa a da sessão.")
    private Long tierIdRosh;

    @Schema(description = "PDV-F034 — a sessão vai direto ao preparo e fica a receber até a conta. Exige "
            + "PDV_SESSION_PAY_LATER (403 SESSION_PAY_LATER_NOT_ALLOWED). O front avisa o risco antes.")
    private boolean pagarNoFinal;

    public enum Modo { NORMAL, DUPLO }

    public boolean isDuplo() {
        return modo == Modo.DUPLO;
    }
}
