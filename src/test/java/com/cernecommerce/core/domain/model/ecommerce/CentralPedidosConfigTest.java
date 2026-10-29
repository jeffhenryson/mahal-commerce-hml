package com.cernecommerce.core.domain.model.ecommerce;

import com.cernecommerce.core.domain.exception.ecommerce.CentralPedidosConfigInvalidaException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CentralPedidosConfigTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private static CentralPedidosConfig comHorarios(List<CentralPedidosConfig.Horario> horarios) {
        return new CentralPedidosConfig(true, true, horarios, false, 20, 40, 30, null, true, null);
    }

    private static CentralPedidosConfig comEntrega(CentralPedidosConfig.Entrega entrega, boolean retirada) {
        return new CentralPedidosConfig(true, true, List.of(), false, 20, 40, 30, entrega, retirada, null);
    }

    /** 2026-10-23 é sexta (ISO 5). */
    private static ZonedDateTime sexta(int hora, int minuto) {
        return LocalDateTime.of(2026, 10, 23, hora, minuto).atZone(SP);
    }

    @Test
    void semHorarioEstaSempreAberto() {
        assertThat(CentralPedidosConfig.padrao().abertoEm(sexta(4, 0))).isTrue();
    }

    @Test
    void horarioQueViraANoiteValeNaMadrugadaSeguinte() {
        CentralPedidosConfig c = comHorarios(List.of(
                new CentralPedidosConfig.Horario(5, LocalTime.of(18, 0), LocalTime.of(2, 0))));

        assertThat(c.abertoEm(sexta(17, 59))).isFalse();
        assertThat(c.abertoEm(sexta(18, 0))).isTrue();
        assertThat(c.abertoEm(sexta(23, 30))).isTrue();
        // Sábado 01:30 ainda é a sexta; 02:00 já fechou.
        assertThat(c.abertoEm(sexta(1, 30).plusDays(1))).isTrue();
        assertThat(c.abertoEm(sexta(2, 0).plusDays(1))).isFalse();
        // Sexta 01:00 seria a madrugada de quinta, que não tem horário.
        assertThat(c.abertoEm(sexta(1, 0))).isFalse();
    }

    @Test
    void appPausadoFechaMesmoNoHorario() {
        CentralPedidosConfig pausado = new CentralPedidosConfig(false, true, List.of(), false, 20, 40, 30, null,
                true, null);

        assertThat(pausado.appAbertoEm(sexta(12, 0))).isFalse();
    }

    @Test
    void taxaDoBairroIgnoraAcentoECaixaESemBairroCaiNaFixa() {
        CentralPedidosConfig c = comEntrega(new CentralPedidosConfig.Entrega(true, new BigDecimal("8"),
                BigDecimal.ZERO, null, List.of(new CentralPedidosConfig.TaxaBairro("Jardim Paraíso", new BigDecimal("5")))),
                true);

        assertThat(c.taxaPara("jardim paraiso")).isEqualByComparingTo("5.00");
        assertThat(c.taxaPara("Centro")).isEqualByComparingTo("8.00");
        assertThat(c.atendeBairro("JARDIM PARAÍSO")).isTrue();
        assertThat(c.atendeBairro("Centro")).isFalse();
        assertThat(CentralPedidosConfig.padrao().atendeBairro("Qualquer")).isTrue();
    }

    @Test
    void validacaoJuntaTodosOsProblemas() {
        assertThatThrownBy(() -> new CentralPedidosConfig(true, true, List.of(
                new CentralPedidosConfig.Horario(1, LocalTime.of(18, 0), LocalTime.of(18, 0)),
                new CentralPedidosConfig.Horario(1, LocalTime.of(9, 0), LocalTime.of(12, 0))),
                false, 0, 40, 2,
                new CentralPedidosConfig.Entrega(false, new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO,
                        List.of(new CentralPedidosConfig.TaxaBairro("Centro", BigDecimal.ONE),
                                new CentralPedidosConfig.TaxaBairro("centro", BigDecimal.ONE))),
                false, "x".repeat(121)))
                .isInstanceOf(CentralPedidosConfigInvalidaException.class)
                .hasMessageContaining("abertura igual ao fechamento")
                .hasMessageContaining("Dia da semana repetido")
                .hasMessageContaining("Tempo de preparo")
                .hasMessageContaining("pelo menos 5 min")
                .hasMessageContaining("não podem ser negativos")
                .hasMessageContaining("Raio de entrega")
                .hasMessageContaining("Bairro repetido")
                .hasMessageContaining("entrega ou a retirada")
                .hasMessageContaining("Chave Pix");
    }

    @Test
    void dinheiroNormalizadoAoCentavo() {
        CentralPedidosConfig c = comEntrega(new CentralPedidosConfig.Entrega(true, new BigDecimal("7.999"),
                new BigDecimal("30"), null, List.of()), true);

        assertThat(c.entrega().taxaFixa()).isEqualTo(new BigDecimal("8.00"));
        assertThat(c.entrega().pedidoMinimo()).isEqualTo(new BigDecimal("30.00"));
    }
}
