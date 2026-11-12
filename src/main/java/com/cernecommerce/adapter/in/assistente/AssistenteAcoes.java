package com.cernecommerce.adapter.in.assistente;

import com.cernecommerce.adapter.in.assistente.ExecutorAcao.Execucao;
import com.cernecommerce.adapter.in.dtos.response.AssistenteAcaoResultadoDTO;
import com.cernecommerce.core.domain.exception.assistente.AcaoEmExecucaoException;
import com.cernecommerce.core.domain.exception.assistente.AcaoExpiradaException;
import com.cernecommerce.core.domain.model.assistente.AcaoPendente;
import com.cernecommerce.core.domain.model.assistente.EstadoAcao;
import com.cernecommerce.core.domain.model.assistente.ReservaAcao;
import com.cernecommerce.core.ports.in.AssistenteConversaUseCase;
import com.cernecommerce.infra.audit.AuditOrigemContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Confirmar e recusar ações propostas pelo modelo. O confirmar executa exatamente os parâmetros
 * guardados, uma vez só, como o usuário logado e pelo controller do recurso — com a auditoria
 * marcada como origem {@code ASSISTENTE}.
 *
 * <p>A reserva (PENDENTE → EXECUTANDO) e o desfecho são transações curtas separadas: a execução
 * no meio abre a própria, e a linha não fica travada enquanto ela roda.</p>
 */
@Component
public class AssistenteAcoes {

    private static final Logger log = LoggerFactory.getLogger(AssistenteAcoes.class);

    private final AssistenteConversaUseCase conversas;
    private final Map<String, ExecutorAcao> executores = new HashMap<>();
    private final Clock clock = Clock.systemUTC();

    public AssistenteAcoes(AssistenteConversaUseCase conversas, List<ProvedorFerramentas> provedores) {
        this.conversas = conversas;
        for (ProvedorFerramentas p : provedores) {
            for (ExecutorAcao e : p.executores()) {
                if (executores.putIfAbsent(e.tipo(), e) != null) {
                    throw new IllegalStateException("Executor duplicado no assistente: " + e.tipo());
                }
            }
        }
    }

    public AssistenteAcaoResultadoDTO confirmar(String acaoId, String username, String ip, String userAgent) {
        ReservaAcao reserva = conversas.reservarParaExecucao(acaoId, username);
        AcaoPendente acao = reserva.acao();
        if (!reserva.reservada()) {
            if (acao.estado() == EstadoAcao.EXPIRADA) {
                throw new AcaoExpiradaException();
            }
            if (acao.estado() == EstadoAcao.EXECUTANDO) {
                throw new AcaoEmExecucaoException();
            }
            // Já executada, recusada ou falha: devolve o desfecho gravado, sem executar de novo.
            return AssistenteMapper.resultado(acao, clock.instant());
        }

        ExecutorAcao executor = executores.get(acao.tipo());
        AcaoPendente concluida;
        if (executor == null) {
            log.error("assistente.acao.sem-executor tipo={} acao={}", acao.tipo(), acaoId);
            concluida = conversas.concluirExecucao(acaoId, EstadoAcao.FALHOU, "Tipo de ação não suportado.", null);
        } else {
            concluida = executar(executor, acao, ip, userAgent);
        }
        return AssistenteMapper.resultado(concluida, clock.instant());
    }

    public AssistenteAcaoResultadoDTO recusar(String acaoId, String username) {
        return AssistenteMapper.resultado(conversas.recusar(acaoId, username), clock.instant());
    }

    private AcaoPendente executar(ExecutorAcao executor, AcaoPendente acao, String ip, String userAgent) {
        AuditOrigemContext.Origem origem = new AuditOrigemContext.Origem(AuditOrigemContext.ASSISTENTE, ip, userAgent);
        try {
            Execucao execucao = AuditOrigemContext.executar(origem,
                    () -> executor.executar().apply(FerramentaSupport.ler(acao.parametrosJson())));
            log.info("assistente.acao.executada tipo={} acao={} user={}", acao.tipo(), acao.id(), acao.username());
            return conversas.concluirExecucao(acao.id(), EstadoAcao.EXECUTADA, execucao.resultado(),
                    AssistenteMapper.json(execucao.links().stream().filter(l -> AssistenteMapper.rotaSegura(l.rota()))
                            .toList()));
        } catch (AccessDeniedException ex) {
            return conversas.concluirExecucao(acao.id(), EstadoAcao.FALHOU,
                    "Você não tem permissão para esta alteração.", null);
        } catch (RuntimeException ex) {
            log.info("assistente.acao.falhou tipo={} acao={} erro={}", acao.tipo(), acao.id(), ex.getMessage());
            String msg = ex.getMessage() == null ? "Não foi possível executar." : PiiSanitizer.limpar(ex.getMessage());
            return conversas.concluirExecucao(acao.id(), EstadoAcao.FALHOU, msg, null);
        }
    }
}
