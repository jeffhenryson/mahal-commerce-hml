package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.ecommerce.AtendimentoNotFoundException;
import com.cernecommerce.core.domain.exception.ecommerce.OrderAlreadyAcceptedException;
import com.cernecommerce.core.domain.exception.ecommerce.OrderNotMarketplaceException;
import com.cernecommerce.core.domain.exception.ecommerce.OrderNotPendingAcceptException;
import com.cernecommerce.core.domain.exception.ecommerce.PedidoAppNaoPagoException;
import com.cernecommerce.core.domain.exception.ecommerce.PedidoAppOutroCaixaException;
import com.cernecommerce.core.domain.exception.ecommerce.RecusaInvalidaException;
import com.cernecommerce.core.domain.exception.pedido.InvalidDeliveryException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoPedidoApp;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoStatus;
import com.cernecommerce.core.domain.model.ecommerce.CentralPedidosConfig;
import com.cernecommerce.core.domain.model.ecommerce.MensagemCliente;
import com.cernecommerce.core.domain.model.ecommerce.MotivoRecusa;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.AvisoClientePedidoAppUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.in.PedidoAppUseCase;
import com.cernecommerce.core.ports.out.ecommerce.AtendimentoPedidoAppRepository;
import com.cernecommerce.core.ports.out.ecommerce.CentralPedidosConfigRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
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
 * <p>Cada etapa vira e-mail e notificação no app depois do commit ({@link AvisoClientePedidoAppUseCase}), com
 * o texto de {@link MensagemCliente}, e o {@code GET /shop/orders/{id}} mostra o atendimento. Falha de
 * aviso não desfaz a etapa.</p>
 *
 * <h2>Separação na entrega (ECM-F013)</h2>
 * <p>A entrega paga pode ser marcada como separada ({@code PRONTO}) antes de sair, esperando o
 * entregador. Só depois de paga: separar o que ainda pode ser recusado faria o cliente ler "separado"
 * de um pedido que talvez não saia.</p>
 */
public class PedidoAppService implements PedidoAppUseCase {

    private static final String MOTIVO_EXPIRADO = "Prazo do dia encerrado sem pagamento";
    /** Status do pedido em que a Central ainda aceita ou recusa (ECM-F012). */
    private static final Set<OrderStatus> AGUARDANDO_ACEITE =
            EnumSet.of(OrderStatus.CRIADO, OrderStatus.AGUARDANDO_PAGAMENTO, OrderStatus.PAGO);

    private final AtendimentoPedidoAppRepository atendimentos;
    private final OrderRepository orderRepository;
    private final OrderUseCase orderUseCase;
    private final PdvService pdvService;
    private final CrmUseCase crmUseCase;
    private final AvisoClientePedidoAppUseCase avisoCliente;
    /** ECM-F013 — chave Pix e taxa sugerida, da configuração da Central de Pedidos. */
    private final CentralPedidosConfigRepository centralConfig;
    private final Clock clock;

    public PedidoAppService(AtendimentoPedidoAppRepository atendimentos, OrderRepository orderRepository,
            OrderUseCase orderUseCase, PdvService pdvService, CrmUseCase crmUseCase,
            AvisoClientePedidoAppUseCase avisoCliente, CentralPedidosConfigRepository centralConfig, Clock clock) {
        this.atendimentos = atendimentos;
        this.orderRepository = orderRepository;
        this.orderUseCase = orderUseCase;
        this.pdvService = pdvService;
        this.crmUseCase = crmUseCase;
        this.avisoCliente = avisoCliente;
        this.centralConfig = centralConfig;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<PedidoApp> listar(Set<AtendimentoStatus> statuses, Long sessionId, int page, int size) {
        Set<AtendimentoStatus> filtro = statuses == null || statuses.isEmpty() ? FILA_PADRAO : statuses;
        PageResult<AtendimentoPedidoApp> pagina = atendimentos.findByStatus(filtro, sessionId, page, size);
        List<Long> ids = pagina.content().stream().map(AtendimentoPedidoApp::orderId).toList();
        CentralPedidosConfig config = centralConfig.atual();
        Map<Long, Order> pedidos = orderRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(Order::id, Function.identity()));
        Map<Long, Customer> clientes = crmUseCase.findCustomersByIds(pedidos.values().stream()
                .map(Order::customerId).collect(Collectors.toSet()));
        List<PedidoApp> conteudo = pagina.content().stream()
                .filter(a -> pedidos.containsKey(a.orderId()))
                .map(a -> {
                    Order order = pedidos.get(a.orderId());
                    return new PedidoApp(a, order, clientes.get(order.customerId()), taxaSugerida(a, order, config),
                            MensagemCliente.para(a, order, config.pixChave()));
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
        avisarCliente(salvo, order);
        return montar(salvo, order);
    }

    @Override
    @Transactional
    public PedidoApp aceitarPedido(Long orderId, Long sessionId, BigDecimal taxaEntrega, String username) {
        AtendimentoPedidoApp atendimento = aguardandoAceite(orderId);
        if (atendimento.status() != AtendimentoStatus.PENDENTE) {
            throw new OrderAlreadyAcceptedException(orderId);
        }
        Long caixa = sessionId != null ? sessionId : pdvService.getCurrentSession(username).id();
        return aceitar(orderId, caixa, taxaEntrega, username);
    }

    @Override
    @Transactional
    public PedidoApp recusarPedido(Long orderId, MotivoRecusa codigo, String nota, String username) {
        if (codigo == null) {
            throw new RecusaInvalidaException("informe o motivo da recusa");
        }
        String motivo = codigo.descrever(nota);
        aguardandoAceite(orderId);
        return recusar(orderId, motivo, codigo, username);
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
        return recusar(orderId, motivo, null, username);
    }

    private PedidoApp recusar(Long orderId, String motivo, MotivoRecusa codigo, String username) {
        AtendimentoPedidoApp atendimento = travar(orderId);
        if (atendimento.sessionId() != null) {
            exigirCaixaDoPedido(atendimento, username);
        }
        AtendimentoPedidoApp recusado = atendimento.recusado(motivo, codigo, clock.instant());
        // cancelOrder valida que ainda não foi pago e devolve a reserva.
        Order cancelado = orderUseCase.cancelOrder(orderId, "Recusado pela loja: " + motivo, username);
        // O e-mail ao cliente sai pelo ORDER_CANCELLED que o controller publica, como no cancelamento
        // pelo app — mandar daqui também duplicaria. A notificação no app (ECM-F013) não tem outro caminho.
        AtendimentoPedidoApp salvo = atendimentos.save(recusado);
        avisoCliente.soNotificacao(cancelado, MensagemCliente.para(salvo, cancelado, null));
        return montar(salvo, cancelado);
    }

    @Override
    @Transactional
    public PedidoApp pronto(Long orderId, String username) {
        AtendimentoPedidoApp atendimento = travar(orderId);
        exigirCaixaDoPedido(atendimento, username);
        Order order = getOrder(orderId);
        // ECM-F013 — na entrega, separado quer dizer "esperando o entregador": só depois do Pix liquidado.
        if (atendimento.isEntrega() && order.status() != OrderStatus.CONCLUIDO) {
            throw new PedidoAppNaoPagoException(orderId);
        }
        AtendimentoPedidoApp salvo = atendimentos.save(atendimento.pronto(clock.instant()));
        avisarCliente(salvo, order);
        return montar(salvo, order);
    }

    @Override
    @Transactional
    public PedidoApp saiuParaEntrega(Long orderId, String linkViagem, String username) {
        AtendimentoPedidoApp atendimento = travar(orderId);
        exigirCaixaDoPedido(atendimento, username);
        Order order = getOrder(orderId);
        // Entrega só sai paga: o Pix foi conferido e liquidado no caixa.
        if (order.status() != OrderStatus.CONCLUIDO) {
            throw new PedidoAppNaoPagoException(orderId);
        }
        AtendimentoPedidoApp saiu = atendimento.saiuParaEntrega(clock.instant());
        // ECM-F013 — o link da corrida vai na entrega do pedido e no aviso ao cliente.
        if (linkViagem != null && !linkViagem.isBlank() && order.delivery() != null) {
            order = orderRepository.save(order.withDelivery(order.delivery().withTrackingUrl(linkViagem)));
        }
        AtendimentoPedidoApp salvo = atendimentos.save(saiu);
        avisarCliente(salvo, order);
        return montar(salvo, order);
    }

    @Override
    @Transactional
    public PedidoApp entregue(Long orderId, String username) {
        AtendimentoPedidoApp atendimento = travar(orderId);
        Order order = getOrder(orderId);
        AtendimentoPedidoApp salvo = atendimentos.save(atendimento.entregue(clock.instant()));
        avisarCliente(salvo, order);
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
        avisoCliente.cancelamento(cancelado, MOTIVO_EXPIRADO);
        return true;
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    /**
     * Pedido do app ainda na fase de aceite (ECM-F012): canal MARKETPLACE, não pago e despachado, e
     * atendimento sem desfecho. O aceite único é de quem chama — a recusa vale também para o aceito.
     */
    private AtendimentoPedidoApp aguardandoAceite(Long orderId) {
        Order order = getOrder(orderId);
        if (order.channel() != SalesChannel.MARKETPLACE) {
            throw new OrderNotMarketplaceException(orderId);
        }
        if (!AGUARDANDO_ACEITE.contains(order.status())) {
            throw new OrderNotPendingAcceptException(orderId, "pedido " + order.status());
        }
        AtendimentoPedidoApp atendimento = atendimentos.findByOrderId(orderId)
                .orElseThrow(() -> new AtendimentoNotFoundException(orderId));
        if (atendimento.status().isFinal()) {
            throw new OrderNotPendingAcceptException(orderId, "atendimento " + atendimento.status());
        }
        return atendimento;
    }

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
        CentralPedidosConfig config = centralConfig.atual();
        return new PedidoApp(atendimento, order, cliente, taxaSugerida(atendimento, order, config),
                MensagemCliente.para(atendimento, order, config.pixChave()));
    }

    /**
     * ECM-F013 — a taxa que a Central sugere no aceite da entrega: a do bairro, se listado, senão a fixa.
     * Só na entrega ainda pendente; depois do aceite a taxa é a do pedido.
     */
    private static BigDecimal taxaSugerida(AtendimentoPedidoApp atendimento, Order order,
            CentralPedidosConfig config) {
        if (!atendimento.isEntrega() || atendimento.status() != AtendimentoStatus.PENDENTE) {
            return null;
        }
        String bairro = order.delivery() == null || order.delivery().address() == null ? null
                : order.delivery().address().district();
        return config.taxaPara(bairro);
    }

    /** O aviso da etapa ao cliente, com o texto de {@link MensagemCliente} e a chave Pix da loja. */
    private void avisarCliente(AtendimentoPedidoApp atendimento, Order order) {
        avisoCliente.etapa(order, MensagemCliente.para(atendimento, order, centralConfig.atual().pixChave()));
    }
}
