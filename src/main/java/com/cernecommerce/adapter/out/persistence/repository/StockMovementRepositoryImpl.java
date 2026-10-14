package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockMovementEntity;
import com.cernecommerce.core.domain.model.estoque.AbcAnalysis;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.ports.out.estoque.StockMovementRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
@Transactional
public class StockMovementRepositoryImpl implements StockMovementRepository {

    private final StockMovementJpaRepository stockMovementJpaRepository;

    public StockMovementRepositoryImpl(StockMovementJpaRepository stockMovementJpaRepository) {
        this.stockMovementJpaRepository = stockMovementJpaRepository;
    }

    @Override
    public StockMovement save(StockMovement movement) {
        StockMovementEntity entity = new StockMovementEntity();
        entity.setId(movement.id());
        entity.setSku(movement.sku());
        entity.setWarehouseId(movement.warehouseId());
        entity.setType(movement.type().name());
        entity.setQuantity(movement.quantity());
        entity.setReason(movement.reason());
        entity.setUsername(movement.username());
        entity.setCreatedAt(movement.createdAt());
        entity.setLotCode(movement.lotCode());
        entity.setUnitCost(movement.unitCost());
        entity.setGoodsReceiptId(movement.goodsReceiptId());
        entity.setCounterpartWarehouseId(movement.counterpartWarehouseId());
        return toDomain(stockMovementJpaRepository.save(entity));
    }

    /**
     * Listagem filtrada do ledger. Cada filtro é opcional e resolvido com uma {@link Specification} —
     * o predicado só entra quando o valor não é nulo, então um filtro ausente nunca vira um bind
     * ambíguo no Postgres.
     *
     * <p><b>EST-C018.</b> Era um {@code @Query} com o padrão {@code :param IS NULL OR ...}, e isso
     * fazia o Postgres real recusar inferir o tipo do bind de {@code from}/{@code to}
     * ({@code Instant}) quando vinham nulos — {@code could not determine data type of parameter $7} —,
     * respondendo 500 em <b>toda</b> chamada de {@code GET /estoque/movements}, com ou sem filtro. O
     * CAST explícito que corrigiria isso tem um bug conhecido de interação Hibernate/pgjdbc que troca
     * o tipo do parâmetro por {@code bytea}; Specification evita a classe inteira do problema. Mesmo
     * caminho já tomado por {@code OrderRepositoryImpl.findAll} e {@code AuditLogRepositoryImpl
     * .findFiltered}.</p>
     *
     * <p>O desempate por {@code id} não é cosmético (EST-C012): uma venda com N itens grava N
     * movimentos no mesmo loop e na mesma transação, com {@code created_at} idêntico. Ordenar só por
     * {@code created_at} deixa a chave não-única, e a paginação fica instável — a mesma linha pode
     * voltar em duas páginas ou não aparecer em nenhuma. {@code id} é BIGSERIAL monotônico, então dá
     * ordem total e determinística.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<StockMovement> findBySkuAndWarehouseId(String sku, Long warehouseId, MovementType type,
            Instant from, Instant to, int page, int size) {
        Specification<StockMovementEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sku         != null) predicates.add(cb.equal(root.get("sku"), sku));
            if (warehouseId != null) predicates.add(cb.equal(root.get("warehouseId"), warehouseId));
            if (type        != null) predicates.add(cb.equal(root.get("type"), type.name()));
            if (from        != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to          != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<StockMovementEntity> result = stockMovementJpaRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(
                        Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockMovement> findEntradasBySkuAndWarehouseId(String sku, Long warehouseId, int page,
            int size) {
        Page<StockMovementEntity> result = stockMovementJpaRepository
                .searchEntradas(sku, warehouseId, PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySku(String sku) {
        return stockMovementJpaRepository.existsBySku(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AbcAnalysis.ConsumptionLine> findConsumptionByPeriod(Long warehouseId, Instant from,
            Instant to) {
        return stockMovementJpaRepository.findConsumptionByPeriod(warehouseId, from, to).stream()
                .map(p -> new AbcAnalysis.ConsumptionLine(p.getSku(), p.getProductName(),
                        p.getConsumedQuantity(), p.getConsumedValue(), p.getCurrentBalance()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransferredLine> transferredInto(Long warehouseId, Instant from, Instant to) {
        return stockMovementJpaRepository.transferredInto(warehouseId, from, to).stream()
                .map(row -> new TransferredLine((String) row[0], (BigDecimal) row[1], (BigDecimal) row[2]))
                .toList();
    }

    private StockMovement toDomain(StockMovementEntity e) {
        return StockMovement.of(e.getId(), e.getSku(), e.getWarehouseId(), MovementType.valueOf(e.getType()),
                e.getQuantity(), e.getReason(), e.getUsername(), e.getCreatedAt(), e.getLotCode(),
                e.getUnitCost(), e.getGoodsReceiptId(), e.getCounterpartWarehouseId());
    }
}
