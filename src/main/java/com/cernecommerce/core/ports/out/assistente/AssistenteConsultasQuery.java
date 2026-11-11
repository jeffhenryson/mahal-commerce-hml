package com.cernecommerce.core.ports.out.assistente;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Agregações só de leitura que o assistente precisa e nenhuma tela expõe pronta (rodada 3): vendas por
 * categoria, ruptura prevista pela média de saída e clientes sem compra recente. A permissão é
 * conferida pela ferramenta que chama, com as mesmas autoridades das telas equivalentes.
 */
public interface AssistenteConsultasQuery {

    /** Categoria do produto (a do pai, para variações); sem categoria vem {@code null}. */
    record CategoriaVendida(String categoria, BigDecimal quantidade, BigDecimal receita) { }

    /**
     * Saldo disponível (quantidade menos reservado) e saída comum (sem transferências) na janela,
     * com o ponto de reposição do par SKU/depósito quando existir.
     */
    record SaidaEstoque(String sku, String produto, String deposito, Long depositoId, BigDecimal disponivel,
            BigDecimal saidaNaJanela, BigDecimal minimo, BigDecimal critico) { }

    /** Só o nome: o assistente não recebe contato, documento nem endereço. */
    record ClienteInativo(Long id, String nome, Instant ultimaCompra, long pedidos) { }

    /** Mesmo filtro de "pagamento confirmado, exceto reembolsado" do resumo de vendas. */
    List<CategoriaVendida> topCategorias(Instant de, Instant ate, int limite, boolean porQuantidade);

    /** Pares SKU/depósito com saída comum desde {@code desde}. */
    List<SaidaEstoque> saidasDesde(Instant desde);

    /** Clientes com compra paga, a última antes de {@code semCompraDesde}; mais recentes primeiro. */
    List<ClienteInativo> clientesInativos(Instant semCompraDesde, int limite);
}
