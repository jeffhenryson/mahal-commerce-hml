package com.cernecommerce.core.domain.model.estoque;

/** Por que uma lata aberta deixou de ser a lata em uso (EST-F027). */
public enum OpenPackageCloseReason {

    /**
     * Rendeu todas as sessões declaradas em {@code sessionsPerUnit} e a próxima abriu outra. Só em
     * latas fechadas até EST-F037: desde então a lata não fecha mais sozinha pela estimativa.
     */
    EXHAUSTED,

    /**
     * O atendente apertou "Repor essência" antes de esgotar — que é o caso comum, porque a lata
     * acaba fisicamente antes da conta fechar. A sobra ({@code uses < sessionsPerUnit} na linha
     * fechada) fica no histórico e <b>não</b> vira ajuste de estoque: a lata já tinha saído do
     * saldo quando foi aberta, e transformar o resto em perda criaria movimento que ninguém pediu
     * para medir uma quantidade que ninguém mediu.
     */
    REPLACED,

    /**
     * EST-F037 — o atendente marcou "Acabou": a lata fecha sem abrir outra e sem baixa. A próxima
     * sessão do sabor abre uma nova, com {@code SAIDA} de 1, como quando não há lata aberta.
     */
    FINISHED
}
