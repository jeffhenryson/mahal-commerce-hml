package com.cernecommerce.core.domain.model.pedido;

import com.cernecommerce.core.domain.exception.pedido.InvalidDeliveryException;

/**
 * Endereço de uma {@link DeliveryType#ENTREGA} (PDV-F022), congelado no pedido — o cliente mudar
 * de endereço depois não reescreve para onde a venda foi.
 */
public record DeliveryAddress(
        String street,
        String number,
        String complement,
        String zipCode,
        String district,
        String city,
        String state,
        String country,
        String reference,
        Double latitude,
        Double longitude) {

    /** Sem coordenadas (anterior à pendência 10 §2). */
    public DeliveryAddress(String street, String number, String complement, String zipCode, String district,
            String city, String state, String country, String reference) {
        this(street, number, complement, zipCode, district, city, state, country, reference, null, null);
    }

    public DeliveryAddress {
        street = blankToNull(street);
        number = blankToNull(number);
        complement = blankToNull(complement);
        zipCode = blankToNull(zipCode);
        district = blankToNull(district);
        city = blankToNull(city);
        state = blankToNull(state);
        country = blankToNull(country);
        reference = blankToNull(reference);
        if (street == null || number == null || city == null || state == null) {
            throw new InvalidDeliveryException("endereço de entrega exige street, number, city e state");
        }
        // Pendência 10 §2 — as duas juntas, e dentro do globo. Uma sem a outra não marca lugar nenhum.
        if ((latitude == null) != (longitude == null)) {
            throw new InvalidDeliveryException("latitude e longitude vêm juntas, ou nenhuma das duas");
        }
        if (latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            throw new InvalidDeliveryException("coordenadas fora do intervalo (latitude -90..90, longitude -180..180)");
        }
    }

    /** Sobrepõe os campos não nulos de {@code patch}; campo em branco apaga o valor. */
    public DeliveryAddress merge(DeliveryAddress.Patch patch) {
        if (patch == null) {
            return this;
        }
        DeliveryAddress texto = new DeliveryAddress(
                pick(patch.street(), street), pick(patch.number(), number), pick(patch.complement(), complement),
                pick(patch.zipCode(), zipCode), pick(patch.district(), district), pick(patch.city(), city),
                pick(patch.state(), state), pick(patch.country(), country), pick(patch.reference(), reference));
        // Coordenadas novas vencem; sem elas, as antigas só valem se o endereço não mudou de lugar.
        if (patch.latitude() != null) {
            return texto.withCoordinates(patch.latitude(), patch.longitude());
        }
        boolean mesmoLugar = java.util.Objects.equals(texto.street(), street)
                && java.util.Objects.equals(texto.number(), number)
                && java.util.Objects.equals(texto.zipCode(), zipCode)
                && java.util.Objects.equals(texto.district(), district)
                && java.util.Objects.equals(texto.city(), city)
                && java.util.Objects.equals(texto.state(), state);
        return mesmoLugar ? texto.withCoordinates(latitude, longitude) : texto;
    }

    public DeliveryAddress withCoordinates(Double novaLatitude, Double novaLongitude) {
        return new DeliveryAddress(street, number, complement, zipCode, district, city, state, country, reference,
                novaLatitude, novaLongitude);
    }

    /** Edição parcial do endereço: {@code null} mantém o valor atual. */
    public record Patch(String street, String number, String complement, String zipCode, String district,
            String city, String state, String country, String reference, Double latitude, Double longitude) {

        public Patch(String street, String number, String complement, String zipCode, String district,
                String city, String state, String country, String reference) {
            this(street, number, complement, zipCode, district, city, state, country, reference, null, null);
        }

        /** Endereço completo a partir do patch — usado quando o pedido ainda não tinha endereço. */
        DeliveryAddress toAddress() {
            return new DeliveryAddress(street, number, complement, zipCode, district, city, state, country,
                    reference, latitude, longitude);
        }
    }

    static String pick(String patched, String current) {
        return patched == null ? current : patched;
    }

    static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
