package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long>,
        JpaSpecificationExecutor<OrderEntity> {

    /**
     * PED-C011 — trava a linha de {@code sales_order} para os caminhos que decidem sobre os pagamentos
     * do pedido (reembolso e correção).
     *
     * <p>Nativo, e não {@code @Lock(PESSIMISTIC_WRITE)} na entidade: o Hibernate 7 estende a trava às
     * tabelas da entidade ("follow-on locking"), e a {@code @SecondaryTable} de entrega não tem linha
     * no pedido sem entrega — a trava falha com {@code AssertionFailure: Expecting results}. A
     * linha do cabeçalho é o que serializa os dois caminhos; a entrega não entra na decisão.</p>
     */
    @Query(value = "SELECT id FROM sales_order WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> lockById(@Param("id") Long id);


    /** PDV-F029 — ids dos pedidos das comandas; os itens vêm por findAllByIdsWithItems. */
    @Query("SELECT o.id FROM OrderEntity o WHERE o.comandaId IN :comandaIds ORDER BY o.id ASC")
    List<Long> findIdsByComandaIdIn(@Param("comandaIds") java.util.Collection<Long> comandaIds);


    Page<OrderEntity> findBySessionIdOrderByIdDesc(Long sessionId, Pageable pageable);

    /**
     * Segunda fase do padrão ID-first (PED-C002): carrega os pedidos da página com os itens numa
     * consulta só.
     *
     * <p>Sem isto, {@code OrderRepositoryImpl.toDomain} toca a coleção {@code LAZY} de cada pedido
     * e a listagem paga <b>uma consulta por pedido</b> — até 101 numa página de 100, no endpoint de
     * pedidos do administrador. Não dá para resolver com {@code JOIN FETCH} na própria consulta
     * paginada: misturar {@code LIMIT}/{@code OFFSET} com fetch de coleção pagina as linhas do
     * produto cartesiano, não os pedidos.</p>
     *
     * <p>{@code ORDER BY o.id DESC} casa com a ordenação das duas chamadoras
     * ({@code findAll} usa {@code Sort.by(DESC, "id")}, {@code findBySessionIdOrderByIdDesc} já é
     * DESC), então a ordem da página é preservada sem o chamador precisar reordenar.</p>
     *
     * <p>A ordem dos <b>itens dentro</b> de cada pedido vem do {@code @OrderBy("id ASC")} de
     * {@code OrderEntity.items}, não daqui: ordenar por um alias de fetch join no {@code ORDER BY}
     * da própria consulta é HQL inválido, e o {@code @OrderBy} ainda tem a vantagem de valer para
     * <b>todo</b> caminho de leitura da coleção, não só para esta consulta.</p>
     */
    @Query("SELECT DISTINCT o FROM OrderEntity o LEFT JOIN FETCH o.items "
            + "WHERE o.id IN :ids ORDER BY o.id DESC")
    List<OrderEntity> findAllByIdsWithItems(@Param("ids") List<Long> ids);

    /**
     * Próximo número da sequência dedicada de numeração de pedido.
     *
     * <p>{@code nextval} é nativo porque a sequência não pertence a nenhuma entidade — ela existe
     * justamente para <b>não</b> derivar do {@code BIGSERIAL} do id, que deixa buracos em rollback.</p>
     */
    @Query(value = "SELECT nextval('order_number_seq')", nativeQuery = true)
    Long nextOrderNumber();

    /**
     * Receita concluída da sessão. Só {@code CONCLUIDO} entra: pedido cancelado não gerou dinheiro
     * na gaveta, e somá-lo faria o esperado do fechamento acusar uma falta que nunca existiu.
     */
    @Query("""
            SELECT COALESCE(SUM(o.netAmount), 0)
            FROM OrderEntity o
            WHERE o.sessionId = :sessionId AND o.status = 'CONCLUIDO'
            """)
    BigDecimal sumConcludedNetAmountBySessionId(@Param("sessionId") Long sessionId);

    /**
     * Troco devolvido pela sessão (PDV-C017) — o dinheiro que <b>saiu</b> da gaveta na própria
     * venda.
     *
     * <p>Existe porque {@code order_payment.amount} em {@code DINHEIRO} é o valor <b>entregue</b>
     * pelo cliente, não o retido: {@code PdvService.validatePaymentsAndComputeChange} deriva o
     * troco de {@code total pago − líquido}, e é a soma dos entregues que a conferência da gaveta
     * usa. Sem esta subtração, toda venda em dinheiro com troco infla o esperado exatamente pelo
     * troco, e o fechamento acusa uma falta que é só aritmética.
     *
     * <p><b>Sem filtro de status, de propósito.</b> O troco saiu da gaveta no instante da venda e
     * não volta: pedido {@code RESERVADO} ainda não retirado já devolveu troco, e pedido
     * {@code REEMBOLSADO} devolve ao cliente o valor <i>entregue</i> (é o {@code amount} da linha
     * {@code CAPTURED} que {@code OrderPayment.refunded} espelha), de modo que o troco continua
     * fora da gaveta dos dois lados da conta. Filtrar por {@code CONCLUIDO} devolveria o troco ao
     * esperado no instante em que a venda fosse reembolsada.</p>
     */
    @Query("""
            SELECT COALESCE(SUM(o.changeAmount), 0)
            FROM OrderEntity o
            WHERE o.sessionId = :sessionId AND o.changeAmount IS NOT NULL
            """)
    BigDecimal sumChangeAmountBySessionId(@Param("sessionId") Long sessionId);

    // findFiltered virou Specification (ver OrderRepositoryImpl.findAll): o padrão
    // "(:from IS NULL OR o.createdAt >= :from)" fazia o Postgres real recusar inferir o tipo do
    // bind quando from/to (Instant) vinham nulos ("could not determine data type of parameter").
    // A correção óbvia — CAST(:from AS timestamp) — trocou esse erro por outro: o Hibernate passou
    // a mandar o parâmetro como bytea para o driver ("cannot cast type bytea to timestamp without
    // time zone"), um problema conhecido de CAST sobre parâmetro nomeado em JPQL. Specification
    // elimina a classe inteira do problema: quando o filtro é nulo, o predicado simplesmente não
    // é adicionado, sem bind ambíguo de tipo em lugar nenhum.

    // ── Agregação de GET /orders/summary ────────────────────────────────────────────────────
    // from/to são obrigatórios neste endpoint (validados em OrderReportService) — nunca chegam
    // nulos aqui, então a comparação direta (sem CAST) nunca teve o problema de "could not
    // determine data type". Um CAST(:from AS timestamp) tinha sido colocado por consistência com
    // findFiltered, mas isso reintroduziu o bug do bytea (ver nota acima) mesmo sem nulo
    // envolvido — o problema é do CAST sobre parâmetro em si, não da nulidade.

    /** Distribuição completa por status — sem filtro de receita, é a contagem de tudo no período. */
    @Query("""
            SELECT o.status AS status, COUNT(o) AS count
            FROM OrderEntity o
            WHERE (:channel    IS NULL OR o.channel    = :channel)
              AND (:status     IS NULL OR o.status     = :status)
              AND (:customerId IS NULL OR o.customerId = :customerId)
              AND o.createdAt >= :from
              AND o.createdAt <= :to
            GROUP BY o.status
            """)
    List<StatusCountProjection> countByStatus(@Param("channel") String channel,
            @Param("status") String status, @Param("customerId") Long customerId,
            @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Totais de receita — só status com pagamento confirmado, exceto reembolsado
     * ({@code CANCELADO}/{@code REEMBOLSADO} não zeram os totais do pedido, e contá-los aqui
     * infla a receita com dinheiro que nunca entrou ou que voltou).
     */
    @Query("""
            SELECT COUNT(o) AS orderCount, COALESCE(SUM(o.netAmount), 0) AS totalRevenueNet,
                   COALESCE(SUM(o.totalAmount), 0) AS totalRevenueGross
            FROM OrderEntity o
            WHERE (:channel    IS NULL OR o.channel    = :channel)
              AND (:status     IS NULL OR o.status     = :status)
              AND (:customerId IS NULL OR o.customerId = :customerId)
              AND o.createdAt >= :from
              AND o.createdAt <= :to
              AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')
            """)
    HeaderTotalsProjection findRevenueTotals(@Param("channel") String channel,
            @Param("status") String status, @Param("customerId") Long customerId,
            @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT o.channel AS channel, COALESCE(SUM(o.netAmount), 0) AS revenue
            FROM OrderEntity o
            WHERE (:channel    IS NULL OR o.channel    = :channel)
              AND (:status     IS NULL OR o.status     = :status)
              AND (:customerId IS NULL OR o.customerId = :customerId)
              AND o.createdAt >= :from
              AND o.createdAt <= :to
              AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')
            GROUP BY o.channel
            """)
    List<ChannelRevenueProjection> findRevenueByChannel(@Param("channel") String channel,
            @Param("status") String status, @Param("customerId") Long customerId,
            @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT CAST(o.createdAt AS date) AS day, COALESCE(SUM(o.netAmount), 0) AS revenue,
                   COUNT(o) AS orderCount
            FROM OrderEntity o
            WHERE (:channel    IS NULL OR o.channel    = :channel)
              AND (:status     IS NULL OR o.status     = :status)
              AND (:customerId IS NULL OR o.customerId = :customerId)
              AND o.createdAt >= :from
              AND o.createdAt <= :to
              AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')
            GROUP BY CAST(o.createdAt AS date)
            ORDER BY CAST(o.createdAt AS date) ASC
            """)
    List<DailyRevenueProjection> findDailyRevenue(@Param("channel") String channel,
            @Param("status") String status, @Param("customerId") Long customerId,
            @Param("from") Instant from, @Param("to") Instant to);

    interface StatusCountProjection {
        String getStatus();
        long getCount();
    }

    interface HeaderTotalsProjection {
        long getOrderCount();
        BigDecimal getTotalRevenueNet();
        BigDecimal getTotalRevenueGross();
    }

    interface ChannelRevenueProjection {
        String getChannel();
        BigDecimal getRevenue();
    }

    interface DailyRevenueProjection {
        java.time.LocalDate getDay();
        BigDecimal getRevenue();
        long getOrderCount();
    }
}
