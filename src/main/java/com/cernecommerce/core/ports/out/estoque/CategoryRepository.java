package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Category;

import java.util.Optional;

/**
 * Port de saída para persistência de categorias do catálogo.
 */
public interface CategoryRepository {

    Category save(Category category);

    Optional<Category> findById(Long id);

    /**
     * Busca por nome, <b>ignorando maiúsculas</b> — é o caminho de compatibilidade: o admin ainda
     * manda a categoria como texto livre no cadastro do produto, e "Narguilé" e "narguilé" não
     * podem virar duas categorias.
     */
    Optional<Category> findByName(String name);

    /** Listagem do admin, ordenada por destaque, ordem e nome. Inclui as inativas. */
    PageResult<Category> findAll(int page, int size);

    /** Categorias ativas na ordem da vitrine: destaque primeiro, depois ordem, depois nome. */
    java.util.List<Category> findActiveOrdered();

    /**
     * Remove a categoria (Bloco 2.3). O chamador ({@code EstoqueService.deleteCategory}) já
     * garantiu que não há produto vinculado — esta camada não repete a checagem.
     */
    void deleteById(Long id);

    /**
     * PDV-F045 — as categorias de cigarros, ativas ou não: categoria inativa some da vitrine, mas os
     * produtos dela continuam à venda (e no balcão, pela Central de cigarros).
     */
    java.util.List<Category> findCigarette();
}
