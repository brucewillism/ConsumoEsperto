package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "alertas_enviados", indexes = {
    @Index(name = "idx_alerta_enviado_usuario_periodo", columnList = "usuario_id, periodo")
})
@Getter
@Setter
@NoArgsConstructor
public class AlertaEnviado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Ex.: {@code 2026-06} — mês do pior risco simulado. */
    @Column(name = "periodo", nullable = false, length = 16)
    private String periodo;

    @Enumerated(EnumType.STRING)
    @Column(name = "gravidade", nullable = false, length = 16)
    private GravidadeAlertaFluxo gravidade;

    @Column(name = "data_envio", nullable = false)
    private LocalDate dataEnvio;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @PrePersist
    void onCreate() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
        if (dataEnvio == null) {
            dataEnvio = LocalDate.now();
        }
    }
}
