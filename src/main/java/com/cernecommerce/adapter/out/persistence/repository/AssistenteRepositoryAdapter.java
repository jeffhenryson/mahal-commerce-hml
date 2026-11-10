package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.AssistenteAcaoEntity;
import com.cernecommerce.adapter.out.persistence.entity.AssistenteConversaEntity;
import com.cernecommerce.adapter.out.persistence.entity.AssistenteMensagemEntity;
import com.cernecommerce.core.domain.model.assistente.AcaoPendente;
import com.cernecommerce.core.domain.model.assistente.Conversa;
import com.cernecommerce.core.domain.model.assistente.EstadoAcao;
import com.cernecommerce.core.domain.model.assistente.Mensagem;
import com.cernecommerce.core.domain.model.assistente.NotaFeedback;
import com.cernecommerce.core.ports.out.assistente.AssistenteRepositoryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Conversas, mensagens e ações do assistente. As exclusões apagam os filhos explicitamente: o FK
 * com ON DELETE CASCADE existe na migration, mas não no H2 do perfil dev (ddl-auto).
 */
@Repository
public class AssistenteRepositoryAdapter implements AssistenteRepositoryPort {

    private static final int LOTE_RETENCAO = 500;

    private final AssistenteConversaJpaRepository conversas;
    private final AssistenteMensagemJpaRepository mensagens;
    private final AssistenteAcaoJpaRepository acoes;

    public AssistenteRepositoryAdapter(AssistenteConversaJpaRepository conversas,
            AssistenteMensagemJpaRepository mensagens, AssistenteAcaoJpaRepository acoes) {
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.acoes = acoes;
    }

    @Override
    public Conversa salvarConversa(Conversa conversa) {
        AssistenteConversaEntity e = conversas.findById(conversa.id()).orElseGet(AssistenteConversaEntity::new);
        e.setId(conversa.id());
        e.setUsername(conversa.username());
        e.setTitulo(conversa.titulo());
        if (e.getCriadaEm() == null) {
            e.setCriadaEm(conversa.criadaEm());
        }
        e.setAtualizadaEm(conversa.atualizadaEm());
        return toDomain(conversas.save(e));
    }

    @Override
    public Optional<Conversa> buscarConversa(String id) {
        return conversas.findById(id).map(AssistenteRepositoryAdapter::toDomain);
    }

    @Override
    public List<Conversa> listarConversas(String username, int limite) {
        return conversas.findByUsernameOrderByAtualizadaEmDesc(username, PageRequest.of(0, limite)).stream()
                .map(AssistenteRepositoryAdapter::toDomain).toList();
    }

    @Override
    public void excluirConversa(String id) {
        List<String> ids = List.of(id);
        acoes.deleteByConversaIds(ids);
        mensagens.deleteByConversaIds(ids);
        conversas.deleteByIds(ids);
    }

    @Override
    public Mensagem salvarMensagem(Mensagem m) {
        // Mensagem não é reescrita depois de criada; a avaliação tem o próprio salvarFeedback.
        AssistenteMensagemEntity e = new AssistenteMensagemEntity();
        e.setId(m.id());
        e.setConversaId(m.conversaId());
        e.setPapel(m.papel());
        e.setTexto(m.texto());
        e.setBlocos(m.blocosJson());
        e.setLinks(m.linksJson());
        e.setProvedor(m.provedor());
        e.setModelo(m.modelo());
        e.setCriadaEm(m.criadaEm());
        return toDomain(mensagens.save(e));
    }

    @Override
    public Optional<Mensagem> buscarMensagem(String id) {
        return mensagens.findById(id).map(AssistenteRepositoryAdapter::toDomain);
    }

    @Override
    public void salvarFeedback(String mensagemId, NotaFeedback nota, String motivo, Instant em) {
        mensagens.findById(mensagemId).ifPresent(e -> {
            e.setFeedbackNota(nota);
            e.setFeedbackMotivo(motivo);
            e.setFeedbackEm(em);
            mensagens.save(e);
        });
    }

    @Override
    public void excluirMensagens(List<String> ids) {
        if (!ids.isEmpty()) {
            mensagens.deleteAllByIdInBatch(ids);
        }
    }

    @Override
    public List<Mensagem> listarMensagens(String conversaId) {
        return mensagens.findByConversaIdOrderByCriadaEmAscIdAsc(conversaId).stream()
                .map(AssistenteRepositoryAdapter::toDomain).toList();
    }

    @Override
    public List<Mensagem> ultimasMensagens(String conversaId, int limite) {
        List<Mensagem> recentes = new ArrayList<>(mensagens
                .findByConversaIdOrderByCriadaEmDescIdDesc(conversaId, PageRequest.of(0, limite)).stream()
                .map(AssistenteRepositoryAdapter::toDomain).toList());
        Collections.reverse(recentes);
        return recentes;
    }

    @Override
    public AcaoPendente salvarAcao(AcaoPendente a) {
        // Dentro da transação que travou a linha, findById devolve a entidade já gerenciada.
        AssistenteAcaoEntity e = acoes.findById(a.id()).orElse(null);
        if (e == null) {
            e = new AssistenteAcaoEntity();
            e.setId(a.id());
            e.setConversaId(a.conversaId());
            e.setMensagemId(a.mensagemId());
            e.setUsername(a.username());
            e.setTipo(a.tipo());
            e.setParametros(a.parametrosJson());
            e.setTitulo(a.titulo());
            e.setCampos(a.camposJson());
            e.setCriadaEm(a.criadaEm());
            e.setExpiraEm(a.expiraEm());
        }
        e.setEstado(a.estado());
        e.setResultado(a.resultado());
        e.setLinks(a.linksJson());
        e.setExecutadaEm(a.executadaEm());
        return toDomain(acoes.save(e));
    }

    @Override
    public Optional<AcaoPendente> buscarAcao(String id) {
        return acoes.findById(id).map(AssistenteRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<AcaoPendente> buscarAcaoParaAtualizar(String id) {
        return acoes.findByIdForUpdate(id).map(AssistenteRepositoryAdapter::toDomain);
    }

    @Override
    public List<AcaoPendente> listarAcoes(String conversaId) {
        return acoes.findByConversaIdOrderByCriadaEmAsc(conversaId).stream()
                .map(AssistenteRepositoryAdapter::toDomain).toList();
    }

    @Override
    public long excluirConversasAntesDe(Instant corte) {
        List<String> ids = conversas.findIdsAtualizadasAntesDe(corte);
        long total = 0;
        for (int i = 0; i < ids.size(); i += LOTE_RETENCAO) {
            List<String> lote = ids.subList(i, Math.min(ids.size(), i + LOTE_RETENCAO));
            acoes.deleteByConversaIds(lote);
            mensagens.deleteByConversaIds(lote);
            total += conversas.deleteByIds(lote);
        }
        return total;
    }

    @Override
    public int expirarPendentes(Instant agora) {
        return acoes.expirar(EstadoAcao.PENDENTE, EstadoAcao.EXPIRADA, agora);
    }

    @Override
    public int falharExecucoesTravadas(Instant corte, String resultado) {
        return acoes.falharTravadas(EstadoAcao.EXECUTANDO, EstadoAcao.FALHOU, resultado, corte, Instant.now());
    }

    private static Conversa toDomain(AssistenteConversaEntity e) {
        return new Conversa(e.getId(), e.getUsername(), e.getTitulo(), e.getCriadaEm(), e.getAtualizadaEm());
    }

    private static Mensagem toDomain(AssistenteMensagemEntity e) {
        return new Mensagem(e.getId(), e.getConversaId(), e.getPapel(), e.getTexto(), e.getBlocos(), e.getLinks(),
                e.getProvedor(), e.getModelo(), e.getCriadaEm(), e.getFeedbackNota(), e.getFeedbackMotivo(),
                e.getFeedbackEm());
    }

    private static AcaoPendente toDomain(AssistenteAcaoEntity e) {
        return new AcaoPendente(e.getId(), e.getConversaId(), e.getMensagemId(), e.getUsername(), e.getTipo(),
                e.getParametros(), e.getTitulo(), e.getCampos(), e.getEstado(), e.getCriadaEm(), e.getExpiraEm(),
                e.getResultado(), e.getLinks(), e.getExecutadaEm());
    }
}
