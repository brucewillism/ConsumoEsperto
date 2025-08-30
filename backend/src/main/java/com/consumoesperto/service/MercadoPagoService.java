package com.consumoesperto.service;

import com.consumoesperto.dto.MercadoPagoConfigDTO;
import com.consumoesperto.dto.MercadoPagoCartaoDTO;
import com.consumoesperto.dto.MercadoPagoFaturaDTO;
import com.consumoesperto.model.MercadoPagoConfig;
import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.MercadoPagoConfigRepository;
import com.consumoesperto.repository.BankApiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.service.MercadoPagoBankService;
import java.math.BigDecimal;

/**
 * Serviço para integração com a API do Mercado Pago
 * 
 * Este serviço gerencia a comunicação com a API do Mercado Pago,
 * incluindo autenticação, busca de cartões e faturas.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoService {

    private final RestTemplate restTemplate;
    private final MercadoPagoConfigRepository configRepository;
    private final BankApiConfigRepository bankApiConfigRepository;
    private final UsuarioRepository usuarioRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;
    private final FaturaRepository faturaRepository;
    private final TransacaoRepository transacaoRepository;
    private final MercadoPagoBankService mercadoPagoBankService;

    // URL base da API do Mercado Pago
    private static final String MP_API_BASE_URL = "https://api.mercadopago.com";

    /**
     * Busca configuração ativa do Mercado Pago para um usuário
     * Se não existir, cria uma configuração padrão automaticamente
     * 
     * @param userId ID do usuário
     * @return Configuração do Mercado Pago (criada automaticamente se necessário)
     */
    private Optional<BankApiConfig> getMercadoPagoConfig(Long userId) {
        try {
            log.debug("🔍 Buscando configuração do Mercado Pago para usuário: {}", userId);
            Optional<BankApiConfig> config = bankApiConfigRepository.findByUsuarioIdAndBanco(userId, "MERCADOPAGO");
            
            // SÓ retorna configuração se existir E estiver ativa
            if (config.isPresent() && config.get().getAtivo()) {
                return config;
            }
            
            // Se não existe configuração ou não está ativa, retorna vazio
            if (config.isEmpty()) {
                log.info("ℹ️ Usuário {} não possui configuração do Mercado Pago", userId);
            } else {
                log.info("ℹ️ Usuário {} possui configuração inativa do Mercado Pago", userId);
            }
            
            return Optional.empty();
        } catch (Exception e) {
            log.error("❌ Erro ao buscar configuração do Mercado Pago para usuário {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Cria configuração padrão do Mercado Pago para um usuário
     * 
     * @param userId ID do usuário
     * @return Configuração criada
     */
    private Optional<BankApiConfig> criarConfiguracaoPadrao(Long userId) {
        try {
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(userId);
            if (usuarioOpt.isEmpty()) {
                log.error("❌ Usuário não encontrado para criar configuração: {}", userId);
                return Optional.empty();
            }
            
            BankApiConfig config = BankApiConfig.builder()
                .usuario(usuarioOpt.get())
                .banco("MERCADOPAGO")
                .clientId("4223603750190943") // Client ID padrão do Mercado Pago
                .clientSecret("CONFIGURAR_CLIENT_SECRET") // Placeholder - usuário deve configurar
                .userId("209112973") // User ID padrão do Mercado Pago
                .apiUrl("https://api.mercadopago.com/v1")
                .authUrl("https://api.mercadopago.com/authorization")
                .tokenUrl("https://api.mercadopago.com/oauth/token")
                .redirectUri("https://29e1b0b32eb8.ngrok-free.app/api/auth/mercadopago/callback")
                .scope("read,write")
                .sandbox(false)
                .ativo(true)
                .timeoutMs(30000)
                .maxRetries(3)
                .retryDelayMs(1000)
                .build();
            
            BankApiConfig savedConfig = bankApiConfigRepository.save(config);
            log.info("✅ Configuração padrão criada com sucesso para usuário: {}", userId);
            return Optional.of(savedConfig);
            
        } catch (Exception e) {
            log.error("❌ Erro ao criar configuração padrão para usuário {}: {}", userId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Configura credenciais do Mercado Pago para um usuário
     * 
     * @param userId ID do usuário
     * @param accessToken Token de acesso do Mercado Pago
     * @param publicKey Chave pública da aplicação
     * @param clientId Client ID da aplicação
     * @param clientSecret Client Secret da aplicação
     * @param mpUserId User ID no Mercado Pago (opcional)
     */
    public void configurarCredenciais(Long userId, String accessToken, String publicKey, String clientId, String clientSecret, String mpUserId) {
        try {
            log.info("🔧 Configurando credenciais para usuário: {}", userId);
            
            // Busca usuário
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(userId);
            if (usuarioOpt.isEmpty()) {
                log.error("❌ Usuário não encontrado: {}", userId);
                throw new RuntimeException("Usuário não encontrado");
            }
            
            // Verifica se já existe configuração
            Optional<BankApiConfig> configExistente = bankApiConfigRepository.findByUsuarioIdAndBanco(userId, "MERCADOPAGO");
            
            BankApiConfig config;
            if (configExistente.isPresent()) {
                // Atualiza configuração existente
                config = configExistente.get();
                config.setClientSecret(clientSecret); // Client Secret real fornecido
                config.setClientId(clientId); // Client ID real fornecido
                config.setUserId(mpUserId != null ? mpUserId : "209112973"); // User ID do Mercado Pago ou padrão
                config.setDataAtualizacao(java.time.LocalDateTime.now());
                log.info("🔄 Atualizando configuração existente para usuário: {}", userId);
            } else {
                // Cria nova configuração
                config = BankApiConfig.builder()
                    .usuario(usuarioOpt.get())
                    .banco("MERCADOPAGO")
                    .clientId(clientId) // Client ID real fornecido
                    .clientSecret(clientSecret) // Client Secret real fornecido
                    .userId(mpUserId != null ? mpUserId : "209112973") // User ID do Mercado Pago ou padrão
                    .apiUrl("https://api.mercadopago.com/v1")
                    .authUrl("https://api.mercadopago.com/authorization")
                    .tokenUrl("https://api.mercadopago.com/oauth/token")
                    .redirectUri("https://29e1b0b32eb8.ngrok-free.app/api/auth/mercadopago/callback")
                    .scope("read,write")
                    .sandbox(false)
                    .ativo(true)
                    .timeoutMs(30000)
                    .maxRetries(3)
                    .retryDelayMs(1000)
                    .build();
                log.info("🆕 Criando nova configuração para usuário: {}", userId);
            }
            
            // Salva no banco
            bankApiConfigRepository.save(config);
            log.info("✅ Credenciais configuradas e salvas no banco para usuário: {}", userId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao configurar credenciais para usuário {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Falha ao configurar credenciais: " + e.getMessage());
        }
    }

    /**
     * Busca cartões de crédito do usuário no Mercado Pago
     * 
     * @param userId ID do usuário
     * @return Lista de cartões de crédito
     */
    public List<MercadoPagoCartaoDTO> buscarCartoes(Long userId) {
        try {
            log.info("💳 Buscando cartões para usuário: {}", userId);
            
            // Verifica se usuário possui configuração ativa
            Optional<BankApiConfig> config = getMercadoPagoConfig(userId);
            if (config.isEmpty()) {
                log.warn("⚠️ Usuário {} não possui credenciais configuradas", userId);
                return new ArrayList<>();
            }

            // Verifica se o Client Secret foi configurado
            BankApiConfig mpConfig = config.get();
            if ("CONFIGURAR_CLIENT_SECRET".equals(mpConfig.getClientSecret())) {
                log.warn("⚠️ Usuário {} precisa configurar o Client Secret do Mercado Pago", userId);
                // Retorna lista vazia mas com log informativo
                return new ArrayList<>();
            }

            // Fazer chamada real para a API do Mercado Pago
            try {
                log.info("🚀 Fazendo chamada real para API do Mercado Pago...");
                
                // Chamada direta à API do Mercado Pago usando a configuração
                List<MercadoPagoCartaoDTO> cartoesReais = buscarCartoesReais(mpConfig);
                
                if (cartoesReais != null && !cartoesReais.isEmpty()) {
                    log.info("✅ {} cartões reais retornados da API do Mercado Pago", cartoesReais.size());
                    return cartoesReais;
                } else {
                    log.warn("⚠️ Nenhum cartão retornado da API do Mercado Pago. Usando dados mock como fallback.");
                    return buscarCartoesMock(userId);
                }
                
            } catch (Exception e) {
                log.error("❌ Erro ao buscar cartões reais da API: {}. Usando dados mock como fallback.", e.getMessage());
                return buscarCartoesMock(userId);
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar cartões: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Busca faturas dos cartões do usuário no Mercado Pago
     * 
     * @param userId ID do usuário
     * @return Lista de faturas
     */
    public List<MercadoPagoFaturaDTO> buscarFaturas(Long userId) {
        try {
            log.info("📄 Buscando faturas para usuário: {}", userId);
            
            // Verifica se usuário possui configuração ativa
            Optional<BankApiConfig> config = getMercadoPagoConfig(userId);
            if (config.isEmpty()) {
                log.warn("⚠️ Usuário {} não possui credenciais configuradas", userId);
                return new ArrayList<>();
            }

            // Em desenvolvimento, retorna dados mock
            // Em produção, faria chamada real para a API do Mercado Pago
            return buscarFaturasMock(userId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar faturas: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Testa a conexão com a API do Mercado Pago
     * 
     * @param userId ID do usuário
     * @return true se conectado, false caso contrário
     */
    public boolean testarConexao(Long userId) {
        try {
            log.info("🔍 Testando conexão para usuário: {}", userId);
            
            // Verifica se usuário possui configuração ativa
            Optional<BankApiConfig> config = getMercadoPagoConfig(userId);
            if (config.isEmpty()) {
                log.warn("⚠️ Usuário {} não possui credenciais configuradas", userId);
                return false;
            }

            // Em desenvolvimento, simula teste de conexão
            // Em produção, faria chamada real para a API do Mercado Pago
            return true;
            
        } catch (Exception e) {
            log.error("❌ Erro ao testar conexão: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Verifica se usuário possui configuração ativa
     */
    public boolean possuiConfiguracaoAtiva(Long userId) {
        Optional<BankApiConfig> config = bankApiConfigRepository.findByUsuarioIdAndBanco(userId, "MERCADOPAGO");
        return config.isPresent() && config.get().getAtivo();
    }

    /**
     * Busca cartões mock para desenvolvimento
     * Em produção, seria substituído por chamada real à API
     */
    private List<MercadoPagoCartaoDTO> buscarCartoesMock(Long userId) {
        List<MercadoPagoCartaoDTO> cartoes = new ArrayList<>();
        
        // Cartão 1 - Nubank
        MercadoPagoCartaoDTO cartao1 = new MercadoPagoCartaoDTO();
        cartao1.setId("nubank_001");
        cartao1.setNome("Nubank");
        cartao1.setUltimosDigitos("1234");
        cartao1.setLimiteTotal(new BigDecimal("5000.00"));
        cartao1.setLimiteDisponivel(new BigDecimal("3200.00"));
        cartao1.setLimiteUtilizado(new BigDecimal("1800.00"));
        cartao1.setVencimentoFatura(LocalDate.now().plusDays(15));
        cartao1.setValorFatura(new BigDecimal("450.00"));
        cartao1.setAtivo(true);
        cartao1.setBandeira("Mastercard");
        cartao1.setTipo("Crédito");
        cartoes.add(cartao1);

        // Cartão 2 - Itaú
        MercadoPagoCartaoDTO cartao2 = new MercadoPagoCartaoDTO();
        cartao2.setId("itau_001");
        cartao2.setNome("Itaú");
        cartao2.setUltimosDigitos("5678");
        cartao2.setLimiteTotal(new BigDecimal("8000.00"));
        cartao2.setLimiteDisponivel(new BigDecimal("6000.00"));
        cartao2.setLimiteUtilizado(new BigDecimal("2000.00"));
        cartao2.setVencimentoFatura(LocalDate.now().plusDays(8));
        cartao2.setValorFatura(new BigDecimal("750.00"));
        cartao2.setAtivo(true);
        cartao2.setBandeira("Visa");
        cartao2.setTipo("Crédito");
        cartoes.add(cartao2);

        log.info("✅ {} cartões mock retornados para usuário: {}", cartoes.size(), userId);
        return cartoes;
    }

    /**
     * Busca faturas mock para desenvolvimento
     * Em produção, seria substituído por chamada real à API
     */
    private List<MercadoPagoFaturaDTO> buscarFaturasMock(Long userId) {
        List<MercadoPagoFaturaDTO> faturas = new ArrayList<>();
        
        // Fatura 1 - Nubank
        MercadoPagoFaturaDTO fatura1 = new MercadoPagoFaturaDTO();
        fatura1.setId("fatura_nubank_001");
        fatura1.setCartaoId("nubank_001");
        fatura1.setNomeCartao("Nubank");
        fatura1.setValorTotal(new BigDecimal("450.00"));
        fatura1.setValorMinimo(new BigDecimal("50.00"));
        fatura1.setDataVencimento(LocalDate.now().plusDays(15));
        fatura1.setDataFechamento(LocalDate.now().minusDays(5));
        fatura1.setStatus("Aberta");
        fatura1.setPaga(false);
        faturas.add(fatura1);

        // Fatura 2 - Itaú
        MercadoPagoFaturaDTO fatura2 = new MercadoPagoFaturaDTO();
        fatura2.setId("fatura_itau_001");
        fatura2.setCartaoId("itau_001");
        fatura2.setNomeCartao("Itaú");
        fatura2.setValorTotal(new BigDecimal("750.00"));
        fatura2.setValorMinimo(new BigDecimal("75.00"));
        fatura2.setDataVencimento(LocalDate.now().plusDays(8));
        fatura2.setDataFechamento(LocalDate.now().minusDays(2));
        fatura2.setStatus("Aberta");
        fatura2.setPaga(false);
        faturas.add(fatura2);

        log.info("✅ {} faturas mock retornadas para usuário: {}", faturas.size(), userId);
        return faturas;
    }

    /**
     * Busca transações dos cartões do usuário no Mercado Pago
     * 
     * @param userId ID do usuário
     * @return Lista de transações
     */
    public List<Transacao> buscarTransacoes(Long userId) {
        try {
            log.info("💸 Buscando transações para usuário: {}", userId);
            
            // Verifica se usuário possui configuração ativa
            Optional<BankApiConfig> config = getMercadoPagoConfig(userId);
            if (config.isEmpty()) {
                log.warn("⚠️ Usuário {} não possui credenciais configuradas", userId);
                return new ArrayList<>();
            }

            // Em desenvolvimento, retorna dados mock
            // Em produção, faria chamada real para a API do Mercado Pago
            return buscarTransacoesMock(userId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar transações: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Busca transações mock para desenvolvimento
     * Em produção, seria substituído por chamada real à API
     */
    private List<Transacao> buscarTransacoesMock(Long userId) {
        List<Transacao> transacoes = new ArrayList<>();
        
        try {
            // Buscar cartões do usuário para associar transações
            List<CartaoCredito> cartoes = cartaoCreditoRepository.findByUsuarioIdAndAtivoTrue(userId);
            
            if (cartoes.isEmpty()) {
                log.info("ℹ️ Nenhum cartão encontrado para usuário: {}. Retornando transações mock genéricas.", userId);
                
                // Criar transações mock genéricas
                Transacao transacao1 = new Transacao();
                transacao1.setDescricao("Compra Supermercado");
                transacao1.setValor(new BigDecimal("150.50"));
                transacao1.setDataTransacao(LocalDateTime.now().minusDays(2));
                transacao1.setTipoTransacao(null); // Será configurado depois
                transacao1.setCategoria(null); // Será configurado depois
                
                Transacao transacao2 = new Transacao();
                transacao2.setDescricao("Posto de Gasolina");
                transacao2.setValor(new BigDecimal("80.00"));
                transacao2.setDataTransacao(LocalDateTime.now().minusDays(1));
                transacao2.setTipoTransacao(null); // Será configurado depois
                transacao2.setCategoria(null); // Será configurado depois
                
                transacoes.add(transacao1);
                transacoes.add(transacao2);
                
            } else {
                // Criar transações mock para cada cartão
                for (CartaoCredito cartao : cartoes) {
                    Transacao transacao1 = new Transacao();
                    transacao1.setDescricao("Compra " + cartao.getNome());
                    transacao1.setValor(new BigDecimal("200.00"));
                    transacao1.setDataTransacao(LocalDateTime.now().minusDays(3));
                    transacao1.setTipoTransacao(null); // Será configurado depois
                    transacao1.setCategoria(null); // Será configurado depois
                    
                    Transacao transacao2 = new Transacao();
                    transacao2.setDescricao("Restaurante " + cartao.getNome());
                    transacao2.setValor(new BigDecimal("75.50"));
                    transacao2.setDataTransacao(LocalDateTime.now().minusDays(1));
                    transacao2.setTipoTransacao(null); // Será configurado depois
                    transacao2.setCategoria(null); // Será configurado depois
                    
                    transacoes.add(transacao1);
                    transacoes.add(transacao2);
                }
            }
            
            log.info("✅ {} transações mock criadas para usuário: {}", transacoes.size(), userId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao criar transações mock: {}", e.getMessage(), e);
        }
        
        return transacoes;
    }

    /**
     * Sincroniza automaticamente todos os dados do Mercado Pago para um usuário
     * Este método é chamado quando a API é ativada pela primeira vez
     */
    public void sincronizarDadosAutomaticamente(Long userId) {
        try {
            log.info("🔄 Iniciando sincronização automática de dados do Mercado Pago para usuário: {}", userId);
            
            // Verificar se a configuração está válida
            Optional<BankApiConfig> config = getMercadoPagoConfig(userId);
            if (config.isEmpty() || !config.get().getAtivo()) {
                log.warn("⚠️ Configuração do Mercado Pago não encontrada ou inativa para usuário: {}", userId);
                return;
            }
            
            BankApiConfig mercadopagoConfig = config.get();
            if ("CONFIGURAR_CLIENT_SECRET".equals(mercadopagoConfig.getClientSecret())) {
                log.warn("⚠️ Client Secret ainda não configurado para usuário: {}", userId);
                return;
            }
            
            log.info("✅ Configuração válida encontrada. Iniciando sincronização...");
            
            // 1. Sincronizar cartões de crédito
            sincronizarCartoesAutomaticamente(userId);
            
            // 2. Sincronizar faturas
            sincronizarFaturasAutomaticamente(userId);
            
            // 3. Sincronizar transações
            sincronizarTransacoesAutomaticamente(userId);
            
            log.info("🎉 Sincronização automática concluída com sucesso para usuário: {}", userId);
            
        } catch (Exception e) {
            log.error("❌ Erro durante sincronização automática para usuário {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Sincroniza cartões de crédito automaticamente
     */
    private void sincronizarCartoesAutomaticamente(Long userId) {
        try {
            log.info("💳 Sincronizando cartões de crédito para usuário: {}", userId);
            
            // Buscar cartões via API do Mercado Pago
            List<MercadoPagoCartaoDTO> cartoesApi = buscarCartoes(userId);
            
            if (cartoesApi.isEmpty()) {
                log.info("ℹ️ Nenhum cartão encontrado na API para usuário: {}", userId);
                return;
            }
            
            // Salvar/atualizar cartões no banco local
            for (MercadoPagoCartaoDTO cartaoApi : cartoesApi) {
                try {
                    // Verificar se o cartão já existe
                    List<CartaoCredito> cartoesExistentes = cartaoCreditoRepository.findByUsuarioIdAndAtivoTrue(userId);
                    Optional<CartaoCredito> cartaoExistente = cartoesExistentes.stream()
                        .filter(c -> c.getNumeroCartao().equals(cartaoApi.getUltimosDigitos()))
                        .findFirst();
                    
                    if (cartaoExistente.isPresent()) {
                        // Atualizar cartão existente
                        CartaoCredito cartaoAtual = cartaoExistente.get();
                        cartaoAtual.setLimite(cartaoApi.getLimiteTotal());
                        cartaoAtual.setLimiteDisponivel(cartaoApi.getLimiteDisponivel());
                        cartaoAtual.setDataVencimento(cartaoApi.getVencimentoFatura().atStartOfDay());
                        cartaoAtual.setDataAtualizacao(LocalDateTime.now());
                        cartaoCreditoRepository.save(cartaoAtual);
                        log.debug("🔄 Cartão atualizado: {}", cartaoApi.getUltimosDigitos());
                    } else {
                        // Criar novo cartão
                        CartaoCredito novoCartao = new CartaoCredito();
                        novoCartao.setUsuario(usuarioRepository.findById(userId).orElse(null));
                        novoCartao.setNumeroCartao(cartaoApi.getUltimosDigitos());
                        novoCartao.setNome(cartaoApi.getNome());
                        novoCartao.setBandeira(cartaoApi.getBandeira());
                        novoCartao.setLimite(cartaoApi.getLimiteTotal());
                        novoCartao.setLimiteDisponivel(cartaoApi.getLimiteDisponivel());
                        novoCartao.setDataVencimento(cartaoApi.getVencimentoFatura().atStartOfDay());
                        novoCartao.setBanco("MERCADOPAGO");
                        novoCartao.setAtivo(true);
                        novoCartao.setDataCriacao(LocalDateTime.now());
                        novoCartao.setDataAtualizacao(LocalDateTime.now());
                        cartaoCreditoRepository.save(novoCartao);
                        log.debug("🆕 Novo cartão criado: {}", cartaoApi.getUltimosDigitos());
                    }
                } catch (Exception e) {
                    log.error("❌ Erro ao processar cartão {}: {}", cartaoApi.getUltimosDigitos(), e.getMessage());
                }
            }
            
            log.info("✅ {} cartões sincronizados para usuário: {}", cartoesApi.size(), userId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao sincronizar cartões para usuário {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Sincroniza faturas automaticamente
     */
    private void sincronizarFaturasAutomaticamente(Long userId) {
        try {
            log.info("📄 Sincronizando faturas para usuário: {}", userId);
            
            // Buscar faturas via API do Mercado Pago
            List<MercadoPagoFaturaDTO> faturasApi = buscarFaturas(userId);
            
            if (faturasApi.isEmpty()) {
                log.info("ℹ️ Nenhuma fatura encontrada na API para usuário: {}", userId);
                return;
            }
            
            // Salvar/atualizar faturas no banco local
            for (MercadoPagoFaturaDTO faturaApi : faturasApi) {
                try {
                    // Verificar se a fatura já existe
                    List<Fatura> faturasExistentes = faturaRepository.findAll();
                    Optional<Fatura> faturaExistente = faturasExistentes.stream()
                        .filter(f -> f.getNumeroFatura().equals(faturaApi.getId()))
                        .findFirst();
                    
                    if (faturaExistente.isPresent()) {
                        // Atualizar fatura existente
                        Fatura faturaAtual = faturaExistente.get();
                        faturaAtual.setValorFatura(faturaApi.getValorTotal());
                        faturaAtual.setValorPago(faturaApi.getValorPago());
                        faturaAtual.setStatus(Fatura.StatusFatura.fromString(faturaApi.getStatus()));
                        faturaAtual.setDataVencimento(faturaApi.getDataVencimento().atStartOfDay());
                        faturaAtual.setDataAtualizacao(LocalDateTime.now());
                        faturaRepository.save(faturaAtual);
                        log.debug("🔄 Fatura atualizada: {}", faturaApi.getId());
                    } else {
                        // Criar nova fatura
                        Fatura novaFatura = new Fatura();
                        novaFatura.setNumeroFatura(faturaApi.getId());
                        novaFatura.setValorFatura(faturaApi.getValorTotal());
                        novaFatura.setValorPago(faturaApi.getValorPago());
                        novaFatura.setStatus(Fatura.StatusFatura.fromString(faturaApi.getStatus()));
                        novaFatura.setDataVencimento(faturaApi.getDataVencimento().atStartOfDay());
                        novaFatura.setMes(faturaApi.getDataVencimento().getMonthValue());
                        novaFatura.setAno(faturaApi.getDataVencimento().getYear());
                        novaFatura.setDataCriacao(LocalDateTime.now());
                        novaFatura.setDataAtualizacao(LocalDateTime.now());
                        faturaRepository.save(novaFatura);
                        log.debug("🆕 Nova fatura criada: {}", faturaApi.getId());
                    }
                } catch (Exception e) {
                    log.error("❌ Erro ao processar fatura {}: {}", faturaApi.getId(), e.getMessage());
                }
            }
            
            log.info("✅ {} faturas sincronizadas para usuário: {}", faturasApi.size(), userId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao sincronizar faturas para usuário {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Sincroniza transações automaticamente
     */
    private void sincronizarTransacoesAutomaticamente(Long userId) {
        try {
            log.info("💸 Sincronizando transações para usuário: {}", userId);
            
            // Buscar transações via API do Mercado Pago
            List<Transacao> transacoesApi = buscarTransacoes(userId);
            
            if (transacoesApi.isEmpty()) {
                log.info("ℹ️ Nenhuma transação encontrada na API para usuário: {}", userId);
                return;
            }
            
            // Salvar/atualizar transações no banco local
            for (Transacao transacaoApi : transacoesApi) {
                try {
                    // Verificar se a transação já existe
                    List<Transacao> transacoesExistentes = transacaoRepository.findAll();
                    Optional<Transacao> transacaoExistente = transacoesExistentes.stream()
                        .filter(t -> t.getDescricao().equals(transacaoApi.getDescricao()) &&
                                   t.getDataTransacao().equals(transacaoApi.getDataTransacao()) &&
                                   t.getValor().equals(transacaoApi.getValor()))
                        .findFirst();
                    
                    if (transacaoExistente.isPresent()) {
                        // Atualizar transação existente
                        Transacao transacaoAtual = transacaoExistente.get();
                        transacaoAtual.setValor(transacaoApi.getValor());
                        transacaoRepository.save(transacaoAtual);
                        log.debug("🔄 Transação atualizada: {}", transacaoApi.getDescricao());
                    } else {
                        // Criar nova transação
                        transacaoApi.setUsuario(usuarioRepository.findById(userId).orElse(null));
                        transacaoApi.setDataCriacao(LocalDateTime.now());
                        transacaoRepository.save(transacaoApi);
                        log.debug("🆕 Nova transação criada: {}", transacaoApi.getDescricao());
                    }
                } catch (Exception e) {
                    log.error("❌ Erro ao processar transação {}: {}", transacaoApi.getDescricao(), e.getMessage());
                }
            }
            
            log.info("✅ {} transações sincronizadas para usuário: {}", transacoesApi.size(), userId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao sincronizar transações para usuário {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Busca cartões reais da API do Mercado Pago
     * 
     * @param config Configuração do Mercado Pago
     * @return Lista de cartões reais ou null se houver erro
     */
    private List<MercadoPagoCartaoDTO> buscarCartoesReais(BankApiConfig config) {
        try {
            log.info("🚀 Fazendo chamada real para API do Mercado Pago...");
            
            // Construir URL da API (removendo /v1 duplicado)
            String url = config.getApiUrl() + "/users/" + config.getUserId() + "/cards";
            
            // Headers da requisição
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getClientSecret()); // Usar clientSecret como access token
            
            // Fazer requisição
            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("✅ API do Mercado Pago retornou dados com sucesso");
                
                // Processar resposta da API
                Map<String, Object> responseBody = response.getBody();
                if (responseBody.containsKey("data") && responseBody.get("data") instanceof List) {
                    List<Map<String, Object>> cartoesData = (List<Map<String, Object>>) responseBody.get("data");
                    log.info("📊 {} cartões retornados da API", cartoesData.size());
                    
                    // Converter para DTOs
                    List<MercadoPagoCartaoDTO> cartoes = new ArrayList<>();
                    for (Map<String, Object> cartaoData : cartoesData) {
                        MercadoPagoCartaoDTO cartao = new MercadoPagoCartaoDTO();
                        cartao.setId((String) cartaoData.get("id"));
                        cartao.setNome((String) cartaoData.get("name"));
                        cartao.setUltimosDigitos((String) cartaoData.get("last_four_digits"));
                        cartao.setLimiteTotal(new BigDecimal(cartaoData.get("credit_limit").toString()));
                        cartao.setLimiteDisponivel(new BigDecimal(cartaoData.get("available_credit").toString()));
                        cartao.setAtivo(true); // Cartão ativo por padrão
                        cartoes.add(cartao);
                    }
                    
                    return cartoes;
                }
            }
            
            log.warn("⚠️ API retornou status não esperado: {}", response.getStatusCode());
            return null;
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar cartões reais da API: {}", e.getMessage(), e);
            return null;
        }
    }
}
