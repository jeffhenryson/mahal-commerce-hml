package com.cernecommerce.adapter.out.llm;

import com.cernecommerce.core.domain.exception.assistente.AssistenteProvedorException;
import com.cernecommerce.core.domain.model.config.AssistantCredentials;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;
import com.cernecommerce.core.domain.model.config.ProvedorIa;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.Ouvinte;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.Requisicao;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.RespostaTurno;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.ResultadoFerramenta;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoteadorLlmAdapterTest {

    private static final AssistantCredentials CLAUDE = new AssistantCredentials(ProvedorIa.ANTHROPIC, "k1", "claude-sonnet-5-5");
    private static final AssistantCredentials GPT = new AssistantCredentials(ProvedorIa.OPENAI, "k2", "gpt-5.5");
    private static final AssistantCredentials GEMINI = new AssistantCredentials(ProvedorIa.GEMINI, "k3", "gemini-3.5-flash");

    private final List<ProvedorIa> chamados = new ArrayList<>();
    private final List<String> textos = new ArrayList<>();

    private final Ouvinte ouvinte = new Ouvinte() {
        @Override public void texto(String delta) { textos.add(delta); }
        @Override public ResultadoFerramenta ferramenta(String nome, String inputJson) {
            return ResultadoFerramenta.ok("{}");
        }
        @Override public boolean cancelado() { return false; }
    };

    /** Cliente falso: registra a chamada e roda o roteiro. */
    private ClienteLlm cliente(ProvedorIa p, BiFunction<AssistantCredentials, Ouvinte, RespostaTurno> roteiro) {
        return new ClienteLlm() {
            @Override public ProvedorIa provedor() { return p; }
            @Override public RespostaTurno conversar(AssistantCredentials c, Requisicao r, Ouvinte o) {
                chamados.add(p);
                return roteiro.apply(c, o);
            }
            @Override public IntegrationTestResult testar(AssistantCredentials c) {
                return IntegrationTestResult.ok(p.name());
            }
        };
    }

    private static Requisicao requisicao(AssistantCredentials... cadeia) {
        return new Requisicao(List.of(cadeia), "sistema", List.of(), List.of(), "oi", 1000, 4);
    }

    private static AssistenteProvedorException falha() {
        return new AssistenteProvedorException("fora do ar", false, null);
    }

    @Test
    void usa_o_ativo_e_informa_quem_respondeu() {
        RoteadorLlmAdapter roteador = new RoteadorLlmAdapter(List.of(
                cliente(ProvedorIa.OPENAI, (c, o) -> { o.texto("olá"); return new RespostaTurno("CONCLUIDO"); }),
                cliente(ProvedorIa.ANTHROPIC, (c, o) -> new RespostaTurno("CONCLUIDO"))));

        RespostaTurno r = roteador.conversar(requisicao(GPT, CLAUDE), ouvinte);

        assertThat(chamados).containsExactly(ProvedorIa.OPENAI);
        assertThat(r.provedor()).isEqualTo("OPENAI");
        assertThat(r.modelo()).isEqualTo("gpt-5.5");
        assertThat(textos).containsExactly("olá");
    }

    @Test
    void cai_para_a_reserva_quando_o_ativo_falha_antes_de_mostrar_algo() {
        RoteadorLlmAdapter roteador = new RoteadorLlmAdapter(List.of(
                cliente(ProvedorIa.GEMINI, (c, o) -> { throw falha(); }),
                cliente(ProvedorIa.ANTHROPIC, (c, o) -> { o.texto("pela reserva"); return new RespostaTurno("CONCLUIDO"); })));

        RespostaTurno r = roteador.conversar(requisicao(GEMINI, CLAUDE), ouvinte);

        assertThat(chamados).containsExactly(ProvedorIa.GEMINI, ProvedorIa.ANTHROPIC);
        assertThat(r.provedor()).isEqualTo("ANTHROPIC");
        assertThat(textos).containsExactly("pela reserva");
    }

    @Test
    void nao_cai_para_a_reserva_depois_de_mostrar_texto() {
        RoteadorLlmAdapter roteador = new RoteadorLlmAdapter(List.of(
                cliente(ProvedorIa.OPENAI, (c, o) -> { o.texto("começou"); throw falha(); }),
                cliente(ProvedorIa.ANTHROPIC, (c, o) -> new RespostaTurno("CONCLUIDO"))));

        assertThatThrownBy(() -> roteador.conversar(requisicao(GPT, CLAUDE), ouvinte))
                .isInstanceOf(AssistenteProvedorException.class);
        assertThat(chamados).containsExactly(ProvedorIa.OPENAI);
    }

    @Test
    void nao_cai_para_a_reserva_depois_de_executar_ferramenta() {
        RoteadorLlmAdapter roteador = new RoteadorLlmAdapter(List.of(
                cliente(ProvedorIa.OPENAI, (c, o) -> { o.ferramenta("buscar_produtos", "{}"); throw falha(); }),
                cliente(ProvedorIa.ANTHROPIC, (c, o) -> new RespostaTurno("CONCLUIDO"))));

        assertThatThrownBy(() -> roteador.conversar(requisicao(GPT, CLAUDE), ouvinte))
                .isInstanceOf(AssistenteProvedorException.class);
        assertThat(chamados).containsExactly(ProvedorIa.OPENAI);
    }

    @Test
    void sem_reserva_a_falha_sobe() {
        RoteadorLlmAdapter roteador = new RoteadorLlmAdapter(List.of(
                cliente(ProvedorIa.ANTHROPIC, (c, o) -> { throw falha(); })));

        assertThatThrownBy(() -> roteador.conversar(requisicao(CLAUDE), ouvinte))
                .isInstanceOf(AssistenteProvedorException.class)
                .hasMessage("fora do ar");
    }

    @Test
    void teste_vai_para_o_cliente_do_provedor() {
        RoteadorLlmAdapter roteador = new RoteadorLlmAdapter(List.of(
                cliente(ProvedorIa.ANTHROPIC, (c, o) -> null),
                cliente(ProvedorIa.GEMINI, (c, o) -> null)));

        assertThat(roteador.testar(GEMINI).detail()).isEqualTo("GEMINI");
    }
}
