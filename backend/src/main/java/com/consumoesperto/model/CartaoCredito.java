package com.consumoesperto.model;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

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
     * Data de vencimento do cartão (dia do mês)
     * Deve ser um número entre 1 e 31
     */
    @NotNull(message = "Dia de vencimento é obrigatório")
    @Column(name = "dia_vencimento")
    private Integer diaVencimento;

    /**
     * Indica se o cartão está ativo
     * Controla se o cartão pode ser usado para transações
     */
    @Column(name = "ativo")
    private Boolean ativo = true;

    /**
     * Cor personalizada do cartão para identificação visual
     * Formato hexadecimal (ex: "#FF5733")
     */
    @Column(name = "cor")
    private String cor;

    /**
     * Ícone do cartão para identificação visual
     * Exemplo: "fas fa-credit-card", "fas fa-cc-visa"
     */
    @Column(name = "icone")
    private String icone;

    /**
     * Usuário proprietário do cartão
     * Relacionamento muitos-para-um: vários cartões podem pertencer a um usuário
     * Carregamento lazy para melhor performance
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @JsonBackReference("usuario-cartoes")
    private Usuario usuario;

    /**
     * Lista de faturas do cartão
     * Relacionamento um-para-muitos: um cartão pode ter várias faturas
     * Carregamento lazy para melhor performance
     */
    @OneToMany(mappedBy = "cartaoCredito", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference("cartao-faturas")
    private Set<Fatura> faturas = new HashSet<>();

    /**
     * Lista de compras parceladas do cartão
     * Relacionamento um-para-muitos: um cartão pode ter várias compras parceladas
     * Carregamento lazy para melhor performance
     */
    @OneToMany(mappedBy = "cartaoCredito", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference("cartao-compras")
    private Set<CompraParcelada> comprasParceladas = new HashSet<>();

    /**
     * Data e hora de criação do cartão no sistema
     * Preenchida automaticamente quando o cartão é criado
     */
    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getBanco() { return banco; }
    public void setBanco(String banco) { this.banco = banco; }

    public String getNumeroCartao() { return numeroCartao; }
    public void setNumeroCartao(String numeroCartao) { this.numeroCartao = numeroCartao; }

    public BigDecimal getLimiteCredito() { return limiteCredito; }
    public void setLimiteCredito(BigDecimal limiteCredito) { this.limiteCredito = limiteCredito; }

    public BigDecimal getLimiteDisponivel() { return limiteDisponivel; }
    public void setLimiteDisponivel(BigDecimal limiteDisponivel) { this.limiteDisponivel = limiteDisponivel; }

    public Integer getDiaVencimento() { return diaVencimento; }
    public void setDiaVencimento(Integer diaVencimento) { this.diaVencimento = diaVencimento; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public String getCor() { return cor; }
    public void setCor(String cor) { this.cor = cor; }

    public String getIcone() { return icone; }
    public void setIcone(String icone) { this.icone = icone; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Set<Fatura> getFaturas() { return faturas; }
    public void setFaturas(Set<Fatura> faturas) { this.faturas = faturas; }

    public Set<CompraParcelada> getComprasParceladas() { return comprasParceladas; }
    public void setComprasParceladas(Set<CompraParcelada> comprasParceladas) { this.comprasParceladas = comprasParceladas; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    // Métodos de compatibilidade para código existente
    public LocalDateTime getDataVencimento() { 
        // Retorna uma data fictícia baseada no dia de vencimento
        if (diaVencimento != null) {
            return LocalDateTime.now().withDayOfMonth(diaVencimento);
        }
        return null;
    }
    public void setDataVencimento(LocalDateTime dataVencimento) { 
        if (dataVencimento != null) {
            this.diaVencimento = dataVencimento.getDayOfMonth();
        }
    }
    
    public BigDecimal getLimite() { return limiteCredito; }
    public void setLimite(BigDecimal limite) { this.limiteCredito = limite; }
    
    public String getBandeira() { return null; } // Campo removido, retorna null para compatibilidade
    public void setBandeira(String bandeira) { /* Campo removido */ }
    
    public LocalDateTime getDataAtualizacao() { return dataCriacao; } // Usa dataCriacao como fallback
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { /* Campo removido */ }
    
    // Método de compatibilidade para código existente
    public void setTipoCartao(TipoCartao tipoCartao) { /* Campo removido, ignora */ }
    
    // Método de compatibilidade para código existente
    public TipoCartao getTipoCartao() { return TipoCartao.CREDITO; } // Retorna valor padrão para compatibilidade
    
    // Método de compatibilidade para código existente
    public void setDiaFechamento(Integer diaFechamento) { /* Campo removido, ignora */ }
    
    // Método de compatibilidade para código existente
    public Integer getDiaFechamento() { return diaVencimento; } // Usa diaVencimento como fallback
    
    // Enum TipoCartao para compatibilidade
    public enum TipoCartao {
        CREDITO, DEBITO, CREDITO_DEBITO, STANDARD, GOLD, PLATINUM, BLACK
    }

    /**
     * Método executado automaticamente antes de persistir a entidade
     * Define a data de criação quando um novo cartão é criado
     */
    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
    }
}
