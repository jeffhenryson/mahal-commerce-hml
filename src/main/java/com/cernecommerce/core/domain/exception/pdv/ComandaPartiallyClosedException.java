package com.cernecommerce.core.domain.exception.pdv;

/**
 * A comanda já teve parte da conta cobrada (PDV-F017) e por isso não pode ser <b>cancelada</b>
 * (PDV-C021).
 *
 * <p>Cancelar devolve ao estoque tudo o que a mesa lançou e a encerra como abandono. Com linha já
 * cobrada isso é falso nas duas pontas: a mercadoria vendida voltaria à prateleira, e a mesa sairia
 * do histórico e dos indicadores (que contam só {@code FECHADA}) com pedidos pagos pendurados nela.
 * Desde que a sessão de narguilé é paga no lançamento (PDV-F027), esse é o estado normal do salão.
 * A saída é encerrar com {@code finish} ou remover as linhas ainda abertas.</p>
 *
 * <p>Até PDV-F031 também barrava a junção de mesas; a junção passou a aceitar a origem paga.</p>
 */
public class ComandaPartiallyClosedException extends RuntimeException {

    public ComandaPartiallyClosedException(Long comandaId) {
        super("A comanda " + comandaId + " já teve parte da conta cobrada e não pode ser cancelada: "
                + "remova as linhas em aberto e encerre a mesa.");
    }
}
