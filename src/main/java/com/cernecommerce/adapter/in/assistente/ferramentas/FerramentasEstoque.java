package com.cernecommerce.adapter.in.assistente.ferramentas;

import com.cernecommerce.adapter.in.assistente.AcaoProposta;
import com.cernecommerce.adapter.in.assistente.ExecutorAcao;
import com.cernecommerce.adapter.in.assistente.ExecutorAcao.Execucao;
import com.cernecommerce.adapter.in.assistente.Ferramenta;
import com.cernecommerce.adapter.in.assistente.FerramentaSupport.EntradaInvalida;
import com.cernecommerce.adapter.in.assistente.ProvedorFerramentas;
import com.cernecommerce.adapter.in.assistente.ResultadoFerramenta;
import com.cernecommerce.adapter.in.controller.EstoqueController;
import com.cernecommerce.adapter.in.dtos.request.BrandRequest;
import com.cernecommerce.adapter.in.dtos.request.CategoryRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductPatchRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductRequest;
import com.cernecommerce.adapter.in.dtos.response.AssistenteBlocoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteCampoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteColunaDTO;
import com.cernecommerce.adapter.in.dtos.response.BrandResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CategoryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.EstoqueSummaryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockBalanceResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.WarehouseResponseDTO;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.MeasurementUnit;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.cernecommerce.adapter.in.assistente.FerramentaSupport.*;

/**
 * Catálogo e estoque. Leitura: produtos, saldo e resumo. Escrita (só proposta): produto como
 * rascunho, dados cadastrais de produto (nunca preço), categoria e marca.
 *
 * <p>Custo e margem não vão ao modelo: o preço de venda basta para as perguntas do dia a dia.</p>
 */
@Component
public class FerramentasEstoque implements ProvedorFerramentas {

    static final String CRIAR_PRODUTO = "CRIAR_PRODUTO";
    static final String EDITAR_PRODUTO = "EDITAR_PRODUTO";
    static final String CRIAR_CATEGORIA = "CRIAR_CATEGORIA";
    static final String CRIAR_MARCA = "CRIAR_MARCA";

    private static final String UNIDADES = "UN, CX, KG, G, L, ML, PCT ou DZ";

    private final EstoqueController estoque;

    public FerramentasEstoque(EstoqueController estoque) {
        this.estoque = estoque;
    }

    @Override
    public List<Ferramenta> ferramentas() {
        return List.of(
                new Ferramenta("buscar_produtos", "Consultando produtos",
                        "Busca produtos do catálogo por parte do nome ou SKU, categoria, marca ou status. "
                                + "Devolve SKU, nome, categoria, marca, status e preço de venda.",
                        schema(props(
                                "busca", str("Parte do nome ou do SKU"),
                                "categoria", str("Nome da categoria"),
                                "marca", str("Nome da marca"),
                                "status", enumeracao("Status de publicação", "RASCUNHO", "ATIVO", "ARQUIVADO"),
                                "sku", str("SKU exato, para ver um produto só"),
                                "limite", inteiro("Quantos produtos (1 a 20, padrão 10)"))),
                        this::buscarProdutos),
                new Ferramenta("consultar_estoque", "Consultando estoque",
                        "Saldo em estoque (quantidade, reservada e disponível) por depósito. Com SKU, o saldo "
                                + "dele em cada depósito; sem SKU, os saldos de um depósito.",
                        schema(props(
                                "sku", str("SKU do produto ou variação"),
                                "deposito", str("Código do depósito; sem ele, o primeiro ativo"),
                                "limite", inteiro("Quantos saldos listar sem SKU (1 a 30, padrão 15)"))),
                        this::consultarEstoque),
                new Ferramenta("resumo_estoque", "Consultando resumo do estoque",
                        "Números gerais do catálogo: total de produtos e variações, valor do estoque a custo, "
                                + "alertas de estoque baixo e a categoria com mais produtos.",
                        schema(Map.of()),
                        in -> resumoEstoque()),
                new Ferramenta("propor_criar_produto", "Preparando produto",
                        "Propõe criar um produto como RASCUNHO, sem preço e sem estoque. Não cria nada: o "
                                + "usuário confirma num cartão. Categoria e marca precisam existir.",
                        schema(props(
                                "sku", str("SKU novo, 3 a 50 caracteres"),
                                "nome", str("Nome do produto"),
                                "categoria", str("Nome de uma categoria existente"),
                                "marca", str("Nome de uma marca existente"),
                                "descricao", str("Descrição"),
                                "unidade", enumeracao("Unidade de medida: " + UNIDADES,
                                        "UN", "CX", "KG", "G", "L", "ML", "PCT", "DZ"),
                                "codigoBarras", str("Código de barras")),
                                "sku", "nome"),
                        this::proporCriarProduto),
                new Ferramenta("propor_editar_produto", "Preparando alteração",
                        "Propõe alterar dados cadastrais de um produto: nome, descrição, categoria, marca, "
                                + "unidade, código de barras e visibilidade no PDV e no app. Nunca preço. Não "
                                + "altera nada: o usuário confirma num cartão.",
                        schema(props(
                                "sku", str("SKU do produto"),
                                "nome", str("Nome novo"),
                                "descricao", str("Descrição nova"),
                                "categoria", str("Nome de uma categoria existente"),
                                "marca", str("Nome de uma marca existente"),
                                "unidade", enumeracao("Unidade de medida: " + UNIDADES,
                                        "UN", "CX", "KG", "G", "L", "ML", "PCT", "DZ"),
                                "codigoBarras", str("Código de barras novo"),
                                "visivelPdv", bool("Aparece no PDV"),
                                "visivelApp", bool("Aparece no app (marketplace)")),
                                "sku"),
                        this::proporEditarProduto),
                new Ferramenta("propor_criar_categoria", "Preparando categoria",
                        "Propõe criar uma categoria de produtos. O usuário confirma num cartão.",
                        schema(props("nome", str("Nome da categoria")), "nome"),
                        in -> proporNome(in, CRIAR_CATEGORIA, "Criar categoria", "Categoria",
                                "ESTOQUE_CATEGORY_MANAGE")),
                new Ferramenta("propor_criar_marca", "Preparando marca",
                        "Propõe criar uma marca. O usuário confirma num cartão.",
                        schema(props("nome", str("Nome da marca")), "nome"),
                        in -> proporNome(in, CRIAR_MARCA, "Criar marca", "Marca", "ESTOQUE_BRAND_MANAGE")));
    }

    @Override
    public List<ExecutorAcao> executores() {
        return List.of(
                new ExecutorAcao(CRIAR_PRODUTO, this::criarProduto),
                new ExecutorAcao(EDITAR_PRODUTO, this::editarProduto),
                new ExecutorAcao(CRIAR_CATEGORIA, this::criarCategoria),
                new ExecutorAcao(CRIAR_MARCA, this::criarMarca));
    }

    // ── Leitura ────────────────────────────────────────────────────────────────

    private ResultadoFerramenta buscarProdutos(JsonNode in) {
        String sku = texto(in, "sku", 50);
        List<ProductResponseDTO> produtos;
        long total;
        if (sku != null) {
            produtos = List.of(corpo(estoque.getProduct(sku)));
            total = 1;
        } else {
            PageResult<ProductResponseDTO> page = corpo(estoque.listProducts(0, limite(in, 10, 20),
                    texto(in, "busca", 100), texto(in, "categoria", 100), texto(in, "marca", 100), null, null, null,
                    enumOpcional(in, "status", ProductStatus.class), null, null, null,
                    ProductSortField.NAME, SortDirection.ASC));
            produtos = page.content();
            total = page.totalElements();
        }
        ArrayNode itens = objeto().arrayNode();
        List<List<String>> linhas = new ArrayList<>();
        for (ProductResponseDTO p : produtos) {
            String preco = p.getPricing() == null ? null : dinheiro(p.getPricing().getEffectivePrice());
            itens.add(objeto()
                    .put("sku", p.getSku())
                    .put("nome", p.getName())
                    .put("categoria", p.getCategory())
                    .put("marca", p.getBrand())
                    .put("status", p.getStatus())
                    .put("ativo", p.isActive())
                    .put("unidade", p.getUnit())
                    .put("precoVenda", preco)
                    .put("variacoes", p.getVariants() == null ? 0 : p.getVariants().size()));
            linhas.add(List.of(p.getSku(), ou(p.getName()), ou(p.getCategory()), ou(p.getBrand()),
                    preco == null ? "—" : preco, ou(p.getStatus())));
        }
        ObjectNode saida = objeto().put("total", total);
        saida.set("produtos", itens);
        ResultadoFerramenta r = ResultadoFerramenta.ok(json(saida));
        if (!linhas.isEmpty()) {
            r = r.comBloco(AssistenteBlocoDTO.tabela("Produtos", List.of(AssistenteColunaDTO.de("SKU"),
                    AssistenteColunaDTO.de("Produto"), AssistenteColunaDTO.de("Categoria"),
                    AssistenteColunaDTO.de("Marca"), AssistenteColunaDTO.sensivel("Preço"),
                    AssistenteColunaDTO.de("Status")), linhas));
        }
        if (produtos.size() == 1) {
            r = r.comLink(link("Ver produto", "/app/estoque/produtos/" + produtos.get(0).getSku() + "/editar"));
        }
        return r;
    }

    private ResultadoFerramenta consultarEstoque(JsonNode in) {
        String sku = texto(in, "sku", 50);
        String deposito = texto(in, "deposito", 50);
        List<WarehouseResponseDTO> depositos = corpo(estoque.listWarehouses(0, 20)).content().stream()
                .filter(WarehouseResponseDTO::isActive)
                .filter(w -> deposito == null || w.getCode().equalsIgnoreCase(deposito))
                .toList();
        if (depositos.isEmpty()) {
            return ResultadoFerramenta.falha(deposito == null ? "Nenhum depósito ativo." : "Depósito não encontrado: " + deposito);
        }
        List<StockBalanceResponseDTO> saldos = new ArrayList<>();
        if (sku != null) {
            for (WarehouseResponseDTO w : depositos.subList(0, Math.min(5, depositos.size()))) {
                try {
                    saldos.add((StockBalanceResponseDTO) Objects.requireNonNull(
                            estoque.getStockBalance(sku, w.getCode(), 0, 1).getBody()));
                } catch (RuntimeException ex) {
                    // SKU sem saldo neste depósito.
                }
            }
        } else {
            @SuppressWarnings("unchecked")
            PageResult<StockBalanceResponseDTO> page = (PageResult<StockBalanceResponseDTO>) Objects.requireNonNull(
                    estoque.getStockBalance(null, depositos.get(0).getCode(), 0, limite(in, 15, 30)).getBody());
            saldos.addAll(page.content());
        }
        ArrayNode itens = objeto().arrayNode();
        List<List<String>> linhas = new ArrayList<>();
        for (StockBalanceResponseDTO s : saldos) {
            itens.add(objeto()
                    .put("sku", s.getSku())
                    .put("deposito", s.getWarehouseCode())
                    .put("quantidade", quantidade(s.getQuantity()))
                    .put("reservada", quantidade(s.getReservedQuantity()))
                    .put("disponivel", quantidade(s.getAvailableQuantity()))
                    .put("unidade", s.getUnit()));
            linhas.add(List.of(s.getSku(), s.getWarehouseCode(), quantidade(s.getQuantity()),
                    quantidade(s.getReservedQuantity()), quantidade(s.getAvailableQuantity())));
        }
        ObjectNode saida = objeto();
        saida.set("saldos", itens);
        ResultadoFerramenta r = ResultadoFerramenta.ok(json(saida));
        if (!linhas.isEmpty()) {
            r = r.comBloco(AssistenteBlocoDTO.tabela("Estoque", List.of(AssistenteColunaDTO.de("SKU"),
                    AssistenteColunaDTO.de("Depósito"), AssistenteColunaDTO.de("Quantidade"),
                    AssistenteColunaDTO.de("Reservada"), AssistenteColunaDTO.de("Disponível")), linhas));
        }
        return r;
    }

    private ResultadoFerramenta resumoEstoque() {
        EstoqueSummaryResponseDTO s = corpo(estoque.getSummary());
        ObjectNode saida = objeto()
                .put("totalProdutos", s.getTotalProdutos())
                .put("totalVariantes", s.getTotalVariantes())
                .put("valorEstoqueCusto", dinheiro(s.getValorEstoqueCusto()))
                .put("alertasCriticos", s.getAlertasCriticos())
                .put("alertasAtencao", s.getAlertasAtencao());
        if (s.getCategoriaComMaisProdutos() != null) {
            saida.put("categoriaComMaisProdutos", s.getCategoriaComMaisProdutos().getNome());
        }
        return ResultadoFerramenta.ok(json(saida))
                .comBloco(AssistenteBlocoDTO.tabela("Resumo do estoque",
                        List.of(AssistenteColunaDTO.de("Indicador"), AssistenteColunaDTO.sensivel("Valor")),
                        List.of(List.of("Produtos", String.valueOf(s.getTotalProdutos())),
                                List.of("Variações", String.valueOf(s.getTotalVariantes())),
                                List.of("Estoque a custo", dinheiro(s.getValorEstoqueCusto())),
                                List.of("Alertas críticos", String.valueOf(s.getAlertasCriticos())),
                                List.of("Alertas de atenção", String.valueOf(s.getAlertasAtencao())))))
                .comLink(link("Ver alertas", "/app/estoque/alertas"));
    }

    // ── Propostas ──────────────────────────────────────────────────────────────

    private ResultadoFerramenta proporCriarProduto(JsonNode in) {
        if (!temPermissao("ESTOQUE_PRODUCT_MANAGE")) {
            return semPermissao("criar produtos");
        }
        String sku = textoObrigatorio(in, "sku", 50);
        if (sku.length() < 3) {
            throw new EntradaInvalida("O SKU precisa ter ao menos 3 caracteres");
        }
        String nome = textoObrigatorio(in, "nome", 255);
        CategoryResponseDTO categoria = categoria(texto(in, "categoria", 100));
        BrandResponseDTO marca = marca(texto(in, "marca", 100));
        MeasurementUnit unidade = enumOpcional(in, "unidade", MeasurementUnit.class);
        String descricao = texto(in, "descricao", 2000);
        String codigoBarras = texto(in, "codigoBarras", 50);

        ObjectNode params = objeto().put("sku", sku).put("nome", nome);
        List<AssistenteCampoDTO> campos = new ArrayList<>(List.of(
                AssistenteCampoDTO.de("SKU", sku), AssistenteCampoDTO.de("Nome", nome),
                AssistenteCampoDTO.de("Status", "Rascunho (sem preço e sem estoque)")));
        if (categoria != null) {
            params.put("categoriaId", categoria.getId());
            campos.add(AssistenteCampoDTO.de("Categoria", categoria.getName()));
        }
        if (marca != null) {
            params.put("marcaId", marca.getId());
            campos.add(AssistenteCampoDTO.de("Marca", marca.getName()));
        }
        if (unidade != null) {
            params.put("unidade", unidade.name());
            campos.add(AssistenteCampoDTO.de("Unidade", unidade.name()));
        }
        if (descricao != null) {
            params.put("descricao", descricao);
            campos.add(AssistenteCampoDTO.de("Descrição", descricao));
        }
        if (codigoBarras != null) {
            params.put("codigoBarras", codigoBarras);
            campos.add(AssistenteCampoDTO.de("Código de barras", codigoBarras));
        }
        return ResultadoFerramenta.proposta(new AcaoProposta(CRIAR_PRODUTO, params.toString(),
                "Criar produto " + nome, campos));
    }

    private ResultadoFerramenta proporEditarProduto(JsonNode in) {
        if (!temPermissao("ESTOQUE_PRODUCT_MANAGE")) {
            return semPermissao("alterar produtos");
        }
        String sku = textoObrigatorio(in, "sku", 50);
        ProductResponseDTO atual = corpo(estoque.getProduct(sku));
        ObjectNode params = objeto().put("sku", atual.getSku());
        List<AssistenteCampoDTO> campos = new ArrayList<>(List.of(AssistenteCampoDTO.de("Produto",
                atual.getSku() + " — " + atual.getName())));

        String nome = texto(in, "nome", 255);
        if (nome != null) {
            params.put("nome", nome);
            campos.add(AssistenteCampoDTO.de("Nome", ou(atual.getName()) + " → " + nome));
        }
        String descricao = texto(in, "descricao", 2000);
        if (descricao != null) {
            params.put("descricao", descricao);
            campos.add(AssistenteCampoDTO.de("Descrição", descricao));
        }
        CategoryResponseDTO categoria = categoria(texto(in, "categoria", 100));
        if (categoria != null) {
            params.put("categoriaId", categoria.getId());
            campos.add(AssistenteCampoDTO.de("Categoria", ou(atual.getCategory()) + " → " + categoria.getName()));
        }
        BrandResponseDTO marca = marca(texto(in, "marca", 100));
        if (marca != null) {
            params.put("marcaId", marca.getId());
            campos.add(AssistenteCampoDTO.de("Marca", ou(atual.getBrand()) + " → " + marca.getName()));
        }
        MeasurementUnit unidade = enumOpcional(in, "unidade", MeasurementUnit.class);
        if (unidade != null) {
            params.put("unidade", unidade.name());
            campos.add(AssistenteCampoDTO.de("Unidade", ou(atual.getUnit()) + " → " + unidade.name()));
        }
        String codigoBarras = texto(in, "codigoBarras", 50);
        if (codigoBarras != null) {
            params.put("codigoBarras", codigoBarras);
            campos.add(AssistenteCampoDTO.de("Código de barras", ou(atual.getBarcode()) + " → " + codigoBarras));
        }
        Boolean visivelPdv = booleano(in, "visivelPdv");
        if (visivelPdv != null) {
            params.put("visivelPdv", visivelPdv);
            campos.add(AssistenteCampoDTO.de("Aparece no PDV", simNao(visivelPdv)));
        }
        Boolean visivelApp = booleano(in, "visivelApp");
        if (visivelApp != null) {
            params.put("visivelApp", visivelApp);
            campos.add(AssistenteCampoDTO.de("Aparece no app", simNao(visivelApp)));
        }
        if (campos.size() == 1) {
            throw new EntradaInvalida("Nenhuma alteração informada");
        }
        return ResultadoFerramenta.proposta(new AcaoProposta(EDITAR_PRODUTO, params.toString(),
                "Alterar produto " + atual.getName(), campos));
    }

    private static ResultadoFerramenta proporNome(JsonNode in, String tipo, String titulo, String rotulo,
            String permissao) {
        if (!temPermissao(permissao)) {
            return semPermissao(titulo.toLowerCase());
        }
        String nome = textoObrigatorio(in, "nome", 100);
        return ResultadoFerramenta.proposta(new AcaoProposta(tipo, objeto().put("nome", nome).toString(),
                titulo + " " + nome, List.of(AssistenteCampoDTO.de(rotulo, nome))));
    }

    // ── Execução (no confirmar) ────────────────────────────────────────────────

    private Execucao criarProduto(JsonNode p) {
        ProductRequest req = new ProductRequest();
        req.setSku(p.get("sku").asText());
        req.setName(p.get("nome").asText());
        req.setStatus(ProductStatus.RASCUNHO);
        if (p.hasNonNull("categoriaId")) {
            req.setCategoryId(p.get("categoriaId").asLong());
        }
        if (p.hasNonNull("marcaId")) {
            req.setBrandId(p.get("marcaId").asLong());
        }
        if (p.hasNonNull("unidade")) {
            req.setUnit(MeasurementUnit.valueOf(p.get("unidade").asText()));
        }
        if (p.hasNonNull("descricao")) {
            req.setDescription(p.get("descricao").asText());
        }
        if (p.hasNonNull("codigoBarras")) {
            req.setBarcode(p.get("codigoBarras").asText());
        }
        ProductResponseDTO criado = corpo(estoque.createProduct(req, autenticacao()));
        return new Execucao("Produto " + criado.getName() + " criado como rascunho.",
                List.of(link("Abrir produto", "/app/estoque/produtos/" + criado.getSku() + "/editar")));
    }

    private Execucao editarProduto(JsonNode p) {
        ProductPatchRequest req = new ProductPatchRequest();
        if (p.hasNonNull("nome")) {
            req.setName(p.get("nome").asText());
        }
        if (p.hasNonNull("descricao")) {
            req.setDescription(p.get("descricao").asText());
        }
        if (p.hasNonNull("categoriaId")) {
            req.setCategoryId(p.get("categoriaId").asLong());
        }
        if (p.hasNonNull("marcaId")) {
            req.setBrandId(p.get("marcaId").asLong());
        }
        if (p.hasNonNull("unidade")) {
            req.setUnit(MeasurementUnit.valueOf(p.get("unidade").asText()));
        }
        if (p.hasNonNull("codigoBarras")) {
            req.setBarcode(p.get("codigoBarras").asText());
        }
        if (p.hasNonNull("visivelPdv")) {
            req.setVisibleInPos(p.get("visivelPdv").asBoolean());
        }
        if (p.hasNonNull("visivelApp")) {
            req.setVisibleInMarketplace(p.get("visivelApp").asBoolean());
        }
        String sku = p.get("sku").asText();
        ProductResponseDTO atualizado = corpo(estoque.updateProduct(sku, req, autenticacao()));
        return new Execucao("Produto " + atualizado.getName() + " atualizado.",
                List.of(link("Abrir produto", "/app/estoque/produtos/" + atualizado.getSku() + "/editar")));
    }

    private Execucao criarCategoria(JsonNode p) {
        CategoryRequest req = new CategoryRequest();
        req.setName(p.get("nome").asText());
        CategoryResponseDTO criada = corpo(estoque.createCategory(req, autenticacao()));
        return new Execucao("Categoria " + criada.getName() + " criada.",
                List.of(link("Ver categorias", "/app/estoque/categorias")));
    }

    private Execucao criarMarca(JsonNode p) {
        BrandRequest req = new BrandRequest();
        req.setName(p.get("nome").asText());
        BrandResponseDTO criada = corpo(estoque.createBrand(req, autenticacao()));
        return new Execucao("Marca " + criada.getName() + " criada.",
                List.of(link("Ver categorias e marcas", "/app/estoque/categorias")));
    }

    // ── Apoio ──────────────────────────────────────────────────────────────────

    private CategoryResponseDTO categoria(String nome) {
        if (nome == null) {
            return null;
        }
        for (int page = 0; page < 5; page++) {
            PageResult<CategoryResponseDTO> r = corpo(estoque.listCategories(page, 100));
            for (CategoryResponseDTO c : r.content()) {
                if (c.getName() != null && c.getName().equalsIgnoreCase(nome)) {
                    return c;
                }
            }
            if (page + 1 >= r.totalPages()) {
                break;
            }
        }
        throw new EntradaInvalida("A categoria '" + nome + "' não existe. Proponha criá-la antes, se o usuário quiser.");
    }

    private BrandResponseDTO marca(String nome) {
        if (nome == null) {
            return null;
        }
        return corpo(estoque.listBrands(0, 50, nome)).content().stream()
                .filter(b -> b.getName() != null && b.getName().equalsIgnoreCase(nome))
                .findFirst()
                .orElseThrow(() -> new EntradaInvalida(
                        "A marca '" + nome + "' não existe. Proponha criá-la antes, se o usuário quiser."));
    }

    static ResultadoFerramenta semPermissao(String oQue) {
        return ResultadoFerramenta.falha("O usuário não tem permissão para " + oQue + ". Não proponha esta ação.");
    }

    static String simNao(boolean valor) {
        return valor ? "Sim" : "Não";
    }

    static <T> T corpo(ResponseEntity<T> resposta) {
        return Objects.requireNonNull(resposta.getBody(), "Resposta vazia");
    }

    static Map<String, Object> props(Object... pares) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put((String) pares[i], pares[i + 1]);
        }
        return mapa;
    }
}
