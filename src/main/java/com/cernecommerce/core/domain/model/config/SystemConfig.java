package com.cernecommerce.core.domain.model.config;

import java.io.Serializable;
import java.time.Instant;

public record SystemConfig(String key, String value, Instant updatedAt, String updatedBy) implements Serializable {

    public boolean asBoolean() {
        return Boolean.parseBoolean(value);
    }
}
