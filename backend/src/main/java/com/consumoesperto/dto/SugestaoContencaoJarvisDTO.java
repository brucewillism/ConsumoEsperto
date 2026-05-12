package com.consumoesperto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SugestaoContencaoJarvisDTO {
    private Long id;
    private Long importacaoFaturaCartaoId;
    private Long categoriaId;
    private String categoriaNome;
    private String chaveAgrupamento;
    private String rotuloExibicao;
    private String tipoHabito;
    private BigDecimal valorGastoReferencia;
    private BigDecimal mediaTresMeses;
    private BigDecimal percentualAumento;
    private BigDecimal valorTetoSugerido;
    private Integer mesAlvo;
    private Integer anoAlvo;
    private String status;
    /** Texto pronto para WhatsApp / UI. */
    private String mensagemResumo;
}
