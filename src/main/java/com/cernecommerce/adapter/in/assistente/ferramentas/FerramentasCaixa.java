package com.cernecommerce.adapter.in.assistente.ferramentas;

import com.cernecommerce.adapter.in.assistente.Ferramenta;
import com.cernecommerce.adapter.in.assistente.FerramentaSupport.EntradaInvalida;
import com.cernecommerce.adapter.in.assistente.ProvedorFerramentas;
import com.cernecommerce.adapter.in.assistente.ResultadoFerramenta;
import com.cernecommerce.adapter.in.controller.PdvController;
import com.cernecommerce.adapter.in.dtos.response.AssistenteBlocoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteBlocoDTO.KpiItem;
import com.cernecommerce.adapter.in.dtos.response.AssistenteColunaDTO;
import com.cernecommerce.adapter.in.dtos.response.CashMovementResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CashRegisterSessionResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.PaymentTotalResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SessionSummaryResponseDTO;
import com.cernecommerce.core.domain.model.PageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.cernecommerce.adapter.in.assistente.FerramentaSupport.*;
import static com.cernecommerce.adapter.in.assistente.ferramentas.FerramentasEstoque.corpo;
import static com.cernecommerce.adapter.in.assistente.ferramentas.FerramentasEstoque.props;

/**
 * Caixa do PDV, só leitura: os caixas abertos num dia com abertura, entradas por forma de pagamento,
 * sangrias, suprimentos e fechamento. Pelo {@link PdvController}: sem {@code PDV_SESSION_READ_ANY}, o
 * usuário só vê os próprios caixas, como na tela. O operador não vai ao modelo.
 */
@Component
public class FerramentasCaixa implements ProvedorFerramentas {

    private static final int MAX_CAIXAS = 20;

    private final PdvController pdv;
    private final Clock clock = Clock.system(FUSO);

    public FerramentasCaixa(PdvController pdv) {
        this.pdv = pdv;
    }

    @Override
    public List<Ferramenta> ferramentas() {
        return List.of(new Ferramenta("resumo_caixa", "Consultando o caixa",
                "Resumo dos caixas do PDV abertos num dia: valor de abertura, entradas por forma de pagamento, "
                        + "sangrias, suprimentos e fechamento (esperado, contado e diferença). Sem data: hoje.",
                schema(props("data", str("Dia, AAAA-MM-DD (padrão: hoje)"))),
                this::resumoCaixa));
    }

    private ResultadoFerramenta resumoCaixa(JsonNode in) {
        LocalDate dia = data(in, "data");
        if (dia == null) {
            dia = LocalDate.now(clock);
        }
        if (dia.isAfter(LocalDate.now(clock))) {
            throw new EntradaInvalida("'data' está no futuro");
        }
        Authentication auth = autenticacao();
        PageResult<CashRegisterSessionResponseDTO> pagina = corpo(pdv.listSessions(null, inicioDoDia(dia),
                fimDoDia(dia), null, 0, MAX_CAIXAS, auth));

        Map<String, BigDecimal> porForma = new LinkedHashMap<>();
        BigDecimal abertura = BigDecimal.ZERO;
        BigDecimal recebido = BigDecimal.ZERO;
        BigDecimal sangrias = BigDecimal.ZERO;
        BigDecimal suprimentos = BigDecimal.ZERO;
        BigDecimal diferenca = BigDecimal.ZERO;
        ArrayNode caixas = objeto().arrayNode();
        List<List<String>> linhas = new ArrayList<>();
        for (CashRegisterSessionResponseDTO s : pagina.content()) {
            SessionSummaryResponseDTO resumo = corpo(pdv.getSessionSummary(s.getId(), auth));
            BigDecimal sangriaCaixa = BigDecimal.ZERO;
            BigDecimal suprimentoCaixa = BigDecimal.ZERO;
            for (CashMovementResponseDTO m : corpo(pdv.listCashMovements(s.getId(), 0, 100, auth)).content()) {
                if ("SANGRIA".equals(m.getType())) {
                    sangriaCaixa = sangriaCaixa.add(nz(m.getAmount()));
                } else if ("SUPRIMENTO".equals(m.getType())) {
                    suprimentoCaixa = suprimentoCaixa.add(nz(m.getAmount()));
                }
            }
            if (resumo.getTotals() != null) {
                for (PaymentTotalResponseDTO t : resumo.getTotals()) {
                    porForma.merge(ou(t.getMethod()), nz(t.getNetAmount()), BigDecimal::add);
                }
            }
            abertura = abertura.add(nz(s.getOpeningAmount()));
            recebido = recebido.add(nz(resumo.getTotalReceived()));
            sangrias = sangrias.add(sangriaCaixa);
            suprimentos = suprimentos.add(suprimentoCaixa);
            diferenca = diferenca.add(nz(s.getDifferenceAmount()));

            boolean fechado = s.getClosedAt() != null;
            caixas.add(objeto()
                    .put("caixa", s.getId())
                    .put("deposito", s.getWarehouseCode())
                    .put("abertoEm", dataHora(s.getOpenedAt()))
                    .put("abertura", dinheiro(s.getOpeningAmount()))
                    .put("recebido", dinheiro(resumo.getTotalReceived()))
                    .put("sangrias", dinheiro(sangriaCaixa))
                    .put("suprimentos", dinheiro(suprimentoCaixa))
                    .put("situacao", fechado ? "fechado" : "aberto")
                    .put("fechadoEm", fechado ? dataHora(s.getClosedAt()) : null)
                    .put("esperado", fechado ? dinheiro(s.getExpectedAmount()) : null)
                    .put("contado", fechado ? dinheiro(s.getCountedAmount()) : null)
                    .put("diferenca", fechado ? dinheiro(s.getDifferenceAmount()) : null));
            linhas.add(List.of("#" + s.getId(), dataHora(s.getOpenedAt()), dinheiro(s.getOpeningAmount()),
                    dinheiro(resumo.getTotalReceived()), dinheiro(sangriaCaixa),
                    fechado ? dinheiro(s.getCountedAmount()) : "aberto",
                    fechado ? dinheiro(s.getDifferenceAmount()) : "—"));
        }

        ObjectNode saida = objeto()
                .put("dia", data(dia))
                .put("caixas", pagina.content().size())
                .put("abertura", dinheiro(abertura))
                .put("recebido", dinheiro(recebido))
                .put("sangrias", dinheiro(sangrias))
                .put("suprimentos", dinheiro(suprimentos))
                .put("diferencaNosFechados", dinheiro(diferenca));
        ObjectNode formas = saida.putObject("recebidoPorForma");
        porForma.forEach((forma, valor) -> formas.put(forma, dinheiro(valor)));
        saida.set("detalhe", caixas);
        if (pagina.totalElements() > pagina.content().size()) {
            saida.put("observacao", "Mostrando " + pagina.content().size() + " de " + pagina.totalElements() + " caixas.");
        }

        ResultadoFerramenta r = ResultadoFerramenta.ok(json(saida));
        if (pagina.content().isEmpty()) {
            return r.comLink(link("Abrir caixas", "/app/vendas/caixas"));
        }
        r = r.comBloco(AssistenteBlocoDTO.kpi("Caixa de " + data(dia), List.of(
                new KpiItem("Recebido", dinheiro(recebido), null, null, true),
                new KpiItem("Sangrias", dinheiro(sangrias), null, null, true),
                new KpiItem("Diferença no fechamento", dinheiro(diferenca), null,
                        diferenca.signum() == 0 ? null : diferenca.signum() > 0 ? "alta" : "baixa", true))));
        if (!porForma.isEmpty()) {
            List<List<String>> formasLinhas = new ArrayList<>();
            porForma.forEach((forma, valor) -> formasLinhas.add(List.of(forma, dinheiro(valor))));
            r = r.comBloco(AssistenteBlocoDTO.tabela("Por forma de pagamento",
                    List.of(AssistenteColunaDTO.de("Forma"), AssistenteColunaDTO.sensivel("Valor")), formasLinhas));
        }
        return r.comBloco(AssistenteBlocoDTO.tabela("Caixas",
                        List.of(AssistenteColunaDTO.de("Caixa"), AssistenteColunaDTO.de("Aberto em"),
                                AssistenteColunaDTO.sensivel("Abertura"), AssistenteColunaDTO.sensivel("Recebido"),
                                AssistenteColunaDTO.sensivel("Sangrias"), AssistenteColunaDTO.sensivel("Contado"),
                                AssistenteColunaDTO.sensivel("Diferença")), linhas))
                .comLink(link("Abrir caixas", "/app/vendas/caixas"));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
