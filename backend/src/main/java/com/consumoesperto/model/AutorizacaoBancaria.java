package com.consumoesperto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Entidade que representa a autorização OAuth2 de um usuário com um banco
 * 
 * Esta classe gerencia os tokens de acesso OAuth2 que permitem ao sistema
 * consultar dados bancários em nome do usuário, incluindo cartões, saldos
 * e faturas através das APIs dos bancos.
 * 
 * Funcionalidades principais:
 * - Armazenamento seguro de tokens OAuth2
 * - Controle de expiração e renovação automática
 * - Histórico de autorizações por banco
 * - Rastreamento de permissões concedidas
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Entity
@Table(name = "autorizacoes_bancarias") // Nome da tabela no banco de dados
@Data // Lombok: gera getters, setters, toString, equals e hashCode
@NoArgsConstructor // Lombok: gera construtor sem argumentos
@AllArgsConstructor // Lombok: gera construtor com todos os argumentos
public class AutorizacaoBancaria {

    /**
     * Identificador único da autorização bancária (chave primária)
     * Gerado automaticamente pelo banco de dados
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Usuário que concedeu a autorização
     * Relacionamento muitos-para-um: um usuário pode ter várias autorizações
     * Carregamento eager para garantir que o usuário esteja sempre disponível
     */
    @NotNull(message = "Usuário é obrigatório")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /**
     * Tipo do banco para o qual a autorização foi concedida
     * Usa enum para garantir valores válidos e consistentes
     */
    @NotNull(message = "Tipo do banco é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_banco", nullable = false)
    private TipoBanco tipoBanco;

    /**
     * Token de acesso OAuth2 para consultas à API do banco
     * Deve ser criptografado antes de ser armazenado por questões de segurança
     * Não pode ser nulo e deve ter tamanho adequado para tokens OAuth2
     */
    @NotBlank(message = "Token de acesso é obrigatório")
    @Column(name = "access_token", nullable = false, length = 1000)
    private String accessToken;

    /**
     * Refresh token OAuth2 para renovação automática do access token
     * Permite renovar o acesso sem necessidade de nova autorização do usuário
     * Deve ser criptografado antes de ser armazenado
     */
    @NotBlank(message = "Refresh token é obrigatório")
    @Column(name = "refresh_token", nullable = false, length = 1000)
    private String refreshToken;

    /**
     * Data e hora de expiração do token de acesso
     * Usada para determinar quando o token precisa ser renovado
     * Não pode ser nula para controle de validade
     */
    @NotNull(message = "Data de expiração é obrigatória")
    @Column(name = "data_expiracao", nullable = false)
    private LocalDateTime dataExpiracao;

    /**
     * Escopo de permissões concedidas pelo usuário
     * Define quais dados o sistema pode acessar (ex: read, write, accounts)
     * Deve ser preenchido e ter tamanho adequado
     */
    @NotBlank(message = "Escopo de permissões é obrigatório")
    @Column(name = "escopo", nullable = false, length = 200)
    private String escopo;

    /**
     * Status atual da autorização
     * Controla se a autorização está ativa, expirada ou revogada
     * Inicializa como ATIVA
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusAutorizacao status = StatusAutorizacao.ATIVA;

    /**
     * Indica se a autorização está ativa para uso
     * Usado para controlar quais autorizações podem ser utilizadas
     * Inicializa como true
     */
    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    /**
     * Data e hora da última utilização da autorização
     * Usada para auditoria e para identificar autorizações não utilizadas
     * Pode ser nula se a autorização nunca foi utilizada
     */
    @Column(name = "ultima_utilizacao")
    private LocalDateTime ultimaUtilizacao;

    /**
     * Data e hora da última sincronização de dados bancários
     * Usada para rastrear quando os dados foram atualizados pela última vez
     * Pode ser nula se nunca houve sincronização
     */
    @Column(name = "ultima_sincronizacao")
    private LocalDateTime dataUltimaSincronizacao;

    /**
     * Contador de renovações automáticas realizadas
     * Usado para controle e auditoria de renovações
     * Inicializa com zero
     */
    @Column(name = "contador_renovacoes")
    private Integer contadorRenovacoes = 0;

    /**
     * Data e hora de criação da autorização
     * Preenchida automaticamente quando a autorização é criada
     */
    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    /**
     * Data e hora da última atualização da autorização
     * Atualizada automaticamente quando qualquer campo é modificado
     */
    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;

    /**
     * Método executado automaticamente antes de persistir a entidade
     * Define as datas de criação e atualização quando uma nova autorização é criada
     */
    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
        dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Método executado automaticamente antes de atualizar a entidade
     * Atualiza a data de modificação quando a autorização é alterada
     */
    @PreUpdate
    protected void onUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Verifica se o token de acesso ainda é válido
     * 
     * @return true se o token não expirou, false caso contrário
     */
    public boolean isTokenValido() {
        return LocalDateTime.now().isBefore(dataExpiracao) && 
               status == StatusAutorizacao.ATIVA;
    }

    /**
     * Verifica se o token de acesso expirou
     * 
     * @return true se o token expirou, false caso contrário
     */
    public boolean isTokenExpirado() {
        return LocalDateTime.now().isAfter(dataExpiracao) || 
               status != StatusAutorizacao.ATIVA;
    }

    /**
     * Verifica se o token precisa ser renovado
     * 
     * Considera que um token precisa ser renovado quando está próximo
     * da expiração (dentro de 1 hora) para evitar interrupções
     * 
     * @return true se o token precisa ser renovado, false caso contrário
     */
    public boolean precisaRenovacao() {
        LocalDateTime umaHoraAntes = dataExpiracao.minusHours(1);
        return LocalDateTime.now().isAfter(umaHoraAntes) && 
               status == StatusAutorizacao.ATIVA;
    }

    /**
     * Marca a autorização como utilizada
     * 
     * Atualiza a data da última utilização para auditoria
     */
    public void marcarComoUtilizada() {
        this.ultimaUtilizacao = LocalDateTime.now();
    }

    /**
     * Incrementa o contador de renovações
     * 
     * Usado quando o token é renovado automaticamente
     */
    public void incrementarContadorRenovacoes() {
        this.contadorRenovacoes++;
    }

    /**
     * Enum que define os tipos de banco suportados pela aplicação
     * 
     * Cada banco possui suas próprias configurações de API e endpoints
     * específicos para autenticação e consulta de dados.
     */
    public enum TipoBanco {
        NUBANK("Nubank", "Banco digital com foco em cartão de crédito"),
        ITAU("Itaú", "Banco tradicional com Open Banking"),
        INTER("Inter", "Banco digital com Open Banking"),
        MERCADO_PAGO("Mercado Pago", "Fintech com serviços financeiros");

        private final String nome;
        private final String descricao;

        TipoBanco(String nome, String descricao) {
            this.nome = nome;
            this.descricao = descricao;
        }

        public String getNome() { return nome; }
        public String getDescricao() { return descricao; }
    }

    /**
     * Enum que define os status possíveis de uma autorização bancária
     * 
     * Controla o ciclo de vida da autorização desde a criação até a revogação
     */
    public enum StatusAutorizacao {
        ATIVA("Ativa", "Autorização válida e ativa"),
        EXPIRADA("Expirada", "Token expirou e precisa ser renovado"),
        REVOGADA("Revogada", "Usuário revogou a autorização"),
        SUSPENSA("Suspensa", "Autorização temporariamente suspensa"),
        PENDENTE("Pendente", "Aguardando confirmação do usuário");

        private final String nome;
        private final String descricao;

        StatusAutorizacao(String nome, String descricao) {
            this.nome = nome;
            this.descricao = descricao;
        }

        public String getNome() { return nome; }
        public String getDescricao() { return descricao; }
    }
}
