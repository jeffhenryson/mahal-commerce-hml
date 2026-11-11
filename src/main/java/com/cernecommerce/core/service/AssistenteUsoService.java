package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.assistente.AssistenteLimiteMensalException;
import com.cernecommerce.core.domain.model.assistente.FeedbackRegistro;
import com.cernecommerce.core.domain.model.assistente.NotaFeedback;
import com.cernecommerce.core.domain.model.assistente.PrecoTokens;
import com.cernecommerce.core.domain.model.assistente.UsoMensal;
import com.cernecommerce.core.domain.model.assistente.UsoTokens;
import com.cernecommerce.core.ports.in.AssistantIntegrationUseCase;
import com.cernecommerce.core.ports.in.AssistenteUsoUseCase;
import com.cernecommerce.core.ports.out.assistente.AssistenteUsoPort;
import com.cernecommerce.core.ports.out.assistente.AssistenteUsoPort.UsoAgrupado;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tokens por resposta, limite mensal (em {@code system_config}, pela integração) e relatórios. O mês é
 * o do fuso da loja. Sem preço configurado para um provedor usado, o custo estimado fica nulo.
 */
public class AssistenteUsoService implements AssistenteUsoUseCase {

    private final AssistenteUsoPort port;
    private final AssistantIntegrationUseCase integracao;
    private final Map<String, PrecoTokens> precos;
    private final String moeda;
    private final Clock clock;
    private final ZoneId fuso;

    public AssistenteUsoService(AssistenteUsoPort port, AssistantIntegrationUseCase integracao,
            Map<String, PrecoTokens> precos, String moeda, Clock clock, ZoneId fuso) {
        this.port = port;
        this.integracao = integracao;
        this.precos = Map.copyOf(precos);
        this.moeda = moeda;
        this.clock = clock;
        this.fuso = fuso;
    }

    @Override
    public void registrar(String mensagemId, UsoTokens uso) {
        if (uso != null && !uso.vazio()) {
            port.registrar(mensagemId, uso);
        }
    }

    @Override
    public void exigirDentroDoLimite() {
        Long limite = integracao.monthlyTokenLimit().orElse(null);
        if (limite == null) {
            return;
        }
        YearMonth mes = YearMonth.now(clock.withZone(fuso));
        long usados = port.tokensNoLimite(inicio(mes), inicio(mes.plusMonths(1)));
        if (usados >= limite) {
            throw new AssistenteLimiteMensalException("O assistente atingiu o limite mensal de uso da loja. "
                    + "Um administrador pode aumentá-lo em Configurações › Integrações.");
        }
    }

    @Override
    public UsoMensal usoDoMes(YearMonth mes) {
        Instant de = inicio(mes);
        Instant ate = inicio(mes.plusMonths(1));
        // Uma linha por usuário e provedor: soma por usuário; custo só se todo provedor usado tem preço.
        Map<String, Long> respostas = new LinkedHashMap<>();
        Map<String, UsoTokens> usos = new LinkedHashMap<>();
        Map<String, BigDecimal> custos = new LinkedHashMap<>();
        Set<String> semPreco = new HashSet<>();
        for (UsoAgrupado linha : port.usoPorUsuario(de, ate)) {
            String u = linha.username();
            respostas.merge(u, linha.respostas(), Long::sum);
            usos.merge(u, linha.uso(), UsoTokens::mais);
            PrecoTokens preco = linha.provedor() == null ? null : precos.get(linha.provedor());
            if (preco == null) {
                semPreco.add(u);
            } else {
                custos.merge(u, preco.custo(linha.uso()), BigDecimal::add);
            }
        }
        List<UsoMensal.PorUsuario> usuarios = new ArrayList<>();
        UsoTokens total = UsoTokens.ZERO;
        BigDecimal custoTotal = BigDecimal.ZERO;
        for (String u : respostas.keySet()) {
            BigDecimal custo = custos.getOrDefault(u, BigDecimal.ZERO);
            usuarios.add(new UsoMensal.PorUsuario(u, respostas.get(u), usos.get(u),
                    semPreco.contains(u) ? null : custo));
            total = total.mais(usos.get(u));
            custoTotal = custoTotal.add(custo);
        }
        boolean algumSemPreco = !semPreco.isEmpty();
        usuarios.sort(Comparator.comparingLong((UsoMensal.PorUsuario u) -> u.uso().contaNoLimite()).reversed());
        return new UsoMensal(mes, integracao.monthlyTokenLimit().orElse(null), total.contaNoLimite(), total,
                algumSemPreco ? null : custoTotal, moeda, usuarios);
    }

    @Override
    public List<FeedbackRegistro> feedbacks(NotaFeedback nota, int limite) {
        return port.feedbacks(nota, Math.max(1, Math.min(limite, 200)));
    }

    private Instant inicio(YearMonth mes) {
        return mes.atDay(1).atStartOfDay(fuso).toInstant();
    }
}
