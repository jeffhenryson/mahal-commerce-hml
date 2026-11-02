package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.compras.NfeImportAlreadyProcessedException;
import com.cernecommerce.core.domain.exception.compras.NfeImportNotFoundException;
import com.cernecommerce.core.domain.exception.compras.SupplierNotFoundByTaxIdException;
import com.cernecommerce.core.domain.exception.compras.UnmatchedNfeLineException;
import com.cernecommerce.core.domain.model.compras.GoodsReceipt;
import com.cernecommerce.core.domain.model.compras.GoodsReceiptItem;
import com.cernecommerce.core.domain.model.compras.NfeImport;
import com.cernecommerce.core.domain.model.compras.NfeImportLine;
import com.cernecommerce.core.domain.model.compras.NfeImportPreview;
import com.cernecommerce.core.domain.model.compras.NfeImportStatus;
import com.cernecommerce.core.domain.model.compras.Supplier;
import com.cernecommerce.core.ports.in.ComprasUseCase;
import com.cernecommerce.core.ports.in.NfeImportUseCase.LineOverride;
import com.cernecommerce.core.ports.in.NfeImportUseCase.NfeImportConfirmCommand;
import com.cernecommerce.core.ports.out.compras.NfeImportRepository;
import com.cernecommerce.core.ports.out.compras.SupplierRepository;
import com.cernecommerce.core.ports.out.estoque.NfeXmlImportPort;
import com.cernecommerce.core.ports.out.storage.NfeImportStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NfeImportServiceTest {

    @Mock NfeXmlImportPort nfeXmlImportPort;
    @Mock SupplierRepository supplierRepository;
    @Mock NfeImportStoragePort nfeImportStoragePort;
    @Mock NfeImportRepository nfeImportRepository;
    @Mock ComprasUseCase comprasUseCase;

    NfeImportService service;

    private static final byte[] XML_BYTES = "<nfeProc/>".getBytes();

    @BeforeEach
    void setUp() {
        service = new NfeImportService(nfeXmlImportPort, supplierRepository, nfeImportStoragePort,
                nfeImportRepository, comprasUseCase);
    }

    private static NfeImportLine matchedLine(int itemNumber) {
        return NfeImportLine.fromXml(itemNumber, "FORN-00" + itemNumber, "789000000000" + itemNumber,
                "Item " + itemNumber, BigDecimal.ONE, new BigDecimal("10.00"), null, null, "SKU-" + itemNumber);
    }

    private static NfeImportLine unmatchedLine(int itemNumber) {
        return NfeImportLine.fromXml(itemNumber, "FORN-00" + itemNumber, null, "Item " + itemNumber,
                BigDecimal.ONE, new BigDecimal("10.00"), null, null, null);
    }

    private static Supplier supplier() {
        return new Supplier(7L, "Fornecedor Ltda", "12345678000199", "contato@fornecedor.com", true);
    }

    // ── previewImport ────────────────────────────────────────────────────────────────────────

    @Test
    void previewImport_matchesSupplierByCnpjAndPersistsPreviewed() {
        NfeImportPreview preview = new NfeImportPreview("12345678000199", List.of(matchedLine(1)));
        when(nfeXmlImportPort.parse(XML_BYTES)).thenReturn(preview);
        when(nfeImportStoragePort.save(eq(XML_BYTES), eq("xml"))).thenReturn("uuid.xml");
        when(supplierRepository.findByTaxId("12345678000199")).thenReturn(Optional.of(supplier()));
        when(nfeImportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NfeImport result = service.previewImport(XML_BYTES, "comprador1");

        assertThat(result.status()).isEqualTo(NfeImportStatus.PREVIEWED);
        assertThat(result.supplierId()).isEqualTo(7L);
        assertThat(result.fileReference()).isEqualTo("uuid.xml");
        verify(nfeImportRepository).save(argThat(n -> n.status() == NfeImportStatus.PREVIEWED));
    }

    @Test
    void previewImport_supplierNotFound_persistsRejectedAndThrows() {
        NfeImportPreview preview = new NfeImportPreview("00000000000000", List.of(matchedLine(1)));
        when(nfeXmlImportPort.parse(XML_BYTES)).thenReturn(preview);
        when(nfeImportStoragePort.save(any(), any())).thenReturn("uuid.xml");
        when(supplierRepository.findByTaxId("00000000000000")).thenReturn(Optional.empty());
        when(nfeImportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.previewImport(XML_BYTES, "comprador1"))
                .isInstanceOf(SupplierNotFoundByTaxIdException.class);

        verify(nfeImportRepository).save(argThat(n -> n.status() == NfeImportStatus.REJECTED
                && n.supplierId() == null));
        verifyNoInteractions(comprasUseCase);
    }

    @Test
    void previewImport_alwaysPersistsTheRawXmlEvenWhenSupplierIsNotFound() {
        NfeImportPreview preview = new NfeImportPreview("00000000000000", List.of(matchedLine(1)));
        when(nfeXmlImportPort.parse(XML_BYTES)).thenReturn(preview);
        when(nfeImportStoragePort.save(any(), any())).thenReturn("uuid.xml");
        when(supplierRepository.findByTaxId(any())).thenReturn(Optional.empty());
        when(nfeImportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.previewImport(XML_BYTES, "comprador1"))
                .isInstanceOf(SupplierNotFoundByTaxIdException.class);

        verify(nfeImportStoragePort).save(XML_BYTES, "xml");
    }

    // ── confirmImport ────────────────────────────────────────────────────────────────────────

    @Test
    void confirmImport_throwsWhenImportNotFound() {
        when(nfeImportRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmImport(
                new NfeImportConfirmCommand(999L, "LOJA-01", List.of()), "comprador1"))
                .isInstanceOf(NfeImportNotFoundException.class);
        verifyNoInteractions(comprasUseCase);
    }

    @Test
    void confirmImport_refusesAlreadyConfirmedImport() {
        NfeImport confirmed = NfeImport.of(1L, 7L, "12345678000199", "LOJA-01", "uuid.xml",
                NfeImportStatus.CONFIRMED, 55L, List.of(matchedLine(1)), "comprador1", Instant.now(), Instant.now());
        when(nfeImportRepository.findById(1L)).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> service.confirmImport(
                new NfeImportConfirmCommand(1L, "LOJA-01", List.of()), "comprador1"))
                .isInstanceOf(NfeImportAlreadyProcessedException.class);
        verifyNoInteractions(comprasUseCase);
    }

    @Test
    void confirmImport_refusesWhenSomeLineStaysUnmatchedAfterOverrides() {
        NfeImport previewed = NfeImport.of(1L, 7L, "12345678000199", null, "uuid.xml",
                NfeImportStatus.PREVIEWED, null, List.of(matchedLine(1), unmatchedLine(2)), "comprador1",
                Instant.now(), null);
        when(nfeImportRepository.findById(1L)).thenReturn(Optional.of(previewed));

        assertThatThrownBy(() -> service.confirmImport(
                new NfeImportConfirmCommand(1L, "LOJA-01", List.of()), "comprador1"))
                .isInstanceOf(UnmatchedNfeLineException.class);
        verifyNoInteractions(comprasUseCase);
    }

    @Test
    void confirmImport_appliesOverridesAndDelegatesToReceiveGoods() {
        NfeImport previewed = NfeImport.of(1L, 7L, "12345678000199", null, "uuid.xml",
                NfeImportStatus.PREVIEWED, null, List.of(matchedLine(1), unmatchedLine(2)), "comprador1",
                Instant.now(), null);
        when(nfeImportRepository.findById(1L)).thenReturn(Optional.of(previewed));
        when(nfeImportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        GoodsReceipt receipt = GoodsReceipt.of(55L, 7L, "LOJA-01",
                List.of(new GoodsReceiptItem("SKU-1", BigDecimal.ONE, null, null, new BigDecimal("10.00"))),
                "comprador1", Instant.now());
        when(comprasUseCase.receiveGoods(eq(7L), eq("LOJA-01"), any(), eq("comprador1"))).thenReturn(receipt);

        GoodsReceipt result = service.confirmImport(new NfeImportConfirmCommand(1L, "LOJA-01",
                List.of(new LineOverride(2, "SKU-MANUAL"))), "comprador1");

        assertThat(result.id()).isEqualTo(55L);
        verify(comprasUseCase).receiveGoods(eq(7L), eq("LOJA-01"), argThat(items ->
                items.size() == 2 && items.stream().anyMatch(i -> i.sku().equals("SKU-MANUAL"))), eq("comprador1"));
        verify(nfeImportRepository).save(argThat(n -> n.status() == NfeImportStatus.CONFIRMED
                && n.goodsReceiptId().equals(55L) && n.warehouseCode().equals("LOJA-01")));
    }

    @Test
    void confirmImport_mapsUnitPriceToUnitCostOfTheGoodsReceiptItem() {
        NfeImport previewed = NfeImport.of(1L, 7L, "12345678000199", null, "uuid.xml",
                NfeImportStatus.PREVIEWED, null, List.of(matchedLine(1)), "comprador1", Instant.now(), null);
        when(nfeImportRepository.findById(1L)).thenReturn(Optional.of(previewed));
        when(nfeImportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        GoodsReceipt receipt = GoodsReceipt.of(55L, 7L, "LOJA-01",
                List.of(new GoodsReceiptItem("SKU-1", BigDecimal.ONE, null, null, new BigDecimal("10.00"))),
                "comprador1", Instant.now());
        when(comprasUseCase.receiveGoods(any(), any(), any(), any())).thenReturn(receipt);

        service.confirmImport(new NfeImportConfirmCommand(1L, "LOJA-01", List.of()), "comprador1");

        verify(comprasUseCase).receiveGoods(any(), any(), argThat(items ->
                items.get(0).unitCost().compareTo(new BigDecimal("10.00")) == 0), any());
    }

    // ── V182: nota repetida, fator de embalagem e memória por fornecedor + cProd ─────────────

    private static final String KEY = "35261112345678000199550010000012341000012345";

    private static NfeImport previewed(String accessKey, NfeImportLine... lines) {
        return NfeImport.of(1L, 7L, "12345678000199", null, "uuid.xml", NfeImportStatus.PREVIEWED, null,
                List.of(lines), "comprador1", Instant.now(), null, accessKey);
    }

    @Test
    void confirmImport_chaveJaRecebida_responde409ComORecebimentoAnterior() {
        Instant antes = Instant.parse("2026-10-30T15:00:00Z");
        when(nfeImportRepository.findById(1L)).thenReturn(Optional.of(previewed(KEY, matchedLine(1))));
        when(nfeImportRepository.findConfirmedByAccessKey(KEY)).thenReturn(Optional.of(NfeImport.of(9L, 7L,
                "12345678000199", "LOJA-01", "old.xml", NfeImportStatus.CONFIRMED, 55L, List.of(matchedLine(1)),
                "comprador1", antes, antes, KEY)));

        assertThatThrownBy(() -> service.confirmImport(new NfeImportConfirmCommand(1L, "LOJA-01", List.of()),
                "comprador1"))
                .isInstanceOfSatisfying(com.cernecommerce.core.domain.exception.compras.NfeAlreadyReceivedException.class,
                        e -> {
                            assertThat(e.getGoodsReceiptId()).isEqualTo(55L);
                            assertThat(e.getReceivedAt()).isEqualTo(antes);
                            assertThat(e.getAccessKey()).isEqualTo(KEY);
                        });
        verifyNoInteractions(comprasUseCase);
    }

    @Test
    void confirmImport_fatorMultiplicaQuantidadeDivideCustoELembra() {
        com.cernecommerce.core.ports.out.compras.SupplierProductMappingRepository mappings =
                mock(com.cernecommerce.core.ports.out.compras.SupplierProductMappingRepository.class);
        service = new NfeImportService(nfeXmlImportPort, supplierRepository, nfeImportStoragePort,
                nfeImportRepository, comprasUseCase, mappings);
        // 2 CX a R$ 36,00, sem EAN: o operador diz o SKU e que cada caixa tem 12.
        NfeImportLine caixa = NfeImportLine.fromXml(1, "FORN-SEDA", null, "Seda caixa 12", new BigDecimal("2"),
                new BigDecimal("36.00"), null, null, null, "CX", "UN", new BigDecimal("24"));
        when(nfeImportRepository.findById(1L)).thenReturn(Optional.of(previewed(KEY, caixa)));
        when(nfeImportRepository.findConfirmedByAccessKey(KEY)).thenReturn(Optional.empty());
        when(comprasUseCase.receiveGoods(eq(7L), eq("LOJA-01"), any(), eq("comprador1")))
                .thenReturn(new GoodsReceipt(30L, 7L, "LOJA-01", List.of(new GoodsReceiptItem("SEDA", new BigDecimal("24"),
                        null, null, new BigDecimal("3.00"))), "comprador1", Instant.now()));

        service.confirmImport(new NfeImportConfirmCommand(1L, "LOJA-01",
                List.of(new LineOverride(1, "SEDA", 12))), "comprador1");

        verify(comprasUseCase).receiveGoods(eq(7L), eq("LOJA-01"), argThat(items -> items.size() == 1
                && items.get(0).sku().equals("SEDA")
                && items.get(0).quantity().compareTo(new BigDecimal("24")) == 0
                && items.get(0).unitCost().compareTo(new BigDecimal("3.00")) == 0), eq("comprador1"));
        verify(mappings).upsert(eq(new com.cernecommerce.core.domain.model.compras.SupplierProductMapping(7L,
                "FORN-SEDA", "SEDA", 12)), eq("comprador1"));
        verify(nfeImportRepository).save(argThat(n -> n.status() == NfeImportStatus.CONFIRMED
                && KEY.equals(n.accessKey()) && n.lines().get(0).unitsPerPackage() == 12));
    }

    @Test
    void previewImport_aplicaFatorESkuLembradosDoFornecedor() {
        com.cernecommerce.core.ports.out.compras.SupplierProductMappingRepository mappings =
                mock(com.cernecommerce.core.ports.out.compras.SupplierProductMappingRepository.class);
        service = new NfeImportService(nfeXmlImportPort, supplierRepository, nfeImportStoragePort,
                nfeImportRepository, comprasUseCase, mappings);
        NfeImportLine semEan = NfeImportLine.fromXml(1, "FORN-SEDA", null, "Seda caixa 12", new BigDecimal("2"),
                new BigDecimal("36.00"), null, null, null, "CX", null, null);
        when(nfeXmlImportPort.parse(XML_BYTES)).thenReturn(new NfeImportPreview("12345678000199", List.of(semEan), KEY));
        when(nfeImportStoragePort.save(any(), any())).thenReturn("uuid.xml");
        when(supplierRepository.findByTaxId("12345678000199")).thenReturn(Optional.of(supplier()));
        when(mappings.findBySupplierAndCodes(eq(7L), any())).thenReturn(java.util.Map.of("FORN-SEDA",
                new com.cernecommerce.core.domain.model.compras.SupplierProductMapping(7L, "FORN-SEDA", "SEDA", 12)));
        when(nfeImportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NfeImport result = service.previewImport(XML_BYTES, "comprador1");

        assertThat(result.accessKey()).isEqualTo(KEY);
        NfeImportLine line = result.lines().get(0);
        assertThat(line.unitsPerPackage()).isEqualTo(12);
        assertThat(line.matchStatus()).isEqualTo(NfeImportLine.MatchStatus.MATCHED);
        assertThat(line.matchedSku()).isEqualTo("SEDA");
    }
}
