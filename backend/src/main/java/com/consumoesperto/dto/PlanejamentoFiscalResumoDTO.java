package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanejamentoFiscalResumoDTO {
    private ConfiguracaoFiscalDTO configuracao;
    private BaseContrachequeFiscalDTO baseContracheque;
    @Builder.Default
    private List<ParcelaReceitaFiscalDTO> parcelas = new ArrayList<>();
    private BigDecimal totalProvisionado;
    private int transacoesSincronizadas;
    private String aviso;
}
