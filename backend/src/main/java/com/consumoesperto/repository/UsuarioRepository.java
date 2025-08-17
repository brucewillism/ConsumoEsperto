package com.consumoesperto.repository;

import com.consumoesperto.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositório para operações de persistência de usuários
 * 
 * Este repositório estende JpaRepository para fornecer operações
 * básicas de CRUD e métodos personalizados para busca de usuários
 * por username e email. Utiliza Spring Data JPA para simplificar
 * o acesso aos dados.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Busca um usuário pelo nome de usuário
     * 
     * @param username Nome de usuário para busca
     * @return Optional contendo o usuário encontrado ou vazio se não encontrado
     */
    Optional<Usuario> findByUsername(String username);
    
    /**
     * Busca um usuário pelo email
     * 
     * @param email Email do usuário para busca
     * @return Optional contendo o usuário encontrado ou vazio se não encontrado
     */
    Optional<Usuario> findByEmail(String email);
    
    /**
     * Verifica se existe um usuário com o username informado
     * 
     * @param username Nome de usuário para verificação
     * @return true se o username já existe, false caso contrário
     */
    boolean existsByUsername(String username);
    
    /**
     * Verifica se existe um usuário com o email informado
     * 
     * @param email Email do usuário para verificação
     * @return true se o email já existe, false caso contrário
     */
    boolean existsByEmail(String email);

    /**
     * Busca um usuário pelo Google ID (OAuth2)
     * 
     * @param googleId ID único do usuário no Google
     * @return Optional contendo o usuário encontrado ou vazio se não encontrado
     */
    Optional<Usuario> findByGoogleId(String googleId);

    /**
     * Verifica se existe um usuário com o Google ID informado
     * 
     * @param googleId ID único do usuário no Google
     * @return true se o Google ID já existe, false caso contrário
     */
    boolean existsByGoogleId(String googleId);
}
