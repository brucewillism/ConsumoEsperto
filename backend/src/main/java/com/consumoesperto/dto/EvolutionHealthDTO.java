package com.consumoesperto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class EvolutionHealthDTO {

    private int sessoesAtivas;
    private int sessoesDesconectadas;
    private long mensagensHoje;
    private int reconexoesHoje;
    private int falhasHoje;
    private long latenciaMediaMs;
    private long latenciaP95Ms;
    private Instant coletadoEm;

    /** Detalhe opcional (?detalhe=true). */
    private List<EvolutionSessaoDetalheDTO> sessoes = new ArrayList<>();
    private List<String> sessoesInstaveis = new ArrayList<>();
}
