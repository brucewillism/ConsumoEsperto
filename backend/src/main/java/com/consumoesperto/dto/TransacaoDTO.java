package com.consumoesperto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import javax.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransacaoDTO {

    private Long id;

    @NotBlank(message = "Descrição é obrigatória")
    @Size(min = 3, max = 200, message = "Descrição deve ter entre 3 e 200 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-.,!?()]+$", message = "Descrição contém caracteres inválidos")
    private String descricao;

    @NotNull(message = "Valor é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    @DecimalMax(value = "999999.99", message = "Valor não pode exceder R$ 999.999,99")
    private BigDecimal valor;

    @NotNull(message = "Tipo de transação é obrigatório")
    private TipoTransacao tipoTransacao;

    private Long categoriaId;
    private String categoriaNome;
    /** Despesa no cartão: id da fatura em aberto (opcional). */
    private Long faturaId;
    /** Despesa no cartão: resolve a fatura aberta do cartão (opcional; ignorado se faturaId preenchido). */
    private Long cartaoCreditoId;
    private Long usuarioId;
    private LocalDateTime dataTransacao;
    private LocalDateTime dataCriacao;
    private boolean recorrente;
    private FrequenciaRecorrencia frequencia;
    private LocalDate proximaExecucao;
    private StatusConferencia statusConferencia;

    @Size(max = 18, message = "CNPJ deve ter no máximo 18 caracteres")
    private String cnpj;

    /** UUID do grupo de parcelas (opcional). */
    private String grupoParcelaId;
    private Integer parcelaAtual;
    private Integer totalParcelas;
    private BigDecimal valorReal;
    private BigDecimal valorComJuros;

    public enum TipoTransacao {
        RECEITA, DESPESA
    }

    public enum FrequenciaRecorrencia {
        SEMANAL, MENSAL
    }

    public enum StatusConferencia {
        CONFIRMADA, PENDENTE
    }
}
