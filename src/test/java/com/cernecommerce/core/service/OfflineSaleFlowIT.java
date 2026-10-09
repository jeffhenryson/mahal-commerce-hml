package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.SessionHasPendingOfflineSalesException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.OfflineRejectionResolution;
import com.cernecommerce.core.domain.model.pdv.OfflineSaleItem;
import com.cernecommerce.core.domain.model.pdv.OfflineSalePayment;
import com.cernecommerce.core.domain.model.pdv.OfflineSaleRejection;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.OfflineSaleUseCase;
import com.cernecommerce.core.ports.in.OfflineSaleUseCase.OfflineSaleCommand;
import com.cernecommerce.core.ports.in.OfflineSaleUseCase.SyncResult;
import com.cernecommerce.core.ports.in.OfflineSaleUseCase.SyncStatus;
import com.cernecommerce.core.ports.in.PdvUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDV-F043 ponta a ponta: a fila offline chega, uma venda entra e a outra fica em revisão por falta
 * de estoque; o caixa não fecha; o gerente dá entrada, reenvia, e o caixa fecha.
 *
 * <p>Sem {@code @Transactional} de classe: cada venda do lote roda na sua própria transação, e é
 * isso que o teste prova — a recusada não derruba a que entrou. Dados com sufixo único.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OfflineSaleFlowIT {

    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired PdvUseCase pdvUseCase;
    @Autowired OfflineSaleUseCase offlineSaleUseCase;

    private static OfflineSaleCommand venda(String sku, Instant soldAt) {
        return new OfflineSaleCommand(UUID.randomUUID().toString(), soldAt, null,
                List.of(new OfflineSaleItem(sku, BigDecimal.ONE, null, null)),
                List.of(new OfflineSalePayment(PaymentMethod.DINHEIRO, new BigDecimal("10.00"), null)));
    }

    @Test
    void filaOffline_recusaPorEstoque_bloqueiaOFechamento_eORetryLibera() {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String warehouse = "OFF-WH-" + suffix;
        String sku = "OFF-SKU-" + suffix;
        String operator = "caixa-" + suffix;
        estoqueUseCase.createWarehouse(warehouse, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Isqueiro " + suffix, "Isqueiros", List.of(),
                Pricing.of(new BigDecimal("5.00"), null, new BigDecimal("10.00")));
        estoqueUseCase.adjustStock(sku, warehouse, MovementType.ENTRADA, BigDecimal.ONE, "carga", operator);
        CashRegisterSession caixa = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouse);

        // Horários dentro do caixa: depois da abertura.
        List<SyncResult> results = offlineSaleUseCase.sync(caixa.id(),
                List.of(venda(sku, caixa.openedAt().plusMillis(1)), venda(sku, caixa.openedAt().plusMillis(2))),
                operator);

        assertThat(results).extracting(SyncResult::status).containsExactly(SyncStatus.SYNCED, SyncStatus.REJECTED);
        assertThat(results.get(1).errorCode()).isEqualTo("INSUFFICIENT_STOCK");
        Long rejectionId = results.get(1).rejectionId();

        assertThat(offlineSaleUseCase.listRejections(caixa.id())).singleElement()
                .satisfies(r -> assertThat(r.isPending()).isTrue());

        assertThatThrownBy(() -> pdvUseCase.closeSession(caixa.id(), new BigDecimal("20.00"), null, operator, false))
                .isInstanceOf(SessionHasPendingOfflineSalesException.class);

        // O gerente dá entrada no que faltou e reenvia.
        estoqueUseCase.adjustStock(sku, warehouse, MovementType.ENTRADA, BigDecimal.ONE, "acerto", "gerente");
        OfflineSaleRejection resolvida = offlineSaleUseCase.retry(rejectionId, "gerente");

        assertThat(resolvida.resolution()).isEqualTo(OfflineRejectionResolution.RETRIED);
        assertThat(resolvida.orderId()).isNotNull();
        assertThat(estoqueUseCase.getStockBalance(sku, warehouse).quantity()).isEqualByComparingTo("0");

        CashRegisterSession fechado = pdvUseCase.closeSession(caixa.id(), new BigDecimal("20.00"), null, operator,
                false);
        assertThat(fechado.expectedAmount()).isEqualByComparingTo("20.00");
    }

    /** Mesma venda chegando duas vezes (a fila repetiu): a segunda é DUPLICATE, e só um pedido existe. */
    @Test
    void reenvioDaMesmaVenda_eDuplicada() {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String warehouse = "OFF-WH2-" + suffix;
        String sku = "OFF-SKU2-" + suffix;
        String operator = "caixa2-" + suffix;
        estoqueUseCase.createWarehouse(warehouse, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Seda " + suffix, "Sedas", List.of(),
                Pricing.of(new BigDecimal("5.00"), null, new BigDecimal("10.00")));
        estoqueUseCase.adjustStock(sku, warehouse, MovementType.ENTRADA, new BigDecimal("5"), "carga", operator);
        CashRegisterSession caixa = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouse);
        OfflineSaleCommand venda = venda(sku, caixa.openedAt().plusMillis(1));

        SyncResult primeira = offlineSaleUseCase.sync(caixa.id(), List.of(venda), operator).get(0);
        SyncResult segunda = offlineSaleUseCase.sync(caixa.id(), List.of(venda), operator).get(0);

        assertThat(primeira.status()).isEqualTo(SyncStatus.SYNCED);
        assertThat(segunda.status()).isEqualTo(SyncStatus.DUPLICATE);
        assertThat(segunda.orderId()).isEqualTo(primeira.orderId());
        assertThat(estoqueUseCase.getStockBalance(sku, warehouse).quantity()).isEqualByComparingTo("4");
    }
}
