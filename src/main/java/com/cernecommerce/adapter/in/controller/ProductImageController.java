package com.cernecommerce.adapter.in.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import com.cernecommerce.core.domain.model.storage.FileServeResult;
import com.cernecommerce.core.domain.model.storage.ImageFormat;
import com.cernecommerce.core.ports.in.ProductImageUseCase;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Serve os arquivos de imagem de produto. Rota <b>pública</b>, fora de {@code /estoque}: a
 * vitrine do marketplace precisa renderizar a foto sem token, do mesmo jeito que
 * {@code /avatars/{filename}}.
 *
 * <p>O upload fica no {@code EstoqueController}, sob {@code ESTOQUE_PRODUCT_MANAGE} — escrever no
 * catálogo é operação de estoque e entra na matriz de permissões do módulo; ler a imagem já
 * publicada não é.</p>
 */
@RestController
public class ProductImageController {

    private final ProductImageUseCase productImageUseCase;

    public ProductImageController(ProductImageUseCase productImageUseCase) {
        this.productImageUseCase = productImageUseCase;
    }

    @Operation(summary = "Serve o arquivo de imagem de produto (público, cache longo). "
            + "Com S3/CDN retorna 308 redirect para a URL pública; com armazenamento local serve os bytes.")
    @ApiResponse(responseCode = "200", description = "O arquivo de imagem",
            content = @Content(mediaType = "image/*", schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/product-images/{filename}")
    public ResponseEntity<?> serve(@PathVariable String filename) {
        // Mesmo guard de AvatarController: o filename entra na resolução de um Path, e ".." ou
        // separador aqui viraria leitura de arquivo arbitrário. O adapter local também barra,
        // mas a defesa mais barata é não deixar chegar lá.
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return ResponseEntity.notFound().build();
        }
        return switch (productImageUseCase.serve(filename)) {
            case FileServeResult.Redirect r -> ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT)
                    .header(HttpHeaders.LOCATION, r.url())
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).immutable())
                    .build();
            case FileServeResult.LocalFile f -> ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(ImageFormat.contentTypeOf(f.extension())))
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).immutable())
                    .body(f.bytes());
            case FileServeResult.NotFound ignored -> ResponseEntity.notFound().build();
        };
    }
}
