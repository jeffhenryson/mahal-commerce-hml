package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.AvisoOperadoresUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Aviso em tempo real para quem opera o PDV: notificação gravada mais o empurrão por SSE, para todo
 * usuário com a permissão dada. Nasceu no autoatendimento do Salão e passou a servir também o pedido
 * do app (ECM-F010).
 *
 * <p>Sempre <b>depois do commit</b>: a tela do PDV relê a fila ao receber o aviso, e o pedido tem de
 * estar lá. Falha aqui só é registrada — o aviso é conveniência, a fila é relida sozinha.</p>
 */
public class AvisoOperadores implements AvisoOperadoresUseCase {

    private static final Logger log = LoggerFactory.getLogger(AvisoOperadores.class);

    private final NotificationUseCase notificationUseCase;
    private final UserRepository userRepository;
    private final NotificationSsePort ssePort;
    private final AfterCommitExecutor afterCommit;

    public AvisoOperadores(NotificationUseCase notificationUseCase, UserRepository userRepository,
            NotificationSsePort ssePort, AfterCommitExecutor afterCommit) {
        this.notificationUseCase = notificationUseCase;
        this.userRepository = userRepository;
        this.ssePort = ssePort;
        this.afterCommit = afterCommit;
    }

    @Override
    public void avisar(String chave, List<String> permissoes, NotificationType tipo, String titulo, String corpo,
            String destino) {
        afterCommit.accumulate(chave, titulo, avisos -> {
            try {
                Set<String> destinatarios = new LinkedHashSet<>();
                permissoes.forEach(p -> destinatarios.addAll(userRepository.findUsernamesByPermission(p)));
                for (String username : destinatarios) {
                    Notification salva = notificationUseCase.notify(username, tipo, titulo, corpo, destino);
                    ssePort.send(username, salva);
                }
            } catch (RuntimeException e) {
                log.warn("Aviso {} não enviado: {}", chave, e.getMessage(), e);
            }
        });
    }
}
