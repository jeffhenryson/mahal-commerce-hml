package com.cernecommerce.core.domain.model.pdv;

import java.math.BigDecimal;
import java.util.List;

/**
 * O que saiu com a sessão além da faixa (PDV-F024): o carvão (só registro) e os adicionais pagos.
 * PDV-F027: e se foi no vaso grande — é o que "repetir sessão" precisa para refazer a mesma.
 *
 * <p>Os adicionais são <b>snapshot</b> (nome e preço no lançamento), não referência ao cadastro:
 * mudar o preço do filtro amanhã não pode reescrever o que a mesa de hoje pagou, e é por eles que o
 * relatório conta quantos filtros saíram.</p>
 *
 * <p>PDV-F057: {@code charcoalCourtesy} — o jumbo saiu de cortesia, sem o acréscimo. Só existe com
 * {@link Charcoal#JUMBO}; com outro carvão é normalizado para {@code false}.</p>
 */
public record SessionSetup(Charcoal charcoal, List<Addon> addons, boolean vasoGrande, boolean charcoalCourtesy) {

    public SessionSetup {
        addons = addons == null ? List.of() : List.copyOf(addons);
        charcoalCourtesy = charcoalCourtesy && charcoal == Charcoal.JUMBO;
    }

    public SessionSetup(Charcoal charcoal, List<Addon> addons, boolean vasoGrande) {
        this(charcoal, addons, vasoGrande, false);
    }

    public SessionSetup(Charcoal charcoal, List<Addon> addons) {
        this(charcoal, addons, false);
    }

    public static SessionSetup of(Charcoal charcoal, List<SessionAddon> chosen) {
        return of(charcoal, chosen, false);
    }

    public static SessionSetup of(Charcoal charcoal, List<SessionAddon> chosen, boolean vasoGrande) {
        return of(charcoal, chosen, vasoGrande, false);
    }

    public static SessionSetup of(Charcoal charcoal, List<SessionAddon> chosen, boolean vasoGrande,
            boolean charcoalCourtesy) {
        return new SessionSetup(charcoal, chosen == null ? List.of()
                : chosen.stream().map(a -> new Addon(a.id(), a.nome(), a.preco())).toList(), vasoGrande,
                charcoalCourtesy);
    }

    public BigDecimal addonsTotal() {
        return addons.stream().map(Addon::preco).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isEmpty() {
        return charcoal == null && addons.isEmpty() && !vasoGrande;
    }

    /** Adicional como foi cobrado na linha. */
    public record Addon(Long addonId, String nome, BigDecimal preco) {
        public Addon {
            if (nome == null || nome.isBlank()) {
                throw new IllegalArgumentException("nome do adicional é obrigatório");
            }
            if (preco == null || preco.signum() < 0) {
                throw new IllegalArgumentException("preço do adicional não pode ser negativo");
            }
        }
    }
}
