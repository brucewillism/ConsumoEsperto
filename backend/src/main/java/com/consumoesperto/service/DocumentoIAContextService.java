package com.consumoesperto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentoIAContextService {

    /** Acima deste volume o texto vai em várias chamadas à IA para não omitir páginas finais sob limite efetivo de contexto. */
    private static final int MAX_CHARS_CHUNK = 11_500;
    private static final int OVERLAP_CHARS = 700;
    private static final int LOG_JSON_MAX_CHARS = 16_000;
    /** Revisão/consolidação: inclui todas as páginas possíveis com teto segurança para o modelo. */
    private static final int TEXTO_REVISION_MAX_CHARS = 38_000;

    private final PdfTextExtractionService pdfTextExtractionService;
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    private String textoAuditoria(LogAuditoria op, String entrada, JsonNode resultado) {
        try {
            String json = resultado == null ? "null"
                : objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
            return textoAuditoriaRaw(op.name(), entrada, json);
        } catch (Exception e) {
            return textoAuditoriaRaw(op.name(), entrada, String.valueOf(resultado));
        }
    }

    private static String textoAuditoriaRaw(String rotulo, String entrada, String json) {
        return "[AUDITORIA-IA-PDF:" + rotulo + "] entradaChars=" + (entrada != null ? entrada.length() : 0)
            + " entrada_truncada="
            + truncarParaLog(entrada, TEXTO_REVISION_MAX_CHARS)
            + "\n[AUDITORIA-IA-PDF:" + rotulo + ".JSON] "
            + truncarParaLog(json, LOG_JSON_MAX_CHARS);
    }

    private static String truncarParaLog(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...[truncado]";
    }

    enum LogAuditoria {
        PDF_EXTRACAO_COMPLETA,
        PDF_CHUNK,
        PDF_CHUNK_CONTINUACAO,
        PDF_REFINAMENTO,
        PDF_PARCELAS
    }

    public JsonNode extrairDocumentoPdf(Long usuarioId, byte[] pdfBytes) {
        List<String> paginas = pdfTextExtractionService.extrairTextoPorPagina(pdfBytes);
        String fullText = juntarPaginas(paginas);
        if (fullText.length() < 80) {
            throw new IllegalArgumentException("Não consegui ler texto suficiente do PDF.");
        }
        List<String> trechos = fatiarTextoParaModelo(paginas);
        JsonNode extracted;
        if (trechos.size() == 1) {
            extracted = extrairJsonPrimeiroTrecho(usuarioId, trechos.get(0), true, LogAuditoria.PDF_EXTRACAO_COMPLETA);
        } else {
            extracted = extrairJsonPrimeiroTrecho(usuarioId, trechos.get(0), true, LogAuditoria.PDF_CHUNK);
            for (int i = 1; i < trechos.size(); i++) {
                JsonNode cont = extrairLancamentosContinuacao(usuarioId, trechos.get(i), i + 1, trechos.size());
                mesclarLancamentosNoAlvo(extracted, cont);
            }
            log.info("{}",
                textoAuditoria(LogAuditoria.PDF_CHUNK_CONTINUACAO, "merge trechos=" + trechos.size(), extracted));
        }
        String textoRevisao = fullText.length() > TEXTO_REVISION_MAX_CHARS
            ? fullText.substring(0, TEXTO_REVISION_MAX_CHARS)
            : fullText;
        extracted = melhorarExtracaoFaturaSeNecessario(usuarioId, extracted, textoRevisao);
        return complementarParcelasFatura(usuarioId, extracted, textoRevisao);
    }

    private static String juntarPaginas(List<String> paginas) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paginas.size(); i++) {
            String p = paginas.get(i) != null ? paginas.get(i) : "";
            if (!p.isBlank()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("--- Página ").append(i + 1).append(" ---\n").append(p);
            }
        }
        return sb.toString().trim();
    }

    /**
     * Marca páginas e partições longas por limite (~tokens), com ligamento por sobreposição.
     */
    private static List<String> fatiarTextoParaModelo(List<String> paginas) {
        StringBuilder rotuloCompleto = new StringBuilder();
        for (int i = 0; i < paginas.size(); i++) {
            String p = paginas.get(i) != null ? paginas.get(i) : "";
            rotuloCompleto.append("--- Página ").append(i + 1).append(" ---\n").append(p).append('\n');
        }
        String full = rotuloCompleto.toString().trim();
        return fatiarPorTamanhoComSobreposicao(full, MAX_CHARS_CHUNK);
    }

    private static List<String> fatiarPorTamanhoComSobreposicao(String texto, int limite) {
        List<String> out = new ArrayList<>();
        if (texto.length() <= limite) {
            out.add(texto);
            return out;
        }
        int pos = 0;
        while (pos < texto.length()) {
            int end = Math.min(texto.length(), pos + limite);
            if (end < texto.length()) {
                int lastNl = texto.lastIndexOf('\n', end);
                if (lastNl > pos + (limite * 7 / 10)) {
                    end = lastNl + 1;
                }
            }
            out.add(texto.substring(pos, end));
            if (end >= texto.length()) {
                break;
            }
            pos = Math.max(pos + 1, end - OVERLAP_CHARS);
        }
        return out;
    }

    private JsonNode extrairJsonPrimeiroTrecho(Long usuarioId, String trechoUsuario, boolean esquemaCabecalho, LogAuditoria logOp) {
        String system = sistemaExtracaoCabecalho(esquemaCabecalho);
        JsonNode json = openAiService.gerarJson(usuarioId, system,
            "(Trecho inicial do PDF; pode haver continuações.)\n\nTexto extraído:\n" + trechoUsuario);
        log.info("{}", textoAuditoria(logOp, trechoUsuario, json));
        return json;
    }

    private JsonNode extrairLancamentosContinuacao(Long usuarioId, String trecho, int parteNum, int totalTrechos) {
        String system = "Você continua a extração da MESMA fatura de cartão brasileira. Retorne apenas JSON válido "
            + "{\"lancamentos\":[{\"data\":\"yyyy-MM-dd\",\"descricao\":\"string\",\"valor\":0.0,\"parcelaAtual\":null,\"totalParcelas\":null}]}. "
            + "Liste somente novos lançamentos presentes neste trecho que ainda não constem em trechos anteriores "
            + "(cartão, PIX, parcelas 02/10, assinaturas, multas internacionais etc.). "
            + "ATENÇÃO Itaú/outros: padrão 'DD/MM estabelecimento … N/N valor' — o par N/N imediatamente antes do valor (ex.: '10/10 64,10') "
            + "é parcela atual/total, NÃO separador de milhar; o valor cobrado NESTA fatura é só o último número (ex.: 64.10). "
            + "Use 1064.10 apenas quando o PDF mostrar milhar com ponto brasileiro explícito (ex.: '1.064,10' ou linha 'R$ 1.064,10'), nunca por causa de '10/10' antes dos centavos. "
            + "Continue procurando além das tabelas principais tarifas: Anuidade, IOF, Juros Rotativos, Tarifas. "
            + "Linhas já extraídas noutras partes NÃO repetir.";
        JsonNode json = openAiService.gerarJson(usuarioId, system,
            "Trecho PDF parte " + parteNum + " de ~" + totalTrechos + "\n\n" + trecho);
        log.info("{}", textoAuditoria(LogAuditoria.PDF_CHUNK, trecho, json));
        return json;
    }

    /** Prompt completo: taxas ocultas, assinatura vs hábito (campo insights), schema estendido. */
    private static String sistemaExtracaoCabecalho(boolean incluirTodosOsTiposDocs) {
        String baseAudit = auditoriaFinanceiraHumanaLinhas();
        if (!incluirTodosOsTiposDocs) {
            return sistemaSóFatura(baseAudit);
        }
        return "Você classifica e extrai PDFs financeiros brasileiros. Retorne apenas JSON válido. "
            + "Reconheça CONTRACHEQUE quando houver termos como Vencimentos, Líquido, IRRF, INSS, Folha, FGTS, holerite. "
            + "Classifique como FATURA_CARTAO quando houver vencimento da fatura, pagamento mínimo, fechamento, limite, cartão, compras parceladas, total da fatura ou lançamentos de cartão. "
            + "Classifique como EXTRATO_CONTA somente quando for movimentação de conta corrente com saldo, crédito/débito em conta, agência/conta, PIX/TED/depósito/saque, sem vencimento de fatura. "
            + taxasEhLeituraFatura()
            + baseAudit + " "
            + "Para CONTRACHEQUE e OUTRO, você pode usar o array insights textual curto quando fizer sentido. "
            + "Para FATURA_CARTAO (e EXTRATO se relevante): preencher taxasForaDaTabelaPrincipal sempre que aparecer valores de Anuidade, IOF internacional/compra exterior, "
            + "Juros Rotativos, Encargos, Tarifa de Cobrança, Seguro Prestamista etc. mesmo fora das linhas típicas de compras.\n\n"
            + "Schema: {\"tipoDocumento\":\"FATURA_CARTAO|EXTRATO_CONTA|CONTRACHEQUE|OUTRO\","
            + "\"bancoCartao\":\"...\",\"dataVencimento\":\"yyyy-MM-dd\",\"dataFechamento\":\"yyyy-MM-dd\","
            + "\"valorTotal\":0.0,\"pagamentoMinimo\":0.0,"
            + "\"taxasForaDaTabelaPrincipal\":[{\"tipo\":\"ANUIDADE|IOF|JUROS|TARIFA|OUTRO\",\"valor\":0.0,\"descricao\":\"string\"}],"
            + "\"lancamentos\":[{\"data\":\"yyyy-MM-dd\",\"descricao\":\"...\",\"valor\":0.0,\"parcelaAtual\":null,\"totalParcelas\":null}],"
            + "\"insights\":[\"pontos objetivos opcionais: não confundir assinatura mensal fixa com hábito de consumo variável conforme auditoria financeira humanizada\"],"
            + "\"empresa\":\"...\",\"mes\":1,\"ano\":2026,\"salarioBruto\":0.0,\"salarioLiquido\":0.0,"
            + "\"descontos\":[{\"rotulo\":\"INSS\",\"valor\":0.0}]}";
    }

    private static String sistemaSóFatura(String baseAudit) {
        return "Você extrai uma fatura de cartão brasileira (tipoDocumento=FATURA_CARTAO). Retorne apenas JSON válido. "
            + taxasEhLeituraFatura()
            + baseAudit + " "
            + "Campo obrigatório taxasForaDaTabelaPrincipal quando houver anuidade, IOF, juros ou tarifa fora do bloco típico de compras.\n\n"
            + "{\"tipoDocumento\":\"FATURA_CARTAO\",\"bancoCartao\":\"...\",\"dataVencimento\":\"yyyy-MM-dd\",\"dataFechamento\":\"yyyy-MM-dd\","
            + "\"valorTotal\":0.0,\"pagamentoMinimo\":0.0,"
            + "\"taxasForaDaTabelaPrincipal\":[{\"tipo\":\"ANUIDADE|IOF|JUROS|TARIFA|OUTRO\",\"valor\":0.0,\"descricao\":\"string\"}],"
            + "\"lancamentos\":[...],\"insights\":[]}";
    }

    private static String taxasEhLeituraFatura() {
        return "Em faturas de cartão, extraia TODOS os lançamentos visíveis em TODAS as páginas do trecho; "
            + "não resuma listas longas. Se a descrição indicar parcelamento como 02/10, 2/10, parcela 2 de 10, "
            + "ou coluna de parcela separada da descrição, preencha parcelaAtual=2 e totalParcelas=10. "
            + "Leitura de valores BR: O valor cobrado na linha é normalmente o ÚLTIMO valor monetário. "
            + "Quando aparecer 'N/M' (dois números usados de parcela) seguido de vírgula decimal (ex.: '10/10 64,10'), "
            + "esse 'N/M' é só indicador de parcela; o valor é 64.10, NÃO 1064.10. "
            + "Separador de milhar só quando o PDF traz ponto entre grupos antes da vírgula decimal (ex.: '1.064,10' → 1064.10). "
            + "Não duplique linhas da secção 'Compras parceladas - próximas faturas' / 'próxima fatura' que já estão em 'Lançamentos: compras e saques' ou 'produtos e serviços' deste mês. "
            + "O valor de cada lançamento deve ser o valor cobrado nesta fatura, não o valor total original da compra. "
            + "Procure ativamente em rodapés, boxes e resumos taxas: Anuidade, IOF, Juros Rotativos, Tarifas e seguros do cartão. "
            + "Inclua essas taxas tanto em \"lancamentos\" (nome descritivo) quanto repetindo o mesmo valor em \"taxasForaDaTabelaPrincipal\" quando forem classificadas. "
            + "Confira a soma dos lançamentos contra o valorTotal informado pela fatura e contra subtotais do PDF (ex.: 'Lançamentos no cartão', 'Total dos lançamentos atuais'); se não bater, revise linhas de parcela N/N e duplicatas. ";
    }

    private static String auditoriaFinanceiraHumanaLinhas() {
        return "Persona auditoria (texto opcional insights): diferenciar Assinaturas/Serviços Telefonia/Streaming/Software/Educacao digital "
            + "com gastos hábituais VARIÁVEIS (postos de combustível, supermercados, restaurantes, farmácias). "
            + "Nunca escreva no insights que algo como posto de combustível ou alimentação são 'assinatura' — trate aumento nelas como variação de consumo.";
    }

    private void mesclarLancamentosNoAlvo(JsonNode alvoCompleto, JsonNode fragmentoTrecho) {
        if (!(alvoCompleto instanceof ObjectNode objeto)) {
            return;
        }
        ArrayNode alvoLista = objeto.path("lancamentos").isArray()
            ? (ArrayNode) objeto.path("lancamentos")
            : JsonNodeFactory.instance.arrayNode();
        if (!objeto.has("lancamentos")) {
            objeto.set("lancamentos", alvoLista);
        }
        ArrayNode adding = fragmentoTrecho.path("lancamentos").isArray()
            ? (ArrayNode) fragmentoTrecho.path("lancamentos")
            : JsonNodeFactory.instance.arrayNode();
        LinkedHashMap<String, JsonNode> vistos = new LinkedHashMap<>();
        dedupeListaPara(vistos, alvoLista);
        dedupeListaPara(vistos, adding);
        ArrayNode novo = JsonNodeFactory.instance.arrayNode();
        for (JsonNode n : vistos.values()) {
            novo.add(n);
        }
        objeto.set("lancamentos", novo);
    }

    private static void dedupeListaPara(LinkedHashMap<String, JsonNode> vistos, ArrayNode lista) {
        for (JsonNode n : lista) {
            String ch = chaveLancamentoDedupe(n);
            vistos.putIfAbsent(ch, n.deepCopy());
        }
    }

    private static String chaveLancamentoDedupe(JsonNode n) {
        String data = norm(n.path("data").asText(""));
        String desc = norm(n.path("descricao").asText(""));
        BigDecimal valor = money(n.path("valor"));
        String vb = valor != null ? valor.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0";
        return data + "|" + desc.substring(0, Math.min(desc.length(), 140)) + "|" + vb;
    }

    private JsonNode melhorarExtracaoFaturaSeNecessario(Long usuarioId, JsonNode extracted, String text) {
        if (extracted == null || !extracted.isObject()) {
            return extracted;
        }
        if (!"FATURA_CARTAO".equalsIgnoreCase(extracted.path("tipoDocumento").asText(""))
            && !"EXTRATO_CONTA".equalsIgnoreCase(extracted.path("tipoDocumento").asText(""))) {
            return extracted;
        }
        ArrayNode lancamentos = extracted.path("lancamentos").isArray()
            ? (ArrayNode) extracted.path("lancamentos")
            : objectMapper.createArrayNode();
        BigDecimal valorTotal = money(extracted.path("valorTotal"));
        BigDecimal soma = somaLancamentos(lancamentos);
        boolean poucosItens = lancamentos.size() > 0 && lancamentos.size() < 15;
        boolean somaDivergente = valorTotal != null
            && valorTotal.compareTo(BigDecimal.ZERO) > 0
            && soma != null
            && valorTotal.subtract(soma).abs().compareTo(new BigDecimal("1.00")) > 0;
        if (!poucosItens && !somaDivergente) {
            return extracted;
        }

        String systemRetry = "Você é extrator/auditor de fatura de cartão brasileira. Retorne apenas JSON válido. "
            + taxasEhLeituraFatura().replace("\n\n", "\n ")
            + auditoriaFinanceiraHumanaLinhas().replace("\n\n", "\n ")
            + " Refaça a extração completa usando TODO o texto fornecido, linha a linha nas tabelas, sem omitir lançamentos. "
            + "Inclua compras, assinaturas de software/streaming/TV, PIX/cartão, parcelas, juros rotativos, IOF internacional e anuidade. "
            + "Respeite parcelas no fim da linha: '10/10 64,10' = 64.10; milhar explícito '4.380,01' = 4380.01. "
            + "Antes de responder, some todos os lançamentos + taxas repetidas onde couber no total e compare com valorTotal "
            + "e com subtotais do próprio PDF (Lançamentos no cartão + produtos/serviços = total desta fatura). "
            + "Schema: {\"tipoDocumento\":\"FATURA_CARTAO\",\"bancoCartao\":\"...\",\"dataVencimento\":\"yyyy-MM-dd\","
            + "\"dataFechamento\":\"yyyy-MM-dd\",\"valorTotal\":0.0,\"pagamentoMinimo\":0.0,"
            + "\"taxasForaDaTabelaPrincipal\":[{\"tipo\":\"ANUIDADE|IOF|JUROS|TARIFA|OUTRO\",\"valor\":0.0,\"descricao\":\"string\"}],"
            + "\"lancamentos\":[{\"data\":\"yyyy-MM-dd\",\"descricao\":\"...\",\"valor\":0.0,\"parcelaAtual\":null,\"totalParcelas\":null}]}";

        JsonNode retry = openAiService.gerarJson(usuarioId, systemRetry, "Texto completo concatenado das páginas do PDF:\n" + text);
        log.info("{}", textoAuditoria(LogAuditoria.PDF_REFINAMENTO, text, retry));

        ArrayNode retryLancamentos = retry.path("lancamentos").isArray()
            ? (ArrayNode) retry.path("lancamentos")
            : objectMapper.createArrayNode();
        BigDecimal retrySoma = somaLancamentos(retryLancamentos);
        BigDecimal retryTotal = money(retry.path("valorTotal"));
        BigDecimal baseDiff = valorTotal != null && soma != null ? valorTotal.subtract(soma).abs() : BigDecimal.valueOf(Long.MAX_VALUE);
        BigDecimal retryDiff = retryTotal != null && retrySoma != null ? retryTotal.subtract(retrySoma).abs() : BigDecimal.valueOf(Long.MAX_VALUE);
        if (retryLancamentos.size() > lancamentos.size() || retryDiff.compareTo(baseDiff) < 0) {
            return retry;
        }
        return extracted;
    }

    private JsonNode complementarParcelasFatura(Long usuarioId, JsonNode extracted, String text) {
        if (extracted == null || !extracted.isObject()) {
            return extracted;
        }
        if (!"FATURA_CARTAO".equalsIgnoreCase(extracted.path("tipoDocumento").asText(""))
            && !"EXTRATO_CONTA".equalsIgnoreCase(extracted.path("tipoDocumento").asText(""))) {
            return extracted;
        }
        ArrayNode lancamentos = extracted.path("lancamentos").isArray()
            ? (ArrayNode) extracted.path("lancamentos")
            : null;
        if (lancamentos == null || lancamentos.isEmpty() || contemParcelas(lancamentos)) {
            return extracted;
        }

        JsonNode parcelas = openAiService.gerarJson(
            usuarioId,
            "Você é auditor de fatura de cartão. Retorne apenas JSON válido no formato "
                + "{\"parcelas\":[{\"data\":\"yyyy-MM-dd\",\"descricao\":\"...\",\"valor\":0.0,\"parcelaAtual\":1,\"totalParcelas\":2}]}. "
                + "Extraia SOMENTE lançamentos parcelados que estejam explicitamente marcados no texto da fatura "
                + "por coluna/marcador como 02/10, 2/10, parcela 2 de 10, Parc. 02/10, ou coluna de parcela separada da descrição. "
                + "Não invente parcelamento por data antiga; se não houver marcador explícito, retorne {\"parcelas\":[]}.",
            "Texto extraído do PDF:\n" + text
        );
        log.info("{}", textoAuditoria(LogAuditoria.PDF_PARCELAS, text, parcelas));

        ArrayNode arr = parcelas.path("parcelas").isArray() ? (ArrayNode) parcelas.path("parcelas") : objectMapper.createArrayNode();
        if (arr.isEmpty()) {
            return extracted;
        }
        for (JsonNode p : arr) {
            ObjectNode target = localizarLancamento(lancamentos, p);
            Integer atual = readPositiveInt(p.path("parcelaAtual"));
            Integer total = readPositiveInt(p.path("totalParcelas"));
            if (target != null && atual != null && total != null && total > 1 && atual <= total) {
                target.put("parcelaAtual", atual);
                target.put("totalParcelas", total);
            }
        }
        return extracted;
    }

    private static boolean contemParcelas(ArrayNode lancamentos) {
        for (JsonNode n : lancamentos) {
            if (n.path("parcelaAtual").isNumber() && n.path("totalParcelas").asInt(0) > 1) {
                return true;
            }
        }
        return false;
    }

    private static ObjectNode localizarLancamento(ArrayNode lancamentos, JsonNode parcela) {
        String data = parcela.path("data").asText("");
        String desc = norm(parcela.path("descricao").asText(""));
        BigDecimal valor = money(parcela.path("valor"));
        ObjectNode melhor = null;
        int melhorScore = 0;
        for (JsonNode node : lancamentos) {
            if (!(node instanceof ObjectNode obj)) {
                continue;
            }
            int score = 0;
            if (!data.isBlank() && data.equals(obj.path("data").asText(""))) {
                score += 3;
            }
            String d2 = norm(obj.path("descricao").asText(""));
            if (!desc.isBlank() && !d2.isBlank() && (desc.contains(d2) || d2.contains(desc))) {
                score += 4;
            }
            if (valor != null && valor.compareTo(money(obj.path("valor"))) == 0) {
                score += 3;
            }
            if (score > melhorScore) {
                melhorScore = score;
                melhor = obj;
            }
        }
        return melhorScore >= 6 ? melhor : null;
    }

    private static Integer readPositiveInt(JsonNode n) {
        try {
            int value = n.isNumber() ? n.asInt() : Integer.parseInt(n.asText("").replaceAll("[^0-9]", ""));
            return value > 0 ? value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal money(JsonNode n) {
        try {
            if (n == null || n.isMissingNode() || n.isNull()) {
                return null;
            }
            if (n.isNumber()) {
                return BigDecimal.valueOf(n.asDouble()).setScale(2, RoundingMode.HALF_UP);
            }
            String t = n.asText("").replace("R$", "").trim();
            if (t.contains(",")) {
                t = t.replace(".", "").replace(",", ".");
            }
            return new BigDecimal(t).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal somaLancamentos(ArrayNode lancamentos) {
        if (lancamentos == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal soma = BigDecimal.ZERO;
        for (JsonNode n : lancamentos) {
            BigDecimal valor = money(n.path("valor"));
            if (valor != null) {
                soma = soma.add(valor);
            }
        }
        return soma.setScale(2, RoundingMode.HALF_UP);
    }

    private static String norm(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
