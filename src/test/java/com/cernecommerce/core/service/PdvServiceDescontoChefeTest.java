package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pedido.BenefitConflictException;
import com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.cashback.CashbackScope;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.BossDiscountRequest;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.PromotionCheck;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.CashMovementRepository;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Cashback × desconto do chefe na venda do balcão: cigarro fora dos benefícios, percentual exato sobre a
 * base elegível, exclusividade com o resgate e o legado do offline.
 */
@ExtendWith(MockitoExtension.class)
class PdvServiceDescontoChefeTest {

    private static final Pricing CARVAO = Pricing.of(new BigDecimal("60.00"), null, new BigDecimal("100.00"));
    private static final Pricing CIGARRO = Pricing.of(new BigDecimal("40.00"), null, new BigDecimal("50.00"));
    private static final Long CLIENTE = 42L;

    @Mock CashRegisterRepository cashRegisterRepository;
    @Mock CashMovementRepository cashMovementRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock ComandaRepository comandaRepository;
    @Mock ReceivableUseCase receivableUseCase;

    private PdvService pdvService;

    @BeforeEach
    void setUp() {
        pdvService = new PdvService(cashRegisterRepository, cashMovementRepository, orderRepository,
                orderPaymentRepository, estoqueUseCase, cashbackUseCase, comandaRepository, new BigDecimal("5"),
                receivableUseCase);
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
                    arg.version(), arg.comandaId(), arg.tableLabel(), arg.serviceFeeAmount(), arg.delivery(),
                    arg.bossDiscountPercent());
        });
        lenient().when(estoqueUseCase.resolveSaleInfo("CARV"))
                .thenReturn(info("Carvão", CARVAO, "Carvão", false));
        lenient().when(estoqueUseCase.resolveSaleInfo("CIG"))
                .thenReturn(info("Cigarro", CIGARRO, "Cigarros", true));
        // Taxa global de 10% para tudo: o cigarro tem que ficar em zero mesmo assim.
        lenient().when(cashbackUseCase.resolveApplicableRate(anyString(), anyString()))
                .thenReturn(new CashbackRate(1L, CashbackScope.GLOBAL, null, new BigDecimal("10"), true,
                        Instant.now(), null, Instant.now()));
    }

    private static EstoqueUseCase.CatalogSaleInfo info(String nome, Pricing pricing, String categoria,
            boolean foraDosBeneficios) {
        return new EstoqueUseCase.CatalogSaleInfo(nome, pricing, true, false, null, null, false, false, null,
                categoria, null, foraDosBeneficios);
    }

    private static List<PaymentCommand> dinheiro(String amount) {
        return List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal(amount), null));
    }

    private Order vender(List<SaleItemCommand> items, String pago, BigDecimal resgate, BossDiscountRequest boss) {
        return pdvService.registerSaleIdempotent(1L, CLIENTE, items, List.of(), dinheiro(pago), "caixa1", false,
                null, null, null, PromotionCheck.strict(null), resgate, boss).order();
    }

    private static SaleItemCommand linha(String sku, String desconto) {
        return new SaleItemCommand(sku, BigDecimal.ONE, desconto == null ? null : new BigDecimal(desconto), null);
    }

    @Test
    void cigarroMaisItemComum_cincoPorCentoSoSobreOComum_eCigarroSemCashback() {
        Order order = vender(List.of(linha("CARV", "5.00"), linha("CIG", null)), "145.00", BigDecimal.ZERO,
                BossDiscountRequest.online(5));

        assertThat(order.bossDiscountPercent()).isEqualTo(5);
        assertThat(order.discountAmount()).isEqualByComparingTo("5.00");
        OrderItem cigarro = order.items().stream().filter(i -> i.sku().equals("CIG")).findFirst().orElseThrow();
        assertThat(cigarro.benefitsExcluded()).isTrue();
        assertThat(cigarro.cashbackPercent()).isEqualByComparingTo("0");
        assertThat(order.totalCashbackEarned()).isEqualByComparingTo("0");
    }

    @Test
    void semDescontoDoChefe_cashbackSoSobreOItemComum() {
        Order order = vender(List.of(linha("CARV", null), linha("CIG", null)), "150.00", BigDecimal.ZERO,
                BossDiscountRequest.online(null));

        assertThat(order.bossDiscountPercent()).isNull();
        assertThat(order.totalCashbackEarned()).isEqualByComparingTo("10.00");
    }

    @Test
    void valorQueNaoBateComOPercentual_e409() {
        // 5% de 100 é 5,00; 7,50 (5% sobre os 150 com o cigarro) não bate.
        assertThatThrownBy(() -> vender(List.of(linha("CARV", "7.50"), linha("CIG", null)), "142.50",
                BigDecimal.ZERO, BossDiscountRequest.online(5)))
                .isInstanceOf(DiscountLimitExceededException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void descontoNaLinhaDoCigarro_e409() {
        assertThatThrownBy(() -> vender(List.of(linha("CARV", "2.50"), linha("CIG", "2.50")), "145.00",
                BigDecimal.ZERO, BossDiscountRequest.online(5)))
                .isInstanceOf(DiscountLimitExceededException.class);
    }

    @Test
    void descontoSemPercentualNaVendaOnline_e409() {
        assertThatThrownBy(() -> vender(List.of(linha("CARV", "5.00")), "95.00", BigDecimal.ZERO,
                BossDiscountRequest.online(null)))
                .isInstanceOf(DiscountLimitExceededException.class);
    }

    @Test
    void percentualAcimaDoTeto_e409() {
        assertThatThrownBy(() -> vender(List.of(linha("CARV", "6.00")), "94.00", BigDecimal.ZERO,
                BossDiscountRequest.online(6)))
                .isInstanceOf(DiscountLimitExceededException.class);
    }

    @Test
    void chefeMaisResgate_eBenefitConflict_antesDeTocarOEstoque() {
        assertThatThrownBy(() -> vender(List.of(linha("CARV", "5.00")), "75.00", new BigDecimal("20.00"),
                BossDiscountRequest.online(5)))
                .isInstanceOf(BenefitConflictException.class);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void resgate_eValidadoContraABaseElegivel_semOCigarro() {
        Order order = vender(List.of(linha("CARV", null), linha("CIG", null)), "50.00", new BigDecimal("100.00"),
                BossDiscountRequest.online(null));

        verify(cashbackUseCase).validateRedemption(eq(CLIENTE), eq(new BigDecimal("100.00")),
                eq(new BigDecimal("100.00")));
        assertThat(order.cashbackRedeemed()).isEqualByComparingTo("100.00");
        assertThat(order.netAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void offlineSemPercentual_aceitaAteOTetoSobreABaseElegivel() {
        Order order = vender(List.of(linha("CARV", "5.00"), linha("CIG", null)), "145.00", BigDecimal.ZERO,
                BossDiscountRequest.legacy());

        assertThat(order.discountAmount()).isEqualByComparingTo("5.00");
        assertThat(order.bossDiscountPercent()).isNull();

        // 5,01 sobre 100 elegíveis passa do teto de 5% — mesmo com os 150 da venda inteira.
        assertThatThrownBy(() -> vender(List.of(linha("CARV", "5.01"), linha("CIG", null)), "144.99",
                BigDecimal.ZERO, BossDiscountRequest.legacy()))
                .isInstanceOf(DiscountLimitExceededException.class);
    }

    @Test
    void vendaComDescontoDoChefe_gravaOPercentualNoPedido() {
        vender(List.of(linha("CARV", "3.00")), "97.00", BigDecimal.ZERO, BossDiscountRequest.online(3));

        ArgumentCaptor<Order> salvo = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(salvo.capture());
        assertThat(salvo.getValue().bossDiscountPercent()).isEqualTo(3);
    }
}
