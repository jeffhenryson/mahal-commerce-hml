package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SalaoChamadoEntity;
import com.cernecommerce.adapter.out.persistence.entity.SalaoEssenciaDiaEntity;
import com.cernecommerce.adapter.out.persistence.entity.SalaoEssenciaPerfilConfigEntity;
import com.cernecommerce.adapter.out.persistence.entity.SalaoEssenciaPerfilEntity;
import com.cernecommerce.adapter.out.persistence.entity.SalaoMesaEntity;
import com.cernecommerce.adapter.out.persistence.entity.SalaoMesaOcupacaoEntity;
import com.cernecommerce.adapter.out.persistence.entity.SalaoSolicitacaoEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.pdv.ProporcaoMistura;
import com.cernecommerce.core.domain.model.salao.ChamadoMesa;
import com.cernecommerce.core.domain.model.salao.EscolhasSessao;
import com.cernecommerce.core.domain.model.salao.FormaPagamentoChamado;
import com.cernecommerce.core.domain.model.salao.MesaOcupacao;
import com.cernecommerce.core.domain.model.salao.PerfilSabor;
import com.cernecommerce.core.domain.model.salao.SalaoMesa;
import com.cernecommerce.core.domain.model.salao.SolicitacaoSessao;
import com.cernecommerce.core.domain.model.salao.SolicitacaoStatus;
import com.cernecommerce.core.domain.model.salao.StatusChamado;
import com.cernecommerce.core.domain.model.salao.TipoChamado;
import com.cernecommerce.core.ports.out.salao.SalaoAutoatendimentoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@Transactional
public class SalaoAutoatendimentoRepositoryImpl implements SalaoAutoatendimentoRepository {

    private final SalaoMesaJpaRepository mesaJpa;
    private final SalaoEssenciaDiaJpaRepository essenciaDiaJpa;
    private final SalaoSolicitacaoJpaRepository solicitacaoJpa;
    private final SalaoMesaOcupacaoJpaRepository ocupacaoJpa;
    private final SalaoEssenciaPerfilJpaRepository perfilJpa;
    private final SalaoEssenciaPerfilConfigJpaRepository perfilConfigJpa;
    private final SalaoChamadoJpaRepository chamadoJpa;

    public SalaoAutoatendimentoRepositoryImpl(SalaoMesaJpaRepository mesaJpa,
            SalaoEssenciaDiaJpaRepository essenciaDiaJpa, SalaoSolicitacaoJpaRepository solicitacaoJpa,
            SalaoMesaOcupacaoJpaRepository ocupacaoJpa, SalaoEssenciaPerfilJpaRepository perfilJpa,
            SalaoEssenciaPerfilConfigJpaRepository perfilConfigJpa, SalaoChamadoJpaRepository chamadoJpa) {
        this.mesaJpa = mesaJpa;
        this.essenciaDiaJpa = essenciaDiaJpa;
        this.solicitacaoJpa = solicitacaoJpa;
        this.ocupacaoJpa = ocupacaoJpa;
        this.perfilJpa = perfilJpa;
        this.perfilConfigJpa = perfilConfigJpa;
        this.chamadoJpa = chamadoJpa;
    }

    // ── Mesas ─────────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<SalaoMesa> findAllMesas() {
        return mesaJpa.findAllByOrderByNomeAsc().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SalaoMesa> findMesaById(Long id) {
        return mesaJpa.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SalaoMesa> findMesaByToken(String token) {
        return token == null || token.isBlank() ? Optional.empty() : mesaJpa.findByToken(token).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsMesaComNome(String nome, Long excetoId) {
        return mesaJpa.existsByNome(nome, excetoId);
    }

    @Override
    public SalaoMesa saveMesa(SalaoMesa mesa) {
        return toDomain(mesaJpa.save(new SalaoMesaEntity(mesa.id(), mesa.nome(), mesa.token(), mesa.ativo(),
                mesa.criadoEm() == null ? Instant.now() : mesa.criadoEm())));
    }

    // ── Essências do dia ──────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Set<String> findEssenciasDoDia(LocalDate data) {
        return essenciaDiaJpa.findByData(data).stream().map(SalaoEssenciaDiaEntity::getSku)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public void replaceEssenciasDoDia(LocalDate data, Collection<String> skus) {
        List<String> copia = List.copyOf(new LinkedHashSet<>(skus));
        essenciaDiaJpa.deleteByData(data);
        essenciaDiaJpa.saveAll(copia.stream().map(sku -> new SalaoEssenciaDiaEntity(data, sku)).toList());
    }

    // ── Solicitações ──────────────────────────────────────────────────────────────────────────

    @Override
    public SolicitacaoSessao saveSolicitacao(SolicitacaoSessao s) {
        EscolhasSessao x = s.escolhas();
        SalaoSolicitacaoEntity e = new SalaoSolicitacaoEntity(s.id(), s.codigo(), s.mesaId(), s.customerId(),
                s.tierId(), s.essenciaSku(), s.essenciaSku2(), s.observacao(), s.status().name(), s.motivo(),
                s.comandaId(), s.criadoEm(), s.decididoEm(), s.decididoPor(), x.vasoGrande(),
                x.carvao() == null ? null : x.carvao().name(),
                x.adicionalIds().isEmpty() ? null
                        : x.adicionalIds().stream().map(String::valueOf).collect(Collectors.joining(",")),
                x.pagarNoFinal(), s.deviceToken(), x.proporcao() == null ? null : x.proporcao().name());
        return toDomain(solicitacaoJpa.save(e));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SolicitacaoSessao> findSolicitacaoById(Long id) {
        return solicitacaoJpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<SolicitacaoSessao> findSolicitacaoByIdForUpdate(Long id) {
        return solicitacaoJpa.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SolicitacaoSessao> findSolicitacaoByCodigo(UUID codigo) {
        return solicitacaoJpa.findByCodigo(codigo).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsPendente(Long mesaId, Long customerId, Instant desde) {
        return solicitacaoJpa.existsByMesaIdAndCustomerIdAndStatusAndCriadoEmAfter(mesaId, customerId,
                SolicitacaoStatus.PENDENTE.name(), desde);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SolicitacaoSessao> findUltimaAceita(Long mesaId) {
        return solicitacaoJpa.findFirstByMesaIdAndStatusOrderByDecididoEmDesc(mesaId,
                SolicitacaoStatus.ACEITA.name()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<SolicitacaoSessao> findSolicitacoes(SolicitacaoStatus status, Instant desde, int page,
            int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<SalaoSolicitacaoEntity> pagina = desde == null
                ? solicitacaoJpa.findByStatusOrderByCriadoEmDesc(status.name(), pageable)
                : solicitacaoJpa.findByStatusAndCriadoEmAfterOrderByCriadoEmDesc(status.name(), desde, pageable);
        return new PageResult<>(pagina.getContent().stream().map(this::toDomain).toList(), page, size,
                pagina.getTotalElements(), pagina.getTotalPages());
    }

    // ── Ocupação da mesa ──────────────────────────────────────────────────────────────────────

    @Override
    public MesaOcupacao saveOcupacao(MesaOcupacao o) {
        return toDomain(ocupacaoJpa.save(new SalaoMesaOcupacaoEntity(o.id(), o.mesaId(), o.comandaId(),
                o.customerId(), o.codigo(), o.abertaEm())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MesaOcupacao> findUltimaOcupacao(Long mesaId) {
        return ocupacaoJpa.findFirstByMesaIdOrderByAbertaEmDescIdDesc(mesaId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MesaOcupacao> findOcupacoesByComandaIds(Collection<Long> comandaIds) {
        return comandaIds == null || comandaIds.isEmpty() ? List.of()
                : ocupacaoJpa.findByComandaIdIn(comandaIds).stream().map(this::toDomain).toList();
    }

    @Override
    public void addAcesso(Long ocupacaoId, String deviceToken) {
        ocupacaoJpa.addAcesso(ocupacaoId, deviceToken);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAcesso(Long ocupacaoId, String deviceToken) {
        return deviceToken != null && !deviceToken.isBlank() && ocupacaoJpa.hasAcesso(ocupacaoId, deviceToken);
    }

    // ── Perfis de sabor ───────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<PerfilSabor>> findPerfis(Collection<String> skus) {
        if (skus == null || skus.isEmpty()) return Map.of();
        return perfilJpa.findBySkuIn(skus).stream().collect(Collectors.groupingBy(SalaoEssenciaPerfilEntity::getSku,
                Collectors.mapping(e -> PerfilSabor.valueOf(e.getPerfil()), Collectors.toList())));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findSkusComPerfilConfigurado(Collection<String> skus) {
        if (skus == null || skus.isEmpty()) return Set.of();
        return perfilConfigJpa.findBySkuIn(skus).stream().map(SalaoEssenciaPerfilConfigEntity::getSku)
                .collect(Collectors.toSet());
    }

    @Override
    public void replacePerfis(String sku, Collection<PerfilSabor> perfis, Instant agora) {
        perfilJpa.deleteBySku(sku);
        perfilJpa.saveAll(new LinkedHashSet<>(perfis).stream()
                .map(p -> new SalaoEssenciaPerfilEntity(sku, p.name())).toList());
        perfilConfigJpa.save(new SalaoEssenciaPerfilConfigEntity(sku, agora));
    }

    // ── Chamados da mesa ──────────────────────────────────────────────────────────────────────

    @Override
    public ChamadoMesa saveChamado(ChamadoMesa c) {
        return toDomain(chamadoJpa.save(new SalaoChamadoEntity(c.id(), c.comandaId(), c.mesaId(), c.ocupacaoId(),
                c.tipo().name(),
                c.formaPagamento() == null ? null : c.formaPagamento().name(), c.status().name(), c.criadoEm(),
                c.atendidoEm(), c.atendidoPor())));
    }

    @Override
    public Optional<ChamadoMesa> findChamadoByIdForUpdate(Long id) {
        return chamadoJpa.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsChamadoAberto(Long comandaId, TipoChamado tipo, Instant desde) {
        return chamadoJpa.existsByComandaIdAndTipoAndStatusAndCriadoEmAfter(comandaId, tipo.name(),
                StatusChamado.ABERTO.name(), desde);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChamadoMesa> findChamadosDaComanda(Long comandaId) {
        return chamadoJpa.findByComandaIdOrderByCriadoEmDesc(comandaId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChamadoMesa> findChamados(StatusChamado status, Instant desde) {
        return chamadoJpa.findByStatusAndCriadoEmAfterOrderByCriadoEmAsc(status.name(), desde).stream()
                .map(this::toDomain).toList();
    }

    private MesaOcupacao toDomain(SalaoMesaOcupacaoEntity e) {
        return new MesaOcupacao(e.getId(), e.getMesaId(), e.getComandaId(), e.getCustomerId(), e.getCodigo(),
                e.getAbertaEm());
    }

    private SalaoMesa toDomain(SalaoMesaEntity e) {
        return new SalaoMesa(e.getId(), e.getNome(), e.getToken(), e.isAtivo(), e.getCriadoEm());
    }

    private SolicitacaoSessao toDomain(SalaoSolicitacaoEntity e) {
        return new SolicitacaoSessao(e.getId(), e.getCodigo(), e.getMesaId(), e.getCustomerId(), e.getTierId(),
                e.getEssenciaSku(), e.getEssenciaSku2(), e.getObservacao(), SolicitacaoStatus.valueOf(e.getStatus()),
                e.getMotivo(), e.getComandaId(), e.getCriadoEm(), e.getDecididoEm(), e.getDecididoPor(),
                new EscolhasSessao(e.isVasoGrande(), e.getCarvao() == null ? null : Charcoal.valueOf(e.getCarvao()),
                        parseIds(e.getAdicionalIds()), e.isPagarNoFinal(),
                        e.getProporcao() == null ? null : ProporcaoMistura.valueOf(e.getProporcao())),
                e.getDeviceToken());
    }

    private ChamadoMesa toDomain(SalaoChamadoEntity e) {
        return new ChamadoMesa(e.getId(), e.getComandaId(), e.getMesaId(), e.getOcupacaoId(),
                TipoChamado.valueOf(e.getTipo()),
                e.getFormaPagamento() == null ? null : FormaPagamentoChamado.valueOf(e.getFormaPagamento()),
                StatusChamado.valueOf(e.getStatus()), e.getCriadoEm(), e.getAtendidoEm(), e.getAtendidoPor());
    }

    private static List<Long> parseIds(String texto) {
        return texto == null || texto.isBlank() ? List.of()
                : Arrays.stream(texto.split(",")).map(String::trim).filter(t -> !t.isEmpty()).map(Long::valueOf)
                        .toList();
    }
}
