package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.CampaignDTOConverter;
import com.cernecommerce.adapter.in.converter.CashbackDTOConverter;
import com.cernecommerce.adapter.in.converter.ChannelStatusDTOConverter;
import com.cernecommerce.adapter.in.converter.CustomerCsvConverter;
import com.cernecommerce.adapter.in.converter.CustomerDTOConverter;
import com.cernecommerce.adapter.in.converter.CustomerNoteDTOConverter;
import com.cernecommerce.adapter.in.converter.StageTransitionDTOConverter;
import com.cernecommerce.adapter.in.converter.TagDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.AssociateTagRequest;
import com.cernecommerce.adapter.in.dtos.request.CampaignActiveRequest;
import com.cernecommerce.adapter.in.dtos.request.CampaignAutomationRequest;
import com.cernecommerce.adapter.in.dtos.request.CustomerNoteRequest;
import com.cernecommerce.adapter.in.dtos.request.CustomerRequest;
import com.cernecommerce.adapter.in.dtos.request.CustomerStageRequest;
import com.cernecommerce.adapter.in.dtos.request.TagRequest;
import com.cernecommerce.adapter.in.dtos.request.UpdateCampaignAutomationRequest;
import com.cernecommerce.adapter.in.dtos.response.CampaignAutomationResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CampaignLogResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.WebhookTestResultResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ChannelStatusResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CrmDashboardResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CashbackEntryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CustomerNoteResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CustomerMatchResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CustomerResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StageTransitionResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.TagResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.TagSummaryResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;
import com.cernecommerce.core.domain.model.crm.CrmDashboardOverview;
import com.cernecommerce.core.domain.model.crm.WebhookTestResult;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerNote;
import com.cernecommerce.core.domain.model.crm.LeadResolution;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.infra.handler.CustomerConflictError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Cadastro de clientes do domínio <b>crm</b>. Fundação do módulo CRM.
 */
@RestController
@RequestMapping("/crm")
@io.swagger.v3.oas.annotations.tags.Tag(name = "CRM", description = "Cadastro e consulta de clientes")
@SecurityRequirement(name = "bearerAuth")
public class CrmController {

    private final CrmUseCase crmUseCase;
    private final CashbackUseCase cashbackUseCase;
    private final ReceivableUseCase receivableUseCase;
    private final CustomerDTOConverter converter;
    private final CustomerNoteDTOConverter noteConverter;
    private final StageTransitionDTOConverter stageConverter;
    private final TagDTOConverter tagConverter;
    private final CustomerCsvConverter csvConverter;
    private final CampaignDTOConverter campaignConverter;
    private final ChannelStatusDTOConverter channelStatusConverter;
    private final CashbackDTOConverter cashbackConverter;
    private final ApplicationEventPublisher publisher;

    public CrmController(CrmUseCase crmUseCase, CashbackUseCase cashbackUseCase, ReceivableUseCase receivableUseCase,
            CustomerDTOConverter converter,
            CustomerNoteDTOConverter noteConverter, StageTransitionDTOConverter stageConverter,
            TagDTOConverter tagConverter, CustomerCsvConverter csvConverter,
            CampaignDTOConverter campaignConverter, ChannelStatusDTOConverter channelStatusConverter,
            CashbackDTOConverter cashbackConverter, ApplicationEventPublisher publisher) {
        this.crmUseCase = crmUseCase;
        this.cashbackUseCase = cashbackUseCase;
        this.receivableUseCase = receivableUseCase;
        this.converter = converter;
        this.noteConverter = noteConverter;
        this.stageConverter = stageConverter;
        this.tagConverter = tagConverter;
        this.csvConverter = csvConverter;
        this.campaignConverter = campaignConverter;
        this.channelStatusConverter = channelStatusConverter;
        this.cashbackConverter = cashbackConverter;
        this.publisher = publisher;
    }

    @Operation(summary = "Cria um cliente")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criado", content = @Content(schema = @Schema(implementation = CustomerResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Telefone, email ou CPF já cadastrados — "
                    + "CUSTOMER_ALREADY_EXISTS com matchedBy e customerId",
                    content = @Content(schema = @Schema(implementation = CustomerConflictError.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/customers")
    @PreAuthorize("hasAnyAuthority('CRM_CUSTOMER_MANAGE', 'CRM_LEAD_CREATE')")
    public ResponseEntity<CustomerResponseDTO> createCustomer(@Valid @RequestBody CustomerRequest request,
            Authentication authentication) {
        Customer created = crmUseCase.createCustomer(request.getNome(), request.getContato(), request.getEmail(),
                request.getCpf(), request.getOrigem());
        publisher.publishEvent(AuditEvent.of(EventType.CUSTOMER_CREATED,
                authentication.getName(), Map.of("customerId", String.valueOf(created.id()))));
        return ResponseEntity.created(URI.create("/crm/customers/" + created.id()))
                .body(converter.toResponse(created));
    }

    @Operation(summary = "Atualiza os dados cadastrais de um cliente (CRM-C006)",
            description = "Nome, contato, email, CPF e origem. CPF aceita máscara e é gravado só com "
                    + "dígitos; campo vazio vira nulo. O estágio muda só por PATCH /estagio.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atualizado", content = @Content(schema = @Schema(implementation = CustomerResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Email ou CPF de outro cliente", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PutMapping("/customers/{id}")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_MANAGE')")
    public ResponseEntity<CustomerResponseDTO> updateCustomer(@PathVariable Long id,
            @Valid @RequestBody CustomerRequest request, Authentication authentication) {
        Customer updated = crmUseCase.updateCustomer(id, request.getNome(), request.getContato(),
                request.getEmail(), request.getCpf(), request.getOrigem());
        publisher.publishEvent(AuditEvent.of(EventType.CUSTOMER_UPDATED,
                authentication.getName(), Map.of("customerId", String.valueOf(id))));
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Cadastra ou reaproveita o lead do balcão/mesa (PDV-F020)",
            description = "Find-or-create: procura por CPF, depois telefone (só dígitos), depois email. "
                    + "Achando, devolve o cadastro existente (200) completando o que faltava (ex.: CPF); "
                    + "não achando, cria em NOVO_LEAD (201). Chamado ao concluir a 1ª etapa da venda.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lead criado", content = @Content(schema = @Schema(implementation = CustomerResponseDTO.class))),
            @ApiResponse(responseCode = "200", description = "Cliente existente reaproveitado", content = @Content(schema = @Schema(implementation = CustomerResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "409", description = "CPF/email de outro cliente", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/customers/lead")
    @PreAuthorize("hasAnyAuthority('CRM_CUSTOMER_MANAGE', 'CRM_LEAD_CREATE')")
    public ResponseEntity<CustomerResponseDTO> resolveLead(@Valid @RequestBody CustomerRequest request,
            Authentication authentication) {
        LeadResolution resolution = crmUseCase.resolveLead(request.getNome(), request.getContato(),
                request.getEmail(), request.getCpf(), request.getOrigem());
        Customer customer = resolution.customer();
        if (!resolution.created()) {
            return ResponseEntity.ok(converter.toResponse(customer));
        }
        publisher.publishEvent(AuditEvent.of(EventType.CUSTOMER_CREATED,
                authentication.getName(), Map.of("customerId", String.valueOf(customer.id()))));
        return ResponseEntity.created(URI.create("/crm/customers/" + customer.id()))
                .body(converter.toResponse(customer));
    }

    @Operation(summary = "Busca um cliente por CPF, email ou contato — o \"CPF na nota?\" do balcão",
            description = "Informe exatamente um critério. Prioridade quando mais de um vier "
                    + "preenchido: cpf (identificador oficial) → email → contato. Não achando, o "
                    + "operador segue para o cadastro rápido (POST /crm/customers) — venda anônima "
                    + "continua sempre permitida sem passar por aqui.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Nenhum critério informado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/customers/lookup")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_LOOKUP')")
    public ResponseEntity<CustomerResponseDTO> lookupCustomer(
            @RequestParam(required = false) String cpf,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String contato) {
        Customer customer = crmUseCase.lookupCustomer(cpf, email, contato);
        return ResponseEntity.ok(converter.toResponse(customer));
    }

    @Operation(summary = "Clientes que já usam este telefone, email ou CPF (CRM-C007)",
            description = "Para o PDV barrar o cadastro duplicado e oferecer \"Selecionar este "
                    + "cliente\". Critérios opcionais, ao menos um: telefone comparado só pelos "
                    + "dígitos, email aparado e sem diferenciar maiúsculas, CPF com ou sem máscara. "
                    + "Devolve TODOS os clientes que batem em qualquer critério, cada um com "
                    + "matchedBy; [] quando ninguém bate.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK — lista, possivelmente vazia"),
            @ApiResponse(responseCode = "400", description = "Nenhum critério informado ou CPF inválido",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/customers/lookup/contact")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_LOOKUP')")
    public ResponseEntity<List<CustomerMatchResponseDTO>> lookupCustomersByContact(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String cpf) {
        return ResponseEntity.ok(crmUseCase.lookupCustomers(phone, email, cpf).stream()
                .map(converter::toMatchResponse)
                .toList());
    }

    @Operation(summary = "Busca um cliente por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/customers/{id}")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_READ')")
    public ResponseEntity<CustomerResponseDTO> getCustomer(@PathVariable Long id) {
        Customer customer = crmUseCase.findCustomerById(id);
        List<String> tagNomes = crmUseCase.listCustomerTags(id).stream()
                .map(com.cernecommerce.core.domain.model.crm.Tag::nome).toList();
        CustomerResponseDTO dto = converter.toResponse(customer, tagNomes);
        // CRM-F003: só a busca por id paga a consulta de saldo — a listagem paginada mantém o
        // placeholder de propósito, para não virar um N+1 de saldo por linha da página.
        dto.setCashback(cashbackUseCase.getCustomerBalance(id).available());
        // CRM-F010 — saldo do "Marcar" na ficha.
        ReceivableUseCase.CustomerBalance balance = receivableUseCase.balance(id);
        dto.setCreditLimit(balance.creditLimit());
        dto.setOpenBalance(balance.openBalance());
        dto.setOverdueBalance(balance.overdueBalance());
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Lista clientes paginados, com filtro opcional por nome ou contato")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/customers")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_READ')")
    public ResponseEntity<PageResult<CustomerResponseDTO>> listCustomers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<Customer> result = crmUseCase.listCustomers(search, page, Math.min(size, 100));
        PageResult<CustomerResponseDTO> response = new PageResult<>(
                result.content().stream().map(converter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Exporta a listagem de clientes em CSV — não paginado, mesmo filtro de listCustomers")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "text/csv")),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/customers/export")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_EXPORT')")
    public ResponseEntity<byte[]> exportCustomersCsv(@RequestParam(required = false) String search,
            Authentication authentication) {
        List<Customer> customers = crmUseCase.listCustomersForExport(search);
        String csv = csvConverter.toCsv(customers);
        byte[] body = withUtf8Bom(csv);
        publisher.publishEvent(AuditEvent.of(EventType.CUSTOMER_LIST_EXPORTED,
                authentication.getName(), Map.of("search", search == null ? "" : search, "count", String.valueOf(customers.size()))));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("clientes.csv", StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    /** Prefixo BOM UTF-8 — evita acentos quebrados ao abrir o CSV no Excel. */
    private byte[] withUtf8Bom(String csv) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write(0xEF);
            out.write(0xBB);
            out.write(0xBF);
            out.write(csv.getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Operation(summary = "Cria uma nota para o cliente")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criada", content = @Content(schema = @Schema(implementation = CustomerNoteResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/customers/{id}/notes")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_MANAGE')")
    public ResponseEntity<CustomerNoteResponseDTO> addNote(@PathVariable Long id,
            @Valid @RequestBody CustomerNoteRequest request, Authentication authentication) {
        CustomerNote created = crmUseCase.addNote(id, authentication.getName(), request.getTexto());
        publisher.publishEvent(AuditEvent.of(EventType.CUSTOMER_NOTE_ADDED,
                authentication.getName(), Map.of("customerId", String.valueOf(id))));
        return ResponseEntity.created(URI.create("/crm/customers/" + id + "/notes/" + created.id()))
                .body(noteConverter.toResponse(created));
    }

    @Operation(summary = "Lista as notas de um cliente, mais recentes primeiro")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/customers/{id}/notes")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_READ')")
    public ResponseEntity<List<CustomerNoteResponseDTO>> listNotes(@PathVariable Long id) {
        List<CustomerNoteResponseDTO> response = crmUseCase.listNotes(id).stream()
                .map(noteConverter::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Histórico de pedidos do cliente — placeholder até o domínio de pedidos existir")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK — sempre lista vazia por enquanto"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/customers/{id}/orders")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_READ')")
    public ResponseEntity<List<Object>> listOrders(@PathVariable Long id) {
        crmUseCase.findCustomerById(id);
        return ResponseEntity.ok(List.of());
    }

    @Operation(summary = "Extrato de cashback do cliente",
            description = "As 100 entradas mais recentes do ledger — EARNED, REDEEMED, REVERSED e "
                    + "EXPIRED. Para extrato paginado completo, use GET /cashback/customers/{id}/entries.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/customers/{id}/cashback")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_READ')")
    public ResponseEntity<List<CashbackEntryResponseDTO>> listCashback(@PathVariable Long id) {
        crmUseCase.findCustomerById(id);
        List<CashbackEntryResponseDTO> response = cashbackUseCase.listCustomerEntries(id, 0, 100)
                .content().stream().map(cashbackConverter::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Move o cliente para um novo estágio no Kanban de atendimento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CustomerResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado", content = @Content),
            @ApiResponse(responseCode = "400", description = "Estágio igual ao atual", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/customers/{id}/estagio")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_MANAGE')")
    public ResponseEntity<CustomerResponseDTO> moveStage(@PathVariable Long id,
            @Valid @RequestBody CustomerStageRequest request, Authentication authentication) {
        Customer updated = crmUseCase.moveStage(id, request.getEstagio(), authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.CUSTOMER_STAGE_CHANGED,
                authentication.getName(),
                Map.of("customerId", String.valueOf(id), "estagio", request.getEstagio().name())));
        List<String> tagNomes = crmUseCase.listCustomerTags(id).stream()
                .map(com.cernecommerce.core.domain.model.crm.Tag::nome).toList();
        return ResponseEntity.ok(converter.toResponse(updated, tagNomes));
    }

    @Operation(summary = "Lista a trilha de transições de estágio de um cliente, mais recentes primeiro")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/customers/{id}/estagio/historico")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_READ')")
    public ResponseEntity<List<StageTransitionResponseDTO>> listStageHistory(@PathVariable Long id) {
        List<StageTransitionResponseDTO> response = crmUseCase.listStageHistory(id).stream()
                .map(stageConverter::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Agrega métricas do CRM para o dashboard overview")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/dashboard/overview")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_READ')")
    public ResponseEntity<CrmDashboardResponseDTO> getDashboardOverview() {
        CrmDashboardOverview overview = crmUseCase.getDashboardOverview();
        CrmDashboardResponseDTO response = new CrmDashboardResponseDTO();
        response.setTotalClientes(overview.totalClientes());
        response.setClientesAtivos(overview.clientesAtivos());
        response.setLtvMedio(overview.ltvMedio());
        response.setDisparosWhatsappMes(overview.disparosWhatsappMes());
        response.setPorSegmento(overview.porSegmento());
        response.setPorEstagio(overview.porEstagio());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cria uma tag")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criada", content = @Content(schema = @Schema(implementation = TagResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Nome já cadastrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/tags")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_MANAGE')")
    public ResponseEntity<TagResponseDTO> createTag(@Valid @RequestBody TagRequest request,
            Authentication authentication) {
        com.cernecommerce.core.domain.model.crm.Tag created = crmUseCase.createTag(request.getNome());
        publisher.publishEvent(AuditEvent.of(EventType.TAG_CREATED,
                authentication.getName(), Map.of("tagId", String.valueOf(created.id()))));
        return ResponseEntity.created(URI.create("/crm/tags/" + created.id()))
                .body(tagConverter.toResponse(created));
    }

    @Operation(summary = "Lista todas as tags com a contagem de clientes associados a cada uma")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/tags")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_READ')")
    public ResponseEntity<List<TagSummaryResponseDTO>> listTags() {
        List<TagSummaryResponseDTO> response = crmUseCase.listTags().stream()
                .map(tagConverter::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Remove uma tag e todas as suas associações")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removida"),
            @ApiResponse(responseCode = "404", description = "Tag não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/tags/{id}")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_MANAGE')")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id, Authentication authentication) {
        crmUseCase.deleteTag(id);
        publisher.publishEvent(AuditEvent.of(EventType.TAG_DELETED,
                authentication.getName(), Map.of("tagId", String.valueOf(id))));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Associa uma tag a um cliente")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Associada"),
            @ApiResponse(responseCode = "404", description = "Cliente ou tag não encontrados", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/customers/{id}/tags")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_MANAGE')")
    public ResponseEntity<Void> addTagToCustomer(@PathVariable Long id,
            @Valid @RequestBody AssociateTagRequest request, Authentication authentication) {
        crmUseCase.addTagToCustomer(id, request.getTagId());
        publisher.publishEvent(AuditEvent.of(EventType.CUSTOMER_TAG_ADDED,
                authentication.getName(),
                Map.of("customerId", String.valueOf(id), "tagId", String.valueOf(request.getTagId()))));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove a associação entre um cliente e uma tag")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removida"),
            @ApiResponse(responseCode = "404", description = "Cliente ou tag não encontrados", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/customers/{id}/tags/{tagId}")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_MANAGE')")
    public ResponseEntity<Void> removeTagFromCustomer(@PathVariable Long id, @PathVariable Long tagId,
            Authentication authentication) {
        crmUseCase.removeTagFromCustomer(id, tagId);
        publisher.publishEvent(AuditEvent.of(EventType.CUSTOMER_TAG_REMOVED,
                authentication.getName(),
                Map.of("customerId", String.valueOf(id), "tagId", String.valueOf(tagId))));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lista as tags associadas a um cliente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/customers/{id}/tags")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_READ')")
    public ResponseEntity<List<TagResponseDTO>> listCustomerTags(@PathVariable Long id) {
        List<TagResponseDTO> response = crmUseCase.listCustomerTags(id).stream()
                .map(tagConverter::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cria uma automação de campanha (ativa por padrão)",
            description = "O segredo de webhookHeaders é cifrado em repouso e nunca volta — só authLast4.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criada", content = @Content(schema = @Schema(implementation = CampaignAutomationResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_AUTOMATION — destino sem o campo que exige, evento ausente...", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/automacoes")
    @PreAuthorize("hasAuthority('AUTOMATION_MANAGE')")
    public ResponseEntity<CampaignAutomationResponseDTO> createAutomation(
            @Valid @RequestBody CampaignAutomationRequest request, Authentication authentication) {
        CampaignAutomation created = crmUseCase.createAutomation(new CrmUseCase.AutomationCommand(
                request.getNome(), request.getGatilho(), request.getEvento(), request.getSegmentoAlvo(),
                request.getCanal(), request.getTemplate(), request.getDestino(), request.getWebhookUrl(),
                request.getWorkflowPath(), request.getWhatsappTemplate(), request.getWhatsappIdioma(),
                request.getAuthTipo(), request.getAuthHeaderNome(), request.getWebhookHeaders(),
                request.getMetadados()));
        publisher.publishEvent(AuditEvent.of(EventType.CAMPAIGN_AUTOMATION_CREATED,
                authentication.getName(), Map.of("automationId", String.valueOf(created.id()))));
        return ResponseEntity.created(URI.create("/crm/automacoes/" + created.id()))
                .body(campaignConverter.toResponse(created));
    }

    @Operation(summary = "Atualiza todos os campos editáveis de uma automação existente",
            description = "webhookHeaders ausente mantém o segredo salvo; {} remove.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CampaignAutomationResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Automação não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PutMapping("/automacoes/{id}")
    @PreAuthorize("hasAuthority('AUTOMATION_MANAGE')")
    public ResponseEntity<CampaignAutomationResponseDTO> updateAutomation(@PathVariable Long id,
            @Valid @RequestBody UpdateCampaignAutomationRequest request, Authentication authentication) {
        CampaignAutomation updated = crmUseCase.updateAutomation(id, new CrmUseCase.AutomationCommand(
                request.getNome(), request.getGatilho(), request.getEvento(), request.getSegmentoAlvo(),
                request.getCanal(), request.getTemplate(), request.getDestino(), request.getWebhookUrl(),
                request.getWorkflowPath(), request.getWhatsappTemplate(), request.getWhatsappIdioma(),
                request.getAuthTipo(), request.getAuthHeaderNome(), request.getWebhookHeaders(),
                request.getMetadados()));
        publisher.publishEvent(AuditEvent.of(EventType.CAMPAIGN_AUTOMATION_UPDATED,
                authentication.getName(), Map.of("automationId", String.valueOf(id))));
        return ResponseEntity.ok(campaignConverter.toResponse(updated));
    }

    @Operation(summary = "Lista todas as automações de campanha")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/automacoes")
    @PreAuthorize("hasAnyAuthority('AUTOMATION_MANAGE', 'CRM_CUSTOMER_READ')")
    public ResponseEntity<List<CampaignAutomationResponseDTO>> listAutomations() {
        List<CampaignAutomationResponseDTO> response = crmUseCase.listAutomations().stream()
                .map(campaignConverter::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Ativa ou desativa uma automação")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Automação não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/automacoes/{id}/ativa")
    @PreAuthorize("hasAuthority('AUTOMATION_MANAGE')")
    public ResponseEntity<CampaignAutomationResponseDTO> setAutomationActive(@PathVariable Long id,
            @Valid @RequestBody CampaignActiveRequest request, Authentication authentication) {
        CampaignAutomation updated = crmUseCase.setAutomationActive(id, request.getAtiva());
        publisher.publishEvent(AuditEvent.of(EventType.CAMPAIGN_AUTOMATION_TOGGLED,
                authentication.getName(),
                Map.of("automationId", String.valueOf(id), "ativa", String.valueOf(request.getAtiva()))));
        return ResponseEntity.ok(campaignConverter.toResponse(updated));
    }

    @Operation(summary = "Remove uma automação e seu log de disparos")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removida"),
            @ApiResponse(responseCode = "404", description = "Automação não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/automacoes/{id}")
    @PreAuthorize("hasAuthority('AUTOMATION_MANAGE')")
    public ResponseEntity<Void> deleteAutomation(@PathVariable Long id, Authentication authentication) {
        crmUseCase.deleteAutomation(id);
        publisher.publishEvent(AuditEvent.of(EventType.CAMPAIGN_AUTOMATION_DELETED,
                authentication.getName(), Map.of("automationId", String.valueOf(id))));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Dispara uma automação manualmente — cria log por cliente-alvo; envia de verdade um "
            + "POST ao webhook quando configurado, senão mantém o log em PENDENTE_INTEGRACAO (ver F008)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Automação não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/automacoes/{id}/disparar")
    @PreAuthorize("hasAuthority('AUTOMATION_MANAGE')")
    public ResponseEntity<List<CampaignLogResponseDTO>> dispatchAutomation(@PathVariable Long id,
            Authentication authentication) {
        List<CampaignLogEntry> entries = crmUseCase.dispatchAutomation(id);
        publisher.publishEvent(AuditEvent.of(EventType.CAMPAIGN_AUTOMATION_DISPATCHED,
                authentication.getName(),
                Map.of("automationId", String.valueOf(id), "clientesAlvo", String.valueOf(entries.size()))));
        List<CampaignLogResponseDTO> response = entries.stream().map(campaignConverter::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Dispara um payload de teste para o webhook configurado, com um cliente fictício — não "
            + "persiste log")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Automação sem webhookUrl configurado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Automação não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/automacoes/{id}/testar")
    @PreAuthorize("hasAuthority('AUTOMATION_MANAGE')")
    public ResponseEntity<WebhookTestResultResponseDTO> testAutomation(@PathVariable Long id,
            Authentication authentication) {
        WebhookTestResult result = crmUseCase.testAutomation(id);
        publisher.publishEvent(AuditEvent.of(EventType.CAMPAIGN_AUTOMATION_TESTED,
                authentication.getName(), Map.of("automationId", String.valueOf(id))));
        return ResponseEntity.ok(campaignConverter.toResponse(result));
    }

    @Operation(summary = "Lista o log de disparos de uma automação, mais recentes primeiro")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Automação não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/automacoes/{id}/log")
    @PreAuthorize("hasAuthority('AUTOMATION_MANAGE')")
    public ResponseEntity<List<CampaignLogResponseDTO>> listAutomationLog(@PathVariable Long id) {
        List<CampaignLogResponseDTO> response = crmUseCase.listAutomationLog(id).stream()
                .map(campaignConverter::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Status de conexão dos canais de envio (WhatsApp/E-mail) — badge da tela Automações")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/canais/status")
    @PreAuthorize("hasAnyAuthority('CRM_CUSTOMER_READ', 'AUTOMATION_MANAGE')")
    public ResponseEntity<List<ChannelStatusResponseDTO>> getChannelStatus() {
        List<ChannelStatusResponseDTO> response = crmUseCase.getChannelStatus().stream()
                .map(channelStatusConverter::toResponse).toList();
        return ResponseEntity.ok(response);
    }
}
