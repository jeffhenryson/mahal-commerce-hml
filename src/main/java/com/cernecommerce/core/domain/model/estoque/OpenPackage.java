package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.exception.estoque.InvalidOpenPackageUsesException;

import java.time.Instant;

/**
 * A lata de essência aberta no balcão (EST-F027) — o pacote que já saiu da prateleira e está
 * rendendo sessões.
 *
 * <p><b>O problema que ela resolve.</b> Uma lata rende várias sessões, e o catálogo já declarava
 * quantas em {@code sessionsPerUnit} desde a V112 — mas o campo nunca foi lido por ninguém, e cada
 * sessão lançada na comanda baixava uma lata inteira. Medido no QA de 06/09/2026:
 * {@code ESSE-ZGY-BLUEBERRY} foi de 50 para 49 numa sessão só. Com {@code sessionsPerUnit: 5}, o
 * estoque sumia cinco vezes mais rápido que a realidade — o alerta de reposição disparava cedo, o
 * custo por sessão saía inflado e a margem do open rosh, que é o número que o dono quer olhar,
 * saía errada.
 *
 * <p><b>A baixa acontece na ABERTURA, não na reposição.</b> É o que mantém o significado do saldo
 * igual ao que o operador conta no balanço: {@code stock_balance} passa a ser "latas lacradas na
 * prateleira", e a lata em uso está aqui. Nenhum movimento é inventado — a {@code SAIDA} de 1
 * acontece no instante físico em que alguém tira a lata da prateleira e abre.</p>
 *
 * <p><b>Escopo por depósito</b>, coerente com o resto do módulo. Dois balcões no mesmo armazém
 * compartilham a lata, que é o comportamento físico correto.</p>
 *
 * <p>{@code sessionsPerUnit} é <b>copiado</b> do catálogo na abertura, não lido dele a cada uso: o
 * admin pode editar o produto no meio da noite, e uma lata pela metade não pode mudar de tamanho
 * porque alguém corrigiu o cadastro.</p>
 */
public record OpenPackage(
        Long id,
        String sku,
        Long warehouseId,
        int uses,
        int sessionsPerUnit,
        Instant openedAt,
        String openedBy,
        Instant closedAt,
        OpenPackageCloseReason closeReason) {

    public OpenPackage {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId é obrigatório");
        }
        if (sessionsPerUnit <= 0) {
            throw new IllegalArgumentException("sessionsPerUnit deve ser maior que zero: " + sessionsPerUnit);
        }
        if (uses < 0) {
            throw new IllegalArgumentException("uses não pode ser negativo: " + uses);
        }
        if (uses > sessionsPerUnit) {
            throw new IllegalArgumentException(
                    "uses não pode passar de sessionsPerUnit: " + uses + " > " + sessionsPerUnit);
        }
        if (openedAt == null) {
            throw new IllegalArgumentException("openedAt é obrigatório");
        }
        if (openedBy == null || openedBy.isBlank()) {
            throw new IllegalArgumentException("openedBy é obrigatório");
        }
        // Fechada e sem motivo — ou o inverso — é meio estado. Mesma régua de StockReservation,
        // que amarra status a resolvedAt no compact constructor.
        if ((closedAt == null) != (closeReason == null)) {
            throw new IllegalArgumentException("closedAt e closeReason precisam vir juntos");
        }
    }

    /** Abre uma lata nova, ainda sem uso. */
    public static OpenPackage open(String sku, Long warehouseId, int sessionsPerUnit, String openedBy,
            Instant openedAt) {
        return new OpenPackage(null, sku, warehouseId, 0, sessionsPerUnit, openedAt, openedBy, null, null);
    }

    /**
     * Cadastra uma lata que <b>já estava aberta</b> antes de o sistema saber dela (EST-F033) — o
     * inventário inicial das essências da mesa.
     *
     * <p>Recebe o que o operador vê, quantas sessões a lata ainda rende, e guarda o que já foi
     * gasto. Ao contrário de {@link #open}, não há {@code SAIDA} por trás: a lata saiu da
     * prateleira antes, e baixar agora tiraria do saldo uma segunda lata que continua lacrada.</p>
     */
    public static OpenPackage registered(String sku, Long warehouseId, int sessionsPerUnit, int usesRemaining,
            String openedBy, Instant openedAt) {
        // Zero restante é lata vazia (vai para o lixo, não para o contador); acima do que a lata
        // rende é erro de digitação, não lata maior.
        if (usesRemaining < 1 || usesRemaining > sessionsPerUnit) {
            throw new InvalidOpenPackageUsesException(usesRemaining, sessionsPerUnit);
        }
        return new OpenPackage(null, sku, warehouseId, sessionsPerUnit - usesRemaining, sessionsPerUnit,
                openedAt, openedBy, null, null);
    }

    public boolean isOpen() {
        return closedAt == null;
    }

    /**
     * Rendeu tudo o que tinha para render. A lata <b>continua aberta</b> nesse estado, de
     * propósito: é ela que o atendente está usando até o fim, e a tela precisa poder mostrar
     * "5 de 5". Quem a fecha é a próxima sessão, ao abrir a seguinte.
     */
    public boolean isExhausted() {
        return uses >= sessionsPerUnit;
    }

    public int remaining() {
        return Math.max(0, sessionsPerUnit - uses);
    }

    /** Registra {@code count} sessões nesta lata. */
    public OpenPackage withUses(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count deve ser maior que zero: " + count);
        }
        // Teto no sessionsPerUnit em vez de estourar: uma sessão que pede mais usos do que a lata
        // ainda tem é a lata acabando no meio do lançamento, e o que sobra é cobrado da próxima
        // pelo caminho normal (esta fecha como EXHAUSTED e outra é aberta). Estourar aqui
        // recusaria um lançamento que o salão já fez.
        return new OpenPackage(id, sku, warehouseId, Math.min(sessionsPerUnit, uses + count),
                sessionsPerUnit, openedAt, openedBy, closedAt, closeReason);
    }

    /**
     * Desconta sessões desta lata — o caminho de cancelamento de comanda e de remoção de linha.
     *
     * <p><b>Não devolve unidade ao estoque, e essa assimetria é o desenho.</b> Antes da lata,
     * cancelar uma comanda lançava {@code ENTRADA} por linha, o que estava certo enquanto cada
     * sessão baixava uma lata inteira. Com o contador, devolver unidade inventaria saldo: a
     * essência já foi queimada e não voltou para a prateleira. O que se desfaz é a contagem.</p>
     *
     * <p>Piso em zero em vez de estouro porque a lata pode ter sido reposta entre o lançamento e o
     * cancelamento — nesse caso não há o que descontar, e recusar o cancelamento por isso deixaria
     * a mesa presa por um detalhe de contabilidade de lata.</p>
     */
    public OpenPackage withoutUses(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count deve ser maior que zero: " + count);
        }
        return new OpenPackage(id, sku, warehouseId, Math.max(0, uses - count), sessionsPerUnit,
                openedAt, openedBy, closedAt, closeReason);
    }

    public OpenPackage closed(OpenPackageCloseReason reason, Instant at) {
        if (reason == null || at == null) {
            throw new IllegalArgumentException("motivo e momento do fechamento são obrigatórios");
        }
        return new OpenPackage(id, sku, warehouseId, uses, sessionsPerUnit, openedAt, openedBy, at, reason);
    }

    public OpenPackage withId(Long newId) {
        return new OpenPackage(newId, sku, warehouseId, uses, sessionsPerUnit, openedAt, openedBy,
                closedAt, closeReason);
    }
}
