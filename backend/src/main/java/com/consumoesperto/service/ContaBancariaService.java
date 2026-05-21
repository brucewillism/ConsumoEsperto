package com.consumoesperto.service;

import com.consumoesperto.dto.ContaBancariaDTO;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.util.ApelidoNormalizador;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ContaBancariaService {

    private final ContaBancariaRepository contaBancariaRepository;
    private final UsuarioRepository usuarioRepository;

    public ContaBancariaDTO criar(ContaBancariaDTO dto) {
        Usuario usuario = usuarioRepository.findById(dto.getUsuarioId())
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        ContaBancaria conta = new ContaBancaria();
        conta.setNome(dto.getNome().trim());
        conta.setTipo(ContaBancaria.TipoConta.valueOf(dto.getTipo().name()));
        conta.setSaldoAtual(escala(dto.getSaldoAtual()));
        conta.setUsuario(usuario);
        conta.setAtiva(dto.isAtiva());
        conta.setPadrao(dto.isPadrao());

        if (conta.isPadrao()) {
            desmarcarOutrasPadrao(usuario.getId(), null);
        } else if (contaBancariaRepository.countByUsuarioIdAndAtivaTrue(usuario.getId()) == 0) {
            conta.setPadrao(true);
        }

        return converterParaDTO(contaBancariaRepository.save(conta));
    }

    @Transactional(readOnly = true)
    public List<ContaBancariaDTO> listarPorUsuario(Long usuarioId, boolean apenasAtivas) {
        List<ContaBancaria> contas = apenasAtivas
            ? contaBancariaRepository.findByUsuarioIdAndAtivaTrueOrderByPadraoDescNomeAsc(usuarioId)
            : contaBancariaRepository.findByUsuarioIdOrderByPadraoDescNomeAsc(usuarioId);
        return contas.stream().map(this::converterParaDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ContaBancariaDTO buscarPorId(Long id, Long usuarioId) {
        return converterParaDTO(buscarEntidade(id, usuarioId));
    }

    public ContaBancariaDTO atualizar(Long id, ContaBancariaDTO dto, Long usuarioId) {
        ContaBancaria conta = buscarEntidade(id, usuarioId);
        conta.setNome(dto.getNome().trim());
        conta.setTipo(ContaBancaria.TipoConta.valueOf(dto.getTipo().name()));
        conta.setAtiva(dto.isAtiva());

        if (dto.isPadrao() && !conta.isPadrao()) {
            desmarcarOutrasPadrao(usuarioId, id);
            conta.setPadrao(true);
        } else if (!dto.isPadrao() && conta.isPadrao()) {
            conta.setPadrao(false);
        }

        return converterParaDTO(contaBancariaRepository.save(conta));
    }

    public void inativar(Long id, Long usuarioId) {
        ContaBancaria conta = buscarEntidade(id, usuarioId);
        conta.setAtiva(false);
        conta.setPadrao(false);
        contaBancariaRepository.save(conta);
    }

    /** Resolve conta para lançamento: explícita no DTO, padrão ou única ativa. */
    @Transactional(readOnly = true)
    public ContaBancaria resolverContaParaTransacao(Long usuarioId, Long contaBancariaId) {
        if (contaBancariaId != null) {
            return buscarEntidade(contaBancariaId, usuarioId);
        }
        return contaBancariaRepository.findFirstByUsuarioIdAndPadraoTrueAndAtivaTrue(usuarioId)
            .or(() -> contaBancariaRepository.findFirstByUsuarioIdAndAtivaTrueOrderByIdAsc(usuarioId))
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public BigDecimal somarSaldosAtivos(Long usuarioId) {
        BigDecimal total = contaBancariaRepository.sumSaldoAtualByUsuarioIdAndAtivaTrue(usuarioId);
        return total != null ? total.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public boolean possuiContasAtivas(Long usuarioId) {
        return contaBancariaRepository.countByUsuarioIdAndAtivaTrue(usuarioId) > 0;
    }

    /**
     * Resolve contas ativas por apelido (nome), com normalização insensível a maiúsculas e acentos.
     */
    @Transactional(readOnly = true)
    public List<ContaBancaria> encontrarAtivasPorApelidoNormalizado(Long usuarioId, String apelido) {
        if (apelido == null || apelido.isBlank()) {
            return List.of();
        }
        String token = ApelidoNormalizador.normalizar(apelido);
        if (token.length() < 2) {
            return List.of();
        }
        List<ContaBancaria> todos = contaBancariaRepository.findByUsuarioIdAndAtivaTrueOrderByPadraoDescNomeAsc(usuarioId);
        List<ContaBancaria> exatos = todos.stream()
            .filter(c -> ApelidoNormalizador.normalizar(c.getNome()).equals(token))
            .collect(Collectors.toList());
        if (!exatos.isEmpty()) {
            return exatos;
        }
        return todos.stream()
            .filter(c -> ApelidoNormalizador.normalizar(c.getNome()).contains(token))
            .sorted(Comparator.comparing(ContaBancaria::isPadrao).reversed()
                .thenComparing(c -> c.getNome() != null ? c.getNome() : ""))
            .collect(Collectors.toList());
    }

    /** Ajuste manual de saldo via WhatsApp (reconciliação). */
    public ContaBancariaDTO ajustarSaldo(Long id, Long usuarioId, BigDecimal novoSaldo) {
        ContaBancaria conta = buscarEntidade(id, usuarioId);
        conta.setSaldoAtual(escala(novoSaldo));
        return converterParaDTO(contaBancariaRepository.save(conta));
    }

    private ContaBancaria buscarEntidade(Long id, Long usuarioId) {
        return contaBancariaRepository.findByIdAndUsuarioId(id, usuarioId)
            .orElseThrow(() -> new RuntimeException("Conta bancária não encontrada"));
    }

    private void desmarcarOutrasPadrao(Long usuarioId, Long excetoId) {
        for (ContaBancaria c : contaBancariaRepository.findByUsuarioIdAndAtivaTrueOrderByPadraoDescNomeAsc(usuarioId)) {
            if (excetoId == null || !excetoId.equals(c.getId())) {
                c.setPadrao(false);
                contaBancariaRepository.save(c);
            }
        }
    }

    private ContaBancariaDTO converterParaDTO(ContaBancaria conta) {
        ContaBancariaDTO dto = new ContaBancariaDTO();
        dto.setId(conta.getId());
        dto.setNome(conta.getNome());
        dto.setTipo(ContaBancariaDTO.TipoConta.valueOf(conta.getTipo().name()));
        dto.setSaldoAtual(conta.getSaldoAtual());
        dto.setUsuarioId(conta.getUsuario() != null ? conta.getUsuario().getId() : null);
        dto.setAtiva(conta.isAtiva());
        dto.setPadrao(conta.isPadrao());
        dto.setDataCriacao(conta.getDataCriacao());
        dto.setDataAtualizacao(conta.getDataAtualizacao());
        return dto;
    }

    private static BigDecimal escala(BigDecimal valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
