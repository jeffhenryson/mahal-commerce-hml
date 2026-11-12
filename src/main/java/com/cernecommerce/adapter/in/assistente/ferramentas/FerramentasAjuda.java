package com.cernecommerce.adapter.in.assistente.ferramentas;

import com.cernecommerce.adapter.in.assistente.Ferramenta;
import com.cernecommerce.adapter.in.assistente.ProvedorFerramentas;
import com.cernecommerce.adapter.in.assistente.ResultadoFerramenta;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.cernecommerce.adapter.in.assistente.FerramentaSupport.*;
import static com.cernecommerce.adapter.in.assistente.ferramentas.FerramentasEstoque.props;

/** Ajuda de uso: onde fica cada coisa no admin. Texto curado, sem consulta ao banco. */
@Component
public class FerramentasAjuda implements ProvedorFerramentas {

    private record Tela(String tema, String rota, String descricao) { }

    private static final List<Tela> TELAS = List.of(
            new Tela("produtos", "/app/estoque/produtos",
                    "Catálogo: cadastrar, editar, precificar e arquivar produtos. Rascunhos ficam fora das vitrines."),
            new Tela("estoque", "/app/estoque/movimentacoes",
                    "Entradas, saídas, ajustes e transferências de estoque entre depósitos."),
            new Tela("alertas", "/app/estoque/alertas", "Produtos abaixo do ponto de reposição."),
            new Tela("reposicao", "/app/estoque/reposicao", "Sugestões de compra para repor o estoque."),
            new Tela("categorias", "/app/estoque/categorias", "Categorias e marcas do catálogo."),
            new Tela("pedidos", "/app/vendas/pedidos", "Pedidos de todos os canais, com filtros e detalhes."),
            new Tela("caixas", "/app/vendas/caixas", "Sessões de caixa do PDV: abertura, fechamento e conferência."),
            new Tela("indicadores", "/app/indicadores/vendas", "Análise de vendas por período, canal e produto."),
            new Tela("central de pedidos", "/app/central-pedidos", "Pedidos do app e do QR da mesa em tempo real."),
            new Tela("salao", "/app/salao/mesas", "Mesas do salão e sessões em andamento."),
            new Tela("clientes", "/app/crm/clientes", "Base de clientes, ficha, tags, observações e segmentos (RFM)."),
            new Tela("cashback", "/app/crm/cashback", "Regras e saldos de cashback."),
            new Tela("compras", "/app/compras", "Pedidos de compra, fornecedores e recebimento de mercadoria."),
            new Tela("financeiro", "/app/financeiro", "Fluxo de caixa e recebíveis."),
            new Tela("integracoes", "/app/settings/integrations",
                    "Chaves das integrações da loja: e-mail, WhatsApp, automações e o próprio assistente."),
            new Tela("automacoes", "/app/settings/automations", "Automações de CRM e seus disparos."));

    @Override
    public List<Ferramenta> ferramentas() {
        return List.of(new Ferramenta("ajuda_uso", "Consultando a ajuda",
                "Onde fica cada função no admin, com o caminho da tela. Use para perguntas de 'como faço' "
                        + "e 'onde fica'.",
                schema(props("tema", str("Assunto, ex.: produtos, pedidos, clientes, caixas"))),
                this::ajuda));
    }

    private ResultadoFerramenta ajuda(JsonNode in) {
        String tema = texto(in, "tema", 60);
        List<Tela> achadas = tema == null ? TELAS : TELAS.stream()
                .filter(t -> t.tema().contains(tema.toLowerCase(Locale.ROOT))
                        || t.descricao().toLowerCase(Locale.ROOT).contains(tema.toLowerCase(Locale.ROOT)))
                .toList();
        if (achadas.isEmpty()) {
            achadas = TELAS;
        }
        ResultadoFerramenta r = ResultadoFerramenta.ok(json(Map.of("telas", achadas.stream()
                .map(t -> Map.of("tema", t.tema(), "rota", t.rota(), "descricao", t.descricao())).toList())));
        if (achadas.size() <= 3) {
            for (Tela t : achadas) {
                r = r.comLink(link("Abrir " + t.tema(), t.rota()));
            }
        }
        return r;
    }
}
