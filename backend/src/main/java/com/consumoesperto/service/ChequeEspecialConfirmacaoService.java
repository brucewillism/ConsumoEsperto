package com.consumoesperto.service;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.ContaBancaria;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Confirmação WhatsApp quando débito em conta usará cheque especial (saldo nominal insuficiente,
 * mas saldo + limite cobrem a operação).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChequeEspecialConfirmacaoService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final int SESSAO_TTL_MIN = 30;

    private final UsuarioSessaoContextoService sessaoContextoService;
    private final ContaBancariaService contaBancariaService;
    private final ObjectMapper objectMapper;

    public boolean precisaConfirmacao(ContaBancaria conta, BigDecimal valor) {
        if (conta == null || valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal saldo = conta.getSaldoAtual() != null ? conta.getSaldoAtual() : BigDecimal.ZERO;
        if (saldo.compareTo(valor) >= 0) {
            return false;
        }
        return conta.getLimiteChequeEspecial().compareTo(BigDecimal.ZERO) > 0
            && conta.temSaldoSuficiente(valor);
    }

    @Transactional
    public void salvarProposta(Long usuarioId, TransacaoDTO dto, Long contaId, BigDecimal valor) {
        ContaBancaria conta = contaBancariaService.buscarEntidade(contaId, usuarioId);
        Map<String, Object> ctx = new HashMap<>();
        try {
            ctx.put("transacaoJson", objectMapper.writeValueAsString(dto));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível guardar a proposta de débito.");
        }
        ctx.put("contaBancariaId", contaId);
        ctx.put("contaBancariaNome", conta.getNome());
        ctx.put("valor", valor.setScale(2, RoundingMode.HALF_UP));
        ctx.put("saldoAtual", conta.getSaldoAtual().setScale(2, RoundingMode.HALF_UP));
        ctx.put("novoSaldo", conta.getSaldoAtual().subtract(valor).setScale(2, RoundingMode.HALF_UP));
        sessaoContextoService.salvar(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_CHEQUE_ESPECIAL_CONFIRMACAO,
            ctx,
            SESSAO_TTL_MIN
        );
    }

    public boolean temPropostaPendente(Long usuarioId) {
        return sessaoContextoService.buscarAtiva(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_CHEQUE_ESPECIAL_CONFIRMACAO
        ).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<TransacaoDTO> transacaoPendente(Long usuarioId) {
        return sessaoContextoService.buscarAtiva(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_CHEQUE_ESPECIAL_CONFIRMACAO
        ).flatMap(ctx -> {
            try {
                String json = String.valueOf(ctx.getOrDefault("transacaoJson", ""));
                if (json.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(objectMapper.readValue(json, TransacaoDTO.class));
            } catch (Exception e) {
                log.warn("JSON transação cheque especial inválido userId={}: {}", usuarioId, e.getMessage());
                return Optional.empty();
            }
        });
    }

    public String mensagemProposta(Map<String, Object> ctx) {
        String conta = String.valueOf(ctx.getOrDefault("contaBancariaNome", "conta"));
        BigDecimal saldo = toBigDecimal(ctx.get("saldoAtual"));
        BigDecimal valor = toBigDecimal(ctx.get("valor"));
        BigDecimal novoSaldo = toBigDecimal(ctx.get("novoSaldo"));
        return "Chefe, o seu saldo na conta *" + conta + "* é de *" + BRL.format(saldo)
            + "*, mas a transação é de *" + BRL.format(valor)
            + "*. Vai entrar no seu *cheque especial*, deixando seu saldo em *" + BRL.format(novoSaldo)
            + "*. Como você tem limite disponível, posso seguir e autorizar o pagamento nos livros? "
            + "Responda *sim* ou *não*.";
    }

    public String mensagemPropostaAtiva(Long usuarioId) {
        return sessaoContextoService.buscarAtiva(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_CHEQUE_ESPECIAL_CONFIRMACAO
        ).map(this::mensagemProposta).orElse("");
    }

    @Transactional
    public void cancelarProposta(Long usuarioId) {
        sessaoContextoService.remover(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_CHEQUE_ESPECIAL_CONFIRMACAO
        );
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }
}
