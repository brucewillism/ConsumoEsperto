package com.consumoesperto.service;

import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        // Calcular receitas e despesas do período
        BigDecimal totalReceitas = transacaoRepository.sumByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.RECEITA, inicio, fim);
        BigDecimal totalDespesas = transacaoRepository.sumByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.DESPESA, inicio, fim);

        // Calcular saldo mensal (receitas - despesas)
        BigDecimal saldo = totalReceitas.subtract(totalDespesas);

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
        BigDecimal totalReceitas = transacaoRepository.sumByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.RECEITA, inicio, fim);
        BigDecimal totalDespesas = transacaoRepository.sumByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.DESPESA, inicio, fim);

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
        BigDecimal receitasMes = transacaoRepository.sumByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.RECEITA, inicioMes, agora);
        BigDecimal despesasMes = transacaoRepository.sumByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.DESPESA, inicioMes, agora);
        BigDecimal saldoMes = receitasMes.subtract(despesasMes);

        // Monta o sistema de alertas com todas as informações críticas
        Map<String, Object> alertas = new HashMap<>();
        alertas.put("faturasVencendo7Dias", faturasVencendo7Dias.size());
        alertas.put("faturasVencendo30Dias", faturasVencendo30Dias.size());
        alertas.put("totalFaturasVencendo7Dias", calcularTotalFaturas(faturasVencendo7Dias));
        alertas.put("totalFaturasVencendo30Dias", calcularTotalFaturas(faturasVencendo30Dias));
        alertas.put("saldoMes", saldoMes);
        alertas.put("saldoBaixo", saldoMes.compareTo(BigDecimal.valueOf(1000)) < 0);
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
        List<Object[]> transacoesPorCategoria = transacaoRepository.findByUsuarioIdAndPeriodoGroupByCategoria(
                usuarioId, inicio, fim);

        // Monta o relatório por categoria
        Map<String, Object> relatorio = new HashMap<>();
        relatorio.put("ano", ano);
        relatorio.put("mes", mes);
        relatorio.put("transacoesPorCategoria", transacoesPorCategoria);

        return relatorio;
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
        return receitas.subtract(despesas).divide(receitas, 4, BigDecimal.ROUND_HALF_UP)
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
}
