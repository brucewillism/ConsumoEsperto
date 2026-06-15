package com.consumoesperto.dto;

import com.consumoesperto.model.TipoConfiguracaoRenda;
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
public class RendaConfigDTO {
    private BigDecimal salarioBruto;
    private List<DescontoFixoDTO> descontosFixos;
    private Integer diaPagamento;
    private BigDecimal salarioLiquido;
    private BigDecimal totalDescontos;
    /** Percentual dos descontos sobre o bruto (0–100+), null se bruto zero. */
    private BigDecimal percentualDescontosSobreBruto;
    private boolean receitaAutomaticaAtiva;
    /** Carteira que recebe o salário automático (ex.: conta Itaú). */
    private Long contaBancariaId;
    private String contaBancariaNome;
    private TipoConfiguracaoRenda tipoConfiguracaoRenda;
    private BigDecimal valorRecebimentoUnico;
    /** Meta de faturamento mensal (FLUXO_DIARIO). */
    private BigDecimal metaFaturamentoMensal;
    /** Renda mensal usada em projeções e dashboard (respeita o tipo configurado). */
    private BigDecimal rendaMensalEstimada;
    /** Rótulo para exibição: Salário líquido, Recebimento mensal ou Média 30 dias. */
    private String rotuloRenda;

    public static RendaConfigDTO vazio() {
        return RendaConfigDTO.builder()
            .salarioBruto(BigDecimal.ZERO)
            .descontosFixos(new ArrayList<>())
            .diaPagamento(null)
            .salarioLiquido(BigDecimal.ZERO)
            .totalDescontos(BigDecimal.ZERO)
            .percentualDescontosSobreBruto(BigDecimal.ZERO)
            .receitaAutomaticaAtiva(false)
            .tipoConfiguracaoRenda(TipoConfiguracaoRenda.CONTRACHEQUE)
            .valorRecebimentoUnico(null)
            .rendaMensalEstimada(BigDecimal.ZERO)
            .rotuloRenda("Salário líquido")
            .build();
    }
}
