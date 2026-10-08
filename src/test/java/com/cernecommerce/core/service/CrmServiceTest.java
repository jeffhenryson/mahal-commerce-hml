package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException;
import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.exception.crm.CustomerAlreadyExistsException;
import com.cernecommerce.core.domain.exception.crm.DuplicateTagNameException;
import com.cernecommerce.core.domain.exception.crm.TagNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.exception.crm.InvalidAutomationException;
import com.cernecommerce.core.domain.model.config.WhatsappConnectionStatus;
import com.cernecommerce.core.domain.model.crm.AutomationAuthType;
import com.cernecommerce.core.domain.model.crm.AutomationDestination;
import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.AutomationMetadata;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.CampaignChannel;
import com.cernecommerce.core.domain.model.crm.CampaignDispatchStatus;
import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import com.cernecommerce.core.domain.model.crm.ChannelStatus;
import com.cernecommerce.core.domain.model.crm.ChannelType;
import com.cernecommerce.core.domain.model.crm.CrmDashboardOverview;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerMatch;
import com.cernecommerce.core.domain.model.crm.CustomerMatchField;
import com.cernecommerce.core.domain.model.crm.CustomerNote;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.crm.StageTransition;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.domain.model.crm.TagSummary;
import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.crm.WebhookDispatchResult;
import com.cernecommerce.core.domain.model.crm.WebhookTestResult;
import com.cernecommerce.core.domain.exception.crm.AutomationWebhookNotConfiguredException;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.ports.in.AutomationPlatformIntegrationUseCase;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.WhatsappIntegrationUseCase;
import com.cernecommerce.core.ports.out.crm.CampaignAutomationRepository;
import com.cernecommerce.core.ports.out.crm.CampaignLogRepository;
import com.cernecommerce.core.ports.out.crm.CampaignWebhookPort;
import com.cernecommerce.core.ports.out.crm.CustomerNoteRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.crm.CustomerTagRepository;
import com.cernecommerce.core.ports.out.crm.StageTransitionRepository;
import com.cernecommerce.core.ports.out.crm.TagRepository;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.notification.WhatsappPort;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrmServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock CustomerNoteRepository customerNoteRepository;
    @Mock StageTransitionRepository stageTransitionRepository;
    @Mock TagRepository tagRepository;
    @Mock CustomerTagRepository customerTagRepository;
    @Mock CampaignAutomationRepository campaignAutomationRepository;
    @Mock CampaignLogRepository campaignLogRepository;
    @Mock EmailPort emailPort;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock CampaignWebhookPort campaignWebhookPort;
    @Mock OrderRepository orderRepository;
    @Mock WhatsappPort whatsappPort;
    @Mock WhatsappIntegrationUseCase whatsappIntegration;
    @Mock AutomationPlatformIntegrationUseCase platformIntegration;

    CrmService crmService;

    @BeforeEach
    void setUp() {
        // Disparo real (AutomationDispatchService) sobre os mesmos mocks: os testes de disparo e
        // de teste de webhook continuam exercitando a entrega de ponta a ponta.
        AutomationDispatchService dispatch = new AutomationDispatchService(campaignAutomationRepository,
                campaignLogRepository, customerRepository, customerTagRepository, cashbackUseCase, orderRepository,
                campaignWebhookPort, whatsappPort, whatsappIntegration, platformIntegration,
                new CampaignTemplateRenderer());
        crmService = new CrmService(customerRepository, customerNoteRepository, stageTransitionRepository,
                tagRepository, customerTagRepository, campaignAutomationRepository, campaignLogRepository,
                emailPort, dispatch, whatsappIntegration);
    }

    private Customer customer(Long id, String email) {
        return Customer.of(id, "Maria Silva", "11999998888", email, null, null, Instant.now(),
                CustomerStage.NOVO_LEAD);
    }

    private Customer customer(Long id, String email, CustomerStage estagio) {
        return Customer.of(id, "Maria Silva", "11999998888", email, null, null, Instant.now(), estagio);
    }

    @Test
    void createCustomer_savesAndReturns() {
        Customer saved = customer(1L, "maria@example.com");
        when(customerRepository.save(any())).thenReturn(saved);

        Customer result = crmService.createCustomer("Maria Silva", "11999998888", "maria@example.com",
                "12345678900", "loja-fisica");

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo("maria@example.com");
        verify(customerRepository).save(any());
    }

    @Test
    void createCustomer_throwsWhenEmailAlreadyExists() {
        when(customerRepository.findAllByEmailIgnoreCase("maria@example.com"))
                .thenReturn(List.of(customer(1L, "maria@example.com")));

        assertThatThrownBy(() -> crmService.createCustomer("Maria Silva", null, "maria@example.com",
                null, null))
                .isInstanceOfSatisfying(CustomerAlreadyExistsException.class, ex -> {
                    assertThat(ex.getMatchedBy()).containsExactly(CustomerMatchField.EMAIL);
                    assertThat(ex.getCustomerId()).isEqualTo(1L);
                });
        verify(customerRepository, never()).save(any());
    }

    // ── CRM-C007: telefone também barra o cadastro duplicado, e o 409 aponta o cliente ──────

    @Test
    void createCustomer_throwsWhenPhoneAlreadyExists() {
        when(customerRepository.findAllByContato("(11) 99999-8888")).thenReturn(List.of(customer(3L, null)));

        assertThatThrownBy(() -> crmService.createCustomer("Maria Silva", "(11) 99999-8888", null, null, null))
                .isInstanceOfSatisfying(CustomerAlreadyExistsException.class, ex -> {
                    assertThat(ex.getMatchedBy()).containsExactly(CustomerMatchField.PHONE);
                    assertThat(ex.getCustomerId()).isEqualTo(3L);
                });
        verify(customerRepository, never()).save(any());
    }

    @Test
    void createCustomer_conflictPointsToStrongestMatch() {
        // Telefone bate no cliente 2, CPF no 7: o 409 aponta o do CPF, o identificador oficial.
        when(customerRepository.findAllByContato("11999998888")).thenReturn(List.of(customer(2L, null)));
        when(customerRepository.findByCpf("12345678900")).thenReturn(Optional.of(customer(7L, null)));

        assertThatThrownBy(() -> crmService.createCustomer("Maria", "11999998888", null, "12345678900", null))
                .isInstanceOfSatisfying(CustomerAlreadyExistsException.class, ex -> {
                    assertThat(ex.getMatchedBy()).containsExactly(CustomerMatchField.CPF);
                    assertThat(ex.getCustomerId()).isEqualTo(7L);
                });
    }

    // ── CRM-C005: cpf é o identificador oficial; email e contato são alternativos ───────────

    @Test
    void createCustomer_throwsWhenCpfAlreadyExists() {
        when(customerRepository.findByCpf("12345678900"))
                .thenReturn(Optional.of(customer(1L, null)));

        assertThatThrownBy(() -> crmService.createCustomer("Maria Silva", null, null,
                "12345678900", null))
                .isInstanceOf(CustomerAlreadyExistsException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void createCustomer_skipsDuplicateChecksForFieldsNotInformed() {
        // Cliente leve (só contato): não checa email nem cpf, porque nenhum dos dois foi informado.
        Customer saved = Customer.of(1L, "Maria Silva", "11999998888", null, null, null, Instant.now(),
                CustomerStage.NOVO_LEAD);
        when(customerRepository.save(any())).thenReturn(saved);

        crmService.createCustomer("Maria Silva", "11999998888", null, null, null);

        verify(customerRepository, never()).findByEmail(any());
        verify(customerRepository, never()).findByCpf(any());
        verify(customerRepository).save(any());
    }

    // ── CRM-C006: normalização, edição e find-or-create de lead ────────────────────────────

    @Test
    void createCustomer_normalizesMaskedCpfAndBlankEmail() {
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Customer result = crmService.createCustomer(" Maria ", "(11) 99999-8888", "", "123.456.789-00", "PDV");

        assertThat(result.cpf()).isEqualTo("12345678900");
        assertThat(result.email()).isNull();
        assertThat(result.nome()).isEqualTo("Maria");
        verify(customerRepository, never()).findByEmail(any());
        verify(customerRepository).findByCpf("12345678900");
    }

    @Test
    void createCustomer_rejectsCpfWithWrongLength() {
        assertThatThrownBy(() -> crmService.createCustomer("Maria", "11999998888", null, "123.456", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void updateCustomer_addsCpfToLightCustomerKeepingStageAndDate() {
        Customer current = customer(1L, null, CustomerStage.QUALIFICADO);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(current));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Customer result = crmService.updateCustomer(1L, "Maria Silva", "11999998888", null,
                "123.456.789-00", "PDV");

        assertThat(result.cpf()).isEqualTo("12345678900");
        assertThat(result.estagio()).isEqualTo(CustomerStage.QUALIFICADO);
        assertThat(result.cadastradoEm()).isEqualTo(current.cadastradoEm());
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void updateCustomer_allowsKeepingOwnCpfButRejectsAnothersCpf() {
        Customer self = Customer.of(1L, "Maria", "11999998888", null, "12345678900", null, Instant.now(),
                CustomerStage.NOVO_LEAD);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(self));
        when(customerRepository.findByCpf("12345678900")).thenReturn(Optional.of(self));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        crmService.updateCustomer(1L, "Maria Souza", "11999998888", null, "12345678900", null);

        when(customerRepository.findByCpf("98765432100")).thenReturn(Optional.of(customer(2L, null)));
        assertThatThrownBy(() -> crmService.updateCustomer(1L, "Maria", null, null, "98765432100", null))
                .isInstanceOf(CustomerAlreadyExistsException.class);
    }

    @Test
    void updateCustomer_throwsWhenCustomerDoesNotExist() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.updateCustomer(99L, "Maria", "11999998888", null, null, null))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void resolveLead_createsWhenNoMatch() {
        when(customerRepository.save(any())).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            return Customer.of(10L, c.nome(), c.contato(), c.email(), c.cpf(), c.origem(), c.cadastradoEm(),
                    c.estagio());
        });

        var resolution = crmService.resolveLead("Jeff", "(83) 99999-0000", null, null, "PDV");

        assertThat(resolution.created()).isTrue();
        assertThat(resolution.customer().id()).isEqualTo(10L);
        assertThat(resolution.customer().estagio()).isEqualTo(CustomerStage.NOVO_LEAD);
        verify(customerRepository).findByContato("(83) 99999-0000");
    }

    @Test
    void resolveLead_reusesByPhoneAndCompletesMissingCpf() {
        Customer existing = Customer.of(5L, "Jeff", "83999990000", null, null, "PDV", Instant.now(),
                CustomerStage.CLIENTE_ATIVO);
        when(customerRepository.findByCpf("12345678900")).thenReturn(Optional.empty());
        when(customerRepository.findByContato("(83) 99999-0000")).thenReturn(Optional.of(existing));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resolution = crmService.resolveLead("Outro Nome", "(83) 99999-0000", null, "123.456.789-00", "PDV");

        assertThat(resolution.created()).isFalse();
        assertThat(resolution.customer().id()).isEqualTo(5L);
        assertThat(resolution.customer().cpf()).isEqualTo("12345678900");
        // Nome e estágio do cadastro existente não são sobrescritos pelo lançamento rápido.
        assertThat(resolution.customer().nome()).isEqualTo("Jeff");
        assertThat(resolution.customer().estagio()).isEqualTo(CustomerStage.CLIENTE_ATIVO);
    }

    @Test
    void resolveLead_reusesByCpfWithoutSavingWhenNothingIsMissing() {
        Customer existing = Customer.of(5L, "Jeff", "83999990000", "jeff@example.com", "12345678900", null,
                Instant.now(), CustomerStage.NOVO_LEAD);
        when(customerRepository.findByCpf("12345678900")).thenReturn(Optional.of(existing));

        var resolution = crmService.resolveLead("Jeff", "83 99999 0000", null, "12345678900", "PDV");

        assertThat(resolution.created()).isFalse();
        assertThat(resolution.customer()).isSameAs(existing);
        verify(customerRepository, never()).findByContato(any());
        verify(customerRepository, never()).save(any());
    }

    @Test
    void lookupCustomer_acceptsMaskedCpf() {
        when(customerRepository.findByCpf("12345678900")).thenReturn(Optional.of(customer(1L, null)));

        assertThat(crmService.lookupCustomer("123.456.789-00", null, null).id()).isEqualTo(1L);
    }

    @Test
    void lookupCustomer_findsByCpfFirstWhenMultipleCriteriaGiven() {
        Customer found = customer(1L, "maria@example.com");
        when(customerRepository.findByCpf("12345678900")).thenReturn(Optional.of(found));

        Customer result = crmService.lookupCustomer("12345678900", "maria@example.com", "11999998888");

        assertThat(result.id()).isEqualTo(1L);
        verify(customerRepository).findByCpf("12345678900");
        verify(customerRepository, never()).findByEmail(any());
        verify(customerRepository, never()).findByContato(any());
    }

    @Test
    void lookupCustomer_fallsBackToEmailThenContato() {
        when(customerRepository.findByEmail("maria@example.com"))
                .thenReturn(Optional.of(customer(1L, "maria@example.com")));

        crmService.lookupCustomer(null, "maria@example.com", "11999998888");

        verify(customerRepository).findByEmail("maria@example.com");
        verify(customerRepository, never()).findByContato(any());

        when(customerRepository.findByContato("11999998888"))
                .thenReturn(Optional.of(customer(2L, null)));

        crmService.lookupCustomer(null, null, "11999998888");

        verify(customerRepository).findByContato("11999998888");
    }

    @Test
    void lookupCustomer_throwsCustomerNotFoundWhenNoMatch() {
        when(customerRepository.findByCpf("12345678900")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.lookupCustomer("12345678900", null, null))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void lookupCustomer_throwsIllegalArgumentWhenNoCriteriaGiven() {
        assertThatThrownBy(() -> crmService.lookupCustomer(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> crmService.lookupCustomer(" ", " ", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── CRM-C007: lookup por contato devolve todos os que batem ─────────────────────────────

    @Test
    void lookupCustomers_acceptsMaskedCpf() {
        when(customerRepository.findByCpf("12345678900")).thenReturn(Optional.of(customer(1L, null)));

        List<CustomerMatch> result = crmService.lookupCustomers(null, null, "123.456.789-00");

        assertThat(result).singleElement().satisfies(m -> {
            assertThat(m.customer().id()).isEqualTo(1L);
            assertThat(m.matchedBy()).containsExactly(CustomerMatchField.CPF);
        });
    }

    @Test
    void lookupCustomers_mergesMatchesPerCustomerAcrossCriteria() {
        Customer maria = customer(1L, "maria@example.com");
        Customer irmao = customer(2L, null);
        when(customerRepository.findAllByContato("(11) 99999-8888")).thenReturn(List.of(maria, irmao));
        when(customerRepository.findAllByEmailIgnoreCase("Maria@Example.com ")).thenReturn(List.of(maria));

        List<CustomerMatch> result = crmService.lookupCustomers("(11) 99999-8888", "Maria@Example.com ", null);

        assertThat(result).extracting(m -> m.customer().id()).containsExactly(1L, 2L);
        assertThat(result.get(0).matchedBy())
                .containsExactlyInAnyOrder(CustomerMatchField.PHONE, CustomerMatchField.EMAIL);
        assertThat(result.get(1).matchedBy()).containsExactly(CustomerMatchField.PHONE);
    }

    @Test
    void lookupCustomers_returnsEmptyListWhenNoMatch() {
        assertThat(crmService.lookupCustomers(null, null, "12345678900")).isEmpty();
    }

    @Test
    void lookupCustomers_throwsIllegalArgumentWhenNoCriteriaGiven() {
        assertThatThrownBy(() -> crmService.lookupCustomers(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> crmService.lookupCustomers(" ", " ", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findCustomerById_returnsCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));

        Customer result = crmService.findCustomerById(1L);

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void findCustomerById_throwsWhenNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.findCustomerById(99L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void listCustomers_delegatesToRepository() {
        PageResult<Customer> page = new PageResult<>(List.of(customer(1L, "maria@example.com")), 0, 20, 1L, 1);
        when(customerRepository.findAll("maria", 0, 20)).thenReturn(page);

        PageResult<Customer> result = crmService.listCustomers("maria", 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void listCustomers_allowsNullSearch() {
        PageResult<Customer> page = new PageResult<>(List.of(), 0, 20, 0L, 0);
        when(customerRepository.findAll(null, 0, 20)).thenReturn(page);

        PageResult<Customer> result = crmService.listCustomers(null, 0, 20);

        assertThat(result.content()).isEmpty();
    }

    @Test
    void listCustomersForExport_delegatesToRepository() {
        when(customerRepository.findAllForExport("maria"))
                .thenReturn(List.of(customer(1L, "maria@example.com")));

        List<Customer> result = crmService.listCustomersForExport("maria");

        assertThat(result).hasSize(1);
    }

    @Test
    void addNote_savesAndReturnsWhenCustomerExists() {
        CustomerNote saved = CustomerNote.of(10L, 1L, "gerente", "Nota", Instant.now());
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));
        when(customerNoteRepository.save(any())).thenReturn(saved);

        CustomerNote result = crmService.addNote(1L, "gerente", "Nota");

        assertThat(result.id()).isEqualTo(10L);
        verify(customerNoteRepository).save(any());
    }

    @Test
    void addNote_throwsWhenCustomerNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.addNote(99L, "gerente", "Nota"))
                .isInstanceOf(CustomerNotFoundException.class);
        verify(customerNoteRepository, never()).save(any());
    }

    @Test
    void listNotes_returnsNotesWhenCustomerExists() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));
        when(customerNoteRepository.findByCustomerId(1L))
                .thenReturn(List.of(CustomerNote.of(10L, 1L, "gerente", "Nota", Instant.now())));

        List<CustomerNote> result = crmService.listNotes(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void listNotes_throwsWhenCustomerNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.listNotes(99L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void moveStage_updatesCustomerAndRecordsTransitionWhenCustomerExists() {
        Customer existing = customer(1L, "maria@example.com", CustomerStage.NOVO_LEAD);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stageTransitionRepository.save(any()))
                .thenReturn(StageTransition.create(1L, CustomerStage.NOVO_LEAD, CustomerStage.EM_ATENDIMENTO, "gerente"));

        Customer result = crmService.moveStage(1L, CustomerStage.EM_ATENDIMENTO, "gerente");

        assertThat(result.estagio()).isEqualTo(CustomerStage.EM_ATENDIMENTO);
        verify(stageTransitionRepository).save(any());
        verify(customerRepository).save(any());
    }

    @Test
    void moveStage_throwsWhenCustomerNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.moveStage(99L, CustomerStage.EM_ATENDIMENTO, "gerente"))
                .isInstanceOf(CustomerNotFoundException.class);
        verify(stageTransitionRepository, never()).save(any());
    }

    @Test
    void moveStage_throwsWhenMovingToSameStage() {
        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer(1L, "maria@example.com", CustomerStage.NOVO_LEAD)));

        assertThatThrownBy(() -> crmService.moveStage(1L, CustomerStage.NOVO_LEAD, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void listStageHistory_returnsHistoryWhenCustomerExists() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));
        when(stageTransitionRepository.findByCustomerId(1L)).thenReturn(
                List.of(StageTransition.create(1L, CustomerStage.NOVO_LEAD, CustomerStage.EM_ATENDIMENTO, "gerente")));

        List<StageTransition> result = crmService.listStageHistory(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void listStageHistory_throwsWhenCustomerNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.listStageHistory(99L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void getDashboardOverview_aggregatesRealCountsWithPlaceholders() {
        when(customerRepository.countAll()).thenReturn(10L);
        when(customerRepository.countActive()).thenReturn(7L);
        when(customerRepository.countByStage()).thenReturn(Map.of(
                CustomerStage.NOVO_LEAD, 5L,
                CustomerStage.EM_ATENDIMENTO, 2L,
                CustomerStage.INATIVO, 3L));

        CrmDashboardOverview result = crmService.getDashboardOverview();

        assertThat(result.totalClientes()).isEqualTo(10L);
        assertThat(result.clientesAtivos()).isEqualTo(7L);
        assertThat(result.porEstagio()).containsEntry(CustomerStage.NOVO_LEAD, 5L);
        assertThat(result.ltvMedio()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.disparosWhatsappMes()).isZero();
        assertThat(result.porSegmento()).containsEntry("NOVO", 10L);
    }

    @Test
    void createTag_savesAndReturns() {
        Tag saved = Tag.of(1L, "VIP");
        when(tagRepository.findByNome("VIP")).thenReturn(Optional.empty());
        when(tagRepository.save(any())).thenReturn(saved);

        Tag result = crmService.createTag("VIP");

        assertThat(result.id()).isEqualTo(1L);
        verify(tagRepository).save(any());
    }

    @Test
    void createTag_throwsWhenNameAlreadyExists() {
        when(tagRepository.findByNome("VIP")).thenReturn(Optional.of(Tag.of(1L, "VIP")));

        assertThatThrownBy(() -> crmService.createTag("VIP"))
                .isInstanceOf(DuplicateTagNameException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void listTags_delegatesToRepository() {
        when(tagRepository.findAllWithCustomerCount())
                .thenReturn(List.of(new TagSummary(1L, "VIP", 3L)));

        List<TagSummary> result = crmService.listTags();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).clientesCount()).isEqualTo(3L);
    }

    @Test
    void deleteTag_deletesWhenExists() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(Tag.of(1L, "VIP")));

        crmService.deleteTag(1L);

        verify(tagRepository).deleteById(1L);
    }

    @Test
    void deleteTag_throwsWhenNotFound() {
        when(tagRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.deleteTag(99L))
                .isInstanceOf(TagNotFoundException.class);
        verify(tagRepository, never()).deleteById(any());
    }

    @Test
    void addTagToCustomer_associatesWhenBothExist() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));
        when(tagRepository.findById(1L)).thenReturn(Optional.of(Tag.of(1L, "VIP")));

        crmService.addTagToCustomer(1L, 1L);

        verify(customerTagRepository).associate(1L, 1L);
    }

    @Test
    void addTagToCustomer_throwsWhenCustomerNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.addTagToCustomer(99L, 1L))
                .isInstanceOf(CustomerNotFoundException.class);
        verify(customerTagRepository, never()).associate(any(), any());
    }

    @Test
    void addTagToCustomer_throwsWhenTagNotFound() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));
        when(tagRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.addTagToCustomer(1L, 99L))
                .isInstanceOf(TagNotFoundException.class);
        verify(customerTagRepository, never()).associate(any(), any());
    }

    @Test
    void removeTagFromCustomer_disassociatesWhenBothExist() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));
        when(tagRepository.findById(1L)).thenReturn(Optional.of(Tag.of(1L, "VIP")));

        crmService.removeTagFromCustomer(1L, 1L);

        verify(customerTagRepository).disassociate(1L, 1L);
    }

    @Test
    void listCustomerTags_returnsTagsWhenCustomerExists() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));
        when(customerTagRepository.findTagsByCustomerId(1L)).thenReturn(List.of(Tag.of(1L, "VIP")));

        List<Tag> result = crmService.listCustomerTags(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void listCustomerTags_throwsWhenCustomerNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.listCustomerTags(99L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    private CampaignAutomation automation(Long id, boolean ativa) {
        return CampaignAutomation.of(id, "Boas-vindas", CampaignTrigger.MANUAL, CustomerStage.NOVO_LEAD,
                CampaignChannel.EMAIL, "Ola {nome}", ativa, Instant.now(), null, Map.of());
    }

    private CampaignAutomation automationWithWebhook(Long id, String webhookUrl) {
        return CampaignAutomation.of(id, "Boas-vindas", CampaignTrigger.MANUAL, CustomerStage.NOVO_LEAD,
                CampaignChannel.EMAIL, "Ola {{cliente.nome}}", true, Instant.now(), webhookUrl, Map.of());
    }

    /** Comando de webhook próprio; {@code headers} null = mantém o segredo salvo. */
    private static CrmUseCase.AutomationCommand webhookCommand(String nome, CustomerStage segmento, String url,
            AutomationAuthType authTipo, Map<String, String> headers) {
        return new CrmUseCase.AutomationCommand(nome, CampaignTrigger.MANUAL, null, segmento, CampaignChannel.WHATSAPP,
                "Novo template", AutomationDestination.WEBHOOK, url, null, null, null, authTipo, null, headers, null);
    }

    @Test
    void createAutomation_savesAndReturns() {
        when(campaignAutomationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CampaignAutomation result = crmService.createAutomation(webhookCommand("Boas-vindas", CustomerStage.NOVO_LEAD,
                "https://n8n.example.com/webhook/abc", AutomationAuthType.BEARER, Map.of("Authorization", "Bearer tk-1234")));

        assertThat(result.ativa()).isTrue();
        assertThat(result.entrega().authLast4()).isEqualTo("1234");
        verify(campaignAutomationRepository).save(any());
    }

    @Test
    void createAutomation_evento_comPlataforma_semSegmento() {
        when(campaignAutomationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CampaignAutomation result = crmService.createAutomation(new CrmUseCase.AutomationCommand("Pós-venda",
                CampaignTrigger.EVENTO, AutomationEvent.PEDIDO_CONCLUIDO, null, CampaignChannel.WHATSAPP, "Oi",
                AutomationDestination.PLATAFORMA, null, "pos-venda", null, null, AutomationAuthType.BEARER, null,
                Map.of("Authorization", "Bearer x"), List.of(AutomationMetadata.PEDIDO)));

        assertThat(result.evento()).isEqualTo(AutomationEvent.PEDIDO_CONCLUIDO);
        assertThat(result.segmentoAlvo()).isNull();
        // Fora do destino WEBHOOK não há autenticação própria: o segredo é descartado.
        assertThat(result.entrega().authTipo()).isEqualTo(AutomationAuthType.NONE);
        assertThat(result.webhookHeaders()).isEmpty();
    }

    @Test
    void createAutomation_rejeitaDestinoIncompleto_eEventoAusente() {
        assertThatThrownBy(() -> crmService.createAutomation(webhookCommand("A", CustomerStage.NOVO_LEAD, null,
                AutomationAuthType.NONE, Map.of())))
                .isInstanceOf(InvalidAutomationException.class).hasMessageContaining("webhookUrl");
        assertThatThrownBy(() -> crmService.createAutomation(new CrmUseCase.AutomationCommand("A",
                CampaignTrigger.EVENTO, null, null, CampaignChannel.EMAIL, "Oi", AutomationDestination.WHATSAPP_META,
                null, null, "promo", null, null, null, null, null)))
                .isInstanceOf(InvalidAutomationException.class).hasMessageContaining("evento");
        assertThatThrownBy(() -> crmService.createAutomation(webhookCommand("A", CustomerStage.NOVO_LEAD,
                "ftp://x.com/h", AutomationAuthType.NONE, Map.of())))
                .isInstanceOf(InvalidAutomationException.class);
        assertThatThrownBy(() -> crmService.createAutomation(webhookCommand("A", CustomerStage.NOVO_LEAD,
                "https://x.com/h", AutomationAuthType.BEARER, null)))
                .isInstanceOf(InvalidAutomationException.class).hasMessageContaining("segredo");
        verify(campaignAutomationRepository, never()).save(any());
    }

    @Test
    void updateAutomation_savesUpdatedFieldsWhenExists() {
        when(campaignAutomationRepository.findById(1L)).thenReturn(Optional.of(automation(1L, true)));
        when(campaignAutomationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CampaignAutomation result = crmService.updateAutomation(1L, webhookCommand("Novo nome",
                CustomerStage.QUALIFICADO, "https://n8n.example.com/webhook/abc", AutomationAuthType.BEARER,
                Map.of("Authorization", "Bearer token")));

        assertThat(result.nome()).isEqualTo("Novo nome");
        assertThat(result.segmentoAlvo()).isEqualTo(CustomerStage.QUALIFICADO);
        assertThat(result.webhookUrl()).isEqualTo("https://n8n.example.com/webhook/abc");
    }

    @Test
    void updateAutomation_semWebhookHeaders_mantemOSegredo_eVazioRemove() {
        CampaignAutomation salva = CampaignAutomation.of(1L, "A", CampaignTrigger.MANUAL, CustomerStage.NOVO_LEAD,
                CampaignChannel.EMAIL, "Oi", true, Instant.now(), "https://x.com/h",
                Map.of("Authorization", "Bearer segredo-9999"));
        when(campaignAutomationRepository.findById(1L)).thenReturn(Optional.of(salva));
        when(campaignAutomationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CampaignAutomation mantida = crmService.updateAutomation(1L, webhookCommand("A", CustomerStage.NOVO_LEAD,
                "https://x.com/h", AutomationAuthType.BEARER, null));
        assertThat(mantida.webhookHeaders()).containsEntry("Authorization", "Bearer segredo-9999");
        assertThat(mantida.entrega().authLast4()).isEqualTo("9999");

        CampaignAutomation semAuth = crmService.updateAutomation(1L, webhookCommand("A", CustomerStage.NOVO_LEAD,
                "https://x.com/h", AutomationAuthType.NONE, Map.of()));
        assertThat(semAuth.webhookHeaders()).isEmpty();
        assertThat(semAuth.entrega().authLast4()).isNull();
    }

    @Test
    void updateAutomation_throwsWhenNotFound() {
        when(campaignAutomationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.updateAutomation(99L, webhookCommand("Nome", CustomerStage.NOVO_LEAD,
                "https://x.com/h", AutomationAuthType.NONE, null)))
                .isInstanceOf(CampaignAutomationNotFoundException.class);
    }

    @Test
    void listAutomations_delegatesToRepository() {
        when(campaignAutomationRepository.findAll()).thenReturn(List.of(automation(1L, true)));

        List<CampaignAutomation> result = crmService.listAutomations();

        assertThat(result).hasSize(1);
    }

    @Test
    void setAutomationActive_updatesFlagWhenExists() {
        when(campaignAutomationRepository.findById(1L)).thenReturn(Optional.of(automation(1L, true)));
        when(campaignAutomationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CampaignAutomation result = crmService.setAutomationActive(1L, false);

        assertThat(result.ativa()).isFalse();
    }

    @Test
    void setAutomationActive_throwsWhenNotFound() {
        when(campaignAutomationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.setAutomationActive(99L, false))
                .isInstanceOf(CampaignAutomationNotFoundException.class);
    }

    @Test
    void deleteAutomation_deletesWhenExists() {
        when(campaignAutomationRepository.findById(1L)).thenReturn(Optional.of(automation(1L, true)));

        crmService.deleteAutomation(1L);

        verify(campaignAutomationRepository).deleteById(1L);
    }

    @Test
    void deleteAutomation_throwsWhenNotFound() {
        when(campaignAutomationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.deleteAutomation(99L))
                .isInstanceOf(CampaignAutomationNotFoundException.class);
        verify(campaignAutomationRepository, never()).deleteById(any());
    }

    @Test
    void dispatchAutomation_createsOneLogEntryPerTargetCustomer() {
        CampaignAutomation automation = automation(1L, true);
        when(campaignAutomationRepository.findById(1L)).thenReturn(Optional.of(automation));
        when(customerRepository.findByEstagio(CustomerStage.NOVO_LEAD)).thenReturn(
                List.of(customer(1L, "maria@example.com"), customer(2L, "joao@example.com")));
        when(campaignLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<CampaignLogEntry> result = crmService.dispatchAutomation(1L);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(entry -> {
            assertThat(entry.status()).isEqualTo(CampaignDispatchStatus.PENDENTE_INTEGRACAO);
            assertThat(entry.convertidoEm()).isNull();
        });
        verify(campaignLogRepository, times(2)).save(any());
    }

    @Test
    void dispatchAutomation_throwsWhenAutomationNotFound() {
        when(campaignAutomationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.dispatchAutomation(99L))
                .isInstanceOf(CampaignAutomationNotFoundException.class);
    }

    @Test
    void dispatchAutomation_withWebhook_sendsRealRequestAndRecordsPartialFailure() {
        CampaignAutomation automation = automationWithWebhook(1L, "https://n8n.example.com/webhook/abc");
        Customer maria = customer(1L, "maria@example.com");
        Customer joao = customer(2L, "joao@example.com");
        when(campaignAutomationRepository.findById(1L)).thenReturn(Optional.of(automation));
        when(customerRepository.findByEstagio(CustomerStage.NOVO_LEAD)).thenReturn(List.of(maria, joao));
        when(campaignLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cashbackUseCase.getCustomerBalance(any())).thenReturn(new CashbackBalance(BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO));
        when(customerTagRepository.findTagsByCustomerId(any())).thenReturn(List.of());
        when(campaignWebhookPort.send(eq("https://n8n.example.com/webhook/abc"), any(), any()))
                .thenReturn(WebhookDispatchResult.ok(200))
                .thenReturn(WebhookDispatchResult.failure("timeout ao conectar"));

        List<CampaignLogEntry> result = crmService.dispatchAutomation(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).status()).isEqualTo(CampaignDispatchStatus.ENVIADO);
        assertThat(result.get(1).status()).isEqualTo(CampaignDispatchStatus.FALHA);
        assertThat(result.get(1).erroDetalhe()).isEqualTo("timeout ao conectar");
        verify(campaignLogRepository, times(2)).save(any());
    }

    @Test
    void testAutomation_sendsSyntheticPayloadWithoutPersistingLog() {
        CampaignAutomation automation = automationWithWebhook(1L, "https://n8n.example.com/webhook/abc");
        when(campaignAutomationRepository.findById(1L)).thenReturn(Optional.of(automation));
        when(campaignWebhookPort.send(eq("https://n8n.example.com/webhook/abc"), any(), any()))
                .thenReturn(WebhookDispatchResult.ok(200));

        WebhookTestResult result = crmService.testAutomation(1L);

        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.payloadEnviado()).containsKey("cliente");
        verify(campaignLogRepository, never()).save(any());
        verify(cashbackUseCase, never()).getCustomerBalance(any());
    }

    @Test
    void testAutomation_throwsWhenWebhookNotConfigured() {
        when(campaignAutomationRepository.findById(1L)).thenReturn(Optional.of(automation(1L, true)));

        assertThatThrownBy(() -> crmService.testAutomation(1L))
                .isInstanceOf(AutomationWebhookNotConfiguredException.class);
        verify(campaignWebhookPort, never()).send(any(), any(), any());
    }

    @Test
    void listAutomationLog_returnsLogWhenAutomationExists() {
        when(campaignAutomationRepository.findById(1L)).thenReturn(Optional.of(automation(1L, true)));
        when(campaignLogRepository.findByAutomationId(1L)).thenReturn(
                List.of(CampaignLogEntry.create(1L, 10L)));

        List<CampaignLogEntry> result = crmService.listAutomationLog(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void listAutomationLog_throwsWhenAutomationNotFound() {
        when(campaignAutomationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.listAutomationLog(99L))
                .isInstanceOf(CampaignAutomationNotFoundException.class);
    }

    @Test
    void getChannelStatus_reflectsEmailPortAndWhatsappIntegration() {
        when(emailPort.channelStatus()).thenReturn(EmailChannelStatus.of(true, "MAILPIT", "Conectado ao Mailpit"));
        when(whatsappIntegration.connectionStatus())
                .thenReturn(WhatsappConnectionStatus.disconnected("Integração desativada"));

        List<ChannelStatus> result = crmService.getChannelStatus();

        assertThat(result).hasSize(2);
        ChannelStatus email = result.stream().filter(s -> s.canal() == ChannelType.EMAIL).findFirst().orElseThrow();
        assertThat(email.conectado()).isTrue();
        assertThat(email.provedor()).isEqualTo("MAILPIT");

        ChannelStatus whatsapp = result.stream().filter(s -> s.canal() == ChannelType.WHATSAPP).findFirst()
                .orElseThrow();
        assertThat(whatsapp.conectado()).isFalse();
        assertThat(whatsapp.provedor()).isNull();
        assertThat(whatsapp.detalhe()).isEqualTo("Integração desativada");
    }

    @Test
    void getChannelStatus_whatsappConectado() {
        when(emailPort.channelStatus()).thenReturn(EmailChannelStatus.of(true, "MAILPIT", "Conectado ao Mailpit"));
        when(whatsappIntegration.connectionStatus()).thenReturn(WhatsappConnectionStatus.up());

        ChannelStatus whatsapp = crmService.getChannelStatus().stream()
                .filter(s -> s.canal() == ChannelType.WHATSAPP).findFirst().orElseThrow();

        assertThat(whatsapp.conectado()).isTrue();
        assertThat(whatsapp.provedor()).isEqualTo("META_CLOUD_API");
    }
}
