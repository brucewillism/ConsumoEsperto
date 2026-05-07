package com.consumoesperto.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "grupos_familiares")
public class GrupoFamiliar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criador_usuario_id", nullable = false)
    private Usuario criador;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Usuario getCriador() { return criador; }
    public void setCriador(Usuario criador) { this.criador = criador; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }
}
