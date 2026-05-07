package com.consumoesperto.repository;

import com.consumoesperto.model.Orcamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrcamentoRepository extends JpaRepository<Orcamento, Long> {

    @Query("SELECT o FROM Orcamento o JOIN FETCH o.categoria WHERE o.usuario.id = :usuarioId AND o.mes = :mes AND o.ano = :ano ORDER BY o.categoria.nome")
    List<Orcamento> findByUsuarioIdAndMesAndAno(
        @Param("usuarioId") Long usuarioId,
        @Param("mes") Integer mes,
        @Param("ano") Integer ano
    );

    Optional<Orcamento> findByUsuarioIdAndCategoriaIdAndMesAndAno(Long usuarioId, Long categoriaId, Integer mes, Integer ano);

    Optional<Orcamento> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Query("SELECT o FROM Orcamento o JOIN FETCH o.categoria WHERE o.grupoFamiliar.id = :grupoId "
        + "AND o.compartilhado = true AND o.mes = :mes AND o.ano = :ano ORDER BY o.categoria.nome")
    List<Orcamento> findCompartilhadosByGrupoIdAndMesAndAno(
        @Param("grupoId") Long grupoId,
        @Param("mes") Integer mes,
        @Param("ano") Integer ano
    );
}
