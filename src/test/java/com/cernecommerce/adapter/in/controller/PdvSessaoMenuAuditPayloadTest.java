package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.pdv.SessionAddon;
import com.cernecommerce.core.domain.model.pdv.SessionAssetType;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDV-C043 — o {@code SESSION_MENU_CHANGED} gravava só {@code target} e {@code id}: dava para saber
 * que a faixa 2 mudou, mas não para quanto foi o preço. O preço de toda sessão sai da faixa e do
 * adicional, então o evento passa a levar o estado gravado.
 */
class PdvSessaoMenuAuditPayloadTest {

    @Test
    void tierPayload_carriesNameAndPriceAndActive() {
        Map<String, Object> payload = PdvSessaoController.menuPayload("faixa",
                new SessionTier(2L, "Premium", new BigDecimal("32.00"), "Luk, Nay", 2, false));

        assertThat(payload).containsEntry("target", "faixa").containsEntry("id", "2")
                .containsEntry("nome", "Premium").containsEntry("preco", new BigDecimal("32.00"))
                .containsEntry("ativo", false);
    }

    @Test
    void addonPayload_carriesNameAndPrice() {
        Map<String, Object> payload = PdvSessaoController.menuPayload("adicional",
                new SessionAddon(1L, "Filtro de gelo", new BigDecimal("6.00"), 1, true));

        assertThat(payload).containsEntry("preco", new BigDecimal("6.00")).containsEntry("ativo", true);
    }

    @Test
    void assetPayload_carriesTheQuantity() {
        Map<String, Object> payload = PdvSessaoController.menuPayload("utensilio",
                new SessionAssetType(3L, "PINCA", "Pinça", 0, true, true));

        assertThat(payload).containsEntry("codigo", "PINCA").containsEntry("quantidadeTotal", 0)
                .containsEntry("incluso", true);
    }

    @Test
    void settingsPayload_carriesUpgradePriceAndDuploDays_andSkipsNullCodes() {
        Map<String, Object> payload = PdvSessaoController.menuPayload("config",
                new SessionSettings("VASO_P", null, new BigDecimal("10.00"), Set.of(DayOfWeek.WEDNESDAY)));

        assertThat(payload).containsEntry("id", "1").containsEntry("vasoPadraoCodigo", "VASO_P")
                .containsEntry("upgradeVasoGrandePreco", new BigDecimal("10.00"))
                .doesNotContainKey("vasoGrandeCodigo");
        assertThat(payload.get("diasDuploRosh").toString()).contains("WEDNESDAY");
    }
}
