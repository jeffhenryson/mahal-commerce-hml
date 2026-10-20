package com.cernecommerce.core.domain.model.salao;

import java.math.BigDecimal;
import java.util.List;

/**
 * Solicitação pronta para o PDV: a mesa e o cliente por nome, a faixa com preço e os rótulos dos
 * sabores — o atendente decide sem abrir outra tela. {@code status} já vem derivado
 * ({@link SolicitacaoSessao#statusEm}). {@code total} é a faixa mais os extras escolhidos, pelos preços
 * de agora.
 */
public record SolicitacaoResumo(SolicitacaoSessao solicitacao, SolicitacaoStatus status, String mesa,
        String cliente, String clienteContato, String tierNome, BigDecimal preco, String essencia,
        String essencia2, List<String> adicionais, BigDecimal total) {
}
