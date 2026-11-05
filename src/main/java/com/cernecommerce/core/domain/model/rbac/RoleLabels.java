package com.cernecommerce.core.domain.model.rbac;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Nome do papel para quem lê um e-mail. Papel criado pela loja usa o próprio nome, sem o prefixo. */
public final class RoleLabels {

    private static final Map<String, String> KNOWN = Map.of(
            "ROLE_ADMIN", "Administrador",
            "ROLE_ATENDENTE", "Atendente",
            "ROLE_DEV", "Desenvolvedor",
            "ROLE_USER", "Usuário");

    private RoleLabels() {
    }

    public static String of(String roleName) {
        String known = KNOWN.get(roleName);
        if (known != null) {
            return known;
        }
        String text = roleName.replaceFirst("^ROLE_", "").replace('_', ' ').toLowerCase(Locale.ROOT);
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    /** "Administrador", "Administrador e Atendente" ou {@code null} sem papel. */
    public static String join(Collection<Role> roles) {
        List<String> labels = new ArrayList<>(roles.stream().map(r -> of(r.getName())).sorted().toList());
        if (labels.isEmpty()) {
            return null;
        }
        String last = labels.removeLast();
        return labels.isEmpty() ? last : String.join(", ", labels) + " e " + last;
    }
}
