package com.consumoesperto.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Serviço para autenticação OAuth2 com Google
 * Usa Spring Security OAuth2 em vez das APIs do Google diretamente
 */
@Service
public class GoogleOAuth2Service {

    @Autowired(required = false)
    private OAuth2AuthorizedClientService clientService;

    /**
     * Obtém informações do usuário autenticado via Google OAuth2
     */
    public OAuth2User getUserInfo(OAuth2AuthenticationToken authentication) {
        return authentication.getPrincipal();
    }

    /**
     * Obtém o cliente OAuth2 autorizado
     */
    public OAuth2AuthorizedClient getAuthorizedClient(OAuth2AuthenticationToken authentication) {
        if (clientService == null) {
            return null;
        }
        return clientService.loadAuthorizedClient(
            authentication.getAuthorizedClientRegistrationId(),
            authentication.getName()
        );
    }

    /**
     * Verifica se o usuário está autenticado
     */
    public boolean isAuthenticated(OAuth2AuthenticationToken authentication) {
        return authentication != null && authentication.isAuthenticated();
    }

    /**
     * Obtém o email do usuário do Google
     */
    public String getUserEmail(OAuth2AuthenticationToken authentication) {
        if (isAuthenticated(authentication)) {
            OAuth2User user = getUserInfo(authentication);
            return user.getAttribute("email");
        }
        return null;
    }

    /**
     * Obtém o nome do usuário do Google
     */
    public String getUserName(OAuth2AuthenticationToken authentication) {
        if (isAuthenticated(authentication)) {
            OAuth2User user = getUserInfo(authentication);
            return user.getAttribute("name");
        }
        return null;
    }

    /**
     * Obtém o ID do usuário do Google
     */
    public String getUserId(OAuth2AuthenticationToken authentication) {
        if (isAuthenticated(authentication)) {
            OAuth2User user = getUserInfo(authentication);
            return user.getName();
        }
        return null;
    }
}
