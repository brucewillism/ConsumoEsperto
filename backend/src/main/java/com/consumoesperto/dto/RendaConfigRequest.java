package com.consumoesperto.dto;

import com.consumoesperto.model.TipoConfiguracaoRenda;
import javax.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RendaConfigRequest {
    private BigDecimal salarioBruto;
    private List<DescontoFixoDTO> descontosFixos;
    private Integer diaPagamento;
    private Boolean receitaAutomaticaAtiva;
    /** Conta destino do salário automático (opcional; senão usa conta Itaú ou padrão). */
    private Long contaBancariaId;
    @NotNull(message = "Tipo de configuração de renda é obrigatório")
    private TipoConfiguracaoRenda tipoConfiguracaoRenda;
    private BigDecimal valorRecebimentoUnico;
}
