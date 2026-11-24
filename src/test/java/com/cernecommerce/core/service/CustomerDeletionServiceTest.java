package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.crm.CustomerHasOpenBalanceException;
import com.cernecommerce.core.domain.exception.crm.CustomerHasOpenComandaException;
import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.ecommerce.CentralPedidosConfig;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.out.cashback.CashbackEntryRepository;
import com.cernecommerce.core.ports.out.crm.CustomerNoteRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.crm.CustomerTagRepository;
import com.cernecommerce.core.ports.out.ecommerce.CentralPedidosConfigRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.recebivel.CustomerReceivableRepository;
import com.cernecommerce.core.ports.out.salao.SalaoAutoatendimentoRepository;
import com.cernecommerce.core.ports.out.storage.FileStoragePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** V206 — exclusão do perfil do cliente (anonimização LGPD). */
@ExtendWith(MockitoExtension.class)
class CustomerDeletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T15:00:00Z");

    @Mock CustomerRepository customerRepository;
    @Mock CustomerTagRepository customerTagRepository;
    @Mock CustomerNoteRepository customerNoteRepository;
    @Mock FileStoragePort storage;
    @Mock CashbackEntryRepository cashbackEntryRepository;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock ComandaRepository comandaRepository;
    @Mock SalaoAutoatendimentoRepository salaoRepository;
    @Mock CentralPedidosConfigRepository centralConfig;
    @Mock CustomerReceivableRepository receivableRepository;

    CustomerDeletionService service;

    @BeforeEach
    void setUp() {
        service = new CustomerDeletionService(customerRepository, customerTagRepository, customerNoteRepository,
                storage, cashbackEntryRepository, cashbackUseCase, comandaRepository, salaoRepository, centralConfig,
                receivableRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static Customer ativo() {
        return Customer.of(7L, "Joana Silva", "83999990000", "joana@example.com", "12345678901", "Balcão",
                NOW.minusSeconds(86_400), CustomerStage.CLIENTE_ATIVO);
    }

    private static Customer anonimizado() {
        return Customer.of(7L, Customer.NOME_ANONIMIZADO, null, null, null, null, NOW.minusSeconds(86_400),
                CustomerStage.NOVO_LEAD, null, null, null, "Novo", false, NOW.minusSeconds(60));
    }

    /** Cliente sem nenhum bloqueio: sem comanda, sem solicitação pendente, sem fiado. */
    private void semBloqueios() {
        when(customerRepository.findById(7L)).thenReturn(Optional.of(ativo()));
        when(comandaRepository.findOpenIdsByCustomerId(7L)).thenReturn(List.of());
        when(centralConfig.atual()).thenReturn(CentralPedidosConfig.padrao());
        when(salaoRepository.existsPendenteDoCliente(eq(7L), any())).thenReturn(false);
        when(receivableRepository.sumOpenBalance(7L)).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void deleteCustomer_anonymizesAndCleansEverythingInOrder() {
        semBloqueios();
        when(customerNoteRepository.findAttachmentKeysByCustomerId(7L)).thenReturn(List.of());
        when(customerRepository.anonymize(7L, "Cliente excluído", NOW)).thenReturn(true);

        assertThat(service.deleteCustomer(7L)).isTrue();

        InOrder order = inOrder(cashbackEntryRepository, cashbackUseCase, customerTagRepository,
                customerNoteRepository, customerRepository);
        order.verify(cashbackEntryRepository).lockCustomer(7L);
        order.verify(cashbackUseCase).forfeitAll(7L, CashbackEntry.REASON_CUSTOMER_DELETED);
        order.verify(customerTagRepository).removeAllByCustomerId(7L);
        order.verify(customerNoteRepository).deleteAllByCustomerId(7L);
        order.verify(customerRepository).anonymize(7L, "Cliente excluído", NOW);
    }

    @Test
    void deleteCustomer_checksPendingSessionRequestWithinTheConfiguredValidity() {
        semBloqueios();
        when(customerNoteRepository.findAttachmentKeysByCustomerId(7L)).thenReturn(List.of());
        when(customerRepository.anonymize(anyLong(), anyString(), any())).thenReturn(true);

        service.deleteCustomer(7L);

        int validadeMin = CentralPedidosConfig.padrao().expiracaoSolicitacaoMin();
        verify(salaoRepository).existsPendenteDoCliente(7L, NOW.minusSeconds(validadeMin * 60L));
    }

    @Test
    void deleteCustomer_unknown_throwsNotFound() {
        when(customerRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCustomer(7L)).isInstanceOf(CustomerNotFoundException.class);
        verifyNoInteractions(cashbackUseCase, customerTagRepository, customerNoteRepository);
    }

    @Test
    void deleteCustomer_alreadyAnonymized_isIdempotentNoOp() {
        when(customerRepository.findById(7L)).thenReturn(Optional.of(anonimizado()));

        assertThat(service.deleteCustomer(7L)).isFalse();
        verify(customerRepository, never()).anonymize(anyLong(), anyString(), any());
        verify(cashbackEntryRepository, never()).lockCustomer(anyLong());
        verifyNoInteractions(cashbackUseCase, customerTagRepository, customerNoteRepository, comandaRepository,
                receivableRepository, storage);
    }

    @Test
    void deleteCustomer_withOpenComanda_throwsConflict_andChangesNothing() {
        when(customerRepository.findById(7L)).thenReturn(Optional.of(ativo()));
        when(comandaRepository.findOpenIdsByCustomerId(7L)).thenReturn(List.of(31L));

        assertThatThrownBy(() -> service.deleteCustomer(7L)).isInstanceOf(CustomerHasOpenComandaException.class);
        verifyNoInteractions(cashbackUseCase, customerTagRepository, customerNoteRepository);
        verify(customerRepository, never()).anonymize(anyLong(), anyString(), any());
    }

    @Test
    void deleteCustomer_withPendingSessionRequest_throwsConflict() {
        when(customerRepository.findById(7L)).thenReturn(Optional.of(ativo()));
        when(comandaRepository.findOpenIdsByCustomerId(7L)).thenReturn(List.of());
        when(centralConfig.atual()).thenReturn(CentralPedidosConfig.padrao());
        when(salaoRepository.existsPendenteDoCliente(eq(7L), any())).thenReturn(true);

        assertThatThrownBy(() -> service.deleteCustomer(7L)).isInstanceOf(CustomerHasOpenComandaException.class);
        verifyNoInteractions(cashbackUseCase);
    }

    @Test
    void deleteCustomer_withOpenBalance_throwsConflict_andChangesNothing() {
        when(customerRepository.findById(7L)).thenReturn(Optional.of(ativo()));
        when(comandaRepository.findOpenIdsByCustomerId(7L)).thenReturn(List.of());
        when(centralConfig.atual()).thenReturn(CentralPedidosConfig.padrao());
        when(salaoRepository.existsPendenteDoCliente(eq(7L), any())).thenReturn(false);
        when(receivableRepository.sumOpenBalance(7L)).thenReturn(new BigDecimal("45.90"));

        assertThatThrownBy(() -> service.deleteCustomer(7L)).isInstanceOf(CustomerHasOpenBalanceException.class);
        verifyNoInteractions(cashbackUseCase, customerTagRepository, customerNoteRepository);
    }

    @Test
    void deleteCustomer_deletesAttachmentFilesOnlyAfterCommit() {
        semBloqueios();
        when(customerNoteRepository.findAttachmentKeysByCustomerId(7L)).thenReturn(List.of("a.pdf", "b.jpg"));
        when(customerRepository.anonymize(anyLong(), anyString(), any())).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();

        service.deleteCustomer(7L);

        verifyNoInteractions(storage);
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs).hasSize(1);
        syncs.forEach(TransactionSynchronization::afterCommit);
        verify(storage).delete("a.pdf");
        verify(storage).delete("b.jpg");
    }

    @Test
    void deleteCustomer_concurrentDeletionWins_isIdempotentNoOp() {
        semBloqueios();
        when(customerNoteRepository.findAttachmentKeysByCustomerId(7L)).thenReturn(List.of("a.pdf"));
        when(customerRepository.anonymize(anyLong(), anyString(), any())).thenReturn(false);

        assertThat(service.deleteCustomer(7L)).isFalse();
        verifyNoInteractions(storage);
    }
}
