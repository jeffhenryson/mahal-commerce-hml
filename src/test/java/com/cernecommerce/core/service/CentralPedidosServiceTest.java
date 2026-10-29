package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.ecommerce.CentralPedidosConfig;
import com.cernecommerce.core.ports.in.CentralPedidosUseCase.Indicadores;
import com.cernecommerce.core.ports.out.ecommerce.CentralPedidosConfigRepository;
import com.cernecommerce.core.ports.out.ecommerce.CentralPedidosIndicadoresQuery;
import com.cernecommerce.core.ports.out.ecommerce.CentralPedidosIndicadoresQuery.TotaisCanal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CentralPedidosServiceTest {

    /** 12h de sexta, 23/10, em Botucatu. */
    private static final Instant AGORA = Instant.parse("2026-10-23T15:00:00Z");
    /** O dia local começa às 03h UTC. */
    private static final Instant DE = Instant.parse("2026-10-23T03:00:00Z");
    private static final Instant ATE = Instant.parse("2026-10-24T03:00:00Z");

    @Mock CentralPedidosIndicadoresQuery query;

    CentralPedidosConfig config = CentralPedidosConfig.padrao();
    final CentralPedidosConfigRepository repository = new CentralPedidosConfigRepository() {
        @Override
        public Optional<CentralPedidosConfig> find() {
            return Optional.ofNullable(config);
        }

        @Override
        public CentralPedidosConfig save(CentralPedidosConfig novo, String updatedBy) {
            config = novo;
            return novo;
        }
    };

    CentralPedidosService service;

    @BeforeEach
    void setUp() {
        service = new CentralPedidosService(repository, query, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void semConfigSalvaDevolveOPadrao() {
        config = null;

        assertThat(service.config()).isEqualTo(CentralPedidosConfig.padrao());
        assertThat(service.statusLoja().aberto()).isTrue();
    }

    @Test
    void indicadores_doDiaLocalComOsDoisCanaisETaxaDeRecusa() {
        when(query.receitaPorCanal(DE, ATE)).thenReturn(List.of(
                new TotaisCanal("MARKETPLACE", 4, new BigDecimal("300.00"))));
        when(query.canceladosPorCanal(DE, ATE)).thenReturn(List.of(
                new TotaisCanal("MARKETPLACE", 1, new BigDecimal("89.90"))));
        when(query.pedidosAppPendentes()).thenReturn(3L);
        when(query.solicitacoesPendentes(AGORA.minusSeconds(30 * 60))).thenReturn(1L);
        when(query.chamadosAbertos(any())).thenReturn(2L);
        Map<String, Long> recusasApp = new HashMap<>();
        recusasApp.put("SEM_ESTOQUE", 1L);
        recusasApp.put(null, 1L);
        recusasApp.put("OUTRO", 1L);
        when(query.recusasPedidoApp(DE, ATE)).thenReturn(recusasApp);
        when(query.pedidosAppRecebidos(DE, ATE)).thenReturn(8L);
        when(query.recusasSolicitacao(DE, ATE)).thenReturn(Map.of());
        when(query.solicitacoesRecebidas(DE, ATE)).thenReturn(0L);

        Indicadores i = service.indicadores(LocalDate.of(2026, 10, 23));

        assertThat(i.porCanal()).extracting(p -> p.channel()).containsExactly("MARKETPLACE", "MESA");
        assertThat(i.porCanal().get(0).ticketMedio()).isEqualByComparingTo("75.00");
        assertThat(i.porCanal().get(1).pedidos()).isZero();
        assertThat(i.porCanal().get(1).ticketMedio()).isEqualByComparingTo("0");
        assertThat(i.pendentesAgora().pedidosApp()).isEqualTo(3);
        assertThat(i.pendentesAgora().solicitacoes()).isEqualTo(1);
        assertThat(i.pendentesAgora().chamados()).isEqualTo(2);
        // A recusa antiga sem código soma no OUTRO.
        assertThat(i.recusas().pedidosApp()).extracting(r -> r.reasonCode() + "=" + r.total())
                .containsExactly("OUTRO=2", "SEM_ESTOQUE=1");
        assertThat(i.cancelamentos()).singleElement().satisfies(c -> {
            assertThat(c.channel()).isEqualTo("MARKETPLACE");
            assertThat(c.valor()).isEqualByComparingTo("89.90");
        });
        assertThat(i.taxaRecusa().get("MARKETPLACE")).isEqualByComparingTo("0.375");
        assertThat(i.taxaRecusa().get("MESA")).isEqualByComparingTo("0");
    }
}
