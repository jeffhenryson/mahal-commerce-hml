package com.cernecommerce.adapter.out.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Bean da WhatsApp Cloud API. Só a URL da Graph API e os timeouts vêm de propriedades — as
 * credenciais moram cifradas em {@code system_config} (tela de Integrações). Mesmo molde de
 * timeout explícito de {@code CrmWebhookConfig}.
 */
@Configuration
class WhatsappAdapterConfig {

    @Bean
    MetaWhatsappAdapter metaWhatsappAdapter(
            @Value("${whatsapp.graph.base-url:https://graph.facebook.com/v20.0}") String baseUrl,
            @Value("${whatsapp.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${whatsapp.read-timeout-ms:5000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        return new MetaWhatsappAdapter(restClient);
    }
}
