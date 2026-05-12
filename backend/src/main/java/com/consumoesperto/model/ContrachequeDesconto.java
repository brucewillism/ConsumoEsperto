package com.consumoesperto.model;

import javax.persistence.*;
import java.math.BigDecimal;

/**
 * Linha de desconto extraída do contracheque (INSS, IRRF, plano de saúde, etc.).
 */
@Entity
@Table(name = "contracheque_descontos", indexes = {
    @Index(name = "idx_cont_desc_import_id", columnList = "contracheque_importado_id")
})
public class ContrachequeDesconto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contracheque_importado_id", nullable = false)
    private ContrachequeImportado contrachequeImportado;

    @Column(name = "descricao", length = 200, nullable = false)
    private String descricao;

    @Column(name = "valor", precision = 19, scale = 2, nullable = false)
    private BigDecimal valor;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ContrachequeImportado getContrachequeImportado() { return contrachequeImportado; }
    public void setContrachequeImportado(ContrachequeImportado contrachequeImportado) {
        this.contrachequeImportado = contrachequeImportado;
    }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
}
