package com.cernecommerce.adapter.in.controller;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CRM-F010/F013 — permissões do "Marcar": RECEIVABLE_READ, _MANAGE, _RECEIVE, _RESCHEDULE e PDV_SALE_MANAGE. */
@SpringBootTest
@ActiveProfiles("dev")
class ReceivableControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void list_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/receivables")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_without_receivable_read_returns_403() throws Exception {
        mockMvc.perform(get("/receivables")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_and_summary_with_receivable_read_return_200() throws Exception {
        mockMvc.perform(get("/receivables").param("status", "ABERTO")
                        .with(user("ana").authorities(new SimpleGrantedAuthority("RECEIVABLE_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mockMvc.perform(get("/receivables/summary").param("overdue", "true")
                        .with(user("ana").authorities(new SimpleGrantedAuthority("RECEIVABLE_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void get_unknown_returns_404_receivable_not_found() throws Exception {
        mockMvc.perform(get("/receivables/999999")
                        .with(user("ana").authorities(new SimpleGrantedAuthority("RECEIVABLE_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECEIVABLE_NOT_FOUND"));
    }

    @Test
    void credit_limit_requires_receivable_manage() throws Exception {
        mockMvc.perform(put("/crm/customers/999999/credit-limit")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"creditLimit\":100}")
                        .with(user("ana").authorities(new SimpleGrantedAuthority("RECEIVABLE_READ"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/crm/customers/999999/credit-limit")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"creditLimit\":100}")
                        .with(user("gerente").authorities(new SimpleGrantedAuthority("RECEIVABLE_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void negative_credit_limit_returns_400() throws Exception {
        mockMvc.perform(put("/crm/customers/999999/credit-limit")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"creditLimit\":-1}")
                        .with(user("gerente").authorities(new SimpleGrantedAuthority("RECEIVABLE_MANAGE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancel_requires_receivable_manage() throws Exception {
        mockMvc.perform(post("/receivables/999999/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"erro\"}")
                        .with(user("ana").authorities(new SimpleGrantedAuthority("RECEIVABLE_READ"))))
                .andExpect(status().isForbidden());
    }

    /** CRM-F013 — receber deixou de ser PDV_SALE_MANAGE: sem uma permissão de marcado, 403. */
    @Test
    void pay_without_a_receivable_permission_returns_403() throws Exception {
        mockMvc.perform(post("/receivables/payments").param("sessionId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1,\"cashConfirmed\":true,\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":10}]}")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    /** CRM-F013 — RECEIVE, READ e MANAGE, cada uma sozinha, passam na rota (o caixa 999999 não existe). */
    @Test
    void pay_accepts_receive_read_or_manage() throws Exception {
        for (String perm : new String[] {"RECEIVABLE_RECEIVE", "RECEIVABLE_READ", "RECEIVABLE_MANAGE"}) {
            mockMvc.perform(post("/receivables/payments").param("sessionId", "999999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":999999,\"payments\":[{\"method\":\"PIX\",\"amount\":10}]}")
                            .with(user("ana").authorities(new SimpleGrantedAuthority(perm))))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .as(perm).isNotIn(401, 403));
        }
    }

    // ── CRM-F013: o atendente recebe e renegocia pelo cliente ─────────────────────────────────

    private static final String RECEIVE = "RECEIVABLE_RECEIVE";
    private static final String RESCHEDULE = "RECEIVABLE_RESCHEDULE";

    @Test
    void receive_reads_one_customer_and_one_receivable() throws Exception {
        mockMvc.perform(get("/crm/customers/999999/receivables")
                        .with(user("atendente").authorities(new SimpleGrantedAuthority(RECEIVE))))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
        mockMvc.perform(get("/receivables/999999")
                        .with(user("atendente").authorities(new SimpleGrantedAuthority(RECEIVE))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECEIVABLE_NOT_FOUND"));
    }

    @Test
    void receive_alone_is_refused_on_the_list_summary_due_date_cancel_and_limit() throws Exception {
        mockMvc.perform(get("/receivables")
                        .with(user("atendente").authorities(new SimpleGrantedAuthority(RECEIVE))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/receivables/summary")
                        .with(user("atendente").authorities(new SimpleGrantedAuthority(RECEIVE))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/receivables/999999/due-date")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueDate\":\"2099-01-10\",\"reason\":\"pediu prazo\"}")
                        .with(user("atendente").authorities(new SimpleGrantedAuthority(RECEIVE))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/receivables/999999/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"erro\"}")
                        .with(user("atendente").authorities(new SimpleGrantedAuthority(RECEIVE))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/crm/customers/999999/credit-limit")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"creditLimit\":100}")
                        .with(user("atendente").authorities(new SimpleGrantedAuthority(RECEIVE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reschedule_opens_the_due_date_but_not_the_cancel() throws Exception {
        mockMvc.perform(patch("/receivables/999999/due-date")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueDate\":\"2099-01-10\",\"reason\":\"pediu prazo\"}")
                        .with(user("atendente").authorities(new SimpleGrantedAuthority(RECEIVE),
                                new SimpleGrantedAuthority(RESCHEDULE))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECEIVABLE_NOT_FOUND"));
        mockMvc.perform(post("/receivables/999999/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"erro\"}")
                        .with(user("atendente").authorities(new SimpleGrantedAuthority(RECEIVE),
                                new SimpleGrantedAuthority(RESCHEDULE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void due_date_still_accepts_receivable_manage() throws Exception {
        mockMvc.perform(patch("/receivables/999999/due-date")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueDate\":\"2099-01-10\",\"reason\":\"pediu prazo\"}")
                        .with(user("gerente").authorities(new SimpleGrantedAuthority("RECEIVABLE_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    /** Caixa 62 — DINHEIRO sem a confirmação de gaveta é recusado antes de qualquer escrita. */
    @Test
    void pay_in_cash_without_cash_confirmed_returns_400() throws Exception {
        mockMvc.perform(post("/receivables/payments").param("sessionId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1,\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":10}]}")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("RECEIVABLE_RECEIVE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CASH_CONFIRMATION_REQUIRED"));
    }

    @Test
    void pay_without_method_returns_400() throws Exception {
        mockMvc.perform(post("/receivables/payments").param("sessionId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1,\"cashConfirmed\":true,\"payments\":[{\"amount\":10}]}")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("RECEIVABLE_RECEIVE"))))
                .andExpect(status().isBadRequest());
    }

    // ── V173: estorno de recebimento ─────────────────────────────────────────────────────────

    @Test
    void reverse_requires_receivable_payment_reverse() throws Exception {
        mockMvc.perform(post("/receivables/payment-batches/1/reverse")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"engano\"}")
                        .with(user("gerente").authorities(new SimpleGrantedAuthority("RECEIVABLE_MANAGE"),
                                new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reverse_unknown_batch_returns_404() throws Exception {
        mockMvc.perform(post("/receivables/payment-batches/999999/reverse")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"engano\"}")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("RECEIVABLE_PAYMENT_REVERSE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECEIVABLE_PAYMENT_BATCH_NOT_FOUND"));
    }

    @Test
    void reverse_without_reason_returns_400() throws Exception {
        mockMvc.perform(post("/receivables/payment-batches/1/reverse")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("RECEIVABLE_PAYMENT_REVERSE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eligibility_for_unknown_customer_returns_404() throws Exception {
        mockMvc.perform(get("/crm/customers/999999/on-account-eligibility")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    // ── PDV-F046: teto do vendedor ───────────────────────────────────────────────────────────

    @Test
    void credit_limit_capped_passaNaRota() throws Exception {
        // Liberado pela permissão com teto: chega ao serviço (cliente inexistente → 404, não 403).
        mockMvc.perform(put("/crm/customers/999999/credit-limit")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"creditLimit\":80}")
                        .with(user("vendedor").authorities(new SimpleGrantedAuthority("RECEIVABLE_LIMIT_SET_CAPPED"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void credit_limit_cap_vendedorRecebeOTeto_gerenteRecebeNulo() throws Exception {
        mockMvc.perform(get("/pdv/config/credit-limit-cap")
                        .with(user("vendedor").authorities(new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxCreditLimit").isNumber());
        mockMvc.perform(get("/pdv/config/credit-limit-cap")
                        .with(user("gerente").authorities(new SimpleGrantedAuthority("RECEIVABLE_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxCreditLimit").doesNotExist());
    }

    @Test
    void credit_limit_cap_semPermissao_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/config/credit-limit-cap")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void trocar_credit_limit_cap_exigeReceivableManage() throws Exception {
        mockMvc.perform(put("/pdv/config/credit-limit-cap")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"maxCreditLimit\":150}")
                        .with(user("vendedor").authorities(new SimpleGrantedAuthority("RECEIVABLE_LIMIT_SET_CAPPED"))))
                .andExpect(status().isForbidden());
    }
}
