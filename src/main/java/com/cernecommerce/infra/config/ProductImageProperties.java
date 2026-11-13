package com.cernecommerce.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do armazenamento das imagens de produto. Espelha {@link AvatarProperties}, mas com
 * chaves próprias — os dois tipos de arquivo têm volume e ciclo de vida bem diferentes, e nada
 * obriga a foto do catálogo a morar no mesmo lugar que o avatar do usuário.
 *
 * <p>{@code storage} é uma classe aninhada de verdade, e não um par de campos {@code storageType}/
 * {@code storageDir}: {@code product.image.storage.type} só chega ao objeto se houver aninhamento
 * correspondente, porque o binder do Spring Boot trata o ponto como separador de nível e não
 * casaria a chave com um campo camelCase de nível único.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "product.image")
public class ProductImageProperties {

    private String baseUrl = "http://localhost:8080";

    /** 5 MB — o admin redimensiona para 600 px antes de enviar, então é folga generosa. */
    private long maxSizeBytes = 5_242_880L;

    /**
     * Maior lado gravado, em px. O admin já envia em 600 px, então o que vem de lá passa direto;
     * isto só pega upload que chegou cru (API direta, cliente antigo).
     */
    private int maxDimension = 1200;

    private final Storage storage = new Storage();
    private final S3 s3 = new S3();

    public int getMaxDimension() { return maxDimension; }
    public void setMaxDimension(int maxDimension) { this.maxDimension = maxDimension; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public long getMaxSizeBytes() { return maxSizeBytes; }
    public void setMaxSizeBytes(long maxSizeBytes) { this.maxSizeBytes = maxSizeBytes; }

    public Storage getStorage() { return storage; }

    public S3 getS3() { return s3; }

    public static class Storage {
        private String type = "local";
        private String dir = "./uploads/product-images";

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getDir() { return dir; }
        public void setDir(String dir) { this.dir = dir; }
    }

    public static class S3 {
        private String bucket = "";
        private String region = "us-east-1";
        private String publicUrlBase = "";
        private String accessKey;
        private String secretKey;

        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }

        public String getPublicUrlBase() { return publicUrlBase; }
        public void setPublicUrlBase(String publicUrlBase) { this.publicUrlBase = publicUrlBase; }

        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    }
}
