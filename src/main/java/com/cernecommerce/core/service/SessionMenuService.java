package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.SessionAssetTypeNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.SessionAssetUnavailableException;
import com.cernecommerce.core.domain.exception.pdv.SessionMenuConflictException;
import com.cernecommerce.core.domain.exception.pdv.SessionAddonNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.SessionTierNotFoundException;
import com.cernecommerce.core.domain.model.pdv.SessionAssetAllocation;
import com.cernecommerce.core.domain.model.pdv.SessionAssetType;
import com.cernecommerce.core.domain.model.pdv.SessionMenu;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionAddon;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.ports.in.SessionMenuUseCase;
import com.cernecommerce.core.ports.out.pdv.SessionMenuRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cardápio de sessão da mesa (PDV-F021).
 *
 * <p>Além do cadastro (port {@link SessionMenuUseCase}), expõe ao {@link ComandaService} as regras
 * que o lançamento da sessão precisa — faixa ativa, preço do upgrade, dia de duplo rosh e a
 * reserva/liberação de utensílios. Mesmo desenho de {@code PdvService} para {@code ComandaService}:
 * dependência service-para-service no bean concreto, para as regras não se duplicarem.</p>
 *
 * <h2>Utensílio é ativo, não estoque</h2>
 * <p>Vaso, pinça, prato e tapete voltam para a casa quando a mesa fecha. O que se controla é
 * quantos estão livres: total cadastrado menos alocações abertas. A reserva trava as linhas dos
 * tipos envolvidos (em ordem de id) antes de contar, então duas mesas nunca levam o último vaso.</p>
 */
public class SessionMenuService implements SessionMenuUseCase {

    /** Dia da promoção é o dia no salão, não o do servidor. */
    static final ZoneId ZONA_SALAO = ZoneId.of("America/Sao_Paulo");

    private final SessionMenuRepository repository;
    private final Clock clock;

    public SessionMenuService(SessionMenuRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    // ── Leitura para a mesa ───────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public SessionMenu getMenu() {
        SessionSettings settings = repository.getSettings();
        Map<Long, Integer> inUse = repository.countInUseByAssetType();
        List<SessionMenu.AssetAvailability> assets = repository.findAllAssetTypes().stream()
                .filter(SessionAssetType::ativo)
                .map(t -> new SessionMenu.AssetAvailability(t, inUse.getOrDefault(t.id(), 0)))
                .toList();
        List<SessionTier> tiers = repository.findAllTiers().stream().filter(SessionTier::ativo).toList();
        List<SessionAddon> addons = repository.findAllAddons().stream().filter(SessionAddon::ativo).toList();
        return new SessionMenu(tiers, assets, settings, isDuploRoshDay(settings, clock.instant()), addons);
    }

    // ── Cadastro (admin) ──────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<SessionTier> listTiers() {
        return repository.findAllTiers();
    }

    @Override
    @Transactional
    public SessionTier createTier(String nome, BigDecimal preco, String marcas, int ordem) {
        SessionTier tier = SessionTier.create(nome, preco, marcas, ordem);
        ensureTierNameFree(tier.nome(), null);
        return repository.saveTier(tier);
    }

    @Override
    @Transactional
    public SessionTier updateTier(Long id, String nome, BigDecimal preco, String marcas, Integer ordem, boolean ativo) {
        SessionTier current = repository.findTierById(id).orElseThrow(() -> new SessionTierNotFoundException(id));
        SessionTier updated = current.withData(nome, preco, marcas, ordem == null ? current.ordem() : ordem, ativo);
        ensureTierNameFree(updated.nome(), id);
        return repository.saveTier(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionAddon> listAddons() {
        return repository.findAllAddons();
    }

    @Override
    @Transactional
    public SessionAddon createAddon(String nome, BigDecimal preco, int ordem) {
        SessionAddon addon = SessionAddon.create(nome, preco, ordem);
        ensureAddonNameFree(addon.nome(), null);
        return repository.saveAddon(addon);
    }

    @Override
    @Transactional
    public SessionAddon updateAddon(Long id, String nome, BigDecimal preco, Integer ordem, boolean ativo) {
        SessionAddon current = repository.findAddonById(id).orElseThrow(() -> new SessionAddonNotFoundException(id));
        SessionAddon updated = current.withData(nome, preco, ordem == null ? current.ordem() : ordem, ativo);
        ensureAddonNameFree(updated.nome(), id);
        return repository.saveAddon(updated);
    }

    /**
     * Adicionais pedidos no lançamento, existentes e ativos, na ordem pedida. Id repetido conta duas
     * vezes — dois filtros são dois filtros.
     */
    List<SessionAddon> requireActiveAddons(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(id -> (id == null ? Optional.<SessionAddon>empty() : repository.findAddonById(id))
                        .filter(SessionAddon::ativo)
                        .orElseThrow(() -> new SessionAddonNotFoundException(id)))
                .toList();
    }

    private void ensureAddonNameFree(String nome, Long selfId) {
        repository.findAddonByNome(nome)
                .filter(a -> !a.id().equals(selfId))
                .ifPresent(a -> {
                    throw new SessionMenuConflictException("Já existe adicional de sessão com o nome " + nome);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionAssetType> listAssetTypes() {
        return repository.findAllAssetTypes();
    }

    @Override
    @Transactional
    public SessionAssetType createAssetType(String codigo, String nome, int quantidadeTotal, boolean incluso) {
        SessionAssetType type = SessionAssetType.create(codigo, nome, quantidadeTotal, incluso);
        if (repository.findAssetTypeByCodigo(type.codigo()).isPresent()) {
            throw new SessionMenuConflictException("Já existe utensílio com o código " + type.codigo());
        }
        return repository.saveAssetType(type);
    }

    @Override
    @Transactional
    public SessionAssetType updateAssetType(Long id, String nome, Integer quantidadeTotal, Boolean incluso,
            boolean ativo) {
        SessionAssetType current = repository.findAssetTypeById(id)
                .orElseThrow(() -> new SessionAssetTypeNotFoundException(id));
        return repository.saveAssetType(current.withData(nome,
                quantidadeTotal == null ? current.quantidadeTotal() : quantidadeTotal,
                incluso == null ? current.incluso() : incluso, ativo));
    }

    @Override
    @Transactional(readOnly = true)
    public SessionSettings getSettings() {
        return repository.getSettings();
    }

    @Override
    @Transactional
    public SessionSettings updateSettings(SessionSettings settings) {
        for (String code : new String[]{settings.vasoPadraoCodigo(), settings.vasoGrandeCodigo()}) {
            if (code != null && repository.findAssetTypeByCodigo(code).isEmpty()) {
                throw new SessionAssetTypeNotFoundException(code);
            }
        }
        return repository.saveSettings(settings);
    }

    // ── Regras usadas pelo lançamento da sessão (ComandaService) ─────────────────────────────

    /** Faixa existente e ativa — faixa desativada some do cardápio e não pode mais ser lançada. */
    SessionTier requireActiveTier(Long tierId) {
        if (tierId == null) {
            throw new SessionTierNotFoundException(null);
        }
        return repository.findTierById(tierId)
                .filter(SessionTier::ativo)
                .orElseThrow(() -> new SessionTierNotFoundException(tierId));
    }

    SessionSettings settings() {
        return repository.getSettings();
    }

    /**
     * A promoção vale pelo dia em que a MESA FOI ABERTA, no fuso do salão: a noite de quarta que
     * passa da meia-noite continua sendo a noite do duplo rosh.
     */
    boolean isDuploRoshDay(SessionSettings settings, Instant comandaOpenedAt) {
        DayOfWeek day = (comandaOpenedAt == null ? clock.instant() : comandaOpenedAt).atZone(ZONA_SALAO).getDayOfWeek();
        return settings.isDuploRoshDay(day);
    }

    /**
     * Trava e confere os utensílios que uma sessão nova leva: o vaso (padrão ou grande) e todo tipo
     * ativo marcado como incluso. Recusa com 409 se algum não tiver unidade livre. Deve ser chamado
     * antes de gravar a linha; a alocação em si é {@link #allocate}, com o id da linha já gerado.
     */
    List<SessionAssetType> reserveAssetsForSession(SessionSettings settings, boolean vasoGrande) {
        String vasoCode = vasoGrande ? settings.vasoGrandeCodigo() : settings.vasoPadraoCodigo();
        if (vasoCode == null) {
            throw new SessionMenuConflictException(vasoGrande
                    ? "Vaso grande não configurado no cardápio de sessão"
                    : "Vaso padrão não configurado no cardápio de sessão");
        }
        SessionAssetType vaso = repository.findAssetTypeByCodigo(vasoCode)
                .filter(SessionAssetType::ativo)
                .orElseThrow(() -> new SessionAssetTypeNotFoundException(vasoCode));
        List<Long> ids = new ArrayList<>();
        ids.add(vaso.id());
        repository.findAllAssetTypes().stream()
                .filter(t -> t.ativo() && t.incluso() && !t.id().equals(vaso.id()))
                .forEach(t -> ids.add(t.id()));

        List<SessionAssetType> locked = repository.lockAssetTypes(ids);
        Map<Long, Integer> inUse = repository.countInUseByAssetType();
        for (SessionAssetType type : locked) {
            if (type.quantidadeTotal() - inUse.getOrDefault(type.id(), 0) < 1) {
                throw new SessionAssetUnavailableException(type.codigo(), type.nome(), type.quantidadeTotal());
            }
        }
        return locked;
    }

    void allocate(Long comandaItemId, List<SessionAssetType> types) {
        Instant now = clock.instant();
        repository.saveAllocations(types.stream()
                .map(t -> SessionAssetAllocation.allocate(comandaItemId, t.id(), now))
                .toList());
    }

    /** Devolve à casa os utensílios das linhas informadas (fechamento, cancelamento, remoção). */
    void release(Collection<Long> comandaItemIds) {
        repository.releaseAllocations(comandaItemIds, clock.instant());
    }

    private void ensureTierNameFree(String nome, Long selfId) {
        repository.findTierByNome(nome)
                .filter(t -> !t.id().equals(selfId))
                .ifPresent(t -> {
                    throw new SessionMenuConflictException("Já existe faixa de sessão com o nome " + nome);
                });
    }
}
