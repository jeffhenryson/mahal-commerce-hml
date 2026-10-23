package com.cernecommerce.core.domain.model.ecommerce;

import com.cernecommerce.core.domain.exception.ecommerce.AtendimentoTransicaoInvalidaException;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Atendimento de um pedido do app no caixa (ECM-F010), 1:1 com o {@code Order} de marketplace.
 *
 * <h2>Por que não é mais um {@code OrderStatus}</h2>
 * <p>O status do pedido responde "foi pago?", e é ele que receita, cashback e fechamento leem. Aceite,
 * separação e despacho são operação da loja: misturá-los ali faria cada consumidor de venda conhecer
 * etapas que não lhe dizem respeito. Mesma filosofia de {@code order_delivery}: tabela própria, só
 * para o canal que a tem.</p>
 *
 * <h2>{@code sessionId} é quem atende, não quem liquida</h2>
 * <p>O caixa que aceitou é o que vê o pedido na sua tela e o único que pode liquidá-lo. Quem diz em
 * que gaveta o dinheiro entrou continua sendo {@code sales_order.session_id}, gravado na liquidação.
 * Se o caixa que aceitou fecha antes, outro operador assume ({@link #assumido}).</p>
 *
 * @param prazo até quando o pedido segura o estoque sem pagamento; passado ele, o varredor cancela.
 */
public record AtendimentoPedidoApp(
        Long orderId,
        AtendimentoStatus status,
        DeliveryType tipo,
        PreferenciaPagamento preferenciaPagamento,
        String observacao,
        Instant prazo,
        Long sessionId,
        String atendidoPor,
        String motivo,
        Instant criadoEm,
        Instant aceitoEm,
        Instant prontoEm,
        Instant saiuEm,
        Instant concluidoEm,
        Instant encerradoEm) {

    private static final Set<AtendimentoStatus> EM_ANDAMENTO =
            EnumSet.of(AtendimentoStatus.PENDENTE, AtendimentoStatus.ACEITO, AtendimentoStatus.PRONTO);

    public AtendimentoPedidoApp {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId é obrigatório");
        }
        if (status == null || tipo == null || preferenciaPagamento == null || prazo == null || criadoEm == null) {
            throw new IllegalArgumentException("status, tipo, preferenciaPagamento, prazo e criadoEm são obrigatórios");
        }
        if (status != AtendimentoStatus.PENDENTE && sessionId == null
                && !(status == AtendimentoStatus.CANCELADO || status == AtendimentoStatus.EXPIRADO
                        || status == AtendimentoStatus.RECUSADO)) {
            throw new IllegalArgumentException("atendimento " + status + " exige o caixa que o aceitou");
        }
        observacao = observacao == null || observacao.isBlank() ? null : observacao.strip();
    }

    /** Recém-chegado do checkout. */
    public static AtendimentoPedidoApp novo(Long orderId, DeliveryType tipo, PreferenciaPagamento preferencia,
            String observacao, Instant prazo, Instant agora) {
        return new AtendimentoPedidoApp(orderId, AtendimentoStatus.PENDENTE, tipo, preferencia, observacao, prazo,
                null, null, null, agora, null, null, null, null, null);
    }

    public boolean isEntrega() {
        return tipo == DeliveryType.ENTREGA;
    }

    /** Ainda pode ser recusado, cancelado ou expirar — nada de final nem despachado. */
    public boolean emAndamento() {
        return EM_ANDAMENTO.contains(status);
    }

    /** Pode ser pago no caixa: aceito, ou separado para retirada. */
    public boolean liquidavel() {
        return status == AtendimentoStatus.ACEITO || (status == AtendimentoStatus.PRONTO && !isEntrega());
    }

    public AtendimentoPedidoApp aceito(Long caixa, String operador, Instant agora) {
        exigir(status == AtendimentoStatus.PENDENTE, AtendimentoStatus.ACEITO);
        return new AtendimentoPedidoApp(orderId, AtendimentoStatus.ACEITO, tipo, preferenciaPagamento, observacao,
                prazo, caixa, operador, motivo, criadoEm, agora, prontoEm, saiuEm, concluidoEm, encerradoEm);
    }

    /** Outro caixa passa a responder pelo pedido — o que aceitou fechou, ou trocou de turno. */
    public AtendimentoPedidoApp assumido(Long caixa, String operador) {
        if (status != AtendimentoStatus.ACEITO && status != AtendimentoStatus.PRONTO) {
            throw new AtendimentoTransicaoInvalidaException(orderId,
                    "só pedido aceito ou pronto pode ser assumido por outro caixa (atual: " + status + ")");
        }
        return new AtendimentoPedidoApp(orderId, status, tipo, preferenciaPagamento, observacao, prazo, caixa,
                operador, motivo, criadoEm, aceitoEm, prontoEm, saiuEm, concluidoEm, encerradoEm);
    }

    public AtendimentoPedidoApp pronto(Instant agora) {
        exigirRetirada(AtendimentoStatus.PRONTO);
        exigir(status == AtendimentoStatus.ACEITO, AtendimentoStatus.PRONTO);
        return com(AtendimentoStatus.PRONTO, motivo, agora, saiuEm, concluidoEm, encerradoEm);
    }

    /** Retirada paga no balcão — a liquidação fecha o atendimento junto. */
    public AtendimentoPedidoApp retirado(Instant agora) {
        exigirRetirada(AtendimentoStatus.RETIRADO);
        exigir(liquidavel(), AtendimentoStatus.RETIRADO);
        return com(AtendimentoStatus.RETIRADO, motivo, prontoEm, saiuEm, agora, encerradoEm);
    }

    public AtendimentoPedidoApp saiuParaEntrega(Instant agora) {
        if (!isEntrega()) {
            throw new AtendimentoTransicaoInvalidaException(orderId, "pedido de retirada não sai para entrega");
        }
        exigir(status == AtendimentoStatus.ACEITO, AtendimentoStatus.SAIU_PARA_ENTREGA);
        return com(AtendimentoStatus.SAIU_PARA_ENTREGA, motivo, prontoEm, agora, concluidoEm, encerradoEm);
    }

    public AtendimentoPedidoApp entregue(Instant agora) {
        exigir(status == AtendimentoStatus.SAIU_PARA_ENTREGA, AtendimentoStatus.ENTREGUE);
        return com(AtendimentoStatus.ENTREGUE, motivo, prontoEm, saiuEm, agora, encerradoEm);
    }

    public AtendimentoPedidoApp recusado(String porque, Instant agora) {
        exigir(emAndamento(), AtendimentoStatus.RECUSADO);
        return com(AtendimentoStatus.RECUSADO, porque, prontoEm, saiuEm, concluidoEm, agora);
    }

    public AtendimentoPedidoApp cancelado(String porque, Instant agora) {
        exigir(emAndamento(), AtendimentoStatus.CANCELADO);
        return com(AtendimentoStatus.CANCELADO, porque, prontoEm, saiuEm, concluidoEm, agora);
    }

    public AtendimentoPedidoApp expirado(Instant agora) {
        exigir(emAndamento(), AtendimentoStatus.EXPIRADO);
        return com(AtendimentoStatus.EXPIRADO, "prazo do dia expirou sem pagamento", prontoEm, saiuEm, concluidoEm,
                agora);
    }

    private AtendimentoPedidoApp com(AtendimentoStatus novo, String novoMotivo, Instant novoPronto, Instant novoSaiu,
            Instant novoConcluido, Instant novoEncerrado) {
        return new AtendimentoPedidoApp(orderId, novo, tipo, preferenciaPagamento, observacao, prazo, sessionId,
                atendidoPor, novoMotivo, criadoEm, aceitoEm, novoPronto, novoSaiu, novoConcluido, novoEncerrado);
    }

    private void exigir(boolean permitido, AtendimentoStatus destino) {
        if (!permitido) {
            throw new AtendimentoTransicaoInvalidaException(orderId,
                    "pedido do app não pode ir de " + status + " para " + destino);
        }
    }

    private void exigirRetirada(AtendimentoStatus destino) {
        if (isEntrega()) {
            throw new AtendimentoTransicaoInvalidaException(orderId, "pedido de entrega não vai para " + destino);
        }
    }
}
