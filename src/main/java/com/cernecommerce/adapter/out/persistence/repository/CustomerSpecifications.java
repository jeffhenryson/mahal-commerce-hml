package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerEntity;
import com.cernecommerce.adapter.out.persistence.entity.CustomerTagEntity;
import com.cernecommerce.core.domain.model.crm.CustomerFilter;
import com.cernecommerce.core.domain.model.crm.CustomerIdentifiers;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lista de clientes com filtro (pendência 7). Specification e não {@code @Query} pelo mesmo motivo
 * de EST-C018: cada filtro é opcional, e o padrão {@code :p IS NULL OR ...} quebra no Postgres com
 * parâmetro nulo sem tipo (foi o 500 de CRM-C008). Aqui o predicado só existe quando o filtro veio.
 */
final class CustomerSpecifications {

    private CustomerSpecifications() {
    }

    static Specification<CustomerEntity> of(CustomerFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            if (filter.search() != null) {
                and.add(search(root, cb, filter.search()));
            }
            if (!filter.estagios().isEmpty()) {
                and.add(root.get("estagio").in(filter.estagios()));
            }
            if (!filter.segmentos().isEmpty()) {
                and.add(root.get("segmento").in(filter.segmentos()));
            }
            if (!filter.origens().isEmpty()) {
                // Origem é texto livre digitado no cadastro: compara sem caixa.
                and.add(cb.lower(root.get("origem")).in(filter.origens().stream()
                        .map(o -> o.toLowerCase(Locale.ROOT)).toList()));
            }
            if (!filter.tagIds().isEmpty()) {
                Subquery<Long> tagged = query.subquery(Long.class);
                Root<CustomerTagEntity> ct = tagged.from(CustomerTagEntity.class);
                tagged.select(ct.get("customerId"))
                        .where(cb.equal(ct.get("customerId"), root.get("id")), ct.get("tagId").in(filter.tagIds()));
                and.add(cb.exists(tagged));
            }
            return cb.and(and.toArray(Predicate[]::new));
        };
    }

    /** Ordem pedida e o id como desempate, para a paginação ser estável. */
    static Sort sort(CustomerFilter filter) {
        Sort.Direction direction = filter.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(new Sort.Order(direction, filter.sort()), new Sort.Order(direction, "id"));
    }

    /** Mesmo critério de CustomerJpaRepository.SEARCH_TEXT: nome/email sem acento, contato e CPF. */
    private static Predicate search(Root<CustomerEntity> root, CriteriaBuilder cb, String search) {
        String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
        Expression<String> term = unaccent(cb, cb.literal(pattern));
        List<Predicate> or = new ArrayList<>();
        or.add(cb.like(unaccent(cb, cb.lower(root.get("nome"))), term));
        or.add(cb.like(unaccent(cb, cb.lower(root.get("email"))), term));
        or.add(cb.like(root.get("contato"), "%" + search + "%"));
        String digits = CustomerIdentifiers.digitsOrNull(search);
        if (digits != null && digits.length() >= 3) {
            or.add(cb.like(root.get("cpf"), "%" + digits + "%"));
        }
        return cb.or(or.toArray(Predicate[]::new));
    }

    private static Expression<String> unaccent(CriteriaBuilder cb, Expression<String> value) {
        return cb.function("unaccent", String.class, value);
    }
}
