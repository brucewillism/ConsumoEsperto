package com.consumoesperto.dto;

import com.consumoesperto.model.TipoRecebimento13;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracaoFiscalDTO {
    private Integer mesRestituicaoIr;
    private BigDecimal valorRestituicao;
    private TipoRecebimento13 tipoRecebimento13;
    private Integer mesParcelaUnica;
    private Integer mesPrimeiraParcela;
    private boolean provisionamentoAtivo;

    public static ConfiguracaoFiscalDTO vazio() {
        return ConfiguracaoFiscalDTO.builder()
            .provisionamentoAtivo(true)
            .build();
    }
}
