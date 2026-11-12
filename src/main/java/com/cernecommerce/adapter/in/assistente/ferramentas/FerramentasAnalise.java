package com.cernecommerce.adapter.in.assistente.ferramentas;

import com.cernecommerce.adapter.in.assistente.Ferramenta;
import com.cernecommerce.adapter.in.assistente.FerramentaSupport.EntradaInvalida;
import com.cernecommerce.adapter.in.assistente.FerramentaSupport.Variacao;
import com.cernecommerce.adapter.in.assistente.ProvedorFerramentas;
import com.cernecommerce.adapter.in.assistente.ResultadoFerramenta;
import com.cernecommerce.adapter.in.controller.OrdersController;
import com.cernecommerce.adapter.in.dtos.response.AssistenteBlocoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteBlocoDTO.KpiItem;
import com.cernecommerce.adapter.in.dtos.response.AssistenteBlocoDTO.Ponto;
import com.cernecommerce.adapter.in.dtos.response.AssistenteColunaDTO;
import com.cernecommerce.adapter.in.dtos.response.DailyRevenueResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderSummaryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.TopProductResponseDTO;
import com.cernecommerce.core.domain.model.estoque.AlertSeverity;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.ports.out.assistente.AssistenteConsultasQuery;
import com.cernecommerce.core.ports.out.assistente.AssistenteConsultasQuery.CategoriaVendida;
import com.cernecommerce.core.ports.out.assistente.AssistenteConsultasQuery.ClienteInativo;
import com.cernecommerce.core.ports.out.assistente.AssistenteConsultasQuery.SaidaEstoque;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.cernecommerce.adapter.in.assistente.FerramentaSupport.*;
import static com.cernecommerce.adapter.in.assistente.ferramentas.FerramentasEstoque.corpo;
import static com.cernecommerce.adapter.in.assistente.ferramentas.FerramentasEstoque.props;

/**
 * Análises de leitura da rodada 3: comparação de períodos, mais vendidos (produtos e categorias),
 * ruptura prevista e clientes inativos. Devolvem cartões ({@code kpi}), tabelas e gráficos.
 *
 * <p>O que passa por controller herda o {@code @PreAuthorize} dele; o que lê pela
 * {@link AssistenteConsultasQuery} confere aqui as autoridades da tela equivalente.</p>
 */
@Component
public class FerramentasAnalise implements ProvedorFerramentas {

    private static final int MAX_DIAS = 366;
    /** Janela da média de saída da ruptura prevista. */
    private static final int BASE_RUPTURA_PADRAO = 30;

    private final OrdersController orders;
    private final AssistenteConsultasQuery consultas;
    private final Clock clock = Clock.system(FUSO);

    public FerramentasAnalise(OrdersController orders, AssistenteConsultasQuery consultas) {
        this.orders = orders;
        this.consultas = consultas;
    }

    @Override
    public List<Ferramenta> ferramentas() {
        return List.of(
                new Ferramenta("comparar_periodos", "Comparando períodos",
                        "Compara vendas pagas de um período com outro: total vendido (líquido), ticket médio e "
                                + "número de vendas, com a variação. comparar_com: periodo_anterior (mesmo "
                                + "tamanho, logo antes; padrão), semana_anterior ou ano_anterior.",
                        schema(props(
                                "inicio", str("Data inicial, AAAA-MM-DD"),
                                "fim", str("Data final, AAAA-MM-DD (inclusive)"),
                                "comparar_com", enumeracao("Com o que comparar", "periodo_anterior",
                                        "semana_anterior", "ano_anterior")),
                                "inicio", "fim"),
                        this::compararPeriodos),
                new Ferramenta("top_produtos", "Consultando mais vendidos",
                        "Produtos mais vendidos no período, por quantidade ou por faturamento.",
                        schema(props(
                                "de", str("Data inicial, AAAA-MM-DD"),
                                "ate", str("Data final, AAAA-MM-DD (inclusive)"),
                                "por", enumeracao("Critério (padrão faturamento)", "quantidade", "faturamento"),
                                "limite", inteiro("Quantos (1 a 20, padrão 10)")),
                                "de", "ate"),
                        this::topProdutos),
                new Ferramenta("top_categorias", "Consultando categorias mais vendidas",
                        "Categorias de produto mais vendidas no período, por quantidade ou por faturamento.",
                        schema(props(
                                "de", str("Data inicial, AAAA-MM-DD"),
                                "ate", str("Data final, AAAA-MM-DD (inclusive)"),
                                "por", enumeracao("Critério (padrão faturamento)", "quantidade", "faturamento"),
                                "limite", inteiro("Quantas (1 a 20, padrão 10)")),
                                "de", "ate"),
                        this::topCategorias),
                new Ferramenta("ruptura_prevista", "Prevendo ruptura de estoque",
                        "Produtos cujo estoque disponível acaba em até N dias, pela média diária de saída dos "
                                + "últimos dias (base_dias, padrão 30). Traz também o nível do alerta de "
                                + "reposição (no limite, abaixo, crítico) quando há ponto de reposição.",
                        schema(props(
                                "dias", inteiro("Horizonte em dias (1 a 90)"),
                                "base_dias", inteiro("Dias usados na média de saída (7 a 180, padrão 30)"),
                                "limite", inteiro("Quantos produtos (1 a 30, padrão 15)")),
                                "dias"),
                        this::rupturaPrevista),
                new Ferramenta("clientes_inativos", "Consultando clientes inativos",
                        "Clientes que já compraram, mas não compram há N dias. Só o nome, a data da última "
                                + "compra e quantos pedidos fizeram.",
                        schema(props(
                                "dias", inteiro("Dias sem compra (7 a 730)"),
                                "limite", inteiro("Quantos clientes (1 a 30, padrão 15)")),
                                "dias"),
                        this::clientesInativos));
    }

    // ── comparar_periodos ──────────────────────────────────────────────────────

    private ResultadoFerramenta compararPeriodos(JsonNode in) {
        LocalDate inicio = data(in, "inicio");
        LocalDate fim = data(in, "fim");
        validarPeriodo(inicio, fim, "inicio", "fim");
        String modo = texto(in, "comparar_com", 20);
        modo = modo == null ? "periodo_anterior" : modo;
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        LocalDate inicioBase;
        LocalDate fimBase;
        String rotuloBase;
        switch (modo) {
            case "periodo_anterior" -> {
                inicioBase = inicio.minusDays(dias);
                fimBase = inicio.minusDays(1);
                rotuloBase = dias > 1 ? "período anterior"
                        : inicio.equals(LocalDate.now(clock)) ? "ontem" : "dia anterior";
            }
            case "semana_anterior" -> {
                inicioBase = inicio.minusWeeks(1);
                fimBase = fim.minusWeeks(1);
                rotuloBase = "semana anterior";
            }
            case "ano_anterior" -> {
                inicioBase = inicio.minusYears(1);
                fimBase = fim.minusYears(1);
                rotuloBase = "ano anterior";
            }
            default -> throw new EntradaInvalida("'comparar_com' inválido: " + modo);
        }

        OrderSummaryResponseDTO atual = resumo(inicio, fim);
        OrderSummaryResponseDTO base = resumo(inicioBase, fimBase);

        Variacao vTotal = variacao(atual.getTotalRevenueNet(), base.getTotalRevenueNet());
        Variacao vTicket = variacao(atual.getAverageTicket(), base.getAverageTicket());
        Variacao vVendas = variacao(BigDecimal.valueOf(atual.getTotalOrders()), BigDecimal.valueOf(base.getTotalOrders()));
        String vs = " vs. " + rotuloBase;

        ObjectNode saida = objeto()
                .put("periodo", periodo(inicio, fim))
                .put("comparadoCom", periodo(inicioBase, fimBase));
        saida.putObject("atual")
                .put("totalVendido", dinheiro(atual.getTotalRevenueNet()))
                .put("ticketMedio", dinheiro(atual.getAverageTicket()))
                .put("vendas", atual.getTotalOrders());
        saida.putObject("anterior")
                .put("totalVendido", dinheiro(base.getTotalRevenueNet()))
                .put("ticketMedio", dinheiro(base.getAverageTicket()))
                .put("vendas", base.getTotalOrders());
        saida.putObject("variacao")
                .put("totalVendido", vTotal.texto())
                .put("ticketMedio", vTicket.texto())
                .put("vendas", vVendas.texto());

        ResultadoFerramenta r = ResultadoFerramenta.ok(json(saida))
                .comBloco(AssistenteBlocoDTO.kpi(tituloPeriodo(inicio, fim), List.of(
                        new KpiItem("Total vendido", dinheiro(atual.getTotalRevenueNet()), vTotal.texto() + vs,
                                vTotal.tendencia(), true),
                        new KpiItem("Ticket médio", dinheiro(atual.getAverageTicket()), vTicket.texto() + vs,
                                vTicket.tendencia(), true),
                        new KpiItem("Vendas", String.valueOf(atual.getTotalOrders()), vVendas.texto() + vs,
                                vVendas.tendencia(), true))));
        AssistenteBlocoDTO grafico = graficoDiario(atual);
        if (grafico != null) {
            r = r.comBloco(grafico);
        }
        return r.comLink(link("Abrir indicadores de vendas", "/app/indicadores/vendas"));
    }

    private OrderSummaryResponseDTO resumo(LocalDate de, LocalDate ate) {
        return corpo(orders.getSummary(null, null, null, null, inicioDoDia(de), fimDoDia(ate)));
    }

    /** Série diária do faturamento, quando o período tem mais de um dia. */
    static AssistenteBlocoDTO graficoDiario(OrderSummaryResponseDTO s) {
        List<DailyRevenueResponseDTO> dias = s.getDailyRevenue();
        if (dias == null || dias.size() < 2) {
            return null;
        }
        List<Ponto> pontos = dias.stream()
                .sorted(Comparator.comparing(DailyRevenueResponseDTO::getDate))
                .map(d -> new Ponto(d.getDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")),
                        d.getRevenue() == null ? 0 : d.getRevenue().setScale(2, RoundingMode.HALF_UP).doubleValue()))
                .toList();
        return AssistenteBlocoDTO.grafico("Vendas por dia", "linha", "Dia", "R$", true, pontos);
    }

    // ── top_produtos / top_categorias ──────────────────────────────────────────

    private ResultadoFerramenta topProdutos(JsonNode in) {
        LocalDate de = data(in, "de");
        LocalDate ate = data(in, "ate");
        validarPeriodo(de, ate, "de", "ate");
        boolean porQuantidade = porQuantidade(in);
        int limite = limite(in, 10, 20);
        List<TopProductResponseDTO> produtos = corpo(orders.getTopProducts(null, null, inicioDoDia(de), fimDoDia(ate),
                limite, porQuantidade ? "quantity" : "revenue"));

        ArrayNode itens = objeto().arrayNode();
        List<List<String>> linhas = new ArrayList<>();
        List<Ponto> pontos = new ArrayList<>();
        for (TopProductResponseDTO p : produtos) {
            itens.add(objeto().put("sku", p.getSku()).put("produto", p.getProductName())
                    .put("quantidade", quantidade(p.getQuantitySold())).put("faturamento", dinheiro(p.getRevenue())));
            linhas.add(List.of(ou(p.getProductName()), quantidade(p.getQuantitySold()), dinheiro(p.getRevenue())));
            pontos.add(new Ponto(curto(p.getProductName()), valorY(porQuantidade ? p.getQuantitySold() : p.getRevenue())));
        }
        ObjectNode saida = objeto().put("periodo", periodo(de, ate)).put("criterio", criterio(porQuantidade));
        saida.set("produtos", itens);
        ResultadoFerramenta r = ResultadoFerramenta.ok(json(saida));
        if (!linhas.isEmpty()) {
            r = r.comBloco(AssistenteBlocoDTO.tabela("Mais vendidos — " + tituloPeriodo(de, ate),
                            List.of(AssistenteColunaDTO.de("Produto"), AssistenteColunaDTO.sensivel("Quantidade"),
                                    AssistenteColunaDTO.sensivel("Faturamento")), linhas))
                    .comBloco(AssistenteBlocoDTO.grafico("Mais vendidos por " + criterio(porQuantidade), "barra",
                            "Produto", porQuantidade ? "Quantidade" : "R$", true, pontos));
        }
        return r.comLink(link("Abrir indicadores de vendas", "/app/indicadores/vendas"));
    }

    private ResultadoFerramenta topCategorias(JsonNode in) {
        exigir("ORDER_READ");
        LocalDate de = data(in, "de");
        LocalDate ate = data(in, "ate");
        validarPeriodo(de, ate, "de", "ate");
        boolean porQuantidade = porQuantidade(in);
        int limite = limite(in, 10, 20);
        List<CategoriaVendida> categorias = consultas.topCategorias(inicioDoDia(de), fimDoDia(ate), limite, porQuantidade);

        ArrayNode itens = objeto().arrayNode();
        List<List<String>> linhas = new ArrayList<>();
        List<Ponto> pontos = new ArrayList<>();
        for (CategoriaVendida c : categorias) {
            String nome = c.categoria() == null || c.categoria().isBlank() ? "Sem categoria" : c.categoria();
            itens.add(objeto().put("categoria", nome).put("quantidade", quantidade(c.quantidade()))
                    .put("faturamento", dinheiro(c.receita())));
            linhas.add(List.of(nome, quantidade(c.quantidade()), dinheiro(c.receita())));
            pontos.add(new Ponto(curto(nome), valorY(porQuantidade ? c.quantidade() : c.receita())));
        }
        ObjectNode saida = objeto().put("periodo", periodo(de, ate)).put("criterio", criterio(porQuantidade));
        saida.set("categorias", itens);
        ResultadoFerramenta r = ResultadoFerramenta.ok(json(saida));
        if (!linhas.isEmpty()) {
            r = r.comBloco(AssistenteBlocoDTO.tabela("Categorias — " + tituloPeriodo(de, ate),
                            List.of(AssistenteColunaDTO.de("Categoria"), AssistenteColunaDTO.sensivel("Quantidade"),
                                    AssistenteColunaDTO.sensivel("Faturamento")), linhas))
                    .comBloco(AssistenteBlocoDTO.grafico("Categorias por " + criterio(porQuantidade), "barra",
                            "Categoria", porQuantidade ? "Quantidade" : "R$", true, pontos));
        }
        return r;
    }

    // ── ruptura_prevista ───────────────────────────────────────────────────────

    private ResultadoFerramenta rupturaPrevista(JsonNode in) {
        // As mesmas da tela de alertas (resumo do estoque).
        exigir("ESTOQUE_PRODUCT_READ", "ESTOQUE_WAREHOUSE_READ");
        long dias = intervalo(numeroObrigatorio(in, "dias"), 1, 90, "dias");
        Long base = numero(in, "base_dias");
        long baseDias = base == null ? BASE_RUPTURA_PADRAO : intervalo(base, 7, 180, "base_dias");
        int limite = limite(in, 15, 30);
        LocalDate hoje = LocalDate.now(clock);
        List<SaidaEstoque> saidas = consultas.saidasDesde(inicioDoDia(hoje.minusDays(baseDias)));

        record Previsao(SaidaEstoque s, BigDecimal porDia, BigDecimal diasRestantes, AlertSeverity alerta) { }
        List<Previsao> previsoes = new ArrayList<>();
        BigDecimal janela = BigDecimal.valueOf(baseDias);
        for (SaidaEstoque s : saidas) {
            BigDecimal porDia = s.saidaNaJanela().divide(janela, 3, RoundingMode.HALF_UP);
            if (porDia.signum() <= 0) {
                continue;
            }
            BigDecimal disponivel = s.disponivel() == null ? BigDecimal.ZERO : s.disponivel().max(BigDecimal.ZERO);
            BigDecimal restantes = disponivel.divide(porDia, 1, RoundingMode.DOWN);
            if (restantes.compareTo(BigDecimal.valueOf(dias)) > 0) {
                continue;
            }
            AlertSeverity alerta = s.minimo() == null ? null
                    : ReorderPoint.of(null, s.sku(), s.depositoId(), s.minimo(), s.critico()).severityFor(disponivel);
            previsoes.add(new Previsao(s, porDia, restantes, alerta));
        }
        previsoes.sort(Comparator.comparing(Previsao::diasRestantes).thenComparing(p -> p.s().sku()));
        List<Previsao> mostradas = previsoes.subList(0, Math.min(limite, previsoes.size()));

        ArrayNode itens = objeto().arrayNode();
        List<List<String>> linhas = new ArrayList<>();
        for (Previsao p : mostradas) {
            String acaba = p.diasRestantes().signum() == 0 ? "sem estoque" : "em " + quantidade(p.diasRestantes()) + " dias";
            itens.add(objeto().put("sku", p.s().sku()).put("produto", p.s().produto())
                    .put("deposito", p.s().deposito())
                    .put("disponivel", quantidade(p.s().disponivel()))
                    .put("saidaPorDia", quantidade(p.porDia()))
                    .put("acaba", acaba)
                    .put("alerta", rotuloAlerta(p.alerta())));
            linhas.add(List.of(ou(p.s().produto()), ou(p.s().deposito()), quantidade(p.s().disponivel()),
                    quantidade(p.porDia()), acaba, rotuloAlerta(p.alerta())));
        }
        ObjectNode saida = objeto()
                .put("horizonteDias", dias)
                .put("baseDaMediaDias", baseDias)
                .put("total", previsoes.size())
                .put("mostrados", mostradas.size());
        saida.set("produtos", itens);
        ResultadoFerramenta r = ResultadoFerramenta.ok(json(saida));
        if (!linhas.isEmpty()) {
            r = r.comBloco(AssistenteBlocoDTO.tabela("Acaba em até " + dias + " dias",
                    List.of(AssistenteColunaDTO.de("Produto"), AssistenteColunaDTO.de("Depósito"),
                            AssistenteColunaDTO.de("Disponível"), AssistenteColunaDTO.de("Saída/dia"),
                            AssistenteColunaDTO.de("Acaba"), AssistenteColunaDTO.de("Alerta")), linhas));
        }
        return r.comLink(link("Ver alertas", "/app/estoque/alertas"));
    }

    private static String rotuloAlerta(AlertSeverity a) {
        if (a == null) {
            return "sem ponto de reposição";
        }
        return switch (a) {
            case OK -> "ok";
            case NO_LIMITE -> "no limite";
            case ABAIXO -> "abaixo do mínimo";
            case CRITICO -> "crítico";
        };
    }

    // ── clientes_inativos ──────────────────────────────────────────────────────

    private ResultadoFerramenta clientesInativos(JsonNode in) {
        exigir("CRM_CUSTOMER_READ");
        long dias = intervalo(numeroObrigatorio(in, "dias"), 7, 730, "dias");
        int limite = limite(in, 15, 30);
        LocalDate corte = LocalDate.now(clock).minusDays(dias);
        List<ClienteInativo> clientes = consultas.clientesInativos(inicioDoDia(corte), limite);

        ArrayNode itens = objeto().arrayNode();
        List<List<String>> linhas = new ArrayList<>();
        for (ClienteInativo c : clientes) {
            String ultima = data(c.ultimaCompra().atZone(FUSO).toLocalDate());
            itens.add(objeto().put("id", c.id()).put("nome", c.nome()).put("ultimaCompra", ultima)
                    .put("pedidos", c.pedidos()));
            linhas.add(List.of(ou(c.nome()), ultima, String.valueOf(c.pedidos())));
        }
        ObjectNode saida = objeto().put("semCompraHaDias", dias).put("mostrados", clientes.size());
        saida.set("clientes", itens);
        ResultadoFerramenta r = ResultadoFerramenta.ok(json(saida));
        if (!linhas.isEmpty()) {
            r = r.comBloco(AssistenteBlocoDTO.tabela("Sem comprar há " + dias + " dias ou mais",
                    List.of(AssistenteColunaDTO.de("Cliente"), AssistenteColunaDTO.de("Última compra"),
                            AssistenteColunaDTO.de("Pedidos")), linhas));
        }
        return r.comLink(link("Abrir CRM", "/app/crm/clientes"));
    }

    // ── apoio ──────────────────────────────────────────────────────────────────

    private static void validarPeriodo(LocalDate de, LocalDate ate, String campoDe, String campoAte) {
        if (de == null || ate == null) {
            throw new EntradaInvalida("Informe '" + campoDe + "' e '" + campoAte + "'");
        }
        if (ate.isBefore(de)) {
            throw new EntradaInvalida("'" + campoAte + "' é anterior a '" + campoDe + "'");
        }
        if (ChronoUnit.DAYS.between(de, ate) >= MAX_DIAS) {
            throw new EntradaInvalida("Período maior que " + MAX_DIAS + " dias");
        }
    }

    private static long intervalo(long valor, long min, long max, String campo) {
        if (valor < min || valor > max) {
            throw new EntradaInvalida("'" + campo + "' deve estar entre " + min + " e " + max);
        }
        return valor;
    }

    private static boolean porQuantidade(JsonNode in) {
        String por = texto(in, "por", 20);
        if (por == null || "faturamento".equals(por)) {
            return false;
        }
        if ("quantidade".equals(por)) {
            return true;
        }
        throw new EntradaInvalida("'por' deve ser quantidade ou faturamento");
    }

    private static String criterio(boolean porQuantidade) {
        return porQuantidade ? "quantidade" : "faturamento";
    }

    private static String periodo(LocalDate de, LocalDate ate) {
        return data(de) + " a " + data(ate);
    }

    private static String tituloPeriodo(LocalDate de, LocalDate ate) {
        return de.equals(ate) ? data(de) : periodo(de, ate);
    }

    private static String curto(String nome) {
        String n = ou(nome);
        return n.length() > 24 ? n.substring(0, 23) + "…" : n;
    }

    private static double valorY(BigDecimal v) {
        return v == null ? 0 : v.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
