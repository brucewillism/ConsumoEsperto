package com.consumoesperto.service;

import com.consumoesperto.dto.MatchResult;
import com.consumoesperto.util.ApelidoNormalizador;
import com.consumoesperto.util.StringFuzzy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Resolve termos digitados pelo utilizador contra nomes reais no cadastro (exato, parcial, Levenshtein ≤ 2).
 */
@Service
public class TextMatcherService {

    private static final int MAX_LEVENSHTEIN = 2;
    private static final int MIN_TERM_LENGTH = 1;

    public MatchResult resolverEntidade(String termoBuscado, Map<Long, String> nomesDisponiveis) {
        if (termoBuscado == null || termoBuscado.isBlank()) {
            return MatchResult.naoEncontrado("");
        }
        if (nomesDisponiveis == null || nomesDisponiveis.isEmpty()) {
            return MatchResult.naoEncontrado(termoBuscado);
        }

        String termoNorm = normalizarTexto(termoBuscado);
        if (termoNorm.length() < MIN_TERM_LENGTH) {
            return MatchResult.naoEncontrado(termoBuscado);
        }

        for (Map.Entry<Long, String> entry : nomesDisponiveis.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (normalizarTexto(entry.getValue()).equals(termoNorm)) {
                return MatchResult.exato(entry.getKey(), entry.getValue());
            }
        }

        List<Map.Entry<Long, String>> matchesParciais = new ArrayList<>();
        for (Map.Entry<Long, String> entry : nomesDisponiveis.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String nomeNorm = normalizarTexto(entry.getValue());
            if (nomeNorm.contains(termoNorm) || termoNorm.contains(nomeNorm)) {
                matchesParciais.add(entry);
            }
        }

        if (matchesParciais.size() == 1) {
            Map.Entry<Long, String> hit = matchesParciais.get(0);
            return MatchResult.parcial(hit.getKey(), hit.getValue());
        }
        if (matchesParciais.size() > 1) {
            return MatchResult.ambiguo(ordenarPorNome(matchesParciais), termoBuscado);
        }

        Map.Entry<Long, String> melhorMatch = null;
        int menorDistancia = Integer.MAX_VALUE;

        for (Map.Entry<Long, String> entry : nomesDisponiveis.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            int distancia = StringFuzzy.levenshtein(termoNorm, normalizarTexto(entry.getValue()));
            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                melhorMatch = entry;
            }
        }

        if (melhorMatch != null && menorDistancia <= MAX_LEVENSHTEIN) {
            return MatchResult.fuzzy(melhorMatch.getKey(), melhorMatch.getValue(), menorDistancia);
        }

        return MatchResult.naoEncontrado(termoBuscado);
    }

    private static List<Map.Entry<Long, String>> ordenarPorNome(List<Map.Entry<Long, String>> entries) {
        return entries.stream()
            .sorted(Comparator.comparing(e -> e.getValue() != null ? e.getValue() : ""))
            .toList();
    }

    private static String normalizarTexto(String texto) {
        return ApelidoNormalizador.normalizar(texto);
    }
}
