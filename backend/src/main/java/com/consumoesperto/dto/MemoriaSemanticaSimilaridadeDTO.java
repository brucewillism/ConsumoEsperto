package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoriaSemanticaSimilaridadeDTO {
    private String contexto;
    private LocalDateTime dataRegistro;
    /** Distância de cosseno pgvector ({@code <=>}); 0 = idêntico. */
    private double distanciaCosseno;
    /** 0–100, derivado de {@code (1 - distância) * 100}. */
    private int similaridadePercentual;
}
