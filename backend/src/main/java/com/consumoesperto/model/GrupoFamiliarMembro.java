package com.consumoesperto.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "grupo_familiar_membros",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_grupo_membro_usuario",
        columnNames = {"grupo_familiar_id", "usuario_id"}
    )
)
public class GrupoFamiliarMembro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_familiar_id", nullable = false)
    private GrupoFamiliar grupoFamiliar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "convidado_por_usuario_id", nullable = false)
    private Usuario convidadoPor;

    @Column(name = "convite_email", length = 120)
    private String conviteEmail;

    @Column(name = "convite_whatsapp", length = 32)
    private String conviteWhatsapp;

    @Column(name = "token_convite", nullable = false, unique = true, length = 64)
    private String tokenConvite;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status;

    @Column(name = "data_convite", nullable = false)
    private LocalDateTime dataConvite;

    @Column(name = "data_resposta")
    private LocalDateTime dataResposta;

    @PrePersist
    protected void onCreate() {
        dataConvite = LocalDateTime.now();
        if (status == null) {
            status = Status.PENDENTE;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public GrupoFamiliar getGrupoFamiliar() { return grupoFamiliar; }
    public void setGrupoFamiliar(GrupoFamiliar grupoFamiliar) { this.grupoFamiliar = grupoFamiliar; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public Usuario getConvidadoPor() { return convidadoPor; }
    public void setConvidadoPor(Usuario convidadoPor) { this.convidadoPor = convidadoPor; }
    public String getConviteEmail() { return conviteEmail; }
    public void setConviteEmail(String conviteEmail) { this.conviteEmail = conviteEmail; }
    public String getConviteWhatsapp() { return conviteWhatsapp; }
    public void setConviteWhatsapp(String conviteWhatsapp) { this.conviteWhatsapp = conviteWhatsapp; }
    public String getTokenConvite() { return tokenConvite; }
    public void setTokenConvite(String tokenConvite) { this.tokenConvite = tokenConvite; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getDataConvite() { return dataConvite; }
    public void setDataConvite(LocalDateTime dataConvite) { this.dataConvite = dataConvite; }
    public LocalDateTime getDataResposta() { return dataResposta; }
    public void setDataResposta(LocalDateTime dataResposta) { this.dataResposta = dataResposta; }

    public enum Status {
        PENDENTE,
        ACEITO,
        RECUSADO,
        CANCELADO
    }
}
