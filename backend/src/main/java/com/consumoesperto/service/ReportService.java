package com.consumoesperto.service;

import com.consumoesperto.dto.RendaConfigDTO;
import com.consumoesperto.dto.relatorio.IrPdfDeclaracaoDados;
import com.consumoesperto.dto.relatorio.RelatorioCategoriaVm;
import com.consumoesperto.dto.relatorio.RelatorioMetaVm;
import com.consumoesperto.model.MetaFinanceira;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.MetaFinanceiraRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Relatório mensal em PDF (dark mode) via Thymeleaf + Flying Saucer; bytes só em memória.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final DateTimeFormatter GERADO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UsuarioRepository usuarioRepository;
    private final TransacaoRepository transacaoRepository;
    private final TransacaoService transacaoService;
    private final MetaFinanceiraRepository metaFinanceiraRepository;
    private final MetaFinanceiraService metaFinanceiraService;
    private final OpenAiService openAiService;
    private final SpringTemplateEngine templateEngine;
    private final RendaConfigService rendaConfigService;
    private final SaldoService saldoService;
    private final RelatorioFinanceiroService relatorioFinanceiroService;

    public record RelatorioPdf(byte[] bytes, String nomeArquivo) {
    }

    /**
     * @param mes 1–12
     * @param ano ano civil
     */
    @Transactional(readOnly = true)
    public Optional<RelatorioPdf> gerarRelatorioMensal(Long userId, int mes, int ano) {
        Objects.requireNonNull(userId, "userId");
        if (mes < 1 || mes > 12) {
            throw new IllegalArgumentException("Mês inválido: " + mes);
        }
        Usuario usuario = usuarioRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Utilizador não encontrado"));

        YearMonth ym = YearMonth.of(ano, mes);
        LocalDateTime inicio = ym.atDay(1).atStartOfDay();
        LocalDateTime fim = ym.atEndOfMonth().atTime(23, 59, 59);

        Map<String, Object> resumoMes = transacaoService.resumoFinanceiroMes(userId, ym);
        long totalLinhas = ((Number) resumoMes.get("totalTransacoes")).longValue();
        BigDecimal totalDespesa = BigDecimal.valueOf((Double) resumoMes.get("totalDespesas"));
        BigDecimal fluxoTotal = BigDecimal.valueOf((Double) resumoMes.get("saldo"));
        Optional<RendaConfigDTO> rendaOpt = rendaConfigService.obterDto(userId);
        boolean temRenda = rendaOpt.isPresent()
            && rendaOpt.get().getSalarioBruto() != null
            && rendaOpt.get().getSalarioBruto().compareTo(BigDecimal.ZERO) > 0;
        BigDecimal saldoReal = saldoService.saldoContaCorrente(userId);
        if (totalLinhas == 0 && !temRenda) {
            log.info("[PDF-REPORT] Sem lançamentos no período nem renda configurada userId={} {}-{}", userId, ano, mes);
            return Optional.empty();
        }
        BigDecimal economiaRealizada = fluxoTotal.compareTo(BigDecimal.ZERO) > 0 ? fluxoTotal : BigDecimal.ZERO;

        List<Object[]> porCategoria = transacaoRepository.findDespesasByUsuarioIdAndPeriodoGroupByCategoria(
            userId, inicio, fim);
        List<MetaFinanceira> metas = metaFinanceiraRepository.findByUsuarioIdOrderByPrioridadeDescDataCriacaoDesc(userId);

        BigDecimal limiteGastoReferencia = calcularLimiteGastoReferencia(userId, metas, totalDespesa);
        int numCat = Math.max(1, porCategoria.size());
        BigDecimal metaPorCategoria = limiteGastoReferencia.divide(BigDecimal.valueOf(numCat), 2, RoundingMode.HALF_UP);

        String maiorCategoria = "—";
        BigDecimal maiorValor = BigDecimal.ZERO;
        List<RelatorioCategoriaVm> categorias = new ArrayList<>();
        for (Object[] row : porCategoria) {
            String nome = row[0] != null ? row[0].toString() : "Sem categoria";
            BigDecimal gasto = row[1] instanceof BigDecimal b ? b : new BigDecimal(row[1].toString());
            if (gasto.compareTo(maiorValor) > 0) {
                maiorValor = gasto;
                maiorCategoria = nome;
            }
            boolean excedeu = gasto.compareTo(metaPorCategoria) > 0;
            int barPct = metaPorCategoria.compareTo(BigDecimal.ZERO) > 0
                ? gasto.multiply(BigDecimal.valueOf(100)).divide(metaPorCategoria, 0, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100)).intValue()
                : 50;
            barPct = Math.max(3, Math.min(100, barPct));
            categorias.add(new RelatorioCategoriaVm(nome, BRL.format(gasto), BRL.format(metaPorCategoria), barPct, excedeu));
        }

        String insight = totalLinhas > 0
            ? openAiService.gerarInsightRelatorioMaiorGasto(
                userId, mes, ano, maiorCategoria, maiorValor, totalDespesa)
            : "Sem despesas confirmadas neste mês — use o WhatsApp ou o app para lançar movimentos.";

        List<RelatorioMetaVm> metaVms = new ArrayList<>();
        if (metas.isEmpty()) {
            metaVms.add(new RelatorioMetaVm("—", "—", "—", "Sem metas no app", false));
        } else {
            for (MetaFinanceira m : metas) {
                BigDecimal proj = m.getValorPoupadoMensal() != null && m.getPrazoMeses() != null
                    ? m.getValorPoupadoMensal().multiply(m.getPrazoMeses()).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                boolean dentro = m.getValorTotal() != null && proj.compareTo(m.getValorTotal()) >= 0;
                boolean alerta = !dentro;
                String ind = m.getValorPoupadoMensal() == null || m.getPrazoMeses() == null
                    ? "Revise valores"
                    : (dentro ? "Dentro da meta" : "Acima da meta");
                if (m.getValorPoupadoMensal() == null || m.getPrazoMeses() == null) {
                    alerta = false;
                }
                metaVms.add(new RelatorioMetaVm(
                    trunc(m.getDescricao(), 52),
                    m.getValorTotal() != null ? BRL.format(m.getValorTotal()) : "—",
                    m.getValorPoupadoMensal() != null ? BRL.format(m.getValorPoupadoMensal()) : "—",
                    ind,
                    alerta
                ));
            }
        }

        String mesAnoLabel = capitalize(ym.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "BR"))) + " de " + ano;

        Context ctx = new Context();
        ctx.setVariable("usuarioNome", usuario.getNome());
        ctx.setVariable("mesAnoLabel", mesAnoLabel);
        ctx.setVariable("totalGasto", BRL.format(totalDespesa));
        ctx.setVariable("fluxoTotal", BRL.format(fluxoTotal));
        ctx.setVariable("fluxoNegativo", fluxoTotal.compareTo(BigDecimal.ZERO) < 0);
        ctx.setVariable("economiaRealizada", BRL.format(economiaRealizada));
        ctx.setVariable("iaInsight", insight);
        ctx.setVariable("categorias", categorias);
        ctx.setVariable("metas", metaVms);
        ctx.setVariable("mesNum", mes);
        ctx.setVariable("anoNum", ano);
        ctx.setVariable("geradoEm", LocalDateTime.now().format(GERADO));
        ctx.setVariable("temRenda", temRenda);
        if (temRenda) {
            RendaConfigDTO rc = rendaOpt.get();
            ctx.setVariable("rendaBruto", BRL.format(rc.getSalarioBruto()));
            ctx.setVariable("rendaDescontos", BRL.format(rc.getTotalDescontos() != null ? rc.getTotalDescontos() : BigDecimal.ZERO));
            ctx.setVariable("rendaLiquido", BRL.format(rc.getSalarioLiquido() != null ? rc.getSalarioLiquido() : BigDecimal.ZERO));
        } else {
            ctx.setVariable("rendaBruto", "—");
            ctx.setVariable("rendaDescontos", "—");
            ctx.setVariable("rendaLiquido", "—");
        }
        ctx.setVariable("saldoReal", BRL.format(saldoReal));
        ctx.setVariable("saldoRealNegativo", saldoReal.compareTo(BigDecimal.ZERO) < 0);

        BigDecimal compromFuturo = Optional.ofNullable(
            transacaoRepository.sumParcelasFuturasConfirmadasApos(userId, fim)
        ).orElse(BigDecimal.ZERO);
        ctx.setVariable("comprometimentoRendaFutura", BRL.format(compromFuturo));

        String html = templateEngine.process("relatorio-dark", ctx);
        byte[] pdf = renderPdf(html);
        String nomeArquivo = String.format("Relatorio_Consumo_%02d_%d.pdf", mes, ano);
        log.info("[PDF-REPORT] Relatório dark gerado userId={} bytes={} ficheiro={}", userId, pdf.length, nomeArquivo);
        return Optional.of(new RelatorioPdf(pdf, nomeArquivo));
    }

    /**
     * PDF de apoio ao IR: despesas confirmadas do ano-calendário, por categoria e CNPJ (mesma base do export CSV).
     */
    @Transactional(readOnly = true)
    public RelatorioPdf gerarRelatorioIrPdf(Long userId, int anoCalendario) {
        Objects.requireNonNull(userId, "userId");
        Usuario usuario = usuarioRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Utilizador não encontrado"));
        IrPdfDeclaracaoDados dados = relatorioFinanceiroService.prepararDeclaracaoIrPdf(userId, anoCalendario);
        Context ctx = new Context();
        ctx.setVariable("usuarioNome", usuario.getNome() != null ? usuario.getNome() : "");
        ctx.setVariable("anoIr", anoCalendario);
        ctx.setVariable("linhas", dados.linhas());
        ctx.setVariable("totalDespesas", BRL.format(dados.totalDespesas()));
        ctx.setVariable("linhasPendentes", dados.linhasPendentes());
        ctx.setVariable("totalPendentes", BRL.format(dados.totalPendentes()));
        ctx.setVariable("temConfirmadas", !dados.linhas().isEmpty());
        ctx.setVariable("temPendentesResumo", !dados.linhasPendentes().isEmpty());
        ctx.setVariable("detalhes", dados.detalhes());
        ctx.setVariable("qtdLancamentosConfirmados", dados.qtdLancamentosConfirmados());
        ctx.setVariable("qtdLancamentosPendentes", dados.qtdLancamentosPendentes());
        ctx.setVariable("geradoEm", LocalDateTime.now().format(GERADO));
        ctx.setVariable("semDados", dados.semDados());
        String html = templateEngine.process("relatorio-ir", ctx);
        byte[] pdf = renderPdf(html);
        String nomeArquivo = "consumo-esperto-ir-" + anoCalendario + ".pdf";
        log.info("[PDF-IR] Gerado userId={} ano={} bytes={}", userId, anoCalendario, pdf.length);
        return new RelatorioPdf(pdf, nomeArquivo);
    }

    private BigDecimal calcularLimiteGastoReferencia(Long userId, List<MetaFinanceira> metas, BigDecimal totalDespesa) {
        BigDecimal somaPercent = metas.stream()
            .map(MetaFinanceira::getPercentualComprometimento)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        Optional<BigDecimal> rendaOpt = metaFinanceiraService.calcularRendaMensalMediaUltimosTresMeses(userId);
        if (rendaOpt.isEmpty() || rendaOpt.get().compareTo(BigDecimal.ZERO) <= 0 || somaPercent.compareTo(BigDecimal.ZERO) <= 0) {
            return totalDespesa.compareTo(BigDecimal.ZERO) > 0 ? totalDespesa : BigDecimal.ONE;
        }
        BigDecimal cap = somaPercent.min(new BigDecimal("100"));
        return rendaOpt.get().multiply(cap).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private static byte[] renderPdf(String html) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(os);
            return os.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Falha ao converter HTML em PDF", e);
        }
    }

    private static String trunc(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
