package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.config.IntegrationHistoryEntry;

import java.util.List;

/** Histórico de alterações de uma integração da loja ({@code /store/integrations/{tipo}/history}). */
public interface IntegrationHistoryUseCase {

    /** Mais recente primeiro; {@code limit} entre 1 e 50. */
    List<IntegrationHistoryEntry> history(String integration, int limit);
}
