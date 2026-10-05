package com.cernecommerce.core.ports.out;

/**
 * Cifra segredos guardados em repouso (tokens de integração). O valor cifrado é opaco e seguro
 * para gravar em {@code system_config}; só o adapter conhece a chave.
 */
public interface SecretCipherPort {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
