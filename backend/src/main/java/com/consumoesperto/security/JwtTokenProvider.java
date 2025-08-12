package com.consumoesperto.security;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Provedor de tokens JWT para autenticação e autorização
 * 
 * Esta classe é responsável por gerar, validar e extrair informações
 * dos tokens JWT (JSON Web Tokens) usados para autenticar usuários
 * no sistema. Os tokens são assinados com uma chave secreta e têm
 * um tempo de expiração configurável.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Component
public class JwtTokenProvider {

    /**
     * Chave secreta para assinar e verificar tokens JWT
     * Configurada no arquivo application.properties
     */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Tempo de expiração do token JWT em milissegundos
     * Configurado no arquivo application.properties
     */
    @Value("${jwt.expiration}")
    private int jwtExpirationInMs;

    /**
     * Gera um token JWT para um usuário autenticado
     * 
     * Cria um token com o ID do usuário, data de criação e expiração.
     * O token é assinado com a chave secreta usando algoritmo HS512.
     * 
     * @param authentication Objeto de autenticação do Spring Security
     * @return Token JWT assinado e codificado
     */
    public String generateToken(Authentication authentication) {
        // Extrai o usuário principal da autenticação
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        // Calcula as datas de criação e expiração
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        // Constrói e retorna o token JWT
        return Jwts.builder()
                .setSubject(Long.toString(userPrincipal.getId())) // ID do usuário como subject
                .setIssuedAt(new Date()) // Data de criação
                .setExpiration(expiryDate) // Data de expiração
                .signWith(SignatureAlgorithm.HS512, jwtSecret) // Assina com algoritmo HS512
                .compact(); // Codifica o token
    }

    /**
     * Gera um token JWT para um usuário pelo username
     * 
     * Versão alternativa do método generateToken que aceita diretamente
     * o username do usuário. Útil para autenticação via Google OAuth2.
     * 
     * @param username Nome de usuário para incluir no token
     * @return Token JWT assinado e codificado
     */
    public String generateToken(String username) {
        // Calcula as datas de criação e expiração
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        // Constrói e retorna o token JWT
        return Jwts.builder()
                .setSubject(username) // Username como subject
                .setIssuedAt(new Date()) // Data de criação
                .setExpiration(expiryDate) // Data de expiração
                .signWith(SignatureAlgorithm.HS512, jwtSecret) // Assina com algoritmo HS512
                .compact(); // Codifica o token
    }

    /**
     * Extrai o ID do usuário de um token JWT válido
     * 
     * Decodifica o token e retorna o ID do usuário que está
     * armazenado no campo 'subject' do token.
     * 
     * @param token Token JWT a ser decodificado
     * @return ID do usuário extraído do token
     */
    public Long getUserIdFromJWT(String token) {
        // Decodifica o token e extrai as claims (reivindicações)
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret) // Usa a chave secreta para verificar
                .parseClaimsJws(token) // Parse do token
                .getBody(); // Extrai o corpo do token

        // Converte o subject (que contém o ID) para Long
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Valida se um token JWT é válido
     * 
     * Verifica se o token está bem formado, não expirou e foi
     * assinado com a chave secreta correta.
     * 
     * @param authToken Token JWT a ser validado
     * @return true se o token for válido, false caso contrário
     */
    public boolean validateToken(String authToken) {
        try {
            // Tenta decodificar o token com a chave secreta
            Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(authToken);
            return true; // Token válido
        } catch (SignatureException ex) {
            // Assinatura JWT inválida (token foi modificado)
            return false;
        } catch (MalformedJwtException ex) {
            // Token JWT malformado (estrutura incorreta)
            return false;
        } catch (ExpiredJwtException ex) {
            // Token JWT expirado
            return false;
        } catch (UnsupportedJwtException ex) {
            // Token JWT não suportado (versão ou formato incorreto)
            return false;
        } catch (IllegalArgumentException ex) {
            // String de claims JWT está vazia
            return false;
        }
    }
}
