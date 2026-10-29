package com.cernecommerce.core.ports.out.ecommerce;

import com.cernecommerce.core.domain.model.ecommerce.CentralPedidosConfig;

import java.util.Optional;

/** Onde a configuração do recebimento da Central de Pedidos fica guardada (ECM-F013). */
public interface CentralPedidosConfigRepository {

    /** Vazio enquanto a loja nunca salvou. */
    Optional<CentralPedidosConfig> find();

    CentralPedidosConfig save(CentralPedidosConfig config, String updatedBy);

    /** A configuração em vigor: a salva, ou o padrão. */
    default CentralPedidosConfig atual() {
        return find().orElseGet(CentralPedidosConfig::padrao);
    }
}
