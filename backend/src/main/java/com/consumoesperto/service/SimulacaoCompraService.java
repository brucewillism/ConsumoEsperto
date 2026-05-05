package com.consumoesperto.service;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviço responsável por simulações de compras e planejamento financeiro
 * 
 * Este serviço implementa a lógica de negócio para simular compras parceladas
 * e à vista, analisando a viabilidade financeira baseada no histórico do usuário.
 * Também fornece ferramentas para planejamento de economia e metas financeiras.
 * 
 * Funcionalidades principais:
 * - Simulação de compras parceladas com análise de viabilidade
 * - Simulação de compras à vista
 * - Cálculo de economia necessária para metas
 * - Análise de impacto no orçamento mensal
 * - Recomendações personalizadas baseadas no perfil financeiro
 * - Planejamento de economia para objetivos específicos
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor // Lombok: gera construtor com campos final automaticamente
@Slf4j // Lombok: fornece logger automático para a classe
public class SimulacaoCompraService {

    // Repositório para consulta de transações para análise financeira
    private final TransacaoRepository transacaoRepository;

    /**
     * Simula uma compra parcelada analisando a viabilidade financeira
     * 
     * Este método analisa o histórico financeiro dos últimos 6 meses para
     * determinar se uma compra parcelada é viável para o usuário.
     * 
     * Análises realizadas:
     * - Média de receitas e despesas dos últimos 6 meses
     * - Cálculo da economia mensal média
     * - Verificação se o valor da parcela é suportável
     * - Cálculo do tempo necessário para economizar o valor total
     * - Análise do impacto percentual no orçamento mensal
     * - Geração de recomendação personalizada
     * 
     * @param usuarioId ID do usuário para análise financeira
     * @param valorCompra Valor total da compra a ser simulada
     * @param numeroParcelas Número de parcelas desejadas
     * @return Map com resultado da simulação e recomendações
     */
    public Map<String, Object> simularCompra(Long usuarioId, BigDecimal valorCompra, int numeroParcelas) {
        // Calcular média de receitas e despesas dos últimos 6 meses para análise de estabilidade
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime seisMesesAtras = agora.minusMonths(6);
        
        // Buscar total de receitas e despesas do período de análise
        BigDecimal totalReceitas = transacaoRepository.sumByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.RECEITA, seisMesesAtras, agora);
        BigDecimal totalDespesas = transacaoRepository.sumByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.DESPESA, seisMesesAtras, agora);
        
        // Calcular médias mensais para análise de capacidade de pagamento
        BigDecimal mediaReceitasMensal = totalReceitas.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
        BigDecimal mediaDespesasMensal = totalDespesas.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
        BigDecimal mediaEconomiaMensal = mediaReceitasMensal.subtract(mediaDespesasMensal);
        
        // Calcular valor da parcela para análise de impacto mensal
        BigDecimal valorParcela = valorCompra.divide(BigDecimal.valueOf(numeroParcelas), 2, RoundingMode.HALF_UP);
        
        // Verificar viabilidade: parcela deve ser menor ou igual à economia mensal
        boolean compraViavel = mediaEconomiaMensal.compareTo(valorParcela) >= 0;
        
        // Calcular tempo necessário para economizar o valor total da compra
        int mesesParaEconomizar = 0;
        if (mediaEconomiaMensal.compareTo(BigDecimal.ZERO) > 0) {
            mesesParaEconomizar = valorCompra.divide(mediaEconomiaMensal, 0, RoundingMode.CEILING).intValue();
        }
        
        // Calcular impacto percentual da parcela no orçamento mensal
        BigDecimal impactoPercentual = BigDecimal.ZERO;
        if (mediaReceitasMensal.compareTo(BigDecimal.ZERO) > 0) {
            impactoPercentual = valorParcela.divide(mediaReceitasMensal, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        
        // Monta o resultado da simulação com todas as análises e recomendações
        Map<String, Object> simulacao = new HashMap<>();
        simulacao.put("valorCompra", valorCompra);
        simulacao.put("numeroParcelas", numeroParcelas);
        simulacao.put("valorParcela", valorParcela);
        simulacao.put("mediaReceitasMensal", mediaReceitasMensal);
        simulacao.put("mediaDespesasMensal", mediaDespesasMensal);
        simulacao.put("mediaEconomiaMensal", mediaEconomiaMensal);
        simulacao.put("compraViavel", compraViavel);
        simulacao.put("mesesParaEconomizar", mesesParaEconomizar);
        simulacao.put("impactoPercentual", impactoPercentual);
        simulacao.put("recomendacao", gerarRecomendacao(compraViavel, impactoPercentual, mesesParaEconomizar));
        
        return simulacao;
    }

    /**
     * Simula uma compra à vista analisando a viabilidade financeira
     * 
     * Este método analisa o histórico financeiro dos últimos 6 meses para
     * determinar se uma compra à vista é viável para o usuário.
     * 
     * Análises realizadas:
     * - Média de receitas e despesas dos últimos 6 meses
     * - Cálculo da economia mensal média
     * - Verificação se o valor total é suportável
     * - Cálculo do tempo necessário para economizar o valor
     * - Análise do impacto percentual no orçamento mensal
     * - Geração de recomendação personalizada
     * 
     * @param usuarioId ID do usuário para análise financeira
     * @param valorCompra Valor total da compra à vista
     * @return Map com resultado da simulação e recomendações
     */
    public Map<String, Object> simularCompraAVista(Long usuarioId, BigDecimal valorCompra) {
        // Calcular média de receitas e despesas dos últimos 6 meses para análise de estabilidade
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime seisMesesAtras = agora.minusMonths(6);
        
        // Buscar total de receitas e despesas do período de análise
        BigDecimal totalReceitas = transacaoRepository.sumByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.RECEITA, seisMesesAtras, agora);
        BigDecimal totalDespesas = transacaoRepository.sumByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.DESPESA, seisMesesAtras, agora);
        
        // Calcular médias mensais para análise de capacidade de compra
        BigDecimal mediaReceitasMensal = totalReceitas.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
        BigDecimal mediaDespesasMensal = totalDespesas.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
        BigDecimal mediaEconomiaMensal = mediaReceitasMensal.subtract(mediaDespesasMensal);
        
        // Verificar viabilidade: valor deve ser menor ou igual à economia mensal
        boolean compraViavel = mediaEconomiaMensal.compareTo(valorCompra) >= 0;
        
        // Calcular tempo necessário para economizar o valor total da compra
        int mesesParaEconomizar = 0;
        if (mediaEconomiaMensal.compareTo(BigDecimal.ZERO) > 0) {
            mesesParaEconomizar = valorCompra.divide(mediaEconomiaMensal, 0, RoundingMode.CEILING).intValue();
        }
        
        // Calcular impacto percentual do valor total no orçamento mensal
        BigDecimal impactoPercentual = BigDecimal.ZERO;
        if (mediaReceitasMensal.compareTo(BigDecimal.ZERO) > 0) {
            impactoPercentual = valorCompra.divide(mediaReceitasMensal, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        
        // Monta o resultado da simulação com todas as análises e recomendações
        Map<String, Object> simulacao = new HashMap<>();
        simulacao.put("valorCompra", valorCompra);
        simulacao.put("mediaReceitasMensal", mediaReceitasMensal);
        simulacao.put("mediaDespesasMensal", mediaDespesasMensal);
        simulacao.put("mediaEconomiaMensal", mediaEconomiaMensal);
        simulacao.put("compraViavel", compraViavel);
        simulacao.put("mesesParaEconomizar", mesesParaEconomizar);
        simulacao.put("impactoPercentual", impactoPercentual);
        simulacao.put("recomendacao", gerarRecomendacao(compraViavel, impactoPercentual, mesesParaEconomizar));
        
        return simulacao;
    }

    /**
     * Calcula a economia necessária para atingir uma meta financeira
     * 
     * Este método analisa o histórico financeiro e calcula quanto o usuário
     * precisa economizar por mês para atingir um objetivo em um prazo específico.
     * 
     * Análises realizadas:
     * - Média de receitas e despesas dos últimos 6 meses
     * - Cálculo da economia mensal média atual
     * - Definição da economia necessária para a meta
     * - Cálculo da economia adicional necessária
     * - Verificação da viabilidade da meta
     * - Geração de recomendação personalizada
     * 
     * @param usuarioId ID do usuário para análise financeira
     * @param valorCompra Valor da meta financeira a ser atingida
     * @param mesesDesejados Prazo desejado para atingir a meta (em meses)
     * @return Map com resultado do cálculo e recomendações
     */
    public Map<String, Object> calcularEconomiaNecessaria(Long usuarioId, BigDecimal valorCompra, int mesesDesejados) {
        // Calcular média de receitas e despesas dos últimos 6 meses para análise de estabilidade
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime seisMesesAtras = agora.minusMonths(6);
        
        // Buscar total de receitas e despesas do período de análise
        BigDecimal totalReceitas = transacaoRepository.sumByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.RECEITA, seisMesesAtras, agora);
        BigDecimal totalDespesas = transacaoRepository.sumByUsuarioIdAndTipoAndPeriodo(
                usuarioId, Transacao.TipoTransacao.DESPESA, seisMesesAtras, agora);
        
        // Calcular médias mensais para análise de capacidade de economia
        BigDecimal mediaReceitasMensal = totalReceitas.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
        BigDecimal mediaDespesasMensal = totalDespesas.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
        BigDecimal mediaEconomiaMensal = mediaReceitasMensal.subtract(mediaDespesasMensal);
        
        // Calcular economia necessária por mês para atingir a meta no prazo desejado
        BigDecimal economiaNecessaria = valorCompra.divide(BigDecimal.valueOf(mesesDesejados), 2, RoundingMode.HALF_UP);
        
        // Calcular economia adicional necessária além da atual
        BigDecimal economiaAdicional = economiaNecessaria.subtract(mediaEconomiaMensal);
        
        // Verificar viabilidade: meta é viável se economia adicional <= 0
        boolean metaViavel = economiaAdicional.compareTo(BigDecimal.ZERO) <= 0;
        
        // Monta o resultado do cálculo com todas as análises e recomendações
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("valorCompra", valorCompra);
        resultado.put("mesesDesejados", mesesDesejados);
        resultado.put("economiaNecessaria", economiaNecessaria);
        resultado.put("mediaEconomiaMensal", mediaEconomiaMensal);
        resultado.put("economiaAdicional", economiaAdicional);
        resultado.put("metaViavel", metaViavel);
        resultado.put("recomendacao", gerarRecomendacaoEconomia(metaViavel, economiaAdicional, mesesDesejados));
        
        return resultado;
    }

    /**
     * Gera recomendação personalizada para compras baseada na análise financeira
     * 
     * Este método analisa os resultados da simulação e gera uma recomendação
     * personalizada considerando viabilidade, impacto no orçamento e tempo de economia.
     * 
     * Critérios de recomendação:
     * - Viabilidade da compra (parcela suportável)
     * - Impacto no orçamento (≤ 30% = baixo impacto)
     * - Tempo para economizar (≤ 3 meses = recomendado)
     * 
     * @param compraViavel Indica se a compra é financeiramente viável
     * @param impactoPercentual Impacto percentual da compra no orçamento mensal
     * @param mesesParaEconomizar Tempo necessário para economizar o valor total
     * @return String com recomendação personalizada para o usuário
     */
    private String gerarRecomendacao(boolean compraViavel, BigDecimal impactoPercentual, int mesesParaEconomizar) {
        // Compra não viável: parcela excede capacidade de pagamento
        if (!compraViavel) {
            return "Compra não recomendada. O valor da parcela excede sua capacidade de pagamento mensal.";
        }
        
        // Compra de alto impacto: mais de 30% do orçamento mensal
        if (impactoPercentual.compareTo(BigDecimal.valueOf(30)) > 0) {
            return "Compra de alto impacto no orçamento. Considere economizar por mais tempo.";
        }
        
        // Compra viável com tempo de economia adequado (até 3 meses)
        if (mesesParaEconomizar <= 3) {
            return "Compra viável. Recomendado economizar por " + mesesParaEconomizar + " meses antes da compra.";
        }
        
        // Compra viável mas com tempo de economia maior
        return "Compra viável, mas considere economizar por mais tempo para reduzir o impacto no orçamento.";
    }

    /**
     * Gera recomendação personalizada para metas de economia
     * 
     * Este método analisa os resultados do cálculo de economia necessária e
     * gera uma recomendação personalizada considerando a viabilidade da meta.
     * 
     * Critérios de recomendação:
     * - Meta viável: economia adicional ≤ 0 (usuário já economiza o suficiente)
     * - Meta desafiadora: economia adicional > 0 (usuário precisa economizar mais)
     * 
     * @param metaViavel Indica se a meta é financeiramente viável
     * @param economiaAdicional Economia adicional necessária para atingir a meta
     * @param mesesDesejados Prazo desejado para atingir a meta
     * @return String com recomendação personalizada para o usuário
     */
    private String gerarRecomendacaoEconomia(boolean metaViavel, BigDecimal economiaAdicional, int mesesDesejados) {
        // Meta viável: usuário já economiza o suficiente ou mais
        if (metaViavel) {
            return "Meta viável! Você pode economizar R$ " + economiaAdicional.abs() + " a mais por mês para atingir sua meta em " + mesesDesejados + " meses.";
        } else {
            // Meta desafiadora: usuário precisa economizar mais do que atualmente
            return "Meta desafiadora. Você precisaria economizar R$ " + economiaAdicional + " a mais por mês. Considere aumentar o prazo ou reduzir o valor.";
        }
    }

    public Map<String, Object> simularInvestimento(BigDecimal valorInicial, BigDecimal aporteMensal, BigDecimal taxaRetornoAnual, int periodoAnos) {
        BigDecimal taxaMensal = BigDecimal.valueOf(Math.pow(
            BigDecimal.ONE.add(taxaRetornoAnual.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)).doubleValue(),
            1.0 / 12.0
        ) - 1.0);

        BigDecimal valorFinal = valorInicial;
        List<Map<String, Object>> projecaoAnual = new ArrayList<>();

        for (int ano = 1; ano <= periodoAnos; ano++) {
            BigDecimal saldoInicial = valorFinal;
            BigDecimal aportesNoAno = aporteMensal.multiply(BigDecimal.valueOf(12));
            for (int mes = 0; mes < 12; mes++) {
                valorFinal = valorFinal.add(aporteMensal).multiply(BigDecimal.ONE.add(taxaMensal));
            }
            BigDecimal jurosAno = valorFinal.subtract(saldoInicial).subtract(aportesNoAno);
            Map<String, Object> linha = new HashMap<>();
            linha.put("ano", ano);
            linha.put("saldoInicial", saldoInicial.setScale(2, RoundingMode.HALF_UP));
            linha.put("aportes", aportesNoAno.setScale(2, RoundingMode.HALF_UP));
            linha.put("juros", jurosAno.setScale(2, RoundingMode.HALF_UP));
            linha.put("saldoFinal", valorFinal.setScale(2, RoundingMode.HALF_UP));
            projecaoAnual.add(linha);
        }

        BigDecimal totalAportes = valorInicial.add(aporteMensal.multiply(BigDecimal.valueOf(periodoAnos * 12L)));
        BigDecimal jurosCompostos = valorFinal.subtract(totalAportes);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("valorInicial", valorInicial.setScale(2, RoundingMode.HALF_UP));
        resultado.put("totalAportes", totalAportes.setScale(2, RoundingMode.HALF_UP));
        resultado.put("jurosCompostos", jurosCompostos.setScale(2, RoundingMode.HALF_UP));
        resultado.put("valorFinal", valorFinal.setScale(2, RoundingMode.HALF_UP));
        resultado.put("projecaoAnual", projecaoAnual);
        return resultado;
    }

    public Map<String, Object> simularFinanciamento(BigDecimal valorBem, BigDecimal entrada, BigDecimal taxaJurosMensal, int prazoMeses) {
        BigDecimal valorFinanciado = valorBem.subtract(entrada);
        BigDecimal i = taxaJurosMensal.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

        BigDecimal valorParcela;
        if (i.compareTo(BigDecimal.ZERO) == 0) {
            valorParcela = valorFinanciado.divide(BigDecimal.valueOf(prazoMeses), 2, RoundingMode.HALF_UP);
        } else {
            double fator = Math.pow(BigDecimal.ONE.add(i).doubleValue(), prazoMeses);
            double parcela = valorFinanciado.doubleValue() * ((i.doubleValue() * fator) / (fator - 1));
            valorParcela = BigDecimal.valueOf(parcela).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal totalPagar = valorParcela.multiply(BigDecimal.valueOf(prazoMeses)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalJuros = totalPagar.subtract(valorFinanciado).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("valorBem", valorBem.setScale(2, RoundingMode.HALF_UP));
        resultado.put("entrada", entrada.setScale(2, RoundingMode.HALF_UP));
        resultado.put("valorFinanciado", valorFinanciado.setScale(2, RoundingMode.HALF_UP));
        resultado.put("valorParcela", valorParcela);
        resultado.put("totalPagar", totalPagar);
        resultado.put("totalJuros", totalJuros);
        return resultado;
    }
}
