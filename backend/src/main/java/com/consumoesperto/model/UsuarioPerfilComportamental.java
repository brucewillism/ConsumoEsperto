package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario_perfil_comportamental")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioPerfilComportamental {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "perfil", nullable = false, length = 32)
    private String perfil;

    @Column(name = "confianca_pct", nullable = false)
    private int confiancaPct;

    @Column(name = "perfil_anterior", length = 32)
    private String perfilAnterior;

    @Column(name = "calculado_em", nullable = false)
    private LocalDateTime calculadoEm;
}
