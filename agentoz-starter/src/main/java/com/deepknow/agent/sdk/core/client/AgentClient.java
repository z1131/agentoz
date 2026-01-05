package com.deepknow.agent.sdk.core.client;

import com.deepknow.agent.api.*;
import com.deepknow.agent.api.dto.*;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 智能体客户端（Dubbo 客户端封装）
 *
 * <p>职责：
 * 1. 封装 Dubbo 调用细节
 * 2. 提供简单 API 给 SDK API 层使用
 * 3. 自动重试、容错
 *
 * <p>使用方式：
 * <pre>
 * &#64;Autowired
 * private AgentClient agentClient;
 *
 * String response = agentClient.chat(sessionId, message, config);
 * </pre>
 *
 * @author Agent Platform
 * @version 1.0.0
 */
@Component
public class AgentClient {

    private static final Logger log = LoggerFactory.getLogger(AgentClient.class);

    @DubboReference(
        version = "1.0.0",
        cluster = "failover",
        retries = 2,
        loadbalance = "roundrobin",
        timeout = 30000,
        check = false
    )
    private AgentService agentService;

    @DubboReference(
        version = "1.0.0",
        cluster = "failover",
        retries = 2,
        loadbalance = "roundrobin",
        timeout = 10000,
        check = false
    )
    private SessionService sessionService;

    @DubboReference(
        version = "1.0.0",
        cluster = "failover",
        retries = 2,
        loadbalance = "roundrobin",
        timeout = 10000,
        check = false
    )
    private ContextService contextService;

    private static volatile AgentClient instance;

    /**
     * 获取单例（用于非 Spring 环境）
     */
    public static AgentClient getInstance() {
        if (instance == null) {
            synchronized (AgentClient.class) {
                if (instance == null) {
                    instance = new AgentClient();
                }
            }
        }
        return instance;
    }

    /**
     * 创建会话
     *
     * @param config 智能体配置
     * @return 会话ID
     */
    public String createSession(com.deepknow.agent.sdk.model.AgentConfig config) {
        CreateSessionRequest request = CreateSessionRequest.builder()
            .userId(config.getUserId())
            .agentType(config.getAgentType())
            .agentRole(config.getAgentRole())
            .title(config.getTitle())
            .metadata(config.getContext()) // Mapping context to metadata
            .build();

        SessionDTO session = sessionService.createSession(request);
        log.info("创建会话成功: sessionId={}, agentType={}", session.getSessionId(), config.getAgentType());
        return session.getSessionId();
    }

    /**
     * 同步对话
     *
     * @param sessionId 会话ID
     * @param message 消息
     * @param config 智能体配置
     * @return 响应
     */
    public String chat(String sessionId, String message, com.deepknow.agent.sdk.model.AgentConfig config) {
        AgentRequest request = AgentRequest.builder()
            .sessionId(sessionId)
            .query(message)
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens())
            .extra(config.getExtra())
            .build();

        AgentResponse response = agentService.execute(request);

        if (!response.getSuccess()) {
            throw new RuntimeException("智能体执行失败: " + response.getErrorMessage());
        }

        return response.getOutput();
    }

    /**
     * 流式对话
     *
     * @param sessionId 会话ID
     * @param message 消息
     * @param config 智能体配置
     * @return 流式响应
     */
    public Flux<String> chatStream(String sessionId, String message, com.deepknow.agent.sdk.model.AgentConfig config) {
        AgentRequest request = AgentRequest.builder()
            .sessionId(sessionId)
            .query(message)
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens())
            .extra(config.getExtra())
            .build();

        return agentService.executeStream(request)
            .map(AgentChunk::getContent)
            .doOnComplete(() -> log.debug("流式对话完成: sessionId={}", sessionId))
            .doOnError(e -> log.error("流式对话异常: sessionId={}", sessionId, e));
    }

    /**
     * 异步对话
     *
     * @param sessionId 会话ID
     * @param message 消息
     * @param config 智能体配置
     * @return CompletableFuture
     */
    public CompletableFuture<String> chatAsync(String sessionId, String message, com.deepknow.agent.sdk.model.AgentConfig config) {
        return CompletableFuture.supplyAsync(() -> chat(sessionId, message, config));
    }

    /**
     * 获取会话历史
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    public List<com.deepknow.agent.sdk.model.Message> getHistory(String sessionId) {
        MessageListResponse response = agentService.getHistory(sessionId);
        return response.getMessages().stream()
            .map(msg -> com.deepknow.agent.sdk.model.Message.builder()
                .role(msg.getRole())
                .content(msg.getContent())
                .timestamp(msg.getCreatedAt())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * 关闭会话
     *
     * @param sessionId 会话ID
     */
    public void closeSession(String sessionId) {
        agentService.closeSession(sessionId);
        log.info("关闭会话: sessionId={}", sessionId);
    }

    /**
     * 获取会话信息
     *
     * @param sessionId 会话ID
     * @return 会话信息
     */
    public SessionDTO getSession(String sessionId) {
        return sessionService.getSession(sessionId);
    }

    /**
     * 更新会话状态
     *
     * @param sessionId 会话ID
     * @param status 状态
     */
    public void updateSessionStatus(String sessionId, String status) {
        sessionService.updateStatus(sessionId, status);
        log.info("更新会话状态: sessionId={}, status={}", sessionId, status);
    }

    /**
     * 创建上下文
     *
     * @param sessionId 会话ID
     * @param tenantId 租户ID
     * @param initialData 初始数据
     * @return 上下文ID
     */
    public String createContext(String sessionId, String tenantId, java.util.Map<String, Object> initialData) {
        List<ContextData> contextDataList = initialData.entrySet().stream()
            .map(entry -> ContextData.builder()
                .key(entry.getKey())
                .value(entry.getValue())
                .build())
            .collect(Collectors.toList());

        CreateContextRequest request = CreateContextRequest.builder()
            .sessionId(sessionId)
            .contextData(contextDataList)
            .build();

        ContextDTO context = contextService.createContext(request);
        log.info("创建上下文: contextId={}, sessionId={}", context.getId(), sessionId);
        return context.getId();
    }
}
