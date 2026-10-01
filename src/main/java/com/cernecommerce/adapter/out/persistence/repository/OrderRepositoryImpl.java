package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderEntity;
import com.cernecommerce.adapter.out.persistence.entity.OrderItemEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.DeliveryAddress;
import com.cernecommerce.core.domain.model.pedido.DeliveryMethod;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderFilter;
import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class OrderRepositoryImpl implements OrderRepository {

    /** Largura da numeração de pedido, zero-padded — {@code 000001000}. */
    private static final int ORDER_NUMBER_WIDTH = 9;

    private final OrderJpaRepository orderJpaRepository;

    public OrderRepositoryImpl(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = orderJpaRepository.findById(order.id() == null ? -1L : order.id())
                .orElseGet(OrderEntity::new);
        entity.setOrderNumber(order.orderNumber());
        entity.setChannel(order.channel().name());
        entity.setStatus(order.status().name());
        entity.setCustomerId(order.customerId());
        entity.setSessionId(order.sessionId());
        entity.setWarehouseCode(order.warehouseCode());
        entity.setTotalAmount(order.grossAmount());
        entity.setDiscountAmount(order.discountAmount());
        entity.setCashbackRedeemed(order.cashbackRedeemed());
        entity.setNetAmount(order.netAmount());
        entity.setChangeAmount(order.changeAmount());
        entity.setCancelReason(order.cancelReason());
        entity.setCreatedAt(order.createdAt());
        entity.setPaidAt(order.paidAt());
        entity.setConcludedAt(order.concludedAt());
        entity.setCancelledAt(order.cancelledAt());
        entity.setRefundedAt(order.refundedAt());
        entity.setReservedAt(order.reservedAt());
        entity.setSeparatedAt(order.separatedAt());
        entity.setShippedAt(order.shippedAt());
        entity.setDeliveredAt(order.deliveredAt());
        entity.setComandaId(order.comandaId());
        entity.setTableLabel(order.tableLabel());
        entity.setServiceFeeAmount(order.serviceFeeAmount());
        writeDelivery(entity, order.delivery());

        // PED-C005 — os itens só são escritos na CRIAÇÃO. Depois disso o pedido muda de status,
        // pagamento e entrega, mas nunca de linhas (todo with* de Order repassa a lista como veio), e
        // regravá-las seria apagar e reinserir order_item: cashback_entry.order_item_id (V70) aponta
        // para elas, e no Postgres o reembolso ou a retirada de pedido com cashback morriam na FK.
        // O H2 das ITs não tem essa FK — OrderCashbackPostgresIT é a prova.
        if (entity.getId() != null) {
            return toDomain(orderJpaRepository.save(entity));
        }
        for (OrderItem item : order.items()) {
            OrderItemEntity itemEntity = new OrderItemEntity();
            itemEntity.setOrder(entity);
            itemEntity.setSku(item.sku());
            itemEntity.setQuantity(item.quantity());
            itemEntity.setUnitPrice(item.unitPrice());
            itemEntity.setCostPrice(item.costPrice());
            itemEntity.setDiscountAmount(item.discountAmount());
            itemEntity.setCashbackPercent(item.cashbackPercent());
            itemEntity.setProductName(item.productName());
            itemEntity.setMode(item.mode().name());
            itemEntity.setCourtesy(item.courtesy());
            itemEntity.setNotes(item.notes());
            itemEntity.setCharcoal(item.charcoal());
            itemEntity.setSurchargeAmount(item.surchargeAmount());
            entity.getItems().add(itemEntity);
        }
        return toDomain(orderJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Order> findByIdForUpdate(Long id) {
        return orderJpaRepository.lockById(id).flatMap(orderJpaRepository::findById).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> findBySessionId(Long sessionId, int page, int size) {
        return withItems(orderJpaRepository
                .findBySessionIdOrderByIdDesc(sessionId, PageRequest.of(page, size)), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByComandaIds(java.util.Collection<Long> comandaIds) {
        if (comandaIds == null || comandaIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = orderJpaRepository.findIdsByComandaIdIn(comandaIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return orderJpaRepository.findAllByIdsWithItems(ids).stream().map(this::toDomain).toList();
    }

    /**
     * Segunda fase do ID-first (PED-C002): recebe a página já resolvida — <b>sem</b> ter tocado a
     * coleção de itens — e carrega os itens de todos os pedidos dela numa consulta só.
     *
     * <p>Existe compartilhada porque {@code findAll} e {@code findBySessionId} fazem exatamente a
     * mesma coisa depois de obterem sua {@code Page}: o que difere entre as duas é só como a página
     * é filtrada. Antes desta correção, ambas mapeavam direto com {@code toDomain}, que toca
     * {@code e.getItems()} e disparava uma consulta por pedido.</p>
     *
     * <p>A ordenação vem do {@code ORDER BY o.id DESC} da própria consulta de fetch, que casa com a
     * ordem das duas chamadoras — ver o javadoc de {@code findAllByIdsWithItems}.</p>
     */
    private PageResult<Order> withItems(Page<OrderEntity> pageResult, int page, int size) {
        List<Long> ids = pageResult.getContent().stream().map(OrderEntity::getId).toList();
        // Página vazia não emite o `IN ()`: é desnecessário, e nem todo banco o aceita. Mesma
        // guarda de ComandaRepositoryImpl.findOpen.
        if (ids.isEmpty()) {
            return new PageResult<>(List.of(), page, size,
                    pageResult.getTotalElements(), pageResult.getTotalPages());
        }
        List<Order> content = orderJpaRepository.findAllByIdsWithItems(ids).stream()
                .map(this::toDomain).toList();
        return new PageResult<>(content, page, size,
                pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    /**
     * Listagem filtrada da visão do administrador. Cada filtro é opcional, resolvido com uma
     * {@link Specification} — o predicado só é adicionado quando o valor não é nulo, então um
     * filtro ausente nunca vira um bind ambíguo no Postgres. Era uma query {@code @Query} com o
     * padrão {@code :param IS NULL OR ...}, mas isso fazia o Postgres real recusar inferir o tipo
     * do bind de {@code from}/{@code to} (Instant) quando vinham nulos, e o CAST explícito que
     * corrigiria isso tem um bug conhecido de interação Hibernate/pgjdbc que troca o tipo do
     * parâmetro por {@code bytea}. Specification evita a classe inteira do problema — mesmo padrão
     * já usado em {@code AuditLogRepositoryImpl.findFiltered}.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> findAll(SalesChannel channel, OrderStatus status, Long customerId,
            Instant from, Instant to, int page, int size) {
        return findAll(OrderFilter.of(channel, status, customerId, from, to), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> findAll(OrderFilter filter, int page, int size) {
        OrderFilter f = filter == null ? OrderFilter.of(null, null, null, null, null) : filter;
        Specification<OrderEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.channel()     != null) predicates.add(cb.equal(root.get("channel"), f.channel().name()));
            if (f.status()      != null) predicates.add(cb.equal(root.get("status"), f.status().name()));
            if (f.customerId()  != null) predicates.add(cb.equal(root.get("customerId"), f.customerId()));
            if (f.from()        != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            if (f.to()          != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), f.to()));
            // PDV-F026 — as colunas já existiam; faltava o filtro.
            if (f.sessionId()   != null) predicates.add(cb.equal(root.get("sessionId"), f.sessionId()));
            if (f.comandaId()   != null) predicates.add(cb.equal(root.get("comandaId"), f.comandaId()));
            if (f.orderNumber() != null) predicates.add(cb.equal(root.get("orderNumber"), f.orderNumber()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        // A Specification resolve só QUAIS pedidos entram na página, sem tocar a coleção de itens;
        // quem os carrega é o withItems, numa consulta só (PED-C002).
        return withItems(orderJpaRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumConcludedNetAmountBySessionId(Long sessionId) {
        BigDecimal sum = orderJpaRepository.sumConcludedNetAmountBySessionId(sessionId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumChangeAmountBySessionId(Long sessionId) {
        BigDecimal sum = orderJpaRepository.sumChangeAmountBySessionId(sessionId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    public String nextOrderNumber() {
        Long next = orderJpaRepository.nextOrderNumber();
        return String.format("%0" + ORDER_NUMBER_WIDTH + "d", next);
    }

    private Order toDomain(OrderEntity e) {
        return Order.of(e.getId(), e.getOrderNumber(), SalesChannel.valueOf(e.getChannel()),
                OrderStatus.valueOf(e.getStatus()), e.getCustomerId(), e.getSessionId(),
                e.getWarehouseCode(), e.getItems().stream().map(this::toDomain).toList(),
                e.getTotalAmount(), e.getDiscountAmount(), e.getCashbackRedeemed(), e.getNetAmount(),
                e.getChangeAmount(), e.getCancelReason(), e.getCreatedAt(), e.getPaidAt(),
                e.getConcludedAt(), e.getCancelledAt(), e.getRefundedAt(), e.getReservedAt(),
                e.getSeparatedAt(), e.getShippedAt(), e.getDeliveredAt(),
                e.getVersion() == null ? 0L : e.getVersion(), e.getComandaId(), e.getTableLabel(),
                // Pedido anterior a PDV-F015 lê como zero: o DEFAULT da V118 cobre as linhas já
                // gravadas, e este null-check cobre carga direta.
                e.getServiceFeeAmount() == null ? java.math.BigDecimal.ZERO : e.getServiceFeeAmount(),
                readDelivery(e));
    }

    /** PDV-F022 — sem entrega, tudo nulo: a linha opcional de order_delivery não é gravada. */
    private static void writeDelivery(OrderEntity entity, OrderDelivery delivery) {
        DeliveryAddress address = delivery == null ? null : delivery.address();
        entity.setDeliveryType(delivery == null ? null : delivery.type().name());
        entity.setDeliveryMethod(delivery == null || delivery.method() == null ? null : delivery.method().name());
        entity.setDeliveryStreet(address == null ? null : address.street());
        entity.setDeliveryNumber(address == null ? null : address.number());
        entity.setDeliveryComplement(address == null ? null : address.complement());
        entity.setDeliveryZipCode(address == null ? null : address.zipCode());
        entity.setDeliveryDistrict(address == null ? null : address.district());
        entity.setDeliveryCity(address == null ? null : address.city());
        entity.setDeliveryState(address == null ? null : address.state());
        entity.setDeliveryCountry(address == null ? null : address.country());
        entity.setDeliveryReference(address == null ? null : address.reference());
        entity.setDeliveryCourierName(delivery == null ? null : delivery.courierName());
        entity.setDeliveryCourierPhone(delivery == null ? null : delivery.courierPhone());
        entity.setDeliveryPickupCode(delivery == null ? null : delivery.pickupCode());
        entity.setDeliveryDropoffCode(delivery == null ? null : delivery.dropoffCode());
        entity.setDeliveryTrackingCode(delivery == null ? null : delivery.trackingCode());
        entity.setDeliveryFee(delivery == null ? null : delivery.fee());
    }

    private static OrderDelivery readDelivery(OrderEntity e) {
        if (e.getDeliveryType() == null) {
            return null;
        }
        DeliveryAddress address = e.getDeliveryStreet() == null ? null : new DeliveryAddress(
                e.getDeliveryStreet(), e.getDeliveryNumber(), e.getDeliveryComplement(), e.getDeliveryZipCode(),
                e.getDeliveryDistrict(), e.getDeliveryCity(), e.getDeliveryState(), e.getDeliveryCountry(),
                e.getDeliveryReference());
        return new OrderDelivery(DeliveryType.valueOf(e.getDeliveryType()), address,
                e.getDeliveryMethod() == null ? null : DeliveryMethod.valueOf(e.getDeliveryMethod()),
                e.getDeliveryCourierName(), e.getDeliveryCourierPhone(), e.getDeliveryPickupCode(),
                e.getDeliveryDropoffCode(), e.getDeliveryTrackingCode(), e.getDeliveryFee());
    }

    private OrderItem toDomain(OrderItemEntity e) {
        return OrderItem.of(e.getId(), e.getSku(), e.getQuantity(), e.getUnitPrice(), e.getCostPrice(),
                e.getDiscountAmount(), e.getCashbackPercent(), e.getProductName(),
                e.getMode() == null ? ConsumptionMode.NORMAL : ConsumptionMode.valueOf(e.getMode()),
                e.isCourtesy(), e.getNotes(), e.getSurchargeAmount(), e.getCharcoal());
    }
}
