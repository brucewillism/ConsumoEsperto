package com.consumoesperto.repository;

import com.consumoesperto.model.DebitoInterno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DebitoInternoRepository extends JpaRepository<DebitoInterno, Long> {

    /** Direitos a receber: o usuário é o credor e o débito está pendente. */
    @Query("SELECT d FROM DebitoInterno d JOIN FETCH d.devedor JOIN FETCH d.credor "
        + "WHERE d.credor.id = :usuarioId AND d.liquidado = false ORDER BY d.dataCriacao DESC")
    List<DebitoInterno> findAReceber(@Param("usuarioId") Long usuarioId);

    /** Pendências do usuário: ele é o devedor e o débito está pendente. */
    @Query("SELECT d FROM DebitoInterno d JOIN FETCH d.devedor JOIN FETCH d.credor "
        + "WHERE d.devedor.id = :usuarioId AND d.liquidado = false ORDER BY d.dataCriacao DESC")
    List<DebitoInterno> findDevidos(@Param("usuarioId") Long usuarioId);

    /** Débitos pendentes em que o usuário é credor de um devedor específico (para quitação por nome). */
    @Query("SELECT d FROM DebitoInterno d JOIN FETCH d.devedor JOIN FETCH d.credor "
        + "WHERE d.credor.id = :credorId AND d.devedor.id = :devedorId AND d.liquidado = false "
        + "ORDER BY d.dataCriacao ASC")
    List<DebitoInterno> findPendentesEntre(@Param("credorId") Long credorId, @Param("devedorId") Long devedorId);

    Optional<DebitoInterno> findByIdAndCredorId(Long id, Long credorId);
}
