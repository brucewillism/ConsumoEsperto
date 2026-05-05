package com.consumoesperto.service.entityupdate;

import com.consumoesperto.dto.CartaoCreditoDTO;
import com.consumoesperto.dto.CategoriaDTO;
import com.consumoesperto.dto.MetaFinanceiraDTO;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.MetaFinanceira;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.service.CartaoCreditoService;
import com.consumoesperto.service.CategoriaService;
import com.consumoesperto.service.MetaFinanceiraService;
import com.consumoesperto.service.TransacaoService;
import com.consumoesperto.util.ApelidoNormalizador;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orquestra {@code UPDATE_ENTITY_CONFIG}: resolve cadastro por apelido (com prioridade em modo AUTO)
 * e aplica apenas campos permitidos por entidade, sempre com trava de {@code usuarioId}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppEntityConfigUpdateService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private final CategoriaService categoriaService;
    private final MetaFinanceiraService metaFinanceiraService;
    private final CartaoCreditoService cartaoCreditoService;
    private final TransacaoService transacaoService;

    public String executar(Long usuarioId, JsonNode cmd) {
        String identifier = firstNonBlank(
            cmd.path("identifier").asText(""),
            cmd.path("cardName").asText(""),
            cmd.path("nome").asText("")
        );
        if (identifier.isBlank()) {
            return "Informe o nome ou apelido do cadastro que quer alterar.";
        }
        JsonNode updates = cmd.path("updates");
        if (updates.isMissingNode() || updates.isNull() || !updates.isObject() || updates.size() == 0) {
            return "Diz quais campos alterar (objeto updates). Ex.: altera a meta Viagem com valorObjetivo 40000.";
        }

        UpdateTargetEntity hint = UpdateTargetEntity.fromAi(cmd.path("targetEntity").asText(""));
        ResolveOutcome out = resolver(usuarioId, identifier, hint);
        if (out.error != null) {
            return out.error;
        }
        EntityMatch match = out.match;
        log.info("[ENTITY-UPDATE] Usuário {} alterando {} id={} identificador='{}'", usuarioId, match.target(), match.id(), identifier);

        try {
            return switch (match.target()) {
                case CATEGORIA -> aplicarCategoria(usuarioId, match, updates);
                case META -> aplicarMeta(usuarioId, match, updates);
                case DESPESA_FIXA -> aplicarDespesaFixa(usuarioId, match, updates);
                case CARTAO, CONTA -> aplicarCartao(usuarioId, match, updates);
                default -> "Tipo de cadastro não suportado.";
            };
        } catch (RuntimeException ex) {
            log.warn("[ENTITY-UPDATE] Falha: {}", ex.getMessage());
            return "Não consegui aplicar: " + ex.getMessage();
        }
    }

    private ResolveOutcome resolver(Long usuarioId, String identifier, UpdateTargetEntity hint) {
        if (hint == UpdateTargetEntity.CATEGORIA) {
            return resolveCategoria(usuarioId, identifier);
        }
        if (hint == UpdateTargetEntity.META) {
            return resolveMeta(usuarioId, identifier);
        }
        if (hint == UpdateTargetEntity.DESPESA_FIXA) {
            return resolveDespesaFixa(usuarioId, identifier);
        }
        if (hint.isCartaoOuConta()) {
            return resolveCartao(usuarioId, identifier);
        }
        // AUTO: categoria → meta → despesa fixa → cartão
        ResolveOutcome c = resolveCategoria(usuarioId, identifier);
        if (c.error != null) {
            return c;
        }
        if (c.match != null) {
            return c;
        }
        ResolveOutcome m = resolveMeta(usuarioId, identifier);
        if (m.error != null) {
            return m;
        }
        if (m.match != null) {
            return m;
        }
        ResolveOutcome d = resolveDespesaFixa(usuarioId, identifier);
        if (d.error != null) {
            return d;
        }
        if (d.match != null) {
            return d;
        }
        return resolveCartao(usuarioId, identifier);
    }

    private ResolveOutcome resolveCategoria(Long usuarioId, String identifier) {
        List<Categoria> found = categoriaService.encontrarAtivasPorApelidoNormalizado(usuarioId, identifier);
        if (found.isEmpty()) {
            return ResolveOutcome.notFound();
        }
        if (found.size() > 1) {
            return ResolveOutcome.err("Encontrei várias categorias parecidas com \"" + identifier + "\". Especifique o nome ou diga que é categoria.");
        }
        return ResolveOutcome.ok(new EntityMatch(UpdateTargetEntity.CATEGORIA, found.get(0).getId(), found.get(0).getNome()));
    }

    private ResolveOutcome resolveMeta(Long usuarioId, String identifier) {
        List<MetaFinanceira> found = metaFinanceiraService.encontrarPorDescricaoNormalizada(usuarioId, identifier);
        if (found.isEmpty()) {
            return ResolveOutcome.notFound();
        }
        if (found.size() > 1) {
            return ResolveOutcome.err("Encontrei várias metas parecidas com \"" + identifier + "\". Refine o nome ou diga que é meta.");
        }
        return ResolveOutcome.ok(new EntityMatch(UpdateTargetEntity.META, found.get(0).getId(), found.get(0).getDescricao()));
    }

    private ResolveOutcome resolveDespesaFixa(Long usuarioId, String identifier) {
        List<Transacao> all = transacaoService.listarDespesasRecorrentes(usuarioId);
        List<Transacao> found = ApelidoNormalizador.filtrarPorNomeNormalizado(all, Transacao::getDescricao, identifier);
        if (found.isEmpty()) {
            return ResolveOutcome.notFound();
        }
        if (found.size() > 1) {
            return ResolveOutcome.err("Encontrei várias despesas fixas parecidas com \"" + identifier + "\".");
        }
        return ResolveOutcome.ok(new EntityMatch(UpdateTargetEntity.DESPESA_FIXA, found.get(0).getId(), found.get(0).getDescricao()));
    }

    private ResolveOutcome resolveCartao(Long usuarioId, String identifier) {
        List<CartaoCredito> found = cartaoCreditoService.encontrarAtivosPorApelidoNormalizado(usuarioId, identifier);
        if (found.isEmpty()) {
            return ResolveOutcome.err("Ops, não encontrei nenhum cartão com o apelido " + identifier
                + ". Quer que eu liste seus cartões cadastrados?");
        }
        if (found.size() > 1) {
            return ResolveOutcome.err("Encontrei mais de um cartão parecido com \"" + identifier + "\". Diga o apelido exato.");
        }
        return ResolveOutcome.ok(new EntityMatch(UpdateTargetEntity.CARTAO, found.get(0).getId(), found.get(0).getNome()));
    }

    private String aplicarCategoria(Long usuarioId, EntityMatch match, JsonNode updates) {
        CategoriaDTO dto = categoriaService.listarPorUsuario(usuarioId).stream()
            .filter(d -> d.getId().equals(match.id()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
        List<String> ignorados = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = updates.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = ApelidoNormalizador.normalizar(e.getKey()).replace(' ', '_');
            JsonNode v = e.getValue();
            switch (key) {
                case "nome", "apelido" -> {
                    if (v != null && !v.isNull() && !v.asText().isBlank()) {
                        dto.setNome(v.asText().trim());
                    }
                }
                case "descricao" -> {
                    if (v != null && !v.isNull()) {
                        dto.setDescricao(v.asText().trim());
                    }
                }
                case "cor" -> {
                    if (v != null && !v.isNull() && !v.asText().isBlank()) {
                        dto.setCor(v.asText().trim());
                    }
                }
                case "icone" -> {
                    if (v != null && !v.isNull() && !v.asText().isBlank()) {
                        dto.setIcone(v.asText().trim());
                    }
                }
                case "limite_mensal", "limitemensal", "limite" -> ignorados.add(e.getKey() + " (categorias não têm limite mensal neste sistema)");
                default -> ignorados.add(e.getKey());
            }
        }
        CategoriaDTO salvo = categoriaService.atualizar(usuarioId, match.id(), dto);
        String base = "✅ Alterado! Categoria \"" + salvo.getNome() + "\" atualizada.";
        if (!ignorados.isEmpty()) {
            base += " Ignorados: " + String.join(", ", ignorados) + ".";
        }
        return base;
    }

    private String aplicarMeta(Long usuarioId, EntityMatch match, JsonNode updates) {
        MetaFinanceiraDTO salvo = metaFinanceiraService.aplicarPatchWhatsApp(usuarioId, match.id(), updates);
        return "✅ Feito! Meta \"" + salvo.getDescricao() + "\" — valor alvo "
            + BRL.format(salvo.getValorTotal().doubleValue()) + ".";
    }

    private String aplicarDespesaFixa(Long usuarioId, EntityMatch match, JsonNode updates) {
        String novaDesc = null;
        BigDecimal novoValor = null;
        List<String> ignorados = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = updates.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = ApelidoNormalizador.normalizar(e.getKey()).replace(' ', '_');
            JsonNode v = e.getValue();
            switch (key) {
                case "nome", "apelido", "descricao" -> {
                    if (v != null && !v.isNull() && !v.asText().isBlank()) {
                        novaDesc = v.asText().trim();
                    }
                }
                case "valor", "amount" -> {
                    BigDecimal nv = readBd(v);
                    if (nv != null && nv.compareTo(BigDecimal.ZERO) > 0) {
                        novoValor = nv;
                    }
                }
                default -> ignorados.add(e.getKey());
            }
        }
        Transacao salva = transacaoService.aplicarPatchDespesaRecorrente(usuarioId, match.id(), novaDesc, novoValor);
        String base = "✅ Feito! Despesa fixa \"" + salva.getDescricao() + "\" atualizada"
            + (novoValor != null ? " — valor " + BRL.format(novoValor.doubleValue()) : "") + ".";
        if (!ignorados.isEmpty()) {
            base += " Ignorados: " + String.join(", ", ignorados) + ".";
        }
        return base;
    }

    private String aplicarCartao(Long usuarioId, EntityMatch match, JsonNode updates) {
        BigDecimal limite = null;
        BigDecimal disp = null;
        String nome = null;
        String banco = null;
        String cor = null;
        String icone = null;
        List<String> ignorados = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = updates.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = ApelidoNormalizador.normalizar(e.getKey()).replace(' ', '_');
            JsonNode v = e.getValue();
            switch (key) {
                case "apelido", "nome" -> {
                    if (v != null && !v.isNull() && !v.asText().isBlank()) {
                        nome = v.asText().trim();
                    }
                }
                case "banco" -> {
                    if (v != null && !v.isNull() && !v.asText().isBlank()) {
                        banco = v.asText().trim();
                    }
                }
                case "limite", "limite_credito", "limitecredito", "newlimit" -> {
                    BigDecimal nv = readBd(v);
                    if (nv != null && nv.compareTo(BigDecimal.ZERO) > 0) {
                        limite = nv;
                    }
                }
                case "limite_disponivel", "limitedisponivel", "newavailablelimit" -> {
                    BigDecimal nv = readBd(v);
                    if (nv != null && nv.compareTo(BigDecimal.ZERO) >= 0) {
                        disp = nv;
                    }
                }
                case "cor" -> {
                    if (v != null && !v.isNull() && !v.asText().isBlank()) {
                        cor = v.asText().trim();
                    }
                }
                case "icone" -> {
                    if (v != null && !v.isNull() && !v.asText().isBlank()) {
                        icone = v.asText().trim();
                    }
                }
                case "saldo_inicial", "saldoinicial", "saldo" -> ignorados.add(e.getKey() + " (campo não existe em cartão)");
                default -> ignorados.add(e.getKey());
            }
        }
        if (limite == null && disp == null && nome == null && banco == null && cor == null && icone == null) {
            throw new RuntimeException("Nenhum campo válido para cartão. Use apelido, banco, limite, limiteDisponivel, cor ou icone.");
        }
        CartaoCreditoDTO u = cartaoCreditoService.atualizarConfigPorCartaoId(usuarioId, match.id(), limite, disp, nome, banco, cor, icone);
        List<String> partes = new ArrayList<>();
        if (limite != null && u.getLimiteCredito() != null) {
            partes.add("limite total " + BRL.format(u.getLimiteCredito().doubleValue()));
        }
        if (disp != null && u.getLimiteDisponivel() != null) {
            partes.add("limite disponível " + BRL.format(u.getLimiteDisponivel().doubleValue()));
        }
        if (nome != null) {
            partes.add("apelido \"" + u.getNome() + "\"");
        }
        if (banco != null) {
            partes.add("banco \"" + u.getBanco() + "\"");
        }
        if (cor != null) {
            partes.add("cor atualizada");
        }
        if (icone != null) {
            partes.add("ícone atualizado");
        }
        String base = "✅ Feito! Cartão " + u.getNome() + ": " + String.join("; ", partes) + ".";
        if (!ignorados.isEmpty()) {
            base += " Ignorados: " + String.join(", ", ignorados) + ".";
        }
        return base;
    }

    private static BigDecimal readBd(JsonNode v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return BigDecimal.valueOf(v.asDouble());
        }
        try {
            return new BigDecimal(v.asText().replace(',', '.').trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... parts) {
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                return p.trim();
            }
        }
        return "";
    }

    private static final class ResolveOutcome {
        final EntityMatch match;
        final String error;

        private ResolveOutcome(EntityMatch match, String error) {
            this.match = match;
            this.error = error;
        }

        static ResolveOutcome ok(EntityMatch m) {
            return new ResolveOutcome(m, null);
        }

        static ResolveOutcome notFound() {
            return new ResolveOutcome(null, null);
        }

        static ResolveOutcome err(String msg) {
            return new ResolveOutcome(null, msg);
        }
    }
}
