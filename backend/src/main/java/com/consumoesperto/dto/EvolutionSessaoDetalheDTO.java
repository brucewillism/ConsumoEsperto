package com.consumoesperto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EvolutionSessaoDetalheDTO {

    private String instancia;
    private String status;
    private boolean ativa;
    private long uptimeSegundos;
    private int memoriaEstimadaMb;
    private long mensagensEnviadas;
    private long mensagensRecebidas;
    private long mensagensEnviadasHoje;
    private long mensagensRecebidasHoje;
    private int desconexoesHoje;
    private int reconexoesHoje;
    private int falhasHoje;
    private long latenciaMediaMs;
    private long latenciaP95Ms;
    private long idadeUltimaAtividadeSegundos;
    private boolean instavel;
    private String motivoInstabilidade;
}
