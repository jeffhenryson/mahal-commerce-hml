package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.crm.LeadResolution;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cernecommerce.adapter.in.converter.ComandaDTOConverter;
import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.core.domain.exception.estoque.ReservedStockException;
import com.cernecommerce.core.domain.exception.pdv.ComandaItemNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.ItemNotOpenInComandaException;
import com.cernecommerce.core.domain.exception.pdv.LinkedItemMustCloseTogetherException;
import com.cernecommerce.core.domain.exception.pdv.ComandaPartiallyClosedException;
import com.cernecommerce.core.domain.exception.pdv.ComandaMergeNotAllowedException;
import com.cernecommerce.core.domain.exception.pdv.ComandaNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.DiscountExceedsBillException;
import com.cernecommerce.core.domain.exception.pdv.LinkedItemIsChargedException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.exception.pdv.SurchargeInvalidException;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

class PdvComandaControllerTest {

    private MockMvc mockMvc;
    private ComandaUseCase comandaUseCase;
    private CrmUseCase crmUseCase;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("caixa1", null, List.of());

    /** PDV-F014 — desconto no fechamento da mesa tem permissão própria, como a cortesia. */
    private static final UsernamePasswordAuthenticationToken AUTH_DISCOUNT =
            new UsernamePasswordAuthenticationToken("gerente", null,
                    List.of(new SimpleGrantedAuthority("PDV_COMANDA_DISCOUNT")));

    /** Cortesia é desconto de 100%, e tem permissão própria (PDV-F010) — só ADMIN a recebe. */
    private static final UsernamePasswordAuthenticationToken AUTH_COURTESY =
            new UsernamePasswordAuthenticationToken("gerente", null,
                    List.of(new SimpleGrantedAuthority("PDV_COMANDA_COURTESY")));

    /** PDV-F011 — acréscimo manual, na mesma lógica da cortesia: só ADMIN o recebe. */
    private static final UsernamePasswordAuthenticationToken AUTH_SURCHARGE =
            new UsernamePasswordAuthenticationToken("gerente", null,
                    List.of(new SimpleGrantedAuthority("PDV_COMANDA_SURCHARGE")));

    @BeforeEach
    void setup() {
        comandaUseCase = mock(ComandaUseCase.class);
        crmUseCase = mock(CrmUseCase.class);
        when(crmUseCase.findCustomerNames(anyCollection())).thenReturn(Map.of());
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PdvComandaController(comandaUseCase, new ComandaDTOConverter(),
                        new OrderDTOConverter(), crmUseCase, publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static Comanda abertaComanda() {
        return Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.ABERTA, List.of(), null,
                "caixa1", Instant.now(), null);
    }

    @Test
    void openComanda_returns_201() throws Exception {
        when(comandaUseCase.openComanda(eq(1L), eq("Mesa 4"), any(), anyString())).thenReturn(abertaComanda());

        mockMvc.perform(post("/pdv/comandas?sessionId=1")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableOrCustomerLabel\":\"Mesa 4\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("ABERTA"));
    }

    @Test
    void addItem_returns_201_withRunningTotal() throws Exception {
        Comanda withItem = abertaComanda().withAddedItem(
                ComandaItem.fromCatalog("ESS-MENTA", BigDecimal.ONE,
                        Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00")), "Essência Menta"));
        when(comandaUseCase.addItem(eq(10L), eq("ESS-MENTA"), any(), any(), eq(false), any(), any(), any(),
                anyString()))
                .thenReturn(withItem);

        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ESS-MENTA\",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].sku").value("ESS-MENTA"))
                .andExpect(jsonPath("$.runningTotal").value(25.00));
    }

    /**
     * EST-C016 — o caminho da mesa. Com pool único (um só saldo servindo salão e marketplace),
     * uma reserva de pedido online derruba o disponível e o {@code adjustStock(SAIDA)} de
     * {@code addItem} bate nela. Antes do handler o atendente recebia <b>500</b> ao lançar a
     * essência, sem saber que bastava cancelar a reserva pelo painel para vender.
     */
    @Test
    void addItem_reservedStock_returns_400() throws Exception {
        when(comandaUseCase.addItem(eq(10L), eq("ESS-MENTA"), any(), any(), eq(false), any(), any(), any(),
                anyString()))
                .thenThrow(new ReservedStockException("ESS-MENTA", 1L, new BigDecimal("10.000"),
                        new BigDecimal("8.000"), new BigDecimal("5.000")));

        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ESS-MENTA\",\"quantity\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RESERVED_STOCK"));
    }

    @Test
    void getComanda_returns_200() throws Exception {
        when(comandaUseCase.getHistoryEntry(10L)).thenReturn(new ComandaUseCase.ComandaHistoryEntry(
                abertaComanda(), null, null, List.of(), Map.of(), null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0));

        mockMvc.perform(get("/pdv/comandas/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableOrCustomerLabel").value("Mesa 4"))
                .andExpect(jsonPath("$.orders").isEmpty());
    }

    @Test
    void getComanda_notFound_returns_404() throws Exception {
        when(comandaUseCase.getHistoryEntry(999L)).thenThrow(new ComandaNotFoundException(999L));

        mockMvc.perform(get("/pdv/comandas/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_NOT_FOUND"));
    }

    /**
     * PDV-C012 — a rota devolvia a List na raiz e passou a devolver PageResult. É quebra de
     * contrato assumida, com o precedente de EST-C005 em {@code GET /estoque/warehouses}.
     */
    @Test
    void listOpenComandas_returns_200_withPageResult() throws Exception {
        when(comandaUseCase.listOpenComandas(1L, null, 0, 50))
                .thenReturn(new PageResult<>(List.of(abertaComanda()), 0, 50, 1, 1));

        mockMvc.perform(get("/pdv/comandas?sessionId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /**
     * PDV-C007 — o ponto da entrega: sem {@code sessionId} a rota devolve as mesas da loja. Era a
     * obrigatoriedade do parâmetro que forçava o cliente a listar as sessões abertas e disparar uma
     * chamada por sessão.
     */
    @Test
    void listOpenComandas_withoutSessionId_listsTheWholeStore() throws Exception {
        Comanda outraMesa = Comanda.of(11L, 2L, "LOJA-01", "Mesa 7", ComandaStatus.ABERTA, List.of(), null,
                "caixa2", Instant.now(), null);
        when(comandaUseCase.listOpenComandas(null, null, 0, 50))
                .thenReturn(new PageResult<>(List.of(abertaComanda(), outraMesa), 0, 50, 2, 1));

        mockMvc.perform(get("/pdv/comandas"))
                .andExpect(status().isOk())
                // Duas mesas de DUAS sessões de caixa diferentes, numa requisição só.
                .andExpect(jsonPath("$.content[0].sessionId").value(1))
                .andExpect(jsonPath("$.content[1].sessionId").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listOpenComandas_passesWarehouseAndPagingThrough() throws Exception {
        when(comandaUseCase.listOpenComandas(null, "LOJA-01", 2, 10))
                .thenReturn(new PageResult<>(List.of(), 2, 10, 0, 0));

        mockMvc.perform(get("/pdv/comandas?warehouseCode=LOJA-01&page=2&size=10"))
                .andExpect(status().isOk());

        verify(comandaUseCase).listOpenComandas(null, "LOJA-01", 2, 10);
    }

    /**
     * PDV-C007 — o nome do cliente é resolvido em UMA consulta ao CRM para a página inteira, não
     * uma por mesa: seria trocar o N+1 de HTTP do cliente por um N+1 de CRM no servidor.
     */
    @Test
    void listOpenComandas_resolvesCustomerNamesInASingleCrmCall() throws Exception {
        Comanda comA = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", 42L, ComandaStatus.ABERTA, List.of(), null,
                "caixa1", Instant.now(), null);
        Comanda comB = Comanda.of(11L, 2L, "LOJA-01", "Mesa 7", 43L, ComandaStatus.ABERTA, List.of(), null,
                "caixa2", Instant.now(), null);
        when(comandaUseCase.listOpenComandas(null, null, 0, 50))
                .thenReturn(new PageResult<>(List.of(comA, comB), 0, 50, 2, 1));
        when(crmUseCase.findCustomerNames(anyCollection())).thenReturn(Map.of(42L, "Ana", 43L, "Bruno"));

        mockMvc.perform(get("/pdv/comandas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerName").value("Ana"))
                .andExpect(jsonPath("$.content[1].customerName").value("Bruno"));

        verify(crmUseCase, times(1)).findCustomerNames(anyCollection());
    }

    /** PDV-C010 — `mode` sai como enum, não como string solta; é o que o cliente gera do OpenAPI. */
    @Test
    void listOpenComandas_serializesModeAsTheEnumValue() throws Exception {
        Comanda comItem = abertaComanda().withAddedItem(ComandaItem.forSession("ESS-MENTA", BigDecimal.ONE,
                new BigDecimal("60.00"), Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00")),
                "Essência Menta", ConsumptionMode.OPEN_ROSH, false, null, null, null));
        when(comandaUseCase.listOpenComandas(null, null, 0, 50))
                .thenReturn(new PageResult<>(List.of(comItem), 0, 50, 1, 1));

        mockMvc.perform(get("/pdv/comandas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].items[0].mode").value("OPEN_ROSH"));
    }

    // ── Remoção de item (PDV-F012) ───────────────────────────────────────────────────────────

    @Test
    void removeItem_returns_200_withTheUpdatedComanda() throws Exception {
        when(comandaUseCase.removeItem(eq(10L), eq(7L), anyString(), isNull())).thenReturn(abertaComanda());

        mockMvc.perform(delete("/pdv/comandas/10/items/7").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("ABERTA"));

        verify(comandaUseCase).removeItem(10L, 7L, "caixa1", null);
    }

    /** PDV-C036 — o motivo da desistência vai por query param (DELETE sem corpo). */
    @Test
    void removeItem_withReason_passesItToTheUseCase() throws Exception {
        when(comandaUseCase.removeItem(eq(10L), eq(7L), anyString(), eq("Cliente foi embora")))
                .thenReturn(abertaComanda());

        mockMvc.perform(delete("/pdv/comandas/10/items/7").param("reason", "Cliente foi embora").principal(AUTH))
                .andExpect(status().isOk());

        verify(comandaUseCase).removeItem(10L, 7L, "caixa1", "Cliente foi embora");
    }

    @Test
    void removeItem_servedSessionWithoutReason_returns_400() throws Exception {
        when(comandaUseCase.removeItem(eq(10L), eq(7L), anyString(), isNull()))
                .thenThrow(new com.cernecommerce.core.domain.exception.pdv.SessionWithdrawalReasonRequiredException(7L));

        mockMvc.perform(delete("/pdv/comandas/10/items/7").principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SESSION_WITHDRAWAL_REASON_REQUIRED"));
    }

    @Test
    void removeItem_itemNotInThisComanda_returns_404() throws Exception {
        when(comandaUseCase.removeItem(eq(10L), eq(999L), anyString(), isNull()))
                .thenThrow(new ComandaItemNotFoundException(999L, 10L));

        mockMvc.perform(delete("/pdv/comandas/10/items/999").principal(AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_ITEM_NOT_FOUND"));
    }

    @Test
    void removeItem_withAChargedSaborExtraHangingOnIt_returns_409() throws Exception {
        when(comandaUseCase.removeItem(eq(10L), eq(1L), anyString(), isNull()))
                .thenThrow(new LinkedItemIsChargedException(1L, List.of(3L)));

        mockMvc.perform(delete("/pdv/comandas/10/items/1").principal(AUTH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LINKED_ITEM_IS_CHARGED"));
    }

    // ── Desconto e taxa no fechamento (PDV-F014 / PDV-F015) ──────────────────────────────────

    /**
     * PDV-C016 — desconto maior que a conta sai como <b>409 com código próprio</b>, no lugar do 400
     * genérico ({@code BAD_REQUEST}) que {@code IllegalArgumentException} produzia.
     */
    @Test
    void closeComanda_discountGreaterThanTheBill_returns_409_withItsOwnErrorCode() throws Exception {
        when(comandaUseCase.closeComanda(eq(10L), any(), any(), anyBoolean(), any(), anyString()))
                .thenThrow(new DiscountExceedsBillException(10L, new BigDecimal("150.00"),
                        new BigDecimal("100.00")));

        mockMvc.perform(post("/pdv/comandas/10/close")
                        .principal(AUTH_DISCOUNT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":100.00}],"
                                + "\"discountAmount\":150.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DISCOUNT_EXCEEDS_BILL"));
    }

    /** PDV-C034 — a mesa usa a mesma conversão do balcão: GATEWAY_PIX não passa do controller. */
    @Test
    void closeComanda_gatewayPix_returns_400_INVALID_PAYMENT_METHOD() throws Exception {
        mockMvc.perform(post("/pdv/comandas/10/close")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"GATEWAY_PIX\",\"amount\":90.00}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAYMENT_METHOD"));

        verify(comandaUseCase, never()).closeComanda(any(), any(), any(), anyBoolean(), any(), anyString());
    }

    @Test
    void closeComanda_discountWithoutAuthority_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/10/close")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":90.00}],"
                                + "\"discountAmount\":10.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_DISCOUNT_NOT_ALLOWED"));

        verify(comandaUseCase, never()).closeComanda(any(), any(), any(), anyBoolean(), any(), anyString());
    }

    /** Fechamento comum não pode exigir a permissão — seria 403 em toda mesa do salão. */
    @Test
    void closeComanda_withoutDiscount_doesNotRequireTheAuthority() throws Exception {
        when(comandaUseCase.closeComanda(eq(10L), any(), any(), anyBoolean(), any(), anyString()))
                .thenReturn(concludedMesaOrder());

        mockMvc.perform(post("/pdv/comandas/10/close")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":25.00}]}"))
                .andExpect(status().isOk());
    }

    /** Desconto zero é um no-op: cobrar permissão por ele só produziria 403 inexplicável. */
    @Test
    void closeComanda_zeroDiscount_doesNotRequireTheAuthority() throws Exception {
        when(comandaUseCase.closeComanda(eq(10L), any(), any(), anyBoolean(), any(), anyString()))
                .thenReturn(concludedMesaOrder());

        mockMvc.perform(post("/pdv/comandas/10/close")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":25.00}],"
                                + "\"discountAmount\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void closeComanda_discountWithAuthority_passesItThrough() throws Exception {
        when(comandaUseCase.closeComanda(eq(10L), any(), any(), anyBoolean(), any(), anyString()))
                .thenReturn(concludedMesaOrder());

        mockMvc.perform(post("/pdv/comandas/10/close")
                        .principal(AUTH_DISCOUNT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":90.00}],"
                                + "\"discountAmount\":10.00}"))
                .andExpect(status().isOk());

        verify(comandaUseCase).closeComanda(eq(10L), any(), eq(new BigDecimal("10.00")), eq(true),
                any(), anyString());
    }

    /** PDV-F015 — omitir applyServiceFee significa SIM: a taxa é o padrão do salão. */
    @Test
    void closeComanda_omittingApplyServiceFee_appliesTheFee() throws Exception {
        when(comandaUseCase.closeComanda(eq(10L), any(), any(), anyBoolean(), any(), anyString()))
                .thenReturn(concludedMesaOrder());

        mockMvc.perform(post("/pdv/comandas/10/close")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":25.00}]}"))
                .andExpect(status().isOk());

        verify(comandaUseCase).closeComanda(eq(10L), any(), isNull(), eq(true), any(), anyString());
    }

    @Test
    void closeComanda_applyServiceFeeFalse_isTheCustomerRefusing() throws Exception {
        when(comandaUseCase.closeComanda(eq(10L), any(), any(), anyBoolean(), any(), anyString()))
                .thenReturn(concludedMesaOrder());

        mockMvc.perform(post("/pdv/comandas/10/close")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":25.00}],"
                                + "\"applyServiceFee\":false}"))
                .andExpect(status().isOk());

        verify(comandaUseCase).closeComanda(eq(10L), any(), isNull(), eq(false), any(), anyString());
    }

    @Test
    void getServiceFee_returns_200_withThePercent() throws Exception {
        when(comandaUseCase.getServiceFeePercent()).thenReturn(new BigDecimal("10"));

        mockMvc.perform(get("/pdv/comandas/service-fee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percent").value(10));
    }

    /** A resposta expõe os dois números: o que a loja vendeu e o que o cliente pagou. */
    @Test
    void closeComanda_responseCarriesServiceFeeAndTotalPayable() throws Exception {
        when(comandaUseCase.closeComanda(eq(10L), any(), any(), anyBoolean(), any(), anyString()))
                .thenReturn(concludedMesaOrder().withServiceFeeOf(new BigDecimal("10")));

        mockMvc.perform(post("/pdv/comandas/10/close")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":27.50}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netAmount").value(25.00))
                .andExpect(jsonPath("$.serviceFeeAmount").value(2.50))
                .andExpect(jsonPath("$.totalPayable").value(27.50));
    }

    /**
     * PDV-C014 — <b>o payload de auditoria não pode derrubar a requisição que ele apenas
     * descreve.</b> {@code Map.of} lança {@code NullPointerException} em valor nulo, e um
     * {@code orderId} nulo o alcançava: o fechamento respondia <b>500</b> no lugar de 200, e o
     * cliente perdia o pedido por causa da linha que só serve para registrá-lo. O helper omite o
     * campo nulo em vez de estourar.
     */
    @Test
    void closeComanda_whenAnAuditFieldIsNull_stillReturns_200() throws Exception {
        Order semId = concludedMesaOrder();
        assertThat(semId.id()).as("o cenário só vale com orderId nulo").isNull();
        when(comandaUseCase.closeComanda(eq(10L), any(), any(), anyBoolean(), any(), anyString()))
                .thenReturn(semId);

        mockMvc.perform(post("/pdv/comandas/10/close")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":25.00}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("000001000"));
    }

    private static Order concludedMesaOrder() {
        return Order.openMesa(1L, "LOJA-01", null, 10L, "Mesa 4", List.of(
                        OrderItem.of(1L, "ESS-MENTA", BigDecimal.ONE, new BigDecimal("25.00"),
                                new BigDecimal("10.00"), BigDecimal.ZERO, null, "Essência Menta")))
                .concluded("000001000", null, Instant.now());
    }

    @Test
    void closeComanda_returns_200_withConcludedOrder() throws Exception {
        Order order = Order.openBalcao(1L, "LOJA-01", null, List.of(
                        OrderItem.of(1L, "ESS-MENTA", BigDecimal.ONE, new BigDecimal("25.00"),
                                new BigDecimal("10.00"), BigDecimal.ZERO, null, "Essência Menta")))
                .concluded("000001000", null, Instant.now());
        when(comandaUseCase.closeComanda(eq(10L), any(), any(), anyBoolean(), any(), anyString())).thenReturn(order);

        mockMvc.perform(post("/pdv/comandas/10/close")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":25.00}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));
    }

    @Test
    void cancelComanda_returns_200() throws Exception {
        Comanda cancelada = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.CANCELADA, List.of(),
                null, "caixa1", Instant.now(), Instant.now());
        when(comandaUseCase.cancelComanda(eq(10L), anyString(), isNull())).thenReturn(cancelada);

        mockMvc.perform(post("/pdv/comandas/10/cancel").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    /** PDV-F029 — o motivo do cancelamento é opcional e vai para o histórico da mesa. */
    @Test
    void cancelComanda_withReason_passesItToTheService() throws Exception {
        Comanda cancelada = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.CANCELADA, List.of(),
                null, "caixa1", Instant.now(), Instant.now());
        when(comandaUseCase.cancelComanda(10L, "caixa1", "Cliente desistiu")).thenReturn(cancelada);

        mockMvc.perform(post("/pdv/comandas/10/cancel").principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Cliente desistiu\"}"))
                .andExpect(status().isOk());

        verify(comandaUseCase).cancelComanda(10L, "caixa1", "Cliente desistiu");
    }
    // ── Cortesia: a permissão que o controller guarda (PDV-F010) ─────────────────────────────

    @Test
    void addItem_courtesyWithoutAuthority_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SESS-UVA\",\"quantity\":1,\"mode\":\"SABOR_EXTRA\","
                                + "\"courtesy\":true,\"linkedItemId\":7}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COURTESY_NOT_ALLOWED"));

        // Recusa ANTES do service: nada pode ter sido debitado do estoque.
        verify(comandaUseCase, never()).addItem(any(), any(), any(), any(), anyBoolean(), any(), any(), any(),
                any());
    }

    /** {@code TROCA} é cortesia por definição — não depende do cliente ter marcado o campo. */
    @Test
    void addItem_trocaWithoutAuthority_returns_403_evenWithoutTheCourtesyFlag() throws Exception {
        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SESS-UVA\",\"quantity\":1,\"mode\":\"TROCA\","
                                + "\"linkedItemId\":7}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COURTESY_NOT_ALLOWED"));
    }

    @Test
    void addItem_courtesyWithAuthority_returns_201_andForwardsTheSessionFields() throws Exception {
        when(comandaUseCase.addItem(eq(10L), eq("SESS-UVA"), any(), eq(ConsumptionMode.SABOR_EXTRA),
                eq(true), eq(7L), any(), any(), anyString())).thenReturn(abertaComanda());

        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH_COURTESY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SESS-UVA\",\"quantity\":1,\"mode\":\"SABOR_EXTRA\","
                                + "\"courtesy\":true,\"linkedItemId\":7}"))
                .andExpect(status().isCreated());

        verify(comandaUseCase).addItem(eq(10L), eq("SESS-UVA"), any(), eq(ConsumptionMode.SABOR_EXTRA),
                eq(true), eq(7L), isNull(), isNull(), eq("gerente"));
    }

    /** Item comum continua passando sem a permissão — o gate é só da linha a zero. */
    @Test
    void addItem_withoutCourtesy_doesNotRequireTheAuthority() throws Exception {
        when(comandaUseCase.addItem(eq(10L), eq("ESS-MENTA"), any(), any(), eq(false), any(), any(), any(),
                anyString()))
                .thenReturn(abertaComanda());

        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ESS-MENTA\",\"quantity\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    void openComanda_withCustomer_resolvesTheNameFromCrm() throws Exception {
        Comanda comCliente = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", 42L, ComandaStatus.ABERTA,
                List.of(), null, "caixa1", Instant.now(), null);
        when(comandaUseCase.openComanda(eq(1L), eq("Mesa 4"), eq(42L), anyString())).thenReturn(comCliente);
        when(crmUseCase.findCustomerById(42L)).thenReturn(cliente(42L));
        when(crmUseCase.findCustomerNames(anyCollection())).thenReturn(Map.of(42L, "Ana"));

        mockMvc.perform(post("/pdv/comandas?sessionId=1")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableOrCustomerLabel\":\"Mesa 4\",\"customerId\":42}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(42))
                .andExpect(jsonPath("$.customerName").value("Ana"));
    }

    // ── PDV-F039 — a mesa com cliente nasce com o nome dele ──────────────────────────────────

    @Test
    void openComanda_withCustomerAndNoLabel_usesTheCustomerName() throws Exception {
        Comanda daAna = Comanda.of(10L, 1L, "LOJA-01", "Ana", 42L, ComandaStatus.ABERTA,
                List.of(), null, "caixa1", Instant.now(), null);
        when(crmUseCase.findCustomerById(42L)).thenReturn(cliente(42L));
        when(comandaUseCase.openComanda(eq(1L), eq("Ana"), eq(42L), anyString())).thenReturn(daAna);

        mockMvc.perform(post("/pdv/comandas?sessionId=1")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":42}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableOrCustomerLabel").value("Ana"));
    }

    @Test
    void openComanda_withCustomerAndLabel_keepsTheTypedLabel() throws Exception {
        when(crmUseCase.findCustomerById(42L)).thenReturn(cliente(42L));
        when(comandaUseCase.openComanda(eq(1L), eq("Mesa 4"), eq(42L), anyString())).thenReturn(abertaComanda());

        mockMvc.perform(post("/pdv/comandas?sessionId=1")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableOrCustomerLabel\":\"Mesa 4\",\"customerId\":42}"))
                .andExpect(status().isCreated());

        verify(comandaUseCase).openComanda(eq(1L), eq("Mesa 4"), eq(42L), anyString());
    }

    @Test
    void openComanda_withLeadAndNoLabel_usesTheLeadName() throws Exception {
        when(crmUseCase.resolveLead(eq("Ana"), any(), any(), any(), eq("Mesa")))
                .thenReturn(new LeadResolution(cliente(42L), true));
        when(comandaUseCase.openComanda(eq(1L), eq("Ana"), eq(42L), anyString())).thenReturn(abertaComanda());

        mockMvc.perform(post("/pdv/comandas?sessionId=1")
                        .principal(AUTH_LEAD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lead\":{\"nome\":\"Ana\",\"contato\":\"(83) 99999-0000\"}}"))
                .andExpect(status().isCreated());

        verify(comandaUseCase).openComanda(eq(1L), eq("Ana"), eq(42L), anyString());
    }

    // ── PDV-F020 — cliente da mesa: validação, lead na abertura e vínculo posterior ──────────

    private static final UsernamePasswordAuthenticationToken AUTH_LEAD =
            new UsernamePasswordAuthenticationToken("atendente", null,
                    List.of(new SimpleGrantedAuthority("CRM_LEAD_CREATE")));

    private static Customer cliente(Long id) {
        return Customer.of(id, "Ana", "83999990000", null, null, "Mesa", Instant.now(), CustomerStage.NOVO_LEAD);
    }

    @Test
    void openComanda_withUnknownCustomer_returns_404_andDoesNotOpen() throws Exception {
        when(crmUseCase.findCustomerById(99L)).thenThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(post("/pdv/comandas?sessionId=1")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableOrCustomerLabel\":\"Mesa 4\",\"customerId\":99}"))
                .andExpect(status().isNotFound());

        verify(comandaUseCase, never()).openComanda(any(), any(), any(), any());
    }

    @Test
    void openComanda_withLead_resolvesCustomerInCrmAndLinksIt() throws Exception {
        when(crmUseCase.resolveLead(eq("Ana"), eq("(83) 99999-0000"), any(), eq("123.456.789-00"), eq("Mesa")))
                .thenReturn(new LeadResolution(cliente(42L), true));
        when(comandaUseCase.openComanda(eq(1L), eq("Mesa 4"), eq(42L), anyString()))
                .thenReturn(Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", 42L, ComandaStatus.ABERTA,
                        List.of(), null, "atendente", Instant.now(), null));

        mockMvc.perform(post("/pdv/comandas?sessionId=1")
                        .principal(AUTH_LEAD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableOrCustomerLabel\":\"Mesa 4\",\"lead\":{\"nome\":\"Ana\","
                                + "\"contato\":\"(83) 99999-0000\",\"cpf\":\"123.456.789-00\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(42));
    }

    /**
     * PDV-C038 — o caixa é conferido ANTES do lead: com o caixa de outro operador (ou fechado), a
     * abertura falha e nenhum cliente é criado no CRM por uma mesa que não existe.
     */
    @Test
    void openComanda_withLeadOnAnotherOperatorsSession_neverCreatesTheLead() throws Exception {
        org.mockito.Mockito.doThrow(new com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException(1L, "atendente"))
                .when(comandaUseCase).requireCanOpenComanda(1L, "atendente");

        mockMvc.perform(post("/pdv/comandas?sessionId=1")
                        .principal(AUTH_LEAD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableOrCustomerLabel\":\"Mesa 4\",\"lead\":{\"nome\":\"Ana\","
                                + "\"contato\":\"83999990000\"}}"))
                .andExpect(status().isForbidden());

        verify(crmUseCase, never()).resolveLead(any(), any(), any(), any(), any());
        verify(comandaUseCase, never()).openComanda(any(), any(), any(), any());
    }

    /** PDV-C038 — a conferência do caixa vem antes da resolução do lead. */
    @Test
    void openComanda_withLead_checksTheSessionBeforeResolvingTheLead() throws Exception {
        when(crmUseCase.resolveLead(eq("Ana"), any(), any(), any(), eq("Mesa")))
                .thenReturn(new LeadResolution(cliente(42L), true));
        when(comandaUseCase.openComanda(eq(1L), eq("Mesa 4"), eq(42L), anyString()))
                .thenReturn(Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", 42L, ComandaStatus.ABERTA,
                        List.of(), null, "atendente", Instant.now(), null));

        mockMvc.perform(post("/pdv/comandas?sessionId=1")
                        .principal(AUTH_LEAD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableOrCustomerLabel\":\"Mesa 4\",\"lead\":{\"nome\":\"Ana\","
                                + "\"contato\":\"83999990000\"}}"))
                .andExpect(status().isCreated());

        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(comandaUseCase, crmUseCase);
        ordem.verify(comandaUseCase).requireCanOpenComanda(1L, "atendente");
        ordem.verify(crmUseCase).resolveLead(eq("Ana"), any(), any(), any(), eq("Mesa"));
    }

    @Test
    void openComanda_withLeadButWithoutLeadAuthority_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas?sessionId=1")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableOrCustomerLabel\":\"Mesa 4\",\"lead\":{\"nome\":\"Ana\","
                                + "\"contato\":\"83999990000\"}}"))
                .andExpect(status().isForbidden());

        verify(crmUseCase, never()).resolveLead(any(), any(), any(), any(), any());
        verify(comandaUseCase, never()).openComanda(any(), any(), any(), any());
    }

    @Test
    void linkCustomer_withLead_linksResolvedCustomer() throws Exception {
        when(crmUseCase.resolveLead(eq("Ana"), eq("83999990000"), any(), any(), eq("Mesa")))
                .thenReturn(new LeadResolution(cliente(42L), false));
        when(comandaUseCase.linkCustomer(eq(10L), eq(42L), anyString()))
                .thenReturn(Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", 42L, ComandaStatus.ABERTA,
                        List.of(), null, "caixa1", Instant.now(), null));

        mockMvc.perform(patch("/pdv/comandas/10/customer")
                        .principal(AUTH_LEAD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lead\":{\"nome\":\"Ana\",\"contato\":\"83999990000\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(42));
    }

    @Test
    void linkCustomer_withEmptyBody_unlinks() throws Exception {
        when(comandaUseCase.linkCustomer(eq(10L), isNull(), anyString())).thenReturn(abertaComanda());

        mockMvc.perform(patch("/pdv/comandas/10/customer")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").doesNotExist());
    }

    // ── PDV-F011 — acréscimo e registro do setup ─────────────────────────────────────────────

    /** Simetria de COURTESY_NOT_ALLOWED: subir o preço à mão tem dono, como zerá-lo tem. */
    @Test
    void addItem_surchargeWithoutAuthority_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SESS-BLUE\",\"quantity\":1,\"mode\":\"OPEN_ROSH\","
                                + "\"surchargeAmount\":15.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SURCHARGE_NOT_ALLOWED"));

        // Recusa ANTES do service: nada pode ter sido debitado do estoque.
        verify(comandaUseCase, never()).addItem(any(), any(), any(), any(), anyBoolean(), any(), any(),
                any(), any());
    }

    @Test
    void addItem_surchargeWithAuthority_returns_201_andForwardsBothFields() throws Exception {
        when(comandaUseCase.addItem(eq(10L), eq("SESS-BLUE"), any(), eq(ConsumptionMode.OPEN_ROSH),
                eq(false), any(), eq("Pinça P-02"), eq(new BigDecimal("15.00")), anyString()))
                .thenReturn(abertaComanda());

        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH_SURCHARGE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SESS-BLUE\",\"quantity\":1,\"mode\":\"OPEN_ROSH\","
                                + "\"notes\":\"Pinça P-02\",\"surchargeAmount\":15.00}"))
                .andExpect(status().isCreated());

        verify(comandaUseCase).addItem(eq(10L), eq("SESS-BLUE"), any(), eq(ConsumptionMode.OPEN_ROSH),
                eq(false), any(), eq("Pinça P-02"), eq(new BigDecimal("15.00")), eq("gerente"));
    }

    /**
     * {@code notes} sozinho não exige permissão nenhuma: registrar qual pinça saiu não é lançamento
     * financeiro. É exatamente por isso que ele existe em vez de a pinça virar linha de cortesia.
     */
    @Test
    void addItem_notesAloneDoesNotRequireAnyAuthority() throws Exception {
        when(comandaUseCase.addItem(eq(10L), eq("ESS-MENTA"), any(), any(), eq(false), any(),
                eq("Narguilé grande · Pinça P-02"), any(), anyString())).thenReturn(abertaComanda());

        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ESS-MENTA\",\"quantity\":1,"
                                + "\"notes\":\"Narguilé grande · Pinça P-02\"}"))
                .andExpect(status().isCreated());
    }

    /**
     * Acréscimo negativo é 400 e não 403, mesmo sem a permissão: o problema é o número, e um 403
     * mandaria o operador procurar permissão quando o que falta é corrigir o valor.
     */
    @Test
    void addItem_negativeSurchargeIsABadRequestNotAForbidden() throws Exception {
        when(comandaUseCase.addItem(eq(10L), eq("SESS-BLUE"), any(), eq(ConsumptionMode.OPEN_ROSH),
                eq(false), any(), any(), eq(new BigDecimal("-5.00")), anyString()))
                .thenThrow(new SurchargeInvalidException(new BigDecimal("-5.00")));

        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SESS-BLUE\",\"quantity\":1,\"mode\":\"OPEN_ROSH\","
                                + "\"surchargeAmount\":-5.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SURCHARGE_INVALID"));
    }

    /** Zero é no-op: não aciona a permissão, e a linha passa como qualquer outra. */
    @Test
    void addItem_zeroSurchargeDoesNotRequireTheAuthority() throws Exception {
        when(comandaUseCase.addItem(eq(10L), eq("ESS-MENTA"), any(), any(), eq(false), any(), any(),
                any(), anyString())).thenReturn(abertaComanda());

        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ESS-MENTA\",\"quantity\":1,\"surchargeAmount\":0}"))
                .andExpect(status().isCreated());
    }

    /** Os dois campos voltam na resposta — sem isso o histórico da mesa não mostra o que foi lançado. */
    @Test
    void addItem_echoesNotesAndSurchargeInTheResponseBody() throws Exception {
        ComandaItem comSetup = ComandaItem.of(88L, "SESS-BLUE", BigDecimal.ONE, new BigDecimal("75.00"),
                new BigDecimal("12.00"), "Sessão de narguilé", Instant.now(), ConsumptionMode.OPEN_ROSH,
                false, null, "Pinça P-02", new BigDecimal("15.00"));
        when(comandaUseCase.addItem(any(), any(), any(), any(), anyBoolean(), any(), any(), any(),
                anyString())).thenReturn(abertaComanda().withAddedItem(comSetup));

        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH_SURCHARGE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SESS-BLUE\",\"quantity\":1,\"mode\":\"OPEN_ROSH\","
                                + "\"notes\":\"Pinça P-02\",\"surchargeAmount\":15.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].notes").value("Pinça P-02"))
                .andExpect(jsonPath("$.items[0].surchargeAmount").value(15.00))
                .andExpect(jsonPath("$.items[0].unitPrice").value(75.00));
    }

    // ── Transferir e juntar mesas (PDV-F016) ─────────────────────────────────────────────────

    @Test
    void renameComanda_returns_200() throws Exception {
        when(comandaUseCase.renameComanda(eq(10L), eq("Mesa 7"), anyString()))
                .thenReturn(abertaComanda());

        mockMvc.perform(patch("/pdv/comandas/10")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableOrCustomerLabel\":\"Mesa 7\"}"))
                .andExpect(status().isOk());

        verify(comandaUseCase).renameComanda(eq(10L), eq("Mesa 7"), anyString());
    }

    @Test
    void mergeComanda_returns_200_withTheTargetComanda() throws Exception {
        when(comandaUseCase.mergeComanda(eq(10L), eq(20L), anyString())).thenReturn(abertaComanda());

        mockMvc.perform(post("/pdv/comandas/10/merge-into/20").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        verify(comandaUseCase).mergeComanda(eq(10L), eq(20L), anyString());
    }

    @Test
    void mergeComanda_whenSourceWasPartiallyCharged_returns_409() throws Exception {
        when(comandaUseCase.mergeComanda(eq(10L), eq(20L), anyString()))
                .thenThrow(new ComandaPartiallyClosedException(10L));

        mockMvc.perform(post("/pdv/comandas/10/merge-into/20").principal(AUTH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_PARTIALLY_CLOSED"));
    }

    @Test
    void mergeComanda_acrossWarehouses_returns_409() throws Exception {
        when(comandaUseCase.mergeComanda(eq(10L), eq(20L), anyString()))
                .thenThrow(new ComandaMergeNotAllowedException("depósitos diferentes"));

        mockMvc.perform(post("/pdv/comandas/10/merge-into/20").principal(AUTH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_MERGE_NOT_ALLOWED"));
    }

    // ── Conta dividida (PDV-F017) ────────────────────────────────────────────────────────────

    @Test
    void closeComanda_forwardsTheSelectedItemIds() throws Exception {
        when(comandaUseCase.closeComanda(eq(10L), any(), any(), anyBoolean(), any(), anyString()))
                .thenReturn(concludedMesaOrder());

        mockMvc.perform(post("/pdv/comandas/10/close")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":30.00}],"
                                + "\"itemIds\":[3,5]}"))
                .andExpect(status().isOk());

        verify(comandaUseCase).closeComanda(eq(10L), any(), isNull(), eq(true),
                eq(List.of(3L, 5L)), anyString());
    }

    @Test
    void closeComanda_withAnItemIdThatIsNotOpen_returns_400() throws Exception {
        when(comandaUseCase.closeComanda(eq(10L), any(), any(), anyBoolean(), any(), anyString()))
                .thenThrow(new ItemNotOpenInComandaException(10L, List.of(42L)));

        mockMvc.perform(post("/pdv/comandas/10/close")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":30.00}],"
                                + "\"itemIds\":[42]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ITEM_NOT_OPEN_IN_COMANDA"));
    }

    @Test
    void closeComanda_selectionThatSplitsLinkedLines_returns_409() throws Exception {
        when(comandaUseCase.closeComanda(eq(10L), any(), any(), anyBoolean(), any(), anyString()))
                .thenThrow(new LinkedItemMustCloseTogetherException(10L, List.of(2L)));

        mockMvc.perform(post("/pdv/comandas/10/close")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":60.00}],"
                                + "\"itemIds\":[1]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LINKED_ITEM_MUST_CLOSE_TOGETHER"));
    }
}
