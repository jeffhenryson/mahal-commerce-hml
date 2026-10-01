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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CRM-F010 — permissões do "Marcar": RECEIVABLE_READ, RECEIVABLE_MANAGE e PDV_SALE_MANAGE. */
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

    @Test
    void pay_requires_pdv_sale_manage() throws Exception {
        mockMvc.perform(post("/receivables/payments").param("sessionId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1,\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":10}]}")
                        .with(user("ana").authorities(new SimpleGrantedAuthority("RECEIVABLE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void eligibility_for_unknown_customer_returns_404() throws Exception {
        mockMvc.perform(get("/crm/customers/999999/on-account-eligibility")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isNotFound());
    }
}
