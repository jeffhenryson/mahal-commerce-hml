package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.usermetrics.UserMetrics;

import java.time.Instant;

/**
 * Perfil > métricas de um usuário, identificado pelo username (o controller resolve {@code me} ou
 * {@code {id}}). Períodos obrigatórios onde indicado, inclusivos, até 366 dias
 * ({@code 400 INVALID_REPORT_PERIOD}).
 */
public interface UserMetricsUseCase {

    UserMetrics.Performance performance(String username, Instant from, Instant to);

    PageResult<UserMetrics.CashSessionItem> cashSessions(String username, int page, int size);

    /** {@code from}/{@code to} opcionais; se os dois vierem, valem as regras do período. */
    PageResult<UserMetrics.AccessLogItem> accessLog(String username, Instant from, Instant to, int page, int size);

    UserMetrics.Attendance attendance(String username, Instant from, Instant to);

    UserMetrics.CustomerOrders customerOrders(Long customerId, Instant from, Instant to, int page, int size);
}
