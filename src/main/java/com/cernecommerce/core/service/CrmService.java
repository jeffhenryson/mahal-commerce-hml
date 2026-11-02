package com.cernecommerce.core.service;

import com.cernecommerce.core.ports.out.crm.CustomerInsightsRepository;
import com.cernecommerce.core.domain.model.crm.CustomerSummary;
import com.cernecommerce.core.domain.model.crm.CustomerFilter;
import com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException;
import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.exception.crm.CustomerAlreadyExistsException;
import com.cernecommerce.core.domain.exception.crm.CustomerUnderageException;
import com.cernecommerce.core.domain.exception.crm.DuplicateTagNameException;
import com.cernecommerce.core.domain.exception.crm.InvalidAutomationException;
import com.cernecommerce.core.domain.exception.crm.TagNotAllowedException;
import com.cernecommerce.core.domain.exception.crm.TagNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.config.WhatsappConnectionStatus;
import com.cernecommerce.core.domain.model.crm.AutomationAuthType;
import com.cernecommerce.core.domain.model.crm.AutomationDelivery;
import com.cernecommerce.core.domain.model.crm.AutomationDestination;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;
import com.cernecommerce.core.domain.model.crm.ChannelStatus;
import com.cernecommerce.core.domain.model.crm.ChannelType;
import com.cernecommerce.core.domain.model.crm.CrmDashboardOverview;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerIdentifiers;
import com.cernecommerce.core.domain.model.crm.CustomerMatch;
import com.cernecommerce.core.domain.model.crm.CustomerMatchField;
import com.cernecommerce.core.domain.model.crm.CustomerNote;
import com.cernecommerce.core.domain.model.crm.LeadResolution;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.crm.StageTransition;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.domain.model.crm.TagSummary;
import com.cernecommerce.core.domain.model.crm.WebhookTestResult;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.ports.in.AutomationDispatchUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.WhatsappIntegrationUseCase;
import com.cernecommerce.core.ports.out.crm.CampaignAutomationRepository;
import com.cernecommerce.core.ports.out.crm.CampaignLogRepository;
import com.cernecommerce.core.ports.out.crm.CustomerNoteRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.crm.CustomerTagRepository;
import com.cernecommerce.core.ports.out.crm.StageTransitionRepository;
import com.cernecommerce.core.ports.out.crm.TagRepository;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class CrmService implements CrmUseCase {

    private static final ZoneId ZONA_LOJA = ZoneId.of("America/Sao_Paulo");
    private static final int IDADE_MINIMA = 18;
    private static final int IDADE_MAXIMA = 120;

    /**
     * Pendência 8 — LTV, cashback, tags e métricas vindos de pedidos, cashback e campanhas. Nulo nos
     * testes antigos: a lista sai sem esses números (nulos, nunca zero fingindo ser dado real).
     */
    private com.cernecommerce.core.ports.out.crm.CustomerInsightsRepository insights;

    /** V183 — storage privado dos anexos de nota. Nulo nos testes antigos: nota com anexo é recusada. */
    private com.cernecommerce.core.ports.out.storage.CustomerNoteAttachmentStoragePort noteAttachmentStorage;

    public void setNoteAttachmentStorage(
            com.cernecommerce.core.ports.out.storage.CustomerNoteAttachmentStoragePort noteAttachmentStorage) {
        this.noteAttachmentStorage = noteAttachmentStorage;
    }

    static final int MAX_ANEXOS = 5;
    static final long MAX_ANEXO_BYTES = 5L * 1024 * 1024;
    /** Extensão → content type servido. O tipo que o navegador declarou não é confiável e é ignorado. */
    static final Map<String, String> ANEXO_TIPOS = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("gif", "image/gif"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

    /** Ligado pelo {@code CoreBeanConfig}. Setter para não multiplicar construtores. */
    public void setInsights(com.cernecommerce.core.ports.out.crm.CustomerInsightsRepository insights) {
        this.insights = insights;
    }

    // Provedor exibido no badge de canais quando o WhatsApp está conectado.
    private static final String WHATSAPP_PROVIDER = "META_CLOUD_API";

    private final CustomerRepository customerRepository;
    private final CustomerNoteRepository customerNoteRepository;
    private final StageTransitionRepository stageTransitionRepository;
    private final TagRepository tagRepository;
    private final CustomerTagRepository customerTagRepository;
    private final CampaignAutomationRepository campaignAutomationRepository;
    private final CampaignLogRepository campaignLogRepository;
    private final EmailPort emailPort;
    private final AutomationDispatchUseCase automationDispatch;
    private final WhatsappIntegrationUseCase whatsappIntegration;

    public CrmService(CustomerRepository customerRepository, CustomerNoteRepository customerNoteRepository,
            StageTransitionRepository stageTransitionRepository, TagRepository tagRepository,
            CustomerTagRepository customerTagRepository, CampaignAutomationRepository campaignAutomationRepository,
            CampaignLogRepository campaignLogRepository, EmailPort emailPort,
            AutomationDispatchUseCase automationDispatch, WhatsappIntegrationUseCase whatsappIntegration) {
        this.customerRepository = customerRepository;
        this.customerNoteRepository = customerNoteRepository;
        this.stageTransitionRepository = stageTransitionRepository;
        this.tagRepository = tagRepository;
        this.customerTagRepository = customerTagRepository;
        this.campaignAutomationRepository = campaignAutomationRepository;
        this.campaignLogRepository = campaignLogRepository;
        this.emailPort = emailPort;
        this.automationDispatch = automationDispatch;
        this.whatsappIntegration = whatsappIntegration;
    }

    @Override
    @Transactional
    public Customer createCustomer(String nome, String contato, String email, String cpf, String origem,
            LocalDate dataNascimento, CustomerExtras extras) {
        validateDataNascimento(dataNascimento);
        // CRM-C005: email e cpf são opcionais agora — só checa duplicidade do que veio preenchido.
        // CRM-C006: normaliza antes (CPF só dígitos, "" vira null) — sem isso "" passava e o 2º
        // cliente leve batia na unique de email.
        String normalizedContato = CustomerIdentifiers.normalizeContato(contato);
        String normalizedEmail = CustomerIdentifiers.normalizeEmail(email);
        String normalizedCpf = CustomerIdentifiers.normalizeCpf(cpf);
        ensureIdentifiersFree(null, normalizedContato, normalizedEmail, normalizedCpf);
        Customer customer = Customer.create(nome.trim(), normalizedContato, normalizedEmail, normalizedCpf,
                CustomerIdentifiers.normalizeContato(origem)).withDataNascimento(dataNascimento);
        if (extras != null) {
            customer = customer.withCidadeEAvatar(extras.cidade(), extras.avatarUrl());
        }
        return customerRepository.save(customer);
    }

    @Override
    @Transactional
    public Customer updateCustomer(Long id, String nome, String contato, String email, String cpf, String origem,
            LocalDate dataNascimento, CustomerExtras extras) {
        validateDataNascimento(dataNascimento);
        Customer current = requireCustomer(id);
        String normalizedContato = CustomerIdentifiers.normalizeContato(contato);
        String normalizedEmail = CustomerIdentifiers.normalizeEmail(email);
        String normalizedCpf = CustomerIdentifiers.normalizeCpf(cpf);
        ensureIdentifiersFree(id, normalizedContato, normalizedEmail, normalizedCpf);
        Customer updated = current.withProfile(nome.trim(), normalizedContato,
                normalizedEmail, normalizedCpf, CustomerIdentifiers.normalizeContato(origem));
        if (dataNascimento != null) {
            updated = updated.withDataNascimento(dataNascimento);
        }
        // V183 — nulo mantém o gravado, vazio apaga (withCidadeEAvatar transforma vazio em nulo).
        if (extras != null && (extras.cidade() != null || extras.avatarUrl() != null)) {
            updated = updated.withCidadeEAvatar(extras.cidade() != null ? extras.cidade() : updated.cidade(),
                    extras.avatarUrl() != null ? extras.avatarUrl() : updated.avatarUrl());
        }
        return customerRepository.save(updated);
    }

    @Override
    @Transactional
    public LeadResolution resolveLead(String nome, String contato, String email, String cpf, String origem,
            LocalDate dataNascimento) {
        validateDataNascimento(dataNascimento);
        String normalizedCpf = CustomerIdentifiers.normalizeCpf(cpf);
        String normalizedEmail = CustomerIdentifiers.normalizeEmail(email);
        Optional<Customer> existing = Optional.empty();
        if (normalizedCpf != null) {
            existing = customerRepository.findByCpf(normalizedCpf);
        }
        if (existing.isEmpty() && CustomerIdentifiers.digitsOrNull(contato) != null) {
            existing = customerRepository.findByContato(contato);
        }
        if (existing.isEmpty() && normalizedEmail != null) {
            existing = customerRepository.findByEmail(normalizedEmail);
        }
        if (existing.isEmpty()) {
            return new LeadResolution(createCustomer(nome, contato, email, cpf, origem, dataNascimento,
                    CustomerExtras.NONE), true);
        }
        Customer completed = completeMissingIdentifiers(existing.get(), contato, normalizedEmail, normalizedCpf);
        // CRM-F012 — mesma regra dos identificadores: completa o que faltava, nunca sobrescreve.
        if (dataNascimento != null && completed.dataNascimento() == null) {
            completed = customerRepository.save(completed.withDataNascimento(dataNascimento));
        }
        return new LeadResolution(completed, false);
    }

    /**
     * CRM-F012 — 18 a 120 anos, contados no fuso da loja. Fica aqui, e não no record: a regra é do
     * cadastro, e um cliente lido do banco não pode virar inválido só porque o tempo passou.
     */
    private static void validateDataNascimento(LocalDate dataNascimento) {
        if (dataNascimento == null) {
            return;
        }
        LocalDate hoje = LocalDate.now(ZONA_LOJA);
        if (!dataNascimento.isBefore(hoje)) {
            throw new IllegalArgumentException("dataNascimento precisa estar no passado");
        }
        int idade = Period.between(dataNascimento, hoje).getYears();
        if (idade < IDADE_MINIMA) {
            throw new CustomerUnderageException(idade);
        }
        if (idade > IDADE_MAXIMA) {
            throw new IllegalArgumentException("dataNascimento implica mais de " + IDADE_MAXIMA + " anos");
        }
    }

    /**
     * Reaproveitou um cadastro: só preenche o que faltava (CPF, email, telefone) — nunca sobrescreve
     * nome nem um identificador que já existia, para um lançamento rápido no balcão não desfazer o
     * cadastro cuidadoso feito no CRM.
     */
    private Customer completeMissingIdentifiers(Customer customer, String contato, String email, String cpf) {
        boolean missingCpf = !customer.isOfficiallyRegistered() && cpf != null;
        boolean missingEmail = (customer.email() == null || customer.email().isBlank()) && email != null;
        String normalizedContato = CustomerIdentifiers.normalizeContato(contato);
        boolean missingContato = (customer.contato() == null || customer.contato().isBlank())
                && normalizedContato != null;
        if (!missingCpf && !missingEmail && !missingContato) {
            return customer;
        }
        String newCpf = missingCpf ? cpf : customer.cpf();
        String newEmail = missingEmail ? email : customer.email();
        // Telefone fica fora da checagem aqui: completar o contato de um lead não deve travar o
        // balcão porque o número já aparece em outro cadastro (telefone compartilhado).
        ensureIdentifiersFree(customer.id(), null, missingEmail ? newEmail : null, missingCpf ? newCpf : null);
        return customerRepository.save(customer.withProfile(customer.nome(),
                missingContato ? normalizedContato : customer.contato(), newEmail, newCpf, customer.origem()));
    }

    /**
     * Telefone, email ou CPF já usados por OUTRO cliente viram 409 (CRM-C007); {@code selfId} é o
     * próprio cliente em edição. Havendo mais de um, aponta o que bateu pelo identificador mais
     * forte (CPF → email → telefone).
     */
    private void ensureIdentifiersFree(Long selfId, String contato, String email, String cpf) {
        findMatches(contato, email, cpf).stream()
                .filter(m -> !m.customer().id().equals(selfId))
                .min(Comparator.comparingInt(CrmService::matchStrength))
                .ifPresent(m -> {
                    throw new CustomerAlreadyExistsException(m.matchedBy(), m.customer().id());
                });
    }

    private static int matchStrength(CustomerMatch match) {
        if (match.matchedBy().contains(CustomerMatchField.CPF)) {
            return 0;
        }
        return match.matchedBy().contains(CustomerMatchField.EMAIL) ? 1 : 2;
    }

    /** Junta por cliente o que bateu em cada identificador informado; ordem por id. */
    private List<CustomerMatch> findMatches(String contato, String email, String cpf) {
        Map<Long, Customer> customers = new LinkedHashMap<>();
        Map<Long, EnumSet<CustomerMatchField>> fields = new LinkedHashMap<>();
        BiConsumer<Customer, CustomerMatchField> add = (c, field) -> {
            customers.putIfAbsent(c.id(), c);
            fields.computeIfAbsent(c.id(), k -> EnumSet.noneOf(CustomerMatchField.class)).add(field);
        };
        if (CustomerIdentifiers.digitsOrNull(contato) != null) {
            customerRepository.findAllByContato(contato).forEach(c -> add.accept(c, CustomerMatchField.PHONE));
        }
        if (CustomerIdentifiers.normalizeEmailForMatch(email) != null) {
            customerRepository.findAllByEmailIgnoreCase(email).forEach(c -> add.accept(c, CustomerMatchField.EMAIL));
        }
        if (cpf != null) {
            customerRepository.findByCpf(cpf).ifPresent(c -> add.accept(c, CustomerMatchField.CPF));
        }
        return customers.values().stream()
                .sorted(Comparator.comparing(Customer::id))
                .map(c -> new CustomerMatch(c, fields.get(c.id())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Customer findCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findCustomerNames(Collection<Long> customerIds) {
        return customerRepository.findByIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::id, Customer::nome));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Customer> findCustomersByIds(Collection<Long> customerIds) {
        return customerIds == null || customerIds.isEmpty() ? Map.of()
                : customerRepository.findByIds(customerIds).stream()
                        .collect(Collectors.toMap(Customer::id, c -> c, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public Customer lookupCustomer(String cpf, String email, String contato) {
        // Ordem de prioridade quando mais de um critério vier preenchido: cpf (oficial) → email →
        // contato — o mais forte primeiro, para não devolver o cliente errado por coincidência de
        // telefone quando o CPF, mais específico, também foi informado.
        if (cpf != null && !cpf.isBlank()) {
            // CRM-C006: aceita CPF com máscara — gravado só com dígitos.
            String digits = CustomerIdentifiers.normalizeCpf(cpf);
            return customerRepository.findByCpf(digits)
                    .orElseThrow(() -> new CustomerNotFoundException("cpf " + digits));
        }
        if (email != null && !email.isBlank()) {
            return customerRepository.findByEmail(email)
                    .orElseThrow(() -> new CustomerNotFoundException("email " + email));
        }
        if (contato != null && !contato.isBlank()) {
            return customerRepository.findByContato(contato)
                    .orElseThrow(() -> new CustomerNotFoundException("contato " + contato));
        }
        throw new IllegalArgumentException("informe cpf, email ou contato para a busca");
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerMatch> lookupCustomers(String phone, String email, String cpf) {
        String normalizedCpf = CustomerIdentifiers.normalizeCpf(cpf);
        if (CustomerIdentifiers.digitsOrNull(phone) == null
                && CustomerIdentifiers.normalizeEmailForMatch(email) == null
                && normalizedCpf == null) {
            throw new IllegalArgumentException("informe phone, email ou cpf para a busca");
        }
        return findMatches(phone, email, normalizedCpf);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Customer> listCustomers(String search, int page, int size) {
        return customerRepository.findAll(search, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> listCustomersForExport(String search) {
        return customerRepository.findAllForExport(search);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CustomerSummary> listCustomerSummaries(CustomerFilter filter, int page, int size) {
        PageResult<Customer> result = customerRepository.findAll(filter, page, size);
        return new PageResult<>(summarize(result.content()), result.page(), result.size(), result.totalElements(),
                result.totalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> listCustomersForExport(CustomerFilter filter) {
        return customerRepository.findAllForExport(filter);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerSummary getCustomerSummary(Long id) {
        return summarize(List.of(requireCustomer(id))).get(0);
    }

    @Override
    @Transactional
    public Customer setSegmento(Long id, String segmento) {
        Customer customer = requireCustomer(id);
        String normalized = segmento == null || segmento.isBlank() ? null : segmento.trim();
        if (normalized != null && !Customer.SEGMENTOS.contains(normalized)) {
            throw new IllegalArgumentException("segmento inválido: " + normalized);
        }
        Customer saved = customerRepository.save(customer.withSegmentoManual(normalized));
        if (normalized == null && insights != null) {
            insights.recalculateSegments(id, Instant.now());
            return requireCustomer(id);
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Set<Long> findVipCustomerIds(Collection<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return java.util.Set.of();
        }
        if (insights != null) {
            return insights.tagNamesFor(customerIds).entrySet().stream()
                    .filter(e -> e.getValue().stream().anyMatch(Tag::isVipName))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }
        // Sem o repositório em lote (testes antigos): uma consulta por cliente.
        return customerIds.stream().filter(java.util.Objects::nonNull)
                .filter(id -> customerTagRepository.findTagsByCustomerId(id).stream()
                        .anyMatch(t -> Tag.isVipName(t.nome())))
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public int recalculateSegments() {
        return insights == null ? 0 : insights.recalculateSegments(null, Instant.now());
    }

    /**
     * Pendência 8 — os números da página em lote: uma consulta para pedidos, uma para cashback e uma
     * para tags, qualquer que seja o tamanho da página. Sem o repositório (testes antigos), os números
     * ficam nulos e as tags vazias.
     */
    private List<CustomerSummary> summarize(List<Customer> customers) {
        if (customers.isEmpty()) {
            return List.of();
        }
        if (insights == null) {
            return customers.stream()
                    .map(c -> new CustomerSummary(c, List.of(), null, 0, null, null, null))
                    .toList();
        }
        List<Long> ids = customers.stream().map(Customer::id).toList();
        Map<Long, CustomerInsightsRepository.CustomerStats> stats = insights.statsFor(ids);
        Map<Long, BigDecimal> cashback = insights.cashbackAvailableFor(ids, Instant.now());
        Map<Long, List<String>> tags = insights.tagNamesFor(ids);
        return customers.stream().map(c -> {
            CustomerInsightsRepository.CustomerStats s = stats.get(c.id());
            return new CustomerSummary(c, tags.getOrDefault(c.id(), List.of()),
                    s == null ? BigDecimal.ZERO.setScale(2) : s.ltv(),
                    s == null ? 0 : s.pedidos(),
                    s == null ? BigDecimal.ZERO.setScale(2) : s.ticketMedio(),
                    s == null ? null : s.ultimaCompraEm(),
                    cashback.getOrDefault(c.id(), BigDecimal.ZERO.setScale(2)));
        }).toList();
    }

    @Override
    @Transactional
    public CustomerNote addNote(Long customerId, String autor, String texto) {
        requireCustomer(customerId);
        return customerNoteRepository.save(CustomerNote.create(customerId, autor, texto));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CustomerNote> listNotes(Long customerId, int page, int size) {
        requireCustomer(customerId);
        return customerNoteRepository.findByCustomerId(customerId, page, size);
    }

    @Override
    @Transactional
    public CustomerNote addNote(Long customerId, String autor, String texto, List<NewNoteAttachment> anexos) {
        requireCustomer(customerId);
        List<NewNoteAttachment> files = anexos == null ? List.of() : anexos;
        // Tudo validado antes de gravar qualquer coisa: um arquivo recusado não deixa nota pela metade.
        if (files.size() > MAX_ANEXOS) {
            throw new com.cernecommerce.core.domain.exception.crm.InvalidNoteAttachmentException(
                    "No máximo " + MAX_ANEXOS + " anexos por nota");
        }
        List<String> extensions = new java.util.ArrayList<>();
        for (NewNoteAttachment file : files) {
            extensions.add(validateAttachment(file));
        }
        if (!files.isEmpty() && noteAttachmentStorage == null) {
            throw new IllegalStateException("Storage de anexos de nota não configurado");
        }
        CustomerNote note = customerNoteRepository.save(CustomerNote.create(customerId, autor, texto));
        List<com.cernecommerce.core.domain.model.crm.CustomerNoteAttachment> saved = new java.util.ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            NewNoteAttachment file = files.get(i);
            String ext = extensions.get(i);
            String key = noteAttachmentStorage.save(file.bytes(), ext);
            saved.add(customerNoteRepository.saveAttachment(new com.cernecommerce.core.domain.model.crm.CustomerNoteAttachment(
                    null, note.id(), safeName(file.nomeArquivo()), ANEXO_TIPOS.get(ext), file.bytes().length, key,
                    Instant.now())));
        }
        return note.withAnexos(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public NoteAttachmentContent loadNoteAttachment(Long customerId, Long noteId, Long attachmentId) {
        requireCustomer(customerId);
        boolean noteOfCustomer = customerNoteRepository.findById(noteId)
                .filter(n -> customerId.equals(n.customerId())).isPresent();
        var attachment = customerNoteRepository.findAttachment(attachmentId)
                .filter(a -> noteOfCustomer && noteId.equals(a.noteId()))
                .orElseThrow(() -> new com.cernecommerce.core.domain.exception.crm.CustomerNoteAttachmentNotFoundException(
                        attachmentId));
        java.io.InputStream stream = noteAttachmentStorage == null ? null
                : noteAttachmentStorage.load(attachment.storageKey()).orElse(null);
        if (stream == null) {
            throw new com.cernecommerce.core.domain.exception.crm.CustomerNoteAttachmentNotFoundException(attachmentId);
        }
        return new NoteAttachmentContent(attachment, stream);
    }

    /** Devolve a extensão aceita, ou recusa o arquivo. */
    private static String validateAttachment(NewNoteAttachment file) {
        String name = file.nomeArquivo() == null ? "" : file.nomeArquivo();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ANEXO_TIPOS.containsKey(ext)) {
            throw new com.cernecommerce.core.domain.exception.crm.InvalidNoteAttachmentException(
                    "Tipo de arquivo não aceito: " + (ext.isEmpty() ? "sem extensão" : "." + ext)
                            + ". Use PDF, imagem, TXT, CSV, DOC/DOCX ou XLS/XLSX.");
        }
        if (file.bytes() == null || file.bytes().length == 0) {
            throw new com.cernecommerce.core.domain.exception.crm.InvalidNoteAttachmentException(
                    "Arquivo vazio: " + name);
        }
        if (file.bytes().length > MAX_ANEXO_BYTES) {
            throw new com.cernecommerce.core.domain.exception.crm.InvalidNoteAttachmentException(
                    "Arquivo acima de 5 MB: " + name);
        }
        return ext;
    }

    /** Só o nome, sem caminho (navegador antigo manda "C:\\fakepath\\x.pdf"), até 255 caracteres. */
    private static String safeName(String raw) {
        String name = raw == null ? "anexo" : raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) {
            name = "anexo";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerNote> listNotes(Long customerId) {
        requireCustomer(customerId);
        return customerNoteRepository.findByCustomerId(customerId);
    }

    @Override
    @Transactional
    public Customer moveStage(Long customerId, CustomerStage novoEstagio, String autor) {
        Customer customer = requireCustomer(customerId);
        stageTransitionRepository.save(StageTransition.create(customerId, customer.estagio(), novoEstagio, autor));
        return customerRepository.save(customer.withEstagio(novoEstagio));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StageTransition> listStageHistory(Long customerId) {
        requireCustomer(customerId);
        return stageTransitionRepository.findByCustomerId(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public CrmDashboardOverview getDashboardOverview() {
        long total = customerRepository.countAll();
        if (insights == null) {
            return new CrmDashboardOverview(total, customerRepository.countActive(), BigDecimal.ZERO, 0L, Map.of(),
                    customerRepository.countByStage());
        }
        // Mês corrente no fuso da loja: "disparos do mês" é o mês que o dono vê no calendário.
        Instant now = Instant.now();
        java.time.ZonedDateTime inicioMes = LocalDate.now(ZONA_LOJA).withDayOfMonth(1).atStartOfDay(ZONA_LOJA);
        return new CrmDashboardOverview(
                total,
                customerRepository.countActive(),
                insights.averageLtv(),
                insights.whatsappDispatchesBetween(inicioMes.toInstant(), inicioMes.plusMonths(1).toInstant()),
                insights.countBySegmento(),
                customerRepository.countByStage(),
                insights.cashbackAvailableTotal(now));
    }

    @Override
    @Transactional
    public Tag createTag(String nome, boolean onlyVip) {
        if (onlyVip && !Tag.isVipName(nome)) {
            throw new TagNotAllowedException(nome);
        }
        tagRepository.findByNome(nome).ifPresent(t -> {
            throw new DuplicateTagNameException(nome);
        });
        return tagRepository.save(Tag.create(nome));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagSummary> listTags() {
        return tagRepository.findAllWithCustomerCount();
    }

    @Override
    @Transactional
    public void deleteTag(Long tagId) {
        requireTag(tagId);
        tagRepository.deleteById(tagId);
    }

    @Override
    @Transactional
    public void addTagToCustomer(Long customerId, Long tagId, boolean onlyVip) {
        requireCustomer(customerId);
        requireAllowedTag(tagId, onlyVip);
        customerTagRepository.associate(customerId, tagId);
        // A tag VIP define o segmento VIP: recalcula este cliente agora, sem esperar o job.
        if (insights != null) {
            insights.recalculateSegments(customerId, Instant.now());
        }
    }

    @Override
    @Transactional
    public void removeTagFromCustomer(Long customerId, Long tagId, boolean onlyVip) {
        requireCustomer(customerId);
        requireAllowedTag(tagId, onlyVip);
        customerTagRepository.disassociate(customerId, tagId);
        if (insights != null) {
            insights.recalculateSegments(customerId, Instant.now());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tag> listCustomerTags(Long customerId) {
        requireCustomer(customerId);
        return customerTagRepository.findTagsByCustomerId(customerId);
    }

    @Override
    @Transactional
    public CampaignAutomation createAutomation(AutomationCommand command) {
        CampaignAutomation created = buildAutomation(null, true, Instant.now(), null, command);
        return campaignAutomationRepository.save(created);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignAutomation> listAutomations() {
        return campaignAutomationRepository.findAll();
    }

    @Override
    @Transactional
    public CampaignAutomation setAutomationActive(Long automationId, boolean ativa) {
        CampaignAutomation automation = requireAutomation(automationId);
        return campaignAutomationRepository.save(automation.withAtiva(ativa));
    }

    @Override
    @Transactional
    public CampaignAutomation updateAutomation(Long automationId, AutomationCommand command) {
        CampaignAutomation current = requireAutomation(automationId);
        return campaignAutomationRepository.save(
                buildAutomation(current.id(), current.ativa(), current.criadoEm(), current.entrega(), command));
    }

    @Override
    @Transactional
    public void deleteAutomation(Long automationId) {
        requireAutomation(automationId);
        campaignAutomationRepository.deleteById(automationId);
    }

    @Override
    @Transactional
    public List<CampaignLogEntry> dispatchAutomation(Long automationId) {
        return automationDispatch.dispatchManual(requireAutomation(automationId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignLogEntry> listAutomationLog(Long automationId) {
        requireAutomation(automationId);
        return campaignLogRepository.findByAutomationId(automationId);
    }

    @Override
    @Transactional(readOnly = true)
    public WebhookTestResult testAutomation(Long automationId) {
        return automationDispatch.test(requireAutomation(automationId));
    }

    /**
     * Monta e valida a automação do comando. O segredo ({@code webhookHeaders}) segue a regra das
     * integrações: ausente mantém o de {@code current}, {@code {}} remove. Fora do destino WEBHOOK
     * não há autenticação própria — o segredo é descartado.
     */
    private static CampaignAutomation buildAutomation(Long id, boolean ativa, Instant criadoEm,
            AutomationDelivery current, AutomationCommand command) {
        AutomationDestination destino = command.destino() == null ? AutomationDestination.WEBHOOK : command.destino();
        AutomationAuthType authTipo = destino == AutomationDestination.WEBHOOK && command.authTipo() != null
                ? command.authTipo() : AutomationAuthType.NONE;
        CampaignAutomation automation;
        try {
            AutomationDelivery entrega = new AutomationDelivery(destino, command.webhookUrl(), command.workflowPath(),
                    command.whatsappTemplate(), command.whatsappIdioma(), authTipo, command.authHeaderNome(),
                    current == null ? null : current.authLast4(),
                    current == null ? Map.of() : current.webhookHeaders())
                    .withSecret(command.webhookHeaders());
            automation = new CampaignAutomation(id, command.nome(), command.gatilho(), command.evento(),
                    command.segmentoAlvo(), command.canal(), command.template(), ativa, criadoEm, entrega,
                    command.metadados());
        } catch (IllegalArgumentException ex) {
            throw new InvalidAutomationException(ex.getMessage());
        }
        AutomationDelivery entrega = automation.entrega();
        String missing = entrega.missingRequirement();
        if (missing != null) {
            throw new InvalidAutomationException(missing);
        }
        if (entrega.webhookUrl() != null && !isHttpUrl(entrega.webhookUrl())) {
            throw new InvalidAutomationException("webhookUrl precisa começar com http:// ou https://");
        }
        if (entrega.workflowPath() != null && entrega.workflowPath().contains("://")) {
            throw new InvalidAutomationException("workflowPath é o caminho depois da URL base da plataforma, não uma URL");
        }
        if (authTipo == AutomationAuthType.HEADER && entrega.authHeaderNome() == null) {
            throw new InvalidAutomationException("authHeaderNome é obrigatório quando authTipo é HEADER");
        }
        if (authTipo != AutomationAuthType.NONE && entrega.webhookHeaders().isEmpty()) {
            throw new InvalidAutomationException("Informe o segredo de autenticação do webhook");
        }
        return automation;
    }

    private static boolean isHttpUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChannelStatus> getChannelStatus() {
        EmailChannelStatus email = emailPort.channelStatus();
        WhatsappConnectionStatus whatsapp = whatsappIntegration.connectionStatus();
        return List.of(
                ChannelStatus.of(ChannelType.EMAIL, email.conectado(), email.provedor(), email.detalhe()),
                ChannelStatus.of(ChannelType.WHATSAPP, whatsapp.connected(),
                        whatsapp.connected() ? WHATSAPP_PROVIDER : null, whatsapp.detail()));
    }

    private Customer requireCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    /** PDV-F046 — a tag existe e, para quem só gerencia VIP, é a VIP. */
    private Tag requireAllowedTag(Long tagId, boolean onlyVip) {
        Tag tag = requireTag(tagId);
        if (onlyVip && !tag.isVip()) {
            throw new TagNotAllowedException(tag.nome());
        }
        return tag;
    }

    private Tag requireTag(Long tagId) {
        return tagRepository.findById(tagId)
                .orElseThrow(() -> new TagNotFoundException(tagId));
    }

    private CampaignAutomation requireAutomation(Long automationId) {
        return campaignAutomationRepository.findById(automationId)
                .orElseThrow(() -> new CampaignAutomationNotFoundException(automationId));
    }
}
