package com.consumoesperto.fiscal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cálculo determinístico INSS (empregado) e IRRF mensal com memória de cálculo.
 * Limitação explícita: INSS apenas para {@link TabelaFiscalAnoRegistry.TipoSeguradoInss#EMPREGADO}
 * (mesma tabela oficial de empregado, doméstico e avulso).
 */
public final class CalculoFiscalService {

    public static final String MSG_ANO_NAO_CADASTRADO = "Tabela fiscal do ano informado não está cadastrada.";
    public static final String MSG_TIPO_NAO_SUPORTADO =
        "Tipo de segurado não suportado pelo ConsumoEsperto. Apenas empregado (tabela progressiva empregado/doméstico/avulso).";

    private static final int SCALE = 2;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private CalculoFiscalService() {}

    public record CalculoFolhaEntrada(
        BigDecimal rendimentoBruto,
        int dependentes,
        LocalDate competencia,
        int ano,
        TabelaFiscalAnoRegistry.TipoSeguradoInss tipoSegurado,
        BigDecimal inssInformado,
        boolean decimoTerceiro
    ) {}

    public record MemoriaCalculoFolha(
        int ano,
        LocalDate competencia,
        BigDecimal rendimentoBruto,
        BigDecimal rendimentoTributavel,
        BigDecimal inss,
        BigDecimal deducaoDependentes,
        BigDecimal deducaoLegalTotal,
        BigDecimal descontoSimplificado,
        boolean usouDescontoSimplificado,
        BigDecimal baseCalculoIr,
        BigDecimal impostoProgressivo,
        BigDecimal reducaoAdicional,
        BigDecimal irrfFinal,
        int dependentes,
        boolean decimoTerceiro,
        List<String> passos
    ) {}

    /** Compatibilidade retroativa — calcula folha mensal empregado. */
    public static MemoriaCalculo calcular(int ano, LocalDate data, BigDecimal baseBruta, int dependentes) {
        MemoriaCalculoFolha m = calcularFolha(new CalculoFolhaEntrada(
            baseBruta, dependentes, data, ano,
            TabelaFiscalAnoRegistry.TipoSeguradoInss.EMPREGADO, null, false
        ));
        String obs = TabelaFiscalAnoRegistry.obterVersao(m.ano(), m.competencia())
            .map(TabelaFiscalAnoRegistry.VersaoTabela::observacoes)
            .orElse("");
        return new MemoriaCalculo(
            m.ano(), m.competencia(), obs, m.rendimentoBruto(),
            m.inss(), m.irrfFinal(), m.dependentes(), m.passos()
        );
    }

    public record MemoriaCalculo(
        int ano,
        LocalDate dataReferencia,
        String versaoObservacoes,
        BigDecimal baseCalculo,
        BigDecimal inss,
        BigDecimal irrf,
        int dependentes,
        List<String> passos
    ) {}

    public static MemoriaCalculoFolha calcularFolha(CalculoFolhaEntrada entrada) {
        var versao = TabelaFiscalAnoRegistry.obterVersao(entrada.ano(), entrada.competencia())
            .orElseThrow(() -> new IllegalStateException(MSG_ANO_NAO_CADASTRADO));

        if (!versao.suporta(entrada.tipoSegurado())) {
            throw new IllegalStateException(MSG_TIPO_NAO_SUPORTADO);
        }

        List<String> passos = new ArrayList<>();
        passos.add("Versão: " + versao.observacoes());
        if (entrada.decimoTerceiro()) {
            passos.add("Competência: décimo terceiro (INSS calculado sobre o valor integral, sem somar à remuneração mensual).");
        }

        BigDecimal bruto = scale(entrada.rendimentoBruto());
        BigDecimal rendimentoTributavel = bruto;

        BigDecimal inss = entrada.inssInformado() != null
            ? scale(entrada.inssInformado())
            : calcularInssProgressivo(bruto, versao.faixasInss(), versao.tetoInss(), passos);

        BigDecimal dedDep = versao.deducaoDependente().multiply(BigDecimal.valueOf(Math.max(0, entrada.dependentes())));
        BigDecimal dedLegal = inss.add(dedDep);
        BigDecimal descSimpl = versao.descontoSimplificadoMax() != null ? versao.descontoSimplificadoMax() : BigDecimal.ZERO;

        boolean usaSimpl = descSimpl.compareTo(dedLegal) > 0;
        BigDecimal deducaoAplicada = usaSimpl ? descSimpl : dedLegal;
        passos.add(usaSimpl
            ? "Desconto simplificado R$ " + descSimpl + " mais vantajoso que deduções legais R$ " + dedLegal
            : "Deduções legais R$ " + dedLegal + " (INSS + dependentes) mais vantajosas que simplificado R$ " + descSimpl);

        BigDecimal baseIr = bruto.subtract(deducaoAplicada).max(BigDecimal.ZERO);
        passos.add("Base de cálculo IR: R$ " + baseIr);

        BigDecimal impostoProgressivo = calcularIrProgressivo(baseIr, versao.faixasIr(), passos);
        BigDecimal reducao = calcularReducaoAdicional(versao.reducaoAdicional(), rendimentoTributavel, impostoProgressivo, passos);
        BigDecimal irrfFinal = impostoProgressivo.subtract(reducao).max(BigDecimal.ZERO).setScale(SCALE, RM);
        passos.add("IRRF final: R$ " + irrfFinal);

        return new MemoriaCalculoFolha(
            entrada.ano(), entrada.competencia(), bruto, rendimentoTributavel, inss, dedDep, dedLegal,
            descSimpl, usaSimpl, baseIr, impostoProgressivo, reducao, irrfFinal,
            entrada.dependentes(), entrada.decimoTerceiro(), List.copyOf(passos)
        );
    }

    public static BigDecimal calcularInssEmpregado(BigDecimal salario, LocalDate competencia, int ano) {
        var versao = TabelaFiscalAnoRegistry.obterVersao(ano, competencia)
            .orElseThrow(() -> new IllegalStateException(MSG_ANO_NAO_CADASTRADO));
        if (!versao.suporta(TabelaFiscalAnoRegistry.TipoSeguradoInss.EMPREGADO)) {
            throw new IllegalStateException(MSG_TIPO_NAO_SUPORTADO);
        }
        return calcularInssProgressivo(scale(salario), versao.faixasInss(), versao.tetoInss(), new ArrayList<>());
    }

    static BigDecimal calcularInssProgressivo(
        BigDecimal base,
        List<TabelaFiscalAnoRegistry.FaixaInss> faixas,
        BigDecimal teto,
        List<String> passos
    ) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal anterior = BigDecimal.ZERO;
        BigDecimal baseLimitada = base.min(teto);
        for (var faixa : faixas) {
            BigDecimal limite = faixa.limiteSuperior();
            BigDecimal faixaBase = baseLimitada.min(limite).subtract(anterior).max(BigDecimal.ZERO);
            if (faixaBase.signum() > 0) {
                BigDecimal parcial = faixaBase.multiply(faixa.aliquotaPct())
                    .divide(BigDecimal.valueOf(100), SCALE, RM);
                total = total.add(parcial);
                if (passos != null) {
                    passos.add("INSS faixa até " + limite + ": " + faixaBase + " x " + faixa.aliquotaPct() + "% = " + parcial);
                }
            }
            anterior = limite;
            if (baseLimitada.compareTo(limite) <= 0) break;
        }
        return total.setScale(SCALE, RM);
    }

    static BigDecimal calcularIrProgressivo(
        BigDecimal base,
        List<TabelaFiscalAnoRegistry.FaixaIr> faixas,
        List<String> passos
    ) {
        for (var faixa : faixas) {
            BigDecimal limite = faixa.limiteSuperior();
            if (limite == null || base.compareTo(limite) <= 0) {
                BigDecimal bruto = base.multiply(faixa.aliquotaPct()).divide(BigDecimal.valueOf(100), SCALE, RM);
                BigDecimal liquido = bruto.subtract(faixa.parcelaDeduzir()).max(BigDecimal.ZERO);
                if (passos != null) {
                    passos.add("IR progressivo alíquota " + faixa.aliquotaPct() + "%: bruto " + bruto + " - parcela " + faixa.parcelaDeduzir() + " = " + liquido);
                }
                return liquido.setScale(SCALE, RM);
            }
        }
        return BigDecimal.ZERO.setScale(SCALE, RM);
    }

    static BigDecimal calcularReducaoAdicional(
        TabelaFiscalAnoRegistry.ReducaoAdicionalIrrf reducao,
        BigDecimal rendimentoTributavel,
        BigDecimal impostoProgressivo,
        List<String> passos
    ) {
        if (reducao == null || reducao.faixas().isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, RM);
        }
        BigDecimal r = rendimentoTributavel;
        for (var faixa : reducao.faixas()) {
            boolean dentro = (faixa.rendimentoDe() == null || r.compareTo(faixa.rendimentoDe()) >= 0)
                && (faixa.rendimentoAte() == null || r.compareTo(faixa.rendimentoAte()) <= 0);
            if (!dentro) continue;

            BigDecimal valor;
            if (faixa.reducaoMaxima() != null) {
                valor = impostoProgressivo.min(faixa.reducaoMaxima());
                if (passos != null) {
                    passos.add("Redução adicional (rendimento até " + faixa.rendimentoAte() + "): min(imposto, " + faixa.reducaoMaxima() + ") = " + valor);
                }
            } else if (faixa.constante() != null && faixa.coeficienteRendimento() != null) {
                valor = faixa.constante().subtract(faixa.coeficienteRendimento().multiply(r)).setScale(SCALE, RM);
                valor = valor.min(impostoProgressivo).max(BigDecimal.ZERO);
                if (passos != null) {
                    passos.add("Redução adicional: " + faixa.constante() + " - (" + faixa.coeficienteRendimento() + " x " + r + ") = " + valor);
                }
            } else {
                valor = BigDecimal.ZERO;
            }
            return valor.setScale(SCALE, RM);
        }
        if (passos != null) {
            passos.add("Sem redução adicional para rendimento R$ " + r);
        }
        return BigDecimal.ZERO.setScale(SCALE, RM);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(SCALE, RM);
    }

    public static Map<String, Object> relatorioTabelaUsada(int ano, LocalDate data) {
        Map<String, Object> out = new LinkedHashMap<>();
        var v = TabelaFiscalAnoRegistry.obterVersao(ano, data);
        if (v.isEmpty()) {
            out.put("erro", MSG_ANO_NAO_CADASTRADO);
            return out;
        }
        out.put("ano", ano);
        out.put("dataReferencia", data);
        out.put("observacoes", v.get().observacoes());
        out.put("fonte", v.get().fonte());
        out.put("tipoSeguradoInss", v.get().tipoSeguradoInss());
        return out;
    }
}
