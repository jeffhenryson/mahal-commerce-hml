package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.assistente.FeedbackRegistro;
import com.cernecommerce.core.domain.model.assistente.NotaFeedback;
import com.cernecommerce.core.domain.model.assistente.UsoTokens;
import com.cernecommerce.core.ports.out.assistente.AssistenteUsoPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** {@link AssistenteUsoPort} em SQL padrão (Postgres e o H2 do perfil dev). */
@Repository
public class AssistenteUsoJdbc implements AssistenteUsoPort {

    private final JdbcTemplate jdbc;

    public AssistenteUsoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void registrar(String mensagemId, UsoTokens uso) {
        jdbc.update("UPDATE assistente_mensagem SET tokens_entrada = ?, tokens_saida = ?, tokens_cache = ? WHERE id = ?",
                inteiro(uso.entrada()), inteiro(uso.saida()), inteiro(uso.cache()), mensagemId);
    }

    @Override
    @Transactional(readOnly = true)
    public long tokensNoLimite(Instant de, Instant ate) {
        Long n = jdbc.queryForObject("""
                SELECT COALESCE(SUM(COALESCE(tokens_entrada, 0) + COALESCE(tokens_saida, 0)), 0)
                FROM assistente_mensagem
                WHERE papel = 'ASSISTENTE' AND criada_em >= ? AND criada_em < ?
                """, Long.class, ts(de), ts(ate));
        return n == null ? 0 : n;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsoAgrupado> usoPorUsuario(Instant de, Instant ate) {
        return jdbc.query("""
                SELECT c.username, m.provedor, COUNT(*),
                       COALESCE(SUM(m.tokens_entrada), 0), COALESCE(SUM(m.tokens_saida), 0),
                       COALESCE(SUM(m.tokens_cache), 0)
                FROM assistente_mensagem m JOIN assistente_conversa c ON c.id = m.conversa_id
                WHERE m.papel = 'ASSISTENTE' AND m.criada_em >= ? AND m.criada_em < ?
                GROUP BY c.username, m.provedor
                ORDER BY c.username
                """, (rs, i) -> new UsoAgrupado(rs.getString(1), rs.getString(2), rs.getLong(3),
                        new UsoTokens(rs.getLong(4), rs.getLong(5), rs.getLong(6))), ts(de), ts(ate));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackRegistro> feedbacks(NotaFeedback nota, int limite) {
        String filtro = nota == null ? "m.feedback_nota IS NOT NULL" : "m.feedback_nota = ?";
        String sql = "SELECT m.id,"
                + " (SELECT u.texto FROM assistente_mensagem u WHERE u.conversa_id = m.conversa_id"
                + "    AND u.papel = 'USUARIO' AND u.criada_em <= m.criada_em ORDER BY u.criada_em DESC LIMIT 1),"
                + " m.texto, m.feedback_nota, m.feedback_motivo, m.provedor, m.modelo, m.feedback_em"
                + " FROM assistente_mensagem m"
                + " WHERE m.papel = 'ASSISTENTE' AND " + filtro
                + " ORDER BY m.feedback_em DESC LIMIT ?";
        Object[] args = nota == null ? new Object[] {limite} : new Object[] {nota.name(), limite};
        return jdbc.query(sql, (rs, i) -> {
            Timestamp em = rs.getTimestamp(8);
            return new FeedbackRegistro(rs.getString(1), rs.getString(2), rs.getString(3),
                    NotaFeedback.valueOf(rs.getString(4)), rs.getString(5), rs.getString(6), rs.getString(7),
                    em == null ? null : em.toInstant());
        }, args);
    }

    private static int inteiro(long n) {
        return (int) Math.min(n, Integer.MAX_VALUE);
    }

    private static Timestamp ts(Instant i) {
        return Timestamp.from(i);
    }
}
