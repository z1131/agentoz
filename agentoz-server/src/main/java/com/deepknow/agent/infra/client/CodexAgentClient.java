package com.deepknow.agent.infra.client;

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
            // 关键：强制指定直连 URL，从 Nacos 配置读取
            url = "tri://${codex.agent.host}:${codex.agent.port}",
            protocol = "tri",
            check = false,
            timeout = 600000 
    )
    private AgentService agentService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行代理任务 (流式返回)
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

    public boolean healthCheck() {
        return agentService != null;
    }
}
