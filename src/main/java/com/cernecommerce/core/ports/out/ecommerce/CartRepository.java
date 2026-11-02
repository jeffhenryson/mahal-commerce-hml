package com.cernecommerce.core.ports.out.ecommerce;

import com.cernecommerce.core.domain.model.ecommerce.Cart;

import com.cernecommerce.core.domain.model.ecommerce.CartItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência do carrinho de compras (ECM-F003, Fatia 9).
 */
public interface CartRepository {

    /** Carrinho do cliente, se já existir alguma linha. Vazio para quem nunca adicionou nada. */
    Optional<Cart> findByCustomerId(Long customerId);

    /**
     * Upsert de quantidade (PUT — idempotente): cria o carrinho se for a primeira vez do cliente,
     * cria a linha se o SKU ainda não estiver nela, ou substitui a quantidade se já estiver.
     */
    Cart upsertItem(Long customerId, String sku, BigDecimal quantity);

    /**
     * ECM-F008 — acrescenta um pacote de kit montável inteiro. As linhas chegam já com
     * {@code kitBundleId} preenchido. Nunca mescla com linha avulsa do mesmo SKU nem com outro
     * pacote: dois kits iguais são dois pacotes.
     */
    Cart addKitBundle(Long customerId, List<CartItem> items);

    /**
     * Remove todas as linhas do pacote.
     *
     * @return {@code false} se o pacote não estava no carrinho
     */
    boolean removeKitBundle(Long customerId, String kitBundleId);

    /**
     * Remove uma linha <b>avulsa</b> do carrinho — linha de kit sai só com o pacote inteiro
     * ({@link #removeKitBundle}).
     *
     * @return {@code true} se havia uma linha para remover, {@code false} se o SKU não estava no
     *         carrinho (o service decide se isso é 404 — a repository só relata o fato)
     */
    boolean removeItem(Long customerId, String sku);

    /**
     * Listagem administrativa (GET /ecommerce/carts): só carrinhos com ao menos uma linha, do mais
     * recentemente alterado para o mais antigo.
     */
    com.cernecommerce.core.domain.model.PageResult<Cart> findAllNonEmpty(int page, int size);

    /** Esvazia o carrinho — o checkout consome as linhas ao virar pedido. Sem efeito se já vazio. */
    void clear(Long customerId);
}
