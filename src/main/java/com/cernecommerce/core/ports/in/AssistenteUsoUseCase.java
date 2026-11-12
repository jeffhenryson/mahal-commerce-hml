package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.assistente.FeedbackRegistro;
import com.cernecommerce.core.domain.model.assistente.NotaFeedback;
import com.cernecommerce.core.domain.model.assistente.UsoMensal;
import com.cernecommerce.core.domain.model.assistente.UsoTokens;

import java.time.YearMonth;
import java.util.List;

/** Custo e limites do assistente: tokens por resposta, limite mensal da loja e relatórios. */
public interface AssistenteUsoUseCase {

    void registrar(String mensagemId, UsoTokens uso);

    /**
     * @throws com.cernecommerce.core.domain.exception.assistente.AssistenteLimiteMensalException
     *         quando o mês corrente já passou do limite configurado
     */
    void exigirDentroDoLimite();

    UsoMensal usoDoMes(YearMonth mes);

    List<FeedbackRegistro> feedbacks(NotaFeedback nota, int limite);
}
