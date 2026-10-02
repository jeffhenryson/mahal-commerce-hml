package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pdv.ComandaHistoryFilter;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.KitSelection;
import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.SessionTimeline;
import com.cernecommerce.core.domain.model.pdv.StorePurchase;
import com.cernecommerce.core.domain.model.pdv.SessionStatus;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port de entrada do domínio <b>comanda de mesa</b> (PDV-F009).
 *
 * <p>Endpoints novos, separados de {@link PdvUseCase#registerSale}: a comanda modela um pedido
 * incremental de horas (lounge de narguilé), enquanto a venda de balcão continua sendo pontual.
 * O estoque é debitado item a item assim que ele é lançado — não no fechamento —, porque é isso
 * que o evento físico (essência preparada, carvão trocado) já significa. Ver
 * {@code ComandaService} para o porquê disso não ser transacionalmente atômico ao longo da vida
 * da comanda.</p>
 */
public interface ComandaUseCase {

    /**
     * Abre uma comanda nova na sessão do operador autenticado.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException
     *         se a sessão não pertencer a quem está abrindo
     */
    default Comanda openComanda(Long sessionId, String tableOrCustomerLabel, String username) {
        return openComanda(sessionId, tableOrCustomerLabel, null, username);
    }

    /**
     * Abre uma comanda nova, opcionalmente vinculada a um cliente do CRM (PDV-F010).
     *
     * <p>{@code customerId} é o que faz o pedido da mesa sair com nome e gerar cashback — o rótulo
     * da mesa nunca foi vínculo de cadastro.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException
     *         se a sessão não pertencer a quem está abrindo
     */
    Comanda openComanda(Long sessionId, String tableOrCustomerLabel, Long customerId, String username);

    /**
     * Lança um item na comanda aberta, debitando o estoque na hora.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException se a comanda já
     *         estiver fechada ou cancelada
     * @throws com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException se o
     *         produto não tiver preço no catálogo
     * @throws com.cernecommerce.core.domain.exception.estoque.InsufficientStockException se o
     *         saldo for insuficiente
     */
    default Comanda addItem(Long comandaId, String sku, BigDecimal quantity, String username) {
        return addItem(comandaId, sku, quantity, ConsumptionMode.NORMAL, false, null, username);
    }

    /**
     * Lança uma linha de <b>sessão de narguilé</b> na comanda aberta (PDV-F010), debitando o
     * estoque na hora como qualquer outra linha.
     *
     * <p>O preço unitário é resolvido <b>aqui</b>, e não pelo chamador — mesma garantia de sempre.
     * A regra por modo: {@code NORMAL} e {@code SABOR_EXTRA} cobram o preço da variação do sabor;
     * {@code OPEN_ROSH} cobra o {@code openRoshPrice} do produto <b>pai</b>; cortesia e
     * {@code TROCA} gravam zero, sempre com o custo congelado normalmente.</p>
     *
     * @param mode por que a linha existe. Nulo resolve para {@code NORMAL}.
     * @param courtesy linha a preço zero que ainda baixa estoque. Quem chama é responsável por
     *        checar a permissão — ver {@code PdvComandaController}, mesmo padrão de
     *        {@code PDV_SALE_DISCOUNT}.
     * @param linkedItemId linha de origem na mesma comanda, obrigatória em {@code SABOR_EXTRA} e
     *        {@code TROCA}.
     * @throws com.cernecommerce.core.domain.exception.pdv.NotAvailableForTableException se o SKU
     *         não estiver disponível para mesa
     * @throws com.cernecommerce.core.domain.exception.pdv.NotASessionProductException se um modo
     *         de sessão for pedido para um SKU que não é produto de sessão
     * @throws com.cernecommerce.core.domain.exception.pdv.OpenRoshNotPricedException se
     *         {@code OPEN_ROSH} for pedido para produto sem preço de consumo livre
     * @throws com.cernecommerce.core.domain.exception.pdv.LinkedItemRequiredException se a linha
     *         de origem faltar ou não pertencer a esta comanda
     * @throws com.cernecommerce.core.domain.exception.pdv.NotAnOpenRoshException se a
     *         {@code TROCA} apontar para uma linha que não é consumo livre
     */
    default Comanda addItem(Long comandaId, String sku, BigDecimal quantity, ConsumptionMode mode,
            boolean courtesy, Long linkedItemId, String username) {
        return addItem(comandaId, sku, quantity, mode, courtesy, linkedItemId, null, null, username);
    }

    /**
     * Lança a linha carregando também o <b>setup da mesa</b> e o <b>acréscimo do open rosh</b>
     * (PDV-F011).
     *
     * <p>{@code notes} é texto opaco: o servidor grava e devolve, nunca interpreta. Existe porque
     * nem tudo que sai para a mesa é venda — a pinça é equipamento do salão, não é consumida, e
     * lançá-la como cortesia baixaria estoque e apareceria no cupom do cliente como um item de
     * R$ 0 que ele não pediu. Registro não é venda a zero.</p>
     *
     * <p>{@code surchargeAmount} é somado ao preço que o servidor resolve, e por isso só faz
     * sentido em {@code OPEN_ROSH}: nos demais modos a diferença do sabor caro já mora no
     * {@code pricing} da variante. <b>Não é um {@code discountAmount} negativo</b> — acréscimo e
     * desconto são operações opostas com o mesmo peso contábil, e o relatório precisa distinguir
     * "cobramos a mais" de "cobramos a menos". O {@code costPrice} segue congelado: acréscimo é
     * margem, não custo.</p>
     *
     * @param notes registro livre do setup, no máximo {@code ComandaItem.NOTES_MAX_LENGTH}.
     * @param surchargeAmount acréscimo sobre o preço resolvido. Quem chama é responsável por
     *        checar {@code PDV_COMANDA_SURCHARGE} — ver {@code PdvComandaController}, mesmo padrão
     *        de {@code courtesy}.
     * @throws com.cernecommerce.core.domain.exception.pdv.NotesTooLongException se {@code notes}
     *         passar do limite — recusa em vez de truncar, para o operador saber que perdeu o
     *         registro
     * @throws com.cernecommerce.core.domain.exception.pdv.SurchargeInvalidException se o acréscimo
     *         for negativo
     * @throws com.cernecommerce.core.domain.exception.pdv.SurchargeOnCourtesyException se houver
     *         acréscimo numa linha de cortesia
     * @throws com.cernecommerce.core.domain.exception.pdv.SurchargeNotApplicableException se
     *         houver acréscimo fora de {@code OPEN_ROSH}
     */
    Comanda addItem(Long comandaId, String sku, BigDecimal quantity, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String notes, BigDecimal surchargeAmount, String username);

    /**
     * Remove uma linha da comanda aberta, devolvendo ao estoque o que ela havia debitado
     * (PDV-F012).
     *
     * <p>Existe porque, até aqui, lançamento errado numa mesa só saía cancelando a comanda
     * <b>inteira</b> — o que devolve tudo ao estoque, encerra a mesa e obriga a relançar item a
     * item um consumo que continua acontecendo.</p>
     *
     * <p><b>As {@code TROCA} penduradas na linha saem junto.</b> Elas são cortesia e não existem
     * sem o consumo livre que as originou, e {@code linked_item_id} é FK auto-referente: deixá-las
     * para trás produziria linha apontando para id inexistente. O {@code SABOR_EXTRA}, ao
     * contrário, é linha própria e pode estar sendo cobrada — a presença dele <b>barra</b> a
     * remoção em vez de ser arrastado, para não tirar valor da conta sem o operador pedir.</p>
     *
     * <p>A devolução é uma {@code ENTRADA} por linha removida, o mesmo padrão de
     * {@link #cancelComanda}. Não exige posse do caixa, como o resto da operação de mesa.</p>
     *
     * @return a comanda sem a linha e sem as trocas dela
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException se a comanda já
     *         estiver fechada ou cancelada — linha de mesa fechada é histórico, e o pedido gerado
     *         já foi pago
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaItemNotFoundException se a linha
     *         não estiver nesta comanda
     * @throws com.cernecommerce.core.domain.exception.pdv.LinkedItemIsChargedException se houver
     *         {@code SABOR_EXTRA} pendurado na linha
     */
    Comanda removeItem(Long comandaId, Long itemId, String username);

    /**
     * PDV-F019 — lança um kit montável na comanda: uma linha por item escolhido, todas com o mesmo
     * pacote e cada uma com a sua parte do desconto do kit. Preço congelado agora, como todo
     * lançamento; o estoque de cada item sai agora, como em {@link #addItem}.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.InvalidKitSelectionException se a
     *         escolha não fecha um kit válido no PDV
     */
    Comanda addKit(Long comandaId, KitSelection selection,
            String username);

    /**
     * Tira o pacote inteiro da comanda e devolve o estoque de cada linha.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaItemNotFoundException se o pacote
     *         não tiver linha aberta nesta comanda
     */
    Comanda removeKit(Long comandaId, String kitBundleId, String username);

    /**
     * Busca uma comanda pelo id.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotFoundException se não existir
     */
    Comanda getComanda(Long comandaId);

    /**
     * Comandas abertas — a lista de "mesas ocupadas" (PDV-C007).
     *
     * <p>Os dois filtros são <b>opcionais</b>. Sem {@code sessionId} a listagem é da <b>loja</b>, e
     * não de um caixa: a decisão do dono é <i>caixa por atendente, mesas compartilhadas</i>, e quem
     * assume o posto do colega precisa ver o salão inteiro. Era isso que forçava o cliente a
     * buscar as sessões abertas e disparar uma chamada por sessão.</p>
     *
     * <p>Paginada desde PDV-C012: a rota devolvia {@code List} sem teto, o que era contido pelo
     * tamanho do salão enquanto a listagem era de um caixa só — e deixa de ser quando ela passa a
     * ser da loja.</p>
     */
    PageResult<Comanda> listOpenComandas(Long sessionId, String warehouseCode, int page, int size);

    /**
     * Fecha a comanda: converte os itens acumulados num {@code Order} concluído, validando os
     * pagamentos contra o total (mesmo contrato de {@link PdvUseCase#registerSale}). O estoque já
     * foi debitado item a item em {@link #addItem} — o fechamento não toca em saldo de novo.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException se a comanda já
     *         estiver fechada ou cancelada
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaEmptyException se não houver
     *         nenhum item lançado
     * <p><b>Desconto (PDV-F014)</b> é de nível de conta — "tira 20 reais" —, mas é <b>rateado entre
     * os itens</b> antes de virar pedido, proporcionalmente ao valor de cada linha
     * ({@code DiscountProration}). Sem o rateio a casa pagaria cashback sobre dinheiro que não
     * recebeu e a margem por item mostraria a venda cheia. O teto é o mesmo do balcão
     * ({@code pdv.sale.max-discount-percent}); quem chama é responsável por checar
     * {@code PDV_COMANDA_DISCOUNT} — ver {@code PdvComandaController}, mesmo padrão de
     * {@code courtesy}.</p>
     *
     * <p><b>Taxa de serviço (PDV-F015)</b> é o oposto: um acréscimo, calculado pelo servidor como
     * percentual sobre o líquido (portanto <b>depois</b> do desconto) e gravado em campo próprio,
     * fora do {@code netAmount} — o líquido é receita da casa, a taxa é repasse ao garçom. Vem
     * aplicada por padrão porque é o padrão do salão; {@code applyServiceFee = false} é o cliente
     * recusando.</p>
     *
     * @param discountAmount abatimento sobre a conta inteira. Nulo ou zero é o caso comum.
     * @param applyServiceFee {@code false} remove a taxa que seria cobrada.
     * @throws com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException se o
     *         desconto passar do teto configurado
     * @throws com.cernecommerce.core.domain.exception.pagamento.InsufficientPaymentException se a
     *         soma dos pagamentos não cobrir o total, <b>já com a taxa somada</b>
     */
    default Order closeComanda(Long comandaId, List<PaymentCommand> payments, BigDecimal discountAmount,
            boolean applyServiceFee, String username) {
        return closeComanda(comandaId, payments, discountAmount, applyServiceFee, null, username);
    }

    /**
     * Fecha <b>parte</b> da conta (PDV-F017): gera um pedido só com as linhas de {@code itemIds},
     * marca-as como cobradas e deixa a comanda <b>ABERTA</b> com o restante. Repetido até não sobrar
     * linha, o último fechamento encerra a mesa. {@code itemIds} nulo ou vazio cobra tudo que está
     * em aberto — o comportamento de sempre.
     *
     * <p>É assim que "cada um paga o que consumiu" existe no modelo. O split que já havia
     * (PDV-F006) é de <b>forma de pagamento</b>: divide como se paga, não quem paga o quê.</p>
     *
     * <p>Desconto, taxa de serviço, troco e cashback incidem sobre <b>o escopo</b>, não sobre a mesa
     * inteira — cada conta é um pedido completo e independente, e somar a taxa do salão sobre o
     * consumo alheio seria cobrar duas vezes pelo mesmo serviço.</p>
     *
     * <p>Uma seleção não pode separar linhas amarradas por {@code linkedItemId}: um {@code OPEN_ROSH}
     * e as trocas dele saem na mesma conta ou em nenhuma.</p>
     *
     * @param itemIds linhas a cobrar; nulo/vazio cobra todas as abertas
     * @throws com.cernecommerce.core.domain.exception.pdv.ItemNotOpenInComandaException se algum id
     *         não for de linha aberta desta comanda
     * @throws com.cernecommerce.core.domain.exception.pdv.LinkedItemMustCloseTogetherException se a
     *         seleção separar linhas ligadas
     */
    Order closeComanda(Long comandaId, List<PaymentCommand> payments, BigDecimal discountAmount,
            boolean applyServiceFee, List<Long> itemIds, String username);

    /**
     * Percentual da taxa de serviço vigente (PDV-F015), para a tela mostrar ao operador quanto será
     * cobrado <b>antes</b> de ele fechar — e para o cliente poder recusar com o número na mão.
     *
     * <p>Existe porque a taxa é aplicada por padrão: sem uma forma de consultá-la, a única maneira
     * de descobrir o valor seria fechar a conta, que é tarde demais.</p>
     */
    BigDecimal getServiceFeePercent();

    /**
     * Abandona a comanda sem cobrança, devolvendo ao estoque cada item já debitado
     * ({@code ENTRADA}, mesmo padrão de {@code OrderService.refundOrder}).
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException se a comanda já
     *         estiver fechada ou cancelada
     */
    Comanda cancelComanda(Long comandaId, String username);

    /** PDV-F029 — idem, com o motivo, que fica no histórico da mesa. */
    Comanda cancelComanda(Long comandaId, String username, String reason);

    /**
     * PDV-F029 — mesas encerradas (FECHADA e CANCELADA), da mais recente para a mais antiga, com os
     * pedidos MESA que geraram e os totais derivados deles.
     */
    PageResult<ComandaHistoryEntry> listHistory(ComandaHistoryFilter filter, int page, int size);

    /** PDV-F029 — uma comanda em qualquer status, com os pedidos que gerou e os pagamentos de cada um. */
    ComandaHistoryEntry getHistoryEntry(Long comandaId);

    /**
     * PDV-F029 — indicadores das mesas FECHADAS no período (encerradas entre {@code from} e
     * {@code to}). Intervalo máximo de 366 dias.
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.InvalidReportPeriodException
     */
    ComandaAnalytics analytics(java.time.Instant from, java.time.Instant to, String warehouseCode);

    /**
     * PDV-F036 — grava a resposta a "o cliente comprou algo na loja?". Uma por mesa, em qualquer
     * status: a pergunta é feita ao recolher a última sessão ou ao encerrar, e pode ser corrigida pelo
     * histórico. Responder de novo sobrescreve.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotFoundException
     */
    void recordStorePurchase(Long comandaId, boolean boughtInStore, String username);

    /**
     * Uma mesa encerrada com o que ela gerou. Os totais vêm dos pedidos MESA (inclusive os parciais);
     * {@code totalPaid} ignora pedido reembolsado. {@code paymentsByOrder} só vem no detalhe.
     *
     * <p>PDV-F035: {@code sessions} (a linha do tempo de cada sessão) e os dois intervalos da mesa só
     * vêm no detalhe; na listagem, {@code sessions} é vazia.</p>
     */
    record ComandaHistoryEntry(Comanda comanda, String closedBy, String cancelReason, List<Order> orders,
            java.util.Map<Long, List<OrderPayment>> paymentsByOrder, Long durationMinutes, BigDecimal totalPaid,
            BigDecimal serviceFeeTotal, BigDecimal discountTotal, BigDecimal courtesyTotal, int sessionsCount,
            StorePurchase storePurchase, List<SessionTimeline> sessions, Long aberturaAtePrimeiraSessaoMin,
            Long ultimoRecolhimentoAteEncerramentoMin) {

        public ComandaHistoryEntry {
            sessions = sessions == null ? List.of() : List.copyOf(sessions);
        }

        public ComandaHistoryEntry(Comanda comanda, String closedBy, String cancelReason, List<Order> orders,
                java.util.Map<Long, List<OrderPayment>> paymentsByOrder, Long durationMinutes, BigDecimal totalPaid,
                BigDecimal serviceFeeTotal, BigDecimal discountTotal, BigDecimal courtesyTotal, int sessionsCount) {
            this(comanda, closedBy, cancelReason, orders, paymentsByOrder, durationMinutes, totalPaid,
                    serviceFeeTotal, discountTotal, courtesyTotal, sessionsCount, null, List.of(), null, null);
        }
    }

    /** Indicadores de mesas — os nomes de campo são o contrato do front (Vendas › Mesas). */
    record ComandaAnalytics(int mesas, BigDecimal ticketMedio, long permanenciaMediaMin, BigDecimal receitaTotal,
            BigDecimal taxaServicoTotal, BigDecimal descontoTotal, SessoesNarguile sessoesNarguile,
            List<PorAtendente> porAtendente, List<PorMesa> porMesa, List<PorHora> porHora,
            CompraNaLoja compraNaLoja) {
    }

    /**
     * Sessões das mesas do período. PDV-F035: médias de fase em minutos, sobre as linhas SESSAO que
     * passaram pela fase (nulas sem nenhuma). {@code esperaMediaMin} ignora as pagas no final, que
     * não esperam.
     */
    record SessoesNarguile(int quantidade, BigDecimal receita, Long esperaMediaMin, Long preparoMedioMin,
            Long naMesaMediaMin, int pagasNoFinal, int desistidas) {
    }

    /**
     * PDV-F036 — quantas mesas com sessão terminaram em compra na loja. {@code taxaConversao} é
     * percentual (0–100, duas casas) sobre as respondidas; nulo sem nenhuma resposta.
     */
    record CompraNaLoja(int mesasComSessao, int respondidas, int compraram, BigDecimal taxaConversao) {
    }

    record PorAtendente(String username, int mesas, BigDecimal receita) {
    }

    record PorMesa(String tableLabel, int mesas, BigDecimal receita, long permanenciaMediaMin) {
    }

    record PorHora(int hora, int mesasAbertas, BigDecimal receita) {
    }

    /**
     * Troca o rótulo da mesa (PDV-F016) — o cliente mudou de lugar no salão.
     *
     * <p>Antes disto {@code tableOrCustomerLabel} era imutável, e trocar de mesa só era possível
     * cancelando (o que devolvia tudo ao estoque e encerrava a conta) e relançando item a item.
     * Nada de físico acontece aqui: itens, depósito e sessão de origem seguem os mesmos.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException se a comanda não
     *         estiver aberta
     */
    Comanda renameComanda(Long comandaId, String newLabel, String username);

    /**
     * Vincula, troca ou remove ({@code customerId = null}) o cliente do CRM da mesa aberta
     * (PDV-F020). A existência do cliente é checada por quem chama — o controller resolve o
     * cliente no CRM (id existente ou find-or-create do lead) antes de chegar aqui. Vale para as
     * linhas ainda não cobradas: o que já foi pago numa conta dividida mantém o cliente da época.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException se a comanda não
     *         estiver aberta
     */
    Comanda linkCustomer(Long comandaId, Long customerId, String username);

    /**
     * Lança uma sessão do cardápio da mesa (PDV-F021): cobra o preço da faixa, mais o upgrade se
     * {@code vasoGrande}, registra a essência (texto) e aloca vaso + utensílios inclusos. Não move
     * estoque.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.SessionTierNotFoundException faixa
     *         inexistente ou inativa
     * @throws com.cernecommerce.core.domain.exception.pdv.SessionAssetUnavailableException sem
     *         utensílio livre
     * @throws com.cernecommerce.core.domain.exception.pdv.SessionMenuConflictException vaso não
     *         configurado
     */
    Comanda addSession(Long comandaId, Long tierId, String essencia, boolean vasoGrande, String username);

    /**
     * Lança uma sessão com carvão, adicionais pagos e, no rosh duplo, o 2º rosh já pago (PDV-F024).
     * Preço da sessão = faixa + upgrade de vaso + Σ adicionais. No {@code duplo} as duas linhas nascem
     * na mesma transação: a sessão em {@code AGUARDANDO_PAGAMENTO} (PDV-F027 — o pagamento a leva ao
     * preparo) e o rosh a R$ 0 (cortesia, ligado) em {@code NA_FILA}, sem utensílio novo. PDV-F027: a
     * mesa aceita sessões em paralelo; o limite é o utensílio livre.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.SessionAddonNotFoundException adicional
     *         inexistente ou inativo
     */
    Comanda addSession(Long comandaId, AddSessionCommand command, String username);

    /**
     * PDV-F027 — lança outra sessão com a configuração de {@code sourceItemId} (faixa, vaso, carvão e
     * adicionais), pelo preço <b>atual</b> do cardápio. Sabor nulo repete o da sessão de origem. A
     * origem pode estar em qualquer status, inclusive recolhida: os utensílios são reservados de novo.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.NotASessionLineException se
     *         {@code sourceItemId} não for uma sessão desta comanda
     */
    Comanda repeatSession(Long comandaId, Long sourceItemId, RepeatSessionCommand command, String username);

    /**
     * O pedido de "repetir sessão" (PDV-F027). Tudo opcional.
     *
     * @param essencia sabor da nova sessão; nulo repete o da origem
     * @param duplo rosh duplo: cria também o 2º rosh já pago, com {@code essenciaRosh}
     * @param tierIdRosh faixa do 2º rosh; nula usa a da sessão
     * @param pagarNoFinal PDV-F034 — ver {@link AddSessionCommand}. Não herda da origem: é decisão
     *        de quem lança agora, e a permissão é conferida no controller sobre o corpo.
     */
    record RepeatSessionCommand(String essencia, boolean duplo, String essenciaRosh, Long tierIdRosh,
            boolean pagarNoFinal) {

        public RepeatSessionCommand(String essencia, boolean duplo, String essenciaRosh, Long tierIdRosh) {
            this(essencia, duplo, essenciaRosh, tierIdRosh, false);
        }
    }

    /**
     * O pedido de sessão (PDV-F024).
     *
     * @param adicionalIds adicionais pagos; id repetido cobra duas vezes
     * @param duplo rosh duplo: cria também o 2º rosh já pago, com {@code essenciaRosh}
     * @param tierIdRosh faixa do 2º rosh; nula usa a da sessão
     * @param pagarNoFinal PDV-F034 — a sessão vai direto ao preparo e fica a receber até a conta.
     *        Quem chama confere {@code PDV_SESSION_PAY_LATER}.
     */
    record AddSessionCommand(Long tierId, String essencia, boolean vasoGrande, Charcoal carvao,
            List<Long> adicionalIds, boolean duplo, String essenciaRosh, Long tierIdRosh, boolean pagarNoFinal) {

        public AddSessionCommand {
            adicionalIds = adicionalIds == null ? List.of() : List.copyOf(adicionalIds);
        }

        public AddSessionCommand(Long tierId, String essencia, boolean vasoGrande, Charcoal carvao,
                List<Long> adicionalIds, boolean duplo, String essenciaRosh, Long tierIdRosh) {
            this(tierId, essencia, vasoGrande, carvao, adicionalIds, duplo, essenciaRosh, tierIdRosh, false);
        }

        public static AddSessionCommand simple(Long tierId, String essencia, boolean vasoGrande) {
            return new AddSessionCommand(tierId, essencia, vasoGrande, null, List.of(), false, null, null);
        }
    }

    /**
     * Lança o 2º rosh de uma sessão (PDV-F021): nova essência, mesmos utensílios, ligado à sessão
     * (fecha junto com ela). De graça no primeiro rosh extra da sessão quando a mesa foi aberta em
     * dia de duplo rosh; pelo preço da faixa ({@code tierId}, ou a da sessão se nulo) nos demais.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.NotASessionLineException se
     *         {@code sessionItemId} não for uma sessão em aberto desta comanda
     */
    Comanda addRoshExtra(Long comandaId, Long sessionItemId, Long tierId, String essencia, String username);

    /**
     * Encerra a mesa cujas linhas já foram todas cobradas em fechamentos parciais (PDV-F023). Não
     * gera pedido: o cabeçalho aponta o último pedido que cobrou a mesa.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaEmptyException mesa sem nenhuma
     *         linha — a saída é cancelar
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaHasOpenItemsException ainda há
     *         linha a cobrar
     * @throws com.cernecommerce.core.domain.exception.pdv.SessionNotCollectedException sessão ainda
     *         no salão
     */
    Comanda finishComanda(Long comandaId, String username);

    /**
     * Avança o status de uma sessão (PDV-F023): {@code NA_FILA → PREPARANDO → ENTREGUE → RECOLHIDO},
     * mais {@code PREPARANDO → RECOLHIDO}. Recolher libera os utensílios quando a sessão e os roshs
     * ligados a ela estão todos recolhidos, e promove a próxima linha da fila para o preparo.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.NotASessionLineException linha sem status
     * @throws com.cernecommerce.core.domain.exception.pdv.InvalidSessionTransitionException
     *         transição fora da ordem
     */
    Comanda updateSessionStatus(Long comandaId, Long itemId, SessionStatus status, String username);

    /**
     * Junta duas mesas que viraram uma conta só (PDV-F016): as linhas em aberto de
     * {@code fromComandaId} passam para {@code toComandaId}, e a origem é encerrada.
     *
     * <p><b>PDV-F031 — origem com linha já cobrada também junta.</b> Vão as linhas em aberto e o
     * grupo inteiro de toda sessão de narguilé ainda no salão (paga ou não), com os utensílios. A
     * linha cobrada que não está no salão fica na origem, e os pedidos pagos continuam apontando
     * para ela: a origem termina {@code FECHADA} no último pedido dela.</p>
     *
     * <p><b>Nenhum estoque se move.</b> A mercadoria não voltou para a prateleira nem saiu de novo —
     * ela mudou de conta. Por isso o merge <b>não</b> passa por {@code cancelComanda}, que devolveria
     * tudo por {@code ENTRADA}: a origem sem nada cobrado termina {@code CANCELADA} por um caminho
     * próprio, sem tocar em saldo. O status é o mesmo, o significado não, e é o evento
     * {@code COMANDA_MERGED} e o motivo "Juntada à comanda #X" que guardam a diferença.</p>
     *
     * <p>As linhas mantêm os ids ao mudar de comanda, o que preserva os vínculos de
     * {@code linkedItemId} — um {@code OPEN_ROSH} e as trocas dele chegam juntos e ainda ligados.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException se qualquer uma
     *         das duas não estiver aberta
     * @throws com.cernecommerce.core.domain.exception.pdv.ComandaMergeNotAllowedException se for a
     *         mesma comanda, ou se os depósitos diferirem
     */
    Comanda mergeComanda(Long fromComandaId, Long toComandaId, String username);

    /**
     * Varre as comandas esquecidas abertas (PDV-F013) — as que passaram de {@code staleHours} sem
     * serem fechadas nem canceladas.
     *
     * <p><b>Age apenas nas que não devem nada, e essa assimetria é o desenho, não uma etapa
     * faltando.</b> As VAZIAS são canceladas; as TODAS PAGAS sem sessão no salão são encerradas como
     * um {@link #finishComanda} que ninguém apertou (PDV-F032, {@code closedBy = system}). Nenhuma das
     * duas mexe em estoque nem em dinheiro, e o único efeito delas abertas é travar o fechamento do
     * caixa (PDV-C005).</p>
     *
     * <p>Mesa que ainda deve algo não é tocada. Cancelar devolveria o estoque por {@code ENTRADA}, e
     * numa mesa com consumo real a essência já foi <i>queimada</i>: a devolução automática criaria
     * saldo que fisicamente não existe — o saldo mentindo para cima, que só apareceria no próximo
     * balanço. Essas geram <b>uma</b> notificação agregada para quem tem {@code PDV_COMANDA_MANAGE};
     * a decisão entre cobrar, fechar como perda ou cancelar é humana. Vêm de consulta separada, lidas
     * sem trava, para não ocuparem o lote das que a varredura resolve (PDV-C024).</p>
     *
     * <p><b>Não exige sessão de caixa aberta</b>, ao contrário de {@link #cancelComanda}. É
     * deliberado: a comanda mais presa de todas é a órfã de um caixa já fechado (possível para o que
     * existia antes de PDV-C005), e exigir sessão aberta faria a varredura recusar exatamente o caso
     * que ela existe para resolver.</p>
     *
     * @param staleHours horas desde a abertura a partir das quais a mesa conta como esquecida
     * @param batchSize teto de comandas por consulta (as resolvidas e as alertadas, cada uma)
     * @return quantas foram canceladas, encerradas e só sinalizadas
     */
    StaleComandaSweepResult sweepStaleComandas(int staleHours, int batchSize);

    /**
     * Resultado de uma passada da varredura: {@code cancelled} são as vazias que foram canceladas,
     * {@code finished} as todas pagas que foram encerradas (PDV-F032), e {@code flagged} as que
     * ainda devem algo e só entraram no alerta.
     */
    record StaleComandaSweepResult(int cancelled, int finished, int flagged) {
    }
}
