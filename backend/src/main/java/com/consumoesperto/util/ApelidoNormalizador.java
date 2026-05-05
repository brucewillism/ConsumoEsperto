package com.consumoesperto.util;

import java.text.Normalizer;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Normalização insensível a maiúsculas/acentos para busca por apelido/nome.
 */
public final class ApelidoNormalizador {

    private ApelidoNormalizador() {
    }

    public static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase();
    }

    /**
     * Mesma regra usada em cartões: exato (nome), depois parcial por contains no nome extraído.
     */
    public static <T> List<T> filtrarPorNomeNormalizado(List<T> itens, Function<T, String> nomeExtractor, String apelidoBusca) {
        String token = normalizar(apelidoBusca);
        if (token.length() < 2) {
            return List.of();
        }
        List<T> exatos = itens.stream()
            .filter(t -> normalizar(nomeExtractor.apply(t)).equals(token))
            .collect(Collectors.toList());
        if (exatos.size() == 1) {
            return exatos;
        }
        if (exatos.size() > 1) {
            return exatos;
        }
        List<T> parcial = itens.stream()
            .filter(t -> {
                String n = normalizar(nomeExtractor.apply(t));
                return n.contains(token);
            })
            .collect(Collectors.toList());
        if (parcial.size() == 1) {
            return parcial;
        }
        if (parcial.size() > 1) {
            return parcial;
        }
        return List.of();
    }
}
