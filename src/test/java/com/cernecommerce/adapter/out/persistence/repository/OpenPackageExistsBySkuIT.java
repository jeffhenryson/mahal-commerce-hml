package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.model.estoque.OpenPackageCloseReason;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.out.estoque.OpenPackageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code existsBySku} é a régua de quem apaga produto ou variação: precisa enxergar a lata aberta
 * <b>e</b> a fechada. A fechada é o histórico e o rendimento do sabor; a aberta cadastrada como
 * "já aberta", sem baixa, não deixa saldo nem movimento — só esta tabela sabe dela.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class OpenPackageExistsBySkuIT {

    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired OpenPackageRepository openPackageRepository;

    @Test
    void enxergaLataAbertaELataFechada_eNaoInventaLataParaOutroSku() {
        String s = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String sku = "ZOMO-MENTA-" + s;
        Warehouse deposito = estoqueUseCase.createWarehouse("SALAO-" + s, "Salão " + s, WarehouseType.LOJA_FISICA);

        assertThat(openPackageRepository.existsBySku(sku)).isFalse();

        // Lata "já aberta", sem baixa: nenhum saldo, nenhum movimento.
        OpenPackage aberta = openPackageRepository.saveAndFlush(
                OpenPackage.registered(sku, deposito.id(), 5, 2, "atendente-" + s, Instant.now()));
        assertThat(openPackageRepository.existsBySku(sku)).isTrue();

        openPackageRepository.saveAndFlush(aberta.closed(OpenPackageCloseReason.FINISHED, Instant.now()));
        assertThat(openPackageRepository.existsBySku(sku)).isTrue();

        assertThat(openPackageRepository.existsBySku("OUTRO-" + s)).isFalse();
    }
}
