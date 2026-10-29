package com.cernecommerce.core.domain.model.pedido;

import com.cernecommerce.core.domain.exception.pedido.InvalidDeliveryException;
import com.cernecommerce.core.domain.exception.pedido.InvalidTrackingUrlException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Entrega ou retirada de uma venda de balcão (PDV-F022) ou de um pedido do app (ECM-F009).
 *
 * <p>Mora em tabela própria ({@code order_delivery}), fiel à regra do {@link Order} de que endereço
 * e frete ficam fora de {@code sales_order} — só a venda que tem entrega tem a linha.</p>
 *
 * <h2>Taxa fora do líquido</h2>
 * <p>{@link #fee} compõe o que o cliente paga ({@link Order#totalPayable()}), mas não o
 * {@code netAmount}: o líquido é a receita da mercadoria, somada em quatro agregações, e o frete é
 * em boa parte repasse (motoboy, corrida da 99, postagem). Mesma decisão da taxa de serviço da
 * mesa (PDV-F015, V118). A taxa é congelada na venda: o pagamento já foi validado contra ela.</p>
 *
 * <p>Os campos de cada método ({@code courier*} para motoboy, {@code pickupCode}/
 * {@code dropoffCode} para a 99, {@code trackingCode} para os Correios) são todos opcionais e
 * podem chegar depois, pelo {@code PATCH /orders/{id}/delivery}.</p>
 *
 * @param trackingUrl ECM-F013 — link para acompanhar a corrida (Uber, 99…), mandado ao cliente na saída.
 *                    Só http(s), até {@value #TRACKING_URL_MAX} caracteres.
 */
public record OrderDelivery(
        DeliveryType type,
        DeliveryAddress address,
        DeliveryMethod method,
        String courierName,
        String courierPhone,
        String pickupCode,
        String dropoffCode,
        String trackingCode,
        BigDecimal fee,
        String trackingUrl) {

    public static final int TRACKING_URL_MAX = 500;

    /** Sem link da corrida — a forma de antes do ECM-F013. */
    public OrderDelivery(DeliveryType type, DeliveryAddress address, DeliveryMethod method, String courierName,
            String courierPhone, String pickupCode, String dropoffCode, String trackingCode, BigDecimal fee) {
        this(type, address, method, courierName, courierPhone, pickupCode, dropoffCode, trackingCode, fee, null);
    }

    public OrderDelivery {
        if (type == null) {
            throw new InvalidDeliveryException("type da entrega é obrigatório (RETIRADA ou ENTREGA)");
        }
        courierName = DeliveryAddress.blankToNull(courierName);
        courierPhone = DeliveryAddress.blankToNull(courierPhone);
        pickupCode = DeliveryAddress.blankToNull(pickupCode);
        dropoffCode = DeliveryAddress.blankToNull(dropoffCode);
        trackingCode = DeliveryAddress.blankToNull(trackingCode);
        trackingUrl = validarTrackingUrl(DeliveryAddress.blankToNull(trackingUrl));
        fee = fee == null ? BigDecimal.ZERO.setScale(2) : fee.setScale(2, RoundingMode.HALF_UP);
        if (fee.signum() < 0) {
            throw new InvalidDeliveryException("taxa de entrega não pode ser negativa");
        }
        if (type == DeliveryType.RETIRADA) {
            if (address != null || method != null || fee.signum() > 0 || courierName != null
                    || courierPhone != null || pickupCode != null || dropoffCode != null || trackingCode != null
                    || trackingUrl != null) {
                throw new InvalidDeliveryException(
                        "RETIRADA não tem endereço, método, entregador, códigos nem taxa");
            }
        } else if (address == null) {
            throw new InvalidDeliveryException("ENTREGA exige endereço");
        }
    }

    public boolean isEntrega() {
        return type == DeliveryType.ENTREGA;
    }

    /**
     * Aplica uma edição parcial (PDV-F022): campo {@code null} mantém o atual, campo em branco
     * apaga. Tipo e taxa não mudam depois da venda — o tipo decidiu o status do pedido e a taxa já
     * entrou no pagamento.
     */
    public OrderDelivery withPatch(Patch patch) {
        if (patch.fee() != null) {
            throw new InvalidDeliveryException("taxa de entrega não pode ser alterada depois da venda");
        }
        if (patch.type() != null && patch.type() != type) {
            throw new InvalidDeliveryException("tipo da entrega não pode ser alterado depois da venda");
        }
        DeliveryAddress newAddress = patch.address() == null ? address
                : address == null ? patch.address().toAddress() : address.merge(patch.address());
        return new OrderDelivery(type, newAddress,
                patch.method() == null ? method : patch.method(),
                DeliveryAddress.pick(patch.courierName(), courierName),
                DeliveryAddress.pick(patch.courierPhone(), courierPhone),
                DeliveryAddress.pick(patch.pickupCode(), pickupCode),
                DeliveryAddress.pick(patch.dropoffCode(), dropoffCode),
                DeliveryAddress.pick(patch.trackingCode(), trackingCode),
                fee,
                DeliveryAddress.pick(patch.trackingUrl(), trackingUrl));
    }

    /** ECM-F013 — o link da corrida, gravado quando o pedido sai para entrega. Nulo mantém o atual. */
    public OrderDelivery withTrackingUrl(String url) {
        if (url == null || url.isBlank()) {
            return this;
        }
        return new OrderDelivery(type, address, method, courierName, courierPhone, pickupCode, dropoffCode,
                trackingCode, fee, url);
    }

    private static String validarTrackingUrl(String url) {
        if (url == null) {
            return null;
        }
        String limpo = url.strip();
        if (limpo.length() > TRACKING_URL_MAX) {
            throw new InvalidTrackingUrlException("link da viagem passa de " + TRACKING_URL_MAX + " caracteres");
        }
        String minusculo = limpo.toLowerCase(java.util.Locale.ROOT);
        if (!(minusculo.startsWith("https://") || minusculo.startsWith("http://")) || limpo.contains(" ")) {
            throw new InvalidTrackingUrlException("link da viagem precisa começar com http:// ou https://");
        }
        return limpo;
    }

    /**
     * ECM-F010 — a taxa que o caixa informa ao aceitar o pedido do app. Não é o {@link #withPatch}
     * (que congela a taxa) porque aqui ela ainda não foi paga: quem garante isso é
     * {@link Order#withDeliveryFee}, que só aceita pedido de marketplace aguardando pagamento.
     */
    public OrderDelivery withFee(BigDecimal newFee) {
        if (!isEntrega()) {
            throw new InvalidDeliveryException("RETIRADA não tem taxa de entrega");
        }
        return new OrderDelivery(type, address, method, courierName, courierPhone, pickupCode, dropoffCode,
                trackingCode, newFee, trackingUrl);
    }

    /** Edição parcial da entrega; {@code null} mantém o valor atual. */
    public record Patch(DeliveryType type, DeliveryAddress.Patch address, DeliveryMethod method,
            String courierName, String courierPhone, String pickupCode, String dropoffCode,
            String trackingCode, BigDecimal fee, String trackingUrl) {

        /** Sem link da corrida — a forma de antes do ECM-F013. */
        public Patch(DeliveryType type, DeliveryAddress.Patch address, DeliveryMethod method, String courierName,
                String courierPhone, String pickupCode, String dropoffCode, String trackingCode, BigDecimal fee) {
            this(type, address, method, courierName, courierPhone, pickupCode, dropoffCode, trackingCode, fee, null);
        }
    }
}
