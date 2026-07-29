package com.consumoesperto.service;

import com.consumoesperto.dto.ExportacaoTransacaoFiltro;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TransacaoExportacaoQueryService {

    private final EntityManager entityManager;
    private final ContaBancariaRepository contaBancariaRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;
    private final CategoriaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public List<Transacao> buscarParaExportacao(Long usuarioId, ExportacaoTransacaoFiltro filtro) {
        validarFiltro(usuarioId, filtro);

        LocalDate inicio = filtro.getDataInicio() != null ? filtro.getDataInicio() : LocalDate.now().minusMonths(1);
        LocalDate fim = filtro.getDataFim() != null ? filtro.getDataFim() : LocalDate.now();
        LocalDateTime dtInicio = inicio.atStartOfDay();
        LocalDateTime dtFim = fim.atTime(23, 59, 59);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Transacao> cq = cb.createQuery(Transacao.class);
        Root<Transacao> root = cq.from(Transacao.class);
        root.fetch("categoria", JoinType.LEFT);
        root.fetch("contaBancaria", JoinType.LEFT);
        Join<Transacao, Fatura> faturaJoin = null;
        if (filtro.getCartaoId() != null) {
            faturaJoin = root.join("fatura", JoinType.LEFT);
            faturaJoin.join("cartaoCredito", JoinType.LEFT);
        }

        List<Predicate> preds = new ArrayList<>();
        preds.add(cb.equal(root.get("usuario").get("id"), usuarioId));
        preds.add(cb.isFalse(root.get("excluido")));
        preds.add(cb.between(
            cb.coalesce(root.get("dataTransacao"), root.get("dataCriacao")),
            dtInicio,
            dtFim
        ));

        if (filtro.getTipoTransacao() != null) {
            preds.add(cb.equal(root.get("tipoTransacao"), filtro.getTipoTransacao()));
        }
        if (filtro.getStatusConferencia() != null) {
            preds.add(cb.equal(root.get("statusConferencia"), filtro.getStatusConferencia()));
        }
        if (filtro.getContaId() != null) {
            preds.add(cb.equal(root.get("contaBancaria").get("id"), filtro.getContaId()));
        }
        if (filtro.getCategoriaId() != null) {
            preds.add(cb.equal(root.get("categoria").get("id"), filtro.getCategoriaId()));
        }
        if (filtro.getCartaoId() != null && faturaJoin != null) {
            preds.add(cb.equal(faturaJoin.get("cartaoCredito").get("id"), filtro.getCartaoId()));
        }
        if (filtro.getDescricaoContem() != null && !filtro.getDescricaoContem().isBlank()) {
            String like = "%" + filtro.getDescricaoContem().trim().toLowerCase(Locale.ROOT) + "%";
            preds.add(cb.like(cb.lower(root.get("descricao")), like));
        }

        cq.select(root).where(preds.toArray(Predicate[]::new));
        cq.orderBy(cb.desc(root.get("dataTransacao")), cb.desc(root.get("id")));

        TypedQuery<Transacao> query = entityManager.createQuery(cq);
        return query.getResultList();
    }

    private void validarFiltro(Long usuarioId, ExportacaoTransacaoFiltro filtro) {
        if (filtro == null) {
            throw new IllegalArgumentException("Informe os filtros de exportação.");
        }
        if (filtro.getDataInicio() != null && filtro.getDataFim() != null
            && filtro.getDataInicio().isAfter(filtro.getDataFim())) {
            throw new IllegalArgumentException("Data inicial não pode ser posterior à data final.");
        }
        if (filtro.getContaId() != null
            && contaBancariaRepository.findByIdAndUsuarioId(filtro.getContaId(), usuarioId).isEmpty()) {
            throw new IllegalArgumentException("Conta não encontrada ou não pertence ao usuário.");
        }
        if (filtro.getCartaoId() != null
            && cartaoCreditoRepository.findByIdAndUsuarioId(filtro.getCartaoId(), usuarioId).isEmpty()) {
            throw new IllegalArgumentException("Cartão não encontrado ou não pertence ao usuário.");
        }
        if (filtro.getCategoriaId() != null
            && categoriaRepository.findByIdAndUsuarioId(filtro.getCategoriaId(), usuarioId).isEmpty()) {
            throw new IllegalArgumentException("Categoria não encontrada ou não pertence ao usuário.");
        }
    }
}
