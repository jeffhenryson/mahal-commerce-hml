package com.cernecommerce.core.domain.model.compras;

import java.util.List;

/**
 * Resultado puro do parsing de um XML de NF-e (EST-F005) — antes de qualquer persistência ou
 * resolução de fornecedor. Produzido por {@code NfeXmlImportPort.parse}.
 */
public record NfeImportPreview(String emitterCnpj, List<NfeImportLine> lines, String accessKey) {

    public NfeImportPreview(String emitterCnpj, List<NfeImportLine> lines) {
        this(emitterCnpj, lines, null);
    }

    public NfeImportPreview {
        if (emitterCnpj == null || emitterCnpj.isBlank()) {
            throw new IllegalArgumentException("emitterCnpj é obrigatório");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines não pode ser vazio");
        }
        lines = List.copyOf(lines);
    }
}
