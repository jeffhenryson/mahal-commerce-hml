package com.cernecommerce.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "avatar")
public class AvatarProperties {

    private String baseUrl = "http://localhost:8080";
    private long maxSizeBytes = 2_097_152L;
    /** Maior lado gravado, em px — avatar aparece pequeno, 400 cobre tela retina com folga. */
    private int maxDimension = 400;
    private final Storage storage = new Storage();
    private final S3 s3 = new S3();

    /**
     * {@code storage} é uma classe aninhada, e não os campos planos {@code storageType}/
     * {@code storageDir} que existiam antes: o binder do Spring Boot trata o ponto como separador
     * de nível, então {@code avatar.storage.dir} nunca casava com um campo {@code storageDir} de
     * nível único e bindava em silêncio no default. Efeito prático do bug: definir
     * {@code AVATAR_STORAGE_DIR} não mudava nada e os avatares iam sempre para
     * {@code ./uploads/avatars}. Passava despercebido porque o valor declarado em
     * application-hml/prod é idêntico ao default, e porque o {@code @ConditionalOnProperty} de
     * {@code avatar.storage.type} lê o Environment direto, sem passar por este bean — a escolha
     * local/s3 sempre funcionou, só o diretório é que não.
     */
    public Storage getStorage() { return storage; }

    public String getStorageType() { return storage.getType(); }

    public String getStorageDir() { return storage.getDir(); }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public long getMaxSizeBytes() { return maxSizeBytes; }
    public void setMaxSizeBytes(long maxSizeBytes) { this.maxSizeBytes = maxSizeBytes; }

    public int getMaxDimension() { return maxDimension; }
    public void setMaxDimension(int maxDimension) { this.maxDimension = maxDimension; }

    public S3 getS3() { return s3; }

    public static class Storage {
        private String type = "local";
        private String dir = "./uploads/avatars";

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
