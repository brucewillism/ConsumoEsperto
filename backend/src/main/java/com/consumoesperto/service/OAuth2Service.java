package com.consumoesperto.service;

import com.consumoesperto.dto.AuthResponse;
import com.consumoesperto.dto.GoogleUserInfo;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Serviço para autenticação OAuth2 com Google
 * 
 * Este serviço gerencia o processo de autenticação OAuth2,
 * incluindo a obtenção de informações do usuário e criação/atualização
 * de registros no banco de dados.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2Service {

    private final UsuarioRepository usuarioRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;
    private final NomeGeneroInferenciaService nomeGeneroInferenciaService;
    private final JarvisProtocolService jarvisProtocolService;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${oauth2.google.redirect-uri:http://localhost:4200/login}")
    private String googleRedirectUri;

    /**
     * URL da API do Google para obter informações do usuário
     */
    private static final String GOOGLE_USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";

    /**
     * Processa autenticação OAuth2 com Google
     * 
     * @param accessToken Token de acesso do Google
     * @return Resposta de autenticação com JWT e dados do usuário
     */
    public AuthResponse processGoogleOAuth2(String accessToken) {
        try {
            log.info("🔐 Processando autenticação OAuth2 com Google...");

            // 1. Obter informações do usuário do Google
            GoogleUserInfo googleUserInfo = getGoogleUserInfo(accessToken);
            log.info("👤 Usuário Google obtido: {}", googleUserInfo.getEmail());

            // 2. Buscar ou criar usuário no banco
            Usuario usuario = findOrCreateUsuario(googleUserInfo);
            log.info("💾 Usuário processado: ID={}, Email={}", usuario.getId(), usuario.getEmail());

            // 3. Gerar token JWT
            String jwtToken = jwtTokenProvider.generateToken(usuario.getUsername());
            log.info("🎫 Token JWT gerado com sucesso");

            // 4. Construir resposta
            return buildAuthResponse(jwtToken, usuario);

        } catch (Exception e) {
            log.error("❌ Erro ao processar OAuth2: {}", e.getMessage(), e);
            throw new RuntimeException("Falha na autenticação OAuth2", e);
        }
    }

    public AuthResponse processGoogleOAuth2ByAuthorizationCode(String authorizationCode) {
        String accessToken = exchangeAuthorizationCodeForAccessToken(authorizationCode);
        return processGoogleOAuth2(accessToken);
    }

    /**
     * Obtém informações do usuário do Google usando o token de acesso
     */
    private GoogleUserInfo getGoogleUserInfo(String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<GoogleUserInfo> response = restTemplate.exchange(
                GOOGLE_USER_INFO_URL,
                HttpMethod.GET,
                entity,
                GoogleUserInfo.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                GoogleUserInfo userInfo = java.util.Objects.requireNonNull(response.getBody());
                userInfo.setAccess_token(accessToken);
                return userInfo;
            } else {
                throw new RuntimeException("Falha ao obter informações do usuário do Google");
            }

        } catch (Exception e) {
            log.error("❌ Erro ao obter informações do Google: {}", e.getMessage());
            throw new RuntimeException("Não foi possível obter informações do usuário do Google", e);
        }
    }

    private static String primeiroNomeGoogle(GoogleUserInfo info) {
        if (info.getGiven_name() != null && !info.getGiven_name().isBlank()) {
            return info.getGiven_name().trim().split("\\s+")[0];
        }
        if (info.getName() != null && !info.getName().isBlank()) {
            return info.getName().trim().split("\\s+")[0];
        }
        return "";
    }

    /**
     * Inferência inicial pelo primeiro nome ({@link NomeGeneroInferenciaService}); confirmação final no frontend.
     */
    private void aplicarPerfilGenero(Usuario usuario, GoogleUserInfo googleUserInfo) {
        if (Boolean.TRUE.equals(usuario.getGeneroConfirmado())) {
            return;
        }

        String primeiro = primeiroNomeGoogle(googleUserInfo);
        Optional<Usuario.GeneroUsuario> inferido = nomeGeneroInferenciaService.inferirPrimeiroNome(primeiro);
        if (inferido.isPresent()) {
            usuario.setGenero(inferido.get());
            usuario.setGeneroConfirmado(false);
        } else {
            usuario.setGenero(Usuario.GeneroUsuario.UNKNOWN);
            usuario.setGeneroConfirmado(false);
        }
    }

    private String exchangeAuthorizationCodeForAccessToken(String authorizationCode) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded");

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("code", authorizationCode);
            form.add("client_id", googleClientId);
            form.add("client_secret", googleClientSecret);
            form.add("redirect_uri", googleRedirectUri);
            form.add("grant_type", "authorization_code");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                GOOGLE_TOKEN_URL,
                HttpMethod.POST,
                request,
                String.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Falha ao trocar código por access token");
            }

            com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody());
            String accessToken = json.path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw new RuntimeException("Access token não retornado pelo Google");
            }
            return accessToken;
        } catch (Exception e) {
            throw new RuntimeException("Não foi possível trocar o código OAuth2 por token", e);
        }
    }

    /**
     * Busca usuário existente ou cria novo baseado nas informações do Google
     */
    private Usuario findOrCreateUsuario(GoogleUserInfo googleUserInfo) {
        // 1. Tentar encontrar por Google ID
        Optional<Usuario> existingByGoogleId = usuarioRepository.findByGoogleId(googleUserInfo.getId());
        if (existingByGoogleId.isPresent()) {
            Usuario usuario = existingByGoogleId.get();
            updateUsuarioFromGoogle(usuario, googleUserInfo);
            return usuarioRepository.save(usuario);
        }

        // 2. Tentar encontrar por email
        Optional<Usuario> existingByEmail = usuarioRepository.findByEmail(googleUserInfo.getEmail());
        if (existingByEmail.isPresent()) {
            Usuario usuario = existingByEmail.get();
            // Se encontrou por email mas não tem Google ID, atualizar
            if (usuario.getGoogleId() == null) {
                usuario.setGoogleId(googleUserInfo.getId());
                usuario.setProvedorAuth(Usuario.ProvedorAuth.GOOGLE);
                updateUsuarioFromGoogle(usuario, googleUserInfo);
                return usuarioRepository.save(usuario);
            }
            // Se já tem Google ID, atualizar informações
            updateUsuarioFromGoogle(usuario, googleUserInfo);
            return usuarioRepository.save(usuario);
        }

        // 3. Criar novo usuário
        Usuario newUsuario = createUsuarioFromGoogle(googleUserInfo);
        return usuarioRepository.save(newUsuario);
    }

    /**
     * Cria novo usuário baseado nas informações do Google
     */
    private Usuario createUsuarioFromGoogle(GoogleUserInfo googleUserInfo) {
        log.info("🆕 Criando novo usuário para: {}", googleUserInfo.getEmail());

        Usuario usuario = new Usuario();
        usuario.setGoogleId(googleUserInfo.getId());
        usuario.setEmail(googleUserInfo.getEmail());
        usuario.setNome(googleUserInfo.getName());
        usuario.setUsername(generateUsername(googleUserInfo.getEmail()));
        usuario.setFotoUrl(googleUserInfo.getPicture());
        usuario.setLocale(googleUserInfo.getLocale());
        usuario.setEmailVerificado(googleUserInfo.getVerified_email());
        usuario.setProvedorAuth(Usuario.ProvedorAuth.GOOGLE);
        usuario.setDataCriacao(LocalDateTime.now());
        usuario.setUltimoAcesso(LocalDateTime.now());
        
        // Para usuários OAuth2, definir uma senha padrão (será ignorada no login)
        usuario.setPassword("OAUTH2_USER_" + System.currentTimeMillis());

        aplicarPerfilGenero(usuario, googleUserInfo);

        return usuario;
    }

    /**
     * Atualiza usuário existente com informações do Google
     */
    private void updateUsuarioFromGoogle(Usuario usuario, GoogleUserInfo googleUserInfo) {
        log.info("🔄 Atualizando usuário existente: {}", usuario.getEmail());

        usuario.setNome(googleUserInfo.getName());
        usuario.setFotoUrl(googleUserInfo.getPicture());
        usuario.setLocale(googleUserInfo.getLocale());
        usuario.setEmailVerificado(googleUserInfo.getVerified_email());
        usuario.setUltimoAcesso(LocalDateTime.now());
        
        // Garantir que o provedor de autenticação seja GOOGLE
        usuario.setProvedorAuth(Usuario.ProvedorAuth.GOOGLE);
        
        // Para usuários OAuth2, definir uma senha padrão (será ignorada no login)
        if (usuario.getPassword() == null || usuario.getPassword().trim().isEmpty()) {
            usuario.setPassword("OAUTH2_USER_" + System.currentTimeMillis());
        }

        aplicarPerfilGenero(usuario, googleUserInfo);
    }

    /**
     * Gera username único baseado no email
     */
    private String generateUsername(String email) {
        String baseUsername = email.split("@")[0];
        String username = baseUsername;
        int counter = 1;

        while (usuarioRepository.findByUsername(username).isPresent()) {
            username = baseUsername + counter;
            counter++;
        }

        return username;
    }

    /**
     * Constrói resposta de autenticação
     */
    private AuthResponse buildAuthResponse(String jwtToken, Usuario usuario) {
        AuthResponse.UserInfo userInfo = AuthResponse.UserInfo.builder()
            .id(usuario.getId())
            .username(usuario.getUsername())
            .email(usuario.getEmail())
            .nome(usuario.getNome())
            .fotoUrl(usuario.getFotoUrl())
            .googleId(usuario.getGoogleId())
            .provedorAuth(usuario.getProvedorAuth().name())
            .dataCriacao(usuario.getDataCriacao())
            .ultimoAcesso(usuario.getUltimoAcesso())
            .jarvisConfigurado(Boolean.TRUE.equals(usuario.getJarvisConfigurado()))
            .tratamento(usuario.getTratamento())
            .jarvisTratamentoResumo(jarvisProtocolService.montarVocativoCompleto(usuario))
            .genero(usuario.getGenero() != null ? usuario.getGenero().name() : Usuario.GeneroUsuario.UNKNOWN.name())
            .generoConfirmado(usuario.getGeneroConfirmado())
            .preferenciaTratamentoJarvis(
                usuario.getPreferenciaTratamentoJarvis() != null
                    ? usuario.getPreferenciaTratamentoJarvis().name()
                    : Usuario.PreferenciaTratamentoJarvis.AUTOMATICO.name())
            .build();

        return AuthResponse.builder()
            .token(jwtToken)
            .tokenType("Bearer")
            .expiresIn((long) jwtTokenProvider.getExpirationTime())
            .user(userInfo)
            .authenticatedAt(LocalDateTime.now())
            .build();
    }
}
