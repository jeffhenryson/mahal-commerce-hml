package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.avatar.AvatarTooLargeException;
import com.cernecommerce.core.domain.exception.avatar.InvalidAvatarFormatException;
import com.cernecommerce.core.domain.exception.user.UserNotFoundException;
import com.cernecommerce.core.domain.model.storage.CompressedImage;
import com.cernecommerce.core.domain.model.storage.FileServeResult;
import com.cernecommerce.core.domain.model.storage.ImageFormat;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.ports.in.AvatarUseCase;
import com.cernecommerce.core.ports.out.storage.AvatarStoragePort;
import com.cernecommerce.core.ports.out.storage.ImageCompressorPort;
import com.cernecommerce.core.ports.out.user.UserCachePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;

public class AvatarService implements AvatarUseCase {

    private final UserRepository userRepository;
    private final AvatarStoragePort storagePort;
    private final UserCachePort userCachePort;
    private final ImageCompressorPort compressor;
    private final long maxSizeBytes;
    private final int maxDimension;
    private final String avatarBaseUrl;

    public AvatarService(UserRepository userRepository, AvatarStoragePort storagePort,
                         UserCachePort userCachePort, ImageCompressorPort compressor,
                         long maxSizeBytes, int maxDimension, String avatarBaseUrl) {
        this.userRepository = userRepository;
        this.storagePort = storagePort;
        this.userCachePort = userCachePort;
        this.compressor = compressor;
        this.maxSizeBytes = maxSizeBytes;
        this.maxDimension = maxDimension;
        this.avatarBaseUrl = avatarBaseUrl;
    }

    @Override
    @Transactional
    public String upload(String username, byte[] bytes, String originalFilename) {
        if (bytes.length > maxSizeBytes) throw new AvatarTooLargeException(maxSizeBytes);

        ImageFormat format = detectFormat(bytes);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (user.getAvatarFilename() != null) {
            storagePort.delete(user.getAvatarFilename());
        }

        // Avatar aparece em 40–80 px na tela; guardar a foto crua do celular é só desperdício.
        CompressedImage image = compressor.compress(bytes, format, maxDimension);
        String filename = storagePort.save(image.bytes(), image.format().extension());
        user.setAvatar(filename);
        userRepository.save(user);
        userCachePort.evict(username);

        return storagePort.getPublicUrl(filename)
                .orElse(avatarBaseUrl + "/avatars/" + filename);
    }

    @Override
    @Transactional
    public void delete(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (user.getAvatarFilename() == null) return;

        storagePort.delete(user.getAvatarFilename());
        user.clearAvatar();
        userRepository.save(user);
        userCachePort.evict(username);
    }

    @Override
    @Transactional(readOnly = true)
    public FileServeResult serve(String filename) {
        java.util.Optional<String> publicUrl = storagePort.getPublicUrl(filename);
        if (publicUrl.isPresent()) {
            return new FileServeResult.Redirect(publicUrl.get());
        }
        return storagePort.load(filename).map(stream -> {
            try (InputStream is = stream) {
                String ext = filename.contains(".")
                        ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                        : "";
                return (FileServeResult) new FileServeResult.LocalFile(is.readAllBytes(), ext);
            } catch (IOException e) {
                return (FileServeResult) new FileServeResult.NotFound();
            }
        }).orElse(new FileServeResult.NotFound());
    }

    /**
     * Validação por magic bytes, hoje em {@link ImageFormat} — foi extraída daqui quando o upload
     * de imagem de produto passou a precisar exatamente da mesma regra. A tradução do "não
     * reconheci" para a exceção de avatar fica neste ponto, porque o formato aceito é comum aos
     * dois casos mas o erro devolvido ao cliente não é.
     */
    private ImageFormat detectFormat(byte[] bytes) {
        return ImageFormat.detect(bytes)
                .orElseThrow(InvalidAvatarFormatException::new);
    }
}
