package com.deepknow.agentoz.mcp.tool;

import com.deepknow.agentoz.api.service.AgentExecutionService;
import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Call Agent Tool - 实现 Agent 间相互调用
 *
 * <p>允许一个 Agent 通过 MCP 协议调用另一个 Agent，实现多智能体协作。</p>
 *
 * @author AgentOZ Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class CallAgentTool {

    @Autowired
    private AgentExecutionService agentExecutionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取工具规范定义
     */
    public McpSchema.Tool getToolDefinition() {
        // 使用简单的 JSON Schema 字符串
        String inputSchemaJson = """
                {
                    "type": "object",
                    "properties": {
                        "targetAgentId": {
                            "type": "string",
                            "description": "目标Agent的ID（必须存在且可用）"
                        },
                        "task": {
                            "type": "string",
                            "description": "要执行的任务描述，可以是问题、指令或需要处理的内容"
                        },
                        "conversationId": {
                            "type": "string",
                            "description": "会话ID，用于关联当前对话。如果为空，将创建新会话"
                        }
                    },
                    "required": ["targetAgentId", "task"]
                }
                """;

        return McpSchema.Tool.builder()
                .name("call_agent")
                .description("调用另一个Agent执行任务，实现Agent间协作。可以传递上下文信息，并获取目标Agent的执行结果。")
                .inputSchema(inputSchemaJson)
                .build();
    }

    /**
     * 执行工具调用
     *
     * @param transportContext MCP 传输上下文
     * @param request 工具调用请求
     * @return 执行结果
     */
    public McpSchema.CallToolResult execute(
            McpTransportContext transportContext,
            McpSchema.CallToolRequest request) {
        try {
            Map<String, Object> arguments = request.arguments();

            // 1. 解析参数
            String targetAgentId = (String) arguments.get("targetAgentId");
            String task = (String) arguments.get("task");
            String conversationId = (String) arguments.getOrDefault("conversationId", "");

            log.info(">>> MCP CallAgent 工具调用: targetAgentId={}, task={}, conversationId={}",
                    targetAgentId, task, conversationId);

            // 2. 验证参数
            if (targetAgentId == null || targetAgentId.isBlank()) {
                return McpSchema.CallToolResult.builder()
                        .addTextContent("{\"error\": \"targetAgentId 不能为空\"}")
                        .isError(true)
                        .build();
            }

            if (task == null || task.isBlank()) {
                return McpSchema.CallToolResult.builder()
                        .addTextContent("{\"error\": \"task 不能为空\"}")
                        .isError(true)
                        .build();
            }

            // 3. 构建执行请求
            ExecuteTaskRequest executeRequest = new ExecuteTaskRequest();
            executeRequest.setAgentId(targetAgentId);
            executeRequest.setConversationId(conversationId);
            executeRequest.setMessage(task);

            // 4. 同步调用 Agent 服务（等待结果）
            CompletableFuture<String> resultFuture = new CompletableFuture<>();

            // 创建响应观察者
            StreamObserver<TaskResponse> responseObserver = new StreamObserver<TaskResponse>() {
                private final StringBuilder fullResponse = new StringBuilder();

                @Override
                public void onNext(TaskResponse response) {
                    if (response != null && response.getFinalResponse() != null) {
                        fullResponse.append(response.getFinalResponse());
                        log.debug("Agent 响应片段: {}", response.getFinalResponse());
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    log.error("Agent 调用失败", throwable);
                    resultFuture.completeExceptionally(throwable);
                }

                @Override
                public void onCompleted() {
                    String result = fullResponse.toString();
                    log.info("Agent 调用完成，总响应长度: {} 字符", result.length());
                    resultFuture.complete(result);
                }
            };

            // 发起调用
            agentExecutionService.executeTask(executeRequest, responseObserver);

            // 5. 等待结果并返回（最多等待 5 分钟）
            String result = resultFuture.get(5, java.util.concurrent.TimeUnit.MINUTES);

            return McpSchema.CallToolResult.builder()
                    .addTextContent(result)
                    .isError(false)
                    .build();

        } catch (Exception e) {
            log.error("CallAgent 工具执行异常", e);
            return McpSchema.CallToolResult.builder()
                    .addTextContent("{\"error\": \"工具执行失败: " + e.getMessage() + "\"}")
                    .isError(true)
                    .build();
        }
    }
}
