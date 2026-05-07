package com.consumoesperto.dto;

public class GrupoFamiliarMembroDTO {
    private Long id;
    private Long usuarioId;
    private String nome;
    private String email;
    private String whatsapp;
    private String status;
    private boolean eu;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getWhatsapp() { return whatsapp; }
    public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isEu() { return eu; }
    public void setEu(boolean eu) { this.eu = eu; }
}
