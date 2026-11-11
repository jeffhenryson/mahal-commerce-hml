package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.ports.out.assistente.AssistenteConsultasQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * {@link AssistenteConsultasQuery} em SQL padrão (roda no Postgres e no H2 do perfil dev). Variação
 * de produto herda nome e categoria do pai, por {@code product_variant}.
 */
@Repository
@Transactional(readOnly = true)
public class AssistenteConsultasJdbc implements AssistenteConsultasQuery {

    /** Mesmo filtro de {@code OrderJpaRepository.findRevenueTotals}. */
    private static final String STATUS_RECEITA = "('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')";

    private static final String PRODUTO_DO_SKU = """
             LEFT JOIN product p ON p.sku = %1$s
             LEFT JOIN product_variant v ON v.sku = %1$s
             LEFT JOIN product pv ON pv.id = v.product_id
            """;

    private final JdbcTemplate jdbc;

    public AssistenteConsultasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CategoriaVendida> topCategorias(Instant de, Instant ate, int limite, boolean porQuantidade) {
        String sql = "SELECT COALESCE(p.category, pv.category) AS categoria,"
                + " SUM(oi.quantity) AS quantidade,"
                + " COALESCE(SUM(oi.quantity * oi.unit_price - oi.discount_amount), 0) AS receita"
                + " FROM order_item oi JOIN sales_order o ON o.id = oi.order_id"
                + PRODUTO_DO_SKU.formatted("oi.sku")
                + " WHERE o.sold_at >= ? AND o.sold_at <= ? AND o.status IN " + STATUS_RECEITA
                + " GROUP BY COALESCE(p.category, pv.category)"
                + " ORDER BY " + (porQuantidade ? "quantidade" : "receita") + " DESC"
                + " LIMIT ?";
        return jdbc.query(sql, (rs, i) -> new CategoriaVendida(rs.getString(1), rs.getBigDecimal(2),
                rs.getBigDecimal(3)), ts(de), ts(ate), limite);
    }

    @Override
    public List<SaidaEstoque> saidasDesde(Instant desde) {
        String sql = "SELECT b.sku, COALESCE(p.name, pv.name, b.sku), w.code, w.id,"
                + " b.quantity - b.reserved_quantity, s.saida, rp.min_quantity, rp.critical_quantity"
                + " FROM stock_balance b"
                + " JOIN warehouse w ON w.id = b.warehouse_id"
                + " JOIN (SELECT sku, warehouse_id, SUM(quantity) AS saida FROM stock_movement"
                + "       WHERE type = 'SAIDA' AND counterpart_warehouse_id IS NULL AND created_at >= ?"
                + "       GROUP BY sku, warehouse_id) s ON s.sku = b.sku AND s.warehouse_id = b.warehouse_id"
                + PRODUTO_DO_SKU.formatted("b.sku")
                + " LEFT JOIN stock_reorder_point rp ON rp.sku = b.sku AND rp.warehouse_id = b.warehouse_id"
                + " WHERE s.saida > 0";
        return jdbc.query(sql, (rs, i) -> new SaidaEstoque(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getLong(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8)),
                ts(desde));
    }

    @Override
    public List<ClienteInativo> clientesInativos(Instant semCompraDesde, int limite) {
        String sql = "SELECT c.id, c.nome, MAX(o.sold_at) AS ultima, COUNT(o.id)"
                + " FROM customers c JOIN sales_order o ON o.customer_id = c.id"
                + " WHERE o.status IN " + STATUS_RECEITA
                + " GROUP BY c.id, c.nome"
                + " HAVING MAX(o.sold_at) < ?"
                + " ORDER BY ultima DESC"
                + " LIMIT ?";
        return jdbc.query(sql, (rs, i) -> new ClienteInativo(rs.getLong(1), rs.getString(2),
                rs.getTimestamp(3).toInstant(), rs.getLong(4)), ts(semCompraDesde), limite);
    }

    private static Timestamp ts(Instant instante) {
        return Timestamp.from(instante);
    }
}
