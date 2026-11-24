package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.crm.CustomerHasOpenBalanceException;
import com.cernecommerce.core.domain.exception.crm.CustomerHasOpenComandaException;
import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.CustomerDeletionUseCase;
import com.cernecommerce.core.ports.out.cashback.CashbackEntryRepository;
import com.cernecommerce.core.ports.out.crm.CustomerNoteRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.crm.CustomerTagRepository;
import com.cernecommerce.core.ports.out.ecommerce.CentralPedidosConfigRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.recebivel.CustomerReceivableRepository;
import com.cernecommerce.core.ports.out.salao.SalaoAutoatendimentoRepository;
import com.cernecommerce.core.ports.out.storage.FileStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * V206 — exclusão do perfil do cliente = anonimização (LGPD). Ver {@link CustomerDeletionUseCase}.
 *
 * <p>Ordem: trava a linha do cliente (a mesma trava do resgate de cashback, que fecha a corrida com
 * uma venda em andamento), confere os bloqueios, zera o cashback por lançamento, apaga tags, notas e
 * anexos e por fim anula os dados pessoais. Os arquivos dos anexos saem do disco só depois do commit:
 * um rollback não pode deixar a nota apontando para um arquivo que já sumiu.</p>
 *
 * <p>Automações e campanhas não têm fila por cliente (o disparo é síncrono): o que se cancela são
 * os disparos futuros, e isso fica nas consultas, que ignoram o cliente anonimizado.</p>
 */
public class CustomerDeletionService implements CustomerDeletionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CustomerDeletionService.class);

    private final CustomerRepository customerRepository;
    private final CustomerTagRepository customerTagRepository;
    private final CustomerNoteRepository customerNoteRepository;
    private final FileStoragePort noteAttachmentStorage;
    private final CashbackEntryRepository cashbackEntryRepository;
    private final CashbackUseCase cashbackUseCase;
    private final ComandaRepository comandaRepository;
    private final SalaoAutoatendimentoRepository salaoRepository;
    private final CentralPedidosConfigRepository centralConfig;
    private final CustomerReceivableRepository receivableRepository;
    private final Clock clock;

    public CustomerDeletionService(CustomerRepository customerRepository, CustomerTagRepository customerTagRepository,
            CustomerNoteRepository customerNoteRepository, FileStoragePort noteAttachmentStorage,
            CashbackEntryRepository cashbackEntryRepository, CashbackUseCase cashbackUseCase,
            ComandaRepository comandaRepository, SalaoAutoatendimentoRepository salaoRepository,
            CentralPedidosConfigRepository centralConfig, CustomerReceivableRepository receivableRepository,
            Clock clock) {
        this.customerRepository = customerRepository;
        this.customerTagRepository = customerTagRepository;
        this.customerNoteRepository = customerNoteRepository;
        this.noteAttachmentStorage = noteAttachmentStorage;
        this.cashbackEntryRepository = cashbackEntryRepository;
        this.cashbackUseCase = cashbackUseCase;
        this.comandaRepository = comandaRepository;
        this.salaoRepository = salaoRepository;
        this.centralConfig = centralConfig;
        this.receivableRepository = receivableRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean deleteCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        if (customer.isAnonymized()) {
            // Idempotente: repetir a exclusão de um perfil já anonimizado é 204, sem efeito.
            return false;
        }
        cashbackEntryRepository.lockCustomer(customer.id());

        Instant now = clock.instant();
        if (!comandaRepository.findOpenIdsByCustomerId(customerId).isEmpty()
                || salaoRepository.existsPendenteDoCliente(customerId, now.minus(validadeSolicitacao()))) {
            throw new CustomerHasOpenComandaException(customerId);
        }
        BigDecimal openBalance = receivableRepository.sumOpenBalance(customerId);
        if (openBalance != null && openBalance.signum() > 0) {
            throw new CustomerHasOpenBalanceException(customerId);
        }

        cashbackUseCase.forfeitAll(customerId, CashbackEntry.REASON_CUSTOMER_DELETED);
        customerTagRepository.removeAllByCustomerId(customerId);
        List<String> attachmentKeys = customerNoteRepository.findAttachmentKeysByCustomerId(customerId);
        customerNoteRepository.deleteAllByCustomerId(customerId);
        if (!customerRepository.anonymize(customerId, Customer.NOME_ANONIMIZADO, now)) {
            // Outra exclusão concorrente chegou antes (a trava acima torna isso raro): o resultado
            // pedido já vale, então é o mesmo caso idempotente. Ela já apagou os arquivos.
            return false;
        }
        deleteFilesAfterCommit(customerId, attachmentKeys);
        return true;
    }

    /** Mesma validade da solicitação de sessão usada no salão: as mais antigas já são EXPIRADA. */
    private Duration validadeSolicitacao() {
        return Duration.ofMinutes(centralConfig.atual().expiracaoSolicitacaoMin());
    }

    private void deleteFilesAfterCommit(Long customerId, List<String> keys) {
        if (keys.isEmpty() || noteAttachmentStorage == null) {
            return;
        }
        Runnable delete = () -> keys.forEach(key -> {
            try {
                noteAttachmentStorage.delete(key);
            } catch (RuntimeException ex) {
                // A linha já não existe; o arquivo órfão não é mais servido por nenhuma rota.
                log.warn("crm.customer.delete.attachment-file-failed customerId={} error={}", customerId,
                        ex.getMessage());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete.run();
                }
            });
        } else {
            delete.run();
        }
    }
}
