package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "ingest_preferencia")
@Getter
@Setter
@NoArgsConstructor
public class IngestPreferencia {

    public static final String AGRUPAMENTO_IMEDIATO = "IMEDIATO";
    public static final String AGRUPAMENTO_RESUMO = "RESUMO";

    @Id
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "agrupamento", nullable = false, length = 16)
    private String agrupamento = AGRUPAMENTO_IMEDIATO;

    @Column(name = "resumo_minutos", nullable = false)
    private int resumoMinutos = 15;

    @Column(name = "silencioso_inicio")
    private LocalTime silenciosoInicio;

    @Column(name = "silencioso_fim")
    private LocalTime silenciosoFim;

    @Column(name = "conta_padrao_id")
    private Long contaPadraoId;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
