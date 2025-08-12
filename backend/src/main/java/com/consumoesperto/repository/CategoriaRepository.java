package com.consumoesperto.repository;

import com.consumoesperto.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    List<Categoria> findByUsuarioIdOrderByNome(Long usuarioId);
    
    boolean existsByNomeAndUsuarioId(String nome, Long usuarioId);
    
    /**
     * Busca categoria por usuário e nome
     * 
     * @param usuarioId ID do usuário
     * @param nome Nome da categoria
     * @return Categoria encontrada ou null
     */
    Categoria findByUsuarioIdAndNome(Long usuarioId, String nome);
}
