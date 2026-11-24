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

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<CustomerEntity> {

    /*
     * V206 — toda consulta daqui para baixo ignora o cliente anonimizado (anonymizedAt preenchido).
     * Só findById/findAllById o enxergam: a ficha devolve 404 no serviço, e pedidos e comandas antigos
     * resolvem o nome "Cliente excluído" pelo findAllById.
     */

    Optional<CustomerEntity> findByEmailAndAnonymizedAtIsNull(String email);

    Optional<CustomerEntity> findByCpfAndAnonymizedAtIsNull(String cpf);

    /**
     * Compara só os dígitos do contato (CRM-C006): "(83) 99999-0000" e "83999990000" são o mesmo
     * telefone. Desde CRM-C007 usa a coluna {@code phone_normalized}, mantida no save.
     */
    List<CustomerEntity> findByPhoneNormalizedAndAnonymizedAtIsNullOrderByIdAsc(String phoneNormalized);

    /** E-mail sem diferenciar maiúsculas (CRM-C007); {@code email} já chega em minúsculas. */
    @Query("SELECT c FROM CustomerEntity c WHERE c.anonymizedAt IS NULL AND lower(c.email) = :email ORDER BY c.id")
    List<CustomerEntity> findByEmailLower(@Param("email") String email);

    /*
     * Busca livre por nome, contato, email ou CPF (CRM-C006 incluiu email e CPF). CRM-C008: a variante
     * com CPF só é chamada com cpfDigits preenchido — um parâmetro nulo em ":p IS NOT NULL" chega ao
     * Postgres sem tipo (bytea) e o LIKE quebra com 500. Por isso são duas consultas, não um filtro opcional.
     * Nome e email passam por unaccent nos dois lados (mesmo critério do catálogo, EST-C020/V122): o
     * autocomplete do PDV com "joao" precisa achar "João".
     */
    String ATIVO = "c.anonymizedAt IS NULL AND ";

    String SEARCH_TEXT = """
            unaccent(lower(c.nome)) LIKE unaccent(lower(concat('%', :search, '%')))
               OR c.contato LIKE concat('%', :search, '%')
               OR unaccent(lower(c.email)) LIKE unaccent(lower(concat('%', :search, '%')))""";

    @Query("SELECT c FROM CustomerEntity c WHERE " + ATIVO + "(" + SEARCH_TEXT + ")")
    Page<CustomerEntity> search(@Param("search") String search, Pageable pageable);

    @Query("SELECT c FROM CustomerEntity c WHERE " + ATIVO + "(" + SEARCH_TEXT
            + " OR c.cpf LIKE concat('%', :cpfDigits, '%'))")
    Page<CustomerEntity> searchWithCpf(@Param("search") String search, @Param("cpfDigits") String cpfDigits,
            Pageable pageable);

    @Query("SELECT c FROM CustomerEntity c WHERE " + ATIVO + "(" + SEARCH_TEXT + ") ORDER BY c.cadastradoEm DESC")
    List<CustomerEntity> searchAll(@Param("search") String search);

    @Query("SELECT c FROM CustomerEntity c WHERE " + ATIVO + "(" + SEARCH_TEXT
            + " OR c.cpf LIKE concat('%', :cpfDigits, '%')) ORDER BY c.cadastradoEm DESC")
    List<CustomerEntity> searchAllWithCpf(@Param("search") String search, @Param("cpfDigits") String cpfDigits);

    long countByEstagioNotAndAnonymizedAtIsNull(CustomerStage estagio);

    List<CustomerEntity> findByEstagioAndAnonymizedAtIsNull(CustomerStage estagio);

    @Query("SELECT c.estagio, COUNT(c) FROM CustomerEntity c WHERE c.anonymizedAt IS NULL GROUP BY c.estagio")
    List<Object[]> countGroupedByEstagio();

    Page<CustomerEntity> findByAnonymizedAtIsNull(Pageable pageable);

    List<CustomerEntity> findByAnonymizedAtIsNull();

    long countByAnonymizedAtIsNull();

    /**
     * V206 — exclusão do perfil (LGPD): apaga tudo o que identifica a pessoa e devolve estágio e
     * segmento ao neutro. A linha fica: pedidos, comandas, cashback e auditoria apontam para ela.
     * Só age sobre cliente ainda ativo — devolve 0 se já foi anonimizado.
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE CustomerEntity c SET c.anonymizedAt = :now, c.nome = :nome, c.contato = NULL,
                   c.phoneNormalized = NULL, c.email = NULL, c.cpf = NULL, c.dataNascimento = NULL,
                   c.cidade = NULL, c.avatarUrl = NULL, c.origem = NULL,
                   c.estagio = com.cernecommerce.core.domain.model.crm.CustomerStage.NOVO_LEAD,
                   c.segmento = 'Novo', c.segmentoManual = false, c.segmentoCalculadoEm = NULL
             WHERE c.id = :id AND c.anonymizedAt IS NULL""")
    int anonymize(@Param("id") Long id, @Param("nome") String nome, @Param("now") java.time.Instant now);
}
