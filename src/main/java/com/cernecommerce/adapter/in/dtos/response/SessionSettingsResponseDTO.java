package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.pdv.SessionSettings;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;

public record SessionSettingsResponseDTO(String vasoPadraoCodigo, String vasoGrandeCodigo,
        BigDecimal upgradeVasoGrandePreco, List<DayOfWeek> diasDuploRosh, BigDecimal upgradeCarvaoJumboPreco) {

    public static SessionSettingsResponseDTO of(SessionSettings s) {
        return new SessionSettingsResponseDTO(s.vasoPadraoCodigo(), s.vasoGrandeCodigo(), s.upgradeVasoGrandePreco(),
                s.diasDuploRosh().stream().sorted().toList(), s.upgradeCarvaoJumboPreco());
    }
}
