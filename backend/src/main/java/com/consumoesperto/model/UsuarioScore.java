package com.consumoesperto.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios_score")
public class UsuarioScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "nivel", nullable = false, length = 40)
    private String nivel;

    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;

    @PrePersist
    @PreUpdate
    protected void touch() {
        dataAtualizacao = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }
    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }
}
