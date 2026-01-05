package com.deepknow.nexus.infra.client;

import codex.agent.v1.Agent;
import codex.agent.v1.AgentServiceGrpc;
import com.deepknow.agent.api.dto.AgentChunk;
import com.deepknow.nexus.common.JsonUtils;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Codex Agent gRPC 服务实现
 */
@Component
public class CodexAgentGrpcServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(CodexAgentGrpcServiceImpl.class);

    @Autowired
    private CodexAgentGrpcClient grpcClient;

    /**
     * 流式调用推理
     */
    public Flux<AgentChunk> inferStream(String sessionId, String agentId, String context, String systemPrompt, String mcpConfigJson, String userMessage) {
        ManagedChannel channel = grpcClient.getChannel();
        if (channel == null || !grpcClient.healthCheck()) {
            return Flux.error(new RuntimeException("codex-agent gRPC 通道不可用"));
        }

        AgentServiceGrpc.AgentServiceStub asyncStub = AgentServiceGrpc.newStub(channel);
        Sinks.Many<AgentChunk> sink = Sinks.many().unicast().onBackpressureBuffer();

        try {
            Agent.RunTaskRequest request = buildRequest(sessionId, agentId, context, systemPrompt, mcpConfigJson, userMessage);

            asyncStub.runTask(request, new StreamObserver<Agent.RunTaskResponse>() {
                @Override
                public void onNext(Agent.RunTaskResponse response) {
                    if (response.getTextDelta() != null && !response.getTextDelta().isEmpty()) {
                        sink.tryEmitNext(AgentChunk.text(response.getTextDelta()));
                    }
                    for (String itemJson : response.getNewItemsJsonList()) {
                        if (itemJson.contains("\"type\":\"thought\"")) {
                            sink.tryEmitNext(AgentChunk.thought(itemJson));
                        } else if (itemJson.contains("\"type\":\"CustomToolCall\"")) {
                            sink.tryEmitNext(AgentChunk.toolCall(itemJson));
                        }
                    }
                    if (response.getFinalResponse() != null && !response.getFinalResponse().isEmpty()) {
                        sink.tryEmitNext(AgentChunk.text(response.getFinalResponse()));
                    }
                }

                @Override
                public void onError(Throwable t) { sink.tryEmitError(t); }

                @Override
                public void onCompleted() {
                    sink.tryEmitNext(AgentChunk.status("FINISHED"));
                    sink.tryEmitComplete();
                }
            });
        } catch (Exception e) { sink.tryEmitError(e); }

        return sink.asFlux();
    }

    public String infer(String sessionId, String agentId, String context, String systemPrompt, String mcpConfigJson, String userMessage) {
        ManagedChannel channel = grpcClient.getChannel();
        if (channel == null || !grpcClient.healthCheck()) {
            throw new RuntimeException("codex-agent gRPC 通道不可用");
        }

        AgentServiceGrpc.AgentServiceBlockingStub blockingStub = AgentServiceGrpc.newBlockingStub(channel);

        try {
            Agent.RunTaskRequest request = buildRequest(sessionId, agentId, context, systemPrompt, mcpConfigJson, userMessage);
            StringBuilder fullResponse = new StringBuilder();
            Iterator<Agent.RunTaskResponse> responses = blockingStub.runTask(request);

            while (responses.hasNext()) {
                Agent.RunTaskResponse response = responses.next();
                if (response.getTextDelta() != null) fullResponse.append(response.getTextDelta());
                if (response.getFinalResponse() != null && fullResponse.length() == 0) {
                    fullResponse.append(response.getFinalResponse());
                }
            }
            return fullResponse.toString();
        } catch (Exception e) {
            throw new RuntimeException("codex-agent 调用失败: " + e.getMessage(), e);
        }
    }

    private Agent.RunTaskRequest buildRequest(String sessionId, String agentId, String context, String systemPrompt, String mcpConfigJson, String userMessage) {
        Agent.RunTaskRequest.Builder requestBuilder = Agent.RunTaskRequest.newBuilder();
        requestBuilder.setSessionId(agentId);

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("provider", Map.of(
            "name", "dashscope",
            "base_url", "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "env_key", "DASHSCOPE_API_KEY",
            "wire_api", "chat"
        ));
        configMap.put("model", "qwen-flash");
        configMap.put("cwd", ".");
        configMap.put("session_source", "vscode");
        configMap.put("developer_instructions", (systemPrompt != null && !systemPrompt.isEmpty()) ? systemPrompt : "You are a helpful assistant.");
        
        Map<String, Object> mcpServers = new HashMap<>();
        mcpServers.put("platform_internal", Map.of(
            "type", "streamable_http",
            "url", "http://localhost:8080/mcp/internal",
            "http_headers", Map.of("X-Session-Id", sessionId)
        ));

        if (mcpConfigJson != null && !mcpConfigJson.isEmpty()) {
            try {
                Map<String, Object> bizMcp = JsonUtils.fromJson(mcpConfigJson, Map.class);
                mcpServers.putAll(bizMcp);
            } catch (Exception e) {}
        }
        configMap.put("mcp_servers", mcpServers);
        requestBuilder.setConfigJson(JsonUtils.toJson(configMap));

        if (context != null && !context.isEmpty()) {
            try {
                List<Object> items = JsonUtils.fromJson(context, List.class);
                for (Object item : items) {
                    requestBuilder.addHistoryItemsJson(JsonUtils.toJson(item));
                }
            } catch (Exception e) {
                requestBuilder.addHistoryItemsJson(context);
            }
        }

        requestBuilder.setInput(Agent.UserInput.newBuilder().setText(userMessage).build());
        return requestBuilder.build();
    }

    public boolean healthCheck() { return grpcClient.healthCheck(); }
}