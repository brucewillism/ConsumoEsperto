package com.consumoesperto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetaFinanceiraDTO {
    private Long id;
    private String descricao;
    private BigDecimal valorTotal;
    private BigDecimal percentualComprometimento;
    private BigDecimal valorPoupadoMensal;
    private BigDecimal prazoMeses;
    private BigDecimal rendaMediaReferencia;
    private LocalDateTime dataCriacao;
    /** 1 (baixa) a 5 (máxima); listagem ordena por prioridade decrescente. */
    private Integer prioridade;
    /** Progresso estimado pelo tempo desde a criação (0–100). */
    private Integer progressPercent;
    /** Preenchido em respostas de criação/atualização quando aplicável. */
    private BigDecimal totalPercentualComprometidoMetas;
    private String alertaComprometimento;
    /** Modo Viagem / Cronos — opcional. */
    private LocalDate dataExpiracao;
    private String googleCalendarEventId;
}
