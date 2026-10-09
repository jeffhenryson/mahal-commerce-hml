package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.estoque.SkuPackaging;

import java.util.List;
import java.util.Optional;

/** Persistência das ligações de embalagem (EST-F032). */
public interface SkuPackagingRepository {

    /** A embalagem que contém {@code childSku}, se houver — um filho tem um pai só. */
    Optional<SkuPackaging> findByChild(String childSku);

    /** As ligações em que {@code parentSku} é a embalagem de fora. */
    List<SkuPackaging> findByParent(String parentSku);

    /** Todas as ligações — poucas dezenas numa tabacaria; é a fonte da central de cigarros (PDV-F041). */
    List<SkuPackaging> findAll();

    /** Grava ou substitui a ligação do filho. */
    SkuPackaging save(SkuPackaging packaging);

    void deleteByChild(String childSku);
}
