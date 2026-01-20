package com.deepknow.agentoz.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.session.Session;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.SimpleSessionKey;
import io.agentscope.core.state.State;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis 实现的 Session
 * 适配 AgentScope 官方 Session 接口
 */
@Slf4j
public class RedisSession implements Session {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final Duration ttl;

    private static final String DEFAULT_PREFIX = "agentoz:session:";
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);

    public RedisSession(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, DEFAULT_PREFIX, DEFAULT_TTL);
    }

    public RedisSession(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, 
                        String keyPrefix, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.ttl = ttl;
    }

    @Override
    public void save(SessionKey sessionKey, String key, State value) {
        String redisKey = buildRedisKey(sessionKey, key);
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(redisKey, json, ttl);
            addToIndex(sessionKey, key);
            log.debug("保存状态: {} -> {}", redisKey, value.getClass().getSimpleName());
        } catch (JsonProcessingException e) {
            log.error("序列化状态失败: {}", redisKey, e);
            throw new RuntimeException("Failed to serialize state", e);
        }
    }

    @Override
    public void save(SessionKey sessionKey, String key, List<? extends State> values) {
        String redisKey = buildRedisKey(sessionKey, key) + ":list";
        // 先删除旧数据
        redisTemplate.delete(redisKey);
        
        for (State value : values) {
            try {
                String json = objectMapper.writeValueAsString(value);
                redisTemplate.opsForList().rightPush(redisKey, json);
            } catch (JsonProcessingException e) {
                log.error("序列化状态列表项失败: {}", redisKey, e);
                throw new RuntimeException("Failed to serialize state list item", e);
            }
        }
        redisTemplate.expire(redisKey, ttl);
        addToIndex(sessionKey, key);
        log.debug("保存状态列表: {} -> {} items", redisKey, values.size());
    }

    @Override
    public <T extends State> Optional<T> get(SessionKey sessionKey, String key, Class<T> type) {
        String redisKey = buildRedisKey(sessionKey, key);
        String json = redisTemplate.opsForValue().get(redisKey);
        if (json == null || json.isEmpty()) {
            return Optional.empty();
        }
        try {
            T value = objectMapper.readValue(json, type);
            return Optional.of(value);
        } catch (JsonProcessingException e) {
            log.error("反序列化状态失败: {}", redisKey, e);
            return Optional.empty();
        }
    }

    @Override
    public <T extends State> List<T> getList(SessionKey sessionKey, String key, Class<T> itemType) {
        String redisKey = buildRedisKey(sessionKey, key) + ":list";
        List<String> jsonList = redisTemplate.opsForList().range(redisKey, 0, -1);
        if (jsonList == null || jsonList.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>();
        for (String json : jsonList) {
            try {
                T value = objectMapper.readValue(json, itemType);
                result.add(value);
            } catch (JsonProcessingException e) {
                log.error("反序列化状态列表项失败: {}", redisKey, e);
            }
        }
        return result;
    }

    @Override
    public boolean exists(SessionKey sessionKey) {
        String indexKey = buildIndexKey(sessionKey);
        Long size = redisTemplate.opsForSet().size(indexKey);
        return size != null && size > 0;
    }

    @Override
    public void delete(SessionKey sessionKey) {
        String indexKey = buildIndexKey(sessionKey);
        Set<String> keys = redisTemplate.opsForSet().members(indexKey);
        if (keys != null && !keys.isEmpty()) {
            for (String key : keys) {
                String redisKey = buildRedisKey(sessionKey, key);
                redisTemplate.delete(redisKey);
                redisTemplate.delete(redisKey + ":list");
            }
        }
        redisTemplate.delete(indexKey);
        log.debug("删除 Session: {}", getSessionId(sessionKey));
    }

    @Override
    public Set<SessionKey> listSessionKeys() {
        String pattern = keyPrefix + "*:_keys";
        Set<String> indexKeys = redisTemplate.keys(pattern);
        if (indexKeys == null || indexKeys.isEmpty()) {
            return Collections.emptySet();
        }
        return indexKeys.stream()
                .map(k -> k.substring(keyPrefix.length(), k.length() - 6)) // 移除前缀和 ":_keys"
                .map(SimpleSessionKey::of)
                .collect(Collectors.toSet());
    }

    @Override
    public void close() {
        // Redis 连接由 Spring 管理，不需要手动关闭
    }

    private String buildRedisKey(SessionKey sessionKey, String stateKey) {
        return keyPrefix + getSessionId(sessionKey) + ":" + stateKey;
    }

    private String buildIndexKey(SessionKey sessionKey) {
        return keyPrefix + getSessionId(sessionKey) + ":_keys";
    }

    private void addToIndex(SessionKey sessionKey, String stateKey) {
        String indexKey = buildIndexKey(sessionKey);
        redisTemplate.opsForSet().add(indexKey, stateKey);
        redisTemplate.expire(indexKey, ttl);
    }

    private String getSessionId(SessionKey sessionKey) {
        if (sessionKey instanceof SimpleSessionKey simpleKey) {
            return simpleKey.sessionId();
        }
        return sessionKey.toString();
    }
}
