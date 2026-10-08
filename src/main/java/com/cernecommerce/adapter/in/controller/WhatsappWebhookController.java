package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.ports.in.WhatsappIntegrationUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Webhook da WhatsApp Cloud API — é a Meta chamando, sem sessão de usuário (rota pública em
 * {@code SecurityConfig}, exceção registrada em {@code SecurityArchitectureTest}).
 *
 * <ul>
 *   <li>{@code GET}: handshake de verificação — devolve {@code hub.challenge} só quando o
 *       {@code hub.verify_token} confere com o salvo na tela de Integrações.</li>
 *   <li>{@code POST}: notificações. A defesa é a assinatura {@code X-Hub-Signature-256}
 *       (HMAC-SHA256 do corpo com o app secret, {@code whatsapp.app-secret}); sem segredo
 *       configurado, todo POST é recusado. Por enquanto só registra os status de entrega.</li>
 * </ul>
 */
@RestController
@RequestMapping("/webhooks/whatsapp")
@Tag(name = "Webhook do WhatsApp", description = "Notificações da WhatsApp Cloud API — público, assinado pela Meta")
public class WhatsappWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsappWebhookController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final WhatsappIntegrationUseCase whatsappIntegrationUseCase;
    private final byte[] appSecret;

    public WhatsappWebhookController(WhatsappIntegrationUseCase whatsappIntegrationUseCase,
            @Value("${whatsapp.app-secret:}") String appSecret) {
        this.whatsappIntegrationUseCase = whatsappIntegrationUseCase;
        this.appSecret = appSecret == null || appSecret.isBlank() ? null : appSecret.strip().getBytes(StandardCharsets.UTF_8);
    }

    @Operation(summary = "Verificação do webhook pela Meta",
            description = "200 com hub.challenge quando hub.verify_token confere; 403 caso contrário.")
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if ("subscribe".equals(mode) && challenge != null && whatsappIntegrationUseCase.matchesVerifyToken(verifyToken)) {
            return ResponseEntity.ok(challenge);
        }
        log.warn("whatsapp.webhook.verify.rejected mode={}", mode);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @Operation(summary = "Notificação da WhatsApp Cloud API",
            description = "Valida X-Hub-Signature-256; 401 sem assinatura válida. Registra os status de entrega.")
    @PostMapping
    public ResponseEntity<Void> receive(@RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody(required = false) byte[] body) {
        byte[] payload = body == null ? new byte[0] : body;
        if (!validSignature(signature, payload)) {
            log.warn("whatsapp.webhook.signature.rejected configured={}", appSecret != null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        logStatuses(payload);
        return ResponseEntity.ok().build();
    }

    private boolean validSignature(String signature, byte[] payload) {
        if (appSecret == null || signature == null || !signature.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret, "HmacSHA256"));
            byte[] expected = mac.doFinal(payload);
            byte[] received = HexFormat.of().parseHex(signature.substring(SIGNATURE_PREFIX.length()));
            return MessageDigest.isEqual(expected, received);
        } catch (IllegalArgumentException ex) {
            return false;
        } catch (Exception ex) {
            log.error("whatsapp.webhook.signature.error error={}", ex.toString());
            return false;
        }
    }

    /** {@code entry[].changes[].value.statuses[]} — um log por status de entrega. */
    private static void logStatuses(byte[] payload) {
        try {
            JsonNode root = MAPPER.readTree(payload);
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    for (JsonNode status : change.path("value").path("statuses")) {
                        JsonNode error = status.path("errors").path(0);
                        log.info("whatsapp.webhook.status id={} status={} error={}",
                                status.path("id").asText(null), status.path("status").asText(null),
                                error.isMissingNode() ? null : error.path("title").asText(null));
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("whatsapp.webhook.payload.unreadable error={}", ex.toString());
        }
    }
}
