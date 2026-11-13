package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.avatar.AvatarTooLargeException;
import com.cernecommerce.core.domain.exception.avatar.InvalidAvatarFormatException;
import com.cernecommerce.core.domain.exception.user.UserNotFoundException;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.storage.CompressedImage;
import com.cernecommerce.core.domain.model.storage.ImageFormat;
import com.cernecommerce.core.ports.out.storage.AvatarStoragePort;
import com.cernecommerce.core.ports.out.storage.ImageCompressorPort;
import com.cernecommerce.core.ports.out.user.UserCachePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    @Mock UserRepository userRepository;
    @Mock AvatarStoragePort storagePort;
    @Mock UserCachePort userCachePort;

    AvatarService avatarService;

    private static final long MAX_BYTES = 2 * 1024 * 1024; // 2 MB

    @BeforeEach
    void setUp() {
        avatarService = new AvatarService(userRepository, storagePort, userCachePort,
                (b, f, d) -> new CompressedImage(b, f), MAX_BYTES, 400, "http://localhost:8080");
    }

    // --- magic bytes JPEG ---
    private static final byte[] JPEG_BYTES = new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, 0x00};

    // --- magic bytes PNG ---
    private static final byte[] PNG_BYTES = new byte[]{
        (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00
    };

    // --- magic bytes WebP (RIFF....WEBP) ---
    private static final byte[] WEBP_BYTES = new byte[]{
        0x52, 0x49, 0x46, 0x46,   // RIFF
        0x00, 0x00, 0x00, 0x00,   // file size (irrelevant for detection)
        0x57, 0x45, 0x42, 0x50,   // WEBP
        0x00
    };

    // --- invalid (PDF header) ---
    private static final byte[] PDF_BYTES = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D};

    private User userWithoutAvatar() {
        return User.fromPersisted(1L, "alice", "hashed", true, null, false, null, null, null, Set.of(), null, null);
    }

    private User userWithAvatar(String filename) {
        User u = User.fromPersisted(1L, "alice", "hashed", true, null, false, null, filename, null, Set.of(), null, null);
        return u;
    }

    @Test
    void upload_jpeg_succeeds_and_returns_avatar_url() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWithoutAvatar()));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storagePort.save(any(), eq("jpg"))).thenReturn("uuid.jpg");

        String url = avatarService.upload("alice", JPEG_BYTES, "photo.jpg");

        assertThat(url).isEqualTo("http://localhost:8080/avatars/uuid.jpg");
        verify(storagePort).save(JPEG_BYTES, "jpg");
        verify(userCachePort).evict("alice");
    }

    @Test
    void upload_png_succeeds() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWithoutAvatar()));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storagePort.save(any(), eq("png"))).thenReturn("uuid.png");

        String url = avatarService.upload("alice", PNG_BYTES, "photo.png");

        assertThat(url).endsWith("uuid.png");
    }

    @Test
    void upload_grava_a_versao_reduzida_pelo_compressor_no_tamanho_de_avatar() {
        ImageCompressorPort compressor = mock(ImageCompressorPort.class);
        byte[] reduzido = new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, 0x01};
        when(compressor.compress(PNG_BYTES, ImageFormat.PNG, 400))
                .thenReturn(new CompressedImage(reduzido, ImageFormat.JPEG));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWithoutAvatar()));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storagePort.save(reduzido, "jpg")).thenReturn("uuid.jpg");
        AvatarService comCompressor = new AvatarService(userRepository, storagePort, userCachePort,
                compressor, MAX_BYTES, 400, "http://localhost:8080");

        assertThat(comCompressor.upload("alice", PNG_BYTES, "photo.png")).endsWith("uuid.jpg");
        verify(storagePort).save(reduzido, "jpg");
    }

    @Test
    void upload_webp_succeeds() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWithoutAvatar()));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storagePort.save(any(), eq("webp"))).thenReturn("uuid.webp");

        String url = avatarService.upload("alice", WEBP_BYTES, "photo.webp");

        assertThat(url).endsWith("uuid.webp");
    }

    @Test
    void upload_pdf_throws_invalid_format() {
        assertThatThrownBy(() -> avatarService.upload("alice", PDF_BYTES, "doc.pdf"))
                .isInstanceOf(InvalidAvatarFormatException.class);
        verifyNoInteractions(storagePort, userRepository);
    }

    @Test
    void upload_too_large_throws_avatar_too_large() {
        byte[] tooBig = new byte[(int) MAX_BYTES + 1];
        tooBig[0] = (byte) 0xFF;

        assertThatThrownBy(() -> avatarService.upload("alice", tooBig, "big.jpg"))
                .isInstanceOf(AvatarTooLargeException.class);
        verifyNoInteractions(storagePort, userRepository);
    }

    @Test
    void upload_replaces_existing_avatar_and_deletes_old_file() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWithAvatar("old-uuid.jpg")));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storagePort.save(any(), eq("jpg"))).thenReturn("new-uuid.jpg");

        avatarService.upload("alice", JPEG_BYTES, "photo.jpg");

        verify(storagePort).delete("old-uuid.jpg");
        verify(storagePort).save(JPEG_BYTES, "jpg");
    }

    @Test
    void upload_unknown_user_throws_not_found() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> avatarService.upload("ghost", JPEG_BYTES, "photo.jpg"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void delete_removes_file_and_clears_avatar() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWithAvatar("uuid.jpg")));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        avatarService.delete("alice");

        verify(storagePort).delete("uuid.jpg");
        verify(userCachePort).evict("alice");
    }

    @Test
    void delete_without_avatar_is_noop() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWithoutAvatar()));

        avatarService.delete("alice");

        verifyNoInteractions(storagePort);
        verifyNoInteractions(userCachePort);
    }
}
