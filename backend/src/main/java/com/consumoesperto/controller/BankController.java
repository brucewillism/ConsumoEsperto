package com.consumoesperto.controller;

import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.dto.CartaoCreditoDTO;
import com.consumoesperto.dto.FaturaDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.AutorizacaoBancariaService;
import com.consumoesperto.service.BankSynchronizationService;
import com.consumoesperto.service.CartaoCreditoService;
import com.consumoesperto.service.FaturaService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller responsável por operações bancárias gerais
 * 
 * Este controller expõe endpoints para:
 * - Gerenciar bancos conectados
 * - Conectar/desconectar bancos
 * - Sincronizar dados bancários
 * - Obter dados consolidados
 * - Gerenciar cartões e faturas
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/bank")
@RequiredArgsConstructor
@Slf4j
@Api(tags = "Operações Bancárias")
@CrossOrigin(origins = "*")
public class BankController {

    private final AutorizacaoBancariaService autorizacaoBancariaService;
    private final BankSynchronizationService bankSynchronizationService;
    private final CartaoCreditoService cartaoCreditoService;
    private final FaturaService faturaService;

    /**
     * Obtém bancos conectados do usuário
     */
    @GetMapping("/connected")
    @ApiOperation("Obter bancos conectados do usuário")
    public ResponseEntity<List<Map<String, Object>>> getConnectedBanks(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            List<AutorizacaoBancaria> autorizacoes = autorizacaoBancariaService
                .buscarAutorizacoesPorUsuario(currentUser.getId());
            
            List<Map<String, Object>> bancosConectados = autorizacoes.stream()
                .map(auth -> {
                    Map<String, Object> banco = new HashMap<>();
                    banco.put("id", auth.getId());
                    banco.put("bankName", auth.getTipoBanco().toString());
                    banco.put("status", "connected");
                    banco.put("lastSync", auth.getDataUltimaSincronizacao() != null ? 
                        auth.getDataUltimaSincronizacao().toString() : "Nunca");
                    banco.put("cardsCount", 0); // TODO: Implementar contagem real de cartões
                    return banco;
                })
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(bancosConectados);
            
        } catch (Exception e) {
            log.error("Erro ao buscar bancos conectados para usuário {}: {}", 
                    currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * Conecta um novo banco
     */
    @PostMapping("/connect")
    @ApiOperation("Conectar um novo banco")
    public ResponseEntity<Map<String, Object>> connectBank(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            String bankType = request.get("bankType");
            if (bankType == null || bankType.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Tipo de banco é obrigatório"
                ));
            }
            
            // Verifica se o banco já está conectado
            List<AutorizacaoBancaria> autorizacoesExistentes = autorizacaoBancariaService
                .buscarAutorizacoesPorUsuario(currentUser.getId());
            
            boolean jaConectado = autorizacoesExistentes.stream()
                .anyMatch(auth -> auth.getTipoBanco().toString().equalsIgnoreCase(bankType));
            
            if (jaConectado) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Banco já está conectado"
                ));
            }
            
            // Gera URL de autorização OAuth2 para o banco
            String authUrl = bankSynchronizationService.generateBankAuthUrl(bankType, currentUser.getId());
            
            if (authUrl != null) {
                return ResponseEntity.ok(Map.of(
                    "status", "AUTORIZACAO_REQUERIDA",
                    "mensagem", "Redirecione o usuário para a URL de autorização",
                    "authUrl", authUrl,
                    "banco", bankType,
                    "usuarioId", currentUser.getId()
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Tipo de banco não suportado"
                ));
            }
            
        } catch (Exception e) {
            log.error("Erro ao conectar banco para usuário {}: {}", 
                    currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha ao conectar banco"
            ));
        }
    }

    /**
     * Desconecta um banco
     */
    @DeleteMapping("/disconnect/{bankId}")
    @ApiOperation("Desconectar um banco")
    public ResponseEntity<Map<String, Object>> disconnectBank(
            @PathVariable Long bankId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca a autorização bancária
            Optional<AutorizacaoBancaria> autorizacao = autorizacaoBancariaService
                .buscarPorId(bankId);
            
            if (autorizacao.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            AutorizacaoBancaria auth = autorizacao.get();
            
            // Verifica se pertence ao usuário
            if (!auth.getUsuario().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(403).body(Map.of("erro", "Acesso negado"));
            }
            
            // Remove a autorização bancária
            autorizacaoBancariaService.removerAutorizacao(bankId);
            
            // Remove cartões e faturas associados a este banco
            cartaoCreditoService.removerPorBanco(currentUser.getId(), auth.getTipoBanco().toString());
            faturaService.removerPorBanco(currentUser.getId(), auth.getTipoBanco().toString());
            
            return ResponseEntity.ok(Map.of(
                "status", "SUCESSO",
                "mensagem", "Banco desconectado com sucesso",
                "banco", auth.getTipoBanco().toString(),
                "usuarioId", currentUser.getId()
            ));
            
        } catch (Exception e) {
            log.error("Erro ao desconectar banco {} para usuário {}: {}", 
                    bankId, currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha ao desconectar banco"
            ));
        }
    }

    /**
     * Sincroniza dados de um banco específico
     */
    @PostMapping("/sync/{bankId}")
    @ApiOperation("Sincronizar dados de um banco específico")
    public ResponseEntity<Map<String, Object>> syncBankData(
            @PathVariable Long bankId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca a autorização bancária
            Optional<AutorizacaoBancaria> autorizacao = autorizacaoBancariaService
                .buscarPorId(bankId);
            
            if (autorizacao.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            AutorizacaoBancaria auth = autorizacao.get();
            
            // Verifica se pertence ao usuário
            if (!auth.getUsuario().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(403).body(Map.of("erro", "Acesso negado"));
            }
            
            // Verifica se o token não expirou
            if (auth.isTokenExpirado()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Token expirado. Renove a autorização do banco."
                ));
            }
            
            // Executa a sincronização real
            Map<String, Object> resultado = bankSynchronizationService
                .synchronizeBankData(auth);
            
            if (resultado != null && resultado.containsKey("sucesso")) {
                return ResponseEntity.ok(Map.of(
                    "status", "SUCESSO",
                    "mensagem", "Dados sincronizados com sucesso",
                    "banco", auth.getTipoBanco().toString(),
                    "resultado", resultado
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Falha na sincronização",
                    "detalhes", resultado
                ));
            }
            
        } catch (Exception e) {
            log.error("Erro ao sincronizar banco {} para usuário {}: {}", 
                    bankId, currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha na sincronização"
            ));
        }
    }

    /**
     * Sincroniza dados de todos os bancos conectados
     */
    @PostMapping("/sync/all")
    @ApiOperation("Sincronizar dados de todos os bancos conectados")
    public ResponseEntity<Map<String, Object>> syncAllBanks(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca todos os bancos conectados do usuário
            List<AutorizacaoBancaria> autorizacoes = autorizacaoBancariaService
                .buscarAutorizacoesPorUsuario(currentUser.getId());
            
            if (autorizacoes.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Nenhum banco conectado para sincronizar"
                ));
            }
            
            Map<String, Object> resultadoGeral = new HashMap<>();
            List<Map<String, Object>> resultadosIndividuais = new ArrayList<>();
            int sucessos = 0;
            int falhas = 0;
            
            // Sincroniza cada banco individualmente
            for (AutorizacaoBancaria auth : autorizacoes) {
                try {
                    if (auth.isTokenExpirado()) {
                        resultadosIndividuais.add(Map.of(
                            "banco", auth.getTipoBanco().toString(),
                            "status", "TOKEN_EXPIRADO",
                            "mensagem", "Token expirado, renove a autorização"
                        ));
                        falhas++;
                        continue;
                    }
                    
                    // Executa sincronização
                    Map<String, Object> resultado = bankSynchronizationService
                        .synchronizeBankData(auth);
                    
                    if (resultado != null && resultado.containsKey("sucesso")) {
                        resultadosIndividuais.add(Map.of(
                            "banco", auth.getTipoBanco().toString(),
                            "status", "SUCESSO",
                            "resultado", resultado
                        ));
                        sucessos++;
                    } else {
                        resultadosIndividuais.add(Map.of(
                            "banco", auth.getTipoBanco().toString(),
                            "status", "FALHA",
                            "resultado", resultado
                        ));
                        falhas++;
                    }
                    
                } catch (Exception e) {
                    log.error("Erro ao sincronizar banco {}: {}", 
                            auth.getTipoBanco(), e.getMessage());
                    resultadosIndividuais.add(Map.of(
                        "banco", auth.getTipoBanco().toString(),
                        "status", "ERRO",
                        "mensagem", e.getMessage()
                    ));
                    falhas++;
                }
            }
            
            resultadoGeral.put("totalBancos", autorizacoes.size());
            resultadoGeral.put("sucessos", sucessos);
            resultadoGeral.put("falhas", falhas);
            resultadoGeral.put("resultados", resultadosIndividuais);
            resultadoGeral.put("status", falhas == 0 ? "SUCESSO_TOTAL" : 
                (sucessos > 0 ? "SUCESSO_PARCIAL" : "FALHA_TOTAL"));
            
            return ResponseEntity.ok(resultadoGeral);
            
        } catch (Exception e) {
            log.error("Erro ao sincronizar todos os bancos para usuário {}: {}", 
                    currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha na sincronização geral"
            ));
        }
    }

    /**
     * Obtém cartões de crédito de todos os bancos conectados
     */
    @GetMapping("/credit-cards")
    @ApiOperation("Obter cartões de crédito de todos os bancos")
    public ResponseEntity<List<CartaoCreditoDTO>> getCreditCards(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca bancos conectados do usuário
            List<AutorizacaoBancaria> autorizacoes = autorizacaoBancariaService
                .buscarAutorizacoesPorUsuario(currentUser.getId());
            
            List<CartaoCreditoDTO> cartoesReais = new ArrayList<>();
            
            // Para cada banco conectado, busca cartões reais
            for (AutorizacaoBancaria auth : autorizacoes) {
                if (!auth.isTokenExpirado()) {
                    try {
                        // Busca cartões reais do banco específico
                        List<Map<String, Object>> cartoesBanco = bankSynchronizationService
                            .getCreditCardsFromBank(auth);
                        
                        // Converte Map para CartaoCreditoDTO
                        for (Map<String, Object> cartaoMap : cartoesBanco) {
                            CartaoCreditoDTO cartaoDTO = new CartaoCreditoDTO();
                            cartaoDTO.setId((Long) cartaoMap.get("id"));
                            cartaoDTO.setNumeroCartao((String) cartaoMap.get("numero"));
                            cartaoDTO.setLimiteCredito((BigDecimal) cartaoMap.get("limite"));
                            cartaoDTO.setLimiteDisponivel((BigDecimal) cartaoMap.get("saldoDisponivel"));
                            cartaoDTO.setBanco(auth.getTipoBanco().toString());
                            cartaoDTO.setUsuarioId(currentUser.getId());
                            cartaoDTO.setAtivo(true);
                            cartoesReais.add(cartaoDTO);
                        }
                        
                    } catch (Exception e) {
                        log.warn("Erro ao buscar cartões do banco {}: {}", 
                                auth.getTipoBanco(), e.getMessage());
                        // Continua com outros bancos
                    }
                }
            }
            
            // Se não há cartões reais, retorna os locais como fallback
            if (cartoesReais.isEmpty()) {
                log.info("Nenhum cartão real encontrado, retornando dados locais para usuário {}", 
                        currentUser.getId());
                cartoesReais = cartaoCreditoService.buscarPorUsuario(currentUser.getId());
            }
            
            return ResponseEntity.ok(cartoesReais);
            
        } catch (Exception e) {
            log.error("Erro ao buscar cartões para usuário {}: {}", 
                    currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * Obtém faturas de todos os cartões
     */
    @GetMapping("/invoices")
    @ApiOperation("Obter faturas de todos os cartões")
    public ResponseEntity<List<FaturaDTO>> getInvoices(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca bancos conectados do usuário
            List<AutorizacaoBancaria> autorizacoes = autorizacaoBancariaService
                .buscarAutorizacoesPorUsuario(currentUser.getId());
            
            List<FaturaDTO> faturasReais = new ArrayList<>();
            
            // Para cada banco conectado, busca faturas reais
            for (AutorizacaoBancaria auth : autorizacoes) {
                if (!auth.isTokenExpirado()) {
                    try {
                        // Busca faturas reais do banco específico
                        List<Map<String, Object>> faturasBanco = bankSynchronizationService
                            .getInvoicesFromBank(auth);
                        
                        // Converte Map para FaturaDTO
                        for (Map<String, Object> faturaMap : faturasBanco) {
                            FaturaDTO faturaDTO = new FaturaDTO();
                            faturaDTO.setId((Long) faturaMap.get("id"));
                            faturaDTO.setValorFatura((BigDecimal) faturaMap.get("valorTotal"));
                            faturaDTO.setValorPago((BigDecimal) faturaMap.get("valorMinimo"));
                            faturaDTO.setStatusFatura(Fatura.StatusFatura.valueOf((String) faturaMap.get("status")));
                            faturaDTO.setCartaoCreditoId(1L); // ID temporário, será ajustado quando implementar a lógica real
                            faturasReais.add(faturaDTO);
                        }
                        
                    } catch (Exception e) {
                        log.warn("Erro ao buscar faturas do banco {}: {}", 
                                auth.getTipoBanco(), e.getMessage());
                        // Continua com outros bancos
                    }
                }
            }
            
            // Se não há faturas reais, retorna as locais como fallback
            if (faturasReais.isEmpty()) {
                log.info("Nenhuma fatura real encontrada, retornando dados locais para usuário {}", 
                        currentUser.getId());
                faturasReais = faturaService.buscarPorUsuario(currentUser.getId());
            }
            
            return ResponseEntity.ok(faturasReais);
            
        } catch (Exception e) {
            log.error("Erro ao buscar faturas para usuário {}: {}", 
                    currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * Obtém saldo consolidado de todos os bancos
     */
    @GetMapping("/balance/consolidated")
    @ApiOperation("Obter saldo consolidado de todos os bancos")
    public ResponseEntity<Map<String, Object>> getConsolidatedBalance(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca bancos conectados do usuário
            List<AutorizacaoBancaria> autorizacoes = autorizacaoBancariaService
                .buscarAutorizacoesPorUsuario(currentUser.getId());
            
            Map<String, Object> saldoConsolidado = new HashMap<>();
            double saldoTotal = 0.0;
            double limiteTotal = 0.0;
            double limiteDisponivel = 0.0;
            Map<String, Object> bancosDetalhes = new HashMap<>();
            
            // Para cada banco conectado, busca dados reais
            for (AutorizacaoBancaria auth : autorizacoes) {
                if (!auth.isTokenExpirado()) {
                    try {
                        // Busca dados reais do banco específico
                        Map<String, Object> dadosBanco = bankSynchronizationService
                            .getBankBalanceData(auth);
                        
                        if (dadosBanco != null) {
                            String nomeBanco = auth.getTipoBanco().toString();
                            double saldoBanco = (Double) dadosBanco.getOrDefault("saldo", 0.0);
                            double limiteBanco = (Double) dadosBanco.getOrDefault("limite", 0.0);
                            double limiteDisponivelBanco = (Double) dadosBanco.getOrDefault("limiteDisponivel", 0.0);
                            
                            saldoTotal += saldoBanco;
                            limiteTotal += limiteBanco;
                            limiteDisponivel += limiteDisponivelBanco;
                            
                            bancosDetalhes.put(nomeBanco, Map.of(
                                "saldo", saldoBanco,
                                "limite", limiteBanco,
                                "limiteDisponivel", limiteDisponivelBanco
                            ));
                        }
                        
                    } catch (Exception e) {
                        log.warn("Erro ao buscar dados do banco {}: {}", 
                                auth.getTipoBanco(), e.getMessage());
                        // Continua com outros bancos
                    }
                }
            }
            
            // Calcula percentual utilizado
            double percentualUtilizado = limiteTotal > 0 ? 
                ((limiteTotal - limiteDisponivel) / limiteTotal) * 100 : 0.0;
            
            saldoConsolidado.put("saldoTotal", saldoTotal);
            saldoConsolidado.put("limiteTotal", limiteTotal);
            saldoConsolidado.put("limiteDisponivel", limiteDisponivel);
            saldoConsolidado.put("percentualUtilizado", Math.round(percentualUtilizado * 100.0) / 100.0);
            saldoConsolidado.put("bancos", bancosDetalhes);
            
            return ResponseEntity.ok(saldoConsolidado);
            
        } catch (Exception e) {
            log.error("Erro ao buscar saldo consolidado para usuário {}: {}", 
                    currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * Obtém estatísticas de sincronização
     */
    @GetMapping("/sync/stats")
    @ApiOperation("Obter estatísticas de sincronização")
    public ResponseEntity<Map<String, Object>> getSyncStats(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            List<AutorizacaoBancaria> autorizacoes = autorizacaoBancariaService
                .buscarAutorizacoesPorUsuario(currentUser.getId());
            
            Map<String, Object> stats = Map.of(
                "totalBancos", autorizacoes.size(),
                "bancosAtivos", autorizacoes.stream()
                    .filter(auth -> !auth.isTokenExpirado())
                    .count(),
                "ultimaSincronizacao", autorizacoes.stream()
                    .mapToLong(auth -> auth.getDataUltimaSincronizacao() != null ? 
                        auth.getDataUltimaSincronizacao().toEpochSecond(java.time.ZoneOffset.UTC) : 0)
                    .max()
                    .orElse(0),
                "statusGeral", autorizacoes.stream()
                    .anyMatch(AutorizacaoBancaria::isTokenExpirado) ? "ATENCAO" : "OK"
            );
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Erro ao buscar estatísticas para usuário {}: {}", 
                    currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * Obtém URL de autorização para conectar banco
     */
    @GetMapping("/auth/url/{bankType}")
    @ApiOperation("Obter URL de autorização para conectar banco")
    public ResponseEntity<Map<String, Object>> getBankAuthUrl(
            @PathVariable String bankType,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // TODO: Implementar geração de URL de autorização real
            // Por enquanto, retorna URL simulada
            String authUrl = String.format("https://auth.%s.com/oauth/authorize?client_id=test&redirect_uri=callback", 
                    bankType.toLowerCase());
            
            return ResponseEntity.ok(Map.of(
                "authUrl", authUrl,
                "bankType", bankType,
                "state", java.util.UUID.randomUUID().toString()
            ));
            
        } catch (Exception e) {
            log.error("Erro ao gerar URL de autorização para banco {}: {}", 
                    bankType, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha ao gerar URL de autorização"
            ));
        }
    }

    /**
     * Processa callback OAuth2
     */
    @PostMapping("/oauth/callback")
    @ApiOperation("Processar callback OAuth2")
    public ResponseEntity<Map<String, Object>> processOAuthCallback(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            String code = request.get("code");
            String state = request.get("state");
            String bankType = request.get("bankType");
            
            if (code == null || state == null || bankType == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Parâmetros obrigatórios: code, state, bankType"
                ));
            }
            
            // Verifica se o state corresponde ao usuário
            if (!state.equals("user_" + currentUser.getId())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "State inválido"
                ));
            }
            
            // Processa o callback OAuth2 real
            Map<String, Object> resultado = bankSynchronizationService
                .processOAuthCallback(code, bankType, currentUser.getId());
            
            if (resultado != null && resultado.containsKey("sucesso")) {
                return ResponseEntity.ok(Map.of(
                    "status", "SUCESSO",
                    "mensagem", "Banco conectado com sucesso",
                    "banco", bankType,
                    "resultado", resultado
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Falha na conexão OAuth2",
                    "detalhes", resultado
                ));
            }
            
        } catch (Exception e) {
            log.error("Erro ao processar callback OAuth2 para usuário {}: {}", 
                    currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha no processamento do callback"
            ));
        }
    }

    /**
     * Verifica status de conexão com banco
     */
    @GetMapping("/connection/status/{bankId}")
    @ApiOperation("Verificar status de conexão com banco")
    public ResponseEntity<Map<String, Object>> checkBankConnectionStatus(
            @PathVariable Long bankId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca a autorização bancária
            Optional<AutorizacaoBancaria> autorizacao = autorizacaoBancariaService
                .buscarPorId(bankId);
            
            if (autorizacao.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            AutorizacaoBancaria auth = autorizacao.get();
            
            // Verifica se pertence ao usuário
            if (!auth.getUsuario().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(403).body(Map.of("erro", "Acesso negado"));
            }
            
            // Verifica status real da conexão
            Map<String, Object> status = bankSynchronizationService
                .checkBankConnectionStatus(auth);
            
            if (status != null) {
                status.put("bancoId", bankId);
                status.put("banco", auth.getTipoBanco().toString());
                status.put("usuarioId", currentUser.getId());
                return ResponseEntity.ok(status);
            } else {
                return ResponseEntity.ok(Map.of(
                    "bancoId", bankId,
                    "banco", auth.getTipoBanco().toString(),
                    "status", "INDEFINIDO",
                    "mensagem", "Não foi possível verificar o status"
                ));
            }
            
        } catch (Exception e) {
            log.error("Erro ao verificar status de conexão do banco {} para usuário {}: {}", 
                    bankId, currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha na verificação de status"
            ));
        }
    }

    /**
     * Obtém histórico de sincronizações
     */
    @GetMapping("/sync/history")
    @ApiOperation("Obter histórico de sincronizações")
    public ResponseEntity<List<Map<String, Object>>> getSyncHistory(
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // TODO: Implementar busca de histórico real
            // Por enquanto, retorna histórico simulado
            List<Map<String, Object>> history = java.util.List.of(
                Map.of(
                    "id", 1,
                    "timestamp", java.time.LocalDateTime.now().minusHours(1),
                    "status", "SUCESSO",
                    "bancosProcessados", 3,
                    "detalhes", "Sincronização automática concluída"
                ),
                Map.of(
                    "id", 2,
                    "timestamp", java.time.LocalDateTime.now().minusHours(2),
                    "status", "SUCESSO",
                    "bancosProcessados", 2,
                    "detalhes", "Sincronização manual do Itaú"
                )
            );
            
            return ResponseEntity.ok(history);
            
        } catch (Exception e) {
            log.error("Erro ao buscar histórico de sincronização para usuário {}: {}", 
                    currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * Força renovação de token
     */
    @PostMapping("/token/refresh/{bankId}")
    @ApiOperation("Forçar renovação de token")
    public ResponseEntity<Map<String, Object>> forceTokenRefresh(
            @PathVariable Long bankId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca a autorização bancária
            Optional<AutorizacaoBancaria> autorizacao = autorizacaoBancariaService
                .buscarPorId(bankId);
            
            if (autorizacao.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            AutorizacaoBancaria auth = autorizacao.get();
            
            // Verifica se pertence ao usuário
            if (!auth.getUsuario().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(403).body(Map.of("erro", "Acesso negado"));
            }
            
            // Executa renovação real do token
            Map<String, Object> resultado = bankSynchronizationService
                .refreshBankToken(auth);
            
            if (resultado != null && resultado.containsKey("sucesso")) {
                return ResponseEntity.ok(Map.of(
                    "status", "SUCESSO",
                    "mensagem", "Token renovado com sucesso",
                    "banco", auth.getTipoBanco().toString(),
                    "resultado", resultado
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Falha na renovação do token",
                    "detalhes", resultado
                ));
            }
            
        } catch (Exception e) {
            log.error("Erro ao renovar token do banco {} para usuário {}: {}", 
                    bankId, currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha na renovação do token"
            ));
        }
    }

    /**
     * Obtém detalhes de um banco específico
     */
    @GetMapping("/details/{bankId}")
    @ApiOperation("Obter detalhes de um banco específico")
    public ResponseEntity<Map<String, Object>> getBankDetails(
            @PathVariable Long bankId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca a autorização bancária
            Optional<AutorizacaoBancaria> autorizacao = autorizacaoBancariaService
                .buscarPorId(bankId);
            
            if (autorizacao.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            AutorizacaoBancaria auth = autorizacao.get();
            
            // Verifica se pertence ao usuário
            if (!auth.getUsuario().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(403).body(Map.of("erro", "Acesso negado"));
            }
            
            // Busca dados reais do banco
            Map<String, Object> detalhesBanco = bankSynchronizationService
                .getBankDetails(auth);
            
            if (detalhesBanco != null) {
                detalhesBanco.put("bancoId", bankId);
                detalhesBanco.put("banco", auth.getTipoBanco().toString());
                detalhesBanco.put("usuarioId", currentUser.getId());
                detalhesBanco.put("dataConexao", auth.getDataCriacao());
                detalhesBanco.put("ultimaSincronizacao", auth.getDataUltimaSincronizacao());
                detalhesBanco.put("tokenExpirado", auth.isTokenExpirado());
                
                return ResponseEntity.ok(detalhesBanco);
            } else {
                return ResponseEntity.ok(Map.of(
                    "bancoId", bankId,
                    "banco", auth.getTipoBanco().toString(),
                    "status", "DADOS_INDISPONIVEIS",
                    "mensagem", "Não foi possível obter detalhes do banco"
                ));
            }
            
        } catch (Exception e) {
            log.error("Erro ao buscar detalhes do banco {} para usuário {}: {}", 
                    bankId, currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha ao buscar detalhes do banco"
            ));
        }
    }

    /**
     * Obtém transações de um banco específico
     */
    @GetMapping("/transactions/{bankId}")
    @ApiOperation("Obter transações de um banco específico")
    public ResponseEntity<List<Map<String, Object>>> getBankTransactions(
            @PathVariable Long bankId,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca a autorização bancária
            Optional<AutorizacaoBancaria> autorizacao = autorizacaoBancariaService
                .buscarPorId(bankId);
            
            if (autorizacao.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            AutorizacaoBancaria auth = autorizacao.get();
            
            // Verifica se pertence ao usuário
            if (!auth.getUsuario().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(403).body((List<Map<String, Object>>) Map.of("erro", "Acesso negado"));
            }
            
            // Verifica se o token não expirou
            if (auth.isTokenExpirado()) {
                List<Map<String, Object>> erroList = List.of(Map.of(
                    "erro", "Token expirado. Renove a autorização do banco."
                ));
                ResponseEntity<List<Map<String, Object>>> response = ResponseEntity.badRequest().body(erroList);
                return response;
            }
            
            // Busca transações reais do banco
            List<Map<String, Object>> transacoes = bankSynchronizationService
                .getBankTransactions(auth, limit);
            
            if (transacoes != null) {
                return ResponseEntity.ok(transacoes);
            } else {
                List<Map<String, Object>> mensagemList = List.of(Map.of(
                    "mensagem", "Nenhuma transação encontrada"
                ));
                ResponseEntity<List<Map<String, Object>>> response = ResponseEntity.ok(mensagemList);
                return response;
            }
            
        } catch (Exception e) {
            log.error("Erro ao buscar transações do banco {} para usuário {}: {}", 
                    bankId, currentUser.getId(), e.getMessage());
            List<Map<String, Object>> erroList = List.of(Map.of(
                "erro", "Falha ao buscar transações"
            ));
            ResponseEntity<List<Map<String, Object>>> response = ResponseEntity.internalServerError().body(erroList);
            return response;
        }
    }

    /**
     * Obtém gastos por categoria
     */
    @GetMapping("/spending/category")
    @ApiOperation("Obter gastos por categoria")
    public ResponseEntity<List<Map<String, Object>>> getSpendingByCategory(
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca bancos conectados do usuário
            List<AutorizacaoBancaria> autorizacoes = autorizacaoBancariaService
                .buscarAutorizacoesPorUsuario(currentUser.getId());
            
            if (autorizacoes.isEmpty()) {
                return ResponseEntity.ok(List.of(Map.of(
                    "mensagem", "Nenhum banco conectado"
                )));
            }
            
            Map<String, Double> gastosPorCategoria = new HashMap<>();
            
            // Para cada banco conectado, busca gastos reais
            for (AutorizacaoBancaria auth : autorizacoes) {
                if (!auth.isTokenExpirado()) {
                    try {
                        // Busca gastos reais do banco específico
                        Map<String, Double> gastosBanco = bankSynchronizationService
                            .getSpendingByCategory(auth, days);
                        
                        if (gastosBanco != null) {
                            // Consolida gastos por categoria
                            gastosBanco.forEach((categoria, valor) -> {
                                gastosPorCategoria.merge(categoria, valor, Double::sum);
                            });
                        }
                        
                    } catch (Exception e) {
                        log.warn("Erro ao buscar gastos do banco {}: {}", 
                                auth.getTipoBanco(), e.getMessage());
                        // Continua com outros bancos
                    }
                }
            }
            
            // Converte para lista de resultados
            List<Map<String, Object>> resultado = gastosPorCategoria.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("categoria", entry.getKey());
                    map.put("valor", entry.getValue());
                    map.put("percentual", 0.0); // Será calculado no frontend
                    return map;
                })
                .sorted((a, b) -> Double.compare((Double) b.get("valor"), (Double) a.get("valor")))
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(resultado);
            
        } catch (Exception e) {
            log.error("Erro ao buscar gastos por categoria para usuário {}: {}", 
                    currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(List.of(Map.of(
                "erro", "Falha ao buscar gastos por categoria"
            )));
        }
    }

    /**
     * Obtém análise de gastos
     */
    @GetMapping("/spending/analysis")
    @ApiOperation("Obter análise de gastos")
    public ResponseEntity<Map<String, Object>> getSpendingAnalysis(
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            // Busca bancos conectados do usuário
            List<AutorizacaoBancaria> autorizacoes = autorizacaoBancariaService
                .buscarAutorizacoesPorUsuario(currentUser.getId());
            
            if (autorizacoes.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "mensagem", "Nenhum banco conectado"
                ));
            }
            
            double gastosTotal = 0.0;
            double receitasTotal = 0.0;
            double saldoPeriodo = 0.0;
            int totalTransacoes = 0;
            Map<String, Double> gastosPorDia = new HashMap<>();
            
            // Para cada banco conectado, busca dados reais
            for (AutorizacaoBancaria auth : autorizacoes) {
                if (!auth.isTokenExpirado()) {
                    try {
                        // Busca análise real do banco específico
                        Map<String, Object> analiseBanco = bankSynchronizationService
                            .getSpendingAnalysis(auth, days);
                        
                        if (analiseBanco != null) {
                            gastosTotal += (Double) analiseBanco.getOrDefault("gastos", 0.0);
                            receitasTotal += (Double) analiseBanco.getOrDefault("receitas", 0.0);
                            totalTransacoes += (Integer) analiseBanco.getOrDefault("totalTransacoes", 0);
                            
                            // Consolida gastos por dia
                            @SuppressWarnings("unchecked")
                            Map<String, Double> gastosDiaBanco = (Map<String, Double>) analiseBanco.getOrDefault("gastosPorDia", new HashMap<>());
                            gastosDiaBanco.forEach((dia, valor) -> {
                                gastosPorDia.merge(dia, valor, Double::sum);
                            });
                        }
                        
                    } catch (Exception e) {
                        log.warn("Erro ao buscar análise do banco {}: {}", 
                                auth.getTipoBanco(), e.getMessage());
                        // Continua com outros bancos
                    }
                }
            }
            
            saldoPeriodo = receitasTotal - gastosTotal;
            
            Map<String, Object> analise = new HashMap<>();
            analise.put("periodoDias", days);
            analise.put("gastosTotal", gastosTotal);
            analise.put("receitasTotal", receitasTotal);
            analise.put("saldoPeriodo", saldoPeriodo);
            analise.put("totalTransacoes", totalTransacoes);
            analise.put("gastosPorDia", gastosPorDia);
            analise.put("mediaGastosDiarios", days > 0 ? gastosTotal / days : 0.0);
            analise.put("tendencia", gastosTotal > 0 ? "GASTOS" : "RECEITAS");
            
            return ResponseEntity.ok(analise);
            
        } catch (Exception e) {
            log.error("Erro ao buscar análise de gastos para usuário {}: {}", 
                    currentUser.getId(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha ao buscar análise de gastos"
            ));
        }
    }
}
