package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.SessionPayLaterNotAllowedException;
import com.cernecommerce.core.domain.exception.salao.AutoatendimentoDadosInvalidosException;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.exception.salao.ChamadoJaAbertoException;
import com.cernecommerce.core.domain.exception.salao.ClienteJaCadastradoException;
import com.cernecommerce.core.domain.exception.salao.CodigoMesaInvalidoException;
import com.cernecommerce.core.domain.exception.salao.ClienteNaoIdentificadoException;
import com.cernecommerce.core.domain.exception.salao.ConfirmacaoBloqueadaException;
import com.cernecommerce.core.domain.exception.salao.ConfirmacaoInvalidaException;
import com.cernecommerce.core.domain.exception.salao.EssenciaIndisponivelException;
import com.cernecommerce.core.domain.exception.salao.MesaOcupadaException;
import com.cernecommerce.core.domain.exception.salao.SalaoMesaNotFoundException;
import com.cernecommerce.core.domain.exception.salao.SolicitacaoJaPendenteException;
import com.cernecommerce.core.domain.exception.salao.SolicitacaoNaoPendenteException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerMatch;
import com.cernecommerce.core.domain.model.crm.CustomerMatchField;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pdv.ProporcaoMistura;
import com.cernecommerce.core.domain.model.pdv.SessionProgress;
import com.cernecommerce.core.domain.model.pdv.SessionSetup;
import com.cernecommerce.core.domain.model.pdv.SessionAddon;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionStatus;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.domain.model.salao.AcessoMesa;
import com.cernecommerce.core.domain.model.salao.CardapioDaMesa;
import com.cernecommerce.core.domain.model.salao.ChamadoMesa;
import com.cernecommerce.core.domain.model.salao.Disponibilidade;
import com.cernecommerce.core.domain.model.salao.EscolhasSessao;
import com.cernecommerce.core.domain.model.salao.EssenciaOferecida;
import com.cernecommerce.core.domain.model.salao.FormaPagamentoChamado;
import com.cernecommerce.core.domain.model.salao.MesaAbertaDoCliente;
import com.cernecommerce.core.domain.model.salao.MesaAtual;
import com.cernecommerce.core.domain.model.salao.MesaOcupacao;
import com.cernecommerce.core.domain.model.salao.OrigemSelecao;
import com.cernecommerce.core.domain.model.salao.PerfilSabor;
import com.cernecommerce.core.domain.model.salao.SalaoMesa;
import com.cernecommerce.core.domain.model.salao.SolicitacaoSessao;
import com.cernecommerce.core.domain.model.salao.SolicitacaoResumo;
import com.cernecommerce.core.domain.model.salao.SolicitacaoStatus;
import com.cernecommerce.core.domain.model.salao.StatusChamado;
import com.cernecommerce.core.domain.model.salao.TipoChamado;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase.Cadastro;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase.ChamadoResumo;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase.Completar;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase.Identificacao;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase.NovoChamado;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase.PerfisDoSku;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase.Pedido;
import com.cernecommerce.core.ports.in.SessionMenuUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.audit.AuditLogRepository;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import com.cernecommerce.core.ports.out.event.AuditEventPublisherPort;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import com.cernecommerce.core.ports.out.salao.SalaoAutoatendimentoRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalaoAutoatendimentoServiceTest {

    /** Sábado, 17/10/2026 21:00 em São Paulo. */
    private static final Instant AGORA = Instant.parse("2026-10-18T00:00:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 17);

    @Mock SalaoAutoatendimentoRepository repository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock WarehouseRepository warehouseRepository;
    @Mock SessionMenuUseCase sessionMenuUseCase;
    @Mock CrmUseCase crmUseCase;
    @Mock ComandaUseCase comandaUseCase;
    @Mock NotificationUseCase notificationUseCase;
    @Mock UserRepository userRepository;
    @Mock NotificationSsePort ssePort;
    @Mock AfterCommitExecutor afterCommit;
    @Mock AuditEventPublisherPort auditEvents;
    @Mock AuditLogRepository auditLog;

    SalaoAutoatendimentoService service;

    private static final SalaoMesa MESA = new SalaoMesa(7L, "Mesa 4", "tok-mesa-4", true, AGORA);
    private static final Warehouse SALAO = new Warehouse(10L, "SALAO", "Salão", WarehouseType.SALAO, true);
    private static final SessionTier TRADICIONAL = new SessionTier(1L, "Tradicional", new BigDecimal("25.00"), "Zomo", 1, true);
    private static final SessionTier PREMIUM = new SessionTier(2L, "Premium", new BigDecimal("30.00"), "Nay", 2, true);
    private static final Customer MARIA = new Customer(50L, "Maria Luiza", "(21) 99999-0000", null, "12345678901",
            "PDV", AGORA, CustomerStage.NOVO_LEAD, LocalDate.of(1995, 3, 10));
    private static final SessionAddon GELO = new SessionAddon(5L, "Mangueira de gelo", new BigDecimal("8.00"), 1, true);
    private static final String APARELHO = "aparelho-a";
    private static final EscolhasSessao NO_CAIXA = new EscolhasSessao(false, null, List.of(), false);

    @BeforeEach
    void setUp() {
        service = new SalaoAutoatendimentoService(repository, estoqueUseCase, warehouseRepository,
                sessionMenuUseCase, crmUseCase, comandaUseCase, notificationUseCase,
                userRepository, ssePort, afterCommit, auditEvents, auditLog, Clock.fixed(AGORA, ZoneOffset.UTC));

        when(repository.findMesaByToken("tok-mesa-4")).thenReturn(Optional.of(MESA));
        when(repository.findMesaById(7L)).thenReturn(Optional.of(MESA));
        when(repository.findAllMesas()).thenReturn(List.of(MESA));
        when(warehouseRepository.findActiveByType(WarehouseType.SALAO)).thenReturn(Optional.of(SALAO));
        when(sessionMenuUseCase.listTiers()).thenReturn(List.of(PREMIUM, TRADICIONAL));

        Product zomo = Product.create("ZOMO", "Zomo", "Essências", List.of(
                        ProductVariant.create("ZOMO-MENTA", List.of(new ProductAttribute("Sabor", "Menta"))),
                        ProductVariant.create("ZOMO-UVA", List.of(new ProductAttribute("Sabor", "Uva"))),
                        ProductVariant.create("ZOMO-LIMAO", List.of(new ProductAttribute("Sabor", "Limão")))))
                .withSessionProduct(true).withSessionTierId(1L).withBrand(null, "Zomo");
        Product nay = Product.create("NAY", "Nay", "Essências", List.of(
                        ProductVariant.create("NAY-PESSEGO", List.of(new ProductAttribute("Sabor", "Pêssego")))))
                .withSessionProduct(true).withSessionTierId(2L).withBrand(null, "Nay");
        Product semFaixa = Product.create("SEMFAIXA", "Sem faixa", "Essências", List.of())
                .withSessionProduct(true);
        when(estoqueUseCase.listProducts(eq(0), anyInt(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(zomo, nay, semFaixa), 0, 100, 3, 1));
        when(estoqueUseCase.findProductBySku(anyString())).thenAnswer(inv -> {
            String sku = inv.getArgument(0);
            return sku.startsWith("NAY") ? nay : zomo;
        });

        // Lata aberta: Menta, Pêssego e "sem faixa". Uva e Limão: só lata fechada ou nada.
        when(estoqueUseCase.listOpenPackages("SALAO")).thenReturn(List.of(
                new OpenPackage(1L, "ZOMO-MENTA", 10L, 2, 5, AGORA, "ana", null, null),
                new OpenPackage(2L, "NAY-PESSEGO", 10L, 0, 5, AGORA, "ana", null, null),
                new OpenPackage(3L, "SEMFAIXA", 10L, 0, 5, AGORA, "ana", null, null)));
        when(sessionMenuUseCase.getSettings()).thenReturn(new SessionSettings("VASO_P", "VASO_G",
                new BigDecimal("10.00"), Set.of(), new BigDecimal("2.00")));
        when(sessionMenuUseCase.listAddons()).thenReturn(List.of(GELO,
                new SessionAddon(6L, "Inativo", BigDecimal.ONE, 2, false)));
        when(repository.findUltimaOcupacao(7L)).thenReturn(Optional.empty());
        when(repository.saveOcupacao(any())).thenAnswer(inv -> {
            MesaOcupacao o = inv.getArgument(0);
            return new MesaOcupacao(70L, o.mesaId(), o.comandaId(), o.solicitacaoId(), o.customerId(), o.codigo(),
                    o.abertaEm());
        });
        // Lata fechada: nenhuma, salvo nos testes de disponibilidade.
        when(estoqueUseCase.listSkusWithFreeStock(anyString(), any())).thenReturn(Set.of());
        lotesPelosStubsPorItem();

        when(repository.findEssenciasDoDia(any())).thenReturn(Set.of());
        when(repository.saveSolicitacao(any())).thenAnswer(inv -> {
            SolicitacaoSessao s = inv.getArgument(0);
            return s.id() != null ? s : new SolicitacaoSessao(99L, s.codigo(), s.mesaId(), s.customerId(), s.tierId(),
                    s.essenciaSku(), s.essenciaSku2(), s.observacao(), s.status(), s.motivo(), s.comandaId(),
                    s.criadoEm(), s.decididoEm(), s.decididoPor(), s.escolhas(), s.deviceToken(), s.acompanhante());
        });
        when(crmUseCase.findCustomerById(50L)).thenReturn(MARIA);
        when(repository.saveChamado(any())).thenAnswer(inv -> {
            ChamadoMesa c = inv.getArgument(0);
            return c.id() != null ? c : new ChamadoMesa(300L, c.comandaId(), c.mesaId(), c.ocupacaoId(), c.tipo(),
                    c.formaPagamento(), c.status(), c.criadoEm(), c.atendidoEm(), c.atendidoPor());
        });
    }

    /**
     * As leituras em lote (PDV-C056..C062) respondem pelos stubs por item que cada teste já faz — a
     * comanda pelo getComanda, a solicitação pelo findSolicitacaoById, e assim por diante. Os testes de
     * N+1 sobrescrevem estes stubs e conferem que o caminho por item não é chamado — sempre com
     * {@code doReturn}: o {@code when(...)} executaria a ponte, que chama outro mock, e o
     * {@code thenReturn} cairia nele.
     */
    private void lotesPelosStubsPorItem() {
        when(comandaUseCase.findOpenComandaIds(any())).thenAnswer(inv -> longs(inv)
                .stream().filter(id -> {
                    Comanda c = comandaUseCase.getComanda(id);
                    return c != null && c.status() == ComandaStatus.ABERTA;
                }).collect(Collectors.toSet()));
        when(comandaUseCase.listComandasByIds(any())).thenAnswer(inv -> longs(inv).stream()
                .map(comandaUseCase::getComanda).filter(Objects::nonNull).toList());
        when(comandaUseCase.listStorePurchasesByComandas(any())).thenAnswer(inv -> {
            Map<Long, List<Order>> porComanda = new HashMap<>();
            for (Long id : longs(inv)) {
                List<Order> vendas = comandaUseCase.listStorePurchases(id);
                if (vendas != null && !vendas.isEmpty()) porComanda.put(id, vendas);
            }
            return porComanda;
        });
        when(repository.findSolicitacoesByIds(any())).thenAnswer(inv -> longs(inv)
                .stream().map(repository::findSolicitacaoById).filter(Objects::nonNull).flatMap(Optional::stream)
                .toList());
        when(repository.findAcesso(anyLong(), any())).thenAnswer(inv ->
                repository.hasAcesso(inv.getArgument(0), inv.getArgument(1)) ? Optional.of(false) : Optional.empty());
        when(repository.findMesasByIds(any())).thenAnswer(inv -> repository.findAllMesas().stream()
                .filter(m -> longs(inv).contains(m.id())).toList());
        when(repository.findUltimaOcupacaoDeCadaMesa()).thenAnswer(inv -> repository.findAllMesas().stream()
                .map(m -> repository.findUltimaOcupacao(m.id())).filter(Objects::nonNull).flatMap(Optional::stream)
                .toList());
        when(repository.findChamadosDasComandas(any())).thenAnswer(inv -> {
            Map<Long, List<ChamadoMesa>> porComanda = new HashMap<>();
            for (Long id : longs(inv)) {
                List<ChamadoMesa> chamados = repository.findChamadosDaComanda(id);
                if (chamados != null && !chamados.isEmpty()) porComanda.put(id, chamados);
            }
            return porComanda;
        });
        when(crmUseCase.findCustomersByIds(any())).thenAnswer(inv -> longs(inv)
                .stream().map(crmUseCase::findCustomerById).filter(Objects::nonNull)
                .collect(Collectors.toMap(Customer::id, c -> c)));
        when(crmUseCase.findCustomerNames(any())).thenAnswer(inv -> longs(inv)
                .stream().map(crmUseCase::findCustomerById).filter(Objects::nonNull)
                .collect(Collectors.toMap(Customer::id, Customer::nome)));
        when(estoqueUseCase.findProductsByAnySku(any())).thenAnswer(inv -> strings(inv)
                .stream().map(estoqueUseCase::findProductBySku).filter(Objects::nonNull).distinct().toList());
    }

    /** O argumento do lote; nulo quando o próprio teste sobrescreve o stub com {@code any()}. */
    private static List<Long> longs(InvocationOnMock inv) {
        Collection<Long> ids = inv.getArgument(0);
        return ids == null ? List.of() : List.copyOf(ids);
    }

    private static List<String> strings(InvocationOnMock inv) {
        Collection<String> skus = inv.getArgument(0);
        return skus == null ? List.of() : List.copyOf(skus);
    }

    // ── O que o QR oferece ────────────────────────────────────────────────────────────────────

    @Test
    void cardapio_ofereceAsLatasAbertasComFaixaAtivaEOsExtras() {
        CardapioDaMesa cardapio = service.cardapioPorToken("tok-mesa-4", APARELHO);

        assertThat(cardapio.mesa()).isEqualTo("Mesa 4");
        assertThat(cardapio.faixas()).extracting(SessionTier::nome).containsExactly("Tradicional", "Premium");
        // Só lata aberta e com faixa: Uva e Limão (sem lata) e "sem faixa" ficam de fora.
        assertThat(cardapio.essencias()).extracting(EssenciaOferecida::sku).containsExactly("NAY-PESSEGO", "ZOMO-MENTA");
        assertThat(cardapio.essencias()).extracting(EssenciaOferecida::sabor).containsExactly("Pêssego", "Menta");
        assertThat(cardapio.essencias().get(0).preco()).isEqualByComparingTo("30.00");
        assertThat(cardapio.upgradeVasoGrande()).isEqualByComparingTo("10.00");
        assertThat(cardapio.upgradeCarvaoJumbo()).isEqualByComparingTo("2.00");
        assertThat(cardapio.adicionais()).containsExactly(GELO);
        assertThat(cardapio.acesso()).isEqualTo(AcessoMesa.LIVRE);
    }

    @Test
    void cardapio_destaquesDoDia_marcamSemFiltrar() {
        when(repository.findEssenciasDoDia(HOJE)).thenReturn(Set.of("ZOMO-MENTA", "ZOMO-LIMAO"));

        CardapioDaMesa cardapio = service.cardapioPorToken("tok-mesa-4", APARELHO);

        assertThat(cardapio.essencias()).extracting(EssenciaOferecida::sku).containsExactly("NAY-PESSEGO", "ZOMO-MENTA");
        assertThat(cardapio.destaques()).containsExactly("ZOMO-MENTA");
    }

    @Test
    void semSalao_usaODepositoPadrao() {
        Warehouse loja = new Warehouse(1L, "LOJA", "Loja", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findActiveByType(WarehouseType.SALAO)).thenReturn(Optional.empty());
        when(estoqueUseCase.getDefaultWarehouse()).thenReturn(loja);
        when(estoqueUseCase.listOpenPackages("LOJA")).thenReturn(List.of(
                new OpenPackage(9L, "ZOMO-UVA", 1L, 0, 5, AGORA, "ana", null, null)));

        assertThat(service.cardapioPorToken("tok-mesa-4", APARELHO).essencias())
                .extracting(EssenciaOferecida::sku).containsExactly("ZOMO-UVA");
    }

    @Test
    void cardapio_mesaOcupada_dizSeOAparelhoTemAcesso() {
        ocupada();
        when(repository.hasAcesso(70L, APARELHO)).thenReturn(true);

        assertThat(service.cardapioPorToken("tok-mesa-4", APARELHO).acesso()).isEqualTo(AcessoMesa.LIBERADO);
        assertThat(service.cardapioPorToken("tok-mesa-4", "outro").acesso()).isEqualTo(AcessoMesa.BLOQUEADO);
    }

    @Test
    void cardapio_comandaDaOcupacaoEncerrada_mesaLivre() {
        ocupada();
        when(comandaUseCase.getComanda(500L)).thenReturn(fechada(500L));

        assertThat(service.cardapioPorToken("tok-mesa-4", "outro").acesso()).isEqualTo(AcessoMesa.LIVRE);
    }

    @Test
    void tokenInvalidoOuMesaInativa_e404() {
        when(repository.findMesaByToken("velho")).thenReturn(Optional.empty());
        when(repository.findMesaByToken("inativa")).thenReturn(Optional.of(MESA.withAtivo(false)));

        assertThatThrownBy(() -> service.cardapioPorToken("velho", null)).isInstanceOf(SalaoMesaNotFoundException.class);
        assertThatThrownBy(() -> service.cardapioPorToken("inativa", null)).isInstanceOf(SalaoMesaNotFoundException.class);
    }

    // ── Identificação e cadastro ──────────────────────────────────────────────────────────────

    @Test
    void identificar_devolveOPrimeiroNomeSeEVipEOQueFalta() {
        identificaMaria();
        when(crmUseCase.listCustomerTags(50L)).thenReturn(List.of(new Tag(1L, "VIP")));

        var achado = service.identificarPorToken("tok-mesa-4", new Identificacao("123.456.789-01", null)).orElseThrow();

        assertThat(achado.nome()).isEqualTo("Maria");
        assertThat(achado.vip()).isTrue();
        assertThat(achado.faltando()).isEmpty();
    }

    @Test
    void identificar_cadastroIncompleto_listaOQueFalta() {
        Customer semCpf = new Customer(51L, "João", "(21) 98888-7777", null, null, "PDV", AGORA,
                CustomerStage.NOVO_LEAD, null);
        when(crmUseCase.lookupCustomers(eq("21988887777"), isNull(), isNull()))
                .thenReturn(List.of(new CustomerMatch(semCpf, Set.of(CustomerMatchField.PHONE))));

        assertThat(service.identificarPorToken("tok-mesa-4", new Identificacao(null, "21988887777"))
                .orElseThrow().faltando()).containsExactly("cpf", "dataNascimento");
    }

    @Test
    void identificar_ninguem_eVazio() {
        when(crmUseCase.lookupCustomers(eq("21988887777"), isNull(), isNull())).thenReturn(List.of());

        assertThat(service.identificarPorToken("tok-mesa-4", new Identificacao(null, "(21) 98888-7777"))).isEmpty();
    }

    @Test
    void cadastrar_comCpfOuCelularJaUsado_recusaSemTocarNoCliente() {
        when(crmUseCase.lookupCustomers(eq("21999990000"), isNull(), any()))
                .thenReturn(List.of(new CustomerMatch(MARIA, Set.of(CustomerMatchField.PHONE))));

        assertThatThrownBy(() -> service.cadastrarPorToken("tok-mesa-4",
                new Cadastro("Outra Pessoa", "(21) 99999-0000", "98765432100", LocalDate.of(1990, 1, 1))))
                .isInstanceOf(ClienteJaCadastradoException.class);
        verify(crmUseCase, never()).createCustomer(any(), any(), any(), any(), any(), any());
    }

    @Test
    void cadastrar_criaLeadComOrigemDoQr() {
        when(crmUseCase.lookupCustomers(any(), any(), any())).thenReturn(List.of());
        when(crmUseCase.createCustomer(any(), any(), any(), any(), any(), any())).thenReturn(MARIA);

        LocalDate nascimento = LocalDate.of(1995, 3, 10);
        assertThat(service.cadastrarPorToken("tok-mesa-4",
                new Cadastro("Maria Luiza", "(21) 99999-0000", "123.456.789-01", nascimento)).nome()).isEqualTo("Maria");
        verify(crmUseCase).createCustomer("Maria Luiza", "(21) 99999-0000", null, "12345678901", "QR Salão", nascimento);
    }

    @Test
    void cadastrar_semCpfOuNascimento_eRecusado() {
        when(crmUseCase.lookupCustomers(any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.cadastrarPorToken("tok-mesa-4",
                new Cadastro("Maria Luiza", "(21) 99999-0000", null, LocalDate.of(1995, 3, 10))))
                .isInstanceOf(AutoatendimentoDadosInvalidosException.class);
        assertThatThrownBy(() -> service.cadastrarPorToken("tok-mesa-4",
                new Cadastro("Maria Luiza", "(21) 99999-0000", "12345678901", null)))
                .isInstanceOf(AutoatendimentoDadosInvalidosException.class);
        verify(crmUseCase, never()).createCustomer(any(), any(), any(), any(), any(), any());
    }

    @Test
    void completar_preencheSoOQueFaltaEMantemOGravado() {
        Customer semCpf = new Customer(51L, "João", "(21) 98888-7777", null, null, "PDV", AGORA,
                CustomerStage.NOVO_LEAD, null);
        when(crmUseCase.lookupCustomers(eq("21988887777"), isNull(), isNull()))
                .thenReturn(List.of(new CustomerMatch(semCpf, Set.of(CustomerMatchField.PHONE))));
        when(crmUseCase.lookupCustomers(isNull(), isNull(), eq("98765432100"))).thenReturn(List.of());
        when(crmUseCase.updateCustomer(any(), any(), any(), any(), any(), any(), any())).thenReturn(MARIA);
        LocalDate nascimento = LocalDate.of(1990, 1, 1);

        service.completarPorToken("tok-mesa-4", new Completar(new Identificacao(null, "21988887777"),
                "(21) 90000-0000", "987.654.321-00", nascimento));

        // O celular já gravado não é trocado pelo informado.
        verify(crmUseCase).updateCustomer(51L, "João", "(21) 98888-7777", null, "98765432100", "PDV", nascimento);
    }

    @Test
    void completar_comCpfDeOutroCliente_eRecusado() {
        Customer semCpf = new Customer(51L, "João", "(21) 98888-7777", null, null, "PDV", AGORA,
                CustomerStage.NOVO_LEAD, LocalDate.of(1990, 1, 1));
        when(crmUseCase.lookupCustomers(eq("21988887777"), isNull(), isNull()))
                .thenReturn(List.of(new CustomerMatch(semCpf, Set.of(CustomerMatchField.PHONE))));
        identificaMaria();

        assertThatThrownBy(() -> service.completarPorToken("tok-mesa-4", new Completar(
                new Identificacao(null, "21988887777"), null, "12345678901", null)))
                .isInstanceOf(ClienteJaCadastradoException.class);
        verify(crmUseCase, never()).updateCustomer(any(), any(), any(), any(), any(), any(), any());
    }

    // ── Pedido ────────────────────────────────────────────────────────────────────────────────

    @Test
    void solicitar_gravaPendenteComAFaixaMaisCaraDaMesclaEAvisaDepoisDoCommit() {
        identificaMaria();

        SolicitacaoSessao s = service.solicitarPorToken("tok-mesa-4",
                new Pedido(new Identificacao("12345678901", null), "ZOMO-MENTA", "NAY-PESSEGO", " sem gelo ",
                        new EscolhasSessao(true, Charcoal.JUMBO, List.of(5L), false), APARELHO));

        assertThat(s.status()).isEqualTo(SolicitacaoStatus.PENDENTE);
        assertThat(s.customerId()).isEqualTo(50L);
        assertThat(s.tierId()).isEqualTo(2L);
        assertThat(s.observacao()).isEqualTo("sem gelo");
        assertThat(s.escolhas()).isEqualTo(new EscolhasSessao(true, Charcoal.JUMBO, List.of(5L), false));
        assertThat(s.deviceToken()).isEqualTo(APARELHO);
        verify(afterCommit).accumulate(anyString(), any(), any());
        // Mesa livre: ocupada já no envio, pelo aparelho que pediu, ainda sem comanda.
        ArgumentCaptor<MesaOcupacao> ocupacao = ArgumentCaptor.forClass(MesaOcupacao.class);
        verify(repository).findMesaByIdForUpdate(7L);
        verify(repository).saveOcupacao(ocupacao.capture());
        assertThat(ocupacao.getValue().comandaId()).isNull();
        assertThat(ocupacao.getValue().solicitacaoId()).isEqualTo(99L);
        assertThat(ocupacao.getValue().customerId()).isEqualTo(50L);
        verify(repository).addAcesso(70L, APARELHO, false);
    }

    @Test
    void solicitar_pagarNoFinal_valeParaQuemNaoEVip_oAtendenteDecideNoAceite() {
        identificaMaria();
        when(crmUseCase.listCustomerTags(50L)).thenReturn(List.of());

        SolicitacaoSessao s = service.solicitarPorToken("tok-mesa-4", new Pedido(new Identificacao("12345678901", null),
                "ZOMO-MENTA", null, null, new EscolhasSessao(false, null, List.of(), true), APARELHO));

        assertThat(s.escolhas().pagarNoFinal()).isTrue();
    }

    // ── Pedido sem CPF de quem já está na mesa ────────────────────────────────────────────────

    @Test
    void solicitar_semCpf_doAparelhoDoTitular_pedeNoTitular() {
        ocupada();
        when(repository.hasAcesso(70L, APARELHO)).thenReturn(true);

        SolicitacaoSessao s = service.solicitarPorToken("tok-mesa-4",
                new Pedido(null, "ZOMO-MENTA", null, null, NO_CAIXA, APARELHO));

        assertThat(s.customerId()).isEqualTo(50L);
        assertThat(s.acompanhante()).isFalse();
        verify(crmUseCase, never()).lookupCustomers(any(), any(), any());
    }

    @Test
    void solicitar_semCpf_doAcompanhante_pedeNoTitularMarcadoEContaOPendentePeloAparelho() {
        ocupada();
        when(repository.hasAcesso(70L, "amigo")).thenReturn(true);
        doReturn(Optional.of(true)).when(repository).findAcesso(70L, "amigo");
        // O titular já tem um pedido pendente: não segura o do acompanhante.
        when(repository.existsPendente(eq(7L), eq(50L), any())).thenReturn(true);

        SolicitacaoSessao s = service.solicitarPorToken("tok-mesa-4",
                new Pedido(new Identificacao(" ", ""), "ZOMO-MENTA", null, null, NO_CAIXA, "amigo"));

        assertThat(s.customerId()).isEqualTo(50L);
        assertThat(s.acompanhante()).isTrue();
    }

    @Test
    void solicitar_semCpf_acompanhanteJaComPendente_eRecusado() {
        ocupada();
        when(repository.hasAcesso(70L, "amigo")).thenReturn(true);
        doReturn(Optional.of(true)).when(repository).findAcesso(70L, "amigo");
        when(repository.existsPendenteDoAparelho(eq(7L), eq("amigo"), any())).thenReturn(true);

        assertThatThrownBy(() -> service.solicitarPorToken("tok-mesa-4",
                new Pedido(null, "ZOMO-MENTA", null, null, NO_CAIXA, "amigo")))
                .isInstanceOf(SolicitacaoJaPendenteException.class);
    }

    @Test
    void solicitar_semCpfESemAcessoAMesa_naoIdentificado() {
        assertThatThrownBy(() -> service.solicitarPorToken("tok-mesa-4",
                new Pedido(null, "ZOMO-MENTA", null, null, NO_CAIXA, APARELHO)))
                .isInstanceOf(ClienteNaoIdentificadoException.class);
        assertThatThrownBy(() -> service.solicitarPorToken("tok-mesa-4",
                new Pedido(new Identificacao("", " "), "ZOMO-MENTA", null, null, NO_CAIXA, APARELHO)))
                .isInstanceOf(ClienteNaoIdentificadoException.class);
        verify(repository, never()).saveSolicitacao(any());
    }

    @Test
    void aceitar_doAcompanhante_oPdvVeAcompanhanteDoTitularEOAparelhoSegueAcompanhante() {
        ocupada();
        SolicitacaoSessao doAmigo = new SolicitacaoSessao(99L, UUID.randomUUID(), 7L, 50L, 1L, "ZOMO-MENTA", null,
                null, SolicitacaoStatus.PENDENTE, null, null, AGORA.minusSeconds(60), null, null, NO_CAIXA, "amigo",
                true);
        when(repository.findSolicitacaoByIdForUpdate(99L)).thenReturn(Optional.of(doAmigo));

        var resumo = service.aceitar(99L, 3L, "ana", true);

        assertThat(resumo.cliente()).isEqualTo("Acompanhante de Maria Luiza");
        verify(repository).addAcesso(70L, "amigo", true);
    }

    @Test
    void solicitar_adicionalInativo_eRecusado() {
        identificaMaria();

        assertThatThrownBy(() -> service.solicitarPorToken("tok-mesa-4", new Pedido(new Identificacao("12345678901", null),
                "ZOMO-MENTA", null, null, new EscolhasSessao(false, null, List.of(6L), false), APARELHO)))
                .isInstanceOf(AutoatendimentoDadosInvalidosException.class);
    }

    @Test
    void solicitar_mesaDeOutroAparelho_pedeOCodigo() {
        identificaMaria();
        ocupada();
        when(repository.hasAcesso(70L, "outro")).thenReturn(false);

        assertThatThrownBy(() -> service.solicitarPorToken("tok-mesa-4", pedido("ZOMO-MENTA", "outro")))
                .isInstanceOf(MesaOcupadaException.class);
        verify(repository, never()).saveSolicitacao(any());
    }

    @Test
    void solicitar_cadastroIncompleto_eRecusado() {
        Customer semNascimento = new Customer(50L, "Maria Luiza", "(21) 99999-0000", null, "12345678901", "PDV", AGORA,
                CustomerStage.NOVO_LEAD, null);
        when(crmUseCase.lookupCustomers(isNull(), isNull(), eq("12345678901")))
                .thenReturn(List.of(new CustomerMatch(semNascimento, Set.of(CustomerMatchField.CPF))));

        assertThatThrownBy(() -> service.solicitarPorToken("tok-mesa-4", pedido("ZOMO-MENTA", APARELHO)))
                .isInstanceOf(AutoatendimentoDadosInvalidosException.class);
    }

    @Test
    void solicitar_essenciaForaDasOferecidas_eRecusada() {
        identificaMaria();

        assertThatThrownBy(() -> service.solicitarPorToken("tok-mesa-4", pedido("ZOMO-UVA", APARELHO)))
                .isInstanceOf(EssenciaIndisponivelException.class);
    }

    @Test
    void solicitar_jaComPendenteNaMesa_eRecusado() {
        identificaMaria();
        when(repository.existsPendente(eq(7L), eq(50L), any())).thenReturn(true);

        assertThatThrownBy(() -> service.solicitarPorToken("tok-mesa-4", pedido("ZOMO-MENTA", APARELHO)))
                .isInstanceOf(SolicitacaoJaPendenteException.class);
    }

    // ── Aceitar / recusar ─────────────────────────────────────────────────────────────────────

    @Test
    void aceitar_abreAMesaLancaAsEscolhasEOcupaComOAparelho() {
        EscolhasSessao escolhas = new EscolhasSessao(true, Charcoal.JUMBO, List.of(5L), false);
        when(repository.findSolicitacaoByIdForUpdate(99L)).thenReturn(Optional.of(
                pendente(AGORA.minusSeconds(60), escolhas)));
        when(repository.findUltimaAceita(7L)).thenReturn(Optional.empty());
        when(comandaUseCase.openComanda(3L, "Mesa 4", 50L, "ana")).thenReturn(comanda(500L, ComandaStatus.ABERTA));

        var resumo = service.aceitar(99L, 3L, "ana", false);

        ArgumentCaptor<ComandaUseCase.AddSessionCommand> cmd = ArgumentCaptor.forClass(ComandaUseCase.AddSessionCommand.class);
        verify(comandaUseCase).addSession(eq(500L), cmd.capture(), eq("ana"));
        assertThat(cmd.getValue().pagarNoFinal()).isFalse();
        assertThat(cmd.getValue().vasoGrande()).isTrue();
        assertThat(cmd.getValue().carvao()).isEqualTo(Charcoal.JUMBO);
        assertThat(cmd.getValue().adicionalIds()).containsExactly(5L);
        assertThat(cmd.getValue().essenciaSku()).isEqualTo("ZOMO-MENTA");
        assertThat(cmd.getValue().tierId()).isNull();
        ArgumentCaptor<MesaOcupacao> ocupacao = ArgumentCaptor.forClass(MesaOcupacao.class);
        verify(repository).saveOcupacao(ocupacao.capture());
        assertThat(ocupacao.getValue().comandaId()).isEqualTo(500L);
        assertThat(ocupacao.getValue().codigo()).matches("\\d{4}");
        verify(repository).addAcesso(70L, APARELHO, false);
        assertThat(resumo.status()).isEqualTo(SolicitacaoStatus.ACEITA);
        assertThat(resumo.solicitacao().comandaId()).isEqualTo(500L);
        assertThat(resumo.cliente()).isEqualTo("Maria Luiza");
        assertThat(resumo.essencia()).isEqualTo("Zomo · Menta");
        // 25 da faixa + 10 do vaso grande + 2 do jumbo + 8 do adicional.
        assertThat(resumo.total()).isEqualByComparingTo("45.00");
        assertThat(resumo.adicionais()).containsExactly("Mangueira de gelo");
    }

    @Test
    void aceitar_pagoNoFinalSemPermissao_eRecusado() {
        when(repository.findSolicitacaoByIdForUpdate(99L)).thenReturn(Optional.of(
                pendente(AGORA.minusSeconds(60), new EscolhasSessao(false, null, List.of(), true))));

        assertThatThrownBy(() -> service.aceitar(99L, 3L, "ana", false))
                .isInstanceOf(SessionPayLaterNotAllowedException.class);
        verify(comandaUseCase, never()).openComanda(anyLong(), anyString(), any(), anyString());
    }

    @Test
    void aceitar_atendenteNegaOPagarNoFinal_aceitaCobrandoAgoraSemPermissao() {
        pedidoPagarNoFinal();

        var resumo = service.aceitar(99L, 3L, "ana", false, false);

        ArgumentCaptor<ComandaUseCase.AddSessionCommand> cmd = ArgumentCaptor.forClass(ComandaUseCase.AddSessionCommand.class);
        verify(comandaUseCase).addSession(eq(500L), cmd.capture(), eq("ana"));
        assertThat(cmd.getValue().pagarNoFinal()).isFalse();
        assertThat(resumo.status()).isEqualTo(SolicitacaoStatus.ACEITA);
        assertThat(resumo.solicitacao().pagarNoFinalAceito()).isFalse();
        // O que o lead pediu continua registrado.
        assertThat(resumo.solicitacao().escolhas().pagarNoFinal()).isTrue();
    }

    @Test
    void aceitar_atendenteConfirmaOPagarNoFinal_comPermissao() {
        pedidoPagarNoFinal();

        var resumo = service.aceitar(99L, 3L, "ana", true, true);

        ArgumentCaptor<ComandaUseCase.AddSessionCommand> cmd = ArgumentCaptor.forClass(ComandaUseCase.AddSessionCommand.class);
        verify(comandaUseCase).addSession(eq(500L), cmd.capture(), eq("ana"));
        assertThat(cmd.getValue().pagarNoFinal()).isTrue();
        assertThat(resumo.solicitacao().pagarNoFinalAceito()).isTrue();
    }

    @Test
    void aceitar_atendenteConfirmaSemPermissao_eRecusado() {
        pedidoPagarNoFinal();

        assertThatThrownBy(() -> service.aceitar(99L, 3L, "ana", false, true))
                .isInstanceOf(SessionPayLaterNotAllowedException.class);
        verify(comandaUseCase, never()).addSession(anyLong(), any(), anyString());
    }

    @Test
    void aceitar_atendenteNaoImpoePagarNoFinalAQuemNaoPediu() {
        when(repository.findSolicitacaoByIdForUpdate(99L)).thenReturn(Optional.of(pendente(AGORA.minusSeconds(60))));
        when(repository.findUltimaAceita(7L)).thenReturn(Optional.empty());
        when(comandaUseCase.openComanda(3L, "Mesa 4", 50L, "ana")).thenReturn(comanda(500L, ComandaStatus.ABERTA));

        var resumo = service.aceitar(99L, 3L, "ana", true, true);

        ArgumentCaptor<ComandaUseCase.AddSessionCommand> cmd = ArgumentCaptor.forClass(ComandaUseCase.AddSessionCommand.class);
        verify(comandaUseCase).addSession(eq(500L), cmd.capture(), eq("ana"));
        assertThat(cmd.getValue().pagarNoFinal()).isFalse();
        assertThat(resumo.solicitacao().pagarNoFinalAceito()).isFalse();
    }

    /** Pedido com pagar no final, de quem não é VIP, em mesa livre. */
    private void pedidoPagarNoFinal() {
        when(repository.findSolicitacaoByIdForUpdate(99L)).thenReturn(Optional.of(
                pendente(AGORA.minusSeconds(60), new EscolhasSessao(false, null, List.of(), true))));
        when(repository.findUltimaAceita(7L)).thenReturn(Optional.empty());
        when(comandaUseCase.openComanda(3L, "Mesa 4", 50L, "ana")).thenReturn(comanda(500L, ComandaStatus.ABERTA));
    }

    @Test
    void aceitar_mesaOcupada_lancaNaComandaDaOcupacaoELiberaOAparelho() {
        ocupada();
        when(repository.findSolicitacaoByIdForUpdate(99L)).thenReturn(Optional.of(pendente(AGORA.minusSeconds(60))));

        service.aceitar(99L, 3L, "ana", true);

        verify(comandaUseCase, never()).openComanda(anyLong(), anyString(), any(), anyString());
        verify(comandaUseCase).addSession(eq(500L), any(ComandaUseCase.AddSessionCommand.class), eq("ana"));
        verify(repository, never()).saveOcupacao(any());
        verify(repository).addAcesso(70L, APARELHO, false);
    }

    @Test
    void aceitar_comAMesaAindaAberta_lancaNaMesmaComanda() {
        when(repository.findSolicitacaoByIdForUpdate(99L)).thenReturn(Optional.of(pendente(AGORA.minusSeconds(60))));
        when(repository.findUltimaAceita(7L)).thenReturn(Optional.of(
                pendente(AGORA.minusSeconds(3600)).aceita(400L, "ana", AGORA.minusSeconds(3000))));
        when(comandaUseCase.getComanda(400L)).thenReturn(comanda(400L, ComandaStatus.ABERTA));

        service.aceitar(99L, 3L, "ana", true);

        verify(comandaUseCase, never()).openComanda(anyLong(), anyString(), any(), anyString());
        verify(comandaUseCase).addSession(eq(400L), any(ComandaUseCase.AddSessionCommand.class), eq("ana"));
    }

    @Test
    void aceitar_expirada_eRecusado() {
        when(repository.findSolicitacaoByIdForUpdate(99L)).thenReturn(Optional.of(pendente(AGORA.minusSeconds(31 * 60))));

        assertThatThrownBy(() -> service.aceitar(99L, 3L, "ana", true)).isInstanceOf(SolicitacaoNaoPendenteException.class)
                .hasMessageContaining("expirou");
        verify(comandaUseCase, never()).openComanda(anyLong(), anyString(), any(), anyString());
    }

    @Test
    void recusar_gravaOMotivo() {
        when(repository.findSolicitacaoByIdForUpdate(99L)).thenReturn(Optional.of(pendente(AGORA.minusSeconds(60))));

        var resumo = service.recusar(99L, "Sem mesa livre", "ana");

        assertThat(resumo.status()).isEqualTo(SolicitacaoStatus.RECUSADA);
        assertThat(resumo.solicitacao().motivo()).isEqualTo("Sem mesa livre");
    }

    // ── Mesa ocupada: código e acesso ─────────────────────────────────────────────────────────

    @Test
    void acessar_comOCodigoCerto_liberaOAparelho() {
        ocupada();

        service.acessarPorToken("tok-mesa-4", "4821", "outro");

        verify(repository).addAcesso(70L, "outro", true);
    }

    @Test
    void acessar_comCodigoErrado_eRecusado() {
        ocupada();

        assertThatThrownBy(() -> service.acessarPorToken("tok-mesa-4", "1234", "outro"))
                .isInstanceOf(CodigoMesaInvalidoException.class);
        verify(repository, never()).addAcesso(anyLong(), anyString(), anyBoolean());
    }

    @Test
    void codigoDaMesa_soParaQuemTemAcesso() {
        ocupada();
        when(repository.hasAcesso(70L, APARELHO)).thenReturn(true);

        assertThat(service.codigoDaMesa("tok-mesa-4", APARELHO)).contains("4821");
        assertThat(service.codigoDaMesa("tok-mesa-4", "outro")).isEmpty();
    }

    @Test
    void mesaAtual_semAcesso_eRecusada() {
        ocupada();

        assertThatThrownBy(() -> service.mesaAtualPorToken("tok-mesa-4", "outro"))
                .isInstanceOf(MesaOcupadaException.class);
    }

    @Test
    void mesaAtual_mostraOCodigoEOClienteMascarado() {
        ocupada();
        when(repository.hasAcesso(70L, APARELHO)).thenReturn(true);

        var mesa = service.mesaAtualPorToken("tok-mesa-4", APARELHO);

        assertThat(mesa.codigo()).isEqualTo("4821");
        assertThat(mesa.cliente()).isEqualTo("Ma***");
        assertThat(mesa.emAberto()).isEqualByComparingTo("0");
    }

    @Test
    void ocupacoesAbertas_soAsDeComandaAberta() {
        ocupada();

        assertThat(service.ocupacoesAbertas()).extracting(MesaOcupacao::codigo).containsExactly("4821");

        when(comandaUseCase.getComanda(500L)).thenReturn(fechada(500L));
        assertThat(service.ocupacoesAbertas()).isEmpty();
    }

    // ── Mistura com proporção ─────────────────────────────────────────────────────────────────

    @Test
    void solicitar_mesclaComProporcao_gravaAProporcao() {
        identificaMaria();

        SolicitacaoSessao s = service.solicitarPorToken("tok-mesa-4", new Pedido(new Identificacao("12345678901", null),
                "ZOMO-MENTA", "NAY-PESSEGO", null,
                new EscolhasSessao(false, null, List.of(), false, ProporcaoMistura.SETENTA_TRINTA), APARELHO));

        assertThat(s.escolhas().proporcao()).isEqualTo(ProporcaoMistura.SETENTA_TRINTA);
        // O preço não muda com a proporção: a faixa mais cara da mescla.
        assertThat(s.tierId()).isEqualTo(2L);
    }

    @Test
    void solicitar_umSaborSo_ignoraAProporcao() {
        identificaMaria();

        SolicitacaoSessao s = service.solicitarPorToken("tok-mesa-4", new Pedido(new Identificacao("12345678901", null),
                "ZOMO-MENTA", null, null,
                new EscolhasSessao(false, null, List.of(), false, ProporcaoMistura.NOVENTA_DEZ), APARELHO));

        assertThat(s.escolhas().proporcao()).isNull();
    }

    @Test
    void aceitar_mesclaComProporcao_passaOTextoEAProporcaoAoLancamento() {
        EscolhasSessao escolhas = new EscolhasSessao(false, null, List.of(), false, ProporcaoMistura.SETENTA_TRINTA);
        when(repository.findSolicitacaoByIdForUpdate(99L)).thenReturn(Optional.of(new SolicitacaoSessao(99L,
                UUID.randomUUID(), 7L, 50L, 2L, "ZOMO-MENTA", "NAY-PESSEGO", null, SolicitacaoStatus.PENDENTE, null,
                null, AGORA.minusSeconds(60), null, null, escolhas, APARELHO)));
        when(comandaUseCase.openComanda(3L, "Mesa 4", 50L, "ana")).thenReturn(comanda(500L, ComandaStatus.ABERTA));

        service.aceitar(99L, 3L, "ana", false);

        ArgumentCaptor<ComandaUseCase.AddSessionCommand> cmd = ArgumentCaptor.forClass(ComandaUseCase.AddSessionCommand.class);
        verify(comandaUseCase).addSession(eq(500L), cmd.capture(), eq("ana"));
        assertThat(cmd.getValue().essencia()).isEqualTo("Zomo · Menta 70% + Nay · Pêssego 30%");
        assertThat(cmd.getValue().essenciaProporcao()).isEqualTo(ProporcaoMistura.SETENTA_TRINTA);
        assertThat(cmd.getValue().essenciaSku()).isEqualTo("ZOMO-MENTA");
        assertThat(cmd.getValue().essenciaSku2()).isEqualTo("NAY-PESSEGO");
    }

    @Test
    void aceitar_semProporcao_deixaOTextoParaOLancamento() {
        when(repository.findSolicitacaoByIdForUpdate(99L)).thenReturn(Optional.of(pendente(AGORA.minusSeconds(60))));
        when(comandaUseCase.openComanda(3L, "Mesa 4", 50L, "ana")).thenReturn(comanda(500L, ComandaStatus.ABERTA));

        service.aceitar(99L, 3L, "ana", false);

        ArgumentCaptor<ComandaUseCase.AddSessionCommand> cmd = ArgumentCaptor.forClass(ComandaUseCase.AddSessionCommand.class);
        verify(comandaUseCase).addSession(eq(500L), cmd.capture(), eq("ana"));
        assertThat(cmd.getValue().essencia()).isNull();
        assertThat(cmd.getValue().essenciaProporcao()).isNull();
    }

    @Test
    void proporcao_rotulo() {
        assertThat(ProporcaoMistura.MEIO_A_MEIO.rotulo("Uva", "Menta")).isEqualTo("Uva 50% + Menta 50%");
        assertThat(ProporcaoMistura.NOVENTA_DEZ.rotulo("Uva", "Menta")).isEqualTo("Uva 90% + Menta 10%");
    }

    // ── Perfis de sabor ───────────────────────────────────────────────────────────────────────

    @Test
    void cardapio_skuNaoConfigurado_ganhaOsPerfisSugeridosPeloNome() {
        CardapioDaMesa cardapio = service.cardapioPorToken("tok-mesa-4", APARELHO);

        assertThat(cardapio.essencias()).extracting(EssenciaOferecida::perfis)
                .containsExactly(List.of(PerfilSabor.FRUTADO), List.of(PerfilSabor.GELADO));
    }

    @Test
    void cardapio_skuConfigurado_valeOMarcado_mesmoVazio() {
        when(repository.findSkusComPerfilConfigurado(any())).thenReturn(Set.of("ZOMO-MENTA", "NAY-PESSEGO"));
        when(repository.findPerfis(any())).thenReturn(Map.of("NAY-PESSEGO", List.of(PerfilSabor.DOCE, PerfilSabor.FRUTADO)));

        CardapioDaMesa cardapio = service.cardapioPorToken("tok-mesa-4", APARELHO);

        // Na ordem do enum, e a Menta configurada sem perfil fica sem — a sugestão parou.
        assertThat(cardapio.essencias()).extracting(EssenciaOferecida::perfis)
                .containsExactly(List.of(PerfilSabor.FRUTADO, PerfilSabor.DOCE), List.of());
    }

    @Test
    void essenciasDoDia_dizSeOPerfilFoiMarcadoPelaCasaOuSugerido() {
        when(repository.findSkusComPerfilConfigurado(any())).thenReturn(Set.of("ZOMO-MENTA"));

        assertThat(service.essenciasDoDia(HOJE).essencias()).extracting(e -> e.essencia().sku(),
                        e -> e.essencia().perfisConfigurados())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("SEMFAIXA", false),
                        org.assertj.core.groups.Tuple.tuple("NAY-PESSEGO", false),
                        org.assertj.core.groups.Tuple.tuple("ZOMO-MENTA", true));
    }

    @Test
    void sugerirPerfis_semAcentoNemCaixa() {
        assertThat(PerfilSabor.sugerir("Limão com Hortelã")).containsExactly(PerfilSabor.CITRICO, PerfilSabor.GELADO);
        assertThat(PerfilSabor.sugerir("Zomo")).isEmpty();
        assertThat(PerfilSabor.sugerir(null)).isEmpty();
    }

    @Test
    void salvarPerfis_substituiCadaSku_oUltimoRepetidoVale() {
        service.salvarPerfis(List.of(
                new PerfisDoSku(" ZOMO-MENTA ", List.of(PerfilSabor.DOCE)),
                new PerfisDoSku("ZOMO-MENTA", List.of(PerfilSabor.GELADO, PerfilSabor.FRUTADO, PerfilSabor.GELADO)),
                new PerfisDoSku("NAY-PESSEGO", List.of())));

        // Uma gravação para todos os SKUs (PDV-C064); o repetido fica com a última lista.
        verify(repository).replacePerfis(Map.of("ZOMO-MENTA", List.of(PerfilSabor.FRUTADO, PerfilSabor.GELADO),
                "NAY-PESSEGO", List.of()), AGORA);
    }

    @Test
    void salvarPerfis_skuVazio_eRecusadoSemGravar() {
        assertThatThrownBy(() -> service.salvarPerfis(List.of(new PerfisDoSku(" ", List.of()))))
                .isInstanceOf(AutoatendimentoDadosInvalidosException.class);
        verify(repository, never()).replacePerfis(any(), any());
    }

    // ── Chamados da mesa ──────────────────────────────────────────────────────────────────────

    @Test
    void chamar_semAcessoAMesa_e403() {
        ocupada();

        assertThatThrownBy(() -> service.chamarPorToken("tok-mesa-4",
                new NovoChamado(TipoChamado.ATENDENTE, null, "outro")))
                .isInstanceOf(MesaOcupadaException.class);
        verify(repository, never()).saveChamado(any());
    }

    @Test
    void chamar_contaSemFormaDePagamento_e400() {
        ocupada();
        when(repository.hasAcesso(70L, APARELHO)).thenReturn(true);

        assertThatThrownBy(() -> service.chamarPorToken("tok-mesa-4", new NovoChamado(TipoChamado.CONTA, null, APARELHO)))
                .isInstanceOf(AutoatendimentoDadosInvalidosException.class);
        verify(repository, never()).saveChamado(any());
    }

    @Test
    void chamar_jaComUmAbertoDoMesmoTipo_e409() {
        ocupada();
        when(repository.hasAcesso(70L, APARELHO)).thenReturn(true);
        when(repository.existsChamadoAberto(500L, TipoChamado.CARVAO, AGORA.minus(ChamadoMesa.VALIDADE)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.chamarPorToken("tok-mesa-4", new NovoChamado(TipoChamado.CARVAO, null, APARELHO)))
                .isInstanceOf(ChamadoJaAbertoException.class);
        verify(repository, never()).saveChamado(any());
    }

    @Test
    void chamar_gravaNaComandaDaMesaEAvisaDepoisDoCommit() {
        ocupada();
        when(repository.hasAcesso(70L, APARELHO)).thenReturn(true);
        when(userRepository.findUsernamesByPermission("PDV_COMANDA_MANAGE")).thenReturn(Set.of("ana"));
        doAnswer(inv -> {
            java.util.function.Consumer<List<Object>> flush = inv.getArgument(2);
            Object item = inv.getArgument(1);
            flush.accept(List.of(item));
            return null;
        }).when(afterCommit).accumulate(anyString(), any(), any());

        ChamadoMesa c = service.chamarPorToken("tok-mesa-4",
                new NovoChamado(TipoChamado.CONTA, FormaPagamentoChamado.PIX, APARELHO));

        assertThat(c.id()).isEqualTo(300L);
        assertThat(c.comandaId()).isEqualTo(500L);
        assertThat(c.ocupacaoId()).isEqualTo(70L);
        assertThat(c.mesaId()).isEqualTo(7L);
        assertThat(c.status()).isEqualTo(StatusChamado.ABERTO);
        assertThat(c.formaPagamento()).isEqualTo(FormaPagamentoChamado.PIX);
        // A janela do 409 é a validade: o esquecido não bloqueia um chamado novo.
        verify(repository).existsChamadoAberto(500L, TipoChamado.CONTA, AGORA.minus(ChamadoMesa.VALIDADE));
        verify(afterCommit).accumulate(eq("salao.chamado.aviso"), eq("Conta pedida na Mesa 4 · Pix"), any());
        verify(notificationUseCase).notify("ana", NotificationType.CHAMADO_MESA, "Conta pedida na Mesa 4 · Pix",
                "Maria Luiza chamou pelo QR.", "/pdv");
    }

    @Test
    void chamado_semConta_descartaAFormaDePagamento() {
        ChamadoMesa c = ChamadoMesa.novo(500L, null, TipoChamado.RECOLHER, FormaPagamentoChamado.PIX, AGORA);

        assertThat(c.formaPagamento()).isNull();
        assertThat(c.mesaId()).isNull();
    }

    @Test
    void chamar_naComandaDoPdv_comAConfirmacao_gravaSemMesa() {
        identificaMaria();
        Comanda balcao = Comanda.of(600L, 3L, "SALAO", "Balcão", 50L, ComandaStatus.ABERTA, List.of(), null, "ana",
                AGORA, null);
        when(comandaUseCase.listOpenComandasByCustomer(50L)).thenReturn(List.of(balcao));

        ChamadoMesa c = service.chamarPorToken("tok-mesa-4", new NovoChamado(TipoChamado.ATENDENTE, null, APARELHO,
                SalaoAutoatendimentoService.ref(balcao), new Identificacao("12345678901", null), "0000"));

        assertThat(c.comandaId()).isEqualTo(600L);
        assertThat(c.mesaId()).isNull();
        assertThat(c.ocupacaoId()).isNull();
        verify(afterCommit).accumulate(eq("salao.chamado.aviso"), eq("Atendente chamado na Balcão"), any());
    }

    @Test
    void chamar_naComandaDoPdv_confirmacaoErrada_e403EAudita() {
        identificaMaria();
        Comanda balcao = Comanda.of(600L, 3L, "SALAO", "Balcão", 50L, ComandaStatus.ABERTA, List.of(), null, "ana",
                AGORA, null);
        when(comandaUseCase.listOpenComandasByCustomer(50L)).thenReturn(List.of(balcao));

        assertThatThrownBy(() -> service.chamarPorToken("tok-mesa-4", new NovoChamado(TipoChamado.ATENDENTE, null,
                APARELHO, SalaoAutoatendimentoService.ref(balcao), new Identificacao("12345678901", null), "1111")))
                .isInstanceOf(ConfirmacaoInvalidaException.class);
        verify(repository, never()).saveChamado(any());
        verify(auditEvents).publish(argThat(e -> e.type() == AuditEvent.EventType.SALAO_CONFIRMACAO_RECUSADA
                && "CHAMADO".equals(e.details().get("acao"))));
    }

    @Test
    void chamar_refDeComandaQueNaoEDoCliente_e403() {
        identificaMaria();
        when(comandaUseCase.listOpenComandasByCustomer(50L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.chamarPorToken("tok-mesa-4", new NovoChamado(TipoChamado.ATENDENTE, null,
                APARELHO, "qualquer", new Identificacao("12345678901", null), "0000")))
                .isInstanceOf(MesaOcupadaException.class);
        verify(repository, never()).saveChamado(any());
    }

    @Test
    void atender_marcaAtendidoUmaVezSo() {
        ChamadoMesa aberto = new ChamadoMesa(300L, 500L, 7L, 70L, TipoChamado.CARVAO, null, StatusChamado.ABERTO,
                AGORA.minusSeconds(120), null, null);
        when(repository.findChamadoByIdForUpdate(300L)).thenReturn(Optional.of(aberto));
        when(comandaUseCase.getComanda(500L)).thenReturn(comanda(500L, ComandaStatus.ABERTA));

        var atendido = service.atenderChamado(300L, "ana");

        assertThat(atendido.chamado().status()).isEqualTo(StatusChamado.ATENDIDO);
        assertThat(atendido.chamado().atendidoPor()).isEqualTo("ana");
        assertThat(atendido.chamado().atendidoEm()).isEqualTo(AGORA);
        assertThat(atendido.mesa()).isEqualTo("Mesa 4");
        assertThat(atendido.cliente()).isEqualTo("Maria Luiza");

        // De novo, por outro atendente: volta como está, com quem atendeu primeiro.
        when(repository.findChamadoByIdForUpdate(300L)).thenReturn(Optional.of(atendido.chamado()));
        var deNovo = service.atenderChamado(300L, "bia");

        assertThat(deNovo.chamado().atendidoPor()).isEqualTo("ana");
        verify(repository).saveChamado(any());
    }

    @Test
    void listChamados_abertos_tiraOsExpiradosEOsDeComandaEncerrada() {
        ChamadoMesa valendo = chamado(1L, 500L, StatusChamado.ABERTO, AGORA.minusSeconds(300));
        ChamadoMesa esquecido = chamado(2L, 500L, StatusChamado.ABERTO, AGORA.minus(Duration.ofMinutes(31)));
        ChamadoMesa deMesaFechada = chamado(3L, 501L, StatusChamado.ABERTO, AGORA.minusSeconds(60));
        when(repository.findChamados(StatusChamado.ABERTO, AGORA.minus(ChamadoMesa.VALIDADE)))
                .thenReturn(List.of(valendo, esquecido, deMesaFechada));
        when(comandaUseCase.getComanda(500L)).thenReturn(comanda(500L, ComandaStatus.ABERTA));
        when(comandaUseCase.getComanda(501L)).thenReturn(fechada(501L));

        var fila = service.listChamados(StatusChamado.ABERTO);

        assertThat(fila).extracting(r -> r.chamado().id()).containsExactly(1L);
        assertThat(fila.get(0).mesa()).isEqualTo("Mesa 4");
        assertThat(fila.get(0).cliente()).isEqualTo("Maria Luiza");
    }

    // ── Leituras em lote (PDV-C056..C062) ─────────────────────────────────────────────────────

    @Test
    void ocupacoesAbertas_emLote_semCarregarAComanda() {
        SolicitacaoSessao origem = pendente(AGORA.minusSeconds(60));
        doReturn(List.of(
                new MesaOcupacao(70L, 7L, 500L, 50L, "4821", AGORA.minusSeconds(600)),
                new MesaOcupacao(71L, 8L, 501L, 50L, "1111", AGORA.minusSeconds(900)),
                new MesaOcupacao(72L, 9L, null, origem.id(), 50L, "2222", AGORA.minusSeconds(60)))).when(repository).findUltimaOcupacaoDeCadaMesa();
        doReturn(Set.of(500L)).when(comandaUseCase).findOpenComandaIds(any());
        doReturn(List.of(origem)).when(repository).findSolicitacoesByIds(any());

        assertThat(service.ocupacoesAbertas()).extracting(MesaOcupacao::codigo).containsExactly("4821", "2222");
        verify(comandaUseCase).findOpenComandaIds(List.of(500L, 501L));
        verify(comandaUseCase, never()).getComanda(any());
        verify(repository, never()).findUltimaOcupacao(anyLong());
        verify(repository, never()).findSolicitacaoById(any());
    }

    @Test
    void cardapio_mesaOcupada_naoCarregaAComanda() {
        ocupada();
        doReturn(Set.of(500L)).when(comandaUseCase).findOpenComandaIds(any());
        when(repository.hasAcesso(70L, APARELHO)).thenReturn(true);

        assertThat(service.cardapioPorToken("tok-mesa-4", APARELHO).acesso()).isEqualTo(AcessoMesa.LIBERADO);
        verify(comandaUseCase, never()).getComanda(any());
    }

    @Test
    void mesaAtual_carregaAComandaUmaVezSo() {
        ocupada();
        doReturn(Set.of(500L)).when(comandaUseCase).findOpenComandaIds(any());
        when(repository.hasAcesso(70L, APARELHO)).thenReturn(true);

        service.mesaAtualPorToken("tok-mesa-4", APARELHO);

        verify(comandaUseCase, times(1)).getComanda(500L);
    }

    @Test
    void solicitar_leOAcessoDoAparelhoUmaVez() {
        ocupada();
        doReturn(Optional.of(true)).when(repository).findAcesso(70L, "amigo");

        service.solicitarPorToken("tok-mesa-4", new Pedido(null, "ZOMO-MENTA", null, null, NO_CAIXA, "amigo"));

        verify(repository, times(1)).findAcesso(70L, "amigo");
        verify(repository, never()).hasAcesso(anyLong(), anyString());
    }

    @Test
    void listChamados_comandasEClientesEmLote() {
        ChamadoMesa daMesa = chamado(1L, 500L, StatusChamado.ABERTO, AGORA.minusSeconds(300));
        ChamadoMesa doBalcao = new ChamadoMesa(2L, 700L, null, null, TipoChamado.CONTA,
                FormaPagamentoChamado.PIX, StatusChamado.ABERTO,
                AGORA.minusSeconds(120), null, null);
        when(repository.findChamados(StatusChamado.ABERTO, AGORA.minus(ChamadoMesa.VALIDADE)))
                .thenReturn(List.of(daMesa, doBalcao));
        doReturn(Set.of(500L, 700L)).when(comandaUseCase).findOpenComandaIds(any());
        doReturn(List.of(comanda(500L, ComandaStatus.ABERTA),
                Comanda.of(700L, 3L, "SALAO", "Balcão", 50L, ComandaStatus.ABERTA, List.of(), null, "ana", AGORA, null))).when(comandaUseCase).listComandasByIds(any());
        doReturn(Map.of(50L, "Maria Luiza")).when(crmUseCase).findCustomerNames(any());

        var fila = service.listChamados(StatusChamado.ABERTO);

        assertThat(fila).extracting(ChamadoResumo::mesa).containsExactly("Mesa 4", "Balcão");
        assertThat(fila).extracting(ChamadoResumo::cliente).containsExactly("Maria Luiza", "Maria Luiza");
        verify(comandaUseCase, never()).getComanda(any());
        verify(crmUseCase, never()).findCustomerById(any());
    }

    @Test
    void listSolicitacoes_clientesERotulosEmLote() {
        SolicitacaoSessao mescla = new SolicitacaoSessao(98L, UUID.randomUUID(), 7L, 50L, 1L, "ZOMO-MENTA", "ZOMO-UVA",
                null, SolicitacaoStatus.PENDENTE, null, null, AGORA.minusSeconds(60), null, null, NO_CAIXA, APARELHO);
        when(repository.findSolicitacoes(eq(SolicitacaoStatus.PENDENTE), any(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(pendente(AGORA.minusSeconds(30)), mescla), 0, 20, 2, 1));
        Product zomo = estoqueUseCase.findProductBySku("ZOMO-MENTA");
        doReturn(Map.of(50L, MARIA)).when(crmUseCase).findCustomersByIds(any());
        doReturn(List.of(zomo)).when(estoqueUseCase).findProductsByAnySku(any());

        var pagina = service.listSolicitacoes(SolicitacaoStatus.PENDENTE, 0, 20);

        assertThat(pagina.content()).extracting(SolicitacaoResumo::essencia, SolicitacaoResumo::essencia2)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Zomo · Menta", null),
                        org.assertj.core.groups.Tuple.tuple("Zomo · Menta", "Zomo · Uva"));
        assertThat(pagina.content()).extracting(SolicitacaoResumo::cliente).containsOnly("Maria Luiza");
        verify(crmUseCase, never()).findCustomerById(any());
        verify(estoqueUseCase, times(1)).findProductsByAnySku(Set.of("ZOMO-MENTA", "ZOMO-UVA"));
    }

    @Test
    void listSolicitacoes_expiradas_filtradasNaConsulta_paginaETotalBatem() {
        SolicitacaoSessao vencida = pendente(AGORA.minus(Duration.ofMinutes(45)));
        when(repository.findSolicitacoesAntesDe(SolicitacaoStatus.PENDENTE, AGORA.minus(SolicitacaoSessao.VALIDADE),
                0, 20)).thenReturn(new PageResult<>(List.of(vencida), 0, 20, 1, 1));

        var pagina = service.listSolicitacoes(SolicitacaoStatus.EXPIRADA, 0, 20);

        assertThat(pagina.content()).extracting(SolicitacaoResumo::status).containsExactly(SolicitacaoStatus.EXPIRADA);
        assertThat(pagina.totalElements()).isEqualTo(1);
        verify(repository, never()).findSolicitacoes(any(), any(), anyInt(), anyInt());
    }

    @Test
    void minhasComandas_chamadosEComprasEmLote() {
        identificaMaria();
        comandasDaMaria();
        doReturn(Map.of()).when(repository).findChamadosDasComandas(any());
        doReturn(Map.of()).when(comandaUseCase).listStorePurchasesByComandas(any());

        service.minhasComandasPorToken("tok-mesa-4", new Identificacao("12345678901", null), "0000");

        verify(repository).findChamadosDasComandas(List.of(500L, 600L, 700L));
        verify(comandaUseCase).listStorePurchasesByComandas(List.of(500L, 600L, 700L));
        verify(repository, never()).findChamadosDaComanda(anyLong());
        verify(comandaUseCase, never()).listStorePurchases(anyLong());
    }

    // ── Mesa atual: chamados e "repetir" ──────────────────────────────────────────────────────

    @Test
    void mesaAtual_chamados_osAbertosEOsAtendidosHaPouco() {
        ocupada();
        when(repository.hasAcesso(70L, APARELHO)).thenReturn(true);
        ChamadoMesa aberto = chamado(1L, 500L, StatusChamado.ABERTO, AGORA.minusSeconds(60));
        ChamadoMesa esquecido = chamado(2L, 500L, StatusChamado.ABERTO, AGORA.minus(Duration.ofMinutes(40)));
        ChamadoMesa atendidoAgora = new ChamadoMesa(3L, 500L, 7L, 70L, TipoChamado.RECOLHER, null,
                StatusChamado.ATENDIDO, AGORA.minusSeconds(900), AGORA.minusSeconds(600), "ana");
        ChamadoMesa atendidoHaMuito = new ChamadoMesa(4L, 500L, 7L, 70L, TipoChamado.ATENDENTE, null,
                StatusChamado.ATENDIDO, AGORA.minus(Duration.ofHours(3)), AGORA.minus(Duration.ofHours(2)), "ana");
        when(repository.findChamadosDaComanda(500L)).thenReturn(List.of(aberto, esquecido, atendidoAgora,
                atendidoHaMuito));

        var mesa = service.mesaAtualPorToken("tok-mesa-4", APARELHO);

        assertThat(mesa.chamados()).extracting(ChamadoMesa::id).containsExactly(1L, 3L);
        assertThat(mesa.ultimaSessao()).isNull();
    }

    @Test
    void mesaAtual_ultimaSessao_eALinhaMaisRecenteComSabor_inclusiveDoPdv() {
        ocupada();
        when(repository.hasAcesso(70L, APARELHO)).thenReturn(true);
        ComandaItem doQr = sessao(1L, AGORA.minusSeconds(3000), "ZOMO-MENTA", null, null,
                new SessionSetup(null, List.of(), false));
        ComandaItem doPdv = sessao(2L, AGORA.minusSeconds(1200), "ZOMO-MENTA", "NAY-PESSEGO",
                ProporcaoMistura.NOVENTA_DEZ, new SessionSetup(Charcoal.JUMBO,
                        List.of(new SessionSetup.Addon(5L, "Mangueira de gelo", new BigDecimal("8.00"))), true));
        ComandaItem soTexto = sessao(3L, AGORA.minusSeconds(60), null, null, null, null);
        when(comandaUseCase.getComanda(500L)).thenReturn(Comanda.of(500L, 3L, "SALAO", "Mesa 4", 50L,
                ComandaStatus.ABERTA, List.of(doQr, doPdv, soTexto), null, "ana", AGORA, null));

        MesaAtual.UltimaSessao ultima = service.mesaAtualPorToken("tok-mesa-4", APARELHO).ultimaSessao();

        assertThat(ultima).isEqualTo(new MesaAtual.UltimaSessao("ZOMO-MENTA", "NAY-PESSEGO",
                ProporcaoMistura.NOVENTA_DEZ, true, Charcoal.JUMBO, List.of(5L)));
    }

    @Test
    void mesaAtual_itensDeSessao_trazemALinhaDoTempo_osDemaisNao() {
        ocupada();
        when(repository.hasAcesso(70L, APARELHO)).thenReturn(true);
        ComandaItem mescla = ComandaItem.of(1L, "SESS-1", BigDecimal.ONE, new BigDecimal("25.00"), null,
                "Sessão Tradicional", AGORA.minusSeconds(1800), ConsumptionMode.SESSAO, false, null,
                "Zomo · Uva 70% + Zomo · Menta 30%" + ComandaItem.VASO_GRANDE_NOTE_SUFFIX, null, null, null, null, null,
                null, null, new SessionProgress(SessionStatus.ENTREGUE, AGORA.minusSeconds(1500),
                        AGORA.minusSeconds(1200), null), null, "ZOMO-UVA", "ZOMO-MENTA", false,
                ProporcaoMistura.SETENTA_TRINTA);
        ComandaItem agua = ComandaItem.of(2L, "AGUA", BigDecimal.ONE, new BigDecimal("5.00"), null, "Água",
                AGORA.minusSeconds(600), ConsumptionMode.NORMAL, false, null, null, null, null, null, null, null, null,
                null, null, null, null, null, false, null);
        when(comandaUseCase.getComanda(500L)).thenReturn(Comanda.of(500L, 3L, "SALAO", "Mesa 4", 50L,
                ComandaStatus.ABERTA, List.of(mescla, agua), null, "ana", AGORA, null));

        List<MesaAtual.Item> itens = service.mesaAtualPorToken("tok-mesa-4", APARELHO).itens();

        assertThat(itens.get(0).sessao()).isEqualTo(new MesaAtual.Sessao(SessionStatus.ENTREGUE,
                "Zomo · Uva 70% + Zomo · Menta 30%", false, AGORA.minusSeconds(1800), AGORA.minusSeconds(1500),
                AGORA.minusSeconds(1200), null, 5L, null));
        assertThat(itens.get(1).sessao()).isNull();
    }

    // ── Mesas abertas do cliente ──────────────────────────────────────────────────────────────

    @Test
    void identificar_listaAsComandasAbertas_soEstaMesaRevelaTokenEValor() {
        identificaMaria();
        comandasDaMaria();

        List<MesaAbertaDoCliente> mesas = service.identificarPorToken("tok-mesa-4",
                new Identificacao("12345678901", null)).orElseThrow().mesasAbertas();

        assertThat(mesas).extracting(MesaAbertaDoCliente::mesa).containsExactly("Mesa 4", "Mesa 9", "Balcão");
        MesaAbertaDoCliente esta = mesas.get(0);
        assertThat(esta.estaMesa()).isTrue();
        assertThat(esta.origem()).isEqualTo(MesaAbertaDoCliente.Origem.QR);
        assertThat(esta.token()).isEqualTo("tok-mesa-4");
        assertThat(esta.emAberto()).isEqualByComparingTo("0");
        MesaAbertaDoCliente outra = mesas.get(1);
        assertThat(outra.origem()).isEqualTo(MesaAbertaDoCliente.Origem.QR);
        assertThat(outra.token()).isNull();
        assertThat(outra.emAberto()).isNull();
        MesaAbertaDoCliente balcao = mesas.get(2);
        assertThat(balcao.origem()).isEqualTo(MesaAbertaDoCliente.Origem.PDV);
        assertThat(balcao.token()).isNull();
        assertThat(balcao.emAberto()).isNull();
        assertThat(mesas).allSatisfy(m -> {
            assertThat(m.itens()).isEmpty();
            assertThat(m.chamados()).isEmpty();
        });
        // A referência é opaca: não é o id sequencial da comanda.
        assertThat(mesas).extracting(MesaAbertaDoCliente::ref).doesNotHaveDuplicates()
                .allSatisfy(ref -> assertThat(ref).hasSize(16).isNotIn("500", "600", "700"));
    }

    @Test
    void identificar_semComandaAberta_listaVazia() {
        identificaMaria();

        assertThat(service.identificarPorToken("tok-mesa-4", new Identificacao("12345678901", null))
                .orElseThrow().mesasAbertas()).isEmpty();
    }

    @Test
    void minhasComandas_comAConfirmacao_revelaTudo() {
        identificaMaria();
        comandasDaMaria();

        List<MesaAbertaDoCliente> mesas = service.minhasComandasPorToken("tok-mesa-4",
                new Identificacao("12345678901", null), "0000");

        assertThat(mesas).extracting(MesaAbertaDoCliente::token).containsExactly("tok-mesa-4", "tok-mesa-9", null);
        assertThat(mesas).allSatisfy(m -> assertThat(m.emAberto()).isNotNull());
    }

    @Test
    void minhasComandas_confirmacaoErrada_e403EAudita() {
        identificaMaria();

        assertThatThrownBy(() -> service.minhasComandasPorToken("tok-mesa-4",
                new Identificacao("12345678901", null), "9999"))
                .isInstanceOf(ConfirmacaoInvalidaException.class);
        verify(comandaUseCase, never()).listOpenComandasByCustomer(anyLong());
        verify(auditEvents).publish(argThat(e -> e.type() == AuditEvent.EventType.SALAO_CONFIRMACAO_RECUSADA
                && "MINHAS_COMANDAS".equals(e.details().get("acao"))
                && Long.valueOf(50L).equals(e.details().get("customerId"))
                && !e.details().containsValue("12345678901")));
    }

    @Test
    void confirmacao_cincoErrosEm15Min_travaAteACerta() {
        identificaMaria();
        when(auditLog.countSince(eq("SALAO_CONFIRMACAO_RECUSADA"), eq("customer:50"),
                eq(AGORA.minus(Duration.ofMinutes(15))))).thenReturn(5L);

        assertThatThrownBy(() -> service.minhasComandasPorToken("tok-mesa-4",
                new Identificacao("12345678901", null), "0000"))
                .isInstanceOf(ConfirmacaoBloqueadaException.class);
        verify(comandaUseCase, never()).listOpenComandasByCustomer(anyLong());
        // A tentativa travada vai para a auditoria, mas sem o cliente: não estende a trava.
        verify(auditEvents).publish(argThat(e -> e.type() == AuditEvent.EventType.SALAO_CONFIRMACAO_RECUSADA
                && "CONFIRMACAO_BLOQUEADA".equals(e.details().get("motivo"))
                && !e.details().containsKey("customerId")));
    }

    @Test
    void confirmacao_quatroErros_aindaEntra() {
        ocupada();
        identificaMaria();
        when(auditLog.countSince(anyString(), anyString(), any())).thenReturn(4L);

        service.entrarPorToken("tok-mesa-4", new Identificacao("12345678901", null), "0000", "novo");

        verify(repository).addAcesso(70L, "novo", false);
    }

    // ── Entrar pela identificação ─────────────────────────────────────────────────────────────

    @Test
    void entrar_titularPeloCpf_confirmaComOsUltimosDoCelular() {
        ocupada();
        identificaMaria();

        service.entrarPorToken("tok-mesa-4", new Identificacao("12345678901", null), "0000", "novo");

        verify(repository).addAcesso(70L, "novo", false);
        verify(auditEvents).publish(argThat(e -> e.type() == AuditEvent.EventType.SALAO_MESA_ENTRADA
                && e.details().get("ocupacaoId").equals(70L) && "CPF".equals(e.details().get("identificacao"))));
    }

    @Test
    void entrar_titularPeloCelular_confirmaComOsPrimeirosDoCpf() {
        ocupada();
        when(crmUseCase.lookupCustomers(eq("21999990000"), isNull(), isNull()))
                .thenReturn(List.of(new CustomerMatch(MARIA, Set.of(CustomerMatchField.PHONE))));

        service.entrarPorToken("tok-mesa-4", new Identificacao(null, "(21) 99999-0000"), "123", "novo");

        verify(repository).addAcesso(70L, "novo", false);
    }

    @Test
    void entrar_confirmacaoErrada_e403SemLiberar() {
        ocupada();
        identificaMaria();

        assertThatThrownBy(() -> service.entrarPorToken("tok-mesa-4", new Identificacao("12345678901", null), "1234",
                "novo")).isInstanceOf(ConfirmacaoInvalidaException.class);
        verify(repository, never()).addAcesso(anyLong(), anyString(), anyBoolean());
        verify(auditEvents).publish(argThat(e -> e.type() == AuditEvent.EventType.SALAO_CONFIRMACAO_RECUSADA
                && "ENTRAR".equals(e.details().get("acao"))));
    }

    @Test
    void entrar_clienteQueNaoEOTitular_e403() {
        ocupada();
        Customer joao = new Customer(51L, "João", "(21) 98888-7777", null, "98765432100", "PDV", AGORA,
                CustomerStage.NOVO_LEAD, LocalDate.of(1990, 1, 1));
        when(crmUseCase.lookupCustomers(isNull(), isNull(), eq("98765432100")))
                .thenReturn(List.of(new CustomerMatch(joao, Set.of(CustomerMatchField.CPF))));

        assertThatThrownBy(() -> service.entrarPorToken("tok-mesa-4", new Identificacao("98765432100", null), "7777",
                "novo")).isInstanceOf(MesaOcupadaException.class);
        verify(repository, never()).addAcesso(anyLong(), anyString(), anyBoolean());
    }

    @Test
    void entrar_mesaLivre_e403() {
        identificaMaria();

        assertThatThrownBy(() -> service.entrarPorToken("tok-mesa-4", new Identificacao("12345678901", null), "0000",
                "novo")).isInstanceOf(MesaOcupadaException.class);
        verify(repository, never()).addAcesso(anyLong(), anyString(), anyBoolean());
    }

    @Test
    void primeiroNome() {
        assertThat(SalaoAutoatendimentoService.primeiroNome(" Maria  Luiza ")).isEqualTo("Maria");
        assertThat(SalaoAutoatendimentoService.primeiroNome("A")).isEqualTo("A");
        assertThat(SalaoAutoatendimentoService.primeiroNome(" ")).isEqualTo("Cliente");
        assertThat(SalaoAutoatendimentoService.primeiroNome(null)).isEqualTo("Cliente");
    }

    @Test
    void mascarar() {
        assertThat(SalaoAutoatendimentoService.mascarar("maria luiza")).isEqualTo("ma***");
        assertThat(SalaoAutoatendimentoService.mascarar("A")).isEqualTo("A***");
        assertThat(SalaoAutoatendimentoService.mascarar(" ")).isEqualTo("***");
    }

    /** Mesa 4 ocupada pela comanda 500, aberta, com o código 4821. */
    private void ocupada() {
        when(repository.findUltimaOcupacao(7L)).thenReturn(Optional.of(
                new MesaOcupacao(70L, 7L, 500L, 50L, "4821", AGORA.minusSeconds(600))));
        when(comandaUseCase.getComanda(500L)).thenReturn(comanda(500L, ComandaStatus.ABERTA));
    }

    /** Maria: na Mesa 4 (este QR, comanda 500), na Mesa 9 (comanda 600) e no balcão (comanda 700). */
    private void comandasDaMaria() {
        SalaoMesa mesa9 = new SalaoMesa(8L, "Mesa 9", "tok-mesa-9", true, AGORA);
        when(repository.findAllMesas()).thenReturn(List.of(MESA, mesa9));
        when(comandaUseCase.listOpenComandasByCustomer(50L)).thenReturn(List.of(
                comanda(500L, ComandaStatus.ABERTA),
                Comanda.of(600L, 3L, "SALAO", "Mesa 9", 50L, ComandaStatus.ABERTA, List.of(), null, "ana", AGORA, null),
                Comanda.of(700L, 3L, "SALAO", "Balcão", 50L, ComandaStatus.ABERTA, List.of(), null, "ana", AGORA, null)));
        when(repository.findOcupacoesByComandaIds(any())).thenReturn(List.of(
                new MesaOcupacao(70L, 7L, 500L, 50L, "4821", AGORA.minusSeconds(600)),
                new MesaOcupacao(80L, 8L, 600L, 50L, "1111", AGORA.minusSeconds(900))));
    }

    private static ChamadoMesa chamado(Long id, Long comandaId, StatusChamado status, Instant criadoEm) {
        return new ChamadoMesa(id, comandaId, 7L, 70L, TipoChamado.CARVAO, null, status, criadoEm, null, null);
    }

    private static ComandaItem sessao(Long id, Instant em, String sku, String sku2, ProporcaoMistura proporcao,
            SessionSetup setup) {
        return ComandaItem.of(id, "SESS-1", BigDecimal.ONE, new BigDecimal("25.00"), null, "Sessão Tradicional", em,
                ConsumptionMode.SESSAO, false, null, "Zomo Menta", null, null, null, null, null, null, null,
                SessionProgress.awaitingPayment(), setup, sku, sku2, false, proporcao);
    }

    // ── Disponibilidade: lata fechada e sem faixa ─────────────────────────────────────────────

    @Test
    void cardapio_ofereceTambemASoComLataFechada_eOAdminVeOMotivo() {
        latasFechadas("ZOMO-LIMAO", "ZOMO-UVA");

        CardapioDaMesa cardapio = service.cardapioPorToken("tok-mesa-4", APARELHO);

        assertThat(cardapio.essencias()).extracting(EssenciaOferecida::sku, EssenciaOferecida::disponibilidade)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("NAY-PESSEGO", Disponibilidade.ABERTA),
                        org.assertj.core.groups.Tuple.tuple("ZOMO-LIMAO", Disponibilidade.FECHADA),
                        org.assertj.core.groups.Tuple.tuple("ZOMO-MENTA", Disponibilidade.ABERTA),
                        org.assertj.core.groups.Tuple.tuple("ZOMO-UVA", Disponibilidade.FECHADA));
        // O admin vê também a sem faixa (sem marca, vem antes), com o motivo; a aberta não tem motivo.
        var admin = service.essenciasDoDia(HOJE).essencias().stream().map(e -> e.essencia()).toList();
        assertThat(admin).extracting(EssenciaOferecida::sku)
                .containsExactly("SEMFAIXA", "NAY-PESSEGO", "ZOMO-LIMAO", "ZOMO-MENTA", "ZOMO-UVA");
        EssenciaOferecida semFaixa = admin.get(0);
        assertThat(semFaixa.disponibilidade()).isEqualTo(Disponibilidade.SEM_FAIXA);
        assertThat(semFaixa.tierId()).isNull();
        assertThat(semFaixa.motivoFora()).isNotBlank();
        assertThat(admin.get(2).motivoFora()).isNotBlank();
        assertThat(admin.get(1).motivoFora()).isNull();
    }

    @Test
    void latasFechadas_consultaOSaldoSoDosSkusDeSessao_semPaginarODeposito() {
        service.cardapioPorToken("tok-mesa-4", APARELHO);

        // PDV-C058: o saldo livre (físico menos reserva, filtrado na consulta) só dos SKUs vendáveis de sessão.
        verify(estoqueUseCase).listSkusWithFreeStock("SALAO",
                Set.of("ZOMO-MENTA", "ZOMO-UVA", "ZOMO-LIMAO", "NAY-PESSEGO", "SEMFAIXA"));
        verify(estoqueUseCase, never()).listStockBalances(anyString(), anyInt(), anyInt());
        // As faixas, uma vez por cardápio.
        verify(sessionMenuUseCase).listTiers();
    }

    @Test
    void solicitar_essenciaSoComLataFechada_eAceito() {
        identificaMaria();
        latasFechadas("ZOMO-UVA");

        assertThat(service.solicitarPorToken("tok-mesa-4", pedido("ZOMO-UVA", APARELHO)).essenciaSku())
                .isEqualTo("ZOMO-UVA");
    }

    @Test
    void solicitar_essenciaSemFaixa_eRecusada() {
        identificaMaria();

        assertThatThrownBy(() -> service.solicitarPorToken("tok-mesa-4", pedido("SEMFAIXA", APARELHO)))
                .isInstanceOf(EssenciaIndisponivelException.class);
    }

    // ── Modelo semanal ────────────────────────────────────────────────────────────────────────

    @Test
    void diaSemSelecao_usaOModeloDoDiaDaSemana() {
        // 17/10/2026 é sábado = 6.
        when(repository.findModelo(6)).thenReturn(Set.of("ZOMO-MENTA"));

        assertThat(service.essenciasDoDia(HOJE).origem()).isEqualTo(OrigemSelecao.MODELO);
        assertThat(service.essenciasDoDia(HOJE).essencias()).filteredOn(e -> e.selecionada())
                .extracting(e -> e.essencia().sku()).containsExactly("ZOMO-MENTA");
        assertThat(service.cardapioPorToken("tok-mesa-4", APARELHO).destaques()).containsExactly("ZOMO-MENTA");
    }

    @Test
    void selecaoDoDia_venceOModelo() {
        when(repository.findModelo(6)).thenReturn(Set.of("ZOMO-MENTA"));
        when(repository.findEssenciasDoDia(HOJE)).thenReturn(Set.of("NAY-PESSEGO"));

        assertThat(service.essenciasDoDia(HOJE).origem()).isEqualTo(OrigemSelecao.DIA);
        assertThat(service.cardapioPorToken("tok-mesa-4", APARELHO).destaques()).containsExactly("NAY-PESSEGO");
    }

    @Test
    void setModelo_limpaOsSkus_eRecusaDiaInvalido() {
        service.setModelo(2, List.of(" ZOMO-MENTA ", "", "ZOMO-MENTA", "NAY-PESSEGO"));

        verify(repository).replaceModelo(2, Set.of("ZOMO-MENTA", "NAY-PESSEGO"));
        assertThatThrownBy(() -> service.setModelo(0, List.of())).isInstanceOf(AutoatendimentoDadosInvalidosException.class);
        assertThatThrownBy(() -> service.setModelo(8, List.of())).isInstanceOf(AutoatendimentoDadosInvalidosException.class);
    }

    @Test
    void copiarModelo_paraCadaDestino() {
        when(repository.findModelo(2)).thenReturn(Set.of("ZOMO-MENTA"));

        service.copiarModelo(2, List.of(3, 4, 2));

        verify(repository).replaceModelo(3, Set.of("ZOMO-MENTA"));
        verify(repository).replaceModelo(4, Set.of("ZOMO-MENTA"));
        verify(repository, never()).replaceModelo(eq(2), any());
        assertThatThrownBy(() -> service.copiarModelo(2, List.of(9)))
                .isInstanceOf(AutoatendimentoDadosInvalidosException.class);
        assertThatThrownBy(() -> service.copiarModelo(2, List.of()))
                .isInstanceOf(AutoatendimentoDadosInvalidosException.class);
    }

    // ── Ocupação no envio do pedido ───────────────────────────────────────────────────────────

    @Test
    void ocupadaNoEnvio_outroAparelhoPrecisaDoCodigo() {
        identificaMaria();
        ocupadaNoEnvio(pendente(AGORA.minusSeconds(60)));

        assertThat(service.cardapioPorToken("tok-mesa-4", "outro").acesso()).isEqualTo(AcessoMesa.BLOQUEADO);
        assertThatThrownBy(() -> service.solicitarPorToken("tok-mesa-4", pedido("ZOMO-MENTA", "outro")))
                .isInstanceOf(MesaOcupadaException.class);
        assertThat(service.codigoDaMesa("tok-mesa-4", APARELHO)).contains("4821");
    }

    @Test
    void ocupadaNoEnvio_recusadaOuExpirada_aMesaFicaLivre() {
        SolicitacaoSessao recusada = pendente(AGORA.minusSeconds(60)).recusada("sem lata", "ana", AGORA);
        ocupadaNoEnvio(recusada);
        assertThat(service.cardapioPorToken("tok-mesa-4", "outro").acesso()).isEqualTo(AcessoMesa.LIVRE);

        ocupadaNoEnvio(pendente(AGORA.minus(SolicitacaoSessao.VALIDADE).minusSeconds(1)));
        assertThat(service.cardapioPorToken("tok-mesa-4", "outro").acesso()).isEqualTo(AcessoMesa.LIVRE);
        assertThat(service.ocupacoesAbertas()).isEmpty();
    }

    @Test
    void ocupadaNoEnvio_mesaAtualSoComOCodigo_eChamadoEspera() {
        ocupadaNoEnvio(pendente(AGORA.minusSeconds(60)));

        MesaAtual mesa = service.mesaAtualPorToken("tok-mesa-4", APARELHO);

        assertThat(mesa.codigo()).isEqualTo("4821");
        assertThat(mesa.itens()).isEmpty();
        assertThat(mesa.emAberto()).isEqualByComparingTo("0");
        verify(comandaUseCase, never()).getComanda(anyLong());
        assertThatThrownBy(() -> service.chamarPorToken("tok-mesa-4",
                new NovoChamado(TipoChamado.ATENDENTE, null, APARELHO)))
                .isInstanceOf(MesaOcupadaException.class);
    }

    @Test
    void aceitar_ocupadaNoEnvio_aMesmaOcupacaoGanhaAComanda() {
        SolicitacaoSessao doEnvio = pendente(AGORA.minusSeconds(60));
        ocupadaNoEnvio(doEnvio);
        when(repository.findSolicitacaoByIdForUpdate(99L)).thenReturn(Optional.of(doEnvio));
        when(repository.findUltimaAceita(7L)).thenReturn(Optional.empty());
        when(comandaUseCase.openComanda(3L, "Mesa 4", 50L, "ana")).thenReturn(comanda(500L, ComandaStatus.ABERTA));

        service.aceitar(99L, 3L, "ana", true);

        ArgumentCaptor<MesaOcupacao> ocupacao = ArgumentCaptor.forClass(MesaOcupacao.class);
        verify(repository).saveOcupacao(ocupacao.capture());
        assertThat(ocupacao.getValue().id()).isEqualTo(70L);
        assertThat(ocupacao.getValue().comandaId()).isEqualTo(500L);
        assertThat(ocupacao.getValue().codigo()).isEqualTo("4821");
        verify(repository).addAcesso(70L, APARELHO, false);
    }

    // ── Linha de sessão e compras da loja ─────────────────────────────────────────────────────

    @Test
    void sessao_semAndamentoGravado_vemSoComOLancamento() {
        // Linha de sessão sem SessionProgress: a anterior à V132.
        ComandaItem antiga = ComandaItem.of(1L, "SESSAO-TRAD", BigDecimal.ONE, new BigDecimal("25.00"),
                BigDecimal.ZERO, "Sessão", AGORA, ConsumptionMode.SESSAO, false, null, "Zomo · Menta", null);
        ComandaItem bebida = ComandaItem.of(2L, "AGUA", BigDecimal.ONE, new BigDecimal("5.00"), BigDecimal.ZERO,
                "Água", AGORA);

        MesaAtual.Sessao sessao = SalaoAutoatendimentoService.sessao(antiga);

        assertThat(sessao).isNotNull();
        assertThat(sessao.status()).isNull();
        assertThat(sessao.essencia()).isEqualTo("Zomo · Menta");
        assertThat(sessao.lancadaEm()).isEqualTo(AGORA);
        assertThat(SalaoAutoatendimentoService.sessao(bebida)).isNull();
    }

    @Test
    void mesaAtual_trazAsComprasDaLoja_semACancelada() {
        ocupada();
        when(repository.hasAcesso(70L, APARELHO)).thenReturn(true);
        when(comandaUseCase.listStorePurchases(500L)).thenReturn(List.of(
                compra(900L, "000123", OrderStatus.CONCLUIDO), compra(901L, "000124", OrderStatus.CANCELADO)));

        MesaAtual mesa = service.mesaAtualPorToken("tok-mesa-4", APARELHO);

        assertThat(mesa.comprasLoja()).hasSize(1);
        MesaAtual.CompraLoja compra = mesa.comprasLoja().get(0);
        assertThat(compra.id()).isEqualTo("000123");
        assertThat(compra.em()).isEqualTo(AGORA);
        assertThat(compra.total()).isEqualByComparingTo("40.00");
        assertThat(compra.itens()).extracting(MesaAtual.ItemCompra::nome, MesaAtual.ItemCompra::valor)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Essência Zomo Menta", new BigDecimal("40.00")));
    }

    private void latasFechadas(String... skus) {
        when(estoqueUseCase.listSkusWithFreeStock(eq("SALAO"), any())).thenReturn(Set.of(skus));
    }

    /** A Mesa 4 ocupada pelo aparelho no envio do pedido {@code origem}, ainda sem comanda. */
    private void ocupadaNoEnvio(SolicitacaoSessao origem) {
        when(repository.findUltimaOcupacao(7L)).thenReturn(Optional.of(
                new MesaOcupacao(70L, 7L, null, origem.id(), 50L, "4821", AGORA.minusSeconds(60))));
        when(repository.findSolicitacaoById(origem.id())).thenReturn(Optional.of(origem));
        when(repository.hasAcesso(70L, APARELHO)).thenReturn(true);
    }

    private static Order compra(Long id, String numero, OrderStatus status) {
        OrderItem item = OrderItem.of(1L, "ZOMO-MENTA-LATA", BigDecimal.ONE, new BigDecimal("40.00"),
                new BigDecimal("20.00"), BigDecimal.ZERO, BigDecimal.ZERO, "Essência Zomo Menta");
        boolean cancelada = status == OrderStatus.CANCELADO;
        return Order.of(id, numero, SalesChannel.BALCAO, status, 50L, 3L, "LOJA", List.of(item),
                new BigDecimal("40.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("40.00"), BigDecimal.ZERO,
                cancelada ? "desistiu" : null, AGORA.minusSeconds(300), AGORA, cancelada ? null : AGORA,
                cancelada ? AGORA : null, null, 0);
    }

    private static Pedido pedido(String sku, String aparelho) {
        return new Pedido(new Identificacao("12345678901", null), sku, null, null, NO_CAIXA, aparelho);
    }

    private void identificaMaria() {
        when(crmUseCase.lookupCustomers(isNull(), isNull(), eq("12345678901")))
                .thenReturn(List.of(new CustomerMatch(MARIA, Set.of(CustomerMatchField.CPF))));
    }

    private static SolicitacaoSessao pendente(Instant criadoEm) {
        return pendente(criadoEm, NO_CAIXA);
    }

    private static SolicitacaoSessao pendente(Instant criadoEm, EscolhasSessao escolhas) {
        return new SolicitacaoSessao(99L, UUID.randomUUID(), 7L, 50L, 1L, "ZOMO-MENTA", null, null,
                SolicitacaoStatus.PENDENTE, null, null, criadoEm, null, null, escolhas, APARELHO);
    }

    private static Comanda fechada(Long id) {
        return Comanda.of(id, 3L, "SALAO", "Mesa 4", 50L, ComandaStatus.FECHADA, List.of(), 900L, "ana", AGORA, AGORA);
    }

    private static Comanda comanda(Long id, ComandaStatus status) {
        return Comanda.of(id, 3L, "SALAO", "Mesa 4", 50L, status, List.of(), null, "ana", AGORA, null);
    }
}
