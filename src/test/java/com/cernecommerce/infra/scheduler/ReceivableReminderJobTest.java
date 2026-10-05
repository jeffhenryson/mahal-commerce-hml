package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.recebivel.CustomerReceivable;
import com.cernecommerce.core.domain.model.recebivel.ReceivableFilter;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.in.ReceivableUseCase.ReceivableView;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReceivableReminderJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    private ReceivableUseCase receivableUseCase;
    private CustomerRepository customerRepository;
    private EmailPort emailPort;
    private ReceivableReminderJob job;

    @BeforeEach
    void setUp() {
        receivableUseCase = mock(ReceivableUseCase.class);
        customerRepository = mock(CustomerRepository.class);
        emailPort = mock(EmailPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        job = new ReceivableReminderJob(receivableUseCase, customerRepository, emailPort, clock);
        when(receivableUseCase.list(any(), eq(0), anyInt())).thenReturn(page());
    }

    @Test
    void lembra_d_menos_3_e_d0_um_email_por_cliente() {
        when(receivableUseCase.list(argThat(dueOn(TODAY.plusDays(3))), eq(0), anyInt())).thenReturn(page(
                receivable(42L, "50.00", TODAY.plusDays(3)), receivable(42L, "30.00", TODAY.plusDays(3))));
        when(receivableUseCase.list(argThat(dueOn(TODAY)), eq(0), anyInt())).thenReturn(page(
                receivable(43L, "20.00", TODAY)));
        when(customerRepository.findById(42L)).thenReturn(Optional.of(customer(42L, "a@x.com")));
        when(customerRepository.findById(43L)).thenReturn(Optional.of(customer(43L, "b@x.com")));

        job.remind();

        ArgumentCaptor<NotificationEmail> first = ArgumentCaptor.forClass(NotificationEmail.class);
        verify(emailPort).sendCustomerNotice(eq("a@x.com"), first.capture());
        assertThat(first.getValue().category()).isEqualTo("fiado.lembrete");
        assertThat(first.getValue().subject()).contains("80,00").contains("08/10/2026");

        ArgumentCaptor<NotificationEmail> today = ArgumentCaptor.forClass(NotificationEmail.class);
        verify(emailPort).sendCustomerNotice(eq("b@x.com"), today.capture());
        assertThat(today.getValue().category()).isEqualTo("fiado.vence-hoje");
        assertThat(today.getValue().tone()).isEqualTo(NotificationEmail.Tone.WARNING);
    }

    @Test
    void cliente_sem_email_e_ignorado() {
        when(receivableUseCase.list(argThat(dueOn(TODAY)), eq(0), anyInt())).thenReturn(page(
                receivable(42L, "20.00", TODAY)));
        when(customerRepository.findById(42L)).thenReturn(Optional.of(customer(42L, null)));

        job.remind();

        verifyNoInteractions(emailPort);
    }

    private static org.mockito.ArgumentMatcher<ReceivableFilter> dueOn(LocalDate date) {
        return f -> f != null && date.equals(f.dueFrom()) && date.equals(f.dueTo());
    }

    private static PageResult<ReceivableView> page(CustomerReceivable... receivables) {
        List<ReceivableView> views = java.util.Arrays.stream(receivables)
                .map(r -> new ReceivableView(r, null, null, null, 0, List.of()))
                .toList();
        return new PageResult<>(views, 0, 500, views.size(), 1);
    }

    private static CustomerReceivable receivable(Long customerId, String amount, LocalDate due) {
        return CustomerReceivable.open(customerId, 1L, null, 1L, new BigDecimal(amount), due, "atendente",
                Instant.parse("2026-10-01T15:00:00Z"), List.of());
    }

    private static Customer customer(Long id, String email) {
        return new Customer(id, "Cliente", "11999999999", email, null, "PDV", Instant.now(), CustomerStage.NOVO_LEAD);
    }
}
