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
 *
 * @param orderId id do pedido, para o link do cliente; nulo nos exemplos sem pedido real
 * @param delivery {@code true} se o pedido é para entrega, o que muda o próximo passo do cliente
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
        BigDecimal total,
        Long orderId,
        boolean delivery) {

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
                order.serviceFeeAmount(), order.deliveryFee(), order.totalPayable(),
                order.id(), order.delivery() != null && order.delivery().isEntrega());
    }

    /** O mesmo pedido mostrando só {@code lines}, para o reembolso parcial listar o que foi devolvido. */
    public OrderEmailView withOnlyItems(List<Line> lines) {
        BigDecimal sum = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderEmailView(customerName, orderReference, lines, sum, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, sum, orderId, delivery);
    }

    /** Unidades devolvidas de um item: {@code amount} é o que o estorno registrou para elas. */
    public static Line refundLine(OrderItem item, BigDecimal quantity, BigDecimal amount) {
        return new Line(name(item), quantity, item.unitPrice(), amount);
    }

    /** "Maria Souza" → "Maria": a saudação dos e-mails é pelo primeiro nome. */
    public String firstName() {
        return customerName == null || customerName.isBlank() ? "" : customerName.trim().split("\\s+")[0];
    }

    /** Desconto ou cashback usado — o e-mail só mostra a linha quando há valor. */
    public boolean hasValue(BigDecimal amount) {
        return amount != null && amount.signum() != 0;
    }

    public int itemCount() {
        return items.size();
    }

    private static Line line(OrderItem item) {
        return new Line(name(item), item.quantity(), item.unitPrice(), item.netAmount());
    }

    private static String name(OrderItem item) {
        return item.productName() != null && !item.productName().isBlank() ? item.productName() : item.sku();
    }
}
