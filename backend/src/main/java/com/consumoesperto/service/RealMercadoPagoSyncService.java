package com.consumoesperto.service;

import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.CategoriaRepository;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serviço para sincronização REAL com o Mercado Pago
 * 
 * Este serviço busca dados reais da API do Mercado Pago e os salva no banco local.
 * Diferencia entre receitas e despesas corretamente e detecta parcelamentos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealMercadoPagoSyncService {

    private final RestTemplate restTemplate;
    private final TransacaoRepository transacaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutorizacaoBancariaService autorizacaoBancariaService;

    /**
     * Sincroniza dados reais do Mercado Pago para um usuário
     */
    public void sincronizarDadosReais(Long userId) {
        try {
            log.info("🔄 Iniciando sincronização REAL do Mercado Pago para usuário: {}", userId);
            
            // Buscar autorização bancária
            Optional<AutorizacaoBancaria> auth = autorizacaoBancariaService
                .buscarAutorizacao(userId, BankApiService.BankType.MERCADO_PAGO);
            
            if (!auth.isPresent()) {
                log.warn("⚠️ Usuário {} não possui autorização do Mercado Pago", userId);
                return;
            }
            
            String accessToken = auth.get().getAccessToken();
            if (accessToken == null || accessToken.trim().isEmpty()) {
                log.warn("⚠️ Access Token não encontrado para usuário {}", userId);
                return;
            }
            
            // Verificar se é um token temporário ou expirado
            if (isTemporaryToken(accessToken) || auth.get().isTokenExpirado()) {
                log.info("ℹ️ Token temporário ou expirado detectado para usuário: {}. Redirecionando para OAuth2...", userId);
                return; // Não tenta usar token temporário ou expirado para chamadas reais
            }
            
            // Buscar pagamentos reais da API
            List<Map<String, Object>> pagamentos = buscarPagamentosReais(accessToken);
            
            if (pagamentos.isEmpty()) {
                log.info("ℹ️ Nenhum pagamento encontrado na API do Mercado Pago");
                return;
            }
            
            log.info("📊 Encontrados {} pagamentos reais na API", pagamentos.size());
            
            // Processar cada pagamento
            Usuario usuario = usuarioRepository.findById(userId).orElse(null);
            if (usuario == null) {
                log.error("❌ Usuário {} não encontrado", userId);
                return;
            }
            
            int sucessos = 0;
            for (Map<String, Object> pagamento : pagamentos) {
                try {
                    processarPagamentoReal(usuario, pagamento);
                    sucessos++;
                } catch (Exception e) {
                    log.error("❌ Erro ao processar pagamento {}: {}", pagamento.get("id"), e.getMessage());
                }
            }
            
            log.info("✅ Sincronização concluída: {} de {} pagamentos processados", sucessos, pagamentos.size());
            
        } catch (Exception e) {
            log.error("❌ Erro na sincronização real: {}", e.getMessage(), e);
        }
    }

    /**
     * Verifica se o token é realmente temporário (não um token real válido)
     * 
     * @param accessToken Token de acesso
     * @return true se é realmente temporário
     */
    private boolean isTemporaryToken(String accessToken) {
        if (accessToken == null) return true;
        
        // Tokens temporários têm padrões específicos que indicam que são fake
        return accessToken.contains("FIXED_TOKEN") || 
               accessToken.contains("SIMULATED") ||
               accessToken.contains("TEST_TOKEN") ||
               accessToken.contains("FAKE_TOKEN") ||
               accessToken.contains("MOCK_TOKEN") ||
               // Tokens temporários criados pelo sistema têm formato específico
               (accessToken.startsWith("TEMPORARY_AUTH_") && accessToken.length() < 100) ||
               // Tokens muito curtos que não são do Mercado Pago
               (accessToken.length() < 50 && !accessToken.startsWith("APP_USR_"));
    }

    /**
     * Busca pagamentos reais da API do Mercado Pago
     * 
     * Busca TODAS as transações disponíveis (até 1000) para garantir
     * que nenhuma transação real seja perdida.
     */
    private List<Map<String, Object>> buscarPagamentosReais(String accessToken) {
        List<Map<String, Object>> todosPagamentos = new ArrayList<>();
        int offset = 0;
        int limit = 100; // Busca em lotes de 100
        int maxPagamentos = 1000; // Limite máximo de transações
        
        try {
            while (offset < maxPagamentos) {
                String url = String.format(
                    "https://api.mercadopago.com/v1/payments/search?limit=%d&offset=%d&sort=date_created&criteria=desc",
                    limit, offset
                );
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(accessToken);
                
                HttpEntity<String> request = new HttpEntity<>(headers);
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseBody = response.getBody();
                    
                    if (responseBody.containsKey("results")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> pagamentos = (List<Map<String, Object>>) responseBody.get("results");
                        
                        if (pagamentos.isEmpty()) {
                            // Não há mais pagamentos
                            break;
                        }
                        
                        todosPagamentos.addAll(pagamentos);
                        log.info("📊 Buscando transações... {} encontradas até agora", todosPagamentos.size());
                        
                        // Se retornou menos que o limite, é a última página
                        if (pagamentos.size() < limit) {
                            break;
                        }
                        
                        offset += limit;
                    } else {
                        break;
                    }
                } else {
                    log.warn("⚠️ API retornou status: {} na página {}", response.getStatusCode(), offset);
                    break;
                }
            }
            
            log.info("✅ Total de {} pagamentos reais encontrados na API", todosPagamentos.size());
            return todosPagamentos;
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar pagamentos da API: {}", e.getMessage(), e);
            return todosPagamentos; // Retorna o que conseguiu buscar até o erro
        }
    }

    /**
     * Processa um pagamento real e cria transação no banco
     */
    private void processarPagamentoReal(Usuario usuario, Map<String, Object> pagamento) {
        try {
            Long pagamentoId = Long.valueOf(pagamento.get("id").toString());
            
            // Verificar se já existe - busca mais específica
            String descricao = pagamento.get("description") != null ? 
                pagamento.get("description").toString() : 
                "Pagamento Mercado Pago #" + pagamentoId;
            BigDecimal valor = new BigDecimal(pagamento.get("transaction_amount").toString());
            LocalDateTime dataTransacao = parseDataTransacao(pagamento.get("date_created").toString());
            
            List<Transacao> existentes = transacaoRepository.findByUsuarioIdAndDescricaoAndDataTransacaoAndValor(
                usuario.getId(),
                descricao,
                dataTransacao,
                valor
            );
            
            if (!existentes.isEmpty()) {
                log.debug("ℹ️ Pagamento {} já existe, pulando...", pagamentoId);
                return;
            }
            
            // Verificação adicional por ID do pagamento na descrição
            List<Transacao> existentesPorId = transacaoRepository.findByUsuarioIdAndDescricaoContaining(
                usuario.getId(),
                "Pagamento Mercado Pago #" + pagamentoId
            );
            
            if (!existentesPorId.isEmpty()) {
                log.debug("ℹ️ Pagamento {} já existe (por ID), pulando...", pagamentoId);
                return;
            }
            
            // Criar nova transação
            Transacao transacao = new Transacao();
            transacao.setUsuario(usuario);
            
            // Usar a descrição já definida anteriormente
            transacao.setDescricao(descricao);
            
            // Usar o valor já definido anteriormente
            transacao.setValor(valor);
            
            // Data da transação
            transacao.setDataTransacao(parseDataTransacao(pagamento.get("date_created").toString()));
            transacao.setDataCriacao(LocalDateTime.now());
            
            // Determinar tipo de transação CORRETAMENTE
            String status = pagamento.get("status").toString();
            if ("approved".equals(status)) {
                // Pagamento aprovado = DESPESA (você está gastando)
                transacao.setTipoTransacao(Transacao.TipoTransacao.DESPESA);
            } else if ("refunded".equals(status)) {
                // Estorno = RECEITA (dinheiro voltou)
                transacao.setTipoTransacao(Transacao.TipoTransacao.RECEITA);
            } else {
                // Outros status = DESPESA por padrão
                transacao.setTipoTransacao(Transacao.TipoTransacao.DESPESA);
            }
            
            // Detectar parcelamento
            Object installments = pagamento.get("installments");
            if (installments != null && Integer.parseInt(installments.toString()) > 1) {
                int parcelas = Integer.parseInt(installments.toString());
                transacao.setDescricao(descricao + " (" + parcelas + "x)");
                log.info("💳 Parcelamento detectado: {} parcelas de R$ {}", 
                    parcelas, valor.divide(new BigDecimal(parcelas), 2, BigDecimal.ROUND_HALF_UP));
            }
            
            // Categorizar automaticamente
            Categoria categoria = categorizarTransacao(descricao, usuario.getId());
            transacao.setCategoria(categoria);
            
            // Salvar no banco
            transacaoRepository.save(transacao);
            
            log.info("✅ Transação real salva: {} - R$ {} ({})", 
                transacao.getDescricao(), 
                transacao.getValor(), 
                transacao.getTipoTransacao());
            
        } catch (Exception e) {
            log.error("❌ Erro ao processar pagamento real: {}", e.getMessage(), e);
        }
    }

    /**
     * Converte data da API para LocalDateTime
     */
    private LocalDateTime parseDataTransacao(String dateStr) {
        try {
            // Mercado Pago usa formato ISO 8601
            return LocalDateTime.parse(dateStr, 
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
        } catch (Exception e) {
            log.warn("⚠️ Erro ao parsear data: {}, usando data atual", dateStr);
            return LocalDateTime.now();
        }
    }

    /**
     * Categoriza transação automaticamente baseada na descrição real
     * 
     * Este método analisa a descrição da transação e cria categorias
     * inteligentes baseadas em palavras-chave e padrões reais.
     */
    private Categoria categorizarTransacao(String descricao, Long usuarioId) {
        String descricaoLower = descricao.toLowerCase();
        
        // Mapeamento inteligente de palavras-chave para categorias
        String categoriaNome = determinarCategoriaPorDescricao(descricaoLower);
        
        // Buscar ou criar categoria
        Categoria categoria = categoriaRepository.findByUsuarioIdAndNome(usuarioId, categoriaNome);
        if (categoria == null) {
            categoria = new Categoria();
            categoria.setNome(categoriaNome);
            categoria.setDescricao("Categoria criada automaticamente baseada em: " + descricao);
            categoria.setUsuario(usuarioRepository.findById(usuarioId).orElse(null));
            categoria.setAtivo(true);
            categoria.setDataCriacao(LocalDateTime.now());
            categoria = categoriaRepository.save(categoria);
            
            log.info("✅ Nova categoria criada: {} para transação: {}", categoriaNome, descricao);
        }
        
        return categoria;
    }

    /**
     * Determina a categoria baseada na descrição da transação
     * 
     * Usa palavras-chave inteligentes para categorizar transações reais
     * do Mercado Pago de forma mais precisa.
     */
    private String determinarCategoriaPorDescricao(String descricao) {
        // Assinaturas e serviços recorrentes
        if (descricao.contains("assinatura") || descricao.contains("meli+") || 
            descricao.contains("netflix") || descricao.contains("spotify") || 
            descricao.contains("prime") || descricao.contains("disney") ||
            descricao.contains("youtube") || descricao.contains("premium")) {
            return "Assinaturas e Serviços";
        }
        
        // Alimentação
        if (descricao.contains("comida") || descricao.contains("alimento") || 
            descricao.contains("restaurante") || descricao.contains("lanchonete") ||
            descricao.contains("delivery") || descricao.contains("ifood") ||
            descricao.contains("uber eats") || descricao.contains("rappi") ||
            descricao.contains("padaria") || descricao.contains("supermercado") ||
            descricao.contains("mercado") || descricao.contains("açaí") ||
            descricao.contains("pizza") || descricao.contains("hamburguer")) {
            return "Alimentação";
        }
        
        // Transporte
        if (descricao.contains("uber") || descricao.contains("99") ||
            descricao.contains("taxi") || descricao.contains("transporte") ||
            descricao.contains("gasolina") || descricao.contains("posto") ||
            descricao.contains("estacionamento") || descricao.contains("pedágio") ||
            descricao.contains("metro") || descricao.contains("ônibus") ||
            descricao.contains("bike") || descricao.contains("scooter")) {
            return "Transporte";
        }
        
        // Roupas e calçados
        if (descricao.contains("tenis") || descricao.contains("tênis") ||
            descricao.contains("roupa") || descricao.contains("calçado") ||
            descricao.contains("sapato") || descricao.contains("bota") ||
            descricao.contains("camisa") || descricao.contains("calça") ||
            descricao.contains("vestido") || descricao.contains("blusa") ||
            descricao.contains("moda") || descricao.contains("fashion") ||
            descricao.contains("loja") || descricao.contains("shopping")) {
            return "Roupas e Calçados";
        }
        
        // Saúde e farmácia
        if (descricao.contains("farmacia") || descricao.contains("farmácia") ||
            descricao.contains("medicamento") || descricao.contains("remédio") ||
            descricao.contains("saude") || descricao.contains("saúde") ||
            descricao.contains("hospital") || descricao.contains("clínica") ||
            descricao.contains("dentista") || descricao.contains("médico") ||
            descricao.contains("laboratório") || descricao.contains("exame")) {
            return "Saúde e Farmácia";
        }
        
        // Tecnologia e eletrônicos
        if (descricao.contains("celular") || descricao.contains("smartphone") ||
            descricao.contains("notebook") || descricao.contains("computador") ||
            descricao.contains("tablet") || descricao.contains("fone") ||
            descricao.contains("cabo") || descricao.contains("carregador") ||
            descricao.contains("eletrônico") || descricao.contains("gadget") ||
            descricao.contains("apple") || descricao.contains("samsung") ||
            descricao.contains("xiaomi") || descricao.contains("motorola")) {
            return "Tecnologia e Eletrônicos";
        }
        
        // Lazer e entretenimento
        if (descricao.contains("cinema") || descricao.contains("teatro") ||
            descricao.contains("show") || descricao.contains("festival") ||
            descricao.contains("jogo") || descricao.contains("game") ||
            descricao.contains("livro") || descricao.contains("revista") ||
            descricao.contains("parque") || descricao.contains("zoo") ||
            descricao.contains("museu") || descricao.contains("exposição")) {
            return "Lazer e Entretenimento";
        }
        
        // Casa e decoração
        if (descricao.contains("casa") || descricao.contains("decoração") ||
            descricao.contains("móvel") || descricao.contains("sofá") ||
            descricao.contains("mesa") || descricao.contains("cadeira") ||
            descricao.contains("cama") || descricao.contains("armário") ||
            descricao.contains("eletrodoméstico") || descricao.contains("geladeira") ||
            descricao.contains("fogão") || descricao.contains("microondas")) {
            return "Casa e Decoração";
        }
        
        // Educação
        if (descricao.contains("curso") || descricao.contains("escola") ||
            descricao.contains("universidade") || descricao.contains("faculdade") ||
            descricao.contains("livro") || descricao.contains("material") ||
            descricao.contains("educação") || descricao.contains("aprendizado") ||
            descricao.contains("professor") || descricao.contains("aula")) {
            return "Educação";
        }
        
        // Viagem e turismo
        if (descricao.contains("viagem") || descricao.contains("hotel") ||
            descricao.contains("passagem") || descricao.contains("aéreo") ||
            descricao.contains("hospedagem") || descricao.contains("turismo") ||
            descricao.contains("praia") || descricao.contains("montanha") ||
            descricao.contains("cidade") || descricao.contains("país")) {
            return "Viagem e Turismo";
        }
        
        // Se não conseguir categorizar, usa a descrição como categoria
        return "Outros - " + descricao.substring(0, Math.min(20, descricao.length()));
    }
}
