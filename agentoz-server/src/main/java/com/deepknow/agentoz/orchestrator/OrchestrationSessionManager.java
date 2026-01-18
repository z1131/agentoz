package com.deepknow.agentoz.orchestrator;

import com.deepknow.agentoz.model.OrchestrationSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 编排会话管理器
 *
 * <p>职责：</p>
 * <ul>
 *   <li>管理所有活跃的 OrchestrationSession</li>
   *   <li>提供会话注册、查询、注销功能</li>
 *   *   <li>处理会话生命周期</li>
   * </ul>
 */
@Slf4j
@Component
public class OrchestrationSessionManager {

    private static volatile OrchestrationSessionManager instance;

    /**
     * 获取单例实例
     */
    public static OrchestrationSessionManager getInstance() {
        if (instance == null) {
            synchronized (OrchestrationSessionManager.class) {
                if (instance == null) {
                    instance = new OrchestrationSessionManager();
                }
            }
        }
        return instance;
    }

    /**
     * 会话存储：conversationId -> OrchestrationSession
     */
    private final Map<String, OrchestrationSession> sessions = new ConcurrentHashMap<>();

    /**
     * 注册会话
     *
     * @param session 会话对象
     */
    public void registerSession(OrchestrationSession session) {
        String sessionId = session.getSessionId();
        sessions.put(sessionId, session);
        log.info("📝 [SessionManager] 注册会话: sessionId={}, mainTaskId={}",
            sessionId, session.getMainTaskId());
    }

    /**
     * 获取会话
     *
     * @param conversationId 会话 ID
     * @return 会话对象，不存在返回 null
     */
    public OrchestrationSession getSession(String conversationId) {
        return sessions.get(conversationId);
    }

    /**
     * 注销会话
     *
     * @param conversationId 会话 ID
     */
    public void unregisterSession(String conversationId) {
        OrchestrationSession removed = sessions.remove(conversationId);
        if (removed != null) {
            log.info("🗑️  [SessionManager] 注销会话: sessionId={}, mainTaskId={}",
                conversationId, removed.getMainTaskId());
        }
    }

    /**
     * 检查会话是否存在
     *
     * @param conversationId 会话 ID
     * @return true 如果会话存在且活跃
     */
    public boolean hasActiveSession(String conversationId) {
        OrchestrationSession session = sessions.get(conversationId);
        return session != null && session.isActive();
    }

    /**
     * 获取所有活跃会话数量
     *
     * @return 活跃会话数量
     */
    public int getActiveSessionCount() {
        return (int) sessions.values().stream()
                .filter(OrchestrationSession::isActive)
                .count();
    }

    /**
     * 清理已完成的会话（定期调用）
     *
     * @return 清理的会话数量
     */
    public int cleanupCompletedSessions() {
        int before = sessions.size();

        sessions.entrySet().removeIf(entry -> {
            OrchestrationSession session = entry.getValue();
            return session.canClose();
        });

        int after = sessions.size();
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
        stats.put("totalSessions", sessions.size());
        stats.put("activeSessions", getActiveSessionCount());

        // 统计活跃子任务总数
        int totalActiveTasks = sessions.values().stream()
                .mapToInt(OrchestrationSession::getActiveTaskCount)
                .sum();
        stats.put("totalActiveTasks", totalActiveTasks);

        return stats;
    }
}
