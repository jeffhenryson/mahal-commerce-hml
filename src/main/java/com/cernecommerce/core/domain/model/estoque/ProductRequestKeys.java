package com.cernecommerce.core.domain.model.estoque;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Chave de busca e similaridade dos pedidos de clientes, no Java e não no banco: o {@code pg_trgm} não está
 * instalado e, em dev, o Flyway fica desligado (o schema sai do Hibernate), então nem {@code unaccent} nem
 * {@code similarity()} existiriam lá. Os itens abertos são poucos, e comparar todos em memória é barato.
 *
 * <p>{@link #similarity} imita o {@code similarity()} do {@code pg_trgm}: trigramas de cada palavra com dois
 * espaços antes e um depois, e índice de Jaccard entre os conjuntos. Assim o limiar de 0.35 do contrato
 * significa o mesmo que significaria no Postgres.</p>
 */
public final class ProductRequestKeys {

    /** Limiar do contrato para sugerir juntar a um item aberto. */
    public static final double SIMILARITY_THRESHOLD = 0.35;

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final int MAX_LENGTH = 400;

    private ProductRequestKeys() {
    }

    /** Descrição, marca e variação juntas, minúsculas, sem acento e com um espaço entre as palavras. */
    public static String normalize(String description, String brand, String variation) {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[] {description, brand, variation}) {
            if (part != null && !part.isBlank()) {
                sb.append(part).append(' ');
            }
        }
        String key = normalizeText(sb.toString());
        return key.length() > MAX_LENGTH ? key.substring(0, MAX_LENGTH).trim() : key;
    }

    /** Mesma normalização para um texto solto (o {@code q} da busca). */
    public static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String noAccents = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return NOT_ALNUM.matcher(noAccents.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    /** Similaridade de trigramas entre duas chaves já normalizadas, de 0 a 1. */
    public static double similarity(String a, String b) {
        Set<String> ta = trigrams(a);
        Set<String> tb = trigrams(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(ta);
        intersection.retainAll(tb);
        int union = ta.size() + tb.size() - intersection.size();
        return (double) intersection.size() / union;
    }

    static Set<String> trigrams(String key) {
        Set<String> out = new HashSet<>();
        if (key == null || key.isBlank()) {
            return out;
        }
        for (String word : key.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String padded = "  " + word + " ";
            for (int i = 0; i + 3 <= padded.length(); i++) {
                out.add(padded.substring(i, i + 3));
            }
        }
        return out;
    }
}
