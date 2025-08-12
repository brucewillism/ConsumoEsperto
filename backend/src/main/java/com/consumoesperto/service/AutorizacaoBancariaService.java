package com.consumoesperto.service;

import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serviço responsável por gerenciar autorizações bancárias OAuth2
 * 
 * Este serviço gerencia todo o ciclo de vida das autorizações OAuth2
 * com bancos, incluindo criação, renovação, validação e revogação
 * de tokens de acesso.
 * 
 * Funcionalidades principais:
 * - Salvamento de autorizações OAuth2
 * - Renovação automática de tokens expirados
 * - Validação de tokens de acesso
 * - Gerenciamento de escopos de permissão
 * - Auditoria de uso de autorizações
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutorizacaoBancariaService {

    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * Salva uma nova autorização bancária
     * 
     * @param userId ID do usuário
     * @param bankType Tipo do banco
     * @param tokenResponse Resposta do banco com tokens
     * @return Autorização salva
     */
    @Transactional
    public AutorizacaoBancaria salvarAutorizacao(Long userId, BankApiService.BankType bankType, Map<String, Object> tokenResponse) {
        try {
            // Busca o usuário
            Usuario usuario = usuarioRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado: " + userId));

            // Verifica se já existe uma autorização para este usuário e banco
            Optional<AutorizacaoBancaria> autorizacaoExistente = autorizacaoBancariaRepository
                    .findByUsuarioIdAndTipoBanco(userId, mapBankType(bankType));

            if (autorizacaoExistente.isPresent()) {
                // Atualiza autorização existente
                return atualizarAutorizacao(autorizacaoExistente.get(), tokenResponse);
            } else {
                // Cria nova autorização
                return criarNovaAutorizacao(usuario, bankType, tokenResponse);
            }

        } catch (Exception e) {
            log.error("Erro ao salvar autorização bancária para usuário {} e banco {}", userId, bankType, e);
            throw new RuntimeException("Falha ao salvar autorização bancária", e);
        }
    }

    /**
     * Cria uma nova autorização bancária
     * 
     * @param usuario Usuário que concedeu a autorização
     * @param bankType Tipo do banco
     * @param tokenResponse Resposta do banco com tokens
     * @return Nova autorização criada
     */
    private AutorizacaoBancaria criarNovaAutorizacao(Usuario usuario, BankApiService.BankType bankType, Map<String, Object> tokenResponse) {
        AutorizacaoBancaria autorizacao = new AutorizacaoBancaria();
        
        autorizacao.setUsuario(usuario);
        autorizacao.setTipoBanco(mapBankType(bankType));
        autorizacao.setAccessToken(extractAccessToken(tokenResponse));
        autorizacao.setRefreshToken(extractRefreshToken(tokenResponse));
        autorizacao.setDataExpiracao(extractExpirationDate(tokenResponse));
        autorizacao.setEscopo(extractScope(tokenResponse));
        autorizacao.setStatus(AutorizacaoBancaria.StatusAutorizacao.ATIVA);
        autorizacao.setDataCriacao(LocalDateTime.now());
        autorizacao.setDataAtualizacao(LocalDateTime.now());
        
        AutorizacaoBancaria autorizacaoSalva = autorizacaoBancariaRepository.save(autorizacao);
        log.info("Nova autorização bancária criada: {} para usuário {} e banco {}", 
                autorizacaoSalva.getId(), usuario.getId(), bankType);
        
        return autorizacaoSalva;
    }

    /**
     * Atualiza uma autorização bancária existente
     * 
     * @param autorizacaoExistente Autorização existente
     * @param tokenResponse Nova resposta do banco com tokens
     * @return Autorização atualizada
     */
    private AutorizacaoBancaria atualizarAutorizacao(AutorizacaoBancaria autorizacaoExistente, Map<String, Object> tokenResponse) {
        autorizacaoExistente.setAccessToken(extractAccessToken(tokenResponse));
        autorizacaoExistente.setRefreshToken(extractRefreshToken(tokenResponse));
        autorizacaoExistente.setDataExpiracao(extractExpirationDate(tokenResponse));
        autorizacaoExistente.setEscopo(extractScope(tokenResponse));
        autorizacaoExistente.setStatus(AutorizacaoBancaria.StatusAutorizacao.ATIVA);
        autorizacaoExistente.setDataAtualizacao(LocalDateTime.now());
        autorizacaoExistente.incrementarContadorRenovacoes();
        
        AutorizacaoBancaria autorizacaoAtualizada = autorizacaoBancariaRepository.save(autorizacaoExistente);
        log.info("Autorização bancária atualizada: {} para usuário {} e banco {}", 
                autorizacaoAtualizada.getId(), autorizacaoExistente.getUsuario().getId(), 
                autorizacaoExistente.getTipoBanco());
        
        return autorizacaoAtualizada;
    }

    /**
     * Busca autorizações ativas de um usuário
     * 
     * @param userId ID do usuário
     * @return Lista de autorizações ativas
     */
    public List<AutorizacaoBancaria> buscarAutorizacoesAtivas(Long userId) {
        return autorizacaoBancariaRepository.findByUsuarioIdAndStatus(userId, AutorizacaoBancaria.StatusAutorizacao.ATIVA);
    }

    /**
     * Busca todas as autorizações de um usuário
     * 
     * @param userId ID do usuário
     * @return Lista de todas as autorizações
     */
    public List<AutorizacaoBancaria> buscarAutorizacoesPorUsuario(Long userId) {
        return autorizacaoBancariaRepository.findByUsuarioId(userId);
    }

    /**
     * Busca autorização de um usuário para um banco específico
     * 
     * @param userId ID do usuário
     * @param bankType Tipo do banco
     * @return Optional com a autorização se encontrada
     */
    public Optional<AutorizacaoBancaria> buscarAutorizacao(Long userId, BankApiService.BankType bankType) {
        return autorizacaoBancariaRepository.findByUsuarioIdAndTipoBanco(userId, mapBankType(bankType));
    }

    /**
     * Verifica se um usuário possui autorização ativa para um banco
     * 
     * @param userId ID do usuário
     * @param bankType Tipo do banco
     * @return true se possui autorização ativa
     */
    public boolean possuiAutorizacaoAtiva(Long userId, BankApiService.BankType bankType) {
        return autorizacaoBancariaRepository.existsByUsuarioIdAndTipoBancoAndStatus(
                userId, mapBankType(bankType), AutorizacaoBancaria.StatusAutorizacao.ATIVA);
    }

    /**
     * Renova um token de acesso expirado
     * 
     * @param autorizacao Autorização que precisa ser renovada
     * @param newTokenResponse Nova resposta do banco com tokens
     * @return Autorização renovada
     */
    @Transactional
    public AutorizacaoBancaria renovarToken(AutorizacaoBancaria autorizacao, Map<String, Object> newTokenResponse) {
        autorizacao.setAccessToken(extractAccessToken(newTokenResponse));
        autorizacao.setRefreshToken(extractRefreshToken(newTokenResponse));
        autorizacao.setDataExpiracao(extractExpirationDate(newTokenResponse));
        autorizacao.setDataAtualizacao(LocalDateTime.now());
        autorizacao.incrementarContadorRenovacoes();
        
        AutorizacaoBancaria autorizacaoRenovada = autorizacaoBancariaRepository.save(autorizacao);
        log.info("Token renovado para autorização: {} do usuário {} e banco {}", 
                autorizacaoRenovada.getId(), autorizacao.getUsuario().getId(), 
                autorizacao.getTipoBanco());
        
        return autorizacaoRenovada;
    }

    /**
     * Revoga uma autorização bancária
     * 
     * @param autorizacaoId ID da autorização
     * @param userId ID do usuário (para validação)
     */
    @Transactional
    public void revogarAutorizacao(Long autorizacaoId, Long userId) {
        Optional<AutorizacaoBancaria> autorizacaoOpt = autorizacaoBancariaRepository.findById(autorizacaoId);
        
        if (autorizacaoOpt.isPresent()) {
            AutorizacaoBancaria autorizacao = autorizacaoOpt.get();
            
            // Valida se a autorização pertence ao usuário
            if (!autorizacao.getUsuario().getId().equals(userId)) {
                throw new RuntimeException("Autorização não pertence ao usuário");
            }
            
            autorizacao.setStatus(AutorizacaoBancaria.StatusAutorizacao.REVOGADA);
            autorizacao.setDataAtualizacao(LocalDateTime.now());
            
            autorizacaoBancariaRepository.save(autorizacao);
            log.info("Autorização bancária revogada: {} do usuário {} e banco {}", 
                    autorizacaoId, userId, autorizacao.getTipoBanco());
        }
    }

    /**
     * Busca autorizações que precisam ser renovadas
     * 
     * @return Lista de autorizações que precisam de renovação
     */
    public List<AutorizacaoBancaria> buscarAutorizacoesParaRenovacao() {
        LocalDateTime dataLimite = LocalDateTime.now().plusHours(1); // 1 hora antes da expiração
        return autorizacaoBancariaRepository.findAutorizacoesParaRenovacao(dataLimite);
    }

    /**
     * Busca uma autorização por ID
     * 
     * @param id ID da autorização
     * @return Autorização encontrada ou vazio
     */
    public Optional<AutorizacaoBancaria> buscarPorId(Long id) {
        return autorizacaoBancariaRepository.findById(id);
    }

    /**
     * Marca uma autorização como utilizada
     * 
     * @param autorizacaoId ID da autorização
     */
    @Transactional
    public void marcarComoUtilizada(Long autorizacaoId) {
        Optional<AutorizacaoBancaria> autorizacaoOpt = autorizacaoBancariaRepository.findById(autorizacaoId);
        
        if (autorizacaoOpt.isPresent()) {
            AutorizacaoBancaria autorizacao = autorizacaoOpt.get();
            autorizacao.marcarComoUtilizada();
            autorizacaoBancariaRepository.save(autorizacao);
        }
    }

    /**
     * Mapeia o tipo de banco do serviço para o enum do modelo
     * 
     * @param bankType Tipo do banco do serviço
     * @return Tipo do banco do modelo
     */
    private AutorizacaoBancaria.TipoBanco mapBankType(BankApiService.BankType bankType) {
        switch (bankType) {
            case NUBANK:
                return AutorizacaoBancaria.TipoBanco.NUBANK;
            case ITAU:
                return AutorizacaoBancaria.TipoBanco.ITAU;
            case INTER:
                return AutorizacaoBancaria.TipoBanco.INTER;
            case MERCADO_PAGO:
                return AutorizacaoBancaria.TipoBanco.MERCADO_PAGO;
            default:
                throw new IllegalArgumentException("Tipo de banco não suportado: " + bankType);
        }
    }

    /**
     * Extrai o token de acesso da resposta do banco
     * 
     * @param tokenResponse Resposta do banco
     * @return Token de acesso
     */
    private String extractAccessToken(Map<String, Object> tokenResponse) {
        Object accessToken = tokenResponse.get("access_token");
        if (accessToken == null) {
            throw new RuntimeException("Token de acesso não encontrado na resposta do banco");
        }
        return accessToken.toString();
    }

    /**
     * Extrai o refresh token da resposta do banco
     * 
     * @param tokenResponse Resposta do banco
     * @return Refresh token
     */
    private String extractRefreshToken(Map<String, Object> tokenResponse) {
        Object refreshToken = tokenResponse.get("refresh_token");
        if (refreshToken == null) {
            throw new RuntimeException("Refresh token não encontrado na resposta do banco");
        }
        return refreshToken.toString();
    }

    /**
     * Extrai a data de expiração da resposta do banco
     * 
     * @param tokenResponse Resposta do banco
     * @return Data de expiração
     */
    private LocalDateTime extractExpirationDate(Map<String, Object> tokenResponse) {
        Object expiresIn = tokenResponse.get("expires_in");
        if (expiresIn == null) {
            // Se não houver expires_in, assume 1 hora
            return LocalDateTime.now().plusHours(1);
        }
        
        try {
            int seconds = Integer.parseInt(expiresIn.toString());
            return LocalDateTime.now().plusSeconds(seconds);
        } catch (NumberFormatException e) {
            log.warn("Não foi possível parsear expires_in: {}, usando 1 hora como padrão", expiresIn);
            return LocalDateTime.now().plusHours(1);
        }
    }

    /**
     * Extrai o escopo da resposta do banco
     * 
     * @param tokenResponse Resposta do banco
     * @return Escopo de permissões
     */
    private String extractScope(Map<String, Object> tokenResponse) {
        Object scope = tokenResponse.get("scope");
        if (scope == null) {
            return "read"; // Escopo padrão
        }
        return scope.toString();
    }

    /**
     * Remove uma autorização bancária (alias para revogarAutorizacao)
     */
    @Transactional
    public void removerAutorizacao(Long autorizacaoId) {
        try {
            // Busca a autorização
            AutorizacaoBancaria autorizacao = autorizacaoBancariaRepository.findById(autorizacaoId)
                    .orElseThrow(() -> new RuntimeException("Autorização não encontrada: " + autorizacaoId));

            // Marca como revogada
            autorizacao.setStatus(AutorizacaoBancaria.StatusAutorizacao.REVOGADA);
            autorizacao.setDataAtualizacao(LocalDateTime.now());
            
            autorizacaoBancariaRepository.save(autorizacao);
            
            log.info("Autorização bancária removida: {} ", autorizacaoId);
            
        } catch (Exception e) {
            log.error("Erro ao remover autorização bancária {}", autorizacaoId, e);
            throw new RuntimeException("Falha ao remover autorização bancária", e);
        }
    }
}
