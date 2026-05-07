package com.consumoesperto.dto;

import java.time.LocalDateTime;

public class UsuarioScoreDTO {
    private Integer score;
    private String nivel;
    private Integer proximoNivelEm;
    private LocalDateTime dataAtualizacao;

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }
    public Integer getProximoNivelEm() { return proximoNivelEm; }
    public void setProximoNivelEm(Integer proximoNivelEm) { this.proximoNivelEm = proximoNivelEm; }
    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }
}
