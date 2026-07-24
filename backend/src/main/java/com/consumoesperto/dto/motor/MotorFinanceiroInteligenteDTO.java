package com.consumoesperto.dto.motor;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class MotorFinanceiroInteligenteDTO {

    private PerfilComportamentalDTO perfilComportamental;
    private ForecastInteligenteDTO forecastInteligente;
    private ScoreExplicavelDTO scoreExplicavel;
    private List<MetaInteligenteDTO> metasInteligentes = new ArrayList<>();
    private AdvisorInvestimentoDTO advisorInvestimento;
    private String narrativaIa;
    private LocalDateTime calculadoEm;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PerfilComportamentalDTO {
        private String perfil;
        private int confiancaPct;
        private Map<String, Integer> pontuacaoPorPerfil = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ForecastInteligenteDTO {
        private BigDecimal saldoPrevisto;
        private BigDecimal despesasPrevistas;
        private BigDecimal receitasPrevistas;
        private int chanceMesPositivoPct;
        private int chanceChequeEspecialPct;
        private int chanceEstourarOrcamentoPct;
        private String explicacaoDeterministica;
        private String explicacaoNarrativa;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ScoreExplicavelDTO {
        private int scoreTotal;
        private List<ComponenteScoreDTO> componentes = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ComponenteScoreDTO {
        private String nome;
        private int pontos;
        private int maximo;
        private String detalhe;
        private String comoRecuperar;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class MetaInteligenteDTO {
        private Long metaId;
        private String descricao;
        private int probabilidadeSucessoPct;
        private BigDecimal ritmoAtualMensal;
        private BigDecimal ritmoNecessarioMensal;
        private BigDecimal diferencaMensal;
        private String recomendacaoDeterministica;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AdvisorInvestimentoDTO {
        private String perfilInvestidor;
        private List<String> produtosCompativeis = new ArrayList<>();
        private String textoDeterministico;
        private String avisoLegal;
    }
}
