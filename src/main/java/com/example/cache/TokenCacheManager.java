package com.example.cache;

import io.vertx.core.json.JsonObject;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;

/**
 * Token 缓存管理器 - 使用 Ehcache 3.x
 *
 * <p>缓存已验证的 JWT token 解析结果，避免每次请求都调用 Keycloak JWKS 验证。</p>
 *
 * <h3>缓存策略：</h3>
 * <ul>
 *   <li>Key: token 的 SHA-256 哈希值（前 32 字符）</li>
 *   <li>Value: TokenInfo 对象（principal, roles, username, expiresAt）</li>
 *   <li>TTL: 使用 JWT 自身的 exp 过期时间，最大 24 小时</li>
 *   <li>最大条目: 10000（可配置）</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>
 * TokenCacheManager cache = TokenCacheManager.getInstance();
 *
 * // 查询缓存
 * TokenCacheManager.TokenInfo cached = cache.get(token);
 * if (cached != null) {
 *     // 使用缓存结果
 * }
 *
 * // 写入缓存
 * cache.put(token, principal, roles, username, expiresAtMillis);
 *
 * // 清除缓存
 * cache.invalidate(token);
 * </pre>
 */
public class TokenCacheManager {

    private static final Logger LOG = LoggerFactory.getLogger(TokenCacheManager.class);

    private static TokenCacheManager INSTANCE;

    private final Cache<String, TokenInfo> cache;
    private final CacheManager cacheManager;
    private final boolean enabled;
    private final int maxSize;

    /** 缓存的 Token 信息 */
    public static class TokenInfo {
        private final JsonObject principal;
        private final Set<String> roles;
        private final String username;
        private final long expiresAt;

        public TokenInfo(JsonObject principal, Set<String> roles, String username, long expiresAt) {
            this.principal = principal;
            this.roles = roles;
            this.username = username;
            this.expiresAt = expiresAt;
        }

        public JsonObject getPrincipal() { return principal; }
        public Set<String> getRoles() { return roles; }
        public String getUsername() { return username; }
        public long getExpiresAt() { return expiresAt; }

        /** 检查是否已过期 */
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private TokenCacheManager(boolean enabled, int maxSize, long defaultTtlMinutes) {
        this.enabled = enabled;
        this.maxSize = maxSize;

        if (enabled) {
            this.cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build();
            this.cacheManager.init();

            // 默认 TTL 为 defaultTtlMinutes 分钟，但实际使用 token 自身的 exp
            Duration ttl = Duration.ofMinutes(defaultTtlMinutes);

            this.cache = cacheManager.createCache("tokenCache",
                CacheConfigurationBuilder.newCacheConfigurationBuilder(
                    String.class,
                    TokenInfo.class,
                    ResourcePoolsBuilder.heap(maxSize)
                )
                .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(ttl))
            );

            LOG.info("[CACHE] Token cache initialized - enabled={}, maxSize={}, defaultTtl={}min",
                enabled, maxSize, defaultTtlMinutes);
        } else {
            this.cacheManager = null;
            this.cache = null;
            LOG.info("[CACHE] Token cache disabled");
        }
    }

    /** 获取单例实例 */
    public static synchronized TokenCacheManager getInstance() {
        if (INSTANCE == null) {
            // 默认配置：启用，最大 10000 条，默认 TTL 60 分钟
            INSTANCE = new TokenCacheManager(true, 10000, 60);
        }
        return INSTANCE;
    }

    /** 使用配置初始化单例 */
    public static synchronized void initialize(boolean enabled, int maxSize, long defaultTtlMinutes) {
        if (INSTANCE != null) {
            LOG.warn("[CACHE] TokenCacheManager already initialized, closing old instance");
            INSTANCE.shutdown();
        }
        INSTANCE = new TokenCacheManager(enabled, maxSize, defaultTtlMinutes);
    }

    /** 生成缓存 key - 使用 token 的 SHA-256 哈希前 32 字符 */
    private String generateKey(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(16, hash.length); i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            LOG.warn("[CACHE] Failed to hash token, using token prefix: {}", e.getMessage());
            return token.substring(0, Math.min(32, token.length()));
        }
    }

    /** 从缓存获取 token 信息 */
    public TokenInfo get(String token) {
        if (!enabled || cache == null || token == null || token.isEmpty()) {
            return null;
        }

        try {
            String key = generateKey(token);
            TokenInfo info = cache.get(key);

            if (info != null) {
                if (info.isExpired()) {
                    LOG.debug("[CACHE] Token expired, removing from cache");
                    cache.remove(key);
                    return null;
                }
                LOG.debug("[CACHE] Token cache hit - user={}", info.getUsername());
                return info;
            }

            LOG.debug("[CACHE] Token cache miss");
            return null;
        } catch (Exception e) {
            LOG.warn("[CACHE] Failed to get from cache: {}", e.getMessage());
            return null;
        }
    }

    /** 将 token 信息写入缓存 */
    public void put(String token, JsonObject principal, Set<String> roles, String username, long expiresAtMillis) {
        if (!enabled || cache == null || token == null || token.isEmpty()) {
            return;
        }

        try {
            String key = generateKey(token);

            // 不缓存已过期的 token
            if (expiresAtMillis <= System.currentTimeMillis()) {
                LOG.debug("[CACHE] Token already expired, not caching");
                return;
            }

            TokenInfo info = new TokenInfo(principal, roles, username, expiresAtMillis);
            cache.put(key, info);

            LOG.debug("[CACHE] Token cached - user={}, expiresAt={}", username,
                new java.util.Date(expiresAtMillis));
        } catch (Exception e) {
            LOG.warn("[CACHE] Failed to put to cache: {}", e.getMessage());
        }
    }

    /** 使指定 token 的缓存失效 */
    public void invalidate(String token) {
        if (!enabled || cache == null || token == null || token.isEmpty()) {
            return;
        }

        try {
            String key = generateKey(token);
            cache.remove(key);
            LOG.debug("[CACHE] Token invalidated");
        } catch (Exception e) {
            LOG.warn("[CACHE] Failed to invalidate cache: {}", e.getMessage());
        }
    }

    /** 清除所有缓存 */
    public void clear() {
        if (!enabled || cache == null) {
            return;
        }

        try {
            cache.clear();
            LOG.info("[CACHE] All token cache cleared");
        } catch (Exception e) {
            LOG.warn("[CACHE] Failed to clear cache: {}", e.getMessage());
        }
    }

    /** 关闭缓存管理器 */
    public void shutdown() {
        if (cacheManager != null) {
            try {
                cacheManager.close();
                LOG.info("[CACHE] Token cache manager shutdown");
            } catch (Exception e) {
                LOG.warn("[CACHE] Failed to shutdown cache manager: {}", e.getMessage());
            }
        }
    }

    public boolean isEnabled() { return enabled; }
    public int getMaxSize() { return maxSize; }
}
