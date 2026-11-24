package com.cernecommerce.core.ports.out.crm;

import com.cernecommerce.core.domain.model.crm.Tag;

import java.util.List;

/**
 * Port de saída para a associação muitos-para-muitos entre clientes e tags do CRM.
 */
public interface CustomerTagRepository {

    /** Associa a tag ao cliente. Idempotente — associar novamente não gera duplicata. */
    void associate(Long customerId, Long tagId);

    void disassociate(Long customerId, Long tagId);

    /** Lista as tags associadas a um cliente. */
    List<Tag> findTagsByCustomerId(Long customerId);

    /** V206 — remove todas as tags do cliente, a VIP inclusive. */
    void removeAllByCustomerId(Long customerId);
}
