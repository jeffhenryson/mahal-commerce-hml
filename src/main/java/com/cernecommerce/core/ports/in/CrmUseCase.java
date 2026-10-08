package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.AutomationAuthType;
import com.cernecommerce.core.domain.model.crm.AutomationDestination;
import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.AutomationMetadata;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.CampaignChannel;
import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import com.cernecommerce.core.domain.model.crm.ChannelStatus;
import com.cernecommerce.core.domain.model.crm.CrmDashboardOverview;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerMatch;
import com.cernecommerce.core.domain.model.crm.CustomerNote;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.crm.LeadResolution;
import com.cernecommerce.core.domain.model.crm.StageTransition;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.domain.model.crm.TagSummary;
import com.cernecommerce.core.domain.model.crm.WebhookTestResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Port de entrada do domínio <b>crm</b>.
 */
public interface CrmUseCase {

    /**
     * Cria um cliente. {@code contato} e {@code email} são opcionais desde CRM-C005 — só
     * {@code nome} e ao menos um identificador (cpf, email ou contato) são exigidos, checado no
     * compact constructor de {@link Customer}.
     *
     * @throws com.cernecommerce.core.domain.exception.crm.CustomerAlreadyExistsException
     *         se telefone, email ou cpf já estiverem cadastrados (CRM-C007)
     */
    Customer createCustomer(String nome, String contato, String email, String cpf, String origem);

    /**
     * Atualiza os dados cadastrais de um cliente (CRM-C006) — é por aqui que um cliente cadastrado
     * sem CPF ganha o CPF depois. Estágio não muda por aqui. Identificadores passam pela mesma
     * normalização do cadastro ({@link com.cernecommerce.core.domain.model.crm.CustomerIdentifiers}).
     *
     * @throws com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException se não existir
     * @throws com.cernecommerce.core.domain.exception.crm.CustomerAlreadyExistsException
     *         se telefone, email ou cpf pertencerem a outro cliente (CRM-C007)
     */
    Customer updateCustomer(Long id, String nome, String contato, String email, String cpf, String origem);

    /**
     * Find-or-create do lead do balcão e da mesa (PDV-F020): procura por CPF e depois por
     * telefone (só dígitos); achando, reaproveita — e completa o CPF se o cadastro existente não
     * tinha —; não achando, cria em {@code NOVO_LEAD}. Nunca duplica o cliente só porque o
     * operador digitou o telefone com outra máscara.
     *
     * @throws com.cernecommerce.core.domain.exception.crm.CustomerAlreadyExistsException
     *         se achou pelo telefone um cliente com CPF diferente do informado e o CPF informado já
     *         pertence a um terceiro
     */
    LeadResolution resolveLead(String nome, String contato, String email, String cpf, String origem);

    /**
     * Busca um cliente por id. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se não existir.
     */
    Customer findCustomerById(Long id);

    /**
     * Resolve nome de clientes em lote, para enriquecer telas de outro domínio (ex.: pedidos) sem
     * uma consulta por linha. Id sem cliente correspondente simplesmente não aparece no mapa — ao
     * contrário de {@link #findCustomerById}, não é um lookup pontual e não lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}.
     */
    Map<Long, String> findCustomerNames(Collection<Long> customerIds);

    /**
     * Busca pontual por CPF, email ou contato — o "CPF na nota?" do balcão (CRM-F002). Informe
     * exatamente um critério; os demais como {@code null}. Tentados nessa ordem de prioridade
     * quando mais de um vier preenchido: cpf (identificador oficial) → email → contato.
     *
     * @throws IllegalArgumentException se nenhum critério for informado
     * @throws com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException
     *         se não achar ninguém pelo critério informado
     */
    Customer lookupCustomer(String cpf, String email, String contato);

    /**
     * Quem já usa este telefone, email ou CPF (CRM-C007) — o PDV barra o cadastro duplicado e
     * oferece selecionar o existente. Devolve TODOS os clientes que batem em qualquer
     * um dos critérios informados — telefone só pelos dígitos, email aparado e sem diferenciar
     * maiúsculas, CPF com ou sem máscara —, cada um com os campos que bateram. Lista vazia quando
     * ninguém bate. Ordem por id.
     *
     * @throws IllegalArgumentException se nenhum critério for informado ou o CPF não tiver 11 dígitos
     */
    List<CustomerMatch> lookupCustomers(String phone, String email, String cpf);

    /**
     * Lista clientes paginados (mais recentes primeiro), filtrando por nome, contato, email ou CPF
     * quando {@code search} não for nulo/vazio.
     */
    PageResult<Customer> listCustomers(String search, int page, int size);

    /**
     * Lista todos os clientes correspondentes ao filtro, sem paginação — usado para exportação
     * (ex.: CSV).
     */
    List<Customer> listCustomersForExport(String search);

    /**
     * Adiciona uma nota a um cliente. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se o cliente não existir.
     */
    CustomerNote addNote(Long customerId, String autor, String texto);

    /**
     * Lista as notas de um cliente, mais recentes primeiro. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se o cliente não existir.
     */
    List<CustomerNote> listNotes(Long customerId);

    /**
     * Move um cliente para um novo estágio no Kanban de atendimento, registrando a transição.
     * Lança {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se o cliente não existir, ou {@link IllegalArgumentException} se {@code novoEstagio} for
     * igual ao estágio atual.
     */
    Customer moveStage(Long customerId, CustomerStage novoEstagio, String autor);

    /**
     * Lista a trilha de transições de estágio de um cliente, mais recentes primeiro. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se o cliente não existir.
     */
    List<StageTransition> listStageHistory(Long customerId);

    /** Agrega as métricas do CRM para o dashboard overview. */
    CrmDashboardOverview getDashboardOverview();

    /**
     * Cria uma tag. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.DuplicateTagNameException}
     * se o nome já existir.
     */
    Tag createTag(String nome);

    /** Lista todas as tags com a contagem de clientes associados a cada uma. */
    List<TagSummary> listTags();

    /**
     * Remove uma tag e todas as suas associações. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.TagNotFoundException}
     * se não existir.
     */
    void deleteTag(Long tagId);

    /**
     * Associa uma tag a um cliente. Idempotente. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se o cliente não existir, ou
     * {@link com.cernecommerce.core.domain.exception.crm.TagNotFoundException}
     * se a tag não existir.
     */
    void addTagToCustomer(Long customerId, Long tagId);

    /**
     * Remove a associação entre um cliente e uma tag. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se o cliente não existir, ou
     * {@link com.cernecommerce.core.domain.exception.crm.TagNotFoundException}
     * se a tag não existir.
     */
    void removeTagFromCustomer(Long customerId, Long tagId);

    /**
     * Lista as tags associadas a um cliente. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se o cliente não existir.
     */
    List<Tag> listCustomerTags(Long customerId);

    /**
     * Cria uma automação de campanha (ativa por padrão). Lança
     * {@link com.cernecommerce.core.domain.exception.crm.InvalidAutomationException} quando o
     * destino não tem o campo que exige, falta o evento de um gatilho EVENTO, etc.
     */
    CampaignAutomation createAutomation(AutomationCommand command);

    /** Lista todas as automações de campanha. */
    List<CampaignAutomation> listAutomations();

    /**
     * Ativa ou desativa uma automação. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException}
     * se não existir.
     */
    CampaignAutomation setAutomationActive(Long automationId, boolean ativa);

    /**
     * Atualiza todos os campos editáveis de uma automação existente, sem precisar recriá-la.
     * {@code webhookHeaders} {@code null} mantém o segredo salvo; {@code {}} o remove. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException}
     * se não existir.
     */
    CampaignAutomation updateAutomation(Long automationId, AutomationCommand command);

    /**
     * Remove uma automação e seu log de disparos. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException}
     * se não existir.
     */
    void deleteAutomation(Long automationId);

    /**
     * Dispara uma automação manualmente: resolve os clientes do {@code segmentoAlvo} e cria uma
     * {@link CampaignLogEntry} por cliente, entregando pelo destino da automação (status
     * {@code ENVIADO}/{@code FALHA} conforme o resultado). Webhook próprio sem {@code webhookUrl}
     * mantém o comportamento legado — status {@code PENDENTE_INTEGRACAO}, sem envio real. Um
     * cliente-alvo com falha não interrompe o disparo para os demais. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException}
     * se a automação não existir.
     */
    List<CampaignLogEntry> dispatchAutomation(Long automationId);

    /**
     * Dispara um payload de teste pelo destino da automação, com um cliente fictício — não
     * persiste {@link CampaignLogEntry}. Destino WhatsApp não envia (cliente fictício). Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException}
     * se a automação não existir, ou
     * {@link com.cernecommerce.core.domain.exception.crm.AutomationWebhookNotConfiguredException}
     * se o destino não tiver o campo que exige (ex.: {@code webhookUrl}).
     */
    WebhookTestResult testAutomation(Long automationId);

    /**
     * Lista o log de disparos de uma automação, mais recentes primeiro. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException}
     * se a automação não existir.
     */
    List<CampaignLogEntry> listAutomationLog(Long automationId);

    /**
     * Status de conexão dos canais de envio (WhatsApp/E-mail) — o badge da tela de Automações.
     * WhatsApp vem da integração com a Cloud API (mesmo {@code connected} da tela de Integrações).
     */
    List<ChannelStatus> getChannelStatus();

    /**
     * Campos editáveis de uma automação (POST/PUT). {@code webhookHeaders} é o segredo de
     * autenticação do webhook próprio: {@code null} mantém o salvo, {@code {}} remove.
     */
    record AutomationCommand(
            String nome,
            CampaignTrigger gatilho,
            AutomationEvent evento,
            CustomerStage segmentoAlvo,
            CampaignChannel canal,
            String template,
            AutomationDestination destino,
            String webhookUrl,
            String workflowPath,
            String whatsappTemplate,
            String whatsappIdioma,
            AutomationAuthType authTipo,
            String authHeaderNome,
            Map<String, String> webhookHeaders,
            List<AutomationMetadata> metadados) {

        @Override
        public String toString() {
            return "AutomationCommand[nome=" + nome + ", gatilho=" + gatilho + ", evento=" + evento
                    + ", destino=" + destino + ", authTipo=" + authTipo
                    + ", webhookHeaders=" + (webhookHeaders == null ? "null" : "***") + "]";
        }
    }
}
