package com.consumoesperto.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entidade que representa uma categoria de transação financeira
 * 
 * As categorias permitem organizar e classificar as transações
 * financeiras do usuário (ex: alimentação, transporte, lazer, etc.).
 * Cada categoria pertence a um usuário específico e pode ter uma cor
 * personalizada para identificação visual.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Entity
@Table(name = "categorias") // Nome da tabela no banco de dados
@Data // Lombok: gera getters, setters, toString, equals e hashCode
@NoArgsConstructor // Lombok: gera construtor sem argumentos
@AllArgsConstructor // Lombok: gera construtor com todos os argumentos
public class Categoria {

    /**
     * Identificador único da categoria (chave primária)
     * Gerado automaticamente pelo banco de dados
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nome da categoria (ex: "Alimentação", "Transporte", "Lazer")
     * Deve ser preenchido e ter no máximo 100 caracteres
     */
    @NotBlank(message = "Nome da categoria é obrigatório")
    @Size(max = 100, message = "Nome da categoria deve ter no máximo 100 caracteres")
    private String nome;

    /**
     * Descrição opcional da categoria
     * Permite detalhar melhor o propósito da categoria
     * Máximo de 500 caracteres
     */
    @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres")
    private String descricao;

    /**
     * Cor personalizada da categoria (formato hexadecimal)
     * Usada para identificação visual nas interfaces gráficas
     * Exemplo: "#FF5733", "#33FF57"
     */
    @Column(name = "cor")
    private String cor;

    /**
     * Ícone da categoria para identificação visual
     * Exemplo: "fas fa-utensils", "fas fa-car"
     */
    @Column(name = "icone")
    private String icone;

    /**
     * Indica se a categoria está ativa
     */
    @Column(name = "ativo")
    private Boolean ativo = true;

    /**
     * Usuário proprietário da categoria
     * Relacionamento muitos-para-um: várias categorias podem pertencer a um usuário
     * Carregamento lazy para melhor performance
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /**
     * Lista de transações associadas a esta categoria
     * Relacionamento um-para-muitos: uma categoria pode ter várias transações
     * Carregamento lazy para melhor performance
     */
    @OneToMany(mappedBy = "categoria", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Transacao> transacoes = new HashSet<>();

    /**
     * Data e hora de criação da categoria no sistema
     * Preenchida automaticamente quando a categoria é criada
     */
    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    /**
     * Método executado automaticamente antes de persistir a entidade
     * Define a data de criação quando uma nova categoria é criada
     */
    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
    }
}
