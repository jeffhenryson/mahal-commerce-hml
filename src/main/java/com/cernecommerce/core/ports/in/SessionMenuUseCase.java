package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.pdv.SessionAssetType;
import com.cernecommerce.core.domain.model.pdv.SessionMenu;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionAddon;
import com.cernecommerce.core.domain.model.pdv.SessionTier;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port de entrada do cardápio de sessão da mesa (PDV-F021): cadastro de faixas, utensílios e
 * configuração (admin) e a leitura do cardápio para a tela da mesa (atendente).
 */
public interface SessionMenuUseCase {

    /** Faixas ativas, utensílios ativos com disponibilidade, configuração e se hoje é duplo rosh. */
    SessionMenu getMenu();

    List<SessionTier> listTiers();

    /** @throws com.cernecommerce.core.domain.exception.pdv.SessionMenuConflictException nome repetido */
    SessionTier createTier(String nome, BigDecimal preco, String marcas, int ordem);

    /**
     * @throws com.cernecommerce.core.domain.exception.pdv.SessionTierNotFoundException se não existir
     * @throws com.cernecommerce.core.domain.exception.pdv.SessionMenuConflictException nome repetido
     */
    /** PDV-C028 — {@code ordem} nula mantém a atual. */
    SessionTier updateTier(Long id, String nome, BigDecimal preco, String marcas, Integer ordem, boolean ativo);

    /** PDV-F024 — adicionais pagos (ativos e inativos). */
    List<SessionAddon> listAddons();

    /** @throws com.cernecommerce.core.domain.exception.pdv.SessionMenuConflictException nome repetido */
    SessionAddon createAddon(String nome, BigDecimal preco, int ordem);

    /**
     * @throws com.cernecommerce.core.domain.exception.pdv.SessionAddonNotFoundException se não existir
     * @throws com.cernecommerce.core.domain.exception.pdv.SessionMenuConflictException nome repetido
     */
    /** PDV-C028 — {@code ordem} nula mantém a atual. */
    SessionAddon updateAddon(Long id, String nome, BigDecimal preco, Integer ordem, boolean ativo);

    List<SessionAssetType> listAssetTypes();

    /** @throws com.cernecommerce.core.domain.exception.pdv.SessionMenuConflictException código repetido */
    SessionAssetType createAssetType(String codigo, String nome, int quantidadeTotal, boolean incluso);

    /** @throws com.cernecommerce.core.domain.exception.pdv.SessionAssetTypeNotFoundException se não existir */
    /**
     * PDV-C028 — {@code quantidadeTotal} e {@code incluso} nulos mantêm os atuais. Primitivos, um PUT
     * que só renomeava a pinça zerava a quantidade dela, e todo lançamento seguinte recusava por
     * falta de utensílio.
     */
    SessionAssetType updateAssetType(Long id, String nome, Integer quantidadeTotal, Boolean incluso, boolean ativo);

    SessionSettings getSettings();

    /**
     * @throws com.cernecommerce.core.domain.exception.pdv.SessionAssetTypeNotFoundException se o
     *         código de vaso não existir
     */
    SessionSettings updateSettings(SessionSettings settings);
}
