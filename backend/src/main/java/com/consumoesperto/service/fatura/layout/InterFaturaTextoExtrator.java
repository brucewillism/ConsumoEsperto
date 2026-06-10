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
 * Extrator determinístico de lançamentos Banco Inter a partir do texto do PDF.
 * Corta «Próximas faturas» e «Opções de pagamento» — fontes comuns de duplicação pela IA.
 */
@Slf4j
public final class InterFaturaTextoExtrator {

    private static final Pattern LINHA_LANCAMENTO = Pattern.compile(
        "(\\d{2})/(\\d{2})(?:/(\\d{4}))?\\s+(.+?)\\s+(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PARCELA_TEXTO = Pattern.compile(
        "(?i)parcela\\s+(\\d{1,2})\\s+de\\s+(\\d{1,2})"
    );
    private static final Pattern TOTAL_FATURA = Pattern.compile(
        "(?i)(?:valor da fatura|total da fatura|total a pagar|fatura atual)"
            + "[^\\d]{0,100}(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})"
    );
    private static final Pattern DATA_CORTE = Pattern.compile(
        "(?i)data de corte:?\\s*(\\d{2})/(\\d{2})/(\\d{4})"
    );
    private static final Pattern DATA_VENCIMENTO = Pattern.compile(
        "(?i)data de vencimento:?\\s*(\\d{2})/(\\d{2})/(\\d{4})"
    );
    private static final String[] MARCADORES_INICIO_TRANSACOES = {
        "detalhamento da fatura",
        "detalhamento de transacoes",
        "detalhamento de transações",
        "lancamentos desta fatura",
        "lançamentos desta fatura",
        "lancamentos do periodo",
        "lançamentos do período",
        "transacoes do cartao",
        "transações do cartão",
        "movimentacoes do cartao",
        "movimentações do cartão",
        "compras realizadas no mes",
        "compras realizadas no mês"
    };
    private static final String[] MARCADORES_FIM_TRANSACOES = {
        "proxima fatura",
        "próxima fatura",
        "proximas faturas",
        "próximas faturas",
        "itens parcelados para proximas",
        "itens parcelados para próximas",
        "opcoes de pagamento",
        "opções de pagamento",
        "parcelamento da fatura",
        "como parcelar",
        "encargos em caso de pagamento minimo",
        "encargos em caso de pagamento mínimo",
        "pagamento via boleto",
        "pagamento via pix",
        "limite de credito total",
        "limite de crédito total"
    };

    private InterFaturaTextoExtrator() {
    }

    public static Optional<BigDecimal> extrairTotalFatura(String textoPdf) {
        if (textoPdf == null || textoPdf.isBlank()) {
            return Optional.empty();
        }
        Matcher m = TOTAL_FATURA.matcher(textoPdf);
        BigDecimal melhor = null;
        while (m.find()) {
            BigDecimal valor = parseMoney(m.group(1));
            if (valor.compareTo(BigDecimal.ZERO) > 0) {
                melhor = valor;
            }
        }
        return Optional.ofNullable(melhor);
    }

    public static Optional<LocalDate> extrairDataCorte(String textoPdf) {
        if (textoPdf == null || textoPdf.isBlank()) {
            return Optional.empty();
        }
        Matcher m = DATA_CORTE.matcher(textoPdf);
        if (!m.find()) {
            return Optional.empty();
        }
        return parseDataCompleta(m.group(1), m.group(2), m.group(3));
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

        boolean textoMaisProximoDoTotal = totalPdf.isPresent()
            && distanciaAoTotal(somaTexto, totalPdf.get())
                .compareTo(distanciaAoTotal(somaDestino, totalPdf.get())) < 0;
        boolean textoBateComTotal = totalPdf.isPresent()
            && distanciaAoTotal(somaTexto, totalPdf.get()).compareTo(new BigDecimal("1.00")) <= 0;

        if (destino.isEmpty()) {
            destino.addAll(doTexto);
            log.info("Inter texto: {} lançamento(s) extraído(s) do PDF (IA vazia).", doTexto.size());
            return;
        }
        if (textoMaisProximoDoTotal || textoBateComTotal) {
            int tamanhoAnterior = destino.size();
            destino.clear();
            destino.addAll(doTexto);
            log.info(
                "Inter texto: lista substituída por {} lançamento(s) do PDF (IA tinha {} com soma {}).",
                doTexto.size(),
                tamanhoAnterior,
                somaDestino
            );
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
            log.info("Inter texto: {} lançamento(s) complementar(es) injetado(s).", inseridos);
        }
    }

    public static List<ImportacaoFaturaItemDTO> extrairLancamentos(String textoPdf, int anoReferencia) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        if (textoPdf == null || textoPdf.isBlank()) {
            return out;
        }
        String trecho = recortarTrechoTransacoes(textoPdf);
        Optional<LocalDate> dataCorte = extrairDataCorte(textoPdf);
        Optional<LocalDate> vencimento = extrairDataVencimento(textoPdf);
        int ano = vencimento.map(LocalDate::getYear).orElse(anoReferencia > 0 ? anoReferencia : YearMonth.now().getYear());

        Matcher m = LINHA_LANCAMENTO.matcher(trecho);
        while (m.find()) {
            String descricao = limparDescricao(m.group(4));
            if (descricao.isBlank() || deveIgnorarDescricao(descricao)) {
                continue;
            }
            BigDecimal valor = parseMoney(m.group(5));
            if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LocalDate data = parseDataInter(m.group(1), m.group(2), m.group(3), ano, vencimento);
            if (dataCorte.isPresent() && data != null && data.isAfter(dataCorte.get())) {
                continue;
            }
            ImportacaoFaturaItemDTO item = new ImportacaoFaturaItemDTO();
            item.setData(data);
            item.setDescricao(descricao);
            item.setValor(valor);
            aplicarParcelaDaDescricao(item, descricao);
            aplicarParcelaLinhaSeguinte(trecho, m.end(), item);
            if (!jaExiste(out, item)) {
                out.add(item);
            }
        }
        return out;
    }

    private static String recortarTrechoTransacoes(String textoPdf) {
        String norm = textoPdf.replace('\r', '\n');
        int inicio = -1;
        for (String marcador : MARCADORES_INICIO_TRANSACOES) {
            int idx = indexOfIgnoreCase(norm, marcador);
            if (idx >= 0 && (inicio < 0 || idx < inicio)) {
                inicio = idx;
            }
        }
        if (inicio < 0) {
            int resumo = indexOfIgnoreCase(norm, "resumo da fatura");
            inicio = resumo >= 0 ? resumo : 0;
        }
        int fim = localizarFimTrecho(norm, inicio);
        return norm.substring(inicio, fim);
    }

    private static int localizarFimTrecho(String norm, int inicio) {
        int fim = norm.length();
        String restante = norm.substring(inicio);
        for (String marcador : MARCADORES_FIM_TRANSACOES) {
            int idx = indexOfIgnoreCase(restante, marcador);
            if (idx > 0) {
                fim = Math.min(fim, inicio + idx);
            }
        }
        return fim;
    }

    private static void aplicarParcelaLinhaSeguinte(String trecho, int posAposMatch, ImportacaoFaturaItemDTO item) {
        if (item.getParcelaAtual() != null) {
            return;
        }
        int fimLinha = trecho.indexOf('\n', posAposMatch);
        if (fimLinha < 0) {
            fimLinha = Math.min(trecho.length(), posAposMatch + 80);
        }
        String proxima = trecho.substring(posAposMatch, fimLinha).trim();
        aplicarParcelaDaDescricao(item, proxima);
    }

    private static void aplicarParcelaDaDescricao(ImportacaoFaturaItemDTO item, String texto) {
        Matcher pm = PARCELA_TEXTO.matcher(texto);
        if (!pm.find()) {
            return;
        }
        try {
            item.setParcelaAtual(Integer.parseInt(pm.group(1)));
            item.setTotalParcelas(Integer.parseInt(pm.group(2)));
        } catch (NumberFormatException ignored) {
            // parcela opcional
        }
    }

    private static Optional<LocalDate> extrairDataVencimento(String textoPdf) {
        Matcher m = DATA_VENCIMENTO.matcher(textoPdf);
        if (!m.find()) {
            return Optional.empty();
        }
        return parseDataCompleta(m.group(1), m.group(2), m.group(3));
    }

    private static Optional<LocalDate> parseDataCompleta(String dia, String mes, String ano) {
        try {
            return Optional.of(LocalDate.of(
                Integer.parseInt(ano),
                Integer.parseInt(mes),
                Integer.parseInt(dia)
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static LocalDate parseDataInter(
        String dia,
        String mes,
        String anoTxt,
        int anoReferencia,
        Optional<LocalDate> vencimento
    ) {
        int d = Integer.parseInt(dia);
        int m = Integer.parseInt(mes);
        int ano = anoReferencia;
        if (anoTxt != null && !anoTxt.isBlank()) {
            ano = Integer.parseInt(anoTxt);
        } else if (vencimento.isPresent()) {
            LocalDate ven = vencimento.get();
            ano = ven.getYear();
            if (m > ven.getMonthValue()) {
                ano = ven.getYear() - 1;
            }
        }
        try {
            return LocalDate.of(ano, m, d);
        } catch (Exception e) {
            return LocalDate.of(anoReferencia, m, Math.min(d, 28));
        }
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

    static boolean deveIgnorarDescricao(String descricao) {
        String n = FaturaPdfLayoutSupport.norm(descricao);
        if (n.isBlank()) {
            return true;
        }
        return n.contains("limite de credito")
            || n.contains("limite disponivel")
            || n.contains("opcoes de pagamento")
            || n.contains("parcelar fatura")
            || n.contains("proximas faturas")
            || n.contains("proxima fatura")
            || n.contains("pagamento minimo")
            || n.contains("resumo da fatura")
            || n.contains("valor da fatura")
            || n.contains("total da fatura")
            || n.contains("data de vencimento")
            || n.contains("data de corte")
            || n.contains("saldo restante")
            || n.contains("fatura anterior")
            || n.contains("valor financiado")
            || n.contains("iof do rotativo")
            || n.contains("taxa efetiva")
            || n.contains("encargos rotativos")
            || n.contains("simulacao")
            || n.matches(".*\\d\\s*\\+\\s*\\d+x.*")
            || n.matches(".*ate\\s+\\d+\\s*x.*");
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
            boolean mesmaParcela = parcelasIguais(item, candidato);
            if (mesmoValor && (mesmaData || descSimilar) && mesmaParcela) {
                return true;
            }
        }
        return false;
    }

    private static boolean parcelasIguais(ImportacaoFaturaItemDTO a, ImportacaoFaturaItemDTO b) {
        if (a.getParcelaAtual() == null && b.getParcelaAtual() == null) {
            return true;
        }
        return a.getParcelaAtual() != null
            && b.getParcelaAtual() != null
            && a.getParcelaAtual().equals(b.getParcelaAtual())
            && a.getTotalParcelas() != null
            && b.getTotalParcelas() != null
            && a.getTotalParcelas().equals(b.getTotalParcelas());
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

    private static int indexOfIgnoreCase(String texto, String needle) {
        if (texto == null || needle == null) {
            return -1;
        }
        return texto.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }
}
