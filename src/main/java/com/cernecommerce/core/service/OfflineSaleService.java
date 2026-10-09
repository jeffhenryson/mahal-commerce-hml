package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.ParentNotSellableException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.ReservedStockException;
import com.cernecommerce.core.domain.exception.pagamento.InsufficientPaymentException;
import com.cernecommerce.core.domain.exception.pagamento.PaymentExceedsOrderTotalException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.exception.pdv.OfflinePaymentNotAllowedException;
import com.cernecommerce.core.domain.exception.pdv.OfflineRejectionNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.OfflineSoldAtOutOfWindowException;
import com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException;
import com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.OfflineSaleItem;
import com.cernecommerce.core.domain.model.pdv.OfflineSalePayment;
import com.cernecommerce.core.domain.model.pdv.OfflineSaleRejection;
import com.cernecommerce.core.ports.in.OfflineSaleUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleRegistration;
import com.cernecommerce.core.ports.out.event.AuditEventPublisherPort;
import com.cernecommerce.core.ports.out.pdv.OfflineSaleRejectionRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Venda offline no balcão (PDV-F043): a fila do caixa chega de uma vez quando a rede volta.
 *
 * <p><b>Sem {@code @Transactional} de propósito.</b> Cada venda vai por
 * {@link PdvUseCase#registerSaleIdempotent}, pelo proxy, e roda na sua própria transação: a que falha
 * reverte sozinha, e as outras do lote ficam gravadas. Uma transação só para o lote faria uma lata de
 * carvão sem saldo derrubar o turno inteiro de vendas.</p>
 *
 * <p>Decisão do dono (2026-10-08): venda que não entra não entra sozinha nem some — fica guardada
 * em {@code offline_sale_rejection} para revisão, e o caixa não fecha até ela ser reenviada ou
 * descartada ({@code PdvService.closeSession}).</p>
 */
public class OfflineSaleService implements OfflineSaleUseCase {

    /** Folga de relógio entre o terminal e o servidor para o horário da venda. */
    static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(5);

    /**
     * O código de erro da recusa é o mesmo que a venda online responderia — o frontend já sabe tratar
     * cada um. A tabela mora aqui, e não no {@code GlobalExceptionHandler}, porque a recusa é gravada
     * pelo core e o adapter não está no caminho.
     */
    private static final Map<Class<? extends RuntimeException>, String> ERROR_CODES = new LinkedHashMap<>();

    static {
        ERROR_CODES.put(InsufficientStockException.class, "INSUFFICIENT_STOCK");
        ERROR_CODES.put(ReservedStockException.class, "RESERVED_STOCK");
        ERROR_CODES.put(ProductNotFoundException.class, "PRODUCT_NOT_FOUND");
        ERROR_CODES.put(ProductNotPricedException.class, "PRODUCT_NOT_PRICED");
        ERROR_CODES.put(ParentNotSellableException.class, "PARENT_NOT_SELLABLE");
        ERROR_CODES.put(DiscountLimitExceededException.class, "DISCOUNT_LIMIT_EXCEEDED");
        ERROR_CODES.put(InsufficientPaymentException.class, "INSUFFICIENT_PAYMENT");
        ERROR_CODES.put(PaymentExceedsOrderTotalException.class, "PAYMENT_EXCEEDS_ORDER_TOTAL");
    }

    private final PdvUseCase pdvUseCase;
    private final OfflineSaleRejectionRepository rejections;
    private final AuditEventPublisherPort auditEvents;

    public OfflineSaleService(PdvUseCase pdvUseCase, OfflineSaleRejectionRepository rejections,
            AuditEventPublisherPort auditEvents) {
        this.pdvUseCase = pdvUseCase;
        this.rejections = rejections;
        this.auditEvents = auditEvents;
    }

    @Override
    public List<SyncResult> sync(Long sessionId, List<OfflineSaleCommand> sales, String username) {
        CashRegisterSession session = pdvUseCase.getSession(sessionId);
        if (!session.isOpen()) {
            throw new CashRegisterSessionClosedException(sessionId);
        }
        if (!session.belongsTo(username)) {
            throw new CashRegisterSessionNotOwnedException(sessionId, username);
        }
        // O lote é validado inteiro antes da primeira venda: forma de pagamento sem rede e horário
        // fora do caixa são defeito de quem montou a fila, não de uma venda — nada entra pela metade.
        Instant latest = Instant.now().plus(CLOCK_TOLERANCE);
        for (OfflineSaleCommand sale : sales) {
            for (OfflineSalePayment payment : sale.payments()) {
                if (!payment.allowedOffline()) {
                    throw new OfflinePaymentNotAllowedException(sale.clientSaleId(), payment.method().name());
                }
            }
            if (sale.soldAt().isBefore(session.openedAt()) || sale.soldAt().isAfter(latest)) {
                throw new OfflineSoldAtOutOfWindowException(sale.clientSaleId(), sale.soldAt(), session.openedAt());
            }
        }

        List<SyncResult> results = new ArrayList<>(sales.size());
        int synced = 0;
        int duplicates = 0;
        int rejected = 0;
        for (OfflineSaleCommand sale : sales) {
            SyncResult result = syncOne(session, sale, username);
            results.add(result);
            switch (result.status()) {
                case SYNCED -> synced++;
                case DUPLICATE -> duplicates++;
                case REJECTED -> rejected++;
            }
        }
        auditEvents.publish(AuditEvent.of(AuditEvent.EventType.OFFLINE_SALES_SYNCED, username, Map.of(
                "sessionId", sessionId, "synced", synced, "duplicates", duplicates, "rejected", rejected)));
        return results;
    }

    private SyncResult syncOne(CashRegisterSession session, OfflineSaleCommand sale, String username) {
        // Venda que já foi recusada antes e chegou de novo (a fila reenviou): devolve a mesma recusa.
        // Já resolvida por reenvio, é duplicada do pedido que gerou.
        Optional<OfflineSaleRejection> previous = rejections.findByClientSaleId(sale.clientSaleId());
        if (previous.isPresent()) {
            OfflineSaleRejection r = previous.get();
            return r.orderId() != null
                    ? SyncResult.synced(sale.clientSaleId(), r.orderId(), true)
                    : SyncResult.rejected(r);
        }
        try {
            SaleRegistration registration = register(session.id(), sale.customerId(), sale.items(), sale.payments(),
                    username, sale.clientSaleId(), sale.soldAt());
            return SyncResult.synced(sale.clientSaleId(), registration.order().id(), registration.replayed());
        } catch (RuntimeException e) {
            // Dois envios simultâneos da mesma venda: o índice único barrou este, o outro gravou.
            Optional<Long> concurrent = pdvUseCase.findOrderIdByClientSaleId(sale.clientSaleId());
            if (concurrent.isPresent()) {
                return SyncResult.synced(sale.clientSaleId(), concurrent.get(), true);
            }
            OfflineSaleRejection saved = rejections.save(OfflineSaleRejection.create(session.id(),
                    sale.clientSaleId(), sale.soldAt(), sale.customerId(), sale.items(), sale.payments(),
                    errorCodeOf(e), e.getMessage(), username, Instant.now()));
            auditEvents.publish(AuditEvent.of(AuditEvent.EventType.OFFLINE_SALE_REJECTED, username, Map.of(
                    "sessionId", session.id(), "clientSaleId", sale.clientSaleId(), "rejectionId", saved.id(),
                    "errorCode", saved.errorCode())));
            return SyncResult.rejected(saved);
        }
    }

    @Override
    public List<OfflineSaleRejection> listRejections(Long sessionId) {
        pdvUseCase.getSession(sessionId);
        return rejections.findBySessionId(sessionId);
    }

    @Override
    public OfflineSaleRejection retry(Long rejectionId, String reviewer) {
        OfflineSaleRejection rejection = requireRejection(rejectionId);
        CashRegisterSession session = pdvUseCase.getSession(rejection.sessionId());
        if (!rejection.isPending()) {
            // Deixa o domínio dizer o porquê (409), em vez de reenviar e descobrir depois.
            rejection.retried(null, reviewer, Instant.now());
        }
        try {
            // Em nome do OPERADOR do caixa: a venda é dele, e a checagem de dono de PdvService
            // recusaria o revisor. Quem reenviou fica em resolvedBy.
            SaleRegistration registration = register(session.id(), rejection.customerId(), rejection.items(),
                    rejection.payments(), session.operator(), rejection.clientSaleId(), rejection.clientSoldAt());
            OfflineSaleRejection resolved = rejections.save(
                    rejection.retried(registration.order().id(), reviewer, Instant.now()));
            auditEvents.publish(AuditEvent.of(AuditEvent.EventType.OFFLINE_SALE_RETRIED, reviewer, Map.of(
                    "rejectionId", rejectionId, "orderId", registration.order().id())));
            return resolved;
        } catch (RuntimeException e) {
            Optional<Long> concurrent = pdvUseCase.findOrderIdByClientSaleId(rejection.clientSaleId());
            if (concurrent.isPresent()) {
                return rejections.save(rejection.retried(concurrent.get(), reviewer, Instant.now()));
            }
            return rejections.save(rejection.stillFailing(errorCodeOf(e), e.getMessage()));
        }
    }

    @Override
    public OfflineSaleRejection discard(Long rejectionId, String reason, String reviewer) {
        OfflineSaleRejection discarded = rejections.save(
                requireRejection(rejectionId).discarded(reason, reviewer, Instant.now()));
        auditEvents.publish(AuditEvent.of(AuditEvent.EventType.OFFLINE_SALE_DISCARDED, reviewer, Map.of(
                "rejectionId", rejectionId, "reason", discarded.resolutionNote())));
        return discarded;
    }

    private SaleRegistration register(Long sessionId, Long customerId, List<OfflineSaleItem> items,
            List<OfflineSalePayment> payments, String operator, String clientSaleId, Instant soldAt) {
        List<SaleItemCommand> itemCommands = items.stream()
                .map(i -> new SaleItemCommand(i.sku(), i.quantity(), i.discountAmount(), i.note()))
                .toList();
        List<PaymentCommand> paymentCommands = payments.stream()
                .map(p -> new PaymentCommand(p.method(), p.amount(), p.installments()))
                .toList();
        return pdvUseCase.registerSaleIdempotent(sessionId, customerId, itemCommands, paymentCommands, operator,
                false, null, clientSaleId, soldAt);
    }

    private OfflineSaleRejection requireRejection(Long rejectionId) {
        return rejections.findById(rejectionId).orElseThrow(() -> new OfflineRejectionNotFoundException(rejectionId));
    }

    private static String errorCodeOf(RuntimeException e) {
        return ERROR_CODES.entrySet().stream()
                .filter(entry -> entry.getKey().isInstance(e))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("SALE_REJECTED");
    }
}
