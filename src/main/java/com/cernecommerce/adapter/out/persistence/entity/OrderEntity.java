package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido de venda de qualquer canal (PDV-F003). Tabela {@code sales_order}, renomeada de
 * {@code cash_register_sale} pela V65.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
// PDV-F043 — a constraint é declarada aqui também para o H2 do perfil dev (sem Flyway) criá-la com o
// MESMO nome da V150: é por ele que o GlobalExceptionHandler reconhece o reenvio simultâneo.
@Table(name = "sales_order", uniqueConstraints = @UniqueConstraint(name = "uk_sales_order_client_sale_id",
        columnNames = "client_sale_id"))
// PDV-F022 — entrega em tabela própria (a regra de Order: endereço e frete fora de sales_order),
// mapeada como tabela secundária em vez de @OneToOne: o lado não-dono de um @OneToOne não é LAZY
// sem bytecode enhancement, e toda listagem de pedidos pagaria uma consulta por linha. A secundária
// entra por LEFT JOIN na mesma consulta. Linha opcional: só é gravada quando algum campo de entrega
// é não nulo, e é apagada se todos voltarem a nulo.
@SecondaryTable(name = OrderEntity.DELIVERY_TABLE, pkJoinColumns = @PrimaryKeyJoinColumn(name = "order_id"))
@org.hibernate.annotations.SecondaryRow(table = OrderEntity.DELIVERY_TABLE, optional = true)
public class OrderEntity {

    public static final String DELIVERY_TABLE = "order_delivery";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "order_number", length = 30)
    private String orderNumber;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    /** Bruto do pedido. Mantém o nome de coluna de V57 para não quebrar o dado legado. */
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "cashback_redeemed", nullable = false, precision = 14, scale = 2)
    private BigDecimal cashbackRedeemed;

    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "change_amount", precision = 14, scale = 2)
    private BigDecimal changeAmount;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    // PDV-F015 — taxa de serviço, os 10% do garçom. Coluna PRÓPRIA, fora de net_amount, porque o
    // líquido é somado como receita em quatro agregações e a gorjeta é repassada, não faturada.
    // Só existe em channel = MESA (CHECK ck_sales_order_service_fee_only_mesa).
    @Column(name = "service_fee_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal serviceFeeAmount = BigDecimal.ZERO;

    // PDV-F010 — origem de mesa. Só preenchidos em channel = MESA (CHECK ck_sales_order_mesa_origin).
    // comanda_id é redundante com comanda.order_id, que aponta de volta: a redundância evita join
    // reverso em toda página de Vendas > Pedidos.
    @Column(name = "comanda_id")
    private Long comandaId;

    // Rótulo congelado no fechamento, não lido da comanda — renomear a mesa depois não pode
    // reescrever o histórico. Mesma razão de order_item.product_name.
    @Column(name = "table_label", length = 100)
    private String tableLabel;

    /** Instante da criação. Mantém o nome de coluna {@code sold_at} de V57. */
    @Column(name = "sold_at", nullable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    /**
     * PDV-F043 — chave da venda gerada no caixa (UUID), com índice único parcial (V150). Fora do
     * domínio {@code Order} de propósito: só a idempotência da venda a lê, por
     * {@code OrderRepository.findIdByClientSaleId}, e o {@code save} do pedido não a sobrescreve.
     */
    @Column(name = "client_sale_id", length = 36)
    private String clientSaleId;

    /** PDV-F043 — hora em que a venda aconteceu no balcão, quando chegou pela fila offline. */
    @Column(name = "client_sold_at")
    private Instant clientSoldAt;

    @Column(name = "concluded_at")
    private Instant concludedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    // PDV-F008 — venda de balcão reservada para retirada depois. Histórico, sem CHECK de
    // coexistência com "status" (diferente de cancelled_at/refunded_at): permanece preenchido
    // mesmo depois de RESERVADO -> CONCLUIDO (retirada), mesma régua de paid_at.
    @Column(name = "reserved_at")
    private Instant reservedAt;

    // Timestamps por etapa da esteira de fulfillment (BACKEND_TODO.md do mahal-admin,
    // §"Vendas: timestamps por etapa"). Histórico, sem CHECK de coexistência com "status" — mesma
    // régua de reserved_at/paid_at.
    @Column(name = "separated_at")
    private Instant separatedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    // ---- PDV-F022: order_delivery (tabela secundária). Tudo nulo quando a venda não tem entrega.
    @Column(table = DELIVERY_TABLE, name = "type", length = 20)
    private String deliveryType;

    @Column(table = DELIVERY_TABLE, name = "method", length = 20)
    private String deliveryMethod;

    @Column(table = DELIVERY_TABLE, name = "street", length = 200)
    private String deliveryStreet;

    @Column(table = DELIVERY_TABLE, name = "number", length = 20)
    private String deliveryNumber;

    @Column(table = DELIVERY_TABLE, name = "complement", length = 100)
    private String deliveryComplement;

    @Column(table = DELIVERY_TABLE, name = "zip_code", length = 20)
    private String deliveryZipCode;

    @Column(table = DELIVERY_TABLE, name = "district", length = 100)
    private String deliveryDistrict;

    @Column(table = DELIVERY_TABLE, name = "city", length = 100)
    private String deliveryCity;

    @Column(table = DELIVERY_TABLE, name = "state", length = 50)
    private String deliveryState;

    @Column(table = DELIVERY_TABLE, name = "country", length = 60)
    private String deliveryCountry;

    @Column(table = DELIVERY_TABLE, name = "reference", length = 200)
    private String deliveryReference;

    @Column(table = DELIVERY_TABLE, name = "courier_name", length = 120)
    private String deliveryCourierName;

    @Column(table = DELIVERY_TABLE, name = "courier_phone", length = 30)
    private String deliveryCourierPhone;

    @Column(table = DELIVERY_TABLE, name = "pickup_code", length = 60)
    private String deliveryPickupCode;

    @Column(table = DELIVERY_TABLE, name = "dropoff_code", length = 60)
    private String deliveryDropoffCode;

    @Column(table = DELIVERY_TABLE, name = "tracking_code", length = 60)
    private String deliveryTrackingCode;

    @Column(table = DELIVERY_TABLE, name = "fee", precision = 14, scale = 2)
    private BigDecimal deliveryFee;

    /**
     * Bloqueio otimista. A {@code Sale} anterior não tinha — era irrelevante numa tabela
     * insert-only, e passa a importar quando o pedido ganha transição de estado.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    // @OrderBy porque o pedido é lido na ordem de lançamento — é assim que o comprovante imprime.
    // Sem isto a ordem é o que o banco quiser devolver: invisível enquanto cada pedido vinha de uma
    // consulta própria, e dependente da intercalação do join desde que PED-C002 passou a trazer
    // vários de uma vez. Mesmo padrão de ComandaEntity.items.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @ToString.Exclude
    private List<OrderItemEntity> items = new ArrayList<>();
}
