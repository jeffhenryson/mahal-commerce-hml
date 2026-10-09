package com.cernecommerce.core.domain.model.pdv;

/** Como uma venda offline recusada saiu da revisão (PDV-F043). */
public enum OfflineRejectionResolution {
    /** Reenviada depois do acerto e registrada — virou pedido. */
    RETRIED,
    /** Descartada com motivo: o dinheiro foi acertado fora do sistema. */
    DISCARDED
}
