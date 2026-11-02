package com.cernecommerce.core.domain.model.compras;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Auditoria de uma importação de NF-e (EST-F005): o XML bruto persistido (via
 * {@code NfeImportStoragePort}), o fornecedor resolvido por CNPJ, as linhas parseadas e — só após
 * a confirmação — o {@code GoodsReceipt} gerado.
 *
 * <h2>Por que existe — não é só o resultado do parsing</h2>
 * <p>O preview e a confirmação são requisições HTTP separadas, e o parsing não é barato de repetir
 * (nem confiável: o operador pode não ter mais o arquivo à mão para reenviar). As linhas parseadas
 * — incluindo o casamento automático por EAN — ficam persistidas aqui entre as duas chamadas.</p>
 *
 * @param supplierId fornecedor resolvido por CNPJ. {@code null} apenas quando {@code status} é
 *        {@code REJECTED} — o CNPJ do emitente não bateu com nenhum fornecedor cadastrado.
 * @param warehouseCode depósito de destino. Só existe depois da confirmação — a NF-e não diz para
 *        qual depósito a mercadoria vai, isso é decisão do operador no fechamento.
 * @param fileReference referência do XML bruto no {@code FileStoragePort}, para auditoria/disputa
 *        com o fornecedor. Sem endpoint de leitura pública, diferente de imagem de produto.
 * @param goodsReceiptId recebimento gerado. Só existe depois da confirmação.
 */
public record NfeImport(
        Long id,
        Long supplierId,
        String emitterCnpj,
        String warehouseCode,
        String fileReference,
        NfeImportStatus status,
        Long goodsReceiptId,
        List<NfeImportLine> lines,
        String uploadedBy,
        Instant uploadedAt,
        Instant confirmedAt,
        String accessKey) {

    public NfeImport {
        if (emitterCnpj == null || emitterCnpj.isBlank()) {
            throw new IllegalArgumentException("emitterCnpj é obrigatório");
        }
        if (fileReference == null || fileReference.isBlank()) {
            throw new IllegalArgumentException("fileReference é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines não pode ser vazio");
        }
        lines = List.copyOf(lines);
        if (uploadedBy == null || uploadedBy.isBlank()) {
            throw new IllegalArgumentException("uploadedBy é obrigatório");
        }
        if (uploadedAt == null) {
            throw new IllegalArgumentException("uploadedAt é obrigatório");
        }
        // Espelha o CHECK ck_nfe_import_status_consistency da V106.
        switch (status) {
            case PREVIEWED -> {
                if (supplierId == null) {
                    throw new IllegalArgumentException("PREVIEWED exige supplierId");
                }
                if (confirmedAt != null || goodsReceiptId != null) {
                    throw new IllegalArgumentException("PREVIEWED não pode ter confirmedAt nem goodsReceiptId");
                }
            }
            case CONFIRMED -> {
                if (supplierId == null || warehouseCode == null || warehouseCode.isBlank()
                        || goodsReceiptId == null || confirmedAt == null) {
                    throw new IllegalArgumentException(
                            "CONFIRMED exige supplierId, warehouseCode, goodsReceiptId e confirmedAt");
                }
            }
            case REJECTED -> {
                if (supplierId != null) {
                    throw new IllegalArgumentException(
                            "REJECTED não pode ter supplierId — fornecedor nunca foi encontrado");
                }
                if (goodsReceiptId != null || confirmedAt != null) {
                    throw new IllegalArgumentException("REJECTED não pode ter goodsReceiptId nem confirmedAt");
                }
            }
        }
    }

    /** Preview aceito: fornecedor encontrado, aguardando confirmação do operador. */
    public static NfeImport previewed(Long supplierId, String emitterCnpj, String fileReference,
            List<NfeImportLine> lines, String uploadedBy) {
        return previewed(supplierId, emitterCnpj, fileReference, lines, uploadedBy, null);
    }

    /** Com a chave de acesso da nota (V182). */
    public static NfeImport previewed(Long supplierId, String emitterCnpj, String fileReference,
            List<NfeImportLine> lines, String uploadedBy, String accessKey) {
        return new NfeImport(null, supplierId, emitterCnpj, null, fileReference, NfeImportStatus.PREVIEWED,
                null, lines, uploadedBy, Instant.now(), null, accessKey);
    }

    /** Preview rejeitado: o CNPJ do emitente não bate com nenhum fornecedor cadastrado. */
    public static NfeImport rejected(String emitterCnpj, String fileReference, List<NfeImportLine> lines,
            String uploadedBy) {
        return rejected(emitterCnpj, fileReference, lines, uploadedBy, null);
    }

    /** Com a chave de acesso da nota (V182). */
    public static NfeImport rejected(String emitterCnpj, String fileReference, List<NfeImportLine> lines,
            String uploadedBy, String accessKey) {
        return new NfeImport(null, null, emitterCnpj, null, fileReference, NfeImportStatus.REJECTED,
                null, lines, uploadedBy, Instant.now(), null, accessKey);
    }

    /** Reconstitui a partir de persistência. */
    public static NfeImport of(Long id, Long supplierId, String emitterCnpj, String warehouseCode,
            String fileReference, NfeImportStatus status, Long goodsReceiptId, List<NfeImportLine> lines,
            String uploadedBy, Instant uploadedAt, Instant confirmedAt) {
        return of(id, supplierId, emitterCnpj, warehouseCode, fileReference, status, goodsReceiptId, lines,
                uploadedBy, uploadedAt, confirmedAt, null);
    }

    /** Reconstitui com a chave de acesso (V182). */
    public static NfeImport of(Long id, Long supplierId, String emitterCnpj, String warehouseCode,
            String fileReference, NfeImportStatus status, Long goodsReceiptId, List<NfeImportLine> lines,
            String uploadedBy, Instant uploadedAt, Instant confirmedAt, String accessKey) {
        return new NfeImport(id, supplierId, emitterCnpj, warehouseCode, fileReference, status, goodsReceiptId,
                lines, uploadedBy, uploadedAt, confirmedAt, accessKey);
    }

    /**
     * Substitui as linhas — usado no fechamento para gravar os overrides manuais aplicados antes
     * de confirmar. Cópia; não muta a instância original.
     */
    public NfeImport withLines(List<NfeImportLine> newLines) {
        return new NfeImport(id, supplierId, emitterCnpj, warehouseCode, fileReference, status, goodsReceiptId,
                newLines, uploadedBy, uploadedAt, confirmedAt, accessKey);
    }

    /**
     * Confirma o import: vincula o depósito de destino e o recebimento gerado.
     *
     * @throws IllegalStateException se o status não for {@code PREVIEWED} — rede de segurança do
     *         domínio; o service checa isso antes, com erro tipado (409)
     */
    public NfeImport confirmed(String warehouseCode, Long goodsReceiptId, Instant confirmedAt) {
        if (status != NfeImportStatus.PREVIEWED) {
            throw new IllegalStateException("nfe_import " + id + " não está aguardando confirmação: " + status);
        }
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new IllegalArgumentException("warehouseCode é obrigatório ao confirmar");
        }
        if (goodsReceiptId == null) {
            throw new IllegalArgumentException("goodsReceiptId é obrigatório ao confirmar");
        }
        if (confirmedAt == null) {
            throw new IllegalArgumentException("confirmedAt é obrigatório ao confirmar");
        }
        return new NfeImport(id, supplierId, emitterCnpj, warehouseCode, fileReference,
                NfeImportStatus.CONFIRMED, goodsReceiptId, lines, uploadedBy, uploadedAt, confirmedAt, accessKey);
    }

    /** Linhas ainda sem SKU resolvido — o que falta para poder confirmar. */
    public List<NfeImportLine> unmatchedLines() {
        List<NfeImportLine> unmatched = new ArrayList<>();
        for (NfeImportLine line : lines) {
            if (line.matchStatus() == NfeImportLine.MatchStatus.UNMATCHED) {
                unmatched.add(line);
            }
        }
        return unmatched;
    }
}
