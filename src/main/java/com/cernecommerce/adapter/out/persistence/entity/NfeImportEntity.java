package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Auditoria de uma importação de NF-e (EST-F005). Tabela {@code nfe_import}.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "nfe_import")
public class NfeImportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** Nulo apenas quando {@code status} é REJECTED — fornecedor nunca foi encontrado. */
    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "emitter_cnpj", nullable = false, length = 20)
    private String emitterCnpj;

    /** Só existe depois da confirmação — a NF-e não diz o depósito de destino. */
    @Column(name = "warehouse_code", length = 50)
    private String warehouseCode;

    @Column(name = "file_reference", nullable = false, length = 255)
    private String fileReference;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "goods_receipt_id")
    private Long goodsReceiptId;

    @Column(name = "uploaded_by", nullable = false, length = 80)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** V182 — chave de acesso da NF-e (44 dígitos). Nula em imports anteriores. */
    @Column(name = "access_key", length = 44)
    private String accessKey;

    @OneToMany(mappedBy = "nfeImport", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<NfeImportLineEntity> lines = new ArrayList<>();
}
