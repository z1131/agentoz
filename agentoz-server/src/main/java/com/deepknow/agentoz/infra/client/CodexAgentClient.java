package com.deepknow.agentoz.infra.client;

import codex.agent.*;
import com.deepknow.agentoz.infra.converter.grpc.ConfigProtoConverter;
import com.deepknow.agentoz.model.AgentConfigEntity;
import org.apache.dubbo.common.stream.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Codex Agent 客户端
 * 负责与 codex-agent (Rust) 服务进行通信 (via Dubbo Triple Protocol)
 *
 * <p>通过 {@link } 接口,使用 Dubbo Triple 协议调用外部 Rust gRPC 服务。</p>
 *
 * <h3>🔄 核心方法</h3>
 * <ul>
 *   <li>{@link #runTask(String, AgentConfigEntity, List, String, StreamObserver)} - 执行Agent任务（流式返回）</li>
 * </ul>
 *
 * @see
 * @see AgentConfigEntity
 */
@Slf4j
@Component
public class CodexAgentClient {

    @DubboReference(
            interfaceClass = AgentService.class,
            // 关键：强制指定直连 URL，从 Nacos 配置读取
            url = "tri://${codex.agent.host}:${codex.agent.port}",
            protocol = "tri",
            check = false,
            timeout = 600000
    )
    private AgentService agentRpcService;

    /**
     * 执行代理任务 (流式返回)
     *
     * <p>使用强类型Proto定义，将AgentConfigEntity转换为SessionConfig后调用Codex-Agent。</p>
     *
     * <h3>🔄 调用流程</h3>
     * <pre>
     * 1. AgentConfigEntity → SessionConfig (Proto)
     * 2. List&lt;MessageDTO&gt; → List&lt;HistoryItem&gt; (Proto)
     * 3. 构建 RunTaskRequest
     * 4. 通过 Dubbo Triple 调用 Codex-Agent (StreamObserver回调)
     * </pre>
     *
     * @param conversationId 会话ID（对齐Codex-Agent的conversation_id）
     * @param config Agent配置实体
     * @param history 历史消息列表（强类型）
     * @param inputText 用户输入文本
     * @param responseObserver 响应流观察者
     */
    public void runTask(
            String conversationId,
            AgentConfigEntity config,
            List<HistoryItem> history,
            String inputText,
            StreamObserver<RunTaskResponse> responseObserver
    ) {
        // 1. 转换配置为Proto
        SessionConfig sessionConfig = ConfigProtoConverter.toSessionConfig(config);

        // 2. 构建用户输入
        UserInput userInput = UserInput.newBuilder()
                .setText(inputText)
                .build();

        // 3. 构建请求
        RunTaskRequest request = RunTaskRequest.newBuilder()
                .setConversationId(conversationId)
                .setConfig(sessionConfig)
                .addAllHistory(history)
                .setInput(userInput)
                .build();

        // 4. 发起Dubbo Triple调用 (直接透传Observer)
        log.info("发起 Codex-Agent 调用: conversationId={}, llmModel={}",
                conversationId, config.getLlmModel());

        try {
            agentRpcService.runTask(request, responseObserver);
        } catch (Exception e) {
            log.error("Codex-Agent 调用异常: conversationId={}", conversationId, e);
            responseObserver.onError(e);
        }
    }

    /**
     * 健康检查
     *
     * @return true if service is available
     */
    public boolean healthCheck() {
        return agentRpcService != null;
    }
}
