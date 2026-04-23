package com.consumoesperto.model;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import javax.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonBackReference;

/**
 * Entidade que representa uma transação financeira
 * 
 * As transações são o núcleo do sistema, representando movimentações
 * financeiras como receitas (entradas) e despesas (saídas). Cada
 * transação está associada a uma categoria e a um usuário específico.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Entity
@Table(name = "transacoes") // Nome da tabela no banco de dados
@NoArgsConstructor // Lombok: gera construtor sem argumentos
@AllArgsConstructor // Lombok: gera construtor com todos os argumentos
public class Transacao {

    /**
     * Identificador único da transação (chave primária)
     * Gerado automaticamente pelo banco de dados
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Descrição da transação (ex: "Compra no supermercado", "Salário")
     * Deve ser preenchida e ter no máximo 200 caracteres
     */
    @NotBlank(message = "Descrição da transação é obrigatória")
    @Size(max = 200, message = "Descrição deve ter no máximo 200 caracteres")
    private String descricao;

    /**
     * Valor monetário da transação
     * Deve ser maior que zero e usar BigDecimal para precisão decimal
     */
    @NotNull(message = "Valor da transação é obrigatório")
    // @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero") // Temporariamente comentado para debug
    private BigDecimal valor;

    /**
     * Tipo da transação: RECEITA (entrada) ou DESPESA (saída)
     * Usa enum para garantir valores válidos
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_transacao")
    private TipoTransacao tipoTransacao;

    /**
     * Categoria à qual a transação pertence
     * Relacionamento muitos-para-um: várias transações podem pertencer a uma categoria
     * Carregamento lazy para melhor performance
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    @JsonBackReference("categoria-transacoes")
    private Categoria categoria;

    /**
     * Usuário proprietário da transação
     * Relacionamento muitos-para-um: várias transações podem pertencer a um usuário
     * Carregamento lazy para melhor performance
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @JsonBackReference("usuario-transacoes")
    private Usuario usuario;

    /**
     * Data e hora em que a transação ocorreu
     * Pode ser diferente da data de criação (ex: transação de ontem registrada hoje)
     */
    @Column(name = "data_transacao")
    private LocalDateTime dataTransacao;

    /**
     * Data e hora de criação do registro da transação no sistema
     * Preenchida automaticamente quando a transação é criada
     */
    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public TipoTransacao getTipoTransacao() { return tipoTransacao; }
    public void setTipoTransacao(TipoTransacao tipoTransacao) { this.tipoTransacao = tipoTransacao; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public LocalDateTime getDataTransacao() { return dataTransacao; }
    public void setDataTransacao(LocalDateTime dataTransacao) { this.dataTransacao = dataTransacao; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    /**
     * Método executado automaticamente antes de persistir a entidade
     * Define a data de criação e, se não informada, a data da transação
     */
    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
        // Se a data da transação não foi informada, usa a data atual
        if (dataTransacao == null) {
            dataTransacao = LocalDateTime.now();
        }
    }

    /**
     * Enum que define os tipos possíveis de transação
     * RECEITA: entrada de dinheiro (salário, vendas, etc.)
     * DESPESA: saída de dinheiro (compras, contas, etc.)
     */
    public enum TipoTransacao {
        RECEITA, // Entrada de dinheiro
        DESPESA  // Saída de dinheiro
    }
}
