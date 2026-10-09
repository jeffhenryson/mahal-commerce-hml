package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.OfflineSaleItem;
import com.cernecommerce.core.domain.model.pdv.OfflineSalePayment;
import com.cernecommerce.core.domain.model.pdv.OfflineSaleRejection;
import com.cernecommerce.core.ports.in.OfflineSaleUseCase;
import com.cernecommerce.core.ports.in.OfflineSaleUseCase.SyncResult;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PDV-F043 — sincronização da fila offline e revisão das recusadas, pela borda HTTP. */
class PdvOfflineControllerTest {

    private static final String CHAVE = "3f1c9a2e-7b4d-4c1e-9a8f-2d6b5e0c1a7b";
    private static final UsernamePasswordAuthenticationToken CAIXA =
            new UsernamePasswordAuthenticationToken("caixa1", null, List.of());
    private static final UsernamePasswordAuthenticationToken CAIXA_COM_DESCONTO =
            new UsernamePasswordAuthenticationToken("caixa1", null,
                    List.of(new SimpleGrantedAuthority("PDV_SALE_DISCOUNT")));

    private MockMvc mockMvc;
    private OfflineSaleUseCase offlineSaleUseCase;

    @BeforeEach
    void setup() {
        offlineSaleUseCase = mock(OfflineSaleUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PdvOfflineController(offlineSaleUseCase, new OrderDTOConverter()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static String lote(String discount) {
        return """
                {"sales":[{"clientSaleId":"%s","soldAt":"2026-10-09T15:00:00Z",
                  "items":[{"sku":"LM-AZUL-MACO","quantity":1%s}],
                  "payments":[{"method":"DINHEIRO","amount":12.00}]}]}"""
                .formatted(CHAVE, discount == null ? "" : ",\"discountAmount\":" + discount);
    }

    @Test
    void sync_devolveOResultadoDeCadaVenda() throws Exception {
        when(offlineSaleUseCase.sync(eq(1L), anyList(), eq("caixa1")))
                .thenReturn(List.of(SyncResult.synced(CHAVE, 55L, false)));

        mockMvc.perform(post("/pdv/sessions/1/sales/sync").principal(CAIXA)
                        .contentType(MediaType.APPLICATION_JSON).content(lote(null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientSaleId").value(CHAVE))
                .andExpect(jsonPath("$[0].status").value("SYNCED"))
                .andExpect(jsonPath("$[0].orderId").value(55));

        verify(offlineSaleUseCase).sync(eq(1L), argThat(sales -> sales.size() == 1
                && sales.get(0).items().get(0).sku().equals("LM-AZUL-MACO")
                && sales.get(0).payments().get(0).method() == PaymentMethod.DINHEIRO
                && sales.get(0).soldAt().equals(Instant.parse("2026-10-09T15:00:00Z"))), eq("caixa1"));
    }

    /** Desconto no lote sem a permissão: 403 antes de qualquer venda — mesma regra da venda online. */
    @Test
    void sync_comDescontoSemPermissao_responde403() throws Exception {
        mockMvc.perform(post("/pdv/sessions/1/sales/sync").principal(CAIXA)
                        .contentType(MediaType.APPLICATION_JSON).content(lote("2.00")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(offlineSaleUseCase);
    }

    @Test
    void sync_comDescontoEPermissao_passa() throws Exception {
        when(offlineSaleUseCase.sync(eq(1L), anyList(), eq("caixa1"))).thenReturn(List.of());

        mockMvc.perform(post("/pdv/sessions/1/sales/sync").principal(CAIXA_COM_DESCONTO)
                        .contentType(MediaType.APPLICATION_JSON).content(lote("2.00")))
                .andExpect(status().isOk());
    }

    @Test
    void sync_chaveQueNaoEUuid_responde400() throws Exception {
        mockMvc.perform(post("/pdv/sessions/1/sales/sync").principal(CAIXA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lote(null).replace(CHAVE, "venda-1")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(offlineSaleUseCase);
    }

    @Test
    void retry_devolveARecusaResolvida() throws Exception {
        OfflineSaleRejection resolvida = OfflineSaleRejection.create(1L, CHAVE, Instant.now(), null,
                        List.of(new OfflineSaleItem("LM-AZUL-MACO", BigDecimal.ONE, null, null)),
                        List.of(new OfflineSalePayment(PaymentMethod.DINHEIRO, new BigDecimal("12.00"), null)),
                        "INSUFFICIENT_STOCK", "sem saldo", "caixa1", Instant.now())
                .withId(70L).retried(55L, "gerente", Instant.now());
        when(offlineSaleUseCase.retry(70L, "gerente")).thenReturn(resolvida);

        mockMvc.perform(post("/pdv/offline-rejections/70/retry")
                        .principal(new UsernamePasswordAuthenticationToken("gerente", null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("RETRIED"))
                .andExpect(jsonPath("$.orderId").value(55))
                .andExpect(jsonPath("$.items[0].sku").value("LM-AZUL-MACO"));
    }

    @Test
    void discard_semMotivo_responde400() throws Exception {
        mockMvc.perform(post("/pdv/offline-rejections/70/discard").principal(CAIXA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
        verify(offlineSaleUseCase, never()).discard(any(), any(), any());
    }

    @Test
    void listRejections_devolveAsDoCaixa() throws Exception {
        when(offlineSaleUseCase.listRejections(1L)).thenReturn(List.of());

        mockMvc.perform(get("/pdv/sessions/1/offline-rejections")).andExpect(status().isOk());
    }
}
