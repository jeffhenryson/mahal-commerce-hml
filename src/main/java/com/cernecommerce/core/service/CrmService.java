package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException;
import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.exception.crm.CustomerAlreadyExistsException;
import com.cernecommerce.core.domain.exception.crm.DuplicateTagNameException;
import com.cernecommerce.core.domain.exception.crm.InvalidAutomationException;
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

    // Placeholder até os domínios de pedidos/cashback e de campanhas existirem —
    // ver crm/listagem-clientes-rfm e crm/automacoes-campanhas.
    private static final BigDecimal LTV_MEDIO_PLACEHOLDER = BigDecimal.ZERO;
    private static final long DISPAROS_WHATSAPP_PLACEHOLDER = 0L;
    private static final String SEGMENTO_PLACEHOLDER = "NOVO";

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
    public Customer createCustomer(String nome, String contato, String email, String cpf, String origem) {
        // CRM-C005: email e cpf são opcionais agora — só checa duplicidade do que veio preenchido.
        // CRM-C006: normaliza antes (CPF só dígitos, "" vira null) — sem isso "" passava e o 2º
        // cliente leve batia na unique de email.
        String normalizedContato = CustomerIdentifiers.normalizeContato(contato);
        String normalizedEmail = CustomerIdentifiers.normalizeEmail(email);
        String normalizedCpf = CustomerIdentifiers.normalizeCpf(cpf);
        ensureIdentifiersFree(null, normalizedContato, normalizedEmail, normalizedCpf);
        Customer customer = Customer.create(nome.trim(), normalizedContato, normalizedEmail, normalizedCpf,
                CustomerIdentifiers.normalizeContato(origem));
        return customerRepository.save(customer);
    }

    @Override
    @Transactional
    public Customer updateCustomer(Long id, String nome, String contato, String email, String cpf, String origem) {
        Customer current = requireCustomer(id);
        String normalizedContato = CustomerIdentifiers.normalizeContato(contato);
        String normalizedEmail = CustomerIdentifiers.normalizeEmail(email);
        String normalizedCpf = CustomerIdentifiers.normalizeCpf(cpf);
        ensureIdentifiersFree(id, normalizedContato, normalizedEmail, normalizedCpf);
        Customer updated = current.withProfile(nome.trim(), normalizedContato,
                normalizedEmail, normalizedCpf, CustomerIdentifiers.normalizeContato(origem));
        return customerRepository.save(updated);
    }

    @Override
    @Transactional
    public LeadResolution resolveLead(String nome, String contato, String email, String cpf, String origem) {
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
            return new LeadResolution(createCustomer(nome, contato, email, cpf, origem), true);
        }
        return new LeadResolution(completeMissingIdentifiers(existing.get(), contato, normalizedEmail,
                normalizedCpf), false);
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
    @Transactional
    public CustomerNote addNote(Long customerId, String autor, String texto) {
        requireCustomer(customerId);
        return customerNoteRepository.save(CustomerNote.create(customerId, autor, texto));
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
        return new CrmDashboardOverview(
                total,
                customerRepository.countActive(),
                LTV_MEDIO_PLACEHOLDER,
                DISPAROS_WHATSAPP_PLACEHOLDER,
                Map.of(SEGMENTO_PLACEHOLDER, total),
                customerRepository.countByStage());
    }

    @Override
    @Transactional
    public Tag createTag(String nome) {
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
    public void addTagToCustomer(Long customerId, Long tagId) {
        requireCustomer(customerId);
        requireTag(tagId);
        customerTagRepository.associate(customerId, tagId);
    }

    @Override
    @Transactional
    public void removeTagFromCustomer(Long customerId, Long tagId) {
        requireCustomer(customerId);
        requireTag(tagId);
        customerTagRepository.disassociate(customerId, tagId);
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

    private Tag requireTag(Long tagId) {
        return tagRepository.findById(tagId)
                .orElseThrow(() -> new TagNotFoundException(tagId));
    }

    private CampaignAutomation requireAutomation(Long automationId) {
        return campaignAutomationRepository.findById(automationId)
                .orElseThrow(() -> new CampaignAutomationNotFoundException(automationId));
    }
}
