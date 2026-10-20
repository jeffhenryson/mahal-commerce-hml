package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SalaoEssenciaDiaEntity;
import com.cernecommerce.adapter.out.persistence.entity.SalaoMesaEntity;
import com.cernecommerce.adapter.out.persistence.entity.SalaoMesaOcupacaoEntity;
import com.cernecommerce.adapter.out.persistence.entity.SalaoSolicitacaoEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.salao.EscolhasSessao;
import com.cernecommerce.core.domain.model.salao.MesaOcupacao;
import com.cernecommerce.core.domain.model.salao.SalaoMesa;
import com.cernecommerce.core.domain.model.salao.SolicitacaoSessao;
import com.cernecommerce.core.domain.model.salao.SolicitacaoStatus;
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

    public SalaoAutoatendimentoRepositoryImpl(SalaoMesaJpaRepository mesaJpa,
            SalaoEssenciaDiaJpaRepository essenciaDiaJpa, SalaoSolicitacaoJpaRepository solicitacaoJpa,
            SalaoMesaOcupacaoJpaRepository ocupacaoJpa) {
        this.mesaJpa = mesaJpa;
        this.essenciaDiaJpa = essenciaDiaJpa;
        this.solicitacaoJpa = solicitacaoJpa;
        this.ocupacaoJpa = ocupacaoJpa;
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
                x.pagarNoFinal(), s.deviceToken());
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
                        parseIds(e.getAdicionalIds()), e.isPagarNoFinal()), e.getDeviceToken());
    }

    private static List<Long> parseIds(String texto) {
        return texto == null || texto.isBlank() ? List.of()
                : Arrays.stream(texto.split(",")).map(String::trim).filter(t -> !t.isEmpty()).map(Long::valueOf)
                        .toList();
    }
}
