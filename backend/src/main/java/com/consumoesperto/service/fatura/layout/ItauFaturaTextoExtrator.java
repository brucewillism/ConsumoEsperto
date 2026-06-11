package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.dto.ProjecaoFaturaMesDTO;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrator determinístico de lançamentos Itaú a partir do texto do PDF.
 * Complementa omissões da IA (comum quando o layout cai em genérico ou a extração vem vazia).
 */
@Slf4j
public final class ItauFaturaTextoExtrator {

    private static final Pattern LINHA_LANCAMENTO = Pattern.compile(
        "(\\d{2})/(\\d{2})(?:/(\\d{2,4}))?\\s+(.+?)\\s+(?:(\\d{1,2})/(\\d{1,2})\\s+)?(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})",
        Pattern.CASE_INSENSITIVE
    );
    /** Itaú: coluna VALOR antes de PARCELA — ex. {@code 05/05 LOJA 89,90 03/12}. */
    private static final Pattern LINHA_LANCAMENTO_PARCELA_APOS_VALOR = Pattern.compile(
        "(\\d{2})/(\\d{2})(?:/(\\d{2,4}))?\\s+(.+?)\\s+(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s+(\\d{1,2})/(\\d{1,2})\\s*$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LINHA_PROXIMA_FATURA = Pattern.compile(
        "^(\\d{2})/(\\d{2})(?:/(\\d{2,4}))?\\s+(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s*$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LINHA_PROXIMA_FATURA_VALOR_ANTES = Pattern.compile(
        "(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s+(?:em\\s+)?(\\d{2})/(\\d{2})(?:/(\\d{2,4}))?",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LINHA_PROXIMA_FATURA_MES_ANO = Pattern.compile(
        "^(\\d{2})/(\\d{4})\\s+(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s*$",
        Pattern.CASE_INSENSITIVE
    );
    /** Itaú costuma listar só mês/ano: {@code 07/26 1.234,56} (sem dia). */
    private static final Pattern LINHA_PROXIMA_MES_ANO_CURTO = Pattern.compile(
        "^(\\d{2})/(\\d{2})\\s+(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s*$",
        Pattern.CASE_INSENSITIVE
    );
    /** Ex.: {@code jul/26 1.234,56} ou {@code JUL 2026 1.234,56}. */
    private static final Pattern LINHA_PROXIMA_MES_ABREV = Pattern.compile(
        "(?i)^(jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)[/.\\s-]*(\\d{2,4})\\s+(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s*$"
    );
    private static final Pattern LINHA_DATA_DESCRICAO = Pattern.compile(
        "^(\\d{2})/(\\d{2})(?:/(\\d{2,4}))?\\s+(.+)$"
    );
    private static final Pattern LINHA_PARCELA_VALOR = Pattern.compile(
        "^(?:\\s*)(\\d{1,2})/(\\d{1,2})\\s+(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s*$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LINHA_VALOR_PARCELA = Pattern.compile(
        "^(?:\\s*)(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s+(\\d{1,2})/(\\d{1,2})\\s*$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LINHA_SO_VALOR = Pattern.compile(
        "^(?:\\s*)(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s*$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LINHA_SO_DATA_VENCIMENTO = Pattern.compile(
        "^(\\d{2})/(\\d{2})(?:/(\\d{2,4}))?\\s*$"
    );
    /** Ordem: marcadores mais específicos primeiro (não usar o índice mais cedo no PDF). */
    private static final String[] MARCADORES_INICIO_PROXIMAS = {
        "compras parceladas - proximas faturas",
        "compras parceladas - próximas faturas",
        "compras parceladas proximas faturas",
        "compras parceladas proximas",
        "demonstrativo de compras parceladas e proximas faturas",
        "demonstrativo de compras parceladas",
        "compras parceladas e proximas",
        "resumo das proximas faturas",
        "valores das proximas faturas",
        "parcelas a vencer",
        "lancamentos futuros",
        "lançamentos futuros",
        "total das proximas faturas",
        "valores projetados para as proximas faturas",
        "proximas faturas",
        "próximas faturas",
        "proxima fatura",
        "próxima fatura"
    };
    private static final String[] MARCADORES_FIM_PROXIMAS = {
        "limite de credito",
        "limite de crédito",
        "simulacao de parcelamento",
        "simulação de parcelamento",
        "pontos itau",
        "pontos itaú",
        "opcoes de pagamento",
        "opções de pagamento"
    };
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

    /**
     * Secção «Compras parceladas - próximas faturas» do PDF Itaú (totais por vencimento futuro).
     */
    public static boolean possuiSecaoProximasFaturas(String textoPdf) {
        return localizarInicioProximasFaturas(textoPdf) >= 0;
    }

    public static String recortarTrechoProximasFaturas(String textoPdf) {
        if (textoPdf == null || textoPdf.isBlank()) {
            return "";
        }
        String norm = textoPdf.replace('\r', '\n');
        int inicio = localizarInicioProximasFaturas(norm);
        if (inicio < 0) {
            return "";
        }
        int fim = norm.length();
        String restante = norm.substring(inicio);
        for (String marcador : MARCADORES_FIM_PROXIMAS) {
            int idx = indexOfIgnoreCase(restante, marcador);
            if (idx > 20) {
                fim = Math.min(fim, inicio + idx);
            }
        }
        return norm.substring(inicio, Math.min(fim, norm.length()));
    }

    public static List<ProjecaoFaturaMesDTO> extrairProximasFaturas(String textoPdf, int anoReferencia) {
        String trecho = recortarTrechoProximasFaturas(textoPdf);
        if (trecho.isBlank()) {
            return List.of();
        }
        List<ProjecaoFaturaMesDTO> proj = extrairProjecoesDoTrecho(trecho, anoReferencia);
        if (!proj.isEmpty()) {
            return proj;
        }
        return extrairProjecoesAposTotalFatura(textoPdf, anoReferencia);
    }

    public static List<ProjecaoFaturaMesDTO> extrairProjecoesDoTrecho(String trecho, int anoReferencia) {
        if (trecho == null || trecho.isBlank()) {
            return List.of();
        }
        Map<String, ProjecaoFaturaMesDTO> porMes = new LinkedHashMap<>();
        for (String linha : trecho.split("\n")) {
            registrarProjecaoDaLinha(linha, anoReferencia, porMes);
        }
        extrairProjecoesMultilinha(trecho, anoReferencia, porMes);
        if (!porMes.isEmpty()) {
            log.info("Itaú próximas faturas: {} mês(es) projetado(s).", porMes.size());
        }
        return new ArrayList<>(porMes.values());
    }

    private static void registrarProjecaoDaLinha(String linha, int anoReferencia, Map<String, ProjecaoFaturaMesDTO> porMes) {
        String t = linha != null ? linha.trim() : "";
        if (t.isBlank() || t.length() > 80 || deveIgnorarDescricao(t)) {
            return;
        }
        Matcher abrev = LINHA_PROXIMA_MES_ABREV.matcher(t);
        if (abrev.matches()) {
            Integer mes = mesDeAbreviacao(abrev.group(1));
            if (mes != null) {
                registrarProjecao(porMes, "01", String.format("%02d", mes), abrev.group(2), abrev.group(3), anoReferencia);
            }
            return;
        }
        Matcher mesAnoCurto = LINHA_PROXIMA_MES_ANO_CURTO.matcher(t);
        if (mesAnoCurto.matches()) {
            int mes = Integer.parseInt(mesAnoCurto.group(1));
            int anoRaw = Integer.parseInt(mesAnoCurto.group(2));
            if (mes >= 1 && mes <= 12 && anoRaw >= 20 && anoRaw <= 99) {
                registrarProjecao(
                    porMes, "01", String.format("%02d", mes), String.valueOf(anoRaw), mesAnoCurto.group(3), anoReferencia
                );
            }
            return;
        }
        Matcher mesAno = LINHA_PROXIMA_FATURA_MES_ANO.matcher(t);
        if (mesAno.matches()) {
            registrarProjecao(porMes, "01", mesAno.group(1), mesAno.group(2), mesAno.group(3), anoReferencia);
            return;
        }
        Matcher valorAntes = LINHA_PROXIMA_FATURA_VALOR_ANTES.matcher(t);
        if (valorAntes.matches()) {
            registrarProjecao(
                porMes, valorAntes.group(2), valorAntes.group(3), valorAntes.group(4), valorAntes.group(1), anoReferencia
            );
            return;
        }
        Matcher proxima = LINHA_PROXIMA_FATURA.matcher(t);
        if (proxima.matches()) {
            String anoRaw = proxima.group(3);
            int mes = Integer.parseInt(proxima.group(2));
            if (anoRaw == null || anoRaw.isBlank()) {
                int dia = Integer.parseInt(proxima.group(1));
                if (mes > 12 && dia >= 1 && dia <= 12) {
                    registrarProjecao(porMes, "01", String.format("%02d", dia), proxima.group(2), proxima.group(4), anoReferencia);
                    return;
                }
            }
            registrarProjecao(porMes, proxima.group(1), proxima.group(2), anoRaw, proxima.group(4), anoReferencia);
        }
    }

    private static Integer mesDeAbreviacao(String abrev) {
        if (abrev == null) {
            return null;
        }
        return switch (abrev.toLowerCase(Locale.ROOT)) {
            case "jan" -> 1;
            case "fev" -> 2;
            case "mar" -> 3;
            case "abr" -> 4;
            case "mai" -> 5;
            case "jun" -> 6;
            case "jul" -> 7;
            case "ago" -> 8;
            case "set" -> 9;
            case "out" -> 10;
            case "nov" -> 11;
            case "dez" -> 12;
            default -> null;
        };
    }

    private static void extrairProjecoesMultilinha(String trecho, int anoReferencia, Map<String, ProjecaoFaturaMesDTO> porMes) {
        String[] linhas = trecho.split("\n");
        for (int i = 0; i < linhas.length; i++) {
            String linha = linhas[i].trim();
            Matcher data = LINHA_SO_DATA_VENCIMENTO.matcher(linha);
            if (!data.matches() || i + 1 >= linhas.length) {
                continue;
            }
            String prox = linhas[i + 1].trim();
            Matcher valor = LINHA_SO_VALOR.matcher(prox);
            if (!valor.matches()) {
                continue;
            }
            registrarProjecao(porMes, data.group(1), data.group(2), data.group(3), valor.group(1), anoReferencia);
            i++;
        }
    }

    private static List<ProjecaoFaturaMesDTO> extrairProjecoesAposTotalFatura(String textoPdf, int anoReferencia) {
        String norm = textoPdf.replace('\r', '\n');
        int inicio = indexOfIgnoreCase(norm, "total desta fatura");
        if (inicio < 0) {
            inicio = indexOfIgnoreCase(norm, "total para pagamento");
        }
        if (inicio < 0) {
            return List.of();
        }
        int fim = norm.length();
        String restante = norm.substring(inicio);
        for (String marcador : MARCADORES_FIM_PROXIMAS) {
            int idx = indexOfIgnoreCase(restante, marcador);
            if (idx > 40) {
                fim = Math.min(fim, inicio + idx);
            }
        }
        Map<String, ProjecaoFaturaMesDTO> porMes = new LinkedHashMap<>();
        extrairProjecoesLinhasCurtas(norm.substring(inicio, fim), anoReferencia, porMes);
        if (!porMes.isEmpty()) {
            log.info("Itaú próximas faturas (heurística pós-total): {} mês(es).", porMes.size());
        }
        return new ArrayList<>(porMes.values());
    }

    private static void extrairProjecoesLinhasCurtas(
        String trecho,
        int anoReferencia,
        Map<String, ProjecaoFaturaMesDTO> porMes
    ) {
        for (String linha : trecho.split("\n")) {
            registrarProjecaoDaLinha(linha, anoReferencia, porMes);
        }
    }

    private static int localizarInicioProximasFaturas(String norm) {
        for (String marcador : MARCADORES_INICIO_PROXIMAS) {
            int idx = indexOfIgnoreCase(norm, marcador);
            if (idx >= 0) {
                return idx;
            }
        }
        String collapsed = norm.replace('\n', ' ');
        for (String marcador : MARCADORES_INICIO_PROXIMAS) {
            int idx = indexOfIgnoreCase(collapsed, marcador);
            if (idx >= 0) {
                return localizarIndiceAproximadoNoOriginal(norm, marcador);
            }
        }
        String[] linhas = norm.split("\n");
        for (int i = 0; i < linhas.length - 1; i++) {
            String duo = semAcentos(linhas[i] + " " + linhas[i + 1]).toLowerCase(Locale.ROOT);
            for (String marcador : MARCADORES_INICIO_PROXIMAS) {
                String m = semAcentos(marcador).toLowerCase(Locale.ROOT);
                if (duo.contains(m)) {
                    return norm.indexOf(linhas[i]);
                }
            }
        }
        return -1;
    }

    private static int localizarIndiceAproximadoNoOriginal(String norm, String marcador) {
        String primeiro = marcador.split("\\s+")[0];
        int idx = indexOfIgnoreCase(norm, primeiro);
        return idx >= 0 ? idx : 0;
    }

    private static void registrarProjecao(
        Map<String, ProjecaoFaturaMesDTO> porMes,
        String dia,
        String mes,
        String anoRaw,
        String valorRaw,
        int anoReferencia
    ) {
        LocalDate venc;
        try {
            venc = parseDataItau(dia, mes, anoRaw, anoReferencia);
        } catch (Exception e) {
            return;
        }
        BigDecimal valor = parseMoney(valorRaw);
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String chave = YearMonth.from(venc).toString();
        porMes.putIfAbsent(chave, new ProjecaoFaturaMesDTO(venc.toString(), valor));
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
            aplicarParcelaLinhaSeguinte(trecho, m.end(), item);
            if (!jaExiste(out, item)) {
                out.add(item);
            }
        }
        extrairLinhasParcelaAposValor(trecho, anoReferencia, out);
        extrairLinhasMultilinha(trecho, anoReferencia, out);
    }

    private static void extrairLinhasParcelaAposValor(String trecho, int anoReferencia, List<ImportacaoFaturaItemDTO> out) {
        Matcher m = LINHA_LANCAMENTO_PARCELA_APOS_VALOR.matcher(trecho);
        while (m.find()) {
            String descricao = limparDescricao(m.group(4));
            if (descricao.isBlank() || deveIgnorarDescricao(descricao)) {
                continue;
            }
            BigDecimal valor = parseMoney(m.group(5));
            if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            ImportacaoFaturaItemDTO item = new ImportacaoFaturaItemDTO();
            item.setData(parseDataItau(m.group(1), m.group(2), m.group(3), anoReferencia));
            item.setDescricao(descricao);
            item.setValor(valor);
            aplicarParcelaNoItem(item, m.group(6), m.group(7));
            if (!jaExiste(out, item)) {
                out.add(item);
            }
        }
    }

    /**
     * PDF Itaú costuma quebrar em duas linhas: {@code DD/MM descrição} e {@code NN/NN valor}.
     */
    private static void extrairLinhasMultilinha(String trecho, int anoReferencia, List<ImportacaoFaturaItemDTO> out) {
        String[] linhas = trecho.split("\n");
        for (int i = 0; i < linhas.length; i++) {
            String linha = linhas[i].trim();
            if (linha.isBlank() || LINHA_LANCAMENTO.matcher(linha).find()) {
                continue;
            }
            Matcher dataDesc = LINHA_DATA_DESCRICAO.matcher(linha);
            if (!dataDesc.matches()) {
                continue;
            }
            String descricao = limparDescricao(dataDesc.group(4));
            if (descricao.isBlank() || deveIgnorarDescricao(descricao)) {
                continue;
            }
            if (linha.matches(".*(?:R\\$\\s*)?\\d{1,3}(?:\\.\\d{3})*,\\d{2}\\s*$")) {
                continue;
            }
            if (i + 1 >= linhas.length) {
                continue;
            }
            String prox = linhas[i + 1].trim();
            Matcher parcelaValor = LINHA_PARCELA_VALOR.matcher(prox);
            if (parcelaValor.matches()) {
                ImportacaoFaturaItemDTO item = montarItemMultilinha(
                    dataDesc.group(1), dataDesc.group(2), dataDesc.group(3),
                    descricao, parcelaValor.group(3), anoReferencia);
                aplicarParcelaNoItem(item, parcelaValor.group(1), parcelaValor.group(2));
                if (!jaExiste(out, item)) {
                    out.add(item);
                }
                i++;
                continue;
            }
            Matcher valorParcela = LINHA_VALOR_PARCELA.matcher(prox);
            if (valorParcela.matches()) {
                ImportacaoFaturaItemDTO item = montarItemMultilinha(
                    dataDesc.group(1), dataDesc.group(2), dataDesc.group(3),
                    descricao, valorParcela.group(1), anoReferencia);
                aplicarParcelaNoItem(item, valorParcela.group(2), valorParcela.group(3));
                if (!jaExiste(out, item)) {
                    out.add(item);
                }
                i++;
                continue;
            }
            Matcher soValor = LINHA_SO_VALOR.matcher(prox);
            if (soValor.matches()) {
                ImportacaoFaturaItemDTO item = montarItemMultilinha(
                    dataDesc.group(1), dataDesc.group(2), dataDesc.group(3),
                    descricao, soValor.group(1), anoReferencia);
                aplicarParcelaNaDescricao(item, descricao);
                if (!jaExiste(out, item)) {
                    out.add(item);
                }
                i++;
            }
        }
    }

    private static ImportacaoFaturaItemDTO montarItemMultilinha(
        String dia,
        String mes,
        String anoRaw,
        String descricao,
        String valorRaw,
        int anoReferencia
    ) {
        ImportacaoFaturaItemDTO item = new ImportacaoFaturaItemDTO();
        item.setData(parseDataItau(dia, mes, anoRaw, anoReferencia));
        item.setDescricao(descricao);
        item.setValor(parseMoney(valorRaw));
        return item;
    }

    private static void aplicarParcelaLinhaSeguinte(String trecho, int posAposMatch, ImportacaoFaturaItemDTO item) {
        if (item.getParcelaAtual() != null && item.getTotalParcelas() != null && item.getTotalParcelas() > 1) {
            return;
        }
        int fimLinha = trecho.indexOf('\n', posAposMatch);
        if (fimLinha < 0) {
            fimLinha = Math.min(trecho.length(), posAposMatch + 120);
        }
        String proxima = trecho.substring(posAposMatch, fimLinha).trim();
        Matcher parcelaValor = LINHA_PARCELA_VALOR.matcher(proxima);
        if (parcelaValor.matches()) {
            aplicarParcelaNoItem(item, parcelaValor.group(1), parcelaValor.group(2));
            return;
        }
        Matcher valorParcela = LINHA_VALOR_PARCELA.matcher(proxima);
        if (valorParcela.matches()) {
            aplicarParcelaNoItem(item, valorParcela.group(2), valorParcela.group(3));
            return;
        }
        aplicarParcelaNaDescricao(item, proxima);
    }

    private static void aplicarParcelaNoItem(ImportacaoFaturaItemDTO item, String atualRaw, String totalRaw) {
        try {
            int atual = Integer.parseInt(atualRaw);
            int total = Integer.parseInt(totalRaw);
            if (atual >= 1 && total > 1 && atual <= total) {
                item.setParcelaAtual(atual);
                item.setTotalParcelas(total);
            }
        } catch (NumberFormatException ignored) {
            // parcela opcional
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
        int proximas = indexOfIgnoreCase(restante, "compras parceladas");
        if (proximas < 0) {
            proximas = indexOfIgnoreCase(restante, "proximas faturas");
        }
        if (proximas > 40) {
            fim = Math.min(fim, inicio + proximas);
        }
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
        } else if (m > 12 && d >= 1 && d <= 12) {
            ano = m < 100 ? 2000 + m : m;
            m = d;
            d = 1;
        }
        return LocalDate.of(ano, m, Math.min(d, YearMonth.of(ano, m).lengthOfMonth()));
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
        return semAcentos(texto).toLowerCase(Locale.ROOT)
            .indexOf(semAcentos(needle).toLowerCase(Locale.ROOT));
    }

    private static String semAcentos(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
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
        int antesAtual = item.getParcelaAtual() != null ? item.getParcelaAtual() : 0;
        int antesTotal = item.getTotalParcelas() != null ? item.getTotalParcelas() : 0;
        aplicarParcelaNoItem(item, atualRaw, totalRaw);
        return (item.getParcelaAtual() != null && item.getParcelaAtual() != antesAtual)
            || (item.getTotalParcelas() != null && item.getTotalParcelas() != antesTotal);
    }
}
