package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.domain.model.ecommerce.CentralPedidosConfig;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.ecommerce.CentralPedidosConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Guarda a configuração da Central de Pedidos como um JSON numa chave só de {@code system_config}
 * (ECM-F013). Uma chave por campo, como o {@code StoreProfileService}, não comporta as listas de
 * horários e bairros; e o {@code PUT} substitui tudo de uma vez. O cache do {@link SystemConfigPort}
 * vale aqui também.
 *
 * <p>JSON ilegível (editado à mão no banco) é registrado e tratado como ausente: a loja volta ao
 * padrão em vez de o checkout parar.</p>
 */
@Repository
public class CentralPedidosConfigAdapter implements CentralPedidosConfigRepository {

    static final String CHAVE = "central-pedidos.config";

    private static final Logger log = LoggerFactory.getLogger(CentralPedidosConfigAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final SystemConfigPort configPort;

    public CentralPedidosConfigAdapter(SystemConfigPort configPort) {
        this.configPort = configPort;
    }

    @Override
    public Optional<CentralPedidosConfig> find() {
        return configPort.findByKey(CHAVE).map(SystemConfig::value).filter(v -> !v.isBlank()).flatMap(json -> {
            try {
                return Optional.of(MAPPER.readValue(json, CentralPedidosConfig.class));
            } catch (JsonProcessingException | RuntimeException e) {
                log.warn("central-pedidos.config ilegível, usando o padrão: {}", e.getMessage());
                return Optional.empty();
            }
        });
    }

    @Override
    public CentralPedidosConfig save(CentralPedidosConfig config, String updatedBy) {
        try {
            configPort.save(new SystemConfig(CHAVE, MAPPER.writeValueAsString(config), Instant.now(), updatedBy));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("configuração da Central de Pedidos não serializável", e);
        }
        return config;
    }
}
