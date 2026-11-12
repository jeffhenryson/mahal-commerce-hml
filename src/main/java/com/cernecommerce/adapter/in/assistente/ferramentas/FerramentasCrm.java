package com.cernecommerce.adapter.in.assistente.ferramentas;

import com.cernecommerce.adapter.in.assistente.AcaoProposta;
import com.cernecommerce.adapter.in.assistente.ExecutorAcao;
import com.cernecommerce.adapter.in.assistente.ExecutorAcao.Execucao;
import com.cernecommerce.adapter.in.assistente.Ferramenta;
import com.cernecommerce.adapter.in.assistente.FerramentaSupport.EntradaInvalida;
import com.cernecommerce.adapter.in.assistente.PiiSanitizer;
import com.cernecommerce.adapter.in.assistente.ProvedorFerramentas;
import com.cernecommerce.adapter.in.assistente.ResultadoFerramenta;
import com.cernecommerce.adapter.in.controller.CrmController;
import com.cernecommerce.adapter.in.dtos.request.AssociateTagRequest;
import com.cernecommerce.adapter.in.dtos.request.CustomerNoteRequest;
import com.cernecommerce.adapter.in.dtos.request.TagRequest;
import com.cernecommerce.adapter.in.dtos.response.AssistenteBlocoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteCampoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteColunaDTO;
import com.cernecommerce.adapter.in.dtos.response.CrmDashboardResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CustomerResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.TagResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.TagSummaryResponseDTO;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.cernecommerce.adapter.in.assistente.FerramentaSupport.*;
import static com.cernecommerce.adapter.in.assistente.ferramentas.FerramentasEstoque.corpo;
import static com.cernecommerce.adapter.in.assistente.ferramentas.FerramentasEstoque.props;
import static com.cernecommerce.adapter.in.assistente.ferramentas.FerramentasEstoque.semPermissao;

/**
 * Clientes (CRM). Ao modelo vão só id, nome e números agregados: CPF, telefone, e-mail, cidade,
 * data de nascimento e foto nunca saem daqui. Para esses dados, o modelo devolve o link do cadastro.
 */
@Component
public class FerramentasCrm implements ProvedorFerramentas {

    static final String CRIAR_TAG = "CRIAR_TAG";
    static final String TAG_CLIENTE = "TAG_CLIENTE";
    static final String NOTA_CLIENTE = "NOTA_CLIENTE";

    private final CrmController crm;

    public FerramentasCrm(CrmController crm) {
        this.crm = crm;
    }

    /** Ficha do cliente no admin (a lista do CRM abre a ficha pelo parâmetro {@code cliente}). */
    static String rotaCliente(long id) {
        return "/app/crm/clientes?cliente=" + id;
    }

    @Override
    public List<Ferramenta> ferramentas() {
        return List.of(
                new Ferramenta("buscar_clientes", "Consultando clientes",
                        "Busca clientes por parte do nome, estágio ou tag. Devolve id, nome, estágio, segmento, "
                                + "tags, pedidos, ticket médio, LTV e última compra. Nunca traz contato nem "
                                + "documentos: para isso, mande o link do cadastro.",
                        schema(props(
                                "busca", str("Parte do nome do cliente"),
                                "clienteId", inteiro("Id do cliente, para ver um só"),
                                "estagio", enumeracao("Estágio no funil", "NOVO_LEAD", "EM_ATENDIMENTO",
                                        "QUALIFICADO", "CLIENTE_ATIVO", "INATIVO"),
                                "tagId", inteiro("Só clientes com esta tag"),
                                "limite", inteiro("Quantos clientes (1 a 20, padrão 10)"))),
                        this::buscarClientes),
                new Ferramenta("listar_tags", "Consultando tags",
                        "Lista as tags de cliente com id, nome e quantos clientes têm cada uma.",
                        schema(Map.of()),
                        in -> listarTags()),
                new Ferramenta("indicadores_crm", "Consultando indicadores do CRM",
                        "Indicadores gerais do CRM: total de clientes, ativos, LTV médio, clientes por "
                                + "estágio e por segmento e cashback em aberto.",
                        schema(Map.of()),
                        in -> indicadores()),
                new Ferramenta("propor_criar_tag", "Preparando tag",
                        "Propõe criar uma tag de cliente. O usuário confirma num cartão.",
                        schema(props("nome", str("Nome da tag")), "nome"),
                        this::proporCriarTag),
                new Ferramenta("propor_tag_cliente", "Preparando tag do cliente",
                        "Propõe adicionar ou remover uma tag de um cliente. Use listar_tags para achar o id "
                                + "da tag. O usuário confirma num cartão.",
                        schema(props(
                                "clienteId", inteiro("Id do cliente"),
                                "tagId", inteiro("Id da tag"),
                                "operacao", enumeracao("O que fazer", "ADICIONAR", "REMOVER")),
                                "clienteId", "tagId", "operacao"),
                        this::proporTagCliente),
                new Ferramenta("propor_nota_cliente", "Preparando observação",
                        "Propõe registrar uma observação na ficha do cliente (as observações não se editam: "
                                + "cada uma é um registro novo). Sem dados pessoais no texto. O usuário confirma "
                                + "num cartão.",
                        schema(props(
                                "clienteId", inteiro("Id do cliente"),
                                "texto", str("Texto da observação")),
                                "clienteId", "texto"),
                        this::proporNota));
    }

    @Override
    public List<ExecutorAcao> executores() {
        return List.of(
                new ExecutorAcao(CRIAR_TAG, this::criarTag),
                new ExecutorAcao(TAG_CLIENTE, this::tagCliente),
                new ExecutorAcao(NOTA_CLIENTE, this::notaCliente));
    }

    // ── Leitura ────────────────────────────────────────────────────────────────

    private ResultadoFerramenta buscarClientes(JsonNode in) {
        Long id = numero(in, "clienteId");
        List<CustomerResponseDTO> clientes;
        long total;
        if (id != null) {
            clientes = List.of(corpo(crm.getCustomer(id)));
            total = 1;
        } else {
            CustomerStage estagio = enumOpcional(in, "estagio", CustomerStage.class);
            Long tagId = numero(in, "tagId");
            PageResult<CustomerResponseDTO> page = corpo(crm.listCustomers(texto(in, "busca", 100),
                    estagio == null ? null : List.of(estagio), null, null, tagId == null ? null : List.of(tagId),
                    "nome", "ASC", 0, limite(in, 10, 20)));
            clientes = page.content();
            total = page.totalElements();
        }
        ArrayNode itens = objeto().arrayNode();
        List<List<String>> linhas = new ArrayList<>();
        for (CustomerResponseDTO c : clientes) {
            ObjectNode cliente = objeto()
                    .put("id", c.getId())
                    .put("nome", c.getNome())
                    .put("estagio", c.getEstagio() == null ? null : c.getEstagio().name())
                    .put("segmento", c.getSegmento())
                    .put("pedidos", c.getPedidosCount())
                    .put("ticketMedio", dinheiro(c.getTicketMedio()))
                    .put("ltv", dinheiro(c.getLtv()))
                    .put("ultimaCompra", c.getUltimaCompraEm() == null ? null : dataHora(c.getUltimaCompraEm()));
            ArrayNode tags = cliente.putArray("tags");
            if (c.getTags() != null) {
                c.getTags().forEach(tags::add);
            }
            itens.add(cliente);
            linhas.add(List.of(String.valueOf(c.getId()), ou(c.getNome()),
                    c.getEstagio() == null ? "—" : c.getEstagio().name(),
                    c.getPedidosCount() == null ? "—" : String.valueOf(c.getPedidosCount()), dinheiro(c.getLtv())));
        }
        ObjectNode saida = objeto().put("total", total);
        saida.set("clientes", itens);
        ResultadoFerramenta r = ResultadoFerramenta.ok(json(saida));
        if (!linhas.isEmpty()) {
            r = r.comBloco(AssistenteBlocoDTO.tabela("Clientes", List.of(AssistenteColunaDTO.de("Id"),
                    AssistenteColunaDTO.de("Nome"), AssistenteColunaDTO.de("Estágio"),
                    AssistenteColunaDTO.sensivel("Pedidos"), AssistenteColunaDTO.sensivel("LTV")), linhas));
        }
        if (clientes.size() == 1) {
            r = r.comLink(link("Ver cliente", rotaCliente(clientes.get(0).getId())));
        }
        return r;
    }

    private ResultadoFerramenta listarTags() {
        List<TagSummaryResponseDTO> tags = corpo(crm.listTags());
        ArrayNode itens = objeto().arrayNode();
        for (TagSummaryResponseDTO t : tags) {
            itens.add(objeto().put("id", t.getId()).put("nome", t.getNome()).put("clientes", t.getClientesCount()));
        }
        ObjectNode saida = objeto();
        saida.set("tags", itens);
        return ResultadoFerramenta.ok(json(saida));
    }

    private ResultadoFerramenta indicadores() {
        CrmDashboardResponseDTO d = corpo(crm.getDashboardOverview());
        ObjectNode saida = objeto()
                .put("totalClientes", d.getTotalClientes())
                .put("clientesAtivos", d.getClientesAtivos())
                .put("ltvMedio", dinheiro(d.getLtvMedio()))
                .put("cashbackEmAberto", dinheiro(d.getCashbackEmAberto()));
        ObjectNode porEstagio = saida.putObject("porEstagio");
        if (d.getPorEstagio() != null) {
            d.getPorEstagio().forEach((k, v) -> porEstagio.put(k.name(), v));
        }
        ObjectNode porSegmento = saida.putObject("porSegmento");
        if (d.getPorSegmento() != null) {
            d.getPorSegmento().forEach(porSegmento::put);
        }
        return ResultadoFerramenta.ok(json(saida))
                .comBloco(AssistenteBlocoDTO.tabela("CRM", List.of(AssistenteColunaDTO.de("Indicador"),
                        AssistenteColunaDTO.sensivel("Valor")), List.of(
                        List.of("Clientes", String.valueOf(d.getTotalClientes())),
                        List.of("Ativos", String.valueOf(d.getClientesAtivos())),
                        List.of("LTV médio", dinheiro(d.getLtvMedio())),
                        List.of("Cashback em aberto", dinheiro(d.getCashbackEmAberto())))))
                .comLink(link("Abrir CRM", "/app/crm/clientes"));
    }

    // ── Propostas ──────────────────────────────────────────────────────────────

    private ResultadoFerramenta proporCriarTag(JsonNode in) {
        if (!temPermissao("CRM_CUSTOMER_MANAGE", "CRM_CUSTOMER_VIP_MANAGE")) {
            return semPermissao("criar tags");
        }
        String nome = textoObrigatorio(in, "nome", 60);
        return ResultadoFerramenta.proposta(new AcaoProposta(CRIAR_TAG, objeto().put("nome", nome).toString(),
                "Criar tag " + nome, List.of(AssistenteCampoDTO.de("Tag", nome))));
    }

    private ResultadoFerramenta proporTagCliente(JsonNode in) {
        if (!temPermissao("CRM_CUSTOMER_MANAGE", "CRM_CUSTOMER_VIP_MANAGE")) {
            return semPermissao("alterar tags de clientes");
        }
        long clienteId = numeroObrigatorio(in, "clienteId");
        long tagId = numeroObrigatorio(in, "tagId");
        String operacao = textoObrigatorio(in, "operacao", 10).toUpperCase();
        if (!operacao.equals("ADICIONAR") && !operacao.equals("REMOVER")) {
            throw new EntradaInvalida("'operacao' deve ser ADICIONAR ou REMOVER");
        }
        CustomerResponseDTO cliente = corpo(crm.getCustomer(clienteId));
        String tag = corpo(crm.listTags()).stream().filter(t -> t.getId() == tagId).map(TagSummaryResponseDTO::getNome)
                .findFirst().orElseThrow(() -> new EntradaInvalida("Tag " + tagId + " não existe"));
        boolean adicionar = operacao.equals("ADICIONAR");
        ObjectNode params = objeto().put("clienteId", clienteId).put("tagId", tagId).put("adicionar", adicionar);
        return ResultadoFerramenta.proposta(new AcaoProposta(TAG_CLIENTE, params.toString(),
                (adicionar ? "Adicionar tag " : "Remover tag ") + tag,
                List.of(AssistenteCampoDTO.de("Cliente", cliente.getNome()), AssistenteCampoDTO.de("Tag", tag),
                        AssistenteCampoDTO.de("Operação", adicionar ? "Adicionar" : "Remover"))));
    }

    private ResultadoFerramenta proporNota(JsonNode in) {
        if (!temPermissao("CRM_CUSTOMER_MANAGE")) {
            return semPermissao("registrar observações de clientes");
        }
        long clienteId = numeroObrigatorio(in, "clienteId");
        // O texto vem do modelo: passa pela mesma limpeza do texto do usuário antes de virar parâmetro.
        String texto = PiiSanitizer.limpar(textoObrigatorio(in, "texto", 2000));
        CustomerResponseDTO cliente = corpo(crm.getCustomer(clienteId));
        ObjectNode params = objeto().put("clienteId", clienteId).put("texto", texto);
        return ResultadoFerramenta.proposta(new AcaoProposta(NOTA_CLIENTE, params.toString(),
                "Registrar observação", List.of(AssistenteCampoDTO.de("Cliente", cliente.getNome()),
                        AssistenteCampoDTO.de("Observação", texto))));
    }

    // ── Execução (no confirmar) ────────────────────────────────────────────────

    private Execucao criarTag(JsonNode p) {
        TagRequest req = new TagRequest();
        req.setNome(p.get("nome").asText());
        TagResponseDTO criada = corpo(crm.createTag(req, autenticacao()));
        return new Execucao("Tag " + criada.getNome() + " criada.", List.of());
    }

    private Execucao tagCliente(JsonNode p) {
        long clienteId = p.get("clienteId").asLong();
        long tagId = p.get("tagId").asLong();
        if (p.get("adicionar").asBoolean()) {
            AssociateTagRequest req = new AssociateTagRequest();
            req.setTagId(tagId);
            crm.addTagToCustomer(clienteId, req, autenticacao());
            return new Execucao("Tag adicionada ao cliente.", List.of(link("Ver cliente", rotaCliente(clienteId))));
        }
        crm.removeTagFromCustomer(clienteId, tagId, autenticacao());
        return new Execucao("Tag removida do cliente.", List.of(link("Ver cliente", rotaCliente(clienteId))));
    }

    private Execucao notaCliente(JsonNode p) {
        long clienteId = p.get("clienteId").asLong();
        CustomerNoteRequest req = new CustomerNoteRequest();
        req.setTexto(p.get("texto").asText());
        crm.addNote(clienteId, req, autenticacao());
        return new Execucao("Observação registrada.", List.of(link("Ver cliente", rotaCliente(clienteId))));
    }
}
