package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * {@code contato} e {@code email} são opcionais desde CRM-C005 — só {@code nome} e ao menos um
 * identificador (cpf, email ou contato) são exigidos. Corpo sem nenhum dos três responde
 * {@code 400 BAD_REQUEST}, checado no compact constructor de {@code Customer}: Bean Validation não
 * expressa bem "pelo menos um destes campos" sem um validador próprio.
 */
@Data
public class CustomerRequest {
    @NotBlank
    @Size(max = 255)
    private String nome;

    @Size(max = 30)
    @Schema(description = "Telefone/contato. Opcional se email ou cpf forem informados.")
    private String contato;

    @Email
    @Size(max = 255)
    @Schema(description = "Opcional se contato ou cpf forem informados.")
    private String email;

    // CRM-C006: aceita máscara ("123.456.789-00"); o serviço normaliza para 11 dígitos e recusa
    // com 400 o que não fechar 11 dígitos. Vazio vira nulo.
    @Size(max = 14)
    @Schema(description = "Identificador OFICIAL do cadastro, com ou sem máscara. Sem CPF, o cliente é "
            + "\"leve\": válido e pesquisável, mas não elegível a cashback quando o programa existir.")
    private String cpf;

    @Size(max = 100)
    private String origem;

    // CRM-F012 — a regra de idade (18 a 120 anos) é do serviço: 400 CUSTOMER_UNDERAGE.
    @Past
    @Schema(description = "CRM-F012 — data de nascimento (yyyy-MM-dd). Opcional; menor de 18 anos responde "
            + "400 CUSTOMER_UNDERAGE. No PUT, omitida mantém a gravada. No lead, só completa um cliente que "
            + "ainda não tem data — nunca sobrescreve.", example = "1990-05-17")
    private LocalDate dataNascimento;
}
