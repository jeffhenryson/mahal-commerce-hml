package com.cernecommerce.core.domain.model.notification;

import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;

import java.math.BigDecimal;
import java.util.List;

/**
 * O pedido como o comprador o lê no e-mail: itens com valor e os totais que ele pagou. Montado a
 * partir do {@link Order} já persistido, para que confirmação, atualização, cancelamento e
 * comprovante do PDV mostrem exatamente o mesmo detalhamento.
 *
 * <p>{@code orderReference} é "#id" e não {@code orderNumber}: este só é emitido na conclusão, e a
 * confirmação do checkout sai antes.</p>
 */
public record OrderEmailView(
        String customerName,
        String orderReference,
        List<Line> items,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal cashbackRedeemed,
        BigDecimal serviceFee,
        BigDecimal deliveryFee,
        BigDecimal total) {

    /** Uma linha do pedido; {@code amount} é o líquido do item (bruto menos desconto). */
    public record Line(String name, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {

        /** "2", "0,5" — quantidade sem os zeros da escala do banco. */
        public String quantityLabel() {
            return quantity == null ? "" : quantity.stripTrailingZeros().toPlainString().replace('.', ',');
        }
    }

    public OrderEmailView {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static OrderEmailView of(Order order, String customerName) {
        List<Line> lines = order.items().stream().map(OrderEmailView::line).toList();
        return new OrderEmailView(customerName, "#" + order.id(), lines,
                order.grossAmount(), order.discountAmount(), order.cashbackRedeemed(),
                order.serviceFeeAmount(), order.deliveryFee(), order.totalPayable());
    }

    /** Desconto ou cashback usado — o e-mail só mostra a linha quando há valor. */
    public boolean hasValue(BigDecimal amount) {
        return amount != null && amount.signum() != 0;
    }

    public int itemCount() {
        return items.size();
    }

    private static Line line(OrderItem item) {
        String name = item.productName() != null && !item.productName().isBlank() ? item.productName() : item.sku();
        return new Line(name, item.quantity(), item.unitPrice(), item.netAmount());
    }
}
