package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryTest {

    @Test
    void create_nasceAtivaSemDestaqueENoInicioDaOrdem() {
        Category category = Category.create("Narguilé");

        assertThat(category.id()).isNull();
        assertThat(category.name()).isEqualTo("Narguilé");
        assertThat(category.featured()).isFalse();
        assertThat(category.displayOrder()).isZero();
        assertThat(category.active()).isTrue();
    }

    @Test
    void nomeEAparadoNasPontas() {
        // O nome chega de texto livre digitado no formulário de produto.
        assertThat(Category.create("  Narguilé  ").name()).isEqualTo("Narguilé");
    }

    @Test
    void nomeVazioOuNuloERecusado() {
        assertThatThrownBy(() -> Category.create(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Category.create("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordemNegativaERecusada() {
        assertThatThrownBy(() -> Category.create("X", false, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withDetails_nuloMantemCadaCampo() {
        Category original = Category.of(1L, "Narguilé", true, 3, true);

        Category soNome = original.withDetails("Essência", null, null);
        assertThat(soNome.name()).isEqualTo("Essência");
        assertThat(soNome.featured()).isTrue();
        assertThat(soNome.displayOrder()).isEqualTo(3);

        Category soDestaque = original.withDetails(null, false, null);
        assertThat(soDestaque.name()).isEqualTo("Narguilé");
        assertThat(soDestaque.featured()).isFalse();

        Category soOrdem = original.withDetails(null, null, 9);
        assertThat(soOrdem.displayOrder()).isEqualTo(9);
        assertThat(soOrdem.featured()).isTrue();
    }

    @Test
    void withDetails_naoMexeEmActive() {
        // Desativar tem endpoint próprio, para render evento de auditoria distinto de um rename.
        Category inativa = Category.of(1L, "Narguilé", false, 0, false);

        assertThat(inativa.withDetails("Outro Nome", true, 5).active()).isFalse();
    }

    @Test
    void withActive_alternaPreservandoOResto() {
        Category original = Category.of(1L, "Narguilé", true, 3, true);

        Category desativada = original.withActive(false);

        assertThat(desativada.active()).isFalse();
        assertThat(desativada.id()).isEqualTo(1L);
        assertThat(desativada.name()).isEqualTo("Narguilé");
        assertThat(desativada.featured()).isTrue();
        assertThat(desativada.displayOrder()).isEqualTo(3);
    }

    @Test
    void destaqueEOrdemSaoIndependentes() {
        // São coisas diferentes de propósito: destacar não pode exigir reordenar todo mundo.
        Category category = Category.create("Promoções", true, 0);

        assertThat(category.featured()).isTrue();
        assertThat(category.displayOrder()).isZero();
    }

    // ===== Cor no PDV (V205) =====

    @Test
    void cor_nasceNulaPorPadrao() {
        assertThat(Category.create("Narguilé").color()).isNull();
        assertThat(Category.of(1L, "Narguilé", false, 0, true).color()).isNull();
    }

    @Test
    void cor_minusculaViraMaiuscula_eEAparada() {
        assertThat(Category.create("Essências", false, 0, false, false, " #f5b638 ").color()).isEqualTo("#F5B638");
    }

    @Test
    void cor_brancaViraNula() {
        assertThat(Category.create("Essências", false, 0, false, false, "  ").color()).isNull();
    }

    @Test
    void cor_hexInvalidoERecusado() {
        for (String cor : java.util.List.of("#12", "vermelho", "#GGGGGG", "F5B638", "#F5B6380")) {
            assertThatThrownBy(() -> Category.create("X", false, 0, false, false, cor))
                    .as(cor)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void withDetails_corNulaMantem_vaziaRemove_eValorTroca() {
        Category comCor = Category.of(1L, "Essências", false, 0, true, false, false, "#F5B638");

        assertThat(comCor.withDetails(null, null, null, null, null, null).color()).isEqualTo("#F5B638");
        assertThat(comCor.withDetails(null, null, null, null, null, "").color()).isNull();
        assertThat(comCor.withDetails(null, null, null, null, null, "#abc123").color()).isEqualTo("#ABC123");
        // As sobrecargas antigas não mexem na cor.
        assertThat(comCor.withDetails("Outro", true, 2).color()).isEqualTo("#F5B638");
    }

    @Test
    void withActive_preservaACor() {
        Category comCor = Category.of(1L, "Essências", false, 0, true, false, false, "#F5B638");

        assertThat(comCor.withActive(false).color()).isEqualTo("#F5B638");
    }
}
