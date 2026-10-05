package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.config.StoreProfile;
import com.cernecommerce.core.ports.in.StoreProfileUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    EmailBranding(ObjectProvider<StoreProfileUseCase> storeProfile,
            @Value("${email.app-base-url:http://localhost:4200}") String appBaseUrl) {
        this.storeProfile = storeProfile;
        this.appBaseUrl = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
    }

    /** O que os templates leem como {@code ${store}}. */
    record Brand(String name, String logoUrl, String address, String phone, String instagram, String website) { }

    Brand brand() {
        StoreProfile profile = profile();
        String name = profile.tradeName() != null ? profile.tradeName() : DEFAULT_NAME;
        return new Brand(name, profile.logoUrl(), address(profile), profile.phone(), instagram(profile.instagram()),
                profile.website());
    }

    /** URL absoluta no painel para um caminho como {@code /app/pdv}; {@code null} se não houver caminho. */
    String appUrl(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return path.startsWith("http") ? path : appBaseUrl + (path.startsWith("/") ? path : "/" + path);
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
