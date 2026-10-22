package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.salao.MesaAbertaDoCliente;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Comanda aberta do cliente no QR. Na identificação simples, token e {@code emAberto} só da mesa deste
 * QR e sem consumo, chamados nem compras da loja; com a confirmação ({@code /minhas-comandas}), completa.
 */
public record MesaAbertaDoClienteResponseDTO(String ref, MesaAbertaDoCliente.Origem origem, String token,
        String mesa, Instant abertaEm, BigDecimal emAberto, boolean estaMesa, List<MesaAtualResponseDTO.Item> itens,
        List<ChamadoDaMesaResponseDTO> chamados, List<MesaAtualResponseDTO.CompraLoja> comprasLoja) {

    public static MesaAbertaDoClienteResponseDTO of(MesaAbertaDoCliente m) {
        return new MesaAbertaDoClienteResponseDTO(m.ref(), m.origem(), m.token(), m.mesa(), m.abertaEm(),
                m.emAberto(), m.estaMesa(), m.itens().stream().map(MesaAtualResponseDTO.Item::of).toList(),
                m.chamados().stream().map(ChamadoDaMesaResponseDTO::of).toList(),
                m.comprasLoja().stream().map(MesaAtualResponseDTO.CompraLoja::of).toList());
    }
}
