package com.cernecommerce.core.ports.out.crm;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port de saída para persistência de clientes do CRM.
 */
public interface CustomerRepository {

    Optional<Customer> findById(Long id);

    /** Busca em lote — evita N+1 ao enriquecer uma página de outro domínio com dado de cliente. */
    List<Customer> findByIds(Collection<Long> ids);

    Optional<Customer> findByEmail(String email);

    /** Identificador OFICIAL do cadastro (CRM-C005). */
    Optional<Customer> findByCpf(String cpf);

    /**
     * Contato não é único — telefone pode ser reaproveitado (família, número corporativo) —, então
     * devolve o primeiro achado. Suficiente para o caso de uso: achar rápido no balcão, não provar
     * unicidade.
     */
    Optional<Customer> findByContato(String contato);

    /** Todos os clientes com o mesmo telefone, comparado só pelos dígitos (CRM-C007), por id. */
    List<Customer> findAllByContato(String contato);

    /** Todos os clientes com o e-mail, aparado e sem diferenciar maiúsculas (CRM-C007), por id. */
    List<Customer> findAllByEmailIgnoreCase(String email);

    Customer save(Customer customer);

    /** Lista clientes paginados, filtrando por nome ou contato quando {@code search} não for nulo/vazio. */
    PageResult<Customer> findAll(String search, int page, int size);

    /** Pendência 7 — filtros repetíveis e ordem; {@code totalElements} já filtrado. */
    PageResult<Customer> findAll(com.cernecommerce.core.domain.model.crm.CustomerFilter filter, int page, int size);

    /** Mesmo filtro e mesma ordem de {@link #findAll(com.cernecommerce.core.domain.model.crm.CustomerFilter, int, int)}, sem paginar. */
    List<Customer> findAllForExport(com.cernecommerce.core.domain.model.crm.CustomerFilter filter);

    /** Total de clientes cadastrados. */
    long countAll();

    /** Total de clientes com estágio diferente de {@link CustomerStage#INATIVO}. */
    long countActive();

    /** Contagem de clientes agrupada por estágio. Estágios sem nenhum cliente não aparecem no mapa. */
    Map<CustomerStage, Long> countByStage();

    /**
     * Lista todos os clientes correspondentes ao filtro, sem paginação — usado para exportação.
     * Mesmo critério de filtro de {@link #findAll(String, int, int)}.
     */
    List<Customer> findAllForExport(String search);

    /** Lista todos os clientes em um estágio — usado para resolver o público-alvo de uma campanha. */
    List<Customer> findByEstagio(CustomerStage estagio);
}
