package com.cernecommerce.adapter.in.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import com.cernecommerce.core.domain.exception.avatar.InvalidAvatarFormatException;
import com.cernecommerce.core.domain.model.storage.FileServeResult;
import com.cernecommerce.core.domain.model.storage.ImageFormat;
import com.cernecommerce.core.ports.in.AvatarUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class AvatarController {

    private final AvatarUseCase avatarUseCase;

    public AvatarController(AvatarUseCase avatarUseCase) {
        this.avatarUseCase = avatarUseCase;
    }

    @Operation(summary = "Faz upload do avatar do usuário autenticado")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/users/me/avatar")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        if (file == null || file.isEmpty()) throw new InvalidAvatarFormatException();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidAvatarFormatException();
        }
        String avatarUrl = avatarUseCase.upload(authentication.getName(), bytes, file.getOriginalFilename());
        return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
    }

    @Operation(summary = "Remove o avatar do usuário autenticado")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/users/me/avatar")
    public ResponseEntity<Void> delete(Authentication authentication) {
        avatarUseCase.delete(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Serve o arquivo de avatar (público, cache longo). " +
               "Com S3/CDN retorna 308 redirect para a URL pública; com armazenamento local serve os bytes.")
    @ApiResponse(responseCode = "200", description = "O arquivo de imagem",
            content = @Content(mediaType = "image/*", schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/avatars/{filename}")
    public ResponseEntity<?> serve(@PathVariable String filename) {
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return ResponseEntity.notFound().build();
        }
        return switch (avatarUseCase.serve(filename)) {
            case FileServeResult.Redirect r -> ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT)
                    .header(HttpHeaders.LOCATION, r.url())
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).immutable())
                    .build();
            case FileServeResult.LocalFile f -> ResponseEntity.ok()
                    .contentType(resolveMediaType(f.extension()))
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).immutable())
                    .body(f.bytes());
            case FileServeResult.NotFound ignored -> ResponseEntity.notFound().build();
        };
    }

    private MediaType resolveMediaType(String extension) {
        return MediaType.parseMediaType(ImageFormat.contentTypeOf(extension));
    }
}
