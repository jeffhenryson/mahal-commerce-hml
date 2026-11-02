package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.DuplicatePromotionNameException;
import com.cernecommerce.core.domain.exception.estoque.PromotionSkuInactiveException;
import com.cernecommerce.core.domain.exception.estoque.PromotionSkuNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.QuantityPromotionNotFoundException;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.PromotionChannel;
import com.cernecommerce.core.domain.model.estoque.QuantityPromotion;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.out.estoque.QuantityPromotionRepository;
import com.cernecommerce.core.ports.out.pedido.PromotionStatsQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuantityPromotionServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-01T12:00:00Z");

    @Mock QuantityPromotionRepository repository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock PromotionStatsQuery statsQuery;

    private QuantityPromotionService service;

    @BeforeEach
    void setUp() {
        service = new QuantityPromotionService(repository, estoqueUseCase, statsQuery,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        lenient().when(repository.save(any())).thenAnswer(inv -> {
            QuantityPromotion p = inv.getArgument(0);
            return p.id() == null ? p.withId(1L) : p;
        });
    }

    private static QuantityPromotion promo(String... skus) {
        return new QuantityPromotion(null, "Seda — leve 4 por R$ 10", null, 4, new BigDecimal("10"), List.of(skus),
                Set.of(PromotionChannel.PDV), true, null, null, null, null, null, null);
    }

    private static Product produto(String sku, boolean active, ProductStatus status) {
        Product p = mock(Product.class);
        lenient().when(p.sku()).thenReturn(sku);
        lenient().when(p.active()).thenReturn(active);
        lenient().when(p.status()).thenReturn(status);
        lenient().when(p.type()).thenReturn(ProductType.SIMPLES);
        lenient().when(p.variants()).thenReturn(List.of());
        lenient().when(p.isSellable(sku)).thenReturn(true);
        return p;
    }

    @Test
    void criaComAuditoria() {
        Product seda = produto("SEDA", true, ProductStatus.ATIVO);
        when(estoqueUseCase.findProductsByAnySku(any())).thenReturn(List.of(seda));
        QuantityPromotion criada = service.create(promo("SEDA"), "dono");
        assertThat(criada.id()).isEqualTo(1L);
        assertThat(criada.createdBy()).isEqualTo("dono");
        assertThat(criada.createdAt()).isEqualTo(AGORA);
    }

    @Test
    void nomeRepetidoE409() {
        when(repository.findByNameIgnoreCase(any())).thenReturn(Optional.of(promo("SEDA").withId(9L)));
        assertThatThrownBy(() -> service.create(promo("SEDA"), "dono"))
                .isInstanceOf(DuplicatePromotionNameException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void skuInexistenteE404() {
        when(estoqueUseCase.findProductsByAnySku(any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.create(promo("NAO-EXISTE"), "dono"))
                .isInstanceOf(PromotionSkuNotFoundException.class);
    }

    @Test
    void skuInativoOuArquivadoE409() {
        Product inativo = produto("SEDA", false, ProductStatus.ATIVO);
        when(estoqueUseCase.findProductsByAnySku(any())).thenReturn(List.of(inativo));
        assertThatThrownBy(() -> service.create(promo("SEDA"), "dono"))
                .isInstanceOf(PromotionSkuInactiveException.class);
        Product arquivado = produto("SEDA", true, ProductStatus.ARQUIVADO);
        when(estoqueUseCase.findProductsByAnySku(any())).thenReturn(List.of(arquivado));
        assertThatThrownBy(() -> service.create(promo("SEDA"), "dono"))
                .isInstanceOf(PromotionSkuInactiveException.class);
    }

    @Test
    void skuPaiQueNaoVendeE409() {
        Product pai = produto("SEDA", true, ProductStatus.ATIVO);
        when(pai.isSellable("SEDA")).thenReturn(false);
        when(estoqueUseCase.findProductsByAnySku(any())).thenReturn(List.of(pai));
        assertThatThrownBy(() -> service.create(promo("SEDA"), "dono"))
                .isInstanceOf(PromotionSkuInactiveException.class);
    }

    @Test
    void atualizarRegraInexistenteE404() {
        when(repository.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(5L, promo("SEDA"), "dono"))
                .isInstanceOf(QuantityPromotionNotFoundException.class);
    }

    @Test
    void vigentesFiltraPorCanalEPeriodo() {
        QuantityPromotion ativa = promo("SEDA").withId(1L);
        QuantityPromotion agendada = new QuantityPromotion(2L, "Agendada", null, 4, BigDecimal.TEN, List.of("SEDA"),
                Set.of(PromotionChannel.PDV), true, Instant.parse("2026-11-02T00:00:00Z"), null, null, null, null, null);
        QuantityPromotion soMesa = new QuantityPromotion(3L, "Mesa", null, 4, BigDecimal.TEN, List.of("SEDA"),
                Set.of(PromotionChannel.COMANDA), true, null, null, null, null, null, null);
        when(repository.findAllActive()).thenReturn(List.of(ativa, agendada, soMesa));
        assertThat(service.listVigentes(PromotionChannel.PDV, AGORA)).extracting(QuantityPromotion::id)
                .containsExactly(1L);
    }

    @Test
    void desativarNaoRevalidaSkus() {
        when(repository.findById(1L)).thenReturn(Optional.of(promo("SEDA").withId(1L)));
        QuantityPromotion salva = service.setActive(1L, false, "dono");
        assertThat(salva.active()).isFalse();
        verify(estoqueUseCase, never()).findProductsByAnySku(any());
    }

    @Test
    void statsCalculaGrupos() {
        when(repository.findById(1L)).thenReturn(Optional.of(promo("SEDA").withId(1L)));
        when(statsQuery.totals(1L, AGORA.minusSeconds(60), AGORA))
                .thenReturn(new PromotionStatsQuery.Totals(3, 12, new BigDecimal("12.00")));
        var stats = service.stats(1L, AGORA.minusSeconds(60), AGORA);
        assertThat(stats.grupos()).isEqualTo(3);
        assertThat(stats.descontoTotal()).isEqualByComparingTo("12.00");
    }
}
