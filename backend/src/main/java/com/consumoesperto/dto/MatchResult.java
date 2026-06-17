package com.consumoesperto.dto;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resultado da resolução aproximada de um termo contra entidades do utilizador.
 */
@Getter
public class MatchResult {

    public enum NivelConfianca {
        EXATO, PARCIAL, FUZZY, AMBIGUO, NAO_ENCONTRADO
    }

    private final Long idResolvido;
    private final String nomeNormalizado;
    private final String termoBuscado;
    private final NivelConfianca confianca;
    private final int distanciaLevenshtein;
    private final List<Map.Entry<Long, String>> opcoes;

    private MatchResult(
        Long idResolvido,
        String nomeNormalizado,
        String termoBuscado,
        NivelConfianca confianca,
        int distanciaLevenshtein,
        List<Map.Entry<Long, String>> opcoes
    ) {
        this.idResolvido = idResolvido;
        this.nomeNormalizado = nomeNormalizado;
        this.termoBuscado = termoBuscado;
        this.confianca = confianca;
        this.distanciaLevenshtein = distanciaLevenshtein;
        this.opcoes = opcoes != null ? List.copyOf(opcoes) : List.of();
    }

    public static MatchResult exato(Long id, String nome) {
        return new MatchResult(id, nome, null, NivelConfianca.EXATO, 0, null);
    }

    public static MatchResult parcial(Long id, String nome) {
        return new MatchResult(id, nome, null, NivelConfianca.PARCIAL, 0, null);
    }

    public static MatchResult fuzzy(Long id, String nome, int distancia) {
        return new MatchResult(id, nome, null, NivelConfianca.FUZZY, distancia, null);
    }

    public static MatchResult ambiguo(List<Map.Entry<Long, String>> opcoes, String termoBuscado) {
        return new MatchResult(null, null, termoBuscado, NivelConfianca.AMBIGUO, 0, opcoes);
    }

    public static MatchResult naoEncontrado(String termoBuscado) {
        return new MatchResult(null, null, termoBuscado, NivelConfianca.NAO_ENCONTRADO, Integer.MAX_VALUE, null);
    }

    public boolean resolvido() {
        return idResolvido != null
            && (confianca == NivelConfianca.EXATO
            || confianca == NivelConfianca.PARCIAL
            || confianca == NivelConfianca.FUZZY);
    }

    public List<Long> idsOpcoes() {
        List<Long> ids = new ArrayList<>();
        for (Map.Entry<Long, String> e : opcoes) {
            ids.add(e.getKey());
        }
        return ids;
    }
}
