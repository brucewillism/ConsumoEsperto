package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrator determinístico de lançamentos Itaú a partir do texto do PDF.
 * Complementa omissões da IA (comum quando o layout cai em genérico ou a extração vem vazia).
 */
@Slf4j
public final class ItauFaturaTextoExtrator {

    private static final Pattern LINHA_LANCAMENTO = Pattern.compile(
        "(\\d{2})/(\\d{2})(?:/(\\d{2,4}))?\\s+(.+?)\\s+(?:(\\d{1,2})/(\\d{1,2})\\s+)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TOTAL_FATURA = Pattern.compile(
        "(?i)(?:total(?:\\s+desta)?\\s+fatura|total\\s+para\\s+pagamento|valor\\s+total(?:\\s+da\\s+fatura)?)"
            + "[^\\d]{0,80}(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})"
    );
    private static final Pattern PAGAMENTO_MINIMO = Pattern.compile(
        "(?i)pagamento\\s+m[ií]nimo[^\\d]{0,60}(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})"
    );

    private ItauFaturaTextoExtrator() {
    }

    public static Optional<BigDecimal> extrairTotalFatura(String textoPdf) {
        if (textoPdf == null || textoPdf.isBlank()) {
            return Optional.empty();
        }
        Matcher m = TOTAL_FATURA.matcher(textoPdf);
        if (!m.find()) {
            return Optional.empty();
        }
        return Optional.of(parseMoney(m.group(1)));
    }

    public static Optional<BigDecimal> extrairPagamentoMinimo(String textoPdf) {
        if (textoPdf == null || textoPdf.isBlank()) {
            return Optional.empty();
        }
        Matcher m = PAGAMENTO_MINIMO.matcher(textoPdf);
        if (!m.find()) {
            return Optional.empty();
        }
        return Optional.of(parseMoney(m.group(1)));
    }

    public static void complementar(List<ImportacaoFaturaItemDTO> destino, String textoPdf, int anoReferencia) {
        if (destino == null || textoPdf == null || textoPdf.isBlank()) {
            return;
        }
        List<ImportacaoFaturaItemDTO> doTexto = extrairLancamentos(textoPdf, anoReferencia);
        if (doTexto.isEmpty()) {
            return;
        }
        if (destino.isEmpty()) {
            destino.addAll(doTexto);
            log.info("Itaú texto: {} lançamento(s) extraído(s) do PDF (IA vazia).", doTexto.size());
            return;
        }
        int inseridos = 0;
        for (ImportacaoFaturaItemDTO candidato : doTexto) {
            if (!jaExiste(destino, candidato)) {
                destino.add(candidato);
                inseridos++;
            }
        }
        if (inseridos > 0) {
            log.info("Itaú texto: {} lançamento(s) complementar(es) injetado(s).", inseridos);
        }
    }

    public static List<ImportacaoFaturaItemDTO> extrairLancamentos(String textoPdf, int anoReferencia) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        if (textoPdf == null || textoPdf.isBlank()) {
            return out;
        }
        String trecho = recortarTrechoLancamentos(textoPdf);
        Matcher m = LINHA_LANCAMENTO.matcher(trecho);
        while (m.find()) {
            String descricao = limparDescricao(m.group(4));
            if (descricao.isBlank() || deveIgnorarDescricao(descricao)) {
                continue;
            }
            BigDecimal valor = parseMoney(m.group(7));
            if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LocalDate data = parseDataItau(m.group(1), m.group(2), m.group(3), anoReferencia);
            ImportacaoFaturaItemDTO item = new ImportacaoFaturaItemDTO();
            item.setData(data);
            item.setDescricao(descricao);
            item.setValor(valor);
            if (m.group(5) != null && m.group(6) != null) {
                try {
                    item.setParcelaAtual(Integer.parseInt(m.group(5)));
                    item.setTotalParcelas(Integer.parseInt(m.group(6)));
                } catch (NumberFormatException ignored) {
                    // parcela opcional
                }
            }
            if (!jaExiste(out, item)) {
                out.add(item);
            }
        }
        return out;
    }

    private static String recortarTrechoLancamentos(String textoPdf) {
        String norm = textoPdf.replace('\r', '\n');
        int inicio = indexOfIgnoreCase(norm, "LANÇAMENTOS");
        if (inicio < 0) {
            inicio = indexOfIgnoreCase(norm, "compras e saques");
        }
        if (inicio < 0) {
            inicio = indexOfIgnoreCase(norm, "DATA ESTABELECIMENTO");
        }
        if (inicio < 0) {
            return norm;
        }
        int fim = norm.length();
        for (String marcador : new String[] {
            "Total desta fatura", "Total da fatura", "Total para pagamento",
            "Encargos financeiros", "Simulação de parcelamento", "Limite de crédito"
        }) {
            int idx = indexOfIgnoreCase(norm.substring(inicio), marcador);
            if (idx > 0) {
                fim = Math.min(fim, inicio + idx);
            }
        }
        return norm.substring(inicio, fim);
    }

    private static String limparDescricao(String raw) {
        if (raw == null) {
            return "";
        }
        String d = raw.replaceAll("\\s+", " ").trim();
        d = d.replaceAll("(?i)R\\$\\s*[\\d.,]+", "").trim();
        if (d.length() > 140) {
            d = d.substring(0, 140).trim();
        }
        return d;
    }

    private static boolean deveIgnorarDescricao(String descricao) {
        String n = FaturaPdfLayoutSupport.norm(descricao);
        if (n.isBlank()) {
            return true;
        }
        return n.contains("limite de credito")
            || n.contains("pontos itau")
            || n.contains("simulacao de parcelamento")
            || n.contains("total dos lancamentos")
            || n.contains("saldo anterior")
            || n.contains("pagamento efetuado")
            || n.contains("data estabelecimento")
            || n.contains("valor em r");
    }

    private static LocalDate parseDataItau(String dia, String mes, String anoTxt, int anoReferencia) {
        int d = Integer.parseInt(dia);
        int m = Integer.parseInt(mes);
        int ano = anoReferencia > 0 ? anoReferencia : YearMonth.now().getYear();
        if (anoTxt != null && !anoTxt.isBlank()) {
            int a = Integer.parseInt(anoTxt);
            ano = a < 100 ? 2000 + a : a;
        }
        try {
            return LocalDate.of(ano, m, d);
        } catch (Exception e) {
            return LocalDate.of(anoReferencia > 0 ? anoReferencia : YearMonth.now().getYear(), m, Math.min(d, 28));
        }
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
            boolean descSimilar = descItem.contains(descCand) || descCand.contains(descItem);
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
