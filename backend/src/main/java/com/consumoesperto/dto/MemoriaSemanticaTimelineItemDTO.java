package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
