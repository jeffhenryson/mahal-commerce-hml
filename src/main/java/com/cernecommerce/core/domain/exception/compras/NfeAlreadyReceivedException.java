package com.cernecommerce.core.domain.exception.compras;

import java.time.Instant;

/**
 * V182 — a nota com esta chave de acesso já foi recebida. Receber de novo dobraria o estoque e o
 * custo; o operador precisa ver qual recebimento já a contém.
 */
public class NfeAlreadyReceivedException extends RuntimeException {

    private final String accessKey;
    private final Long goodsReceiptId;
    private final Instant receivedAt;

    public NfeAlreadyReceivedException(String accessKey, Long goodsReceiptId, Instant receivedAt) {
        super("Esta NF-e já foi recebida (recebimento #" + goodsReceiptId + ")");
        this.accessKey = accessKey;
        this.goodsReceiptId = goodsReceiptId;
        this.receivedAt = receivedAt;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public Long getGoodsReceiptId() {
        return goodsReceiptId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
