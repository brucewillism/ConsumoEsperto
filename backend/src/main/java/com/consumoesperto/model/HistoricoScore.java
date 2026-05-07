package com.consumoesperto.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "historico_score")
public class HistoricoScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "delta", nullable = false)
    private Integer delta;

    @Column(name = "score_resultante", nullable = false)
    private Integer scoreResultante;

    @Column(name = "motivo", nullable = false, length = 120)
    private String motivo;

    @Column(name = "detalhe", length = 500)
    private String detalhe;

    @Column(name = "data_evento", nullable = false)
    private LocalDateTime dataEvento;

    @PrePersist
    protected void onCreate() {
        dataEvento = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public Integer getDelta() { return delta; }
    public void setDelta(Integer delta) { this.delta = delta; }
    public Integer getScoreResultante() { return scoreResultante; }
    public void setScoreResultante(Integer scoreResultante) { this.scoreResultante = scoreResultante; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getDetalhe() { return detalhe; }
    public void setDetalhe(String detalhe) { this.detalhe = detalhe; }
    public LocalDateTime getDataEvento() { return dataEvento; }
    public void setDataEvento(LocalDateTime dataEvento) { this.dataEvento = dataEvento; }
}
