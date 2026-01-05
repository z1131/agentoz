package com.deepknow.nexus.infra.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Codex Agent Mock 客户端
 *
 * 业务语义：
 * - 模拟调用 codex-agent 进行推理
 * - 用于本地开发和测试环境
 */
@Component
public class CodexAgentClient {

    private static final Logger log = LoggerFactory.getLogger(CodexAgentClient.class);

    /**
     * 调用 codex-agent 进行推理
     */
    public String infer(String agentId, String context, String systemPrompt, String mcpConfigJson, String userMessage) {
        log.info("调用 Mock Codex Agent: agentId={}, message={}", agentId, userMessage);

        try {
            Thread.sleep(100);
            return "[Mock Response] 我是您的智能助手，收到消息: " + userMessage;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error";
        }
    }

    /**
     * 重载方法
     */
    public String infer(String agentId, String context, String userMessage) {
        return infer(agentId, context, null, null, userMessage);
    }

    /**
     * 检查服务是否可用
     */
    public boolean healthCheck() {
        return true;
    }
}