package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.InvalidKitSelectionException;
import com.cernecommerce.core.domain.exception.estoque.InvalidKitSelectionException.Reason;
import com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException;
import com.cernecommerce.core.domain.model.estoque.KitChannel;
import com.cernecommerce.core.domain.model.estoque.KitQuote;
import com.cernecommerce.core.domain.model.estoque.KitSelection;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.PromotionChannel;
import com.cernecommerce.core.domain.model.estoque.QuantityPromotion;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.KitBuilderUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.PromotionCheck;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.out.estoque.QuantityPromotionRepository;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kit montável vendido no balcão (PDV-F019 levado ao balcão): o servidor cota o kit, e o desconto do
 * kit fica fora do teto de desconto e da promoção por quantidade — como na comanda.
 */
@ExtendWith(MockitoExtension.class)
class PdvServiceKitBalcaoTest {

    private static final Pricing SEDA = Pricing.of(new BigDecimal("1.00"), null, new BigDecimal("3.50"));
    private static final Pricing ESSENCIA = Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("30.00"));
    private static final QuantityPromotion LEVE_4 = new QuantityPromotion(7L, "Seda — leve 4 por R$ 10", null, 4,
            new BigDecimal("10.00"), List.of("SEDA"), Set.of(PromotionChannel.PDV), true, null, null, null, null,
            null, null);
    private static final KitSelection SELECAO = new KitSelection(5L,
            List.of(new KitSelection.Pick(1L, "ESS"), new KitSelection.Pick(2L, "SEDA")));
    /** Kit de 20%: R$ 33,50 de bruto, R$ 6,70 de desconto rateado (6,00 + 0,70). */
    private static final KitQuote COTACAO = new KitQuote(null, List.of(
            new KitQuote.Line(1L, "Essência", "ESS", "Essência", new BigDecimal("30.00"), new BigDecimal("6.00")),
            new KitQuote.Line(2L, "Seda", "SEDA", "Seda", new BigDecimal("3.50"), new BigDecimal("0.70"))),
            new BigDecimal("33.50"), new BigDecimal("6.70"), new BigDecimal("26.80"));

    @Mock CashRegisterRepository cashRegisterRepository;
    @Mock CashMovementRepository cashMovementRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock ComandaRepository comandaRepository;
    @Mock ReceivableUseCase receivableUseCase;
    @Mock QuantityPromotionRepository promotionRepository;
    @Mock KitBuilderUseCase kitBuilder;

    private PdvService pdvService;

    @BeforeEach
    void setUp() {
        // Teto de 10%: o kit de 20% só passa porque não conta no teto.
        pdvService = new PdvService(cashRegisterRepository, cashMovementRepository, orderRepository,
                orderPaymentRepository, estoqueUseCase, cashbackUseCase, comandaRepository, new BigDecimal("10"),
                receivableUseCase);
        pdvService.setQuantityPromotions(new QuantityPromotionService(promotionRepository, estoqueUseCase, null,
                Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC)));
        pdvService.setKitBuilder(kitBuilder);
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
        lenient().when(estoqueUseCase.resolveSaleInfo("ESS"))
                .thenReturn(new EstoqueUseCase.CatalogSaleInfo("Essência", ESSENCIA));
    }

    private static List<PaymentCommand> dinheiro(String amount) {
        return List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal(amount), null));
    }

    private Order vender(List<SaleItemCommand> items, List<KitSelection> kits, String pago, PromotionCheck check) {
        return pdvService.registerSaleIdempotent(1L, null, items, kits, dinheiro(pago), "caixa1", false, null, null,
                null, check).order();
    }

    @Test
    void kitDe20PorCentoPassaComTetoDe10EGravaUmaLinhaPorEscolha() {
        when(kitBuilder.quote(SELECAO, KitChannel.PDV)).thenReturn(COTACAO);

        Order order = vender(List.of(), List.of(SELECAO), "26.80", PromotionCheck.strict(null));

        assertThat(order.grossAmount()).isEqualByComparingTo("33.50");
        assertThat(order.discountAmount()).isEqualByComparingTo("6.70");
        assertThat(order.netAmount()).isEqualByComparingTo("26.80");
        assertThat(order.items()).extracting(OrderItem::sku).containsExactly("ESS", "SEDA");
        assertThat(order.items()).extracting(OrderItem::discountAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("6.00"), new BigDecimal("0.70"));
        verify(estoqueUseCase).adjustStock(eq("ESS"), eq("LOJA-01"), eq(MovementType.SAIDA), eq(BigDecimal.ONE),
                any(), eq("caixa1"));
    }

    @Test
    void sedaDoKitNaoEntraNaPromocaoPorQuantidade() {
        when(kitBuilder.quote(SELECAO, KitChannel.PDV)).thenReturn(COTACAO);

        // 3 sedas avulsas + 1 no kit: o kit não completa o "leve 4", logo promoção zero.
        Order order = vender(List.of(new SaleItemCommand("SEDA", new BigDecimal("3"), null, null)), List.of(SELECAO),
                "37.30", PromotionCheck.strict(null));

        assertThat(order.items()).allSatisfy(i -> assertThat(i.promotionId()).isNull());
        assertThat(order.netAmount()).isEqualByComparingTo("37.30");
    }

    @Test
    void descontoManualAindaRespeitaOTetoMesmoComKit() {
        when(kitBuilder.quote(SELECAO, KitChannel.PDV)).thenReturn(COTACAO);

        // Essência avulsa de 30 com R$ 10 de desconto manual: 10 / 63,50 = 15,7% > 10%.
        assertThatThrownBy(() -> vender(List.of(new SaleItemCommand("ESS", BigDecimal.ONE, new BigDecimal("10.00"),
                null)), List.of(SELECAO), "53.50", PromotionCheck.strict(null)))
                .isInstanceOf(DiscountLimitExceededException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void kitQueNaoFechaMaisPropagaKitStarAntesDeMexerNoEstoque() {
        when(kitBuilder.quote(SELECAO, KitChannel.PDV)).thenThrow(
                new InvalidKitSelectionException(Reason.KIT_ITEM_NOT_SELLABLE, "Produto SEDA não está disponível"));

        assertThatThrownBy(() -> vender(List.of(), List.of(SELECAO), "26.80", PromotionCheck.strict(null)))
                .isInstanceOfSatisfying(InvalidKitSelectionException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.KIT_ITEM_NOT_SELLABLE));
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }
}
