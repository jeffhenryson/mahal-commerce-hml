package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class NfeImportResponseDTO {

    private Long id;

    @Schema(description = "Nulo quando status=REJECTED — CNPJ do emitente não encontrado.")
    private Long supplierId;

    private String emitterCnpj;
    private String warehouseCode;
    private String status;
    private Long goodsReceiptId;
    private List<NfeImportLineResponseDTO> lines;
    private String uploadedBy;
    private Instant uploadedAt;
    private Instant confirmedAt;

    @io.swagger.v3.oas.annotations.media.Schema(description = "V182 — chave de acesso da NF-e (44 dígitos), lida "
            + "de protNFe/infProt/chNFe ou de infNFe@Id. Nula se a nota não trouxer uma chave válida (aí não há "
            + "trava de nota repetida).", nullable = true)
    private String accessKey;
}
