package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ComandaSessionAssetEntity;
import com.cernecommerce.adapter.out.persistence.entity.SessionAssetTypeEntity;
import com.cernecommerce.adapter.out.persistence.entity.SessionSettingsEntity;
import com.cernecommerce.adapter.out.persistence.entity.SessionAddonEntity;
import com.cernecommerce.adapter.out.persistence.entity.SessionTierEntity;
import com.cernecommerce.core.domain.model.pdv.SessionAssetAllocation;
import com.cernecommerce.core.domain.model.pdv.SessionAssetType;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionAddon;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.ports.out.pdv.SessionMenuRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional
public class SessionMenuRepositoryImpl implements SessionMenuRepository {

    private final SessionTierJpaRepository tierJpa;
    private final SessionAssetTypeJpaRepository assetTypeJpa;
    private final SessionSettingsJpaRepository settingsJpa;
    private final ComandaSessionAssetJpaRepository allocationJpa;

    private final SessionAddonJpaRepository addonJpa;

    public SessionMenuRepositoryImpl(SessionTierJpaRepository tierJpa, SessionAssetTypeJpaRepository assetTypeJpa,
            SessionSettingsJpaRepository settingsJpa, ComandaSessionAssetJpaRepository allocationJpa,
            SessionAddonJpaRepository addonJpa) {
        this.addonJpa = addonJpa;
        this.tierJpa = tierJpa;
        this.assetTypeJpa = assetTypeJpa;
        this.settingsJpa = settingsJpa;
        this.allocationJpa = allocationJpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionTier> findAllTiers() {
        return tierJpa.findAllByOrderByOrdemAscIdAsc().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionTier> findTierById(Long id) {
        return tierJpa.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionTier> findTierByNome(String nome) {
        return tierJpa.findFirstByNomeIgnoreCase(nome).map(this::toDomain);
    }

    @Override
    public SessionTier saveTier(SessionTier tier) {
        SessionTierEntity e = new SessionTierEntity(tier.id(), tier.nome(), tier.preco(), tier.marcas(), tier.ordem(),
                tier.ativo());
        return toDomain(tierJpa.save(e));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionAddon> findAllAddons() {
        return addonJpa.findAllByOrderByOrdemAscIdAsc().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionAddon> findAddonById(Long id) {
        return addonJpa.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionAddon> findAddonByNome(String nome) {
        return addonJpa.findFirstByNomeIgnoreCase(nome).map(this::toDomain);
    }

    @Override
    public SessionAddon saveAddon(SessionAddon addon) {
        return toDomain(addonJpa.save(new SessionAddonEntity(addon.id(), addon.nome(), addon.preco(), addon.ordem(),
                addon.ativo())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionAssetType> findAllAssetTypes() {
        return assetTypeJpa.findAllByOrderByIdAsc().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionAssetType> findAssetTypeById(Long id) {
        return assetTypeJpa.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionAssetType> findAssetTypeByCodigo(String codigo) {
        return assetTypeJpa.findByCodigo(codigo).map(this::toDomain);
    }

    @Override
    public SessionAssetType saveAssetType(SessionAssetType type) {
        SessionAssetTypeEntity e = new SessionAssetTypeEntity(type.id(), type.codigo(), type.nome(),
                type.quantidadeTotal(), type.incluso(), type.ativo());
        return toDomain(assetTypeJpa.save(e));
    }

    @Override
    public List<SessionAssetType> lockAssetTypes(Collection<Long> ids) {
        return assetTypeJpa.findAllByIdForUpdate(ids).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SessionSettings getSettings() {
        return settingsJpa.findById(SessionSettingsEntity.SINGLETON_ID)
                .map(e -> new SessionSettings(e.getVasoPadraoCodigo(), e.getVasoGrandeCodigo(),
                        e.getUpgradeVasoGrandePreco(), SessionSettings.parseDias(e.getDiasDuploRosh()),
                        e.getUpgradeCarvaoJumboPreco()))
                .orElseGet(SessionSettings::defaults);
    }

    @Override
    public SessionSettings saveSettings(SessionSettings settings) {
        settingsJpa.save(new SessionSettingsEntity(SessionSettingsEntity.SINGLETON_ID, settings.vasoPadraoCodigo(),
                settings.vasoGrandeCodigo(), settings.upgradeVasoGrandePreco(), settings.diasDuploRoshAsText(),
                settings.upgradeCarvaoJumboPreco()));
        return getSettings();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> countInUseByAssetType() {
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : allocationJpa.sumInUseByType()) {
            result.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return result;
    }

    @Override
    public void saveAllocations(List<SessionAssetAllocation> allocations) {
        allocationJpa.saveAll(allocations.stream()
                .map(a -> new ComandaSessionAssetEntity(a.id(), a.comandaItemId(), a.assetTypeId(), a.quantidade(),
                        a.alocadoEm(), a.liberadoEm()))
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionAssetAllocation> findOpenAllocations(Collection<Long> comandaItemIds) {
        if (comandaItemIds.isEmpty()) {
            return List.of();
        }
        return allocationJpa.findByComandaItemIdInAndLiberadoEmIsNull(comandaItemIds).stream()
                .map(e -> new SessionAssetAllocation(e.getId(), e.getComandaItemId(), e.getAssetTypeId(),
                        e.getQuantidade(), e.getAlocadoEm(), e.getLiberadoEm()))
                .toList();
    }

    @Override
    public int releaseAllocations(Collection<Long> comandaItemIds, Instant at) {
        if (comandaItemIds.isEmpty()) {
            return 0;
        }
        return allocationJpa.releaseByItemIds(comandaItemIds, at);
    }

    private SessionTier toDomain(SessionTierEntity e) {
        return new SessionTier(e.getId(), e.getNome(), e.getPreco(), e.getMarcas(), e.getOrdem(), e.isAtivo());
    }

    private SessionAddon toDomain(SessionAddonEntity e) {
        return new SessionAddon(e.getId(), e.getNome(), e.getPreco(), e.getOrdem(), e.isAtivo());
    }

    private SessionAssetType toDomain(SessionAssetTypeEntity e) {
        return new SessionAssetType(e.getId(), e.getCodigo(), e.getNome(), e.getQuantidadeTotal(), e.isIncluso(),
                e.isAtivo());
    }
}
