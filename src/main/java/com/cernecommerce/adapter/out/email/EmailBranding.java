package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.config.StoreProfile;
import com.cernecommerce.core.ports.in.StoreProfileUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Identidade da loja nos e-mails: nome, logo e rodapé vêm de Dados da loja ({@link StoreProfile}),
 * o mesmo cadastro do cupom. Loja sem perfil preenchido cai no nome {@value #DEFAULT_NAME}.
 *
 * <p>Lido a cada envio (são poucas chaves em {@code system_config}) para que editar o perfil valha
 * no próximo e-mail. {@link ObjectProvider} porque o perfil mora no core e o adapter é montado antes.</p>
 */
@Component
class EmailBranding {

    static final String DEFAULT_NAME = "Mahal";

    private static final Logger log = LoggerFactory.getLogger(EmailBranding.class);

    private final ObjectProvider<StoreProfileUseCase> storeProfile;
    private final String appBaseUrl;
    private final String marketBaseUrl;

    EmailBranding(ObjectProvider<StoreProfileUseCase> storeProfile,
            @Value("${email.app-base-url:http://localhost:4200}") String appBaseUrl,
            @Value("${email.market-base-url:}") String marketBaseUrl) {
        this.storeProfile = storeProfile;
        this.appBaseUrl = withoutTrailingSlash(appBaseUrl);
        this.marketBaseUrl = withoutTrailingSlash(marketBaseUrl);
    }

    /** O que os templates leem como {@code ${store}}. */
    record Brand(String name, String logoUrl, String address, String phone, String instagram, String website,
            String legalName, String cnpj, String receiptFooter) {

        /** Razão social e CNPJ como no cupom; {@code null} se a loja não preencheu nenhum. */
        public String legalLine() {
            if (legalName == null) {
                return cnpj == null ? null : "CNPJ " + cnpj;
            }
            return cnpj == null ? legalName : legalName + " · CNPJ " + cnpj;
        }
    }

    Brand brand() {
        StoreProfile profile = profile();
        String name = profile.tradeName() != null ? profile.tradeName() : DEFAULT_NAME;
        return new Brand(name, profile.logoUrl(), address(profile), profile.phone(), instagram(profile.instagram()),
                profile.website(), profile.legalName(), profile.cnpj(), profile.receiptFooter());
    }

    /** URL absoluta no painel para um caminho como {@code /app/pdv}; {@code null} se não houver caminho. */
    String appUrl(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return path.startsWith("http") ? path : appBaseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    /** URL absoluta no app do cliente; {@code null} sem {@code email.market-base-url}, e o e-mail sai sem o link. */
    String marketUrl(String path) {
        return marketBaseUrl.isBlank() ? null : marketBaseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    /**
     * Conversa no WhatsApp da loja, já com {@code message} digitada. O perfil não tem campo próprio de
     * WhatsApp: usa o telefone. {@code null} se ele não parecer um número brasileiro.
     */
    String whatsappUrl(String message) {
        String phone = profile().phone();
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "").replaceFirst("^0+", "");
        if (digits.length() == 10 || digits.length() == 11) {
            digits = "55" + digits;
        }
        if (!digits.matches("55\\d{10,11}")) {
            return null;
        }
        return "https://wa.me/" + digits + "?text="
                + URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String withoutTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private StoreProfile profile() {
        try {
            StoreProfileUseCase useCase = storeProfile.getIfAvailable();
            return useCase == null ? StoreProfile.empty() : useCase.get();
        } catch (Exception ex) {
            // Marca é enfeite: e-mail sem o perfil da loja ainda tem que sair.
            log.warn("email.branding.profile.failed error={}", ex.getMessage());
            return StoreProfile.empty();
        }
    }

    private static String address(StoreProfile p) {
        List<String> parts = new ArrayList<>();
        if (p.addressLine1() != null) {
            parts.add(p.addressLine1());
        }
        if (p.addressLine2() != null) {
            parts.add(p.addressLine2());
        }
        String cityState = p.city() == null ? p.state() : (p.state() == null ? p.city() : p.city() + " - " + p.state());
        if (cityState != null) {
            parts.add(cityState);
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String instagram(String handle) {
        if (handle == null) {
            return null;
        }
        String h = handle.replaceFirst("^https?://(www\\.)?instagram\\.com/", "").replaceAll("/+$", "");
        return h.startsWith("@") ? h : "@" + h;
    }
}
