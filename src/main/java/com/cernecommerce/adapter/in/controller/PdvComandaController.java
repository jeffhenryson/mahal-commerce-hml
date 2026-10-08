package com.cernecommerce.adapter.in.controller;

import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import com.cernecommerce.adapter.in.dtos.request.ComandaCancelRequest;
import com.cernecommerce.adapter.in.dtos.request.StorePurchaseRequest;
import com.cernecommerce.adapter.in.dtos.response.ComandaSessionTimelineDTO;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pdv.ComandaHistoryFilter;
import com.cernecommerce.adapter.in.dtos.request.CustomerRequest;
import com.cernecommerce.adapter.in.dtos.request.LinkComandaCustomerRequest;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.LeadResolution;
import org.springframework.security.access.AccessDeniedException;
import com.cernecommerce.adapter.in.converter.ComandaDTOConverter;
import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.AddComandaItemRequest;
import com.cernecommerce.adapter.in.dtos.request.CloseComandaRequest;
import com.cernecommerce.adapter.in.dtos.request.RenameComandaRequest;
import com.cernecommerce.adapter.in.dtos.request.OpenComandaRequest;
import com.cernecommerce.adapter.in.dtos.response.ComandaResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ServiceFeeResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.exception.pdv.ComandaDiscountNotAllowedException;
import com.cernecommerce.core.domain.exception.pdv.CourtesyNotAllowedException;
import com.cernecommerce.core.domain.exception.pdv.SurchargeNotAllowedException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Controller de <b>comanda de mesa</b> (PDV-F009), separado de {@link PdvController}: o PDV
 * atual modela venda pontual de balcão, e a comanda é um pedido incremental de horas — o caso do
 * lounge de narguilé. Endpoints novos, sem mudar {@code POST /pdv/sessions/{id}/sales}.
 */
@RestController
@RequestMapping("/pdv/comandas")
@Tag(name = "PDV (Comanda de Mesa)", description = "Pedidos incrementais numa sessão de caixa aberta por horas")
@SecurityRequirement(name = "bearerAuth")
// PDV-C011/C012 — sem @Validated as constraints de parâmetro de query (@Min/@Max abaixo) não são
// aplicadas. PdvController já o tinha; este ficou de fora desde PDV-F009.
@Validated
public class PdvComandaController {

    /**
     * PDV-F010 — cortesia é desconto de 100%, e desconto tem dono. Checada aqui, e não no service,
     * pelo mesmo motivo de {@code PDV_SALE_DISCOUNT} em {@code PdvController}: o núcleo não conhece
     * Spring Security.
     */
    private static final String COURTESY_AUTHORITY = "PDV_COMANDA_COURTESY";

    /**
     * PDV-F011 — a simetria de {@link #COURTESY_AUTHORITY}, na direção oposta: se lançar linha a
     * zero é um desconto de 100% e tem dono, subir o preço à mão também tem. Checada aqui pelo
     * mesmo motivo — o núcleo não conhece Spring Security.
     */
    private static final String SURCHARGE_AUTHORITY = "PDV_COMANDA_SURCHARGE";

    /**
     * PDV-F014 — abater da conta no fechamento tem dono, como lançar a zero (cortesia) e subir o
     * preço (acréscimo). Permissão de <b>mesa</b>, separada de {@code PDV_SALE_DISCOUNT}: alçada de
     * salão e alçada de caixa são concedidas a pessoas diferentes. O teto, esse, é compartilhado.
     */
    private static final String COMANDA_DISCOUNT_AUTHORITY = "PDV_COMANDA_DISCOUNT";

    /** PDV-F020 — cadastrar o lead da mesa é a mesma alçada do cadastro rápido do balcão. */
    private static final String LEAD_CREATE_AUTHORITY = "CRM_LEAD_CREATE";
    private static final String CUSTOMER_MANAGE_AUTHORITY = "CRM_CUSTOMER_MANAGE";
    private static final String LEAD_ORIGIN = "Mesa";
    /** comanda.table_or_customer_label VARCHAR(100) (V104), o mesmo teto de OpenComandaRequest. */
    private static final int MAX_LABEL = 100;

    private final ComandaUseCase comandaUseCase;
    private final ComandaDTOConverter comandaConverter;
    private final OrderDTOConverter orderConverter;
    private final CrmUseCase crmUseCase;
    private final ApplicationEventPublisher publisher;

    public PdvComandaController(ComandaUseCase comandaUseCase, ComandaDTOConverter comandaConverter,
            OrderDTOConverter orderConverter, CrmUseCase crmUseCase, ApplicationEventPublisher publisher) {
        this.comandaUseCase = comandaUseCase;
        this.comandaConverter = comandaConverter;
        this.orderConverter = orderConverter;
        this.crmUseCase = crmUseCase;
        this.publisher = publisher;
    }

    /**
     * Resolve {@code customerName} no CRM para as comandas já convertidas — em lote, para a lista
     * de mesas abertas não pagar uma consulta por mesa. Mesmo padrão de
     * {@code OrdersController.enrichCustomerNames}.
     */
    private List<ComandaResponseDTO> enrichCustomerNames(List<ComandaResponseDTO> comandas) {
        List<Long> customerIds = comandas.stream()
                .map(ComandaResponseDTO::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (customerIds.isEmpty()) {
            return comandas;
        }
        Map<Long, String> names = crmUseCase.findCustomerNames(customerIds);
        comandas.forEach(dto -> dto.setCustomerName(names.get(dto.getCustomerId())));
        return comandas;
    }

    /** Ver {@link #COURTESY_AUTHORITY}. {@code TROCA} é cortesia mesmo sem o cliente marcar. */
    private void requireCourtesyAuthority(Boolean courtesy, ConsumptionMode mode,
            Authentication authentication) {
        boolean isCourtesy = Boolean.TRUE.equals(courtesy)
                || (mode != null && mode.impliesCourtesy());
        if (!isCourtesy) {
            return;
        }
        requireAuthority(COURTESY_AUTHORITY, authentication,
                () -> new CourtesyNotAllowedException(authentication.getName()));
    }

    /**
     * Ver {@link #SURCHARGE_AUTHORITY}. Só acréscimo <b>positivo</b> exige a permissão: zero e
     * nulo são a mesma coisa — um no-op — e cobrar permissão por um no-op só produziria 403
     * inexplicável. Mesmo critério de {@code requireDiscountAuthority} em {@code PdvController},
     * que também olha o valor e não a presença do campo.
     *
     * <p>Acréscimo <b>negativo</b> de propósito não cai aqui: ele segue para o service e volta como
     * {@code 400 SURCHARGE_INVALID}, que é a resposta certa. Um 403 ali diria ao operador que o
     * problema é de permissão quando o problema é o número.</p>
     */
    private void requireSurchargeAuthority(BigDecimal surchargeAmount, Authentication authentication) {
        if (surchargeAmount == null || surchargeAmount.signum() <= 0) {
            return;
        }
        requireAuthority(SURCHARGE_AUTHORITY, authentication,
                () -> new SurchargeNotAllowedException(authentication.getName()));
    }

    /**
     * Ver {@link #COMANDA_DISCOUNT_AUTHORITY}. Só desconto <b>positivo</b> exige a permissão, mesmo
     * critério de {@code requireSurchargeAuthority} e de {@code requireDiscountAuthority} no
     * balcão: zero e nulo são um no-op, e cobrar permissão por um no-op só produziria 403
     * inexplicável em todo fechamento comum.
     */
    private void requireComandaDiscountAuthority(BigDecimal discountAmount, Authentication authentication) {
        if (discountAmount == null || discountAmount.signum() <= 0) {
            return;
        }
        requireAuthority(COMANDA_DISCOUNT_AUTHORITY, authentication,
                () -> new ComandaDiscountNotAllowedException(authentication.getName()));
    }

    /**
     * Payload de auditoria da comanda (PDV-C014), sempre com o {@code comandaId} na frente — é a
     * chave por onde alguém procura a mesa depois de um fechamento estranho.
     *
     * <p><b>Tolera valores nulos, e é por isso que existe em vez de um {@code Map.of} direto:</b>
     * {@code Map.of} lança {@code NullPointerException} em valor nulo, e um payload de auditoria
     * não pode ser capaz de derrubar a requisição que ele apenas descreve. Campo nulo é simplesmente
     * omitido — ausência não carrega informação nenhuma na trilha.</p>
     */
    private static Map<String, Object> auditPayload(Long comandaId, Object... keyValuePairs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("comandaId", comandaId);
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            Object value = keyValuePairs[i + 1];
            if (value != null) {
                payload.put((String) keyValuePairs[i], value);
            }
        }
        return payload;
    }

    /**
     * PDV-F020 — resolve o cliente da mesa: {@code customerId} existente (404 se não existir, em
     * vez do 409 genérico da FK), ou {@code lead} por find-or-create no CRM, ou nenhum.
     */
    private Long resolveComandaCustomer(Long customerId, CustomerRequest lead, Authentication authentication) {
        Customer customer = resolveComandaCustomerRecord(customerId, lead, authentication);
        return customer == null ? null : customer.id();
    }

    private Customer resolveComandaCustomerRecord(Long customerId, CustomerRequest lead,
            Authentication authentication) {
        if (customerId != null) {
            return crmUseCase.findCustomerById(customerId);
        }
        if (lead == null) {
            return null;
        }
        boolean allowed = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> LEAD_CREATE_AUTHORITY.equals(a) || CUSTOMER_MANAGE_AUTHORITY.equals(a));
        if (!allowed) {
            throw new AccessDeniedException("Sem permissão para cadastrar cliente: " + LEAD_CREATE_AUTHORITY);
        }
        LeadResolution resolution = crmUseCase.resolveLead(lead.getNome(), lead.getContato(), lead.getEmail(),
                lead.getCpf(), lead.getOrigem() == null || lead.getOrigem().isBlank() ? LEAD_ORIGIN : lead.getOrigem());
        if (resolution.created()) {
            publisher.publishEvent(AuditEvent.of(EventType.CUSTOMER_CREATED, authentication.getName(),
                    Map.of("customerId", String.valueOf(resolution.customer().id()))));
        }
        return resolution.customer();
    }

    /**
     * PDV-F039 — a mesa aberta com cliente ou lead e sem rótulo nasce com o nome dele. Rótulo
     * digitado prevalece. Sem rótulo e sem cliente, a mesa avulsa cai no 400 do domínio.
     */
    static String comandaLabel(String informed, Customer customer) {
        if (informed != null && !informed.isBlank()) {
            return informed;
        }
        if (customer == null || customer.nome() == null || customer.nome().isBlank()) {
            return informed;
        }
        String nome = customer.nome().trim();
        return nome.length() > MAX_LABEL ? nome.substring(0, MAX_LABEL) : nome;
    }

    private void requireAuthority(String authority, Authentication authentication,
            Supplier<RuntimeException> onDenied) {
        boolean allowed = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
        if (!allowed) {
            throw onDenied.get();
        }
    }

    @Operation(summary = "Abre uma comanda na sessão do operador autenticado",
            description = "Abrir exige a PRÓPRIA sessão: a mesa nasce na gaveta de quem a abriu, e "
                    + "é esse depósito que baixa estoque. Operar mesa já aberta (lançar, fechar, "
                    + "cancelar) é que não exige posse — ver PDV-F010.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Aberta", content = @Content(schema = @Schema(implementation = ComandaResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "A sessão é de outro operador", content = @Content),
            @ApiResponse(responseCode = "404", description = "Sessão não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Sessão encerrada", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> openComanda(@RequestParam Long sessionId,
            @Valid @RequestBody OpenComandaRequest request, Authentication authentication) {
        Customer customer = resolveComandaCustomerRecord(request.getCustomerId(), request.getLead(), authentication);
        Comanda comanda = comandaUseCase.openComanda(sessionId,
                comandaLabel(request.getTableOrCustomerLabel(), customer),
                customer == null ? null : customer.id(), authentication.getName());
        // PDV-C014 — abrir mesa não deixava rastro nenhum, ao contrário de abrir caixa
        // (CASH_SESSION_OPENED). É o evento que responde "quem abriu a Mesa 4, e quando".
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_OPENED, authentication.getName(),
                auditPayload(comanda.id(),
                        "sessionId", sessionId,
                        "warehouseCode", comanda.warehouseCode(),
                        "tableOrCustomerLabel", comanda.tableOrCustomerLabel())));
        ComandaResponseDTO dto = comandaConverter.toResponse(comanda);
        enrichCustomerNames(List.of(dto));
        return ResponseEntity.created(URI.create("/pdv/comandas/" + comanda.id())).body(dto);
    }

    @Operation(summary = "Lança um item na comanda aberta, debitando o estoque na hora",
            description = "Aceita item comum e linha de sessão de narguilé (PDV-F010). O preço "
                    + "unitário é sempre resolvido pelo servidor: NORMAL e SABOR_EXTRA cobram o "
                    + "preço da variação do sabor, OPEN_ROSH cobra o openRoshPrice do produto PAI "
                    + "(não o da variação), e cortesia/TROCA gravam zero — sempre com o custo "
                    + "congelado normalmente, para a margem mostrar o prejuízo real da promo. "
                    + "Cortesia exige PDV_COMANDA_COURTESY. A mesa pode ser operada por quem não é "
                    + "dono do caixa que a abriu. PDV-F011: aceita notes (registro do setup da "
                    + "mesa, texto opaco sem efeito em preço) e surchargeAmount (acréscimo somado "
                    + "ao openRoshPrice do produto PAI, só em OPEN_ROSH, exigindo "
                    + "PDV_COMANDA_SURCHARGE).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lançado", content = @Content(schema = @Schema(implementation = ComandaResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Saldo insuficiente, SKU sem disponibilidade para mesa (NOT_AVAILABLE_FOR_TABLE), modo de sessão em SKU comum (NOT_A_SESSION_PRODUCT), open rosh sem preço (OPEN_ROSH_NOT_PRICED), linha de origem ausente (LINKED_ITEM_REQUIRED), nota acima de 200 caracteres (NOTES_TOO_LONG), acréscimo negativo (SURCHARGE_INVALID), em cortesia (SURCHARGE_ON_COURTESY) ou fora de OPEN_ROSH (SURCHARGE_NOT_APPLICABLE)", content = @Content),
            @ApiResponse(responseCode = "403", description = "Cortesia sem PDV_COMANDA_COURTESY (COURTESY_NOT_ALLOWED) ou acréscimo sem PDV_COMANDA_SURCHARGE (SURCHARGE_NOT_ALLOWED)", content = @Content),
            @ApiResponse(responseCode = "404", description = "Comanda ou SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não está aberta, produto sem preço, sessão de caixa encerrada, ou troca sobre linha que não é open rosh (NOT_AN_OPEN_ROSH)", content = @Content)
    })
    @PostMapping("/{id}/items")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> addItem(@PathVariable("id") Long comandaId,
            @Valid @RequestBody AddComandaItemRequest request, Authentication authentication) {
        requireCourtesyAuthority(request.getCourtesy(), request.getMode(), authentication);
        requireSurchargeAuthority(request.getSurchargeAmount(), authentication);
        Comanda comanda = comandaUseCase.addItem(comandaId, request.getSku(), request.getQuantity(),
                request.getMode(), Boolean.TRUE.equals(request.getCourtesy()), request.getLinkedItemId(),
                request.getNotes(), request.getSurchargeAmount(), authentication.getName());
        // PDV-C014 — tipo próprio, no lugar do STOCK_MOVEMENT_REGISTERED emprestado. O estoque de
        // fato se move aqui, mas quem audita uma mesa procura pela mesa, não pelo ledger — e o
        // rastro item a item continua em stock_movement, que não mudou.
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_ITEM_ADDED, authentication.getName(),
                auditPayload(comandaId,
                        "warehouseCode", comanda.warehouseCode(),
                        "type", MovementType.SAIDA.name(),
                        "sku", request.getSku(),
                        // A cortesia baixa estoque sem entrar dinheiro: é exatamente o lançamento
                        // que alguém vai querer auditar depois de um fechamento estranho.
                        "mode", request.getMode() == null ? ConsumptionMode.NORMAL.name() : request.getMode().name(),
                        "courtesy", Boolean.TRUE.equals(request.getCourtesy()))));
        ComandaResponseDTO dto = comandaConverter.toResponse(comanda);
        enrichCustomerNames(List.of(dto));
        return ResponseEntity.status(201).body(dto);
    }

    @Operation(summary = "Remove uma linha da comanda aberta, devolvendo o estoque dela",
            description = "PDV-F012 — até aqui um lançamento errado só saía cancelando a comanda "
                    + "INTEIRA, o que devolve tudo ao estoque, encerra a mesa e obriga a relançar "
                    + "item a item um consumo que continua acontecendo. As linhas TROCA penduradas "
                    + "nesta saem JUNTO: são cortesia e não existem sem o consumo livre que as "
                    + "originou. Já um SABOR_EXTRA pendurado BARRA a remoção (409 "
                    + "LINKED_ITEM_IS_CHARGED) em vez de ser arrastado — é linha própria e pode "
                    + "estar sendo cobrada, e apagá-la em cascata tiraria valor da conta sem o "
                    + "operador pedir. Cada linha removida gera uma ENTRADA de estoque, o mesmo "
                    + "padrão do cancelamento. Reusa PDV_COMANDA_MANAGE: quem já pode cancelar a "
                    + "mesa inteira não precisa de permissão maior para remover uma linha dela.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Removida, com a comanda atualizada", content = @Content(schema = @Schema(implementation = ComandaResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "A linha já foi cobrada num fechamento parcial (ITEM_NOT_OPEN_IN_COMANDA, PDV-C023): está num pedido pago, e desfazer venda paga é reembolso do pedido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Comanda ou item não encontrado (COMANDA_ITEM_NOT_FOUND)", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não está aberta, sessão de caixa encerrada, ou a linha tem SABOR_EXTRA pendurado (LINKED_ITEM_IS_CHARGED)", content = @Content)
    })
    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> removeItem(@PathVariable("id") Long comandaId,
            @PathVariable("itemId") Long itemId, Authentication authentication) {
        Comanda comanda = comandaUseCase.removeItem(comandaId, itemId, authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_ITEM_REMOVED, authentication.getName(),
                auditPayload(comandaId,
                        "itemId", itemId,
                        "warehouseCode", comanda.warehouseCode(),
                        "type", MovementType.ENTRADA.name())));
        ComandaResponseDTO dto = comandaConverter.toResponse(comanda);
        enrichCustomerNames(List.of(dto));
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Consulta uma comanda em qualquer status, com o total corrente e os pedidos gerados",
            description = "PDV-F029 — também para comanda FECHADA ou CANCELADA: traz closedBy, durationMinutes, "
                    + "cancelReason, os totais derivados dos pedidos e orders[] com os pagamentos de cada um. "
                    + "PDV-F035: sessions[] com a linha do tempo de cada sessão (espera, preparo, na mesa). "
                    + "PDV-F036: boughtInStore.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PDV_READ', 'ORDER_READ')")
    public ResponseEntity<ComandaResponseDTO> getComanda(@PathVariable("id") Long comandaId) {
        ComandaUseCase.ComandaHistoryEntry entry = comandaUseCase.getHistoryEntry(comandaId);
        ComandaResponseDTO dto = toHistoryResponse(entry);
        dto.setOrders(entry.orders().stream().map(order -> {
            ComandaResponseDTO.ComandaOrder o = new ComandaResponseDTO.ComandaOrder();
            o.setId(order.id());
            o.setOrderNumber(order.orderNumber());
            o.setClosedAt(order.concludedAt());
            o.setTotalPayable(order.totalPayable());
            o.setStatus(order.status().name());
            o.setPayments(entry.paymentsByOrder().getOrDefault(order.id(), List.of()).stream()
                    .map(orderConverter::toResponse).toList());
            return o;
        }).toList());
        dto.setSessions(entry.sessions().stream().map(ComandaSessionTimelineDTO::of).toList());
        dto.setAberturaAtePrimeiraSessaoMin(entry.aberturaAtePrimeiraSessaoMin());
        dto.setUltimoRecolhimentoAteEncerramentoMin(entry.ultimoRecolhimentoAteEncerramentoMin());
        enrichCustomerNames(List.of(dto));
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Histórico de mesas encerradas (PDV-F029)",
            description = "FECHADA e CANCELADA, da mais recente para a mais antiga (por closedAt). from/to recortam "
                    + "pelo encerramento; tableLabel compara sem caixa e sem espaços nas pontas. Cada item traz "
                    + "closedBy, durationMinutes, cancelReason, orderIds (inclusive pedidos parciais), totalPaid, "
                    + "serviceFeeTotal, discountTotal, courtesyTotal, sessionsCount e boughtInStore. "
                    + "boughtInStore=true|false filtra pela resposta a \"comprou na loja?\" (PDV-F036).")
    @GetMapping("/history")
    @PreAuthorize("hasAnyAuthority('PDV_READ', 'ORDER_READ')")
    public ResponseEntity<PageResult<ComandaResponseDTO>> listHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) ComandaStatus status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String openedBy,
            @RequestParam(required = false) String closedBy,
            @RequestParam(required = false) String tableLabel,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) Boolean boughtInStore,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        if (status == ComandaStatus.ABERTA) {
            throw new IllegalArgumentException("o histórico é de mesas encerradas: status FECHADA ou CANCELADA");
        }
        PageResult<ComandaUseCase.ComandaHistoryEntry> result = comandaUseCase.listHistory(
                new ComandaHistoryFilter(from, to, status, customerId, openedBy, closedBy, tableLabel, warehouseCode,
                        boughtInStore),
                page, size);
        List<ComandaResponseDTO> content = enrichCustomerNames(
                result.content().stream().map(this::toHistoryResponse).toList());
        return ResponseEntity.ok(new PageResult<>(content, result.page(), result.size(),
                result.totalElements(), result.totalPages()));
    }

    @Operation(summary = "Indicadores de mesas no período (PDV-F029)",
            description = "Sobre as mesas FECHADAS com encerramento entre from e to (máximo 366 dias). porMesa "
                    + "agrupa pelo tableLabel sem caixa e sem espaços nas pontas; porHora usa a hora de abertura "
                    + "(America/Sao_Paulo); porAtendente usa quem abriu. receita é o totalPayable dos pedidos não "
                    + "reembolsados. PDV-F035: sessoesNarguile traz as médias de fase (espera, preparo, na mesa), "
                    + "pagasNoFinal e desistidas. PDV-F036: compraNaLoja é a conversão das mesas com sessão, "
                    + "sobre as respondidas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Período ausente, invertido ou acima de 366 dias", content = @Content)
    })
    @GetMapping("/analytics")
    @PreAuthorize("hasAnyAuthority('PDV_READ', 'ORDER_READ')")
    public ResponseEntity<ComandaUseCase.ComandaAnalytics> analytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String warehouseCode) {
        return ResponseEntity.ok(comandaUseCase.analytics(from, to, warehouseCode));
    }

    private ComandaResponseDTO toHistoryResponse(ComandaUseCase.ComandaHistoryEntry entry) {
        ComandaResponseDTO dto = comandaConverter.toResponse(entry.comanda());
        dto.setClosedBy(entry.closedBy());
        dto.setDurationMinutes(entry.durationMinutes());
        dto.setCancelReason(entry.cancelReason());
        dto.setOrderIds(entry.orders().stream().map(Order::id).toList());
        dto.setTotalPaid(entry.totalPaid());
        dto.setServiceFeeTotal(entry.serviceFeeTotal());
        dto.setDiscountTotal(entry.discountTotal());
        dto.setCourtesyTotal(entry.courtesyTotal());
        dto.setSessionsCount(entry.sessionsCount());
        if (entry.storePurchase() != null) {
            dto.setBoughtInStore(entry.storePurchase().bought());
            dto.setBoughtInStoreBy(entry.storePurchase().answeredBy());
            dto.setBoughtInStoreAt(entry.storePurchase().answeredAt());
        }
        return dto;
    }

    @Operation(summary = "Registra se o cliente da mesa comprou algo na loja (PDV-F036)",
            description = "Uma resposta por mesa, em qualquer status: o front pergunta ao recolher a última "
                    + "sessão ou ao encerrar, e dá para corrigir pelo histórico. Responder de novo sobrescreve. "
                    + "Alimenta compraNaLoja em /analytics.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Registrado"),
            @ApiResponse(responseCode = "400", description = "boughtInStore ausente", content = @Content),
            @ApiResponse(responseCode = "404", description = "Comanda não encontrada", content = @Content)
    })
    @PutMapping("/{id}/store-purchase")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<Void> recordStorePurchase(@PathVariable("id") Long comandaId,
            @Valid @RequestBody StorePurchaseRequest request, Authentication authentication) {
        comandaUseCase.recordStorePurchase(comandaId, request.getBoughtInStore(), authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_STORE_PURCHASE_RECORDED, authentication.getName(),
                auditPayload(comandaId, "boughtInStore", request.getBoughtInStore())));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lista as comandas abertas — as \"mesas ocupadas\"",
            description = "Sem sessionId a listagem é da LOJA INTEIRA, não de um caixa (PDV-C007): a "
                    + "decisão do dono é caixa por atendente, mesas compartilhadas, e quem assume o "
                    + "posto do colega precisa ver o salão todo. Era isso que obrigava o cliente a "
                    + "listar as sessões abertas e disparar uma chamada por sessão. Não há filtro "
                    + "por status da sessão porque não é preciso: desde PDV-C005 o caixa não fecha "
                    + "com mesa aberta, então comanda ABERTA já implica sessão OPEN.")
    @GetMapping
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<PageResult<ComandaResponseDTO>> listOpenComandas(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        PageResult<Comanda> result = comandaUseCase.listOpenComandas(sessionId, warehouseCode, page, size);
        // enrichCustomerNames roda uma vez sobre a página inteira, não por mesa: é o que impede a
        // listagem da loja de pagar uma consulta de CRM por comanda.
        List<ComandaResponseDTO> content = enrichCustomerNames(comandaConverter.toResponse(result.content()));
        return ResponseEntity.ok(new PageResult<>(content, result.page(), result.size(),
                result.totalElements(), result.totalPages()));
    }

    @Operation(summary = "Fecha a comanda, convertendo os itens acumulados num pedido concluído",
            description = "Mesmo contrato de pagamento de POST /pdv/sessions/{id}/sales: payments "
                    + "exige pelo menos uma linha, e só DINHEIRO pode ser tendido a mais para gerar "
                    + "troco. CRM-F010: aceita uma linha MARCADO (com dueDate), com ou sem itemIds — "
                    + "mesmas regras e códigos de erro do balcão; o cliente é o da mesa. O estoque já foi debitado item a item em cada lançamento — o "
                    + "fechamento não toca em saldo de novo. O pedido gerado NASCE com channel = "
                    + "MESA (o canal é imutável), carregando comandaId, tableLabel e o cliente da "
                    + "mesa. Qualquer atendente com PDV_COMANDA_MANAGE pode fechar, e o pedido "
                    + "entra na sessão de caixa de QUEM FECHA — o dinheiro pertence à gaveta que o "
                    + "recebeu. PDV-F014: discountAmount abate a CONTA INTEIRA e é rateado entre as "
                    + "linhas pelo servidor (exige PDV_COMANDA_DISCOUNT, teto "
                    + "pdv.sale.max-discount-percent). PDV-F015: a taxa de serviço vem APLICADA POR "
                    + "PADRÃO sobre o líquido — applyServiceFee=false é o cliente recusando — e é "
                    + "gravada fora do netAmount, que continua sendo só a receita da mercadoria. O "
                    + "pagamento é validado contra netAmount + taxa. PDV-F017: mandando itemIds, "
                    + "o fechamento cobra SÓ aquelas linhas e a comanda CONTINUA ABERTA com o "
                    + "restante — é a conta dividida, \"cada um paga o que consumiu\". Desconto, "
                    + "taxa e troco incidem só sobre o escopo. PDV-F023: o fechamento com itemIds "
                    + "NUNCA encerra a mesa nem libera utensílio, mesmo levando a última linha "
                    + "aberta — encerra-se com POST /{id}/finish. Omitir itemIds cobra tudo que "
                    + "está em aberto e encerra a mesa, desde que toda sessão esteja RECOLHIDO "
                    + "(senão 409 SESSION_NOT_COLLECTED). A taxa de serviço nunca incide sobre "
                    + "linhas SESSAO/ROSH_EXTRA.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fechada", content = @Content(schema = @Schema(implementation = OrderResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Pagamento insuficiente", content = @Content),
            @ApiResponse(responseCode = "403", description = "Desconto sem PDV_COMANDA_DISCOUNT (COMANDA_DISCOUNT_NOT_ALLOWED)", content = @Content),
            @ApiResponse(responseCode = "404", description = "Comanda não encontrada", content = @Content),
            @ApiResponse(responseCode = "400", description = "Linha inexistente ou já cobrada em itemIds (ITEM_NOT_OPEN_IN_COMANDA)", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não está aberta, sem itens (COMANDA_EMPTY), só com cortesias (COMANDA_ONLY_COURTESY), quem fecha não tem caixa aberto, desconto acima do teto (DISCOUNT_LIMIT_EXCEEDED), pagamento não-dinheiro acima do total, ou seleção que separa linhas ligadas (LINKED_ITEM_MUST_CLOSE_TOGETHER) ou sessão não recolhida no fechamento total (SESSION_NOT_COLLECTED)", content = @Content)
    })
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<OrderResponseDTO> closeComanda(@PathVariable("id") Long comandaId,
            @Valid @RequestBody CloseComandaRequest request, Authentication authentication) {
        requireComandaDiscountAuthority(request.getDiscountAmount(), authentication);
        List<PaymentCommand> payments = request.getPayments().stream()
                .map(p -> new PaymentCommand(
                        com.cernecommerce.core.domain.model.pagamento.PaymentMethod.valueOf(p.getMethod()),
                        p.getAmount(), p.getInstallments(), p.getChannel(), p.getProvider(),
                        "MARCADO".equals(p.getMethod()) ? p.getDueDate() : null))
                .toList();
        OnAccountGuard.requireAuthorityIfOnAccount(payments, authentication);
        // A sobrecarga completa, sempre: as de conveniência de ComandaUseCase são `default` da
        // interface e perdem a transação quando chamadas pelo proxy (PLAT-C047).
        Order order = comandaUseCase.closeComanda(comandaId, payments, request.getDiscountAmount(),
                request.isServiceFeeApplied(), request.getItemIds(), authentication.getName());
        // PDV-C014 — era o pior dos três: STOCK_MOVEMENT_REGISTERED num caminho que NÃO move
        // estoque nenhum (o débito aconteceu item a item, no lançamento). O evento entrava na
        // trilha de movimentação descrevendo algo que não aconteceu.
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_CLOSED, authentication.getName(),
                auditPayload(comandaId,
                        "orderId", order.id(),
                        "customerId", order.customerId(),
                        "orderNumber", order.orderNumber(),
                        "warehouseCode", order.warehouseCode(),
                        "netAmount", order.netAmount(),
                        "serviceFeeAmount", order.serviceFeeAmount(),
                        "discountAmount", order.discountAmount())));
        OnAccountGuard.publishCreatedIfOnAccount(publisher, order, payments, authentication.getName());
        if (order.totalCashbackEarned().signum() > 0) {
            publisher.publishEvent(AuditEvent.of(EventType.CASHBACK_EARNED, authentication.getName(),
                    Map.of("orderId", order.id(), "orderNumber", order.orderNumber(),
                            "amount", order.totalCashbackEarned())));
        }
        return ResponseEntity.ok(orderConverter.toResponse(order));
    }

    @Operation(summary = "Encerra a mesa já toda paga",
            description = "PDV-F023 — com a sessão paga no lançamento, a mesa chega ao fim sem nada a "
                    + "cobrar, e o fechamento parcial nunca a encerra. Não gera pedido: o cabeçalho "
                    + "aponta o último pedido que cobrou a mesa. Mesa sem nenhuma linha se cancela.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Encerrada"),
            @ApiResponse(responseCode = "404", description = "Comanda não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não aberta, sem linhas (COMANDA_EMPTY), com linha a cobrar (COMANDA_HAS_OPEN_ITEMS) ou com sessão não recolhida (SESSION_NOT_COLLECTED)", content = @Content)
    })
    @PostMapping("/{id}/finish")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> finishComanda(@PathVariable("id") Long comandaId,
            Authentication authentication) {
        Comanda comanda = comandaUseCase.finishComanda(comandaId, authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_FINISHED, authentication.getName(),
                auditPayload(comandaId, "orderId", comanda.orderId(), "customerId", comanda.customerId())));
        ComandaResponseDTO dto = comandaConverter.toResponse(comanda);
        enrichCustomerNames(List.of(dto));
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Percentual da taxa de serviço vigente",
            description = "PDV-F015 — para a tela mostrar quanto será cobrado ANTES de fechar, e "
                    + "para o cliente poder recusar com o número na mão. A taxa é aplicada por "
                    + "padrão: sem esta consulta, a única forma de saber o valor seria fechar a "
                    + "conta, que é tarde demais. Zero significa que a casa não cobra taxa.")
    @GetMapping("/service-fee")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<ServiceFeeResponseDTO> getServiceFee() {
        return ResponseEntity.ok(new ServiceFeeResponseDTO(comandaUseCase.getServiceFeePercent()));
    }

    @Operation(summary = "Troca o rótulo da mesa (PDV-F016)",
            description = "O cliente mudou de lugar no salão. Nada de físico acontece: itens, "
                    + "depósito e sessão de origem seguem os mesmos, e nenhum estoque se move. "
                    + "Antes disto o rótulo era imutável e trocar de mesa só era possível "
                    + "cancelando a comanda — o que devolvia tudo ao estoque — e relançando item a "
                    + "item.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Renomeada", content = @Content(schema = @Schema(implementation = ComandaResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Comanda não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não está aberta, ou o caixa de origem está fechado", content = @Content)
    })
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> renameComanda(@PathVariable("id") Long comandaId,
            @Valid @RequestBody RenameComandaRequest request, Authentication authentication) {
        Comanda comanda = comandaUseCase.renameComanda(comandaId, request.getTableOrCustomerLabel(),
                authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_RENAMED, authentication.getName(),
                auditPayload(comandaId, "tableOrCustomerLabel", comanda.tableOrCustomerLabel())));
        ComandaResponseDTO dto = comandaConverter.toResponse(comanda);
        enrichCustomerNames(List.of(dto));
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Vincula, troca ou remove o cliente da mesa aberta (PDV-F020)",
            description = "customerId para cliente existente; lead para cadastrar na hora "
                    + "(find-or-create por CPF/telefone, exige CRM_LEAD_CREATE); os dois nulos "
                    + "desvinculam. O cliente vale para o que ainda não foi cobrado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vinculado", content = @Content(schema = @Schema(implementation = ComandaResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Comanda ou cliente não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não está aberta", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/{id}/customer")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> linkCustomer(@PathVariable("id") Long comandaId,
            @Valid @RequestBody LinkComandaCustomerRequest request, Authentication authentication) {
        Long customerId = resolveComandaCustomer(request.getCustomerId(), request.getLead(), authentication);
        Comanda comanda = comandaUseCase.linkCustomer(comandaId, customerId, authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_CUSTOMER_LINKED, authentication.getName(),
                auditPayload(comandaId, "customerId", customerId)));
        ComandaResponseDTO dto = comandaConverter.toResponse(comanda);
        enrichCustomerNames(List.of(dto));
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Junta esta mesa em outra, que passa a ter a conta inteira (PDV-F016)",
            description = "As linhas em aberto desta comanda passam para a de destino e esta é "
                    + "encerrada. **Nenhum estoque se move**: a mercadoria não voltou para a "
                    + "prateleira nem saiu de novo, ela mudou de conta. Os ids das linhas são "
                    + "preservados, então um OPEN_ROSH e as TROCA dele chegam juntos e ainda "
                    + "ligados. As duas mesas precisam estar ABERTAS e no mesmo depósito. "
                    + "PDV-F031: origem com parte já cobrada também junta — vão as linhas em aberto "
                    + "e toda sessão de narguilé ainda no salão (paga ou não, com os utensílios); "
                    + "os pedidos pagos ficam na origem, que termina FECHADA. Sem nada cobrado, a "
                    + "origem termina CANCELADA, sem a devolução que POST /cancel faria.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Juntadas — devolve a comanda de destino", content = @Content(schema = @Schema(implementation = ComandaResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Comanda de origem ou destino não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Alguma das duas não está aberta, depósitos diferentes ou mesma comanda (COMANDA_MERGE_NOT_ALLOWED)", content = @Content)
    })
    @PostMapping("/{id}/merge-into/{targetId}")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> mergeComanda(@PathVariable("id") Long comandaId,
            @PathVariable("targetId") Long targetComandaId, Authentication authentication) {
        Comanda destino = comandaUseCase.mergeComanda(comandaId, targetComandaId, authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_MERGED, authentication.getName(),
                auditPayload(comandaId,
                        "targetComandaId", targetComandaId,
                        "targetLabel", destino.tableOrCustomerLabel(),
                        "itemCount", destino.items().size())));
        ComandaResponseDTO dto = comandaConverter.toResponse(destino);
        enrichCustomerNames(List.of(dto));
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Abandona a comanda sem cobrança, devolvendo ao estoque cada item já lançado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelada", content = @Content(schema = @Schema(implementation = ComandaResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Comanda não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não está aberta, ou já teve linha cobrada (COMANDA_PARTIALLY_CLOSED, PDV-C021): remova as linhas abertas e use POST /finish", content = @Content)
    })
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> cancelComanda(@PathVariable("id") Long comandaId,
            @RequestBody(required = false) @Valid ComandaCancelRequest request, Authentication authentication) {
        String reason = request == null ? null : request.getReason();
        Comanda comanda = comandaUseCase.cancelComanda(comandaId, authentication.getName(), reason);
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_CANCELLED, authentication.getName(),
                auditPayload(comandaId,
                        "warehouseCode", comanda.warehouseCode(),
                        "type", MovementType.ENTRADA.name(),
                        "itemCount", comanda.items().size(),
                        "reason", reason)));
        return ResponseEntity.ok(comandaConverter.toResponse(comanda));
    }
}
