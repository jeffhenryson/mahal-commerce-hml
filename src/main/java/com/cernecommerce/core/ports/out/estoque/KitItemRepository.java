package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.estoque.KitItem;

import java.util.List;

/** Itens de kit pré-montado (tabela {@code kit_item}). */
public interface KitItemRepository {

    /** Itens do kit, ordenados por {@code position}. Nomes de categoria/marca vêm nulos. */
    List<KitItem> findByKitProductId(Long kitProductId);

    /**
     * Substitui a lista inteira do kit: item com id existente atualiza, item sem id cria, id que não
     * veio é apagado. Devolve a lista salva, ordenada por {@code position}.
     */
    List<KitItem> replaceAll(Long kitProductId, List<KitItem> items);
}
