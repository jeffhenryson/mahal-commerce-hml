package com.cernecommerce.core.domain.exception.pagamento;

/** Correção de pagamento sem motivo (PDV-F027) — o motivo é o que justifica o lastro. */
public class CorrectionReasonRequiredException extends RuntimeException {

    public CorrectionReasonRequiredException() {
        super("Informe o motivo da correção");
    }
}
