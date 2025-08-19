package com.consumoesperto.repository;

import com.consumoesperto.model.AutorizacaoBancaria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositório para gerenciar operações de banco de dados relacionadas
 * às autorizações bancárias dos usuários
 * 
 * Este repositório fornece métodos para buscar, criar, atualizar e excluir
 * autorizações OAuth2 que permitem ao sistema acessar dados bancários
 * em nome dos usuários.
 * 
 * Funcionalidades principais:
 * - CRUD completo de autorizações bancárias
 * - Busca por usuário e tipo de banco
 * - Filtros por status e validade
 * - Queries personalizadas para auditoria
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Repository
public interface AutorizacaoBancariaRepository extends JpaRepository<AutorizacaoBancaria, Long> {

    /**
     * Busca autorizações de um usuário específico
     * 
     * Retorna todas as autorizações bancárias ativas de um usuário,
     * incluindo diferentes bancos e tipos de conta.
     * 
     * @param usuarioId ID do usuário
     * @return Lista de autorizações do usuário
     */
    List<AutorizacaoBancaria> findByUsuarioId(Long usuarioId);

    /**
     * Busca autorização de um usuário para um banco específico
     * 
     * Retorna a autorização ativa de um usuário para um banco
     * específico, útil para verificar se o usuário já autorizou
     * o acesso a um determinado banco.
     * 
     * @param usuarioId ID do usuário
     * @param banco Nome do banco
     * @return Optional contendo a autorização se encontrada
     */
    Optional<AutorizacaoBancaria> findByUsuarioIdAndBanco(Long usuarioId, String banco);

    /**
     * Busca autorizações ativas de um usuário
     * 
     * Retorna apenas as autorizações ativas, excluindo
     * autorizações inativas.
     * 
     * @param usuarioId ID do usuário
     * @return Lista de autorizações ativas
     */
    List<AutorizacaoBancaria> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    /**
     * Busca autorizações que precisam ser renovadas
     * 
     * Retorna autorizações que estão próximas da expiração (dentro de 1 hora)
     * e precisam ser renovadas automaticamente para evitar interrupções
     * no acesso aos dados bancários.
     * 
     * @param dataLimite Data limite para renovação (1 hora antes da expiração)
     * @return Lista de autorizações que precisam ser renovadas
     */
    @Query("SELECT a FROM AutorizacaoBancaria a WHERE a.dataExpiracao <= :dataLimite AND a.ativo = true")
    List<AutorizacaoBancaria> findAutorizacoesParaRenovacao(@Param("dataLimite") LocalDateTime dataLimite);

    /**
     * Busca autorizações expiradas
     * 
     * Retorna autorizações que já expiraram e precisam ser tratadas
     * pelo sistema (renovação ou notificação ao usuário).
     * 
     * @return Lista de autorizações expiradas
     */
    @Query("SELECT a FROM AutorizacaoBancaria a WHERE a.dataExpiracao < CURRENT_TIMESTAMP AND a.ativo = true")
    List<AutorizacaoBancaria> findAutorizacoesExpiradas();

    /**
     * Busca autorizações por banco
     * 
     * Retorna todas as autorizações para um banco específico,
     * útil para operações em lote ou auditoria por banco.
     * 
     * @param banco Nome do banco
     * @return Lista de autorizações do banco especificado
     */
    List<AutorizacaoBancaria> findByBanco(String banco);

    /**
     * Verifica se usuário possui autorização para um banco específico
     * 
     * Método de conveniência para verificar rapidamente se um usuário
     * já autorizou o acesso a um determinado banco.
     * 
     * @param usuarioId ID do usuário
     * @param banco Nome do banco
     * @return true se possui autorização ativa, false caso contrário
     */
    boolean existsByUsuarioIdAndBancoAndAtivoTrue(Long usuarioId, String banco);

    /**
     * Busca autorizações criadas em um período específico
     * 
     * Retorna autorizações criadas dentro de um intervalo de datas,
     * útil para relatórios de crescimento e auditoria.
     * 
     * @param dataInicio Data de início do período
     * @param dataFim Data de fim do período
     * @return Lista de autorizações criadas no período
     */
    @Query("SELECT a FROM AutorizacaoBancaria a WHERE a.dataCriacao BETWEEN :dataInicio AND :dataFim")
    List<AutorizacaoBancaria> findAutorizacoesPorPeriodo(
            @Param("dataInicio") LocalDateTime dataInicio,
            @Param("dataFim") LocalDateTime dataFim);

    /**
     * Busca autorizações que precisam de atenção
     * 
     * Retorna autorizações que podem ter problemas ou precisam
     * de intervenção manual, como tokens expirados.
     * 
     * @return Lista de autorizações que precisam de atenção
     */
    @Query("SELECT a FROM AutorizacaoBancaria a WHERE " +
           "a.ativo = true AND a.dataExpiracao <= CURRENT_TIMESTAMP")
    List<AutorizacaoBancaria> findAutorizacoesQuePrecisamAtencao();
}
