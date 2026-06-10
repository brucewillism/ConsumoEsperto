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
    /** Encargos, IOF e anuidade costumam vir sem data no bloco «Encargos financeiros». */
    private static final Pattern LINHA_ENCARGO_SEM_DATA = Pattern.compile(
        "(?i)^\\s*([A-Za-zÀ-ú][A-Za-zÀ-ú0-9\\s./\\-]{2,}?)\\s+(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s*$"
    );
    private static final String[] MARCADORES_FIM_FATURA = {
        "Total desta fatura", "Total da fatura", "Total para pagamento",
        "Simulação de parcelamento", "Limite de crédito"
    };
    private static final String[] MARCADORES_INICIO_SECAO = {
        "LANÇAMENTOS: compras e saques",
        "LANÇAMENTOS compras e saques",
        "LANÇAMENTOS: produtos e serviços",
        "LANÇAMENTOS produtos e serviços",
        "Encargos financeiros",
        "LANÇAMENTOS",
        "compras e saques",
        "DATA ESTABELECIMENTO"
    };
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
        Optional<BigDecimal> totalPdf = extrairTotalFatura(textoPdf);
        BigDecimal somaTexto = somaValores(doTexto);
        BigDecimal somaDestino = somaValores(destino);

        boolean textoMaisCompleto = doTexto.size() > destino.size();
        boolean textoMaisProximoDoTotal = totalPdf.isPresent()
            && distanciaAoTotal(somaTexto, totalPdf.get())
                .compareTo(distanciaAoTotal(somaDestino, totalPdf.get())) < 0;

        if (destino.isEmpty() || textoMaisCompleto || textoMaisProximoDoTotal) {
            mesclarPreferindoTexto(destino, doTexto);
            reconciliarComTotalFatura(destino, textoPdf, anoReferencia, totalPdf);
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
        reconciliarComTotalFatura(destino, textoPdf, anoReferencia, totalPdf);
    }

    public static List<ImportacaoFaturaItemDTO> extrairLancamentos(String textoPdf, int anoReferencia) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        if (textoPdf == null || textoPdf.isBlank()) {
            return out;
        }
        String trecho = recortarTrechosLancamentos(textoPdf);
        extrairLinhasComData(trecho, anoReferencia, out);
        extrairEncargosFinanceiros(textoPdf, out);
        return out;
    }

    private static void extrairLinhasComData(String trecho, int anoReferencia, List<ImportacaoFaturaItemDTO> out) {
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
                    int parcelaAtual = Integer.parseInt(m.group(5));
                    int totalParcelas = Integer.parseInt(m.group(6));
                    if (parcelaAtual >= 1 && totalParcelas > 1 && parcelaAtual <= totalParcelas) {
                        item.setParcelaAtual(parcelaAtual);
                        item.setTotalParcelas(totalParcelas);
                    }
                } catch (NumberFormatException ignored) {
                    // parcela opcional
                }
            }
            aplicarParcelaNaDescricao(item, descricao);
            if (!jaExiste(out, item)) {
                out.add(item);
            }
        }
    }

    private static void extrairEncargosFinanceiros(String textoPdf, List<ImportacaoFaturaItemDTO> out) {
        String norm = textoPdf.replace('\r', '\n');
        int inicio = indexOfIgnoreCase(norm, "Encargos financeiros");
        if (inicio < 0) {
            inicio = indexOfIgnoreCase(norm, "encargos e custo");
        }
        if (inicio < 0) {
            return;
        }
        int fim = localizarFimTrecho(norm, inicio);
        String trecho = norm.substring(inicio, fim);
        for (String linha : trecho.split("\n")) {
            String trimmed = linha.trim();
            if (trimmed.isBlank() || trimmed.matches("(?i).*encargos financeiros.*")) {
                continue;
            }
            if (trimmed.matches("(?i)\\d{2}/\\d{2}.*")) {
                continue;
            }
            Matcher m = LINHA_ENCARGO_SEM_DATA.matcher(trimmed);
            if (!m.matches()) {
                continue;
            }
            String descricao = limparDescricao(m.group(1));
            if (descricao.isBlank() || deveIgnorarDescricao(descricao)) {
                continue;
            }
            BigDecimal valor = parseMoney(m.group(2));
            if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            ImportacaoFaturaItemDTO item = new ImportacaoFaturaItemDTO();
            item.setDescricao(descricao);
            item.setValor(valor);
            if (!jaExiste(out, item)) {
                out.add(item);
                log.info("Itaú encargo extraído do PDF: '{}' = {}", descricao, valor);
            }
        }
    }

    /** Do primeiro bloco de lançamentos até o total da fatura (inclui encargos e produtos/serviços). */
    private static String recortarTrechosLancamentos(String textoPdf) {
        String norm = textoPdf.replace('\r', '\n');
        int inicio = -1;
        for (String marcador : MARCADORES_INICIO_SECAO) {
            int idx = indexOfIgnoreCase(norm, marcador);
            if (idx >= 0 && (inicio < 0 || idx < inicio)) {
                inicio = idx;
            }
        }
        if (inicio < 0) {
            inicio = 0;
        }
        int fim = localizarFimTrecho(norm, inicio);
        return norm.substring(inicio, fim);
    }

    private static int localizarFimTrecho(String norm, int inicio) {
        int fim = norm.length();
        String restante = norm.substring(inicio);
        for (String marcador : MARCADORES_FIM_FATURA) {
            int idx = indexOfIgnoreCase(restante, marcador);
            if (idx > 0) {
                fim = Math.min(fim, inicio + idx);
            }
        }
        return fim;
    }

    private static void reconciliarComTotalFatura(
        List<ImportacaoFaturaItemDTO> destino,
        String textoPdf,
        int anoReferencia,
        Optional<BigDecimal> totalPdf
    ) {
        if (destino == null || destino.isEmpty() || totalPdf.isEmpty()) {
            return;
        }
        BigDecimal total = totalPdf.get();
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal soma = somaValores(destino);
        BigDecimal diff = total.subtract(soma).abs();
        if (diff.compareTo(new BigDecimal("1.00")) <= 0) {
            return;
        }
        List<ImportacaoFaturaItemDTO> reforco = extrairLancamentos(textoPdf, anoReferencia);
        int inseridos = 0;
        for (ImportacaoFaturaItemDTO candidato : reforco) {
            if (!jaExiste(destino, candidato)) {
                destino.add(candidato);
                inseridos++;
            }
        }
        if (inseridos > 0) {
            log.info(
                "Itaú reconciliação: {} lançamento(s) injetado(s); soma {} → total {}.",
                inseridos,
                somaValores(destino),
                total
            );
        }
    }

    private static void mesclarPreferindoTexto(List<ImportacaoFaturaItemDTO> destino, List<ImportacaoFaturaItemDTO> doTexto) {
        List<ImportacaoFaturaItemDTO> mesclado = new ArrayList<>(doTexto);
        int extrasIa = 0;
        for (ImportacaoFaturaItemDTO ia : destino) {
            if (!jaExiste(mesclado, ia)) {
                mesclado.add(ia);
                extrasIa++;
            }
        }
        destino.clear();
        destino.addAll(mesclado);
        log.info(
            "Itaú texto: mescla com {} lançamento(s) do PDF (+{} só na IA).",
            doTexto.size(),
            extrasIa
        );
    }

    private static BigDecimal somaValores(List<ImportacaoFaturaItemDTO> itens) {
        if (itens == null || itens.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return itens.stream()
            .map(i -> i.getValor() != null ? i.getValor() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal distanciaAoTotal(BigDecimal soma, BigDecimal total) {
        if (soma == null || total == null) {
            return BigDecimal.valueOf(Long.MAX_VALUE);
        }
        return soma.subtract(total).abs();
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
            || n.startsWith("total ")
            || n.contains("total encargos")
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

    /** «Parcela 04 de 06» ou último NN/NN válido na descrição (evita confundir data DD/MM com parcela). */
    private static void aplicarParcelaNaDescricao(ImportacaoFaturaItemDTO item, String descricao) {
        if (item.getParcelaAtual() != null && item.getTotalParcelas() != null && item.getTotalParcelas() > 1) {
            return;
        }
        Matcher parcelaDe = Pattern.compile("(?i)parcela\\s+(\\d{1,2})\\s+de\\s+(\\d{1,2})").matcher(descricao);
        if (parcelaDe.find()) {
            preencherParcelaSeValida(item, parcelaDe.group(1), parcelaDe.group(2));
            return;
        }
        Matcher slash = Pattern.compile("(?i)(?:parc(?:ela)?\\.?\\s*)?(\\d{1,2})\\s*/\\s*(\\d{1,2})").matcher(descricao);
        while (slash.find()) {
            if (preencherParcelaSeValida(item, slash.group(1), slash.group(2))) {
                return;
            }
        }
    }

    private static boolean preencherParcelaSeValida(ImportacaoFaturaItemDTO item, String atualRaw, String totalRaw) {
        try {
            int atual = Integer.parseInt(atualRaw);
            int total = Integer.parseInt(totalRaw);
            if (atual >= 1 && total > 1 && atual <= total) {
                item.setParcelaAtual(atual);
                item.setTotalParcelas(total);
                return true;
            }
        } catch (NumberFormatException ignored) {
            // tenta próximo candidato
        }
        return false;
    }
}
