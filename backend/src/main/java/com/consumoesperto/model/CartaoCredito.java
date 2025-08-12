package com.consumoesperto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entidade que representa um cartão de crédito do usuário
 * 
 * Esta classe gerencia informações sobre cartões de crédito, incluindo
 * limites, disponibilidade, faturas e compras parceladas. Permite
 * controle completo do uso do cartão e planejamento financeiro.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Entity
@Table(name = "cartoes_credito") // Nome da tabela no banco de dados
@Data // Lombok: gera getters, setters, toString, equals e hashCode
@NoArgsConstructor // Lombok: gera construtor sem argumentos
@AllArgsConstructor // Lombok: gera construtor com todos os argumentos
public class CartaoCredito {

    /**
     * Identificador único do cartão de crédito (chave primária)
     * Gerado automaticamente pelo banco de dados
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nome/apelido do cartão (ex: "Nubank", "Itaú Black")
     * Deve ser preenchido e ter no máximo 100 caracteres
     */
    @NotBlank(message = "Nome do cartão é obrigatório")
    @Size(max = 100, message = "Nome do cartão deve ter no máximo 100 caracteres")
    private String nome;

    /**
     * Nome do banco emissor do cartão
     * Deve ser preenchido e ter no máximo 50 caracteres
     */
    @NotBlank(message = "Nome do banco é obrigatório")
    @Size(max = 50, message = "Nome do banco deve ter no máximo 50 caracteres")
    private String banco;

    /**
     * Número do cartão (mascarado para segurança)
     * Deve ser preenchido e ter no máximo 20 caracteres
     * Exemplo: "**** **** **** 1234"
     */
    @NotBlank(message = "Número do cartão é obrigatório")
    @Size(max = 20, message = "Número do cartão deve ter no máximo 20 caracteres")
    @Column(name = "numero_cartao")
    private String numeroCartao;

    /**
     * Limite total de crédito do cartão
     * Deve ser informado e usar BigDecimal para precisão decimal
     */
    @NotNull(message = "Limite de crédito é obrigatório")
    @Column(name = "limite_credito")
    private BigDecimal limiteCredito;

    /**
     * Limite disponível para uso no cartão
     * Calculado automaticamente: limite total - gastos atuais
     * Deve ser informado e usar BigDecimal para precisão decimal
     */
    @NotNull(message = "Limite disponível é obrigatório")
    @Column(name = "limite_disponivel")
    private BigDecimal limiteDisponivel;

    /**
     * Data de vencimento do cartão
     * Usada para controle de renovação e segurança
     */
    @Column(name = "data_vencimento")
    private LocalDateTime dataVencimento;

    /**
     * Tipo do cartão (ex: STANDARD, GOLD, PLATINUM, BLACK)
     * Usa enum para garantir valores válidos
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cartao")
    private TipoCartao tipoCartao;

    /**
     * Bandeira do cartão (Visa, Mastercard, etc.)
     */
    @Column(name = "bandeira")
    private String bandeira;

    /**
     * Limite do cartão
     */
    @Column(name = "limite")
    private BigDecimal limite;

    /**
     * Dia de fechamento da fatura
     */
    @Column(name = "dia_fechamento")
    private Integer diaFechamento;

    /**
     * Dia de vencimento da fatura
     */
    @Column(name = "dia_vencimento")
    private Integer diaVencimento;

    /**
     * Indica se o cartão está ativo para uso
     * Permite desativar temporariamente um cartão sem excluí-lo
     */
    @Column(name = "ativo")
    private Boolean ativo = true;

    /**
     * Usuário proprietário do cartão de crédito
     * Relacionamento muitos-para-um: vários cartões podem pertencer a um usuário
     * Carregamento lazy para melhor performance
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /**
     * Lista de faturas associadas a este cartão
     * Relacionamento um-para-muitos: um cartão pode ter várias faturas
     * Carregamento lazy para melhor performance
     */
    @OneToMany(mappedBy = "cartaoCredito", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Fatura> faturas = new HashSet<>();

    /**
     * Lista de compras parceladas associadas a este cartão
     * Relacionamento um-para-muitos: um cartão pode ter várias compras parceladas
     * Carregamento lazy para melhor performance
     */
    @OneToMany(mappedBy = "cartaoCredito", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<CompraParcelada> comprasParceladas = new HashSet<>();

    /**
     * Data e hora de criação do cartão no sistema
     * Preenchida automaticamente quando o cartão é criado
     */
    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    /**
     * Data e hora da última atualização do cartão
     * Atualizada automaticamente quando qualquer campo é modificado
     */
    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    /**
     * Método executado automaticamente antes de persistir a entidade
     * Define as datas de criação e atualização quando um novo cartão é criado
     */
    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
        dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Método executado automaticamente antes de atualizar a entidade
     * Atualiza a data de modificação quando o cartão é alterado
     */
    @PreUpdate
    protected void onUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Enum que define os tipos possíveis de cartão de crédito
     * Cada tipo pode ter benefícios e limites diferentes
     */
    public enum TipoCartao {
        STANDARD,  // Cartão básico
        GOLD,      // Cartão intermediário com benefícios
        PLATINUM,  // Cartão premium com mais benefícios
        BLACK      // Cartão exclusivo com benefícios especiais
    }
}
