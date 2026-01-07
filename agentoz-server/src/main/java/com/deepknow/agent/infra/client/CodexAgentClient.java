package com.deepknow.nexus.infra.client;

import codex.agent.v1.Agent;
import codex.agent.v1.AgentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Codex Agent 客户端
 * 负责与 codex-agent 服务进行通信 (via Dubbo Triple Protocol)
 */
@Slf4j
@Component
public class CodexAgentClient {

    @DubboReference(
            interfaceClass = AgentService.class,
            protocol = "tri",
            check = false,
            timeout = 600000 // 10分钟超时，适应长任务
    )
    private AgentService agentService;
    
    // 用于序列化配置对象
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行代理任务 (流式返回)
     *
     * @param sessionId 会话ID
     * @param config    会话配置对象 (将被序列化为 JSON)
     * @param history   历史记录列表 (JSON 字符串列表)
     * @param inputText 用户输入文本
     * @return RunTaskResponse 的 Flux 流
     */
    public Flux<Agent.RunTaskResponse> runTask(String sessionId, Object config, List<String> history, String inputText) {
        String configJson;
        try {
            configJson = config instanceof String ? (String) config : objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            return Flux.error(new IllegalArgumentException("无法序列化 config 对象", e));
        }

        Agent.UserInput userInput = Agent.UserInput.newBuilder()
                .setText(inputText)
                .build();

        Agent.RunTaskRequest request = Agent.RunTaskRequest.newBuilder()
                .setSessionId(sessionId)
                .setConfigJson(configJson)
                .addAllHistoryItemsJson(history)
                .setInput(userInput)
                .build();

        return Flux.create(sink -> {
            log.info("发起 Dubbo Triple 调用: sessionId={}", sessionId);
            agentService.runTask(request, new StreamObserver<Agent.RunTaskResponse>() {
                @Override
                public void onNext(Agent.RunTaskResponse value) {
                    sink.next(value);
                }

                @Override
                public void onError(Throwable t) {
                    log.error("Codex Agent 调用异常", t);
                    sink.error(t);
                }

                @Override
                public void onCompleted() {
                    log.info("Codex Agent 调用完成");
                    sink.complete();
                }
            });
        });
    }

    /**
     * 健康检查
     */
    public boolean healthCheck() {
        // 简单判断代理是否注入
        return agentService != null;
    }
}