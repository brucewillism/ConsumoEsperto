package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrator determinístico de lançamentos Nubank a partir do texto do PDF.
 * Complementa omissões da IA (comum em faturas longas com Pix no crédito).
 */
@Slf4j
public final class NubankFaturaTextoExtrator {

    private static final Pattern BLOCO_DATA = Pattern.compile(
        "(?m)(\\d{2})\\s+(ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ|JAN|FEV|MAR)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern VALOR_RS = Pattern.compile(
        "(?:−|-)?R\\$\\s*([\\d.]+,\\d{2})",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TOTAL_COMPRAS = Pattern.compile(
        "Total de compras de todos os cart[oõ]es[^\\d]{0,80}R\\$\\s*([\\d.]+,\\d{2})",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PARCELA = Pattern.compile(
        "(?i)(?:parc(?:ela)?\\.?\\s*)?(\\d{1,2})\\s*/\\s*(\\d{1,2})"
    );
    private static final Pattern TOTAL_A_PAGAR = Pattern.compile(
        "(?i)total a pagar[^\\d]{0,12}R\\$\\s*([\\d.]+,\\d{2})"
    );

    private NubankFaturaTextoExtrator() {
    }

    public static Optional<BigDecimal> extrairTotalCompras(String textoPdf) {
        if (textoPdf == null || textoPdf.isBlank()) {
            return Optional.empty();
        }
        Matcher m = TOTAL_COMPRAS.matcher(textoPdf);
        if (!m.find()) {
            return Optional.empty();
        }
        return Optional.of(parseMoney(m.group(1)));
    }

    public static void complementar(List<ImportacaoFaturaItemDTO> destino, String textoPdf, int anoReferencia) {
        if (destino == null || textoPdf == null || textoPdf.isBlank()) {
            return;
        }
        int inseridos = 0;
        for (ImportacaoFaturaItemDTO candidato : extrairLancamentos(textoPdf, anoReferencia)) {
            if (!jaExiste(destino, candidato)) {
                destino.add(candidato);
                inseridos++;
                log.info("Complemento texto Nubank: '{}' = {}", candidato.getDescricao(), candidato.getValor());
            }
        }
        if (inseridos > 0) {
            log.info("Nubank texto: {} lançamento(s) complementar(es) injetado(s).", inseridos);
        }
    }

    public static List<ImportacaoFaturaItemDTO> extrairLancamentos(String textoPdf, int anoReferencia) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        int inicio = indexOfIgnoreCase(textoPdf, "TRANSAÇÕES DE");
        if (inicio < 0) {
            inicio = indexOfIgnoreCase(textoPdf, "TRANSAÇÕES");
        }
        String trecho = inicio >= 0 ? textoPdf.substring(inicio) : textoPdf;
        Matcher m = BLOCO_DATA.matcher(trecho);
        List<int[]> blocos = new ArrayList<>();
        while (m.find()) {
            blocos.add(new int[] { m.start(), m.end() });
        }
        for (int i = 0; i < blocos.size(); i++) {
            int start = blocos.get(i)[0];
            int end = i + 1 < blocos.size() ? blocos.get(i + 1)[0] : trecho.length();
            String bloco = trecho.substring(start, end).trim();
            parseBloco(bloco, anoReferencia).ifPresent(out::add);
        }
        return out;
    }

    private static Optional<ImportacaoFaturaItemDTO> parseBloco(String bloco, int anoReferencia) {
        Matcher dm = BLOCO_DATA.matcher(bloco);
        if (!dm.find()) {
            return Optional.empty();
        }
        String norm = FaturaPdfLayoutSupport.norm(bloco);
        if (norm.contains("estorno")
            || norm.contains("pagamento em")
            || norm.contains("saldo restante")
            || norm.contains("pagamentos e financiamentos")) {
            return Optional.empty();
        }
        if (bloco.contains("−R$") || bloco.contains("-R$")) {
            return Optional.empty();
        }

        LocalDate data = parseDataNubank(dm.group(1), dm.group(2), anoReferencia);
        boolean pix = norm.contains("total a pagar");
        boolean cartao = bloco.contains("••••");
        if (!pix && !cartao) {
            return Optional.empty();
        }

        BigDecimal valor = null;
        if (pix) {
            Matcher tm = TOTAL_A_PAGAR.matcher(bloco);
            if (tm.find()) {
                valor = parseMoney(tm.group(1));
            }
        }
        if (valor == null) {
            Matcher vm = VALOR_RS.matcher(bloco);
            String ultimoValor = null;
            while (vm.find()) {
                if (!vm.group(0).startsWith("−") && !vm.group(0).startsWith("-")) {
                    ultimoValor = vm.group(1);
                }
            }
            if (ultimoValor == null) {
                return Optional.empty();
            }
            valor = parseMoney(ultimoValor);
        }
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        String descricao = extrairDescricao(bloco, pix);
        if (descricao.isBlank()) {
            return Optional.empty();
        }

        ImportacaoFaturaItemDTO item = new ImportacaoFaturaItemDTO();
        item.setData(data);
        item.setDescricao(descricao);
        item.setValor(valor);
        aplicarParcelaNaDescricao(item);
        return Optional.of(item);
    }

    private static String extrairDescricao(String bloco, boolean pix) {
        String limpo = bloco.replaceAll("(?m)^\\d{2}\\s+(?:ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ|JAN|FEV|MAR)\\s*", "");
        limpo = limpo.replaceAll("••••\\s*\\d{4}\\s*", "").trim();
        if (pix) {
            int idx = limpo.toLowerCase(Locale.ROOT).indexOf("total a pagar");
            if (idx > 0) {
                limpo = limpo.substring(0, idx).trim();
            }
        }
        limpo = limpo.replaceAll("(?i)R\\$\\s*[\\d.,]+.*$", "").trim();
        limpo = limpo.replaceAll("\\s+", " ");
        return limpo.length() > 120 ? limpo.substring(0, 120).trim() : limpo;
    }

    private static void aplicarParcelaNaDescricao(ImportacaoFaturaItemDTO item) {
        if (item.getDescricao() == null) {
            return;
        }
        Matcher m = PARCELA.matcher(item.getDescricao());
        if (!m.find()) {
            return;
        }
        try {
            int atual = Integer.parseInt(m.group(1));
            int total = Integer.parseInt(m.group(2));
            if (atual >= 1 && total > 1 && atual <= total) {
                item.setParcelaAtual(atual);
                item.setTotalParcelas(total);
            }
        } catch (Exception ignored) {
            // mantém sem parcela
        }
    }

    private static LocalDate parseDataNubank(String dia, String mesTxt, int anoReferencia) {
        int diaInt = Integer.parseInt(dia);
        Month mes = parseMesAbrev(mesTxt);
        YearMonth ref = YearMonth.of(anoReferencia, mes);
        return LocalDate.of(ref.getYear(), ref.getMonth(), Math.min(diaInt, ref.lengthOfMonth()));
    }

    private static Month parseMesAbrev(String abrev) {
        return switch (abrev.toUpperCase(Locale.ROOT)) {
            case "JAN" -> Month.JANUARY;
            case "FEV" -> Month.FEBRUARY;
            case "MAR" -> Month.MARCH;
            case "ABR" -> Month.APRIL;
            case "MAI" -> Month.MAY;
            case "JUN" -> Month.JUNE;
            case "JUL" -> Month.JULY;
            case "AGO" -> Month.AUGUST;
            case "SET" -> Month.SEPTEMBER;
            case "OUT" -> Month.OCTOBER;
            case "NOV" -> Month.NOVEMBER;
            case "DEZ" -> Month.DECEMBER;
            default -> Month.JANUARY;
        };
    }

    private static BigDecimal parseMoney(String raw) {
        String n = raw.replace(".", "").replace(",", ".").trim();
        return new BigDecimal(n).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean jaExiste(List<ImportacaoFaturaItemDTO> itens, ImportacaoFaturaItemDTO candidato) {
        String descCand = FaturaPdfLayoutSupport.norm(candidato.getDescricao());
        for (ImportacaoFaturaItemDTO item : itens) {
            if (item.getValor() == null || candidato.getValor() == null) {
                continue;
            }
            boolean mesmaData = candidato.getData() != null && candidato.getData().equals(item.getData());
            boolean mesmoValor = item.getValor().subtract(candidato.getValor()).abs()
                .compareTo(new BigDecimal("0.04")) <= 0;
            String descItem = FaturaPdfLayoutSupport.norm(item.getDescricao());
            boolean descSimilar = descItem.contains(descCand) || descCand.contains(descItem)
                || descItem.length() > 8 && descCand.length() > 8
                && descItem.substring(0, Math.min(12, descItem.length()))
                    .equals(descCand.substring(0, Math.min(12, descCand.length())));
            if (mesmoValor && (mesmaData || descSimilar)) {
                return true;
            }
        }
        return false;
    }

    private static int indexOfIgnoreCase(String texto, String needle) {
        if (texto == null || needle == null) {
            return -1;
        }
        return texto.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }
}
