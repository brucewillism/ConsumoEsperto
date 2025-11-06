package com.consumoesperto.service;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.CartaoCreditoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Serviço para gerar relatórios PDF
 * 
 * Este serviço gera relatórios em PDF com dados financeiros do usuário.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RelatorioPdfService {

    private final TransacaoRepository transacaoRepository;
    private final FaturaRepository faturaRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;

    /**
     * Gera relatório financeiro completo em PDF
     */
    public byte[] gerarRelatorioFinanceiroCompleto(Long usuarioId, LocalDate dataInicio, LocalDate dataFim) {
        try {
            log.info("📊 Gerando relatório financeiro completo para usuário: {} ({} a {})", 
                    usuarioId, dataInicio, dataFim);
            
            // Buscar dados do usuário
            Usuario usuario = buscarUsuario(usuarioId);
            if (usuario == null) {
                throw new RuntimeException("Usuário não encontrado: " + usuarioId);
            }
            
            // Buscar transações do período
            List<Transacao> transacoes = transacaoRepository
                .findByUsuarioIdAndDataTransacaoBetween(usuarioId, dataInicio.atStartOfDay(), dataFim.atTime(23, 59, 59));
            
            // Buscar faturas do período
            List<Fatura> faturas = faturaRepository
                .findByUsuarioIdAndDataVencimentoBetween(usuarioId, 
                    dataInicio.atStartOfDay(), 
                    dataFim.atTime(23, 59, 59));
            
            // Buscar cartões do usuário
            List<CartaoCredito> cartoes = cartaoCreditoRepository.findByUsuarioId(usuarioId);
            
            // Calcular estatísticas
            Map<String, Object> estatisticas = calcularEstatisticas(transacoes, faturas, cartoes);
            
            // Gerar PDF
            return gerarPdfRelatorio(usuario, transacoes, faturas, cartoes, estatisticas, dataInicio, dataFim);
            
        } catch (Exception e) {
            log.error("❌ Erro ao gerar relatório financeiro: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao gerar relatório: " + e.getMessage());
        }
    }
    
    /**
     * Gera relatório de transações em PDF
     */
    public byte[] gerarRelatorioTransacoes(Long usuarioId, LocalDate dataInicio, LocalDate dataFim) {
        try {
            log.info("💳 Gerando relatório de transações para usuário: {} ({} a {})", 
                    usuarioId, dataInicio, dataFim);
            
            Usuario usuario = buscarUsuario(usuarioId);
            if (usuario == null) {
                throw new RuntimeException("Usuário não encontrado: " + usuarioId);
            }
            
            List<Transacao> transacoes = transacaoRepository
                .findByUsuarioIdAndDataTransacaoBetween(usuarioId, dataInicio.atStartOfDay(), dataFim.atTime(23, 59, 59));
            
            return gerarPdfTransacoes(usuario, transacoes, dataInicio, dataFim);
            
        } catch (Exception e) {
            log.error("❌ Erro ao gerar relatório de transações: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao gerar relatório: " + e.getMessage());
        }
    }
    
    /**
     * Gera relatório de faturas em PDF
     */
    public byte[] gerarRelatorioFaturas(Long usuarioId, LocalDate dataInicio, LocalDate dataFim) {
        try {
            log.info("📄 Gerando relatório de faturas para usuário: {} ({} a {})", 
                    usuarioId, dataInicio, dataFim);
            
            Usuario usuario = buscarUsuario(usuarioId);
            if (usuario == null) {
                throw new RuntimeException("Usuário não encontrado: " + usuarioId);
            }
            
            List<Fatura> faturas = faturaRepository
                .findByUsuarioIdAndDataVencimentoBetween(usuarioId, 
                    dataInicio.atStartOfDay(), 
                    dataFim.atTime(23, 59, 59));
            
            return gerarPdfFaturas(usuario, faturas, dataInicio, dataFim);
            
        } catch (Exception e) {
            log.error("❌ Erro ao gerar relatório de faturas: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao gerar relatório: " + e.getMessage());
        }
    }
    
    /**
     * Busca usuário por ID
     */
    private Usuario buscarUsuario(Long usuarioId) {
        // Implementar busca do usuário
        // TODO: Implementar busca do usuário do banco de dados
        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        usuario.setNome("Bruce Willis");
        usuario.setEmail("bruce.willis.br07@gmail.com");
        return usuario;
    }
    
    /**
     * Calcula estatísticas dos dados
     */
    private Map<String, Object> calcularEstatisticas(List<Transacao> transacoes, 
                                                   List<Fatura> faturas, 
                                                   List<CartaoCredito> cartoes) {
        Map<String, Object> stats = new HashMap<>();
        
        // Estatísticas de transações
        BigDecimal totalReceitas = transacoes.stream()
            .filter(t -> "RECEITA".equals(t.getTipoTransacao()))
            .map(Transacao::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalDespesas = transacoes.stream()
            .filter(t -> "DESPESA".equals(t.getTipoTransacao()))
            .map(Transacao::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal saldoPeriodo = totalReceitas.subtract(totalDespesas);
        
        // Estatísticas de faturas
        BigDecimal totalFaturas = faturas.stream()
            .map(Fatura::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalFaturasPagas = faturas.stream()
            .filter(Fatura::isPaga)
            .map(Fatura::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalFaturasPendentes = totalFaturas.subtract(totalFaturasPagas);
        
        // Estatísticas de cartões
        BigDecimal limiteTotal = cartoes.stream()
            .map(CartaoCredito::getLimiteTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal limiteUtilizado = cartoes.stream()
            .map(CartaoCredito::getLimiteUtilizado)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal limiteDisponivel = limiteTotal.subtract(limiteUtilizado);
        
        stats.put("total_receitas", totalReceitas);
        stats.put("total_despesas", totalDespesas);
        stats.put("saldo_periodo", saldoPeriodo);
        stats.put("total_faturas", totalFaturas);
        stats.put("total_faturas_pagas", totalFaturasPagas);
        stats.put("total_faturas_pendentes", totalFaturasPendentes);
        stats.put("limite_total", limiteTotal);
        stats.put("limite_utilizado", limiteUtilizado);
        stats.put("limite_disponivel", limiteDisponivel);
        stats.put("quantidade_transacoes", transacoes.size());
        stats.put("quantidade_faturas", faturas.size());
        stats.put("quantidade_cartoes", cartoes.size());
        
        return stats;
    }
    
    /**
     * Gera PDF do relatório completo
     */
    private byte[] gerarPdfRelatorio(Usuario usuario, List<Transacao> transacoes, 
                                   List<Fatura> faturas, List<CartaoCredito> cartoes,
                                   Map<String, Object> estatisticas, LocalDate dataInicio, LocalDate dataFim) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // Usar biblioteca de PDF (exemplo com iText)
            // Document document = new Document(PageSize.A4);
            // PdfWriter.getInstance(document, baos);
            // document.open();
            
            // Por enquanto, gerar um PDF simples com texto
            String conteudo = gerarConteudoRelatorioCompleto(usuario, transacoes, faturas, cartoes, estatisticas, dataInicio, dataFim);
            
            // Simular geração de PDF (em produção, usar biblioteca real)
            baos.write(conteudo.getBytes("UTF-8"));
            
            log.info("✅ Relatório PDF gerado com sucesso - {} bytes", baos.size());
            return baos.toByteArray();
            
        } catch (IOException e) {
            log.error("❌ Erro ao gerar PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao gerar PDF: " + e.getMessage());
        }
    }
    
    /**
     * Gera PDF de transações
     */
    private byte[] gerarPdfTransacoes(Usuario usuario, List<Transacao> transacoes, 
                                    LocalDate dataInicio, LocalDate dataFim) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            String conteudo = gerarConteudoRelatorioTransacoes(usuario, transacoes, dataInicio, dataFim);
            baos.write(conteudo.getBytes("UTF-8"));
            
            log.info("✅ Relatório de transações PDF gerado com sucesso - {} bytes", baos.size());
            return baos.toByteArray();
            
        } catch (IOException e) {
            log.error("❌ Erro ao gerar PDF de transações: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao gerar PDF: " + e.getMessage());
        }
    }
    
    /**
     * Gera PDF de faturas
     */
    private byte[] gerarPdfFaturas(Usuario usuario, List<Fatura> faturas, 
                                 LocalDate dataInicio, LocalDate dataFim) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            String conteudo = gerarConteudoRelatorioFaturas(usuario, faturas, dataInicio, dataFim);
            baos.write(conteudo.getBytes("UTF-8"));
            
            log.info("✅ Relatório de faturas PDF gerado com sucesso - {} bytes", baos.size());
            return baos.toByteArray();
            
        } catch (IOException e) {
            log.error("❌ Erro ao gerar PDF de faturas: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao gerar PDF: " + e.getMessage());
        }
    }
    
    /**
     * Gera conteúdo do relatório completo
     */
    private String gerarConteudoRelatorioCompleto(Usuario usuario, List<Transacao> transacoes,
                                                 List<Fatura> faturas, List<CartaoCredito> cartoes,
                                                 Map<String, Object> estatisticas, LocalDate dataInicio, LocalDate dataFim) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("RELATÓRIO FINANCEIRO COMPLETO\n");
        sb.append("==============================\n\n");
        
        sb.append("Usuário: ").append(usuario.getNome()).append("\n");
        sb.append("Email: ").append(usuario.getEmail()).append("\n");
        sb.append("Período: ").append(dataInicio).append(" a ").append(dataFim).append("\n");
        sb.append("Data de geração: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("\n\n");
        
        sb.append("RESUMO FINANCEIRO\n");
        sb.append("==================\n");
        sb.append("Total de Receitas: R$ ").append(estatisticas.get("total_receitas")).append("\n");
        sb.append("Total de Despesas: R$ ").append(estatisticas.get("total_despesas")).append("\n");
        sb.append("Saldo do Período: R$ ").append(estatisticas.get("saldo_periodo")).append("\n\n");
        
        sb.append("FATURAS\n");
        sb.append("=======\n");
        sb.append("Total de Faturas: R$ ").append(estatisticas.get("total_faturas")).append("\n");
        sb.append("Faturas Pagas: R$ ").append(estatisticas.get("total_faturas_pagas")).append("\n");
        sb.append("Faturas Pendentes: R$ ").append(estatisticas.get("total_faturas_pendentes")).append("\n\n");
        
        sb.append("CARTÕES DE CRÉDITO\n");
        sb.append("==================\n");
        sb.append("Limite Total: R$ ").append(estatisticas.get("limite_total")).append("\n");
        sb.append("Limite Utilizado: R$ ").append(estatisticas.get("limite_utilizado")).append("\n");
        sb.append("Limite Disponível: R$ ").append(estatisticas.get("limite_disponivel")).append("\n\n");
        
        sb.append("TRANSAÇÕES DETALHADAS\n");
        sb.append("=====================\n");
        for (Transacao transacao : transacoes) {
            sb.append(transacao.getDataTransacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            sb.append(" - ").append(transacao.getDescricao());
            sb.append(" - ").append(transacao.getTipoTransacao());
            sb.append(" - R$ ").append(transacao.getValor()).append("\n");
        }
        
        sb.append("\nFATURAS DETALHADAS\n");
        sb.append("==================\n");
        for (Fatura fatura : faturas) {
            sb.append("Vencimento: ").append(fatura.getDataVencimento());
            sb.append(" - Valor: R$ ").append(fatura.getValor());
            sb.append(" - Status: ").append(fatura.isPaga() ? "PAGA" : "PENDENTE").append("\n");
        }
        
        sb.append("\nCARTÕES DE CRÉDITO\n");
        sb.append("==================\n");
        for (CartaoCredito cartao : cartoes) {
            sb.append(cartao.getNome());
            sb.append(" - Limite: R$ ").append(cartao.getLimiteTotal());
            sb.append(" - Utilizado: R$ ").append(cartao.getLimiteUtilizado()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Gera conteúdo do relatório de transações
     */
    private String gerarConteudoRelatorioTransacoes(Usuario usuario, List<Transacao> transacoes, 
                                                  LocalDate dataInicio, LocalDate dataFim) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("RELATÓRIO DE TRANSAÇÕES\n");
        sb.append("=======================\n\n");
        
        sb.append("Usuário: ").append(usuario.getNome()).append("\n");
        sb.append("Email: ").append(usuario.getEmail()).append("\n");
        sb.append("Período: ").append(dataInicio).append(" a ").append(dataFim).append("\n");
        sb.append("Data de geração: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("\n\n");
        
        sb.append("TRANSAÇÕES DETALHADAS\n");
        sb.append("=====================\n");
        
        for (Transacao transacao : transacoes) {
            sb.append(transacao.getDataTransacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            sb.append(" - ").append(transacao.getDescricao());
            sb.append(" - ").append(transacao.getTipoTransacao());
            sb.append(" - R$ ").append(transacao.getValor()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Gera conteúdo do relatório de faturas
     */
    private String gerarConteudoRelatorioFaturas(Usuario usuario, List<Fatura> faturas, 
                                               LocalDate dataInicio, LocalDate dataFim) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("RELATÓRIO DE FATURAS\n");
        sb.append("====================\n\n");
        
        sb.append("Usuário: ").append(usuario.getNome()).append("\n");
        sb.append("Email: ").append(usuario.getEmail()).append("\n");
        sb.append("Período: ").append(dataInicio).append(" a ").append(dataFim).append("\n");
        sb.append("Data de geração: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("\n\n");
        
        sb.append("FATURAS DETALHADAS\n");
        sb.append("==================\n");
        
        for (Fatura fatura : faturas) {
            sb.append("Vencimento: ").append(fatura.getDataVencimento());
            sb.append(" - Valor: R$ ").append(fatura.getValor());
            sb.append(" - Status: ").append(fatura.isPaga() ? "PAGA" : "PENDENTE").append("\n");
        }
        
        return sb.toString();
    }
}
