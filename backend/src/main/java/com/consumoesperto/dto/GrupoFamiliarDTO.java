package com.consumoesperto.dto;

import java.util.List;

public class GrupoFamiliarDTO {
    private Long id;
    private String nome;
    private List<GrupoFamiliarMembroDTO> membros;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public List<GrupoFamiliarMembroDTO> getMembros() { return membros; }
    public void setMembros(List<GrupoFamiliarMembroDTO> membros) { this.membros = membros; }
}
