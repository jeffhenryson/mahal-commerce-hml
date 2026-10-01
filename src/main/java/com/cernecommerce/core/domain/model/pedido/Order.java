package com.cernecommerce.core.domain.model.pedido;

import com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Pedido de venda, de qualquer canal (PDV-F003).
 *
 * <p>Substitui a {@code Sale} anterior, que não tinha cliente, pagamento, desconto, status nem
 * cancelamento — não era "faltavam campos", era uma entidade que não representava um pedido.</p>
 *
 * <h2>Um canal, uma tabela</h2>
 * <p>Balcão e marketplace são o <b>mesmo</b> documento, distinguidos por {@link #channel}. Tudo que
 * vem depois — extrato do cliente, ledger de cashback, devolução, faturamento, documento fiscal,
 * relatório de margem — consulta "vendas, independente de canal". Com duas tabelas, cada um desses
 * consumidores pagaria um {@code UNION} ou duplicaria lógica.</p>
 *
 * <h2>O que deliberadamente não mora aqui</h2>
 * <p>Sessão de caixa, formas de pagamento, endereço e frete ficam em tabelas próprias, populadas só
 * pelo canal que as tem. É o que evita o pedido de 40 colunas em que metade é sempre nula.</p>
 *
 * <h2>{@code channel} é origem; {@code sessionId} é liquidação</h2>
 * <p>São dimensões independentes, e confundi-las custaria caro. {@link #channel} diz <b>onde o
 * pedido nasceu</b> e nunca muda. {@link #sessionId} diz <b>qual caixa o liquidou</b>.</p>
 *
 * <p>O caso que separa os dois: o cliente monta o pedido no aplicativo, vem à loja e paga no balcão.
 * Esse pedido continua sendo {@code MARKETPLACE} — foi o site que o gerou, e é assim que ele tem que
 * aparecer no relatório de conversão — mas o dinheiro entrou numa gaveta específica, e o fechamento
 * daquele caixa precisa contabilizá-lo. Reescrever o canal para {@code BALCAO} na liquidação faria o
 * marketplace parecer não vender nada.</p>
 *
 * <h2>Numeração</h2>
 * <p>{@link #orderNumber} vem de sequência própria e é emitido na <b>conclusão</b> (ou na reserva
 * para retirada — PDV-F008, o outro ponto em que o balcão fixa a venda), não na criação: o
 * {@code BIGSERIAL} do id deixa buracos quando uma transação faz rollback, e buraco em numeração
 * de documento fiscal é problema com o fisco.</p>
 */
public record Order(
        Long id,
        String orderNumber,
        SalesChannel channel,
        OrderStatus status,
        Long customerId,
        Long sessionId,
        String warehouseCode,
        List<OrderItem> items,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal cashbackRedeemed,
        BigDecimal netAmount,
        BigDecimal changeAmount,
        String cancelReason,
        Instant createdAt,
        Instant paidAt,
        Instant concludedAt,
        Instant cancelledAt,
        Instant refundedAt,
        Instant reservedAt,
        Instant separatedAt,
        Instant shippedAt,
        Instant deliveredAt,
        long version,
        Long comandaId,
        String tableLabel,
        BigDecimal serviceFeeAmount,
        OrderDelivery delivery) {

    public Order {
        if (channel == null) {
            throw new IllegalArgumentException("channel é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório");
        }
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new IllegalArgumentException("warehouseCode é obrigatório");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items não pode ser vazio");
        }
        items = List.copyOf(items);

        // Cliente: opcional no balcão (a venda anônima de passagem é a maioria), obrigatório no
        // marketplace — pedido online sem cliente não tem para quem entregar nem para quem estornar.
        if (channel == SalesChannel.MARKETPLACE && customerId == null) {
            throw new IllegalArgumentException("customerId é obrigatório em pedido de MARKETPLACE");
        }
        // Sessão de caixa: o balcão sempre tem uma, e a MESA também — a comanda nasce dentro de
        // uma sessão aberta. O marketplace normalmente não tem, mas PODE ter, quando o cliente
        // monta o pedido no app e vem pagar na loja. Ver a nota sobre sessionId na documentação
        // do tipo.
        if ((channel == SalesChannel.BALCAO || channel == SalesChannel.MESA) && sessionId == null) {
            throw new IllegalArgumentException("sessionId é obrigatório em venda de " + channel);
        }
        // PDV-F010: a origem de mesa só existe no canal MESA, e é obrigatória nele — é o que
        // permite o histórico da mesa aparecer no pedido sem consulta reversa à comanda.
        if (channel == SalesChannel.MESA) {
            if (comandaId == null) {
                throw new IllegalArgumentException("comandaId é obrigatório em pedido de MESA");
            }
            if (tableLabel == null || tableLabel.isBlank()) {
                throw new IllegalArgumentException("tableLabel é obrigatório em pedido de MESA");
            }
        } else if (comandaId != null || tableLabel != null) {
            throw new IllegalArgumentException(
                    "comandaId/tableLabel só existem em pedido de MESA: channel=" + channel);
        }

        grossAmount = requireNonNegative(grossAmount, "grossAmount");
        discountAmount = requireNonNegative(discountAmount, "discountAmount");
        cashbackRedeemed = requireNonNegative(cashbackRedeemed, "cashbackRedeemed");
        netAmount = requireNonNegative(netAmount, "netAmount");
        serviceFeeAmount = requireNonNegative(serviceFeeAmount, "serviceFeeAmount");

        // PDV-F015: os 10% do garçom são serviço de mesa, e só existem onde há mesa. No balcão não
        // há o que cobrar — mesma razão pela qual comandaId/tableLabel também são exclusivos de
        // MESA. O CHECK do schema espelha esta regra, para sobreviver a carga direta.
        if (channel != SalesChannel.MESA && serviceFeeAmount.signum() > 0) {
            throw new IllegalArgumentException(
                    "serviceFeeAmount só existe em pedido de MESA: channel=" + channel);
        }

        // A taxa fica DE FORA do netAmount de propósito (PDV-F015). O líquido é o valor da
        // mercadoria depois dos abatimentos, e é ele que quatro agregações somam como receita
        // (findRevenueTotals, por canal, por dia, e o total da sessão). A gorjeta é do garçom, não
        // da casa: somá-la ali inflaria receita e margem com dinheiro que a loja apenas repassa.
        // O que o cliente paga é totalPayable(), e é contra ele que o pagamento é validado.
        BigDecimal expectedNet = grossAmount.subtract(discountAmount).subtract(cashbackRedeemed);
        if (netAmount.compareTo(expectedNet) != 0) {
            throw new IllegalArgumentException("netAmount deve ser grossAmount - discountAmount - cashbackRedeemed: "
                    + "esperado " + expectedNet + ", recebido " + netAmount);
        }

        // Troco só existe onde há dinheiro em espécie mudando de mão — balcão e mesa. O
        // fechamento de comanda passa pela MESMA validatePaymentsAndComputeChange da venda de
        // balcão, então recusar troco aqui quebraria o fechamento em dinheiro de toda mesa.
        if (changeAmount != null) {
            if (changeAmount.signum() < 0) {
                throw new IllegalArgumentException("changeAmount não pode ser negativo");
            }
            if (channel == SalesChannel.MARKETPLACE && changeAmount.signum() > 0) {
                throw new IllegalArgumentException("changeAmount não existe em pedido de MARKETPLACE");
            }
        }

        // PDV-F022 — entrega/retirada é do balcão. A mesa consome no salão, e o marketplace ainda
        // não tem frete modelado (quando tiver, esta regra é o lugar de abrir).
        if (delivery != null && channel != SalesChannel.BALCAO) {
            throw new IllegalArgumentException("delivery só existe em pedido de BALCAO: channel=" + channel);
        }

        // Estado e carimbo de tempo não podem discordar: é a invariante que o CHECK do schema
        // espelha, para sobreviver a carga direta e script de correção.
        if ((status == OrderStatus.CANCELADO) != (cancelledAt != null)) {
            throw new IllegalArgumentException(
                    "status CANCELADO e cancelledAt têm que coexistir: status=" + status + ", cancelledAt=" + cancelledAt);
        }
        if ((status == OrderStatus.REEMBOLSADO) != (refundedAt != null)) {
            throw new IllegalArgumentException(
                    "status REEMBOLSADO e refundedAt têm que coexistir: status=" + status + ", refundedAt=" + refundedAt);
        }
        // reservedAt/separatedAt/shippedAt/deliveredAt NÃO têm CHECK de coexistência com o status
        // atual (diferente de cancelledAt/refundedAt acima) — são histórico, como paidAt: um
        // pedido que já passou por SEPARADO continua com separatedAt preenchido mesmo depois de
        // avançar para ENTREGUE ou ser reembolsado.
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " não pode ser negativo");
        }
        return value;
    }

    /**
     * Abre uma venda de balcão em {@link OrderStatus#CRIADO}. Estado efêmero: quem chama conclui na
     * mesma transação, via {@link #concluded} ou {@link #reserved} (PDV-F008).
     *
     * <p>O {@code warehouseCode} vem da <b>sessão</b>, não do chamador — é o que restringe a venda
     * ao depósito do caixa aberto e fecha o buraco de isolamento do módulo.</p>
     */
    public static Order openBalcao(Long sessionId, String warehouseCode, Long customerId, List<OrderItem> items) {
        return openBalcao(sessionId, warehouseCode, customerId, items, null);
    }

    /**
     * Igual a {@link #openBalcao(Long, String, Long, List)}, com entrega ou retirada (PDV-F022).
     * Com ENTREGA, quem chama fixa a venda via {@link #reserved}: a mercadoria ainda não saiu da
     * loja. RETIRADA imediata conclui ({@link #concluded}); só a retirada "volto depois" reserva.
     */
    public static Order openBalcao(Long sessionId, String warehouseCode, Long customerId, List<OrderItem> items,
            OrderDelivery delivery) {
        Totals totals = Totals.from(items);
        return new Order(null, null, SalesChannel.BALCAO, OrderStatus.CRIADO, customerId, sessionId,
                warehouseCode, items, totals.gross(), totals.discount(), BigDecimal.ZERO, totals.net(),
                null, null, Instant.now(), null, null, null, null, null, null, null, null, 0L, null, null,
                BigDecimal.ZERO, delivery);
    }

    /**
     * Abre uma venda de <b>mesa</b> em {@link OrderStatus#CRIADO} — o pedido gerado pelo
     * fechamento de uma comanda (PDV-F010). Estado efêmero, como {@link #openBalcao}: quem chama
     * conclui na mesma transação.
     *
     * <p>O canal é <b>imutável</b>, então o pedido da mesa precisa <b>nascer</b> {@code MESA}: não
     * há caminho de "virar MESA depois". É por isso que esta fábrica existe em vez de um
     * {@code withChannel}.</p>
     *
     * <p>{@code customerId} é opcional, como no balcão — a mesa pode ser aberta sem vínculo de
     * cadastro. Quando vem preenchido, é ele que faz o pedido sair com nome e gerar cashback.</p>
     *
     * @param sessionId a sessão de caixa que <b>recebe</b> o pagamento, que não é necessariamente
     *        a que abriu a comanda: com mesas compartilhadas entre atendentes, quem fecha a mesa
     *        pode ser outro operador, e o dinheiro pertence à gaveta que o recebeu.
     * @param warehouseCode o depósito da <b>comanda</b>, não o de quem fecha — é de lá que o
     *        estoque já saiu, item a item, no lançamento.
     */
    public static Order openMesa(Long sessionId, String warehouseCode, Long customerId, Long comandaId,
            String tableLabel, List<OrderItem> items) {
        Totals totals = Totals.from(items);
        return new Order(null, null, SalesChannel.MESA, OrderStatus.CRIADO, customerId, sessionId,
                warehouseCode, items, totals.gross(), totals.discount(), BigDecimal.ZERO, totals.net(),
                null, null, Instant.now(), null, null, null, null, null, null, null, null, 0L,
                comandaId, tableLabel, BigDecimal.ZERO, null);
    }

    /**
     * Abre um pedido de marketplace em {@link OrderStatus#AGUARDANDO_PAGAMENTO} — o estado em que o
     * estoque já está reservado e a janela de pagamento está correndo.
     */
    public static Order openMarketplace(Long customerId, String warehouseCode, List<OrderItem> items) {
        Totals totals = Totals.from(items);
        return new Order(null, null, SalesChannel.MARKETPLACE, OrderStatus.AGUARDANDO_PAGAMENTO, customerId,
                null, warehouseCode, items, totals.gross(), totals.discount(), BigDecimal.ZERO, totals.net(),
                null, null, Instant.now(), null, null, null, null, null, null, null, null, 0L, null, null,
                BigDecimal.ZERO, null);
    }

    /**
     * Reconstitui um pedido a partir de persistência — forma anterior a PDV-F008, sem
     * {@code reservedAt} (dado legado, lê como {@code null}: pedido nunca passou por
     * {@code RESERVADO}).
     */
    public static Order of(Long id, String orderNumber, SalesChannel channel, OrderStatus status,
            Long customerId, Long sessionId, String warehouseCode, List<OrderItem> items,
            BigDecimal grossAmount, BigDecimal discountAmount, BigDecimal cashbackRedeemed,
            BigDecimal netAmount, BigDecimal changeAmount, String cancelReason, Instant createdAt,
            Instant paidAt, Instant concludedAt, Instant cancelledAt, Instant refundedAt, long version) {
        return of(id, orderNumber, channel, status, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, cancelledAt, refundedAt, null, version);
    }

    /**
     * Reconstitui um pedido a partir de persistência — forma anterior aos timestamps por etapa,
     * com {@code reservedAt} (PDV-F008) mas sem {@code separatedAt}/{@code shippedAt}/
     * {@code deliveredAt} (dado legado, lêem como {@code null}).
     */
    public static Order of(Long id, String orderNumber, SalesChannel channel, OrderStatus status,
            Long customerId, Long sessionId, String warehouseCode, List<OrderItem> items,
            BigDecimal grossAmount, BigDecimal discountAmount, BigDecimal cashbackRedeemed,
            BigDecimal netAmount, BigDecimal changeAmount, String cancelReason, Instant createdAt,
            Instant paidAt, Instant concludedAt, Instant cancelledAt, Instant refundedAt, Instant reservedAt,
            long version) {
        return of(id, orderNumber, channel, status, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, cancelledAt, refundedAt, reservedAt, null, null, null, version);
    }

    /**
     * Reconstitui um pedido a partir de persistência — forma canônica, com os timestamps por etapa
     * da esteira de fulfillment.
     */
    public static Order of(Long id, String orderNumber, SalesChannel channel, OrderStatus status,
            Long customerId, Long sessionId, String warehouseCode, List<OrderItem> items,
            BigDecimal grossAmount, BigDecimal discountAmount, BigDecimal cashbackRedeemed,
            BigDecimal netAmount, BigDecimal changeAmount, String cancelReason, Instant createdAt,
            Instant paidAt, Instant concludedAt, Instant cancelledAt, Instant refundedAt, Instant reservedAt,
            Instant separatedAt, Instant shippedAt, Instant deliveredAt, long version) {
        return of(id, orderNumber, channel, status, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, cancelledAt, refundedAt, reservedAt, separatedAt, shippedAt,
                deliveredAt, version, null, null);
    }

    /**
     * Reconstitui um pedido a partir de persistência — forma <b>com</b> a origem de mesa
     * (PDV-F010) e <b>sem</b> taxa de serviço (dado anterior a PDV-F015, lê como zero: nenhum
     * pedido gravado antes daquela entrega cobrou taxa).
     */
    public static Order of(Long id, String orderNumber, SalesChannel channel, OrderStatus status,
            Long customerId, Long sessionId, String warehouseCode, List<OrderItem> items,
            BigDecimal grossAmount, BigDecimal discountAmount, BigDecimal cashbackRedeemed,
            BigDecimal netAmount, BigDecimal changeAmount, String cancelReason, Instant createdAt,
            Instant paidAt, Instant concludedAt, Instant cancelledAt, Instant refundedAt, Instant reservedAt,
            Instant separatedAt, Instant shippedAt, Instant deliveredAt, long version, Long comandaId,
            String tableLabel) {
        return of(id, orderNumber, channel, status, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, cancelledAt, refundedAt, reservedAt, separatedAt, shippedAt,
                deliveredAt, version, comandaId, tableLabel, BigDecimal.ZERO);
    }

    /**
     * Reconstitui um pedido a partir de persistência — forma com a taxa de serviço (PDV-F015) e
     * sem entrega (dado anterior a PDV-F022, ou venda sem entrega).
     */
    public static Order of(Long id, String orderNumber, SalesChannel channel, OrderStatus status,
            Long customerId, Long sessionId, String warehouseCode, List<OrderItem> items,
            BigDecimal grossAmount, BigDecimal discountAmount, BigDecimal cashbackRedeemed,
            BigDecimal netAmount, BigDecimal changeAmount, String cancelReason, Instant createdAt,
            Instant paidAt, Instant concludedAt, Instant cancelledAt, Instant refundedAt, Instant reservedAt,
            Instant separatedAt, Instant shippedAt, Instant deliveredAt, long version, Long comandaId,
            String tableLabel, BigDecimal serviceFeeAmount) {
        return of(id, orderNumber, channel, status, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, cancelledAt, refundedAt, reservedAt, separatedAt, shippedAt,
                deliveredAt, version, comandaId, tableLabel, serviceFeeAmount, null);
    }

    /**
     * Reconstitui um pedido a partir de persistência — forma canônica, com a entrega (PDV-F022).
     */
    public static Order of(Long id, String orderNumber, SalesChannel channel, OrderStatus status,
            Long customerId, Long sessionId, String warehouseCode, List<OrderItem> items,
            BigDecimal grossAmount, BigDecimal discountAmount, BigDecimal cashbackRedeemed,
            BigDecimal netAmount, BigDecimal changeAmount, String cancelReason, Instant createdAt,
            Instant paidAt, Instant concludedAt, Instant cancelledAt, Instant refundedAt, Instant reservedAt,
            Instant separatedAt, Instant shippedAt, Instant deliveredAt, long version, Long comandaId,
            String tableLabel, BigDecimal serviceFeeAmount, OrderDelivery delivery) {
        return new Order(id, orderNumber, channel, status, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, cancelledAt, refundedAt, reservedAt, separatedAt, shippedAt,
                deliveredAt, version, comandaId, tableLabel, serviceFeeAmount, delivery);
    }

    /**
     * Conclui a venda: carimba o número do pedido e o instante da conclusão.
     *
     * @param orderNumber numeração emitida <b>agora</b>, por sequência própria — ver a nota de
     *        numeração na documentação do tipo
     * @param changeAmount troco devolvido, ou {@code null} quando não houve dinheiro em espécie
     */
    public Order concluded(String orderNumber, BigDecimal changeAmount, Instant concludedAt) {
        requireTransition(OrderStatus.CONCLUIDO);
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("orderNumber é obrigatório na conclusão do pedido");
        }
        return new Order(id, orderNumber, channel, OrderStatus.CONCLUIDO, customerId, sessionId,
                warehouseCode, items, grossAmount, discountAmount, cashbackRedeemed, netAmount,
                changeAmount, cancelReason, createdAt, paidAt == null ? concludedAt : paidAt,
                concludedAt, null, null, reservedAt, separatedAt, shippedAt, deliveredAt, version, comandaId, tableLabel, serviceFeeAmount, delivery);
    }

    /**
     * Marca a venda de balcão como reservada para retirada posterior (PDV-F008): pagamento
     * capturado e mercadoria já baixada do estoque — exatamente como {@link #concluded} — mas o
     * cliente ainda não levou a mercadoria. Só alcançável a partir de {@link OrderStatus#CRIADO},
     * o que restringe esta transição ao balcão por construção: pedido de marketplace nasce em
     * {@link OrderStatus#AGUARDANDO_PAGAMENTO} e nunca passa por {@code CRIADO}.
     *
     * <p>A numeração fiscal é consumida aqui, igual em {@link #concluded} — é o mesmo ponto de
     * "venda fixada", só o destino final (retirada na hora ou depois) que muda.</p>
     *
     * @param orderNumber numeração emitida <b>agora</b>, mesma regra de {@link #concluded}
     * @param changeAmount troco devolvido, ou {@code null} quando não houve dinheiro em espécie
     */
    public Order reserved(String orderNumber, BigDecimal changeAmount, Instant reservedAt) {
        requireTransition(OrderStatus.RESERVADO);
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("orderNumber é obrigatório ao reservar o pedido");
        }
        return new Order(id, orderNumber, channel, OrderStatus.RESERVADO, customerId, sessionId,
                warehouseCode, items, grossAmount, discountAmount, cashbackRedeemed, netAmount,
                changeAmount, cancelReason, createdAt, paidAt == null ? reservedAt : paidAt,
                null, null, null, reservedAt, separatedAt, shippedAt, deliveredAt, version, comandaId, tableLabel, serviceFeeAmount, delivery);
    }

    /**
     * Marca a retirada de um pedido reservado (PDV-F008): carimba {@code concludedAt}, distinto do
     * {@code withStatus} genérico usado pela esteira de fulfillment, que não carimba {@code concludedAt}
     * nenhum. Sem este método dedicado, {@code RESERVADO → CONCLUIDO} deixaria {@code concludedAt}
     * nulo para sempre, quebrando a garantia que {@link #concluded} estabelece em todo outro
     * caminho para {@code CONCLUIDO}.
     */
    public Order pickedUp(Instant concludedAt) {
        requireTransition(OrderStatus.CONCLUIDO);
        return new Order(id, orderNumber, channel, OrderStatus.CONCLUIDO, customerId, sessionId,
                warehouseCode, items, grossAmount, discountAmount, cashbackRedeemed, netAmount,
                changeAmount, cancelReason, createdAt, paidAt, concludedAt, null, null, reservedAt,
                separatedAt, shippedAt, deliveredAt, version, comandaId, tableLabel, serviceFeeAmount, delivery);
    }

    /** Marca o pagamento como confirmado — caminho do marketplace, disparado pelo webhook. */
    public Order paid(Instant paidAt) {
        requireTransition(OrderStatus.PAGO);
        return new Order(id, orderNumber, channel, OrderStatus.PAGO, customerId, sessionId, warehouseCode,
                items, grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, null, null, reservedAt, separatedAt, shippedAt, deliveredAt,
                version, comandaId, tableLabel, serviceFeeAmount, delivery);
    }

    /**
     * Avança o pedido na esteira de fulfillment ({@code SEPARADO → ENVIADO → ENTREGUE}), carimbando
     * o timestamp da etapa alcançada — {@code separatedAt}/{@code shippedAt}/{@code deliveredAt}.
     * Outros destinos (ex.: {@code REEMBOLSADO}) não carimbam nenhum desses três, só mudam o status.
     */
    public Order withStatus(OrderStatus newStatus) {
        requireTransition(newStatus);
        Instant now = Instant.now();
        Instant newSeparatedAt = newStatus == OrderStatus.SEPARADO ? now : separatedAt;
        Instant newShippedAt = newStatus == OrderStatus.ENVIADO ? now : shippedAt;
        Instant newDeliveredAt = newStatus == OrderStatus.ENTREGUE ? now : deliveredAt;
        return new Order(id, orderNumber, channel, newStatus, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, null, null, reservedAt, newSeparatedAt, newShippedAt,
                newDeliveredAt, version, comandaId, tableLabel, serviceFeeAmount, delivery);
    }

    /**
     * Cancela o pedido ANTES de qualquer pagamento confirmado — nunca houve dinheiro capturado
     * nem baixa real de estoque para desfazer. A liberação da reserva é responsabilidade do
     * service. Pedido com pagamento confirmado usa {@link #refunded} — cancelar e reembolsar são
     * eventos diferentes (PDV-F007).
     */
    public Order cancelled(String reason, Instant cancelledAt) {
        requireTransition(OrderStatus.CANCELADO);
        if (cancelledAt == null) {
            throw new IllegalArgumentException("cancelledAt é obrigatório no cancelamento");
        }
        return new Order(id, orderNumber, channel, OrderStatus.CANCELADO, customerId, sessionId,
                warehouseCode, items, grossAmount, discountAmount, cashbackRedeemed, netAmount,
                changeAmount, reason, createdAt, paidAt, concludedAt, cancelledAt, null, reservedAt,
                separatedAt, shippedAt, deliveredAt, version, comandaId, tableLabel, serviceFeeAmount, delivery);
    }

    /**
     * Reembolsa o pedido DEPOIS de pagamento confirmado. Os estornos de estoque, pagamento e
     * cashback são responsabilidade do service — aqui só se valida a transição e se carimba o
     * motivo e o instante.
     */
    public Order refunded(String reason, Instant refundedAt) {
        requireTransition(OrderStatus.REEMBOLSADO);
        if (refundedAt == null) {
            throw new IllegalArgumentException("refundedAt é obrigatório no reembolso");
        }
        return new Order(id, orderNumber, channel, OrderStatus.REEMBOLSADO, customerId, sessionId,
                warehouseCode, items, grossAmount, discountAmount, cashbackRedeemed, netAmount,
                changeAmount, reason, createdAt, paidAt, concludedAt, null, refundedAt, reservedAt,
                separatedAt, shippedAt, deliveredAt, version, comandaId, tableLabel, serviceFeeAmount, delivery);
    }

    /**
     * Registra o resgate de cashback, que abate do líquido a pagar.
     *
     * <p>Resgate <b>não</b> é forma de pagamento: é um desconto no pedido mais uma entrada no ledger
     * de pontos. Misturar com o pagamento faria o DRE contar a mesma receita duas vezes.</p>
     */
    public Order withCashbackRedeemed(BigDecimal redeemed) {
        BigDecimal value = redeemed == null ? BigDecimal.ZERO : redeemed;
        BigDecimal newNet = grossAmount.subtract(discountAmount).subtract(value);
        return new Order(id, orderNumber, channel, status, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, value, newNet, changeAmount, cancelReason, createdAt,
                paidAt, concludedAt, cancelledAt, refundedAt, reservedAt, separatedAt, shippedAt,
                deliveredAt, version, comandaId, tableLabel, serviceFeeAmount, delivery);
    }

    /**
     * Identificador que a reserva de estoque usa para apontar de volta para este pedido.
     *
     * <p>O formato mora aqui, e não espalhado por quem reserva e por quem consome, porque
     * {@code stock_reservation.owner_reference} é texto livre — não há FK que force os dois lados a
     * concordarem. O {@code COMMENT} da coluna na V64 já antecipava este formato.</p>
     */
    public static String reservationOwnerReference(Long orderId) {
        return "ORDER:" + orderId;
    }

    /**
     * Vincula o pedido à sessão de caixa que o liquidou. É o que acontece quando um pedido montado
     * no aplicativo é pago no balcão: o canal continua {@code MARKETPLACE}, mas o dinheiro entrou
     * numa gaveta específica e o fechamento dela precisa contabilizá-lo.
     */
    public Order withSession(Long newSessionId) {
        return new Order(id, orderNumber, channel, status, customerId, newSessionId, warehouseCode,
                items, grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount,
                cancelReason, createdAt, paidAt, concludedAt, cancelledAt, refundedAt, reservedAt,
                separatedAt, shippedAt, deliveredAt, version, comandaId, tableLabel, serviceFeeAmount, delivery);
    }

    /** Vincula o pedido a um cliente identificado depois da montagem — o "CPF na nota?" do balcão. */
    public Order withCustomer(Long newCustomerId) {
        return new Order(id, orderNumber, channel, status, newCustomerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, cancelledAt, refundedAt, reservedAt, separatedAt, shippedAt,
                deliveredAt, version, comandaId, tableLabel, serviceFeeAmount, delivery);
    }

    /**
     * O que o cliente efetivamente paga: {@link #netAmount} <b>mais</b> a taxa de serviço
     * (PDV-F015).
     *
     * <p>É contra este valor — e não contra o líquido — que o pagamento é validado e o troco
     * calculado. A distinção existe porque os dois números respondem a perguntas diferentes:
     * {@code netAmount} é quanto a loja vendeu, {@code totalPayable} é quanto entrou na gaveta.
     * Fora da mesa os dois coincidem sempre, porque a taxa é zero.</p>
     */
    public BigDecimal totalPayable() {
        return netAmount.add(serviceFeeAmount).add(deliveryFee());
    }

    /** Taxa de entrega (PDV-F022), zero quando não há entrega. Fora do líquido, como a taxa de serviço. */
    public BigDecimal deliveryFee() {
        return delivery == null ? BigDecimal.ZERO : delivery.fee();
    }

    /**
     * Troca os dados de entrega — o {@code PATCH /orders/{id}/delivery} (PDV-F022). Quem valida o
     * que pode mudar é {@link OrderDelivery#withPatch}: tipo e taxa ficam congelados.
     */
    public Order withDelivery(OrderDelivery newDelivery) {
        return new Order(id, orderNumber, channel, status, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, cancelledAt, refundedAt, reservedAt, separatedAt, shippedAt,
                deliveredAt, version, comandaId, tableLabel, serviceFeeAmount, newDelivery);
    }

    /**
     * Estados alcançáveis a partir do atual, para ESTE pedido. Difere de
     * {@link OrderStatus#allowedTransitions()} num ponto só (PDV-F022): uma venda reservada com
     * {@link DeliveryType#ENTREGA} segue a esteira de expedição ({@code RESERVADO → SEPARADO}); a
     * reservada para retirada não — ela termina no balcão, via {@link #pickedUp}.
     */
    public Set<OrderStatus> allowedTransitions() {
        Set<OrderStatus> allowed = status.allowedTransitions();
        if (status == OrderStatus.RESERVADO && (delivery == null || !delivery.isEntrega())) {
            EnumSet<OrderStatus> copy = EnumSet.noneOf(OrderStatus.class);
            copy.addAll(allowed);
            copy.remove(OrderStatus.SEPARADO);
            return Collections.unmodifiableSet(copy);
        }
        return allowed;
    }

    /**
     * Aplica a taxa de serviço como um percentual sobre o líquido (PDV-F015).
     *
     * <p>O cálculo mora aqui, e não no service, porque a taxa é uma regra do pedido: a base é o
     * líquido — depois do desconto, portanto —, e deixar a conta do lado de fora abriria caminho
     * para o valor chegar pronto do cliente HTTP, que é exatamente o que este módulo não faz com
     * dinheiro desde PDV-F004.</p>
     *
     * <p>Percentual zero (ou nulo) devolve o pedido intacto em vez de carimbar um zero — não é a
     * mesma coisa que uma taxa de R$ 0,00 recusada pelo cliente, mas o resultado gravado é o
     * mesmo, e não vale um campo a mais para distinguir.</p>
     */
    public Order withServiceFeeOf(BigDecimal percent) {
        if (percent == null || percent.signum() <= 0) {
            return this;
        }
        BigDecimal fee = netAmount.multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        return new Order(id, orderNumber, channel, status, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, cancelledAt, refundedAt, reservedAt, separatedAt, shippedAt,
                deliveredAt, version, comandaId, tableLabel, fee, delivery);
    }

    /**
     * PDV-F023 — taxa de serviço só sobre as linhas de catálogo: a sessão de narguilé
     * ({@code SESSAO}/{@code ROSH_EXTRA}) nunca leva os 10%, qualquer que seja o pedido do cliente
     * HTTP. A base é o líquido de cada linha, então o desconto já rateado continua abatido dela.
     */
    public Order withServiceFeeOnCatalogLines(BigDecimal percent) {
        if (percent == null || percent.signum() <= 0) {
            return this;
        }
        BigDecimal base = items.stream()
                .filter(i -> i.mode() == null || i.mode().isCatalogLine())
                .map(OrderItem::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (base.signum() <= 0) {
            return this;
        }
        BigDecimal fee = base.multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        return new Order(id, orderNumber, channel, status, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, cancelledAt, refundedAt, reservedAt, separatedAt, shippedAt,
                deliveredAt, version, comandaId, tableLabel, fee, delivery);
    }

    /** Soma do cashback gerado por todos os itens; ignora itens sem taxa carimbada. */
    public BigDecimal totalCashbackEarned() {
        return items.stream()
                .map(OrderItem::cashbackAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Indica se o pedido ainda pode mudar de estado. */
    public boolean isCancelled() {
        return status == OrderStatus.CANCELADO;
    }

    private void requireTransition(OrderStatus target) {
        Set<OrderStatus> allowed = allowedTransitions();
        if (target == null || !allowed.contains(target)) {
            throw new InvalidOrderStatusTransitionException(id, status, target, allowed);
        }
    }

    /** Totais derivados dos itens. O servidor calcula; o cliente HTTP nunca informa. */
    private record Totals(BigDecimal gross, BigDecimal discount, BigDecimal net) {

        static Totals from(List<OrderItem> items) {
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("items não pode ser vazio");
            }
            BigDecimal gross = BigDecimal.ZERO;
            BigDecimal discount = BigDecimal.ZERO;
            for (OrderItem item : items) {
                BigDecimal itemGross = item.grossAmount();
                if (itemGross == null) {
                    throw new IllegalArgumentException(
                            "item sem preço congelado não pode compor pedido novo: " + item.sku());
                }
                gross = gross.add(itemGross);
                discount = discount.add(item.discountAmount());
            }
            return new Totals(gross, discount, gross.subtract(discount));
        }
    }
}
