package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Preferências de planejamento fiscal (IR restituição + 13º salário) — uma linha por utilizador.
 */
@Entity
@Table(name = "usuario_configuracao_fiscal")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracaoFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    /** Mês esperado da restituição de IR (1–12). */
    @Column(name = "mes_restituicao_ir")
    private Integer mesRestituicaoIr;

    @Column(name = "valor_restituicao", precision = 19, scale = 2)
    private BigDecimal valorRestituicao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_recebimento_13", length = 32)
    private TipoRecebimento13 tipoRecebimento13;

    /** Mês do pagamento integral do 13º ({@link TipoRecebimento13#PARCELA_UNICA}). */
    @Column(name = "mes_parcela_unica")
    private Integer mesParcelaUnica;

    /** Mês do adiantamento de 50% bruto ({@link TipoRecebimento13#DUAS_PARCELAS}). */
    @Column(name = "mes_primeira_parcela")
    private Integer mesPrimeiraParcela;

    @Column(name = "provisionamento_ativo", nullable = false)
    private boolean provisionamentoAtivo = true;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @PrePersist
    @PreUpdate
    void touch() {
        dataAtualizacao = LocalDateTime.now();
    }
}
