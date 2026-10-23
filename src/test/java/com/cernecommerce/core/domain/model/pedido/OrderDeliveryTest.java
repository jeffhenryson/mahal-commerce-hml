package com.cernecommerce.core.domain.model.pedido;

import com.cernecommerce.core.domain.exception.pedido.InvalidDeliveryException;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderDeliveryTest {

    private static DeliveryAddress address() {
        return new DeliveryAddress("Rua A", "10", null, null, null, "João Pessoa", "PB", null, null);
    }

    private static OrderDelivery entrega(String fee) {
        return new OrderDelivery(DeliveryType.ENTREGA, address(), DeliveryMethod.APP_99, null, null, null, null,
                null, fee == null ? null : new BigDecimal(fee));
    }

    private static OrderDelivery.Patch patch(DeliveryType type, DeliveryAddress.Patch address, String pickup,
            String tracking, BigDecimal fee) {
        return new OrderDelivery.Patch(type, address, null, null, null, pickup, null, tracking, fee);
    }

    @Test
    void entrega_requiresAddressWithStreetNumberCityAndState() {
        assertThatThrownBy(() -> new OrderDelivery(DeliveryType.ENTREGA, null, null, null, null, null, null, null, null))
                .isInstanceOf(InvalidDeliveryException.class);
        assertThatThrownBy(() -> new DeliveryAddress("Rua A", " ", null, null, null, "JP", "PB", null, null))
                .isInstanceOf(InvalidDeliveryException.class);
    }

    @Test
    void retirada_refusesAddressMethodOrFee() {
        assertThatThrownBy(() -> new OrderDelivery(DeliveryType.RETIRADA, address(), null, null, null, null, null,
                null, null)).isInstanceOf(InvalidDeliveryException.class);
        assertThatThrownBy(() -> new OrderDelivery(DeliveryType.RETIRADA, null, null, null, null, null, null, null,
                new BigDecimal("5.00"))).isInstanceOf(InvalidDeliveryException.class);
        assertThat(new OrderDelivery(DeliveryType.RETIRADA, null, null, null, null, null, null, null, null).fee())
                .isEqualByComparingTo("0");
    }

    @Test
    void fee_defaultsToZeroAndRejectsNegative() {
        assertThat(entrega(null).fee()).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> entrega("-1")).isInstanceOf(InvalidDeliveryException.class);
    }

    @Test
    void withPatch_fillsCodesLaterKeepingTheRest() {
        OrderDelivery patched = entrega("8.00").withPatch(patch(null, null, "1234", null, null));

        assertThat(patched.pickupCode()).isEqualTo("1234");
        assertThat(patched.method()).isEqualTo(DeliveryMethod.APP_99);
        assertThat(patched.address()).isEqualTo(address());
        assertThat(patched.fee()).isEqualByComparingTo("8.00");
    }

    @Test
    void withPatch_mergesAddressFieldByFieldAndBlankClears() {
        OrderDelivery patched = new OrderDelivery(DeliveryType.ENTREGA,
                new DeliveryAddress("Rua A", "10", "Apto 1", null, null, "João Pessoa", "PB", null, null),
                null, null, null, null, null, null, null)
                .withPatch(patch(null, new DeliveryAddress.Patch(null, "20", "", null, null, null, null, null,
                        "Portão azul"), null, null, null));

        assertThat(patched.address().street()).isEqualTo("Rua A");
        assertThat(patched.address().number()).isEqualTo("20");
        assertThat(patched.address().complement()).isNull();
        assertThat(patched.address().reference()).isEqualTo("Portão azul");
    }

    @Test
    void withPatch_refusesFeeAndTypeChange() {
        assertThatThrownBy(() -> entrega("8.00").withPatch(patch(null, null, null, null, new BigDecimal("9.00"))))
                .isInstanceOf(InvalidDeliveryException.class);
        assertThatThrownBy(() -> entrega("8.00").withPatch(patch(DeliveryType.RETIRADA, null, null, null, null)))
                .isInstanceOf(InvalidDeliveryException.class);
        // Mesmo tipo reenviado não é mudança.
        assertThat(entrega("8.00").withPatch(patch(DeliveryType.ENTREGA, null, null, "X", null)).trackingCode())
                .isEqualTo("X");
    }

    // ── Order + entrega ────────────────────────────────────────────────────────────────────

    private static List<OrderItem> twoCharcoals() {
        return List.of(OrderItem.fromCatalog("CARV-001", new BigDecimal("2.000"),
                Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00")), null));
    }

    @Test
    void order_totalPayableIncludesDeliveryFeeButNetDoesNot() {
        Order order = Order.openBalcao(1L, "LOJA-01", null, twoCharcoals(), entrega("8.00"));

        assertThat(order.netAmount()).isEqualByComparingTo("44.00");
        assertThat(order.deliveryFee()).isEqualByComparingTo("8.00");
        assertThat(order.totalPayable()).isEqualByComparingTo("52.00");
    }

    @Test
    void order_reservedWithEntregaCanEnterShippingPipeline_retiradaCannot() {
        Order entrega = Order.openBalcao(1L, "LOJA-01", null, twoCharcoals(), entrega("0"))
                .reserved("000000001", null, Instant.now());
        assertThat(entrega.withStatus(OrderStatus.SEPARADO).status()).isEqualTo(OrderStatus.SEPARADO);

        Order retirada = Order.openBalcao(1L, "LOJA-01", null, twoCharcoals(),
                        new OrderDelivery(DeliveryType.RETIRADA, null, null, null, null, null, null, null, null))
                .reserved("000000002", null, Instant.now());
        assertThat(retirada.allowedTransitions()).containsExactlyInAnyOrder(OrderStatus.CONCLUIDO,
                OrderStatus.REEMBOLSADO);
        assertThatThrownBy(() -> retirada.withStatus(OrderStatus.SEPARADO))
                .hasMessageContaining("RESERVADO → SEPARADO");

        Order semEntrega = Order.openBalcao(1L, "LOJA-01", null, twoCharcoals())
                .reserved("000000003", null, Instant.now());
        assertThat(semEntrega.allowedTransitions()).doesNotContain(OrderStatus.SEPARADO);
    }

    @Test
    void order_deliveryOnBalcaoAndMarketplace_neverOnMesa() {
        // ECM-F009 — o app escolhe retirada ou entrega no checkout; a mesa consome no salão.
        assertThat(Order.openMarketplace(42L, "LOJA-01", twoCharcoals(), entrega("0")).delivery().isEntrega())
                .isTrue();
        assertThatThrownBy(() -> Order.openMesa(1L, "LOJA-01", null, 10L, "Mesa 4", twoCharcoals())
                .withDelivery(entrega("0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withDeliveryFee_caixaDefineATaxaDoPedidoDoAppAntesDoPagamento() {
        Order pedido = Order.openMarketplace(42L, "LOJA-01", twoCharcoals(), entrega("0"));

        Order comTaxa = pedido.withDeliveryFee(new BigDecimal("8.00"));

        assertThat(comTaxa.deliveryFee()).isEqualByComparingTo("8.00");
        assertThat(comTaxa.netAmount()).isEqualByComparingTo("44.00");
        assertThat(comTaxa.totalPayable()).isEqualByComparingTo("52.00");
    }

    @Test
    void withDeliveryFee_recusaDepoisDoPagamentoForaDoMarketplaceENaRetirada() {
        assertThatThrownBy(() -> Order.openMarketplace(42L, "LOJA-01", twoCharcoals(), entrega("0"))
                .paid(Instant.now()).withDeliveryFee(BigDecimal.ONE))
                .isInstanceOf(InvalidDeliveryException.class);
        assertThatThrownBy(() -> Order.openBalcao(1L, "LOJA-01", null, twoCharcoals(), entrega("0"))
                .withDeliveryFee(BigDecimal.ONE))
                .isInstanceOf(InvalidDeliveryException.class);
        assertThatThrownBy(() -> Order.openMarketplace(42L, "LOJA-01", twoCharcoals(),
                        new OrderDelivery(DeliveryType.RETIRADA, null, null, null, null, null, null, null, null))
                .withDeliveryFee(BigDecimal.ONE))
                .isInstanceOf(InvalidDeliveryException.class);
        assertThatThrownBy(() -> Order.openMarketplace(42L, "LOJA-01", twoCharcoals()).withDeliveryFee(BigDecimal.ONE))
                .isInstanceOf(InvalidDeliveryException.class);
    }
}
