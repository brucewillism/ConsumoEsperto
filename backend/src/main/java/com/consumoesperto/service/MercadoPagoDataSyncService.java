package com.consumoesperto.service;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoDataSyncService {

    private final TransacaoRepository transacaoRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

<<<<<<< HEAD
=======
    @Transactional
>>>>>>> origin/main
    public void sincronizarPagamentos(Long usuarioId, List<Map<String, Object>> pagamentos) {
        log.info("🔄 Iniciando sincronização de {} pagamentos para usuário: {}", pagamentos.size(), usuarioId);
        
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: " + usuarioId));

<<<<<<< HEAD
        int sucessos = 0;
        int erros = 0;
        
        // Processar todos os pagamentos
        log.info("🔄 Processando {} pagamentos", pagamentos.size());
        
        for (Map<String, Object> pagamento : pagamentos) {
            try {
                sincronizarPagamento(usuario, pagamento);
                sucessos++;
            } catch (Exception e) {
                log.error("❌ Erro ao sincronizar pagamento {}: {}", pagamento.get("id"), e.getMessage());
                erros++;
            }
        }
        
        log.info("✅ Sincronização de pagamentos concluída para usuário: {} - Sucessos: {}, Erros: {}", usuarioId, sucessos, erros);
=======
        for (Map<String, Object> pagamento : pagamentos) {
            try {
                sincronizarPagamento(usuario, pagamento);
            } catch (Exception e) {
                log.error("❌ Erro ao sincronizar pagamento {}: {}", pagamento.get("id"), e.getMessage());
            }
        }
        
        log.info("✅ Sincronização de pagamentos concluída para usuário: {}", usuarioId);
>>>>>>> origin/main
    }

    private void sincronizarPagamento(Usuario usuario, Map<String, Object> pagamento) {
        Long pagamentoId = Long.valueOf(pagamento.get("id").toString());
        
        // Converter data primeiro
        String dateCreated = pagamento.get("date_created").toString();
        LocalDateTime dataTransacao = LocalDateTime.parse(dateCreated, 
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
        
<<<<<<< HEAD
        // Verificar campos obrigatórios (exceto descrição que pode ser null)
        Object descriptionObj = pagamento.get("description");
        Object transactionAmountObj = pagamento.get("transaction_amount");
        Object statusObj = pagamento.get("status");
        
        if (transactionAmountObj == null || statusObj == null) {
            log.warn("⚠️ Pagamento {} tem campos obrigatórios nulos, pulando...", pagamentoId);
            return;
        }
        
        // Criar descrição - usar descrição se disponível, senão usar estabelecimento
        String description;
        if (descriptionObj != null && !descriptionObj.toString().trim().isEmpty()) {
            description = descriptionObj.toString();
        } else {
            // Tentar usar o estabelecimento como fallback
            Object statementDescriptor = pagamento.get("statement_descriptor");
            if (statementDescriptor != null && !statementDescriptor.toString().trim().isEmpty()) {
                description = "Pagamento - " + statementDescriptor.toString();
            } else {
                description = "Pagamento Mercado Pago #" + pagamentoId;
            }
        }
        
        BigDecimal valor = new BigDecimal(transactionAmountObj.toString());
        String status = statusObj.toString();
        
        // Adicionado: Validação para garantir que o valor seja maior que zero
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("⚠️ Pagamento {} tem valor menor ou igual a zero (R$ {}), pulando...", pagamentoId, valor);
            return;
        }
        
        // Verificar se já existe (usando método disponível)
        List<Transacao> existentes = transacaoRepository.findByUsuarioIdAndDescricaoAndDataTransacaoAndValor(
            usuario.getId(),
            description,
            dataTransacao,
            valor
=======
        // Verificar se já existe (usando método disponível)
        List<Transacao> existentes = transacaoRepository.findByUsuarioIdAndDescricaoAndDataTransacaoAndValor(
            usuario.getId(),
            pagamento.get("description").toString(),
            dataTransacao,
            new BigDecimal(pagamento.get("transaction_amount").toString())
>>>>>>> origin/main
        );
        
        if (!existentes.isEmpty()) {
            log.debug("ℹ️ Pagamento {} já existe, pulando...", pagamentoId);
            return;
        }

        // Criar nova transação
        Transacao transacao = new Transacao();
        transacao.setUsuario(usuario);
<<<<<<< HEAD
        transacao.setDescricao(description);
        transacao.setValor(valor);
        transacao.setDataTransacao(dataTransacao);
        
        // Determinar tipo de transação
=======
        transacao.setDescricao(pagamento.get("description").toString());
        transacao.setValor(new BigDecimal(pagamento.get("transaction_amount").toString()));
        transacao.setDataTransacao(dataTransacao);
        
        // Determinar tipo de transação
        String status = pagamento.get("status").toString();
>>>>>>> origin/main
        if ("refunded".equals(status)) {
            transacao.setTipoTransacao(Transacao.TipoTransacao.DESPESA); // Estorno é despesa
        } else if ("approved".equals(status)) {
            transacao.setTipoTransacao(Transacao.TipoTransacao.RECEITA);
        } else {
            transacao.setTipoTransacao(Transacao.TipoTransacao.DESPESA); // Outros como despesa
        }
        
<<<<<<< HEAD
        // Categorizar automaticamente - usar categoria padrão simples
        try {
            Categoria categoria = criarCategoria("Outros", usuario.getId());
            transacao.setCategoria(categoria);
        } catch (Exception e) {
            log.error("❌ Erro ao criar categoria para pagamento {}: {}", pagamentoId, e.getMessage());
            // Continuar sem categoria se houver erro
        }
=======
        // Categorizar automaticamente
        Categoria categoria = categorizarTransacao(transacao.getDescricao());
        transacao.setCategoria(categoria);
>>>>>>> origin/main
        
        transacaoRepository.save(transacao);
        log.info("✅ Pagamento {} sincronizado: {} - R$ {}", 
            pagamentoId, transacao.getDescricao(), transacao.getValor());
    }

    @Transactional
    public void sincronizarCartoes(Long usuarioId, List<Map<String, Object>> cartoes) {
        log.info("🔄 Iniciando sincronização de {} cartões para usuário: {}", cartoes.size(), usuarioId);
        
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: " + usuarioId));

        for (Map<String, Object> cartao : cartoes) {
            try {
                sincronizarCartao(usuario, cartao);
            } catch (Exception e) {
                log.error("❌ Erro ao sincronizar cartão {}: {}", cartao.get("id"), e.getMessage());
            }
        }
        
        log.info("✅ Sincronização de cartões concluída para usuário: {}", usuarioId);
    }

    private void sincronizarCartao(Usuario usuario, Map<String, Object> cartao) {
        String cartaoId = cartao.get("id").toString();
        
        // Verificar se já existe
        String numeroCartao = "****" + cartao.get("last_four_digits").toString();
        boolean existe = cartaoCreditoRepository.existsByNumeroCartaoAndUsuarioId(numeroCartao, usuario.getId());
        
        if (existe) {
            log.debug("ℹ️ Cartão {} já existe, pulando...", cartaoId);
            return;
        }

        // Criar novo cartão
        CartaoCredito cartaoCredito = new CartaoCredito();
        cartaoCredito.setUsuario(usuario);
        cartaoCredito.setNome(cartao.get("cardholder").toString());
        cartaoCredito.setNumeroCartao("****" + cartao.get("last_four_digits").toString());
        cartaoCredito.setBanco("Mercado Pago");
        cartaoCredito.setAtivo(true);
        
        // Converter data de vencimento
        Integer mes = (Integer) cartao.get("expiration_month");
        Integer ano = (Integer) cartao.get("expiration_year");
        cartaoCredito.setDiaVencimento(mes);
        
        // Definir limite padrão (não disponível na API)
        cartaoCredito.setLimiteCredito(BigDecimal.ZERO);
        cartaoCredito.setLimiteDisponivel(BigDecimal.ZERO);
        
        cartaoCreditoRepository.save(cartaoCredito);
        log.info("✅ Cartão {} sincronizado: {} - ****{}", 
            cartaoId, cartaoCredito.getNome(), cartao.get("last_four_digits"));
    }

<<<<<<< HEAD
    private Categoria categorizarTransacao(String descricao, Long usuarioId) {
        String descricaoLower = descricao.toLowerCase();
        
        if (descricaoLower.contains("aspirador") || descricaoLower.contains("eletro")) {
            Categoria categoria = categoriaRepository.findByUsuarioIdAndNome(usuarioId, "Casa e Eletrodomésticos");
            return categoria != null ? categoria : criarCategoria("Casa e Eletrodomésticos", usuarioId);
        } else if (descricaoLower.contains("comida") || descricaoLower.contains("alimento")) {
            Categoria categoria = categoriaRepository.findByUsuarioIdAndNome(usuarioId, "Alimentação");
            return categoria != null ? categoria : criarCategoria("Alimentação", usuarioId);
        } else if (descricaoLower.contains("transporte") || descricaoLower.contains("uber")) {
            Categoria categoria = categoriaRepository.findByUsuarioIdAndNome(usuarioId, "Transporte");
            return categoria != null ? categoria : criarCategoria("Transporte", usuarioId);
        } else {
            Categoria categoria = categoriaRepository.findByUsuarioIdAndNome(usuarioId, "Outros");
            return categoria != null ? categoria : criarCategoria("Outros", usuarioId);
        }
    }

    private Categoria criarCategoria(String nome, Long usuarioId) {
        // Verificar se a categoria já existe
        Categoria categoriaExistente = categoriaRepository.findByUsuarioIdAndNome(usuarioId, nome);
        if (categoriaExistente != null) {
            return categoriaExistente;
        }
        
        Usuario usuario = usuarioRepository.findById(usuarioId)
=======
    private Categoria categorizarTransacao(String descricao) {
        String descricaoLower = descricao.toLowerCase();
        
        if (descricaoLower.contains("aspirador") || descricaoLower.contains("eletro")) {
            Categoria categoria = categoriaRepository.findByUsuarioIdAndNome(1L, "Casa e Eletrodomésticos");
            return categoria != null ? categoria : criarCategoria("Casa e Eletrodomésticos");
        } else if (descricaoLower.contains("comida") || descricaoLower.contains("alimento")) {
            Categoria categoria = categoriaRepository.findByUsuarioIdAndNome(1L, "Alimentação");
            return categoria != null ? categoria : criarCategoria("Alimentação");
        } else if (descricaoLower.contains("transporte") || descricaoLower.contains("uber")) {
            Categoria categoria = categoriaRepository.findByUsuarioIdAndNome(1L, "Transporte");
            return categoria != null ? categoria : criarCategoria("Transporte");
        } else {
            Categoria categoria = categoriaRepository.findByUsuarioIdAndNome(1L, "Outros");
            return categoria != null ? categoria : criarCategoria("Outros");
        }
    }

    private Categoria criarCategoria(String nome) {
        Usuario usuario = usuarioRepository.findById(1L)
>>>>>>> origin/main
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
            
        Categoria categoria = new Categoria();
        categoria.setNome(nome);
        categoria.setDescricao("Categoria criada automaticamente");
        categoria.setUsuario(usuario);
        return categoriaRepository.save(categoria);
    }
}
<<<<<<< HEAD

=======
>>>>>>> origin/main
