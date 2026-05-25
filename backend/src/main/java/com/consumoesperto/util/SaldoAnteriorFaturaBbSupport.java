package com.consumoesperto.util;

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
 * Faturas Banco do Brasil costumam exibir saldo da fatura anterior + saldo desta fatura;
 * o total a pagar pode ser a soma dos dois — o utilizador escolhe se importa o combinado ou só o mês atual.
 */
public final class SaldoAnteriorFaturaBbSupport {

    public static final String META_SALDO_ANTERIOR_BB_PREFIX = "__SALDO_ANTERIOR_BB__:";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SaldoAnteriorFaturaBbSupport() {
    }

    public record SaldoAnteriorBbPendente(
        BigDecimal saldoAnterior,
        BigDecimal saldoAtual,
        BigDecimal totalPdf
    ) {}

    public record SaldoAnteriorBbMeta(
        BigDecimal saldoAnterior,
        BigDecimal saldoAtual,
        BigDecimal totalPdf,
        boolean resolvido,
        boolean somouSaldoAnterior
    ) {}

    public static boolean bancoPareceBb(String bancoCartao) {
        return BancoBrasilCatalog.bancosCorrespondem("bb", bancoCartao);
    }

    /**
     * Detecta quando o PDF traz saldo anterior + saldo atual e o total parece ser a soma dos dois.
     */
    public static Optional<SaldoAnteriorBbPendente> detectar(
        JsonNode extracted,
        String bancoCartao,
        BigDecimal valorTotalPdf,
        BigDecimal somaLancamentos,
        Optional<BigDecimal> valorUltimaFaturaCartao
    ) {
        if (valorTotalPdf == null || valorTotalPdf.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        BigDecimal saldoAnt = money(extracted.path("saldoFaturaAnterior"));
        BigDecimal saldoAtual = money(extracted.path("saldoFaturaAtual"));
        if (saldoAtual.compareTo(BigDecimal.ZERO) <= 0) {
            saldoAtual = money(extracted.path("saldoDestaFatura"));
        }
        if (saldoAtual.compareTo(BigDecimal.ZERO) <= 0 && somaLancamentos != null
            && somaLancamentos.compareTo(BigDecimal.ZERO) > 0) {
            saldoAtual = somaLancamentos;
        }
        if (saldoAnt.compareTo(BigDecimal.ZERO) <= 0
            && valorUltimaFaturaCartao.isPresent()
            && valorUltimaFaturaCartao.get().compareTo(BigDecimal.ZERO) > 0) {
            saldoAnt = valorUltimaFaturaCartao.get();
        }

        if (saldoAnt.compareTo(BigDecimal.ZERO) <= 0 || saldoAtual.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        BigDecimal soma = saldoAnt.add(saldoAtual).setScale(2, RoundingMode.HALF_UP);
        boolean totalPareceSoma = moedasQuaseIguais(valorTotalPdf, soma, tolerancia(soma));
        boolean bb = bancoPareceBb(bancoCartao);

        if (!bb) {
            if (!totalPareceSoma) {
                return Optional.empty();
            }
        } else if (!totalPareceSoma) {
            BigDecimal diff = valorTotalPdf.subtract(saldoAtual).abs();
            if (diff.compareTo(tolerancia(saldoAtual)) <= 0) {
                return Optional.empty();
            }
        }

        return Optional.of(new SaldoAnteriorBbPendente(
            saldoAnt.setScale(2, RoundingMode.HALF_UP),
            saldoAtual.setScale(2, RoundingMode.HALF_UP),
            valorTotalPdf.setScale(2, RoundingMode.HALF_UP)
        ));
    }

    public static void registrarPendenciaNasAuditorias(List<String> auditorias, SaldoAnteriorBbPendente pendente) {
        try {
            ObjectNode meta = MAPPER.createObjectNode();
            meta.put("saldoAnterior", pendente.saldoAnterior().toPlainString());
            meta.put("saldoAtual", pendente.saldoAtual().toPlainString());
            meta.put("totalPdf", pendente.totalPdf().toPlainString());
            meta.put("resolvido", false);
            meta.put("somouSaldoAnterior", false);
            auditorias.add(0, META_SALDO_ANTERIOR_BB_PREFIX + MAPPER.writeValueAsString(meta));
            auditorias.add(1,
                "Fatura com saldo anterior de " + formatBrl(pendente.saldoAnterior())
                    + " e saldo desta fatura de " + formatBrl(pendente.saldoAtual())
                    + ". O total do PDF (" + formatBrl(pendente.totalPdf())
                    + ") parece incluir os dois. Escolha *sim* para somar e importar ou *não* para importar só o saldo atual.");
        } catch (Exception e) {
            auditorias.add(0,
                "Fatura BB: saldo anterior " + formatBrl(pendente.saldoAnterior())
                    + " e saldo atual " + formatBrl(pendente.saldoAtual())
                    + ". Responda *sim* (somar) ou *não* (só atual).");
        }
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
                return Optional.of(new SaldoAnteriorBbMeta(
                    new BigDecimal(n.path("saldoAnterior").asText("0")),
                    new BigDecimal(n.path("saldoAtual").asText("0")),
                    new BigDecimal(n.path("totalPdf").asText("0")),
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
            out.add(linha);
        }
        metaOpt.ifPresent(m -> {
            try {
                ObjectNode meta = MAPPER.createObjectNode();
                meta.put("saldoAnterior", m.saldoAnterior().toPlainString());
                meta.put("saldoAtual", m.saldoAtual().toPlainString());
                meta.put("totalPdf", m.totalPdf().toPlainString());
                meta.put("resolvido", true);
                meta.put("somouSaldoAnterior", somouSaldoAnterior);
                out.add(0, META_SALDO_ANTERIOR_BB_PREFIX + MAPPER.writeValueAsString(meta));
            } catch (Exception ignored) {
                // omit meta on failure
            }
        });
        return out;
    }

    public static BigDecimal valorTotalAposEscolha(SaldoAnteriorBbMeta meta, boolean somar) {
        if (somar) {
            return meta.saldoAnterior().add(meta.saldoAtual()).setScale(2, RoundingMode.HALF_UP);
        }
        return meta.saldoAtual().setScale(2, RoundingMode.HALF_UP);
    }

    public static String mensagemPosEscolha(boolean somou, BigDecimal valorTotalFinal) {
        if (somou) {
            return "Total da fatura definido em " + formatBrl(valorTotalFinal)
                + " (saldo anterior + saldo desta fatura).";
        }
        return "Total da fatura definido em " + formatBrl(valorTotalFinal)
            + " (apenas saldo desta fatura, sem somar o anterior).";
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
