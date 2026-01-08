package com.deepknow.agentoz.infra.client;

import com.deepknow.agentoz.infra.adapter.grpc.CodexAgentRpcService;
import com.deepknow.agentoz.infra.adapter.grpc.*;
import com.deepknow.agentoz.infra.converter.grpc.ConfigProtoConverter;
import com.deepknow.agentoz.model.AgentConfigEntity;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Codex Agent 客户端
 * 负责与 codex-agent (Rust) 服务进行通信 (via Dubbo Triple Protocol)
 *
 * <p>通过 {@link CodexAgentRpcService} 接口,使用 Dubbo Triple 协议调用外部 Rust gRPC 服务。</p>
 *
 * <h3>🔄 核心方法</h3>
 * <ul>
 *   <li>{@link #runTask(String, AgentConfigEntity, List, String)} - 执行Agent任务（流式返回）</li>
 * </ul>
 *
 * @see CodexAgentRpcService
 * @see AgentConfigEntity
 */
@Slf4j
@Component
public class CodexAgentClient {

    @DubboReference(
            interfaceClass = CodexAgentRpcService.class,
            // 关键：强制指定直连 URL，从 Nacos 配置读取
            url = "tri://${codex.agent.host}:${codex.agent.port}",
            protocol = "tri",
            check = false,
            timeout = 600000
    )
    private CodexAgentRpcService agentRpcService;

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
     * 4. 通过 Dubbo Triple 调用 Codex-Agent
     * 5. 流式返回 RunTaskResponse
     * </pre>
     *
     * @param conversationId 会话ID（对齐Codex-Agent的conversation_id）
     * @param config Agent配置实体
     * @param history 历史消息列表（强类型）
     * @param inputText 用户输入文本
     * @return 流式响应
     */
    public Flux<RunTaskResponse> runTask(
            String conversationId,
            AgentConfigEntity config,
            List<HistoryItem> history,
            String inputText
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

        // 4. 发起Dubbo Triple调用
        return Flux.create(sink -> {
            log.info("发起 Codex-Agent 调用: conversationId={}, model={}",
                    conversationId, config.getModel());

            agentRpcService.runTask(request, new StreamObserver<RunTaskResponse>() {
                @Override
                public void onNext(RunTaskResponse value) {
                    log.debug("收到 Codex-Agent 响应: status={}, textDelta={}",
                            value.getStatus(), value.getTextDelta());
                    sink.next(value);
                }

                @Override
                public void onError(Throwable t) {
                    log.error("Codex-Agent 调用异常: conversationId={}", conversationId, t);
                    sink.error(t);
                }

                @Override
                public void onCompleted() {
                    log.info("Codex-Agent 调用完成: conversationId={}", conversationId);
                    sink.complete();
                }
            });
        });
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
