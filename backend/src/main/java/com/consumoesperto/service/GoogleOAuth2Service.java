package com.consumoesperto.service;

import com.consumoesperto.dto.UsuarioDTO;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Serviço responsável pela autenticação e autorização via Google OAuth2
 * 
 * Este serviço implementa a integração com o Google OAuth2 para permitir
 * que usuários se autentiquem usando suas contas do Google. Oferece
 * funcionalidades de verificação de tokens, autenticação de usuários e
 * criação automática de contas para novos usuários.
 * 
 * Funcionalidades principais:
 * - Autenticação de usuários via Google OAuth2
 * - Verificação de tokens ID do Google
 * - Criação automática de contas para novos usuários
 * - Atualização de informações de acesso para usuários existentes
 * - Conversão de dados do Google para o modelo interno da aplicação
 * - Validação de email verificado pelo Google
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor // Lombok: gera construtor com campos final automaticamente
@Slf4j // Lombok: fornece logger automático para a classe
public class GoogleOAuth2Service {

    // Repositório para operações de usuário (criação, busca, atualização)
    private final UsuarioRepository usuarioRepository;
    
    // Encoder de senha para gerar senhas temporárias seguras
    private final PasswordEncoder passwordEncoder;

    // Client ID do Google OAuth2 configurado nas propriedades da aplicação
    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    /**
     * Autentica um usuário via Google OAuth2 usando um token ID
     * 
     * Este método verifica a validade do token ID do Google, extrai as informações
     * do usuário e cria ou atualiza a conta correspondente no sistema.
     * 
     * Processo de autenticação:
     * 1. Verifica a validade do token ID usando a biblioteca oficial do Google
     * 2. Extrai informações do usuário do payload do token (ID, email, nome, foto)
     * 3. Valida se o email foi verificado pelo Google
     * 4. Busca usuário existente por email ou cria nova conta
     * 5. Atualiza informações de acesso para usuários existentes
     * 6. Retorna DTO com dados do usuário autenticado
     * 
     * @param idTokenString Token ID do Google recebido do frontend
     * @return UsuarioDTO com dados do usuário autenticado
     * @throws RuntimeException se o token for inválido, email não verificado ou erro na autenticação
     */
    public UsuarioDTO authenticateGoogleUser(String idTokenString) {
        try {
            // Cria verificador de token usando biblioteca oficial do Google
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            // Verifica a validade do token ID
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                // Extrai informações do usuário do payload do token
                Payload payload = idToken.getPayload();

                // Obter informações do usuário do token
                String userId = payload.getSubject(); // ID único do usuário no Google
                String email = payload.getEmail(); // Email do usuário
                String name = (String) payload.get("name"); // Nome completo do usuário
                String picture = (String) payload.get("picture"); // URL da foto do perfil
                boolean emailVerified = Boolean.valueOf(payload.getEmailVerified()); // Status de verificação do email

                // Validação de segurança: apenas emails verificados pelo Google são aceitos
                if (!emailVerified) {
                    throw new RuntimeException("Email do Google não verificado");
                }

                // Verificar se o usuário já existe no sistema
                Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
                
                if (usuario == null) {
                    // Criar novo usuário para primeiro acesso via Google
                    usuario = new Usuario();
                    usuario.setEmail(email);
                    usuario.setNome(name);
                    usuario.setUsername(email); // Usar email como username para simplicidade
                    // Gera senha temporária única baseada no ID do Google
                    usuario.setPassword(passwordEncoder.encode("google_oauth_" + userId));
                    
                    // Salva o novo usuário no banco de dados
                    usuario = usuarioRepository.save(usuario);
                    log.info("Novo usuário criado via Google OAuth2: {}", email);
                } else {
                    // Atualizar último acesso para usuário existente
                    usuario.setUltimoAcesso(java.time.LocalDateTime.now());
                    usuario = usuarioRepository.save(usuario);
                    log.info("Usuário existente autenticado via Google OAuth2: {}", email);
                }

                // Retorna DTO com dados do usuário autenticado
                return converterParaDTO(usuario);
            } else {
                // Token inválido ou expirado
                throw new RuntimeException("Token do Google inválido");
            }
        } catch (Exception e) {
            // Log do erro para debugging e auditoria
            log.error("Erro ao autenticar usuário via Google OAuth2", e);
            throw new RuntimeException("Falha na autenticação via Google", e);
        }
    }

    /**
     * Verifica se um token ID do Google é válido
     * 
     * Este método verifica a validade de um token ID sem realizar
     * a autenticação completa do usuário. Útil para validações
     * rápidas de tokens.
     * 
     * Processo de verificação:
     * 1. Cria verificador de token usando biblioteca oficial do Google
     * 2. Verifica a validade do token ID fornecido
     * 3. Retorna true se válido, false caso contrário
     * 
     * @param idTokenString Token ID do Google a ser verificado
     * @return true se o token for válido, false caso contrário
     */
    public boolean verifyGoogleToken(String idTokenString) {
        try {
            // Cria verificador de token usando biblioteca oficial do Google
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            // Verifica a validade do token ID
            GoogleIdToken idToken = verifier.verify(idTokenString);
            return idToken != null;
        } catch (Exception e) {
            // Log do erro para debugging
            log.error("Erro ao verificar token do Google", e);
            return false;
        }
    }

    /**
     * Converte um objeto Usuario para UsuarioDTO
     * 
     * Método utilitário para converter o modelo interno de usuário
     * para o DTO que será retornado para o frontend, ocultando
     * informações sensíveis como senha.
     * 
     * Dados convertidos:
     * - ID do usuário
     * - Username (email)
     * - Email
     * - Nome completo
     * - Data de criação da conta
     * - Último acesso
     * 
     * @param usuario Objeto Usuario do modelo interno
     * @return UsuarioDTO com dados seguros para o frontend
     */
    private UsuarioDTO converterParaDTO(Usuario usuario) {
        UsuarioDTO dto = new UsuarioDTO();
        dto.setId(usuario.getId());
        dto.setUsername(usuario.getUsername());
        dto.setEmail(usuario.getEmail());
        dto.setNome(usuario.getNome());
        dto.setDataCriacao(usuario.getDataCriacao());
        dto.setUltimoAcesso(usuario.getUltimoAcesso());
        return dto;
    }
}
