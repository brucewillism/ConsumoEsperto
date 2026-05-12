package com.consumoesperto.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Sugestão proativa de teto mensal (protocolo de contenção), gerada pelo J.A.R.V.I.S.
 * A aceitação cria/atualiza {@link Orcamento} no mês alvo — não confundir com meta de poupança ({@link MetaFinanceira}).
 */
@Entity
@Table(name = "sugestoes_contencao_jarvis")
@Getter
@Setter
@NoArgsConstructor
public class SugestaoContencaoJarvis {

    public enum Status {
        PENDENTE,
        ACEITA,
        RECUSADA
    }

    public enum TipoHabito {
        COMBUSTIVEL,
        MERCADO,
        RESTAURANTE,
        OUTRO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    @JsonBackReference("usuario-sugestoes-contencao")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "importacao_fatura_cartao_id")
    private ImportacaoFaturaCartao importacaoFaturaCartao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Column(name = "chave_agrupamento", nullable = false, length = 200)
    private String chaveAgrupamento;

    @Column(name = "rotulo_exibicao", nullable = false, length = 260)
    private String rotuloExibicao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_habito", nullable = false, length = 24)
    private TipoHabito tipoHabito = TipoHabito.OUTRO;

    @Column(name = "valor_gasto_referencia", precision = 19, scale = 2, nullable = false)
    private BigDecimal valorGastoReferencia;

    @Column(name = "media_tres_meses", precision = 19, scale = 2)
    private BigDecimal mediaTresMeses;

    @Column(name = "percentual_aumento", precision = 8, scale = 2)
    private BigDecimal percentualAumento;

    @Column(name = "valor_teto_sugerido", precision = 19, scale = 2, nullable = false)
    private BigDecimal valorTetoSugerido;

    @Column(name = "mes_alvo", nullable = false)
    private Integer mesAlvo;

    @Column(name = "ano_alvo", nullable = false)
    private Integer anoAlvo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDENTE;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @PrePersist
    protected void onCreate() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
    }
}
