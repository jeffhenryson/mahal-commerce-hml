package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.PedidoAppAtendimentoEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoPedidoApp;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoStatus;
import com.cernecommerce.core.domain.model.ecommerce.PreferenciaPagamento;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;
import com.cernecommerce.core.ports.out.ecommerce.AtendimentoPedidoAppRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@Transactional
public class PedidoAppAtendimentoRepositoryImpl implements AtendimentoPedidoAppRepository {

    private final PedidoAppAtendimentoJpaRepository jpaRepository;

    public PedidoAppAtendimentoRepositoryImpl(PedidoAppAtendimentoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AtendimentoPedidoApp save(AtendimentoPedidoApp a) {
        PedidoAppAtendimentoEntity e = jpaRepository.findById(a.orderId()).orElseGet(PedidoAppAtendimentoEntity::new);
        e.setOrderId(a.orderId());
        e.setStatus(a.status().name());
        e.setTipo(a.tipo().name());
        e.setPreferenciaPagamento(a.preferenciaPagamento().name());
        e.setObservacao(truncate(a.observacao()));
        e.setPrazo(a.prazo());
        e.setSessionId(a.sessionId());
        e.setAtendidoPor(a.atendidoPor());
        e.setMotivo(truncate(a.motivo()));
        e.setCriadoEm(a.criadoEm());
        e.setAceitoEm(a.aceitoEm());
        e.setProntoEm(a.prontoEm());
        e.setSaiuEm(a.saiuEm());
        e.setConcluidoEm(a.concluidoEm());
        e.setEncerradoEm(a.encerradoEm());
        return toDomain(jpaRepository.save(e));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AtendimentoPedidoApp> findByOrderId(Long orderId) {
        return jpaRepository.findById(orderId).map(PedidoAppAtendimentoRepositoryImpl::toDomain);
    }

    @Override
    public Optional<AtendimentoPedidoApp> findByOrderIdForUpdate(Long orderId) {
        return jpaRepository.findByIdForUpdate(orderId).map(PedidoAppAtendimentoRepositoryImpl::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AtendimentoPedidoApp> findByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByOrderIdIn(orderIds).stream().map(PedidoAppAtendimentoRepositoryImpl::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AtendimentoPedidoApp> findByStatus(Set<AtendimentoStatus> statuses, Long sessionId, int page,
            int size) {
        List<String> names = statuses.stream().map(Enum::name).toList();
        PageRequest pageable = PageRequest.of(page, size);
        Page<PedidoAppAtendimentoEntity> result = sessionId == null
                ? jpaRepository.findByStatusInOrderByCriadoEmAsc(names, pageable)
                : jpaRepository.findByStatusInAndSessionIdOrderByCriadoEmAsc(names, sessionId, pageable);
        return new PageResult<>(result.getContent().stream().map(PedidoAppAtendimentoRepositoryImpl::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findVencidos(Instant agora, int limite) {
        return jpaRepository.findVencidos(agora, PageRequest.of(0, limite));
    }

    private static AtendimentoPedidoApp toDomain(PedidoAppAtendimentoEntity e) {
        return new AtendimentoPedidoApp(e.getOrderId(), AtendimentoStatus.valueOf(e.getStatus()),
                DeliveryType.valueOf(e.getTipo()), PreferenciaPagamento.valueOf(e.getPreferenciaPagamento()),
                e.getObservacao(), e.getPrazo(), e.getSessionId(), e.getAtendidoPor(), e.getMotivo(), e.getCriadoEm(),
                e.getAceitoEm(), e.getProntoEm(), e.getSaiuEm(), e.getConcluidoEm(), e.getEncerradoEm());
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }
}
