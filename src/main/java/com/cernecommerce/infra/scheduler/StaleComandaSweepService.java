package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.in.ComandaUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Varredor de mesa esquecida (PDV-F013).
 *
 * <p>Cancela as comandas abertas há mais de {@code pdv.comanda.stale.hours} que estão <b>vazias</b>,
 * e avisa sobre as que têm consumo. A assimetria mora no service — ver
 * {@link ComandaUseCase#sweepStaleComandas} — e o resumo é que devolver estoque de consumo real
 * criaria saldo que não existe na prateleira.</p>
 *
 * <p><b>Cadência diária, não a de 5 minutos do varredor de reserva.</b> Lá o TTL é de 30 minutos e
 * uma passada lenta deixaria saldo travado invisível; aqui a janela é de horas e o que se ganha
 * correndo mais é ruído. Às 5h porque a varredura precisa cair <i>depois</i> do fim do expediente e
 * <i>antes</i> da abertura seguinte: assim o caixa da manhã já encontra o salão limpo, em vez de
 * descobrir a mesa pendurada na hora de fechar o próprio turno (PDV-C005).</p>
 */
@Component
public class StaleComandaSweepService {

    private static final Logger log = LoggerFactory.getLogger(StaleComandaSweepService.class);

    private final ComandaUseCase comandaUseCase;
    private final int staleHours;
    private final int batchSize;

    public StaleComandaSweepService(ComandaUseCase comandaUseCase,
            @Value("${pdv.comanda.stale.hours:12}") int staleHours,
            @Value("${pdv.comanda.stale.batch-size:200}") int batchSize) {
        this.comandaUseCase = comandaUseCase;
        this.staleHours = staleHours;
        this.batchSize = batchSize;
    }

    // lockAtMostFor cobre uma varredura anormalmente lenta sem travar a próxima passada para
    // sempre; lockAtLeastFor evita reexecução imediata em caso de falha rápida logo no início.
    // PDV-C024 — fuso explícito: a imagem (alpine) roda em UTC, e o "5h" virava 02:00 em São Paulo,
    // com o salão aberto. Mesmo critério de ReceivableOverdueJob.
    @Scheduled(cron = "${pdv.comanda.stale.cron:0 0 5 * * *}", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "staleComandaSweep", lockAtMostFor = "PT55M", lockAtLeastFor = "PT5M")
    public void sweep() {
        log.info("scheduler.pdv.comanda.stale.start staleHours={} batchSize={}", staleHours, batchSize);
        ComandaUseCase.StaleComandaSweepResult result = comandaUseCase.sweepStaleComandas(staleHours, batchSize);
        log.info("scheduler.pdv.comanda.stale.done cancelled={} finished={} flagged={}",
                result.cancelled(), result.finished(), result.flagged());
    }
}
