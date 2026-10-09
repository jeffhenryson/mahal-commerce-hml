package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PDV-F041 — a central de cigarros: famílias com a cadeia carteira → maço → solto. */
class PdvCigarrosControllerTest {

    private MockMvc mockMvc;
    private EstoqueUseCase estoqueUseCase;

    @BeforeEach
    void setup() {
        estoqueUseCase = mock(EstoqueUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PdvCigarrosController(estoqueUseCase))
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
}
