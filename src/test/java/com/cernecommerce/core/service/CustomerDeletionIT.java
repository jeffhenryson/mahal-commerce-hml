package com.cernecommerce.core.service;

import com.cernecommerce.adapter.out.persistence.repository.ComandaRepositoryImpl;
import com.cernecommerce.core.domain.exception.crm.CustomerHasOpenComandaException;
import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackEntryType;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.CustomerDeletionUseCase;
import com.cernecommerce.core.ports.out.cashback.CashbackEntryRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V206 — exclusão do perfil contra o banco (H2 do perfil dev): o que é apagado, o que fica e o que
 * passa a ignorar o cliente anonimizado.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CustomerDeletionIT {

    @Autowired CustomerDeletionUseCase deletion;
    @Autowired CrmUseCase crmUseCase;
    @Autowired CustomerRepository customerRepository;
    @Autowired CashbackEntryRepository cashbackEntryRepository;
    @Autowired ComandaRepositoryImpl comandaRepository;
    @Autowired com.cernecommerce.core.ports.out.crm.CustomerTagRepository customerTagRepository;
    @Autowired com.cernecommerce.core.ports.out.crm.CustomerNoteRepository customerNoteRepository;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static String unique() {
        return String.format("%08d", System.nanoTime() % 100_000_000L);
    }

    private Customer givenCustomer(String phone, String email, String cpf) {
        return customerRepository.save(Customer.create("Joana Excluída " + phone, phone, email, cpf, "Balcão"));
    }

    @Test
    void deleteCustomer_anonymizesPersonalData_keepsHistory_andZeroesCashbackByEntry() {
        String n = unique();
        String phone = "839" + n;
        String email = "joana" + n + "@example.com";
        String cpf = "123" + n;
        Customer customer = givenCustomer(phone, email, cpf);
        Long id = customer.id();

        Tag vip = crmUseCase.createTag("VIP_IT_" + n, false);
        crmUseCase.addTagToCustomer(id, vip.id(), false);
        crmUseCase.addNote(id, "admin", "Prefere WhatsApp");

        Instant past = Instant.now().minus(Duration.ofDays(30));
        Instant future = Instant.now().plus(Duration.ofDays(5));
        cashbackEntryRepository.save(CashbackEntry.earned(id, 9001L, 1L, new BigDecimal("10.00"), past, past,
                Instant.now().plus(Duration.ofDays(150))));
        cashbackEntryRepository.save(CashbackEntry.earned(id, 9002L, 2L, new BigDecimal("3.00"), past, future,
                future.plus(Duration.ofDays(150))));

        Comanda open = comandaRepository.save(Comanda.open(77L, "LOJA-01", "Mesa 9", "caixa1").withCustomer(id));
        Comanda encerrada = comandaRepository.save(open.cancelled(Instant.now()));
        flushAndClear();

        assertThat(deletion.deleteCustomer(id)).isTrue();
        flushAndClear();

        // Dados pessoais nulos, linha mantida.
        Customer anon = customerRepository.findById(id).orElseThrow();
        assertThat(anon.isAnonymized()).isTrue();
        assertThat(anon.nome()).isEqualTo("Cliente excluído");
        assertThat(anon.contato()).isNull();
        assertThat(anon.email()).isNull();
        assertThat(anon.cpf()).isNull();
        assertThat(anon.dataNascimento()).isNull();
        assertThat(anon.cidade()).isNull();
        assertThat(anon.avatarUrl()).isNull();
        assertThat(anon.segmento()).isEqualTo("Novo");

        // A comanda antiga continua apontando para o cliente, que agora se chama "Cliente excluído".
        assertThat(comandaRepository.findById(encerrada.id()).orElseThrow().customerId()).isEqualTo(id);
        assertThat(crmUseCase.findCustomerNames(List.of(id))).containsEntry(id, "Cliente excluído");

        // Saldo zerado por lançamento; os dois ganhos originais continuam no extrato.
        Instant now = Instant.now();
        assertThat(cashbackEntryRepository.sumAvailableByCustomerId(id, now)).isEqualByComparingTo("0");
        assertThat(cashbackEntryRepository.sumPendingByCustomerId(id, now)).isEqualByComparingTo("0");
        List<CashbackEntry> extrato = cashbackEntryRepository.findByCustomerId(id, 0, 50).content();
        assertThat(extrato).hasSize(4);
        assertThat(extrato).filteredOn(e -> e.type() == CashbackEntryType.EARNED).hasSize(2);
        assertThat(extrato).filteredOn(e -> e.type() != CashbackEntryType.EARNED)
                .allSatisfy(e -> assertThat(e.reason()).isEqualTo("CUSTOMER_DELETED"));

        // Tags e notas foram embora (pelo repositório: pelo use case o cliente já dá 404).
        assertThat(customerTagRepository.findTagsByCustomerId(id)).isEmpty();
        assertThat(customerNoteRepository.findByCustomerId(id)).isEmpty();
        assertThatThrownBy(() -> crmUseCase.listNotes(id)).isInstanceOf(CustomerNotFoundException.class);

        // Some da busca e do lookup; a ficha dá 404; a segunda exclusão é idempotente (sem efeito).
        assertThat(crmUseCase.listCustomers("Joana Excluída " + phone, 0, 20).content()).isEmpty();
        assertThat(crmUseCase.listCustomers("Cliente exclu", 0, 20).content())
                .extracting(Customer::id).doesNotContain(id);
        assertThat(customerRepository.findAllByContato(phone)).isEmpty();
        assertThat(customerRepository.findByCpf(cpf)).isEmpty();
        assertThatThrownBy(() -> crmUseCase.findCustomerById(id)).isInstanceOf(CustomerNotFoundException.class);
        assertThat(deletion.deleteCustomer(id)).isFalse();

        // Mesmo telefone, e-mail e CPF cadastram de novo.
        Customer novo = givenCustomer(phone, email, cpf);
        flushAndClear();
        assertThat(customerRepository.findByCpf(cpf)).map(Customer::id).contains(novo.id());
    }

    @Test
    void deleteCustomer_withOpenComanda_isRefused_andNothingChanges() {
        String n = unique();
        Customer customer = givenCustomer("839" + n, null, null);
        comandaRepository.save(Comanda.open(78L, "LOJA-01", "Mesa 10", "caixa1").withCustomer(customer.id()));
        flushAndClear();

        assertThatThrownBy(() -> deletion.deleteCustomer(customer.id()))
                .isInstanceOf(CustomerHasOpenComandaException.class);
        assertThat(customerRepository.findById(customer.id()).orElseThrow().isAnonymized()).isFalse();
    }
}
