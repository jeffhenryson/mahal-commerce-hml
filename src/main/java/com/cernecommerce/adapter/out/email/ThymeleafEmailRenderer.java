package com.cernecommerce.adapter.out.email;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

/**
 * Renderiza {@code templates/email/<nome>.html}. Todo template recebe {@code ${store}} (marca da loja,
 * ver {@link EmailBranding}) além das variáveis do e-mail, e o locale pt-BR faz
 * {@code #numbers.formatCurrency} sair em R$.
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
}
