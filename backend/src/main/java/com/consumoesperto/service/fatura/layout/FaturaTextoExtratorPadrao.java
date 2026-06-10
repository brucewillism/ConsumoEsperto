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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor compartilhado de extração determinística de faturas PDF por banco.
 * Cada emissor define {@link BancoTextoConfig} com marcadores e regras de poda.
 */
@Slf4j
final class FaturaTextoExtratorPadrao {

    private static final Pattern LINHA_LANCAMENTO = Pattern.compile(
        "(\\d{2})/(\\d{2})(?:/(\\d{4}))?\\s+(.+?)\\s+(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PARCELA_TEXTO = Pattern.compile(
        "(?i)parcela\\s+(\\d{1,2})\\s+de\\s+(\\d{1,2})"
    );
    private static final Pattern DATA_VENCIMENTO = Pattern.compile(
        "(?i)data de vencimento:?\\s*(\\d{2})/(\\d{2})/(\\d{4})"
    );
    private static final Pattern DATA_CORTE = Pattern.compile(
        "(?i)data de corte:?\\s*(\\d{2})/(\\d{2})/(\\d{4})"
    );

    private FaturaTextoExtratorPadrao() {
    }

    record BancoTextoConfig(
        String nomeLog,
        String[] marcadoresInicio,
        String[] marcadoresFim,
        Pattern[] padroesTotal,
        String[] tokensIgnorarDescricao,
        Predicate<String> pareceEncargo,
        Predicate<String> ignorarLancamento
    ) {
        BancoTextoConfig {
            marcadoresInicio = marcadoresInicio != null ? marcadoresInicio : new String[0];
            marcadoresFim = marcadoresFim != null ? marcadoresFim : new String[0];
            padroesTotal = padroesTotal != null ? padroesTotal : new Pattern[0];
            tokensIgnorarDescricao = tokensIgnorarDescricao != null ? tokensIgnorarDescricao : new String[0];
            pareceEncargo = pareceEncargo != null ? pareceEncargo : s -> false;
            ignorarLancamento = ignorarLancamento != null ? ignorarLancamento : s -> false;
        }
    }

    static Optional<BigDecimal> extrairTotalFatura(String textoPdf, BancoTextoConfig cfg) {
        if (textoPdf == null || textoPdf.isBlank() || cfg.padroesTotal().length == 0) {
            return Optional.empty();
        }
        String resumo = textoPdf.length() > 5_000 ? textoPdf.substring(0, 5_000) : textoPdf;
        for (Pattern p : cfg.padroesTotal()) {
            Matcher m = p.matcher(resumo);
            if (m.find()) {
                return Optional.of(parseMoney(m.group(1)));
            }
        }
        return Optional.empty();
    }

    static void complementar(
        List<ImportacaoFaturaItemDTO> destino,
        String textoPdf,
        int anoReferencia,
        BancoTextoConfig cfg
    ) {
        if (destino == null || textoPdf == null || textoPdf.isBlank()) {
            return;
        }
        try {
            complementarInterno(destino, textoPdf, anoReferencia, cfg);
        } catch (Exception e) {
            log.warn("{} complementar falhou (mantendo lista da IA): {}", cfg.nomeLog(), e.getMessage(), e);
        }
    }

    private static void complementarInterno(
        List<ImportacaoFaturaItemDTO> destino,
        String textoPdf,
        int anoReferencia,
        BancoTextoConfig cfg
    ) {
        podarEspurios(destino, textoPdf, cfg);
        List<ImportacaoFaturaItemDTO> doTexto = extrairLancamentos(textoPdf, anoReferencia, cfg);
        Optional<BigDecimal> totalPdf = extrairTotalFatura(textoPdf, cfg);
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
            destino.clear();
            destino.addAll(doTexto);
            log.info("{} texto: lista substituída por {} lançamento(s) do PDF.", cfg.nomeLog(), doTexto.size());
        } else if (!doTexto.isEmpty()) {
            int inseridos = 0;
            for (ImportacaoFaturaItemDTO candidato : doTexto) {
                if (!jaExiste(destino, candidato)) {
                    destino.add(candidato);
                    inseridos++;
                }
            }
            if (inseridos > 0) {
                log.info("{} texto: {} lançamento(s) complementar(es).", cfg.nomeLog(), inseridos);
            }
        }
        podarEspurios(destino, textoPdf, cfg);
    }

    static void finalizar(
        List<ImportacaoFaturaItemDTO> itens,
        String textoPdf,
        BigDecimal totalFatura,
        int anoReferencia,
        BancoTextoConfig cfg
    ) {
        if (itens == null || textoPdf == null || textoPdf.isBlank()) {
            return;
        }
        try {
            finalizarInterno(itens, textoPdf, totalFatura, anoReferencia, cfg);
        } catch (Exception e) {
            log.warn("{} finalizar falhou (mantendo lista atual): {}", cfg.nomeLog(), e.getMessage(), e);
        }
    }

    private static void finalizarInterno(
        List<ImportacaoFaturaItemDTO> itens,
        String textoPdf,
        BigDecimal totalFatura,
        int anoReferencia,
        BancoTextoConfig cfg
    ) {
        BigDecimal total = totalFatura != null && totalFatura.compareTo(BigDecimal.ZERO) > 0
            ? totalFatura
            : extrairTotalFatura(textoPdf, cfg).orElse(null);

        podarEspurios(itens, textoPdf, cfg);

        List<ImportacaoFaturaItemDTO> doTexto = extrairLancamentos(textoPdf, anoReferencia, cfg);
        if (total != null && !doTexto.isEmpty()) {
            BigDecimal somaAtual = somaValores(itens);
            BigDecimal somaTexto = somaValores(doTexto);
            if (distanciaAoTotal(somaTexto, total).compareTo(distanciaAoTotal(somaAtual, total)) <= 0) {
                itens.clear();
                itens.addAll(doTexto);
                podarEspurios(itens, textoPdf, cfg);
                log.info("{} finalizar: lista do texto ({} itens).", cfg.nomeLog(), itens.size());
            }
        }

        if (total != null && somaValores(itens).compareTo(total) > 0) {
            colapsarMesmaDataValorParcelaMenor(itens);
            podarEspurios(itens, textoPdf, cfg);
            ajustarSomaAoTotal(itens, total, cfg.nomeLog());
        }
    }

    static List<ImportacaoFaturaItemDTO> extrairLancamentos(
        String textoPdf,
        int anoReferencia,
        BancoTextoConfig cfg
    ) {
        if (textoPdf == null || textoPdf.isBlank()) {
            return List.of();
        }
        try {
            return extrairLancamentosInterno(textoPdf, anoReferencia, cfg);
        } catch (Exception e) {
            log.warn("{} extrairLancamentos falhou: {}", cfg.nomeLog(), e.getMessage(), e);
            return List.of();
        }
    }

    private static List<ImportacaoFaturaItemDTO> extrairLancamentosInterno(
        String textoPdf,
        int anoReferencia,
        BancoTextoConfig cfg
    ) {
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        String norm = textoPdf.replace('\r', '\n');
        String trecho = recortarTrechoTransacoes(norm, cfg);
        Optional<LocalDate> dataCorte = extrairDataCorte(textoPdf);
        Optional<LocalDate> vencimento = extrairDataVencimento(textoPdf);
        int ano = vencimento.map(LocalDate::getYear).orElse(anoReferencia > 0 ? anoReferencia : YearMonth.now().getYear());

        extrairLinhasDoTrecho(trecho, ano, vencimento, dataCorte, out, cfg);
        if (out.isEmpty()) {
            int fim = localizarIndiceFim(norm, cfg, 0);
            if (fim < 0) {
                fim = norm.length();
            }
            extrairLinhasDoTrecho(substringSeguro(norm, 0, fim), ano, vencimento, dataCorte, out, cfg);
        }
        return out;
    }

    static void podarEspurios(List<ImportacaoFaturaItemDTO> itens, String textoPdf, BancoTextoConfig cfg) {
        if (itens == null || itens.isEmpty()) {
            return;
        }
        int antes = itens.size();
        itens.removeIf(i -> deveIgnorar(i.getDescricao(), cfg) || cfg.ignorarLancamento().test(i.getDescricao()));
        removerItensSoEmSecoesPosteriores(itens, textoPdf, cfg);
        colapsarMesmaDataValorParcelaMenor(itens);
        int removidos = antes - itens.size();
        if (removidos > 0) {
            log.info("{} poda: {} lançamento(s) espúrio(s) removido(s).", cfg.nomeLog(), removidos);
        }
    }

    static boolean deveIgnorar(String descricao, BancoTextoConfig cfg) {
        String n = FaturaPdfLayoutSupport.norm(descricao);
        if (n.isBlank()) {
            return true;
        }
        if (cfg.pareceEncargo().test(descricao)) {
            return true;
        }
        if (n.matches(".*\\d\\s*\\+\\s*\\d+x.*") || n.matches(".*ate\\s+\\d+\\s*x.*")) {
            return true;
        }
        for (String token : TOKENS_IGNORAR_COMUNS) {
            if (n.contains(token)) {
                return true;
            }
        }
        for (String token : cfg.tokensIgnorarDescricao()) {
            if (n.contains(FaturaPdfLayoutSupport.norm(token))) {
                return true;
            }
        }
        return false;
    }

    static boolean pareceEncargoComum(String descricao) {
        String n = FaturaPdfLayoutSupport.norm(descricao);
        if (n.isBlank()) {
            return false;
        }
        return n.startsWith("total a pagar")
            || n.contains("valor total de juros")
            || n.contains("total de juros e encargos")
            || n.contains("encargos e iof")
            || n.contains("iof do rotativo")
            || n.contains("juros e encargos")
            || n.contains("encargos em caso")
            || n.contains("valor total financiado")
            || n.contains("tarifas e encargos")
            || n.contains("cet ")
            || n.contains("simulacao de parcelamento");
    }

    static Pattern padraoTotalPadrao() {
        return Pattern.compile(
            "(?i)(?:valor(?:\\s+da)?\\s+fatura|total(?:\\s+da)?\\s+fatura|total\\s+a\\s+pagar|total\\s+para\\s+pagamento)"
                + "[^\\d]{0,100}(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})"
        );
    }

    static String[] fimProximasFaturas() {
        return new String[] {
            "proximas faturas", "próximas faturas", "proxima fatura", "próxima fatura",
            "opcoes de pagamento", "opções de pagamento", "encargos em caso de pagamento"
        };
    }

    private static final String[] TOKENS_IGNORAR_COMUNS = {
        "limite de credito", "limite total", "limite utilizado", "limite disponivel",
        "saque total", "opcoes de pagamento", "parcelar fatura",
        "proximas faturas", "proxima fatura", "pagamento minimo", "resumo da fatura",
        "valor da fatura", "total da fatura", "data de vencimento", "data de corte",
        "saldo restante", "valor financiado", "taxa efetiva", "simulacao", "consumos de",
        "pagamentos e creditos", "pagamentos e créditos"
    };

    private static String recortarTrechoTransacoes(String norm, BancoTextoConfig cfg) {
        int fim = localizarIndiceFim(norm, cfg, resolverInicioSecao(cfg, norm));
        if (fim < 0) {
            fim = norm.length();
        }
        int inicio = localizarInicioPrimeiroLancamento(norm, fim, cfg);
        if (inicio >= fim) {
            fim = norm.length();
            inicio = localizarInicioPrimeiroLancamento(norm, fim, cfg);
        }
        return substringSeguro(norm, inicio, fim);
    }

    private static int resolverInicioSecao(BancoTextoConfig cfg, String norm) {
        int inicio = -1;
        for (String marcador : cfg.marcadoresInicio()) {
            int idx = indexOfIgnoreCase(norm, marcador);
            if (idx >= 0 && (inicio < 0 || idx < inicio)) {
                inicio = idx;
            }
        }
        if (inicio >= 0) {
            return inicio;
        }
        int corte = indexOfIgnoreCase(norm, "data de corte");
        if (corte >= 0) {
            int nl = norm.indexOf('\n', corte);
            return nl >= 0 ? nl + 1 : corte;
        }
        return 0;
    }

    private static int localizarIndiceFim(String norm, BancoTextoConfig cfg, int minPos) {
        int idx = -1;
        for (String marcador : cfg.marcadoresFim()) {
            int found = indexOfIgnoreCase(norm, marcador);
            if (found >= minPos && (idx < 0 || found < idx)) {
                idx = found;
            }
        }
        return idx;
    }

    private static int localizarInicioPrimeiroLancamento(String norm, int fim, BancoTextoConfig cfg) {
        int searchFrom = resolverInicioSecao(cfg, norm);
        int valorFatura = indexOfIgnoreCase(norm, "valor da fatura");
        if (valorFatura >= 0) {
            int nl = norm.indexOf('\n', valorFatura);
            searchFrom = Math.max(searchFrom, nl >= 0 ? nl + 1 : valorFatura);
        }
        Pattern primeiraData = Pattern.compile("(?m)(\\d{2})/(\\d{2})(?:/\\d{4})?\\s+\\S");
        Matcher m = primeiraData.matcher(norm);
        while (m.find()) {
            if (m.start() < searchFrom || m.start() >= fim) {
                continue;
            }
            String linha = linhaEm(norm, m.start());
            String desc = limparDescricao(linha.replaceFirst("^\\d{2}/\\d{2}(?:/\\d{4})?\\s+", ""));
            if (!deveIgnorar(desc, cfg)) {
                return m.start();
            }
        }
        return Math.max(searchFrom, 0);
    }

    private static void extrairLinhasDoTrecho(
        String trecho,
        int ano,
        Optional<LocalDate> vencimento,
        Optional<LocalDate> dataCorte,
        List<ImportacaoFaturaItemDTO> out,
        BancoTextoConfig cfg
    ) {
        Matcher m = LINHA_LANCAMENTO.matcher(trecho);
        while (m.find()) {
            String descricao = limparDescricao(m.group(4));
            if (descricao.isBlank() || deveIgnorar(descricao, cfg)) {
                continue;
            }
            BigDecimal valor = parseMoney(m.group(5));
            if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LocalDate data = parseData(m.group(1), m.group(2), m.group(3), ano, vencimento);
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
    }

    private static void removerItensSoEmSecoesPosteriores(
        List<ImportacaoFaturaItemDTO> itens,
        String textoPdf,
        BancoTextoConfig cfg
    ) {
        itens.removeIf(item -> apareceSomenteAposMarcadores(textoPdf, item, cfg));
    }

    private static boolean apareceSomenteAposMarcadores(
        String textoPdf,
        ImportacaoFaturaItemDTO item,
        BancoTextoConfig cfg
    ) {
        if (item.getValor() == null || item.getData() == null) {
            return false;
        }
        String norm = textoPdf.replace('\r', '\n').toLowerCase(Locale.ROOT);
        int minPos = resolverInicioSecao(cfg, norm);
        int limite = norm.length();
        for (String marcador : cfg.marcadoresFim()) {
            int idx = indexOfIgnoreCase(norm, marcador);
            if (idx >= minPos) {
                limite = Math.min(limite, idx);
            }
        }
        if (limite <= 0 || limite >= norm.length()) {
            return false;
        }
        String valorBr = formatarValorBr(item.getValor());
        String dataCurta = String.format("%02d/%02d",
            item.getData().getDayOfMonth(), item.getData().getMonthValue());
        boolean achouAntes = contemDataValor(norm, 0, limite, dataCurta, valorBr);
        boolean achouDepois = contemDataValor(norm, limite, norm.length(), dataCurta, valorBr);
        return achouDepois && !achouAntes;
    }

    private static boolean contemDataValor(String norm, int ini, int fim, String dataCurta, String valorBr) {
        String trecho = substringSeguro(norm, ini, fim);
        if (trecho.isEmpty()) {
            return false;
        }
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

    private static void colapsarMesmaDataValorParcelaMenor(List<ImportacaoFaturaItemDTO> itens) {
        Map<String, ImportacaoFaturaItemDTO> menorPorChave = new HashMap<>();
        Map<String, Integer> contagem = new HashMap<>();
        for (ImportacaoFaturaItemDTO item : itens) {
            if (item.getData() == null || item.getValor() == null) {
                continue;
            }
            String chave = item.getData() + "|" + item.getValor().setScale(2, RoundingMode.HALF_UP);
            contagem.merge(chave, 1, Integer::sum);
            ImportacaoFaturaItemDTO atual = menorPorChave.get(chave);
            if (atual == null || parcelaMenor(item, atual)) {
                menorPorChave.put(chave, item);
            }
        }
        Iterator<ImportacaoFaturaItemDTO> it = itens.iterator();
        while (it.hasNext()) {
            ImportacaoFaturaItemDTO item = it.next();
            if (item.getData() == null || item.getValor() == null) {
                continue;
            }
            String chave = item.getData() + "|" + item.getValor().setScale(2, RoundingMode.HALF_UP);
            if (contagem.getOrDefault(chave, 0) < 2) {
                continue;
            }
            if (item != menorPorChave.get(chave)) {
                it.remove();
            }
        }
    }

    private static boolean parcelaMenor(ImportacaoFaturaItemDTO a, ImportacaoFaturaItemDTO b) {
        if (a.getParcelaAtual() == null) {
            return false;
        }
        if (b.getParcelaAtual() == null) {
            return true;
        }
        return a.getParcelaAtual() < b.getParcelaAtual();
    }

    private static void ajustarSomaAoTotal(List<ImportacaoFaturaItemDTO> itens, BigDecimal total, String nomeLog) {
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
            log.info("{} ajuste total: removido «{}» ({}) — soma {}.", nomeLog, remover.getDescricao(), remover.getValor(), soma);
            if (distanciaAoTotal(soma, total).compareTo(tolerancia) <= 0) {
                break;
            }
        }
    }

    private static Optional<LocalDate> extrairDataCorte(String textoPdf) {
        Matcher m = DATA_CORTE.matcher(textoPdf);
        if (!m.find()) {
            return Optional.empty();
        }
        return parseDataCompleta(m.group(1), m.group(2), m.group(3));
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
            return Optional.of(LocalDate.of(Integer.parseInt(ano), Integer.parseInt(mes), Integer.parseInt(dia)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static LocalDate parseData(
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

    private static void aplicarParcelaLinhaSeguinte(String trecho, int posAposMatch, ImportacaoFaturaItemDTO item) {
        if (item.getParcelaAtual() != null) {
            return;
        }
        int fimLinha = trecho.indexOf('\n', posAposMatch);
        if (fimLinha < 0) {
            fimLinha = Math.min(trecho.length(), posAposMatch + 80);
        }
        aplicarParcelaDaDescricao(item, trecho.substring(posAposMatch, fimLinha).trim());
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

    private static int indexOfIgnoreCase(String texto, String busca) {
        if (texto == null || busca == null) {
            return -1;
        }
        return FaturaPdfLayoutSupport.norm(texto).indexOf(FaturaPdfLayoutSupport.norm(busca));
    }

    private static String formatarValorBr(BigDecimal valor) {
        String plain = valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
        if (plain.contains(",") && plain.split(",")[0].length() >= 4) {
            String[] p = plain.split(",");
            String milhar = p[0];
            return milhar.substring(0, milhar.length() - 3) + "." + milhar.substring(milhar.length() - 3) + "," + p[1];
        }
        return plain;
    }

    private static BigDecimal parseMoney(String raw) {
        return new BigDecimal(raw.replace(".", "").replace(",", ".").trim())
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal somaValores(List<ImportacaoFaturaItemDTO> itens) {
        return itens.stream()
            .map(i -> i.getValor() != null ? i.getValor() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal distanciaAoTotal(BigDecimal soma, BigDecimal total) {
        return soma.subtract(total).abs();
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
            if (mesmoValor && (mesmaData || descItem.contains(descCand) || descCand.contains(descItem))) {
                return true;
            }
        }
        return false;
    }
}
