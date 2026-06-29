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
    /** Valor sem prefixo R$ (layout recente do PDF Inter). */
    private static final Pattern LINHA_LANCAMENTO_SEM_RS = Pattern.compile(
        "(\\d{2})/(\\d{2})(?:/(\\d{4}))?\\s+(.{3,}?)\\s+(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s*(?:$|\\r?\\n)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    /** Data, estabelecimento e valor em linhas separadas. */
    private static final Pattern BLOCO_MULTILINHA = Pattern.compile(
        "(\\d{2})/(\\d{2})(?:/(\\d{4}))?\\s*\\r?\\n\\s*([^\\n\\d][^\\n]{2,}?)\\s*\\r?\\n\\s*(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PARCELA_TEXTO = Pattern.compile(
        "(?i)parcela\\s+(\\d{1,2})\\s+de\\s+(\\d{1,2})"
    );
    private static final Pattern PARCELA_PARENTESES = Pattern.compile(
        "\\(\\s*(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*\\)"
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
        "compras realizadas no mês",
        "historico de transacoes",
        "histórico de transações",
        "historico da fatura",
        "histórico da fatura",
        "compras e lancamentos",
        "compras e lançamentos",
        "fatura paga",
        "pagamento efetuado",
        "pagamento recebido",
        "lancamentos desta fatura",
        "lançamentos desta fatura"
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
            BigDecimal lido = parseMoney(valor.group(1));
            if (lido.compareTo(BigDecimal.ZERO) > 0) {
                return Optional.of(lido);
            }
        }
        Matcher total = TOTAL_FATURA_RESUMO.matcher(resumo);
        if (total.find()) {
            BigDecimal lido = parseMoney(total.group(1));
            if (lido.compareTo(BigDecimal.ZERO) > 0) {
                return Optional.of(lido);
            }
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
        try {
            complementarInterno(destino, textoPdf, anoReferencia);
        } catch (Exception e) {
            log.warn("Inter complementar falhou (mantendo lista da IA): {}", e.getMessage(), e);
        }
    }

    private static void complementarInterno(List<ImportacaoFaturaItemDTO> destino, String textoPdf, int anoReferencia) {
        podarEspurios(destino, textoPdf);

        List<ImportacaoFaturaItemDTO> doTexto = extrairLancamentos(textoPdf, anoReferencia);
        Optional<BigDecimal> totalPdf = extrairTotalFatura(textoPdf);
        BigDecimal somaTexto = somaValores(doTexto);
        BigDecimal somaDestino = somaValores(destino);
        boolean totalPositivo = totalPdf.isPresent() && totalPdf.get().compareTo(BigDecimal.ZERO) > 0;

        boolean textoMaisProximoDoTotal = totalPositivo
            && !doTexto.isEmpty()
            && distanciaAoTotal(somaTexto, totalPdf.get())
                .compareTo(distanciaAoTotal(somaDestino, totalPdf.get())) < 0;
        boolean textoBateComTotal = totalPositivo
            && !doTexto.isEmpty()
            && distanciaAoTotal(somaTexto, totalPdf.get()).compareTo(new BigDecimal("1.00")) <= 0;
        boolean destinoGenerico = FaturaPdfLayoutSupport.pareceListaGenericaIa(destino);
        boolean destinoTemGenericos = destino.stream()
            .anyMatch(i -> FaturaPdfLayoutSupport.pareceDescricaoGenericaIa(i.getDescricao()));
        boolean substituirPorTexto = !doTexto.isEmpty() && (
            destino.isEmpty()
            || destinoGenerico
            || destinoTemGenericos
            || !totalPositivo
            || textoMaisProximoDoTotal
            || textoBateComTotal
        );

        if (substituirPorTexto) {
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
    }

    /**
     * Segunda passagem com o total conhecido — colapsa parcelas futuras repetidas (mesma data/valor)
     * e remove linhas que só existem após «Próximas faturas» / «Opções de pagamento».
     */
    public static void finalizarListaInter(
        List<ImportacaoFaturaItemDTO> itens,
        String textoPdf,
        BigDecimal totalFatura,
        int anoReferencia
    ) {
        if (itens == null || textoPdf == null || textoPdf.isBlank()) {
            return;
        }
        try {
            finalizarListaInterInterno(itens, textoPdf, totalFatura, anoReferencia);
        } catch (Exception e) {
            log.warn("Inter finalizar falhou (mantendo lista atual): {}", e.getMessage(), e);
        }
    }

    private static void finalizarListaInterInterno(
        List<ImportacaoFaturaItemDTO> itens,
        String textoPdf,
        BigDecimal totalFatura,
        int anoReferencia
    ) {
        BigDecimal total = totalFatura != null && totalFatura.compareTo(BigDecimal.ZERO) > 0
            ? totalFatura
            : extrairTotalFatura(textoPdf).orElse(null);

        podarEspurios(itens, textoPdf);

        List<ImportacaoFaturaItemDTO> doTexto = extrairLancamentos(textoPdf, anoReferencia);
        itens.removeIf(i -> FaturaPdfLayoutSupport.pareceDescricaoGenericaIa(i.getDescricao()));
        if ((total == null || total.compareTo(BigDecimal.ZERO) <= 0) && !doTexto.isEmpty()) {
            if (itens.isEmpty() || FaturaPdfLayoutSupport.pareceListaGenericaIa(itens) || doTexto.size() >= itens.size()) {
                itens.clear();
                itens.addAll(doTexto);
                podarEspurios(itens, textoPdf);
                log.info("Inter finalizar: fatura paga/total zerado — {} lançamento(s) do texto.", itens.size());
            }
            total = somaValores(itens);
        } else if (total != null && !doTexto.isEmpty()) {
            BigDecimal somaAtual = somaValores(itens);
            BigDecimal somaTexto = somaValores(doTexto);
            if (distanciaAoTotal(somaTexto, total).compareTo(distanciaAoTotal(somaAtual, total)) <= 0) {
                itens.clear();
                itens.addAll(doTexto);
                podarEspurios(itens, textoPdf);
                log.info("Inter finalizar: lista do texto ({} itens, soma {}).", itens.size(), somaValores(itens));
            }
        }

        if (total != null && somaValores(itens).compareTo(total) > 0) {
            removerValoresCitadosEmOpcoesPagamento(itens, textoPdf);
            colapsarMesmaDataValorParcelaMenor(itens);
            podarEspurios(itens, textoPdf);
            ajustarSomaAoTotal(itens, total);
        }

        podarEspurios(itens, textoPdf);
        Optional<LocalDate> vencimento = extrairDataVencimento(textoPdf);
        removerLinhasResumoFatura(itens, total, vencimento);
    }

    /**
     * Quando a soma ainda excede o total da fatura, remove o lançamento cuja exclusão
     * aproxima mais a soma do total (ex.: simulação de juros R$ 53,21 ou Apple duplicado em «Próximas faturas»).
     */
    private static void ajustarSomaAoTotal(List<ImportacaoFaturaItemDTO> itens, BigDecimal total) {
        if (itens == null || itens.isEmpty() || total == null) {
            return;
        }
        BigDecimal tolerancia = new BigDecimal("0.01");
        BigDecimal soma = somaValores(itens);
        while (soma.compareTo(total.add(tolerancia)) > 0 && itens.size() > 1) {
            ImportacaoFaturaItemDTO remover = null;
            BigDecimal melhorDist = distanciaAoTotal(soma, total);
            for (ImportacaoFaturaItemDTO candidato : itens) {
                if (candidato.getValor() == null) {
                    continue;
                }
                BigDecimal novaSoma = soma.subtract(candidato.getValor());
                BigDecimal dist = distanciaAoTotal(novaSoma, total);
                if (dist.compareTo(melhorDist) >= 0) {
                    continue;
                }
                boolean bateExato = dist.compareTo(tolerancia) <= 0;
                boolean naoFicaMuitoAbaixo = novaSoma.compareTo(total.subtract(tolerancia)) >= 0;
                if (bateExato || naoFicaMuitoAbaixo) {
                    remover = candidato;
                    melhorDist = dist;
                }
            }
            if (remover == null) {
                break;
            }
            itens.remove(remover);
            soma = somaValores(itens);
            log.info(
                "Inter ajuste total: removido «{}» ({}) — soma agora {} (alvo {}).",
                remover.getDescricao(),
                remover.getValor(),
                soma,
                total
            );
            if (distanciaAoTotal(soma, total).compareTo(tolerancia) <= 0) {
                break;
            }
        }
    }

    /** Remove simulações, encargos e duplicatas de «Próximas faturas» mesmo quando a IA as incluiu. */
    public static void podarEspurios(List<ImportacaoFaturaItemDTO> itens, String textoPdf) {
        if (itens == null || itens.isEmpty() || textoPdf == null || textoPdf.isBlank()) {
            return;
        }
        int antes = itens.size();
        itens.removeIf(i -> deveIgnorarDescricao(i.getDescricao()) || pareceLinhaEncargoInter(i.getDescricao()));
        itens.removeIf(i -> pareceLinhaSimulacaoTaxaInter(i.getDescricao(), i.getValor()));
        itens.removeIf(i -> FaturaPdfLayoutSupport.pareceDescricaoGenericaIa(i.getDescricao()));
        removerItensSoEmSecoesPosteriores(itens, textoPdf);
        colapsarMesmaDataValorParcelaMenor(itens);
        deduplicarParcelasFuturasMesmoPlano(itens);
        int removidos = antes - itens.size();
        if (removidos > 0) {
            log.info("Inter poda: {} lançamento(s) espúrio(s) removido(s).", removidos);
        }
    }

    public static List<ImportacaoFaturaItemDTO> extrairLancamentos(String textoPdf, int anoReferencia) {
        if (textoPdf == null || textoPdf.isBlank()) {
            return List.of();
        }
        try {
            return extrairLancamentosInterno(textoPdf, anoReferencia);
        } catch (Exception e) {
            log.warn("Inter extrairLancamentos falhou: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private static List<ImportacaoFaturaItemDTO> extrairLancamentosInterno(String textoPdf, int anoReferencia) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        String norm = textoPdf.replace('\r', '\n');
        String trecho = recortarTrechoTransacoes(norm);
        Optional<LocalDate> dataCorte = extrairDataCorte(textoPdf);
        Optional<LocalDate> vencimento = extrairDataVencimento(textoPdf);
        Optional<BigDecimal> totalPdf = extrairTotalFatura(textoPdf);
        int ano = vencimento.map(LocalDate::getYear).orElse(anoReferencia > 0 ? anoReferencia : YearMonth.now().getYear());

        extrairLinhasDoTrecho(trecho, ano, vencimento, dataCorte, out, LINHA_LANCAMENTO, true, totalPdf);
        if (out.isEmpty() || somenteLinhasResumo(out, totalPdf.orElse(null), vencimento)) {
            List<ImportacaoFaturaItemDTO> alt = new ArrayList<>();
            extrairLinhasDoTrecho(trecho, ano, vencimento, dataCorte, alt, LINHA_LANCAMENTO_SEM_RS, false, totalPdf);
            extrairBlocosMultilinha(trecho, ano, vencimento, dataCorte, alt);
            if (!alt.isEmpty() && (out.isEmpty() || somaValores(alt).compareTo(somaValores(out)) > 0)) {
                out.clear();
                out.addAll(alt);
            }
        }
        if (out.isEmpty()) {
            int fim = localizarIndiceProximas(norm);
            if (fim < 0) {
                fim = norm.length();
            }
            String fallback = substringSeguro(norm, 0, fim);
            extrairLinhasDoTrecho(fallback, ano, vencimento, dataCorte, out, LINHA_LANCAMENTO, true, totalPdf);
            extrairLinhasDoTrecho(fallback, ano, vencimento, dataCorte, out, LINHA_LANCAMENTO_SEM_RS, false, totalPdf);
            extrairBlocosMultilinha(fallback, ano, vencimento, dataCorte, out);
        }
        if (out.isEmpty()) {
            extrairLancamentosModoAmplo(norm, ano, vencimento, dataCorte, out, totalPdf);
        }
        if (out.isEmpty() && FaturaPdfLayoutSupport.pareceFaturaPagaNoTexto(textoPdf)) {
            extrairLancamentosFaturaPaga(norm, ano, vencimento, dataCorte, out, totalPdf);
        }
        removerLinhasResumoFatura(out, totalPdf.orElse(null), vencimento);
        podarEspurios(out, textoPdf);
        return out;
    }

    /** Fallback quando o recorte «Detalhamento» falha — usa trecho entre corte e próximas faturas. */
    private static void extrairLancamentosModoAmplo(
        String norm,
        int ano,
        Optional<LocalDate> vencimento,
        Optional<LocalDate> dataCorte,
        List<ImportacaoFaturaItemDTO> out,
        Optional<BigDecimal> totalPdf
    ) {
        int fim = localizarIndiceProximas(norm);
        if (fim < 0) {
            fim = norm.length();
        }
        int inicio = resolverInicioSecaoDetalhamento(norm);
        if (inicio >= fim) {
            return;
        }
        String trecho = substringSeguro(norm, inicio, fim);
        extrairLinhasDoTrecho(trecho, ano, vencimento, dataCorte, out, LINHA_LANCAMENTO, true, totalPdf);
        extrairLinhasDoTrecho(trecho, ano, vencimento, dataCorte, out, LINHA_LANCAMENTO_SEM_RS, false, totalPdf);
        extrairBlocosMultilinha(trecho, ano, vencimento, dataCorte, out);
    }

    /** Fatura paga: lançamentos costumam vir após «Valor da fatura R$ 0,00» / «Fatura paga». */
    private static void extrairLancamentosFaturaPaga(
        String norm,
        int ano,
        Optional<LocalDate> vencimento,
        Optional<LocalDate> dataCorte,
        List<ImportacaoFaturaItemDTO> out,
        Optional<BigDecimal> totalPdf
    ) {
        int inicio = -1;
        for (String marcador : new String[] {
            "fatura paga", "pagamento efetuado", "pagamento recebido",
            "valor da fatura", "detalhamento da fatura", "data de corte"
        }) {
            int idx = indexOfIgnoreCase(norm, marcador);
            if (idx < 0) {
                continue;
            }
            int nl = norm.indexOf('\n', idx);
            inicio = Math.max(inicio, nl >= 0 ? nl + 1 : idx);
        }
        if (inicio < 0) {
            inicio = 0;
        }
        int fim = localizarIndiceProximas(norm);
        if (fim < 0 || fim <= inicio) {
            fim = norm.length();
        }
        String trecho = substringSeguro(norm, inicio, fim);
        extrairLinhasDoTrecho(trecho, ano, vencimento, dataCorte, out, LINHA_LANCAMENTO, true, totalPdf);
        extrairLinhasDoTrecho(trecho, ano, vencimento, dataCorte, out, LINHA_LANCAMENTO_SEM_RS, false, totalPdf);
        extrairBlocosMultilinha(trecho, ano, vencimento, dataCorte, out);
        if (!out.isEmpty()) {
            log.info("Inter fatura paga: {} lançamento(s) extraído(s) do trecho pós-resumo.", out.size());
        }
    }

    static boolean pareceListaGenericaIa(List<ImportacaoFaturaItemDTO> itens) {
        return FaturaPdfLayoutSupport.pareceListaGenericaIa(itens);
    }

    private static void extrairBlocosMultilinha(
        String trecho,
        int ano,
        Optional<LocalDate> vencimento,
        Optional<LocalDate> dataCorte,
        List<ImportacaoFaturaItemDTO> out
    ) {
        Matcher m = BLOCO_MULTILINHA.matcher(trecho);
        while (m.find()) {
            adicionarLancamentoExtraido(out, m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), ano, vencimento, dataCorte);
        }
    }

    private static void extrairLinhasDoTrecho(
        String trecho,
        int ano,
        Optional<LocalDate> vencimento,
        Optional<LocalDate> dataCorte,
        List<ImportacaoFaturaItemDTO> out,
        Pattern pattern,
        boolean parcelaLinhaSeguinte,
        Optional<BigDecimal> totalFatura
    ) {
        Matcher m = pattern.matcher(trecho);
        while (m.find()) {
            String descricao = limparDescricao(m.group(4));
            if (descricao.isBlank() || deveIgnorarDescricao(descricao) || descricaoInvalida(descricao)) {
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
            if (pareceDataVencimentoComTotal(data, valor, vencimento, totalFatura)) {
                continue;
            }
            ImportacaoFaturaItemDTO item = new ImportacaoFaturaItemDTO();
            item.setData(data);
            item.setDescricao(descricao);
            item.setValor(valor);
            aplicarParcelaDaDescricao(item, descricao);
            if (parcelaLinhaSeguinte) {
                aplicarParcelaLinhaSeguinte(trecho, m.end(), item);
            }
            if (!jaExiste(out, item)) {
                out.add(item);
            }
        }
    }

    private static void adicionarLancamentoExtraido(
        List<ImportacaoFaturaItemDTO> out,
        String dia,
        String mes,
        String anoTxt,
        String descricaoBruta,
        String valorBruto,
        int ano,
        Optional<LocalDate> vencimento,
        Optional<LocalDate> dataCorte
    ) {
        String descricao = limparDescricao(descricaoBruta);
        if (descricao.isBlank() || deveIgnorarDescricao(descricao) || descricaoInvalida(descricao)) {
            return;
        }
        BigDecimal valor = parseMoney(valorBruto);
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        LocalDate data = parseDataInter(dia, mes, anoTxt, ano, vencimento);
        if (dataCorte.isPresent() && data != null && data.isAfter(dataCorte.get())) {
            return;
        }
        ImportacaoFaturaItemDTO item = new ImportacaoFaturaItemDTO();
        item.setData(data);
        item.setDescricao(descricao);
        item.setValor(valor);
        aplicarParcelaDaDescricao(item, descricao);
        if (!jaExiste(out, item)) {
            out.add(item);
        }
    }

    private static boolean descricaoInvalida(String descricao) {
        String n = FaturaPdfLayoutSupport.norm(descricao);
        return n.isBlank() || n.equals("r$") || n.length() < 3
            || FaturaPdfLayoutSupport.pareceDescricaoGenericaIa(descricao);
    }

    static boolean descricaoInvalidaPublica(String descricao) {
        return descricaoInvalida(descricao);
    }

    private static boolean pareceDataVencimentoComTotal(
        LocalDate data,
        BigDecimal valor,
        Optional<LocalDate> vencimento,
        Optional<BigDecimal> totalFatura
    ) {
        if (data == null || valor == null || vencimento.isEmpty() || totalFatura.isEmpty()) {
            return false;
        }
        LocalDate ven = vencimento.get();
        boolean mesmaDataVenc = data.getDayOfMonth() == ven.getDayOfMonth()
            && data.getMonthValue() == ven.getMonthValue();
        return mesmaDataVenc && valor.subtract(totalFatura.get()).abs().compareTo(new BigDecimal("0.02")) <= 0;
    }

    private static boolean somenteLinhasResumo(
        List<ImportacaoFaturaItemDTO> itens,
        BigDecimal totalFatura,
        Optional<LocalDate> vencimento
    ) {
        if (itens == null || itens.isEmpty()) {
            return true;
        }
        for (ImportacaoFaturaItemDTO item : itens) {
            if (!pareceLinhaResumoTotal(item, totalFatura, vencimento)) {
                return false;
            }
        }
        return true;
    }

    static boolean pareceLinhaResumoTotal(
        ImportacaoFaturaItemDTO item,
        BigDecimal totalFatura,
        Optional<LocalDate> vencimento
    ) {
        if (item == null || item.getValor() == null) {
            return true;
        }
        if (descricaoInvalida(item.getDescricao()) || deveIgnorarDescricao(item.getDescricao())) {
            return true;
        }
        if (totalFatura != null
            && item.getValor().subtract(totalFatura).abs().compareTo(new BigDecimal("0.02")) <= 0
            && pareceDataVencimentoComTotal(item.getData(), item.getValor(), vencimento, Optional.of(totalFatura))) {
            return true;
        }
        return false;
    }

    static void removerLinhasResumoFatura(
        List<ImportacaoFaturaItemDTO> itens,
        BigDecimal totalFatura,
        Optional<LocalDate> vencimento
    ) {
        if (itens == null || itens.isEmpty()) {
            return;
        }
        itens.removeIf(i -> pareceLinhaResumoTotal(i, totalFatura, vencimento));
    }

    private static String recortarTrechoTransacoes(String norm) {
        int fim = localizarIndiceProximas(norm);
        if (fim < 0) {
            fim = localizarFimTrecho(norm, 0);
        }
        int inicio = localizarInicioPrimeiroLancamento(norm, fim);
        if (inicio >= fim) {
            fim = norm.length();
            inicio = localizarInicioPrimeiroLancamento(norm, fim);
        }
        return substringSeguro(norm, inicio, fim);
    }

    private static int localizarIndiceProximas(String norm) {
        int minPos = resolverInicioSecaoDetalhamento(norm);
        int idx = -1;
        for (String marcador : new String[] {
            "proximas faturas", "próximas faturas", "proxima fatura", "próxima fatura",
            "opcoes de pagamento", "opções de pagamento"
        }) {
            int found = indexOfIgnoreCase(norm, marcador);
            if (found >= minPos && (idx < 0 || found < idx)) {
                idx = found;
            }
        }
        return idx;
    }

    private static int resolverInicioSecaoDetalhamento(String norm) {
        int inicioMarcador = -1;
        for (String marcador : MARCADORES_INICIO_TRANSACOES) {
            int idx = indexOfIgnoreCase(norm, marcador);
            if (idx >= 0 && (inicioMarcador < 0 || idx < inicioMarcador)) {
                inicioMarcador = idx;
            }
        }
        if (inicioMarcador >= 0) {
            return inicioMarcador;
        }
        int corte = indexOfIgnoreCase(norm, "data de corte");
        if (corte >= 0) {
            int nl = norm.indexOf('\n', corte);
            return nl >= 0 ? nl + 1 : corte;
        }
        return 0;
    }

    private static String substringSeguro(String texto, int ini, int fim) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        int inicio = Math.max(0, Math.min(ini, texto.length()));
        int fimSeguro = Math.max(inicio, Math.min(fim, texto.length()));
        if (inicio >= fimSeguro) {
            return "";
        }
        return texto.substring(inicio, fimSeguro);
    }

    private static int localizarInicioPrimeiroLancamento(String norm, int fim) {
        int searchFrom = 0;
        int valorFatura = indexOfIgnoreCase(norm, "valor da fatura");
        if (valorFatura >= 0) {
            int nl = norm.indexOf('\n', valorFatura);
            searchFrom = nl >= 0 ? nl + 1 : valorFatura;
        }
        int corte = indexOfIgnoreCase(norm, "data de corte");
        if (corte >= 0) {
            int nl = norm.indexOf('\n', corte);
            searchFrom = Math.max(searchFrom, nl >= 0 ? nl + 1 : corte);
        }
        int inicioMarcador = -1;
        for (String marcador : MARCADORES_INICIO_TRANSACOES) {
            int idx = indexOfIgnoreCase(norm, marcador);
            if (idx >= 0 && idx < fim) {
                inicioMarcador = inicioMarcador < 0 ? idx : Math.min(inicioMarcador, idx);
            }
        }
        if (inicioMarcador >= 0) {
            searchFrom = Math.max(searchFrom, inicioMarcador);
        }
        Pattern primeiraData = Pattern.compile("(?m)(\\d{2})/(\\d{2})(?:/\\d{4})?\\s+\\S");
        Matcher m = primeiraData.matcher(norm);
        while (m.find()) {
            if (m.start() < searchFrom || m.start() >= fim) {
                continue;
            }
            String linha = linhaEm(norm, m.start());
            String desc = limparDescricao(linha.replaceFirst("^\\d{2}/\\d{2}(?:/\\d{4})?\\s+", ""));
            if (!deveIgnorarDescricao(desc) && !pareceLinhaEncargoInter(desc)) {
                return m.start();
            }
        }
        return Math.max(searchFrom, 0);
    }

    private static String linhaEm(String norm, int pos) {
        int ini = norm.lastIndexOf('\n', pos);
        int fim = norm.indexOf('\n', pos);
        if (ini < 0) {
            ini = 0;
        } else {
            ini++;
        }
        if (fim < 0) {
            fim = norm.length();
        }
        return norm.substring(ini, fim);
    }

    /** Mesma data+valor com parcelas diferentes (ex.: PARC 4/6, 5/6, 6/6) → fica só a menor parcela. */
    private static void colapsarMesmaDataValorParcelaMenor(List<ImportacaoFaturaItemDTO> itens) {
        Map<String, ImportacaoFaturaItemDTO> menorPorDataValor = new HashMap<>();
        Map<String, Integer> contagem = new HashMap<>();
        for (ImportacaoFaturaItemDTO item : itens) {
            if (item.getData() == null || item.getValor() == null) {
                continue;
            }
            String chave = chaveDataValor(item);
            contagem.merge(chave, 1, Integer::sum);
            ImportacaoFaturaItemDTO atual = menorPorDataValor.get(chave);
            if (atual == null || parcelaPreferida(item, atual)) {
                menorPorDataValor.put(chave, item);
            }
        }
        Iterator<ImportacaoFaturaItemDTO> it = itens.iterator();
        while (it.hasNext()) {
            ImportacaoFaturaItemDTO item = it.next();
            if (item.getData() == null || item.getValor() == null) {
                continue;
            }
            String chave = chaveDataValor(item);
            if (contagem.getOrDefault(chave, 0) < 2) {
                continue;
            }
            if (item != menorPorDataValor.get(chave)) {
                it.remove();
            }
        }
    }

    private static String chaveDataValor(ImportacaoFaturaItemDTO item) {
        return item.getData() + "|" + item.getValor().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Menor parcelaAtual vence; sem parcela perde para quem tem parcela definida. */
    private static boolean parcelaPreferida(ImportacaoFaturaItemDTO candidato, ImportacaoFaturaItemDTO atual) {
        if (candidato.getParcelaAtual() == null) {
            return false;
        }
        if (atual.getParcelaAtual() == null) {
            return true;
        }
        return candidato.getParcelaAtual() < atual.getParcelaAtual();
    }

    private static void removerItensSoEmSecoesPosteriores(List<ImportacaoFaturaItemDTO> itens, String textoPdf) {
        itens.removeIf(item -> apareceSomenteAposMarcadores(textoPdf, item,
            "proximas faturas", "próximas faturas", "proxima fatura", "próxima fatura",
            "opcoes de pagamento", "opções de pagamento",
            "encargos em caso de pagamento"));
    }

    private static boolean apareceSomenteAposMarcadores(
        String textoPdf,
        ImportacaoFaturaItemDTO item,
        String... marcadores
    ) {
        if (item.getValor() == null) {
            return false;
        }
        String norm = textoPdf.replace('\r', '\n').toLowerCase(Locale.ROOT);
        int minPos = resolverInicioSecaoDetalhamento(norm);
        int limite = norm.length();
        for (String marcador : marcadores) {
            int idx = indexOfIgnoreCase(norm, marcador);
            if (idx >= minPos) {
                limite = Math.min(limite, idx);
            }
        }
        if (limite <= 0 || limite >= norm.length()) {
            return false;
        }
        String valorBr = formatarValorBr(item.getValor());
        boolean achouAntes = contemDataValorNoTrecho(norm, 0, limite, item, valorBr);
        boolean achouDepois = contemDataValorNoTrecho(norm, limite, norm.length(), item, valorBr);
        return achouDepois && !achouAntes;
    }

    private static boolean contemDataValorNoTrecho(
        String norm,
        int ini,
        int fim,
        ImportacaoFaturaItemDTO item,
        String valorBr
    ) {
        if (item.getData() == null) {
            return false;
        }
        String trecho = substringSeguro(norm, ini, fim);
        if (trecho.isEmpty()) {
            return false;
        }
        String dataCurta = String.format("%02d/%02d",
            item.getData().getDayOfMonth(), item.getData().getMonthValue());
        int idx = trecho.indexOf(dataCurta);
        while (idx >= 0) {
            String janela = trecho.substring(idx, Math.min(idx + 160, trecho.length()));
            if (janela.contains(valorBr)) {
                return true;
            }
            idx = trecho.indexOf(dataCurta, idx + 1);
        }
        return false;
    }

    private static String formatarValorBr(BigDecimal valor) {
        String plain = valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
        if (plain.contains(",")) {
            String[] p = plain.split(",");
            if (p[0].length() >= 4) {
                String milhar = p[0];
                String comPonto = milhar.substring(0, milhar.length() - 3) + "." + milhar.substring(milhar.length() - 3);
                return comPonto + "," + p[1];
            }
        }
        return plain;
    }

    private static void removerValoresCitadosEmOpcoesPagamento(List<ImportacaoFaturaItemDTO> itens, String textoPdf) {
        String norm = textoPdf.replace('\r', '\n');
        int indiceOpcoes = indexOfIgnoreCase(norm, "opcoes de pagamento");
        if (indiceOpcoes < 0) {
            indiceOpcoes = indexOfIgnoreCase(norm, "opções de pagamento");
        }
        if (indiceOpcoes < 0) {
            return;
        }
        final int limiteOpcoes = indiceOpcoes;
        String secao = norm.substring(limiteOpcoes, Math.min(limiteOpcoes + 2_500, norm.length()));
        Matcher vm = Pattern.compile("(\\d{1,3}(?:\\.\\d{3})*,\\d{2})").matcher(secao);
        Set<BigDecimal> valores = new LinkedHashSet<>();
        while (vm.find()) {
            valores.add(parseMoney(vm.group(1)));
        }
        if (valores.isEmpty()) {
            return;
        }
        itens.removeIf(item -> item.getValor() != null
            && valores.stream().anyMatch(v -> v.subtract(item.getValor()).abs().compareTo(new BigDecimal("0.04")) <= 0)
            && !contemDataValorNoTrecho(norm.toLowerCase(Locale.ROOT), 0, limiteOpcoes, item, formatarValorBr(item.getValor())));
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
            fimLinha = trecho.length();
        }
        String proxima = trecho.substring(posAposMatch, fimLinha).trim();
        if (proxima.isBlank() && fimLinha < trecho.length()) {
            int iniProx = fimLinha + 1;
            int fimProx = trecho.indexOf('\n', iniProx);
            if (fimProx < 0) {
                fimProx = Math.min(trecho.length(), iniProx + 80);
            }
            proxima = trecho.substring(iniProx, fimProx).trim();
        }
        aplicarParcelaDaDescricao(item, proxima);
    }

    private static void aplicarParcelaDaDescricao(ImportacaoFaturaItemDTO item, String texto) {
        Matcher pm = PARCELA_TEXTO.matcher(texto);
        if (pm.find()) {
            try {
                item.setParcelaAtual(Integer.parseInt(pm.group(1)));
                item.setTotalParcelas(Integer.parseInt(pm.group(2)));
                return;
            } catch (NumberFormatException ignored) {
                // tenta formato (N/N)
            }
        }
        Matcher pp = PARCELA_PARENTESES.matcher(texto);
        if (pp.find()) {
            try {
                item.setParcelaAtual(Integer.parseInt(pp.group(1)));
                item.setTotalParcelas(Integer.parseInt(pp.group(2)));
            } catch (NumberFormatException ignored) {
                // parcela opcional
            }
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
            || n.matches(".*\\d\\s+\\d+x.*")
            || n.matches(".*ate\\s+\\d+\\s*x.*");
    }

    static boolean pareceLinhaEncargoInter(String descricao) {
        String n = FaturaPdfLayoutSupport.norm(descricao);
        if (n.isBlank()) {
            return false;
        }
        return n.startsWith("total a pagar")
            || n.contains("total a pagar em")
            || n.contains("valor total de juros")
            || n.contains("total de juros e encargos")
            || n.contains("encargos e iof")
            || n.contains("iof do rotativo")
            || n.contains("iof adicional")
            || n.contains("iof diario")
            || n.contains("iof internacional")
            || n.contains("encargos rotativos")
            || n.contains("encargos maximo")
            || n.contains("maximo proximo periodo")
            || n.contains("juros do rotativo")
            || n.contains("juros rotativos")
            || n.contains("juros de mora")
            || n.contains("juros de parcelamento")
            || n.contains("multa por atraso")
            || n.contains("juros e encargos")
            || n.contains("encargos em caso")
            || n.contains("valor total financiado")
            || n.matches(".*\\d+ \\d{2}% am.*")
            || n.contains("% am")
            || n.contains("% a m");
    }

    /** Linhas da tabela de encargos/simulação (ex.: «Juros de mora 1,00% am» R$ 0,01). */
    static boolean pareceLinhaSimulacaoTaxaInter(String descricao, BigDecimal valor) {
        if (pareceLinhaEncargoInter(descricao)) {
            return true;
        }
        String n = FaturaPdfLayoutSupport.norm(descricao);
        if (n.isBlank()) {
            return false;
        }
        if (n.contains("% am") || n.contains("% a m")) {
            return true;
        }
        if (valor != null
            && valor.compareTo(new BigDecimal("1.00")) < 0
            && (n.contains("juros") || n.contains("multa") || n.contains("iof") || n.contains("encargos"))) {
            return true;
        }
        return false;
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
