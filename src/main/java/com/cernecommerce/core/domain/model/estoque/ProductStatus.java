package com.cernecommerce.core.domain.model.estoque;

/**
 * Status de publicação de um {@link Product} (EST-F023).
 *
 * <p>{@link #RASCUNHO} deixa o produto/kit fora das vitrines e listagens que filtram por
 * publicado, sem exigir os campos normalmente esperados de um item à venda — é o rascunho salvo
 * antes de o cadastro estar completo. Ortogonal a {@code active} (disponibilidade para venda) e a
 * {@code type} (produto ou kit): um rascunho pode estar {@code active=true} e mesmo assim não
 * aparecer em lugar nenhum, porque o filtro de publicação é outro.</p>
 *
 * <p>{@link #ARQUIVADO} é a exclusão lógica de um produto já publicado (EST-F054): some de
 * listagens, buscas, leitor de código de barras, vitrine, kits e reposição, mas o histórico
 * (vendas, movimentações, compras) continua apontando para o SKU, que fica reservado. Como
 * {@code RASCUNHO}, é ortogonal a {@code active} — arquivar não mexe nele nem no saldo.</p>
 */
public enum ProductStatus {
    RASCUNHO,
    ATIVO,
    ARQUIVADO
}
