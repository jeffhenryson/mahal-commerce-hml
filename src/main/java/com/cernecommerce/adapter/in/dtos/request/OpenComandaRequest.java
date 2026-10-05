package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OpenComandaRequest {

    // PDV-F039 — opcional quando há customerId ou lead: a mesa nasce com o nome do cliente. Mesa
    // avulsa sem rótulo continua 400 (o domínio exige o rótulo).
    // PDV-C011 — casa com comanda.table_or_customer_label VARCHAR(100) (V104). Sem o @Size um
    // rótulo mais longo atravessava a validação e estourava no banco como 500, quando o certo é
    // 400. Molde no próprio módulo: CashMovementRequest.reason com @Size(max = 255).
    @Size(max = 100)
    @Schema(description = "Identificação livre da mesa ou do cliente — não é um vínculo de cadastro. "
            + "Serve para achar a mesa na tela; o vínculo é o customerId. PDV-F039: em branco com "
            + "customerId ou lead, a mesa recebe o nome do cliente; obrigatório na mesa avulsa.",
            example = "Mesa 4")
    private String tableOrCustomerLabel;

    @Schema(description = "Cliente do CRM vinculado à mesa (PDV-F010), opcional. É ele que faz o "
            + "pedido gerado no fechamento sair com nome e gerar cashback — sem ele a mesa vira "
            + "venda anônima, como o balcão sem CPF na nota.",
            example = "42")
    private Long customerId;

    @Valid
    @Schema(description = "PDV-F020 — cadastro rápido do cliente na abertura da mesa (nome + telefone, "
            + "CPF opcional). Faz find-or-create no CRM: reaproveita quem já existe pelo CPF ou "
            + "telefone. Ignorado se customerId vier preenchido. Exige CRM_LEAD_CREATE.")
    private CustomerRequest lead;
}
