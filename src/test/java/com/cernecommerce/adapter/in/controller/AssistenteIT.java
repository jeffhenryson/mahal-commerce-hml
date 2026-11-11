package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.out.persistence.entity.AuditLogEntity;
import com.cernecommerce.adapter.out.persistence.repository.AuditLogJpaRepository;
import com.cernecommerce.adapter.out.security.ratelimit.InMemoryResourceRateLimiterAdapter;
import com.cernecommerce.core.ports.in.AssistantIntegrationUseCase;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.Ouvinte;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.Requisicao;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.RespostaTurno;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.ResultadoFerramenta;
import com.cernecommerce.core.ports.out.ratelimit.ResourceRateLimiterPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Assistente de IA de ponta a ponta, com o provedor trocado por um roteiro: o stream SSE, as
 * ferramentas passando pelo {@code @PreAuthorize} dos controllers, a remoção de dados pessoais, as
 * ações confirmadas uma vez só e a auditoria com origem ASSISTENTE.
 *
 * <p>Sem {@code @Transactional}: o turno roda em outra thread e precisa enxergar o que o teste gravou.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
class AssistenteIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private WebApplicationContext context;
    @Autowired private AssistantIntegrationUseCase integracao;
    @Autowired private ResourceRateLimiterPort rateLimiter;
    @Autowired private AuditLogJpaRepository auditRepo;

    @MockitoBean private AssistenteLlmPort llm;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        if (rateLimiter instanceof InMemoryResourceRateLimiterAdapter r) {
            r.reset();
        }
        integracao.update(new AssistantIntegrationUseCase.UpdateCommand(true, "sk-ant-teste-0000", null), "admin");
    }

    @AfterEach
    void tearDown() {
        integracao.update(new AssistantIntegrationUseCase.UpdateCommand(false, "", null), "admin");
    }

    private static RequestPostProcessor como(String usuario, String... permissoes) {
        return user(usuario).authorities(Arrays.stream(permissoes).map(SimpleGrantedAuthority::new).toList());
    }

    private void roteiro(BiFunction<Requisicao, Ouvinte, RespostaTurno> passos) {
        doAnswer(inv -> passos.apply(inv.getArgument(0), inv.getArgument(1))).when(llm).conversar(any(), any());
    }

    private String novaConversa(RequestPostProcessor quem) throws Exception {
        String body = mockMvc.perform(post("/assistente/conversas").with(quem))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    /** Envia e espera o stream terminar (evento fim ou erro). Devolve o corpo SSE bruto. */
    private String enviar(String conversaId, RequestPostProcessor quem, String texto) throws Exception {
        return enviar(conversaId, quem, texto, null);
    }

    /** Com {@code substituirDesde}: "gerar de novo" / "editar e reenviar". */
    private String enviar(String conversaId, RequestPostProcessor quem, String texto, String substituirDesde)
            throws Exception {
        MvcResult r = mockMvc.perform(post("/assistente/conversas/" + conversaId + "/mensagens").with(quem)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMensagem(texto, substituirDesde)))
                .andExpect(request().asyncStarted())
                .andReturn();
        long limite = System.currentTimeMillis() + 5000;
        String corpo = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        while (System.currentTimeMillis() < limite && !corpo.contains("event:fim") && !corpo.contains("event:erro")) {
            Thread.sleep(25);
            corpo = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        }
        assertThat(corpo).as("stream terminou").containsAnyOf("event:fim", "event:erro");
        return corpo;
    }

    private static String corpoMensagem(String texto, String substituirDesde) throws Exception {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("texto", texto);
        body.put("contexto", java.util.Map.of("rota", "/app/vendas/pedidos?cliente=123", "titulo", "Pedidos"));
        if (substituirDesde != null) {
            body.put("substituirDesde", substituirDesde);
        }
        return JSON.writeValueAsString(body);
    }

    private static JsonNode fim(String corpo) throws Exception {
        List<JsonNode> fins = eventos(corpo, "fim");
        assertThat(fins).hasSize(1);
        return fins.get(0);
    }

    private static List<JsonNode> eventos(String corpo, String nome) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        for (String bloco : corpo.split("\n\n")) {
            if (bloco.contains("event:" + nome + "\n")) {
                String data = bloco.substring(bloco.indexOf("data:") + 5);
                out.add(JSON.readTree(data));
            }
        }
        return out;
    }

    @Test
    void status_sem_permissao_403_e_com_permissao_configurado() throws Exception {
        mockMvc.perform(get("/assistente/status").with(como("atendente", "PDV_READ")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/assistente/status").with(como("gerente", "ASSISTENTE_USE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitado").value(true))
                .andExpect(jsonPath("$.configurado").value(true));
    }

    @Test
    void mensagem_sem_permissao_403_em_json_antes_do_stream() throws Exception {
        String id = novaConversa(como("gerente", "ASSISTENTE_USE"));
        mockMvc.perform(post("/assistente/conversas/" + id + "/mensagens").with(como("atendente", "PDV_READ"))
                        .accept(MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"oi\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        verify(llm, never()).conversar(any(), any());
    }

    @Test
    void conversa_de_outro_usuario_404() throws Exception {
        String id = novaConversa(como("dono", "ASSISTENTE_USE"));
        mockMvc.perform(get("/assistente/conversas/" + id).with(como("intruso", "ASSISTENTE_USE")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/assistente/conversas/" + id + "/mensagens").with(como("intruso", "ASSISTENTE_USE"))
                        .accept(MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"oi\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CONVERSA_NAO_ENCONTRADA"));
        mockMvc.perform(delete("/assistente/conversas/" + id).with(como("intruso", "ASSISTENTE_USE")))
                .andExpect(status().isNotFound());
    }

    @Test
    void nao_configurado_409() throws Exception {
        RequestPostProcessor quem = como("gerente", "ASSISTENTE_USE");
        String id = novaConversa(quem);
        integracao.update(new AssistantIntegrationUseCase.UpdateCommand(false, null, null), "admin");

        mockMvc.perform(post("/assistente/conversas/" + id + "/mensagens").with(quem)
                        .accept(MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"oi\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ASSISTENTE_NAO_CONFIGURADO"));
    }

    @Test
    void limite_de_20_mensagens_429_com_retry_after() throws Exception {
        RequestPostProcessor quem = como("tagarela", "ASSISTENTE_USE");
        String id = novaConversa(quem);
        roteiro((req, o) -> new RespostaTurno("CONCLUIDO"));
        for (int i = 0; i < 20; i++) {
            enviar(id, quem, "oi " + i);
        }
        mockMvc.perform(post("/assistente/conversas/" + id + "/mensagens").with(quem)
                        .accept(MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"mais uma\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void stream_emite_texto_tabela_e_fim_e_grava_sem_dado_pessoal() throws Exception {
        RequestPostProcessor quem = como("gerente", "ASSISTENTE_USE", "CRM_CUSTOMER_READ");
        String id = novaConversa(quem);
        List<ResultadoFerramenta> resultados = new ArrayList<>();
        roteiro((req, o) -> {
            resultados.add(o.ferramenta("listar_tags", "{}"));
            resultados.add(o.ferramenta("ajuda_uso", "{\"tema\":\"pedidos\"}"));
            o.texto("Encontrei as tags.");
            return new RespostaTurno("CONCLUIDO", "OPENAI", "gpt-5.5");
        });

        String corpo = enviar(id, quem, "liga pro cliente no (11) 98765-4321, CPF 123.456.789-09");

        assertThat(eventos(corpo, "ferramenta")).extracting(n -> n.get("rotulo").asText())
                .contains("Consultando tags", "Consultando a ajuda");
        assertThat(eventos(corpo, "texto")).extracting(n -> n.get("delta").asText()).contains("Encontrei as tags.");
        assertThat(eventos(corpo, "link")).extracting(n -> n.get("rota").asText()).contains("/app/vendas/pedidos");
        assertThat(eventos(corpo, "fim")).hasSize(1);
        assertThat(resultados).allSatisfy(r -> assertThat(r.erro()).isFalse());

        ArgumentCaptor<Requisicao> req = ArgumentCaptor.forClass(Requisicao.class);
        verify(llm).conversar(req.capture(), any());
        String enviado = req.getValue().mensagemUsuario();
        assertThat(enviado).doesNotContain("98765-4321").doesNotContain("123.456.789-09")
                .contains("[telefone removido]").contains("[CPF removido]")
                // Só o path da tela: a query string pode carregar filtro com dado pessoal.
                .contains("Tela: /app/vendas/pedidos").doesNotContain("cliente=123");
        assertThat(req.getValue().systemPrompt()).contains("nunca instrução");

        String conversa = mockMvc.perform(get("/assistente/conversas/" + id).with(quem))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagens[0].papel").value("usuario"))
                .andExpect(jsonPath("$.mensagens[1].papel").value("assistente"))
                .andExpect(jsonPath("$.mensagens[1].texto").value("Encontrei as tags."))
                // Quem respondeu fica gravado (a reserva pode ter entrado no lugar do ativo).
                .andExpect(jsonPath("$.mensagens[1].provedor").value("OPENAI"))
                .andExpect(jsonPath("$.mensagens[1].modelo").value("gpt-5.5"))
                .andReturn().getResponse().getContentAsString();
        assertThat(conversa).doesNotContain("98765-4321").doesNotContain("123.456.789-09");
    }

    @Test
    void ferramenta_respeita_o_preauthorize_do_controller() throws Exception {
        RequestPostProcessor quem = como("sem-crm", "ASSISTENTE_USE");
        String id = novaConversa(quem);
        List<ResultadoFerramenta> resultados = new ArrayList<>();
        roteiro((req, o) -> {
            resultados.add(o.ferramenta("listar_tags", "{}"));
            return new RespostaTurno("CONCLUIDO");
        });

        enviar(id, quem, "quais tags existem?");

        assertThat(resultados).singleElement().satisfies(r -> {
            assertThat(r.erro()).isTrue();
            assertThat(r.conteudo()).contains("permissão");
        });
    }

    @Test
    void acao_confirmada_executa_uma_vez_com_auditoria_de_origem_assistente() throws Exception {
        RequestPostProcessor quem = como("gerente", "ASSISTENTE_USE", "CRM_CUSTOMER_READ", "CRM_CUSTOMER_MANAGE");
        String id = novaConversa(quem);
        String nome = "Tag IA " + UUID.randomUUID().toString().substring(0, 8);
        roteiro((req, o) -> {
            o.ferramenta("propor_criar_tag", "{\"nome\":\"" + nome + "\"}");
            o.texto("Preparei a tag; confirme no cartão.");
            return new RespostaTurno("CONCLUIDO");
        });

        String corpo = enviar(id, quem, "crie a tag " + nome);
        List<JsonNode> acoes = eventos(corpo, "acao");
        assertThat(acoes).hasSize(1);
        assertThat(acoes.get(0).get("estado").asText()).isEqualTo("PENDENTE");
        assertThat(acoes.get(0).get("campos").get(0).get("valor").asText()).isEqualTo(nome);
        String acaoId = acoes.get(0).get("acaoId").asText();

        // Nada foi criado antes de confirmar.
        mockMvc.perform(get("/crm/tags").with(quem))
                .andExpect(jsonPath("$[?(@.nome == '" + nome + "')]").isEmpty());

        // Outro usuário não confirma.
        mockMvc.perform(post("/assistente/acoes/" + acaoId + "/confirmar").with(como("intruso", "ASSISTENTE_USE",
                        "CRM_CUSTOMER_MANAGE")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/assistente/acoes/" + acaoId + "/confirmar").with(quem))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EXECUTADA"));
        // Repetir devolve o mesmo resultado sem executar de novo (criar a tag duas vezes daria erro).
        mockMvc.perform(post("/assistente/acoes/" + acaoId + "/confirmar").with(quem))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EXECUTADA"));
        mockMvc.perform(get("/crm/tags").with(quem))
                .andExpect(jsonPath("$[?(@.nome == '" + nome + "')]").isNotEmpty());

        AuditLogEntity tagCriada = esperarAuditoria("TAG_CREATED", "gerente");
        assertThat(tagCriada.getDetails()).contains("\"origem\":\"ASSISTENTE\"");

        mockMvc.perform(get("/assistente/conversas/" + id).with(quem))
                .andExpect(jsonPath("$.mensagens[1].acoes[0].estado").value("EXECUTADA"));
    }

    @Test
    void acao_sem_permissao_no_confirmar_falha_sem_executar() throws Exception {
        // Propôs com permissão; confirmou depois de perder a permissão: o @PreAuthorize decide.
        RequestPostProcessor comPermissao = como("gerente2", "ASSISTENTE_USE", "CRM_CUSTOMER_MANAGE");
        String id = novaConversa(comPermissao);
        String nome = "Tag sem perm " + UUID.randomUUID().toString().substring(0, 8);
        roteiro((req, o) -> {
            o.ferramenta("propor_criar_tag", "{\"nome\":\"" + nome + "\"}");
            return new RespostaTurno("CONCLUIDO");
        });
        String acaoId = eventos(enviar(id, comPermissao, "crie a tag"), "acao").get(0).get("acaoId").asText();

        mockMvc.perform(post("/assistente/acoes/" + acaoId + "/confirmar").with(como("gerente2", "ASSISTENTE_USE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FALHOU"));
    }

    @Test
    void acao_vencida_409_e_recusada_fica_recusada() throws Exception {
        RequestPostProcessor quem = como("gerente3", "ASSISTENTE_USE", "CRM_CUSTOMER_MANAGE");
        String id = novaConversa(quem);
        roteiro((req, o) -> {
            o.ferramenta("propor_criar_tag", "{\"nome\":\"Vence " + UUID.randomUUID().toString().substring(0, 6) + "\"}");
            o.ferramenta("propor_criar_tag", "{\"nome\":\"Recusa " + UUID.randomUUID().toString().substring(0, 6) + "\"}");
            return new RespostaTurno("CONCLUIDO");
        });
        List<JsonNode> acoes = eventos(enviar(id, quem, "duas tags"), "acao");
        String vencida = acoes.get(0).get("acaoId").asText();
        String recusada = acoes.get(1).get("acaoId").asText();

        // expira_em não é atualizável pela entidade: força direto no banco.
        vencer(vencida);

        mockMvc.perform(post("/assistente/acoes/" + vencida + "/confirmar").with(quem))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACAO_EXPIRADA"));

        mockMvc.perform(post("/assistente/acoes/" + recusada + "/recusar").with(quem))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECUSADA"));
        mockMvc.perform(post("/assistente/acoes/" + recusada + "/confirmar").with(quem))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECUSADA"));
    }

    // ── Melhorias: perguntaId, titulo, renomear, feedback, substituirDesde ────────────────────

    @Test
    void titulo_sai_so_no_primeiro_turno_e_fim_traz_pergunta_provedor_e_modelo() throws Exception {
        RequestPostProcessor quem = como("titulador", "ASSISTENTE_USE");
        String id = novaConversa(quem);
        roteiro((req, o) -> {
            o.texto("Olá!");
            return new RespostaTurno("CONCLUIDO", "CLAUDE", "claude-sonnet-5-5");
        });

        String primeiro = enviar(id, quem, "Como   vejo as vendas de hoje?");
        assertThat(eventos(primeiro, "titulo")).singleElement()
                .satisfies(t -> assertThat(t.get("titulo").asText()).isEqualTo("Como vejo as vendas de hoje?"));
        // O título sai antes de qualquer texto, para o cabeçalho atualizar logo.
        assertThat(primeiro.indexOf("event:titulo")).isLessThan(primeiro.indexOf("event:texto"));
        JsonNode fim = fim(primeiro);
        assertThat(fim.get("perguntaId").asText()).isNotBlank();
        assertThat(fim.get("provedor").asText()).isEqualTo("CLAUDE");
        assertThat(fim.get("modelo").asText()).isEqualTo("claude-sonnet-5-5");

        mockMvc.perform(get("/assistente/conversas/" + id).with(quem))
                .andExpect(jsonPath("$.mensagens[0].id").value(fim.get("perguntaId").asText()))
                .andExpect(jsonPath("$.mensagens[1].id").value(fim.get("mensagemId").asText()));

        String segundo = enviar(id, quem, "e de ontem?");
        assertThat(eventos(segundo, "titulo")).isEmpty();
        assertThat(fim(segundo).get("perguntaId").asText()).isNotEqualTo(fim.get("perguntaId").asText());
    }

    @Test
    void erro_do_provedor_traz_a_pergunta_gravada() throws Exception {
        RequestPostProcessor quem = como("azarado", "ASSISTENTE_USE");
        String id = novaConversa(quem);
        roteiro((req, o) -> {
            throw new com.cernecommerce.core.domain.exception.assistente.AssistenteProvedorException("fora do ar", false, null);
        });

        String corpo = enviar(id, quem, "oi");
        JsonNode erro = eventos(corpo, "erro").get(0);
        assertThat(erro.get("codigo").asText()).isEqualTo("PROVEDOR");
        mockMvc.perform(get("/assistente/conversas/" + id).with(quem))
                .andExpect(jsonPath("$.mensagens[0].id").value(erro.get("perguntaId").asText()));
    }

    @Test
    void renomear_dono_200_sem_mudar_atualizadaEm_e_sem_dado_pessoal() throws Exception {
        RequestPostProcessor quem = como("renomeador", "ASSISTENTE_USE");
        String id = novaConversa(quem);
        String antes = mockMvc.perform(get("/assistente/conversas").with(quem))
                .andReturn().getResponse().getContentAsString();
        String atualizadaAntes = JSON.readTree(antes).get(0).get("atualizadaEm").asText();

        String corpo = mockMvc.perform(patch("/assistente/conversas/" + id).with(quem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"  Cliente   CPF 123.456.789-09  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andReturn().getResponse().getContentAsString();
        JsonNode r = JSON.readTree(corpo);
        assertThat(r.get("titulo").asText()).isEqualTo("Cliente CPF [CPF removido]");
        assertThat(r.get("atualizadaEm").asText()).isEqualTo(atualizadaAntes);

        mockMvc.perform(get("/assistente/conversas/" + id).with(quem))
                .andExpect(jsonPath("$.titulo").value("Cliente CPF [CPF removido]"));
    }

    @Test
    void renomear_outro_usuario_404_vazio_e_longo_400() throws Exception {
        String id = novaConversa(como("dono-r", "ASSISTENTE_USE"));
        mockMvc.perform(patch("/assistente/conversas/" + id).with(como("intruso-r", "ASSISTENTE_USE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"meu\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/assistente/conversas/" + id).with(como("dono-r", "ASSISTENTE_USE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        mockMvc.perform(patch("/assistente/conversas/" + id).with(como("dono-r", "ASSISTENTE_USE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"" + "a".repeat(81) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void feedback_grava_limpa_e_so_vale_para_resposta_do_proprio_usuario() throws Exception {
        RequestPostProcessor quem = como("avaliador", "ASSISTENTE_USE");
        String id = novaConversa(quem);
        roteiro((req, o) -> {
            o.texto("Resposta.");
            return new RespostaTurno("CONCLUIDO", "GEMINI", "gemini-3-pro");
        });
        JsonNode fim = fim(enviar(id, quem, "pergunta"));
        String resposta = fim.get("mensagemId").asText();
        String pergunta = fim.get("perguntaId").asText();

        mockMvc.perform(put("/assistente/mensagens/" + resposta + "/feedback").with(quem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":\"NEGATIVO\",\"motivo\":\"  errou o CPF 123.456.789-09  \"}"))
                .andExpect(status().isNoContent());
        String conversa = mockMvc.perform(get("/assistente/conversas/" + id).with(quem))
                .andExpect(jsonPath("$.mensagens[0].feedback").doesNotExist())
                .andExpect(jsonPath("$.mensagens[1].feedback.nota").value("NEGATIVO"))
                .andExpect(jsonPath("$.mensagens[1].feedback.motivo").value("errou o CPF [CPF removido]"))
                .andReturn().getResponse().getContentAsString();
        assertThat(conversa).doesNotContain("123.456.789-09");

        mockMvc.perform(put("/assistente/mensagens/" + resposta + "/feedback").with(quem)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":\"POSITIVO\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/assistente/conversas/" + id).with(quem))
                .andExpect(jsonPath("$.mensagens[1].feedback.nota").value("POSITIVO"))
                .andExpect(jsonPath("$.mensagens[1].feedback.motivo").doesNotExist());

        mockMvc.perform(put("/assistente/mensagens/" + resposta + "/feedback").with(quem)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":null}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/assistente/conversas/" + id).with(quem))
                .andExpect(jsonPath("$.mensagens[1].feedback").doesNotExist());

        // Mensagem do usuário, de outro usuário, inexistente e nota inválida.
        mockMvc.perform(put("/assistente/mensagens/" + pergunta + "/feedback").with(quem)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":\"POSITIVO\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/assistente/mensagens/" + resposta + "/feedback").with(como("intruso-f", "ASSISTENTE_USE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":\"POSITIVO\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/assistente/mensagens/" + UUID.randomUUID() + "/feedback").with(quem)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":\"POSITIVO\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/assistente/mensagens/" + resposta + "/feedback").with(quem)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":\"OTIMO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void feedback_nao_muda_atualizadaEm() throws Exception {
        RequestPostProcessor quem = como("avaliador2", "ASSISTENTE_USE");
        String id = novaConversa(quem);
        roteiro((req, o) -> new RespostaTurno("CONCLUIDO"));
        String resposta = fim(enviar(id, quem, "oi")).get("mensagemId").asText();
        String antes = JSON.readTree(mockMvc.perform(get("/assistente/conversas").with(quem))
                .andReturn().getResponse().getContentAsString()).get(0).get("atualizadaEm").asText();

        mockMvc.perform(put("/assistente/mensagens/" + resposta + "/feedback").with(quem)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":\"POSITIVO\"}"))
                .andExpect(status().isNoContent());

        String depois = JSON.readTree(mockMvc.perform(get("/assistente/conversas").with(quem))
                .andReturn().getResponse().getContentAsString()).get(0).get("atualizadaEm").asText();
        assertThat(depois).isEqualTo(antes);
    }

    @Test
    void substituir_desde_pergunta_que_nao_e_a_ultima_409() throws Exception {
        RequestPostProcessor quem = como("refazedor", "ASSISTENTE_USE");
        String id = novaConversa(quem);
        roteiro((req, o) -> new RespostaTurno("CONCLUIDO"));
        String primeira = fim(enviar(id, quem, "primeira")).get("perguntaId").asText();
        String resposta = fim(enviar(id, quem, "segunda")).get("mensagemId").asText();

        for (String alvo : List.of(primeira, resposta, UUID.randomUUID().toString())) {
            mockMvc.perform(post("/assistente/conversas/" + id + "/mensagens").with(quem)
                            .accept(MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
                            .content(corpoMensagem("de novo", alvo)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("SUBSTITUICAO_INVALIDA"));
        }
        // A conversa não ficou travada.
        enviar(id, quem, "terceira");
    }

    @Test
    void substituir_desde_com_acao_executada_409_sem_travar_a_conversa() throws Exception {
        RequestPostProcessor quem = como("refazedor2", "ASSISTENTE_USE", "CRM_CUSTOMER_READ", "CRM_CUSTOMER_MANAGE");
        String id = novaConversa(quem);
        roteiro((req, o) -> {
            o.ferramenta("propor_criar_tag", "{\"nome\":\"Refaz " + UUID.randomUUID().toString().substring(0, 6) + "\"}");
            return new RespostaTurno("CONCLUIDO");
        });
        String corpo = enviar(id, quem, "crie uma tag");
        String pergunta = fim(corpo).get("perguntaId").asText();
        String acaoId = eventos(corpo, "acao").get(0).get("acaoId").asText();
        mockMvc.perform(post("/assistente/acoes/" + acaoId + "/confirmar").with(quem))
                .andExpect(jsonPath("$.estado").value("EXECUTADA"));

        mockMvc.perform(post("/assistente/conversas/" + id + "/mensagens").with(quem)
                        .accept(MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMensagem("de novo", pergunta)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACAO_JA_EXECUTADA"));

        // Nada foi apagado e o próximo envio passa.
        mockMvc.perform(get("/assistente/conversas/" + id).with(quem))
                .andExpect(jsonPath("$.mensagens.length()").value(2));
        roteiro((req, o) -> new RespostaTurno("CONCLUIDO"));
        enviar(id, quem, "outra coisa");
    }

    @Test
    void substituir_desde_apaga_a_pergunta_e_expira_acao_pendente() throws Exception {
        RequestPostProcessor quem = como("refazedor3", "ASSISTENTE_USE", "CRM_CUSTOMER_READ", "CRM_CUSTOMER_MANAGE");
        String id = novaConversa(quem);
        roteiro((req, o) -> new RespostaTurno("CONCLUIDO"));
        enviar(id, quem, "pergunta que fica");
        roteiro((req, o) -> {
            o.ferramenta("propor_criar_tag", "{\"nome\":\"Pendente " + UUID.randomUUID().toString().substring(0, 6) + "\"}");
            o.texto("Confirme no cartão.");
            return new RespostaTurno("CONCLUIDO");
        });
        String corpo = enviar(id, quem, "pergunta que sai");
        String pergunta = fim(corpo).get("perguntaId").asText();
        String acaoId = eventos(corpo, "acao").get(0).get("acaoId").asText();

        List<Requisicao> pedidos = new ArrayList<>();
        roteiro((req, o) -> {
            pedidos.add(req);
            o.texto("Nova resposta.");
            return new RespostaTurno("CONCLUIDO");
        });
        JsonNode fim = fim(enviar(id, quem, "pergunta editada", pergunta));
        assertThat(fim.get("perguntaId").asText()).isNotEqualTo(pergunta);

        // O histórico enviado ao modelo já não tem a pergunta substituída nem a resposta dela.
        assertThat(pedidos).singleElement().satisfies(req -> assertThat(req.historico())
                .extracting(AssistenteLlmPort.MensagemHistorico::texto)
                .noneMatch(t -> t.contains("pergunta que sai") || t.contains("Confirme no cartão.")));

        mockMvc.perform(get("/assistente/conversas/" + id).with(quem))
                .andExpect(jsonPath("$.mensagens.length()").value(4))
                .andExpect(jsonPath("$.mensagens[2].id").value(fim.get("perguntaId").asText()))
                .andExpect(jsonPath("$.mensagens[2].texto").value("pergunta editada"))
                .andExpect(jsonPath("$.mensagens[3].texto").value("Nova resposta."));

        assertThat(jdbc.queryForObject("SELECT estado FROM assistente_acao WHERE id = ?", String.class, acaoId))
                .isEqualTo("EXPIRADA");
        mockMvc.perform(post("/assistente/acoes/" + acaoId + "/confirmar").with(quem))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACAO_EXPIRADA"));
    }

    @Autowired private com.cernecommerce.infra.scheduler.AssistenteRetencaoJob retencao;

    @Test
    void retencao_apaga_conversa_parada_ha_mais_de_90_dias_com_mensagens_e_acoes() throws Exception {
        RequestPostProcessor quem = como("antigo", "ASSISTENTE_USE", "CRM_CUSTOMER_MANAGE");
        String velha = novaConversa(quem);
        String nova = novaConversa(quem);
        roteiro((req, o) -> {
            o.ferramenta("propor_criar_tag", "{\"nome\":\"Antiga " + UUID.randomUUID().toString().substring(0, 6) + "\"}");
            return new RespostaTurno("CONCLUIDO");
        });
        enviar(velha, quem, "uma tag");
        jdbc.update("UPDATE assistente_conversa SET atualizada_em = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(java.time.Duration.ofDays(91))), velha);

        retencao.executar();

        mockMvc.perform(get("/assistente/conversas/" + velha).with(quem)).andExpect(status().isNotFound());
        mockMvc.perform(get("/assistente/conversas/" + nova).with(quem)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assistente_mensagem WHERE conversa_id = ?",
                Integer.class, velha)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assistente_acao WHERE conversa_id = ?",
                Integer.class, velha)).isZero();
    }

    @Test
    void blocos_e_links_saem_depois_do_ultimo_texto_e_etapas_marcam_o_progresso() throws Exception {
        RequestPostProcessor quem = como("ordenado", "ASSISTENTE_USE", "CRM_CUSTOMER_READ");
        String id = novaConversa(quem);
        roteiro((req, o) -> {
            o.ferramenta("indicadores_crm", "{}");
            o.ferramenta("ferramenta_desconhecida_sem_etapa", "{}");
            o.ferramenta("ajuda_uso", "[]");
            o.texto("Resumo: 2 tags.");
            return new RespostaTurno("CONCLUIDO", "ANTHROPIC", "claude");
        });

        String corpo = enviar(id, quem, "quais tags?");

        int ultimoTexto = corpo.lastIndexOf("event:texto");
        int primeiroBloco = corpo.indexOf("event:bloco");
        assertThat(primeiroBloco).as("tabela da ferramenta").isPositive();
        assertThat(primeiroBloco).isGreaterThan(ultimoTexto);
        assertThat(corpo.lastIndexOf("event:bloco")).isLessThan(corpo.indexOf("event:fim"));
        List<JsonNode> etapas = eventos(corpo, "etapa");
        assertThat(etapas).extracting(n -> n.get("id").asText() + ":" + n.get("estado").asText())
                .containsExactly("t1:inicio", "t1:fim", "t2:inicio", "t2:falha");
        assertThat(etapas.get(0).get("rotulo").asText()).isEqualTo("Consultando indicadores do CRM");
        // A gravação não muda: o bloco continua na resposta salva.
        mockMvc.perform(get("/assistente/conversas/" + id).with(quem))
                .andExpect(jsonPath("$.mensagens[1].blocos[0].tipo").value("tabela"))
                .andExpect(jsonPath("$.turnoEmAndamento").value(false));
    }

    @Test
    void cancelar_para_o_turno_que_aparece_em_andamento_e_grava_o_parcial() throws Exception {
        RequestPostProcessor quem = como("paciente", "ASSISTENTE_USE");
        String id = novaConversa(quem);
        java.util.concurrent.CountDownLatch comecou = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean viuCancelado = new java.util.concurrent.atomic.AtomicBoolean();
        roteiro((req, o) -> {
            o.texto("Começando...");
            comecou.countDown();
            long limite = System.currentTimeMillis() + 5000;
            while (!o.cancelado() && System.currentTimeMillis() < limite) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            viuCancelado.set(o.cancelado());
            return new RespostaTurno("CANCELADO", "ANTHROPIC", "claude");
        });

        MvcResult r = mockMvc.perform(post("/assistente/conversas/" + id + "/mensagens").with(quem)
                        .accept(MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMensagem("demora", null)))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertThat(comecou.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        mockMvc.perform(get("/assistente/conversas/" + id).with(quem))
                .andExpect(jsonPath("$.turnoEmAndamento").value(true));
        // De outro usuário: 404, e o turno segue.
        mockMvc.perform(post("/assistente/conversas/" + id + "/cancelar").with(como("intruso", "ASSISTENTE_USE")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/assistente/conversas/" + id + "/cancelar").with(quem))
                .andExpect(status().isNoContent());
        r.getAsyncResult(5000);

        assertThat(viuCancelado).isTrue();
        assertThat(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("event:fim");
        mockMvc.perform(get("/assistente/conversas/" + id).with(quem))
                .andExpect(jsonPath("$.turnoEmAndamento").value(false))
                .andExpect(jsonPath("$.mensagens[1].texto").value("Começando..."));
        // Idempotente, sem turno: 204.
        mockMvc.perform(post("/assistente/conversas/" + id + "/cancelar").with(quem))
                .andExpect(status().isNoContent());
    }

    @Test
    void ferramentas_de_analise_devolvem_kpi_grafico_e_tabela_e_conferem_permissao() throws Exception {
        String sufixo = String.valueOf(System.nanoTime());
        String sku = "IT-RUP-" + sufixo.substring(sufixo.length() - 8);
        jdbc.update("INSERT INTO warehouse (code, name, type, active) VALUES (?, 'Depósito IT', 'LOJA_FISICA', TRUE)", sku);
        Long deposito = jdbc.queryForObject("SELECT id FROM warehouse WHERE code = ?", Long.class, sku);
        jdbc.update("INSERT INTO stock_balance (sku, warehouse_id, quantity, reserved_quantity, version) VALUES (?, ?, 5, 0, 0)",
                sku, deposito);
        jdbc.update("INSERT INTO stock_movement (sku, warehouse_id, type, quantity, reason, username, created_at)"
                + " VALUES (?, ?, 'SAIDA', 30, 'teste', 'it', ?)", sku, deposito,
                java.sql.Timestamp.from(Instant.now().minusSeconds(86400)));

        RequestPostProcessor quem = como("analista", "ASSISTENTE_USE", "ORDER_READ", "ESTOQUE_PRODUCT_READ",
                "ESTOQUE_WAREHOUSE_READ");
        String id = novaConversa(quem);
        java.time.LocalDate hoje = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        List<ResultadoFerramenta> resultados = new ArrayList<>();
        roteiro((req, o) -> {
            resultados.add(o.ferramenta("comparar_periodos", "{\"inicio\":\"" + hoje.minusDays(6) + "\",\"fim\":\"" + hoje + "\"}"));
            resultados.add(o.ferramenta("top_produtos", "{\"de\":\"" + hoje.minusDays(6) + "\",\"ate\":\"" + hoje + "\",\"por\":\"quantidade\"}"));
            resultados.add(o.ferramenta("top_categorias", "{\"de\":\"" + hoje.minusDays(6) + "\",\"ate\":\"" + hoje + "\"}"));
            resultados.add(o.ferramenta("ruptura_prevista", "{\"dias\":7}"));
            // Sem CRM_CUSTOMER_READ nem PDV_READ.
            resultados.add(o.ferramenta("clientes_inativos", "{\"dias\":30}"));
            resultados.add(o.ferramenta("resumo_caixa", "{}"));
            o.texto("Pronto.");
            return new RespostaTurno("CONCLUIDO", "ANTHROPIC", "claude");
        });

        String corpo = enviar(id, quem, "como estamos?");

        assertThat(resultados.subList(0, 4)).allSatisfy(r -> assertThat(r.erro()).as(r.conteudo()).isFalse());
        assertThat(resultados.get(4).erro()).isTrue();
        assertThat(resultados.get(4).conteudo()).contains("permissão");
        assertThat(resultados.get(5).erro()).isTrue();
        assertThat(resultados.get(3).conteudo()).contains(sku).contains("em 5 dias");

        List<JsonNode> blocos = eventos(corpo, "bloco");
        JsonNode kpi = blocos.stream().filter(b -> "kpi".equals(b.get("tipo").asText())).findFirst().orElseThrow();
        assertThat(kpi.get("itens")).hasSize(3);
        assertThat(kpi.get("itens").get(0).get("rotulo").asText()).isEqualTo("Total vendido");
        assertThat(kpi.get("itens").get(0).get("variacao").asText()).contains("vs. período anterior");
        assertThat(kpi.get("itens").get(0).get("sensivel").asBoolean()).isTrue();
        assertThat(kpi.has("colunas")).as("campos de outro tipo ficam de fora").isFalse();
        assertThat(blocos).anySatisfy(b -> {
            assertThat(b.get("tipo").asText()).isEqualTo("tabela");
            assertThat(b.get("titulo").asText()).isEqualTo("Acaba em até 7 dias");
        });
        // A gravação devolve os mesmos tipos.
        mockMvc.perform(get("/assistente/conversas/" + id).with(quem))
                .andExpect(jsonPath("$.mensagens[1].blocos[0].tipo").value("kpi"));
    }

    @Autowired private com.cernecommerce.core.ports.in.RoleUseCase roleUseCase;

    /**
     * Prod: o stream termina com {@code emitter.complete()}, que faz um re-dispatch ASYNC. Com JWT
     * de verdade (sem o {@code user()} do teste, que guarda o contexto no request), o filtro de JWT
     * não roda no ASYNC; se a segurança barrar esse dispatch, sai 401 com a resposta já comprometida
     * e o navegador vê "erro de rede".
     */
    @Test
    void stream_com_jwt_real_termina_sem_401_no_redispatch_assincrono() throws Exception {
        String sufixo = String.valueOf(System.nanoTime());
        String papel = "ROLE_ASSIST_IT_" + sufixo;
        String usuario = "assist_it_" + sufixo;
        roleUseCase.createRole(papel);
        mockMvc.perform(post("/roles/{r}/permissions/{p}", papel, "ASSISTENTE_USE")
                        .with(como("admin_setup", "ROLE_MANAGE_PERMISSIONS")))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/users").with(como("admin_setup", "USER_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + usuario + "\",\"password\":\"Secure@123\",\"roles\":[]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/users/{u}/roles/{r}", usuario, papel).with(como("admin_setup", "USER_ROLE_ASSIGN")))
                .andExpect(status().isNoContent());
        String login = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + usuario + "\",\"password\":\"Secure@123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JSON.readTree(login).get("accessToken").asText();

        String id = JSON.readTree(mockMvc.perform(post("/assistente/conversas").header("Authorization", bearer))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        roteiro((req, o) -> {
            o.texto("Vendas de hoje: R$ 10,00.");
            return new RespostaTurno("CONCLUIDO", "ANTHROPIC", "claude");
        });
        MvcResult r = mockMvc.perform(post("/assistente/conversas/" + id + "/mensagens")
                        .header("Authorization", bearer)
                        .accept(MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMensagem("Como foram as vendas de hoje?", null)))
                .andExpect(request().asyncStarted())
                .andReturn();
        r.getAsyncResult(5000);

        mockMvc.perform(asyncDispatch(r))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-transform")));
        assertThat(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("event:fim");
    }

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private void vencer(String acaoId) {
        jdbc.update("UPDATE assistente_acao SET expira_em = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)), acaoId);
    }

    private AuditLogEntity esperarAuditoria(String acao, String usuario) throws InterruptedException {
        long limite = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < limite) {
            var achada = auditRepo.findAll().stream()
                    .filter(e -> acao.equals(e.getAction()) && usuario.equals(e.getUsername()))
                    .reduce((a, b) -> b);
            if (achada.isPresent()) {
                return achada.get();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Sem auditoria " + acao + " de " + usuario);
    }
}
