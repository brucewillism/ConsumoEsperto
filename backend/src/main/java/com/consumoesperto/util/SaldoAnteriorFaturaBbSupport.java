package com.consumoesperto.util;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Faturas Banco do Brasil: o PDF lista «SALDO FATURA ANTERIOR» nos lançamentos e um total a pagar menor.
 * O utilizador escolhe importar só o mês atual ou a soma completa.
 */
public final class SaldoAnteriorFaturaBbSupport {

    public static final String META_SALDO_ANTERIOR_BB_PREFIX = "__SALDO_ANTERIOR_BB__:";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SaldoAnteriorFaturaBbSupport() {
    }

    public record SaldoAnteriorBbPendente(
        BigDecimal saldoAnterior,
        /** Total desta fatura (sem carregar o anterior de novo). */
        BigDecimal saldoMesAtual,
        BigDecimal totalPdf,
        BigDecimal somaTodosLancamentos
    ) {}

    public record SaldoAnteriorBbMeta(
        BigDecimal saldoAnterior,
        BigDecimal saldoMesAtual,
        BigDecimal totalPdf,
        BigDecimal somaTodosLancamentos,
        boolean resolvido,
        boolean somouSaldoAnterior
    ) {}

    public static boolean bancoPareceBb(String bancoCartao) {
        return BancoBrasilCatalog.bancosCorrespondem("bb", bancoCartao);
    }

    public static boolean descricaoEhLinhaSaldoFaturaAnterior(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return false;
        }
        String n = ApelidoNormalizador.normalizar(descricao);
        return n.contains("saldo fatura anterior")
            || n.contains("saldo da fatura anterior")
            || (n.contains("saldo") && n.contains("anterior") && !n.contains("proxima"));
    }

    public static BigDecimal somaLancamentosExcluindoSaldoAnterior(List<ImportacaoFaturaItemDTO> itens) {
        if (itens == null || itens.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return itens.stream()
            .filter(i -> !descricaoEhLinhaSaldoFaturaAnterior(i.getDescricao()))
            .map(i -> i.getValor() != null ? i.getValor() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Detecta BB quando a soma de todos os lançamentos ≈ saldo anterior + total do PDF.
     */
    public static Optional<SaldoAnteriorBbPendente> detectar(
        JsonNode extracted,
        String bancoCartao,
        BigDecimal valorTotalPdf,
        BigDecimal somaTodosLancamentos,
        List<ImportacaoFaturaItemDTO> itens,
        Optional<BigDecimal> valorUltimaFaturaCartao
    ) {
        if (valorTotalPdf == null || valorTotalPdf.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        if (somaTodosLancamentos == null || somaTodosLancamentos.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        BigDecimal saldoAnt = money(extracted.path("saldoFaturaAnterior"));
        if (saldoAnt.compareTo(BigDecimal.ZERO) <= 0) {
            saldoAnt = inferirSaldoAnteriorDosLancamentos(itens, somaTodosLancamentos, valorTotalPdf);
        }
        if (saldoAnt.compareTo(BigDecimal.ZERO) <= 0
            && valorUltimaFaturaCartao.isPresent()
            && valorUltimaFaturaCartao.get().compareTo(BigDecimal.ZERO) > 0) {
            saldoAnt = valorUltimaFaturaCartao.get();
        }

        BigDecimal saldoMes = money(extracted.path("saldoFaturaAtual"));
        if (saldoMes.compareTo(BigDecimal.ZERO) <= 0) {
            saldoMes = money(extracted.path("saldoDestaFatura"));
        }
        BigDecimal somaSemAnterior = somaLancamentosExcluindoSaldoAnterior(itens);
        if (saldoMes.compareTo(BigDecimal.ZERO) <= 0) {
            if (moedasQuaseIguais(somaSemAnterior, valorTotalPdf, tolerancia(valorTotalPdf))) {
                saldoMes = somaSemAnterior;
            } else if (moedasQuaseIguais(valorTotalPdf, somaSemAnterior, tolerancia(valorTotalPdf))) {
                saldoMes = valorTotalPdf;
            }
        }

        if (saldoAnt.compareTo(BigDecimal.ZERO) <= 0 || saldoMes.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        boolean bb = bancoPareceBb(bancoCartao);
        boolean padraoBb = moedasQuaseIguais(
            somaTodosLancamentos,
            saldoAnt.add(saldoMes).setScale(2, RoundingMode.HALF_UP),
            tolerancia(somaTodosLancamentos)
        ) || moedasQuaseIguais(somaTodosLancamentos, saldoAnt.add(valorTotalPdf), tolerancia(somaTodosLancamentos));

        if (!bb && !padraoBb) {
            return Optional.empty();
        }
        if (!bb) {
            return Optional.empty();
        }

        return Optional.of(new SaldoAnteriorBbPendente(
            saldoAnt.setScale(2, RoundingMode.HALF_UP),
            saldoMes.setScale(2, RoundingMode.HALF_UP),
            valorTotalPdf.setScale(2, RoundingMode.HALF_UP),
            somaTodosLancamentos.setScale(2, RoundingMode.HALF_UP)
        ));
    }

    private static BigDecimal inferirSaldoAnteriorDosLancamentos(
        List<ImportacaoFaturaItemDTO> itens,
        BigDecimal somaTodos,
        BigDecimal totalPdf
    ) {
        if (itens == null) {
            return BigDecimal.ZERO;
        }
        for (ImportacaoFaturaItemDTO item : itens) {
            if (descricaoEhLinhaSaldoFaturaAnterior(item.getDescricao()) && item.getValor() != null) {
                return item.getValor().abs().setScale(2, RoundingMode.HALF_UP);
            }
        }
        BigDecimal diff = somaTodos.subtract(totalPdf).setScale(2, RoundingMode.HALF_UP);
        if (diff.compareTo(BigDecimal.ZERO) > 0 && moedasQuaseIguais(somaTodos, totalPdf.add(diff), tolerancia(somaTodos))) {
            return diff;
        }
        return BigDecimal.ZERO;
    }

    public static void registrarPendenciaNasAuditorias(List<String> auditorias, SaldoAnteriorBbPendente pendente) {
        try {
            ObjectNode meta = metaJson(pendente, false, false);
            auditorias.add(0, META_SALDO_ANTERIOR_BB_PREFIX + MAPPER.writeValueAsString(meta));
            auditorias.add(1, textoPerguntaSaldo(pendente));
        } catch (Exception e) {
            auditorias.add(0, textoPerguntaSaldo(pendente));
        }
    }

    private static String textoPerguntaSaldo(SaldoAnteriorBbPendente p) {
        return "Fatura BB: saldo anterior " + formatBrl(p.saldoAnterior())
            + ", lançamentos desta fatura " + formatBrl(p.saldoMesAtual())
            + " (total no PDF " + formatBrl(p.totalPdf())
            + "; lista completa " + formatBrl(p.somaTodosLancamentos()) + " com linha de saldo anterior). "
            + "Responda *sim* para importar tudo (" + formatBrl(p.somaTodosLancamentos())
            + ") ou *não* para importar só esta fatura (" + formatBrl(p.saldoMesAtual())
            + ") sem a linha de saldo anterior.";
    }

    private static ObjectNode metaJson(SaldoAnteriorBbPendente p, boolean resolvido, boolean somou) {
        ObjectNode meta = MAPPER.createObjectNode();
        meta.put("saldoAnterior", p.saldoAnterior().toPlainString());
        meta.put("saldoMesAtual", p.saldoMesAtual().toPlainString());
        meta.put("saldoAtual", p.saldoMesAtual().toPlainString());
        meta.put("totalPdf", p.totalPdf().toPlainString());
        meta.put("somaTodosLancamentos", p.somaTodosLancamentos().toPlainString());
        meta.put("resolvido", resolvido);
        meta.put("somouSaldoAnterior", somou);
        return meta;
    }

    public static Optional<SaldoAnteriorBbMeta> lerMeta(List<String> auditoriasCompletas) {
        if (auditoriasCompletas == null) {
            return Optional.empty();
        }
        for (String linha : auditoriasCompletas) {
            if (linha == null || !linha.startsWith(META_SALDO_ANTERIOR_BB_PREFIX)) {
                continue;
            }
            try {
                String json = linha.substring(META_SALDO_ANTERIOR_BB_PREFIX.length());
                JsonNode n = MAPPER.readTree(json);
                BigDecimal saldoMes = new BigDecimal(
                    firstNonBlank(n.path("saldoMesAtual").asText(""), n.path("saldoAtual").asText("0")));
                return Optional.of(new SaldoAnteriorBbMeta(
                    new BigDecimal(n.path("saldoAnterior").asText("0")),
                    saldoMes,
                    new BigDecimal(n.path("totalPdf").asText("0")),
                    new BigDecimal(n.path("somaTodosLancamentos").asText("0")),
                    n.path("resolvido").asBoolean(false),
                    n.path("somouSaldoAnterior").asBoolean(false)
                ));
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static boolean pendenteNaoResolvido(List<String> auditoriasCompletas) {
        return lerMeta(auditoriasCompletas)
            .filter(m -> !m.resolvido())
            .isPresent();
    }

    public static boolean escolhaSaldoAnteriorJaResolvida(List<String> auditoriasCompletas) {
        return lerMeta(auditoriasCompletas)
            .filter(SaldoAnteriorBbMeta::resolvido)
            .isPresent();
    }

    public static List<String> marcarMetaResolvida(List<String> auditoriasCompletas, boolean somouSaldoAnterior) {
        List<String> out = new ArrayList<>();
        if (auditoriasCompletas == null) {
            return out;
        }
        Optional<SaldoAnteriorBbMeta> metaOpt = Optional.empty();
        for (String linha : auditoriasCompletas) {
            if (linha != null && linha.startsWith(META_SALDO_ANTERIOR_BB_PREFIX)) {
                metaOpt = lerMeta(List.of(linha));
                continue;
            }
            if (linha != null && ehAuditoriaDivergenciaChecksum(linha)) {
                continue;
            }
            if (linha != null && (linha.contains("Escolha *sim*") || linha.contains("parece incluir os dois")
                || linha.contains("Responda *sim* para importar tudo"))) {
                continue;
            }
            out.add(linha);
        }
        metaOpt.ifPresent(m -> {
            try {
                SaldoAnteriorBbPendente p = new SaldoAnteriorBbPendente(
                    m.saldoAnterior(), m.saldoMesAtual(), m.totalPdf(), m.somaTodosLancamentos());
                out.add(0, META_SALDO_ANTERIOR_BB_PREFIX + MAPPER.writeValueAsString(
                    metaJson(p, true, somouSaldoAnterior)));
            } catch (Exception ignored) {
                // omit
            }
        });
        return out;
    }

    public static boolean ehAuditoriaDivergenciaChecksum(String linha) {
        if (linha == null) {
            return false;
        }
        String n = ApelidoNormalizador.normalizar(linha);
        return n.contains("soma dos lancamentos extraidos") && n.contains("nao bate com o total da fatura");
    }

    /** {@code somar=true} → total da lista completa; {@code false} → só lançamentos do mês (total PDF / sem linha anterior). */
    public static BigDecimal valorTotalAposEscolha(SaldoAnteriorBbMeta meta, boolean somar) {
        if (somar) {
            if (meta.somaTodosLancamentos() != null && meta.somaTodosLancamentos().compareTo(BigDecimal.ZERO) > 0) {
                return meta.somaTodosLancamentos().setScale(2, RoundingMode.HALF_UP);
            }
            return meta.saldoAnterior().add(meta.saldoMesAtual()).setScale(2, RoundingMode.HALF_UP);
        }
        if (meta.saldoMesAtual() != null && meta.saldoMesAtual().compareTo(BigDecimal.ZERO) > 0) {
            return meta.saldoMesAtual().setScale(2, RoundingMode.HALF_UP);
        }
        return meta.totalPdf().setScale(2, RoundingMode.HALF_UP);
    }

    /** Desmarca linha «saldo fatura anterior» para não duplicar despesa ao confirmar. */
    public static int desmarcarLinhasSaldoAnterior(List<ImportacaoFaturaItemDTO> itens) {
        if (itens == null) {
            return 0;
        }
        int n = 0;
        for (ImportacaoFaturaItemDTO item : itens) {
            if (descricaoEhLinhaSaldoFaturaAnterior(item.getDescricao())) {
                item.setSelecionado(false);
                item.setNovo(false);
                n++;
            }
        }
        return n;
    }

    public static String mensagemPosEscolha(boolean somou, BigDecimal valorTotalFinal, int linhasSaldoAnteriorIgnoradas) {
        String base = somou
            ? "Total da fatura definido em " + formatBrl(valorTotalFinal)
                + " (lista completa, incluindo saldo anterior)."
            : "Total da fatura definido em " + formatBrl(valorTotalFinal)
                + " (apenas lançamentos desta fatura";
        if (linhasSaldoAnteriorIgnoradas > 0) {
            base += "; " + linhasSaldoAnteriorIgnoradas + " linha(s) de saldo anterior ignorada(s)";
        }
        return base + ").";
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null ? b : "");
    }

    private static BigDecimal money(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            if (n.isNumber()) {
                return n.decimalValue().setScale(2, RoundingMode.HALF_UP);
            }
            String t = n.asText("").trim();
            if (t.isBlank()) {
                return BigDecimal.ZERO;
            }
            t = t.replaceAll("[Rr$\\s]", "");
            if (t.contains(",") && t.contains(".")) {
                t = t.replace(".", "").replace(",", ".");
            } else if (t.contains(",")) {
                t = t.replace(",", ".");
            }
            return new BigDecimal(t).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static boolean moedasQuaseIguais(BigDecimal a, BigDecimal b, BigDecimal eps) {
        if (a == null || b == null) {
            return false;
        }
        return a.subtract(b).abs().compareTo(eps) <= 0;
    }

    private static BigDecimal tolerancia(BigDecimal ref) {
        return ref.multiply(new BigDecimal("0.02"))
            .max(new BigDecimal("2.00"))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static String formatBrl(BigDecimal v) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", v);
    }
}
