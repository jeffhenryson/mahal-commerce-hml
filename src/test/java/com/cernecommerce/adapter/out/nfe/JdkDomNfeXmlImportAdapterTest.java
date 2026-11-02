package com.cernecommerce.adapter.out.nfe;

import com.cernecommerce.core.domain.exception.compras.MalformedNfeXmlException;
import com.cernecommerce.core.domain.exception.estoque.BarcodeNotFoundException;
import com.cernecommerce.core.domain.model.compras.NfeImportLine;
import com.cernecommerce.core.domain.model.compras.NfeImportPreview;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdkDomNfeXmlImportAdapterTest {

    private EstoqueUseCase estoqueUseCase;
    private JdkDomNfeXmlImportAdapter adapter;

    @BeforeEach
    void setup() {
        estoqueUseCase = mock(EstoqueUseCase.class);
        adapter = new JdkDomNfeXmlImportAdapter(estoqueUseCase);
    }

    private static byte[] bytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private static final String WELL_FORMED_NFE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <nfeProc>
              <NFe>
                <infNFe>
                  <emit><CNPJ>12345678000199</CNPJ></emit>
                  <det nItem="1">
                    <prod>
                      <cProd>FORN-001</cProd>
                      <cEAN>7891234567890</cEAN>
                      <xProd>Essencia Menta 50g</xProd>
                      <qCom>10.000</qCom>
                      <vUnCom>12.50</vUnCom>
                      <rastro>
                        <nLote>L2026A</nLote>
                        <dVal>2027-06-01</dVal>
                      </rastro>
                    </prod>
                  </det>
                  <det nItem="2">
                    <prod>
                      <cProd>FORN-002</cProd>
                      <cEAN>SEM GTIN</cEAN>
                      <xProd>Carvao a granel</xProd>
                      <qCom>5.000</qCom>
                      <vUnCom>8.00</vUnCom>
                    </prod>
                  </det>
                </infNFe>
              </NFe>
            </nfeProc>
            """;

    @Test
    void parse_resolvesMatchedLineByEan() {
        Product product = Product.create("ESS-MENTA-50", "Essência Menta", "Essências", List.of());
        when(estoqueUseCase.findProductByBarcode(eq("7891234567890"))).thenReturn(product);

        NfeImportPreview preview = adapter.parse(bytes(WELL_FORMED_NFE));

        assertThat(preview.emitterCnpj()).isEqualTo("12345678000199");
        assertThat(preview.lines()).hasSize(2);

        NfeImportLine matched = preview.lines().get(0);
        assertThat(matched.itemNumber()).isEqualTo(1);
        assertThat(matched.supplierProductCode()).isEqualTo("FORN-001");
        assertThat(matched.ean()).isEqualTo("7891234567890");
        assertThat(matched.quantity()).isEqualByComparingTo("10.000");
        assertThat(matched.unitPrice()).isEqualByComparingTo("12.50");
        assertThat(matched.lotCode()).isEqualTo("L2026A");
        assertThat(matched.expiryDate()).isEqualTo("2027-06-01");
        assertThat(matched.matchStatus()).isEqualTo(NfeImportLine.MatchStatus.MATCHED);
        assertThat(matched.matchedSku()).isEqualTo("ESS-MENTA-50");
    }

    @Test
    void parse_semGtinLineComesBackUnmatchedWithoutCallingTheCatalog() {
        Product product = Product.create("ESS-MENTA-50", "Essência Menta", "Essências", List.of());
        when(estoqueUseCase.findProductByBarcode(eq("7891234567890"))).thenReturn(product);

        NfeImportPreview preview = adapter.parse(bytes(WELL_FORMED_NFE));

        NfeImportLine semGtin = preview.lines().get(1);
        assertThat(semGtin.ean()).isNull();
        assertThat(semGtin.matchStatus()).isEqualTo(NfeImportLine.MatchStatus.UNMATCHED);
        assertThat(semGtin.matchedSku()).isNull();
        assertThat(semGtin.lotCode()).isNull();
        assertThat(semGtin.expiryDate()).isNull();
    }

    @Test
    void parse_eanNotInCatalogComesBackUnmatched() {
        when(estoqueUseCase.findProductByBarcode(eq("7891234567890")))
                .thenThrow(new BarcodeNotFoundException("7891234567890"));

        NfeImportPreview preview = adapter.parse(bytes(WELL_FORMED_NFE));

        assertThat(preview.lines().get(0).matchStatus()).isEqualTo(NfeImportLine.MatchStatus.UNMATCHED);
        assertThat(preview.lines().get(0).matchedSku()).isNull();
    }

    @Test
    void parse_malformedXmlThrows() {
        assertThatThrownBy(() -> adapter.parse(bytes("isto nao e xml")))
                .isInstanceOf(MalformedNfeXmlException.class);
    }

    @Test
    void parse_xmlWithoutEmitterThrows() {
        String noEmit = """
                <nfeProc><NFe><infNFe>
                  <det nItem="1"><prod><cProd>X</cProd><xProd>Y</xProd><qCom>1</qCom><vUnCom>1.00</vUnCom></prod></det>
                </infNFe></NFe></nfeProc>
                """;

        assertThatThrownBy(() -> adapter.parse(bytes(noEmit)))
                .isInstanceOf(MalformedNfeXmlException.class);
    }

    @Test
    void parse_xmlWithoutAnyItemThrows() {
        String noItems = """
                <nfeProc><NFe><infNFe>
                  <emit><CNPJ>12345678000199</CNPJ></emit>
                </infNFe></NFe></nfeProc>
                """;

        assertThatThrownBy(() -> adapter.parse(bytes(noItems)))
                .isInstanceOf(MalformedNfeXmlException.class);
    }

    /**
     * O teste de regressão de segurança que realmente importa: um XML com DOCTYPE declarando uma
     * entidade externa apontando para um arquivo local. Sem o hardening
     * (disallow-doctype-decl/external-general-entities), o parser expandiria a entidade e o
     * conteúdo de {@code /etc/passwd} vazaria para dentro do XML processado — aqui, para dentro do
     * CNPJ. Com o hardening, o parser tem que falhar fechado (lançar, não silenciosamente ignorar
     * a entidade e seguir em frente).
     */
    @Test
    void parse_xxeAttackFixture_failsClosedInsteadOfResolvingTheExternalEntity() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE nfeProc [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <nfeProc><NFe><infNFe>
                  <emit><CNPJ>&xxe;</CNPJ></emit>
                  <det nItem="1"><prod><cProd>X</cProd><xProd>Y</xProd><qCom>1</qCom><vUnCom>1.00</vUnCom></prod></det>
                </infNFe></NFe></nfeProc>
                """;

        assertThatThrownBy(() -> adapter.parse(bytes(xxe)))
                .isInstanceOf(MalformedNfeXmlException.class);
    }

    /** Mesma ideia, com uma entidade parametrizada externa em vez de uma entidade geral. */
    @Test
    void parse_xxeWithExternalParameterEntity_failsClosed() {
        String xxeParam = """
                <?xml version="1.0"?>
                <!DOCTYPE nfeProc [
                  <!ENTITY % xxe SYSTEM "http://169.254.169.254/latest/meta-data/">
                  %xxe;
                ]>
                <nfeProc><NFe><infNFe>
                  <emit><CNPJ>12345678000199</CNPJ></emit>
                  <det nItem="1"><prod><cProd>X</cProd><xProd>Y</xProd><qCom>1</qCom><vUnCom>1.00</vUnCom></prod></det>
                </infNFe></NFe></nfeProc>
                """;

        assertThatThrownBy(() -> adapter.parse(bytes(xxeParam)))
                .isInstanceOf(MalformedNfeXmlException.class);
    }

    // ── V182: chave de acesso e unidades ────────────────────────────────────────────────────

    private static final String KEY = "35261112345678000199550010000012341000012345";

    private static String nfe(String infNFeId, String protocol) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <nfeProc>
                  <NFe>
                    <infNFe %s>
                      <emit><CNPJ>12345678000199</CNPJ></emit>
                      <det nItem="1">
                        <prod>
                          <cProd>FORN-SEDA</cProd>
                          <cEAN>SEM GTIN</cEAN>
                          <xProd>Seda caixa 12</xProd>
                          <qCom>2.0000</qCom>
                          <uCom>CX</uCom>
                          <vUnCom>36.00</vUnCom>
                          <uTrib>UN</uTrib>
                          <qTrib>24.0000</qTrib>
                        </prod>
                      </det>
                    </infNFe>
                  </NFe>
                  %s
                </nfeProc>
                """.formatted(infNFeId, protocol);
    }

    @Test
    void parse_leChaveDoProtocoloEUnidades() {
        NfeImportPreview preview = adapter.parse(bytes(nfe("Id=\"NFe" + "0".repeat(44) + "\"",
                "<protNFe><infProt><chNFe>" + KEY + "</chNFe></infProt></protNFe>")));

        assertThat(preview.accessKey()).isEqualTo(KEY);
        NfeImportLine line = preview.lines().get(0);
        assertThat(line.unit()).isEqualTo("CX");
        assertThat(line.taxUnit()).isEqualTo("UN");
        assertThat(line.taxQuantity()).isEqualByComparingTo("24");
        assertThat(line.unitsPerPackage()).isNull();
    }

    @Test
    void parse_semProtocolo_usaIdDoInfNFe() {
        NfeImportPreview preview = adapter.parse(bytes(nfe("Id=\"NFe" + KEY + "\"", "")));

        assertThat(preview.accessKey()).isEqualTo(KEY);
    }

    @Test
    void parse_semChaveValida_devolveNula() {
        NfeImportPreview preview = adapter.parse(bytes(nfe("", "")));

        assertThat(preview.accessKey()).isNull();
    }
}
