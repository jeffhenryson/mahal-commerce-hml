package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.salao.ChamadoMesa;
import com.cernecommerce.core.domain.model.salao.FormaPagamentoChamado;
import com.cernecommerce.core.domain.model.salao.StatusChamado;
import com.cernecommerce.core.domain.model.salao.TipoChamado;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase.ChamadoResumo;

import java.time.Instant;

/** Chamado do QR como o PDV o vê: a mesa (ou o rótulo da comanda) e o cliente por nome. */
public record ChamadoMesaResponseDTO(Long id, TipoChamado tipo, FormaPagamentoChamado formaPagamento,
        StatusChamado status, String mesa, String cliente, Instant criadoEm, Instant atendidoEm,
        String atendidoPor) {

    public static ChamadoMesaResponseDTO of(ChamadoResumo r) {
        ChamadoMesa c = r.chamado();
        return new ChamadoMesaResponseDTO(c.id(), c.tipo(), c.formaPagamento(), c.status(), r.mesa(), r.cliente(),
                c.criadoEm(), c.atendidoEm(), c.atendidoPor());
    }
}
