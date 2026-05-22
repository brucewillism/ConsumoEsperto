package com.consumoesperto.service;

import com.consumoesperto.dto.ForecastFinanceiroDTO;
import com.consumoesperto.dto.ProjecaoMesResumoDTO;
import com.consumoesperto.repository.TransacaoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastFinanceiroService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private final TransacaoRepository transacaoRepository;
    private final OpenAiService openAiService;
    private final JarvisProtocolService jarvisProtocolService;
    private final SaldoService saldoService;

    @Transactional(readOnly = true)
    public ForecastFinanceiroDTO calcular(Long usuarioId) {
        SaldoService.ProjecaoMesCaixa p = saldoService.calcularProjecaoMes(usuarioId);
        YearMonth ym = YearMonth.now();
        LocalDate hoje = LocalDate.now();
        LocalDateTime inicio = ym.atDay(1).atStartOfDay();
        LocalDateTime fimHoje = hoje.atTime(23, 59, 59);

        BigDecimal mediaDiaria = p.gastoAtual().divide(BigDecimal.valueOf(Math.max(1, p.diaAtual())), 2, RoundingMode.HALF_UP);

        ForecastFinanceiroDTO dto = new ForecastFinanceiroDTO();
        dto.setDiaAtual(p.diaAtual());
        dto.setDiasNoMes(p.diasNoMes());
        dto.setRendaLiquida(p.rendaLiquida());
        dto.setGastoAtual(p.gastoAtual());
        dto.setMediaDiaria(mediaDiaria);
        dto.setGastoProjetado(p.gastoProjetado());
        dto.setPatrimonioLiquido(p.patrimonioLiquido());
        dto.setReceitasPrevistas(p.receitasPrevistas());
        dto.setReceitasFiscaisPrevistas(p.receitasFiscaisPrevistas());
        dto.setDespesasPrevistas(p.despesasPrevistas());
        dto.setSaldoProjetado(p.saldoProjetadoFimMes());
        dto.setMaioresCategorias(maioresCategorias(usuarioId, inicio, fimHoje));
        dto.setSafraPatrimonio(saldoService.calcularProjecaoSafraDto(usuarioId, 2));
        aplicarAnaliseIa(usuarioId, dto);
        return dto;
    }

    public String montarRespostaWhatsapp(Long usuarioId) {
        ForecastFinanceiroDTO f = calcular(usuarioId);
        String intro = jarvisProtocolService.introducaoProjecaoRotasCapital();
        String linhaFiscal = f.getReceitasFiscaisPrevistas() != null
            && f.getReceitasFiscaisPrevistas().compareTo(BigDecimal.ZERO) > 0
            ? "Receitas sazonais (13º/IR previsto): *" + BRL.format(f.getReceitasFiscaisPrevistas()) + "*\n"
            : "";
        String corpo = "*📊 Previsão de fechamento do mês*\n"
            + "Dia " + f.getDiaAtual() + " de " + f.getDiasNoMes() + "\n"
            + "Patrimônio em contas: *" + BRL.format(f.getPatrimonioLiquido()) + "*\n"
            + "Gasto até agora: *" + BRL.format(f.getGastoAtual()) + "*\n"
            + "Média diária: *" + BRL.format(f.getMediaDiaria()) + "*\n"
            + "Projeção de gasto: *" + BRL.format(f.getGastoProjetado()) + "*\n"
            + "Renda considerada: *" + BRL.format(f.getRendaLiquida()) + "*\n"
            + linhaFiscal
            + "Saldo projetado: *" + BRL.format(f.getSaldoProjetado()) + "*\n"
            + formatarSafraWhatsapp(f)
            + "Risco: *" + f.getNivelRisco() + "* (" + f.getProbabilidadeVermelho() + "%)\n\n"
            + f.getMensagemIa();
        return intro + corpo;
    }

    private String formatarSafraWhatsapp(ForecastFinanceiroDTO f) {
        if (f.getSafraPatrimonio() == null || f.getSafraPatrimonio().getMeses() == null
            || f.getSafraPatrimonio().getMeses().size() <= 1) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n*Curva de patrimônio (safra):*\n");
        for (ProjecaoMesResumoDTO m : f.getSafraPatrimonio().getMeses()) {
            sb.append("• ").append(m.getRotuloMes()).append(": *")
                .append(BRL.format(m.getSaldoProjetadoFimMes())).append("*");
            if (m.getReceitasFiscaisPrevistas() != null
                && m.getReceitasFiscaisPrevistas().compareTo(BigDecimal.ZERO) > 0) {
                sb.append(" _(+").append(BRL.format(m.getReceitasFiscaisPrevistas())).append(" fiscal)_");
            }
            sb.append("\n");
        }
        return sb.toString();

    public String gerarDicaSemanal(Long usuarioId, String resumo) {
        String fallback = "Priorize reduzir a categoria que mais cresceu nesta semana e revise compras pequenas recorrentes antes de novos lançamentos.";
        return openAiService.gerarTexto(
            usuarioId,
            "Você é um consultor financeiro pessoal. Gere uma dica curta, acionável e personalizada para economizar na próxima semana.",
            "Resumo real do usuário:\n" + resumo,
            fallback
        );
    }

    private void aplicarAnaliseIa(Long usuarioId, ForecastFinanceiroDTO dto) {
        BigDecimal probFallback = calcularProbabilidadeFallback(dto.getRendaLiquida(), dto.getGastoProjetado());
        dto.setProbabilidadeVermelho(probFallback);
        dto.setNivelRisco(nivel(probFallback));
        dto.setMensagemIa("Mantendo o ritmo atual, você deve fechar com saldo projetado de "
            + BRL.format(dto.getSaldoProjetado()) + ".");
        try {
            JsonNode json = openAiService.gerarJson(
                usuarioId,
                "Você é um analista financeiro. Retorne apenas JSON: {\"probabilidadeVermelho\":0-100,\"nivelRisco\":\"BAIXO|MEDIO|ALTO|CRITICO\",\"mensagem\":\"texto curto\"}.",
                "Dia atual do mês: " + dto.getDiaAtual()
                    + "\nDias no mês: " + dto.getDiasNoMes()
                    + "\nPatrimônio líquido: " + dto.getPatrimonioLiquido()
                    + "\nRenda líquida: " + dto.getRendaLiquida()
                    + "\nReceitas previstas (salário): " + dto.getReceitasPrevistas()
                    + "\nReceitas fiscais previstas (13º/IR): " + dto.getReceitasFiscaisPrevistas()
                    + "\nGasto atual: " + dto.getGastoAtual()
                    + "\nMédia diária: " + dto.getMediaDiaria()
                    + "\nGasto projetado: " + dto.getGastoProjetado()
                    + "\nSaldo projetado: " + dto.getSaldoProjetado()
                    + "\nMaiores categorias: " + dto.getMaioresCategorias()
            );
            BigDecimal prob = BigDecimal.valueOf(json.path("probabilidadeVermelho").asDouble(probFallback.doubleValue()))
                .setScale(2, RoundingMode.HALF_UP);
            dto.setProbabilidadeVermelho(prob.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)));
            String nivel = json.path("nivelRisco").asText(nivel(dto.getProbabilidadeVermelho())).trim().toUpperCase(Locale.ROOT);
            dto.setNivelRisco(nivel.isBlank() ? nivel(dto.getProbabilidadeVermelho()) : nivel);
            String msg = json.path("mensagem").asText("").trim();
            if (!msg.isBlank()) {
                dto.setMensagemIa(msg);
            }
        } catch (Exception e) {
            log.debug("Forecast IA indisponível, usando heurística: {}", e.getMessage());
        }
    }

    private List<String> maioresCategorias(Long usuarioId, LocalDateTime inicio, LocalDateTime fim) {
        List<String> out = new ArrayList<>();
        List<Object[]> rows = transacaoRepository.findDespesasByUsuarioIdAndPeriodoGroupByCategoria(usuarioId, inicio, fim);
        for (int i = 0; i < Math.min(5, rows.size()); i++) {
            Object[] r = rows.get(i);
            out.add(String.valueOf(r[0]) + ": " + r[1]);
        }
        return out;
    }

    private static BigDecimal calcularProbabilidadeFallback(BigDecimal renda, BigDecimal projetado) {
        if (renda == null || renda.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.valueOf(50);
        }
        BigDecimal pct = projetado.multiply(BigDecimal.valueOf(100)).divide(renda, 2, RoundingMode.HALF_UP);
        if (pct.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return BigDecimal.valueOf(90).min(pct);
        }
        if (pct.compareTo(BigDecimal.valueOf(85)) >= 0) {
            return BigDecimal.valueOf(65);
        }
        if (pct.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return BigDecimal.valueOf(35);
        }
        return BigDecimal.valueOf(10);
    }

    private static String nivel(BigDecimal prob) {
        if (prob.compareTo(BigDecimal.valueOf(85)) >= 0) {
            return "CRITICO";
        }
        if (prob.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "ALTO";
        }
        if (prob.compareTo(BigDecimal.valueOf(30)) >= 0) {
            return "MEDIO";
        }
        return "BAIXO";
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
