package com.consumoesperto.service;

import com.consumoesperto.model.*;
import com.consumoesperto.repository.*;
import com.consumoesperto.dto.TransacaoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Serviço responsável pela sincronização automática de dados financeiros
 * das APIs bancárias para o sistema local
 * 
 * Este serviço sincroniza automaticamente:
 * - Categorias baseadas em transações bancárias
 * - Transações em tempo real dos bancos
 * - Faturas e vencimentos
 * - Compras parceladas e parcelas
 * - Saldos e limites dos cartões
 * 
 * @author ConsumoEsperto Team
 * @version 2.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialDataSyncService {

    // Serviços bancários
    private final BankSynchronizationService bankSyncService;
    private final ItauBankService itauBankService;
    private final MercadoPagoBankService mercadoPagoBankService;
    private final InterBankService interBankService;
    private final NubankBankService nubankBankService;
    
    // Repositórios
    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;
    private final FaturaRepository faturaRepository;
    private final CompraParceladaRepository compraParceladaRepository;
    private final ParcelaRepository parcelaRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;
    
    // Executor para processamento paralelo
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    
    // Cache para evitar sincronizações desnecessárias
    private final Map<String, SyncCacheEntry> syncCache = new HashMap<>();
    private static final long CACHE_TTL_MS = 15 * 60 * 1000; // 15 minutos

    /**
     * Sincroniza todos os dados financeiros de um usuário
     * 
     * @param usuarioId ID do usuário
     * @return Resumo da sincronização
     */
    @Transactional
    public Map<String, Object> syncAllFinancialData(Long usuarioId) {
        log.info("Iniciando sincronização completa de dados financeiros para usuário: {}", usuarioId);
        
        try {
            Usuario usuario = usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
            
            // Verifica se há cache válido
            String cacheKey = "sync_" + usuarioId;
            if (isCacheValid(cacheKey)) {
                log.info("Usando dados em cache para usuário: {}", usuarioId);
                return (Map<String, Object>) syncCache.get(cacheKey).getData();
            }
            
            // Executa sincronização completa
            Map<String, Object> result = new HashMap<>();
            
            // 1. Sincroniza categorias
            result.put("categorias", syncCategoriesFromBanks(usuario));
            
            // 2. Sincroniza transações
            result.put("transacoes", syncTransactionsFromBanks(usuario));
            
            // 3. Sincroniza faturas
            result.put("faturas", syncInvoicesFromBanks(usuario));
            
            // 4. Sincroniza compras parceladas
            result.put("compras_parceladas", syncInstallmentPurchasesFromBanks(usuario));
            
            // 5. Sincroniza cartões de crédito
            result.put("cartoes", syncCreditCardsFromBanks(usuario));
            
            // Cache o resultado
            cacheData(cacheKey, result);
            
            log.info("Sincronização completa finalizada para usuário: {}", usuarioId);
            return result;
            
        } catch (Exception e) {
            log.error("Erro na sincronização de dados financeiros para usuário: {}", usuarioId, e);
            throw new RuntimeException("Erro na sincronização: " + e.getMessage());
        }
    }

    /**
     * Sincroniza categorias baseadas em transações bancárias
     */
    private Map<String, Object> syncCategoriesFromBanks(Usuario usuario) {
        log.info("Sincronizando categorias para usuário: {}", usuario.getId());
        
        try {
            List<AutorizacaoBancaria> autorizacoes = autorizacaoBancariaRepository.findByUsuarioIdAndAtivoTrue(usuario.getId());
            Set<String> categoriasBancarias = new HashSet<>();
            
            for (AutorizacaoBancaria auth : autorizacoes) {
                try {
                    // Obtém transações do banco
                    List<Map<String, Object>> transacoes = getTransactionsFromBank(auth);
                    
                    // Extrai categorias das transações
                    for (Map<String, Object> transacao : transacoes) {
                        String categoria = extractCategoryFromTransaction(transacao);
                        if (categoria != null) {
                            categoriasBancarias.add(categoria);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Erro ao obter transações do banco {}: {}", auth.getBanco(), e.getMessage());
                }
            }
            
            // Cria/atualiza categorias no sistema
            List<Categoria> categoriasCriadas = createOrUpdateCategories(usuario, categoriasBancarias);
            
            Map<String, Object> result = new HashMap<>();
            result.put("total_categorias", categoriasCriadas.size());
            result.put("categorias_sincronizadas", categoriasCriadas.stream()
                    .map(this::convertCategoryToMap)
                    .collect(Collectors.toList()));
            
            return result;
            
        } catch (Exception e) {
            log.error("Erro ao sincronizar categorias para usuário: {}", usuario.getId(), e);
            return Map.of("erro", "Erro ao sincronizar categorias: " + e.getMessage());
        }
    }

    /**
     * Sincroniza transações das APIs bancárias
     */
    private Map<String, Object> syncTransactionsFromBanks(Usuario usuario) {
        log.info("Sincronizando transações para usuário: {}", usuario.getId());
        
        try {
            List<AutorizacaoBancaria> autorizacoes = autorizacaoBancariaRepository.findByUsuarioIdAndAtivoTrue(usuario.getId());
            List<Transacao> transacoesSincronizadas = new ArrayList<>();
            
            for (AutorizacaoBancaria auth : autorizacoes) {
                try {
                    // Obtém transações do banco
                    List<Map<String, Object>> transacoesBanco = getTransactionsFromBank(auth);
                    
                    // Converte e salva transações
                    for (Map<String, Object> transacaoBanco : transacoesBanco) {
                        Transacao transacao = convertBankTransactionToLocal(transacaoBanco, usuario, auth);
                        if (transacao != null) {
                            transacao = transacaoRepository.save(transacao);
                            transacoesSincronizadas.add(transacao);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Erro ao sincronizar transações do banco {}: {}", auth.getBanco(), e.getMessage());
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("total_transacoes", transacoesSincronizadas.size());
            result.put("transacoes_sincronizadas", transacoesSincronizadas.stream()
                    .map(this::convertTransactionToMap)
                    .collect(Collectors.toList()));
            
            return result;
            
        } catch (Exception e) {
            log.error("Erro ao sincronizar transações para usuário: {}", usuario.getId(), e);
            return Map.of("erro", "Erro ao sincronizar transações: " + e.getMessage());
        }
    }

    /**
     * Sincroniza faturas das APIs bancárias
     */
    private Map<String, Object> syncInvoicesFromBanks(Usuario usuario) {
        log.info("Sincronizando faturas para usuário: {}", usuario.getId());
        
        try {
            List<AutorizacaoBancaria> autorizacoes = autorizacaoBancariaRepository.findByUsuarioIdAndAtivoTrue(usuario.getId());
            List<Fatura> faturasSincronizadas = new ArrayList<>();
            
            for (AutorizacaoBancaria auth : autorizacoes) {
                try {
                    // Obtém faturas do banco
                    List<Map<String, Object>> faturasBanco = getInvoicesFromBank(auth);
                    
                    // Converte e salva faturas
                    for (Map<String, Object> faturaBanco : faturasBanco) {
                        Fatura fatura = convertBankInvoiceToLocal(faturaBanco, usuario, auth);
                        if (fatura != null) {
                            fatura = faturaRepository.save(fatura);
                            faturasSincronizadas.add(fatura);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Erro ao sincronizar faturas do banco {}: {}", auth.getBanco(), e.getMessage());
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("total_faturas", faturasSincronizadas.size());
            result.put("faturas_sincronizadas", faturasSincronizadas.stream()
                    .map(this::convertInvoiceToMap)
                    .collect(Collectors.toList()));
            
            return result;
            
        } catch (Exception e) {
            log.error("Erro ao sincronizar faturas para usuário: {}", usuario.getId(), e);
            return Map.of("erro", "Erro ao sincronizar faturas: " + e.getMessage());
        }
    }

    /**
     * Sincroniza compras parceladas das APIs bancárias
     */
    private Map<String, Object> syncInstallmentPurchasesFromBanks(Usuario usuario) {
        log.info("Sincronizando compras parceladas para usuário: {}", usuario.getId());
        
        try {
            List<AutorizacaoBancaria> autorizacoes = autorizacaoBancariaRepository.findByUsuarioIdAndAtivoTrue(usuario.getId());
            List<CompraParcelada> comprasSincronizadas = new ArrayList<>();
            
            for (AutorizacaoBancaria auth : autorizacoes) {
                try {
                    // Obtém compras parceladas do banco
                    List<Map<String, Object>> comprasBanco = getInstallmentPurchasesFromBank(auth);
                    
                    // Converte e salva compras parceladas
                    for (Map<String, Object> compraBanco : comprasBanco) {
                        CompraParcelada compra = convertBankInstallmentPurchaseToLocal(compraBanco, usuario, auth);
                        if (compra != null) {
                            compra = compraParceladaRepository.save(compra);
                            
                            // Cria parcelas
                            createParcelasForCompra(compra, compraBanco);
                            
                            comprasSincronizadas.add(compra);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Erro ao sincronizar compras parceladas do banco {}: {}", auth.getBanco(), e.getMessage());
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("total_compras", comprasSincronizadas.size());
            result.put("compras_sincronizadas", comprasSincronizadas.stream()
                    .map(this::convertInstallmentPurchaseToMap)
                    .collect(Collectors.toList()));
            
            return result;
            
        } catch (Exception e) {
            log.error("Erro ao sincronizar compras parceladas para usuário: {}", usuario.getId(), e);
            return Map.of("erro", "Erro ao sincronizar compras parceladas: " + e.getMessage());
        }
    }

    /**
     * Sincroniza cartões de crédito das APIs bancárias
     */
    private Map<String, Object> syncCreditCardsFromBanks(Usuario usuario) {
        log.info("Sincronizando cartões de crédito para usuário: {}", usuario.getId());
        
        try {
            List<AutorizacaoBancaria> autorizacoes = autorizacaoBancariaRepository.findByUsuarioIdAndAtivoTrue(usuario.getId());
            List<CartaoCredito> cartoesSincronizados = new ArrayList<>();
            
            for (AutorizacaoBancaria auth : autorizacoes) {
                try {
                    // Obtém cartões do banco
                    List<Map<String, Object>> cartoesBanco = getCreditCardsFromBank(auth);
                    
                    // Converte e salva cartões
                    for (Map<String, Object> cartaoBanco : cartoesBanco) {
                        CartaoCredito cartao = convertBankCreditCardToLocal(cartaoBanco, usuario, auth);
                        if (cartao != null) {
                            cartao = cartaoCreditoRepository.save(cartao);
                            cartoesSincronizados.add(cartao);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Erro ao sincronizar cartões do banco {}: {}", auth.getBanco(), e.getMessage());
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("total_cartoes", cartoesSincronizados.size());
            result.put("cartoes_sincronizados", cartoesSincronizados.stream()
                    .map(this::convertCreditCardToMap)
                    .collect(Collectors.toList()));
            
            return result;
            
        } catch (Exception e) {
            log.error("Erro ao sincronizar cartões para usuário: {}", usuario.getId(), e);
            return Map.of("erro", "Erro ao sincronizar cartões: " + e.getMessage());
        }
    }

    /**
     * Obtém transações de um banco específico
     */
    private List<Map<String, Object>> getTransactionsFromBank(AutorizacaoBancaria auth) {
        switch (auth.getBanco()) {
            case "ITAU":
                return itauBankService.getTransactions(auth);
            case "MERCADO_PAGO":
                return mercadoPagoBankService.getTransactions(auth);
            case "INTER":
                return interBankService.getTransactions(auth);
            case "NUBANK":
                return nubankBankService.getTransactions(auth);
            default:
                log.warn("Tipo de banco não suportado: {}", auth.getBanco());
                return new ArrayList<>();
        }
    }

    /**
     * Obtém faturas de um banco específico
     */
    private List<Map<String, Object>> getInvoicesFromBank(AutorizacaoBancaria auth) {
        switch (auth.getBanco()) {
            case "ITAU":
                return itauBankService.getInvoices(auth);
            case "MERCADO_PAGO":
                return mercadoPagoBankService.getInvoices(auth);
            case "INTER":
                return interBankService.getInvoices(auth);
            case "NUBANK":
                return nubankBankService.getInvoices(auth);
            default:
                log.warn("Tipo de banco não suportado: {}", auth.getBanco());
                return new ArrayList<>();
        }
    }

    /**
     * Obtém compras parceladas de um banco específico
     */
    private List<Map<String, Object>> getInstallmentPurchasesFromBank(AutorizacaoBancaria auth) {
        // Por enquanto, retorna lista vazia pois os métodos não estão implementados
        // TODO: Implementar métodos getInstallmentPurchases nos serviços bancários
        log.info("Método getInstallmentPurchases não implementado para banco: {}", auth.getBanco());
        return new ArrayList<>();
    }

    /**
     * Obtém cartões de crédito de um banco específico
     */
    private List<Map<String, Object>> getCreditCardsFromBank(AutorizacaoBancaria auth) {
        switch (auth.getBanco()) {
            case "ITAU":
                return itauBankService.getCreditCards(auth);
            case "MERCADO_PAGO":
                return mercadoPagoBankService.getCreditCards(auth);
            case "INTER":
                return interBankService.getCreditCards(auth);
            case "NUBANK":
                return nubankBankService.getCreditCards(auth);
            default:
                log.warn("Tipo de banco não suportado: {}", auth.getBanco());
                return new ArrayList<>();
        }
    }

    /**
     * Extrai categoria de uma transação bancária
     */
    private String extractCategoryFromTransaction(Map<String, Object> transacao) {
        // Lógica para extrair categoria baseada na descrição, MCC, etc.
        String descricao = (String) transacao.get("description");
        String mcc = (String) transacao.get("mcc");
        String tipo = (String) transacao.get("type");
        
        if (descricao != null) {
            return categorizeByDescription(descricao);
        } else if (mcc != null) {
            return categorizeByMCC(mcc);
        } else if (tipo != null) {
            return categorizeByType(tipo);
        }
        
        return "Outros";
    }

    /**
     * Categoriza transação por descrição
     */
    private String categorizeByDescription(String descricao) {
        String descLower = descricao.toLowerCase();
        
        if (descLower.contains("supermercado") || descLower.contains("extra") || 
            descLower.contains("carrefour") || descLower.contains("pao de acucar")) {
            return "Alimentação";
        } else if (descLower.contains("uber") || descLower.contains("99") || 
                   descLower.contains("taxi") || descLower.contains("onibus")) {
            return "Transporte";
        } else if (descLower.contains("netflix") || descLower.contains("spotify") || 
                   descLower.contains("youtube")) {
            return "Entretenimento";
        } else if (descLower.contains("farmacia") || descLower.contains("drogaria")) {
            return "Saúde";
        }
        
        return "Outros";
    }

    /**
     * Categoriza transação por MCC (Merchant Category Code)
     */
    private String categorizeByMCC(String mcc) {
        switch (mcc) {
            case "5411": return "Alimentação";
            case "5541": return "Combustível";
            case "5812": return "Restaurantes";
            case "5912": return "Farmácias";
            case "5999": return "Outros";
            default: return "Outros";
        }
    }

    /**
     * Categoriza transação por tipo
     */
    private String categorizeByType(String tipo) {
        switch (tipo.toLowerCase()) {
            case "food": return "Alimentação";
            case "transport": return "Transporte";
            case "entertainment": return "Entretenimento";
            case "health": return "Saúde";
            default: return "Outros";
        }
    }

    /**
     * Cria ou atualiza categorias no sistema
     */
    private List<Categoria> createOrUpdateCategories(Usuario usuario, Set<String> categoriasBancarias) {
        List<Categoria> categoriasCriadas = new ArrayList<>();
        
        for (String nomeCategoria : categoriasBancarias) {
            Categoria categoria = categoriaRepository.findByUsuarioIdAndNome(usuario.getId(), nomeCategoria);
            
            if (categoria == null) {
                categoria = new Categoria();
                categoria.setUsuario(usuario);
                categoria.setNome(nomeCategoria);
                categoria.setDescricao("Categoria sincronizada automaticamente do banco");
                categoria.setCor(generateRandomColor());
                categoria.setIcone(generateIconForCategory(nomeCategoria));
                categoria.setAtivo(true);
                categoria.setDataCriacao(LocalDateTime.now());
            }
            
            categoria = categoriaRepository.save(categoria);
            categoriasCriadas.add(categoria);
        }
        
        return categoriasCriadas;
    }

    /**
     * Converte transação bancária para transação local
     */
    private Transacao convertBankTransactionToLocal(Map<String, Object> transacaoBanco, Usuario usuario, AutorizacaoBancaria auth) {
        try {
            Transacao transacao = new Transacao();
            transacao.setUsuario(usuario);
            transacao.setDescricao((String) transacaoBanco.get("description"));
            transacao.setValor(new BigDecimal(transacaoBanco.get("amount").toString()));
            transacao.setTipoTransacao(Transacao.TipoTransacao.DESPESA);
            transacao.setDataTransacao(LocalDateTime.parse((String) transacaoBanco.get("date")));
            transacao.setDataCriacao(LocalDateTime.now());
            
            // Associa categoria se existir
            String categoriaNome = extractCategoryFromTransaction(transacaoBanco);
            if (categoriaNome != null) {
                Categoria categoria = categoriaRepository.findByUsuarioIdAndNome(usuario.getId(), categoriaNome);
                if (categoria != null) {
                    transacao.setCategoria(categoria);
                }
            }
            
            return transacao;
        } catch (Exception e) {
            log.warn("Erro ao converter transação bancária: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Converte fatura bancária para fatura local
     */
    private Fatura convertBankInvoiceToLocal(Map<String, Object> faturaBanco, Usuario usuario, AutorizacaoBancaria auth) {
        try {
            Fatura fatura = new Fatura();
            fatura.setCartaoCredito(findOrCreateCreditCard(usuario, faturaBanco, auth));
            fatura.setMes((Integer) faturaBanco.get("month"));
            fatura.setAno((Integer) faturaBanco.get("year"));
            fatura.setValorTotal(new BigDecimal(faturaBanco.get("totalAmount").toString()));
            fatura.setValorPago(new BigDecimal(faturaBanco.get("paidAmount").toString()));
            fatura.setStatus(Fatura.StatusFatura.fromString((String) faturaBanco.get("status")));
            fatura.setDataVencimento(LocalDateTime.parse((String) faturaBanco.get("dueDate")));
            fatura.setDataCriacao(LocalDateTime.now());
            
            return fatura;
        } catch (Exception e) {
            log.warn("Erro ao converter fatura bancária: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Converte compra parcelada bancária para compra local
     */
    private CompraParcelada convertBankInstallmentPurchaseToLocal(Map<String, Object> compraBanco, Usuario usuario, AutorizacaoBancaria auth) {
        try {
            CompraParcelada compra = new CompraParcelada();
            compra.setUsuario(usuario);
            compra.setCartaoCredito(findOrCreateCreditCard(usuario, compraBanco, auth));
            compra.setDescricao((String) compraBanco.get("description"));
            compra.setValorTotal(new BigDecimal(compraBanco.get("totalAmount").toString()));
            compra.setNumeroParcelas((Integer) compraBanco.get("installments"));
            compra.setValorParcela(new BigDecimal(compraBanco.get("installmentAmount").toString()));
            compra.setDataPrimeiraParcela(LocalDateTime.parse((String) compraBanco.get("firstInstallmentDate")));
            compra.setAtivo(true);
            compra.setDataCriacao(LocalDateTime.now());
            
            return compra;
        } catch (Exception e) {
            log.warn("Erro ao converter compra parcelada bancária: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Converte cartão bancário para cartão local
     */
    private CartaoCredito convertBankCreditCardToLocal(Map<String, Object> cartaoBanco, Usuario usuario, AutorizacaoBancaria auth) {
        try {
            CartaoCredito cartao = new CartaoCredito();
            cartao.setUsuario(usuario);
            cartao.setNome((String) cartaoBanco.get("name"));
            cartao.setBandeira((String) cartaoBanco.get("brand"));
            cartao.setLimite(new BigDecimal(cartaoBanco.get("limit").toString()));
            cartao.setLimiteDisponivel(new BigDecimal(cartaoBanco.get("availableLimit").toString()));
            cartao.setDiaFechamento((Integer) cartaoBanco.get("closingDay"));
            cartao.setDiaVencimento((Integer) cartaoBanco.get("dueDay"));
            cartao.setAtivo(true);
            cartao.setDataCriacao(LocalDateTime.now());
            
            return cartao;
        } catch (Exception e) {
            log.warn("Erro ao converter cartão bancário: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Encontra ou cria cartão de crédito
     */
    private CartaoCredito findOrCreateCreditCard(Usuario usuario, Map<String, Object> dados, AutorizacaoBancaria auth) {
        String nomeCartao = (String) dados.get("cardName");
        if (nomeCartao == null) {
            nomeCartao = auth.getBanco().toString();
        }
        
        CartaoCredito cartaoExistente = cartaoCreditoRepository.findByUsuarioIdAndNome(usuario.getId(), nomeCartao);
        if (cartaoExistente != null) {
            return cartaoExistente;
        }
        
        CartaoCredito cartao = new CartaoCredito();
        cartao.setUsuario(usuario);
        cartao.setNome(nomeCartao);
        cartao.setBandeira("N/A");
        cartao.setAtivo(true);
        cartao.setDataCriacao(LocalDateTime.now());
        return cartaoCreditoRepository.save(cartao);
    }

    /**
     * Cria parcelas para uma compra parcelada
     */
    private void createParcelasForCompra(CompraParcelada compra, Map<String, Object> compraBanco) {
        try {
            Integer numeroParcelas = (Integer) compraBanco.get("installments");
            BigDecimal valorParcela = compra.getValorParcela();
            LocalDateTime dataPrimeiraParcela = compra.getDataPrimeiraParcela();
            
            for (int i = 1; i <= numeroParcelas; i++) {
                Parcela parcela = new Parcela();
                parcela.setCompraParcelada(compra);
                parcela.setNumeroParcela(i);
                parcela.setValor(valorParcela);
                parcela.setDataVencimento(dataPrimeiraParcela.plusMonths(i - 1));
                parcela.setStatus(Parcela.StatusParcela.PENDENTE);
                parcela.setDataCriacao(LocalDateTime.now());
                
                parcelaRepository.save(parcela);
            }
        } catch (Exception e) {
            log.warn("Erro ao criar parcelas para compra: {}", e.getMessage());
        }
    }

    /**
     * Gera cor aleatória para categoria
     */
    private String generateRandomColor() {
        String[] cores = {"#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7", "#DDA0DD", "#98D8C8", "#F7DC6F"};
        return cores[new Random().nextInt(cores.length)];
    }

    /**
     * Gera ícone para categoria
     */
    private String generateIconForCategory(String nomeCategoria) {
        switch (nomeCategoria.toLowerCase()) {
            case "alimentação": return "restaurant";
            case "transporte": return "directions_car";
            case "saúde": return "local_hospital";
            case "entretenimento": return "movie";
            case "educação": return "school";
            default: return "shopping_cart";
        }
    }

    // Métodos de conversão para Map (para retorno da API)
    private Map<String, Object> convertCategoryToMap(Categoria categoria) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", categoria.getId());
        map.put("nome", categoria.getNome());
        map.put("descricao", categoria.getDescricao());
        map.put("cor", categoria.getCor());
        map.put("icone", categoria.getIcone());
        map.put("ativo", categoria.getAtivo());
        return map;
    }

    private Map<String, Object> convertTransactionToMap(Transacao transacao) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", transacao.getId());
        map.put("descricao", transacao.getDescricao());
        map.put("valor", transacao.getValor());
        map.put("tipo_transacao", transacao.getTipoTransacao());
        map.put("data_transacao", transacao.getDataTransacao());
        if (transacao.getCategoria() != null) {
            map.put("categoria", convertCategoryToMap(transacao.getCategoria()));
        }
        return map;
    }

    private Map<String, Object> convertInvoiceToMap(Fatura fatura) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", fatura.getId());
        map.put("mes", fatura.getMes());
        map.put("ano", fatura.getAno());
        map.put("valor_total", fatura.getValorTotal());
        map.put("valor_pago", fatura.getValorPago());
        map.put("status", fatura.getStatus());
        map.put("data_vencimento", fatura.getDataVencimento());
        return map;
    }

    private Map<String, Object> convertInstallmentPurchaseToMap(CompraParcelada compra) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", compra.getId());
        map.put("descricao", compra.getDescricao());
        map.put("valor_total", compra.getValorTotal());
        map.put("numero_parcelas", compra.getNumeroParcelas());
        map.put("valor_parcela", compra.getValorParcela());
        map.put("data_primeira_parcela", compra.getDataPrimeiraParcela());
        return map;
    }

    private Map<String, Object> convertCreditCardToMap(CartaoCredito cartao) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", cartao.getId());
        map.put("nome", cartao.getNome());
        map.put("bandeira", cartao.getBandeira());
        map.put("limite", cartao.getLimite());
        map.put("limite_disponivel", cartao.getLimiteDisponivel());
        map.put("dia_fechamento", cartao.getDiaFechamento());
        map.put("dia_vencimento", cartao.getDiaVencimento());
        return map;
    }

    // Métodos de cache
    private boolean isCacheValid(String cacheKey) {
        SyncCacheEntry entry = syncCache.get(cacheKey);
        return entry != null && (System.currentTimeMillis() - entry.getTimestamp()) < CACHE_TTL_MS;
    }

    private void cacheData(String cacheKey, Object data) {
        syncCache.put(cacheKey, new SyncCacheEntry(data, System.currentTimeMillis()));
    }

    /**
     * Sincronização automática agendada (a cada 15 minutos)
     */
    @Scheduled(fixedRate = 15 * 60 * 1000) // 15 minutos
    public void scheduledSync() {
        log.info("Executando sincronização automática agendada");
        
        try {
            List<Usuario> usuarios = usuarioRepository.findAll();
            for (Usuario usuario : usuarios) {
                try {
                    syncAllFinancialData(usuario.getId());
                } catch (Exception e) {
                    log.error("Erro na sincronização automática para usuário: {}", usuario.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Erro na sincronização automática agendada", e);
        }
    }

    /**
     * Classe interna para cache
     */
    private static class SyncCacheEntry {
        private final Object data;
        private final long timestamp;

        public SyncCacheEntry(Object data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }

        public Object getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }
}
