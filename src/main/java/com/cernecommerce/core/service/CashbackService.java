package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.domain.exception.cashback.CashbackRateAlreadyExistsException;
import com.cernecommerce.core.domain.exception.cashback.CashbackRateNotFoundException;
import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.model.Money;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackMarginImpactItem;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.cashback.CashbackScope;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.cashback.CashbackEntryRepository;
import com.cernecommerce.core.ports.out.cashback.CashbackRateRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Programa de cashback (CRM-F003, §2.4 do plano de arquitetura).
 *
 * <p>Esta fatia cobre ganhar cashback e as consultas de saldo/extrato/margem. Resgate e ajuste
 * manual ficam para uma fatia seguinte — ver {@link CashbackUseCase}.</p>
 */
public class CashbackService implements CashbackUseCase {

    private static final int MARGIN_IMPACT_PAGE_SIZE = 200;
    private static final int EXPIRING_SOON_WINDOW_DAYS = 30;

    private final CashbackRateRepository cashbackRateRepository;
    private final CashbackEntryRepository cashbackEntryRepository;
    private final EstoqueUseCase estoqueUseCase;
    private final CustomerRepository customerRepository;
    private final SystemConfigPort systemConfigPort;
    private final OrderPaymentRepository orderPaymentRepository;

    /**
     * Lock em memória por {@code orderId}, exclusivo de {@link #recordEarnedForOrder} — achado
     * real de {@code CashbackLedgerConcurrencyIT}: nada impedia duas chamadas concorrentes para o
     * MESMO pedido de duplicarem o ganho. Nunca removido do mapa (cresce no máximo um lançamento
     * por pedido que já passou por aqui — irrelevante em memória, e evita a corrida clássica de
     * limpar uma entrada enquanto outra thread ainda a segura). Protege só contra duas chamadas
     * concorrentes NESTA instância — não substitui a unicidade que valeria entre processos
     * diferentes, que este esqueleto de único-processo ainda não precisa.
     */
    private final ConcurrentHashMap<Long, Object> earnedLocksByOrderId = new ConcurrentHashMap<>();

    public CashbackService(CashbackRateRepository cashbackRateRepository,
            CashbackEntryRepository cashbackEntryRepository, EstoqueUseCase estoqueUseCase,
            CustomerRepository customerRepository, SystemConfigPort systemConfigPort,
            OrderPaymentRepository orderPaymentRepository) {
        this.cashbackRateRepository = cashbackRateRepository;
        this.cashbackEntryRepository = cashbackEntryRepository;
        this.estoqueUseCase = estoqueUseCase;
        this.customerRepository = customerRepository;
        this.systemConfigPort = systemConfigPort;
        this.orderPaymentRepository = orderPaymentRepository;
    }

    @Override
    @Transactional
    public CashbackRate createRate(CashbackScope scope, String scopeRef, BigDecimal percent,
            Instant validFrom, Instant validTo) {
        if (cashbackRateRepository.existsActiveOpenEnded(scope, scopeRef)) {
            throw new CashbackRateAlreadyExistsException(scope, scopeRef);
        }
        Instant from = validFrom == null ? Instant.now() : validFrom;
        CashbackRate rate = new CashbackRate(null, scope, scopeRef, percent, true, from, validTo, Instant.now());
        return cashbackRateRepository.save(rate);
    }

    @Override
    @Transactional
    public CashbackRate patchRate(Long id, BigDecimal percent, Boolean active, Instant validTo) {
        CashbackRate current = cashbackRateRepository.findById(id)
                .orElseThrow(() -> new CashbackRateNotFoundException(id));
        return cashbackRateRepository.save(current.withPatch(percent, active, validTo));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CashbackRate> listRates(int page, int size) {
        return cashbackRateRepository.findAll(page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public CashbackRate resolveApplicableRate(String sku) {
        Product product = estoqueUseCase.findProductBySku(sku);
        return resolveApplicableRate(sku, product.category());
    }

    @Override
    @Transactional(readOnly = true)
    public CashbackRate resolveApplicableRate(String sku, String category) {
        return cashbackRateRepository.findApplicable(sku, category, Instant.now()).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashbackMarginImpactItem> findMarginImpact(BigDecimal maxSharePercent) {
        List<CashbackMarginImpactItem> result = new ArrayList<>();
        Instant now = Instant.now();
        int page = 0;
        PageResult<Product> products;
        do {
            products = estoqueUseCase.listProducts(page, MARGIN_IMPACT_PAGE_SIZE);
            for (Product product : products.content()) {
                // findPricingBySku, não product.pricing() direto: para um kit (EST-F015), o
                // costPrice cru é sempre nulo (só é derivado na leitura), então ler a Pricing
                // crua excluiria todo kit deste relatório — justo o contrário do que
                // marginPercent() promete cobrir "sem uma linha de matemática nova".
                BigDecimal marginPercent = estoqueUseCase.findPricingBySku(product.sku()).marginPercent();
                if (marginPercent == null || marginPercent.signum() == 0) {
                    continue;
                }
                Optional<CashbackRate> rate = cashbackRateRepository.findApplicable(
                        product.sku(), product.category(), now);
                if (rate.isEmpty()) {
                    continue;
                }
                BigDecimal cashbackPercent = rate.get().percent();
                BigDecimal share = cashbackPercent
                        .divide(marginPercent, Money.INTERMEDIATE_SCALE, Money.ROUNDING)
                        .multiply(Money.HUNDRED)
                        .setScale(Money.PERCENT_SCALE, Money.ROUNDING);
                if (share.compareTo(maxSharePercent) > 0) {
                    result.add(new CashbackMarginImpactItem(product.sku(), product.name(),
                            marginPercent, cashbackPercent, share));
                }
            }
            page++;
        } while (page < products.totalPages());
        return result;
    }

    @Override
    @Transactional
    public void recordEarnedForOrder(Order order) {
        if (order.customerId() == null) {
            // Venda anônima de passagem — sem cliente não há para quem lançar o ganho.
            return;
        }
        Customer customer = customerRepository.findById(order.customerId())
                .orElseThrow(() -> new CustomerNotFoundException(order.customerId()));
        if (!customer.isOfficiallyRegistered()) {
            // Cliente "leve" (sem CPF) não acumula cashback — regra de elegibilidade da Fatia 4.
            return;
        }

        // Idempotência por pedido sob chamada concorrente (achado de CashbackLedgerConcurrencyIT):
        // nenhum caminho de produção hoje chama isto duas vezes para o mesmo pedido de propósito
        // (registerSale e PaymentWebhookService chamam uma única vez, dentro da transação que
        // confirma o pedido) — mas nada impedia que chamasse. O lock é por orderId para não
        // travar pedidos diferentes entre si; a checagem por dentro do lock ignora reversão de
        // propósito (existsEarnedForOrder, não findEarnedByOrderId) — um pedido já revertido não
        // pode "ganhar" cashback de novo só porque a entrada original não conta mais como ativa.
        synchronized (earnedLocksByOrderId.computeIfAbsent(order.id(), id -> new Object())) {
            if (cashbackEntryRepository.existsEarnedForOrder(order.id())) {
                return;
            }

            Instant anchor = order.paidAt() != null ? order.paidAt() : Instant.now();
            int carenciaDias = systemConfigPort.getInt("cashback.carencia.dias", 7);
            int expiracaoDias = systemConfigPort.getInt("cashback.expiracao.dias", 180);
            Instant availableAt = anchor.plus(carenciaDias, ChronoUnit.DAYS);
            Instant expiresAt = availableAt.plus(expiracaoDias, ChronoUnit.DAYS);
            Instant now = Instant.now();

            // CRM-F010 — a parte MARCADA ainda não foi paga: o ganho dela vem na quitação
            // (recordEarnedForReceivablePayment). Aqui só a fração efetivamente paga.
            BigDecimal paidFraction = paidFraction(order);

            for (OrderItem item : order.items()) {
                BigDecimal amount = item.cashbackAmount();
                if (amount != null && paidFraction.compareTo(BigDecimal.ONE) < 0) {
                    amount = amount.multiply(paidFraction).setScale(Money.MONEY_SCALE, Money.ROUNDING);
                }
                if (amount != null && amount.signum() > 0) {
                    cashbackEntryRepository.save(CashbackEntry.earned(order.customerId(), order.id(),
                            item.id(), amount, now, availableAt, expiresAt));
                }
            }
        }
    }

    /** Fração do total a pagar que não foi MARCADA: 1 numa venda sem marcar. */
    private BigDecimal paidFraction(Order order) {
        BigDecimal total = order.totalPayable();
        if (total.signum() <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal onAccount = orderPaymentRepository.findByOrderId(order.id()).stream()
                .filter(p -> p.status() == PaymentStatus.ON_ACCOUNT)
                .map(OrderPayment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (onAccount.signum() == 0) {
            return BigDecimal.ONE;
        }
        return total.subtract(onAccount).max(BigDecimal.ZERO)
                .divide(total, Money.INTERMEDIATE_SCALE, Money.ROUNDING);
    }

    @Override
    @Transactional
    public void recordEarnedForReceivablePayment(Order order, BigDecimal settledAmount) {
        if (order.customerId() == null || settledAmount == null || settledAmount.signum() <= 0
                || order.totalPayable().signum() <= 0) {
            return;
        }
        Customer customer = customerRepository.findById(order.customerId())
                .orElseThrow(() -> new CustomerNotFoundException(order.customerId()));
        if (!customer.isOfficiallyRegistered()) {
            return;
        }
        BigDecimal orderCashback = order.items().stream()
                .map(OrderItem::cashbackAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = orderCashback.multiply(settledAmount)
                .divide(order.totalPayable(), Money.MONEY_SCALE, Money.ROUNDING);
        if (amount.signum() <= 0) {
            return;
        }
        Instant now = Instant.now();
        int carenciaDias = systemConfigPort.getInt("cashback.carencia.dias", 7);
        int expiracaoDias = systemConfigPort.getInt("cashback.expiracao.dias", 180);
        Instant availableAt = now.plus(carenciaDias, ChronoUnit.DAYS);
        cashbackEntryRepository.save(CashbackEntry.earned(order.customerId(), order.id(), null, amount, now,
                availableAt, availableAt.plus(expiracaoDias, ChronoUnit.DAYS)));
    }

    @Override
    @Transactional
    public void reverseEarningsForOrder(Order order) {
        Instant now = Instant.now();
        for (CashbackEntry earned : cashbackEntryRepository.findEarnedByOrderId(order.id())) {
            cashbackEntryRepository.save(CashbackEntry.reversed(earned, now));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CashbackBalance getCustomerBalance(Long customerId) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        Instant now = Instant.now();
        BigDecimal available = cashbackEntryRepository.sumAvailableByCustomerId(customerId, now);
        BigDecimal pending = cashbackEntryRepository.sumPendingByCustomerId(customerId, now);
        BigDecimal expiringSoon = cashbackEntryRepository.sumExpiringSoonByCustomerId(customerId, now,
                now.plus(EXPIRING_SOON_WINDOW_DAYS, ChronoUnit.DAYS));
        return new CashbackBalance(available, pending, expiringSoon);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CashbackEntry> listCustomerEntries(Long customerId, int page, int size) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        return cashbackEntryRepository.findByCustomerId(customerId, page, size);
    }

    @Override
    @Transactional
    public int expireEntries(int batchSize) {
        Instant now = Instant.now();
        List<CashbackEntry> pendingExpiry = cashbackEntryRepository.findEarnedPendingExpiry(now, batchSize);
        for (CashbackEntry entry : pendingExpiry) {
            cashbackEntryRepository.save(CashbackEntry.expired(entry, now));
        }
        return pendingExpiry.size();
    }
}
