package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerIdentifiers;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional
public class CustomerRepositoryImpl implements CustomerRepository {

    private final CustomerJpaRepository customerJpaRepository;

    public CustomerRepositoryImpl(CustomerJpaRepository customerJpaRepository) {
        this.customerJpaRepository = customerJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findById(Long id) {
        return customerJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findByIds(Collection<Long> ids) {
        return customerJpaRepository.findAllById(ids).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findByEmail(String email) {
        return customerJpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findByCpf(String cpf) {
        return customerJpaRepository.findByCpf(cpf).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findByContato(String contato) {
        return findAllByContato(contato).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findAllByContato(String contato) {
        String digits = CustomerIdentifiers.normalizePhone(contato);
        if (digits == null) {
            return List.of();
        }
        return customerJpaRepository.findByPhoneNormalizedOrderByIdAsc(digits).stream()
                .map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findAllByEmailIgnoreCase(String email) {
        String normalized = CustomerIdentifiers.normalizeEmailForMatch(email);
        if (normalized == null) {
            return List.of();
        }
        return customerJpaRepository.findByEmailLower(normalized).stream().map(this::toDomain).toList();
    }

    @Override
    public Customer save(Customer customer) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(customer.id());
        entity.setNome(customer.nome());
        entity.setContato(customer.contato());
        entity.setPhoneNormalized(CustomerIdentifiers.normalizePhone(customer.contato()));
        entity.setEmail(customer.email());
        entity.setCpf(customer.cpf());
        entity.setOrigem(customer.origem());
        entity.setCadastradoEm(customer.cadastradoEm());
        entity.setEstagio(customer.estagio());
        CustomerEntity saved = customerJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Customer> findAll(String search, int page, int size) {
        // Ordem estável: sem sort a página vinha em ordem indefinida e o cliente recém-cadastrado
        // podia não aparecer na primeira página (CRM-C006).
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("cadastradoEm"), Sort.Order.desc("id")));
        Page<CustomerEntity> result = (search == null || search.isBlank())
                ? customerJpaRepository.findAll(pageRequest)
                : searchPage(search.trim(), cpfSearchDigits(search), pageRequest);
        List<Customer> content = result.getContent().stream().map(this::toDomain).toList();
        return new PageResult<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findAllForExport(String search) {
        List<CustomerEntity> entities = (search == null || search.isBlank())
                ? customerJpaRepository.findAll()
                : searchAll(search.trim(), cpfSearchDigits(search));
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countAll() {
        return customerJpaRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countActive() {
        return customerJpaRepository.countByEstagioNot(CustomerStage.INATIVO);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<CustomerStage, Long> countByStage() {
        Map<CustomerStage, Long> result = new HashMap<>();
        for (Object[] row : customerJpaRepository.countGroupedByEstagio()) {
            result.put((CustomerStage) row[0], (Long) row[1]);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findByEstagio(CustomerStage estagio) {
        return customerJpaRepository.findByEstagio(estagio).stream().map(this::toDomain).toList();
    }

    /**
     * CPF é gravado só com dígitos; a busca por CPF só entra quando o termo tem dígitos suficientes
     * para não casar qualquer cliente cujo CPF contenha "1".
     */
    // CRM-C008: cpfDigits nulo nunca vai para a consulta — ver CustomerJpaRepository.
    private Page<CustomerEntity> searchPage(String search, String cpfDigits, PageRequest pageRequest) {
        return cpfDigits == null
                ? customerJpaRepository.search(search, pageRequest)
                : customerJpaRepository.searchWithCpf(search, cpfDigits, pageRequest);
    }

    private List<CustomerEntity> searchAll(String search, String cpfDigits) {
        return cpfDigits == null
                ? customerJpaRepository.searchAll(search)
                : customerJpaRepository.searchAllWithCpf(search, cpfDigits);
    }

    private static String cpfSearchDigits(String search) {
        String digits = CustomerIdentifiers.digitsOrNull(search);
        return digits != null && digits.length() >= 3 ? digits : null;
    }

    private Customer toDomain(CustomerEntity e) {
        return Customer.of(e.getId(), e.getNome(), e.getContato(), e.getEmail(), e.getCpf(), e.getOrigem(),
                e.getCadastradoEm(), e.getEstagio());
    }
}
