package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.ProductRequest;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEntry;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEvent;
import com.cernecommerce.core.domain.model.estoque.ProductRequestNotifyChannel;
import com.cernecommerce.core.domain.model.estoque.ProductRequestSort;
import com.cernecommerce.core.domain.model.estoque.ProductRequestStats;
import com.cernecommerce.core.domain.model.estoque.ProductRequestStatus;
import com.cernecommerce.core.domain.model.estoque.ProductRequestUrgency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Pedidos de clientes (Estoque › Pedidos de clientes): produtos que a loja não vende e que clientes pediram.
 * Ver {@code frontend-admin/Docs/PROMPT_BACKEND_PEDIDOS_CLIENTES.md}.
 *
 * <p>Quem registra no balcão ({@code ESTOQUE_PRODUCT_REQUEST_CREATE}) só cria, junta e consulta parecidos;
 * o resto exige {@code ESTOQUE_PRODUCT_REQUEST_MANAGE}. Essa separação fica no controller.</p>
 */
public interface ProductRequestUseCase {

    /** Variáveis de template que o evento {@code PEDIDO_PRODUTO_CHEGOU} resolve, além de {@code cliente.*}. */
    List<String> AUTOMATION_VARIABLES = List.of("loja.nome", "produto.nome", "produto.marca", "produto.variacao",
            "pedidoProduto.quantidade");

    PageResult<View> list(Set<ProductRequestStatus> statuses, String q, Long categoryId, ProductRequestSort sort,
            SortDirection direction, int page, int size);

    Dashboard summary();

    /** @throws com.cernecommerce.core.domain.exception.estoque.ProductRequestNotFoundException */
    Detail get(Long id);

    /** Até 5 itens abertos parecidos. Item já vinculado a {@code sku} vem primeiro. */
    List<Similar> similar(String description, String brand, String variation, String sku);

    Detail create(CreateCommand command, String actor);

    /** Junta uma solicitação. O mesmo cliente com solicitação ativa soma a quantidade. */
    Detail addEntry(Long id, EntryCommand command, String actor);

    Detail update(Long id, PatchCommand command, String actor);

    /** Muda o status. Indo para {@code ARRIVED}, dispara a automação para as solicitações elegíveis. */
    Detail changeStatus(Long id, StatusCommand command, String actor);

    Detail updateEntry(Long id, Long entryId, EntryPatchCommand command, String actor);

    /** Cancela a solicitação (não apaga). O item não fecha sozinho se não sobrar nenhuma ativa. */
    Detail cancelEntry(Long id, Long entryId, String actor);

    /** Vincula o SKU do catálogo; {@code null} desvincula. */
    Detail linkSku(Long id, String sku, String actor);

    NotifyResult notify(Long id, Collection<Long> entryIds, ProductRequestNotifyChannel channel, String actor);

    /** Itens em que o cliente tem solicitação (perfil do cliente e PDV). */
    List<View> byCustomer(Long customerId);

    /**
     * Gatilho opcional (seção 7): houve entrada de estoque nesses SKUs. Itens {@code ORDERED} vinculados a eles
     * vão para {@code ARRIVED} com o ator {@code "sistema"} e disparam o aviso.
     *
     * @return quantos itens mudaram
     */
    int arriveByStockEntry(Collection<String> skus);

    // ── Comandos ─────────────────────────────────────────────────────────────

    record CreateCommand(String description, String brand, String variation, Long categoryId, String categoryOther,
            EntryCommand entry) {
    }

    record EntryCommand(Long customerId, String customerName, String customerPhone, int quantity,
            BigDecimal maxPrice, ProductRequestUrgency urgency, LocalDate neededBy, String note) {
    }

    /**
     * PATCH do item: {@code null} = não muda. Nos textos opcionais (marca, variação, outra categoria, nota do
     * fornecedor), {@code ""} limpa. {@code clearExpectedAt} limpa a previsão.
     */
    record PatchCommand(String description, String brand, String variation, Long categoryId, String categoryOther,
            LocalDate expectedAt, boolean clearExpectedAt, String supplierNote) {
    }

    record StatusCommand(ProductRequestStatus status, LocalDate expectedAt, String supplierNote, String reason,
            boolean withoutSku) {
    }

    /** PATCH da solicitação: {@code null} = não muda; {@code note: ""} limpa a nota. */
    record EntryPatchCommand(Integer quantity, BigDecimal maxPrice, ProductRequestUrgency urgency,
            LocalDate neededBy, String note) {
    }

    // ── Leitura ──────────────────────────────────────────────────────────────

    /** Item com agregados e rótulos resolvidos (nome da categoria ou o "outro", nome do produto vinculado). */
    record View(ProductRequest request, ProductRequestStats stats, String categoryName, String linkedProductName) {
    }

    /**
     * @param customerName  do CRM (anonimizado vira "Cliente excluído") ou o do avulso
     * @param customerPhone só dígitos, sem máscara: quem mascara é o controller, conforme a permissão
     */
    record EntryView(ProductRequestEntry entry, String customerName, String customerPhone, String createdByName) {
    }

    record EventView(ProductRequestEvent event, String actorName) {
    }

    record Detail(View view, String createdByName, List<EntryView> entries, List<EventView> events) {
    }

    record Similar(ProductRequest request, int entriesCount, double similarity) {
    }

    record Top(Long id, String description, int entriesCount) {
    }

    record Dashboard(long pending, long ordered, long arrived, long awaitingNotification, List<Top> topRequested) {
    }

    record NotifyResult(int notified, int skippedNoConsent, boolean skippedNoAutomation, Detail request) {
    }
}
