package com.consumoesperto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModoViagemJarvisDTO {
    private String eventIdGoogle;
    private String titulo;
    private LocalDate dataEvento;
    private BigDecimal tetoSugerido;
    private String mensagemResumo;
}
