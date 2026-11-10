package com.cernecommerce.adapter.in.assistente;

import com.cernecommerce.adapter.in.dtos.response.AssistenteAcaoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteAcaoResultadoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteBlocoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteCampoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteConversaDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteConversaResumoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteFeedbackDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteLinkDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteMensagemDTO;
import com.cernecommerce.core.domain.model.assistente.AcaoPendente;
import com.cernecommerce.core.domain.model.assistente.Conversa;
import com.cernecommerce.core.domain.model.assistente.ConversaDetalhe;
import com.cernecommerce.core.domain.model.assistente.EstadoAcao;
import com.cernecommerce.core.domain.model.assistente.Mensagem;
import com.cernecommerce.core.domain.model.assistente.PapelMensagem;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Conversão entre o domínio do assistente e o contrato do front, e (de)serialização dos JSONs gravados. */
public final class AssistenteMapper {

    private static final Pattern ROTA_SEGURA = Pattern.compile("/app/[A-Za-z0-9/_\\-]*(\\?[A-Za-z0-9_\\-=&.%]*)?");

    private AssistenteMapper() { }

    /** Só rotas internas do admin, sem truques ({@code //}, espaços, esquemas). */
    public static boolean rotaSegura(String rota) {
        return rota != null && rota.length() <= 300 && !rota.contains("//") && ROTA_SEGURA.matcher(rota).matches();
    }

    public static AssistenteConversaResumoDTO resumo(Conversa c) {
        return new AssistenteConversaResumoDTO(c.id(), c.titulo(), c.atualizadaEm());
    }

    public static AssistenteConversaDTO detalhe(ConversaDetalhe d, Instant agora) {
        Map<String, List<AcaoPendente>> porMensagem = d.acoes().stream()
                .collect(Collectors.groupingBy(AcaoPendente::mensagemId));
        List<AssistenteMensagemDTO> mensagens = d.mensagens().stream()
                .map(m -> mensagem(m, porMensagem.getOrDefault(m.id(), List.of()), agora))
                .toList();
        return new AssistenteConversaDTO(d.conversa().id(), d.conversa().titulo(), mensagens);
    }

    static AssistenteMensagemDTO mensagem(Mensagem m, List<AcaoPendente> acoes, Instant agora) {
        return new AssistenteMensagemDTO(m.id(), m.papel().api(), m.texto(), blocos(m.blocosJson()),
                links(m.linksJson()), acoes.stream().map(a -> acao(a, agora)).toList(), m.provedor(), m.modelo(),
                m.criadaEm(), feedback(m));
    }

    static AssistenteFeedbackDTO feedback(Mensagem m) {
        if (m.papel() != PapelMensagem.ASSISTENTE || m.feedbackNota() == null) {
            return null;
        }
        return new AssistenteFeedbackDTO(m.feedbackNota().name(), m.feedbackMotivo());
    }

    public static AssistenteAcaoDTO acao(AcaoPendente a, Instant agora) {
        return new AssistenteAcaoDTO(a.id(), a.titulo(), campos(a.camposJson()), estadoApi(a.estadoEm(agora)),
                a.expiraEm() == null ? null : a.expiraEm().toString(), a.resultado(), links(a.linksJson()));
    }

    public static AssistenteAcaoResultadoDTO resultado(AcaoPendente a, Instant agora) {
        return new AssistenteAcaoResultadoDTO(estadoApi(a.estadoEm(agora)), a.resultado(), links(a.linksJson()));
    }

    /** EXECUTANDO é interno: para o front, a ação ainda está pendente (o cartão mostra "executando"). */
    static String estadoApi(EstadoAcao estado) {
        return estado == EstadoAcao.EXECUTANDO ? EstadoAcao.PENDENTE.name() : estado.name();
    }

    static String json(Object valor) {
        if (valor == null || (valor instanceof List<?> l && l.isEmpty())) {
            return null;
        }
        try {
            return FerramentaSupport.MAPPER.writeValueAsString(valor);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static List<AssistenteBlocoDTO> blocos(String json) {
        return ler(json, new TypeReference<List<AssistenteBlocoDTO>>() { });
    }

    static List<AssistenteLinkDTO> links(String json) {
        return ler(json, new TypeReference<List<AssistenteLinkDTO>>() { });
    }

    static List<AssistenteCampoDTO> campos(String json) {
        return ler(json, new TypeReference<List<AssistenteCampoDTO>>() { });
    }

    private static <T> List<T> ler(String json, TypeReference<List<T>> tipo) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return FerramentaSupport.MAPPER.readValue(json, tipo);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
