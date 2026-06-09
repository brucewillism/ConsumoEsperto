package com.consumoesperto.service;

import com.consumoesperto.dto.TransferenciaContaDTO;
import com.consumoesperto.dto.TransferenciaContaRequest;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.TransferenciaConta;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.TransferenciaContaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferenciaContaService {

    private final TransferenciaContaRepository transferenciaContaRepository;
    private final ContaBancariaService contaBancariaService;
    private final SaldoMovimentacaoService saldoMovimentacaoService;
    private final SaldoService saldoService;

    @Transactional
    public TransferenciaContaDTO transferir(Long usuarioId, TransferenciaContaRequest request) {
        if (request.getContaOrigemId().equals(request.getContaDestinoId())) {
            throw new IllegalArgumentException("Conta de origem e destino devem ser diferentes.");
        }
        ContaBancaria origem = contaBancariaService.buscarEntidade(request.getContaOrigemId(), usuarioId);
        ContaBancaria destino = contaBancariaService.buscarEntidade(request.getContaDestinoId(), usuarioId);
        BigDecimal valor = request.getValor().setScale(2, RoundingMode.HALF_UP);
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor deve ser positivo.");
        }
        if (!origem.temSaldoSuficiente(valor)) {
            throw new IllegalArgumentException(
                "Saldo insuficiente na conta de origem. Disponível (incluindo cheque especial): R$ "
                    + origem.getSaldoDisponivel().setScale(2, RoundingMode.HALF_UP));
        }

        saldoMovimentacaoService.aplicarTransferenciaEntreContas(origem.getId(), destino.getId(), valor);

        TransferenciaConta tx = new TransferenciaConta();
        Usuario u = new Usuario();
        u.setId(usuarioId);
        tx.setUsuario(u);
        tx.setContaOrigem(origem);
        tx.setContaDestino(destino);
        tx.setValor(valor);
        tx.setDescricao(request.getDescricao() != null && !request.getDescricao().isBlank()
            ? request.getDescricao().trim()
            : "Transferência " + origem.getNome() + " → " + destino.getNome());
        tx.setDataTransferencia(request.getDataTransferencia() != null
            ? request.getDataTransferencia() : LocalDateTime.now());

        TransferenciaConta salva = transferenciaContaRepository.save(tx);
        BigDecimal patrimonioDepois = saldoService.patrimonioLiquido(usuarioId);

        return TransferenciaContaDTO.builder()
            .id(salva.getId())
            .contaOrigemId(origem.getId())
            .contaOrigemNome(origem.getNome())
            .contaDestinoId(destino.getId())
            .contaDestinoNome(destino.getNome())
            .valor(valor)
            .descricao(salva.getDescricao())
            .dataTransferencia(salva.getDataTransferencia())
            .patrimonioLiquidoApos(patrimonioDepois)
            .build();
    }

    @Transactional(readOnly = true)
    public List<TransferenciaContaDTO> listar(Long usuarioId) {
        return transferenciaContaRepository.findByUsuarioIdOrderByDataTransferenciaDesc(usuarioId).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    private TransferenciaContaDTO toDto(TransferenciaConta t) {
        return TransferenciaContaDTO.builder()
            .id(t.getId())
            .contaOrigemId(t.getContaOrigem() != null ? t.getContaOrigem().getId() : null)
            .contaOrigemNome(t.getContaOrigem() != null ? t.getContaOrigem().getNome() : null)
            .contaDestinoId(t.getContaDestino() != null ? t.getContaDestino().getId() : null)
            .contaDestinoNome(t.getContaDestino() != null ? t.getContaDestino().getNome() : null)
            .valor(t.getValor())
            .descricao(t.getDescricao())
            .dataTransferencia(t.getDataTransferencia())
            .build();
    }
}
