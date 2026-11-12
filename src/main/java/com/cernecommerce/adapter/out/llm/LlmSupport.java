package com.cernecommerce.adapter.out.llm;

import com.cernecommerce.core.domain.exception.assistente.AssistenteProvedorException;
import com.cernecommerce.core.domain.model.config.ProvedorIa;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.FerramentaDef;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/** Apoio comum aos clientes de IA: cache de cliente por chave, schema das ferramentas e erros. */
final class LlmSupport {

    private static final Logger log = LoggerFactory.getLogger(LlmSupport.class);
    static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmSupport() { }

    /**
     * Clientes HTTP do SDK, um por chave (pelo hash: a chave nunca vira chave de mapa). A loja pode
     * trocar a chave a qualquer momento; passando de {@code MAX}, os antigos são fechados.
     */
    static final class Clientes<C> {

        private static final int MAX = 4;

        private final Map<String, C> porChave = new ConcurrentHashMap<>();
        private final Function<String, C> criar;
        private final Consumer<C> fechar;

        Clientes(Function<String, C> criar, Consumer<C> fechar) {
            this.criar = criar;
            this.fechar = fechar;
        }

        C de(String apiKey) {
            String chave = hash(apiKey);
            C existente = porChave.get(chave);
            if (existente != null) {
                return existente;
            }
            if (porChave.size() >= MAX) {
                fecharTodos();
            }
            return porChave.computeIfAbsent(chave, k -> criar.apply(apiKey));
        }

        void fecharTodos() {
            porChave.values().forEach(c -> {
                try {
                    fechar.accept(c);
                } catch (RuntimeException ignored) {
                    // Melhor esforço.
                }
            });
            porChave.clear();
        }
    }

    static String hash(String apiKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** O JSON Schema da ferramenta como mapa ({@code type}, {@code properties}, {@code required}). */
    static Map<String, Object> schema(FerramentaDef def) {
        try {
            return MAPPER.readValue(def.schemaJson(), new TypeReference<>() { });
        } catch (Exception ex) {
            throw new IllegalStateException("Schema inválido na ferramenta " + def.nome(), ex);
        }
    }

    static AssistenteProvedorException chaveRecusada(ProvedorIa p, Throwable causa) {
        log.warn("assistente.llm.chave-recusada provedor={}", p);
        return new AssistenteProvedorException(p.rotulo() + " recusou a chave da API.", true, causa);
    }

    static AssistenteProvedorException porStatus(ProvedorIa p, int status, Throwable causa) {
        String msg = switch (status) {
            case 401, 403 -> p.rotulo() + " recusou a chave da API.";
            case 400 -> p.rotulo() + " recusou a requisição (verifique o modelo configurado).";
            case 404 -> "Modelo não encontrado em " + p.rotulo() + ".";
            case 429 -> p.rotulo() + " está limitando as requisições (ou a cota acabou). Tente em instantes.";
            default -> p.rotulo() + " está indisponível. Tente em instantes.";
        };
        return new AssistenteProvedorException(msg, status == 401 || status == 403, causa);
    }

    static AssistenteProvedorException semConexao(ProvedorIa p, Throwable causa) {
        log.warn("assistente.llm.io provedor={} erro={}", p, causa.getMessage());
        return new AssistenteProvedorException("Não foi possível falar com " + p.rotulo() + ".", false, causa);
    }

    static AssistenteProvedorException desconhecido(ProvedorIa p, Throwable causa) {
        log.error("assistente.llm.falha provedor={}", p, causa);
        return new AssistenteProvedorException("Falha ao falar com " + p.rotulo() + ".", false, causa);
    }
}
