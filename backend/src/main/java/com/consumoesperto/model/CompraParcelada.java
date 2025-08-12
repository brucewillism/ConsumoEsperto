package com.consumoesperto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "compras_parceladas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompraParcelada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 200)
    private String descricao;

    @NotNull
    @Column(name = "valor_total")
    private BigDecimal valorTotal;

    @NotNull
    @Column(name = "valor_parcela")
    private BigDecimal valorParcela;

    @NotNull
    @Column(name = "numero_parcelas")
    private Integer numeroParcelas;

    @NotNull
    @Column(name = "parcela_atual")
    private Integer parcelaAtual = 1;

    @Column(name = "data_compra")
    private LocalDateTime dataCompra;

    @Column(name = "data_primeira_parcela")
    private LocalDateTime dataPrimeiraParcela;

    @Column(name = "data_ultima_parcela")
    private LocalDateTime dataUltimaParcela;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_compra")
    private StatusCompra statusCompra = StatusCompra.ATIVA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_credito_id")
    private CartaoCredito cartaoCredito;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "ativo")
    private Boolean ativo = true;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
        dataAtualizacao = LocalDateTime.now();
        if (dataCompra == null) {
            dataCompra = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }

    public enum StatusCompra {
        ATIVA,
        FINALIZADA,
        CANCELADA
    }
}
