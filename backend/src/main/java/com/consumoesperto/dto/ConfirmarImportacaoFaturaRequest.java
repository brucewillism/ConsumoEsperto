package com.consumoesperto.dto;

import java.time.LocalDate;
import java.util.List;

public class ConfirmarImportacaoFaturaRequest {
    private List<Integer> indices;
    /** Quando true, confirma mesmo que a soma dos lançamentos não bata com o total da fatura. */
    private boolean ignorarDivergencia;

    /** CONTA ou CARTAO — obrigatório quando a detecção ficou em NEEDS_REVIEW. */
    private String tipoRecurso;
    private Long contaBancariaId;
    private Long cartaoCreditoId;
    private List<ImportacaoItemAjusteDTO> ajustes;

    public List<Integer> getIndices() { return indices; }
    public void setIndices(List<Integer> indices) { this.indices = indices; }

    public boolean isIgnorarDivergencia() { return ignorarDivergencia; }
    public void setIgnorarDivergencia(boolean ignorarDivergencia) { this.ignorarDivergencia = ignorarDivergencia; }

    public String getTipoRecurso() { return tipoRecurso; }
    public void setTipoRecurso(String tipoRecurso) { this.tipoRecurso = tipoRecurso; }

    public Long getContaBancariaId() { return contaBancariaId; }
    public void setContaBancariaId(Long contaBancariaId) { this.contaBancariaId = contaBancariaId; }

    public Long getCartaoCreditoId() { return cartaoCreditoId; }
    public void setCartaoCreditoId(Long cartaoCreditoId) { this.cartaoCreditoId = cartaoCreditoId; }

    public List<ImportacaoItemAjusteDTO> getAjustes() { return ajustes; }
    public void setAjustes(List<ImportacaoItemAjusteDTO> ajustes) { this.ajustes = ajustes; }

    public static class ImportacaoItemAjusteDTO {
        private Integer index;
        private Boolean selecionado;
        private LocalDate data;
        private String tipoLinha;
        private Long categoriaId;
        private Long contaBancariaId;
        private Long cartaoCreditoId;
        private String statusPreview;

        public Integer getIndex() { return index; }
        public void setIndex(Integer index) { this.index = index; }

        public Boolean getSelecionado() { return selecionado; }
        public void setSelecionado(Boolean selecionado) { this.selecionado = selecionado; }

        public LocalDate getData() { return data; }
        public void setData(LocalDate data) { this.data = data; }

        public String getTipoLinha() { return tipoLinha; }
        public void setTipoLinha(String tipoLinha) { this.tipoLinha = tipoLinha; }

        public Long getCategoriaId() { return categoriaId; }
        public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }

        public Long getContaBancariaId() { return contaBancariaId; }
        public void setContaBancariaId(Long contaBancariaId) { this.contaBancariaId = contaBancariaId; }

        public Long getCartaoCreditoId() { return cartaoCreditoId; }
        public void setCartaoCreditoId(Long cartaoCreditoId) { this.cartaoCreditoId = cartaoCreditoId; }

        public String getStatusPreview() { return statusPreview; }
        public void setStatusPreview(String statusPreview) { this.statusPreview = statusPreview; }
    }
}
