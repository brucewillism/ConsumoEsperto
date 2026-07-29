package com.consumoesperto.fiscal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Registro versionado de tabelas fiscais por ano-calendário.
 * Carrega e valida {@code fiscal/tabelas/YYYY.json}.
 */
public final class TabelaFiscalAnoRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile List<TabelaFiscalAno> cache;

    private TabelaFiscalAnoRegistry() {}

    public enum TipoSeguradoInss {
        EMPREGADO,
        CONTRIBUINTE_INDIVIDUAL,
        FACULTATIVO,
        MEI,
        TRABALHADOR_AVULSO,
        EMPREGADO_DOMESTICO,
        RPPS
    }

    public record FaixaInss(BigDecimal limiteSuperior, BigDecimal aliquotaPct) {}
    public record FaixaIr(BigDecimal limiteSuperior, BigDecimal aliquotaPct, BigDecimal parcelaDeduzir) {}

    public record FaixaReducaoIrrf(
        BigDecimal rendimentoAte,
        BigDecimal rendimentoDe,
        BigDecimal reducaoMaxima,
        BigDecimal constante,
        BigDecimal coeficienteRendimento
    ) {}

    public record ReducaoAdicionalIrrf(List<FaixaReducaoIrrf> faixas, String variavelReducao) {}

    public record VersaoTabela(
        LocalDate inicioVigencia,
        LocalDate fimVigencia,
        TipoSeguradoInss tipoSeguradoInss,
        List<TipoSeguradoInss> tiposSuportados,
        BigDecimal tetoInss,
        BigDecimal salarioMinimo,
        BigDecimal deducaoDependente,
        BigDecimal descontoSimplificadoMax,
        List<FaixaInss> faixasInss,
        List<FaixaIr> faixasIr,
        ReducaoAdicionalIrrf reducaoAdicional,
        String fonte,
        String observacoes
    ) {
        public boolean suporta(TipoSeguradoInss tipo) {
            return tiposSuportados.contains(tipo);
        }
    }

    public record TabelaFiscalAno(int ano, List<VersaoTabela> versoes) {}

    public static Optional<VersaoTabela> obterVersao(int ano, LocalDate dataReferencia) {
        if (dataReferencia == null) {
            return Optional.empty();
        }
        return obter(ano).flatMap(t ->
            t.versoes().stream()
                .filter(v -> !dataReferencia.isBefore(v.inicioVigencia())
                    && (v.fimVigencia() == null || !dataReferencia.isAfter(v.fimVigencia())))
                .findFirst()
        );
    }

    public static Optional<TabelaFiscalAno> obter(int ano) {
        return carregarTodas().stream().filter(t -> t.ano() == ano).findFirst();
    }

    public static List<TabelaFiscalAno> anosDisponiveis() {
        return carregarTodas();
    }

    static List<TabelaFiscalAno> carregarTodas() {
        if (cache != null) {
            return cache;
        }
        synchronized (TabelaFiscalAnoRegistry.class) {
            if (cache != null) {
                return cache;
            }
            List<TabelaFiscalAno> loaded = new ArrayList<>();
            for (int ano : List.of(2025, 2026)) {
                carregarArquivo(ano).ifPresent(loaded::add);
            }
            cache = Collections.unmodifiableList(loaded);
            return cache;
        }
    }

    static void resetCacheForTests() {
        cache = null;
    }

    private static Optional<TabelaFiscalAno> carregarArquivo(int ano) {
        String path = "fiscal/tabelas/" + ano + ".json";
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            JsonNode root = MAPPER.readTree(in);
            FiscalTabelaSchemaValidator.validarArquivo(root, ano);
            List<VersaoTabela> versoes = new ArrayList<>();
            for (JsonNode v : root.path("versoes")) {
                versoes.add(parseVersao(v));
            }
            return Optional.of(new TabelaFiscalAno(ano, List.copyOf(versoes)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao carregar tabela fiscal " + ano + ": " + e.getMessage(), e);
        }
    }

    private static VersaoTabela parseVersao(JsonNode v) {
        LocalDate inicio = LocalDate.parse(v.path("inicioVigencia").asText());
        LocalDate fim = v.hasNonNull("fimVigencia") ? LocalDate.parse(v.path("fimVigencia").asText()) : null;
        JsonNode inss = v.path("inss");
        TipoSeguradoInss tipo = TipoSeguradoInss.valueOf(inss.path("tipoSegurado").asText("EMPREGADO"));
        List<TipoSeguradoInss> suportados = new ArrayList<>();
        for (JsonNode t : inss.path("tiposSuportadosPeloSistema")) {
            suportados.add(TipoSeguradoInss.valueOf(t.asText()));
        }
        if (suportados.isEmpty()) {
            suportados.add(tipo);
        }
        BigDecimal teto = decimal(inss.path("teto"));
        BigDecimal salarioMinimo = decimal(inss.path("salarioMinimo"));
        List<FaixaInss> faixasInss = parseFaixasInss(inss.path("faixas"));
        JsonNode irrf = v.path("irrf");
        List<FaixaIr> faixasIr = parseFaixasIr(irrf.path("faixas"));
        BigDecimal dep = decimal(v.path("dependente").path("deducaoMensal"));
        if (dep == null) {
            dep = decimal(irrf.path("deducaoDependente"));
        }
        BigDecimal descSimpl = decimal(irrf.path("descontoSimplificadoMax"));
        ReducaoAdicionalIrrf reducao = parseReducao(irrf.path("reducaoAdicional"));
        String fonte = irrf.path("fonteNormativa").path("url").asText(
            v.path("observacoes").asText("")
        );
        return new VersaoTabela(
            inicio, fim, tipo, List.copyOf(suportados), teto, salarioMinimo, dep, descSimpl,
            faixasInss, faixasIr, reducao, fonte, v.path("observacoes").asText("")
        );
    }

    private static ReducaoAdicionalIrrf parseReducao(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        String variavel = node.path("variavelReducao").asText("RENDIMENTO_TRIBUTAVEL_MENSAL");
        List<FaixaReducaoIrrf> faixas = new ArrayList<>();
        for (JsonNode f : node.path("faixas")) {
            faixas.add(new FaixaReducaoIrrf(
                decimal(f.path("rendimentoAte")),
                decimal(f.path("rendimentoDe")),
                decimal(f.path("reducaoMaxima")),
                decimal(f.path("constante")),
                decimal(f.path("coeficienteRendimento"))
            ));
        }
        return faixas.isEmpty() ? null : new ReducaoAdicionalIrrf(faixas, variavel);
    }

    private static List<FaixaInss> parseFaixasInss(JsonNode arr) {
        List<FaixaInss> out = new ArrayList<>();
        for (JsonNode n : arr) {
            out.add(new FaixaInss(decimal(n.path("limiteSuperior")), decimal(n.path("aliquotaPct"))));
        }
        return out;
    }

    private static List<FaixaIr> parseFaixasIr(JsonNode arr) {
        List<FaixaIr> out = new ArrayList<>();
        for (JsonNode n : arr) {
            JsonNode lim = n.get("limiteSuperior");
            BigDecimal limite = lim == null || lim.isNull() ? null : lim.decimalValue();
            out.add(new FaixaIr(limite, decimal(n.path("aliquotaPct")), decimal(n.path("parcelaDeduzir"))));
        }
        return out;
    }

    private static BigDecimal decimal(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        return n.decimalValue();
    }
}
