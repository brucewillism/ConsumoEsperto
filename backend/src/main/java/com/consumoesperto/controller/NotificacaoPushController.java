package com.consumoesperto.controller;

import com.consumoesperto.model.Notificacao;
import com.consumoesperto.service.NotificacaoPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.consumoesperto.security.UserPrincipal;

import java.util.List;
import java.util.Map;

/**
 * Controller para gerenciar notificações push
 * 
 * Este controller permite gerenciar notificações dos usuários
 * e enviar notificações personalizadas.
 */
@RestController
@RequestMapping("/api/notificacoes")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app", "*"})
public class NotificacaoPushController {

    private final NotificacaoPushService notificacaoPushService;

    /**
     * Busca notificações não lidas do usuário
     */
    @GetMapping("/nao-lidas")
    public ResponseEntity<List<Notificacao>> buscarNotificacoesNaoLidas(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            log.info("🔍 Buscando notificações não lidas para usuário: {}", currentUser.getId());
            
            List<Notificacao> notificacoes = notificacaoPushService.buscarNotificacoesNaoLidas(currentUser.getId());
            
            return ResponseEntity.ok(notificacoes);
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar notificações não lidas: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Busca todas as notificações do usuário
     */
    @GetMapping("/todas")
    public ResponseEntity<List<Notificacao>> buscarTodasNotificacoes(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            log.info("🔍 Buscando todas as notificações para usuário: {}", currentUser.getId());
            
            List<Notificacao> notificacoes = notificacaoPushService.buscarTodasNotificacoes(currentUser.getId());
            
            return ResponseEntity.ok(notificacoes);
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar todas as notificações: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Marca notificação como lida
     */
    @PutMapping("/{notificacaoId}/marcar-lida")
    public ResponseEntity<Map<String, Object>> marcarNotificacaoComoLida(
            @PathVariable Long notificacaoId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            log.info("✅ Marcando notificação como lida: {} para usuário: {}", notificacaoId, currentUser.getId());
            
            notificacaoPushService.marcarNotificacaoComoLida(notificacaoId);
            
            Map<String, Object> response = Map.of(
                "sucesso", true,
                "mensagem", "Notificação marcada como lida com sucesso",
                "notificacao_id", notificacaoId
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro ao marcar notificação como lida: {}", e.getMessage(), e);
            
            Map<String, Object> response = Map.of(
                "sucesso", false,
                "erro", "Erro ao marcar notificação como lida: " + e.getMessage()
            );
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Marca todas as notificações do usuário como lidas
     */
    @PutMapping("/marcar-todas-lidas")
    public ResponseEntity<Map<String, Object>> marcarTodasNotificacoesComoLidas(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            log.info("✅ Marcando todas as notificações como lidas para usuário: {}", currentUser.getId());
            
            notificacaoPushService.marcarTodasNotificacoesComoLidas(currentUser.getId());
            
            Map<String, Object> response = Map.of(
                "sucesso", true,
                "mensagem", "Todas as notificações foram marcadas como lidas"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro ao marcar todas as notificações como lidas: {}", e.getMessage(), e);
            
            Map<String, Object> response = Map.of(
                "sucesso", false,
                "erro", "Erro ao marcar todas as notificações como lidas: " + e.getMessage()
            );
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Envia notificação de teste
     */
    @PostMapping("/teste")
    public ResponseEntity<Map<String, Object>> enviarNotificacaoTeste(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            log.info("🧪 Enviando notificação de teste para usuário: {}", currentUser.getId());
            
            notificacaoPushService.enviarNotificacaoTeste(currentUser.getId());
            
            Map<String, Object> response = Map.of(
                "sucesso", true,
                "mensagem", "Notificação de teste enviada com sucesso"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro ao enviar notificação de teste: {}", e.getMessage(), e);
            
            Map<String, Object> response = Map.of(
                "sucesso", false,
                "erro", "Erro ao enviar notificação de teste: " + e.getMessage()
            );
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Envia notificação personalizada
     */
    @PostMapping("/personalizada")
    public ResponseEntity<Map<String, Object>> enviarNotificacaoPersonalizada(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            log.info("🔔 Enviando notificação personalizada para usuário: {}", currentUser.getId());
            
            String titulo = request.get("titulo");
            String mensagem = request.get("mensagem");
            String tipo = request.getOrDefault("tipo", "INFO");
            
            if (titulo == null || titulo.trim().isEmpty()) {
                Map<String, Object> response = Map.of(
                    "sucesso", false,
                    "erro", "Título da notificação é obrigatório"
                );
                return ResponseEntity.badRequest().body(response);
            }
            
            if (mensagem == null || mensagem.trim().isEmpty()) {
                Map<String, Object> response = Map.of(
                    "sucesso", false,
                    "erro", "Mensagem da notificação é obrigatória"
                );
                return ResponseEntity.badRequest().body(response);
            }
            
            notificacaoPushService.enviarNotificacaoPersonalizada(currentUser.getId(), titulo, mensagem, tipo);
            
            Map<String, Object> response = Map.of(
                "sucesso", true,
                "mensagem", "Notificação personalizada enviada com sucesso"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro ao enviar notificação personalizada: {}", e.getMessage(), e);
            
            Map<String, Object> response = Map.of(
                "sucesso", false,
                "erro", "Erro ao enviar notificação personalizada: " + e.getMessage()
            );
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Lista tipos de notificação disponíveis
     */
    @GetMapping("/tipos")
    public ResponseEntity<Map<String, Object>> listarTiposNotificacao() {
        try {
            log.info("📋 Listando tipos de notificação disponíveis");
            
            Map<String, Object> tipos = Map.of(
                "tipos", List.of(
                    Map.of(
                        "id", "INFO",
                        "nome", "Informação",
                        "descricao", "Notificações informativas gerais",
                        "cor", "#007bff",
                        "icone", "info-circle"
                    ),
                    Map.of(
                        "id", "AVISO",
                        "nome", "Aviso",
                        "descricao", "Notificações de aviso importantes",
                        "cor", "#ffc107",
                        "icone", "exclamation-triangle"
                    ),
                    Map.of(
                        "id", "ALERTA",
                        "nome", "Alerta",
                        "descricao", "Notificações de alerta crítico",
                        "cor", "#dc3545",
                        "icone", "exclamation-circle"
                    ),
                    Map.of(
                        "id", "SUCESSO",
                        "nome", "Sucesso",
                        "descricao", "Notificações de sucesso",
                        "cor", "#28a745",
                        "icone", "check-circle"
                    ),
                    Map.of(
                        "id", "TESTE",
                        "nome", "Teste",
                        "descricao", "Notificações de teste do sistema",
                        "cor", "#6c757d",
                        "icone", "cog"
                    )
                ),
                "instrucoes", Map.of(
                    "titulo", "Título da notificação (obrigatório)",
                    "mensagem", "Mensagem da notificação (obrigatório)",
                    "tipo", "Tipo da notificação (opcional, padrão: INFO)",
                    "observacao", "As notificações são salvas no banco de dados e podem ser marcadas como lidas"
                )
            );
            
            return ResponseEntity.ok(tipos);
            
        } catch (Exception e) {
            log.error("❌ Erro ao listar tipos de notificação: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Busca estatísticas de notificações do usuário
     */
    @GetMapping("/estatisticas")
    public ResponseEntity<Map<String, Object>> buscarEstatisticasNotificacoes(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            log.info("📊 Buscando estatísticas de notificações para usuário: {}", currentUser.getId());
            
            List<Notificacao> todasNotificacoes = notificacaoPushService.buscarTodasNotificacoes(currentUser.getId());
            List<Notificacao> notificacoesNaoLidas = notificacaoPushService.buscarNotificacoesNaoLidas(currentUser.getId());
            
            Map<String, Object> estatisticas = Map.of(
                "total_notificacoes", todasNotificacoes.size(),
                "notificacoes_nao_lidas", notificacoesNaoLidas.size(),
                "notificacoes_lidas", todasNotificacoes.size() - notificacoesNaoLidas.size(),
                "percentual_lidas", todasNotificacoes.isEmpty() ? 0 : 
                    ((todasNotificacoes.size() - notificacoesNaoLidas.size()) * 100) / todasNotificacoes.size(),
                "tipos_notificacao", Map.of(
                    "INFO", todasNotificacoes.stream().filter(n -> "INFO".equals(n.getTipo())).count(),
                    "AVISO", todasNotificacoes.stream().filter(n -> "AVISO".equals(n.getTipo())).count(),
                    "ALERTA", todasNotificacoes.stream().filter(n -> "ALERTA".equals(n.getTipo())).count(),
                    "SUCESSO", todasNotificacoes.stream().filter(n -> "SUCESSO".equals(n.getTipo())).count(),
                    "TESTE", todasNotificacoes.stream().filter(n -> "TESTE".equals(n.getTipo())).count()
                )
            );
            
            return ResponseEntity.ok(estatisticas);
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar estatísticas de notificações: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
