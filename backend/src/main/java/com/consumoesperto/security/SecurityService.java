package com.consumoesperto.security;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Serviço de Segurança para Verificações de Autorização
 * 
 * Este serviço implementa verificações de autorização específicas para recursos,
 * permitindo controle granular de acesso baseado em propriedades dos recursos
 * e contexto do usuário autenticado.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service("securityService")
@RequiredArgsConstructor
@Slf4j
public class SecurityService {

    private final UsuarioRepository usuarioRepository;

    /**
     * Verifica se o usuário pode acessar dados de outro usuário
     */
    public boolean canAccessUserData(Long targetUserId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return false;
            }

            UserPrincipal userPrincipal = (UserPrincipal) auth.getPrincipal();
            
            // Usuário só pode acessar seus próprios dados
            return userPrincipal.getId().equals(targetUserId);
        } catch (Exception e) {
            log.warn("Erro ao verificar acesso a dados do usuário: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifica se o usuário tem role específica
     */
    public boolean hasRole(String role) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return false;
            }

            return auth.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
        } catch (Exception e) {
            log.warn("Erro ao verificar role: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Obtém o usuário autenticado atual
     */
    public Optional<Usuario> getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.empty();
            }

            UserPrincipal userPrincipal = (UserPrincipal) auth.getPrincipal();
            return usuarioRepository.findById(userPrincipal.getId());
        } catch (Exception e) {
            log.warn("Erro ao obter usuário atual: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Verifica se o usuário está autenticado
     */
    public boolean isAuthenticated() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null && auth.isAuthenticated();
        } catch (Exception e) {
            log.warn("Erro ao verificar autenticação: {}", e.getMessage());
            return false;
        }
    }
}
