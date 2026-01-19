package com.deepknow.agentoz.infra.redis;

import com.deepknow.agentoz.model.OrchestrationSession;
import com.deepknow.agentoz.orchestrator.OrchestrationSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的分布式会话存储
 *
 * <h3>🎯 设计</h3>
 * <ul>
 *   <li>Session 状态存储在 Redis，所有节点共享</li>
 *   * <li>设置合理的过期时间（如 2 小时）</li>
   *   *   <li>本地内存缓存作为二级缓存，提升性能</li>
 * </ul>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisOrchestrationSessionRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String SESSION_PREFIX = "orchestration:session:";
    private static final long SESSION_TTL_MINUTES = 120; // 2小时过期

    /**
     * 保存会话到 Redis
     */
    public void saveSession(OrchestrationSession session) {
        try {
            String key = SESSION_PREFIX + session.getSessionId();

            // 序列化 Session（只序列化必要字段）
            SessionData data = new SessionData();
            data.setSessionId(session.getSessionId());
            data.setMainTaskId(session.getMainTaskId());
            data.setCurrentAgentId(session.getCurrentAgentId());
            data.setStatus(session.getStatus().name());
            data.setActiveTaskCount(session.getActiveTaskCount());

            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, SESSION_TTL_MINUTES, TimeUnit.MINUTES);

            log.debug("✅ [RedisSession] Session saved to Redis: sessionId={}", session.getSessionId());
        } catch (Exception e) {
            log.error("❌ [RedisSession] Failed to save session: sessionId={}", session.getSessionId(), e);
        }
    }

    /**
     * 从 Redis 加载会话
     */
    public OrchestrationSession loadSession(String conversationId) {
        try {
            String key = SESSION_PREFIX + conversationId;
            String json = redisTemplate.opsForValue().get(key);

            if (json == null) {
                log.debug("Session not found in Redis: conversationId={}", conversationId);
                return null;
            }

            SessionData data = objectMapper.readValue(json, SessionData.class);

            // 重建 Session 对象（注意：subscribers 不会被持久化，需要重新注册）
            OrchestrationSession session = OrchestrationSession.builder()
                    .sessionId(data.getSessionId())
                    .mainTaskId(data.getMainTaskId())
                    .currentAgentId(data.getCurrentAgentId())
                    .status(OrchestrationSession.SessionStatus.valueOf(data.getStatus()))
                    .build();

            log.info("✅ [RedisSession] Session loaded from Redis: sessionId={}, status={}",
                    conversationId, data.getStatus());

            return session;
        } catch (Exception e) {
            log.error("❌ [RedisSession] Failed to load session: conversationId={}", conversationId, e);
            return null;
        }
    }

    /**
     * 删除会话
     */
    public void deleteSession(String conversationId) {
        String key = SESSION_PREFIX + conversationId;
        redisTemplate.delete(key);
        log.info("🗑️  [RedisSession] Session deleted: conversationId={}", conversationId);
    }

    /**
     * 检查会话是否存在
     */
    public boolean existsSession(String conversationId) {
        String key = SESSION_PREFIX + conversationId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 更新会话状态（增量更新，避免频繁序列化整个 Session）
     */
    public void updateSessionStatus(String conversationId, String status, Integer activeTaskCount) {
        try {
            String key = SESSION_PREFIX + conversationId;
            String json = redisTemplate.opsForValue().get(key);

            if (json != null) {
                SessionData data = objectMapper.readValue(json, SessionData.class);
                data.setStatus(status);
                if (activeTaskCount != null) {
                    data.setActiveTaskCount(activeTaskCount);
                }

                String updatedJson = objectMapper.writeValueAsString(data);
                redisTemplate.opsForValue().set(key, updatedJson, SESSION_TTL_MINUTES, TimeUnit.MINUTES);

                log.debug("📝 [RedisSession] Session status updated: conversationId={}, status={}, activeTasks={}",
                        conversationId, status, activeTaskCount);
            }
        } catch (Exception e) {
            log.error("❌ [RedisSession] Failed to update status: conversationId={}", conversationId, e);
        }
    }

    /**
     * Session 序列化数据（只包含持久化需要的字段）
     */
    @lombok.Data
    public static class SessionData {
        private String sessionId;
        private String mainTaskId;
        private String currentAgentId;
        private String status;
        private Integer activeTaskCount;
    }
}
