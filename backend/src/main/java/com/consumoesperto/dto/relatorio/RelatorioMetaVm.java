package com.consumoesperto.dto.relatorio;

import lombok.Value;

/**
 * Linha de meta financeira para o template Thymeleaf do relatório PDF.
 */
@Value
public class RelatorioMetaVm {
    String descricao;
    String valorObjetivoFmt;
    String poupancaFmt;
    String indicador;
    boolean alerta;
}
