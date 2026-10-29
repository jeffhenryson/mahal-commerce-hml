package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.SessionPayLaterNotAllowedException;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.exception.salao.AutoatendimentoDadosInvalidosException;
import com.cernecommerce.core.domain.exception.salao.ChamadoJaAbertoException;
import com.cernecommerce.core.domain.exception.salao.ChamadoNotFoundException;
import com.cernecommerce.core.domain.exception.salao.ClienteJaCadastradoException;
import com.cernecommerce.core.domain.exception.salao.ClienteNaoIdentificadoException;
import com.cernecommerce.core.domain.exception.salao.CodigoMesaInvalidoException;
import com.cernecommerce.core.domain.exception.salao.ConfirmacaoBloqueadaException;
import com.cernecommerce.core.domain.exception.salao.ConfirmacaoInvalidaException;
import com.cernecommerce.core.domain.exception.salao.EssenciaIndisponivelException;
import com.cernecommerce.core.domain.exception.salao.MesaOcupadaException;
import com.cernecommerce.core.domain.exception.salao.SalaoMesaNomeDuplicadoException;
import com.cernecommerce.core.domain.exception.salao.SalaoMesaNotFoundException;
import com.cernecommerce.core.domain.exception.salao.QrPausadoException;
import com.cernecommerce.core.domain.exception.salao.SolicitacaoJaPendenteException;
import com.cernecommerce.core.domain.exception.salao.SolicitacaoNaoPendenteException;
import com.cernecommerce.core.domain.exception.salao.SolicitacaoNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerIdentifiers;
import com.cernecommerce.core.domain.model.crm.CustomerMatch;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ProporcaoMistura;
import com.cernecommerce.core.domain.model.pdv.SessionAddon;
import com.cernecommerce.core.domain.model.pdv.SessionSetup;
import com.cernecommerce.core.domain.model.pdv.SessionTimeline;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.domain.model.salao.AcessoMesa;
import com.cernecommerce.core.domain.model.salao.CardapioDaMesa;
import com.cernecommerce.core.domain.model.salao.ChamadoMesa;
import com.cernecommerce.core.domain.model.salao.Disponibilidade;
import com.cernecommerce.core.domain.model.salao.EscolhasSessao;
import com.cernecommerce.core.domain.model.salao.EssenciaDoDia;
import com.cernecommerce.core.domain.model.salao.EssenciaOferecida;
import com.cernecommerce.core.domain.model.salao.FormaPagamentoChamado;
import com.cernecommerce.core.domain.model.salao.MesaAbertaDoCliente;
import com.cernecommerce.core.domain.model.salao.MesaAtual;
import com.cernecommerce.core.domain.model.salao.MesaOcupacao;
import com.cernecommerce.core.domain.model.salao.OrigemSelecao;
import com.cernecommerce.core.domain.model.salao.PerfilSabor;
import com.cernecommerce.core.domain.model.salao.SalaoMesa;
import com.cernecommerce.core.domain.model.salao.SolicitacaoResumo;
import com.cernecommerce.core.domain.model.salao.SolicitacaoSessao;
import com.cernecommerce.core.domain.model.salao.SolicitacaoStatus;
import com.cernecommerce.core.domain.model.salao.StatusChamado;
import com.cernecommerce.core.domain.model.salao.TipoChamado;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase;
import com.cernecommerce.core.ports.in.SessionMenuUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.ecommerce.CentralPedidosConfigRepository;
import com.cernecommerce.core.ports.out.audit.AuditLogRepository;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import com.cernecommerce.core.ports.out.event.AuditEventPublisherPort;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import com.cernecommerce.core.ports.out.salao.SalaoAutoatendimentoRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Autoatendimento do Salão — ver {@link SalaoAutoatendimentoUseCase}.
 *
 * <h2>O que o QR oferece</h2>
 * <p>As sessões consomem do depósito do Salão, se houver um ativo, senão do depósito padrão. Lá, o QR
 * oferece as essências com faixa ativa e <b>lata aberta</b> ou só <b>fechada</b> — a fechada abre na
 * 1ª sessão ({@code consumeSession}). A sem faixa o lançamento recusaria: só o admin a vê, com o
 * motivo ({@link Disponibilidade}). As marcadas no dia são destaques: aparecem no topo, não filtram.
 * A data sem seleção própria usa o modelo do dia da semana.</p>
 *
 * <h2>Mesa ocupada</h2>
 * <p>O envio da 1ª solicitação de mesa livre grava uma {@link MesaOcupacao} com código de 4 dígitos
 * e dá acesso ao aparelho que pediu — o lead recebe o código já com o pedido pendente. Sem comanda, a
 * ocupação vale enquanto o pedido estiver pendente: recusado ou expirado sem nada lançado, a mesa
 * está livre. O aceite grava a comanda nela; daí vale enquanto a comanda estiver aberta. Aparelho
 * sem acesso não pede nem vê a mesa — entra com o código.</p>
 *
 * <h2>Dados pessoais no lado público</h2>
 * <p>Identificar responde só se achou e o primeiro nome (sem sobrenome). O cadastro público nunca toca
 * cliente existente (o find-or-create do CRM completaria o CPF de um terceiro): CPF ou celular já
 * usado é 409 e o lead se identifica. O pedido não carrega id — o servidor resolve o cliente de novo
 * pelo CPF/celular.</p>
 *
 * <h2>Entrar pela identificação</h2>
 * <p>Quem é o cliente da mesa entra num aparelho novo sem o código, mas confirmando um dado que só
 * ele tem à mão: os 4 últimos dígitos do celular (entrou pelo CPF) ou os 3 primeiros do CPF (entrou
 * pelo celular). Saber o CPF de alguém não basta. As mesas abertas que a identificação lista só
 * trazem token e valor da mesa deste QR — as outras são aviso. Cada tentativa vai para a auditoria, e
 * {@link #TENTATIVAS_CONFIRMACAO} erros em {@link #JANELA_TRAVA} travam o cliente até a janela passar
 * (os 3 primeiros do CPF são só mil combinações). Sem o outro dado no cadastro, não há fallback: entra
 * com o código da mesa.</p>
 *
 * <h2>Pedido sem CPF</h2>
 * <p>O aparelho que já tem acesso à mesa pede sem se identificar: o cliente é o titular da ocupação.
 * Se o aparelho entrou pelo código, o pedido é de acompanhante — cobra no titular, e o PDV vê
 * "Acompanhante de &lt;titular&gt;". Pagar no final vale para qualquer cliente: quem decide é o
 * atendente no aceite ({@code PDV_SESSION_PAY_LATER}).</p>
 *
 * <h2>Chamados</h2>
 * <p>Conta, recolher, carvão e atendente: um ABERTO por tipo e comanda. O esquecido vence em
 * {@link ChamadoMesa#VALIDADE} na leitura, e o da comanda encerrada sai da fila com a ocupação.</p>
 */
public class SalaoAutoatendimentoService implements SalaoAutoatendimentoUseCase {

    static final ZoneId ZONA_SALAO = ZoneId.of("America/Sao_Paulo");
    static final String ORIGEM_LEAD = "QR Salão";
    /** Quem recebe o aviso de pedido novo: quem opera mesa. */
    static final String PERMISSAO_ATENDENTE = "PDV_COMANDA_MANAGE";
    private static final String DESTINO_AVISO = "/pdv";
    private static final int PAGINA_CATALOGO = 100;
    private static final int DEVICE_TOKEN_MAX = 64;
    /** Quem aparece na auditoria das tentativas de entrar pela identificação: o QR não tem login. */
    static final String USUARIO_QR = "qr-publico";
    /** Os atendidos que a fila do PDV ainda mostra. */
    private static final Duration JANELA_ATENDIDOS = Duration.ofHours(12);
    /** Cabe na nota da linha com o sufixo de vaso grande ({@code ComandaItem.NOTES_MAX_LENGTH}). */
    private static final int TEXTO_MISTURA_MAX = 150;
    /** Erros de confirmação que travam o cliente, dentro de {@link #JANELA_TRAVA}. */
    static final int TENTATIVAS_CONFIRMACAO = 5;
    static final Duration JANELA_TRAVA = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CADASTRO_NAO_ENCONTRADO = "Não encontramos seu cadastro — confira o CPF ou celular.";
    private static final String SEM_ACESSO = "Sem acesso a esta mesa — digite o código da mesa.";

    private final SalaoAutoatendimentoRepository repository;
    private final EstoqueUseCase estoqueUseCase;
    private final WarehouseRepository warehouseRepository;
    private final SessionMenuUseCase sessionMenuUseCase;
    private final CrmUseCase crmUseCase;
    private final ComandaUseCase comandaUseCase;
    private final AvisoOperadores avisoOperadores;
    /** ECM-F012 — os mesmos eventos vão para a Central de Pedidos, que relê a fila do QR. */
    private final CentralPedidosEventos centralPedidos;
    private final AuditEventPublisherPort auditEvents;
    private final AuditLogRepository auditLog;
    /** ECM-F013 — pausa do QR e validade da solicitação, da configuração da Central de Pedidos. */
    private final CentralPedidosConfigRepository centralConfig;
    private final Clock clock;

    public SalaoAutoatendimentoService(SalaoAutoatendimentoRepository repository, EstoqueUseCase estoqueUseCase,
            WarehouseRepository warehouseRepository, SessionMenuUseCase sessionMenuUseCase, CrmUseCase crmUseCase, ComandaUseCase comandaUseCase,
            NotificationUseCase notificationUseCase, UserRepository userRepository, NotificationSsePort ssePort,
            AfterCommitExecutor afterCommit, AuditEventPublisherPort auditEvents, AuditLogRepository auditLog,
            CentralPedidosConfigRepository centralConfig, Clock clock) {
        this.repository = repository;
        this.estoqueUseCase = estoqueUseCase;
        this.warehouseRepository = warehouseRepository;
        this.sessionMenuUseCase = sessionMenuUseCase;
        this.crmUseCase = crmUseCase;
        this.comandaUseCase = comandaUseCase;
        this.avisoOperadores = new AvisoOperadores(notificationUseCase, userRepository, ssePort, afterCommit);
        this.centralPedidos = new CentralPedidosEventos(userRepository, ssePort, afterCommit);
        this.auditEvents = auditEvents;
        this.auditLog = auditLog;
        this.centralConfig = centralConfig;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    // ── Admin: mesas ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<SalaoMesa> listMesas() {
        return repository.findAllMesas();
    }

    @Override
    @Transactional
    public SalaoMesa createMesa(String nome) {
        String limpo = nome == null ? null : nome.trim();
        if (limpo != null) exigirNomeLivre(limpo, null);
        return repository.saveMesa(new SalaoMesa(null, limpo, novoToken(), true, clock.instant()));
    }

    @Override
    @Transactional
    public SalaoMesa updateMesa(Long id, String nome, boolean ativo) {
        SalaoMesa atual = mesa(id);
        String limpo = nome == null ? atual.nome() : nome.trim();
        exigirNomeLivre(limpo, id);
        return repository.saveMesa(atual.withNome(limpo).withAtivo(ativo));
    }

    private void exigirNomeLivre(String nome, Long excetoId) {
        if (repository.existsMesaComNome(nome, excetoId)) {
            throw new SalaoMesaNomeDuplicadoException("Já existe uma mesa chamada \"" + nome + "\".");
        }
    }

    @Override
    @Transactional
    public SalaoMesa regenerarToken(Long id) {
        return repository.saveMesa(mesa(id).withToken(novoToken()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MesaOcupacao> ocupacoesAbertas() {
        // A última de cada mesa numa consulta e a validade de todas em lote (PDV-C057), em vez de 2–3
        // consultas por mesa. Ordem: a do id da mesa.
        return valendo(repository.findUltimaOcupacaoDeCadaMesa());
    }

    // ── Admin: essências do dia ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public SelecaoDoDia essenciasDoDia(LocalDate data) {
        Map.Entry<OrigemSelecao, Set<String>> selecao = selecaoDoDia(data);
        return new SelecaoDoDia(selecao.getKey(), essenciasDoSalao(faixasAtivas()).stream()
                .map(e -> new EssenciaDoDia(e, selecao.getValue().contains(e.sku()))).toList());
    }

    @Override
    @Transactional
    public void setEssenciasDoDia(LocalDate data, Collection<String> skus) {
        repository.replaceEssenciasDoDia(data, limpar(skus));
    }

    private static Set<String> limpar(Collection<String> skus) {
        return skus == null ? Set.of() : new LinkedHashSet<>(skus.stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    /** 1 = segunda … 7 = domingo, como {@link java.time.DayOfWeek#getValue}. */
    private static int diaSemana(int dia) {
        if (dia < 1 || dia > 7) {
            throw new AutoatendimentoDadosInvalidosException("Dia da semana inválido: use 1 (segunda) a 7 (domingo).");
        }
        return dia;
    }

    /**
     * Origem → SKUs: a seleção da data ou, sem ela, o modelo do dia da semana. (Par em vez de record
     * próprio: tipo aninhado em {@code core.service} fere a regra de HexagonalArchitectureTest.)
     */
    private Map.Entry<OrigemSelecao, Set<String>> selecaoDoDia(LocalDate data) {
        Set<String> doDia = repository.findEssenciasDoDia(data);
        return doDia.isEmpty()
                ? Map.entry(OrigemSelecao.MODELO, repository.findModelo(data.getDayOfWeek().getValue()))
                : Map.entry(OrigemSelecao.DIA, doDia);
    }

    @Override
    @Transactional
    public void copiarEssenciasDoDia(LocalDate de, LocalDate para) {
        repository.replaceEssenciasDoDia(para, repository.findEssenciasDoDia(de));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Integer, Set<String>> modeloSemanal() {
        return new TreeMap<>(repository.findModelo());
    }

    @Override
    @Transactional
    public void setModelo(int diaSemana, Collection<String> skus) {
        repository.replaceModelo(diaSemana(diaSemana), limpar(skus));
    }

    @Override
    @Transactional
    public void copiarModelo(int de, Collection<Integer> para) {
        Set<String> skus = repository.findModelo(diaSemana(de));
        if (para == null || para.isEmpty()) {
            throw new AutoatendimentoDadosInvalidosException("Escolha para quais dias copiar.");
        }
        for (Integer destino : new LinkedHashSet<>(para)) {
            if (destino == null) throw new AutoatendimentoDadosInvalidosException("Dia da semana inválido.");
            if (destino != de) repository.replaceModelo(diaSemana(destino), skus);
        }
    }

    @Override
    @Transactional
    public void salvarPerfis(List<PerfisDoSku> itens) {
        Map<String, List<PerfilSabor>> porSku = new LinkedHashMap<>();
        for (PerfisDoSku item : itens == null ? List.<PerfisDoSku>of() : itens) {
            String sku = item.sku() == null ? "" : item.sku().trim();
            if (sku.isEmpty()) {
                throw new AutoatendimentoDadosInvalidosException("Informe o SKU da essência.");
            }
            porSku.put(sku, item.perfis().stream().filter(Objects::nonNull).distinct().sorted().toList());
        }
        repository.replacePerfis(porSku, clock.instant());
    }

    @Override
    public LocalDate hoje() {
        return LocalDate.now(clock.withZone(ZONA_SALAO));
    }

    // ── Público ───────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public CardapioDaMesa cardapioPorToken(String token, String deviceToken) {
        SalaoMesa mesa = mesaPorToken(token);
        List<SessionTier> faixas = faixasAtivas();
        List<EssenciaOferecida> oferecidas = disponiveis(faixas);
        Set<String> destaques = new HashSet<>(selecaoDoDia(hoje()).getValue());
        destaques.retainAll(oferecidas.stream().map(EssenciaOferecida::sku).collect(Collectors.toSet()));
        SessionSettings settings = sessionMenuUseCase.getSettings();
        return new CardapioDaMesa(mesa.nome(), faixas, oferecidas, destaques, settings.upgradeVasoGrandePreco(),
                settings.upgradeCarvaoJumboPreco(), adicionaisAtivos(), acesso(mesa, deviceToken));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Identificado> identificarPorToken(String token, Identificacao identificacao) {
        SalaoMesa mesa = mesaPorToken(token);
        return encontrar(identificacao).map(c -> identificado(c, mesa));
    }

    @Override
    @Transactional
    public Identificado cadastrarPorToken(String token, Cadastro cadastro) {
        mesaPorToken(token);
        if (cadastro.nome() == null || cadastro.nome().trim().length() < 2) {
            throw new AutoatendimentoDadosInvalidosException("Informe seu nome.");
        }
        String celular = celularOuNulo(cadastro.celular());
        if (celular == null) {
            throw new AutoatendimentoDadosInvalidosException("Informe o celular.");
        }
        String cpf = cpfOuNulo(cadastro.cpf());
        if (cpf == null) {
            throw new AutoatendimentoDadosInvalidosException("Informe o CPF.");
        }
        if (cadastro.dataNascimento() == null) {
            throw new AutoatendimentoDadosInvalidosException("Informe a data de nascimento.");
        }
        if (!crmUseCase.lookupCustomers(celular, null, cpf).isEmpty()) {
            throw new ClienteJaCadastradoException(
                    "Já existe cadastro com este CPF ou celular — use \"Já sou cliente\".");
        }
        Customer criado = crmUseCase.createCustomer(cadastro.nome(), cadastro.celular().trim(), null, cpf,
                ORIGEM_LEAD, cadastro.dataNascimento());
        return identificado(criado);
    }

    @Override
    @Transactional
    public Identificado completarPorToken(String token, Completar completar) {
        SalaoMesa mesa = mesaPorToken(token);
        Customer cliente = encontrar(completar.identificacao()).orElseThrow(() ->
                new ClienteNaoIdentificadoException(CADASTRO_NAO_ENCONTRADO));
        String contato = cliente.contato();
        String cpf = cliente.cpf();
        LocalDate nascimento = null;
        if (celularOuNulo(contato) == null) {
            String celular = celularOuNulo(completar.celular());
            if (celular == null) throw new AutoatendimentoDadosInvalidosException("Informe o celular.");
            exigirLivre(crmUseCase.lookupCustomers(celular, null, null), cliente, "celular");
            contato = completar.celular().trim();
        }
        if (cpf == null || cpf.isBlank()) {
            cpf = cpfOuNulo(completar.cpf());
            if (cpf == null) throw new AutoatendimentoDadosInvalidosException("Informe o CPF.");
            exigirLivre(crmUseCase.lookupCustomers(null, null, cpf), cliente, "CPF");
        }
        if (cliente.dataNascimento() == null) {
            nascimento = completar.dataNascimento();
            if (nascimento == null) throw new AutoatendimentoDadosInvalidosException("Informe a data de nascimento.");
        }
        // Nascimento nulo no update mantém o gravado (CRM-F012).
        Customer salvo = crmUseCase.updateCustomer(cliente.id(), cliente.nome(), contato, cliente.email(), cpf,
                cliente.origem(), nascimento);
        return identificado(salvo, mesa);
    }

    @Override
    @Transactional
    public SolicitacaoSessao solicitarPorToken(String token, Pedido pedido) {
        SalaoMesa mesa = mesaPorToken(token);
        if (!centralConfig.atual().qrRecebendo()) {
            throw new QrPausadoException();
        }
        String device = deviceObrigatorio(pedido.deviceToken());
        // Dois aparelhos pedindo na mesma mesa livre: o segundo espera e já a encontra ocupada.
        repository.findMesaByIdForUpdate(mesa.id());
        Optional<MesaOcupacao> ocupacao = ocupacaoAberta(mesa);
        // O acesso e se é de acompanhante, numa leitura (PDV-C062).
        Optional<Boolean> acesso = ocupacao.flatMap(o -> repository.findAcesso(o.id(), device));
        boolean naMesa = acesso.isPresent();
        if (ocupacao.isPresent() && !naMesa) {
            throw new MesaOcupadaException("Esta mesa já está com outro cliente — digite o código da mesa.");
        }
        Customer cliente;
        boolean acompanhante = false;
        if (semIdentificacao(pedido.identificacao()) && naMesa) {
            // Quem já está na mesa pede sem CPF: o cliente é o titular dela.
            cliente = crmUseCase.findCustomerById(ocupacao.get().customerId());
            acompanhante = acesso.get();
        } else if (semIdentificacao(pedido.identificacao())) {
            throw new ClienteNaoIdentificadoException("Identifique-se com o CPF ou o celular.");
        } else {
            cliente = encontrar(pedido.identificacao()).orElseThrow(() ->
                    new ClienteNaoIdentificadoException(CADASTRO_NAO_ENCONTRADO));
        }
        if (!faltando(cliente).isEmpty()) {
            throw new AutoatendimentoDadosInvalidosException("Complete seu cadastro antes de pedir.");
        }
        // Pagar no final vale para todos: o atendente decide no aceite (PDV_SESSION_PAY_LATER).
        EscolhasSessao escolhas = pedido.escolhas() == null
                ? new EscolhasSessao(false, null, List.of(), false) : pedido.escolhas();
        Set<Long> adicionais = adicionaisAtivos().stream().map(SessionAddon::id).collect(Collectors.toSet());
        if (!adicionais.containsAll(escolhas.adicionalIds())) {
            throw new AutoatendimentoDadosInvalidosException("Um dos adicionais não está disponível — revise o pedido.");
        }
        Map<String, EssenciaOferecida> oferecidas = disponiveis(faixasAtivas()).stream()
                .collect(Collectors.toMap(EssenciaOferecida::sku, Function.identity(), (a, b) -> a));
        EssenciaOferecida sabor = oferecida(oferecidas, pedido.essenciaSku());
        String sku2 = pedido.essenciaSku2() == null || pedido.essenciaSku2().isBlank() ? null : pedido.essenciaSku2();
        EssenciaOferecida sabor2 = sku2 == null ? null : oferecida(oferecidas, sku2);
        if (sabor2 != null && sabor2.sku().equals(sabor.sku())) {
            sabor2 = null;
        }
        // A proporção é da mescla: um sabor só não tem.
        escolhas = escolhas.withProporcao(sabor2 == null ? null : escolhas.proporcao());
        Instant agora = clock.instant();
        Instant validade = agora.minus(validadeSolicitacao());
        // Um pendente por pessoa: o acompanhante pede no titular, então conta pelo aparelho dele.
        if (acompanhante
                ? repository.existsPendenteDoAparelho(mesa.id(), device, validade)
                : repository.existsPendente(mesa.id(), cliente.id(), validade)) {
            throw new SolicitacaoJaPendenteException(
                    "Você já tem um pedido aguardando o atendente nesta mesa.");
        }
        // Mescla: a faixa mais cara entre os sabores, como no lançamento (PDV-F051).
        EssenciaOferecida faixa = sabor2 != null && sabor2.preco().compareTo(sabor.preco()) > 0 ? sabor2 : sabor;
        SolicitacaoSessao salva = repository.saveSolicitacao(SolicitacaoSessao.nova(mesa.id(), cliente.id(),
                faixa.tierId(), sabor.sku(), sabor2 == null ? null : sabor2.sku(), pedido.observacao(), escolhas,
                device, acompanhante, agora));
        if (ocupacao.isEmpty()) {
            // A mesa fica com quem pediu já no envio: o código vai com o pedido pendente.
            MesaOcupacao nova = repository.saveOcupacao(
                    MesaOcupacao.pendente(mesa.id(), salva.id(), cliente.id(), agora));
            repository.addAcesso(nova.id(), device, false);
        }
        avisarAtendentes(mesa, quemPediu(cliente.nome(), acompanhante), sabor, sabor2);
        centralPedidos.solicitacaoCriada(salva.id(), mesa.nome());
        return salva;
    }

    @Override
    @Transactional(readOnly = true)
    public SolicitacaoSessao solicitacaoPorToken(String token, UUID codigo) {
        SalaoMesa mesa = mesaPorToken(token);
        return repository.findSolicitacaoByCodigo(codigo)
                .filter(s -> s.mesaId().equals(mesa.id()))
                .orElseThrow(() -> new SolicitacaoNotFoundException("Pedido não encontrado."));
    }

    @Override
    public SolicitacaoStatus statusPublico(SolicitacaoSessao solicitacao) {
        return solicitacao.statusEm(clock.instant(), validadeSolicitacao());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> codigoDaMesa(String token, String deviceToken) {
        return ocupacaoAberta(mesaPorToken(token))
                .filter(o -> repository.hasAcesso(o.id(), deviceToken))
                .map(MesaOcupacao::codigo);
    }

    @Override
    @Transactional
    public void acessarPorToken(String token, String codigo, String deviceToken) {
        SalaoMesa mesa = mesaPorToken(token);
        String device = deviceObrigatorio(deviceToken);
        Optional<MesaOcupacao> ocupacao = ocupacaoAberta(mesa);
        if (ocupacao.isEmpty()) {
            return; // Mesa livre: não há o que liberar, o aparelho já pode pedir.
        }
        // Comparação em tempo constante: o código é curto, a resposta não pode vazar dígitos.
        byte[] esperado = ocupacao.get().codigo().getBytes(StandardCharsets.UTF_8);
        byte[] digitado = (codigo == null ? "" : codigo.trim()).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(esperado, digitado)) {
            throw new CodigoMesaInvalidoException(
                    "Código incorreto — confira com quem está na mesa ou peça ao atendente.");
        }
        repository.addAcesso(ocupacao.get().id(), device, true);
    }

    @Override
    @Transactional(readOnly = true)
    public MesaAtual mesaAtualPorToken(String token, String deviceToken) {
        SalaoMesa mesa = mesaPorToken(token);
        MesaOcupacao ocupacao = ocupacaoAberta(mesa)
                .filter(o -> repository.hasAcesso(o.id(), deviceToken))
                .orElseThrow(() -> new MesaOcupadaException(SEM_ACESSO));
        Customer cliente = crmUseCase.findCustomerById(ocupacao.customerId());
        if (ocupacao.pendente()) {
            // Pedido ainda com o atendente: nada lançado, só o código.
            return new MesaAtual(mesa.nome(), mascarar(cliente.nome()), ocupacao.codigo(), ocupacao.abertaEm(),
                    List.of(), BigDecimal.ZERO);
        }
        Comanda comanda = comandaUseCase.getComanda(ocupacao.comandaId());
        return new MesaAtual(mesa.nome(), mascarar(cliente.nome()), ocupacao.codigo(), ocupacao.abertaEm(), itens(comanda),
                comanda.runningTotal(), recentes(repository.findChamadosDaComanda(comanda.id())),
                ultimaSessao(comanda).orElse(null), comprasLoja(comandaUseCase.listStorePurchases(comanda.id())));
    }

    @Override
    @Transactional
    public ChamadoMesa chamarPorToken(String token, NovoChamado pedido) {
        SalaoMesa mesa = mesaPorToken(token);
        String device = deviceObrigatorio(pedido.deviceToken());
        if (pedido.tipo() == null) {
            throw new AutoatendimentoDadosInvalidosException("Diga o que você precisa.");
        }
        if (pedido.tipo() == TipoChamado.CONTA && pedido.formaPagamento() == null) {
            throw new AutoatendimentoDadosInvalidosException("Escolha como vai pagar.");
        }
        Comanda comanda;
        MesaOcupacao ocupacao;
        String onde;
        if (pedido.comandaRef() == null || pedido.comandaRef().isBlank()) {
            // A mesa deste QR, pelo aparelho que tem acesso a ela.
            ocupacao = ocupacaoAberta(mesa)
                    .filter(o -> repository.hasAcesso(o.id(), device))
                    .orElseThrow(() -> new MesaOcupadaException(SEM_ACESSO));
            if (ocupacao.pendente()) {
                throw new MesaOcupadaException("Aguarde o atendente aceitar o pedido para chamar a equipe.");
            }
            comanda = comandaUseCase.getComanda(ocupacao.comandaId());
            onde = mesa.nome();
        } else {
            // Uma comanda do cliente — a do PDV, sem QR —, pela identificação confirmada.
            Customer cliente = confirmado(mesa, pedido.identificacao(), pedido.confirmacao(), "CHAMADO");
            comanda = comandaUseCase.listOpenComandasByCustomer(cliente.id()).stream()
                    .filter(c -> ref(c).equals(pedido.comandaRef().trim()))
                    .findFirst()
                    .orElseThrow(() -> new MesaOcupadaException("Esta comanda não está aberta no seu nome."));
            ocupacao = repository.findOcupacoesByComandaIds(List.of(comanda.id())).stream()
                    .max(Comparator.comparing(MesaOcupacao::abertaEm)).orElse(null);
            onde = ocupacao == null ? comanda.tableOrCustomerLabel() : mesa(ocupacao.mesaId()).nome();
        }
        Instant agora = clock.instant();
        if (repository.existsChamadoAberto(comanda.id(), pedido.tipo(), agora.minus(ChamadoMesa.VALIDADE))) {
            throw new ChamadoJaAbertoException("A equipe já foi avisada — já estamos indo.");
        }
        ChamadoMesa salvo = repository.saveChamado(ChamadoMesa.novo(comanda.id(), ocupacao, pedido.tipo(),
                pedido.formaPagamento(), agora));
        String nome = comanda.customerId() == null ? "O cliente"
                : crmUseCase.findCustomerById(comanda.customerId()).nome();
        avisar("salao.chamado.aviso", NotificationType.CHAMADO_MESA, tituloDoChamado(salvo, onde),
                nome + " chamou pelo QR.");
        centralPedidos.chamadoCriado(salvo.id(), onde, salvo.tipo().name());
        return salvo;
    }

    @Override
    @Transactional
    public void entrarPorToken(String token, Identificacao identificacao, String confirmacao, String deviceToken) {
        SalaoMesa mesa = mesaPorToken(token);
        String device = deviceObrigatorio(deviceToken);
        Optional<MesaOcupacao> ocupacao = ocupacaoAberta(mesa);
        Optional<Customer> cliente = encontrar(identificacao);
        if (ocupacao.isEmpty() || cliente.isEmpty() || !cliente.get().id().equals(ocupacao.get().customerId())) {
            auditarRecusa(mesa, identificacao, "ENTRAR", "MESA_OCUPADA", null);
            throw new MesaOcupadaException("Esta mesa não está aberta no seu nome — peça o código a quem está nela.");
        }
        conferir(mesa, cliente.get(), identificacao, confirmacao, "ENTRAR");
        repository.addAcesso(ocupacao.get().id(), device, false);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mesaId", mesa.id());
        payload.put("mesa", mesa.nome());
        payload.put("ocupacaoId", ocupacao.get().id());
        payload.put("identificacao", peloCpf(identificacao) ? "CPF" : "CELULAR");
        auditEvents.publish(AuditEvent.of(AuditEvent.EventType.SALAO_MESA_ENTRADA, USUARIO_QR, payload));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MesaAbertaDoCliente> minhasComandasPorToken(String token, Identificacao identificacao,
            String confirmacao) {
        SalaoMesa mesa = mesaPorToken(token);
        return mesasAbertas(confirmado(mesa, identificacao, confirmacao, "MINHAS_COMANDAS"), mesa, true);
    }

    // ── PDV ───────────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResult<SolicitacaoResumo> listSolicitacoes(SolicitacaoStatus status, int page, int size) {
        Instant agora = clock.instant();
        Instant validade = agora.minus(validadeSolicitacao());
        // EXPIRADA não é gravada: são as PENDENTE anteriores à validade — filtradas na consulta, para a
        // página e o total baterem (PDV-C061).
        PageResult<SolicitacaoSessao> pagina = switch (status) {
            case EXPIRADA -> repository.findSolicitacoesAntesDe(SolicitacaoStatus.PENDENTE, validade, page, size);
            case PENDENTE -> repository.findSolicitacoes(status, validade, page, size);
            default -> repository.findSolicitacoes(status, null, page, size);
        };
        List<SolicitacaoResumo> resumos = resumir(pagina.content(), agora);
        return new PageResult<>(resumos, pagina.page(), pagina.size(), pagina.totalElements(), pagina.totalPages());
    }

    @Override
    @Transactional
    public SolicitacaoResumo aceitar(Long id, Long cashSessionId, String username, boolean podePagarNoFinal,
            Boolean pagarNoFinal) {
        SolicitacaoSessao solicitacao = pendente(id);
        // O lead pede; o atendente confirma ou nega (cobra agora) — nunca impõe a quem não pediu.
        boolean pagaNoFinal = solicitacao.escolhas().pagarNoFinal() && (pagarNoFinal == null || pagarNoFinal);
        EscolhasSessao escolhas = solicitacao.escolhas().withPagarNoFinal(pagaNoFinal);
        if (pagaNoFinal && !podePagarNoFinal) {
            throw new SessionPayLaterNotAllowedException(username);
        }
        SalaoMesa mesa = mesa(solicitacao.mesaId());
        Instant agora = clock.instant();
        Optional<MesaOcupacao> ocupada = ocupacaoAberta(mesa);
        Long comandaId = ocupada.map(MesaOcupacao::comandaId)
                .or(() -> comandaAbertaDaMesa(mesa))
                .orElseGet(() -> comandaUseCase.openComanda(cashSessionId, mesa.nome(), solicitacao.customerId(),
                        username).id());
        // Paga no final nasce em PREPARANDO (PDV-F034); senão espera o pagamento no caixa. A faixa sai
        // da essência (PDV-F050). Na mescla com proporção, o texto da essência a diz ao preparador.
        ProporcaoMistura proporcao = solicitacao.essenciaSku2() == null ? null : escolhas.proporcao();
        String essencia = proporcao == null ? null
                : textoDaMistura(proporcao, solicitacao.essenciaSku(), solicitacao.essenciaSku2());
        comandaUseCase.addSession(comandaId, new ComandaUseCase.AddSessionCommand(null, essencia,
                escolhas.vasoGrande(), escolhas.carvao(), escolhas.adicionalIds(), false, null, null,
                escolhas.pagarNoFinal(), solicitacao.essenciaSku(), null, solicitacao.essenciaSku2(), null, proporcao),
                username);
        // Ocupada no envio: a mesma ocupação (e o mesmo código) ganha a comanda.
        MesaOcupacao ocupacao = ocupada
                .map(o -> o.pendente() ? repository.saveOcupacao(o.withComandaId(comandaId)) : o)
                .orElseGet(() -> repository.saveOcupacao(
                        MesaOcupacao.nova(mesa.id(), comandaId, solicitacao.customerId(), agora)));
        if (solicitacao.deviceToken() != null) {
            // Quem pediu em mesa já ocupada por outro cliente entra nela como acompanhante.
            repository.addAcesso(ocupacao.id(), solicitacao.deviceToken(),
                    solicitacao.acompanhante() || !ocupacao.customerId().equals(solicitacao.customerId()));
        }
        SolicitacaoSessao aceita = repository.saveSolicitacao(
                solicitacao.aceita(comandaId, username, pagaNoFinal, agora));
        centralPedidos.solicitacaoAtualizada(aceita.id(), aceita.status().name());
        return resumir(List.of(aceita), agora).get(0);
    }

    @Override
    @Transactional
    public SolicitacaoResumo recusar(Long id, String motivo, String username) {
        Instant agora = clock.instant();
        SolicitacaoSessao recusada = repository.saveSolicitacao(pendente(id).recusada(motivo, username, agora));
        centralPedidos.solicitacaoAtualizada(recusada.id(), recusada.status().name());
        return resumir(List.of(recusada), agora).get(0);
    }

    // ── PDV: chamados da mesa ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ChamadoResumo> listChamados(StatusChamado status) {
        Instant agora = clock.instant();
        if (status == StatusChamado.ATENDIDO) {
            return resumirChamados(repository.findChamados(StatusChamado.ATENDIDO, agora.minus(JANELA_ATENDIDOS)));
        }
        List<ChamadoMesa> vigentes = repository.findChamados(StatusChamado.ABERTO, agora.minus(ChamadoMesa.VALIDADE))
                .stream().filter(c -> c.abertoEm(agora)).toList();
        // O da comanda encerrada sai da fila: a mesa já foi embora. Status de todas numa consulta (PDV-C059).
        Set<Long> abertas = comandaUseCase.findOpenComandaIds(
                vigentes.stream().map(ChamadoMesa::comandaId).collect(Collectors.toSet()));
        return resumirChamados(vigentes.stream().filter(c -> abertas.contains(c.comandaId())).toList());
    }

    @Override
    @Transactional
    public ChamadoResumo atenderChamado(Long id, String username) {
        ChamadoMesa chamado = repository.findChamadoByIdForUpdate(id)
                .orElseThrow(() -> new ChamadoNotFoundException("Chamado não encontrado: " + id));
        if (chamado.status() == StatusChamado.ATENDIDO) {
            return resumirChamados(List.of(chamado)).get(0);
        }
        ChamadoMesa atendido = repository.saveChamado(chamado.atendido(username, clock.instant()));
        centralPedidos.chamadoAtualizado(atendido.id(), atendido.status().name());
        return resumirChamados(List.of(atendido)).get(0);
    }

    /**
     * A mesa do QR por nome, ou o rótulo da comanda do PDV; o cliente da comanda por nome. Mesas,
     * comandas e clientes em lote: uma consulta de cada, não uma por chamado (PDV-C059).
     */
    private List<ChamadoResumo> resumirChamados(List<ChamadoMesa> chamados) {
        if (chamados.isEmpty()) return List.of();
        Map<Long, String> mesas = repository.findMesasByIds(chamados.stream().map(ChamadoMesa::mesaId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SalaoMesa::id, SalaoMesa::nome));
        Map<Long, Comanda> comandas = comandaUseCase.listComandasByIds(chamados.stream().map(ChamadoMesa::comandaId)
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(Comanda::id, Function.identity(), (a, b) -> a));
        Map<Long, String> clientes = crmUseCase.findCustomerNames(comandas.values().stream().map(Comanda::customerId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        List<ChamadoResumo> resumos = new ArrayList<>();
        for (ChamadoMesa c : chamados) {
            Comanda comanda = comandas.get(c.comandaId());
            String onde = c.mesaId() != null ? mesas.getOrDefault(c.mesaId(), "Mesa")
                    : comanda == null ? "Comanda" : comanda.tableOrCustomerLabel();
            String cliente = comanda == null || comanda.customerId() == null ? null : clientes.get(comanda.customerId());
            resumos.add(new ChamadoResumo(c, onde, cliente));
        }
        return resumos;
    }

    // ── Disponibilidade ───────────────────────────────────────────────────────────────────────

    /** As que o QR oferece: com faixa ativa, de lata aberta ou fechada. Ordem: marca, sabor. */
    private List<EssenciaOferecida> disponiveis(List<SessionTier> faixasAtivas) {
        return essenciasDoSalao(faixasAtivas).stream().filter(e -> e.disponibilidade().noQr()).toList();
    }

    /** As faixas ativas, na ordem do cardápio. Lidas uma vez por requisição e passadas adiante (PDV-C058). */
    private List<SessionTier> faixasAtivas() {
        return sessionMenuUseCase.listTiers().stream().filter(SessionTier::ativo)
                .sorted(Comparator.comparingInt(SessionTier::ordem)).toList();
    }

    /**
     * Todas as essências com lata aberta ou fechada no depósito das sessões, com a disponibilidade —
     * a que não tem estoque lá fica de fora. Ordem: marca, sabor.
     */
    private List<EssenciaOferecida> essenciasDoSalao(List<SessionTier> faixasAtivas) {
        Warehouse deposito = warehouseRepository.findActiveByType(WarehouseType.SALAO)
                .orElseGet(estoqueUseCase::getDefaultWarehouse);
        Set<String> abertas = estoqueUseCase.listOpenPackages(deposito.code()).stream()
                .map(OpenPackage::sku).collect(Collectors.toSet());
        Map<Long, SessionTier> faixas = faixasAtivas.stream()
                .collect(Collectors.toMap(SessionTier::id, Function.identity()));
        List<Map.Entry<Product, Map<String, String>>> produtos = produtosDeSessao().stream()
                .map(p -> Map.entry(p, skusVendaveis(p))).toList();
        // Saldo livre só dos SKUs de sessão, numa consulta — não o depósito inteiro (PDV-C058).
        Set<String> fechadas = estoqueUseCase.listSkusWithFreeStock(deposito.code(),
                produtos.stream().flatMap(e -> e.getValue().keySet().stream()).collect(Collectors.toSet()));

        List<EssenciaOferecida> lista = new ArrayList<>();
        for (Map.Entry<Product, Map<String, String>> entrada : produtos) {
            Product p = entrada.getKey();
            SessionTier faixa = p.sessionTierId() == null ? null : faixas.get(p.sessionTierId());
            for (Map.Entry<String, String> sku : entrada.getValue().entrySet()) {
                boolean aberta = abertas.contains(sku.getKey());
                if (!aberta && !fechadas.contains(sku.getKey())) continue;
                Disponibilidade disponibilidade = faixa == null ? Disponibilidade.SEM_FAIXA
                        : aberta ? Disponibilidade.ABERTA : Disponibilidade.FECHADA;
                lista.add(new EssenciaOferecida(sku.getKey(), sku.getValue(), p.name(), p.brand(),
                        faixa == null ? null : faixa.id(), faixa == null ? null : faixa.nome(),
                        faixa == null ? null : faixa.preco(), List.of(), false, disponibilidade,
                        motivoFora(disponibilidade)));
            }
        }
        lista.sort(Comparator.comparing((EssenciaOferecida e) -> Objects.toString(e.marca(), ""),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(EssenciaOferecida::sabor, String.CASE_INSENSITIVE_ORDER));
        return comPerfis(lista);
    }

    private static String motivoFora(Disponibilidade disponibilidade) {
        return switch (disponibilidade) {
            case ABERTA -> null;
            case FECHADA -> "Só lata fechada no Salão — abre na 1ª sessão.";
            case SEM_FAIXA -> "Sem faixa de sessão ativa — não aparece no QR.";
        };
    }

    /**
     * Os perfis marcados de cada SKU; o SKU que a casa ainda não configurou ganha os sugeridos pelo
     * nome — sem gravar: marcou (mesmo nenhum), a sugestão para.
     */
    private List<EssenciaOferecida> comPerfis(List<EssenciaOferecida> lista) {
        if (lista.isEmpty()) return lista;
        Set<String> skus = lista.stream().map(EssenciaOferecida::sku).collect(Collectors.toSet());
        Map<String, List<PerfilSabor>> marcados = repository.findPerfis(skus);
        Set<String> configurados = repository.findSkusComPerfilConfigurado(skus);
        return lista.stream().map(e -> {
                    boolean configurado = configurados.contains(e.sku()) || marcados.containsKey(e.sku());
                    return e.comPerfis(configurado ? marcados.getOrDefault(e.sku(), List.of())
                            : PerfilSabor.sugerir(e.sabor() + " " + e.produto()), configurado);
                })
                .toList();
    }

    private List<Product> produtosDeSessao() {
        // EST-F054 — só publicado: rascunho não é cardápio (o arquivado o filtro já exclui).
        ProductFilter filtro = new ProductFilter(null, null, null, true, null, null, ProductStatus.ATIVO, true);
        List<Product> todos = new ArrayList<>();
        for (int page = 0; ; page++) {
            PageResult<Product> pagina = estoqueUseCase.listProducts(page, PAGINA_CATALOGO, filtro,
                    ProductSortField.ID, SortDirection.ASC);
            todos.addAll(pagina.content());
            if (page + 1 >= pagina.totalPages() || pagina.content().isEmpty()) return todos;
        }
    }

    /**
     * SKU vendável → sabor. Mesma regra do admin ({@code skusVendaveis}): as variações ativas e, se o
     * produto não tem variação ou vende o base, o próprio produto.
     */
    static Map<String, String> skusVendaveis(Product p) {
        Map<String, String> skus = new LinkedHashMap<>();
        List<ProductVariant> variacoes = p.variants() == null ? List.of() : p.variants();
        if (variacoes.isEmpty() || p.parentSellable()) {
            skus.put(p.sku(), p.name());
        }
        for (ProductVariant v : variacoes) {
            if (!v.active()) continue;
            String sabor = v.attributes().stream().map(ProductAttribute::value)
                    .filter(Objects::nonNull).collect(Collectors.joining(" · "));
            skus.put(v.sku(), sabor.isBlank() ? p.name() : sabor);
        }
        return skus;
    }

    private static EssenciaOferecida oferecida(Map<String, EssenciaOferecida> oferecidas, String sku) {
        EssenciaOferecida e = sku == null ? null : oferecidas.get(sku);
        if (e == null) {
            throw new EssenciaIndisponivelException("Esta essência não está disponível agora — escolha outra.");
        }
        return e;
    }

    // ── Clientes ──────────────────────────────────────────────────────────────────────────────

    private Optional<Customer> encontrar(Identificacao identificacao) {
        if (identificacao == null) return Optional.empty();
        String cpf = cpfOuNulo(identificacao.cpf());
        String celular = celularOuNulo(identificacao.celular());
        if (cpf == null && celular == null) {
            throw new AutoatendimentoDadosInvalidosException("Informe o CPF ou o celular.");
        }
        List<CustomerMatch> achados = crmUseCase.lookupCustomers(celular, null, cpf);
        return achados.stream().map(CustomerMatch::customer).findFirst();
    }

    private static boolean semIdentificacao(Identificacao identificacao) {
        return identificacao == null || ((identificacao.cpf() == null || identificacao.cpf().isBlank())
                && (identificacao.celular() == null || identificacao.celular().isBlank()));
    }

    private static String cpfOuNulo(String cpf) {
        if (cpf == null || cpf.isBlank()) return null;
        try {
            return CustomerIdentifiers.normalizeCpf(cpf);
        } catch (IllegalArgumentException e) {
            throw new AutoatendimentoDadosInvalidosException("CPF inválido.");
        }
    }

    private static String celularOuNulo(String celular) {
        String digitos = CustomerIdentifiers.normalizePhone(celular);
        if (digitos == null) return null;
        if (!celularValido(digitos)) {
            throw new AutoatendimentoDadosInvalidosException("Celular inválido — use DDD + número.");
        }
        return digitos;
    }

    /** DDD + número: 10 ou 11 dígitos. */
    private static boolean celularValido(String digitos) {
        return digitos != null && digitos.length() >= 10 && digitos.length() <= 11;
    }

    private Identificado identificado(Customer c) {
        return new Identificado(primeiroNome(c.nome()), vip(c), faltando(c));
    }

    private Identificado identificado(Customer c, SalaoMesa mesaDoQr) {
        return new Identificado(primeiroNome(c.nome()), vip(c), faltando(c), mesasAbertas(c, mesaDoQr, false));
    }

    /**
     * As comandas abertas do cliente: as de mesa do QR (pela ocupação) e as do PDV. Sem
     * {@code completo} a lista responde a quem só tem o CPF: token e valor só da mesa deste QR, sem
     * consumo nem chamados — não pode dizer onde mais o cliente está nem quanto deve. Completo, depois
     * da confirmação, vem tudo.
     */
    private List<MesaAbertaDoCliente> mesasAbertas(Customer c, SalaoMesa mesaDoQr, boolean completo) {
        List<Comanda> comandas = comandaUseCase.listOpenComandasByCustomer(c.id());
        if (comandas.isEmpty()) return List.of();
        List<Long> ids = comandas.stream().map(Comanda::id).toList();
        Map<Long, MesaOcupacao> ocupacoes = repository.findOcupacoesByComandaIds(ids).stream()
                .collect(Collectors.toMap(MesaOcupacao::comandaId, Function.identity(),
                        (a, b) -> a.abertaEm().isAfter(b.abertaEm()) ? a : b));
        Map<Long, SalaoMesa> mesas = repository.findMesasByIds(ocupacoes.values().stream()
                        .map(MesaOcupacao::mesaId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SalaoMesa::id, Function.identity()));
        // Consumo, chamados e compras da loja de todas as comandas em lote, não por comanda (PDV-C060).
        Map<Long, List<ChamadoMesa>> chamados = completo ? repository.findChamadosDasComandas(ids) : Map.of();
        Map<Long, List<Order>> compras = completo ? comandaUseCase.listStorePurchasesByComandas(ids) : Map.of();
        List<MesaAbertaDoCliente> lista = new ArrayList<>();
        for (Comanda comanda : comandas) {
            MesaOcupacao o = ocupacoes.get(comanda.id());
            SalaoMesa m = o == null ? null : mesas.get(o.mesaId());
            boolean estaMesa = m != null && m.id().equals(mesaDoQr.id());
            boolean revela = completo || estaMesa;
            lista.add(new MesaAbertaDoCliente(ref(comanda),
                    m == null ? MesaAbertaDoCliente.Origem.PDV : MesaAbertaDoCliente.Origem.QR,
                    m != null && revela ? m.token() : null,
                    m == null ? comanda.tableOrCustomerLabel() : m.nome(),
                    o == null ? comanda.openedAt() : o.abertaEm(),
                    revela ? comanda.runningTotal() : null, estaMesa,
                    completo ? itens(comanda) : List.of(),
                    completo ? recentes(chamados.getOrDefault(comanda.id(), List.of())) : List.of(),
                    completo ? comprasLoja(compras.getOrDefault(comanda.id(), List.of())) : List.of()));
        }
        // A desta mesa primeiro: é a que o QR deixa entrar.
        lista.sort(Comparator.comparing(MesaAbertaDoCliente::estaMesa).reversed());
        return lista;
    }

    /**
     * Id opaco da comanda para o lado público: não expõe o sequencial (o volume da casa) e só é
     * resolvido entre as comandas do próprio cliente, depois da confirmação.
     */
    static String ref(Comanda comanda) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(("comanda:" + comanda.id() + ":"
                    + comanda.openedAt()).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /**
     * O cliente identificado e confirmado: os 4 últimos dígitos do celular (veio pelo CPF) ou os 3
     * primeiros do CPF (veio pelo celular).
     *
     * @throws ClienteNaoIdentificadoException
     * @throws ConfirmacaoInvalidaException
     */
    private Customer confirmado(SalaoMesa mesa, Identificacao identificacao, String confirmacao, String acao) {
        Customer cliente = encontrar(identificacao).orElseThrow(() ->
                new ClienteNaoIdentificadoException(CADASTRO_NAO_ENCONTRADO));
        conferir(mesa, cliente, identificacao, confirmacao, acao);
        return cliente;
    }

    private void conferir(SalaoMesa mesa, Customer cliente, Identificacao identificacao, String confirmacao,
            String acao) {
        boolean peloCpf = peloCpf(identificacao);
        // Travado nem a certa entra: senão a trava só avisaria quando parar de tentar.
        if (auditLog.countSince(AuditEvent.EventType.SALAO_CONFIRMACAO_RECUSADA.name(), "customer:" + cliente.id(),
                clock.instant().minus(JANELA_TRAVA)) >= TENTATIVAS_CONFIRMACAO) {
            auditarRecusa(mesa, identificacao, acao, "CONFIRMACAO_BLOQUEADA", null);
            throw new ConfirmacaoBloqueadaException("Muitas tentativas erradas — tente de novo em "
                    + JANELA_TRAVA.toMinutes() + " minutos ou peça o código a quem está na mesa.",
                    JANELA_TRAVA.toSeconds());
        }
        // Comparação em tempo constante, como o código da mesa. Sem o dado no cadastro não há o que
        // conferir: entra com o código.
        String esperado = peloCpf ? ultimos(CustomerIdentifiers.normalizePhone(cliente.contato()), 4)
                : primeiros(digitos(cliente.cpf()), 3);
        String digitado = digitos(confirmacao);
        if (esperado == null || digitado == null || !MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                digitado.getBytes(StandardCharsets.UTF_8))) {
            auditarRecusa(mesa, identificacao, acao, "CONFIRMACAO_INVALIDA", cliente.id());
            throw new ConfirmacaoInvalidaException(peloCpf
                    ? "Os 4 últimos dígitos do celular não conferem."
                    : "Os 3 primeiros dígitos do CPF não conferem.");
        }
    }

    private static boolean peloCpf(Identificacao identificacao) {
        return identificacao != null && cpfOuNulo(identificacao.cpf()) != null;
    }

    private static String digitos(String texto) {
        if (texto == null) return null;
        String d = texto.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    private static String ultimos(String digitos, int n) {
        return digitos == null || digitos.length() < n ? null : digitos.substring(digitos.length() - n);
    }

    private static String primeiros(String digitos, int n) {
        return digitos == null || digitos.length() < n ? null : digitos.substring(0, n);
    }

    private boolean vip(Customer c) {
        return crmUseCase.listCustomerTags(c.id()).stream().anyMatch(Tag::isVip);
    }

    /** O que o QR exige e o cadastro ainda não tem. */
    static List<String> faltando(Customer c) {
        List<String> faltam = new ArrayList<>();
        if (!celularValido(CustomerIdentifiers.normalizePhone(c.contato()))) faltam.add("celular");
        if (c.cpf() == null || c.cpf().isBlank()) faltam.add("cpf");
        if (c.dataNascimento() == null) faltam.add("dataNascimento");
        return faltam;
    }

    /** O dado que o lead quer completar não pode ser de outro cliente. */
    private static void exigirLivre(List<CustomerMatch> achados, Customer cliente, String campo) {
        if (achados.stream().anyMatch(m -> !m.customer().id().equals(cliente.id()))) {
            throw new ClienteJaCadastradoException("Este " + campo + " já está em outro cadastro — fale com o atendente.");
        }
    }

    /** "Maria Luiza" → "Maria". O lead se reconhece; o sobrenome fica no cadastro. */
    static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) return "Cliente";
        return nome.trim().split("\\s+")[0];
    }

    /** "Maria Luiza" → "Ma***". O suficiente para o lead se reconhecer, sem expor o cadastro. */
    static String mascarar(String nome) {
        if (nome == null || nome.isBlank()) return "***";
        String primeiro = primeiroNome(nome);
        return primeiro.substring(0, Math.min(2, primeiro.length())) + "***";
    }

    // ── Apoio ─────────────────────────────────────────────────────────────────────────────────

    private SalaoMesa mesa(Long id) {
        return repository.findMesaById(id)
                .orElseThrow(() -> new SalaoMesaNotFoundException("Mesa não encontrada: " + id));
    }

    private SalaoMesa mesaPorToken(String token) {
        return repository.findMesaByToken(token).filter(SalaoMesa::ativo)
                .orElseThrow(() -> new SalaoMesaNotFoundException("QR inválido ou mesa desativada."));
    }

    private SolicitacaoSessao pendente(Long id) {
        SolicitacaoSessao s = repository.findSolicitacaoByIdForUpdate(id)
                .orElseThrow(() -> new SolicitacaoNotFoundException("Solicitação não encontrada: " + id));
        SolicitacaoStatus status = s.statusEm(clock.instant(), validadeSolicitacao());
        if (status != SolicitacaoStatus.PENDENTE) {
            throw new SolicitacaoNaoPendenteException(status == SolicitacaoStatus.EXPIRADA
                    ? "O pedido expirou — confirme com o cliente e lance pelo PDV."
                    : "O pedido já foi " + (status == SolicitacaoStatus.ACEITA ? "aceito." : "recusado."));
        }
        return s;
    }

    /**
     * A ocupação da mesa, se ainda vale: com comanda, enquanto ela estiver aberta; sem comanda (ocupada
     * no envio), enquanto o pedido estiver pendente — recusado ou expirado sem nada lançado, a mesa
     * está livre.
     */
    private Optional<MesaOcupacao> ocupacaoAberta(SalaoMesa mesa) {
        return repository.findUltimaOcupacao(mesa.id()).filter(o -> !valendo(List.of(o)).isEmpty());
    }

    /**
     * As ocupações que ainda valem (ver {@link #ocupacaoAberta}). Só os ids das comandas abertas e as
     * solicitações de origem, em lote: nada de carregar a comanda inteira para ler o status (PDV-C056).
     */
    private List<MesaOcupacao> valendo(List<MesaOcupacao> ocupacoes) {
        if (ocupacoes.isEmpty()) return List.of();
        List<Long> comandaIds = ocupacoes.stream().filter(o -> !o.pendente()).map(MesaOcupacao::comandaId).toList();
        List<Long> solicitacaoIds = ocupacoes.stream().filter(MesaOcupacao::pendente)
                .map(MesaOcupacao::solicitacaoId).filter(Objects::nonNull).toList();
        Set<Long> abertas = comandaIds.isEmpty() ? Set.of() : comandaUseCase.findOpenComandaIds(comandaIds);
        Instant agora = clock.instant();
        Duration validade = validadeSolicitacao();
        Set<Long> pendentes = solicitacaoIds.isEmpty() ? Set.of()
                : repository.findSolicitacoesByIds(solicitacaoIds).stream()
                        .filter(s -> s.statusEm(agora, validade) == SolicitacaoStatus.PENDENTE)
                        .map(SolicitacaoSessao::id).collect(Collectors.toSet());
        return ocupacoes.stream()
                .filter(o -> o.pendente() ? pendentes.contains(o.solicitacaoId()) : abertas.contains(o.comandaId()))
                .toList();
    }

    private boolean comandaAberta(Long comandaId) {
        return comandaUseCase.findOpenComandaIds(List.of(comandaId)).contains(comandaId);
    }

    private AcessoMesa acesso(SalaoMesa mesa, String deviceToken) {
        return ocupacaoAberta(mesa)
                .map(o -> repository.hasAcesso(o.id(), deviceToken) ? AcessoMesa.LIBERADO : AcessoMesa.BLOQUEADO)
                .orElse(AcessoMesa.LIVRE);
    }

    private static String deviceObrigatorio(String deviceToken) {
        if (deviceToken == null || deviceToken.isBlank() || deviceToken.trim().length() > DEVICE_TOKEN_MAX) {
            throw new AutoatendimentoDadosInvalidosException("Recarregue a página e tente de novo.");
        }
        return deviceToken.trim();
    }

    /**
     * A última sessão lançada na comanda — pelo QR ou pelo PDV —, do jeito que o QR remonta o pedido.
     * Só a sessão com sabor do catálogo: a em texto livre não tem SKU para o QR oferecer de novo.
     */
    static Optional<MesaAtual.UltimaSessao> ultimaSessao(Comanda comanda) {
        return comanda.items().stream()
                .filter(i -> i.mode() == ConsumptionMode.SESSAO && i.essenceSku() != null)
                .max(Comparator.comparing(ComandaItem::addedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ComandaItem::id, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(i -> {
                    SessionSetup setup = i.setup();
                    return new MesaAtual.UltimaSessao(i.essenceSku(), i.essenceSku2(), i.essenceRatio(),
                            setup != null && setup.vasoGrande(), setup == null ? null : setup.charcoal(),
                            setup == null ? List.of() : setup.addons().stream().map(SessionSetup.Addon::addonId)
                                    .filter(Objects::nonNull).toList());
                });
    }

    /** "Zomo · Uva 70% + Zomo · Menta 30%". Longo demais para a nota, fica com o texto padrão do lançamento. */
    private String textoDaMistura(ProporcaoMistura proporcao, String sku1, String sku2) {
        Map<String, String> rotulos = rotulos(List.of(sku1, sku2));
        String texto = proporcao.rotulo(rotulos.getOrDefault(sku1, sku1), rotulos.getOrDefault(sku2, sku2));
        return texto.length() <= TEXTO_MISTURA_MAX ? texto : null;
    }

    private static String tituloDoChamado(ChamadoMesa c, String onde) {
        return switch (c.tipo()) {
            case CONTA -> "Conta pedida na " + onde + " · " + rotulo(c.formaPagamento());
            case RECOLHER -> "Recolher narguilé na " + onde;
            case CARVAO -> "Trocar carvão na " + onde;
            case ATENDENTE -> "Atendente chamado na " + onde;
        };
    }

    private static String rotulo(FormaPagamentoChamado forma) {
        return switch (forma) {
            case PIX -> "Pix";
            case CARTAO -> "Cartão";
            case DINHEIRO -> "Dinheiro";
        };
    }

    /** O consumo da comanda como o cliente o vê, na ordem do lançamento; a sessão com a linha do tempo. */
    private static List<MesaAtual.Item> itens(Comanda comanda) {
        return comanda.items().stream()
                .sorted(Comparator.comparing(ComandaItem::addedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(i -> new MesaAtual.Item(i.productName(), i.quantity(), i.netSubtotal(), !i.isOpen(), i.addedAt(),
                        sessao(i)))
                .toList();
    }

    /**
     * A linha do tempo (PDV-F035) da linha de sessão; nula nas demais. Toda linha de sessão tem a
     * sua: a anterior à V132, sem andamento gravado, vem só com o lançamento e o status nulo.
     */
    static MesaAtual.Sessao sessao(ComandaItem item) {
        if (item.mode() == null || !item.mode().isMenuSession()) return null;
        // O texto da linha é a essência (com a proporção) mais o sufixo do vaso grande.
        String notes = item.notes();
        if (notes != null && notes.endsWith(ComandaItem.VASO_GRANDE_NOTE_SUFFIX)) {
            notes = notes.substring(0, notes.length() - ComandaItem.VASO_GRANDE_NOTE_SUFFIX.length());
        }
        String essencia = notes == null || notes.isBlank() ? null : notes.trim();
        SessionTimeline t = SessionTimeline.of(item, null);
        if (t == null) {
            return new MesaAtual.Sessao(null, essencia, false, item.addedAt(), null, null, null, null, null);
        }
        return new MesaAtual.Sessao(t.status(), essencia, t.pagarNoFinal(), t.lancadaEm(), t.inicioEm(),
                t.entregueEm(), t.recolhidaEm(), t.preparoMin(), t.naMesaMin());
    }

    /** As vendas da loja ligadas à mesa (PDV-F036); a cancelada ou reembolsada não conta. */
    private static List<MesaAtual.CompraLoja> comprasLoja(List<Order> vendas) {
        return vendas.stream()
                .filter(o -> o.status() != OrderStatus.CANCELADO && o.status() != OrderStatus.REEMBOLSADO)
                .map(SalaoAutoatendimentoService::compraLoja)
                .toList();
    }

    private static MesaAtual.CompraLoja compraLoja(Order o) {
        return new MesaAtual.CompraLoja(o.orderNumber(), o.concludedAt() != null ? o.concludedAt() : o.createdAt(),
                o.items().stream().map(i -> new MesaAtual.ItemCompra(
                        i.productName() == null ? i.sku() : i.productName(), i.quantity(), i.netAmount())).toList(),
                o.netAmount());
    }

    /** Validade da solicitação pelo QR: a da Central de Pedidos (ECM-F013), 30 min de padrão. */
    private Duration validadeSolicitacao() {
        return Duration.ofMinutes(centralConfig.atual().expiracaoSolicitacaoMin());
    }

    /** Dos chamados da comanda, os que a tela do cliente mostra ({@link ChamadoMesa#recenteEm}). */
    private List<ChamadoMesa> recentes(List<ChamadoMesa> chamados) {
        Instant agora = clock.instant();
        return chamados.stream().filter(c -> c.recenteEm(agora)).toList();
    }

    /**
     * Sem CPF nem celular no rastro: só a mesa, a ação, por onde a pessoa se identificou e o motivo. O
     * cliente vai só no erro de confirmação — é a contagem da trava; a tentativa já travada não a
     * estende.
     */
    private void auditarRecusa(SalaoMesa mesa, Identificacao identificacao, String acao, String motivo,
            Long customerId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (customerId != null) payload.put("customerId", customerId);
        payload.put("mesaId", mesa.id());
        payload.put("mesa", mesa.nome());
        payload.put("acao", acao);
        payload.put("identificacao", peloCpf(identificacao) ? "CPF" : "CELULAR");
        payload.put("motivo", motivo);
        auditEvents.publish(AuditEvent.of(AuditEvent.EventType.SALAO_CONFIRMACAO_RECUSADA, USUARIO_QR, payload));
    }

    private List<SessionAddon> adicionaisAtivos() {
        return sessionMenuUseCase.listAddons().stream().filter(SessionAddon::ativo)
                .sorted(Comparator.comparingInt(SessionAddon::ordem)).toList();
    }

    /**
     * A comanda da última solicitação aceita na mesa, se ainda aberta — para as aceitas antes da
     * ocupação existir (V168).
     */
    private Optional<Long> comandaAbertaDaMesa(SalaoMesa mesa) {
        return repository.findUltimaAceita(mesa.id())
                .map(SolicitacaoSessao::comandaId)
                .filter(Objects::nonNull)
                .filter(this::comandaAberta);
    }

    private List<SolicitacaoResumo> resumir(List<SolicitacaoSessao> linhas, Instant agora) {
        Duration validade = validadeSolicitacao();
        if (linhas.isEmpty()) return List.of();
        // Mesas, clientes e rótulos dos sabores em lote: uma consulta de cada, não uma por linha (PDV-C059).
        Map<Long, String> mesas = repository.findMesasByIds(linhas.stream().map(SolicitacaoSessao::mesaId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SalaoMesa::id, SalaoMesa::nome));
        Map<Long, Customer> clientes = crmUseCase.findCustomersByIds(linhas.stream()
                .map(SolicitacaoSessao::customerId).collect(Collectors.toSet()));
        Map<String, String> rotulos = rotulos(linhas.stream()
                .flatMap(s -> Stream.of(s.essenciaSku(), s.essenciaSku2())).filter(Objects::nonNull).toList());
        Map<Long, SessionTier> faixas = sessionMenuUseCase.listTiers().stream()
                .collect(Collectors.toMap(SessionTier::id, Function.identity()));
        Map<Long, SessionAddon> adicionais = sessionMenuUseCase.listAddons().stream()
                .collect(Collectors.toMap(SessionAddon::id, Function.identity()));
        SessionSettings settings = sessionMenuUseCase.getSettings();
        List<SolicitacaoResumo> resumos = new ArrayList<>();
        for (SolicitacaoSessao s : linhas) {
            Customer c = clientes.get(s.customerId());
            SessionTier faixa = s.tierId() == null ? null : faixas.get(s.tierId());
            EscolhasSessao x = s.escolhas();
            List<SessionAddon> escolhidos = x.adicionalIds().stream().map(adicionais::get).filter(Objects::nonNull)
                    .toList();
            BigDecimal total = faixa == null ? null : faixa.preco()
                    .add(x.vasoGrande() ? settings.upgradeVasoGrandePreco() : BigDecimal.ZERO)
                    .add(settings.charcoalSurcharge(x.carvao()))
                    .add(escolhidos.stream().map(SessionAddon::preco).reduce(BigDecimal.ZERO, BigDecimal::add));
            resumos.add(new SolicitacaoResumo(s, s.statusEm(agora, validade), mesas.getOrDefault(s.mesaId(), "Mesa"),
                    quemPediu(c == null ? "Cliente" : c.nome(), s.acompanhante()), c == null ? null : c.contato(),
                    faixa == null ? null : faixa.nome(), faixa == null ? null : faixa.preco(),
                    rotulos.getOrDefault(s.essenciaSku(), s.essenciaSku()),
                    s.essenciaSku2() == null ? null : rotulos.getOrDefault(s.essenciaSku2(), s.essenciaSku2()),
                    escolhidos.stream().map(SessionAddon::nome).toList(), total));
        }
        return resumos;
    }

    /**
     * SKU → "Zomo · Menta" (o produto e o sabor), numa consulta. O SKU que sumiu do catálogo não volta
     * no mapa: quem usa mostra o próprio SKU.
     */
    private Map<String, String> rotulos(Collection<String> skus) {
        Set<String> pedidos = new HashSet<>(skus);
        Map<String, String> rotulos = new HashMap<>();
        for (Product p : estoqueUseCase.findProductsByAnySku(pedidos)) {
            Map<String, String> vendaveis = skusVendaveis(p);
            Stream.concat(Stream.of(p.sku()), (p.variants() == null ? List.<ProductVariant>of() : p.variants())
                            .stream().map(ProductVariant::sku))
                    .filter(pedidos::contains)
                    .forEach(sku -> {
                        String sabor = vendaveis.getOrDefault(sku, p.name());
                        rotulos.put(sku, sabor.equals(p.name()) ? p.name() : p.name() + " · " + sabor);
                    });
        }
        return rotulos;
    }

    /** "Acompanhante de Maria": quem pediu sem CPF num aparelho que entrou pelo código. */
    static String quemPediu(String titular, boolean acompanhante) {
        return acompanhante ? "Acompanhante de " + titular : titular;
    }

    private void avisarAtendentes(SalaoMesa mesa, String quem, EssenciaOferecida sabor,
            EssenciaOferecida sabor2) {
        String titulo = "Sessão pedida na " + mesa.nome();
        String corpo = quem + " pediu " + sabor.sabor()
                + (sabor2 == null ? "" : " + " + sabor2.sabor()) + " (" + sabor.marca() + ") pelo QR.";
        avisar("salao.solicitacao.aviso", NotificationType.SOLICITACAO_SESSAO, titulo, corpo);
    }

    /** Avisa quem opera mesa: todos com {@link #PERMISSAO_ATENDENTE}. */
    private void avisar(String chave, NotificationType tipo, String titulo, String corpo) {
        avisoOperadores.avisar(chave, PERMISSAO_ATENDENTE, tipo, titulo, corpo, DESTINO_AVISO);
    }

    private static String novoToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
