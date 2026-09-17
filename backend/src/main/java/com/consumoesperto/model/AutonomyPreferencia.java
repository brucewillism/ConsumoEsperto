package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "autonomy_preferencia")
@Getter
@Setter
@NoArgsConstructor
public class AutonomyPreferencia {

    @Id
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "nivel", nullable = false, length = 24)
    private String nivel = "ASSISTED";

    @Column(name = "registrar_auto", nullable = false)
    private boolean registrarAuto = true;

    @Column(name = "classificar_auto", nullable = false)
    private boolean classificarAuto = true;

    @Column(name = "aprender_categorias", nullable = false)
    private boolean aprenderCategorias = true;

    @Column(name = "detectar_assinaturas", nullable = false)
    private boolean detectarAssinaturas = true;

    @Column(name = "detectar_duplicatas", nullable = false)
    private boolean detectarDuplicatas = true;

    @Column(name = "detectar_anomalias", nullable = false)
    private boolean detectarAnomalias = true;

    @Column(name = "prever_saldo", nullable = false)
    private boolean preverSaldo = true;

    @Column(name = "jarvis_proativo", nullable = false)
    private boolean jarvisProativo = false;

    @Column(name = "resumo_diario", nullable = false)
    private boolean resumoDiario = false;

    @Column(name = "resumo_semanal", nullable = false)
    private boolean resumoSemanal = false;

    @Column(name = "silencioso_inicio")
    private LocalTime silenciosoInicio;

    @Column(name = "silencioso_fim")
    private LocalTime silenciosoFim;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
