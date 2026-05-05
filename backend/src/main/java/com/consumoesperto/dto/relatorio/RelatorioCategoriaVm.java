package com.consumoesperto.dto.relatorio;

import lombok.Value;

/**
 * Linha de categoria para o template Thymeleaf do relatório PDF (getters para Thymeleaf 3.0).
 */
@Value
public class RelatorioCategoriaVm {
    String nome;
    String gastoFmt;
    String metaFmt;
    int barPct;
    boolean excedeuMeta;
}
