package com.cernecommerce.core.domain.model.assistente;

/**
 * Resultado de tentar reservar uma ação para execução. {@code reservada} só é verdade para quem
 * a tirou de PENDENTE: esse executa; os demais devolvem o estado gravado.
 */
public record ReservaAcao(AcaoPendente acao, boolean reservada) {
}
