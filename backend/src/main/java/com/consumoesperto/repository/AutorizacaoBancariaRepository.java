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
     * Retorna a autorização ativa de um usuário para um tipo de banco
     * específico, útil para verificar se o usuário já autorizou
     * o acesso a um determinado banco.
     * 
     * @param usuarioId ID do usuário
     * @param tipoBanco Tipo do banco (NUBANK, ITAU, INTER, MERCADO_PAGO)
     * @return Optional contendo a autorização se encontrada
     */
    Optional<AutorizacaoBancaria> findByUsuarioIdAndTipoBanco(Long usuarioId, AutorizacaoBancaria.TipoBanco tipoBanco);

    /**
     * Busca autorizações ativas de um usuário
     * 
     * Retorna apenas as autorizações com status ATIVA, excluindo
     * autorizações expiradas, revogadas ou suspensas.
     * 
     * @param usuarioId ID do usuário
     * @return Lista de autorizações ativas
     */
    List<AutorizacaoBancaria> findByUsuarioIdAndStatus(Long usuarioId, AutorizacaoBancaria.StatusAutorizacao status);

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
    @Query("SELECT a FROM AutorizacaoBancaria a WHERE a.dataExpiracao <= :dataLimite AND a.status = 'ATIVA'")
    List<AutorizacaoBancaria> findAutorizacoesParaRenovacao(@Param("dataLimite") LocalDateTime dataLimite);

    /**
     * Busca autorizações expiradas
     * 
     * Retorna autorizações que já expiraram e precisam ser tratadas
     * pelo sistema (renovação ou notificação ao usuário).
     * 
     * @return Lista de autorizações expiradas
     */
    @Query("SELECT a FROM AutorizacaoBancaria a WHERE a.dataExpiracao < CURRENT_TIMESTAMP AND a.status = 'ATIVA'")
    List<AutorizacaoBancaria> findAutorizacoesExpiradas();

    /**
     * Busca autorizações não utilizadas há muito tempo
     * 
     * Retorna autorizações que não foram utilizadas por um período
     * específico, útil para identificar autorizações obsoletas
     * que podem ser removidas ou notificadas ao usuário.
     * 
     * @param dataLimite Data limite para considerar como não utilizada
     * @return Lista de autorizações não utilizadas
     */
    @Query("SELECT a FROM AutorizacaoBancaria a WHERE a.ultimaUtilizacao < :dataLimite OR a.ultimaUtilizacao IS NULL")
    List<AutorizacaoBancaria> findAutorizacoesNaoUtilizadas(@Param("dataLimite") LocalDateTime dataLimite);

    /**
     * Busca autorizações por tipo de banco
     * 
     * Retorna todas as autorizações para um tipo de banco específico,
     * útil para operações em lote ou auditoria por banco.
     * 
     * @param tipoBanco Tipo do banco
     * @return Lista de autorizações do tipo de banco especificado
     */
    List<AutorizacaoBancaria> findByTipoBanco(AutorizacaoBancaria.TipoBanco tipoBanco);

    /**
     * Busca autorizações por status
     * 
     * Retorna todas as autorizações com um status específico,
     * útil para auditoria e relatórios de sistema.
     * 
     * @param status Status das autorizações
     * @return Lista de autorizações com o status especificado
     */
    List<AutorizacaoBancaria> findByStatus(AutorizacaoBancaria.StatusAutorizacao status);

    /**
     * Conta autorizações ativas por usuário
     * 
     * Retorna o número de autorizações ativas que um usuário possui,
     * útil para validações de limite de bancos conectados.
     * 
     * @param usuarioId ID do usuário
     * @return Número de autorizações ativas
     */
    long countByUsuarioIdAndStatus(Long usuarioId, AutorizacaoBancaria.StatusAutorizacao status);

    /**
     * Verifica se usuário possui autorização para um banco específico
     * 
     * Método de conveniência para verificar rapidamente se um usuário
     * já autorizou o acesso a um determinado banco.
     * 
     * @param usuarioId ID do usuário
     * @param tipoBanco Tipo do banco
     * @return true se possui autorização ativa, false caso contrário
     */
    boolean existsByUsuarioIdAndTipoBancoAndStatus(Long usuarioId, AutorizacaoBancaria.TipoBanco tipoBanco, AutorizacaoBancaria.StatusAutorizacao status);

    /**
     * Busca autorizações com contador de renovações alto
     * 
     * Retorna autorizações que foram renovadas muitas vezes,
     * útil para identificar possíveis problemas ou autorizações
     * que precisam de atenção especial.
     * 
     * @param limiteRenovacoes Limite mínimo de renovações
     * @return Lista de autorizações com muitas renovações
     */
    @Query("SELECT a FROM AutorizacaoBancaria a WHERE a.contadorRenovacoes >= :limiteRenovacoes")
    List<AutorizacaoBancaria> findAutorizacoesComMuitasRenovacoes(@Param("limiteRenovacoes") Integer limiteRenovacoes);

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
     * Busca autorizações por escopo de permissões
     * 
     * Retorna autorizações que possuem um escopo específico,
     * útil para filtrar por nível de acesso concedido.
     * 
     * @param escopo Escopo de permissões (ex: "read", "read write")
     * @return Lista de autorizações com o escopo especificado
     */
    List<AutorizacaoBancaria> findByEscopoContaining(String escopo);

    /**
     * Busca autorizações que precisam de atenção
     * 
     * Retorna autorizações que podem ter problemas ou precisam
     * de intervenção manual, como tokens expirados ou muitas renovações.
     * 
     * @return Lista de autorizações que precisam de atenção
     */
    @Query("SELECT a FROM AutorizacaoBancaria a WHERE " +
           "a.status = 'ATIVA' AND (" +
           "a.dataExpiracao <= CURRENT_TIMESTAMP OR " +
           "a.contadorRenovacoes >= 10 OR " +
           "(a.ultimaUtilizacao IS NULL AND a.dataCriacao < :dataLimite))")
    List<AutorizacaoBancaria> findAutorizacoesQuePrecisamAtencao(
            @Param("dataLimite") LocalDateTime dataLimite);

    /**
     * Busca autorizações ativas de um usuário
     * 
     * @param usuarioId ID do usuário
     * @return Lista de autorizações ativas
     */
    List<AutorizacaoBancaria> findByUsuarioIdAndAtivoTrue(Long usuarioId);
}
