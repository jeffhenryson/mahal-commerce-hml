package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerEntity;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, Long> {

    Optional<CustomerEntity> findByEmail(String email);

    Optional<CustomerEntity> findByCpf(String cpf);

    /**
     * Compara só os dígitos do contato (CRM-C006): "(83) 99999-0000" e "83999990000" são o mesmo
     * telefone. Desde CRM-C007 usa a coluna {@code phone_normalized}, mantida no save.
     */
    List<CustomerEntity> findByPhoneNormalizedOrderByIdAsc(String phoneNormalized);

    /** E-mail sem diferenciar maiúsculas (CRM-C007); {@code email} já chega em minúsculas. */
    @Query("SELECT c FROM CustomerEntity c WHERE lower(c.email) = :email ORDER BY c.id")
    List<CustomerEntity> findByEmailLower(@Param("email") String email);

    /*
     * Busca livre por nome, contato, email ou CPF (CRM-C006 incluiu email e CPF). CRM-C008: a variante
     * com CPF só é chamada com cpfDigits preenchido — um parâmetro nulo em ":p IS NOT NULL" chega ao
     * Postgres sem tipo (bytea) e o LIKE quebra com 500. Por isso são duas consultas, não um filtro opcional.
     */
    String SEARCH_TEXT = """
            lower(c.nome) LIKE lower(concat('%', :search, '%'))
               OR c.contato LIKE concat('%', :search, '%')
               OR lower(c.email) LIKE lower(concat('%', :search, '%'))""";

    @Query("SELECT c FROM CustomerEntity c WHERE " + SEARCH_TEXT)
    Page<CustomerEntity> search(@Param("search") String search, Pageable pageable);

    @Query("SELECT c FROM CustomerEntity c WHERE " + SEARCH_TEXT
            + " OR c.cpf LIKE concat('%', :cpfDigits, '%')")
    Page<CustomerEntity> searchWithCpf(@Param("search") String search, @Param("cpfDigits") String cpfDigits,
            Pageable pageable);

    @Query("SELECT c FROM CustomerEntity c WHERE " + SEARCH_TEXT + " ORDER BY c.cadastradoEm DESC")
    List<CustomerEntity> searchAll(@Param("search") String search);

    @Query("SELECT c FROM CustomerEntity c WHERE " + SEARCH_TEXT
            + " OR c.cpf LIKE concat('%', :cpfDigits, '%') ORDER BY c.cadastradoEm DESC")
    List<CustomerEntity> searchAllWithCpf(@Param("search") String search, @Param("cpfDigits") String cpfDigits);

    long countByEstagioNot(CustomerStage estagio);

    List<CustomerEntity> findByEstagio(CustomerStage estagio);

    @Query("SELECT c.estagio, COUNT(c) FROM CustomerEntity c GROUP BY c.estagio")
    List<Object[]> countGroupedByEstagio();
}
