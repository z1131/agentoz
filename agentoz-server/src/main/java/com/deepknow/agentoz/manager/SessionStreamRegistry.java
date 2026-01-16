package com.deepknow.agentoz.manager;

import com.deepknow.agentoz.dto.InternalCodexEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 会话流注册表 (Session Stream Registry)
 * <p>
 * 用于在不同的执行线程间共享用户的输出通道。
 * 典型场景：CallAgentTool 在执行子任务时，需要将子智能体的实时输出“插播”到主智能体对应的用户连接中。
 * </p>
 */
@Slf4j
@Component
public class SessionStreamRegistry {

    private final Map<String, Consumer<InternalCodexEvent>> activeSessions = new ConcurrentHashMap<>();

    /**
     * 注册会话通道
     */
    public void register(String conversationId, Consumer<InternalCodexEvent> eventConsumer) {
        if (conversationId == null || eventConsumer == null) return;
        activeSessions.put(conversationId, eventConsumer);
        log.debug("[SessionStreamRegistry] 注册会话通道: {}", conversationId);
    }

    /**
     * 注销会话通道
     */
    public void unregister(String conversationId) {
        if (conversationId == null) return;
        activeSessions.remove(conversationId);
        log.debug("[SessionStreamRegistry] 注销会话通道: {}", conversationId);
    }

    /**
     * 广播事件到指定会话
     * (如果会话存在，则推送事件；否则忽略)
     */
    public void broadcast(String conversationId, InternalCodexEvent event) {
        if (conversationId == null || event == null) return;

        Consumer<InternalCodexEvent> consumer = activeSessions.get(conversationId);
        if (consumer != null) {
            try {
                consumer.accept(event);
            } catch (Exception e) {
                log.warn("[SessionStreamRegistry] 广播事件失败: convId={}, error={}", conversationId, e.getMessage());
            }
        }
    }
}
