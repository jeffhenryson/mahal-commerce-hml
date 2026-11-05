package com.cernecommerce.adapter.out.email;

/**
 * Cores e estilos repetidos dos e-mails, lidos nos templates como {@code ${theme}}. E-mail não aceita
 * folha de estilo confiável, então o CSS vai inline; aqui fica o único lugar onde a paleta é definida.
 *
 * <p>{@code brand} é o acento padrão e, ao contrário de {@code caramel}, tem contraste para texto
 * sobre fundo claro. {@code success}, {@code warning} e {@code danger} só indicam estado.</p>
 */
public record EmailTheme(
        String wine,
        String cream,
        String caramel,
        String brand,
        String success,
        String warning,
        String danger,
        String page,
        String card,
        String surface,
        String line,
        String text,
        String muted) {

    static final EmailTheme DEFAULT = new EmailTheme(
            "#2e1312", "#ffebdb", "#d2b096", "#8a5a3c",
            "#15803d", "#b45309", "#b91c1c",
            "#f4ece4", "#fffaf5", "#f6eee6", "#eadfd5", "#3b2a26", "#75645c");

    /** Título principal do e-mail, na cor da marca. */
    public String title() {
        return title(wine);
    }

    public String title(String color) {
        return "margin:0 0 16px;font-size:22px;line-height:1.3;color:" + color;
    }

    /** Caixa de destaque, com a barra lateral na cor indicada. */
    public String callout(String color) {
        return "margin:20px 0;padding:12px 16px;border-radius:0 6px 6px 0;background:" + surface
                + ";border-left:4px solid " + color;
    }

    /** Texto miúdo do fim do e-mail: validade do link, "se não foi você". */
    public String note() {
        return "margin:20px 0 0;font-size:13px;line-height:1.5;color:" + muted;
    }
}
