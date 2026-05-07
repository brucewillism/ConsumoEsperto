package com.consumoesperto.service;

import com.consumoesperto.dto.relatorio.IrPdfDeclaracaoDados;
import com.consumoesperto.dto.relatorio.IrPdfLinhaVm;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço responsável por gerar relatórios financeiros e alertas
 * 
 * Este serviço implementa a lógica de negócio para geração de relatórios
 * financeiros mensais, anuais e por categoria. Também fornece métodos
 * para geração de alertas e cálculos de indicadores financeiros.
 * 
 * Funcionalidades principais:
 * - Relatórios mensais com receitas, despesas e saldo
 * - Relatórios anuais consolidados
 * - Relatórios por categoria de transação
 * - Sistema de alertas para faturas vencendo
 * - Cálculo de percentual de economia
 * - Indicadores de saúde financeira
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor // Lombok: gera construtor com campos final automaticamente
@Slf4j // Lombok: fornece logger automático para a classe
public class RelatorioFinanceiroService {

    // Repositório para operações de consulta de transações
    private final TransacaoRepository transacaoRepository;

    private final TransacaoService transacaoService;
    
    // Repositório para operações de consulta de faturas
    private final FaturaRepository faturaRepository;

    /**
     * Gera relatório financeiro mensal completo para um usuário
     * 
     * Este método calcula e retorna um relatório abrangente do mês especificado,
     * incluindo receitas, despesas, saldo, faturas vencendo e percentual de economia.
     * 
     * Métricas calculadas:
     * - Total de receitas do mês
     * - Total de despesas do mês
     * - Saldo (receitas - despesas)
     * - Total de faturas vencendo no mês
     * - Quantidade de faturas vencendo
     * - Percentual de economia baseado no saldo
     * 
     * @param usuarioId ID do usuário para o qual gerar o relatório
     * @param ano Ano do relatório (ex: 2024)
     * @param mes Mês do relatório (1-12)
     * @return Map com todas as métricas financeiras do mês
     */
    public Map<String, Object> gerarRelatorioMensal(Long usuarioId, int ano, int mes) {
        // Define o período do mês (do primeiro ao último dia)
        YearMonth yearMonth = YearMonth.of(ano, mes);
        LocalDateTime inicio = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime fim = yearMonth.atEndOfMonth().atTime(23, 59, 59);

        Map<String, Object> nucleo = transacaoService.resumoFinanceiroMes(usuarioId, yearMonth);
        BigDecimal totalReceitas = BigDecimal.valueOf((Double) nucleo.get("totalReceitas"));
        BigDecimal totalDespesas = BigDecimal.valueOf((Double) nucleo.get("totalDespesas"));
        BigDecimal saldo = BigDecimal.valueOf((Double) nucleo.get("saldo"));

        // Buscar faturas vencendo no mês especificado
        List<Fatura> faturasVencendo = faturaRepository.findByUsuarioIdAndDataVencimentoBetween(
                usuarioId, inicio, fim);

        // Calcular valor total das faturas vencendo
        BigDecimal totalFaturas = faturasVencendo.stream()
                .map(Fatura::getValorFatura)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Monta o relatório com todas as métricas calculadas
        Map<String, Object> relatorio = new HashMap<>();
        relatorio.put("ano", ano);
        relatorio.put("mes", mes);
        relatorio.put("totalTransacoes", nucleo.get("totalTransacoes"));
        relatorio.put("totalReceitas", totalReceitas);
        relatorio.put("totalDespesas", totalDespesas);
        relatorio.put("saldo", saldo);
        relatorio.put("totalFaturas", totalFaturas);
        relatorio.put("faturasVencendo", faturasVencendo.size());
        relatorio.put("percentualEconomia", calcularPercentualEconomia(totalReceitas, totalDespesas));

        return relatorio;
    }

    /**
     * Gera relatório financeiro anual consolidado para um usuário
     * 
     * Este método calcula e retorna um relatório consolidado do ano especificado,
     * incluindo receitas, despesas, saldo anual e percentual de economia.
     * 
     * Métricas calculadas:
     * - Total de receitas do ano
     * - Total de despesas do ano
     * - Saldo anual (receitas - despesas)
     * - Percentual de economia baseado no saldo anual
     * 
     * @param usuarioId ID do usuário para o qual gerar o relatório
     * @param ano Ano do relatório (ex: 2024)
     * @return Map com todas as métricas financeiras do ano
     */
    public Map<String, Object> gerarRelatorioAnual(Long usuarioId, int ano) {
        // Define o período do ano (do primeiro ao último dia)
        LocalDateTime inicio = LocalDateTime.of(ano, 1, 1, 0, 0);
        LocalDateTime fim = LocalDateTime.of(ano, 12, 31, 23, 59, 59);

        // Calcular receitas e despesas do ano
        BigDecimal totalReceitas = transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.RECEITA, inicio, fim);
        BigDecimal totalDespesas = transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.DESPESA, inicio, fim);
        totalReceitas = totalReceitas != null ? totalReceitas : BigDecimal.ZERO;
        totalDespesas = totalDespesas != null ? totalDespesas : BigDecimal.ZERO;

        // Calcular saldo anual (receitas - despesas)
        BigDecimal saldo = totalReceitas.subtract(totalDespesas);

        // Monta o relatório anual com todas as métricas calculadas
        Map<String, Object> relatorio = new HashMap<>();
        relatorio.put("ano", ano);
        relatorio.put("totalReceitas", totalReceitas);
        relatorio.put("totalDespesas", totalDespesas);
        relatorio.put("saldo", saldo);
        relatorio.put("percentualEconomia", calcularPercentualEconomia(totalReceitas, totalDespesas));

        return relatorio;
    }

    /**
     * Gera sistema de alertas financeiros para um usuário
     * 
     * Este método analisa a situação financeira atual do usuário e gera
     * alertas importantes sobre faturas vencendo e saúde financeira.
     * 
     * Alertas gerados:
     * - Faturas vencendo nos próximos 7 dias
     * - Faturas vencendo nos próximos 30 dias
     * - Valores totais das faturas vencendo
     * - Saldo atual do mês
     * - Alerta de saldo baixo (< R$ 1.000)
     * - Indicador de faturas vencendo em breve
     * 
     * @param usuarioId ID do usuário para o qual gerar os alertas
     * @return Map com todos os alertas e indicadores financeiros
     */
    public Map<String, Object> gerarAlertas(Long usuarioId) {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime proximos7Dias = agora.plusDays(7);
        LocalDateTime proximos30Dias = agora.plusDays(30);

        // Faturas vencendo nos próximos 7 dias (crítico)
        List<Fatura> faturasVencendo7Dias = faturaRepository.findVencidasByUsuarioId(usuarioId, proximos7Dias);
        
        // Faturas vencendo nos próximos 30 dias (atenção)
        List<Fatura> faturasVencendo30Dias = faturaRepository.findVencidasByUsuarioId(usuarioId, proximos30Dias);

        // Calcular saldo atual do mês
        LocalDateTime inicioMes = agora.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        BigDecimal receitasMes = transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.RECEITA, inicioMes, agora);
        BigDecimal despesasMes = transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.DESPESA, inicioMes, agora);
        receitasMes = receitasMes != null ? receitasMes : BigDecimal.ZERO;
        despesasMes = despesasMes != null ? despesasMes : BigDecimal.ZERO;
        BigDecimal saldoMes = receitasMes.subtract(despesasMes);
        boolean temMovimentoNoMes = receitasMes.compareTo(BigDecimal.ZERO) > 0 || despesasMes.compareTo(BigDecimal.ZERO) > 0;

        // Monta o sistema de alertas com todas as informações críticas
        Map<String, Object> alertas = new HashMap<>();
        alertas.put("faturasVencendo7Dias", faturasVencendo7Dias.size());
        alertas.put("faturasVencendo30Dias", faturasVencendo30Dias.size());
        alertas.put("totalFaturasVencendo7Dias", calcularTotalFaturas(faturasVencendo7Dias));
        alertas.put("totalFaturasVencendo30Dias", calcularTotalFaturas(faturasVencendo30Dias));
        alertas.put("saldoMes", saldoMes);
        alertas.put("saldoBaixo", temMovimentoNoMes && saldoMes.compareTo(BigDecimal.valueOf(1000)) < 0);
        alertas.put("temFaturasVencendo", !faturasVencendo7Dias.isEmpty());

        return alertas;
    }

    /**
     * Gera relatório financeiro por categoria para um usuário
     * 
     * Este método analisa as transações de um mês específico agrupadas
     * por categoria, permitindo identificar padrões de gastos.
     * 
     * Informações retornadas:
     * - Ano e mês do relatório
     * - Transações agrupadas por categoria
     * - Totais por categoria para análise de gastos
     * 
     * @param usuarioId ID do usuário para o qual gerar o relatório
     * @param ano Ano do relatório (ex: 2024)
     * @param mes Mês do relatório (1-12)
     * @return Map com relatório por categoria do período especificado
     */
    public Map<String, Object> gerarRelatorioPorCategoria(Long usuarioId, int ano, int mes) {
        // Define o período do mês para análise por categoria
        YearMonth yearMonth = YearMonth.of(ano, mes);
        LocalDateTime inicio = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime fim = yearMonth.atEndOfMonth().atTime(23, 59, 59);

        // Buscar transações agrupadas por categoria no período
        List<Object[]> transacoesPorCategoria = transacaoRepository.findConfirmadasByUsuarioIdAndPeriodoGroupByCategoria(
                usuarioId, inicio, fim);

        // Monta o relatório por categoria
        Map<String, Object> relatorio = new HashMap<>();
        relatorio.put("ano", ano);
        relatorio.put("mes", mes);
        relatorio.put("transacoesPorCategoria", transacoesPorCategoria);

        return relatorio;
    }

    public Map<String, Object> gerarDespesasPorCategoriaMesAtual(Long usuarioId) {
        YearMonth mesAtual = YearMonth.now();
        LocalDateTime inicio = mesAtual.atDay(1).atStartOfDay();
        LocalDateTime fim = mesAtual.atEndOfMonth().atTime(23, 59, 59);

        List<Object[]> resultados = transacaoRepository.findDespesasByUsuarioIdAndPeriodoGroupByCategoria(usuarioId, inicio, fim);
        List<Map<String, Object>> itens = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Object[] linha : resultados) {
            String categoria = linha[0] != null ? linha[0].toString() : "Sem categoria";
            BigDecimal valor = linha[1] instanceof BigDecimal ? (BigDecimal) linha[1] : BigDecimal.ZERO;
            total = total.add(valor);
            Map<String, Object> item = new HashMap<>();
            item.put("categoria", categoria);
            item.put("valor", valor);
            itens.add(item);
        }

        for (Map<String, Object> item : itens) {
            BigDecimal valor = (BigDecimal) item.get("valor");
            BigDecimal percentual = total.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : valor.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
            item.put("percentual", percentual);
        }

        Map<String, Object> resposta = new HashMap<>();
        resposta.put("ano", mesAtual.getYear());
        resposta.put("mes", mesAtual.getMonthValue());
        resposta.put("totalDespesas", total);
        resposta.put("itens", itens);
        return resposta;
    }

    /**
     * Calcula o percentual de economia baseado em receitas e despesas
     * 
     * Este método calcula quanto o usuário está economizando em relação
     * às suas receitas totais.
     * 
     * Fórmula: ((Receitas - Despesas) / Receitas) * 100
     * 
     * @param receitas Valor total das receitas
     * @param despesas Valor total das despesas
     * @return BigDecimal com o percentual de economia (0-100)
     */
    private BigDecimal calcularPercentualEconomia(BigDecimal receitas, BigDecimal despesas) {
        // Evita divisão por zero
        if (receitas.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // Calcula: ((receitas - despesas) / receitas) * 100
        return receitas.subtract(despesas).divide(receitas, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Calcula o valor total de uma lista de faturas
     * 
     * Método utilitário para somar os valores de todas as faturas
     * em uma lista, usado para cálculos de alertas.
     * 
     * @param faturas Lista de faturas para calcular o total
     * @return BigDecimal com o valor total das faturas
     */
    private BigDecimal calcularTotalFaturas(List<Fatura> faturas) {
        // Soma os valores de todas as faturas usando Stream API
        return faturas.stream()
                .map(Fatura::getValorFatura)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Dados agregados para PDF (e mesma lógica do CSV): despesas confirmadas do ano-calendário por categoria e CNPJ.
     */
    @Transactional(readOnly = true)
    public IrPdfDeclaracaoDados prepararDeclaracaoIrPdf(Long usuarioId, int anoCalendario) {
        LinkedHashMap<String, IrGrupoCsv> acumulado = acumularIrGrupos(usuarioId, anoCalendario);
        NumberFormat brl = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        BigDecimal total = BigDecimal.ZERO;
        List<IrPdfLinhaVm> linhas = new ArrayList<>();
        for (IrGrupoCsv g : acumulado.values()) {
            total = total.add(g.total != null ? g.total : BigDecimal.ZERO);
            linhas.add(new IrPdfLinhaVm(
                g.categoria,
                formatarCnpjExibicao(g.cnpj),
                brl.format(g.total != null ? g.total : BigDecimal.ZERO),
                g.quantidade
            ));
        }
        return new IrPdfDeclaracaoDados(linhas, total);
    }

    /**
     * Exportação IR: despesas CONFIRMADAS do ano-calendário, agregadas por categoria e CNPJ.
     * Pagina leitura no banco e escreve CSV no {@link PrintWriter} (adequado para streaming).
     */
    @Transactional(readOnly = true)
    public void escreverCsvIr(Long usuarioId, int anoCalendario, PrintWriter writer) {
        writer.write('\uFEFF');
        writer.println("categoria;cnpj;soma_valor;quantidade_transacoes");
        LinkedHashMap<String, IrGrupoCsv> acumulado = acumularIrGrupos(usuarioId, anoCalendario);
        for (IrGrupoCsv g : acumulado.values()) {
            writer.print(escaparCampoCsv(g.categoria));
            writer.print(";");
            writer.print(escaparCampoCsv(g.cnpj));
            writer.print(";");
            writer.print(g.total.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ','));
            writer.print(";");
            writer.println(g.quantidade);
        }
        writer.flush();
    }

    private LinkedHashMap<String, IrGrupoCsv> acumularIrGrupos(Long usuarioId, int anoCalendario) {
        LocalDateTime inicio = LocalDate.of(anoCalendario, 1, 1).atStartOfDay();
        LocalDateTime fim = LocalDate.of(anoCalendario, 12, 31).atTime(23, 59, 59);
        LinkedHashMap<String, IrGrupoCsv> acumulado = new LinkedHashMap<>();
        int pageIdx = 0;
        Page<Transacao> page;
        do {
            page = transacaoRepository.findPagedForIrExport(
                usuarioId,
                Transacao.TipoTransacao.DESPESA,
                inicio,
                fim,
                PageRequest.of(pageIdx++, 500)
            );
            for (Transacao t : page.getContent()) {
                String categoria = t.getCategoria() != null ? t.getCategoria().getNome() : "Sem categoria";
                String cnpj = t.getCnpj() != null ? t.getCnpj() : "";
                String key = categoria + "\0" + cnpj;
                acumulado.merge(key, new IrGrupoCsv(categoria, cnpj, t.getValor(), 1L), (ex, inc) -> {
                    ex.total = ex.total.add(inc.total != null ? inc.total : BigDecimal.ZERO);
                    ex.quantidade += inc.quantidade;
                    return ex;
                });
            }
        } while (page.hasNext());
        return acumulado;
    }

    private static String formatarCnpjExibicao(String cnpj) {
        if (cnpj == null || cnpj.isBlank()) {
            return "—";
        }
        String d = cnpj.replaceAll("\\D", "");
        if (d.length() != 14) {
            return cnpj.trim();
        }
        return d.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
    }

    private static String escaparCampoCsv(String valor) {
        if (valor == null) {
            return "";
        }
        String v = valor.replace("\"", "\"\"");
        if (v.contains(";") || v.contains("\n") || v.contains("\"")) {
            return "\"" + v + "\"";
        }
        return v;
    }

    private static final class IrGrupoCsv {
        private final String categoria;
        private final String cnpj;
        private BigDecimal total;
        private long quantidade;

        private IrGrupoCsv(String categoria, String cnpj, BigDecimal total, long quantidade) {
            this.categoria = categoria;
            this.cnpj = cnpj;
            this.total = total != null ? total : BigDecimal.ZERO;
            this.quantidade = quantidade;
        }

    }
}
