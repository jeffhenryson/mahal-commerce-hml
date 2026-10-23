package com.cernecommerce.adapter.out.redis;

import com.cernecommerce.core.domain.exception.ratelimit.RateLimitExceededException;
import com.cernecommerce.core.ports.out.ratelimit.ResourceRateLimiterPort;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Profile({"hml", "prod"})
public class RedisResourceRateLimiterAdapter implements ResourceRateLimiterPort {

    private static final String KEY_PREFIX = "rate:resource:";

    // PLAT-C030 / cobertura de testes de segurança — buckets fixos por recurso sensível.
    // crm-export: exportação de PII é rara e não deve ser repetida em massa por um mesmo usuário.
    // estoque-movements: listagem interna, limite generoso para não atrapalhar uso legítimo do PDV/admin.
    // notifications-stream: cada abertura de SSE é uma conexão persistente, e o EventSource do
    //   navegador reconecta sozinho a cada erro. O limite existe para conter o laço de reconexão,
    //   não o uso normal — um operador abre o stream uma vez por sessão de trabalho. Chave é o
    //   usuário, não o IP (PLAT-C051): o salão inteiro sai por um NAT só.
    // shop-catalog: endpoint público sem autenticação — chave é o IP, protege contra scraping.
    private static final Map<String, BucketPolicy> POLICIES = Map.of(
            "crm-export", new BucketPolicy(3600, 5),
            "estoque-movements", new BucketPolicy(60, 60),
            "notifications-stream", new BucketPolicy(60, 30),
            "shop-catalog", new BucketPolicy(60, 120),
            // ECM-F010 — checkout do app, por cliente: cada pedido segura estoque até o fim do dia
            // e avisa o caixa. Folgado para quem erra e refaz, apertado para quem trava a vitrine.
            "shop-checkout", new BucketPolicy(600, 10),
            // Página do QR da mesa (pública, por IP). A escrita é o identificar/cadastrar/pedir:
            // apertada o bastante para não servir de oráculo de CPF, folgada para uma mesa cheia.
            "salao-publico-leitura", new BucketPolicy(60, 120),
            "salao-publico-escrita", new BucketPolicy(60, 30),
            // Código de 4 dígitos da mesa: 10 tentativas a cada 10 min por IP.
            "salao-publico-acesso", new BucketPolicy(600, 10));

    // Mesmo script atômico usado em RedisLoginRateLimiterAdapter: adiciona entrada, remove
    // expirados, conta e decide em um único round-trip.
    private static final RedisScript<Long> SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local key      = KEYS[1]
            local score    = tonumber(ARGV[1])
            local cutoff   = tonumber(ARGV[2])
            local member   = ARGV[3]
            local ttl      = tonumber(ARGV[4])
            local maxReqs  = tonumber(ARGV[5])
            redis.call('ZADD', key, score, member)
            redis.call('ZREMRANGEBYSCORE', key, 0, cutoff)
            local count = redis.call('ZCARD', key)
            redis.call('EXPIRE', key, ttl)
            if count <= maxReqs then return 1 else return 0 end
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisResourceRateLimiterAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void checkOrThrow(String bucket, String key) {
        BucketPolicy policy = POLICIES.getOrDefault(bucket, new BucketPolicy(60, 30));
        long nowMillis = Instant.now().toEpochMilli();
        long cutoffMillis = nowMillis - (policy.windowSeconds() * 1000);
        String member = nowMillis + ":" + UUID.randomUUID();

        Long allowed = redis.execute(SLIDING_WINDOW_SCRIPT,
                List.of(KEY_PREFIX + bucket + ":" + key),
                String.valueOf(nowMillis),
                String.valueOf(cutoffMillis),
                member,
                String.valueOf(policy.windowSeconds()),
                String.valueOf(policy.maxRequests()));

        if (allowed == null || allowed != 1L) {
            throw new RateLimitExceededException(bucket, policy.windowSeconds());
        }
    }

    private record BucketPolicy(long windowSeconds, int maxRequests) {
    }
}
