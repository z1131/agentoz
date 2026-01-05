package com.deepknow.agent.sdk.agents;

import com.deepknow.agent.sdk.core.client.AgentClient;
import com.deepknow.agent.sdk.model.AgentConfig;
import com.deepknow.agent.sdk.model.Message;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 智能体抽象基类
 *
 * <p>提供流畅的 Builder API，业务人员直接使用此类或其子类
 *
 * <p>使用示例：
 * <pre>
 * ChatAgent agent = new ChatAgent()
 *     .name("客服助手")
 *     .prompt("你是友好的客服代表")
 *     .temperature(0.8);
 *
 * String response = agent.chat("用户投诉产品有问题");
 * </pre>
 *
 * @param <T> 子类类型
 * @author Agent Platform
 * @version 1.0.0
 */
@Slf4j
public abstract class BaseAgent<T extends BaseAgent<T>> {

    /**
     * 智能体配置
     */
    protected AgentConfig config;

    /**
     * 会话ID（首次调用时自动创建）
     */
    protected String sessionId;

    /**
     * Dubbo 客户端
     */
    protected AgentClient client;

    /**
     * 构造函数
     */
    protected BaseAgent() {
        this.config = AgentConfig.builder().build();
        this.client = AgentClient.getInstance();
    }

    /**
     * 设置智能体名称
     *
     * @param name 名称
     * @return this
     */
    public T name(String name) {
        this.config.setAgentRole(name);
        this.config.setTitle(name);
        return self();
    }

    /**
     * 设置系统提示词
     *
     * @param prompt 提示词
     * @return this
     */
    public T prompt(String prompt) {
        this.config.setSystemPrompt(prompt);
        return self();
    }

    /**
     * 设置温度参数
     *
     * @param temperature 温度 (0.0 - 1.0)
     * @return this
     */
    public T temperature(Double temperature) {
        this.config.setTemperature(temperature);
        return self();
    }

    /**
     * 设置最大tokens
     *
     * @param maxTokens 最大tokens数
     * @return this
     */
    public T maxTokens(Integer maxTokens) {
        this.config.setMaxTokens(maxTokens);
        return self();
    }

    /**
     * 设置租户ID
     *
     * @param tenantId 租户ID
     * @return this
     */
    public T tenantId(String tenantId) {
        this.config.setTenantId(tenantId);
        return self();
    }

    /**
     * 设置用户ID
     *
     * @param userId 用户ID
     * @return this
     */
    public T userId(String userId) {
        this.config.setUserId(userId);
        return self();
    }

    /**
     * 添加上下文
     *
     * @param key 键
     * @param value 值
     * @return this
     */
    public T context(String key, Object value) {
        this.config.addContext(key, value);
        return self();
    }

    /**
     * 批量添加上下文
     *
     * @param data 上下文数据
     * @return this
     */
    public T contexts(java.util.Map<String, Object> data) {
        if (data != null) {
            data.forEach(this.config::addContext);
        }
        return self();
    }

    /**
     * 初始化会话（如果尚未创建）
     */
    protected void initSession() {
        if (sessionId == null) {
            // 设置默认值
            if (config.getTenantId() == null) {
                config.setTenantId("default");
            }
            if (config.getUserId() == null) {
                config.setUserId("anonymous");
            }

            sessionId = client.createSession(config);
            log.info("Agent[{}] 创建会话: {}", config.getAgentRole(), sessionId);
        }
    }

    /**
     * 同步对话
     *
     * @param message 用户消息
     * @return 智能体回复
     */
    public String chat(String message) {
        initSession();
        log.debug("Agent[{}] 同步对话: sessionId={}, message={}", config.getAgentRole(), sessionId, message);

        String response = client.chat(sessionId, message, config);
        log.debug("Agent[{}] 同步响应: sessionId={}, response={}", config.getAgentRole(), sessionId, response);

        return response;
    }

    /**
     * 流式对话（用于实时场景）
     *
     * @param message 用户消息
     * @return 流式响应
     */
    public Flux<String> chatStream(String message) {
        initSession();
        log.debug("Agent[{}] 流式对话: sessionId={}, message={}", config.getAgentRole(), sessionId, message);

        return client.chatStream(sessionId, message, config);
    }

    /**
     * 异步对话
     *
     * @param message 用户消息
     * @return CompletableFuture
     */
    public CompletableFuture<String> chatAsync(String message) {
        initSession();
        log.debug("Agent[{}] 异步对话: sessionId={}, message={}", config.getAgentRole(), sessionId, message);

        return client.chatAsync(sessionId, message, config);
    }

    /**
     * 流式对话 + 回调（适用于非响应式场景）
     *
     * @param message 用户消息
     * @param onNext 每个内容块的回调
     * @param onComplete 完成回调
     * @param onError 错误回调
     */
    public void chatStream(String message,
                           Consumer<String> onNext,
                           Runnable onComplete,
                           Consumer<Throwable> onError) {
        chatStream(message)
            .doOnComplete(onComplete == null ? () -> {} : onComplete)
            .doOnError(onError == null ? e -> log.error("流式对话异常", e) : onError)
            .subscribe(onNext);
    }

    /**
     * 获取会话历史
     *
     * @return 历史消息列表
     */
    public List<Message> getHistory() {
        initSession();
        return client.getHistory(sessionId);
    }

    /**
     * 清除会话（关闭当前会话）
     */
    public void clear() {
        if (sessionId != null) {
            client.closeSession(sessionId);
            log.info("Agent[{}] 关闭会话: {}", config.getAgentRole(), sessionId);
            sessionId = null;
        }
    }

    /**
     * 获取会话ID
     *
     * @return 会话ID
     */
    public String getSessionId() {
        initSession();
        return sessionId;
    }

    /**
     * 获取配置
     *
     * @return 配置
     */
    public AgentConfig getConfig() {
        return config;
    }

    /**
     * 获取当前时间戳
     *
     * @return LocalDateTime
     */
    protected LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * 返回子类实例
     */
    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }
}
