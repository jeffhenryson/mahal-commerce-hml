package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Mensagem do usuário e onde ele está no admin (só o path e o título da tela). */
@Data
public class AssistenteMensagemRequest {
    @NotBlank
    @Size(max = 4000)
    private String texto;

    @Valid
    private Contexto contexto;

    /**
     * "Gerar de novo" / "editar e reenviar": id da última pergunta. Ela e tudo depois dela são
     * apagados antes do novo turno.
     */
    @Size(max = 36)
    private String substituirDesde;

    @Data
    public static class Contexto {
        @Size(max = 300)
        private String rota;
        @Size(max = 200)
        private String titulo;
    }
}
