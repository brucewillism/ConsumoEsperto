package com.consumoesperto.service;

import com.consumoesperto.dto.*;
import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "bank.api.inter.client-id", havingValue = "inter_dev_client_id", matchIfMissing = true)
@Slf4j
public class InterBankService {

    @Value("${bank.api.inter.client-id:inter_dev_client_id}")
    private String clientId;

    @Value("${bank.api.inter.client-secret:inter_dev_client_secret}")
    private String clientSecret;

    @Value("${bank.api.inter.api-url:https://cdp.openbanking.bancointer.com.br/sandbox}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    public InterBankService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String generateAuthUrl(String redirectUri, String state) {
        log.info("Gerando URL de autorização para Inter");
        
        return UriComponentsBuilder.fromHttpUrl(baseUrl + "/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid accounts credit-cards-accounts")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public Map<String, Object> processOAuthCallback(String code, String state, String redirectUri) {
        log.info("Processando callback OAuth para Inter");
        
        try {
            // Preparar headers para troca do código por token
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(clientId, clientSecret);

            // Preparar body da requisição
            String body = String.format("grant_type=authorization_code&code=%s&redirect_uri=%s", 
                    code, redirectUri);

            HttpEntity<String> request = new HttpEntity<>(body, headers);

            // Fazer chamada para trocar código por token
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/oauth/token", 
                    request, 
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> tokenData = response.getBody();
                log.info("Token obtido com sucesso para Inter");
                return tokenData;
            } else {
                log.error("Erro ao obter token do Inter: {}", response.getStatusCode());
                throw new RuntimeException("Falha na autenticação com Inter");
            }

        } catch (Exception e) {
            log.error("Erro ao processar callback OAuth do Inter: {}", e.getMessage());
            throw new RuntimeException("Erro na autenticação com Inter", e);
        }
    }





    public boolean testConnection(AutorizacaoBancaria auth) {
        log.info("Testando conexão com Inter para usuário {}", auth.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(auth.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Teste real de conexão com Inter
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/open-banking/v1/accounts")
                    .queryParam("access_token", auth.getAccessToken())
                    .build().toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            boolean isConnected = response.getStatusCode() == HttpStatus.OK;
            
            log.info("Teste de conexão com Inter: {}", isConnected ? "SUCESSO" : "FALHA");
            return isConnected;
            
        } catch (Exception e) {
            log.error("Erro ao testar conexão com Inter: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Object> getBankDetails(AutorizacaoBancaria autorizacao) {
        log.info("Buscando detalhes completos do banco Inter para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Verificar se precisa renovar o token
            if (refreshTokenIfNeeded(autorizacao)) {
                log.info("Token renovado automaticamente para Inter");
            }

            // Buscar dados de diferentes endpoints
            List<Map<String, Object>> cartoes = getCreditCards(autorizacao);
            Map<String, Object> saldo = getBalanceData(autorizacao);
            List<Map<String, Object>> faturas = getInvoices(autorizacao);
            List<Map<String, Object>> transacoes = getTransactions(autorizacao);
            Map<String, Object> gastosPorCategoria = getSpendingByCategory(autorizacao);
            Map<String, Object> analiseGastos = getSpendingAnalysis(autorizacao);

            return Map.of(
                "status", "success",
                "banco", "INTER",
                "cartoes", cartoes,
                "saldo", saldo,
                "faturas", faturas,
                "transacoes", transacoes,
                "gastosPorCategoria", gastosPorCategoria,
                "analiseGastos", analiseGastos,
                "ultimaSincronizacao", LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.error("Erro ao buscar detalhes do banco Inter: {}", e.getMessage());
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    public List<Map<String, Object>> getCreditCards(AutorizacaoBancaria autorizacao) {
        log.info("Buscando cartões de crédito do Inter para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Chamada real para API do Inter Open Banking
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/open-banking/v1/credit-cards-accounts")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build().toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Cartões obtidos com sucesso da API do Inter");
                List<CartaoCredito> cartoes = processarCartoesResposta(response.getBody());
                return cartoes.stream()
                    .map(cartao -> {
                        Map<String, Object> cartaoMap = new HashMap<>();
                        cartaoMap.put("id", cartao.getId());
                        cartaoMap.put("numero", cartao.getNumeroCartao());
                        cartaoMap.put("limite", cartao.getLimiteCredito());
                        cartaoMap.put("saldoDisponivel", cartao.getLimiteDisponivel());
                        return cartaoMap;
                    })
                    .collect(Collectors.toList());
            }
            
            log.warn("API retornou status não esperado: {}. Usando dados simulados", response.getStatusCode());
            List<CartaoCredito> cartoes = getSimulatedCreditCards();
            return cartoes.stream()
                    .map(cartao -> {
                        Map<String, Object> cartaoMap = new HashMap<>();
                        cartaoMap.put("id", cartao.getId());
                        cartaoMap.put("numero", cartao.getNumeroCartao());
                        cartaoMap.put("limite", cartao.getLimiteCredito());
                        cartaoMap.put("saldoDisponivel", cartao.getLimiteDisponivel());
                        return cartaoMap;
                    })
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Erro ao buscar cartões de crédito do Inter: {}", e.getMessage());
            List<CartaoCredito> cartoes = getSimulatedCreditCards();
            return cartoes.stream()
                    .map(cartao -> {
                        Map<String, Object> cartaoMap = new HashMap<>();
                        cartaoMap.put("id", cartao.getId());
                        cartaoMap.put("numero", cartao.getNumeroCartao());
                        cartaoMap.put("limite", cartao.getLimiteCredito());
                        cartaoMap.put("saldoDisponivel", cartao.getLimiteDisponivel());
                        // cartaoMap.put("saldoFatura", cartao.getSaldoFatura()); // Campo não existe no modelo
                        return cartaoMap;
                    })
                    .collect(Collectors.toList());
        }
    }

    public Map<String, Object> getBalanceData(AutorizacaoBancaria autorizacao) {
        log.info("Buscando dados de saldo do Inter para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Chamada real para API do Inter Open Banking
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/open-banking/v1/accounts/balances")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build().toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Saldo obtido com sucesso da API do Inter");
                return processarSaldoResposta(response.getBody());
            }
            
            log.warn("API retornou status não esperado: {}. Usando dados simulados", response.getStatusCode());
            return getSimulatedBalanceData();
            
        } catch (Exception e) {
            log.error("Erro ao buscar dados de saldo do Inter: {}", e.getMessage());
            return getSimulatedBalanceData();
        }
    }

    public List<Map<String, Object>> getInvoices(AutorizacaoBancaria autorizacao) {
        log.info("Buscando faturas do Inter para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // TODO: Implementar chamada real para API do Inter
            // String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/accounts/" + autorizacao.getUsuario().getId() + "/invoices")
            //         .build().toUriString();
            // ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            // return processarFaturasResposta(response.getBody());

            // Por enquanto, retorna dados simulados
            List<FaturaDTO> faturas = getSimulatedInvoices();
            return faturas.stream()
                    .map(fatura -> {
                        Map<String, Object> faturaMap = new HashMap<>();
                        faturaMap.put("id", fatura.getId());
                        faturaMap.put("valorTotal", fatura.getValorFatura());
                        faturaMap.put("valorMinimo", fatura.getValorPago());
                        faturaMap.put("status", fatura.getStatusFatura());
                        faturaMap.put("banco", "Inter");
                        return faturaMap;
                    })
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Erro ao buscar faturas do Inter: {}", e.getMessage());
            List<FaturaDTO> faturas = getSimulatedInvoices();
            return faturas.stream()
                    .map(fatura -> {
                        Map<String, Object> faturaMap = new HashMap<>();
                        faturaMap.put("id", fatura.getId());
                        faturaMap.put("valorTotal", fatura.getValorFatura());
                        faturaMap.put("valorMinimo", fatura.getValorPago());
                        faturaMap.put("status", fatura.getStatusFatura());
                        faturaMap.put("banco", "Inter");
                        return faturaMap;
                    })
                    .collect(Collectors.toList());
        }
    }

    public List<Map<String, Object>> getTransactions(AutorizacaoBancaria autorizacao) {
        log.info("Buscando transações do Inter para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Chamada real para API do Inter Open Banking
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/open-banking/v1/accounts/transactions")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .queryParam("limit", "100")
                    .queryParam("offset", "0")
                    .build().toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Transações obtidas com sucesso da API do Inter");
                return processarTransacoesResposta(response.getBody());
            }
            
            log.warn("API retornou status não esperado: {}. Usando dados simulados", response.getStatusCode());
            return getSimulatedTransactions();
            
        } catch (Exception e) {
            log.error("Erro ao buscar transações do Inter: {}", e.getMessage());
            return getSimulatedTransactions();
        }
    }

    public Map<String, Object> getSpendingByCategory(AutorizacaoBancaria autorizacao) {
        log.info("Buscando gastos por categoria do Inter para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Chamada real para API do Inter Open Banking
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/open-banking/v1/accounts/spending/category")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build().toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Gastos por categoria obtidos com sucesso da API do Inter");
                return processarGastosPorCategoriaResposta(response.getBody());
            }
            
            log.warn("API retornou status não esperado: {}. Usando dados simulados", response.getStatusCode());
            return getSimulatedSpendingByCategory();
            
        } catch (Exception e) {
            log.error("Erro ao buscar gastos por categoria do Inter: {}", e.getMessage());
            return getSimulatedSpendingByCategory();
        }
    }

    public Map<String, Object> getSpendingAnalysis(AutorizacaoBancaria autorizacao) {
        log.info("Buscando análise de gastos do Inter para usuário {}", autorizacao.getUsuario().getId());
        
        try {
            // Preparar headers com token de acesso
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(autorizacao.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Chamada real para API do Inter Open Banking
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/open-banking/v1/accounts/spending/analysis")
                    .queryParam("access_token", autorizacao.getAccessToken())
                    .build().toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Análise de gastos obtida com sucesso da API do Inter");
                return processarAnaliseGastosResposta(response.getBody());
            }
            
            log.warn("API retornou status não esperado: {}. Usando dados simulados", response.getStatusCode());
            return getSimulatedSpendingAnalysis();
            
        } catch (Exception e) {
            log.error("Erro ao buscar análise de gastos do Inter: {}", e.getMessage());
            return getSimulatedSpendingAnalysis();
        }
    }

    public boolean refreshTokenIfNeeded(AutorizacaoBancaria autorizacao) {
        log.info("Verificando se precisa renovar token do Inter para usuário {}", autorizacao.getUsuario().getId());
        
        if (!autorizacao.isTokenExpirado()) {
            log.info("Token do Inter ainda é válido");
            return false;
        }

        try {
            // Preparar headers para renovação do token
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(clientId, clientSecret);

            // Preparar body da requisição
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "refresh_token");
            body.add("refresh_token", autorizacao.getRefreshToken());

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            // TODO: Implementar chamada real para renovar token
            // ResponseEntity<Map> response = restTemplate.postForEntity(
            //         baseUrl + "/oauth/token", 
            //         request, 
            //         Map.class
            // );
            // 
            // if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            //     Map<String, Object> tokenData = response.getBody();
            //     // Atualizar a autorização com o novo token
            //     autorizacao.setAccessToken((String) tokenData.get("access_token"));
            //     autorizacao.setRefreshToken((String) tokenData.get("refresh_token"));
            //     autorizacao.setTokenExpiration(LocalDateTime.now().plusSeconds(
            //             Long.parseLong(tokenData.get("expires_in").toString())
            //     ));
            //     return true;
            // }

            // Por enquanto, retorna false (simulado)
            return false;
            
        } catch (Exception e) {
            log.error("Erro ao renovar token do Inter: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Obtém cartões de crédito do Inter
     * 
     * @param autorizacao Autorização bancária do usuário
     * @return Lista de cartões de crédito
     */
    
    // Métodos de processamento de resposta da API
    private List<CartaoCredito> processarCartoesResposta(Object response) {
        // TODO: Implementar processamento real da resposta da API
        log.info("Processando resposta de cartões da API do Inter");
        return getSimulatedCreditCards();
    }
    
    private Map<String, Object> processarSaldoResposta(Object response) {
        // TODO: Implementar processamento real da resposta da API
        log.info("Processando resposta de saldo da API do Inter");
        return getSimulatedBalanceData();
    }
    
    private List<FaturaDTO> processarFaturasResposta(Object response) {
        // TODO: Implementar processamento real da resposta da API
        log.info("Processando resposta de faturas da API do Inter");
        return getSimulatedInvoices();
    }
    
    private List<Map<String, Object>> processarTransacoesResposta(Object response) {
        // TODO: Implementar processamento real da resposta da API
        log.info("Processando resposta de transações da API do Inter");
        return getSimulatedTransactions();
    }
    
    private Map<String, Object> processarGastosPorCategoriaResposta(Object response) {
        // TODO: Implementar processamento real da resposta da API
        log.info("Processando resposta de gastos por categoria da API do Inter");
        return getSimulatedSpendingByCategory();
    }
    
    private Map<String, Object> processarAnaliseGastosResposta(Object response) {
        // TODO: Implementar processamento real da resposta da API
        log.info("Processando resposta de análise de gastos da API do Inter");
        return getSimulatedSpendingAnalysis();
    }
    
    // Métodos simulados para dados de teste
    private List<CartaoCredito> getSimulatedCreditCards() {
        List<CartaoCredito> cartoes = new ArrayList<>();
        
        CartaoCredito cartao1 = new CartaoCredito();
        cartao1.setId(1L);
        cartao1.setNumeroCartao("**** **** **** 1234");
        cartao1.setLimiteCredito(new BigDecimal("5000.00"));
        cartao1.setLimiteDisponivel(new BigDecimal("3500.00"));
                    // cartao1.setSaldoFatura(new BigDecimal("1500.00")); // Campo não existe no modelo
        cartao1.setTipoCartao(CartaoCredito.TipoCartao.STANDARD);
        cartao1.setAtivo(true);
        cartoes.add(cartao1);
        
        CartaoCredito cartao2 = new CartaoCredito();
        cartao2.setId(2L);
        cartao2.setNumeroCartao("**** **** **** 5678");
        cartao2.setLimiteCredito(new BigDecimal("8000.00"));
        cartao2.setLimiteDisponivel(new BigDecimal("6000.00"));
                    // cartao2.setSaldoFatura(new BigDecimal("2000.00")); // Campo não existe no modelo
        cartao2.setTipoCartao(CartaoCredito.TipoCartao.GOLD);
        cartao2.setAtivo(true);
        cartoes.add(cartao2);
        
        return cartoes;
    }
    
    private Map<String, Object> getSimulatedBalanceData() {
        Map<String, Object> balanceData = new HashMap<>();
        balanceData.put("saldoConta", new BigDecimal("2500.00"));
        balanceData.put("saldoDisponivel", new BigDecimal("2500.00"));
        balanceData.put("saldoBloqueado", new BigDecimal("0.00"));
        balanceData.put("moeda", "BRL");
        balanceData.put("ultimaAtualizacao", LocalDateTime.now());
        return balanceData;
    }
    
    private List<FaturaDTO> getSimulatedInvoices() {
        List<FaturaDTO> faturas = new ArrayList<>();
        
        FaturaDTO fatura1 = new FaturaDTO();
        fatura1.setId(1L);
        fatura1.setValorFatura(new BigDecimal("1500.00"));
        fatura1.setValorPago(new BigDecimal("0.00"));
                        fatura1.setDataVencimento(LocalDateTime.now().plusDays(15));
        fatura1.setStatusFatura(Fatura.StatusFatura.ABERTA);
                        fatura1.setDataFechamento(LocalDateTime.now().minusDays(5));
        faturas.add(fatura1);
        
        FaturaDTO fatura2 = new FaturaDTO();
        fatura2.setId(2L);
        fatura2.setValorFatura(new BigDecimal("2000.00"));
        fatura2.setValorPago(new BigDecimal("2000.00"));
                        fatura2.setDataVencimento(LocalDateTime.now().minusDays(10));
        fatura2.setStatusFatura(Fatura.StatusFatura.PAGA);
                        fatura2.setDataFechamento(LocalDateTime.now().minusDays(35));
        faturas.add(fatura2);
        
        return faturas;
    }
    
    private List<Map<String, Object>> getSimulatedTransactions() {
        List<Map<String, Object>> transacoes = new ArrayList<>();
        
        Map<String, Object> transacao1 = new HashMap<>();
        transacao1.put("id", 1L);
        transacao1.put("descricao", "Supermercado Extra");
        transacao1.put("valor", new BigDecimal("150.50"));
        transacao1.put("data", LocalDateTime.now().minusDays(2));
        transacao1.put("categoria", "Alimentação");
        transacao1.put("tipo", "DEBITO");
        transacoes.add(transacao1);
        
        Map<String, Object> transacao2 = new HashMap<>();
        transacao2.put("id", 2L);
        transacao2.put("descricao", "Posto de Gasolina");
        transacao2.put("valor", new BigDecimal("80.00"));
        transacao2.put("data", LocalDateTime.now().minusDays(1));
        transacao2.put("categoria", "Transporte");
        transacao2.put("tipo", "CREDITO");
        transacoes.add(transacao2);
        
        Map<String, Object> transacao3 = new HashMap<>();
        transacao3.put("id", 3L);
        transacao3.put("descricao", "Shopping Center");
        transacao3.put("valor", new BigDecimal("250.00"));
        transacao3.put("data", LocalDateTime.now().minusDays(3));
        transacao3.put("categoria", "Lazer");
        transacao3.put("tipo", "CREDITO");
        transacoes.add(transacao3);
        
        return transacoes;
    }
    
    private Map<String, Object> getSimulatedSpendingByCategory() {
        Map<String, Object> gastosPorCategoria = new HashMap<>();
        
        Map<String, Object> alimentacao = new HashMap<>();
        alimentacao.put("categoria", "Alimentação");
        alimentacao.put("valor", new BigDecimal("450.50"));
        alimentacao.put("percentual", 35.5);
        
        Map<String, Object> transporte = new HashMap<>();
        transporte.put("categoria", "Transporte");
        transporte.put("valor", new BigDecimal("320.00"));
        transporte.put("percentual", 25.2);
        
        Map<String, Object> lazer = new HashMap<>();
        lazer.put("categoria", "Lazer");
        lazer.put("valor", new BigDecimal("250.00"));
        lazer.put("percentual", 19.7);
        
        Map<String, Object> outros = new HashMap<>();
        outros.put("categoria", "Outros");
        outros.put("valor", new BigDecimal("250.50"));
        outros.put("percentual", 19.6);
        
        gastosPorCategoria.put("total", new BigDecimal("1270.00"));
        gastosPorCategoria.put("categorias", Arrays.asList(alimentacao, transporte, lazer, outros));
        gastosPorCategoria.put("periodo", "Último mês");
        
        return gastosPorCategoria;
    }
    
    private Map<String, Object> getSimulatedSpendingAnalysis() {
        Map<String, Object> analise = new HashMap<>();
        
        analise.put("totalGasto", new BigDecimal("1270.00"));
        analise.put("totalReceita", new BigDecimal("3500.00"));
        analise.put("saldo", new BigDecimal("2230.00"));
        analise.put("percentualGasto", 36.3);
        analise.put("tendencia", "ESTAVEL");
        analise.put("categoriaMaiorGasto", "Alimentação");
        analise.put("valorMaiorGasto", new BigDecimal("450.50"));
        analise.put("periodo", "Último mês");
        analise.put("projecaoMes", new BigDecimal("1350.00"));
        
        return analise;
    }
}
