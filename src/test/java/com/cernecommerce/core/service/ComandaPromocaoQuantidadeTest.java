package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.PromotionChannel;
import com.cernecommerce.core.domain.model.estoque.QuantityPromotion;
import com.cernecommerce.core.domain.model.estoque.QuantityPromotionEngine;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.KitBuilderUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.QuantityPromotionUseCase;
import com.cernecommerce.core.ports.out.user.UserRepository;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Promoção por quantidade na mesa: recalculada a cada item que entra ou sai. */
@ExtendWith(MockitoExtension.class)
class ComandaPromocaoQuantidadeTest {

    private static final Pricing SEDA = Pricing.of(new BigDecimal("1.00"), null, new BigDecimal("3.50"));
    private static final QuantityPromotion LEVE_4 = new QuantityPromotion(7L, "Seda — leve 4 por R$ 10", null, 4,
            new BigDecimal("10.00"), List.of("SEDA"), Set.of(PromotionChannel.COMANDA), true, null, null, null, null,
            null, null);

    @Mock ComandaRepository comandaRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock OrderRepository orderRepository;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock PdvService pdvService;
    @Mock NotificationUseCase notificationUseCase;
    @Mock UserRepository userRepository;
    @Mock KitBuilderUseCase kitBuilderUseCase;
    @Mock QuantityPromotionUseCase promotions;

    private ComandaService comandaService;

    @BeforeEach
    void setUp() {
        comandaService = new ComandaService(comandaRepository, estoqueUseCase, orderRepository,
                orderPaymentRepository, cashbackUseCase, pdvService, notificationUseCase, userRepository,
                new BigDecimal("10"), kitBuilderUseCase);
        comandaService.setQuantityPromotions(promotions);
        lenient().when(promotions.calculate(eq(PromotionChannel.COMANDA), any(), any(), any())).thenAnswer(inv ->
                QuantityPromotionEngine.apply(inv.getArgument(2), List.of(LEVE_4), inv.getArgument(3)));
        lenient().when(pdvService.requireOpenSession(1L)).thenReturn(CashRegisterSession.of(1L, "caixa1",
                Instant.now(), BigDecimal.TEN, "LOJA-01", null, null, null, null, null,
                CashRegisterSession.Status.OPEN));
        lenient().when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(estoqueUseCase.resolveSaleInfo("SEDA"))
                .thenReturn(new EstoqueUseCase.CatalogSaleInfo("Seda", SEDA));
    }

    private static ComandaItem seda(long id) {
        return ComandaItem.of(id, "SEDA", BigDecimal.ONE, new BigDecimal("3.50"), new BigDecimal("1.00"), "Seda",
                Instant.now(), ConsumptionMode.NORMAL, false, null);
    }

    private static Comanda mesa(ComandaItem... items) {
        Comanda comanda = Comanda.open(1L, "LOJA-01", "Mesa 4", "caixa1");
        for (ComandaItem item : items) {
            comanda = comanda.withAddedItem(item);
        }
        return Comanda.of(10L, comanda.sessionId(), comanda.warehouseCode(), comanda.tableOrCustomerLabel(),
                comanda.customerId(), comanda.status(), comanda.items(), comanda.orderId(), comanda.openedBy(),
                comanda.openedAt(), comanda.closedAt());
    }

    @Test
    void aQuartaSedaAplicaAPromocao() {
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mesa(seda(1), seda(2), seda(3))));

        Comanda updated = comandaService.addItem(10L, "SEDA", BigDecimal.ONE, "caixa1");

        assertThat(updated.items()).hasSize(4);
        assertThat(updated.promotionDiscountTotal()).isEqualByComparingTo("4.00");
        assertThat(updated.runningTotal()).isEqualByComparingTo("10.00");
        assertThat(updated.items()).allSatisfy(i -> {
            assertThat(i.promotionDiscount()).isEqualByComparingTo("1.00");
            assertThat(i.promotionId()).isEqualTo(7L);
        });
    }

    @Test
    void tirarUmaSedaDerrubaAPromocao() {
        ComandaItem[] comPromo = {seda(1).withPromotion(7L, "x", BigDecimal.ONE),
                seda(2).withPromotion(7L, "x", BigDecimal.ONE), seda(3).withPromotion(7L, "x", BigDecimal.ONE),
                seda(4).withPromotion(7L, "x", BigDecimal.ONE)};
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mesa(comPromo)));

        Comanda updated = comandaService.removeItem(10L, 4L, "caixa1");

        assertThat(updated.items()).hasSize(3);
        assertThat(updated.promotionDiscountTotal()).isEqualByComparingTo("0");
        assertThat(updated.runningTotal()).isEqualByComparingTo("10.50");
        assertThat(updated.items()).allSatisfy(i -> assertThat(i.promotionId()).isNull());
    }

    @Test
    void cortesiaELinhaJaCobradaNaoContam() {
        ComandaItem cortesia = ComandaItem.of(5L, "SEDA", BigDecimal.ONE, BigDecimal.ZERO,
                new BigDecimal("1.00"), "Seda", Instant.now(), ConsumptionMode.NORMAL, true, null);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mesa(seda(1), seda(2), cortesia)));

        Comanda updated = comandaService.addItem(10L, "SEDA", BigDecimal.ONE, "caixa1");

        assertThat(updated.promotionDiscountTotal()).isEqualByComparingTo("0");
    }

    @Test
    void semMotorLigadoNadaMuda() {
        comandaService.setQuantityPromotions(null);
        when(comandaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mesa(seda(1), seda(2), seda(3))));
        Comanda updated = comandaService.addItem(10L, "SEDA", BigDecimal.ONE, "caixa1");
        assertThat(updated.runningTotal()).isEqualByComparingTo("14.00");
    }

    @Test
    void aPromocaoEntraNoDescontoDaLinhaQueOFechamentoLeva() {
        ComandaItem linha = seda(1).withPromotion(7L, "Seda", new BigDecimal("1.00"));
        assertThat(linha.lineDiscount()).isEqualByComparingTo("1.00");
        assertThat(linha.netSubtotal()).isEqualByComparingTo("2.50");
        assertThat(linha.withoutPromotion().promotionDiscount()).isEqualByComparingTo("0");
    }
}
