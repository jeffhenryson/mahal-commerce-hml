package com.cernecommerce.core.ports.out.ecommerce;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Contagens agregadas para os indicadores do dia da Central de Pedidos (ECM-F013). Intervalos são
 * {@code [de, ate)}. Só leitura, direto no banco: somar em memória pediria carregar o dia inteiro.
 */
public interface CentralPedidosIndicadoresQuery {

    /** Pedidos e valor (soma do {@code netAmount}) de um canal num intervalo. */
    record TotaisCanal(String channel, long pedidos, BigDecimal valor) {
    }

    /**
     * Receita por canal, com a regra do {@code totalRevenueNet} de {@code GET /orders/summary}: só
     * status com pagamento confirmado, sem {@code CANCELADO}/{@code REEMBOLSADO}.
     */
    List<TotaisCanal> receitaPorCanal(Instant de, Instant ate);

    /** Pedidos {@code CANCELADO} por canal, criados no intervalo. */
    List<TotaisCanal> canceladosPorCanal(Instant de, Instant ate);

    /** Pedidos do app esperando aceite agora (atendimento {@code PENDENTE}). */
    long pedidosAppPendentes();

    /** Solicitações do QR pendentes criadas a partir de {@code validasDesde} (as anteriores expiraram). */
    long solicitacoesPendentes(Instant validasDesde);

    /** Chamados de mesa abertos criados a partir de {@code validosDesde}. */
    long chamadosAbertos(Instant validosDesde);

    /** Atendimentos de pedido do app que nasceram no intervalo. */
    long pedidosAppRecebidos(Instant de, Instant ate);

    /** Recusas pela loja encerradas no intervalo, por código de motivo; sem código vem com chave nula. */
    Map<String, Long> recusasPedidoApp(Instant de, Instant ate);

    /** Solicitações do QR criadas no intervalo. */
    long solicitacoesRecebidas(Instant de, Instant ate);

    /** Solicitações recusadas decididas no intervalo, pelo motivo em texto; sem motivo vem com chave nula. */
    Map<String, Long> recusasSolicitacao(Instant de, Instant ate);
}
