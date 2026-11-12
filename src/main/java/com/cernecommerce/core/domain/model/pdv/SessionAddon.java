package com.cernecommerce.core.domain.model.pdv;

import java.math.BigDecimal;

/**
 * Adicional pago da sessão (PDV-F024) — "Filtro de gelo R$ 5". Item do cardápio da mesa no molde
 * de {@link SessionTier}: fora do catálogo, sem estoque. Entra no preço da linha da sessão.
 */
public record SessionAddon(Long id, String nome, BigDecimal preco, int ordem, boolean ativo,
        boolean permitePosEntrega) {

    public static final int NOME_MAX_LENGTH = 60;

    /** PDV-F061 — SKU sintético da linha do adicional cobrado depois que a sessão já foi paga. */
    public static final String SKU_PREFIX = "ADIC-";

    public SessionAddon {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome do adicional é obrigatório");
        }
        nome = nome.trim();
        if (nome.length() > NOME_MAX_LENGTH) {
            throw new IllegalArgumentException("nome do adicional excede " + NOME_MAX_LENGTH + " caracteres");
        }
        if (preco == null || preco.signum() < 0) {
            throw new IllegalArgumentException("preço do adicional é obrigatório e não pode ser negativo");
        }
        if (ordem < 0) {
            throw new IllegalArgumentException("ordem não pode ser negativa");
        }
    }

    /** Adicional que só entra no lançamento e na edição, não depois da entrega. */
    public SessionAddon(Long id, String nome, BigDecimal preco, int ordem, boolean ativo) {
        this(id, nome, preco, ordem, ativo, false);
    }

    public static SessionAddon create(String nome, BigDecimal preco, int ordem) {
        return create(nome, preco, ordem, false);
    }

    public static SessionAddon create(String nome, BigDecimal preco, int ordem, boolean permitePosEntrega) {
        return new SessionAddon(null, nome, preco, ordem, true, permitePosEntrega);
    }

    public SessionAddon withData(String novoNome, BigDecimal novoPreco, int novaOrdem, boolean novoAtivo) {
        return withData(novoNome, novoPreco, novaOrdem, novoAtivo, permitePosEntrega);
    }

    public SessionAddon withData(String novoNome, BigDecimal novoPreco, int novaOrdem, boolean novoAtivo,
            boolean novoPermitePosEntrega) {
        return new SessionAddon(id, novoNome, novoPreco, novaOrdem, novoAtivo, novoPermitePosEntrega);
    }
}
