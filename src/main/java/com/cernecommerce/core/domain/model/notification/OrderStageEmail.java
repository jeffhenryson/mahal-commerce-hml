package com.cernecommerce.core.domain.model.notification;

import java.util.ArrayList;
import java.util.List;

/**
 * O e-mail de uma etapa do pedido. Separado da frase da notificação do app: num assunto, aquela frase
 * levava a chave Pix e o link da corrida.
 *
 * @param details linhas da caixa de destaque, como o total a pagar e a chave Pix
 * @param pickup mostra o endereço da loja na caixa de destaque
 * @param showItems mostra a lista de itens e valores
 * @param tracking mostra o botão para o pedido no app
 * @param rideUrl link da corrida; quando existe, o botão leva a ele em vez do pedido no app
 */
public record OrderStageEmail(
        String subject,
        String preheader,
        String title,
        String body,
        List<Detail> details,
        boolean pickup,
        boolean showItems,
        boolean tracking,
        String rideUrl) {

    public record Detail(String label, String value) { }

    public OrderStageEmail {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public boolean hasCallout() {
        return pickup || !details.isEmpty();
    }

    public static Builder builder(String subject, String preheader, String title, String body) {
        return new Builder(subject, preheader, title, body);
    }

    public static final class Builder {
        private final String subject;
        private final String preheader;
        private final String title;
        private final String body;
        private final List<Detail> details = new ArrayList<>();
        private boolean pickup;
        private boolean showItems;
        private boolean tracking;
        private String rideUrl;

        private Builder(String subject, String preheader, String title, String body) {
            this.subject = subject;
            this.preheader = preheader;
            this.title = title;
            this.body = body;
        }

        public Builder detail(String label, String value) {
            details.add(new Detail(label, value));
            return this;
        }

        public Builder pickup() {
            this.pickup = true;
            return this;
        }

        public Builder items() {
            this.showItems = true;
            return this;
        }

        public Builder tracking() {
            this.tracking = true;
            return this;
        }

        public Builder ride(String url) {
            this.rideUrl = url;
            return this;
        }

        public OrderStageEmail build() {
            return new OrderStageEmail(subject, preheader, title, body, details, pickup, showItems, tracking, rideUrl);
        }
    }
}
