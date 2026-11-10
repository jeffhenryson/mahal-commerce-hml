package com.cernecommerce.adapter.in.assistente;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remove dados pessoais de texto livre antes de gravar e antes de enviar ao modelo: CPF, CNPJ,
 * e-mail, telefone, CEP e endereço (logradouro + número).
 *
 * <p>É a segunda camada. A primeira é a projeção das ferramentas, que nem copia esses campos dos
 * DTOs. Endereço por regex é melhor esforço: pega "Rua X, 123", não um endereço sem tipo de
 * logradouro. Números soltos de 8, 9, 12 e 13 dígitos (SKU, código de barras, pedido) ficam.</p>
 */
public final class PiiSanitizer {

    private record Regra(Pattern padrao, String substituto) { }

    private static final List<Regra> REGRAS = List.of(
            new Regra(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[e-mail removido]"),
            new Regra(Pattern.compile("(?<!\\d)\\d{2}\\.?\\d{3}\\.?\\d{3}/\\d{4}-?\\d{2}(?!\\d)"), "[CNPJ removido]"),
            new Regra(Pattern.compile("(?<![\\d.])\\d{3}\\.\\d{3}\\.\\d{3}-?\\d{2}(?![\\d])"), "[CPF removido]"),
            new Regra(Pattern.compile("(?<!\\d)\\d{9}-\\d{2}(?!\\d)"), "[CPF removido]"),
            new Regra(Pattern.compile("(?<!\\d)\\d{5}-\\d{3}(?!\\d)"), "[CEP removido]"),
            // Telefone com separador: +55 (11) 98765-4321, (11) 8765-4321, 11 98765 4321.
            new Regra(Pattern.compile("(?<![\\d\\w])(?:\\+?55[\\s.-]?)?\\(?\\d{2}\\)?[\\s.-]?9?\\d{4}[\\s.-]\\d{4}(?!\\d)"),
                    "[telefone removido]"),
            // Dígitos corridos: 14 = CNPJ; 10 ou 11 = telefone com DDD ou CPF; 12/13 com 55 na frente = telefone.
            new Regra(Pattern.compile("(?<![\\d\\w])\\d{14}(?!\\d)"), "[CNPJ removido]"),
            new Regra(Pattern.compile("(?<![\\d\\w])\\+55\\d{10,11}(?!\\d)"), "[telefone removido]"),
            new Regra(Pattern.compile("(?<![\\d\\w])\\d{10,11}(?!\\d)"), "[documento removido]"),
            new Regra(Pattern.compile("(?iu)\\b(?:rua|r\\.|avenida|av\\.?|travessa|tv\\.|alameda|al\\.|rodovia|rod\\.|"
                    + "estrada|praça|praca|largo|quadra|qd\\.?)\\s+[\\p{L}\\p{M}0-9 .'-]{2,60}?,?\\s*(?:n[º°o.]?\\s*)?\\d{1,5}\\b"),
                    "[endereço removido]"));

    private PiiSanitizer() { }

    public static String limpar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        String resultado = texto;
        for (Regra regra : REGRAS) {
            Matcher m = regra.padrao().matcher(resultado);
            if (m.find()) {
                resultado = m.replaceAll(Matcher.quoteReplacement(regra.substituto()));
            }
        }
        return resultado;
    }
}
