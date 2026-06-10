package com.consumoesperto.dto;

import com.consumoesperto.model.TipoRecebimento13;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ConfiguracaoFiscalRequest {
    private Integer mesRestituicaoIr;
    private BigDecimal valorRestituicao;
    private TipoRecebimento13 tipoRecebimento13;
    private Integer mesParcelaUnica;
    private Integer mesPrimeiraParcela;
    private Integer mesSegundaParcela;
    private Integer diaPagamento13;
    private Boolean provisionamentoAtivo;
}
