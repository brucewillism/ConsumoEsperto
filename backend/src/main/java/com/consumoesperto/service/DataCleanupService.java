package com.consumoesperto.service;

import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço para limpeza de dados antigos
 */
@Service
public class DataCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(DataCleanupService.class);

    @Autowired
    private CartaoCreditoRepository cartaoCreditoRepository;

    @Autowired
    private FaturaRepository faturaRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    /**
     * Limpa dados antigos de um usuário específico
     */
    @Transactional
    public void limparDadosAntigos(Long userId) {
        try {
            logger.info("🧹 Limpando dados antigos para usuário: {}", userId);
            
            // Limpar transações antigas
            int transacoesRemovidas = transacaoRepository.deleteByUsuarioId(userId);
            logger.info("   🗑️ Transações removidas: {}", transacoesRemovidas);
            
            // Limpar faturas antigas
            int faturasRemovidas = faturaRepository.deleteByCartaoCreditoUsuarioId(userId);
            logger.info("   🗑️ Faturas removidas: {}", faturasRemovidas);
            
            // Limpar cartões antigos (manter apenas os ativos)
            int cartoesRemovidos = cartaoCreditoRepository.deleteByUsuarioIdAndAtivoFalse(userId);
            logger.info("   🗑️ Cartões inativos removidos: {}", cartoesRemovidos);
            
            logger.info("✅ Limpeza de dados antigos concluída para usuário: {}", userId);
            
        } catch (Exception e) {
            logger.error("❌ Erro ao limpar dados antigos para usuário {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Limpa dados antigos de todos os usuários
     */
    @Transactional
    public void limparTodosDadosAntigos() {
        try {
            logger.info("🧹 Limpando dados antigos para todos os usuários");
            
            // Limpar todas as transações
            transacaoRepository.deleteAll();
            logger.info("   🗑️ Todas as transações foram removidas");
            
            // Limpar todas as faturas
            faturaRepository.deleteAll();
            logger.info("   🗑️ Todas as faturas foram removidas");
            
            // Limpar todos os cartões inativos
            cartaoCreditoRepository.deleteByAtivoFalse();
            logger.info("   🗑️ Todos os cartões inativos foram removidos");
            
            logger.info("✅ Limpeza de dados antigos concluída para todos os usuários");
            
        } catch (Exception e) {
            logger.error("❌ Erro ao limpar dados antigos: {}", e.getMessage(), e);
        }
    }
}
