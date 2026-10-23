package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.ecommerce.PreferenciaPagamento;
import com.cernecommerce.core.domain.model.pedido.DeliveryMethod;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Escolhas do cliente no checkout do app (ECM-F009). Corpo opcional: sem ele o pedido é retirada
 * no balcão, como o front antigo chama. Não há taxa aqui — quem a define é o caixa, no aceite.
 */
@Data
public class ShopCheckoutRequest {

    @Schema(description = "RETIRADA (padrão) ou ENTREGA.", example = "ENTREGA")
    private DeliveryType deliveryType;

    @Valid
    @Schema(description = "Obrigatório em ENTREGA; ignorado em RETIRADA.")
    private DeliveryAddressRequest address;

    @Schema(description = "ENTREGA — MOTOBOY_LOJA, APP_99 ou UBER_FLASH.", example = "MOTOBOY_LOJA")
    private DeliveryMethod method;

    @Schema(description = "Como o cliente vai pagar no caixa: PIX, CREDITO, DEBITO ou DINHEIRO. Só intenção — "
            + "o pagamento real é lançado pelo operador. ENTREGA aceita só PIX (padrão).", example = "PIX")
    private PreferenciaPagamento paymentPreference;

    @Size(max = 500)
    @Schema(description = "Observação do cliente para a loja.", example = "Interfone 12, deixar na portaria")
    private String notes;
}
