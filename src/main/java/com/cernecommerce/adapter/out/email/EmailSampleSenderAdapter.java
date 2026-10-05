package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.config.EmailSample;
import com.cernecommerce.core.domain.model.config.EmailSenderConfig;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.notification.EmailSampleSenderPort;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Exemplos de teste pelo Resend da loja ({@link StoreResendAdapterFactory}) ou pelo adapter do
 * ambiente. Este é um bean com {@code @Async}: chamamos o alvo desembrulhado para o envio ser
 * síncrono e o erro do provedor voltar no resultado do teste.
 */
@Component
class EmailSampleSenderAdapter implements EmailSampleSenderPort {

    private final StoreResendAdapterFactory storeAdapterFactory;
    private final EmailPort environment;

    EmailSampleSenderAdapter(StoreResendAdapterFactory storeAdapterFactory,
            @Qualifier(EmailAdapterConfig.FALLBACK) EmailPort environment) {
        this.storeAdapterFactory = storeAdapterFactory;
        this.environment = environment;
    }

    @Override
    public void sendSample(EmailSenderConfig config, String to, EmailSample sample) {
        storeAdapterFactory.adapterFor(config).sendSample(to, sample);
    }

    @Override
    public void sendEnvironmentSample(String to, EmailSample sample) {
        Object target = AopProxyUtils.getSingletonTarget(environment);
        EmailSamples.send(target instanceof EmailPort port ? port : environment, to, sample);
    }

    @Override
    public EmailChannelStatus environmentChannel() {
        return environment.channelStatus();
    }
}
