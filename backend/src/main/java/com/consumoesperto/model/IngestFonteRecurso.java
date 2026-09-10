package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(
    name = "ingest_fonte_recurso",
    uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "app", "canal"})
)
@Getter
@Setter
@NoArgsConstructor
public class IngestFonteRecurso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "app", nullable = false, length = 32)
    private String app;

    @Column(name = "canal", nullable = false, length = 16)
    private String canal;

    @Column(name = "conta_bancaria_id")
    private Long contaBancariaId;

    @Column(name = "cartao_credito_id")
    private Long cartaoCreditoId;
}
