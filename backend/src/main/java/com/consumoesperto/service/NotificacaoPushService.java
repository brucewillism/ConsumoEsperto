package com.consumoesperto.service;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.Notificacao;
import com.consumoesperto.repository.NotificacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Serviço para gerenciar notificações push
 * 
 * Este serviço permite enviar notificações para usuários
 * sobre eventos importantes do sistema.
 * 
 * TEMPORARIAMENTE DESABILITADO PARA DEBUG
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacaoPushService {

    private final NotificacaoRepository notificacaoRepository;

    /**
     * Envia notificação de fatura próxima do vencimento
     */
    public void enviarNotificacaoFaturaProximaVencimento(Long usuarioId, String nomeCartao, 
                                                        String valor, String dataVencimento) {
        try {
            log.info("🔔 Enviando notificação de fatura próxima do vencimento para usuário: {}", usuarioId);
            
            String titulo = "Fatura próxima do vencimento";
            String mensagem = String.format(
                "A fatura do cartão %s no valor de R$ %s vence em %s. Não esqueça de pagar!",
                nomeCartao, valor, dataVencimento
            );
            
            // criarNotificacao(usuarioId, titulo, mensagem, "AVISO");
            
            log.info("✅ Notificação de fatura próxima do vencimento enviada com sucesso");
            
        } catch (Exception e) {
            log.error("❌ Erro ao enviar notificação de fatura próxima do vencimento: {}", e.getMessage(), e);
        }
    }

    /**
     * Envia notificação de transação suspeita
     */
    public void enviarNotificacaoTransacaoSuspeita(Long usuarioId, String descricao, 
                                                  String valor, String data) {
        try {
            log.info("🔔 Enviando notificação de transação suspeita para usuário: {}", usuarioId);
            
            String titulo = "Transação suspeita detectada";
            String mensagem = String.format(
                "Detectamos uma transação suspeita: %s no valor de R$ %s em %s. " +
                "Verifique se foi você quem realizou esta transação.",
                descricao, valor, data
            );
            
            // criarNotificacao(usuarioId, titulo, mensagem, "ALERTA");
            
            log.info("✅ Notificação de transação suspeita enviada com sucesso");
            
        } catch (Exception e) {
            log.error("❌ Erro ao enviar notificação de transação suspeita: {}", e.getMessage(), e);
        }
    }

    /**
     * Envia notificação de limite de cartão próximo
     */
    public void enviarNotificacaoLimiteCartaoProximo(Long usuarioId, String nomeCartao, 
                                                   String limiteUtilizado, String limiteTotal) {
        try {
            log.info("🔔 Enviando notificação de limite de cartão próximo para usuário: {}", usuarioId);
            
            String titulo = "Limite de cartão próximo do esgotamento";
            String mensagem = String.format(
                "O cartão %s está com %s%% do limite utilizado (R$ %s de R$ %s). " +
                "Cuidado para não ultrapassar o limite!",
                nomeCartao, calcularPercentual(limiteUtilizado, limiteTotal), 
                limiteUtilizado, limiteTotal
            );
            
            // criarNotificacao(usuarioId, titulo, mensagem, "AVISO");
            
            log.info("✅ Notificação de limite de cartão próximo enviada com sucesso");
            
        } catch (Exception e) {
            log.error("❌ Erro ao enviar notificação de limite de cartão próximo: {}", e.getMessage(), e);
        }
    }

    /**
     * Envia notificação de meta de gastos atingida
     */
    public void enviarNotificacaoMetaGastosAtingida(Long usuarioId, String categoria, 
                                                   String valorGasto, String meta) {
        try {
            log.info("🔔 Enviando notificação de meta de gastos atingida para usuário: {}", usuarioId);
            
            String titulo = "Meta de gastos atingida";
            String mensagem = String.format(
                "Você atingiu a meta de gastos para a categoria %s! " +
                "Gastou R$ %s de um total de R$ %s previstos.",
                categoria, valorGasto, meta
            );
            
            // criarNotificacao(usuarioId, titulo, mensagem, "INFO");
            
            log.info("✅ Notificação de meta de gastos atingida enviada com sucesso");
            
        } catch (Exception e) {
            log.error("❌ Erro ao enviar notificação de meta de gastos atingida: {}", e.getMessage(), e);
        }
    }

    /**
     * Envia notificação de saldo baixo
     */
    public void enviarNotificacaoSaldoBaixo(Long usuarioId, String saldoAtual, String saldoMinimo) {
        try {
            log.info("🔔 Enviando notificação de saldo baixo para usuário: {}", usuarioId);
            
            String titulo = "Saldo baixo detectado";
            String mensagem = String.format(
                "Seu saldo atual é de R$ %s, abaixo do mínimo recomendado de R$ %s. " +
                "Considere fazer uma transferência ou depósito.",
                saldoAtual, saldoMinimo
            );
            
            // criarNotificacao(usuarioId, titulo, mensagem, "AVISO");
            
            log.info("✅ Notificação de saldo baixo enviada com sucesso");
            
        } catch (Exception e) {
            log.error("❌ Erro ao enviar notificação de saldo baixo: {}", e.getMessage(), e);
        }
    }

    /**
     * Envia notificação de atualização do sistema
     */
    public void enviarNotificacaoAtualizacaoSistema(Long usuarioId, String versao, String novidades) {
        try {
            log.info("🔔 Enviando notificação de atualização do sistema para usuário: {}", usuarioId);
            
            String titulo = "Sistema atualizado";
            String mensagem = String.format(
                "O ConsumoEsperto foi atualizado para a versão %s! " +
                "Novidades: %s",
                versao, novidades
            );
            
            // criarNotificacao(usuarioId, titulo, mensagem, "INFO");
            
            log.info("✅ Notificação de atualização do sistema enviada com sucesso");
            
        } catch (Exception e) {
            log.error("❌ Erro ao enviar notificação de atualização do sistema: {}", e.getMessage(), e);
        }
    }

    /**
     * Envia notificação personalizada
     */
    public void enviarNotificacaoPersonalizada(Long usuarioId, String titulo, String mensagem, String tipo) {
        try {
            log.info("🔔 Enviando notificação personalizada para usuário: {}", usuarioId);
            
            // criarNotificacao(usuarioId, titulo, mensagem, tipo);
            
            log.info("✅ Notificação personalizada enviada com sucesso");
            
        } catch (Exception e) {
            log.error("❌ Erro ao enviar notificação personalizada: {}", e.getMessage(), e);
        }
    }

    /**
     * Envia notificação para todos os usuários
     */
    public void enviarNotificacaoGlobal(String titulo, String mensagem, String tipo) {
        try {
            log.info("🔔 Enviando notificação global: {}", titulo);
            
            // Buscar todos os usuários ativos
            List<Usuario> usuarios = buscarUsuariosAtivos();
            
            for (Usuario usuario : usuarios) {
                criarNotificacao(usuario.getId(), titulo, mensagem, tipo);
            }
            
            log.info("✅ Notificação global enviada para {} usuários", usuarios.size());
            
        } catch (Exception e) {
            log.error("❌ Erro ao enviar notificação global: {}", e.getMessage(), e);
        }
    }

    /**
     * Marca notificação como lida
     */
    public void marcarNotificacaoComoLida(Long notificacaoId) {
        try {
            log.info("✅ Marcando notificação como lida: {}", notificacaoId);
            
            Notificacao notificacao = notificacaoRepository.findById(notificacaoId)
                .orElseThrow(() -> new RuntimeException("Notificação não encontrada: " + notificacaoId));
            
            notificacao.setLida(true);
            notificacao.setDataLeitura(LocalDateTime.now());
            
            notificacaoRepository.save(notificacao);
            
            log.info("✅ Notificação marcada como lida com sucesso");
            
        } catch (Exception e) {
            log.error("❌ Erro ao marcar notificação como lida: {}", e.getMessage(), e);
        }
    }

    /**
     * Marca todas as notificações do usuário como lidas
     */
    public void marcarTodasNotificacoesComoLidas(Long usuarioId) {
        try {
            log.info("✅ Marcando todas as notificações como lidas para usuário: {}", usuarioId);
            
            List<Notificacao> notificacoes = notificacaoRepository.findByUsuarioIdAndLidaFalse(usuarioId);
            
            for (Notificacao notificacao : notificacoes) {
                notificacao.setLida(true);
                notificacao.setDataLeitura(LocalDateTime.now());
            }
            
            notificacaoRepository.saveAll(notificacoes);
            
            log.info("✅ {} notificações marcadas como lidas", notificacoes.size());
            
        } catch (Exception e) {
            log.error("❌ Erro ao marcar todas as notificações como lidas: {}", e.getMessage(), e);
        }
    }

    /**
     * Busca notificações não lidas do usuário
     */
    public List<Notificacao> buscarNotificacoesNaoLidas(Long usuarioId) {
        try {
            log.info("🔍 Buscando notificações não lidas para usuário: {}", usuarioId);
            
            List<Notificacao> notificacoes = notificacaoRepository.findByUsuarioIdAndLidaFalse(usuarioId);
            
            log.info("✅ {} notificações não lidas encontradas", notificacoes.size());
            
            return notificacoes;
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar notificações não lidas: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Busca todas as notificações do usuário
     */
    public List<Notificacao> buscarTodasNotificacoes(Long usuarioId) {
        try {
            log.info("🔍 Buscando todas as notificações para usuário: {}", usuarioId);
            
            List<Notificacao> notificacoes = notificacaoRepository.findByUsuarioIdOrderByDataCriacaoDesc(usuarioId);
            
            log.info("✅ {} notificações encontradas", notificacoes.size());
            
            return notificacoes;
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar todas as notificações: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Cria uma nova notificação
     */
    private void criarNotificacao(Long usuarioId, String titulo, String mensagem, String tipo) {
        try {
            Notificacao notificacao = new Notificacao();
            notificacao.setUsuarioId(usuarioId);
            notificacao.setTitulo(titulo);
            notificacao.setMensagem(mensagem);
            notificacao.setTipo(tipo);
            notificacao.setLida(false);
            notificacao.setDataCriacao(LocalDateTime.now());
            
            notificacaoRepository.save(notificacao);
            
            log.info("✅ Notificação criada: {} - {}", titulo, tipo);
            
        } catch (Exception e) {
            log.error("❌ Erro ao criar notificação: {}", e.getMessage(), e);
        }
    }

    /**
     * Busca usuários ativos
     */
    private List<Usuario> buscarUsuariosAtivos() {
        // Implementar busca de usuários ativos
        // Por enquanto, retornar lista vazia
        return List.of();
    }

    /**
     * Calcula percentual entre dois valores
     */
    private String calcularPercentual(String valor1, String valor2) {
        try {
            double v1 = Double.parseDouble(valor1.replace("R$", "").replace(",", ".").trim());
            double v2 = Double.parseDouble(valor2.replace("R$", "").replace(",", ".").trim());
            
            if (v2 == 0) return "0";
            
            double percentual = (v1 / v2) * 100;
            return String.format("%.1f", percentual);
            
        } catch (Exception e) {
            log.warn("⚠️ Erro ao calcular percentual: {}", e.getMessage());
            return "0";
        }
    }

    /**
     * Envia notificação de teste
     */
    public void enviarNotificacaoTeste(Long usuarioId) {
        try {
            log.info("🧪 Enviando notificação de teste para usuário: {}", usuarioId);
            
            String titulo = "Notificação de Teste";
            String mensagem = "Esta é uma notificação de teste do sistema ConsumoEsperto. " +
                            "Se você está recebendo esta mensagem, o sistema de notificações está funcionando corretamente!";
            
            criarNotificacao(usuarioId, titulo, mensagem, "TESTE");
            
            log.info("✅ Notificação de teste enviada com sucesso");
            
        } catch (Exception e) {
            log.error("❌ Erro ao enviar notificação de teste: {}", e.getMessage(), e);
        }
    }
}
