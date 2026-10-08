package com.cernecommerce.adapter.in.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.core.domain.model.crm.AutomationMetadata;
import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.AutomationDelivery;
import com.cernecommerce.core.domain.model.crm.AutomationAuthType;
import com.cernecommerce.core.domain.exception.crm.InvalidAutomationException;
import com.cernecommerce.adapter.in.converter.CampaignDTOConverter;
import com.cernecommerce.adapter.in.converter.CashbackDTOConverter;
import com.cernecommerce.adapter.in.converter.ChannelStatusDTOConverter;
import com.cernecommerce.adapter.in.converter.CustomerCsvConverter;
import com.cernecommerce.adapter.in.converter.CustomerDTOConverter;
import com.cernecommerce.adapter.in.converter.CustomerNoteDTOConverter;
import com.cernecommerce.adapter.in.converter.StageTransitionDTOConverter;
import com.cernecommerce.adapter.in.converter.TagDTOConverter;
import com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException;
import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.exception.crm.CustomerAlreadyExistsException;
import com.cernecommerce.core.domain.exception.crm.DuplicateTagNameException;
import com.cernecommerce.core.domain.exception.crm.TagNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.CampaignChannel;
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
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CrmControllerTest {

    private MockMvc mockMvc;
    private CrmUseCase crmUseCase;
    private CashbackUseCase cashbackUseCase;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @BeforeEach
    void setup() {
        crmUseCase = mock(CrmUseCase.class);
        cashbackUseCase = mock(CashbackUseCase.class);
        ReceivableUseCase receivableUseCase = mock(ReceivableUseCase.class);
        when(receivableUseCase.balance(any())).thenReturn(new ReceivableUseCase.CustomerBalance(
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO));
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CrmController(crmUseCase, cashbackUseCase, receivableUseCase,
                        new CustomerDTOConverter(),
                        new CustomerNoteDTOConverter(), new StageTransitionDTOConverter(),
                        new TagDTOConverter(), new CustomerCsvConverter(), new CampaignDTOConverter(),
                        new ChannelStatusDTOConverter(), new CashbackDTOConverter(), publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Customer customer(Long id, String email) {
        return Customer.of(id, "Maria Silva", "11999998888", email, null, null, Instant.now(),
                CustomerStage.NOVO_LEAD);
    }

    private CampaignAutomation automation(Long id, boolean ativa) {
        return CampaignAutomation.of(id, "Boas-vindas", CampaignTrigger.MANUAL, CustomerStage.NOVO_LEAD,
                CampaignChannel.EMAIL, "Ola {nome}", ativa, Instant.now(), null, Map.of());
    }

    private CustomerNote note(Long id, Long customerId) {
        return CustomerNote.of(id, customerId, "admin", "Cliente prefere WhatsApp", Instant.now());
    }

    private StageTransition transition(Long id, Long customerId) {
        return StageTransition.of(id, customerId, CustomerStage.NOVO_LEAD, CustomerStage.EM_ATENDIMENTO, "admin",
                Instant.now());
    }

    @Test
    void create_returns_201() throws Exception {
        when(crmUseCase.createCustomer("Maria Silva", "11999998888", "maria@example.com", null, null))
                .thenReturn(customer(1L, "maria@example.com"));

        mockMvc.perform(post("/crm/customers")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria Silva\",\"contato\":\"11999998888\",\"email\":\"maria@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("maria@example.com"))
                .andExpect(jsonPath("$.ltv").value(0))
                .andExpect(jsonPath("$.cashback").value(0))
                .andExpect(jsonPath("$.segmento").value("NOVO"))
                .andExpect(jsonPath("$.tags").isEmpty());
    }

    @Test
    void create_withoutNome_returns_400() throws Exception {
        mockMvc.perform(post("/crm/customers")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contato\":\"11999998888\",\"email\":\"maria@example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withInvalidEmail_returns_400() throws Exception {
        mockMvc.perform(post("/crm/customers")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria Silva\",\"contato\":\"11999998888\",\"email\":\"invalido\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicate_returns_409_withMatchedByAndCustomerId() throws Exception {
        when(crmUseCase.createCustomer(any(), any(), eq("maria@example.com"), any(), any()))
                .thenThrow(new CustomerAlreadyExistsException(
                        Set.of(CustomerMatchField.PHONE, CustomerMatchField.EMAIL), 7L));

        mockMvc.perform(post("/crm/customers")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria Silva\",\"contato\":\"11999998888\",\"email\":\"maria@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.customerId").value(7))
                .andExpect(jsonPath("$.matchedBy[0]").value("PHONE"))
                .andExpect(jsonPath("$.matchedBy[1]").value("EMAIL"));
    }

    @Test
    void create_customerLeveWithOnlyContato_returns_201() throws Exception {
        // CRM-C005: sem email nem cpf, só nome + contato — "cliente leve".
        when(crmUseCase.createCustomer("Maria Silva", "11999998888", null, null, null))
                .thenReturn(Customer.of(1L, "Maria Silva", "11999998888", null, null, null, Instant.now(),
                        CustomerStage.NOVO_LEAD));

        mockMvc.perform(post("/crm/customers")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria Silva\",\"contato\":\"11999998888\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    // ── CRM-F002: busca de balcão ─────────────────────────────────────────────────────────────

    @Test
    void lookup_byCpf_returns_200() throws Exception {
        when(crmUseCase.lookupCustomer("12345678900", null, null))
                .thenReturn(customer(1L, "maria@example.com"));

        mockMvc.perform(get("/crm/customers/lookup").param("cpf", "12345678900").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void lookup_notFound_returns_404() throws Exception {
        when(crmUseCase.lookupCustomer(null, null, "11999998888"))
                .thenThrow(new CustomerNotFoundException("contato 11999998888"));

        mockMvc.perform(get("/crm/customers/lookup").param("contato", "11999998888").principal(AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void lookup_withoutAnyCriteria_returns_400() throws Exception {
        when(crmUseCase.lookupCustomer(null, null, null))
                .thenThrow(new IllegalArgumentException("informe cpf, email ou contato para a busca"));

        mockMvc.perform(get("/crm/customers/lookup").principal(AUTH))
                .andExpect(status().isBadRequest());
    }

    // ── CRM-C007: quem já usa este telefone/email/CPF ─────────────────────────────────────────

    @Test
    void lookupContact_returnsCustomerDtoWithMatchedBy() throws Exception {
        when(crmUseCase.lookupCustomers("11999998888", "maria@example.com", null))
                .thenReturn(List.of(new CustomerMatch(customer(1L, "maria@example.com"),
                        Set.of(CustomerMatchField.EMAIL, CustomerMatchField.PHONE))));

        mockMvc.perform(get("/crm/customers/lookup/contact").param("phone", "11999998888")
                        .param("email", "maria@example.com").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nome").value("Maria Silva"))
                .andExpect(jsonPath("$[0].email").value("maria@example.com"))
                .andExpect(jsonPath("$[0].matchedBy[0]").value("PHONE"))
                .andExpect(jsonPath("$[0].matchedBy[1]").value("EMAIL"));
    }

    @Test
    void lookupContact_noMatch_returns_emptyList() throws Exception {
        when(crmUseCase.lookupCustomers("11999998888", null, null)).thenReturn(List.of());

        mockMvc.perform(get("/crm/customers/lookup/contact").param("phone", "11999998888").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void get_returns_200() throws Exception {
        when(crmUseCase.findCustomerById(1L)).thenReturn(customer(1L, "maria@example.com"));
        when(cashbackUseCase.getCustomerBalance(1L))
                .thenReturn(new CashbackBalance(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        mockMvc.perform(get("/crm/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void get_notFound_returns_404() throws Exception {
        when(crmUseCase.findCustomerById(99L)).thenThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(get("/crm/customers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void list_returns_200_with_customers() throws Exception {
        when(crmUseCase.listCustomers(null, 0, 20))
                .thenReturn(new PageResult<>(List.of(customer(1L, "maria@example.com")), 0, 20, 1L, 1));

        mockMvc.perform(get("/crm/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].email").value("maria@example.com"))
                .andExpect(jsonPath("$.content[0].segmento").value("NOVO"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_withSearch_passesSearchThrough() throws Exception {
        when(crmUseCase.listCustomers("maria", 0, 20))
                .thenReturn(new PageResult<>(List.of(customer(1L, "maria@example.com")), 0, 20, 1L, 1));

        mockMvc.perform(get("/crm/customers").param("search", "maria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("maria@example.com"));
    }

    @Test
    void list_capsPageSizeAt100() throws Exception {
        when(crmUseCase.listCustomers(null, 0, 100))
                .thenReturn(new PageResult<>(List.of(), 0, 100, 0L, 0));

        mockMvc.perform(get("/crm/customers").param("size", "500"))
                .andExpect(status().isOk());

        verify(crmUseCase).listCustomers(null, 0, 100);
    }

    @Test
    void addNote_returns_201() throws Exception {
        when(crmUseCase.addNote(1L, "admin", "Cliente prefere WhatsApp")).thenReturn(note(10L, 1L));

        mockMvc.perform(post("/crm/customers/1/notes")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Cliente prefere WhatsApp\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.autor").value("admin"));
    }

    @Test
    void addNote_withoutTexto_returns_400() throws Exception {
        mockMvc.perform(post("/crm/customers/1/notes")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addNote_customerNotFound_returns_404() throws Exception {
        when(crmUseCase.addNote(eq(99L), any(), any())).thenThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(post("/crm/customers/99/notes")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Nota\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void listNotes_returns_200() throws Exception {
        when(crmUseCase.listNotes(1L)).thenReturn(List.of(note(10L, 1L)));

        mockMvc.perform(get("/crm/customers/1/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void listNotes_customerNotFound_returns_404() throws Exception {
        when(crmUseCase.listNotes(99L)).thenThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(get("/crm/customers/99/notes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void listOrders_returns_200_withEmptyPlaceholder() throws Exception {
        when(crmUseCase.findCustomerById(1L)).thenReturn(customer(1L, "maria@example.com"));

        mockMvc.perform(get("/crm/customers/1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listOrders_customerNotFound_returns_404() throws Exception {
        when(crmUseCase.findCustomerById(99L)).thenThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(get("/crm/customers/99/orders"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listCashback_delegatesToCashbackUseCaseAndReturns200() throws Exception {
        when(crmUseCase.findCustomerById(1L)).thenReturn(customer(1L, "maria@example.com"));
        when(cashbackUseCase.listCustomerEntries(1L, 0, 100))
                .thenReturn(new PageResult<>(List.of(), 0, 100, 0, 0));

        mockMvc.perform(get("/crm/customers/1/cashback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listCashback_customerNotFound_returns_404() throws Exception {
        when(crmUseCase.findCustomerById(99L)).thenThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(get("/crm/customers/99/cashback"))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveStage_returns_200() throws Exception {
        Customer moved = Customer.of(1L, "Maria Silva", "11999998888", "maria@example.com", null, null,
                Instant.now(), CustomerStage.EM_ATENDIMENTO);
        when(crmUseCase.moveStage(1L, CustomerStage.EM_ATENDIMENTO, "admin")).thenReturn(moved);

        mockMvc.perform(patch("/crm/customers/1/estagio")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estagio\":\"EM_ATENDIMENTO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estagio").value("EM_ATENDIMENTO"));
    }

    @Test
    void moveStage_withoutEstagio_returns_400() throws Exception {
        mockMvc.perform(patch("/crm/customers/1/estagio")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moveStage_invalidEstagioValue_returns_400() throws Exception {
        mockMvc.perform(patch("/crm/customers/1/estagio")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estagio\":\"INEXISTENTE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moveStage_customerNotFound_returns_404() throws Exception {
        when(crmUseCase.moveStage(eq(99L), any(), any())).thenThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(patch("/crm/customers/99/estagio")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estagio\":\"EM_ATENDIMENTO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void moveStage_sameStage_returns_400() throws Exception {
        when(crmUseCase.moveStage(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("de e para não podem ser o mesmo estágio"));

        mockMvc.perform(patch("/crm/customers/1/estagio")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estagio\":\"NOVO_LEAD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listStageHistory_returns_200() throws Exception {
        when(crmUseCase.listStageHistory(1L)).thenReturn(List.of(transition(20L, 1L)));

        mockMvc.perform(get("/crm/customers/1/estagio/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(20))
                .andExpect(jsonPath("$[0].de").value("NOVO_LEAD"))
                .andExpect(jsonPath("$[0].para").value("EM_ATENDIMENTO"));
    }

    @Test
    void listStageHistory_customerNotFound_returns_404() throws Exception {
        when(crmUseCase.listStageHistory(99L)).thenThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(get("/crm/customers/99/estagio/historico"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDashboardOverview_returns_200() throws Exception {
        CrmDashboardOverview overview = new CrmDashboardOverview(
                10L, 7L, BigDecimal.ZERO, 0L,
                Map.of("NOVO", 10L),
                Map.of(CustomerStage.NOVO_LEAD, 5L, CustomerStage.INATIVO, 3L));
        when(crmUseCase.getDashboardOverview()).thenReturn(overview);

        mockMvc.perform(get("/crm/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClientes").value(10))
                .andExpect(jsonPath("$.clientesAtivos").value(7))
                .andExpect(jsonPath("$.ltvMedio").value(0))
                .andExpect(jsonPath("$.disparosWhatsappMes").value(0))
                .andExpect(jsonPath("$.porSegmento.NOVO").value(10))
                .andExpect(jsonPath("$.porEstagio.NOVO_LEAD").value(5));
    }

    @Test
    void get_returns_tags_from_use_case() throws Exception {
        when(crmUseCase.findCustomerById(1L)).thenReturn(customer(1L, "maria@example.com"));
        when(crmUseCase.listCustomerTags(1L)).thenReturn(List.of(Tag.of(1L, "VIP")));
        when(cashbackUseCase.getCustomerBalance(1L))
                .thenReturn(new CashbackBalance(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        mockMvc.perform(get("/crm/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0]").value("VIP"));
    }

    @Test
    void createTag_returns_201() throws Exception {
        when(crmUseCase.createTag("VIP")).thenReturn(Tag.of(1L, "VIP"));

        mockMvc.perform(post("/crm/tags")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"VIP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("VIP"));
    }

    @Test
    void createTag_withoutNome_returns_400() throws Exception {
        mockMvc.perform(post("/crm/tags")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTag_duplicateName_returns_409() throws Exception {
        when(crmUseCase.createTag("VIP")).thenThrow(new DuplicateTagNameException("VIP"));

        mockMvc.perform(post("/crm/tags")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"VIP\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TAG_ALREADY_EXISTS"));
    }

    @Test
    void listTags_returns_200_withCounts() throws Exception {
        when(crmUseCase.listTags()).thenReturn(List.of(new TagSummary(1L, "VIP", 3L)));

        mockMvc.perform(get("/crm/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("VIP"))
                .andExpect(jsonPath("$[0].clientesCount").value(3));
    }

    @Test
    void deleteTag_returns_204() throws Exception {
        mockMvc.perform(delete("/crm/tags/1").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(crmUseCase).deleteTag(1L);
    }

    @Test
    void deleteTag_notFound_returns_404() throws Exception {
        doThrow(new TagNotFoundException(99L)).when(crmUseCase).deleteTag(99L);

        mockMvc.perform(delete("/crm/tags/99").principal(AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TAG_NOT_FOUND"));
    }

    @Test
    void addTagToCustomer_returns_204() throws Exception {
        mockMvc.perform(post("/crm/customers/1/tags")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagId\":1}"))
                .andExpect(status().isNoContent());

        verify(crmUseCase).addTagToCustomer(1L, 1L);
    }

    @Test
    void addTagToCustomer_withoutTagId_returns_400() throws Exception {
        mockMvc.perform(post("/crm/customers/1/tags")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addTagToCustomer_customerNotFound_returns_404() throws Exception {
        doThrow(new CustomerNotFoundException(99L)).when(crmUseCase).addTagToCustomer(99L, 1L);

        mockMvc.perform(post("/crm/customers/99/tags")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagId\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void addTagToCustomer_tagNotFound_returns_404() throws Exception {
        doThrow(new TagNotFoundException(99L)).when(crmUseCase).addTagToCustomer(1L, 99L);

        mockMvc.perform(post("/crm/customers/1/tags")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagId\":99}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TAG_NOT_FOUND"));
    }

    @Test
    void removeTagFromCustomer_returns_204() throws Exception {
        mockMvc.perform(delete("/crm/customers/1/tags/1").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(crmUseCase).removeTagFromCustomer(1L, 1L);
    }

    @Test
    void listCustomerTags_returns_200() throws Exception {
        when(crmUseCase.listCustomerTags(1L)).thenReturn(List.of(Tag.of(1L, "VIP")));

        mockMvc.perform(get("/crm/customers/1/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("VIP"));
    }

    @Test
    void listCustomerTags_customerNotFound_returns_404() throws Exception {
        when(crmUseCase.listCustomerTags(99L)).thenThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(get("/crm/customers/99/tags"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportCustomersCsv_returns_200_withCsvContentAndHeaders() throws Exception {
        when(crmUseCase.listCustomersForExport(null)).thenReturn(List.of(customer(1L, "maria@example.com")));

        byte[] body = mockMvc.perform(get("/crm/customers/export").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("clientes.csv")))
                .andReturn().getResponse().getContentAsByteArray();

        String csv = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        assertThatCsvHasUtf8Bom(body);
        org.assertj.core.api.Assertions.assertThat(csv).contains("maria@example.com");
    }

    @Test
    void exportCustomersCsv_passesSearchThrough() throws Exception {
        when(crmUseCase.listCustomersForExport("maria")).thenReturn(List.of(customer(1L, "maria@example.com")));

        mockMvc.perform(get("/crm/customers/export").param("search", "maria").principal(AUTH))
                .andExpect(status().isOk());

        verify(crmUseCase).listCustomersForExport("maria");
    }

    private void assertThatCsvHasUtf8Bom(byte[] body) {
        org.assertj.core.api.Assertions.assertThat(body[0] & 0xFF).isEqualTo(0xEF);
        org.assertj.core.api.Assertions.assertThat(body[1] & 0xFF).isEqualTo(0xBB);
        org.assertj.core.api.Assertions.assertThat(body[2] & 0xFF).isEqualTo(0xBF);
    }

    @Test
    void createAutomation_returns_201() throws Exception {
        when(crmUseCase.createAutomation(any())).thenReturn(automation(1L, true));

        mockMvc.perform(post("/crm/automacoes")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Boas-vindas\",\"gatilho\":\"MANUAL\",\"segmentoAlvo\":\"NOVO_LEAD\","
                                + "\"canal\":\"EMAIL\",\"template\":\"Ola {nome}\",\"webhookUrl\":\"https://x.com/h\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ativa").value(true))
                .andExpect(jsonPath("$.destino").value("WEBHOOK"))
                .andExpect(jsonPath("$.metadados.length()").value(4));

        verify(crmUseCase).createAutomation(argThat(c -> "Boas-vindas".equals(c.nome())
                && c.gatilho() == CampaignTrigger.MANUAL && c.webhookHeaders() == null));
    }

    @Test
    void createAutomation_repassaCamposNovos_eNuncaDevolveOSegredo() throws Exception {
        CampaignAutomation saved = new CampaignAutomation(2L, "Pós-venda", CampaignTrigger.EVENTO,
                AutomationEvent.PEDIDO_CONCLUIDO, null, CampaignChannel.WHATSAPP, "Oi", true, Instant.now(),
                AutomationDelivery.webhook("https://x.com/h", Map.of("Authorization", "Bearer segredo-1234")),
                List.of(AutomationMetadata.CLIENTE, AutomationMetadata.PEDIDO));
        when(crmUseCase.createAutomation(any())).thenReturn(saved);

        mockMvc.perform(post("/crm/automacoes")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pós-venda\",\"gatilho\":\"EVENTO\",\"evento\":\"PEDIDO_CONCLUIDO\","
                                + "\"canal\":\"WHATSAPP\",\"template\":\"Oi\",\"destino\":\"WEBHOOK\","
                                + "\"webhookUrl\":\"https://x.com/h\",\"authTipo\":\"BEARER\","
                                + "\"webhookHeaders\":{\"Authorization\":\"Bearer segredo-1234\"},"
                                + "\"metadados\":[\"CLIENTE\",\"PEDIDO\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evento").value("PEDIDO_CONCLUIDO"))
                .andExpect(jsonPath("$.authTipo").value("BEARER"))
                .andExpect(jsonPath("$.authLast4").value("1234"))
                .andExpect(jsonPath("$.webhookHeaders").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("segredo"))));

        verify(crmUseCase).createAutomation(argThat(c -> c.evento() == AutomationEvent.PEDIDO_CONCLUIDO
                && c.segmentoAlvo() == null && c.authTipo() == AutomationAuthType.BEARER
                && c.metadados().equals(List.of(AutomationMetadata.CLIENTE, AutomationMetadata.PEDIDO))));
    }

    @Test
    void createAutomation_invalida_returns_400_invalid_automation() throws Exception {
        when(crmUseCase.createAutomation(any())).thenThrow(new InvalidAutomationException("webhookUrl é obrigatório"));

        mockMvc.perform(post("/crm/automacoes")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"A\",\"gatilho\":\"MANUAL\",\"segmentoAlvo\":\"NOVO_LEAD\","
                                + "\"canal\":\"EMAIL\",\"template\":\"Oi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_AUTOMATION"));
    }

    @Test
    void updateAutomation_semWebhookHeaders_repassaNull() throws Exception {
        when(crmUseCase.updateAutomation(eq(1L), any())).thenReturn(automation(1L, true));

        mockMvc.perform(put("/crm/automacoes/1")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"A\",\"gatilho\":\"MANUAL\",\"segmentoAlvo\":\"NOVO_LEAD\","
                                + "\"canal\":\"EMAIL\",\"template\":\"Oi\",\"webhookUrl\":\"https://x.com/h\","
                                + "\"authTipo\":\"BEARER\"}"))
                .andExpect(status().isOk());

        verify(crmUseCase).updateAutomation(eq(1L), argThat(c -> c.webhookHeaders() == null
                && c.authTipo() == AutomationAuthType.BEARER));
    }

    @Test
    void createAutomation_withoutNome_returns_400() throws Exception {
        mockMvc.perform(post("/crm/automacoes")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gatilho\":\"MANUAL\",\"segmentoAlvo\":\"NOVO_LEAD\",\"canal\":\"EMAIL\","
                                + "\"template\":\"Ola {nome}\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listAutomations_returns_200() throws Exception {
        when(crmUseCase.listAutomations()).thenReturn(List.of(automation(1L, true)));

        mockMvc.perform(get("/crm/automacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void setAutomationActive_returns_200() throws Exception {
        when(crmUseCase.setAutomationActive(1L, false)).thenReturn(automation(1L, false));

        mockMvc.perform(patch("/crm/automacoes/1/ativa")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativa\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativa").value(false));
    }

    @Test
    void setAutomationActive_notFound_returns_404() throws Exception {
        when(crmUseCase.setAutomationActive(eq(99L), anyBoolean())).thenThrow(new CampaignAutomationNotFoundException(99L));

        mockMvc.perform(patch("/crm/automacoes/99/ativa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativa\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_AUTOMATION_NOT_FOUND"));
    }

    @Test
    void deleteAutomation_returns_204() throws Exception {
        mockMvc.perform(delete("/crm/automacoes/1").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(crmUseCase).deleteAutomation(1L);
    }

    @Test
    void deleteAutomation_notFound_returns_404() throws Exception {
        doThrow(new CampaignAutomationNotFoundException(99L)).when(crmUseCase).deleteAutomation(99L);

        mockMvc.perform(delete("/crm/automacoes/99").principal(AUTH))
                .andExpect(status().isNotFound());
    }

    @Test
    void dispatchAutomation_returns_200_withLogEntries() throws Exception {
        CampaignLogEntry entry = CampaignLogEntry.create(1L, 10L);
        when(crmUseCase.dispatchAutomation(1L)).thenReturn(List.of(entry));

        mockMvc.perform(post("/crm/automacoes/1/disparar").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].automationId").value(1))
                .andExpect(jsonPath("$[0].customerId").value(10))
                .andExpect(jsonPath("$[0].status").value("PENDENTE_INTEGRACAO"))
                .andExpect(jsonPath("$[0].convertidoEm").doesNotExist());
    }

    @Test
    void dispatchAutomation_notFound_returns_404() throws Exception {
        when(crmUseCase.dispatchAutomation(99L)).thenThrow(new CampaignAutomationNotFoundException(99L));

        mockMvc.perform(post("/crm/automacoes/99/disparar").principal(AUTH))
                .andExpect(status().isNotFound());
    }

    @Test
    void listAutomationLog_returns_200() throws Exception {
        when(crmUseCase.listAutomationLog(1L)).thenReturn(List.of(CampaignLogEntry.create(1L, 10L)));

        mockMvc.perform(get("/crm/automacoes/1/log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(10));
    }

    @Test
    void listAutomationLog_notFound_returns_404() throws Exception {
        when(crmUseCase.listAutomationLog(99L)).thenThrow(new CampaignAutomationNotFoundException(99L));

        mockMvc.perform(get("/crm/automacoes/99/log"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getChannelStatus_returns_200_with_email_and_whatsapp_status() throws Exception {
        when(crmUseCase.getChannelStatus()).thenReturn(List.of(
                ChannelStatus.of(ChannelType.EMAIL, true, "MAILPIT", "Conectado ao Mailpit"),
                ChannelStatus.of(ChannelType.WHATSAPP, false, null, "Integração de WhatsApp ainda não implementada")));

        mockMvc.perform(get("/crm/canais/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].canal").value("EMAIL"))
                .andExpect(jsonPath("$[0].conectado").value(true))
                .andExpect(jsonPath("$[0].provedor").value("MAILPIT"))
                .andExpect(jsonPath("$[1].canal").value("WHATSAPP"))
                .andExpect(jsonPath("$[1].conectado").value(false));
    }
}
