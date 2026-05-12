package com.consumoesperto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrevisaoFuturoChartDTO {

    private int diaHoje;
    private int ultimoDiaMes;
    private BigDecimal saldoAtual;
    private BigDecimal saldoProjetadoFimMes;
    private boolean projecaoNegativa;
    /** J.A.R.V.I.S. — recomendação de protocolo tático (escudo &lt; 6 meses, colisão ≤15d ou projeto no vermelho). */
    private boolean protocoloOtimizacaoRecomendado;
    /** Meses de autonomia (saldo ÷ despesas do mês corrente), quando calculável. */
    private BigDecimal mesesEscudoEnergia;
    /** Dias até o primeiro saldo projetado negativo no mês (null se não ocorre). */
    private Integer diasAteSaldoNegativo;
    /**
     * Dias do mês (após o dia corrente) com vencimento de obrigação fixa cadastrada —
     * usado no painel para marcar a série projetada (Sentinela).
     */
    private List<Integer> diasVencimentoDespesasFixas = new ArrayList<>();
    /** Dias com provisão de memória (“gasto fantasma” / sazonal). */
    private List<Integer> diasProvisaoMemoria = new ArrayList<>();
    private List<ProvisaoMemoriaDTO> provisoesMemoria = new ArrayList<>();
    private MarketIndicatorsDTO indicadoresMercado;
    /** Fator multiplicador aplicado ao burn (consumo recorrente), ex.: 1.012. */
    private BigDecimal fatorCorrecaoInflacao;
    private String notaJarvisMercado;
    private List<Ponto> pontos = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Ponto {
        private int dia;
        private BigDecimal saldo;
        /** REAL = saldo observado no dia corrente; PROJETADO = linha futura. */
        private String serie;
    }
}
