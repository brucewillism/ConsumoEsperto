package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final Pattern VALOR_FATURA_RESUMO = Pattern.compile(
        "(?i)valor da fatura[^\\d]{0,100}(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})"
    );
    private static final Pattern TOTAL_FATURA_RESUMO = Pattern.compile(
        "(?i)total da fatura[^\\d]{0,100}(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})"
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
        String resumo = textoPdf.length() > 4_500 ? textoPdf.substring(0, 4_500) : textoPdf;
        Matcher valor = VALOR_FATURA_RESUMO.matcher(resumo);
        if (valor.find()) {
            return Optional.of(parseMoney(valor.group(1)));
        }
        Matcher total = TOTAL_FATURA_RESUMO.matcher(resumo);
        if (total.find()) {
            return Optional.of(parseMoney(total.group(1)));
        }
        return Optional.empty();
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
        podarEspurios(destino, textoPdf);

        List<ImportacaoFaturaItemDTO> doTexto = extrairLancamentos(textoPdf, anoReferencia);
        Optional<BigDecimal> totalPdf = extrairTotalFatura(textoPdf);
        BigDecimal somaTexto = somaValores(doTexto);
        BigDecimal somaDestino = somaValores(destino);

        boolean textoMaisProximoDoTotal = totalPdf.isPresent()
            && !doTexto.isEmpty()
            && distanciaAoTotal(somaTexto, totalPdf.get())
                .compareTo(distanciaAoTotal(somaDestino, totalPdf.get())) < 0;
        boolean textoBateComTotal = totalPdf.isPresent()
            && !doTexto.isEmpty()
            && distanciaAoTotal(somaTexto, totalPdf.get()).compareTo(new BigDecimal("1.00")) <= 0;

        if (!doTexto.isEmpty() && (destino.isEmpty() || textoMaisProximoDoTotal || textoBateComTotal)) {
            int tamanhoAnterior = destino.size();
            BigDecimal somaAnterior = somaDestino;
            destino.clear();
            destino.addAll(doTexto);
            log.info(
                "Inter texto: lista substituída por {} lançamento(s) do PDF (antes: {} soma {}).",
                doTexto.size(),
                tamanhoAnterior,
                somaAnterior
            );
        } else if (!doTexto.isEmpty()) {
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

        podarEspurios(destino, textoPdf);
        totalPdf.ifPresent(total -> reconciliarComTotal(destino, doTexto, total));
    }

    /** Remove simulações, encargos e duplicatas de «Próximas faturas» mesmo quando a IA as incluiu. */
    public static void podarEspurios(List<ImportacaoFaturaItemDTO> itens, String textoPdf) {
        if (itens == null || itens.isEmpty() || textoPdf == null || textoPdf.isBlank()) {
            return;
        }
        int antes = itens.size();
        itens.removeIf(i -> deveIgnorarDescricao(i.getDescricao()) || pareceLinhaEncargoInter(i.getDescricao()));
        removerDuplicatasSecaoProximas(itens, textoPdf);
        deduplicarParcelasFuturasMesmoPlano(itens);
        int removidos = antes - itens.size();
        if (removidos > 0) {
            log.info("Inter poda: {} lançamento(s) espúrio(s) removido(s).", removidos);
        }
    }

    private static void reconciliarComTotal(
        List<ImportacaoFaturaItemDTO> destino,
        List<ImportacaoFaturaItemDTO> doTexto,
        BigDecimal total
    ) {
        if (destino == null || destino.isEmpty()) {
            return;
        }
        BigDecimal soma = somaValores(destino);
        if (distanciaAoTotal(soma, total).compareTo(new BigDecimal("1.00")) <= 0) {
            return;
        }
        if (!doTexto.isEmpty() && distanciaAoTotal(somaValores(doTexto), total)
            .compareTo(distanciaAoTotal(soma, total)) < 0) {
            destino.clear();
            destino.addAll(doTexto);
            log.info("Inter reconciliação: lista trocada pelo texto (soma {} → total {}).", somaValores(doTexto), total);
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
            int corte = indexOfIgnoreCase(norm, "data de corte");
            if (corte >= 0) {
                int nl = norm.indexOf('\n', corte);
                inicio = nl >= 0 ? nl + 1 : corte;
            }
        }
        if (inicio < 0) {
            int resumo = indexOfIgnoreCase(norm, "resumo da fatura");
            inicio = resumo >= 0 ? resumo : 0;
        }
        int fim = localizarFimTrecho(norm, inicio);
        return norm.substring(inicio, fim);
    }

    private static void removerDuplicatasSecaoProximas(List<ImportacaoFaturaItemDTO> itens, String textoPdf) {
        String norm = textoPdf.replace('\r', '\n');
        int proximas = indexOfIgnoreCase(norm, "proximas faturas");
        if (proximas < 0) {
            proximas = indexOfIgnoreCase(norm, "proxima fatura");
        }
        if (proximas < 0) {
            return;
        }
        int fimAtual = proximas;
        int inicioAtual = recortarInicioTransacoes(norm);
        String trechoAtual = norm.substring(inicioAtual, fimAtual);
        String trechoProximas = norm.substring(proximas, localizarFimTrecho(norm, proximas));
        Set<String> chavesAtual = chavesLancamentosNoTrecho(trechoAtual);
        Set<String> chavesProximas = chavesLancamentosNoTrecho(trechoProximas);
        chavesProximas.removeAll(chavesAtual);
        if (chavesProximas.isEmpty()) {
            return;
        }
        itens.removeIf(item -> chavesProximas.contains(chaveLancamento(item)));
    }

    private static int recortarInicioTransacoes(String norm) {
        int inicio = -1;
        for (String marcador : MARCADORES_INICIO_TRANSACOES) {
            int idx = indexOfIgnoreCase(norm, marcador);
            if (idx >= 0 && (inicio < 0 || idx < inicio)) {
                inicio = idx;
            }
        }
        if (inicio < 0) {
            int corte = indexOfIgnoreCase(norm, "data de corte");
            if (corte >= 0) {
                int nl = norm.indexOf('\n', corte);
                inicio = nl >= 0 ? nl + 1 : corte;
            }
        }
        return Math.max(inicio, 0);
    }

    private static Set<String> chavesLancamentosNoTrecho(String trecho) {
        Set<String> chaves = new LinkedHashSet<>();
        Matcher m = LINHA_LANCAMENTO.matcher(trecho);
        while (m.find()) {
            String descricao = limparDescricao(m.group(4));
            if (descricao.isBlank() || deveIgnorarDescricao(descricao) || pareceLinhaEncargoInter(descricao)) {
                continue;
            }
            BigDecimal valor = parseMoney(m.group(5));
            ImportacaoFaturaItemDTO tmp = new ImportacaoFaturaItemDTO();
            tmp.setDescricao(descricao);
            tmp.setValor(valor);
            aplicarParcelaDaDescricao(tmp, descricao);
            aplicarParcelaLinhaSeguinte(trecho, m.end(), tmp);
            chaves.add(chaveLancamento(descricao, valor, m.group(1), m.group(2), tmp.getParcelaAtual()));
        }
        return chaves;
    }

    private static String chaveLancamento(ImportacaoFaturaItemDTO item) {
        String dia = item.getData() != null ? String.format("%02d", item.getData().getDayOfMonth()) : "";
        String mes = item.getData() != null ? String.format("%02d", item.getData().getMonthValue()) : "";
        Integer parcela = item.getParcelaAtual();
        return chaveLancamento(item.getDescricao(), item.getValor(), dia, mes, parcela);
    }

    private static String chaveLancamento(
        String descricao,
        BigDecimal valor,
        String dia,
        String mes,
        Integer parcela
    ) {
        String desc = FaturaPdfLayoutSupport.norm(descricao);
        String vb = valor != null ? valor.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0";
        String p = parcela != null ? parcela.toString() : "";
        return dia + "/" + mes + "|" + desc.substring(0, Math.min(desc.length(), 80)) + "|" + vb + "|" + p;
    }

    private static void deduplicarParcelasFuturasMesmoPlano(List<ImportacaoFaturaItemDTO> itens) {
        Map<String, ImportacaoFaturaItemDTO> menorParcelaPorGrupo = new HashMap<>();
        Map<String, Integer> contagemPorGrupo = new HashMap<>();
        for (ImportacaoFaturaItemDTO item : itens) {
            String grupo = grupoParcelamento(item);
            if (grupo == null) {
                continue;
            }
            contagemPorGrupo.merge(grupo, 1, Integer::sum);
            ImportacaoFaturaItemDTO atual = menorParcelaPorGrupo.get(grupo);
            if (atual == null || parcelaMenorQue(item, atual)) {
                menorParcelaPorGrupo.put(grupo, item);
            }
        }
        Iterator<ImportacaoFaturaItemDTO> it = itens.iterator();
        while (it.hasNext()) {
            ImportacaoFaturaItemDTO item = it.next();
            String grupo = grupoParcelamento(item);
            if (grupo == null || contagemPorGrupo.getOrDefault(grupo, 0) < 2) {
                continue;
            }
            if (item != menorParcelaPorGrupo.get(grupo)) {
                it.remove();
            }
        }
    }

    private static String grupoParcelamento(ImportacaoFaturaItemDTO item) {
        if (item.getParcelaAtual() == null || item.getValor() == null) {
            return null;
        }
        String n = FaturaPdfLayoutSupport.norm(item.getDescricao());
        n = n.replaceAll("parcela\\s+\\d+\\s+de\\s+\\d+", " ")
            .replaceAll("\\d+\\s*/\\s*\\d+", " ")
            .replaceAll("\\s+", " ")
            .trim();
        if (!n.contains("parc")) {
            return null;
        }
        return n + "|" + item.getValor().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static boolean parcelaMenorQue(ImportacaoFaturaItemDTO a, ImportacaoFaturaItemDTO b) {
        return a.getParcelaAtual() != null && b.getParcelaAtual() != null
            && a.getParcelaAtual() < b.getParcelaAtual();
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
        return pareceLinhaEncargoInter(descricao)
            || n.contains("limite de credito")
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
            || n.contains("taxa efetiva")
            || n.contains("simulacao")
            || n.matches(".*\\d\\s*\\+\\s*\\d+x.*")
            || n.matches(".*ate\\s+\\d+\\s*x.*");
    }

    static boolean pareceLinhaEncargoInter(String descricao) {
        String n = FaturaPdfLayoutSupport.norm(descricao);
        if (n.isBlank()) {
            return false;
        }
        return n.startsWith("total a pagar")
            || n.contains("total a pagar em")
            || n.contains("encargos e iof")
            || n.contains("iof do rotativo")
            || n.contains("iof adicional")
            || n.contains("iof diario")
            || n.contains("encargos rotativos")
            || n.contains("juros do rotativo")
            || n.contains("juros rotativos")
            || n.contains("encargos em caso")
            || n.contains("valor total financiado");
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
