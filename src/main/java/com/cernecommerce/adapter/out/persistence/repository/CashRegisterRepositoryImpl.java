package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashRegisterSessionEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import java.util.List;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSessionFilter;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional
public class CashRegisterRepositoryImpl implements CashRegisterRepository {

    private final CashRegisterSessionJpaRepository cashRegisterSessionJpaRepository;

    public CashRegisterRepositoryImpl(CashRegisterSessionJpaRepository cashRegisterSessionJpaRepository) {
        this.cashRegisterSessionJpaRepository = cashRegisterSessionJpaRepository;
    }

    /**
     * PDV-C013 — {@code PageRequest.of(page, size)} vinha <b>sem {@code Sort}</b>. Paginação sem
     * {@code ORDER BY} não tem ordem determinística: o Postgres pode devolver a mesma sessão em
     * duas páginas e omitir outra, e o cliente nunca saberia. É a mesma armadilha que EST-C012
     * corrigiu no ledger de estoque.
     *
     * <p>Ordena por {@code id DESC} — chave única e monotônica, então o desempate é dispensável
     * (ao contrário do ledger, onde {@code created_at} repetia dentro da mesma transação). Mais
     * recentes primeiro, como {@code /pdv/sessions/&#123;id&#125;/sales} e a listagem de mesas.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<CashRegisterSession> findAll(int page, int size) {
        Page<CashRegisterSessionEntity> result = cashRegisterSessionJpaRepository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    /**
     * PDV-F026 — filtros por status, operador e período de abertura. Specification em vez de
     * {@code :x IS NULL OR}, pela mesma razão de {@code OrderRepositoryImpl.findAll}: o Postgres não
     * infere o tipo de um {@code Instant} nulo. Ordena por {@code openedAt DESC} (a aba Caixas lê
     * por data), com {@code id DESC} de desempate para a paginação continuar determinística
     * (PDV-C013).
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<CashRegisterSession> findAll(CashRegisterSessionFilter filter, int page, int size) {
        CashRegisterSessionFilter f = filter == null ? CashRegisterSessionFilter.none() : filter;
        Specification<CashRegisterSessionEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.status()   != null) predicates.add(cb.equal(root.get("status"), f.status().name()));
            if (f.operator() != null) predicates.add(cb.equal(root.get("operator"), f.operator()));
            if (f.from()     != null) predicates.add(cb.greaterThanOrEqualTo(root.get("openedAt"), f.from()));
            if (f.to()       != null) predicates.add(cb.lessThanOrEqualTo(root.get("openedAt"), f.to()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<CashRegisterSessionEntity> result = cashRegisterSessionJpaRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("openedAt"), Sort.Order.desc("id"))));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashRegisterSession> findOpenByOperator(String operator) {
        return cashRegisterSessionJpaRepository
                .findByOperatorAndStatus(operator, CashRegisterSession.Status.OPEN.name())
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashRegisterSession> findById(Long id) {
        return cashRegisterSessionJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findOperatorsByIds(Collection<Long> ids) {
        Map<Long, String> operators = new HashMap<>();
        if (ids.isEmpty()) {
            return operators;
        }
        for (Object[] row : cashRegisterSessionJpaRepository.findOperatorsByIds(ids)) {
            operators.put((Long) row[0], (String) row[1]);
        }
        return operators;
    }

    @Override
    public CashRegisterSession save(CashRegisterSession session) {
        CashRegisterSessionEntity entity = session.id() == null
                ? new CashRegisterSessionEntity()
                : cashRegisterSessionJpaRepository.findById(session.id())
                        .orElseGet(CashRegisterSessionEntity::new);
        entity.setId(session.id());
        entity.setOperator(session.operator());
        entity.setOpenedAt(session.openedAt());
        entity.setOpeningAmount(session.openingAmount());
        entity.setWarehouseCode(session.warehouseCode());
        entity.setClosedAt(session.closedAt());
        entity.setClosedBy(session.closedBy());
        entity.setExpectedAmount(session.expectedAmount());
        entity.setCountedAmount(session.countedAmount());
        entity.setDifferenceAmount(session.differenceAmount());
        entity.setStatus(session.status().name());
        entity.setClosingNotes(session.closingNotes());
        return toDomain(cashRegisterSessionJpaRepository.save(entity));
    }

    private CashRegisterSession toDomain(CashRegisterSessionEntity e) {
        return CashRegisterSession.of(e.getId(), e.getOperator(), e.getOpenedAt(), e.getOpeningAmount(),
                e.getWarehouseCode(), e.getClosedAt(), e.getClosedBy(), e.getExpectedAmount(),
                e.getCountedAmount(), e.getDifferenceAmount(),
                CashRegisterSession.Status.valueOf(e.getStatus()), e.getClosingNotes());
    }
}
