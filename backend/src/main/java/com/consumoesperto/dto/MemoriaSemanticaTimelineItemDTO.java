package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoriaSemanticaTimelineItemDTO {
    private Long id;
    private String contexto;
    private String categoriaOrigem;
    private Instant dataRegistro;
    private Boolean temEmbedding;
    /** FATO, HABITO, PREFERENCIA, PLANO_FUTURO, CORRECAO, RESUMO_MENSAL, EVENTO_SAZONAL. */
    private String tipo;
    /** ATIVA, INVALIDADA, SUPERADA, ARQUIVADA, REFUTADA. */
    private String status;
    private BigDecimal confianca;
    private Integer contadorReforco;
}
