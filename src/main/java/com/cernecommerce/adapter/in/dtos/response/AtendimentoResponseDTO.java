package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/** Etapa do pedido do app no caixa (ECM-F010). */
@Data
public class AtendimentoResponseDTO {

    @Schema(description = "PENDENTE, ACEITO, PRONTO, SAIU_PARA_ENTREGA, RETIRADO, ENTREGUE, RECUSADO, CANCELADO ou EXPIRADO.")
    private String status;

    @Schema(description = "RETIRADA ou ENTREGA.")
    private String tipo;

    @Schema(description = "PIX, CREDITO, DEBITO ou DINHEIRO — intenção do cliente, não o pagamento lançado.")
    private String preferenciaPagamento;

    private String observacao;

    @Schema(description = "Até quando o pedido segura o estoque sem pagamento; depois é cancelado.")
    private Instant prazo;

    @Schema(description = "Motivo da recusa, do cancelamento ou da expiração.")
    private String motivo;

    @Schema(description = "Caixa que atende o pedido. Só no PDV.")
    private Long sessionId;

    @Schema(description = "Operador que aceitou ou assumiu. Só no PDV.")
    private String atendidoPor;

    private Instant criadoEm;
    private Instant aceitoEm;
    private Instant prontoEm;
    private Instant saiuEm;
    private Instant concluidoEm;
    private Instant encerradoEm;
}
