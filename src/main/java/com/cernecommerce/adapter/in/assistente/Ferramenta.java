package com.cernecommerce.adapter.in.assistente;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.function.Function;

/**
 * Ferramenta curada oferecida ao modelo. Roda na thread do turno com o {@code SecurityContext} do
 * usuário, chamando os controllers — a mesma camada que aplica {@code @PreAuthorize} e publica a
 * auditoria. Nada de use case direto nem conta de serviço.
 *
 * @param rotulo o que o front mostra enquanto roda ("Consultando pedidos")
 * @param schemaJson JSON Schema de objeto com {@code properties} e {@code required}
 */
public record Ferramenta(String nome, String rotulo, String descricao, String schemaJson,
        Function<JsonNode, ResultadoFerramenta> executar) { }
