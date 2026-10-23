package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.in.PedidoAppUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Varredor do prazo do dia dos pedidos do app (ECM-F011).
 *
 * <p>Cancela o pedido do app que passou do horário-limite sem pagamento e devolve a reserva —
 * antes, a reserva vencia sozinha e o pedido ficava de pé em {@code AGUARDANDO_PAGAMENTO}, sem
 * estoque. A reserva do pedido do app vence de propósito um pouco depois do prazo, para que seja
 * este varredor, e não o de reserva, quem a devolve.</p>
 *
 * <p>Um pedido por transação: um pedido que falha (corrida com a liquidação no caixa, por exemplo)
 * não desfaz os outros do lote, e é retentado na passada seguinte.</p>
 */
@Component
public class PedidoAppExpiryService {

    private static final Logger log = LoggerFactory.getLogger(PedidoAppExpiryService.class);

    private final PedidoAppUseCase pedidoAppUseCase;
    private final int batchSize;

    public PedidoAppExpiryService(PedidoAppUseCase pedidoAppUseCase,
            @Value("${shop.pedido.expiry.batch-size:100}") int batchSize) {
        this.pedidoAppUseCase = pedidoAppUseCase;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${shop.pedido.expiry.cron:0 */5 * * * *}")
    @SchedulerLock(name = "pedidoAppExpiry", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
    public void expirar() {
        List<Long> vencidos = pedidoAppUseCase.vencidos(Instant.now(), batchSize);
        if (vencidos.isEmpty()) {
            return;
        }
        int expirados = 0;
        for (Long orderId : vencidos) {
            try {
                if (pedidoAppUseCase.expirar(orderId)) {
                    expirados++;
                }
            } catch (RuntimeException e) {
                log.warn("scheduler.pedido-app.expiry.failed orderId={}: {}", orderId, e.getMessage(), e);
            }
        }
        log.info("scheduler.pedido-app.expiry.done candidatos={} expirados={}", vencidos.size(), expirados);
    }
}
