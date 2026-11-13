package com.cernecommerce.core.ports.out.storage;

import com.cernecommerce.core.domain.model.storage.CompressedImage;
import com.cernecommerce.core.domain.model.storage.ImageFormat;

/**
 * Reduz uma imagem já validada antes de ela ir para o storage: limita o maior lado a
 * {@code maxDimension} e recomprime. É a rede de segurança do backend — o frontend-admin já
 * redimensiona antes de enviar, mas upload direto na API, cliente antigo ou avatar podem chegar
 * com a foto crua do celular (3–4 MB, 4000 px).
 *
 * <p>Contrato: nunca piora. Imagem que já está pequena, ou que a implementação não consegue
 * decodificar, volta como veio — o tamanho de entrada já foi limitado pelo serviço.</p>
 */
@FunctionalInterface
public interface ImageCompressorPort {

    /**
     * @throws com.cernecommerce.core.domain.exception.storage.ImageDimensionsTooLargeException
     *         quando a quantidade de pixels passa do limite seguro de decodificação
     *         (proteção contra decompression bomb: arquivo pequeno com dimensões gigantes).
     */
    CompressedImage compress(byte[] bytes, ImageFormat format, int maxDimension);
}
