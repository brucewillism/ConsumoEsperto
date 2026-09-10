package com.consumoesperto.ingest.notificacao;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.service.CategoriaService;
import com.consumoesperto.service.ContaBancariaService;
import com.consumoesperto.service.TransacaoService;
import com.consumoesperto.service.UsuarioSessaoContextoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IngestNotificacaoWhatsappHandler {

    private static final Pattern APAGAR = Pattern.compile(
        "(?i)^\\s*(apagar|excluir|nao\\s+foi\\s+isso|não\\s+foi\\s+isso|nao\\s+era\\s+isso|não\\s+era\\s+isso)\\s*\\.?\\s*$");
    private static final Pattern CATEGORIA = Pattern.compile(
        "(?i)^\\s*(?:categoria|cat|muda(?:r)?\\s+categoria(?:\\s+para)?)\\s+(.+)$");
    private static final Pattern CONTA = Pattern.compile(
        "(?i)^\\s*conta\\s+(.+)$");
    private static final Pattern CARTAO = Pattern.compile(
        "(?i)^\\s*cart[aã]o\\s+(.+)$");

    private final UsuarioSessaoContextoService sessaoContextoService;
    private final TransacaoService transacaoService;
    private final CategoriaService categoriaService;
    private final ContaBancariaService contaBancariaService;
    private final CartaoCreditoRepository cartaoCreditoRepository;

    public Optional<String> tryHandle(Long userId, String text) {
        if (userId == null || text == null || text.isBlank()) {
            return Optional.empty();
        }
        Optional<Map<String, Object>> ctxOpt = sessaoContextoService.buscarAtiva(
            userId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_CAPTURA_NOTIFICACAO
        );
        if (ctxOpt.isEmpty()) {
            return Optional.empty();
        }
        Long transacaoId = asLong(ctxOpt.get().get("transacaoId"));
        if (transacaoId == null) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        if (APAGAR.matcher(trimmed).matches()) {
            transacaoService.deletarTransacao(transacaoId, userId);
            sessaoContextoService.remover(
                userId, UsuarioSessaoContextoService.CANAL_WHATSAPP,
                UsuarioSessaoContextoService.CHAVE_CAPTURA_NOTIFICACAO);
            return Optional.of("Pronto — apaguei o lançamento e estornei o saldo.");
        }
        Matcher cat = CATEGORIA.matcher(trimmed);
        if (cat.matches()) {
            return corrigirCategoria(userId, transacaoId, cat.group(1).trim());
        }
        Matcher conta = CONTA.matcher(trimmed);
        if (conta.matches()) {
            return mudarConta(userId, transacaoId, conta.group(1).trim());
        }
        Matcher cartao = CARTAO.matcher(trimmed);
        if (cartao.matches()) {
            return mudarCartao(userId, transacaoId, cartao.group(1).trim());
        }
        return Optional.empty();
    }

    private Optional<String> corrigirCategoria(Long userId, Long transacaoId, String nome) {
        List<Categoria> found = categoriaService.encontrarAtivasPorApelidoNormalizado(userId, nome);
        if (found.isEmpty()) {
            return Optional.of("Não encontrei a categoria «" + nome + "». Tente o nome exacto.");
        }
        if (found.size() > 1) {
            return Optional.of("Há mais de uma categoria parecida. Seja mais específico.");
        }
        TransacaoDTO dto = transacaoService.buscarPorId(transacaoId, userId);
        dto.setCategoriaId(found.get(0).getId());
        transacaoService.atualizarTransacao(transacaoId, dto, userId);
        sessaoContextoService.remover(
            userId, UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_CAPTURA_NOTIFICACAO);
        return Optional.of("Categoria actualizada para *" + found.get(0).getNome() + "*. Vou lembrar da próxima vez.");
    }

    private Optional<String> mudarConta(Long userId, Long transacaoId, String nome) {
        List<ContaBancaria> found = contaBancariaService.encontrarAtivasPorApelidoNormalizado(userId, nome);
        if (found.isEmpty()) {
            return Optional.of("Não encontrei a conta «" + nome + "».");
        }
        if (found.size() > 1) {
            return Optional.of("Há mais de uma conta parecida. Seja mais específico.");
        }
        TransacaoDTO dto = transacaoService.buscarPorId(transacaoId, userId);
        dto.setContaBancariaId(found.get(0).getId());
        dto.setCartaoCreditoId(null);
        dto.setFaturaId(null);
        transacaoService.atualizarTransacao(transacaoId, dto, userId);
        sessaoContextoService.remover(
            userId, UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_CAPTURA_NOTIFICACAO);
        return Optional.of("Mudei para a conta *" + found.get(0).getNome() + "*.");
    }

    private Optional<String> mudarCartao(Long userId, Long transacaoId, String nome) {
        List<CartaoCredito> found = cartaoCreditoRepository.findAtivosByUsuarioIdAndNomeOrBancoLike(userId, nome);
        if (found.isEmpty()) {
            return Optional.of("Não encontrei o cartão «" + nome + "».");
        }
        if (found.size() > 1) {
            return Optional.of("Há mais de um cartão parecido. Seja mais específico.");
        }
        TransacaoDTO dto = transacaoService.buscarPorId(transacaoId, userId);
        dto.setCartaoCreditoId(found.get(0).getId());
        dto.setContaBancariaId(null);
        transacaoService.atualizarTransacao(transacaoId, dto, userId);
        sessaoContextoService.remover(
            userId, UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_CAPTURA_NOTIFICACAO);
        return Optional.of("Mudei para o cartão *" + found.get(0).getNome() + "*.");
    }

    private static Long asLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
