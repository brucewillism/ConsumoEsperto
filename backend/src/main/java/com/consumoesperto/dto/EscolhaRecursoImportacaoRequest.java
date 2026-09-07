package com.consumoesperto.dto;

public class EscolhaRecursoImportacaoRequest {
    /** CONTA ou CARTAO */
    private String tipoRecurso;
    private Long contaBancariaId;
    private Long cartaoCreditoId;

    public String getTipoRecurso() { return tipoRecurso; }
    public void setTipoRecurso(String tipoRecurso) { this.tipoRecurso = tipoRecurso; }

    public Long getContaBancariaId() { return contaBancariaId; }
    public void setContaBancariaId(Long contaBancariaId) { this.contaBancariaId = contaBancariaId; }

    public Long getCartaoCreditoId() { return cartaoCreditoId; }
    public void setCartaoCreditoId(Long cartaoCreditoId) { this.cartaoCreditoId = cartaoCreditoId; }
}
