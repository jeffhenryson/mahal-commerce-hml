package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pdv.Charcoal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank
    @Size(max = 150)
    @Schema(description = "Marca e sabor da essência, texto livre.", example = "Zomo Blueberry")
    private String essencia;

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
    @Schema(description = "Essência do 2º rosh — obrigatória no DUPLO.", example = "Nay Uva")
    private String essenciaRosh;

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
