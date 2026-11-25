package com.example.ami.cache;

import com.example.ami.dto.DailyCurveResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

/** 管理日曲线的进程内缓存与 Redis 缓存。 */
@Component
public class DailyCurveCache {

    private static final Logger log = LoggerFactory.getLogger(DailyCurveCache.class);
    private static final Duration REDIS_TTL = Duration.ofMinutes(2);
    private static final Duration EMPTY_TTL = Duration.ofSeconds(10);

    private final Cache<String, DailyCurveResponse> local = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public DailyCurveCache(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    /** 先查本地，再查 Redis；两层都未命中时由调用方查库。 */
    public Optional<DailyCurveResponse> get(String deviceNo, LocalDate date) {
        String key = key(deviceNo, date);

        DailyCurveResponse cached = local.getIfPresent(key);
        if (cached != null) {
            log.debug("日曲线命中 Caffeine key={}", key);
            return Optional.of(cached);
        }

        try {
            String value = redis.opsForValue().get(key);
            if (value != null) {
                DailyCurveResponse response = json.readValue(value, DailyCurveResponse.class);
                rememberLocally(key, response);
                log.debug("日曲线命中 Redis key={}", key);
                return Optional.of(response);
            }
        } catch (DataAccessException | JsonProcessingException ex) {
            log.warn("读取日曲线缓存失败，将查询数据库 key={}", key, ex);
        }

        log.debug("日曲线回源 MySQL key={}", key);
        return Optional.empty();
    }

    /** 按结果是否为空选择缓存有效期。 */
    public void put(DailyCurveResponse response) {
        String key = key(response.deviceNo(), response.date());
        Duration ttl = response.points().isEmpty() ? EMPTY_TTL : REDIS_TTL;

        try {
            redis.opsForValue().set(key, json.writeValueAsString(response), ttl);
        } catch (DataAccessException | JsonProcessingException ex) {
            log.warn("写入 Redis 日曲线缓存失败 key={}", key, ex);
        }

        rememberLocally(key, response);
    }

    /** 清理两层缓存，Redis 失败时仍清理本地副本。 */
    public void evict(String deviceNo, LocalDate date) {
        String key = key(deviceNo, date);
        try {
            redis.delete(key);
        } catch (DataAccessException ex) {
            log.warn("Redis 日曲线缓存失效失败，旧值可能保留至过期 key={}", key, ex);
        } finally {
            local.invalidate(key);
        }
    }

    /** 只在本地保留有采样点的结果。 */
    private void rememberLocally(String key, DailyCurveResponse response) {
        // 缺报数据可能很快补到；空日曲线只在 Redis 短暂保存。
        if (!response.points().isEmpty()) {
            local.put(key, response);
        }
    }

    private String key(String deviceNo, LocalDate date) {
        return "ami:daily:v1:" + deviceNo + ":" + date;
    }
}
