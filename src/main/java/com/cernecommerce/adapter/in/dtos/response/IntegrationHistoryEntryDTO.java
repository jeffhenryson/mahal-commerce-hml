package com.cernecommerce.adapter.in.dtos.response;

import java.time.Instant;
import java.util.List;

/** Alteração numa integração. Nunca traz valores, só os nomes dos campos alterados. */
public record IntegrationHistoryEntryDTO(Instant at, String user, String action, List<String> fields) { }
