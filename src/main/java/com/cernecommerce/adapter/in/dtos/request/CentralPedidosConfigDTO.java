package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuração do recebimento da Central de Pedidos (ECM-F013) — corpo do {@code PUT} e resposta do
 * {@code GET /central-pedidos/config}. O {@code PUT} substitui tudo. As regras entre campos (dia
 * repetido, bairro repetido, entrega e retirada desligadas com o app recebendo…) ficam no domínio e
 * voltam como {@code 400 INVALID_CENTRAL_CONFIG}.
 */
@Data
@Schema(name = "CentralPedidosConfig")
public class CentralPedidosConfigDTO {

    private static final String HORA = "^([01]\\d|2[0-3]):[0-5]\\d$";

    @Schema(description = "Desligado, o app mostra \"loja fechada\" e o checkout responde 409 LOJA_FECHADA.")
    private boolean appRecebendo = true;

    @Schema(description = "Desligado, nova solicitação pelo QR da mesa responde 409 QR_PAUSADO.")
    private boolean qrRecebendo = true;

    @Valid
    @Schema(description = "Vazio = sempre aberto. Um por dia; fecha antes de abre vira a noite (18:00 → 02:00).")
    private List<Horario> horarios = new ArrayList<>();

    @Schema(description = "Só gravado por ora: o aceite ainda exige caixa aberto e, na entrega, a taxa.")
    private boolean aceiteAutomatico;

    @Schema(description = "Estimativa mostrada no app, em minutos.", example = "20")
    private int tempoPreparoMin = 20;

    @Schema(description = "Estimativa mostrada no app, em minutos.", example = "40")
    private int tempoEntregaMin = 40;

    @Schema(description = "Validade da solicitação pelo QR, em minutos (mínimo 5).", example = "30")
    private int expiracaoSolicitacaoMin = 30;

    @Valid
    @NotNull
    private Entrega entrega = new Entrega();

    private boolean retiradaHabilitada = true;

    @Size(max = 120)
    @Schema(description = "Chave Pix da loja, enviada ao cliente no aceite da entrega.", example = "pix@mahal.com.br")
    private String pixChave;

    @Data
    @Schema(name = "CentralPedidosHorario")
    public static class Horario {

        @Schema(description = "ISO: 1 = segunda … 7 = domingo.", example = "5")
        private int diaSemana;

        @NotNull
        @Pattern(regexp = HORA, message = "use HH:MM")
        @Schema(example = "18:00")
        private String abre;

        @NotNull
        @Pattern(regexp = HORA, message = "use HH:MM")
        @Schema(example = "02:00")
        private String fecha;
    }

    @Data
    @Schema(name = "CentralPedidosEntrega")
    public static class Entrega {

        private boolean habilitada = true;

        @Digits(integer = 12, fraction = 2)
        @Schema(description = "Taxa sugerida quando o bairro não tem taxa própria.", example = "8.00")
        private BigDecimal taxaFixa = BigDecimal.ZERO;

        @Digits(integer = 12, fraction = 2)
        @Schema(description = "Abaixo disso a entrega é recusada no checkout (409 PEDIDO_MINIMO).", example = "30.00")
        private BigDecimal pedidoMinimo = BigDecimal.ZERO;

        @Schema(description = "Só gravado: não há geocodificação do endereço. Nulo = sem limite.", example = "5.0")
        private BigDecimal raioKm;

        @Valid
        @Schema(description = "Vazio = entrega em qualquer bairro. Com bairros, só neles (409 FORA_DA_AREA).")
        private List<TaxaBairro> bairros = new ArrayList<>();
    }

    @Data
    @Schema(name = "CentralPedidosTaxaBairro")
    public static class TaxaBairro {

        @Schema(example = "Centro")
        private String nome;

        @Digits(integer = 12, fraction = 2)
        @Schema(example = "5.00")
        private BigDecimal taxa;
    }
}
