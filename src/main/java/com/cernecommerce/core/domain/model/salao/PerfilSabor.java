package com.cernecommerce.core.domain.model.salao;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Perfil de sabor da essência, marcado pela casa por SKU — é o filtro "Gelado", "Cítrico"… do QR.
 * Lista fixa; a ordem do enum é a ordem em que aparecem.
 */
public enum PerfilSabor {
    FRUTADO("uva", "grape", "morango", "strawberry", "melancia", "watermelon", "melao", "melon", "manga", "mango",
            "pessego", "peach", "maracuja", "passion", "abacaxi", "pineapple", "banana", "maca", "apple", "fruta",
            "fruit", "cereja", "cherry", "mirtilo", "blueberry", "framboesa", "raspberry", "kiwi", "goiaba", "coco",
            "acai", "tropical", "berry", "amora", "pera", "ameixa", "lichia", "pitaya"),
    CITRICO("limao", "lima", "laranja", "tangerina", "mexerica", "bergamota", "citr", "lemon", "lime", "orange",
            "grapefruit", "toranja"),
    GELADO("menta", "mint", "ice", "gelad", "freeze", "frost", "cool", "hortel", "polar", "glacial", "eucalipto"),
    DOCE("chiclete", "gum", "bubble", "baunilha", "vanilla", "caramel", "chocolate", "doce", "mel ", "honey",
            "cookie", "bolo", "cream", "creme", "pudim", "candy", "marshmallow", "algodao"),
    FLORAL("rosa", "rose", "jasmim", "jasmine", "lavanda", "lavender", "floral", "violeta", "hibisco", "flor"),
    ESPECIARIAS("canela", "cinnamon", "cravo", "chai", "anis", "cardamomo", "gengibre", "ginger", "pimenta",
            "especiaria");

    /** Pedaços do nome que sugerem o perfil — as mesmas palavras do admin ({@code sugerirPerfis}). */
    private final List<String> palavras;

    PerfilSabor(String... palavras) {
        this.palavras = List.of(palavras);
    }

    /**
     * Perfis sugeridos pelo nome do sabor/produto, na ordem do enum. É só o ponto de partida para o
     * SKU que a casa ainda não marcou: marcou, vale o que ela marcou.
     */
    public static List<PerfilSabor> sugerir(String texto) {
        if (texto == null || texto.isBlank()) return List.of();
        String t = " " + Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT) + " ";
        return Arrays.stream(values()).filter(p -> p.palavras.stream().anyMatch(t::contains)).toList();
    }
}
