package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.PedidoAppAtendimentoEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PedidoAppAtendimentoJpaRepository extends JpaRepository<PedidoAppAtendimentoEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PedidoAppAtendimentoEntity a WHERE a.orderId = :orderId")
    Optional<PedidoAppAtendimentoEntity> findByIdForUpdate(@Param("orderId") Long orderId);

    List<PedidoAppAtendimentoEntity> findByOrderIdIn(Collection<Long> orderIds);

    Page<PedidoAppAtendimentoEntity> findByStatusInOrderByCriadoEmAsc(Collection<String> statuses, Pageable pageable);

    Page<PedidoAppAtendimentoEntity> findByStatusInAndSessionIdOrderByCriadoEmAsc(Collection<String> statuses,
            Long sessionId, Pageable pageable);

    // Só os ainda não pagos: a entrega liquidada continua ACEITO até sair, e não pode expirar —
    // nem ocupar o lote do varredor para sempre.
    @Query("SELECT a.orderId FROM PedidoAppAtendimentoEntity a WHERE a.status IN ('PENDENTE', 'ACEITO', 'PRONTO') "
            + "AND a.prazo < :agora AND EXISTS (SELECT 1 FROM OrderEntity o WHERE o.id = a.orderId "
            + "AND o.status = 'AGUARDANDO_PAGAMENTO') ORDER BY a.prazo ASC")
    List<Long> findVencidos(@Param("agora") Instant agora, Pageable pageable);
}
