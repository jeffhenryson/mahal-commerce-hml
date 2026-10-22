package com.cernecommerce.core.ports.out.salao;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.salao.ChamadoMesa;
import com.cernecommerce.core.domain.model.salao.MesaOcupacao;
import com.cernecommerce.core.domain.model.salao.PerfilSabor;
import com.cernecommerce.core.domain.model.salao.SalaoMesa;
import com.cernecommerce.core.domain.model.salao.SolicitacaoSessao;
import com.cernecommerce.core.domain.model.salao.SolicitacaoStatus;
import com.cernecommerce.core.domain.model.salao.StatusChamado;
import com.cernecommerce.core.domain.model.salao.TipoChamado;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persistência do autoatendimento do Salão: mesas com QR, essências do dia, perfis de sabor,
 * solicitações, ocupações e chamados.
 */
public interface SalaoAutoatendimentoRepository {

    List<SalaoMesa> findAllMesas();

    Optional<SalaoMesa> findMesaById(Long id);

    /** As mesas dos ids, numa consulta; id sem mesa fica de fora. */
    List<SalaoMesa> findMesasByIds(Collection<Long> ids);

    Optional<SalaoMesa> findMesaByToken(String token);

    /** Com trava de escrita — dois aparelhos pedindo na mesma mesa livre não a ocupam duas vezes. */
    Optional<SalaoMesa> findMesaByIdForUpdate(Long id);

    boolean existsMesaComNome(String nome, Long excetoId);

    SalaoMesa saveMesa(SalaoMesa mesa);

    Set<String> findEssenciasDoDia(LocalDate data);

    void replaceEssenciasDoDia(LocalDate data, Collection<String> skus);

    /** O modelo semanal: dia da semana (1 = segunda) → SKUs. Dia sem seleção não volta. */
    Map<Integer, Set<String>> findModelo();

    Set<String> findModelo(int diaSemana);

    void replaceModelo(int diaSemana, Collection<String> skus);

    SolicitacaoSessao saveSolicitacao(SolicitacaoSessao solicitacao);

    Optional<SolicitacaoSessao> findSolicitacaoById(Long id);

    /** As solicitações dos ids, numa consulta; id sem solicitação fica de fora. */
    List<SolicitacaoSessao> findSolicitacoesByIds(Collection<Long> ids);

    /** Com trava de escrita — dois atendentes aceitando o mesmo pedido não abrem duas mesas. */
    Optional<SolicitacaoSessao> findSolicitacaoByIdForUpdate(Long id);

    Optional<SolicitacaoSessao> findSolicitacaoByCodigo(UUID codigo);

    /** Pendentes desde {@code desde} (as mais antigas já expiraram) desta mesa e cliente. */
    boolean existsPendente(Long mesaId, Long customerId, Instant desde);

    /** Pendentes desde {@code desde} pedidas por este aparelho na mesa — a regra do acompanhante. */
    boolean existsPendenteDoAparelho(Long mesaId, String deviceToken, Instant desde);

    /** Última aceita da mesa — a comanda dela, se ainda aberta, recebe a próxima sessão pedida. */
    Optional<SolicitacaoSessao> findUltimaAceita(Long mesaId);

    /**
     * Gravadas com {@code status}, mais recentes primeiro. Para {@code PENDENTE}, só as criadas
     * depois de {@code desde} — as anteriores já são EXPIRADA na leitura.
     */
    PageResult<SolicitacaoSessao> findSolicitacoes(SolicitacaoStatus status, Instant desde, int page, int size);

    /** Gravadas com {@code status} antes de {@code antes}, mais recentes primeiro — as PENDENTE vencidas são as EXPIRADA. */
    PageResult<SolicitacaoSessao> findSolicitacoesAntesDe(SolicitacaoStatus status, Instant antes, int page, int size);

    // ── Ocupação da mesa ──

    MesaOcupacao saveOcupacao(MesaOcupacao ocupacao);

    /**
     * A ocupação mais recente da mesa — vale se a comanda dela ainda está aberta ou, sem comanda, se a
     * solicitação de origem ainda está pendente.
     */
    Optional<MesaOcupacao> findUltimaOcupacao(Long mesaId);

    /** {@link #findUltimaOcupacao} de todas as mesas, numa consulta. Mesa nunca ocupada não volta. */
    List<MesaOcupacao> findUltimaOcupacaoDeCadaMesa();

    /** A ocupação mais recente de cada comanda pedida (as que não são de mesa do QR não voltam). */
    List<MesaOcupacao> findOcupacoesByComandaIds(Collection<Long> comandaIds);

    /**
     * Dá acesso à ocupação ao aparelho. {@code acompanhante}: entrou pelo código, não é o titular. Acesso
     * do titular prevalece — o aparelho que já era dele continua sendo.
     */
    void addAcesso(Long ocupacaoId, String deviceToken, boolean acompanhante);

    boolean hasAcesso(Long ocupacaoId, String deviceToken);

    /**
     * O acesso do aparelho à ocupação numa leitura: vazio sem acesso; {@code true} se entrou como
     * acompanhante (pelo código), {@code false} se é do titular.
     */
    Optional<Boolean> findAcesso(Long ocupacaoId, String deviceToken);

    // ── Perfis de sabor ──

    /** Os perfis marcados de cada SKU pedido; SKU sem perfil não volta. */
    Map<String, List<PerfilSabor>> findPerfis(Collection<String> skus);

    /** Dos SKUs pedidos, os que a casa já configurou — mesmo que com nenhum perfil. */
    Set<String> findSkusComPerfilConfigurado(Collection<String> skus);

    /** Substitui os perfis de cada SKU e os marca como configurados, em lote. Lista vazia limpa o SKU. */
    void replacePerfis(Map<String, List<PerfilSabor>> perfisPorSku, Instant agora);

    // ── Chamados da mesa ──

    ChamadoMesa saveChamado(ChamadoMesa chamado);

    /** Com trava de escrita — dois atendentes atendendo o mesmo chamado. */
    Optional<ChamadoMesa> findChamadoByIdForUpdate(Long id);

    /** Há chamado {@code tipo} ABERTO na comanda, criado depois de {@code desde} (os anteriores venceram). */
    boolean existsChamadoAberto(Long comandaId, TipoChamado tipo, Instant desde);

    /** Os chamados da comanda, mais recentes primeiro. */
    List<ChamadoMesa> findChamadosDaComanda(Long comandaId);

    /** {@link #findChamadosDaComanda} de várias comandas numa consulta: comanda → chamados, mais recentes primeiro. */
    Map<Long, List<ChamadoMesa>> findChamadosDasComandas(Collection<Long> comandaIds);

    /** Gravados com {@code status} desde {@code desde}, mais antigos primeiro — a ordem da fila. */
    List<ChamadoMesa> findChamados(StatusChamado status, Instant desde);
}
