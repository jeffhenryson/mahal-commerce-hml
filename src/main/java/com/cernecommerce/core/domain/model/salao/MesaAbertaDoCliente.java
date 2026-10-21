package com.cernecommerce.core.domain.model.salao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Comanda aberta do cliente identificado no QR — a de uma mesa do QR ou a aberta pelo PDV.
 *
 * <p>Na identificação simples (só CPF/celular) a lista é aviso: token e valor só da mesa deste QR,
 * sem consumo nem chamados — quem sabe só o CPF de alguém não descobre onde mais ele está nem
 * quanto deve. Com a confirmação ({@code /minhas-comandas}) vem tudo.</p>
 *
 * @param ref id opaco da comanda, para os chamados na comanda do PDV — nunca o id sequencial
 * @param token o do QR da mesa; nulo na comanda do PDV e onde a lista não revela
 * @param mesa o nome da mesa do QR, ou o rótulo da comanda no PDV
 * @param emAberto nulo onde a lista não revela
 * @param itens vazio onde a lista não revela
 * @param chamados os abertos e os atendidos há pouco; vazio onde a lista não revela
 */
public record MesaAbertaDoCliente(String ref, Origem origem, String token, String mesa, Instant abertaEm,
        BigDecimal emAberto, boolean estaMesa, List<MesaAtual.Item> itens, List<ChamadoMesa> chamados) {

    public MesaAbertaDoCliente {
        itens = itens == null ? List.of() : List.copyOf(itens);
        chamados = chamados == null ? List.of() : List.copyOf(chamados);
    }

    /** QR: mesa com QR (há ocupação). PDV: comanda aberta pelo PDV, sem QR. */
    public enum Origem {
        QR,
        PDV
    }
}
