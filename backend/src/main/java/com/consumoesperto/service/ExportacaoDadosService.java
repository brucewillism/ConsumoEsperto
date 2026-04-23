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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Serviço para exportação de dados
 * 
 * Este serviço permite exportar dados financeiros em diferentes formatos
 * como CSV, JSON, XML, etc.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportacaoDadosService {

    private final TransacaoRepository transacaoRepository;
    private final FaturaRepository faturaRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;

    /**
     * Exporta dados completos em CSV
     */
    public byte[] exportarDadosCompletosCsv(Long usuarioId, LocalDate dataInicio, LocalDate dataFim) {
        try {
            log.info("📤 Exportando dados completos em CSV para usuário: {} ({} a {})", 
                    usuarioId, dataInicio, dataFim);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Writer writer = new OutputStreamWriter(baos, "UTF-8");
            
            // Buscar dados
            Usuario usuario = buscarUsuario(usuarioId);
            List<Transacao> transacoes = transacaoRepository
                .findByUsuarioIdAndDataTransacaoBetween(usuarioId, dataInicio.atStartOfDay(), dataFim.atTime(23, 59, 59));
            List<Fatura> faturas = faturaRepository
                .findByUsuarioIdAndDataVencimentoBetween(usuarioId, 
                    dataInicio.atStartOfDay(), 
                    dataFim.atTime(23, 59, 59));
            List<CartaoCredito> cartoes = cartaoCreditoRepository.findByUsuarioId(usuarioId);
            
            // Escrever cabeçalho
            writer.write("DADOS FINANCEIROS - CONSUMO ESPERTO\n");
            writer.write("Usuário: " + usuario.getNome() + "\n");
            writer.write("Email: " + usuario.getEmail() + "\n");
            writer.write("Período: " + dataInicio + " a " + dataFim + "\n");
            writer.write("Data de exportação: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + "\n\n");
            
            // Escrever transações
            writer.write("TRANSAÇÕES\n");
            writer.write("Data,Descrição,Tipo,Valor,Categoria\n");
            for (Transacao transacao : transacoes) {
                writer.write(transacao.getDataTransacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ",");
                writer.write("\"" + transacao.getDescricao() + "\",");
                writer.write(transacao.getTipoTransacao() + ",");
                writer.write(transacao.getValor().toString() + ",");
                writer.write(transacao.getCategoria() != null ? transacao.getCategoria().getNome() : "Sem categoria");
                writer.write("\n");
            }
            
            writer.write("\nFATURAS\n");
            writer.write("Vencimento,Valor,Status,Paga\n");
            for (Fatura fatura : faturas) {
                writer.write(fatura.getDataVencimento().toString() + ",");
                writer.write(fatura.getValor().toString() + ",");
                writer.write(fatura.isPaga() ? "PAGA" : "PENDENTE" + ",");
                writer.write(fatura.isPaga() ? "Sim" : "Não");
                writer.write("\n");
            }
            
            writer.write("\nCARTÕES DE CRÉDITO\n");
            writer.write("Nome,Limite Total,Limite Utilizado,Limite Disponível,Bandeira\n");
            for (CartaoCredito cartao : cartoes) {
                writer.write("\"" + cartao.getNome() + "\",");
                writer.write(cartao.getLimiteTotal().toString() + ",");
                writer.write(cartao.getLimiteUtilizado().toString() + ",");
                writer.write(cartao.getLimiteDisponivel().toString() + ",");
                writer.write(cartao.getBandeira());
                writer.write("\n");
            }
            
            writer.close();
            
            log.info("✅ Dados exportados em CSV com sucesso - {} bytes", baos.size());
            return baos.toByteArray();
            
        } catch (IOException e) {
            log.error("❌ Erro ao exportar dados em CSV: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao exportar dados: " + e.getMessage());
        }
    }

    /**
     * Exporta transações em CSV
     */
    public byte[] exportarTransacoesCsv(Long usuarioId, LocalDate dataInicio, LocalDate dataFim) {
        try {
            log.info("💳 Exportando transações em CSV para usuário: {} ({} a {})", 
                    usuarioId, dataInicio, dataFim);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Writer writer = new OutputStreamWriter(baos, "UTF-8");
            
            List<Transacao> transacoes = transacaoRepository
                .findByUsuarioIdAndDataTransacaoBetween(usuarioId, dataInicio.atStartOfDay(), dataFim.atTime(23, 59, 59));
            
            // Escrever cabeçalho
            writer.write("TRANSAÇÕES - CONSUMO ESPERTO\n");
            writer.write("Período: " + dataInicio + " a " + dataFim + "\n");
            writer.write("Data de exportação: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + "\n\n");
            
            // Escrever dados
            writer.write("Data,Descrição,Tipo,Valor,Categoria\n");
            for (Transacao transacao : transacoes) {
                writer.write(transacao.getDataTransacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ",");
                writer.write("\"" + transacao.getDescricao() + "\",");
                writer.write(transacao.getTipoTransacao() + ",");
                writer.write(transacao.getValor().toString() + ",");
                writer.write(transacao.getCategoria() != null ? transacao.getCategoria().getNome() : "Sem categoria");
                writer.write("\n");
            }
            
            writer.close();
            
            log.info("✅ Transações exportadas em CSV com sucesso - {} bytes", baos.size());
            return baos.toByteArray();
            
        } catch (IOException e) {
            log.error("❌ Erro ao exportar transações em CSV: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao exportar transações: " + e.getMessage());
        }
    }

    /**
     * Exporta dados em JSON
     */
    public byte[] exportarDadosJson(Long usuarioId, LocalDate dataInicio, LocalDate dataFim) {
        try {
            log.info("📤 Exportando dados em JSON para usuário: {} ({} a {})", 
                    usuarioId, dataInicio, dataFim);
            
            // Buscar dados
            Usuario usuario = buscarUsuario(usuarioId);
            List<Transacao> transacoes = transacaoRepository
                .findByUsuarioIdAndDataTransacaoBetween(usuarioId, dataInicio.atStartOfDay(), dataFim.atTime(23, 59, 59));
            List<Fatura> faturas = faturaRepository
                .findByUsuarioIdAndDataVencimentoBetween(usuarioId, 
                    dataInicio.atStartOfDay(), 
                    dataFim.atTime(23, 59, 59));
            List<CartaoCredito> cartoes = cartaoCreditoRepository.findByUsuarioId(usuarioId);
            
            // Criar estrutura JSON
            Map<String, Object> dados = new HashMap<>();
            dados.put("usuario", Map.of(
                "id", usuario.getId(),
                "nome", usuario.getNome(),
                "email", usuario.getEmail()
            ));
            dados.put("periodo", Map.of(
                "inicio", dataInicio.toString(),
                "fim", dataFim.toString()
            ));
            dados.put("data_exportacao", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            
            // Converter transações
            List<Map<String, Object>> transacoesJson = transacoes.stream()
                .map(t -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", t.getId());
                    map.put("data", t.getDataTransacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                    map.put("descricao", t.getDescricao());
                    map.put("tipo", t.getTipoTransacao());
                    map.put("valor", t.getValor());
                    map.put("categoria", t.getCategoria() != null ? t.getCategoria().getNome() : "Sem categoria");
                    return map;
                })
                .toList();
            dados.put("transacoes", transacoesJson);
            
            // Converter faturas
            List<Map<String, Object>> faturasJson = faturas.stream()
                .map(f -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", f.getId());
                    map.put("vencimento", f.getDataVencimento().toString());
                    map.put("valor", f.getValor());
                    map.put("paga", f.isPaga());
                    map.put("status", f.isPaga() ? "PAGA" : "PENDENTE");
                    return map;
                })
                .toList();
            dados.put("faturas", faturasJson);
            
            // Converter cartões
            List<Map<String, Object>> cartoesJson = cartoes.stream()
                .map(c -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", c.getId());
                    map.put("nome", c.getNome());
                    map.put("limite_total", c.getLimiteTotal());
                    map.put("limite_utilizado", c.getLimiteUtilizado());
                    map.put("limite_disponivel", c.getLimiteDisponivel());
                    map.put("bandeira", c.getBandeira());
                    return map;
                })
                .toList();
            dados.put("cartoes", cartoesJson);
            
            // Converter para JSON (simplificado)
            String json = converterParaJson(dados);
            
            log.info("✅ Dados exportados em JSON com sucesso - {} bytes", json.getBytes("UTF-8").length);
            return json.getBytes("UTF-8");
            
        } catch (Exception e) {
            log.error("❌ Erro ao exportar dados em JSON: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao exportar dados: " + e.getMessage());
        }
    }

    /**
     * Exporta dados em XML
     */
    public byte[] exportarDadosXml(Long usuarioId, LocalDate dataInicio, LocalDate dataFim) {
        try {
            log.info("📤 Exportando dados em XML para usuário: {} ({} a {})", 
                    usuarioId, dataInicio, dataFim);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Writer writer = new OutputStreamWriter(baos, "UTF-8");
            
            // Buscar dados
            Usuario usuario = buscarUsuario(usuarioId);
            List<Transacao> transacoes = transacaoRepository
                .findByUsuarioIdAndDataTransacaoBetween(usuarioId, dataInicio.atStartOfDay(), dataFim.atTime(23, 59, 59));
            List<Fatura> faturas = faturaRepository
                .findByUsuarioIdAndDataVencimentoBetween(usuarioId, 
                    dataInicio.atStartOfDay(), 
                    dataFim.atTime(23, 59, 59));
            List<CartaoCredito> cartoes = cartaoCreditoRepository.findByUsuarioId(usuarioId);
            
            // Escrever XML
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<dados_financeiros>\n");
            writer.write("  <usuario>\n");
            writer.write("    <id>" + usuario.getId() + "</id>\n");
            writer.write("    <nome>" + usuario.getNome() + "</nome>\n");
            writer.write("    <email>" + usuario.getEmail() + "</email>\n");
            writer.write("  </usuario>\n");
            writer.write("  <periodo>\n");
            writer.write("    <inicio>" + dataInicio + "</inicio>\n");
            writer.write("    <fim>" + dataFim + "</fim>\n");
            writer.write("  </periodo>\n");
            writer.write("  <data_exportacao>" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + "</data_exportacao>\n");
            
            // Escrever transações
            writer.write("  <transacoes>\n");
            for (Transacao transacao : transacoes) {
                writer.write("    <transacao>\n");
                writer.write("      <id>" + transacao.getId() + "</id>\n");
                writer.write("      <data>" + transacao.getDataTransacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "</data>\n");
                writer.write("      <descricao>" + transacao.getDescricao() + "</descricao>\n");
                writer.write("      <tipo>" + transacao.getTipoTransacao() + "</tipo>\n");
                writer.write("      <valor>" + transacao.getValor() + "</valor>\n");
                writer.write("      <categoria>" + (transacao.getCategoria() != null ? transacao.getCategoria().getNome() : "Sem categoria") + "</categoria>\n");
                writer.write("    </transacao>\n");
            }
            writer.write("  </transacoes>\n");
            
            // Escrever faturas
            writer.write("  <faturas>\n");
            for (Fatura fatura : faturas) {
                writer.write("    <fatura>\n");
                writer.write("      <id>" + fatura.getId() + "</id>\n");
                writer.write("      <vencimento>" + fatura.getDataVencimento() + "</vencimento>\n");
                writer.write("      <valor>" + fatura.getValor() + "</valor>\n");
                writer.write("      <paga>" + fatura.isPaga() + "</paga>\n");
                writer.write("      <status>" + (fatura.isPaga() ? "PAGA" : "PENDENTE") + "</status>\n");
                writer.write("    </fatura>\n");
            }
            writer.write("  </faturas>\n");
            
            // Escrever cartões
            writer.write("  <cartoes>\n");
            for (CartaoCredito cartao : cartoes) {
                writer.write("    <cartao>\n");
                writer.write("      <id>" + cartao.getId() + "</id>\n");
                writer.write("      <nome>" + cartao.getNome() + "</nome>\n");
                writer.write("      <limite_total>" + cartao.getLimiteTotal() + "</limite_total>\n");
                writer.write("      <limite_utilizado>" + cartao.getLimiteUtilizado() + "</limite_utilizado>\n");
                writer.write("      <limite_disponivel>" + cartao.getLimiteDisponivel() + "</limite_disponivel>\n");
                writer.write("      <bandeira>" + cartao.getBandeira() + "</bandeira>\n");
                writer.write("    </cartao>\n");
            }
            writer.write("  </cartoes>\n");
            
            writer.write("</dados_financeiros>\n");
            writer.close();
            
            log.info("✅ Dados exportados em XML com sucesso - {} bytes", baos.size());
            return baos.toByteArray();
            
        } catch (IOException e) {
            log.error("❌ Erro ao exportar dados em XML: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao exportar dados: " + e.getMessage());
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
     * Converte objeto para JSON (simplificado)
     */
    private String converterParaJson(Map<String, Object> dados) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        
        boolean first = true;
        for (Map.Entry<String, Object> entry : dados.entrySet()) {
            if (!first) json.append(",\n");
            json.append("  \"").append(entry.getKey()).append("\": ");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else if (value instanceof List) {
                json.append("[\n");
                List<?> list = (List<?>) value;
                boolean firstItem = true;
                for (Object item : list) {
                    if (!firstItem) json.append(",\n");
                    if (item instanceof Map) {
                        json.append("    ").append(converterMapParaJson((Map<?, ?>) item));
                    } else {
                        json.append("    \"").append(item).append("\"");
                    }
                    firstItem = false;
                }
                json.append("\n  ]");
            } else if (value instanceof Map) {
                json.append(converterMapParaJson((Map<?, ?>) value));
            } else {
                json.append(value);
            }
            
            first = false;
        }
        
        json.append("\n}");
        return json.toString();
    }

    /**
     * Converte Map para JSON (simplificado)
     */
    private String converterMapParaJson(Map<?, ?> map) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) json.append(",\n");
            json.append("      \"").append(entry.getKey()).append("\": ");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else {
                json.append(value);
            }
            
            first = false;
        }
        
        json.append("\n    }");
        return json.toString();
    }
}
