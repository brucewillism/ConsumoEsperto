package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Configuração de renda (salário bruto, descontos fixos, dia de pagamento) — uma linha por utilizador.
 */
@Entity
@Table(name = "usuario_renda_config")
@Getter
@Setter
@NoArgsConstructor
public class RendaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(name = "salario_bruto", nullable = false, precision = 19, scale = 2)
    private BigDecimal salarioBruto = BigDecimal.ZERO;

    /** JSON: array de { "rotulo": "INSS", "valor": 600 } */
    @Column(name = "descontos_fixos_json", columnDefinition = "TEXT")
    private String descontosFixosJson;

    @Column(name = "dia_pagamento")
    private Integer diaPagamento;

    @Column(name = "salario_liquido", nullable = false, precision = 19, scale = 2)
    private BigDecimal salarioLiquido = BigDecimal.ZERO;

    @Column(name = "receita_automatica_ativa", nullable = false)
    private boolean receitaAutomaticaAtiva;

    /** Formato yyyyMM (ex.: 202604) para evitar lançamento duplicado no mesmo mês. */
    @Column(name = "ultimo_mes_lancamento_auto")
    private Integer ultimoMesLancamentoAuto;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @PrePersist
    @PreUpdate
    void touch() {
        dataAtualizacao = LocalDateTime.now();
    }
}
