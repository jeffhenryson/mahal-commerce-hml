package com.cernecommerce.core.ports.in;

/**
 * V206 — exclusão do perfil do cliente (LGPD). A decisão do produto é anonimizar, não apagar a linha:
 * some tudo o que identifica a pessoa, e pedidos, comandas, movimentações de estoque, extrato de
 * cashback e auditoria continuam apontando para o mesmo {@code customer_id}. Não tem volta.
 */
public interface CustomerDeletionUseCase {

    /**
     * Anonimiza o cliente numa transação só: dados pessoais, tags, notas e anexos, saldo de cashback
     * (por lançamento) e disparos futuros. Idempotente: num cliente já anonimizado não faz nada.
     *
     * @return {@code true} se anonimizou agora; {@code false} se o cliente já estava anonimizado
     *         (inclusive por uma exclusão concorrente que chegou antes)
     * @throws com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException não existe
     * @throws com.cernecommerce.core.domain.exception.crm.CustomerHasOpenComandaException comanda,
     *         mesa ou solicitação de sessão aberta
     * @throws com.cernecommerce.core.domain.exception.crm.CustomerHasOpenBalanceException saldo
     *         devedor em Marcados
     */
    boolean deleteCustomer(Long customerId);
}
