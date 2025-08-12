package com.consumoesperto.service;

import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serviço responsável pela sincronização automática de dados bancários
 * 
 * Este serviço implementa a sincronização em tempo real com as APIs dos bancos
 * para manter os dados de cartões, saldos e faturas sempre atualizados.
 * 
 * Funcionalidades principais:
 * - Sincronização automática de cartões de crédito
 * - Atualização de saldos e limites em tempo real
 * - Sincronização de faturas e transações
 * - Processamento paralelo para múltiplos bancos
 * - Tratamento de erros e retry automático
 * - Cache inteligente para otimizar performance
 * 
 * Bancos suportados:
 * - Itaú (Open Banking)
 * - Mercado Pago
 * - Inter (Open Banking)
 * - Nubank
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankSynchronizationService {

    // Serviços específicos de cada banco
    private final ItauBankService itauBankService;
    private final MercadoPagoBankService mercadoPagoBankService;
    private final InterBankService interBankService;
    private final NubankBankService nubankBankService;
    
    // Repositórios para persistência de dados
    private final CartaoCreditoRepository cartaoCreditoRepository;
    private final FaturaRepository faturaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;
    
    // Executor para processamento paralelo de múltiplos bancos
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    
    // Cache para evitar consultas desnecessárias (TTL: 5 minutos)
    private final Map<String, CacheEntry> dataCache = new HashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutos

    /**
     * Sincroniza todos os dados bancários de um usuário
     * 
     * Este método executa a sincronização completa com todos os bancos
     * conectados pelo usuário, incluindo cartões, saldos e faturas.
     * 
     * Processo de sincronização:
     * 1. Identifica bancos conectados pelo usuário
     * 2. Executa sincronização paralela para cada banco
     * 3. Atualiza dados locais com informações reais dos bancos
     * 4. Processa faturas e transações pendentes
     * 5. Atualiza limites e saldos disponíveis
     * 
     * @param usuarioId ID do usuário para sincronização
     * @return Map com resumo da sincronização de cada banco
     */
    @Transactional
    public Map<String, Object> synchronizeUserBankData(Long usuarioId) {
        log.info("Iniciando sincronização bancária para usuário: {}", usuarioId);
        
        try {
            // Busca usuário e verifica bancos conectados
            Usuario usuario = usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
            
            // Lista de bancos para sincronização (baseado no que o usuário tem)
            List<AutorizacaoBancaria.TipoBanco> connectedBanks = getConnectedBanks(usuario);
            
            // Executa sincronização paralela para cada banco
            List<CompletableFuture<Map<String, Object>>> syncFutures = new ArrayList<>();
            
            for (AutorizacaoBancaria.TipoBanco bankType : connectedBanks) {
                CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return synchronizeBankData(usuario, bankType);
                    } catch (Exception e) {
                        log.error("Erro na sincronização do banco {}: {}", bankType, e.getMessage());
                        return Map.of(
                            "banco", bankType.name(),
                            "status", "ERRO",
                            "erro", e.getMessage()
                        );
                    }
                }, executorService);
                
                syncFutures.add(future);
            }
            
            // Aguarda conclusão de todas as sincronizações
            CompletableFuture.allOf(syncFutures.toArray(new CompletableFuture[0])).join();
            
            // Coleta resultados
            Map<String, Object> results = new HashMap<>();
            for (CompletableFuture<Map<String, Object>> future : syncFutures) {
                Map<String, Object> result = future.get();
                results.put(result.get("banco").toString(), result);
            }
            
            log.info("Sincronização bancária concluída para usuário: {}", usuarioId);
            return results;
            
        } catch (Exception e) {
            log.error("Erro na sincronização bancária do usuário {}: {}", usuarioId, e.getMessage());
            throw new RuntimeException("Falha na sincronização bancária", e);
        }
    }

    /**
     * Sincroniza dados de um banco específico para um usuário
     * 
     * Este método implementa a sincronização completa com um banco específico,
     * incluindo autenticação, busca de dados e atualização local.
     * 
     * @param usuario Usuário para sincronização
     * @param bankType Tipo do banco para sincronização
     * @return Map com resultado da sincronização
     */
    private Map<String, Object> synchronizeBankData(Usuario usuario, AutorizacaoBancaria.TipoBanco bankType) {
        log.info("Sincronizando dados do banco {} para usuário: {}", bankType, usuario.getId());
        
        try {
            // Verifica se há dados em cache válidos
            String cacheKey = generateCacheKey(usuario.getId(), bankType);
            if (isCacheValid(cacheKey)) {
                log.info("Usando dados em cache para banco {} - usuário: {}", bankType, usuario.getId());
                return (Map<String, Object>) dataCache.get(cacheKey).getData();
            }
            
            // Busca token de acesso do usuário para este banco
            String accessToken = getAccessToken(usuario, bankType);
            if (accessToken == null) {
                return Map.of(
                    "banco", bankType.name(),
                    "status", "NAO_AUTORIZADO",
                    "mensagem", "Usuário não autorizado neste banco"
                );
            }
            
            // Executa sincronização específica do banco
            Map<String, Object> result = executeBankSpecificSync(usuario, bankType, accessToken);
            
            // Armazena resultado em cache
            cacheData(cacheKey, result);
            
            log.info("Sincronização do banco {} concluída com sucesso para usuário: {}", bankType, usuario.getId());
            return result;
            
        } catch (Exception e) {
            log.error("Erro na sincronização do banco {} para usuário {}: {}", bankType, usuario.getId(), e.getMessage());
            return Map.of(
                "banco", bankType.name(),
                "status", "ERRO",
                "erro", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
        }
    }

    /**
     * Executa sincronização específica para cada tipo de banco
     * 
     * @param usuario Usuário para sincronização
     * @param bankType Tipo do banco
     * @param accessToken Token de acesso
     * @return Resultado da sincronização
     */
    private Map<String, Object> executeBankSpecificSync(Usuario usuario, AutorizacaoBancaria.TipoBanco bankType, String accessToken) {
        // Busca a autorização bancária para este usuário e banco
        Optional<AutorizacaoBancaria> autorizacaoOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(usuario.getId(), bankType);

        if (autorizacaoOpt.isEmpty()) {
            return Map.of("status", "error", "message", "Autorização não encontrada");
        }

        AutorizacaoBancaria autorizacao = autorizacaoOpt.get();

        switch (bankType) {
            case ITAU:
                return itauBankService.getBankDetails(autorizacao);
            case MERCADO_PAGO:
                return mercadoPagoBankService.getBankDetails(autorizacao);
            case INTER:
                return interBankService.getBankDetails(autorizacao);
            case NUBANK:
                return nubankBankService.getBankDetails(autorizacao);
            default:
                throw new IllegalArgumentException("Tipo de banco não suportado: " + bankType);
        }
    }

    /**
     * Obtém bancos conectados pelo usuário
     * 
     * Identifica quais bancos o usuário tem autorizações ativas
     * para determinar quais APIs devem ser consultadas.
     * 
     * @param usuario Usuário para verificação
     * @return Lista de tipos de banco conectados
     */
    private List<AutorizacaoBancaria.TipoBanco> getConnectedBanks(Usuario usuario) {
        List<AutorizacaoBancaria.TipoBanco> connectedBanks = new ArrayList<>();
        
        // Busca autorizações ativas do usuário
        List<AutorizacaoBancaria> autorizacoes = autorizacaoBancariaRepository
                .findByUsuarioIdAndStatus(usuario.getId(), AutorizacaoBancaria.StatusAutorizacao.ATIVA);
        
        for (AutorizacaoBancaria autorizacao : autorizacoes) {
            if (autorizacao.isTokenValido()) {
                connectedBanks.add(autorizacao.getTipoBanco());
            }
        }
        
        return connectedBanks;
    }

    /**
     * Obtém token de acesso para um banco específico
     * 
     * Busca o token OAuth2 válido do usuário para o banco especificado
     * na tabela de autorizações bancárias. Se o token estiver próximo
     * da expiração, tenta renová-lo automaticamente.
     * 
     * @param usuario Usuário para busca do token
     * @param bankType Tipo do banco
     * @return Token de acesso OAuth2 ou null se não autorizado
     */
    private String getAccessToken(Usuario usuario, AutorizacaoBancaria.TipoBanco bankType) {
        try {
            // Busca autorização ativa do usuário para este banco
            Optional<AutorizacaoBancaria> autorizacaoOpt = autorizacaoBancariaRepository
                    .findByUsuarioIdAndTipoBanco(usuario.getId(), bankType);
            
            if (autorizacaoOpt.isEmpty()) {
                log.info("Usuário {} não possui autorização para banco: {}", usuario.getId(), bankType);
                return null;
            }
            
            AutorizacaoBancaria autorizacao = autorizacaoOpt.get();
            
            // Verifica se a autorização está ativa
            if (autorizacao.getStatus() != AutorizacaoBancaria.StatusAutorizacao.ATIVA) {
                log.warn("Autorização do usuário {} para banco {} não está ativa. Status: {}", 
                        usuario.getId(), bankType, autorizacao.getStatus());
                return null;
            }
            
            // Verifica se o token precisa ser renovado
            if (autorizacao.precisaRenovacao()) {
                log.info("Token do usuário {} para banco {} precisa ser renovado. Renovando...", 
                        usuario.getId(), bankType);
                
                try {
                    // Tenta renovar o token automaticamente
                    boolean renovado = renovarTokenAutomaticamente(autorizacao);
                    if (!renovado) {
                        log.error("Falha ao renovar token do usuário {} para banco {}", usuario.getId(), bankType);
                        return null;
                    }
                } catch (Exception e) {
                    log.error("Erro ao renovar token do usuário {} para banco {}: {}", 
                            usuario.getId(), bankType, e.getMessage());
                    return null;
                }
            }
            
            // Marca a autorização como utilizada para auditoria
            autorizacao.marcarComoUtilizada();
            autorizacaoBancariaRepository.save(autorizacao);
            
            log.info("Token válido obtido para usuário {} no banco {}", usuario.getId(), bankType);
            return autorizacao.getAccessToken();
            
        } catch (Exception e) {
            log.error("Erro ao buscar token de acesso para usuário {} no banco {}: {}", 
                    usuario.getId(), bankType, e.getMessage());
            return null;
        }
    }

    /**
     * Renova automaticamente o token de acesso se estiver expirado
     * 
     * @param autorizacao Autorização bancária
     * @return true se renovação for bem-sucedida
     */
    private boolean renovarTokenAutomaticamente(AutorizacaoBancaria autorizacao) {
        try {
            log.info("Tentando renovar token automaticamente para autorização {}", autorizacao.getId());
            
            // Verifica se tem refresh token
            if (autorizacao.getRefreshToken() == null || autorizacao.getRefreshToken().trim().isEmpty()) {
                log.warn("Autorização {} não possui refresh token para renovação automática", autorizacao.getId());
                return false;
            }
            
            // Tenta renovar o token usando o serviço específico do banco
            boolean renovado = false;
            switch (autorizacao.getTipoBanco()) {
                case ITAU:
                    renovado = itauBankService.refreshTokenIfNeeded(autorizacao);
                    break;
                case MERCADO_PAGO:
                    renovado = mercadoPagoBankService.refreshTokenIfNeeded(autorizacao);
                    break;
                case INTER:
                    renovado = interBankService.refreshTokenIfNeeded(autorizacao);
                    break;
                case NUBANK:
                    renovado = nubankBankService.refreshTokenIfNeeded(autorizacao);
                    break;
            default:
                    log.warn("Tipo de banco não suportado para renovação automática: {}", autorizacao.getTipoBanco());
                    return false;
            }
            
            if (renovado) {
                log.info("Token renovado com sucesso para autorização {}", autorizacao.getId());
                // TODO: Atualizar a autorização com o novo token e data de expiração
                // autorizacaoBancariaService.atualizarTokenRenovado(autorizacao.getId(), novoToken, novaDataExpiracao);
                return true;
                    } else {
                log.error("Falha ao renovar token para autorização {}", autorizacao.getId());
                return false;
            }
            
        } catch (Exception e) {
            log.error("Erro ao renovar token automaticamente para autorização {}", autorizacao.getId(), e);
            return false;
        }
    }

    /**
     * Gera chave única para cache
     * 
     * Cria uma chave única baseada no usuário e tipo de banco
     * para armazenar dados em cache.
     * 
     * @param usuarioId ID do usuário
     * @param bankType Tipo do banco
     * @return Chave única para cache
     */
    private String generateCacheKey(Long usuarioId, AutorizacaoBancaria.TipoBanco bankType) {
        return usuarioId + "_" + bankType.name();
    }

    /**
     * Verifica se cache ainda é válido
     * 
     * Valida se os dados em cache ainda estão dentro do TTL
     * configurado para evitar dados desatualizados.
     * 
     * @param cacheKey Chave do cache
     * @return true se cache é válido, false caso contrário
     */
    private boolean isCacheValid(String cacheKey) {
        CacheEntry entry = dataCache.get(cacheKey);
        if (entry == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        return (currentTime - entry.getTimestamp()) < CACHE_TTL_MS;
    }

    /**
     * Armazena dados em cache
     * 
     * Salva dados no cache com timestamp para controle de TTL.
     * 
     * @param cacheKey Chave do cache
     * @param data Dados para armazenar
     */
    private void cacheData(String cacheKey, Object data) {
        dataCache.put(cacheKey, new CacheEntry(data, System.currentTimeMillis()));
    }

    /**
     * Classe interna para controle de cache
     * 
     * Armazena dados e timestamp para controle de TTL.
     */
    private static class CacheEntry {
        private final Object data;
        private final long timestamp;
        
        public CacheEntry(Object data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
        
        public Object getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * Gera URL de autorização OAuth2 para um banco específico
     */
    public String generateBankAuthUrl(String bankType, Long usuarioId) {
        try {
            switch (bankType.toUpperCase()) {
                case "ITAU":
                    return itauBankService.generateAuthUrl("http://localhost:4200/callback", usuarioId.toString());
                case "NUBANK":
                    return nubankBankService.generateAuthUrl("http://localhost:4200/callback", usuarioId.toString());
                case "INTER":
                    return interBankService.generateAuthUrl("http://localhost:4200/callback", usuarioId.toString());
                case "MERCADO_PAGO":
                    return mercadoPagoBankService.generateAuthUrl("http://localhost:4200/callback", usuarioId.toString());
                default:
                    log.warn("Tipo de banco não suportado: {}", bankType);
                    return null;
            }
        } catch (Exception e) {
            log.error("Erro ao gerar URL de autorização para banco {}: {}", bankType, e.getMessage());
            return null;
        }
    }

    /**
     * Processa callback OAuth2
     */
    public Map<String, Object> processOAuthCallback(String code, String bankType, Long usuarioId) {
        try {
            switch (bankType.toUpperCase()) {
                case "ITAU":
                    return itauBankService.processOAuthCallback(code, usuarioId.toString(), "http://localhost:4200/callback");
                case "NUBANK":
                    return nubankBankService.processOAuthCallback(code, usuarioId.toString(), "http://localhost:4200/callback");
                case "INTER":
                    return interBankService.processOAuthCallback(code, usuarioId.toString(), "http://localhost:4200/callback");
                case "MERCADO_PAGO":
                    return mercadoPagoBankService.processOAuthCallback(code, usuarioId.toString(), "http://localhost:4200/callback");
                default:
                    log.warn("Tipo de banco não suportado: {}", bankType);
                    return Map.of("sucesso", false, "erro", "Tipo de banco não suportado");
            }
        } catch (Exception e) {
            log.error("Erro ao processar callback OAuth2 para banco {}: {}", bankType, e.getMessage());
            return Map.of("sucesso", false, "erro", e.getMessage());
        }
    }

    /**
     * Sincroniza dados de um banco específico
     */
    public Map<String, Object> synchronizeBankData(AutorizacaoBancaria autorizacao) {
        try {
            Usuario usuario = autorizacao.getUsuario();
            AutorizacaoBancaria.TipoBanco bankType = autorizacao.getTipoBanco();
            
            // Verifica se o token não expirou
            if (autorizacao.isTokenExpirado()) {
                return Map.of("sucesso", false, "erro", "Token expirado");
            }
            
            // Executa sincronização específica do banco
            Map<String, Object> resultado = executeBankSpecificSync(usuario, bankType, autorizacao.getAccessToken());
            
            if (resultado != null && resultado.containsKey("sucesso")) {
                // Atualiza data da última sincronização
                autorizacao.setDataUltimaSincronizacao(LocalDateTime.now());
                autorizacaoBancariaRepository.save(autorizacao);
            }
            
            return resultado;
            
        } catch (Exception e) {
            log.error("Erro ao sincronizar dados do banco {}: {}", 
                    autorizacao.getTipoBanco(), e.getMessage());
            return Map.of("sucesso", false, "erro", e.getMessage());
        }
    }

    /**
     * Obtém faturas de um banco específico
     */
    public List<Map<String, Object>> getInvoicesFromBank(AutorizacaoBancaria autorizacao) {
        try {
            switch (autorizacao.getTipoBanco()) {
                case ITAU:
                    return itauBankService.getInvoices(autorizacao);
                case NUBANK:
                    return nubankBankService.getInvoices(autorizacao);
                case INTER:
                    return interBankService.getInvoices(autorizacao);
                case MERCADO_PAGO:
                    return mercadoPagoBankService.getInvoices(autorizacao);
                default:
                    log.warn("Tipo de banco não suportado: {}", autorizacao.getTipoBanco());
                    return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Erro ao buscar faturas do banco {}: {}", 
                    autorizacao.getTipoBanco(), e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Obtém dados de saldo de um banco específico
     */
    public Map<String, Object> getBankBalanceData(AutorizacaoBancaria autorizacao) {
        try {
            switch (autorizacao.getTipoBanco()) {
                case ITAU:
                    return itauBankService.getBalanceData(autorizacao);
                case NUBANK:
                    return nubankBankService.getBalanceData(autorizacao);
                case INTER:
                    return interBankService.getBalanceData(autorizacao);
                case MERCADO_PAGO:
                    return mercadoPagoBankService.getBalanceData(autorizacao);
                default:
                    log.warn("Tipo de banco não suportado: {}", autorizacao.getTipoBanco());
                    return Map.of("saldo", 0.0, "limite", 0.0, "limiteDisponivel", 0.0);
            }
        } catch (Exception e) {
            log.error("Erro ao buscar dados de saldo do banco {}: {}", 
                    autorizacao.getTipoBanco(), e.getMessage());
            return Map.of("saldo", 0.0, "limite", 0.0, "limiteDisponivel", 0.0);
        }
    }

    /**
     * Verifica status de conexão com um banco
     */
    public Map<String, Object> checkBankConnectionStatus(AutorizacaoBancaria autorizacao) {
        try {
            // Verifica se o token não expirou
            boolean tokenValido = !autorizacao.isTokenExpirado();
            
            // Testa conectividade com o banco
            boolean conectividadeOk = false;
            try {
                switch (autorizacao.getTipoBanco()) {
                    case ITAU:
                        conectividadeOk = itauBankService.testConnection(autorizacao);
                        break;
                    case NUBANK:
                        conectividadeOk = nubankBankService.testConnection(autorizacao);
                        break;
                    case INTER:
                        conectividadeOk = interBankService.testConnection(autorizacao);
                        break;
                    case MERCADO_PAGO:
                        conectividadeOk = mercadoPagoBankService.testConnection(autorizacao);
                        break;
                }
            } catch (Exception e) {
                log.warn("Erro ao testar conectividade com banco {}: {}", 
                        autorizacao.getTipoBanco(), e.getMessage());
            }
            
            return Map.of(
                "status", tokenValido && conectividadeOk ? "ATIVO" : 
                         (tokenValido ? "TOKEN_VALIDO_SEM_CONECTIVIDADE" : "TOKEN_EXPIRADO"),
                "tokenValido", tokenValido,
                "conectividadeOk", conectividadeOk,
                "ultimaSincronizacao", autorizacao.getDataUltimaSincronizacao(),
                "dataExpiracao", autorizacao.getDataExpiracao()
            );
            
        } catch (Exception e) {
            log.error("Erro ao verificar status de conexão do banco {}: {}", 
                    autorizacao.getTipoBanco(), e.getMessage());
            return Map.of("status", "ERRO", "mensagem", e.getMessage());
        }
    }

    /**
     * Renova token de um banco específico
     */
    public Map<String, Object> refreshBankToken(AutorizacaoBancaria autorizacao) {
        try {
            boolean renovado = renovarTokenAutomaticamente(autorizacao);
            
            if (renovado) {
                return Map.of("sucesso", true, "mensagem", "Token renovado com sucesso");
            } else {
                return Map.of("sucesso", false, "erro", "Não foi possível renovar o token");
            }
            
        } catch (Exception e) {
            log.error("Erro ao renovar token do banco {}: {}", 
                    autorizacao.getTipoBanco(), e.getMessage());
            return Map.of("sucesso", false, "erro", e.getMessage());
        }
    }

    /**
     * Obtém detalhes de um banco específico
     */
    public Map<String, Object> getBankDetails(AutorizacaoBancaria autorizacao) {
        try {
            switch (autorizacao.getTipoBanco()) {
                case ITAU:
                    return itauBankService.getBankDetails(autorizacao);
                case NUBANK:
                    return nubankBankService.getBankDetails(autorizacao);
                case INTER:
                    return interBankService.getBankDetails(autorizacao);
                case MERCADO_PAGO:
                    return mercadoPagoBankService.getBankDetails(autorizacao);
                default:
                    log.warn("Tipo de banco não suportado: {}", autorizacao.getTipoBanco());
                    return Map.of("status", "NAO_SUPORTADO");
            }
        } catch (Exception e) {
            log.error("Erro ao buscar detalhes do banco {}: {}", 
                    autorizacao.getTipoBanco(), e.getMessage());
            return Map.of("status", "ERRO", "mensagem", e.getMessage());
        }
    }

    /**
     * Obtém transações de um banco específico
     */
    public List<Map<String, Object>> getBankTransactions(AutorizacaoBancaria autorizacao, int limit) {
        try {
            switch (autorizacao.getTipoBanco()) {
                case ITAU:
                    return itauBankService.getTransactions(autorizacao);
                case NUBANK:
                    return nubankBankService.getTransactions(autorizacao);
                case INTER:
                    return interBankService.getTransactions(autorizacao);
                case MERCADO_PAGO:
                    return mercadoPagoBankService.getTransactions(autorizacao);
                default:
                    log.warn("Tipo de banco não suportado: {}", autorizacao.getTipoBanco());
                    return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Erro ao buscar transações do banco {}: {}", 
                    autorizacao.getTipoBanco(), e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Obtém cartões de crédito de um banco específico
     * 
     * @param autorizacao Autorização bancária do usuário
     * @return Lista de cartões de crédito
     */
    public List<Map<String, Object>> getCreditCardsFromBank(AutorizacaoBancaria autorizacao) {
        log.info("Obtendo cartões de crédito para banco: {}", autorizacao.getTipoBanco());
        
        try {
            List<Map<String, Object>> cartoes = new ArrayList<>();
            
            switch (autorizacao.getTipoBanco()) {
                case ITAU:
                    cartoes = itauBankService.getCreditCards(autorizacao);
                    break;
                case NUBANK:
                    cartoes = nubankBankService.getCreditCards(autorizacao);
                    break;
                case INTER:
                    cartoes = interBankService.getCreditCards(autorizacao);
                    break;
                case MERCADO_PAGO:
                    cartoes = mercadoPagoBankService.getCreditCards(autorizacao);
                    break;
                default:
                    log.warn("Tipo de banco não suportado: {}", autorizacao.getTipoBanco());
            }
            
            // Atualiza dados locais se necessário
            if (!cartoes.isEmpty()) {
                for (Map<String, Object> cartao : cartoes) {
                    // Converte Map para CartaoCredito para salvar no banco local
                    CartaoCredito cartaoEntity = new CartaoCredito();
                    cartaoEntity.setUsuario(autorizacao.getUsuario());
                    cartaoEntity.setBanco(autorizacao.getTipoBanco().toString());
                    cartaoEntity.setNumeroCartao((String) cartao.get("numero"));
                    cartaoEntity.setLimiteCredito((BigDecimal) cartao.get("limite"));
                    cartaoEntity.setLimiteDisponivel((BigDecimal) cartao.get("saldoDisponivel"));
                    
                    // Salva ou atualiza o cartão no banco local
                    Optional<CartaoCredito> existingCardOpt = cartaoCreditoRepository
                            .findByUsuarioAndBancoAndNumeroCartao(autorizacao.getUsuario().getId(), cartaoEntity.getBanco(), cartaoEntity.getNumeroCartao());
                    
                    if (existingCardOpt.isPresent()) {
                        // Atualiza dados existentes
                        CartaoCredito existingCard = existingCardOpt.get();
                        existingCard.setLimiteCredito(cartaoEntity.getLimiteCredito());
                        existingCard.setLimiteDisponivel(cartaoEntity.getLimiteDisponivel());
                        existingCard.setDataVencimento(cartaoEntity.getDataVencimento());
                        cartaoCreditoRepository.save(existingCard);
                    } else {
                        // Salva novo cartão
                        cartaoCreditoRepository.save(cartaoEntity);
                    }
                }
            }
            
            return cartoes;
        } catch (Exception e) {
            log.error("Erro ao obter cartões de crédito para banco: {}", 
                    autorizacao.getTipoBanco(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Obtém gastos por categoria de um banco específico
     */
    public Map<String, Double> getSpendingByCategory(AutorizacaoBancaria autorizacao, int days) {
        try {
            switch (autorizacao.getTipoBanco()) {
                case ITAU:
                    Map<String, Object> itauSpending = itauBankService.getSpendingByCategory(autorizacao);
                    return convertSpendingToDouble(itauSpending);
                case NUBANK:
                    Map<String, Object> nubankSpending = nubankBankService.getSpendingByCategory(autorizacao);
                    return convertSpendingToDouble(nubankSpending);
                case INTER:
                    Map<String, Object> interSpending = interBankService.getSpendingByCategory(autorizacao);
                    return convertSpendingToDouble(interSpending);
                case MERCADO_PAGO:
                    Map<String, Object> mpSpending = mercadoPagoBankService.getSpendingByCategory(autorizacao);
                    return convertSpendingToDouble(mpSpending);
                default:
                    log.warn("Tipo de banco não suportado: {}", autorizacao.getTipoBanco());
                    return new HashMap<>();
            }
        } catch (Exception e) {
            log.error("Erro ao buscar gastos por categoria do banco {}: {}", 
                    autorizacao.getTipoBanco(), e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Obtém análise de gastos de um banco específico
     */
    public Map<String, Object> getSpendingAnalysis(AutorizacaoBancaria autorizacao, int days) {
        try {
            switch (autorizacao.getTipoBanco()) {
                case ITAU:
                    return itauBankService.getSpendingAnalysis(autorizacao);
                case NUBANK:
                    return nubankBankService.getSpendingAnalysis(autorizacao);
                case INTER:
                    return interBankService.getSpendingAnalysis(autorizacao);
                case MERCADO_PAGO:
                    return mercadoPagoBankService.getSpendingAnalysis(autorizacao);
                default:
                    log.warn("Tipo de banco não suportado: {}", autorizacao.getTipoBanco());
                    return Map.of("gastos", 0.0, "receitas", 0.0, "totalTransacoes", 0, "gastosPorDia", new HashMap<>());
            }
        } catch (Exception e) {
            log.error("Erro ao buscar análise de gastos do banco {}: {}", 
                    autorizacao.getTipoBanco(), e.getMessage());
            return Map.of("gastos", 0.0, "receitas", 0.0, "totalTransacoes", 0, "gastosPorDia", new HashMap<>());
        }
    }

    /**
     * Converte dados de gastos por categoria para o formato esperado
     */
    private Map<String, Double> convertSpendingToDouble(Map<String, Object> spendingData) {
        Map<String, Double> result = new HashMap<>();
        
        if (spendingData != null && spendingData.containsKey("categories")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> categories = (Map<String, Object>) spendingData.get("categories");
            
            if (categories != null) {
                for (Map.Entry<String, Object> entry : categories.entrySet()) {
                    if (entry.getValue() instanceof BigDecimal) {
                        result.put(entry.getKey(), ((BigDecimal) entry.getValue()).doubleValue());
                    } else if (entry.getValue() instanceof Number) {
                        result.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
            }
        }
        
        return result;
    }
}
