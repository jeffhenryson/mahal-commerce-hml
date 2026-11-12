package com.cernecommerce.adapter.in.assistente.ferramentas;

import com.cernecommerce.adapter.in.assistente.Ferramenta;
import com.cernecommerce.adapter.in.assistente.FerramentaSupport.EntradaInvalida;
import com.cernecommerce.adapter.in.assistente.ProvedorFerramentas;
import com.cernecommerce.adapter.in.assistente.ResultadoFerramenta;
import com.cernecommerce.adapter.in.controller.OrdersController;
import com.cernecommerce.adapter.in.dtos.response.AssistenteBlocoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteColunaDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderAdminResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderItemAdminResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderSummaryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.TopProductResponseDTO;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.cernecommerce.adapter.in.assistente.FerramentaSupport.*;
import static com.cernecommerce.adapter.in.assistente.ferramentas.FerramentasEstoque.corpo;
import static com.cernecommerce.adapter.in.assistente.ferramentas.FerramentasEstoque.props;

/**
 * Pedidos e vendas, só leitura. Do pedido vão ao modelo número, canal, status, cliente (id e
 * nome), total e itens — nunca endereço de entrega, entregador, operador ou pagamentos.
 */
@Component
public class FerramentasVendas implements ProvedorFerramentas {

    private static final int MAX_DIAS = 366;

    private final OrdersController orders;

    public FerramentasVendas(OrdersController orders) {
        this.orders = orders;
    }

    @Override
    public List<Ferramenta> ferramentas() {
        return List.of(
                new Ferramenta("buscar_pedidos", "Consultando pedidos",
                        "Lista pedidos por número, canal, status, cliente ou período, do mais recente para o "
                                + "mais antigo. Com 'numero', traz também os itens.",
                        schema(props(
                                "numero", str("Número do pedido"),
                                "canal", enumeracao("Canal de venda", "BALCAO", "MESA", "MARKETPLACE"),
                                "status", enumeracao("Status do pedido", "CRIADO", "AGUARDANDO_PAGAMENTO", "PAGO",
                                        "SEPARADO", "ENVIADO", "ENTREGUE", "CONCLUIDO", "CANCELADO", "REEMBOLSADO",
                                        "RESERVADO"),
                                "clienteId", inteiro("Id do cliente"),
                                "de", str("Data inicial, AAAA-MM-DD"),
                                "ate", str("Data final, AAAA-MM-DD"),
                                "limite", inteiro("Quantos pedidos (1 a 20, padrão 10)"))),
                        this::buscarPedidos),
                new Ferramenta("resumo_vendas", "Consultando vendas",
                        "Resumo de vendas pagas num período: pedidos, faturamento bruto e líquido, ticket "
                                + "médio, faturamento por canal, taxa de cancelamento e produtos mais vendidos. "
                                + "Período de até 366 dias.",
                        schema(props(
                                "de", str("Data inicial, AAAA-MM-DD"),
                                "ate", str("Data final, AAAA-MM-DD (inclusive)"),
                                "canal", enumeracao("Só um canal", "BALCAO", "MESA", "MARKETPLACE")),
                                "de", "ate"),
                        this::resumoVendas));
    }

    private ResultadoFerramenta buscarPedidos(JsonNode in) {
        LocalDate de = data(in, "de");
        LocalDate ate = data(in, "ate");
        String numero = texto(in, "numero", 50);
        PageResult<OrderAdminResponseDTO> page = corpo(orders.listOrders(
                enumOpcional(in, "canal", SalesChannel.class),
                enumOpcional(in, "status", OrderStatus.class),
                numero(in, "clienteId"),
                de == null ? null : inicioDoDia(de),
                ate == null ? null : fimDoDia(ate),
                null, null, numero, 0, limite(in, 10, 20), autenticacao()));

        boolean detalhar = numero != null && page.content().size() == 1;
        ArrayNode itens = objeto().arrayNode();
        List<List<String>> linhas = new ArrayList<>();
        for (OrderAdminResponseDTO o : page.content()) {
            ObjectNode pedido = objeto()
                    .put("id", o.getId())
                    .put("numero", o.getOrderNumber())
                    .put("canal", o.getChannel())
                    .put("status", o.getStatus())
                    .put("clienteId", o.getCustomerId())
                    .put("cliente", o.getCustomerName())
                    .put("total", dinheiro(o.getNetAmount()))
                    .put("criadoEm", dataHora(o.getCreatedAt()))
                    .put("pagoEm", o.getPaidAt() == null ? null : dataHora(o.getPaidAt()));
            if (detalhar && o.getItems() != null) {
                ArrayNode produtos = pedido.putArray("itens");
                for (OrderItemAdminResponseDTO i : o.getItems()) {
                    produtos.add(objeto()
                            .put("sku", i.getSku())
                            .put("produto", i.getProductName())
                            .put("quantidade", quantidade(i.getQuantity()))
                            .put("total", dinheiro(i.getNetAmount())));
                }
            }
            itens.add(pedido);
            linhas.add(List.of(ou(o.getOrderNumber()), dataHora(o.getCreatedAt()), ou(o.getChannel()),
                    ou(o.getStatus()), ou(o.getCustomerName()), dinheiro(o.getNetAmount())));
        }
        ObjectNode saida = objeto().put("total", page.totalElements());
        saida.set("pedidos", itens);
        ResultadoFerramenta r = ResultadoFerramenta.ok(json(saida));
        if (!linhas.isEmpty()) {
            r = r.comBloco(AssistenteBlocoDTO.tabela("Pedidos", List.of(AssistenteColunaDTO.de("Pedido"),
                    AssistenteColunaDTO.de("Data"), AssistenteColunaDTO.de("Canal"), AssistenteColunaDTO.de("Status"),
                    AssistenteColunaDTO.de("Cliente"), AssistenteColunaDTO.sensivel("Total")), linhas));
        }
        if (page.content().size() == 1) {
            r = r.comLink(link("Ver pedido", "/app/vendas/pedidos?pedido=" + page.content().get(0).getId()));
        }
        return r;
    }

    private ResultadoFerramenta resumoVendas(JsonNode in) {
        LocalDate de = data(in, "de");
        LocalDate ate = data(in, "ate");
        if (de == null || ate == null) {
            throw new EntradaInvalida("Informe 'de' e 'ate'");
        }
        if (ate.isBefore(de)) {
            throw new EntradaInvalida("'ate' é anterior a 'de'");
        }
        if (ChronoUnit.DAYS.between(de, ate) >= MAX_DIAS) {
            throw new EntradaInvalida("Período maior que " + MAX_DIAS + " dias");
        }
        OrderSummaryResponseDTO s = corpo(orders.getSummary(enumOpcional(in, "canal", SalesChannel.class), null,
                null, null, inicioDoDia(de), fimDoDia(ate)));

        ObjectNode saida = objeto()
                .put("periodo", data(de) + " a " + data(ate))
                .put("pedidos", s.getTotalOrders())
                .put("faturamentoLiquido", dinheiro(s.getTotalRevenueNet()))
                .put("faturamentoBruto", dinheiro(s.getTotalRevenueGross()))
                .put("ticketMedio", dinheiro(s.getAverageTicket()))
                .put("taxaCancelamentoOuReembolso", percentual(s.getCancelledOrRefundedRate()));
        ObjectNode porCanal = saida.putObject("faturamentoPorCanal");
        if (s.getRevenueByChannel() != null) {
            for (Map.Entry<String, BigDecimal> e : s.getRevenueByChannel().entrySet()) {
                porCanal.put(e.getKey(), dinheiro(e.getValue()));
            }
        }
        ArrayNode top = saida.putArray("maisVendidos");
        List<List<String>> linhas = new ArrayList<>();
        List<TopProductResponseDTO> produtos = s.getTopProducts() == null ? List.of() : s.getTopProducts();
        for (TopProductResponseDTO p : produtos.subList(0, Math.min(10, produtos.size()))) {
            top.add(objeto()
                    .put("sku", p.getSku())
                    .put("produto", p.getProductName())
                    .put("quantidade", quantidade(p.getQuantitySold()))
                    .put("receita", dinheiro(p.getRevenue())));
            linhas.add(List.of(ou(p.getProductName()), quantidade(p.getQuantitySold()), dinheiro(p.getRevenue())));
        }

        ResultadoFerramenta r = ResultadoFerramenta.ok(json(saida))
                .comBloco(AssistenteBlocoDTO.tabela("Vendas de " + data(de) + " a " + data(ate),
                        List.of(AssistenteColunaDTO.de("Indicador"), AssistenteColunaDTO.sensivel("Valor")),
                        List.of(List.of("Pedidos", String.valueOf(s.getTotalOrders())),
                                List.of("Faturamento líquido", dinheiro(s.getTotalRevenueNet())),
                                List.of("Ticket médio", dinheiro(s.getAverageTicket())))));
        AssistenteBlocoDTO grafico = FerramentasAnalise.graficoDiario(s);
        if (grafico != null) {
            r = r.comBloco(grafico);
        }
        if (!linhas.isEmpty()) {
            r = r.comBloco(AssistenteBlocoDTO.tabela("Mais vendidos", List.of(AssistenteColunaDTO.de("Produto"),
                    AssistenteColunaDTO.sensivel("Quantidade"), AssistenteColunaDTO.sensivel("Receita")), linhas));
        }
        return r.comLink(link("Abrir indicadores de vendas", "/app/indicadores/vendas"));
    }
}
