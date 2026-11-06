package com.cernecommerce.core.domain.exception.user;

/** Senha temporária, 2FA e sessões do outro não valem para si: pulariam a confirmação da senha atual. */
public class SelfTargetNotAllowedException extends RuntimeException {
    public SelfTargetNotAllowedException() {
        super("Use as configurações da sua própria conta para esta ação");
    }
}
