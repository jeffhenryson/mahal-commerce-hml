package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.request.CentralPedidosConfigDTO;
import com.cernecommerce.adapter.in.dtos.response.CentralPedidosIndicadoresResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.LojaStatusResponseDTO;
import com.cernecommerce.core.domain.model.ecommerce.CentralPedidosConfig;
import com.cernecommerce.core.ports.in.CentralPedidosUseCase.Indicadores;
import com.cernecommerce.core.ports.in.CentralPedidosUseCase.StatusLoja;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Configuração, status da loja e indicadores da Central de Pedidos (ECM-F013), entre DTO e domínio. */
public final class CentralPedidosDTOConverter {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private CentralPedidosDTOConverter() {
    }

    public static CentralPedidosConfig toDomain(CentralPedidosConfigDTO dto) {
        CentralPedidosConfigDTO.Entrega e = dto.getEntrega() == null ? new CentralPedidosConfigDTO.Entrega()
                : dto.getEntrega();
        List<CentralPedidosConfig.Horario> horarios = dto.getHorarios() == null ? List.of()
                : dto.getHorarios().stream()
                        .map(h -> new CentralPedidosConfig.Horario(h.getDiaSemana(), hora(h.getAbre()),
                                hora(h.getFecha())))
                        .toList();
        List<CentralPedidosConfig.TaxaBairro> bairros = e.getBairros() == null ? List.of()
                : e.getBairros().stream().map(b -> new CentralPedidosConfig.TaxaBairro(b.getNome(), b.getTaxa()))
                        .toList();
        return new CentralPedidosConfig(dto.isAppRecebendo(), dto.isQrRecebendo(), horarios,
                dto.isAceiteAutomatico(), dto.getTempoPreparoMin(), dto.getTempoEntregaMin(),
                dto.getExpiracaoSolicitacaoMin(),
                new CentralPedidosConfig.Entrega(e.isHabilitada(), e.getTaxaFixa(), e.getPedidoMinimo(), e.getRaioKm(),
                        bairros),
                dto.isRetiradaHabilitada(), dto.getPixChave());
    }

    public static CentralPedidosConfigDTO toDto(CentralPedidosConfig config) {
        CentralPedidosConfigDTO dto = new CentralPedidosConfigDTO();
        dto.setAppRecebendo(config.appRecebendo());
        dto.setQrRecebendo(config.qrRecebendo());
        dto.setHorarios(config.horarios().stream().map(h -> {
            CentralPedidosConfigDTO.Horario out = new CentralPedidosConfigDTO.Horario();
            out.setDiaSemana(h.diaSemana());
            out.setAbre(h.abre().format(HORA));
            out.setFecha(h.fecha().format(HORA));
            return out;
        }).toList());
        dto.setAceiteAutomatico(config.aceiteAutomatico());
        dto.setTempoPreparoMin(config.tempoPreparoMin());
        dto.setTempoEntregaMin(config.tempoEntregaMin());
        dto.setExpiracaoSolicitacaoMin(config.expiracaoSolicitacaoMin());
        CentralPedidosConfigDTO.Entrega entrega = new CentralPedidosConfigDTO.Entrega();
        entrega.setHabilitada(config.entrega().habilitada());
        entrega.setTaxaFixa(config.entrega().taxaFixa());
        entrega.setPedidoMinimo(config.entrega().pedidoMinimo());
        entrega.setRaioKm(config.entrega().raioKm());
        entrega.setBairros(config.entrega().bairros().stream().map(b -> {
            CentralPedidosConfigDTO.TaxaBairro out = new CentralPedidosConfigDTO.TaxaBairro();
            out.setNome(b.nome());
            out.setTaxa(b.taxa());
            return out;
        }).toList());
        dto.setEntrega(entrega);
        dto.setRetiradaHabilitada(config.retiradaHabilitada());
        dto.setPixChave(config.pixChave());
        return dto;
    }

    public static LojaStatusResponseDTO toDto(StatusLoja s) {
        return new LojaStatusResponseDTO(s.aberto(), s.appRecebendo(), s.entregaHabilitada(), s.retiradaHabilitada(),
                s.tempoPreparoMin(), s.tempoEntregaMin(), s.pedidoMinimo(), s.taxaFixa(),
                s.bairros().stream().map(b -> new LojaStatusResponseDTO.Bairro(b.nome(), b.taxa())).toList());
    }

    public static CentralPedidosIndicadoresResponseDTO toDto(Indicadores i) {
        return new CentralPedidosIndicadoresResponseDTO(
                i.porCanal().stream().map(c -> new CentralPedidosIndicadoresResponseDTO.PorCanal(c.channel(),
                        c.pedidos(), c.receitaLiquida(), c.ticketMedio())).toList(),
                new CentralPedidosIndicadoresResponseDTO.Pendentes(i.pendentesAgora().pedidosApp(),
                        i.pendentesAgora().solicitacoes(), i.pendentesAgora().chamados()),
                new CentralPedidosIndicadoresResponseDTO.Recusas(
                        i.recusas().pedidosApp().stream().map(r ->
                                new CentralPedidosIndicadoresResponseDTO.RecusaApp(r.reasonCode(), r.total())).toList(),
                        i.recusas().solicitacoes().stream().map(r ->
                                new CentralPedidosIndicadoresResponseDTO.RecusaQr(r.motivo(), r.total())).toList()),
                i.cancelamentos().stream().map(c -> new CentralPedidosIndicadoresResponseDTO.Cancelamentos(c.channel(),
                        c.total(), c.valor())).toList(),
                i.taxaRecusa());
    }

    /** O {@code @Pattern} do DTO já barrou o formato; nulo chega ao domínio, que recusa com mensagem. */
    private static LocalTime hora(String texto) {
        return texto == null ? null : LocalTime.parse(texto, HORA);
    }
}
