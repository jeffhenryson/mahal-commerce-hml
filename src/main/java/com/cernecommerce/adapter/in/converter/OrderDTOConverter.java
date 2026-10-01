package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.request.DeliveryAddressRequest;
import com.cernecommerce.adapter.in.dtos.request.DeliveryRequest;
import com.cernecommerce.adapter.in.dtos.request.SaleItemRequest;
import com.cernecommerce.adapter.in.dtos.request.SalePaymentRequest;
import com.cernecommerce.adapter.in.dtos.response.DailyRevenueResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.DeliveryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.MarginByProductResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.MarginReportResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderAdminResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderItemAdminResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderItemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderPaymentResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderSummaryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.PaymentTotalResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SaleReceiptItemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SaleReceiptResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.TopProductResponseDTO;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pedido.MarginSummary;
import com.cernecommerce.core.domain.model.pedido.DeliveryAddress;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderSummary;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentTotal;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OrderDTOConverter {

    /**
     * Converte os itens do request em comandos de venda. Note que <b>não há preço</b> aqui: o
     * request só informa SKU, quantidade e desconto (PDV-F004).
     */
    public List<SaleItemCommand> toCommands(List<SaleItemRequest> requests) {
        return requests.stream()
                .map(r -> new SaleItemCommand(r.getSku(), r.getQuantity(), r.getDiscountAmount(), r.getNote()))
                .toList();
    }

    /** Converte as linhas de pagamento do request em comandos (PDV-F006). */
    public List<PaymentCommand> toPaymentCommands(List<SalePaymentRequest> requests) {
        return requests.stream()
                .map(r -> {
                    PaymentMethod method = PaymentMethod.valueOf(r.getMethod());
                    return new PaymentCommand(method, r.getAmount(), r.getInstallments(), r.getChannel(),
                            r.getProvider(), method == PaymentMethod.MARCADO ? r.getDueDate() : null);
                })
                .toList();
    }

    public OrderResponseDTO toResponse(Order order) {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(order.id());
        dto.setOrderNumber(order.orderNumber());
        dto.setChannel(order.channel().name());
        dto.setComandaId(order.comandaId());
        dto.setTableLabel(order.tableLabel());
        dto.setStatus(order.status().name());
        dto.setCustomerId(order.customerId());
        dto.setSessionId(order.sessionId());
        dto.setWarehouseCode(order.warehouseCode());
        dto.setGrossAmount(order.grossAmount());
        dto.setDiscountAmount(order.discountAmount());
        dto.setCashbackRedeemed(order.cashbackRedeemed());
        dto.setNetAmount(order.netAmount());
        // PDV-F015 — os dois lado a lado de propósito: netAmount é o que a loja vendeu,
        // totalPayable é o que o cliente pagou. Fora da mesa coincidem.
        dto.setServiceFeeAmount(order.serviceFeeAmount());
        dto.setTotalPayable(order.totalPayable());
        dto.setChangeAmount(order.changeAmount());
        dto.setCancelReason(order.cancelReason());
        dto.setCreatedAt(order.createdAt());
        dto.setPaidAt(order.paidAt());
        dto.setConcludedAt(order.concludedAt());
        dto.setCancelledAt(order.cancelledAt());
        dto.setReservedAt(order.reservedAt());
        dto.setDelivery(toDeliveryResponse(order.delivery()));
        dto.setItems(order.items().stream().map(this::toResponse).toList());
        return dto;
    }

    /** Igual a {@link #toResponse(Order)}, mas com os pagamentos anexados (PDV-F006). */
    public OrderResponseDTO toResponse(Order order, List<OrderPayment> payments) {
        OrderResponseDTO dto = toResponse(order);
        dto.setPayments(payments.stream().map(this::toResponse).toList());
        // CRM-F010 — na resposta da venda o marcado acabou de nascer: nada quitado ainda.
        boolean onAccount = payments.stream()
                .anyMatch(p -> p.status() == com.cernecommerce.core.domain.model.pagamento.PaymentStatus.ON_ACCOUNT);
        dto.setPaymentStatus(onAccount ? "PENDENTE" : "PAGO");
        return dto;
    }

    /** CRM-F010 — situação do pagamento a partir do marcado do pedido; sem marcado, PAGO. */
    public static String paymentStatus(com.cernecommerce.core.domain.model.recebivel.CustomerReceivable receivable) {
        if (receivable == null || !receivable.status().isOpen()) {
            return "PAGO";
        }
        return receivable.amountPaid().signum() > 0 ? "PARCIAL" : "PENDENTE";
    }

    /** Resposta de {@code POST /shop/checkout} (ECM-F004): o único lugar com {@code checkoutUrl}. */
    public OrderResponseDTO toCheckoutResponse(Order order, String checkoutUrl) {
        OrderResponseDTO dto = toResponse(order);
        dto.setCheckoutUrl(checkoutUrl);
        return dto;
    }

    public PageResult<OrderResponseDTO> toResponse(PageResult<Order> page) {
        return new PageResult<>(page.content().stream().map(this::toResponse).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    public OrderPaymentResponseDTO toResponse(OrderPayment payment) {
        OrderPaymentResponseDTO dto = new OrderPaymentResponseDTO();
        dto.setId(payment.id());
        dto.setMethod(payment.method().name());
        dto.setAmount(payment.amount());
        dto.setStatus(payment.status().name());
        dto.setInstallments(payment.installments());
        dto.setCapturedAt(payment.capturedAt());
        dto.setCreatedAt(payment.createdAt());
        dto.setChannel(payment.channel());
        dto.setProvider(payment.provider());
        dto.setCorrectionId(payment.correctionId() != null ? payment.correctionId() : payment.originCorrectionId());
        dto.setOriginCorrectionId(payment.originCorrectionId());
        dto.setCorrectedAt(payment.correctedAt());
        dto.setCorrectedBy(payment.correctedBy());
        dto.setDueDate(payment.dueDate());
        return dto;
    }

    public List<PaymentTotalResponseDTO> toPaymentTotalResponse(List<PaymentTotal> totals) {
        return totals.stream().map(t -> {
            PaymentTotalResponseDTO dto = new PaymentTotalResponseDTO();
            dto.setMethod(t.method().name());
            dto.setAmount(t.amount());
            dto.setRefundedAmount(t.refundedAmount());
            dto.setChangeAmount(t.changeAmount());
            dto.setNetAmount(t.netAmount());
            dto.setReceivableReceived(t.receivableReceived());
            return dto;
        }).toList();
    }

    /**
     * Comprovante interno, não fiscal (PDV §Comprovante). {@code items} não carrega nome de
     * produto — o domínio {@code OrderItem} não guarda um, e resolver por SKU aqui acoplaria o PDV
     * ao catálogo só para exibição; quem consome já tem o catálogo carregado.
     */
    public SaleReceiptResponseDTO toReceipt(Order order, List<OrderPayment> payments) {
        SaleReceiptResponseDTO dto = new SaleReceiptResponseDTO();
        dto.setOrderId(order.id());
        dto.setOrderNumber(order.orderNumber());
        dto.setWarehouseCode(order.warehouseCode());
        dto.setCreatedAt(order.createdAt());
        dto.setConcludedAt(order.concludedAt());
        dto.setChannel(order.channel().name());
        dto.setStatus(order.status().name());
        dto.setTableLabel(order.tableLabel());
        dto.setComandaId(order.comandaId());
        dto.setSessionId(order.sessionId());
        dto.setCustomerId(order.customerId());
        dto.setDelivery(toDeliveryResponse(order.delivery()));
        dto.setCashbackRedeemed(order.cashbackRedeemed());
        dto.setItems(order.items().stream().map(this::toReceiptItem).toList());
        dto.setGrossAmount(order.grossAmount());
        dto.setDiscountAmount(order.discountAmount());
        dto.setNetAmount(order.netAmount());
        // PDV-F015 — os dois lado a lado de propósito: netAmount é o que a loja vendeu,
        // totalPayable é o que o cliente pagou. Fora da mesa coincidem.
        dto.setServiceFeeAmount(order.serviceFeeAmount());
        dto.setDeliveryFee(order.deliveryFee());
        dto.setTotalPayable(order.totalPayable());
        dto.setChangeAmount(order.changeAmount());
        dto.setPayments(payments.stream().map(this::toResponse).toList());
        return dto;
    }

    private SaleReceiptItemResponseDTO toReceiptItem(OrderItem item) {
        SaleReceiptItemResponseDTO dto = new SaleReceiptItemResponseDTO();
        dto.setSku(item.sku());
        dto.setProductName(item.productName());
        dto.setQuantity(item.quantity());
        dto.setUnitPrice(item.unitPrice());
        dto.setDiscountAmount(item.discountAmount());
        dto.setNetAmount(item.netAmount());
        dto.setSurchargeAmount(item.surchargeAmount());
        dto.setCashbackAmount(item.cashbackAmount());
        dto.setCourtesy(item.courtesy());
        dto.setMode(item.mode().name());
        dto.setNotes(item.notes());
        dto.setCarvao(item.charcoal());
        return dto;
    }

    // ── Visão do administrador: acrescenta custo e margem ────────────────────────────────────

    public OrderAdminResponseDTO toAdminResponse(Order order) {
        OrderAdminResponseDTO dto = new OrderAdminResponseDTO();
        dto.setId(order.id());
        dto.setOrderNumber(order.orderNumber());
        dto.setChannel(order.channel().name());
        dto.setComandaId(order.comandaId());
        dto.setTableLabel(order.tableLabel());
        dto.setStatus(order.status().name());
        dto.setCustomerId(order.customerId());
        dto.setSessionId(order.sessionId());
        dto.setWarehouseCode(order.warehouseCode());
        dto.setGrossAmount(order.grossAmount());
        dto.setDiscountAmount(order.discountAmount());
        dto.setCashbackRedeemed(order.cashbackRedeemed());
        dto.setNetAmount(order.netAmount());
        // PDV-F015 — os dois lado a lado de propósito: netAmount é o que a loja vendeu,
        // totalPayable é o que o cliente pagou. Fora da mesa coincidem.
        dto.setServiceFeeAmount(order.serviceFeeAmount());
        dto.setTotalPayable(order.totalPayable());
        dto.setChangeAmount(order.changeAmount());
        dto.setMarginAmount(totalMargin(order));
        dto.setCancelReason(order.cancelReason());
        dto.setCreatedAt(order.createdAt());
        dto.setPaidAt(order.paidAt());
        dto.setConcludedAt(order.concludedAt());
        dto.setCancelledAt(order.cancelledAt());
        dto.setReservedAt(order.reservedAt());
        dto.setSeparatedAt(order.separatedAt());
        dto.setShippedAt(order.shippedAt());
        dto.setDeliveredAt(order.deliveredAt());
        dto.setDelivery(toDeliveryResponse(order.delivery()));
        dto.setAllowedTransitions(order.allowedTransitions().stream()
                .map(Enum::name).sorted().toList());
        dto.setItems(order.items().stream().map(this::toAdminResponse).toList());
        return dto;
    }

    public PageResult<OrderAdminResponseDTO> toAdminResponse(PageResult<Order> page) {
        return new PageResult<>(page.content().stream().map(this::toAdminResponse).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    private OrderItemAdminResponseDTO toAdminResponse(OrderItem item) {
        OrderItemAdminResponseDTO dto = new OrderItemAdminResponseDTO();
        dto.setId(item.id());
        dto.setSku(item.sku());
        dto.setProductName(item.productName());
        dto.setQuantity(item.quantity());
        dto.setUnitPrice(item.unitPrice());
        dto.setDiscountAmount(item.discountAmount());
        dto.setGrossAmount(item.grossAmount());
        dto.setNetAmount(item.netAmount());
        dto.setCostPrice(item.costPrice());
        dto.setMarginAmount(item.marginAmount());
        dto.setCashbackPercent(item.cashbackPercent());
        dto.setCashbackAmount(item.cashbackAmount());
        dto.setMode(item.mode());
        dto.setCourtesy(item.courtesy());
        dto.setNotes(item.notes());
        dto.setCarvao(item.charcoal());
        dto.setSurchargeAmount(item.surchargeAmount());
        return dto;
    }

    /**
     * Margem total do pedido. Devolve {@code null} — e não um total parcial — se <b>algum</b> item
     * não tiver custo congelado: somar só os itens conhecidos produziria um número que parece a
     * margem do pedido e não é, o que é pior do que não ter número nenhum.
     */
    private BigDecimal totalMargin(Order order) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : order.items()) {
            BigDecimal margin = item.marginAmount();
            if (margin == null) {
                return null;
            }
            total = total.add(margin);
        }
        return total;
    }

    private OrderItemResponseDTO toResponse(OrderItem item) {
        OrderItemResponseDTO dto = new OrderItemResponseDTO();
        dto.setId(item.id());
        dto.setSku(item.sku());
        dto.setProductName(item.productName());
        dto.setQuantity(item.quantity());
        dto.setUnitPrice(item.unitPrice());
        dto.setDiscountAmount(item.discountAmount());
        dto.setGrossAmount(item.grossAmount());
        dto.setNetAmount(item.netAmount());
        dto.setCashbackPercent(item.cashbackPercent());
        dto.setCashbackAmount(item.cashbackAmount());
        dto.setNote(item.notes());
        return dto;
    }

    // ── Entrega (PDV-F022) ───────────────────────────────────────────────────────────────────

    /** Entrega do request de venda; {@code null} quando a venda não tem. */
    public OrderDelivery toDelivery(DeliveryRequest request) {
        if (request == null) {
            return null;
        }
        DeliveryAddressRequest a = request.getAddress();
        DeliveryAddress address = a == null ? null : new DeliveryAddress(a.getStreet(), a.getNumber(),
                a.getComplement(), a.getZipCode(), a.getDistrict(), a.getCity(), a.getState(), a.getCountry(),
                a.getReference());
        return new OrderDelivery(request.getType(), address, request.getMethod(), request.getCourierName(),
                request.getCourierPhone(), request.getPickupCode(), request.getDropoffCode(),
                request.getTrackingCode(), request.getFee());
    }

    /** Edição parcial da entrega — {@code PATCH /orders/{id}/delivery}. */
    public OrderDelivery.Patch toDeliveryPatch(DeliveryRequest request) {
        DeliveryAddressRequest a = request.getAddress();
        DeliveryAddress.Patch address = a == null ? null : new DeliveryAddress.Patch(a.getStreet(), a.getNumber(),
                a.getComplement(), a.getZipCode(), a.getDistrict(), a.getCity(), a.getState(), a.getCountry(),
                a.getReference());
        return new OrderDelivery.Patch(request.getType(), address, request.getMethod(), request.getCourierName(),
                request.getCourierPhone(), request.getPickupCode(), request.getDropoffCode(),
                request.getTrackingCode(), request.getFee());
    }

    private DeliveryResponseDTO toDeliveryResponse(OrderDelivery delivery) {
        if (delivery == null) {
            return null;
        }
        DeliveryResponseDTO dto = new DeliveryResponseDTO();
        dto.setType(delivery.type().name());
        dto.setMethod(delivery.method() == null ? null : delivery.method().name());
        dto.setCourierName(delivery.courierName());
        dto.setCourierPhone(delivery.courierPhone());
        dto.setPickupCode(delivery.pickupCode());
        dto.setDropoffCode(delivery.dropoffCode());
        dto.setTrackingCode(delivery.trackingCode());
        dto.setFee(delivery.fee());
        DeliveryAddress a = delivery.address();
        if (a != null) {
            DeliveryResponseDTO.Address address = new DeliveryResponseDTO.Address();
            address.setStreet(a.street());
            address.setNumber(a.number());
            address.setComplement(a.complement());
            address.setZipCode(a.zipCode());
            address.setDistrict(a.district());
            address.setCity(a.city());
            address.setState(a.state());
            address.setCountry(a.country());
            address.setReference(a.reference());
            dto.setAddress(address);
        }
        return dto;
    }

    // ── Resumo agregado de vendas (GET /orders/summary) ──────────────────────────────────────

    public OrderSummaryResponseDTO toSummaryResponse(OrderSummary summary) {
        OrderSummaryResponseDTO dto = new OrderSummaryResponseDTO();
        dto.setTotalOrders(summary.totalOrders());
        dto.setTotalRevenueNet(summary.totalRevenueNet());
        dto.setTotalRevenueGross(summary.totalRevenueGross());
        dto.setAverageTicket(summary.averageTicket());
        dto.setRevenueByChannel(summary.revenueByChannel().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue)));
        dto.setOrdersByStatus(summary.ordersByStatus().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue)));
        dto.setCancelledOrRefundedRate(summary.cancelledOrRefundedRate());
        dto.setDailyRevenue(summary.dailyRevenue().stream().map(this::toResponse).toList());
        dto.setTopProducts(summary.topProducts().stream().map(this::toResponse).toList());
        return dto;
    }

    private DailyRevenueResponseDTO toResponse(OrderSummary.DailyRevenue daily) {
        DailyRevenueResponseDTO dto = new DailyRevenueResponseDTO();
        dto.setDate(daily.date());
        dto.setRevenue(daily.revenue());
        dto.setOrderCount(daily.orderCount());
        return dto;
    }

    // ── Ranking de produtos mais vendidos (GET /orders/analytics/top-products) ──────────────

    public List<TopProductResponseDTO> toTopProductsResponse(List<OrderSummary.TopProduct> topProducts) {
        return topProducts.stream().map(this::toResponse).toList();
    }

    private TopProductResponseDTO toResponse(OrderSummary.TopProduct product) {
        TopProductResponseDTO dto = new TopProductResponseDTO();
        dto.setSku(product.sku());
        dto.setProductName(product.productName());
        dto.setQuantitySold(product.quantitySold());
        dto.setRevenue(product.revenue());
        return dto;
    }

    // ── Relatório de margem (FIN-F003, GET /financeiro/margem) ──────────────────────────────

    public MarginReportResponseDTO toMarginResponse(MarginSummary summary) {
        MarginReportResponseDTO dto = new MarginReportResponseDTO();
        dto.setItemsConsidered(summary.itemsConsidered());
        dto.setTotalRevenueNet(summary.totalRevenueNet());
        dto.setTotalCost(summary.totalCost());
        dto.setTotalMargin(summary.totalMargin());
        dto.setMarginPercent(summary.marginPercent());
        dto.setTopProductsByMargin(summary.topProductsByMargin().stream().map(this::toResponse).toList());
        return dto;
    }

    private MarginByProductResponseDTO toResponse(MarginSummary.MarginByProduct product) {
        MarginByProductResponseDTO dto = new MarginByProductResponseDTO();
        dto.setSku(product.sku());
        dto.setProductName(product.productName());
        dto.setQuantitySold(product.quantitySold());
        dto.setMargin(product.margin());
        return dto;
    }
}
