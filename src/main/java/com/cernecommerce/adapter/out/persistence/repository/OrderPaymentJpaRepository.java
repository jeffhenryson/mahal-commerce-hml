package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderPaymentJpaRepository extends JpaRepository<OrderPaymentEntity, Long> {

    List<OrderPaymentEntity> findByOrderIdOrderByIdAsc(Long orderId);

    /** PDV-C054 — pagamentos de vários pedidos numa consulta, na ordem de lançamento. */
    List<OrderPaymentEntity> findByOrderIdInOrderByIdAsc(Collection<Long> orderIds);

    /** Único por {@code uk_order_payment_gateway_ref} (V68) — derived query é segura aqui. */
    Optional<OrderPaymentEntity> findByGatewayRef(String gatewayRef);

    /**
     * Join implícito com {@code OrderEntity} pelo par {@code order_id = id} — não há
     * {@code @ManyToOne} entre as duas entidades (ver o javadoc de {@code OrderPaymentEntity}),
     * então o vínculo é feito na própria query, no molde de {@code StockIntegrityJpaRepository}.
     *
     * <p>{@code COALESCE} porque uma sessão sem pagamento nenhum daquele método é o caso normal
     * (ex.: caixa que só recebeu em dinheiro nunca gera linha de PIX), e devolver {@code null}
     * obrigaria todo chamador a tratá-lo.</p>
     *
     * <p>PED-F006 — a diferença paga numa troca ({@code captureSessionId}) fica de fora daqui e conta
     * na sessão de quem fez a troca, por {@link #sumCapturedInSessionAndMethod}.</p>
     */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM OrderPaymentEntity p, OrderEntity o
            WHERE p.orderId = o.id
              AND o.sessionId = :sessionId
              AND p.captureSessionId IS NULL
              AND p.status = 'CAPTURED'
              AND p.method = :method
            """)
    BigDecimal sumCapturedAmountBySessionIdAndMethod(@Param("sessionId") Long sessionId,
            @Param("method") String method);

    /** PED-F006 — a outra metade: diferença de troca paga nesta sessão (índice parcial da V164). */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM OrderPaymentEntity p
            WHERE p.captureSessionId = :sessionId
              AND p.status = 'CAPTURED'
              AND p.method = :method
            """)
    BigDecimal sumCapturedInSessionAndMethod(@Param("sessionId") Long sessionId, @Param("method") String method);

    /**
     * Espelho da soma acima, para o que <b>saiu</b> da gaveta em estorno (PDV-C018).
     *
     * <p>Existe porque o ledger é append-only: {@code OrderPayment.refunded} grava uma linha nova
     * {@code REFUNDED} e <b>deixa a {@code CAPTURED} original de pé</b> — que é o desenho certo
     * para o histórico, e exatamente por isso a soma de capturados sozinha não descreve a gaveta.
     * Sem esta subtração, estornar uma venda em dinheiro devolve a cédula ao cliente e o esperado
     * do fechamento continua contando-a.</p>
     *
     * <p>PED-F004 — o estorno conta na sessão <b>onde foi feito</b> ({@code refundSessionId}): é dessa
     * gaveta que a cédula sai. Linha sem ela (anterior a PED-F004, ou estorno feito por quem não tinha
     * caixa aberto) cai na sessão do pedido, como antes.</p>
     *
     * <p>PDV-C049 — as duas metades são consultas separadas, somadas pelo adapter. A forma única,
     * {@code COALESCE(p.refundSessionId, o.sessionId) = :sessionId}, não usava índice nenhum: o
     * Postgres varria todo {@code REFUNDED} com o join. Esta metade usa o índice parcial da V162.</p>
     */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM OrderPaymentEntity p
            WHERE p.refundSessionId = :sessionId
              AND p.status = 'REFUNDED'
              AND p.method = :method
            """)
    BigDecimal sumRefundedInSessionAndMethod(@Param("sessionId") Long sessionId, @Param("method") String method);

    /** PDV-C049 — a outra metade: estorno sem sessão própria, contado na sessão do pedido. */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM OrderPaymentEntity p, OrderEntity o
            WHERE p.orderId = o.id
              AND o.sessionId = :sessionId
              AND p.refundSessionId IS NULL
              AND p.status = 'REFUNDED'
              AND p.method = :method
            """)
    BigDecimal sumRefundedWithoutSessionByOrderSessionAndMethod(@Param("sessionId") Long sessionId,
            @Param("method") String method);

    /** PDV-C051 — pares {@code [method, soma]} dos {@code CAPTURED} da sessão, numa consulta só. */
    @Query("""
            SELECT p.method, SUM(p.amount)
            FROM OrderPaymentEntity p, OrderEntity o
            WHERE p.orderId = o.id
              AND o.sessionId = :sessionId
              AND p.captureSessionId IS NULL
              AND p.status = 'CAPTURED'
            GROUP BY p.method
            """)
    List<Object[]> sumCapturedBySessionIdGroupByMethod(@Param("sessionId") Long sessionId);

    /** PED-F006 — pares {@code [method, soma]} das diferenças de troca pagas na sessão. */
    @Query("""
            SELECT p.method, SUM(p.amount)
            FROM OrderPaymentEntity p
            WHERE p.captureSessionId = :sessionId
              AND p.status = 'CAPTURED'
            GROUP BY p.method
            """)
    List<Object[]> sumCapturedInSessionGroupByMethod(@Param("sessionId") Long sessionId);

    /** PDV-C051 — pares {@code [method, soma]} dos estornos feitos na sessão (ver PDV-C049). */
    @Query("""
            SELECT p.method, SUM(p.amount)
            FROM OrderPaymentEntity p
            WHERE p.refundSessionId = :sessionId
              AND p.status = 'REFUNDED'
            GROUP BY p.method
            """)
    List<Object[]> sumRefundedInSessionGroupByMethod(@Param("sessionId") Long sessionId);

    /** PDV-C051 — pares {@code [method, soma]} dos estornos sem sessão própria, pela sessão do pedido. */
    @Query("""
            SELECT p.method, SUM(p.amount)
            FROM OrderPaymentEntity p, OrderEntity o
            WHERE p.orderId = o.id
              AND o.sessionId = :sessionId
              AND p.refundSessionId IS NULL
              AND p.status = 'REFUNDED'
            GROUP BY p.method
            """)
    List<Object[]> sumRefundedWithoutSessionByOrderSessionGroupByMethod(@Param("sessionId") Long sessionId);

    /** PDV-F038 — o marcado (CRM-F010) vendido na sessão: linhas ON_ACCOUNT dos pedidos dela. */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM OrderPaymentEntity p, OrderEntity o
            WHERE p.orderId = o.id
              AND o.sessionId = :sessionId
              AND p.status = 'ON_ACCOUNT'
            """)
    BigDecimal sumOnAccountAmountBySessionId(@Param("sessionId") Long sessionId);

    /**
     * PDV-F026 — pares (pedido, método) dos pagamentos {@code CAPTURED} dos pedidos informados, sem
     * repetição: a listagem de pedidos mostra como cada um foi pago numa consulta só por página.
     */
    @Query("""
            SELECT DISTINCT p.orderId, p.method
            FROM OrderPaymentEntity p
            WHERE p.orderId IN :orderIds
              AND p.status = 'CAPTURED'
            ORDER BY p.orderId, p.method
            """)
    List<Object[]> findCapturedMethodsByOrderIds(@Param("orderIds") Collection<Long> orderIds);
}
