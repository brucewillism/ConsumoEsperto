package com.consumoesperto.model;

import lombok.Getter;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Resultado determinístico da simulação de fluxo (horizonte 90 dias).
 */
@Getter
public class RiscoFluxo {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final DateTimeFormatter SEMANA = DateTimeFormatter.ofPattern("dd/MM");

    private GravidadeAlertaFluxo gravidade = GravidadeAlertaFluxo.NENHUMA;
    private LocalDate piorDia;
    /** Saldo negativo projetado (VERMELHO) ou meses de escudo (AMBAR). */
    private BigDecimal valorReferencia;

    public static RiscoFluxo nenhum() {
        return new RiscoFluxo();
    }

    public boolean temRisco() {
        return gravidade != null && gravidade != GravidadeAlertaFluxo.NENHUMA;
    }

    /** Chave de idempotência mensal (mês do pior dia detectado). */
    public String getPeriodo() {
        if (piorDia == null) {
            return YearMonth.now().toString();
        }
        return YearMonth.from(piorDia).toString();
    }

    public String getSemanaFormatada() {
        if (piorDia == null) {
            return SEMANA.format(LocalDate.now());
        }
        LocalDate fim = piorDia.plusDays(6);
        return SEMANA.format(piorDia) + " a " + SEMANA.format(fim);
    }

    public String getValorNegativoFormatado() {
        if (valorReferencia == null) {
            return BRL.format(BigDecimal.ZERO);
        }
        if (gravidade == GravidadeAlertaFluxo.AMBAR) {
            return valorReferencia.setScale(1, java.math.RoundingMode.HALF_UP) + " meses de escudo";
        }
        return BRL.format(valorReferencia.abs());
    }

    public void registrar(GravidadeAlertaFluxo nova, LocalDate dia, BigDecimal valor) {
        if (nova == null || nova == GravidadeAlertaFluxo.NENHUMA || dia == null) {
            return;
        }
        if (gravidade == GravidadeAlertaFluxo.NENHUMA || nova.ordinal() > gravidade.ordinal()) {
            gravidade = nova;
            piorDia = dia;
            valorReferencia = valor;
            return;
        }
        if (nova == gravidade && piorDia != null && dia.isBefore(piorDia)) {
            piorDia = dia;
            valorReferencia = valor;
        }
    }
}
