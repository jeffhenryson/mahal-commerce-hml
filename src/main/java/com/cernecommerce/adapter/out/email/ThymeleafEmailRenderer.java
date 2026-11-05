package com.cernecommerce.adapter.out.email;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

/**
 * Renderiza {@code templates/email/<nome>.html}. Todo template recebe {@code ${store}} (marca da loja,
 * ver {@link EmailBranding}) e {@code ${theme}} (ver {@link EmailTheme}) além das variáveis do e-mail,
 * e o locale pt-BR faz {@code #numbers.formatCurrency} sair em R$. {@code ${preheader}} é opcional: é o
 * texto que a caixa de entrada mostra ao lado do assunto.
 */
@Component
class ThymeleafEmailRenderer {

    private final TemplateEngine templateEngine;
    private final EmailBranding branding;

    ThymeleafEmailRenderer(TemplateEngine templateEngine, EmailBranding branding) {
        this.templateEngine = templateEngine;
        this.branding = branding;
    }

    String render(String templateName, Map<String, Object> variables) {
        Context ctx = new Context(Locale.forLanguageTag("pt-BR"));
        ctx.setVariables(variables);
        ctx.setVariable("theme", EmailTheme.DEFAULT);
        if (!variables.containsKey("store")) {
            ctx.setVariable("store", branding.brand());
        }
        return templateEngine.process("email/" + templateName, ctx);
    }

    String storeName() {
        return branding.brand().name();
    }

    String appUrl(String path) {
        return branding.appUrl(path);
    }

    String marketUrl(String path) {
        return branding.marketUrl(path);
    }

    String whatsappUrl(String message) {
        return branding.whatsappUrl(message);
    }
}
