package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.Money;
import com.cernecommerce.core.domain.model.ecommerce.CentralPedidosConfig;
import com.cernecommerce.core.domain.model.ecommerce.MotivoRecusa;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.domain.model.salao.ChamadoMesa;
import com.cernecommerce.core.ports.in.CentralPedidosUseCase;
import com.cernecommerce.core.ports.out.ecommerce.CentralPedidosConfigRepository;
import com.cernecommerce.core.ports.out.ecommerce.CentralPedidosIndicadoresQuery;
import com.cernecommerce.core.ports.out.ecommerce.CentralPedidosIndicadoresQuery.TotaisCanal;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central de Pedidos (ECM-F013): a configuração do recebimento e os indicadores do dia.
 *
 * <h2>Indicadores</h2>
 * <p>O dia é o local da loja. Receita e cancelamentos contam pelo nascimento do pedido, como o
 * {@code GET /orders/summary}; recusas, pelo momento da recusa. A taxa de recusa é recusados ÷ recebidos
 * no dia — num dia com recusa de pedido da véspera ela pode passar de 1, e isso é informação, não erro.</p>
 */
public class CentralPedidosService implements CentralPedidosUseCase {

    static final ZoneId ZONA_LOJA = ZoneId.of("America/Sao_Paulo");
    /** Os dois canais da Central, sempre presentes e nesta ordem — zerados num dia sem pedido. */
    private static final List<SalesChannel> CANAIS = List.of(SalesChannel.MARKETPLACE, SalesChannel.MESA);
    private static final int ESCALA_TAXA = 4;

    private final CentralPedidosConfigRepository configRepository;
    private final CentralPedidosIndicadoresQuery indicadoresQuery;
    private final Clock clock;

    public CentralPedidosService(CentralPedidosConfigRepository configRepository,
            CentralPedidosIndicadoresQuery indicadoresQuery, Clock clock) {
        this.configRepository = configRepository;
        this.indicadoresQuery = indicadoresQuery;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public CentralPedidosConfig config() {
        return configRepository.atual();
    }

    @Override
    @Transactional
    public CentralPedidosConfig salvarConfig(CentralPedidosConfig config, String username) {
        return configRepository.save(config, username);
    }

    @Override
    public StatusLoja statusLoja() {
        CentralPedidosConfig c = configRepository.atual();
        return new StatusLoja(c.appAbertoEm(clock.instant().atZone(ZONA_LOJA)), c.appRecebendo(),
                c.entrega().habilitada(), c.retiradaHabilitada(), c.tempoPreparoMin(), c.tempoEntregaMin(),
                c.entrega().pedidoMinimo(), c.entrega().taxaFixa(), c.entrega().bairros());
    }

    @Override
    @Transactional(readOnly = true)
    public Indicadores indicadores(LocalDate dia) {
        Instant de = dia.atStartOfDay(ZONA_LOJA).toInstant();
        Instant ate = dia.plusDays(1).atStartOfDay(ZONA_LOJA).toInstant();
        Instant agora = clock.instant();

        Map<String, TotaisCanal> receita = porCanal(indicadoresQuery.receitaPorCanal(de, ate));
        List<PorCanal> porCanal = CANAIS.stream().map(canal -> {
            TotaisCanal t = receita.get(canal.name());
            long pedidos = t == null ? 0 : t.pedidos();
            BigDecimal valor = Money.of(t == null ? BigDecimal.ZERO : t.valor());
            BigDecimal ticket = pedidos == 0 ? Money.of(BigDecimal.ZERO)
                    : valor.divide(BigDecimal.valueOf(pedidos), Money.MONEY_SCALE, Money.ROUNDING);
            return new PorCanal(canal.name(), pedidos, valor, ticket);
        }).toList();

        Duration validadeQr = Duration.ofMinutes(configRepository.atual().expiracaoSolicitacaoMin());
        Pendentes pendentes = new Pendentes(indicadoresQuery.pedidosAppPendentes(),
                indicadoresQuery.solicitacoesPendentes(agora.minus(validadeQr)),
                indicadoresQuery.chamadosAbertos(agora.minus(ChamadoMesa.VALIDADE)));

        // Recusa antiga, sem código, entra como OUTRO: o gráfico só conhece os códigos.
        Map<String, Long> recusasApp = new LinkedHashMap<>();
        indicadoresQuery.recusasPedidoApp(de, ate).forEach((codigo, total) ->
                recusasApp.merge(codigo == null ? MotivoRecusa.OUTRO.name() : codigo, total, Long::sum));
        Map<String, Long> recusasQr = indicadoresQuery.recusasSolicitacao(de, ate);
        Recusas recusas = new Recusas(
                ordenar(recusasApp).stream().map(e -> new RecusaApp(e.getKey(), e.getValue())).toList(),
                ordenar(recusasQr).stream().map(e -> new RecusaQr(e.getKey(), e.getValue())).toList());

        Map<String, TotaisCanal> cancelados = porCanal(indicadoresQuery.canceladosPorCanal(de, ate));
        List<Cancelamentos> cancelamentos = CANAIS.stream()
                .map(canal -> cancelados.get(canal.name()))
                .filter(t -> t != null && t.pedidos() > 0)
                .map(t -> new Cancelamentos(t.channel(), t.pedidos(), Money.of(t.valor())))
                .toList();

        Map<String, BigDecimal> taxaRecusa = new LinkedHashMap<>();
        taxaRecusa.put(SalesChannel.MARKETPLACE.name(), fracao(soma(recusasApp),
                indicadoresQuery.pedidosAppRecebidos(de, ate)));
        taxaRecusa.put(SalesChannel.MESA.name(), fracao(soma(recusasQr),
                indicadoresQuery.solicitacoesRecebidas(de, ate)));

        return new Indicadores(porCanal, pendentes, recusas, cancelamentos, taxaRecusa);
    }

    private static Map<String, TotaisCanal> porCanal(List<TotaisCanal> totais) {
        return totais.stream().collect(Collectors.toMap(TotaisCanal::channel, Function.identity(), (a, b) -> a));
    }

    /** Do mais frequente ao menos; empate pela ordem em que veio. */
    private static <K> List<Map.Entry<K, Long>> ordenar(Map<K, Long> totais) {
        return totais.entrySet().stream()
                .sorted(Map.Entry.<K, Long>comparingByValue(Comparator.reverseOrder()))
                .toList();
    }

    private static long soma(Map<?, Long> totais) {
        return totais.values().stream().mapToLong(Long::longValue).sum();
    }

    private static BigDecimal fracao(long parte, long base) {
        if (base == 0) {
            return BigDecimal.ZERO.setScale(ESCALA_TAXA);
        }
        return BigDecimal.valueOf(parte).divide(BigDecimal.valueOf(base), ESCALA_TAXA, RoundingMode.HALF_UP);
    }
}
