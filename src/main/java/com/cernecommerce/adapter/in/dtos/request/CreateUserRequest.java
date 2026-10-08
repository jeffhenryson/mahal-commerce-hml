package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateUserRequest {
    @NotBlank
    @Size(min = 3, max = 80)
    private String username;

    /** Opcional: sem senha, o usuário é criado por convite e define a senha pelo link enviado ao e-mail. */
    @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
    @Pattern(regexp = PasswordPolicy.COMPLEXITY_REGEXP, message = PasswordPolicy.COMPLEXITY_MESSAGE)
    private String password;

    /** Obrigatório quando {@code password} vem vazio (convite). */
    @Email(message = "Email inválido")
    @Size(max = 254)
    private String email;

    private List<String> roles = new ArrayList<>();
}
