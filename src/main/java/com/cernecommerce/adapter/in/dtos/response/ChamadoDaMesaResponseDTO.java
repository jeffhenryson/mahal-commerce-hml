package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.salao.ChamadoMesa;
import com.cernecommerce.core.domain.model.salao.FormaPagamentoChamado;
import com.cernecommerce.core.domain.model.salao.StatusChamado;
import com.cernecommerce.core.domain.model.salao.TipoChamado;

import java.time.Instant;

/** O chamado como a tela da mesa o vê: sem mesa, comanda nem quem atendeu. */
public record ChamadoDaMesaResponseDTO(Long id, TipoChamado tipo, FormaPagamentoChamado formaPagamento,
        StatusChamado status, Instant criadoEm, Instant atendidoEm) {

    public static ChamadoDaMesaResponseDTO of(ChamadoMesa c) {
        return new ChamadoDaMesaResponseDTO(c.id(), c.tipo(), c.formaPagamento(), c.status(), c.criadoEm(),
                c.atendidoEm());
    }
}
