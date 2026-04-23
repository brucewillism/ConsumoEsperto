package com.consumoesperto.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Cliente Feign para integração com APIs bancárias
 * Exemplo de uso com Open Banking ou APIs de bancos
 */
@FeignClient(
    name = "banco-api",
    url = "${app.banco.api.url:https://api.banco.com}",
    configuration = com.consumoesperto.config.OpenFeignConfig.class
)
public interface BancoApiClient {

    /**
     * Busca informações da conta
     */
    @GetMapping("/contas/{contaId}")
    ContaResponse getConta(
        @PathVariable("contaId") String contaId,
        @RequestHeader("Authorization") String authorization
    );

    /**
     * Busca transações da conta
     */
    @GetMapping("/contas/{contaId}/transacoes")
    TransacoesResponse getTransacoes(
        @PathVariable("contaId") String contaId,
        @RequestHeader("Authorization") String authorization
    );

    /**
     * Response da conta
     */
    class ContaResponse {
        private String id;
        private String numero;
        private String agencia;
        private String tipo;
        private Double saldo;
        
        // Getters e Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getNumero() { return numero; }
        public void setNumero(String numero) { this.numero = numero; }
        public String getAgencia() { return agencia; }
        public void setAgencia(String agencia) { this.agencia = agencia; }
        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }
        public Double getSaldo() { return saldo; }
        public void setSaldo(Double saldo) { this.saldo = saldo; }
    }

    /**
     * Response das transações
     */
    class TransacoesResponse {
        private java.util.List<Transacao> transacoes;
        
        public java.util.List<Transacao> getTransacoes() { return transacoes; }
        public void setTransacoes(java.util.List<Transacao> transacoes) { this.transacoes = transacoes; }
    }

    /**
     * Transação individual
     */
    class Transacao {
        private String id;
        private String descricao;
        private Double valor;
        private String data;
        private String tipo;
        
        // Getters e Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDescricao() { return descricao; }
        public void setDescricao(String descricao) { this.descricao = descricao; }
        public Double getValor() { return valor; }
        public void setValor(Double valor) { this.valor = valor; }
        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }
    }
}
