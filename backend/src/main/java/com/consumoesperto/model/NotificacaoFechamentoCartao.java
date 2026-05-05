package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registo de envio do Watcher (anti-spam): um alerta por cartão e data de fechamento estimada.
 */
@Entity
@Table(
    name = "notificacoes_fechamento_cartao",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_notif_cartao_data_fechamento",
        columnNames = { "cartao_credito_id", "data_fechamento_referencia" }
    )
)
@Getter
@Setter
@NoArgsConstructor
public class NotificacaoFechamentoCartao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cartao_credito_id", nullable = false)
    private CartaoCredito cartaoCredito;

    @Column(name = "data_fechamento_referencia", nullable = false)
    private LocalDate dataFechamentoReferencia;

    /** OK = saldo cobre; DEFICIT = falta saldo */
    @Column(name = "tipo", nullable = false, length = 16)
    private String tipo;

    @Column(name = "mensagem_preview", length = 500)
    private String mensagemPreview;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    void onCreate() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }
}
