package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.PromotionChangedException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.PromotionChannel;
import com.cernecommerce.core.domain.model.estoque.QuantityPromotion;
import com.cernecommerce.core.domain.model.estoque.QuantityPromotionEngine;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.PromotionCheck;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import com.cernecommerce.core.ports.out.estoque.QuantityPromotionRepository;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.CashMovementRepository;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Promoção por quantidade na venda do balcão: "seda a R$ 3,50, leve 4 por R$ 10". */
@ExtendWith(MockitoExtension.class)
class PdvServicePromocaoQuantidadeTest {

    private static final Pricing SEDA = Pricing.of(new BigDecimal("1.00"), null, new BigDecimal("3.50"));
    private static final QuantityPromotion LEVE_4 = new QuantityPromotion(7L, "Seda — leve 4 por R$ 10", null, 4,
            new BigDecimal("10.00"), List.of("SEDA"), Set.of(PromotionChannel.PDV), true, null, null, null, null,
            null, null);

    @Mock CashRegisterRepository cashRegisterRepository;
    @Mock CashMovementRepository cashMovementRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock ComandaRepository comandaRepository;
    @Mock ReceivableUseCase receivableUseCase;
    @Mock QuantityPromotionRepository promotionRepository;

    private PdvService pdvService;

    @BeforeEach
    void setUp() {
        // Teto de 10%: a promoção (28,6% aqui) só passa porque não conta no teto.
        pdvService = new PdvService(cashRegisterRepository, cashMovementRepository, orderRepository,
                orderPaymentRepository, estoqueUseCase, cashbackUseCase, comandaRepository, new BigDecimal("10"),
                receivableUseCase);
        // Serviço real de promoção sobre o repositório falso: o mesmo caminho da produção.
        pdvService.setQuantityPromotions(new QuantityPromotionService(promotionRepository, estoqueUseCase, null,
                Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC)));
        lenient().when(promotionRepository.findAllActive()).thenReturn(List.of(LEVE_4));
        lenient().when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(CashRegisterSession.of(1L, "caixa1",
                Instant.now(), BigDecimal.TEN, "LOJA-01", null, null, null, null, null,
                CashRegisterSession.Status.OPEN)));
        lenient().when(orderRepository.nextOrderNumber()).thenReturn("000001000");
        lenient().when(orderRepository.save(any())).thenAnswer(inv -> {
            Order arg = inv.getArgument(0);
            return arg.id() != null ? arg : Order.of(100L, arg.orderNumber(), arg.channel(), arg.status(),
                    arg.customerId(), arg.sessionId(), arg.warehouseCode(), arg.items(), arg.grossAmount(),
                    arg.discountAmount(), arg.cashbackRedeemed(), arg.netAmount(), arg.changeAmount(),
                    arg.cancelReason(), arg.createdAt(), arg.paidAt(), arg.concludedAt(), arg.cancelledAt(),
                    arg.refundedAt(), arg.reservedAt(), arg.separatedAt(), arg.shippedAt(), arg.deliveredAt(),
                    arg.version(), arg.comandaId(), arg.tableLabel(), arg.serviceFeeAmount(), arg.delivery());
        });
        lenient().when(estoqueUseCase.resolveSaleInfo("SEDA"))
                .thenReturn(new EstoqueUseCase.CatalogSaleInfo("Seda", SEDA));
    }

    private static List<PaymentCommand> dinheiro(String amount) {
        return List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal(amount), null));
    }

    private Order vender(List<SaleItemCommand> items, String pago, PromotionCheck check) {
        return pdvService.registerSaleIdempotent(1L, null, items, dinheiro(pago), "caixa1", false, null, null, null,
                check).order();
    }

    @Test
    void quatroSedasFechamPorDezSemPermissaoDeDescontoENemTeto() {
        Order order = vender(List.of(new SaleItemCommand("SEDA", new BigDecimal("4"), null, null)), "10.00",
                PromotionCheck.strict(new BigDecimal("4.00")));

        assertThat(order.grossAmount()).isEqualByComparingTo("14.00");
        assertThat(order.netAmount()).isEqualByComparingTo("10.00");
        OrderItem linha = order.items().get(0);
        assertThat(linha.promotionDiscountAmount()).isEqualByComparingTo("4.00");
        assertThat(linha.discountAmount()).isEqualByComparingTo("4.00");
        assertThat(linha.promotionId()).isEqualTo(7L);
        assertThat(linha.promotionName()).isEqualTo("Seda — leve 4 por R$ 10");
        assertThat(linha.costPrice()).isEqualByComparingTo("1.00");
        // Estoque: as 4 unidades saem do SKU real da seda.
        verify(estoqueUseCase).adjustStock(eq("SEDA"), eq("LOJA-01"), eq(MovementType.SAIDA),
                eq(new BigDecimal("4")), any(), eq("caixa1"));
    }

    @Test
    void semExpectedComPromocaoAplicavelResponde409ComODescontoAtual() {
        assertThatThrownBy(() -> vender(List.of(new SaleItemCommand("SEDA", new BigDecimal("4"), null, null)),
                "14.00", PromotionCheck.strict(null)))
                .isInstanceOfSatisfying(PromotionChangedException.class,
                        e -> assertThat(e.getCurrentPromotionDiscount()).isEqualByComparingTo("4.00"));
        verify(orderRepository, never()).save(any());
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void expectedSemPromocaoNoServidorTambemE409() {
        assertThatThrownBy(() -> vender(List.of(new SaleItemCommand("SEDA", new BigDecimal("3"), null, null)),
                "10.50", PromotionCheck.strict(new BigDecimal("4.00"))))
                .isInstanceOfSatisfying(PromotionChangedException.class,
                        e -> assertThat(e.getCurrentPromotionDiscount()).isEqualByComparingTo("0"));
    }

    @Test
    void cincoSedasUmGrupoEAQuintaNormal() {
        Order order = vender(List.of(new SaleItemCommand("SEDA", new BigDecimal("5"), null, null)), "13.50",
                PromotionCheck.strict(new BigDecimal("4.00")));
        assertThat(order.netAmount()).isEqualByComparingTo("13.50");
    }

    @Test
    void unidadesEmKitNaoContam() {
        // 6 sedas, 2 no kit montável (com o desconto do kit na linha): sobram 4 livres, um grupo.
        SaleItemCommand linha = new SaleItemCommand("SEDA", new BigDecimal("6"), new BigDecimal("0.70"), null, 2,
                new BigDecimal("4.00"));
        Order order = pdvService.registerSaleIdempotent(1L, null, List.of(linha), dinheiro("16.30"), "caixa1",
                false, null, null, null, PromotionCheck.strict(new BigDecimal("4.00"))).order();
        assertThat(order.items().get(0).promotionDiscountAmount()).isEqualByComparingTo("4.00");
        assertThat(order.items().get(0).discountAmount()).isEqualByComparingTo("4.70");
        assertThat(order.netAmount()).isEqualByComparingTo("16.30");

        // Com 3 em kit sobram 3 livres: não fecha.
        SaleItemCommand tresNoKit = new SaleItemCommand("SEDA", new BigDecimal("6"), null, null, 3, null);
        assertThatThrownBy(() -> pdvService.registerSaleIdempotent(1L, null, List.of(tresNoKit), dinheiro("21.00"),
                "caixa1", false, null, null, null, PromotionCheck.strict(new BigDecimal("4.00"))))
                .isInstanceOf(PromotionChangedException.class);
    }

    @Test
    void duasLinhasDoMesmoSkuRepartemODesconto() {
        Order order = vender(List.of(new SaleItemCommand("SEDA", new BigDecimal("2"), null, null),
                new SaleItemCommand("SEDA", new BigDecimal("2"), null, null)), "10.00",
                PromotionCheck.strict(new BigDecimal("4.00")));
        assertThat(order.items()).extracting(OrderItem::promotionDiscountAmount)
                .allSatisfy(d -> assertThat(d).isEqualByComparingTo("2.00"));
        verify(estoqueUseCase, times(2)).adjustStock(eq("SEDA"), any(), any(), any(), any(), any());
    }

    @Test
    void offlineDivergenteAceitaOValorDoCaixa() {
        // A regra venceu depois da venda offline: o servidor acharia 0, mas a venda já foi cobrada com 4,00.
        when(promotionRepository.findAllActive()).thenReturn(List.of());
        SaleItemCommand linha = new SaleItemCommand("SEDA", new BigDecimal("4"), null, null, null,
                new BigDecimal("4.00"));
        Order order = pdvService.registerSaleIdempotent(1L, null, List.of(linha), dinheiro("10.00"), "caixa1",
                false, null, "k-1", Instant.parse("2026-11-01T12:00:00Z"),
                PromotionCheck.lenient(new BigDecimal("4.00"))).order();
        assertThat(order.netAmount()).isEqualByComparingTo("10.00");
        assertThat(order.items().get(0).promotionDiscountAmount()).isEqualByComparingTo("4.00");
    }

    @Test
    void semMotorLigadoVendeComoAntes() {
        pdvService.setQuantityPromotions(null);
        Order order = vender(List.of(new SaleItemCommand("SEDA", new BigDecimal("4"), null, null)), "14.00",
                PromotionCheck.strict(null));
        assertThat(order.netAmount()).isEqualByComparingTo("14.00");
    }
}
