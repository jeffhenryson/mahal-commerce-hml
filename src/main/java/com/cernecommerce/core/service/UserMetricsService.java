package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSessionFilter;
import com.cernecommerce.core.domain.model.usermetrics.UserMetrics;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.UserMetricsUseCase;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.user.UserMetricsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Perfil > métricas. As regras de receita e de total vendido são as mesmas das telas de admin
 * ({@code GET /orders/analytics/operators} e {@code GET /pdv/sessions/operators/{u}/stats}), para
 * os números do perfil baterem com os da gerência.
 */
public class UserMetricsService implements UserMetricsUseCase {

    static final ZoneId ZONA_LOJA = ZoneId.of("America/Sao_Paulo");

    private static final Set<String> LOGIN_ACTIONS = Set.of(
            EventType.USER_LOGGED_IN.name(), EventType.OAUTH_GOOGLE_LOGIN.name());
    private static final String LOGOUT_ACTION = EventType.USER_LOGGED_OUT.name();
    private static final String LOGIN_FAILED_ACTION = EventType.LOGIN_FAILED.name();
    private static final List<String> ACCESS_ACTIONS = List.of(EventType.USER_LOGGED_IN.name(),
            EventType.OAUTH_GOOGLE_LOGIN.name(), LOGOUT_ACTION, LOGIN_FAILED_ACTION);
    private static final List<String> ATTENDANCE_ACTIONS = List.of(EventType.USER_LOGGED_IN.name(),
            EventType.OAUTH_GOOGLE_LOGIN.name(), LOGOUT_ACTION);

    private final UserMetricsRepository repository;
    private final CashRegisterRepository cashRegisterRepository;
    private final PdvUseCase pdvUseCase;

    public UserMetricsService(UserMetricsRepository repository, CashRegisterRepository cashRegisterRepository,
            PdvUseCase pdvUseCase) {
        this.repository = repository;
        this.cashRegisterRepository = cashRegisterRepository;
        this.pdvUseCase = pdvUseCase;
    }

    @Override
    public UserMetrics.Performance performance(String username, Instant from, Instant to) {
        OrderReportService.checkPeriod(from, to);

        UserMetrics.PdvTotals pdv = repository.pdvTotals(username, from, to);
        BigDecimal averageTicket = pdv.orders() == 0 ? BigDecimal.ZERO
                : pdv.revenue().divide(BigDecimal.valueOf(pdv.orders()), 2, RoundingMode.HALF_UP);

        List<UserMetrics.ClosedSession> closed = repository.cashSessionsClosed(username, from, to);
        BigDecimal totalSold = BigDecimal.ZERO;
        BigDecimal totalDifference = BigDecimal.ZERO;
        long divergences = 0;
        for (UserMetrics.ClosedSession session : closed) {
            // Mesmo cálculo do resumo de fechamento: não há total gravado na sessão.
            totalSold = totalSold.add(pdvUseCase.getSessionSummary(session.id()).totalReceived());
            BigDecimal difference = session.differenceAmount();
            if (difference != null && difference.signum() != 0) {
                divergences++;
                totalDifference = totalDifference.add(difference);
            }
        }

        return new UserMetrics.Performance(
                new UserMetrics.PdvPerformance(pdv.revenue(), pdv.orders(), averageTicket),
                new UserMetrics.CashPerformance(repository.cashSessionsOpened(username, from, to), closed.size(),
                        totalSold, divergences, totalDifference),
                new UserMetrics.TablesPerformance(repository.tabsOpened(username, from, to),
                        repository.tabsClosed(username, from, to), repository.itemsLaunched(username, from, to)));
    }

    @Override
    public PageResult<UserMetrics.CashSessionItem> cashSessions(String username, int page, int size) {
        PageResult<CashRegisterSession> sessions = cashRegisterRepository.findAll(
                new CashRegisterSessionFilter(null, null, null, username), page, size);
        List<UserMetrics.CashSessionItem> content = sessions.content().stream()
                .map(s -> new UserMetrics.CashSessionItem(s.id(), s.openedAt(), s.closedAt(), s.status().name(),
                        pdvUseCase.getSessionSummary(s.id()).totalReceived(),
                        s.status() == CashRegisterSession.Status.OPEN ? null : s.differenceAmount()))
                .toList();
        return new PageResult<>(content, sessions.page(), sessions.size(), sessions.totalElements(),
                sessions.totalPages());
    }

    @Override
    public PageResult<UserMetrics.AccessLogItem> accessLog(String username, Instant from, Instant to, int page,
            int size) {
        // Período opcional aqui; só se valida quando vem inteiro.
        if (from != null && to != null) {
            OrderReportService.checkPeriod(from, to);
        }
        PageResult<UserMetrics.AuthEvent> events = repository.authEvents(username, ACCESS_ACTIONS, from, to, page,
                size);
        List<UserMetrics.AccessLogItem> content = events.content().stream()
                .map(e -> new UserMetrics.AccessLogItem(e.id(), e.timestamp(), accessType(e.action()), e.ipAddress(),
                        e.userAgent()))
                .toList();
        return new PageResult<>(content, events.page(), events.size(), events.totalElements(), events.totalPages());
    }

    @Override
    public UserMetrics.Attendance attendance(String username, Instant from, Instant to) {
        OrderReportService.checkPeriod(from, to);
        // Cronológico: o primeiro LOGIN do dia é o primeiro visto, o último LOGOUT é o último visto.
        Map<LocalDate, Instant[]> byDay = new LinkedHashMap<>();
        for (UserMetrics.AuthEvent e : repository.authEventsChronological(username, ATTENDANCE_ACTIONS, from, to)) {
            LocalDate day = e.timestamp().atZone(ZONA_LOJA).toLocalDate();
            if (LOGIN_ACTIONS.contains(e.action())) {
                byDay.computeIfAbsent(day, d -> new Instant[]{e.timestamp(), null});
            } else {
                Instant[] slot = byDay.get(day);
                if (slot != null) slot[1] = e.timestamp();
            }
        }
        List<UserMetrics.AttendanceDay> days = new ArrayList<>();
        byDay.forEach((day, slot) -> days.add(new UserMetrics.AttendanceDay(day, slot[0], slot[1])));
        days.sort(Comparator.comparing(UserMetrics.AttendanceDay::date).reversed());
        return new UserMetrics.Attendance(days.size(), days);
    }

    @Override
    public UserMetrics.CustomerOrders customerOrders(Long customerId, Instant from, Instant to, int page, int size) {
        OrderReportService.checkPeriod(from, to);
        return new UserMetrics.CustomerOrders(repository.customerOrdersSummary(customerId, from, to),
                repository.customerOrders(customerId, from, to, page, size));
    }

    private static UserMetrics.AccessType accessType(String action) {
        if (LOGOUT_ACTION.equals(action)) return UserMetrics.AccessType.LOGOUT;
        if (LOGIN_FAILED_ACTION.equals(action)) return UserMetrics.AccessType.LOGIN_FAILED;
        return UserMetrics.AccessType.LOGIN;
    }
}
