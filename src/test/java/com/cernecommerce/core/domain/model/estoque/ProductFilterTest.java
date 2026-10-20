package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductFilterTest {

    @Test
    void empty_nao_filtra_nada() {
        assertTrue(ProductFilter.EMPTY.isEmpty());
        assertNull(ProductFilter.EMPTY.search());
        assertNull(ProductFilter.EMPTY.category());
        assertNull(ProductFilter.EMPTY.brand());
        assertNull(ProductFilter.EMPTY.active());
    }

    @Test
    void texto_em_branco_vira_nulo_para_nao_filtrar_por_string_vazia() {
        // "?search=" chega como string vazia; filtrar por ela não devolveria nada.
        ProductFilter filter = new ProductFilter("", "   ", "\t", null);
        assertTrue(filter.isEmpty());
    }

    @Test
    void texto_e_normalizado_para_minusculas_e_sem_espacos_nas_pontas() {
        // A comparação é case-insensitive; normalizar aqui evita repetir isso em cada adapter.
        ProductFilter filter = new ProductFilter("  MeNtA  ", " Narguilé ", "ZOMO", null);
        assertEquals("menta", filter.search());
        assertEquals("narguilé", filter.category());
        assertEquals("zomo", filter.brand());
    }

    @Test
    void active_false_e_filtro_de_verdade_e_nao_ausencia() {
        // Boolean e não boolean exatamente por isto: false significa "só os inativos".
        ProductFilter filter = new ProductFilter(null, null, null, false);
        assertFalse(filter.isEmpty());
        assertEquals(false, filter.active());
    }

    @Test
    void um_unico_criterio_ja_torna_o_filtro_nao_vazio() {
        assertFalse(new ProductFilter("menta", null, null, null).isEmpty());
        assertFalse(new ProductFilter(null, "narguile", null, null).isEmpty());
        assertFalse(new ProductFilter(null, null, "zomo", null).isEmpty());
        assertFalse(new ProductFilter(null, null, null, true).isEmpty());
        assertFalse(new ProductFilter(null, null, null, null, null, null, null, true).isEmpty());
    }
}
