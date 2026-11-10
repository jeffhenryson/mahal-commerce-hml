package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.in.AssistenteConversaUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retenção do assistente de IA: apaga conversas sem atividade há mais de
 * {@code assistente.retencao-dias} (90 por padrão), com mensagens e ações, e fecha ações que
 * ficaram para trás (pendentes vencidas, execuções interrompidas).
 */
@Component
public class AssistenteRetencaoJob {

    private static final Logger log = LoggerFactory.getLogger(AssistenteRetencaoJob.class);

    private final AssistenteConversaUseCase conversas;
    private final int retencaoDias;

    public AssistenteRetencaoJob(AssistenteConversaUseCase conversas,
            @Value("${assistente.retencao-dias:90}") int retencaoDias) {
        this.conversas = conversas;
        this.retencaoDias = retencaoDias;
    }

    @Scheduled(cron = "${assistente.retencao.cron:0 30 4 * * *}")
    @SchedulerLock(name = "assistenteRetencao", lockAtMostFor = "PT15M", lockAtLeastFor = "PT2M")
    public void executar() {
        long apagadas = conversas.aplicarRetencao(retencaoDias);
        log.info("scheduler.assistente.retencao.done retencaoDias={} conversasApagadas={}", retencaoDias, apagadas);
    }
}
