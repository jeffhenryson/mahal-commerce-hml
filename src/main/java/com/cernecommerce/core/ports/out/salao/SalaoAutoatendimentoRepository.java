package com.cernecommerce.core.ports.out.salao;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.salao.MesaOcupacao;
import com.cernecommerce.core.domain.model.salao.SalaoMesa;
import com.cernecommerce.core.domain.model.salao.SolicitacaoSessao;
import com.cernecommerce.core.domain.model.salao.SolicitacaoStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistência do autoatendimento do Salão: mesas com QR, essências do dia e solicitações. */
public interface SalaoAutoatendimentoRepository {

    List<SalaoMesa> findAllMesas();

    Optional<SalaoMesa> findMesaById(Long id);

    Optional<SalaoMesa> findMesaByToken(String token);

    boolean existsMesaComNome(String nome, Long excetoId);

    SalaoMesa saveMesa(SalaoMesa mesa);

    Set<String> findEssenciasDoDia(LocalDate data);

    void replaceEssenciasDoDia(LocalDate data, Collection<String> skus);

    SolicitacaoSessao saveSolicitacao(SolicitacaoSessao solicitacao);

    Optional<SolicitacaoSessao> findSolicitacaoById(Long id);

    /** Com trava de escrita — dois atendentes aceitando o mesmo pedido não abrem duas mesas. */
    Optional<SolicitacaoSessao> findSolicitacaoByIdForUpdate(Long id);

    Optional<SolicitacaoSessao> findSolicitacaoByCodigo(UUID codigo);

    /** Pendentes desde {@code desde} (as mais antigas já expiraram) desta mesa e cliente. */
    boolean existsPendente(Long mesaId, Long customerId, Instant desde);

    /** Última aceita da mesa — a comanda dela, se ainda aberta, recebe a próxima sessão pedida. */
    Optional<SolicitacaoSessao> findUltimaAceita(Long mesaId);

    /**
     * Gravadas com {@code status}, mais recentes primeiro. Para {@code PENDENTE}, só as criadas
     * depois de {@code desde} — as anteriores já são EXPIRADA na leitura.
     */
    PageResult<SolicitacaoSessao> findSolicitacoes(SolicitacaoStatus status, Instant desde, int page, int size);

    // ── Ocupação da mesa ──

    MesaOcupacao saveOcupacao(MesaOcupacao ocupacao);

    /** A ocupação mais recente da mesa — vale se a comanda dela ainda está aberta. */
    Optional<MesaOcupacao> findUltimaOcupacao(Long mesaId);

    /** A ocupação mais recente de cada comanda pedida (as que não são de mesa do QR não voltam). */
    List<MesaOcupacao> findOcupacoesByComandaIds(Collection<Long> comandaIds);

    void addAcesso(Long ocupacaoId, String deviceToken);

    boolean hasAcesso(Long ocupacaoId, String deviceToken);
}
