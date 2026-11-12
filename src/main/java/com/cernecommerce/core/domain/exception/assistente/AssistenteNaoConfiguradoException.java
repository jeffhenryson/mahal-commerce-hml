package com.cernecommerce.core.domain.exception.assistente;

/** A loja não salvou a chave da IA ou deixou a integração desativada (409 ASSISTENTE_NAO_CONFIGURADO). */
public class AssistenteNaoConfiguradoException extends RuntimeException {
    public AssistenteNaoConfiguradoException() {
        super("O assistente não está configurado. Peça a um administrador para ativar a integração de IA.");
    }
}
