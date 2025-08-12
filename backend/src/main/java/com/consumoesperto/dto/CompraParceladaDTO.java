package com.consumoesperto.dto;

import com.consumoesperto.model.CompraParcelada;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompraParceladaDTO {

    private Long id;

    @NotBlank(message = "Descrição é obrigatória")
    @Size(max = 200, message = "Descrição deve ter no máximo 200 caracteres")
    private String descricao;

    @NotNull(message = "Valor total é obrigatório")
    private BigDecimal valorTotal;

    @NotNull(message = "Valor da parcela é obrigatório")
    private BigDecimal valorParcela;

    @NotNull(message = "Número de parcelas é obrigatório")
    private Integer numeroParcelas;

    @NotNull(message = "Parcela atual é obrigatória")
    private Integer parcelaAtual = 1;

    private LocalDateTime dataCompra;
    private LocalDateTime dataPrimeiraParcela;
    private LocalDateTime dataUltimaParcela;

    @NotNull(message = "Status da compra é obrigatório")
    private CompraParcelada.StatusCompra statusCompra = CompraParcelada.StatusCompra.ATIVA;

    private Long cartaoCreditoId;
    private Long categoriaId;

    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;
}
