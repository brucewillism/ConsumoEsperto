package com.consumoesperto.repository;

import com.consumoesperto.model.CartaoCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;

@Repository
public interface CartaoCreditoRepository extends JpaRepository<CartaoCredito, Long> {

    List<CartaoCredito> findByUsuarioId(Long usuarioId);

    List<CartaoCredito> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    Optional<CartaoCredito> findByIdAndUsuarioId(Long id, Long usuarioId);

    Optional<CartaoCredito> findByNumeroCartaoAndUsuarioId(String numeroCartao, Long usuarioId);

    @Query("SELECT c FROM CartaoCredito c WHERE c.usuario.id = :usuarioId AND c.banco = :banco AND c.numeroCartao = :numeroCartao")
    Optional<CartaoCredito> findByUsuarioAndBancoAndNumeroCartao(@Param("usuarioId") Long usuarioId, @Param("banco") String banco, @Param("numeroCartao") String numeroCartao);

    @Query("SELECT c FROM CartaoCredito c WHERE c.usuario.id = :usuarioId AND c.ativo = true")
    List<CartaoCredito> findActiveCardsByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Query("SELECT SUM(c.limiteCredito) FROM CartaoCredito c WHERE c.usuario.id = :usuarioId AND c.ativo = true")
    Double getTotalCreditLimitByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Query("SELECT SUM(c.limiteDisponivel) FROM CartaoCredito c WHERE c.usuario.id = :usuarioId AND c.ativo = true")
    Double getTotalAvailableLimitByUsuarioId(@Param("usuarioId") Long usuarioId);
    
    /**
     * Busca cartão por usuário e nome
     * 
     * @param usuarioId ID do usuário
     * @param nome Nome do cartão
     * @return Cartão encontrado ou null
     */
    CartaoCredito findByUsuarioIdAndNome(Long usuarioId, String nome);
    
    /**
     * Busca cartões por usuário, nome e banco
     * 
     * @param usuarioId ID do usuário
     * @param nome Nome do cartão
     * @param banco Banco do cartão
     * @return Lista de cartões encontrados
     */
    List<CartaoCredito> findByUsuarioIdAndNomeAndBanco(Long usuarioId, String nome, String banco);

    @Query("SELECT c FROM CartaoCredito c " +
           "WHERE c.usuario.id = :usuarioId AND c.ativo = true " +
           "AND (LOWER(c.nome) LIKE LOWER(CONCAT('%', :termo, '%')) OR LOWER(c.banco) LIKE LOWER(CONCAT('%', :termo, '%')))")
    List<CartaoCredito> findAtivosByUsuarioIdAndNomeOrBancoLike(@Param("usuarioId") Long usuarioId, @Param("termo") String termo);
    
    int deleteByUsuarioIdAndAtivoFalse(Long usuarioId);
    
    int deleteByAtivoFalse();

    @EntityGraph(attributePaths = "usuario")
    @Query("SELECT c FROM CartaoCredito c WHERE c.ativo = true")
    List<CartaoCredito> findAllAtivosComUsuario();
}
