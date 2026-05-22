package com.consumoesperto.service;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.RendaConfig;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.RendaConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Lança receita confirmada de salário quando a opção automática está activa
 * e o dia de pagamento do mês já passou (catch-up se o backend não estava no ar no dia exacto).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SalarioAutomaticoService {

    private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

    private final RendaConfigRepository rendaConfigRepository;
    private final TransacaoService transacaoService;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * @return {@code true} se criou a receita confirmada nesta invocação
     */
    @Transactional
    public boolean tentarLancarSalarioMesAtual(RendaConfig cfg) {
        if (cfg == null || !cfg.isReceitaAutomaticaAtiva()) {
            return false;
        }
        LocalDate hoje = LocalDate.now(ZONA_BR);
        int ym = hoje.getYear() * 100 + hoje.getMonthValue();
        Integer diaPagamento = cfg.getDiaPagamento();
        if (diaPagamento == null || diaPagamento < 1 || diaPagamento > 31) {
            return false;
        }
        if (diaPagamento > hoje.getDayOfMonth()) {
            return false;
        }
        if (cfg.getSalarioLiquido() == null || cfg.getSalarioLiquido().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        Integer ultimo = cfg.getUltimoMesLancamentoAuto();
        if (ultimo != null && ultimo == ym) {
            return false;
        }
        Usuario u = cfg.getUsuario();
        if (u == null) {
            return false;
        }
        Long uid = u.getId();
        int diaEfetivo = Math.min(diaPagamento, hoje.lengthOfMonth());
        LocalDateTime dataLancamento = hoje.withDayOfMonth(diaEfetivo).atStartOfDay();

        TransacaoDTO dto = new TransacaoDTO();
        dto.setDescricao("Salário líquido (automático)");
        dto.setValor(cfg.getSalarioLiquido());
        dto.setTipoTransacao(TransacaoDTO.TipoTransacao.RECEITA);
        dto.setCategoriaId(resolveCategoriaSalario(uid));
        dto.setDataTransacao(dataLancamento);
        dto.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
        transacaoService.criarTransacao(dto, uid);

        cfg.setUltimoMesLancamentoAuto(ym);
        rendaConfigRepository.save(cfg);
        log.info("Salário automático lançado userId={} valor={} ym={} data={}", uid, cfg.getSalarioLiquido(), ym, dataLancamento);
        return true;
    }

    @Transactional
    public boolean tentarLancarSalarioMesAtual(Long usuarioId) {
        return rendaConfigRepository.findByUsuarioId(usuarioId)
            .map(this::tentarLancarSalarioMesAtual)
            .orElse(false);
    }

    private Long resolveCategoriaSalario(Long usuarioId) {
        Categoria existente = categoriaRepository.findByUsuarioIdAndNome(usuarioId, "Salário");
        if (existente != null) {
            return existente.getId();
        }
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        Categoria c = new Categoria();
        c.setUsuario(usuario);
        c.setNome("Salário");
        c.setDescricao("Receitas de salário/contracheque");
        c.setCor("#10b981");
        c.setIcone("money-bill-wave");
        return categoriaRepository.save(c).getId();
    }
}
