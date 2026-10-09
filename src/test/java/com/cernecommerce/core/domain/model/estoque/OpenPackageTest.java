package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.exception.estoque.InvalidOpenPackageUsesException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EST-F027 — as invariantes e as transições da lata aberta, sem banco.
 *
 * <p>É aqui que mora a regra que o QA de 06/09/2026 provou estar faltando: uma lata rende
 * {@code sessionsPerUnit} sessões, e o sistema baixava uma lata inteira por sessão. O contador é
 * a correção, e os casos abaixo são os que decidem se ele está certo — esgotar, repor antes do
 * fim, e desfazer um uso quando a mesa é cancelada.</p>
 */
class OpenPackageTest {

    private static final Instant T0 = Instant.parse("2026-09-08T20:00:00Z");

    private static OpenPackage lata(int sessionsPerUnit) {
        return OpenPackage.open("ESSE-BLUE", 1L, sessionsPerUnit, "atendente", T0);
    }

    @Test
    void open_nasceZerada_eAberta() {
        OpenPackage lata = lata(5);

        assertThat(lata.uses()).isZero();
        assertThat(lata.sessionsPerUnit()).isEqualTo(5);
        assertThat(lata.remaining()).isEqualTo(5);
        assertThat(lata.isOpen()).isTrue();
        assertThat(lata.isExhausted()).isFalse();
        assertThat(lata.closedAt()).isNull();
        assertThat(lata.closeReason()).isNull();
    }

    @Test
    void withUses_incrementaOContador() {
        OpenPackage lata = lata(5).withUses(1).withUses(2);

        assertThat(lata.uses()).isEqualTo(3);
        assertThat(lata.remaining()).isEqualTo(2);
        assertThat(lata.isExhausted()).isFalse();
    }

    /**
     * A regra que o bug violava: cinco sessões consomem <b>uma</b> lata, não cinco. Aqui isso
     * aparece como o contador chegando a 5 sem nenhuma lata nova ter sido aberta.
     */
    @Test
    void cincoSessoes_esgotamUmaLataSo() {
        OpenPackage lata = lata(5);
        for (int i = 0; i < 5; i++) {
            lata = lata.withUses(1);
        }

        assertThat(lata.uses()).isEqualTo(5);
        assertThat(lata.remaining()).isZero();
        assertThat(lata.isExhausted()).isTrue();
        // Continua ABERTA: é a lata que o atendente está terminando. Quem a fecha é a sessão
        // seguinte, ao abrir a próxima.
        assertThat(lata.isOpen()).isTrue();
    }

    @Test
    void withUses_naoPassaDoTeto_emVezDeEstourar() {
        // Uma sessão que pede mais usos do que ainda cabe é a lata acabando no meio do lançamento.
        // O que sobra é cobrado da próxima pelo caminho normal; recusar aqui negaria um lançamento
        // que o salão já fez.
        OpenPackage lata = lata(5).withUses(4).withUses(3);

        assertThat(lata.uses()).isEqualTo(5);
        assertThat(lata.isExhausted()).isTrue();
    }

    @Test
    void withoutUses_desfazOContador_semPassarDeZero() {
        assertThat(lata(5).withUses(3).withoutUses(1).uses()).isEqualTo(2);
        // Lata reposta entre o lançamento e o cancelamento: não há o que descontar, e recusar o
        // cancelamento por isso deixaria a mesa presa.
        assertThat(lata(5).withUses(1).withoutUses(4).uses()).isZero();
    }

    @Test
    void closed_carimbaMotivoEMomento() {
        Instant fechamento = T0.plusSeconds(3600);
        OpenPackage fechada = lata(5).withUses(2).closed(OpenPackageCloseReason.REPLACED, fechamento);

        assertThat(fechada.isOpen()).isFalse();
        assertThat(fechada.closedAt()).isEqualTo(fechamento);
        assertThat(fechada.closeReason()).isEqualTo(OpenPackageCloseReason.REPLACED);
        // A sobra fica registrada: 2 de 5 conta a história da reposição antecipada, e é por isso
        // que ela não precisa virar ajuste de estoque.
        assertThat(fechada.uses()).isEqualTo(2);
        assertThat(fechada.remaining()).isEqualTo(3);
    }

    @Test
    void compactConstructor_recusaMeioEstadoDeFechamento() {
        assertThatThrownBy(() -> new OpenPackage(1L, "ESSE-BLUE", 1L, 0, 5, T0, "atendente", T0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closedAt e closeReason");

        assertThatThrownBy(() -> new OpenPackage(1L, "ESSE-BLUE", 1L, 0, 5, T0, "atendente", null,
                OpenPackageCloseReason.EXHAUSTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closedAt e closeReason");
    }

    @Test
    void compactConstructor_recusaContadorInvalido() {
        assertThatThrownBy(() -> new OpenPackage(null, "ESSE-BLUE", 1L, -1, 5, T0, "atendente", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uses");

        assertThatThrownBy(() -> new OpenPackage(null, "ESSE-BLUE", 1L, 6, 5, T0, "atendente", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionsPerUnit");

        assertThatThrownBy(() -> new OpenPackage(null, "ESSE-BLUE", 1L, 0, 0, T0, "atendente", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionsPerUnit");
    }

    @Test
    void compactConstructor_exigeSkuDepositoEAutor() {
        assertThatThrownBy(() -> new OpenPackage(null, " ", 1L, 0, 5, T0, "atendente", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sku");

        assertThatThrownBy(() -> new OpenPackage(null, "ESSE-BLUE", null, 0, 5, T0, "atendente", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warehouseId");

        assertThatThrownBy(() -> new OpenPackage(null, "ESSE-BLUE", 1L, 0, 5, T0, " ", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("openedBy");
    }

    @Test
    void withUses_eWithoutUses_recusamContagemNaoPositiva() {
        assertThatThrownBy(() -> lata(5).withUses(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lata(5).withoutUses(0)).isInstanceOf(IllegalArgumentException.class);
    }

    // ── Lata já aberta antes do sistema (EST-F033) ───────────────────────────────────────────

    /**
     * O operador informa o que ele vê — quantas sessões a lata ainda rende —, e o contador guarda
     * o que já foi gasto. Uma lata de 5 com 2 restantes nasce com 3 usos.
     */
    @Test
    void registered_guardaOsUsosJaGastos() {
        OpenPackage lata = OpenPackage.registered("ESSE-BLUE", 1L, 5, 2, "atendente", T0);

        assertThat(lata.uses()).isEqualTo(3);
        assertThat(lata.remaining()).isEqualTo(2);
        assertThat(lata.isOpen()).isTrue();
        assertThat(lata.isExhausted()).isFalse();
        assertThat(lata.openedAt()).isEqualTo(T0);
    }

    /** Lata recém-aberta cadastrada: o mesmo estado de {@code open}, só que sem a baixa. */
    @Test
    void registered_cheia_nasceZerada() {
        assertThat(OpenPackage.registered("ESSE-BLUE", 1L, 5, 5, "atendente", T0).uses()).isZero();
    }

    /**
     * Zero restante é lata vazia, que não tem o que cadastrar — vai para o lixo, não para o
     * contador. E mais restante do que a lata rende é erro de digitação, não lata maior.
     */
    @Test
    void registered_recusaRestanteForaDaLata() {
        assertThatThrownBy(() -> OpenPackage.registered("ESSE-BLUE", 1L, 5, 0, "atendente", T0))
                .isInstanceOf(InvalidOpenPackageUsesException.class)
                .hasMessageContaining("entre 1 e 5");
        assertThatThrownBy(() -> OpenPackage.registered("ESSE-BLUE", 1L, 5, 6, "atendente", T0))
                .isInstanceOf(InvalidOpenPackageUsesException.class)
                .hasMessageContaining("entre 1 e 5");
    }
}
