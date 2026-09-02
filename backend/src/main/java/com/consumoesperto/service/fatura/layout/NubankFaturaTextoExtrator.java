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
        "(?m)(\\d{2})\\s+(ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ|JAN|FEV|MAR)(?=\\s|[^a-z]|$)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MES_COLADO = Pattern.compile(
        "(?i)(\\d{2})\\s+(ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ|JAN|FEV|MAR)(?=[A-Za-z])"
    );
    private static final Pattern VALOR_RS = Pattern.compile(
        "(?:−|-)?R\\$\\s*([\\d.]+,\\d{2})",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TOTAL_COMPRAS = Pattern.compile(
        "Total de compras.*?R\\$\\s*([\\d.]+,\\d{2})",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL
    );
    private static final Pattern TOTAL_A_PAGAR_RESUMO = Pattern.compile(
        "(?is)RESUMO DA FATURA ATUAL.*?Total a pagar\\s*R\\$\\s*([\\d.]+,\\d{2})"
    );
    private static final Pattern TOTAL_FATURA_CAPA = Pattern.compile(
        "(?i)fatura\\s+(?:de\\s+\\w+\\s+)?(?:no valor de|em)\\s*R\\$\\s*([\\d.]+,\\d{2})"
    );
    private static final Pattern MES_COLADO_RS = Pattern.compile(
        "(?i)(ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ|JAN|FEV|MAR)R\\$"
    );
    private static final Pattern INICIO_LANCAMENTO_REAL = Pattern.compile(
        "(?m)(\\d{2})\\s+(ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ|JAN|FEV|MAR)\\s*\\R"
            + "(?:(?!\\d{2}\\s+(?:ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ|JAN|FEV|MAR)\\b).{0,220}\\R){0,4}"
            + ".*(?:Parcela|[•●*]{4}|xxxx)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL
    );
    private static final Pattern PARCELA = Pattern.compile(
        "(?i)(?:parc(?:ela)?\\.?\\s*)?(\\d{1,2})\\s*/\\s*(\\d{1,2})"
    );
    private static final Pattern PARCELAS_DE_VALOR = Pattern.compile(
        "(?i)(\\d{1,2})\\s+parcelas?\\s+de\\s+R\\$\\s*([\\d.]+,\\d{2})"
    );
    private static final Pattern TOTAL_A_PAGAR = Pattern.compile(
        "(?i)total a pagar\\s*:?\\s*R\\$\\s*([\\d.]+,\\d{2})"
    );
    private static final Pattern MASCARA_CARTAO = Pattern.compile(
        "(?:[•●*\\.]{4}|xxxx)\\s*\\d{4}|(?m)^\\d{4}\\s*$",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    /** Rodapé de paginação do Nubank («5 de 7»), sempre em linha própria. */
    private static final Pattern RODAPE_PAGINA = Pattern.compile(
        "(?m)^\\s*\\d{1,2}\\s+de\\s+\\d{1,2}\\s*$"
    );

    private NubankFaturaTextoExtrator() {
    }

    public static Optional<BigDecimal> extrairTotalCompras(String textoPdf) {
        if (textoPdf == null || textoPdf.isBlank()) {
            return Optional.empty();
        }
        Matcher m = TOTAL_COMPRAS.matcher(normalizarTextoNubank(textoPdf));
        if (!m.find()) {
            return Optional.empty();
        }
        return Optional.of(parseMoney(m.group(1)));
    }

    /** Total a pagar no resumo/capa (pode incluir abatimentos; distinto do total de compras). */
    public static Optional<BigDecimal> extrairTotalAPagar(String textoPdf) {
        if (textoPdf == null || textoPdf.isBlank()) {
            return Optional.empty();
        }
        String texto = normalizarTextoNubank(textoPdf);
        Matcher resumo = TOTAL_A_PAGAR_RESUMO.matcher(texto);
        if (resumo.find()) {
            return Optional.of(parseMoney(resumo.group(1)));
        }
        Matcher capa = TOTAL_FATURA_CAPA.matcher(texto);
        if (capa.find()) {
            return Optional.of(parseMoney(capa.group(1)));
        }
        return Optional.empty();
    }

    /** Referência de conciliação: total de compras; fallback para total a pagar. */
    public static Optional<BigDecimal> extrairTotalFatura(String textoPdf) {
        return extrairTotalCompras(textoPdf).or(() -> extrairTotalAPagar(textoPdf));
    }

    public static void finalizarLista(
        List<ImportacaoFaturaItemDTO> itens,
        String textoPdf,
        BigDecimal totalFatura
    ) {
        if (itens == null || itens.isEmpty()) {
            return;
        }
        podarEspurios(itens);
        if (totalFatura != null && totalFatura.compareTo(BigDecimal.ZERO) > 0) {
            ajustarPixParceladoParaTotalCompras(itens, totalFatura);
            conciliarResidualPequeno(itens, totalFatura);
        }
    }

    /**
     * Pix parcelado em andamento (ex.: 1/2) pode vir com «Total a pagar» integral; ajusta o residual
     * para bater com o Total de compras do PDF quando a diferença é pequena e localizada.
     */
    static void ajustarPixParceladoParaTotalCompras(List<ImportacaoFaturaItemDTO> itens, BigDecimal totalCompras) {
        if (itens == null || itens.isEmpty() || totalCompras == null) {
            return;
        }
        BigDecimal soma = somaValores(itens);
        BigDecimal diff = soma.subtract(totalCompras).setScale(2, RoundingMode.HALF_UP);
        if (diff.abs().compareTo(new BigDecimal("0.01")) <= 0) {
            return;
        }
        if (diff.abs().compareTo(new BigDecimal("320.00")) > 0) {
            return;
        }
        for (ImportacaoFaturaItemDTO item : itens) {
            if (!parecePixParceladoEmAndamento(item)) {
                continue;
            }
            if (item.getValor() == null) {
                continue;
            }
            Integer totalParcelas = item.getTotalParcelas();
            if (totalParcelas != null && totalParcelas > 1
                && item.getValor().compareTo(new BigDecimal("350.00")) > 0) {
                BigDecimal parcela = item.getValor()
                    .divide(BigDecimal.valueOf(totalParcelas), 2, RoundingMode.HALF_UP);
                log.info(
                    "Nubank: Pix parcelado '{}' usa parcela {} (total {} / {} parcelas), não {}.",
                    item.getDescricao(),
                    parcela,
                    item.getValor(),
                    totalParcelas,
                    item.getValor()
                );
                item.setValor(parcela);
                ajustarPixParceladoParaTotalCompras(itens, totalCompras);
                return;
            }
            if (diff.abs().compareTo(new BigDecimal("10.00")) > 0) {
                continue;
            }
            BigDecimal novo = item.getValor().subtract(diff).setScale(2, RoundingMode.HALF_UP);
            if (novo.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            log.info(
                "Nubank: Pix parcelado «{}» ajustado de {} para {} (concilia total compras {}).",
                item.getDescricao(),
                item.getValor(),
                novo,
                totalCompras
            );
            item.setValor(novo);
            return;
        }
    }

    private static boolean parecePixParceladoEmAndamento(ImportacaoFaturaItemDTO item) {
        if (item == null || item.getDescricao() == null) {
            return false;
        }
        String n = FaturaPdfLayoutSupport.norm(item.getDescricao());
        if (!FaturaPdfLayoutSupport.contem(n, "pix boleto")) {
            return false;
        }
        Integer atual = item.getParcelaAtual();
        Integer total = item.getTotalParcelas();
        return atual != null && total != null && total > 1;
    }


    /** Residual pequeno após parcelas Pix: ajusta a 1ª parcela quando o texto usa valor mensal ligeiramente diferente. */
    static void conciliarResidualPequeno(List<ImportacaoFaturaItemDTO> itens, BigDecimal totalCompras) {
        if (itens == null || itens.isEmpty() || totalCompras == null) {
            return;
        }
        BigDecimal diff = somaValores(itens).subtract(totalCompras).setScale(2, RoundingMode.HALF_UP);
        if (diff.abs().compareTo(new BigDecimal("0.01")) <= 0
            || diff.abs().compareTo(new BigDecimal("10.00")) > 0) {
            return;
        }
        for (ImportacaoFaturaItemDTO item : itens) {
            if (item.getValor() == null || item.getParcelaAtual() == null || item.getTotalParcelas() == null) {
                continue;
            }
            if (item.getTotalParcelas() <= 1 || item.getParcelaAtual() != 1) {
                continue;
            }
            BigDecimal novo = item.getValor().subtract(diff).setScale(2, RoundingMode.HALF_UP);
            if (novo.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            log.info(
                "Nubank: residual {} conciliado em «{}»: {} -> {} (total compras {}).",
                diff,
                item.getDescricao(),
                item.getValor(),
                novo,
                totalCompras
            );
            item.setValor(novo);
            return;
        }
    }

    private static BigDecimal somaValores(List<ImportacaoFaturaItemDTO> itens) {
        return itens.stream()
            .map(i -> i.getValor() != null ? i.getValor() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    static void podarEspurios(List<ImportacaoFaturaItemDTO> itens) {
        if (itens == null || itens.isEmpty()) {
            return;
        }
        int antes = itens.size();
        itens.removeIf(NubankFaturaTextoExtrator::pareceEspurio);
        if (itens.size() < antes) {
            log.info("Nubank poda: {} lançamento(s) espúrio(s) removido(s).", antes - itens.size());
        }
    }

    private static boolean pareceEspurio(ImportacaoFaturaItemDTO item) {
        if (item == null || item.getDescricao() == null) {
            return false;
        }
        String n = FaturaPdfLayoutSupport.norm(item.getDescricao());
        return n.contains(" de 7 ")
            || n.contains("emissao e envio")
            || n.contains("fatura 03 ago")
            || n.contains("alternativas de pagamento")
            || n.contains("parcelar em ")
            || n.contains("valor de entrada")
            || n.contains("juros totais")
            || n.contains("custo efetivo total");
    }

    public static void complementar(List<ImportacaoFaturaItemDTO> destino, String textoPdf, int anoReferencia) {
        if (destino == null || textoPdf == null || textoPdf.isBlank()) {
            return;
        }
        List<ImportacaoFaturaItemDTO> doTexto = extrairLancamentos(textoPdf, anoReferencia);
        injetarPixAusentes(doTexto, extrairPixFinanciados(textoPdf, anoReferencia));
        if (doTexto.size() >= 12) {
            mesclarPreferindoTexto(destino, doTexto);
            return;
        }
        int inseridos = 0;
        for (ImportacaoFaturaItemDTO candidato : doTexto) {
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

    static void mesclarPreferindoTexto(List<ImportacaoFaturaItemDTO> destino, List<ImportacaoFaturaItemDTO> doTexto) {
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
            "Nubank texto: mescla com {} lançamento(s) do PDF (+{} só na IA).",
            doTexto.size(),
            extrasIa
        );
    }

    public static List<ImportacaoFaturaItemDTO> extrairLancamentos(String textoPdf, int anoReferencia) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        String textoNorm = normalizarTextoNubank(textoPdf);
        String trecho = recortarTrechoLancamentos(textoNorm);
        Matcher m = BLOCO_DATA.matcher(trecho);
        List<int[]> blocos = new ArrayList<>();
        while (m.find()) {
            blocos.add(new int[] { m.start(), m.end() });
        }
        for (int i = 0; i < blocos.size(); i++) {
            int start = blocos.get(i)[0];
            int end = i + 1 < blocos.size() ? blocos.get(i + 1)[0] : trecho.length();
            String bloco = trecho.substring(start, end).trim();
            if (pareceSimulacaoOuResumoFatura(bloco)) {
                continue;
            }
            parseBloco(bloco, anoReferencia).ifPresent(item -> {
                if (!jaExiste(out, item)) {
                    out.add(item);
                }
            });
        }
        injetarPixAusentes(out, extrairPixFinanciados(textoNorm, anoReferencia));
        return out;
    }

    static String recortarTrechoLancamentos(String textoNorm) {
        int inicio = encontrarInicioTransacoes(textoNorm);
        int fim = encontrarFimTransacoes(textoNorm, inicio);
        if (inicio >= fim) {
            return textoNorm;
        }
        String trecho = textoNorm.substring(inicio, fim);
        return cortarRepeticaoDeTransacoes(trecho);
    }

    /**
     * PDFs Nubank de várias páginas repetem o mesmo bloco de lançamentos; corta na segunda ocorrência.
     */
    static String cortarRepeticaoDeTransacoes(String trecho) {
        if (trecho == null || trecho.length() < 160) {
            return trecho;
        }
        Matcher inicio = INICIO_LANCAMENTO_REAL.matcher(trecho);
        if (!inicio.find()) {
            return trecho;
        }
        String merchant = extrairMerchantDoBloco(inicio.group(0));
        if (merchant.length() < 8) {
            return trecho;
        }
        int first = indexOfIgnoreCase(trecho, merchant);
        if (first < 0) {
            return trecho;
        }
        int second = indexOfIgnoreCase(trecho, merchant, first + merchant.length());
        if (second > first + 180) {
            log.info("Nubank: bloco de transações repetido em «{}» — cortando {} chars.", merchant, trecho.length() - second);
            return trecho.substring(0, second);
        }
        return trecho;
    }

    private static String extrairMerchantDoBloco(String bloco) {
        String[] lines = bloco.split("\\R");
        for (String line : lines) {
            String t = line.trim();
            if (t.isBlank()) {
                continue;
            }
            if (BLOCO_DATA.matcher(t).lookingAt()) {
                continue;
            }
            if (t.contains("••••") || t.contains("****") || t.matches("(?i).*(R\\$|total a pagar).*")) {
                continue;
            }
            return t.length() > 48 ? t.substring(0, 48) : t;
        }
        return "";
    }

    private static int indexOfIgnoreCase(String texto, String needle, int from) {
        if (texto == null || needle == null) {
            return -1;
        }
        return texto.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT), from);
    }

    private static int encontrarInicioTransacoes(String texto) {
        int trans = indexOfIgnoreCase(texto, "TRANSAÇÕES DE");
        if (trans >= 0) {
            return trans;
        }
        trans = indexOfIgnoreCase(texto, "TRANSAÇÕES");
        if (trans >= 0) {
            return trans;
        }
        Matcher m = INICIO_LANCAMENTO_REAL.matcher(texto);
        if (m.find()) {
            return m.start();
        }
        return 0;
    }

    private static int encontrarFimTransacoes(String texto, int inicio) {
        int fim = texto.length();
        for (String marcador : List.of(
            "RESUMO DA FATURA ATUAL",
            "PRÓXIMAS FATURAS",
            "PROXIMAS FATURAS",
            "Encargos e Custo Efetivo Total"
        )) {
            int idx = indexOfIgnoreCase(texto, marcador);
            if (idx > inicio) {
                fim = Math.min(fim, idx);
            }
        }
        return fim;
    }

    /**
     * Pix/boletos financiados na seção «Pagamentos e Financiamentos».
     * Varredura global porque o OpenPDF costuma colar a data ao estabelecimento (08 MAIPREFEITURA).
     */
    public static List<ImportacaoFaturaItemDTO> extrairPixFinanciados(String textoPdf, int anoReferencia) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        if (textoPdf == null || textoPdf.isBlank()) {
            return out;
        }
        String texto = normalizarTextoNubank(textoPdf);
        int secao = indexOfIgnoreCase(texto, "Pagamentos e Financiamentos");
        if (secao < 0) {
            return out;
        }
        int fimSecao = encontrarFimTransacoes(texto, secao);
        String trecho = texto.substring(secao, fimSecao);
        Matcher tm = TOTAL_A_PAGAR.matcher(trecho);
        while (tm.find()) {
            int inicio = Math.max(0, tm.start() - 140);
            int fimContexto = Math.min(trecho.length(), tm.end() + 140);
            String contexto = trecho.substring(inicio, fimContexto);
            if (pareceSimulacaoOuResumoFatura(contexto)) {
                continue;
            }
            LocalDate data = ultimaDataNoTrecho(contexto, anoReferencia).orElse(null);
            if (data == null) {
                continue;
            }
            BigDecimal valor = parseMoney(tm.group(1));
            String descricao = extrairDescricaoPix(contexto);
            if (descricao.isBlank() || valor.compareTo(BigDecimal.ZERO) <= 0
                || pareceDescricaoPixEspuria(descricao)) {
                continue;
            }
            ImportacaoFaturaItemDTO item = new ImportacaoFaturaItemDTO();
            item.setData(data);
            item.setDescricao(descricao + " (Pix/boleto no crédito)");
            aplicarParcelaNaDescricao(item);
            if (pareceTotalFinanciadoPixParcelado(contexto, valor, item)
                && extrairValorCobradoPix(contexto, valor).isEmpty()) {
                continue;
            }
            item.setValor(resolverValorPixFinanciado(contexto, valor, item));
            if (!jaExiste(out, item)) {
                out.add(item);
            }
        }
        return out;
    }


    /**
     * Pix/boleto parcelado: «Total a pagar» é o financiamento inteiro; na fatura entra só a parcela do mês.
     */
    static BigDecimal resolverValorPixFinanciado(String contexto, BigDecimal totalAPagar, ImportacaoFaturaItemDTO item) {
        Integer atual = item.getParcelaAtual();
        Integer total = item.getTotalParcelas();
        if (atual == null || total == null || total <= 1) {
            return totalAPagar;
        }
        // «divididos em N parcelas de R$ X» declara a cobrança do mês — inclusive na última parcela,
        // onde o «Total a pagar» do bloco continua sendo o financiamento inteiro.
        Matcher parcelas = PARCELAS_DE_VALOR.matcher(contexto);
        if (parcelas.find()) {
            try {
                int nParcelas = Integer.parseInt(parcelas.group(1));
                BigDecimal valorParcela = parseMoney(parcelas.group(2));
                if (nParcelas == total && valorParcela.compareTo(BigDecimal.ZERO) > 0
                    && parcelaCompativelComTotal(totalAPagar, nParcelas, valorParcela)) {
                    log.info(
                        "Nubank Pix parcelado {}/{}: usa parcela R$ {} (texto), não total R$ {}.",
                        atual,
                        total,
                        valorParcela,
                        totalAPagar
                    );
                    return valorParcela;
                }
            } catch (Exception ignored) {
            }
        }
        if (atual < total) {
            BigDecimal dividido = totalAPagar.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            log.info(
                "Nubank Pix parcelado {}/{}: usa R$ {} (total/{}) em vez de R$ {}.",
                atual,
                total,
                dividido,
                total,
                totalAPagar
            );
            return dividido;
        }
        if (parcelaExplicitaNaDescricao(item)) {
            Optional<BigDecimal> cobrado = extrairValorCobradoPix(contexto, totalAPagar);
            if (cobrado.isPresent() && cobrado.get().compareTo(totalAPagar) != 0) {
                log.info(
                    "Nubank Pix parcelado {}/{}: usa cobrança R$ {} (texto), não total financiado R$ {}.",
                    atual,
                    total,
                    cobrado.get(),
                    totalAPagar
                );
                return cobrado.get();
            }
        }
        return totalAPagar;
    }


    private static boolean parcelaCompativelComTotal(BigDecimal totalAPagar, int nParcelas, BigDecimal valorParcela) {
        if (totalAPagar == null || valorParcela == null || nParcelas <= 0) {
            return false;
        }
        BigDecimal saldo = valorParcela.multiply(BigDecimal.valueOf(nParcelas));
        return totalAPagar.subtract(saldo).abs().compareTo(new BigDecimal("3.00")) <= 0
            || totalAPagar.subtract(valorParcela).abs().compareTo(new BigDecimal("3.00")) <= 0;
    }

    private static void aplicarParcelaNoTexto(String texto, ImportacaoFaturaItemDTO item) {
        Matcher m = PARCELA.matcher(texto);
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
        }
    }

    private static boolean parcelaExplicitaNaDescricao(ImportacaoFaturaItemDTO item) {
        return item.getDescricao() != null && PARCELA.matcher(item.getDescricao()).find();
    }

    private static Optional<BigDecimal> extrairValorCobradoPix(String contexto, BigDecimal totalAPagar) {
        Matcher vm = VALOR_RS.matcher(contexto);
        List<BigDecimal> valores = new ArrayList<>();
        while (vm.find()) {
            String bruto = vm.group(0);
            if (bruto.startsWith("-") || bruto.startsWith("\u2212")) {
                continue;
            }
            BigDecimal v = parseMoney(vm.group(1));
            if (v.compareTo(BigDecimal.ZERO) > 0) {
                valores.add(v);
            }
        }
        if (valores.isEmpty()) {
            return Optional.empty();
        }
        if (totalAPagar != null) {
            for (BigDecimal v : valores) {
                if (v.subtract(totalAPagar).abs().compareTo(new BigDecimal("0.01")) <= 0) {
                    return Optional.of(v);
                }
            }
            return valores.stream()
                .min((a, b) -> a.subtract(totalAPagar).abs().compareTo(b.subtract(totalAPagar).abs()));
        }
        return Optional.of(valores.get(valores.size() - 1));
    }

    private static boolean pareceTotalFinanciadoPixParcelado(
        String contexto,
        BigDecimal totalAPagar,
        ImportacaoFaturaItemDTO item
    ) {
        Integer atual = item.getParcelaAtual();
        Integer total = item.getTotalParcelas();
        if (atual == null || total == null || total <= 1 || !atual.equals(total)) {
            return false;
        }
        Matcher parcelas = PARCELAS_DE_VALOR.matcher(contexto);
        if (!parcelas.find()) {
            return false;
        }
        try {
            int nParcelas = Integer.parseInt(parcelas.group(1));
            BigDecimal valorParcela = parseMoney(parcelas.group(2));
            if (nParcelas != total || valorParcela.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }
            BigDecimal saldoIntegral = valorParcela.multiply(BigDecimal.valueOf(total));
            return totalAPagar.subtract(saldoIntegral).abs().compareTo(new BigDecimal("2.00")) <= 0;
        } catch (Exception ignored) {
            return false;
        }
    }


    static String normalizarTextoNubank(String textoPdf) {
        if (textoPdf == null) {
            return "";
        }
        String out = textoPdf.replace('\u00a0', ' ');
        Matcher m = MES_COLADO.matcher(out);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(out, last, m.start());
            sb.append(m.group(1)).append(' ').append(m.group(2)).append('\n');
            last = m.end();
        }
        sb.append(out.substring(last));
        out = sb.toString();
        out = MES_COLADO_RS.matcher(out).replaceAll("$1 R$");
        out = out.replaceAll("(?i)(Pagamentos e Financiamentos)\\s*-R\\$", "$1\n-R\\$");
        return out;
    }

    /**
     * O bloco do último lançamento de cada página arrasta o rodapé de paginação, o cabeçalho da
     * página seguinte e o título da seção de pagamentos. Descartar o bloco por causa desse ruído
     * apagava o lançamento; aqui o ruído é cortado e o lançamento é preservado.
     */
    private static String cortarRuidoAposLancamento(String bloco) {
        int corte = bloco.length();
        Matcher rodape = RODAPE_PAGINA.matcher(bloco);
        if (rodape.find()) {
            corte = Math.min(corte, rodape.start());
        }
        int secaoPagamentos = indexOfIgnoreCase(bloco, "Pagamentos e Financiamentos");
        if (secaoPagamentos > 0) {
            corte = Math.min(corte, secaoPagamentos);
        }
        return corte < bloco.length() ? bloco.substring(0, corte).trim() : bloco;
    }

    private static Optional<ImportacaoFaturaItemDTO> parseBloco(String blocoBruto, int anoReferencia) {
        Matcher dm = BLOCO_DATA.matcher(blocoBruto);
        if (!dm.find()) {
            return Optional.empty();
        }
        String bloco = cortarRuidoAposLancamento(blocoBruto);
        String norm = FaturaPdfLayoutSupport.norm(bloco);
        if (norm.contains("pagamentos e financiamentos") && !norm.contains("total a pagar")) {
            return Optional.empty();
        }
        if (parecePagamentoOuCredito(bloco) && !norm.contains("total a pagar")) {
            return Optional.empty();
        }

        LocalDate data = parseDataNubank(dm.group(1), dm.group(2), anoReferencia);
        boolean pix = norm.contains("total a pagar");
        boolean cartao = temMascaraCartao(bloco) || norm.contains("parcela");
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
        aplicarParcelaNaDescricao(item);
        if (item.getParcelaAtual() == null) {
            aplicarParcelaNoTexto(bloco, item);
        }
        if (pix) {
            item.setValor(resolverValorPixFinanciado(bloco, valor, item));
        } else {
            item.setValor(valor);
        }
        return Optional.of(item);
    }

    private static String extrairDescricao(String bloco, boolean pix) {
        String limpo = bloco.replaceAll("(?m)^\\d{2}\\s+(?:ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ|JAN|FEV|MAR)\\s*", "");
        limpo = limpo.replaceAll("(?:[•●*\\.]{4}|xxxx)\\s*\\d{4}\\s*", "").trim();
        limpo = limpo.replaceAll("(?m)^\\d{4}\\s*$", "").trim();
        if (pix) {
            int idx = limpo.toLowerCase(Locale.ROOT).indexOf("total a pagar");
            if (idx > 0) {
                limpo = limpo.substring(0, idx).trim();
            }
        }
        limpo = limpo.replaceAll("(?i)R\\$\\s*[\\d.,]+\\s*\\d+\\s*de\\s*\\d+.*$", "");
        limpo = limpo.replaceAll("(?i)\\d+\\s*de\\s*\\d+\\s+BRUCE.*$", "");
        limpo = limpo.replaceAll("(?i)FATURA\\s+\\d{2}\\s+AGO.*$", "");
        limpo = limpo.replaceAll("(?i)EMISS[AÃ]O E ENVIO.*$", "");
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
            String descItem = FaturaPdfLayoutSupport.norm(item.getDescricao());
            if (mesmaParcela(item, candidato) && credorSimilar(descItem, descCand)) {
                return true;
            }
            if (item.getValor() == null || candidato.getValor() == null) {
                continue;
            }
            // Duplicata é o mesmo lançamento vindo de duas fontes (IA e texto): exige dia, valor e
            // descrição compatíveis. Só valor e descrição descartava compras recorrentes em dias
            // diferentes; só dia e valor descartaria compras distintas de mesmo preço no mesmo dia.
            boolean mesmaData = candidato.getData() != null && candidato.getData().equals(item.getData());
            boolean mesmoValor = item.getValor().subtract(candidato.getValor()).abs()
                .compareTo(new BigDecimal("0.04")) <= 0;
            boolean descSimilar = descItem.contains(descCand) || descCand.contains(descItem)
                || descItem.length() > 8 && descCand.length() > 8
                && descItem.substring(0, Math.min(12, descItem.length()))
                    .equals(descCand.substring(0, Math.min(12, descCand.length())));
            if (mesmaData && mesmoValor && descSimilar) {
                return true;
            }
        }
        return false;
    }

    private static boolean mesmaParcela(ImportacaoFaturaItemDTO item, ImportacaoFaturaItemDTO candidato) {
        Integer pa = item.getParcelaAtual();
        Integer pt = item.getTotalParcelas();
        Integer ca = candidato.getParcelaAtual();
        Integer ct = candidato.getTotalParcelas();
        return pa != null && ca != null && pt != null && ct != null
            && pt > 1 && pt.equals(ct) && pa.equals(ca);
    }

    private static boolean credorSimilar(String descItem, String descCand) {
        for (String token : tokensCredor(descItem)) {
            if (token.length() >= 6 && descCand.contains(token)) {
                return true;
            }
        }
        for (String token : tokensCredor(descCand)) {
            if (token.length() >= 6 && descItem.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String[] tokensCredor(String descNorm) {
        if (descNorm == null || descNorm.isBlank()) {
            return new String[0];
        }
        String limpo = descNorm
            .replace("pix boleto no credito", " ")
            .replace("pagamentos e financiamentos", " ")
            .replaceAll("pagamento em \\d{2} [a-z]{3}", " ")
            .replaceAll("parcela \\d+ \\d+", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return limpo.split(" ");
    }

    private static void injetarPixAusentes(List<ImportacaoFaturaItemDTO> destino, List<ImportacaoFaturaItemDTO> pix) {
        int inseridos = 0;
        for (ImportacaoFaturaItemDTO item : pix) {
            if (!jaExiste(destino, item)) {
                destino.add(item);
                inseridos++;
                log.info("Pix Nubank complementado: '{}' = {}", item.getDescricao(), item.getValor());
            }
        }
        if (inseridos > 0) {
            log.info("Nubank: {} Pix/boleto(s) financiado(s) injetado(s) do texto.", inseridos);
        }
    }

    private static boolean parecePagamentoOuCredito(String bloco) {
        String n = FaturaPdfLayoutSupport.norm(bloco);
        return n.contains("estorno")
            || n.contains("pagamento em")
            || n.contains("pagamento recebido")
            || n.contains("saldo restante");
    }

    private static boolean pareceSimulacaoOuResumoFatura(String contexto) {
        String n = FaturaPdfLayoutSupport.norm(contexto);
        return n.contains("resumo da fatura")
            || n.contains("alternativas de pagamento")
            || n.contains("parcelar em")
            || n.contains("valor de entrada")
            || n.contains("juros totais")
            || n.contains("valor da parcela")
            || n.contains("pagamento total da fatura")
            || n.contains("consulte o aplicativo");
    }

    private static Optional<LocalDate> ultimaDataNoTrecho(String trecho, int anoReferencia) {
        Matcher dm = BLOCO_DATA.matcher(trecho);
        String dia = null;
        String mes = null;
        while (dm.find()) {
            dia = dm.group(1);
            mes = dm.group(2);
        }
        if (dia == null || mes == null) {
            return Optional.empty();
        }
        return Optional.of(parseDataNubank(dia, mes, anoReferencia));
    }

    private static String extrairDescricaoPix(String contexto) {
        int idxTotal = indexOfIgnoreCase(contexto, "total a pagar");
        String trecho = idxTotal > 0 ? contexto.substring(0, idxTotal) : contexto;
        Matcher datas = BLOCO_DATA.matcher(trecho);
        int ultimaData = -1;
        while (datas.find()) {
            ultimaData = datas.start();
        }
        String limpo = ultimaData >= 0 ? trecho.substring(ultimaData) : trecho;
        limpo = limpo.replaceAll("(?m)^\\d{2}\\s+(?:ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ|JAN|FEV|MAR)\\s*", "");
        limpo = limpo.replaceAll("(?:[\u2022\u25cf*\\.]{4}|xxxx)\\s*\\d{4}\\s*", "").trim();
        limpo = limpo.replaceAll("(?i)R\\$\\s*[\\d.,]+.*", "").trim();
        limpo = limpo.replaceAll("\\s+", " ");
        if (limpo.length() > 120) {
            limpo = limpo.substring(0, 120).trim();
        }
        return limpo;
    }

    private static boolean pareceDescricaoPixEspuria(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return true;
        }
        String n = FaturaPdfLayoutSupport.norm(descricao);
        if (n.contains("3443") || n.contains("xxxx") || n.contains("pagamentos e financiamentos")) {
            return true;
        }
        long datas = Pattern.compile("\\b\\d{2} [a-z]{3}\\b").matcher(n).results().count();
        return datas > 1;
    }

    private static boolean temMascaraCartao(String bloco) {
        return bloco.contains("••••") || MASCARA_CARTAO.matcher(bloco).find();
    }

    private static int indexOfIgnoreCase(String texto, String needle) {
        if (texto == null || needle == null) {
            return -1;
        }
        return texto.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }
}
