package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.SessionPayLaterNotAllowedException;
import com.cernecommerce.core.domain.exception.salao.AutoatendimentoDadosInvalidosException;
import com.cernecommerce.core.domain.exception.salao.ClienteJaCadastradoException;
import com.cernecommerce.core.domain.exception.salao.CodigoMesaInvalidoException;
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
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pdv.SessionAddon;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.domain.model.salao.AcessoMesa;
import com.cernecommerce.core.domain.model.salao.CardapioDaMesa;
import com.cernecommerce.core.domain.model.salao.EscolhasSessao;
import com.cernecommerce.core.domain.model.salao.EssenciaOferecida;
import com.cernecommerce.core.domain.model.salao.MesaOcupacao;
import com.cernecommerce.core.domain.model.salao.SalaoMesa;
import com.cernecommerce.core.domain.model.salao.SolicitacaoSessao;
import com.cernecommerce.core.domain.model.salao.SolicitacaoStatus;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase.Cadastro;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase.Completar;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase.Identificacao;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase.Pedido;
import com.cernecommerce.core.ports.in.SessionMenuUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import com.cernecommerce.core.ports.out.salao.SalaoAutoatendimentoRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
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
                userRepository, ssePort, afterCommit, Clock.fixed(AGORA, ZoneOffset.UTC));

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
            return new MesaOcupacao(70L, o.mesaId(), o.comandaId(), o.customerId(), o.codigo(), o.abertaEm());
        });

        when(repository.findEssenciasDoDia(any())).thenReturn(Set.of());
        when(repository.saveSolicitacao(any())).thenAnswer(inv -> {
            SolicitacaoSessao s = inv.getArgument(0);
            return s.id() != null ? s : new SolicitacaoSessao(99L, s.codigo(), s.mesaId(), s.customerId(), s.tierId(),
                    s.essenciaSku(), s.essenciaSku2(), s.observacao(), s.status(), s.motivo(), s.comandaId(),
                    s.criadoEm(), s.decididoEm(), s.decididoPor(), s.escolhas(), s.deviceToken());
        });
        when(crmUseCase.findCustomerById(50L)).thenReturn(MARIA);
    }

    // ── O que o QR oferece ────────────────────────────────────────────────────────────────────

    @Test
    void cardapio_ofereceAsLatasAbertasComFaixaAtivaEOsExtras() {
        CardapioDaMesa cardapio = service.cardapioPorToken("tok-mesa-4", APARELHO);

        assertThat(cardapio.mesa()).isEqualTo("Mesa 4");
        assertThat(cardapio.faixas()).extracting(SessionTier::nome).containsExactly("Tradicional", "Premium");
        // Só lata aberta e com faixa: Uva (só fechada), Limão (nada) e "sem faixa" ficam de fora.
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
    void identificar_devolveONomeMascaradoSeEVipEOQueFalta() {
        identificaMaria();
        when(crmUseCase.listCustomerTags(50L)).thenReturn(List.of(new Tag(1L, "VIP")));

        var achado = service.identificarPorToken("tok-mesa-4", new Identificacao("123.456.789-01", null)).orElseThrow();

        assertThat(achado.nome()).isEqualTo("Ma***");
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
                new Cadastro("Maria Luiza", "(21) 99999-0000", "123.456.789-01", nascimento)).nome()).isEqualTo("Ma***");
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
    }

    @Test
    void solicitar_pagarNoFinalSemSerVip_eRecusado() {
        identificaMaria();
        when(crmUseCase.listCustomerTags(50L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.solicitarPorToken("tok-mesa-4", new Pedido(new Identificacao("12345678901", null),
                "ZOMO-MENTA", null, null, new EscolhasSessao(false, null, List.of(), true), APARELHO)))
                .isInstanceOf(AutoatendimentoDadosInvalidosException.class).hasMessageContaining("VIP");
    }

    @Test
    void solicitar_pagarNoFinalVip_passa() {
        identificaMaria();
        when(crmUseCase.listCustomerTags(50L)).thenReturn(List.of(new Tag(1L, "VIP")));

        SolicitacaoSessao s = service.solicitarPorToken("tok-mesa-4", new Pedido(new Identificacao("12345678901", null),
                "ZOMO-MENTA", null, null, new EscolhasSessao(false, null, List.of(), true), APARELHO));

        assertThat(s.escolhas().pagarNoFinal()).isTrue();
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
        verify(repository).addAcesso(70L, APARELHO);
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
    void aceitar_mesaOcupada_lancaNaComandaDaOcupacaoELiberaOAparelho() {
        ocupada();
        when(repository.findSolicitacaoByIdForUpdate(99L)).thenReturn(Optional.of(pendente(AGORA.minusSeconds(60))));

        service.aceitar(99L, 3L, "ana", true);

        verify(comandaUseCase, never()).openComanda(anyLong(), anyString(), any(), anyString());
        verify(comandaUseCase).addSession(eq(500L), any(ComandaUseCase.AddSessionCommand.class), eq("ana"));
        verify(repository, never()).saveOcupacao(any());
        verify(repository).addAcesso(70L, APARELHO);
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

        verify(repository).addAcesso(70L, "outro");
    }

    @Test
    void acessar_comCodigoErrado_eRecusado() {
        ocupada();

        assertThatThrownBy(() -> service.acessarPorToken("tok-mesa-4", "1234", "outro"))
                .isInstanceOf(CodigoMesaInvalidoException.class);
        verify(repository, never()).addAcesso(anyLong(), anyString());
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
