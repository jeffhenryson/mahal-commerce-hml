package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EST-F032 — a venda do PDV abre a embalagem sozinha: o cigarro solto que falta sai de um maço, e o
 * maço que falta sai de uma carteira, na mesma transação da venda.
 *
 * <p>Modelo da decisão do dono (2026-10-08): um produto "LM" com variações cor × embalagem, cada uma
 * com SKU e preço próprios, ligadas por "contém N".</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PackagingBreakIT {

    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired PdvUseCase pdvUseCase;

    private record Lm(String cart, String maco, String un, String warehouse, String operator) {}

    private Lm givenLmAzul(String carteiras) {
        String s = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Lm lm = new Lm("LM-AZ-CART-" + s, "LM-AZ-MACO-" + s, "LM-AZ-UN-" + s, "LOJA-" + s, "caixa-" + s);
        estoqueUseCase.createWarehouse(lm.warehouse(), "Loja " + s, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct("LM-" + s, "LM " + s, "Cigarros", List.of(
                        ProductVariant.create(lm.cart(), List.of(new ProductAttribute("embalagem", "carteira")),
                                Pricing.of(new BigDecimal("80.00"), null, new BigDecimal("110.00"))),
                        ProductVariant.create(lm.maco(), List.of(new ProductAttribute("embalagem", "maço")),
                                Pricing.of(new BigDecimal("8.00"), null, new BigDecimal("12.00"))),
                        ProductVariant.create(lm.un(), List.of(new ProductAttribute("embalagem", "unidade")),
                                Pricing.of(new BigDecimal("0.40"), null, new BigDecimal("1.00")))),
                Pricing.of(new BigDecimal("8.00"), null, new BigDecimal("12.00")));
        estoqueUseCase.definePackaging(lm.un(), lm.maco(), 20);
        estoqueUseCase.definePackaging(lm.maco(), lm.cart(), 10);
        estoqueUseCase.adjustStock(lm.cart(), lm.warehouse(), MovementType.ENTRADA, new BigDecimal(carteiras),
                "Compra de carteiras", lm.operator());
        return lm;
    }

    private BigDecimal saldo(Lm lm, String sku) {
        return estoqueUseCase.getStockBalance(sku, lm.warehouse()).quantity();
    }

    private void vender(Lm lm, CashRegisterSession caixa, String sku, String quantidade, String total) {
        pdvUseCase.registerSale(caixa.id(), null,
                List.of(new SaleItemCommand(sku, new BigDecimal(quantidade), null)),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal(total), null)), lm.operator());
    }

    /**
     * O exemplo do dono: comprou 2 carteiras, vende 25 cigarros soltos. Abre-se 1 carteira (sobram
     * 1 carteira e 10 maços), abrem-se 2 maços (sobram 8 maços e 40 soltos), saem 25 soltos.
     */
    @Test
    void venderSoltos_abreMacoECarteiraSozinho() {
        Lm lm = givenLmAzul("2");
        CashRegisterSession caixa = pdvUseCase.openSession(lm.operator(), BigDecimal.ZERO, lm.warehouse());

        vender(lm, caixa, lm.un(), "25", "25.00");

        assertThat(saldo(lm, lm.cart())).isEqualByComparingTo("1");
        assertThat(saldo(lm, lm.maco())).isEqualByComparingTo("8");
        assertThat(saldo(lm, lm.un())).isEqualByComparingTo("15");
    }

    /** O maço também é vendido inteiro: sem maço fechado, abre-se uma carteira. */
    @Test
    void venderMaco_semMacoFechado_abreACarteira() {
        Lm lm = givenLmAzul("1");
        CashRegisterSession caixa = pdvUseCase.openSession(lm.operator(), BigDecimal.ZERO, lm.warehouse());

        vender(lm, caixa, lm.maco(), "1", "12.00");

        assertThat(saldo(lm, lm.cart())).isEqualByComparingTo("0");
        assertThat(saldo(lm, lm.maco())).isEqualByComparingTo("9");
    }

    /**
     * A cadeia inteira não cobre (1 carteira = 10 maços, pedem-se 11): a venda falha com o erro de
     * sempre. A reversão das quebras já feitas é da transação da venda, que é uma só.
     */
    @Test
    void venderAlemDaCadeia_falhaSemAbrirNada() {
        Lm lm = givenLmAzul("1");
        CashRegisterSession caixa = pdvUseCase.openSession(lm.operator(), BigDecimal.ZERO, lm.warehouse());

        assertThatThrownBy(() -> vender(lm, caixa, lm.maco(), "11", "132.00"))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void cadeia_listaOsTresNiveisComODisponivel() {
        Lm lm = givenLmAzul("2");

        List<EstoqueUseCase.PackagingLevel> cadeia = estoqueUseCase.getPackagingChain(lm.maco(), lm.warehouse());

        assertThat(cadeia).extracting(EstoqueUseCase.PackagingLevel::sku)
                .containsExactly(lm.cart(), lm.maco(), lm.un());
        assertThat(cadeia.get(0).containsUnits()).isEqualTo(10);
        assertThat(cadeia.get(0).available()).isEqualByComparingTo("2");
        assertThat(cadeia.get(2).containsSku()).isNull();
    }

    /** PDV-F041 — a família aparece na central sozinha, por ter embalagem ligada. */
    @Test
    void central_listaAFamiliaComPrecoESaldo() {
        Lm lm = givenLmAzul("2");

        List<EstoqueUseCase.PackagedFamily> familias = estoqueUseCase.listPackagedFamilies(lm.warehouse());

        EstoqueUseCase.PackagedFamily familia = familias.stream()
                .filter(f -> f.lines().stream().anyMatch(l -> l.levels().get(0).sku().equals(lm.cart())))
                .findFirst().orElseThrow();
        List<EstoqueUseCase.PackagedLevel> niveis = familia.lines().get(0).levels();
        assertThat(niveis).extracting(EstoqueUseCase.PackagedLevel::sku).containsExactly(lm.cart(), lm.maco(), lm.un());
        assertThat(niveis.get(0).available()).isEqualByComparingTo("2");
        assertThat(niveis.get(1).price()).isEqualByComparingTo("12.00");
        assertThat(niveis.get(2).label()).isEqualTo("unidade");
    }
}
