package com.cernecommerce.adapter.in.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.adapter.out.persistence.repository.AuditLogJpaRepository;
import com.cernecommerce.adapter.out.security.ratelimit.InMemoryResourceRateLimiterAdapter;
import com.cernecommerce.core.ports.out.ratelimit.ResourceRateLimiterPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("dev")
public class CrmControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AuditLogJpaRepository auditRepo;

    @Autowired
    private ResourceRateLimiterPort resourceRateLimiter;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        if (resourceRateLimiter instanceof InMemoryResourceRateLimiterAdapter r) r.reset();
    }

    /** Aguarda até 2 segundos que uma entrada com a action apareça no banco (async). */
    private void awaitAction(String action) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (auditRepo.findAll().stream().anyMatch(e -> action.equals(e.getAction()))) return;
            Thread.sleep(50);
        }
        assertThat(auditRepo.findAll().stream().anyMatch(e -> action.equals(e.getAction())))
                .as("Expected audit entry with action=" + action).isTrue();
    }

    /**
     * Cadastra um cliente único e devolve o id — fixture reaproveitada pelos testes de sucesso
     * que dependem de um cliente existente (notas, pedidos, cashback, tags).
     */
    private Long givenCustomer() throws Exception {
        String email = "sec_test_" + System.nanoTime() + "@example.com";
        String location = mockMvc.perform(post("/crm/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"contato\":\"" + uniquePhone() + "\",\"email\":\"" + email + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    /**
     * Cadastra uma tag única e devolve o id — fixture reaproveitada pelos testes de sucesso de
     * associação/remoção de tags.
     */
    private Long givenTag() throws Exception {
        String nome = "TAG_SEC_TEST_" + System.nanoTime();
        String location = mockMvc.perform(post("/crm/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    /**
     * Cadastra uma automação de gatilho MANUAL e devolve o id — fixture reaproveitada pelos
     * testes de sucesso de ativação, remoção, disparo e log.
     */
    /** Porta fechada: o disparo falha na hora (FALHA no log), sem esperar timeout. */
    private static final String WEBHOOK_URL = "http://localhost:9/automacao";

    private Long givenAutomation() throws Exception {
        String nome = "AUTOMACAO_SEC_TEST_" + System.nanoTime();
        String location = mockMvc.perform(post("/crm/automacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\",\"gatilho\":\"MANUAL\",\"segmentoAlvo\":\"NOVO_LEAD\","
                        + "\"canal\":\"EMAIL\",\"template\":\"Ola {nome}\",\"webhookUrl\":\"" + WEBHOOK_URL + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("AUTOMATION_MANAGE"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void create_customer_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/crm/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"contato\":\"11999998888\",\"email\":\"maria@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_customer_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(post("/crm/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"contato\":\"11999998888\",\"email\":\"maria@example.com\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_customer_with_crm_customer_manage_returns_201() throws Exception {
        String email = "sec_test_" + System.currentTimeMillis() + "@example.com";
        mockMvc.perform(post("/crm/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"contato\":\"" + uniquePhone() + "\",\"email\":\"" + email + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isCreated());
    }

    // ── CRM-C006 / PDV-F020: atendente cadastra lead; edição continua com o admin ─────────────

    private static SimpleGrantedAuthority[] atendente() {
        return new SimpleGrantedAuthority[]{
                new SimpleGrantedAuthority("ROLE_ATENDENTE"),
                new SimpleGrantedAuthority("CRM_CUSTOMER_READ"),
                new SimpleGrantedAuthority("CRM_CUSTOMER_LOOKUP"),
                new SimpleGrantedAuthority("CRM_LEAD_CREATE")};
    }

    /** Telefone único por teste, sem máscara (11 dígitos). */
    /** Telefone único por teste: desde CRM-C007 telefone repetido barra o cadastro com 409. */
    private static String uniquePhone() {
        return "839" + String.format("%08d", System.nanoTime() % 100_000_000L);
    }

    @Test
    void create_customer_with_crm_lead_create_returns_201() throws Exception {
        mockMvc.perform(post("/crm/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Lead Mesa\",\"contato\":\"" + uniquePhone() + "\",\"email\":\"\"}")
                .with(user("atendente").authorities(atendente())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void resolve_lead_creates_then_reuses_same_customer_by_phone_with_other_mask() throws Exception {
        String phone = uniquePhone();
        String masked = "(" + phone.substring(0, 2) + ") " + phone.substring(2, 7) + "-" + phone.substring(7);
        String cpf = String.valueOf(10000000000L + (System.nanoTime() % 89999999999L));
        String maskedCpf = cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-"
                + cpf.substring(9);

        String location = mockMvc.perform(post("/crm/customers/lead")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Lead PDV\",\"contato\":\"" + phone + "\",\"origem\":\"PDV\"}")
                .with(user("atendente").authorities(atendente())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estagio").value("NOVO_LEAD"))
                .andReturn().getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(post("/crm/customers/lead")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Lead PDV\",\"contato\":\"" + masked + "\",\"cpf\":\"" + maskedCpf + "\"}")
                .with(user("atendente").authorities(atendente())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Long.valueOf(id)))
                .andExpect(jsonPath("$.cpf").value(cpf));
    }

    @Test
    void resolve_lead_without_permission_returns_403() throws Exception {
        mockMvc.perform(post("/crm/customers/lead")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Lead\",\"contato\":\"11999998888\"}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_customer_requires_crm_customer_manage() throws Exception {
        mockMvc.perform(put("/crm/customers/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"contato\":\"11999998888\"}")
                .with(user("atendente").authorities(atendente())))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_customer_adds_cpf_to_light_customer() throws Exception {
        Long id = givenCustomer();
        String cpf = String.valueOf(10000000000L + (System.nanoTime() % 89999999999L));

        mockMvc.perform(put("/crm/customers/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria Editada\",\"contato\":\"" + uniquePhone() + "\",\"cpf\":\"" + cpf + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(cpf))
                .andExpect(jsonPath("$.nome").value("Maria Editada"))
                .andExpect(jsonPath("$.email").doesNotExist());

        mockMvc.perform(get("/crm/customers/" + id)
                .with(user("caixa").authorities(new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(cpf));
    }

    @Test
    void get_customer_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/crm/customers/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_customer_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/1")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_customer_with_crm_customer_read_returns_200_or_404() throws Exception {
        // Sem cliente prévio cadastrado com esse id, a resposta é 404 (CUSTOMER_NOT_FOUND) —
        // o importante aqui é a authority ser aceita (200/404), não bloqueada por 401/403.
        mockMvc.perform(get("/crm/customers/999999")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_customer_leve_without_email_or_cpf_returns_201() throws Exception {
        // CRM-C005: nome + contato bastam agora — sem email, sem cpf.
        String contato = "SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/crm/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\",\"contato\":\"" + contato + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void create_customer_without_any_identifier_returns_400() throws Exception {
        mockMvc.perform(post("/crm/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lookup_customer_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/crm/customers/lookup").param("cpf", "12345678900"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lookup_customer_without_crm_customer_lookup_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/lookup").param("cpf", "12345678900")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void lookup_customer_with_crm_customer_lookup_returns_404_when_not_found() throws Exception {
        mockMvc.perform(get("/crm/customers/lookup").param("cpf", "99999999999")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("CRM_CUSTOMER_LOOKUP"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void lookup_contact_returns_empty_list_when_not_found() throws Exception {
        mockMvc.perform(get("/crm/customers/lookup/contact").param("cpf", "99999999999")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("CRM_CUSTOMER_LOOKUP"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void lookup_contact_without_crm_customer_lookup_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/lookup/contact").param("phone", "11999998888")
                .with(user("bob").authorities(new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void lookup_customer_without_any_criteria_returns_400() throws Exception {
        mockMvc.perform(get("/crm/customers/lookup")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("CRM_CUSTOMER_LOOKUP"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lookup_customer_finds_by_cpf_after_quick_registration() throws Exception {
        // De ponta a ponta: cadastra (CRM_CUSTOMER_MANAGE) e acha pelo lookup (CRM_CUSTOMER_LOOKUP)
        // — a mesma jornada do balcão, com as duas permissões que o plano mantém separadas.
        String cpf = String.valueOf(10000000000L + (System.nanoTime() % 89999999999L));
        mockMvc.perform(post("/crm/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cliente Balcao\",\"cpf\":\"" + cpf + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/crm/customers/lookup").param("cpf", cpf)
                .with(user("caixa").authorities(new SimpleGrantedAuthority("CRM_CUSTOMER_LOOKUP"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(cpf))
                .andExpect(jsonPath("$.nome").value("Cliente Balcao"));
    }

    @Test
    void create_customer_with_existing_phone_returns_409_with_customer_id() throws Exception {
        String phone = uniquePhone();
        String location = mockMvc.perform(post("/crm/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Primeiro\",\"contato\":\"" + phone + "\"}")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        long id = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        // Mesmo número com DDI e máscara: bate pelo phone_normalized (dígitos, sem 55).
        String masked = "+55 (" + phone.substring(0, 2) + ") " + phone.substring(2, 7) + "-" + phone.substring(7);
        mockMvc.perform(post("/crm/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Segundo\",\"contato\":\"" + masked + "\"}")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.customerId").value(id))
                .andExpect(jsonPath("$.matchedBy[0]").value("PHONE"));

        // O front manda só dígitos, sem DDI — casa com o contato gravado com +55 e máscara.
        mockMvc.perform(get("/crm/customers/lookup/contact").param("phone", phone)
                .with(user("caixa").authorities(new SimpleGrantedAuthority("CRM_CUSTOMER_LOOKUP"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].nome").value("Primeiro"))
                .andExpect(jsonPath("$[0].matchedBy[0]").value("PHONE"));
    }

    @Test
    void list_customers_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/crm/customers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_customers_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_customers_with_crm_customer_read_returns_200() throws Exception {
        mockMvc.perform(get("/crm/customers")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void add_note_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/crm/customers/999999/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"Nota\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void add_note_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(post("/crm/customers/999999/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"Nota\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void add_note_with_crm_customer_manage_returns_201_or_404() throws Exception {
        // Sem cliente prévio cadastrado com esse id, a resposta é 404 (CUSTOMER_NOT_FOUND) —
        // o importante aqui é a authority ser aceita (201/404), não bloqueada por 401/403.
        mockMvc.perform(post("/crm/customers/999999/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"Nota\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_notes_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/999999/notes")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_notes_with_crm_customer_read_returns_200() throws Exception {
        Long customerId = givenCustomer();
        mockMvc.perform(post("/crm/customers/" + customerId + "/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"Nota de teste\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/crm/customers/" + customerId + "/notes")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void list_orders_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/999999/orders")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_orders_with_crm_customer_read_returns_200() throws Exception {
        Long customerId = givenCustomer();
        mockMvc.perform(get("/crm/customers/" + customerId + "/orders")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void list_cashback_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/999999/cashback")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_cashback_with_crm_customer_read_returns_200() throws Exception {
        Long customerId = givenCustomer();
        mockMvc.perform(get("/crm/customers/" + customerId + "/cashback")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void move_stage_without_auth_returns_401() throws Exception {
        mockMvc.perform(patch("/crm/customers/999999/estagio")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estagio\":\"EM_ATENDIMENTO\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void move_stage_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(patch("/crm/customers/999999/estagio")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estagio\":\"EM_ATENDIMENTO\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void move_stage_with_crm_customer_manage_returns_404_for_inexistent_customer() throws Exception {
        // Sem cliente prévio cadastrado com esse id, a resposta é 404 (CUSTOMER_NOT_FOUND) —
        // o importante aqui é a authority ser aceita (200/404), não bloqueada por 401/403.
        mockMvc.perform(patch("/crm/customers/999999/estagio")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estagio\":\"EM_ATENDIMENTO\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_stage_history_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/999999/estagio/historico")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboard_overview_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/crm/dashboard/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboard_overview_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/dashboard/overview")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboard_overview_with_crm_customer_read_returns_200() throws Exception {
        mockMvc.perform(get("/crm/dashboard/overview")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void channel_status_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/crm/canais/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void channel_status_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/canais/status")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void channel_status_with_crm_customer_read_returns_200() throws Exception {
        mockMvc.perform(get("/crm/canais/status")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void create_tag_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/crm/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"VIP\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_tag_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(post("/crm/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"VIP\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_tag_with_crm_customer_manage_returns_201() throws Exception {
        String nome = "TAG_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/crm/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void list_tags_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/crm/tags"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_tags_with_crm_customer_read_returns_200() throws Exception {
        mockMvc.perform(get("/crm/tags")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void delete_tag_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(delete("/crm/tags/999999")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_tag_with_crm_customer_manage_returns_204() throws Exception {
        Long tagId = givenTag();
        mockMvc.perform(delete("/crm/tags/" + tagId)
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void add_tag_to_customer_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(post("/crm/customers/999999/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tagId\":999999}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void add_tag_to_customer_with_crm_customer_manage_returns_2xx() throws Exception {
        Long customerId = givenCustomer();
        Long tagId = givenTag();
        mockMvc.perform(post("/crm/customers/" + customerId + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tagId\":" + tagId + "}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void remove_tag_from_customer_without_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(delete("/crm/customers/999999/tags/999999")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void remove_tag_from_customer_with_crm_customer_manage_returns_2xx() throws Exception {
        Long customerId = givenCustomer();
        Long tagId = givenTag();
        mockMvc.perform(post("/crm/customers/" + customerId + "/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tagId\":" + tagId + "}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/crm/customers/" + customerId + "/tags/" + tagId)
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void list_customer_tags_without_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/999999/tags")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_customer_tags_with_crm_customer_read_returns_200() throws Exception {
        Long customerId = givenCustomer();
        mockMvc.perform(get("/crm/customers/" + customerId + "/tags")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void export_customers_csv_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/crm/customers/export"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void export_customers_csv_without_crm_customer_export_returns_403() throws Exception {
        mockMvc.perform(get("/crm/customers/export")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void export_customers_csv_with_crm_customer_read_only_returns_403() throws Exception {
        // CRM-C002: ler clientes não implica poder exportar a base inteira — CRM_CUSTOMER_EXPORT
        // é uma permissão dedicada, separada de CRM_CUSTOMER_READ.
        mockMvc.perform(get("/crm/customers/export")
                .with(user("leitor").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void export_customers_csv_with_crm_customer_export_returns_200() throws Exception {
        mockMvc.perform(get("/crm/customers/export")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_EXPORT"))))
                .andExpect(status().isOk());
    }

    @Test
    void export_customers_csv_publishes_audit_event() throws Exception {
        mockMvc.perform(get("/crm/customers/export")
                .with(user("audit_export_user").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_EXPORT"))))
                .andExpect(status().isOk());

        awaitAction("CUSTOMER_LIST_EXPORTED");
    }

    @Test
    void export_customers_csv_exceeding_rate_limit_returns_429() throws Exception {
        // Bucket crm-export: 5 requisições/hora por usuário (ResourceRateLimitingFilter +
        // InMemoryResourceRateLimiterAdapter, perfil dev). A chave é o usuário autenticado, então
        // usamos sempre o mesmo usuário.
        String username = "rate_limit_export_user";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/crm/customers/export")
                    .with(user(username).authorities(
                            new SimpleGrantedAuthority("ROLE_ADMIN"),
                            new SimpleGrantedAuthority("CRM_CUSTOMER_EXPORT"))))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/crm/customers/export")
                .with(user(username).authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_EXPORT"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void create_automation_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/crm/automacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Boas-vindas\",\"gatilho\":\"MANUAL\",\"segmentoAlvo\":\"NOVO_LEAD\","
                        + "\"canal\":\"EMAIL\",\"template\":\"Ola {nome}\",\"webhookUrl\":\"" + WEBHOOK_URL + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_automation_without_automation_manage_returns_403() throws Exception {
        mockMvc.perform(post("/crm/automacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Boas-vindas\",\"gatilho\":\"MANUAL\",\"segmentoAlvo\":\"NOVO_LEAD\","
                        + "\"canal\":\"EMAIL\",\"template\":\"Ola {nome}\",\"webhookUrl\":\"" + WEBHOOK_URL + "\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_automation_with_automation_manage_returns_201() throws Exception {
        String nome = "AUTOMACAO_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/crm/automacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\",\"gatilho\":\"MANUAL\",\"segmentoAlvo\":\"NOVO_LEAD\","
                        + "\"canal\":\"EMAIL\",\"template\":\"Ola {nome}\",\"webhookUrl\":\"" + WEBHOOK_URL + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("AUTOMATION_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void list_automations_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/crm/automacoes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_automations_with_crm_customer_read_returns_200() throws Exception {
        mockMvc.perform(get("/crm/automacoes")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void set_automation_active_without_automation_manage_returns_403() throws Exception {
        mockMvc.perform(patch("/crm/automacoes/999999/ativa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ativa\":false}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void set_automation_active_with_automation_manage_returns_200() throws Exception {
        Long automationId = givenAutomation();
        mockMvc.perform(patch("/crm/automacoes/" + automationId + "/ativa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ativa\":false}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("AUTOMATION_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void set_automation_active_publishes_audit_event() throws Exception {
        Long automationId = givenAutomation();
        mockMvc.perform(patch("/crm/automacoes/" + automationId + "/ativa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ativa\":false}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("AUTOMATION_MANAGE"))))
                .andExpect(status().isOk());

        awaitAction("CAMPAIGN_AUTOMATION_TOGGLED");
    }

    @Test
    void delete_automation_without_automation_manage_returns_403() throws Exception {
        mockMvc.perform(delete("/crm/automacoes/999999")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_automation_with_automation_manage_returns_204() throws Exception {
        Long automationId = givenAutomation();
        mockMvc.perform(delete("/crm/automacoes/" + automationId)
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("AUTOMATION_MANAGE"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void dispatch_automation_without_automation_manage_returns_403() throws Exception {
        mockMvc.perform(post("/crm/automacoes/999999/disparar")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatch_automation_with_automation_manage_returns_404_for_inexistent_automation() throws Exception {
        mockMvc.perform(post("/crm/automacoes/999999/disparar")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("AUTOMATION_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void dispatch_automation_with_automation_manage_returns_200_or_204() throws Exception {
        // CrmController#dispatchAutomation sempre devolve ResponseEntity.ok(...) — 200.
        Long automationId = givenAutomation();
        mockMvc.perform(post("/crm/automacoes/" + automationId + "/disparar")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("AUTOMATION_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void list_automation_log_without_automation_manage_returns_403() throws Exception {
        mockMvc.perform(get("/crm/automacoes/999999/log")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_automation_log_with_automation_manage_returns_200() throws Exception {
        Long automationId = givenAutomation();
        mockMvc.perform(post("/crm/automacoes/" + automationId + "/disparar")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("AUTOMATION_MANAGE"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/crm/automacoes/" + automationId + "/log")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("AUTOMATION_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void create_automation_with_only_crm_customer_manage_returns_403() throws Exception {
        mockMvc.perform(post("/crm/automacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Boas-vindas\",\"gatilho\":\"MANUAL\",\"segmentoAlvo\":\"NOVO_LEAD\","
                        + "\"canal\":\"EMAIL\",\"template\":\"Ola {nome}\",\"webhookUrl\":\"" + WEBHOOK_URL + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_automation_log_with_only_crm_customer_read_returns_403() throws Exception {
        mockMvc.perform(get("/crm/automacoes/999999/log")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("CRM_CUSTOMER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_automations_with_automation_manage_returns_200() throws Exception {
        mockMvc.perform(get("/crm/automacoes")
                .with(user("dev").authorities(new SimpleGrantedAuthority("AUTOMATION_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void create_automation_without_webhook_url_returns_400_invalid_automation() throws Exception {
        mockMvc.perform(post("/crm/automacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Sem destino\",\"gatilho\":\"MANUAL\",\"segmentoAlvo\":\"NOVO_LEAD\","
                        + "\"canal\":\"EMAIL\",\"template\":\"Ola {nome}\"}")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("AUTOMATION_MANAGE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_AUTOMATION"));
    }
}
