package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.domain.exception.cashback.CashbackRateAlreadyExistsException;
import com.cernecommerce.core.domain.exception.cashback.CashbackRateNotFoundException;
import com.cernecommerce.core.domain.exception.cashback.CashbackRedemptionNotAllowedException;
import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.model.Money;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackMarginImpactItem;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.cashback.CashbackRedemptionAllocation;
import com.cernecommerce.core.domain.model.cashback.CashbackRedemptionQuote;
import com.cernecommerce.core.domain.model.cashback.CashbackSettings;
import com.cernecommerce.core.domain.model.cashback.RedeemableEarning;
import com.cernecommerce.core.domain.model.config.SystemConfig;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Programa de cashback (CRM-F003, §2.4 do plano de arquitetura).
 *
 * <p>Ganhar cashback, consultas de saldo/extrato/margem e, desde a V186, o resgate (com alocação
 * FIFO nos ganhos) e os parâmetros que o admin ajusta — ver {@link CashbackUseCase}.</p>
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
            CashbackSettings settings = getSettings();
            Instant availableAt = anchor.plus(settings.carenciaDias(), ChronoUnit.DAYS);
            Instant expiresAt = availableAt.plus(settings.expiracaoDias(), ChronoUnit.DAYS);
            Instant now = Instant.now();

            // CRM-F010 — a parte MARCADA ainda não foi paga: o ganho dela vem na quitação
            // (recordEarnedForReceivablePayment). Aqui só a fração efetivamente paga.
            // V186 — e a parte paga com cashback resgatado também não gera cashback: o ganho sai
            // sobre o líquido efetivamente pago.
            BigDecimal paidFraction = paidFraction(order).multiply(order.cashbackEarningFraction());

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
        BigDecimal amount = receivablePaymentCashback(order, settledAmount);
        if (amount.signum() <= 0) {
            return;
        }
        Instant now = Instant.now();
        CashbackSettings settings = getSettings();
        Instant availableAt = now.plus(settings.carenciaDias(), ChronoUnit.DAYS);
        cashbackEntryRepository.save(CashbackEntry.earned(order.customerId(), order.id(), null, amount, now,
                availableAt, availableAt.plus(settings.expiracaoDias(), ChronoUnit.DAYS)));
    }

    /** A fração do cashback do pedido proporcional a {@code settledAmount / totalPayable}. */
    private static BigDecimal receivablePaymentCashback(Order order, BigDecimal settledAmount) {
        BigDecimal orderCashback = order.items().stream()
                .map(OrderItem::cashbackAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(order.cashbackEarningFraction());
        return orderCashback.multiply(settledAmount)
                .divide(order.totalPayable(), Money.MONEY_SCALE, Money.ROUNDING);
    }

    /** V173 — ganho de quitação mais distante que isto do lote não é dele. */
    private static final java.time.Duration RECEIVABLE_EARNING_MATCH_WINDOW = java.time.Duration.ofMinutes(5);

    @Override
    @Transactional
    public void reverseEarnedForReceivablePayment(Order order, BigDecimal settledAmount, Instant settledAt) {
        if (order.customerId() == null || settledAmount == null || settledAmount.signum() <= 0
                || order.totalPayable().signum() <= 0) {
            return;
        }
        BigDecimal amount = receivablePaymentCashback(order, settledAmount);
        // Mesmo valor primeiro; entre iguais, o lançado mais perto do lote — dois lotes do mesmo
        // pedido podem sair com segundos de diferença.
        Comparator<CashbackEntry> closest = Comparator
                .comparing((CashbackEntry e) -> e.amount().compareTo(amount) == 0 ? 0 : 1)
                .thenComparing(e -> java.time.Duration.between(e.createdAt(), settledAt).abs());
        cashbackEntryRepository.findEarnedByOrderId(order.id()).stream()
                .filter(e -> e.orderItemId() == null)
                .filter(e -> java.time.Duration.between(e.createdAt(), settledAt).abs()
                        .compareTo(RECEIVABLE_EARNING_MATCH_WINDOW) <= 0)
                .min(closest)
                .ifPresent(e -> cashbackEntryRepository.save(CashbackEntry.reversed(e, Instant.now())));
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
    @Transactional
    public void reverseEarningsForOrderItems(Order order, java.util.Collection<Long> orderItemIds) {
        if (orderItemIds == null || orderItemIds.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (CashbackEntry earned : cashbackEntryRepository.findEarnedByOrderId(order.id())) {
            if (earned.orderItemId() != null && orderItemIds.contains(earned.orderItemId())) {
                cashbackEntryRepository.save(CashbackEntry.reversed(earned, now));
            }
        }
    }

    @Override
    @Transactional
    public CashbackEntry revokeEntry(Long customerId, Long entryId) {
        customerRepository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
        CashbackEntry entry = cashbackEntryRepository.findById(entryId)
                .filter(e -> customerId.equals(e.customerId()))
                .orElseThrow(() -> new com.cernecommerce.core.domain.exception.cashback.CashbackEntryNotFoundException(
                        entryId));
        if (entry.type() != com.cernecommerce.core.domain.model.cashback.CashbackEntryType.EARNED) {
            throw new com.cernecommerce.core.domain.exception.cashback.CashbackEntryNotRevocableException(entryId,
                    "só um ganho (EARNED) pode ser revogado");
        }
        if (cashbackEntryRepository.isReversed(entryId)) {
            throw new com.cernecommerce.core.domain.exception.cashback.CashbackEntryNotRevocableException(entryId,
                    "já foi revertido ou expirado");
        }
        return cashbackEntryRepository.save(CashbackEntry.reversed(entry, Instant.now()));
    }

    @Override
    @Transactional
    public CashbackBalance releasePending(Long customerId) {
        customerRepository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
        cashbackEntryRepository.releasePendingEarned(customerId, Instant.now());
        return getCustomerBalance(customerId);
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
        int expired = 0;
        for (CashbackEntry entry : pendingExpiry) {
            // V186 — expira só a sobra que nenhum resgate consumiu. A trava do cliente fecha a
            // corrida com um resgate em andamento consumindo este mesmo ganho.
            cashbackEntryRepository.lockCustomer(entry.customerId());
            if (cashbackEntryRepository.isReversed(entry.id())) {
                continue;
            }
            BigDecimal remaining = entry.amount().subtract(consumedOf(entry.id()));
            if (remaining.signum() > 0) {
                cashbackEntryRepository.save(CashbackEntry.expired(entry, remaining, now));
                expired++;
            }
        }
        return expired;
    }

    private BigDecimal consumedOf(Long earnedId) {
        return cashbackEntryRepository.sumConsumedByEarnedIds(List.of(earnedId))
                .getOrDefault(earnedId, BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------------------------------
    // V186 — parâmetros do programa
    // ------------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CashbackSettings getSettings() {
        return new CashbackSettings(
                systemConfigPort.getInt(CashbackSettings.KEY_CARENCIA_DIAS, CashbackSettings.DEFAULT_CARENCIA_DIAS),
                systemConfigPort.getInt(CashbackSettings.KEY_EXPIRACAO_DIAS, CashbackSettings.DEFAULT_EXPIRACAO_DIAS),
                decimalOrDefault(CashbackSettings.KEY_RESGATE_TETO_PERCENT,
                        CashbackSettings.DEFAULT_RESGATE_TETO_PERCENT),
                decimalOrDefault(CashbackSettings.KEY_RESGATE_MINIMO_SALDO,
                        CashbackSettings.DEFAULT_RESGATE_MINIMO_SALDO));
    }

    private BigDecimal decimalOrDefault(String key, BigDecimal defaultValue) {
        BigDecimal value = systemConfigPort.getDecimal(key, defaultValue);
        return value == null ? defaultValue : value;
    }

    @Override
    @Transactional
    public CashbackSettings updateSettings(CashbackSettings settings, String username) {
        Instant now = Instant.now();
        systemConfigPort.save(new SystemConfig(CashbackSettings.KEY_CARENCIA_DIAS,
                String.valueOf(settings.carenciaDias()), now, username));
        systemConfigPort.save(new SystemConfig(CashbackSettings.KEY_EXPIRACAO_DIAS,
                String.valueOf(settings.expiracaoDias()), now, username));
        systemConfigPort.save(new SystemConfig(CashbackSettings.KEY_RESGATE_TETO_PERCENT,
                settings.resgateTetoPercent().stripTrailingZeros().toPlainString(), now, username));
        systemConfigPort.save(new SystemConfig(CashbackSettings.KEY_RESGATE_MINIMO_SALDO,
                settings.resgateMinimoSaldo().setScale(Money.MONEY_SCALE, Money.ROUNDING).toPlainString(),
                now, username));
        return settings;
    }

    // ------------------------------------------------------------------------------------------
    // V186 — resgate
    // ------------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CashbackRedemptionQuote quoteRedemption(Long customerId, BigDecimal merchandiseNet) {
        CashbackSettings settings = getSettings();
        BigDecimal base = merchandiseNet == null ? BigDecimal.ZERO : merchandiseNet.max(BigDecimal.ZERO);
        BigDecimal capAmount = base.multiply(settings.resgateTetoPercent())
                .divide(Money.HUNDRED, Money.MONEY_SCALE, RoundingMode.DOWN);
        if (customerId == null) {
            return new CashbackRedemptionQuote(BigDecimal.ZERO, settings.resgateMinimoSaldo(),
                    settings.resgateTetoPercent(), capAmount, BigDecimal.ZERO,
                    CashbackRedemptionNotAllowedException.CUSTOMER_REQUIRED);
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        BigDecimal available = redeemableBalance(customerId, Instant.now());

        String reason = null;
        if (!customer.isOfficiallyRegistered()) {
            reason = CashbackRedemptionNotAllowedException.CUSTOMER_NOT_ELIGIBLE;
        } else if (!settings.redemptionEnabled()) {
            reason = CashbackRedemptionNotAllowedException.REDEMPTION_DISABLED;
        } else if (available.signum() <= 0) {
            reason = CashbackRedemptionNotAllowedException.INSUFFICIENT_BALANCE;
        } else if (available.compareTo(settings.resgateMinimoSaldo()) < 0) {
            reason = CashbackRedemptionNotAllowedException.BELOW_MINIMUM_BALANCE;
        }
        BigDecimal max = reason == null ? available.min(capAmount) : BigDecimal.ZERO;
        return new CashbackRedemptionQuote(available, settings.resgateMinimoSaldo(), settings.resgateTetoPercent(),
                capAmount, max, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateRedemption(Long customerId, BigDecimal merchandiseNet, BigDecimal requested) {
        if (requested == null || requested.signum() <= 0) {
            return;
        }
        CashbackRedemptionQuote quote = quoteRedemption(customerId, merchandiseNet);
        if (quote.reason() != null) {
            throw new CashbackRedemptionNotAllowedException(quote.reason(), refusalMessage(quote));
        }
        if (requested.compareTo(quote.available()) > 0) {
            throw new CashbackRedemptionNotAllowedException(CashbackRedemptionNotAllowedException.INSUFFICIENT_BALANCE,
                    "Saldo de cashback insuficiente: disponível R$ " + quote.available() + ", pedido R$ " + requested);
        }
        if (requested.compareTo(quote.capAmount()) > 0) {
            throw new CashbackRedemptionNotAllowedException(CashbackRedemptionNotAllowedException.ABOVE_CAP,
                    "O resgate pode cobrir no máximo " + quote.capPercent().stripTrailingZeros().toPlainString()
                            + "% da compra (R$ " + quote.capAmount() + "); pedido R$ " + requested);
        }
    }

    private static String refusalMessage(CashbackRedemptionQuote quote) {
        return switch (quote.reason()) {
            case CashbackRedemptionNotAllowedException.CUSTOMER_REQUIRED ->
                    "Identifique o cliente para usar cashback";
            case CashbackRedemptionNotAllowedException.CUSTOMER_NOT_ELIGIBLE ->
                    "Cliente sem CPF no cadastro não participa do cashback";
            case CashbackRedemptionNotAllowedException.REDEMPTION_DISABLED -> "Resgate de cashback desativado";
            case CashbackRedemptionNotAllowedException.BELOW_MINIMUM_BALANCE ->
                    "Saldo de cashback (R$ " + quote.available() + ") abaixo do mínimo para resgate (R$ "
                            + quote.minimumBalance() + ")";
            default -> "Saldo de cashback insuficiente";
        };
    }

    /**
     * Saldo resgatável agora: o menor entre o saldo do ledger e a soma das sobras dos ganhos vivos.
     * Os dois só divergem em casos de borda — um ganho já vencido que o varredor ainda não expirou
     * conta no ledger mas não pode ser consumido; um ganho revertido depois de resgatado deixa
     * dívida no ledger.
     */
    private BigDecimal redeemableBalance(Long customerId, Instant now) {
        BigDecimal ledger = cashbackEntryRepository.sumAvailableByCustomerId(customerId, now);
        BigDecimal live = cashbackEntryRepository.findRedeemableEarned(customerId, now).stream()
                .map(RedeemableEarning::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ledger.min(live).max(BigDecimal.ZERO);
    }

    @Override
    @Transactional
    public void redeemForOrder(Order savedOrder) {
        BigDecimal amount = savedOrder.cashbackRedeemed();
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        Long customerId = savedOrder.customerId();
        if (customerId == null) {
            throw new CashbackRedemptionNotAllowedException(CashbackRedemptionNotAllowedException.CUSTOMER_REQUIRED,
                    "Identifique o cliente para usar cashback");
        }
        cashbackEntryRepository.lockCustomer(customerId);
        if (cashbackEntryRepository.findRedeemedByOrderId(savedOrder.id()).isPresent()) {
            return; // replay do mesmo pedido — o débito já foi gravado
        }
        // De novo, agora sob a trava: entre a validação do canal e aqui outro resgate pode ter
        // consumido o saldo.
        validateRedemption(customerId, savedOrder.merchandiseNet(), amount);

        Instant now = Instant.now();
        Map<Long, BigDecimal> pending = new java.util.LinkedHashMap<>();
        BigDecimal left = amount;
        for (RedeemableEarning earning : cashbackEntryRepository.findRedeemableEarned(customerId, now)) {
            if (left.signum() <= 0) {
                break;
            }
            BigDecimal take = earning.remaining().min(left);
            pending.put(earning.earned().id(), take);
            left = left.subtract(take);
        }
        if (left.signum() > 0) {
            throw new CashbackRedemptionNotAllowedException(CashbackRedemptionNotAllowedException.INSUFFICIENT_BALANCE,
                    "Saldo de cashback insuficiente para resgatar R$ " + amount);
        }
        CashbackEntry redeemed = cashbackEntryRepository.save(
                CashbackEntry.redeemed(customerId, savedOrder.id(), amount, now));
        pending.forEach((earnedId, take) -> cashbackEntryRepository.saveAllocation(
                CashbackRedemptionAllocation.of(redeemed.id(), earnedId, take, now)));
    }

    @Override
    @Transactional
    public BigDecimal restoreRedemptionForOrder(Order order, BigDecimal amount) {
        if (order == null || order.id() == null) {
            return BigDecimal.ZERO;
        }
        Optional<CashbackEntry> found = cashbackEntryRepository.findRedeemedByOrderId(order.id());
        if (found.isEmpty()) {
            return BigDecimal.ZERO;
        }
        CashbackEntry redeemed = found.get();
        cashbackEntryRepository.lockCustomer(redeemed.customerId());

        BigDecimal outstanding = redeemed.amount().negate()
                .subtract(cashbackEntryRepository.sumRedemptionReversed(redeemed.id()));
        BigDecimal target = amount == null ? outstanding : amount.min(outstanding);
        if (target.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        Instant now = Instant.now();
        Map<Long, BigDecimal> net = cashbackEntryRepository.sumNetAllocationByRedemption(redeemed.id());
        // Devolve primeiro ao ganho que vence mais tarde — o crédito dura mais. A parte que veio de
        // ganho já vencido não volta: teria expirado de qualquer jeito, e devolvê-la viraria saldo
        // que nunca vence.
        List<CashbackEntry> earnings = new ArrayList<>(cashbackEntryRepository.findAllById(net.keySet()));
        earnings.sort(Comparator.comparing(CashbackEntry::expiresAt,
                Comparator.nullsFirst(Comparator.<Instant>naturalOrder())).reversed()
                .thenComparing(CashbackEntry::id, Comparator.reverseOrder()));

        Map<Long, BigDecimal> returns = new java.util.LinkedHashMap<>();
        BigDecimal credit = BigDecimal.ZERO;
        for (CashbackEntry earned : earnings) {
            BigDecimal left = target.subtract(credit);
            if (left.signum() <= 0) {
                break;
            }
            if (earned.expiresAt() != null && !earned.expiresAt().isAfter(now)) {
                continue;
            }
            BigDecimal allocated = net.getOrDefault(earned.id(), BigDecimal.ZERO);
            if (allocated.signum() <= 0) {
                continue;
            }
            BigDecimal back = allocated.min(left);
            returns.put(earned.id(), back);
            credit = credit.add(back);
        }
        if (credit.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        CashbackEntry reversal = cashbackEntryRepository.save(CashbackEntry.redemptionReversed(redeemed, credit, now));
        returns.forEach((earnedId, back) -> cashbackEntryRepository.saveAllocation(
                CashbackRedemptionAllocation.of(reversal.id(), earnedId, back.negate(), now)));
        return credit;
    }
}
