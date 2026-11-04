package com.cernecommerce.adapter.in.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.adapter.in.converter.CashbackDTOConverter;
import com.cernecommerce.core.domain.exception.cashback.CashbackRateAlreadyExistsException;
import com.cernecommerce.core.domain.exception.cashback.CashbackRateNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackEntryType;
import com.cernecommerce.core.domain.model.cashback.CashbackMarginImpactItem;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.cashback.CashbackScope;
import com.cernecommerce.core.ports.in.CashbackUseCase;
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

public class CashbackControllerTest {

    private MockMvc mockMvc;
    private CashbackUseCase cashbackUseCase;
    private CashbackDTOConverter converter;
    private ApplicationEventPublisher publisher;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @BeforeEach
    void setup() {
        cashbackUseCase = mock(CashbackUseCase.class);
        converter = new CashbackDTOConverter();
        publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CashbackController(cashbackUseCase, converter, publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private CashbackRate rate(Long id, CashbackScope scope, String ref, BigDecimal percent) {
        return CashbackRate.of(id, scope, ref, percent, true, Instant.now(), null, Instant.now());
    }

    @Test
    void createRate_returns_201() throws Exception {
        CashbackRate created = rate(1L, CashbackScope.GLOBAL, null, new BigDecimal("5.0"));
        when(cashbackUseCase.createRate(eq(CashbackScope.GLOBAL), eq(null), eq(new BigDecimal("5.0")), any(), any()))
                .thenReturn(created);

        mockMvc.perform(post("/cashback/rates")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"GLOBAL\",\"percent\":5.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.scope").value("GLOBAL"))
                .andExpect(jsonPath("$.percent").value(5.0));
                
        verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    void createRate_conflict_returns_409() throws Exception {
        when(cashbackUseCase.createRate(any(), any(), any(), any(), any()))
                .thenThrow(new CashbackRateAlreadyExistsException(CashbackScope.GLOBAL, null));

        mockMvc.perform(post("/cashback/rates")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"GLOBAL\",\"percent\":5.0}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createRate_withoutScope_returns_400() throws Exception {
        mockMvc.perform(post("/cashback/rates")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percent\":5.0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listRates_returns_200() throws Exception {
        when(cashbackUseCase.listRates(0, 20)).thenReturn(
                new PageResult<>(List.of(rate(1L, CashbackScope.GLOBAL, null, new BigDecimal("5.0"))), 0, 20, 1, 1));

        mockMvc.perform(get("/cashback/rates").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void patchRate_returns_200() throws Exception {
        CashbackRate patched = rate(1L, CashbackScope.GLOBAL, null, new BigDecimal("6.0"));
        when(cashbackUseCase.patchRate(eq(1L), eq(new BigDecimal("6.0")), eq(false), any()))
                .thenReturn(patched);

        mockMvc.perform(patch("/cashback/rates/1")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percent\":6.0,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percent").value(6.0));
                
        verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    void patchRate_notFound_returns_404() throws Exception {
        when(cashbackUseCase.patchRate(eq(99L), any(), any(), any()))
                .thenThrow(new CashbackRateNotFoundException(99L));

        mockMvc.perform(patch("/cashback/rates/99")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolveRate_found_returns_200_withBody() throws Exception {
        when(cashbackUseCase.resolveApplicableRate("SKU-123"))
                .thenReturn(rate(1L, CashbackScope.SKU, "SKU-123", new BigDecimal("10.0")));

        mockMvc.perform(get("/cashback/rates/resolve").param("sku", "SKU-123").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("SKU"))
                .andExpect(jsonPath("$.percent").value(10.0));
    }

    @Test
    void resolveRate_notFound_returns_200_emptyBody() throws Exception {
        when(cashbackUseCase.resolveApplicableRate("SKU-456")).thenReturn(null);

        mockMvc.perform(get("/cashback/rates/resolve").param("sku", "SKU-456").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").doesNotExist());
    }
    
    @Test
    void resolveRate_withoutSku_returns_400() throws Exception {
        mockMvc.perform(get("/cashback/rates/resolve").principal(AUTH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMarginImpact_returns_200() throws Exception {
        when(cashbackUseCase.findMarginImpact(new BigDecimal("30.0"))).thenReturn(List.of(
                new CashbackMarginImpactItem("SKU-123", "Produto 1", new BigDecimal("20.0"), new BigDecimal("10.0"), new BigDecimal("50.0"))
        ));

        mockMvc.perform(get("/cashback/margin-impact").param("maxShare", "30.0").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("SKU-123"))
                .andExpect(jsonPath("$[0].marginShareConsumed").value(50.0));
    }

    @Test
    void getCustomerBalance_returns_200() throws Exception {
        when(cashbackUseCase.getCustomerBalance(1L)).thenReturn(
                new CashbackBalance(new BigDecimal("10.0"), new BigDecimal("5.0"), new BigDecimal("0.0"))
        );

        mockMvc.perform(get("/cashback/customers/1").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(10.0))
                .andExpect(jsonPath("$.pending").value(5.0));
    }

    @Test
    void listCustomerEntries_returns_200() throws Exception {
        CashbackEntry entry = CashbackEntry.of(10L, 1L, 100L, 1001L, CashbackEntryType.EARNED, new BigDecimal("10.0"), Instant.now(), null, null, Instant.now());
        when(cashbackUseCase.listCustomerEntries(1L, 0, 20)).thenReturn(
                new PageResult<>(List.of(entry), 0, 20, 1, 1)
        );

        mockMvc.perform(get("/cashback/customers/1/entries").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].type").value("EARNED"))
                .andExpect(jsonPath("$.content[0].amount").value(10.0));
    }

    // ── V186 — parâmetros e prévia de resgate ───────────────────────────────────────────────

    @Test
    void getSettings_returns_current_values() throws Exception {
        when(cashbackUseCase.getSettings()).thenReturn(com.cernecommerce.core.domain.model.cashback.CashbackSettings.defaults());

        mockMvc.perform(get("/cashback/settings").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carenciaDias").value(7))
                .andExpect(jsonPath("$.expiracaoDias").value(180))
                .andExpect(jsonPath("$.resgateTetoPercent").value(50))
                .andExpect(jsonPath("$.resgateMinimoSaldo").value(50.00));
    }

    @Test
    void putSettings_saves_and_publishes_audit() throws Exception {
        when(cashbackUseCase.updateSettings(any(), eq("admin"))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/cashback/settings").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carenciaDias\":3,\"expiracaoDias\":90,\"resgateTetoPercent\":30,\"resgateMinimoSaldo\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carenciaDias").value(3))
                .andExpect(jsonPath("$.resgateTetoPercent").value(30));

        verify(cashbackUseCase).updateSettings(eq(new com.cernecommerce.core.domain.model.cashback.CashbackSettings(
                3, 90, new BigDecimal("30"), new BigDecimal("20"))), eq("admin"));
        verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    void putSettings_out_of_range_returns_400() throws Exception {
        mockMvc.perform(put("/cashback/settings").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carenciaDias\":7,\"expiracaoDias\":180,\"resgateTetoPercent\":101,\"resgateMinimoSaldo\":50}"))
                .andExpect(status().isBadRequest());

        verify(cashbackUseCase, never()).updateSettings(any(), any());
    }

    @Test
    void redemptionQuote_returns_max_and_reason() throws Exception {
        when(cashbackUseCase.quoteRedemption(eq(5L), eq(new BigDecimal("100"))))
                .thenReturn(new com.cernecommerce.core.domain.model.cashback.CashbackRedemptionQuote(
                        new BigDecimal("30.00"), new BigDecimal("50.00"), new BigDecimal("50"),
                        new BigDecimal("50.00"), BigDecimal.ZERO, "CASHBACK_BELOW_MINIMUM_BALANCE"));

        mockMvc.perform(get("/cashback/customers/5/redemption-quote").param("amount", "100").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(30.00))
                .andExpect(jsonPath("$.maxRedeemable").value(0))
                .andExpect(jsonPath("$.reason").value("CASHBACK_BELOW_MINIMUM_BALANCE"));
    }
}
