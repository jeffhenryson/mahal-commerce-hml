package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.ecommerce.AtendimentoNotFoundException;
import com.cernecommerce.core.domain.exception.ecommerce.PedidoAppNaoPagoException;
import com.cernecommerce.core.domain.exception.ecommerce.PedidoAppOutroCaixaException;
import com.cernecommerce.core.domain.exception.pedido.InvalidDeliveryException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoPedidoApp;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoStatus;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.in.PedidoAppUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.ecommerce.AtendimentoPedidoAppRepository;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fila "Pedidos do app" do caixa (ECM-F010) e o prazo do dia (ECM-F011).
 *
 * <h2>Quem pode mexer</h2>
 * <p>Aceitar exige o <b>próprio</b> caixa aberto ({@link PdvService#requireOwnOpenSession}), como
 * vender: o pedido aceito aparece na tela daquele caixa, e só ele o liquida. Separar, despachar e
 * recusar um pedido já aceito também exigem ser o dono do caixa que o tem. Pedido de caixa que
 * fechou é assumido por outro ({@link #assumir}); entregue não exige caixa aberto, porque o motoboy
 * pode voltar depois do fechamento.</p>
 *
 * <h2>Cliente acompanha por e-mail e pelo app</h2>
 * <p>Cada etapa vira um e-mail depois do commit, e o {@code GET /shop/orders/{id}} mostra o
 * atendimento. Falha de e-mail não desfaz a etapa.</p>
 */
public class PedidoAppService implements PedidoAppUseCase {

    private static final String MOTIVO_EXPIRADO = "Prazo do dia encerrado sem pagamento";

    private final AtendimentoPedidoAppRepository atendimentos;
    private final OrderRepository orderRepository;
    private final OrderUseCase orderUseCase;
    private final PdvService pdvService;
    private final CrmUseCase crmUseCase;
    private final EmailPort emailPort;
    private final AfterCommitExecutor afterCommit;
    private final Clock clock;

    public PedidoAppService(AtendimentoPedidoAppRepository atendimentos, OrderRepository orderRepository,
            OrderUseCase orderUseCase, PdvService pdvService, CrmUseCase crmUseCase, EmailPort emailPort,
            AfterCommitExecutor afterCommit, Clock clock) {
        this.atendimentos = atendimentos;
        this.orderRepository = orderRepository;
        this.orderUseCase = orderUseCase;
        this.pdvService = pdvService;
        this.crmUseCase = crmUseCase;
        this.emailPort = emailPort;
        this.afterCommit = afterCommit;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<PedidoApp> listar(Set<AtendimentoStatus> statuses, Long sessionId, int page, int size) {
        Set<AtendimentoStatus> filtro = statuses == null || statuses.isEmpty() ? FILA_PADRAO : statuses;
        PageResult<AtendimentoPedidoApp> pagina = atendimentos.findByStatus(filtro, sessionId, page, size);
        List<Long> ids = pagina.content().stream().map(AtendimentoPedidoApp::orderId).toList();
        Map<Long, Order> pedidos = orderRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(Order::id, Function.identity()));
        Map<Long, Customer> clientes = crmUseCase.findCustomersByIds(pedidos.values().stream()
                .map(Order::customerId).collect(Collectors.toSet()));
        List<PedidoApp> conteudo = pagina.content().stream()
                .filter(a -> pedidos.containsKey(a.orderId()))
                .map(a -> {
                    Order order = pedidos.get(a.orderId());
                    return new PedidoApp(a, order, clientes.get(order.customerId()));
                })
                .toList();
        return new PageResult<>(conteudo, pagina.page(), pagina.size(), pagina.totalElements(), pagina.totalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public PedidoApp buscar(Long orderId) {
        AtendimentoPedidoApp atendimento = atendimentos.findByOrderId(orderId)
                .orElseThrow(() -> new AtendimentoNotFoundException(orderId));
        return montar(atendimento, getOrder(orderId));
    }

    @Override
    @Transactional
    public PedidoApp aceitar(Long orderId, Long sessionId, BigDecimal taxaEntrega, String username) {
        CashRegisterSession caixa = pdvService.requireOwnOpenSession(sessionId, username);
        AtendimentoPedidoApp atendimento = travar(orderId);
        AtendimentoPedidoApp aceito = atendimento.aceito(caixa.id(), username, clock.instant());

        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (aceito.isEntrega()) {
            if (taxaEntrega == null) {
                throw new InvalidDeliveryException("informe a taxa de entrega ao aceitar (zero se for grátis)");
            }
            order = orderRepository.save(order.withDeliveryFee(taxaEntrega));
        } else if (taxaEntrega != null && taxaEntrega.signum() != 0) {
            throw new InvalidDeliveryException("pedido de retirada não tem taxa de entrega");
        }
        AtendimentoPedidoApp salvo = atendimentos.save(aceito);
        avisarCliente(order, aceito.isEntrega()
                ? "Pedido aceito — total R$ " + order.totalPayable().toPlainString()
                        + ". A loja vai te mandar o Pix pelo WhatsApp"
                : "Pedido aceito — já estamos separando");
        return montar(salvo, order);
    }

    @Override
    @Transactional
    public PedidoApp assumir(Long orderId, Long sessionId, String username) {
        CashRegisterSession caixa = pdvService.requireOwnOpenSession(sessionId, username);
        AtendimentoPedidoApp atendimento = travar(orderId);
        if (atendimento.sessionId() != null && !atendimento.sessionId().equals(caixa.id())) {
            CashRegisterSession anterior = pdvService.getSession(atendimento.sessionId());
            if (anterior.isOpen() && !anterior.belongsTo(username)) {
                throw new PedidoAppOutroCaixaException(orderId, anterior.id());
            }
        }
        return montar(atendimentos.save(atendimento.assumido(caixa.id(), username)), getOrder(orderId));
    }

    @Override
    @Transactional
    public PedidoApp recusar(Long orderId, String motivo, String username) {
        AtendimentoPedidoApp atendimento = travar(orderId);
        if (atendimento.sessionId() != null) {
            exigirCaixaDoPedido(atendimento, username);
        }
        AtendimentoPedidoApp recusado = atendimento.recusado(motivo, clock.instant());
        // cancelOrder valida que ainda não foi pago e devolve a reserva.
        Order cancelado = orderUseCase.cancelOrder(orderId, "Recusado pela loja: " + motivo, username);
        // O e-mail ao cliente sai pelo ORDER_CANCELLED que o controller publica, como no cancelamento
        // pelo app — mandar daqui também duplicaria.
        return montar(atendimentos.save(recusado), cancelado);
    }

    @Override
    @Transactional
    public PedidoApp pronto(Long orderId, String username) {
        AtendimentoPedidoApp atendimento = travar(orderId);
        exigirCaixaDoPedido(atendimento, username);
        Order order = getOrder(orderId);
        AtendimentoPedidoApp salvo = atendimentos.save(atendimento.pronto(clock.instant()));
        avisarCliente(order, "Pedido pronto para retirada");
        return montar(salvo, order);
    }

    @Override
    @Transactional
    public PedidoApp saiuParaEntrega(Long orderId, String username) {
        AtendimentoPedidoApp atendimento = travar(orderId);
        exigirCaixaDoPedido(atendimento, username);
        Order order = getOrder(orderId);
        // Entrega só sai paga: o Pix foi conferido e liquidado no caixa.
        if (order.status() != OrderStatus.CONCLUIDO) {
            throw new PedidoAppNaoPagoException(orderId);
        }
        AtendimentoPedidoApp salvo = atendimentos.save(atendimento.saiuParaEntrega(clock.instant()));
        avisarCliente(order, "Pedido saiu para entrega");
        return montar(salvo, order);
    }

    @Override
    @Transactional
    public PedidoApp entregue(Long orderId, String username) {
        AtendimentoPedidoApp atendimento = travar(orderId);
        Order order = getOrder(orderId);
        AtendimentoPedidoApp salvo = atendimentos.save(atendimento.entregue(clock.instant()));
        avisarCliente(order, "Pedido entregue");
        return montar(salvo, order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AtendimentoPedidoApp> atendimentosDe(Collection<Long> orderIds) {
        return atendimentos.findByOrderIds(orderIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> vencidos(Instant agora, int limite) {
        return atendimentos.findVencidos(agora, limite);
    }

    @Override
    @Transactional
    public boolean expirar(Long orderId) {
        Instant agora = clock.instant();
        AtendimentoPedidoApp atendimento = atendimentos.findByOrderIdForUpdate(orderId).orElse(null);
        // Reconferido sob a trava: entre a busca do varredor e aqui o caixa pode ter liquidado.
        if (atendimento == null || !atendimento.emAndamento() || atendimento.prazo().isAfter(agora)) {
            return false;
        }
        Order order = getOrder(orderId);
        if (order.status() != OrderStatus.AGUARDANDO_PAGAMENTO) {
            return false;
        }
        Order cancelado = orderUseCase.cancelOrder(orderId, MOTIVO_EXPIRADO, "sistema");
        atendimentos.save(atendimento.expirado(agora));
        // Sem controller no caminho do varredor, nenhum ORDER_CANCELLED é publicado: o aviso sai daqui.
        avisarCancelamento(cancelado, MOTIVO_EXPIRADO);
        return true;
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private AtendimentoPedidoApp travar(Long orderId) {
        return atendimentos.findByOrderIdForUpdate(orderId).orElseThrow(() -> new AtendimentoNotFoundException(orderId));
    }

    /** Só o dono do caixa que aceitou mexe no pedido; caixa fechado pede o "assumir". */
    private void exigirCaixaDoPedido(AtendimentoPedidoApp atendimento, String username) {
        if (atendimento.sessionId() == null) {
            return;
        }
        pdvService.requireOwnOpenSession(atendimento.sessionId(), username);
    }

    private Order getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private PedidoApp montar(AtendimentoPedidoApp atendimento, Order order) {
        Customer cliente = order.customerId() == null ? null
                : crmUseCase.findCustomersByIds(Set.of(order.customerId())).get(order.customerId());
        return new PedidoApp(atendimento, order, cliente);
    }

    private void avisarCliente(Order order, String etapa) {
        Customer cliente = cliente(order);
        if (cliente == null) {
            return;
        }
        OrderEmailView view = OrderEmailView.of(order, cliente.nome());
        afterCommit.accumulate("shop.pedido-app.etapa:" + order.id() + ":" + etapa, order,
                pedidos -> emailPort.sendOrderStatusUpdate(cliente.email(), view, etapa));
    }

    private void avisarCancelamento(Order order, String motivo) {
        Customer cliente = cliente(order);
        if (cliente == null) {
            return;
        }
        OrderEmailView view = OrderEmailView.of(order, cliente.nome());
        afterCommit.accumulate("shop.pedido-app.cancelado:" + order.id(), order,
                pedidos -> emailPort.sendOrderCancellation(cliente.email(), view, motivo, false));
    }

    /** O cliente com e-mail para avisar, ou nulo — cliente sem e-mail acompanha só pelo app. */
    private Customer cliente(Order order) {
        if (order.customerId() == null) {
            return null;
        }
        Customer cliente = crmUseCase.findCustomersByIds(Set.of(order.customerId())).get(order.customerId());
        return cliente == null || cliente.email() == null || cliente.email().isBlank() ? null : cliente;
    }
}
