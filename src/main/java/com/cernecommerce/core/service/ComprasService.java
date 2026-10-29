package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.compras.DuplicateSupplierTaxIdException;
import com.cernecommerce.core.domain.exception.compras.SupplierNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.compras.GoodsReceipt;
import com.cernecommerce.core.domain.model.compras.GoodsReceiptItem;
import com.cernecommerce.core.domain.model.compras.Supplier;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.ports.in.ComprasUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.out.compras.GoodsReceiptRepository;
import com.cernecommerce.core.ports.out.compras.SupplierRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public class ComprasService implements ComprasUseCase {

    private final SupplierRepository supplierRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final EstoqueUseCase estoqueUseCase;

    public ComprasService(SupplierRepository supplierRepository, GoodsReceiptRepository goodsReceiptRepository,
            EstoqueUseCase estoqueUseCase) {
        this.supplierRepository = supplierRepository;
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.estoqueUseCase = estoqueUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Supplier> listSuppliers(int page, int size) {
        return supplierRepository.findAll(page, size);
    }

    @Override
    @Transactional
    public Supplier registerSupplier(String legalName, String taxId, String email) {
        // Supplier.create já normaliza o taxId; a busca de duplicidade tem que usar o valor
        // NORMALIZADO, senão "12.345.678/0001-99" passaria por cima de "12345678000199" e a
        // uk_supplier_tax_id transformaria o erro do usuário num 409 genérico do driver.
        Supplier candidate = Supplier.create(legalName, taxId, email);
        supplierRepository.findByTaxId(candidate.taxId()).ifPresent(existing -> {
            throw new DuplicateSupplierTaxIdException(candidate.taxId());
        });
        return supplierRepository.save(candidate);
    }

    @Override
    @Transactional
    public Supplier updateSupplier(Long id, String legalName, String email) {
        Supplier current = supplierRepository.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException(id));
        return supplierRepository.save(current.updatedWith(legalName, email));
    }

    @Override
    @Transactional
    public Supplier setSupplierActive(Long id, boolean active) {
        Supplier current = supplierRepository.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException(id));
        return supplierRepository.save(current.withActive(active));
    }

    @Override
    @Transactional
    public GoodsReceipt receiveGoods(Long supplierId, String warehouseCode, List<GoodsReceiptItem> items,
            String username) {
        supplierRepository.findById(supplierId)
                .orElseThrow(() -> new SupplierNotFoundException(supplierId));
        // EST-F054 — produto excluído não recebe mercadoria (cobre a NF-e, que chega por aqui).
        // Checado antes de gravar o recebimento, para a recusa não deixar nada pela metade.
        for (GoodsReceiptItem item : items) {
            estoqueUseCase.requireNotArchived(item.sku());
        }

        // Salva o recebimento ANTES do loop de itens, para ter o id disponível e linkar cada
        // ENTRADA de volta a ele (item 2 do pedido do frontend — histórico de compras por SKU
        // precisa saber de qual fornecedor/recebimento veio a entrada). Mesma transação, então um
        // adjustStock que falhar no meio do loop desfaz o recebimento junto.
        GoodsReceipt receipt = goodsReceiptRepository.save(GoodsReceipt.create(supplierId, warehouseCode, items,
                username));

        for (GoodsReceiptItem item : items) {
            // Sempre a sobrecarga de 10 argumentos (EST-F007/EST-F008 + vínculo de recebimento):
            // para SKU não lote-rastreado ou sem custo conhecido, lotCode/expiryDate/unitCost
            // chegam nulos e o comportamento é idêntico ao overload antigo.
            estoqueUseCase.adjustStock(item.sku(), warehouseCode, MovementType.ENTRADA, item.quantity(),
                    "Recebimento de mercadoria - fornecedor #" + supplierId, username,
                    item.lotCode(), item.expiryDate(), item.unitCost(), receipt.id());
        }

        return receipt;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GoodsReceipt> findGoodsReceiptById(Long id) {
        return goodsReceiptRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Supplier> findSupplierById(Long id) {
        return supplierRepository.findById(id);
    }
}
