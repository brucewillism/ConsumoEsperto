package com.consumoesperto.service;

import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Serviço para sincronização automática de dados na inicialização da aplicação
 */
@Service
public class AutoSyncService {

    private static final Logger logger = LoggerFactory.getLogger(AutoSyncService.class);

    @Autowired
    private CategoriaRepository categoriaRepository;
    
    @Autowired
    private MercadoPagoService mercadoPagoService;
    
    @Autowired
    private UsuarioRepository usuarioRepository;



    /**
     * Executa sincronização automática quando a aplicação estiver pronta
     * DESATIVADO: Sistema NÃO sincroniza automaticamente dados simulados
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("🚀 Iniciando aplicação...");
        
        try {
            // 1. Criar apenas categorias padrão
            criarCategoriasPadrao();
            
            // 2. NÃO sincronizar dados automaticamente - apenas dados reais quando solicitado
            logger.info("ℹ️ Sincronização automática desativada - apenas dados reais serão usados");
            
            logger.info("✅ Aplicação iniciada com sucesso!");
            
        } catch (Exception e) {
            logger.error("❌ Erro na inicialização: {}", e.getMessage(), e);
        }
    }

    /**
     * Cria categorias padrão se não existirem
     * Método público para ser chamado manualmente
     */
    public void criarCategoriasPadrao() {
        logger.info("📂 Criando categorias padrão...");
        
        List<Categoria> categoriasPadrao = Arrays.asList(
            criarCategoria("Alimentação", "Gastos com comida e bebida", "#FF6B6B", "🍽️"),
            criarCategoria("Transporte", "Gastos com transporte", "#4ECDC4", "🚗"),
            criarCategoria("Saúde", "Gastos com saúde e medicamentos", "#45B7D1", "🏥"),
            criarCategoria("Educação", "Gastos com educação", "#96CEB4", "📚"),
            criarCategoria("Lazer", "Gastos com entretenimento", "#FFEAA7", "🎮"),
            criarCategoria("Compras", "Compras diversas", "#DDA0DD", "🛍️"),
            criarCategoria("Serviços", "Serviços diversos", "#98D8C8", "🔧"),
            criarCategoria("Casa", "Gastos com casa e moradia", "#F39C12", "🏠"),
            criarCategoria("Roupas", "Gastos com roupas e acessórios", "#E74C3C", "👕"),
            criarCategoria("Outros", "Outros gastos", "#F7DC6F", "📦")
        );

        int categoriasCriadas = 0;
        for (Categoria categoria : categoriasPadrao) {
            if (!categoriaRepository.existsByNome(categoria.getNome())) {
                categoriaRepository.save(categoria);
                categoriasCriadas++;
                logger.info("   ✅ Categoria '{}' criada", categoria.getNome());
            } else {
                logger.debug("   ⚠️ Categoria '{}' já existe", categoria.getNome());
            }
        }
        
        logger.info("📊 Categorias criadas: {} de {}", categoriasCriadas, categoriasPadrao.size());
    }

    /**
     * Cria uma categoria com os dados fornecidos
     */
    private Categoria criarCategoria(String nome, String descricao, String cor, String icone) {
        Categoria categoria = new Categoria();
        categoria.setNome(nome);
        categoria.setDescricao(descricao);
        categoria.setCor(cor);
        categoria.setIcone(icone);
        categoria.setAtivo(true);
        categoria.setDataCriacao(LocalDateTime.now());
        return categoria;
    }

    /**
     * Sincroniza dados reais do Mercado Pago para todos os usuários
     */
    private void sincronizarDadosReais() {
        logger.info("🔄 Iniciando sincronização de dados reais do Mercado Pago...");
        
        try {
            // Buscar todos os usuários e sincronizar dados para cada um
            List<Usuario> usuarios = usuarioRepository.findAll();
            for (Usuario usuario : usuarios) {
                try {
                    mercadoPagoService.sincronizarDadosReais(usuario.getId());
                } catch (Exception e) {
                    logger.error("❌ Erro ao sincronizar dados para usuário {}: {}", usuario.getId(), e.getMessage());
                }
            }
            
            logger.info("✅ Sincronização de dados reais concluída!");
            
        } catch (Exception e) {
            logger.error("❌ Erro na sincronização de dados reais: {}", e.getMessage(), e);
        }
    }

}
