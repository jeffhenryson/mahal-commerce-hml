package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.SessionPayLaterNotAllowedException;
import com.cernecommerce.core.domain.exception.salao.AutoatendimentoDadosInvalidosException;
import com.cernecommerce.core.domain.exception.salao.ClienteJaCadastradoException;
import com.cernecommerce.core.domain.exception.salao.ClienteNaoIdentificadoException;
import com.cernecommerce.core.domain.exception.salao.CodigoMesaInvalidoException;
import com.cernecommerce.core.domain.exception.salao.EssenciaIndisponivelException;
import com.cernecommerce.core.domain.exception.salao.MesaOcupadaException;
import com.cernecommerce.core.domain.exception.salao.SalaoMesaNomeDuplicadoException;
import com.cernecommerce.core.domain.exception.salao.SalaoMesaNotFoundException;
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
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pdv.SessionAddon;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.domain.model.salao.AcessoMesa;
import com.cernecommerce.core.domain.model.salao.CardapioDaMesa;
import com.cernecommerce.core.domain.model.salao.EscolhasSessao;
import com.cernecommerce.core.domain.model.salao.EssenciaDoDia;
import com.cernecommerce.core.domain.model.salao.EssenciaOferecida;
import com.cernecommerce.core.domain.model.salao.MesaAtual;
import com.cernecommerce.core.domain.model.salao.MesaOcupacao;
import com.cernecommerce.core.domain.model.salao.SalaoMesa;
import com.cernecommerce.core.domain.model.salao.SolicitacaoResumo;
import com.cernecommerce.core.domain.model.salao.SolicitacaoSessao;
import com.cernecommerce.core.domain.model.salao.SolicitacaoStatus;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase;
import com.cernecommerce.core.ports.in.SessionMenuUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import com.cernecommerce.core.ports.out.salao.SalaoAutoatendimentoRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Autoatendimento do Salão — ver {@link SalaoAutoatendimentoUseCase}.
 *
 * <h2>O que o QR oferece</h2>
 * <p>A mesma regra do seletor de essência do PDV ({@code montarCatalogoEssencias} no admin): as
 * sessões consomem do depósito do Salão, se houver um ativo, senão do depósito padrão; o QR oferece
 * as essências com <b>lata aberta</b> lá e faixa ativa (a sem faixa o lançamento recusaria). As
 * marcadas no dia são destaques: aparecem no topo, não filtram.</p>
 *
 * <h2>Mesa ocupada</h2>
 * <p>O aceite da 1ª solicitação de mesa livre grava uma {@link MesaOcupacao} com código de 4 dígitos
 * e dá acesso ao aparelho que pediu. Enquanto a comanda dela estiver aberta, aparelho sem acesso
 * não pede nem vê a mesa — entra com o código. Encerrou a comanda, a mesa está livre de novo.</p>
 *
 * <h2>Dados pessoais no lado público</h2>
 * <p>Identificar responde só se achou e o primeiro nome mascarado. O cadastro público nunca toca
 * cliente existente (o find-or-create do CRM completaria o CPF de um terceiro): CPF ou celular já
 * usado é 409 e o lead se identifica. O pedido não carrega id — o servidor resolve o cliente de novo
 * pelo CPF/celular.</p>
 */
public class SalaoAutoatendimentoService implements SalaoAutoatendimentoUseCase {

    static final ZoneId ZONA_SALAO = ZoneId.of("America/Sao_Paulo");
    static final String ORIGEM_LEAD = "QR Salão";
    /** Quem recebe o aviso de pedido novo: quem opera mesa. */
    static final String PERMISSAO_ATENDENTE = "PDV_COMANDA_MANAGE";
    private static final String DESTINO_AVISO = "/pdv";
    private static final int PAGINA_CATALOGO = 100;
    private static final int DEVICE_TOKEN_MAX = 64;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SalaoAutoatendimentoRepository repository;
    private final EstoqueUseCase estoqueUseCase;
    private final WarehouseRepository warehouseRepository;
    private final SessionMenuUseCase sessionMenuUseCase;
    private final CrmUseCase crmUseCase;
    private final ComandaUseCase comandaUseCase;
    private final NotificationUseCase notificationUseCase;
    private final UserRepository userRepository;
    private final NotificationSsePort ssePort;
    private final AfterCommitExecutor afterCommit;
    private final Clock clock;

    public SalaoAutoatendimentoService(SalaoAutoatendimentoRepository repository, EstoqueUseCase estoqueUseCase,
            WarehouseRepository warehouseRepository, SessionMenuUseCase sessionMenuUseCase, CrmUseCase crmUseCase, ComandaUseCase comandaUseCase,
            NotificationUseCase notificationUseCase, UserRepository userRepository, NotificationSsePort ssePort,
            AfterCommitExecutor afterCommit, Clock clock) {
        this.repository = repository;
        this.estoqueUseCase = estoqueUseCase;
        this.warehouseRepository = warehouseRepository;
        this.sessionMenuUseCase = sessionMenuUseCase;
        this.crmUseCase = crmUseCase;
        this.comandaUseCase = comandaUseCase;
        this.notificationUseCase = notificationUseCase;
        this.userRepository = userRepository;
        this.ssePort = ssePort;
        this.afterCommit = afterCommit;
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
        if (limpo != null && repository.existsMesaComNome(limpo, null)) {
            throw new SalaoMesaNomeDuplicadoException("Já existe uma mesa chamada \"" + limpo + "\".");
        }
        return repository.saveMesa(new SalaoMesa(null, limpo, novoToken(), true, clock.instant()));
    }

    @Override
    @Transactional
    public SalaoMesa updateMesa(Long id, String nome, boolean ativo) {
        SalaoMesa atual = mesa(id);
        String limpo = nome == null ? atual.nome() : nome.trim();
        if (repository.existsMesaComNome(limpo, id)) {
            throw new SalaoMesaNomeDuplicadoException("Já existe uma mesa chamada \"" + limpo + "\".");
        }
        return repository.saveMesa(atual.withNome(limpo).withAtivo(ativo));
    }

    @Override
    @Transactional
    public SalaoMesa regenerarToken(Long id) {
        return repository.saveMesa(mesa(id).withToken(novoToken()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MesaOcupacao> ocupacoesAbertas() {
        return repository.findAllMesas().stream().map(this::ocupacaoAberta).flatMap(Optional::stream).toList();
    }

    // ── Admin: essências do dia ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<EssenciaDoDia> listEssenciasDoDia(LocalDate data) {
        Set<String> doDia = repository.findEssenciasDoDia(data);
        return disponiveis().stream().map(e -> new EssenciaDoDia(e, doDia.contains(e.sku()))).toList();
    }

    @Override
    @Transactional
    public void setEssenciasDoDia(LocalDate data, Collection<String> skus) {
        repository.replaceEssenciasDoDia(data, skus == null ? List.of() : new LinkedHashSet<>(skus.stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList()));
    }

    @Override
    @Transactional
    public void copiarEssenciasDoDia(LocalDate de, LocalDate para) {
        repository.replaceEssenciasDoDia(para, repository.findEssenciasDoDia(de));
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
        List<SessionTier> faixas = sessionMenuUseCase.listTiers().stream().filter(SessionTier::ativo)
                .sorted(Comparator.comparingInt(SessionTier::ordem)).toList();
        List<EssenciaOferecida> oferecidas = disponiveis();
        Set<String> destaques = new HashSet<>(repository.findEssenciasDoDia(hoje()));
        destaques.retainAll(oferecidas.stream().map(EssenciaOferecida::sku).collect(Collectors.toSet()));
        SessionSettings settings = sessionMenuUseCase.getSettings();
        return new CardapioDaMesa(mesa.nome(), faixas, oferecidas, destaques, settings.upgradeVasoGrandePreco(),
                settings.upgradeCarvaoJumboPreco(), adicionaisAtivos(), acesso(mesa, deviceToken));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Identificado> identificarPorToken(String token, Identificacao identificacao) {
        mesaPorToken(token);
        return encontrar(identificacao).map(this::identificado);
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
        mesaPorToken(token);
        Customer cliente = encontrar(completar.identificacao()).orElseThrow(() ->
                new ClienteNaoIdentificadoException("Não encontramos seu cadastro — confira o CPF ou celular."));
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
        return identificado(salvo);
    }

    @Override
    @Transactional
    public SolicitacaoSessao solicitarPorToken(String token, Pedido pedido) {
        SalaoMesa mesa = mesaPorToken(token);
        String device = deviceObrigatorio(pedido.deviceToken());
        if (acesso(mesa, device) == AcessoMesa.BLOQUEADO) {
            throw new MesaOcupadaException("Esta mesa já está com outro cliente — digite o código da mesa.");
        }
        Customer cliente = encontrar(pedido.identificacao()).orElseThrow(() ->
                new ClienteNaoIdentificadoException("Não encontramos seu cadastro — confira o CPF ou celular."));
        if (!faltando(cliente).isEmpty()) {
            throw new AutoatendimentoDadosInvalidosException("Complete seu cadastro antes de pedir.");
        }
        EscolhasSessao escolhas = pedido.escolhas() == null
                ? new EscolhasSessao(false, null, List.of(), false) : pedido.escolhas();
        if (escolhas.pagarNoFinal() && !vip(cliente)) {
            throw new AutoatendimentoDadosInvalidosException("Pagar no final é só para clientes VIP.");
        }
        Set<Long> adicionais = adicionaisAtivos().stream().map(SessionAddon::id).collect(Collectors.toSet());
        if (!adicionais.containsAll(escolhas.adicionalIds())) {
            throw new AutoatendimentoDadosInvalidosException("Um dos adicionais não está disponível — revise o pedido.");
        }
        Map<String, EssenciaOferecida> oferecidas = disponiveis().stream()
                .collect(Collectors.toMap(EssenciaOferecida::sku, Function.identity(), (a, b) -> a));
        EssenciaOferecida sabor = oferecida(oferecidas, pedido.essenciaSku());
        String sku2 = pedido.essenciaSku2() == null || pedido.essenciaSku2().isBlank() ? null : pedido.essenciaSku2();
        EssenciaOferecida sabor2 = sku2 == null ? null : oferecida(oferecidas, sku2);
        if (sabor2 != null && sabor2.sku().equals(sabor.sku())) {
            sabor2 = null;
        }
        Instant agora = clock.instant();
        if (repository.existsPendente(mesa.id(), cliente.id(), agora.minus(SolicitacaoSessao.VALIDADE))) {
            throw new SolicitacaoJaPendenteException(
                    "Você já tem um pedido aguardando o atendente nesta mesa.");
        }
        // Mescla: a faixa mais cara entre os sabores, como no lançamento (PDV-F051).
        EssenciaOferecida faixa = sabor2 != null && sabor2.preco().compareTo(sabor.preco()) > 0 ? sabor2 : sabor;
        SolicitacaoSessao salva = repository.saveSolicitacao(SolicitacaoSessao.nova(mesa.id(), cliente.id(),
                faixa.tierId(), sabor.sku(), sabor2 == null ? null : sabor2.sku(), pedido.observacao(), escolhas,
                device, agora));
        avisarAtendentes(mesa, cliente, sabor, sabor2);
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
        return solicitacao.statusEm(clock.instant());
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
        repository.addAcesso(ocupacao.get().id(), device);
    }

    @Override
    @Transactional(readOnly = true)
    public MesaAtual mesaAtualPorToken(String token, String deviceToken) {
        SalaoMesa mesa = mesaPorToken(token);
        MesaOcupacao ocupacao = ocupacaoAberta(mesa)
                .filter(o -> repository.hasAcesso(o.id(), deviceToken))
                .orElseThrow(() -> new MesaOcupadaException("Sem acesso a esta mesa — digite o código da mesa."));
        Comanda comanda = comandaUseCase.getComanda(ocupacao.comandaId());
        Customer cliente = crmUseCase.findCustomerById(ocupacao.customerId());
        List<MesaAtual.Item> itens = comanda.items().stream()
                .sorted(Comparator.comparing(ComandaItem::addedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(i -> new MesaAtual.Item(i.productName(), i.quantity(), i.netSubtotal(), !i.isOpen(), i.addedAt()))
                .toList();
        return new MesaAtual(mesa.nome(), mascarar(cliente.nome()), ocupacao.codigo(), ocupacao.abertaEm(), itens,
                comanda.runningTotal());
    }

    // ── PDV ───────────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResult<SolicitacaoResumo> listSolicitacoes(SolicitacaoStatus status, int page, int size) {
        Instant agora = clock.instant();
        // EXPIRADA não é gravada: são as PENDENTE anteriores à validade.
        PageResult<SolicitacaoSessao> pagina = status == SolicitacaoStatus.EXPIRADA
                ? repository.findSolicitacoes(SolicitacaoStatus.PENDENTE, null, page, size)
                : repository.findSolicitacoes(status, status == SolicitacaoStatus.PENDENTE
                        ? agora.minus(SolicitacaoSessao.VALIDADE) : null, page, size);
        List<SolicitacaoSessao> linhas = status == SolicitacaoStatus.EXPIRADA
                ? pagina.content().stream().filter(s -> s.statusEm(agora) == SolicitacaoStatus.EXPIRADA).toList()
                : pagina.content();
        List<SolicitacaoResumo> resumos = resumir(linhas, agora);
        return new PageResult<>(resumos, pagina.page(), pagina.size(), pagina.totalElements(), pagina.totalPages());
    }

    @Override
    @Transactional
    public SolicitacaoResumo aceitar(Long id, Long cashSessionId, String username, boolean podePagarNoFinal) {
        SolicitacaoSessao solicitacao = pendente(id);
        EscolhasSessao escolhas = solicitacao.escolhas();
        if (escolhas.pagarNoFinal() && !podePagarNoFinal) {
            throw new SessionPayLaterNotAllowedException(username);
        }
        SalaoMesa mesa = mesa(solicitacao.mesaId());
        Optional<MesaOcupacao> ocupada = ocupacaoAberta(mesa);
        Long comandaId = ocupada.map(MesaOcupacao::comandaId)
                .or(() -> comandaAbertaDaMesa(mesa))
                .orElseGet(() -> comandaUseCase.openComanda(cashSessionId, mesa.nome(), solicitacao.customerId(),
                        username).id());
        // Paga no final nasce em PREPARANDO (PDV-F034); senão espera o pagamento no caixa. A faixa sai
        // da essência (PDV-F050).
        comandaUseCase.addSession(comandaId, new ComandaUseCase.AddSessionCommand(null, null, escolhas.vasoGrande(),
                escolhas.carvao(), escolhas.adicionalIds(), false, null, null, escolhas.pagarNoFinal(),
                solicitacao.essenciaSku(), null, solicitacao.essenciaSku2(), null), username);
        MesaOcupacao ocupacao = ocupada.orElseGet(() -> repository.saveOcupacao(
                MesaOcupacao.nova(mesa.id(), comandaId, solicitacao.customerId(), clock.instant())));
        if (solicitacao.deviceToken() != null) {
            repository.addAcesso(ocupacao.id(), solicitacao.deviceToken());
        }
        SolicitacaoSessao aceita = repository.saveSolicitacao(
                solicitacao.aceita(comandaId, username, clock.instant()));
        return resumir(List.of(aceita), clock.instant()).get(0);
    }

    @Override
    @Transactional
    public SolicitacaoResumo recusar(Long id, String motivo, String username) {
        SolicitacaoSessao recusada = repository.saveSolicitacao(
                pendente(id).recusada(motivo, username, clock.instant()));
        return resumir(List.of(recusada), clock.instant()).get(0);
    }

    // ── Disponibilidade ───────────────────────────────────────────────────────────────────────

    /** Lata aberta no depósito das sessões, e com faixa ativa. Ordem: marca, sabor. */
    private List<EssenciaOferecida> disponiveis() {
        Warehouse deposito = warehouseRepository.findActiveByType(WarehouseType.SALAO)
                .orElseGet(estoqueUseCase::getDefaultWarehouse);
        Set<String> abertas = estoqueUseCase.listOpenPackages(deposito.code()).stream()
                .map(OpenPackage::sku).collect(Collectors.toSet());
        Map<Long, SessionTier> faixas = sessionMenuUseCase.listTiers().stream().filter(SessionTier::ativo)
                .collect(Collectors.toMap(SessionTier::id, Function.identity()));

        List<EssenciaOferecida> lista = new ArrayList<>();
        for (Product p : produtosDeSessao()) {
            SessionTier faixa = p.sessionTierId() == null ? null : faixas.get(p.sessionTierId());
            if (faixa == null) continue;
            for (Map.Entry<String, String> sku : skusVendaveis(p).entrySet()) {
                if (!abertas.contains(sku.getKey())) continue;
                lista.add(new EssenciaOferecida(sku.getKey(), sku.getValue(), p.name(), p.brand(), faixa.id(),
                        faixa.nome(), faixa.preco()));
            }
        }
        lista.sort(Comparator.comparing((EssenciaOferecida e) -> Objects.toString(e.marca(), ""),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(EssenciaOferecida::sabor, String.CASE_INSENSITIVE_ORDER));
        return lista;
    }

    private List<Product> produtosDeSessao() {
        ProductFilter filtro = new ProductFilter(null, null, null, true, null, null, null, true);
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
        Map<String, String> skus = new java.util.LinkedHashMap<>();
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
        if (digitos.length() < 10 || digitos.length() > 11) {
            throw new AutoatendimentoDadosInvalidosException("Celular inválido — use DDD + número.");
        }
        return digitos;
    }

    private Identificado identificado(Customer c) {
        return new Identificado(mascarar(c.nome()), vip(c), faltando(c));
    }

    private boolean vip(Customer c) {
        return crmUseCase.listCustomerTags(c.id()).stream().anyMatch(Tag::isVip);
    }

    /** O que o QR exige e o cadastro ainda não tem. */
    static List<String> faltando(Customer c) {
        List<String> faltam = new ArrayList<>();
        String digitos = CustomerIdentifiers.normalizePhone(c.contato());
        if (digitos == null || digitos.length() < 10 || digitos.length() > 11) faltam.add("celular");
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

    /** "Maria Luiza" → "Ma***". O suficiente para o lead se reconhecer, sem expor o cadastro. */
    static String mascarar(String nome) {
        if (nome == null || nome.isBlank()) return "***";
        String primeiro = nome.trim().split("\\s+")[0];
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
        SolicitacaoStatus status = s.statusEm(clock.instant());
        if (status != SolicitacaoStatus.PENDENTE) {
            throw new SolicitacaoNaoPendenteException(status == SolicitacaoStatus.EXPIRADA
                    ? "O pedido expirou — confirme com o cliente e lance pelo PDV."
                    : "O pedido já foi " + (status == SolicitacaoStatus.ACEITA ? "aceito." : "recusado."));
        }
        return s;
    }

    /** A ocupação da mesa, se a comanda dela ainda está aberta. */
    private Optional<MesaOcupacao> ocupacaoAberta(SalaoMesa mesa) {
        return repository.findUltimaOcupacao(mesa.id()).filter(o -> comandaAberta(o.comandaId()));
    }

    private boolean comandaAberta(Long comandaId) {
        try {
            return comandaUseCase.getComanda(comandaId).status() == ComandaStatus.ABERTA;
        } catch (RuntimeException e) {
            return false;
        }
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
                .filter(id -> comandaUseCase.getComanda(id).status() == ComandaStatus.ABERTA);
    }

    private List<SolicitacaoResumo> resumir(List<SolicitacaoSessao> linhas, Instant agora) {
        if (linhas.isEmpty()) return List.of();
        Map<Long, String> mesas = repository.findAllMesas().stream()
                .collect(Collectors.toMap(SalaoMesa::id, SalaoMesa::nome));
        Map<Long, SessionTier> faixas = sessionMenuUseCase.listTiers().stream()
                .collect(Collectors.toMap(SessionTier::id, Function.identity()));
        Map<Long, SessionAddon> adicionais = sessionMenuUseCase.listAddons().stream()
                .collect(Collectors.toMap(SessionAddon::id, Function.identity()));
        SessionSettings settings = sessionMenuUseCase.getSettings();
        Map<Long, Customer> clientes = new HashMap<>();
        Map<String, String> rotulos = new HashMap<>();
        List<SolicitacaoResumo> resumos = new ArrayList<>();
        for (SolicitacaoSessao s : linhas) {
            Customer c = clientes.computeIfAbsent(s.customerId(), crmUseCase::findCustomerById);
            SessionTier faixa = s.tierId() == null ? null : faixas.get(s.tierId());
            EscolhasSessao x = s.escolhas();
            List<SessionAddon> escolhidos = x.adicionalIds().stream().map(adicionais::get).filter(Objects::nonNull)
                    .toList();
            BigDecimal total = faixa == null ? null : faixa.preco()
                    .add(x.vasoGrande() ? settings.upgradeVasoGrandePreco() : BigDecimal.ZERO)
                    .add(settings.charcoalSurcharge(x.carvao()))
                    .add(escolhidos.stream().map(SessionAddon::preco).reduce(BigDecimal.ZERO, BigDecimal::add));
            resumos.add(new SolicitacaoResumo(s, s.statusEm(agora), mesas.getOrDefault(s.mesaId(), "Mesa"),
                    c.nome(), c.contato(), faixa == null ? null : faixa.nome(), faixa == null ? null : faixa.preco(),
                    rotulo(rotulos, s.essenciaSku()), s.essenciaSku2() == null ? null : rotulo(rotulos, s.essenciaSku2()),
                    escolhidos.stream().map(SessionAddon::nome).toList(), total));
        }
        return resumos;
    }

    /** "Zomo · Menta" — o produto e o sabor. SKU que sumiu do catálogo aparece como SKU. */
    private String rotulo(Map<String, String> cache, String sku) {
        return cache.computeIfAbsent(sku, s -> {
            try {
                Product p = estoqueUseCase.findProductBySku(s);
                String sabor = skusVendaveis(p).getOrDefault(s, p.name());
                return sabor.equals(p.name()) ? p.name() : p.name() + " · " + sabor;
            } catch (RuntimeException e) {
                return s;
            }
        });
    }

    private void avisarAtendentes(SalaoMesa mesa, Customer cliente, EssenciaOferecida sabor,
            EssenciaOferecida sabor2) {
        String titulo = "Sessão pedida na " + mesa.nome();
        String corpo = cliente.nome() + " pediu " + sabor.sabor()
                + (sabor2 == null ? "" : " + " + sabor2.sabor()) + " (" + sabor.marca() + ") pelo QR.";
        // Depois do commit: a tela do PDV relê a fila ao receber o aviso, e o pedido tem de estar lá.
        // Falha aqui não desfaz o pedido — o aviso é conveniência, e o PDV relê a fila sozinho.
        afterCommit.accumulate("salao.solicitacao.aviso", titulo, avisos -> {
            try {
                for (String username : userRepository.findUsernamesByPermission(PERMISSAO_ATENDENTE)) {
                    Notification salva = notificationUseCase.notify(username, NotificationType.SOLICITACAO_SESSAO,
                            titulo, corpo, DESTINO_AVISO);
                    ssePort.send(username, salva);
                }
            } catch (RuntimeException e) {
                // ver acima
            }
        });
    }

    private static String novoToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
