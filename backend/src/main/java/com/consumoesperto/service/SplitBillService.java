package com.consumoesperto.service;

import com.consumoesperto.dto.BalancoGrupoDTO;
import com.consumoesperto.dto.DebitoInternoDTO;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.DebitoInterno;
import com.consumoesperto.model.GrupoFamiliar;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.DebitoInternoRepository;
import com.consumoesperto.util.ApelidoNormalizador;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Racha-contas inteligente (split bills) dentro do grupo familiar.
 *
 * <p>O pagador registra apenas a sua fatia como despesa real (abatendo o seu saldo) e o
 * restante vira {@link DebitoInterno} — um direito a receber dos demais membros marcados.
 * O sistema nunca debita a conta bancária dos devedores; gerencia só o livro do grupo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SplitBillService {

    private static final int SCALE = 2;

    private final GrupoFamiliarService grupoFamiliarService;
    private final DebitoInternoRepository debitoInternoRepository;
    private final TransacaoService transacaoService;

    /** Resultado da divisão para montagem da resposta do J.A.R.V.I.S. */
    public record ResultadoDivisao(
        BigDecimal valorTotal,
        BigDecimal fatiaPagador,
        List<DebitoInternoDTO> debitos,
        String descricao
    ) {}

    /**
     * Divide a despesa entre o pagador e os membros marcados.
     *
     * @param pagador       usuário que pagou a conta
     * @param valorTotal    valor total da despesa
     * @param aliasesMembros apelidos/nomes dos demais membros (ex.: "Esposa", "Filho")
     * @param descricao     descrição livre (ex.: "Restaurante")
     */
    @Transactional
    public ResultadoDivisao processarDivisao(Usuario pagador, BigDecimal valorTotal,
                                             List<String> aliasesMembros, String descricao) {
        if (pagador == null) {
            throw new IllegalArgumentException("Pagador não identificado.");
        }
        if (valorTotal == null || valorTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Informe um valor positivo para rachar.");
        }
        if (aliasesMembros == null || aliasesMembros.isEmpty()) {
            throw new IllegalArgumentException(
                "Você precisa marcar pelo menos um membro do grupo para dividir (ex.: \"racha 150 com a Esposa\").");
        }

        GrupoFamiliar grupo = grupoFamiliarService.grupoAceitoDoUsuario(pagador.getId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Você ainda não tem um grupo familiar ativo. Crie um em Família antes de rachar contas."));

        List<Usuario> membros = grupoFamiliarService.membrosAceitos(grupo.getId());

        List<Usuario> devedores = new ArrayList<>();
        for (String alias : aliasesMembros) {
            Usuario membro = resolverMembro(membros, alias, pagador.getId());
            if (membro == null) {
                throw new IllegalArgumentException(
                    "Não encontrei \"" + alias + "\" no seu grupo familiar. "
                        + "Confira o nome ou convide a pessoa em Família antes de rachar.");
            }
            if (devedores.stream().noneMatch(d -> d.getId().equals(membro.getId()))) {
                devedores.add(membro);
            }
        }

        BigDecimal total = valorTotal.setScale(SCALE, RoundingMode.HALF_UP);
        int participantes = devedores.size() + 1;
        BigDecimal fatia = total.divide(BigDecimal.valueOf(participantes), SCALE, RoundingMode.HALF_UP);

        // O pagador absorve a diferença de centavos para fechar a conta contábil sem quebra.
        BigDecimal somaDevedores = fatia.multiply(BigDecimal.valueOf(devedores.size())).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal fatiaPagador = total.subtract(somaDevedores).setScale(SCALE, RoundingMode.HALF_UP);

        String descricaoLimpa = (descricao != null && !descricao.isBlank())
            ? descricao.trim()
            : "Racha-contas";

        // Despesa real do pagador: apenas a sua fatia.
        TransacaoDTO despesa = new TransacaoDTO();
        despesa.setDescricao(descricaoLimpa + " (sua parte do racha)");
        despesa.setValor(fatiaPagador);
        despesa.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
        despesa.setDataTransacao(LocalDateTime.now());
        despesa.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
        transacaoService.criarTransacao(despesa, pagador.getId());

        List<DebitoInternoDTO> debitos = new ArrayList<>();
        for (Usuario devedor : devedores) {
            DebitoInterno debito = new DebitoInterno();
            debito.setGrupoFamiliar(grupo);
            debito.setCredor(pagador);
            debito.setDevedor(devedor);
            debito.setValor(fatia);
            debito.setDescricao(descricaoLimpa);
            debito.setLiquidado(false);
            debitos.add(toDto(debitoInternoRepository.save(debito)));
        }

        return new ResultadoDivisao(total, fatiaPagador, debitos, descricaoLimpa);
    }

    @Transactional(readOnly = true)
    public BalancoGrupoDTO balanco(Long usuarioId) {
        List<DebitoInternoDTO> aReceber = debitoInternoRepository.findAReceber(usuarioId).stream()
            .map(this::toDto).collect(Collectors.toList());
        List<DebitoInternoDTO> devidos = debitoInternoRepository.findDevidos(usuarioId).stream()
            .map(this::toDto).collect(Collectors.toList());

        BigDecimal totalReceber = somar(aReceber);
        BigDecimal totalDevido = somar(devidos);

        return BalancoGrupoDTO.builder()
            .aReceber(aReceber)
            .devidos(devidos)
            .totalAReceber(totalReceber)
            .totalDevido(totalDevido)
            .saldoLiquido(totalReceber.subtract(totalDevido).setScale(SCALE, RoundingMode.HALF_UP))
            .build();
    }

    /** Liquida um débito específico onde o usuário é o credor. */
    @Transactional
    public DebitoInternoDTO liquidarDebito(Long usuarioId, Long debitoId) {
        DebitoInterno debito = debitoInternoRepository.findByIdAndCredorId(debitoId, usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Débito não encontrado para o seu usuário."));
        if (debito.isLiquidado()) {
            return toDto(debito);
        }
        debito.setLiquidado(true);
        debito.setDataLiquidacao(LocalDateTime.now());
        return toDto(debitoInternoRepository.save(debito));
    }

    /**
     * Quita os débitos pendentes do credor para com um devedor identificado por apelido/nome
     * (comando "acertei os X com a @Fulano"). Liquida todos os débitos pendentes entre eles.
     *
     * @return total liquidado
     */
    @Transactional
    public BigDecimal quitarComDevedor(Usuario credor, String aliasDevedor) {
        if (credor == null) {
            throw new IllegalArgumentException("Credor não identificado.");
        }
        if (aliasDevedor == null || aliasDevedor.isBlank()) {
            throw new IllegalArgumentException("Informe com quem você acertou o débito.");
        }
        GrupoFamiliar grupo = grupoFamiliarService.grupoAceitoDoUsuario(credor.getId())
            .orElseThrow(() -> new IllegalArgumentException("Você não tem um grupo familiar ativo."));
        List<Usuario> membros = grupoFamiliarService.membrosAceitos(grupo.getId());
        Usuario devedor = resolverMembro(membros, aliasDevedor, credor.getId());
        if (devedor == null) {
            throw new IllegalArgumentException(
                "Não encontrei \"" + aliasDevedor + "\" no seu grupo para acertar o débito.");
        }
        List<DebitoInterno> pendentes = debitoInternoRepository.findPendentesEntre(credor.getId(), devedor.getId());
        if (pendentes.isEmpty()) {
            throw new IllegalArgumentException("Não há débitos pendentes de " + nomeCurto(devedor) + " com você.");
        }
        BigDecimal totalLiquidado = BigDecimal.ZERO;
        LocalDateTime agora = LocalDateTime.now();
        for (DebitoInterno d : pendentes) {
            d.setLiquidado(true);
            d.setDataLiquidacao(agora);
            debitoInternoRepository.save(d);
            totalLiquidado = totalLiquidado.add(d.getValor());
        }
        return totalLiquidado.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private Usuario resolverMembro(List<Usuario> membros, String alias, Long pagadorId) {
        String token = ApelidoNormalizador.normalizar(alias);
        if (token.length() < 2) {
            return null;
        }
        List<Usuario> candidatos = membros.stream()
            .filter(u -> u != null && u.getId() != null && !u.getId().equals(pagadorId))
            .collect(Collectors.toList());

        // Exato pelo primeiro nome ou nome completo.
        for (Usuario u : candidatos) {
            String nomeNorm = ApelidoNormalizador.normalizar(u.getNome());
            String primeiro = nomeNorm.contains(" ") ? nomeNorm.substring(0, nomeNorm.indexOf(' ')) : nomeNorm;
            if (nomeNorm.equals(token) || primeiro.equals(token)) {
                return u;
            }
        }
        // Parcial (contains).
        List<Usuario> parciais = candidatos.stream()
            .filter(u -> ApelidoNormalizador.normalizar(u.getNome()).contains(token))
            .collect(Collectors.toList());
        return parciais.size() == 1 ? parciais.get(0) : null;
    }

    private BigDecimal somar(List<DebitoInternoDTO> lista) {
        return lista.stream()
            .map(DebitoInternoDTO::getValor)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private String nomeCurto(Usuario u) {
        if (u == null || u.getNome() == null || u.getNome().isBlank()) {
            return "o membro";
        }
        String nome = u.getNome().trim();
        return nome.contains(" ") ? nome.substring(0, nome.indexOf(' ')) : nome;
    }

    private DebitoInternoDTO toDto(DebitoInterno d) {
        return DebitoInternoDTO.builder()
            .id(d.getId())
            .credorId(d.getCredor() != null ? d.getCredor().getId() : null)
            .credorNome(d.getCredor() != null ? d.getCredor().getNome() : null)
            .devedorId(d.getDevedor() != null ? d.getDevedor().getId() : null)
            .devedorNome(d.getDevedor() != null ? d.getDevedor().getNome() : null)
            .valor(d.getValor())
            .descricao(d.getDescricao())
            .liquidado(d.isLiquidado())
            .dataCriacao(d.getDataCriacao())
            .dataLiquidacao(d.getDataLiquidacao())
            .build();
    }
}
