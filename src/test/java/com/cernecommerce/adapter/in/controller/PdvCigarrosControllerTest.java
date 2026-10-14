package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.exception.estoque.NotEnoughClosedPackagingException;
import com.cernecommerce.core.domain.exception.estoque.PackagingNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.NoOpenCashRegisterSessionException;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PDV-F041 — a central de cigarros: famílias com a cadeia carteira → maço → solto. */
class PdvCigarrosControllerTest {

    private MockMvc mockMvc;
    private EstoqueUseCase estoqueUseCase;
    private PdvUseCase pdvUseCase;

    private static final UsernamePasswordAuthenticationToken ATENDENTE =
            new UsernamePasswordAuthenticationToken("atendente", null, List.of());

    @BeforeEach
    void setup() {
        estoqueUseCase = mock(EstoqueUseCase.class);
        pdvUseCase = mock(PdvUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PdvCigarrosController(estoqueUseCase, pdvUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listaAsFamiliasComCadaNivel() throws Exception {
        when(estoqueUseCase.listPackagedFamilies("LOJA-01")).thenReturn(List.of(
                new EstoqueUseCase.PackagedFamily("LM", "LM", List.of(new EstoqueUseCase.PackagedLine(List.of(
                        new EstoqueUseCase.PackagedLevel("LM-AZUL-CART", "azul · carteira", new BigDecimal("110.00"),
                                new BigDecimal("1"), "LM-AZUL-MACO", 10),
                        new EstoqueUseCase.PackagedLevel("LM-AZUL-MACO", "azul · maço", new BigDecimal("12.00"),
                                new BigDecimal("8"), "LM-AZUL-UN", 20),
                        new EstoqueUseCase.PackagedLevel("LM-AZUL-UN", "azul · unidade", new BigDecimal("1.00"),
                                new BigDecimal("15"), null, null)))))));

        mockMvc.perform(get("/pdv/cigarros").param("warehouseCode", "LOJA-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productSku").value("LM"))
                .andExpect(jsonPath("$[0].lines[0].levels[1].sku").value("LM-AZUL-MACO"))
                .andExpect(jsonPath("$[0].lines[0].levels[1].label").value("azul · maço"))
                .andExpect(jsonPath("$[0].lines[0].levels[1].price").value(12.00))
                .andExpect(jsonPath("$[0].lines[0].levels[1].available").value(8))
                .andExpect(jsonPath("$[0].lines[0].levels[1].containsUnits").value(20));
    }

    @Test
    void semDeposito_respondeErroDeParametro() throws Exception {
        mockMvc.perform(get("/pdv/cigarros")).andExpect(status().isBadRequest());
    }

    // ── PDV-F045 — abrir embalagem no balcão ──

    private void givenCaixaAberto() {
        when(pdvUseCase.getCurrentSession("atendente")).thenReturn(CashRegisterSession.of(1L, "atendente",
                Instant.now(), BigDecimal.ZERO, "PRINCIPAL", null, null, null, null, null,
                CashRegisterSession.Status.OPEN));
    }

    @Test
    void abrir_usaODepositoDoCaixa_eDevolveOsDoisNiveis() throws Exception {
        givenCaixaAberto();
        when(estoqueUseCase.openPackagingManually("LM-AZUL-CART", BigDecimal.ONE, "PRINCIPAL", "atendente"))
                .thenReturn(new EstoqueUseCase.OpenedPackaging("LM-AZUL-CART", new BigDecimal("2"), "LM-AZUL-MACO",
                        new BigDecimal("13")));

        mockMvc.perform(post("/pdv/cigarros/abrir").principal(ATENDENTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"LM-AZUL-CART\",\"quantidade\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aberto.sku").value("LM-AZUL-CART"))
                .andExpect(jsonPath("$.aberto.available").value(2))
                .andExpect(jsonPath("$.dentro.sku").value("LM-AZUL-MACO"))
                .andExpect(jsonPath("$.dentro.available").value(13));
    }

    @Test
    void abrir_comODepositoDoCaixaNoCorpo_passa_eComOutro_eRecusado() throws Exception {
        givenCaixaAberto();
        when(estoqueUseCase.openPackagingManually(any(), any(), any(), any()))
                .thenReturn(new EstoqueUseCase.OpenedPackaging("A", BigDecimal.ZERO, "B", BigDecimal.TEN));

        mockMvc.perform(post("/pdv/cigarros/abrir").principal(ATENDENTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"A\",\"quantidade\":1,\"warehouseCode\":\"principal\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/pdv/cigarros/abrir").principal(ATENDENTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"A\",\"quantidade\":1,\"warehouseCode\":\"LOJA-02\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_MISMATCH"));
    }

    @Test
    void abrir_semCaixaAberto_eRecusadoSemMexerNoEstoque() throws Exception {
        when(pdvUseCase.getCurrentSession("atendente")).thenThrow(new NoOpenCashRegisterSessionException("atendente"));

        mockMvc.perform(post("/pdv/cigarros/abrir").principal(ATENDENTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"LM-AZUL-CART\",\"quantidade\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NO_OPEN_SESSION"));
        verify(estoqueUseCase, never()).openPackagingManually(any(), any(), any(), any());
    }

    @Test
    void abrir_skuQueNaoContemNada_404PackagingNotFound() throws Exception {
        givenCaixaAberto();
        when(estoqueUseCase.openPackagingManually(any(), any(), any(), any()))
                .thenThrow(PackagingNotFoundException.containsNothing("LM-AZUL-UN"));

        mockMvc.perform(post("/pdv/cigarros/abrir").principal(ATENDENTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"LM-AZUL-UN\",\"quantidade\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PACKAGING_NOT_FOUND"));
    }

    @Test
    void abrir_semEmbalagemFechada_409InsufficientStock() throws Exception {
        givenCaixaAberto();
        when(estoqueUseCase.openPackagingManually(any(), any(), any(), any()))
                .thenThrow(new NotEnoughClosedPackagingException("LM-AZUL-CART", BigDecimal.ZERO, BigDecimal.ONE));

        mockMvc.perform(post("/pdv/cigarros/abrir").principal(ATENDENTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"LM-AZUL-CART\",\"quantidade\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void abrir_quantidadeZero_eRecusada() throws Exception {
        mockMvc.perform(post("/pdv/cigarros/abrir").principal(ATENDENTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"LM-AZUL-CART\",\"quantidade\":0}"))
                .andExpect(status().isBadRequest());
        verify(estoqueUseCase, never()).openPackagingManually(any(), any(), any(), any());
    }
}
