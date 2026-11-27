package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.exception.estoque.InvalidProductRequestTransitionException;
import com.cernecommerce.core.domain.exception.estoque.ProductRequestCustomerRequiredException;
import com.cernecommerce.core.domain.exception.estoque.ProductRequestValidationException;
import com.cernecommerce.core.domain.exception.estoque.SkuRequiredForArrivedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductRequestTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    private static ProductRequest at(ProductRequestStatus status, String linkedSku) {
        return new ProductRequest(1L, "Essência Ziggy Love 66", "Ziggy", "50g", null, null, null, status, linkedSku,
                null, null, "ana", NOW, NOW, null, 0L);
    }

    @ParameterizedTest(name = "{0} → {1} permitido={2}")
    @CsvSource({
            "PENDING, ORDERED, true", "PENDING, ARRIVED, true", "PENDING, CANCELLED, true", "PENDING, DONE, false",
            "ORDERED, PENDING, true", "ORDERED, ARRIVED, true", "ORDERED, CANCELLED, true", "ORDERED, DONE, false",
            "ARRIVED, DONE, true", "ARRIVED, CANCELLED, true", "ARRIVED, PENDING, false", "ARRIVED, ORDERED, false",
            "DONE, CANCELLED, false", "DONE, PENDING, false", "CANCELLED, PENDING, false", "CANCELLED, DONE, false",
            "PENDING, PENDING, false"
    })
    void matriz_de_transicoes(ProductRequestStatus from, ProductRequestStatus to, boolean allowed) {
        ProductRequest request = at(from, "SKU-1");
        if (allowed) {
            assertThat(request.transitionTo(to, null, null, false, NOW).status()).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> request.transitionTo(to, null, null, false, NOW))
                    .isInstanceOf(InvalidProductRequestTransitionException.class);
        }
    }

    @Test
    void arrived_exige_sku_ou_without_sku() {
        assertThatThrownBy(() -> at(ProductRequestStatus.ORDERED, null)
                .transitionTo(ProductRequestStatus.ARRIVED, null, null, false, NOW))
                .isInstanceOf(SkuRequiredForArrivedException.class);
        assertThat(at(ProductRequestStatus.ORDERED, null)
                .transitionTo(ProductRequestStatus.ARRIVED, null, null, true, NOW).status())
                .isEqualTo(ProductRequestStatus.ARRIVED);
    }

    @Test
    void status_final_preenche_closed_at_e_volta_limpa() {
        ProductRequest done = at(ProductRequestStatus.ARRIVED, "SKU-1")
                .transitionTo(ProductRequestStatus.DONE, null, null, false, NOW);
        assertThat(done.closedAt()).isEqualTo(NOW);
        ProductRequest pending = at(ProductRequestStatus.ORDERED, null)
                .transitionTo(ProductRequestStatus.PENDING, null, null, false, NOW);
        assertThat(pending.closedAt()).isNull();
    }

    @Test
    void encomenda_guarda_previsao_e_nota() {
        ProductRequest ordered = at(ProductRequestStatus.PENDING, null).transitionTo(ProductRequestStatus.ORDERED,
                LocalDate.of(2026, 12, 10), "Fornecedor X", false, NOW);
        assertThat(ordered.expectedAt()).isEqualTo(LocalDate.of(2026, 12, 10));
        assertThat(ordered.supplierNote()).isEqualTo("Fornecedor X");
    }

    @Test
    void item_fechado_nao_aceita_edicao_nem_sku() {
        ProductRequest closed = at(ProductRequestStatus.CANCELLED, null);
        assertThatThrownBy(() -> closed.withLinkedSku("SKU-1", NOW))
                .isInstanceOf(InvalidProductRequestTransitionException.class);
        assertThatThrownBy(() -> closed.withDetails("x y", null, null, null, null, null, null, NOW))
                .isInstanceOf(InvalidProductRequestTransitionException.class);
    }

    @Test
    void chave_normalizada_sem_acento_e_minuscula() {
        ProductRequest r = ProductRequest.create("  Essência  Ziggy Love ", "Zíggy", "50G", null, null, "ana", NOW);
        assertThat(r.normalizedKey()).isEqualTo("essencia ziggy love ziggy 50g");
    }

    // ── Solicitações ─────────────────────────────────────────────────────────

    @Test
    void solicitacao_exige_cliente_do_crm_ou_nome_e_telefone() {
        assertThatThrownBy(() -> ProductRequestEntry.create(1L, null, "João", null, 1, null, null, null, null, "ana",
                NOW)).isInstanceOf(ProductRequestCustomerRequiredException.class);
        ProductRequestEntry avulso = ProductRequestEntry.create(1L, null, "João", "(85) 99999-1234", 1, null, null,
                null, null, "ana", NOW);
        assertThat(avulso.customerPhone()).isEqualTo("85999991234");
        assertThat(avulso.urgency()).isEqualTo(ProductRequestUrgency.MEDIUM);
    }

    @Test
    void telefone_fora_de_10_a_13_digitos_e_recusado() {
        assertThatThrownBy(() -> ProductRequestEntry.create(1L, null, "João", "9999-1234", 1, null, null, null, null,
                "ana", NOW)).isInstanceOf(ProductRequestValidationException.class);
    }

    @Test
    void mesmo_cliente_soma_quantidade_e_atualiza_o_resto() {
        ProductRequestEntry first = ProductRequestEntry.create(1L, 7L, null, null, 2, new BigDecimal("40"),
                ProductRequestUrgency.LOW, null, "primeira", "ana", NOW);
        ProductRequestEntry again = ProductRequestEntry.create(1L, 7L, null, null, 3, null, ProductRequestUrgency.HIGH,
                null, null, "bia", NOW);
        assertThat(first.sameCustomerAs(again)).isTrue();
        ProductRequestEntry merged = first.mergeWith(again);
        assertThat(merged.quantity()).isEqualTo(5);
        assertThat(merged.urgency()).isEqualTo(ProductRequestUrgency.HIGH);
        assertThat(merged.maxPrice()).isEqualByComparingTo("40");
        assertThat(merged.note()).isEqualTo("primeira");
    }

    @Test
    void avulso_compara_pelo_telefone_e_nunca_casa_com_cliente_do_crm() {
        ProductRequestEntry a = ProductRequestEntry.create(1L, null, "João", "85999991234", 1, null, null, null, null,
                "ana", NOW);
        ProductRequestEntry b = ProductRequestEntry.create(1L, null, "Joao S.", "(85) 99999-1234", 1, null, null, null,
                null, "ana", NOW);
        ProductRequestEntry crm = ProductRequestEntry.create(1L, 7L, null, null, 1, null, null, null, null, "ana", NOW);
        assertThat(a.sameCustomerAs(b)).isTrue();
        assertThat(a.sameCustomerAs(crm)).isFalse();
    }

    @Test
    void agregados_contam_so_as_ativas() {
        ProductRequestEntry ativa = ProductRequestEntry.create(1L, 7L, null, null, 2, new BigDecimal("30"),
                ProductRequestUrgency.HIGH, LocalDate.of(2026, 12, 1), null, "ana", NOW);
        ProductRequestEntry avisada = ProductRequestEntry.create(1L, 8L, null, null, 1, new BigDecimal("50"),
                ProductRequestUrgency.LOW, LocalDate.of(2026, 11, 30), null, "ana", NOW)
                .notified(ProductRequestNotifyChannel.MANUAL, NOW);
        ProductRequestEntry cancelada = ProductRequestEntry.create(1L, 9L, null, null, 10, null, null, null, null,
                "ana", NOW).withStatus(ProductRequestEntryStatus.CANCELLED);

        ProductRequestStats stats = ProductRequestStats.of(List.of(ativa, avisada, cancelada));

        assertThat(stats.entriesCount()).isEqualTo(2);
        assertThat(stats.totalQuantity()).isEqualTo(3);
        assertThat(stats.maxUrgency()).isEqualTo(ProductRequestUrgency.HIGH);
        assertThat(stats.earliestNeededBy()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(stats.maxPriceMin()).isEqualByComparingTo("30");
        assertThat(stats.maxPriceMax()).isEqualByComparingTo("50");
        assertThat(stats.pendingNotifications()).isEqualTo(1);
    }
}
