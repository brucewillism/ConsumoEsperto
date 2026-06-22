package com.consumoesperto.dto;

import com.consumoesperto.model.GravidadeAlertaFluxo;
import com.consumoesperto.model.RiscoFluxo;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class SentinelaRunResultDTO {
    private Long usuarioId;
    private GravidadeAlertaFluxo gravidade;
    private LocalDate piorDia;
    private BigDecimal valorReferencia;
    private String periodo;
    private boolean alertaEnviado;
    private boolean optInNotificacoes;
    private String mensagemPreview;

    public static SentinelaRunResultDTO from(Long usuarioId, RiscoFluxo risco, boolean enviado, boolean optIn) {
        return SentinelaRunResultDTO.builder()
            .usuarioId(usuarioId)
            .gravidade(risco != null ? risco.getGravidade() : GravidadeAlertaFluxo.NENHUMA)
            .piorDia(risco != null ? risco.getPiorDia() : null)
            .valorReferencia(risco != null ? risco.getValorReferencia() : null)
            .periodo(risco != null ? risco.getPeriodo() : null)
            .alertaEnviado(enviado)
            .optInNotificacoes(optIn)
            .build();
    }
}
