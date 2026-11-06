package com.cernecommerce.infra.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O front gera o client a partir deste spec. Três defeitos já custaram tipos escritos à mão por lá:
 * rota sem resposta 2xx (some o schema), resposta em {@code *}{@code /*} (vira blob) e classes
 * aninhadas de mesmo nome (viram um schema só).
 */
@SpringBootTest
@ActiveProfiles("dev")
class OpenApiContractIT {

    /** Só responde 403: o autocadastro está desativado, não há 2xx para declarar. */
    private static final Set<String> SEM_SUCESSO = Set.of("POST /auth/register");
    private static final Set<String> METODOS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired WebApplicationContext context;

    private JsonNode spec;

    @BeforeEach
    void carregaSpec() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        String json = mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs")).andReturn().getResponse()
                .getContentAsString();
        spec = new ObjectMapper().readTree(json);
    }

    @Test
    void todaOperacaoDeclaraUmaRespostaDeSucesso() {
        List<String> semSucesso = new ArrayList<>();
        cadaOperacao((nome, operacao) -> {
            boolean temSucesso = false;
            for (var campos = operacao.path("responses").fieldNames(); campos.hasNext();) {
                temSucesso |= campos.next().startsWith("2");
            }
            if (!temSucesso && !SEM_SUCESSO.contains(nome)) {
                semSucesso.add(nome);
            }
        });
        assertThat(semSucesso).isEmpty();
    }

    @Test
    void nenhumaRespostaSaiComoTipoGenerico() {
        List<String> genericas = new ArrayList<>();
        cadaOperacao((nome, operacao) -> operacao.path("responses").forEach(resposta -> {
            if (resposta.path("content").has("*/*")) {
                genericas.add(nome);
            }
        }));
        assertThat(genericas).isEmpty();
    }

    @Test
    void classesAninhadasNaoColidemNumSchemaGenerico() {
        JsonNode schemas = spec.path("components").path("schemas");
        assertThat(List.of("Item", "Step", "Line", "Payment")).noneMatch(schemas::has);
    }

    private void cadaOperacao(java.util.function.BiConsumer<String, JsonNode> acao) {
        spec.path("paths").properties().forEach(rota -> rota.getValue().properties().forEach(metodo -> {
            if (METODOS.contains(metodo.getKey())) {
                acao.accept(metodo.getKey().toUpperCase() + " " + rota.getKey(), metodo.getValue());
            }
        }));
    }
}
