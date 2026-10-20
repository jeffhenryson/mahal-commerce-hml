package com.cernecommerce.core.domain.model.salao;

/**
 * Essência oferecida com a marcação de destaque do dia. O QR oferece todas as latas abertas; as
 * marcadas aparecem no topo.
 */
public record EssenciaDoDia(EssenciaOferecida essencia, boolean selecionada) {
}
