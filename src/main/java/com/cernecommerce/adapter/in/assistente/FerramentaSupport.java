package com.cernecommerce.adapter.in.assistente;

import com.cernecommerce.adapter.in.dtos.response.AssistenteLinkDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

/** Utilitários das ferramentas: leitura do input do modelo, JSON de saída e formatação pt-BR. */
public final class FerramentaSupport {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(FUSO);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private FerramentaSupport() { }

    /** Input inválido do modelo (campo faltando, tipo errado). Volta a ele como erro de ferramenta. */
    public static class EntradaInvalida extends RuntimeException {
        public EntradaInvalida(String message) {
            super(message);
        }
    }

    public static String json(Object valor) {
        try {
            return PiiSanitizer.limpar(MAPPER.writeValueAsString(valor));
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao serializar o resultado da ferramenta", ex);
        }
    }

    public static ObjectNode objeto() {
        return MAPPER.createObjectNode();
    }

    public static String texto(JsonNode in, String campo, int max) {
        JsonNode v = in.get(campo);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isTextual()) {
            throw new EntradaInvalida("'" + campo + "' deve ser texto");
        }
        String s = v.asText().strip();
        if (s.isEmpty()) {
            return null;
        }
        if (s.length() > max) {
            throw new EntradaInvalida("'" + campo + "' passa de " + max + " caracteres");
        }
        return s;
    }

    public static String textoObrigatorio(JsonNode in, String campo, int max) {
        String s = texto(in, campo, max);
        if (s == null) {
            throw new EntradaInvalida("Informe '" + campo + "'");
        }
        return s;
    }

    public static Long numero(JsonNode in, String campo) {
        JsonNode v = in.get(campo);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.canConvertToLong() && v.isIntegralNumber()) {
            return v.asLong();
        }
        if (v.isTextual() && v.asText().strip().matches("\\d{1,18}")) {
            return Long.parseLong(v.asText().strip());
        }
        throw new EntradaInvalida("'" + campo + "' deve ser um número inteiro");
    }

    public static long numeroObrigatorio(JsonNode in, String campo) {
        Long n = numero(in, campo);
        if (n == null) {
            throw new EntradaInvalida("Informe '" + campo + "'");
        }
        return n;
    }

    public static Boolean booleano(JsonNode in, String campo) {
        JsonNode v = in.get(campo);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isBoolean()) {
            throw new EntradaInvalida("'" + campo + "' deve ser true ou false");
        }
        return v.asBoolean();
    }

    public static int limite(JsonNode in, int padrao, int maximo) {
        Long n = numero(in, "limite");
        return n == null ? padrao : (int) Math.max(1, Math.min(n, maximo));
    }

    public static <E extends Enum<E>> E enumOpcional(JsonNode in, String campo, Class<E> tipo) {
        String s = texto(in, campo, 40);
        if (s == null) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new EntradaInvalida("'" + campo + "' inválido: " + s);
        }
    }

    /** Data {@code AAAA-MM-DD} no fuso da loja. */
    public static LocalDate data(JsonNode in, String campo) {
        String s = texto(in, campo, 10);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException ex) {
            throw new EntradaInvalida("'" + campo + "' deve estar no formato AAAA-MM-DD");
        }
    }

    public static Instant inicioDoDia(LocalDate d) {
        return d.atStartOfDay(FUSO).toInstant();
    }

    public static Instant fimDoDia(LocalDate d) {
        return d.plusDays(1).atStartOfDay(FUSO).toInstant().minusMillis(1);
    }

    public static String dinheiro(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        return NumberFormat.getCurrencyInstance(PT_BR).format(valor.setScale(2, RoundingMode.HALF_UP))
                .replace(' ', ' ');
    }

    public static String quantidade(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMaximumFractionDigits(3);
        return f.format(valor);
    }

    public static String percentual(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMaximumFractionDigits(1);
        return f.format(valor) + "%";
    }

    public static String dataHora(Instant i) {
        return i == null ? "—" : DATA_HORA.format(i);
    }

    public static String data(LocalDate d) {
        return d == null ? "—" : DATA.format(d);
    }

    public static String ou(String valor) {
        return valor == null || valor.isBlank() ? "—" : valor;
    }

    public static AssistenteLinkDTO link(String rotulo, String rota) {
        return new AssistenteLinkDTO(rotulo, rota);
    }

    public static Authentication autenticacao() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /** Checagem antecipada só para a experiência; quem decide é o {@code @PreAuthorize} do controller. */
    public static boolean temPermissao(String... autoridades) {
        Authentication auth = autenticacao();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            for (String pedida : autoridades) {
                if (pedida.equals(a.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Lê o JSON dos parâmetros guardados de uma ação. */
    public static JsonNode ler(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Parâmetros da ação ilegíveis", ex);
        }
    }

    public static String schema(Map<String, Object> propriedades, String... obrigatorios) {
        try {
            return MAPPER.writeValueAsString(Map.of(
                    "type", "object",
                    "properties", propriedades,
                    "required", java.util.List.of(obrigatorios)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static Map<String, Object> str(String descricao) {
        return Map.of("type", "string", "description", descricao);
    }

    public static Map<String, Object> inteiro(String descricao) {
        return Map.of("type", "integer", "description", descricao);
    }

    public static Map<String, Object> bool(String descricao) {
        return Map.of("type", "boolean", "description", descricao);
    }

    public static Map<String, Object> enumeracao(String descricao, String... valores) {
        return Map.of("type", "string", "description", descricao, "enum", java.util.List.of(valores));
    }
}
