package com.deepknow.agentoz.orchestrator;

import com.deepknow.agentoz.infra.redis.RedisOrchestrationSessionRepository;
import com.deepknow.agentoz.model.OrchestrationSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 编排会话管理器（支持分布式环境）
 *
 * <p>职责：</p>
 * <ul>
 *   <li>管理所有活跃的 OrchestrationSession</li>
 *   <li>提供会话注册、查询、注销功能</li>
 *   <li>处理会话生命周期</li>
 * </ul>
 *
 * <h3>🏗️ 分布式架构</h3>
 * <ul>
 *   <li>本地缓存：快速访问当前节点的会话</li>
 *   <li>Redis 存储：跨节点共享会话状态</li>
 *   <li>会话恢复：从 Redis 加载远程节点创建的会话</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrchestrationSessionManager {

    private final RedisOrchestrationSessionRepository redisRepository;

    /**
     * 本地会话缓存：conversationId -> OrchestrationSession
     * 仅缓存当前节点的活跃会话，用于快速访问
     */
    private final Map<String, OrchestrationSession> localSessions = new ConcurrentHashMap<>();

    /**
     * 注册会话（本地缓存 + Redis 持久化）
     *
     * @param session 会话对象
     */
    public void registerSession(OrchestrationSession session) {
        String sessionId = session.getSessionId();

        // 1. 本地缓存
        localSessions.put(sessionId, session);

        // 2. Redis 持久化
        redisRepository.saveSession(session);

        log.info("[SessionManager] 注册会话: sessionId={}, mainTaskId={}, node=LOCAL",
            sessionId, session.getMainTaskId());
    }

    /**
     * 获取会话（先查本地缓存，再查 Redis）
     *
     * @param conversationId 会话 ID
     * @return 会话对象，不存在返回 null
     */
    public OrchestrationSession getSession(String conversationId) {
        // 1. 先查本地缓存（快速路径）
        OrchestrationSession session = localSessions.get(conversationId);
        if (session != null) {
            log.debug("[SessionManager] 从本地缓存获取会话: sessionId={}", conversationId);
            return session;
        }

        // 2. 本地未命中，从 Redis 加载（可能是远程节点创建的会话）
        log.debug("[SessionManager] 本地缓存未命中，尝试从 Redis 加载: sessionId={}", conversationId);
        OrchestrationSession loadedSession = redisRepository.loadSession(conversationId);

        if (loadedSession != null) {
            // 3. 加载成功，放入本地缓存（注意：subscribers 不会被持久化，需要重新注册）
            localSessions.put(conversationId, loadedSession);
            log.info("[SessionManager] 从 Redis 恢复会话: sessionId={}, status={}, node=REMOTE",
                    conversationId, loadedSession.getStatus());
        }

        return loadedSession;
    }

    /**
     * 注销会话（本地缓存 + Redis）
     *
     * @param conversationId 会话 ID
     */
    public void unregisterSession(String conversationId) {
        // 1. 从本地缓存移除
        OrchestrationSession removed = localSessions.remove(conversationId);

        // 2. 从 Redis 删除
        redisRepository.deleteSession(conversationId);

        if (removed != null) {
            // 3. 关闭事件调度器（释放线程资源）
            removed.close();

            log.info("🗑️  [SessionManager] 注销会话: sessionId={}, mainTaskId={}",
                conversationId, removed.getMainTaskId());
        }
    }

    /**
     * 更新会话状态到 Redis（增量更新，避免频繁序列化整个 Session）
     *
     * @param conversationId 会话 ID
     * @param status 状态
     * @param activeTaskCount 活跃任务数
     */
    public void updateSessionStatus(String conversationId,
                                     OrchestrationSession.SessionStatus status,
                                     Integer activeTaskCount) {
        redisRepository.updateSessionStatus(conversationId, status.name(), activeTaskCount);

        // 同时更新本地缓存（如果存在）
        OrchestrationSession localSession = localSessions.get(conversationId);
        if (localSession != null) {
            if (status != null) {
                localSession.setStatus(status);
            }
            // 注意：activeTaskCount 是通过 increment/decrement 方法管理的，不支持直接设置
            // 这里我们只更新 Redis，本地缓存的 activeTaskCount 由业务逻辑自动维护
        }

        log.debug("[SessionManager] 会话状态已更新到 Redis: sessionId={}, status={}, activeTasks={}",
                conversationId, status, activeTaskCount);
    }

    /**
     * 检查会话是否存在（本地或 Redis）
     *
     * @param conversationId 会话 ID
     * @return true 如果会话存在且活跃
     */
    public boolean hasActiveSession(String conversationId) {
        // 1. 先查本地
        OrchestrationSession localSession = localSessions.get(conversationId);
        if (localSession != null && localSession.isActive()) {
            return true;
        }

        // 2. 本地不存在，查 Redis
        return redisRepository.existsSession(conversationId);
    }

    /**
     * 获取所有活跃会话数量（仅统计本地）
     *
     * @return 活跃会话数量
     */
    public int getActiveSessionCount() {
        return (int) localSessions.values().stream()
                .filter(OrchestrationSession::isActive)
                .count();
    }

    /**
     * 清理已完成的会话（定期调用）
     *
     * @return 清理的会话数量
     */
    public int cleanupCompletedSessions() {
        int before = localSessions.size();

        localSessions.entrySet().removeIf(entry -> {
            OrchestrationSession session = entry.getValue();
            if (session.canClose()) {
                // 关闭事件调度器（释放线程资源）
                session.close();
                return true;
            }
            return false;
        });

        int after = localSessions.size();
        int cleaned = before - after;

        if (cleaned > 0) {
            log.info("🧹 [SessionManager] 清理已完成会话: cleaned={}, remaining={}",
                cleaned, after);
        }

        return cleaned;
    }

    /**
     * 获取会话统计信息
     *
     * @return 统计信息 Map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("localSessions", localSessions.size());
        stats.put("activeSessions", getActiveSessionCount());

        // 统计活跃子任务总数
        int totalActiveTasks = localSessions.values().stream()
                .mapToInt(OrchestrationSession::getActiveTaskCount)
                .sum();
        stats.put("totalActiveTasks", totalActiveTasks);

        return stats;
    }
}
