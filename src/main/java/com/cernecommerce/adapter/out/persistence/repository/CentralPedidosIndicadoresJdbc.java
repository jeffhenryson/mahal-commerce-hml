package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.ports.out.ecommerce.CentralPedidosIndicadoresQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link CentralPedidosIndicadoresQuery} em SQL puro (ECM-F013): agregações sobre {@code sales_order},
 * {@code pedido_app_atendimento}, {@code salao_solicitacao} e {@code salao_chamado}. SQL padrão, sem
 * nada do Postgres, para rodar também no H2 do perfil dev.
 */
@Repository
@Transactional(readOnly = true)
public class CentralPedidosIndicadoresJdbc implements CentralPedidosIndicadoresQuery {

    /** Mesmo filtro de {@code OrderJpaRepository.findRevenueTotals}. */
    private static final String STATUS_RECEITA = "('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')";

    private final JdbcTemplate jdbc;

    public CentralPedidosIndicadoresJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<TotaisCanal> receitaPorCanal(Instant de, Instant ate) {
        return totaisPorCanal("o.status IN " + STATUS_RECEITA, de, ate);
    }

    @Override
    public List<TotaisCanal> canceladosPorCanal(Instant de, Instant ate) {
        return totaisPorCanal("o.status = 'CANCELADO'", de, ate);
    }

    private List<TotaisCanal> totaisPorCanal(String filtroStatus, Instant de, Instant ate) {
        String sql = "SELECT o.channel, COUNT(*) AS pedidos, COALESCE(SUM(o.net_amount), 0) AS valor"
                + " FROM sales_order o"
                + " WHERE o.channel IN ('MARKETPLACE', 'MESA')"
                + " AND o.sold_at >= ? AND o.sold_at < ?"
                + " AND " + filtroStatus
                + " GROUP BY o.channel";
        return jdbc.query(sql,
                (rs, i) -> new TotaisCanal(rs.getString(1), rs.getLong(2), rs.getBigDecimal(3)),
                ts(de), ts(ate));
    }

    @Override
    public long pedidosAppPendentes() {
        return contar("SELECT COUNT(*) FROM pedido_app_atendimento WHERE status = 'PENDENTE'");
    }

    @Override
    public long solicitacoesPendentes(Instant validasDesde) {
        return contar("SELECT COUNT(*) FROM salao_solicitacao WHERE status = 'PENDENTE' AND criado_em >= ?",
                ts(validasDesde));
    }

    @Override
    public long chamadosAbertos(Instant validosDesde) {
        return contar("SELECT COUNT(*) FROM salao_chamado WHERE status = 'ABERTO' AND criado_em >= ?",
                ts(validosDesde));
    }

    @Override
    public long pedidosAppRecebidos(Instant de, Instant ate) {
        return contar("SELECT COUNT(*) FROM pedido_app_atendimento WHERE criado_em >= ? AND criado_em < ?",
                ts(de), ts(ate));
    }

    @Override
    public Map<String, Long> recusasPedidoApp(Instant de, Instant ate) {
        return agrupar("""
                SELECT motivo_codigo, COUNT(*) FROM pedido_app_atendimento
                WHERE status = 'RECUSADO' AND encerrado_em >= ? AND encerrado_em < ?
                GROUP BY motivo_codigo
                """, de, ate);
    }

    @Override
    public long solicitacoesRecebidas(Instant de, Instant ate) {
        return contar("SELECT COUNT(*) FROM salao_solicitacao WHERE criado_em >= ? AND criado_em < ?",
                ts(de), ts(ate));
    }

    @Override
    public Map<String, Long> recusasSolicitacao(Instant de, Instant ate) {
        return agrupar("""
                SELECT motivo, COUNT(*) FROM salao_solicitacao
                WHERE status = 'RECUSADA' AND decidido_em >= ? AND decidido_em < ?
                GROUP BY motivo
                """, de, ate);
    }

    private long contar(String sql, Object... args) {
        Long total = jdbc.queryForObject(sql, Long.class, args);
        return total == null ? 0 : total;
    }

    private Map<String, Long> agrupar(String sql, Instant de, Instant ate) {
        Map<String, Long> totais = new LinkedHashMap<>();
        RowCallbackHandler somar = rs -> totais.merge(rs.getString(1), rs.getLong(2), Long::sum);
        jdbc.query(sql, somar, ts(de), ts(ate));
        return totais;
    }

    private static Timestamp ts(Instant instante) {
        return Timestamp.from(instante);
    }
}
