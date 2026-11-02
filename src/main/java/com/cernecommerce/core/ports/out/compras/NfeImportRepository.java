package com.cernecommerce.core.ports.out.compras;

import com.cernecommerce.core.domain.model.compras.NfeImport;

import java.util.Optional;

/** Port de saída para persistência de importações de NF-e (EST-F005). */
public interface NfeImportRepository {

    Optional<NfeImport> findById(Long id);

    NfeImport save(NfeImport nfeImport);

    /** V182 — o import já confirmado com esta chave de acesso, se houver. Chave nula: vazio. */
    Optional<NfeImport> findConfirmedByAccessKey(String accessKey);
}
