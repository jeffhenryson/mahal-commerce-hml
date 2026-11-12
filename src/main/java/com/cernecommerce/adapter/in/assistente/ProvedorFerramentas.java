package com.cernecommerce.adapter.in.assistente;

import java.util.List;

/** Grupo de ferramentas (e dos executores das ações que elas propõem) de uma área do sistema. */
public interface ProvedorFerramentas {

    List<Ferramenta> ferramentas();

    default List<ExecutorAcao> executores() {
        return List.of();
    }
}
