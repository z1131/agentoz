package com.deepknow.agentoz.infra.converter.grpc;

import com.deepknow.agentoz.api.dto.TaskResponse;
import codex.agent.RunTaskResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 任务响应转换器（对齐 adapter.proto 事件驱动模式）
 *
 * <h3>🔄 响应事件类型 (oneof event)</h3>
 * <ul>
 *   <li>codex_event_json - 原始 Codex JSONL 事件（思考过程、工具调用等）</li>
 *   <li>adapter_log - 系统日志</li>
 *   <li>error - 错误信息</li>
 *   <li>updated_rollout - 最终会话状态数据（JSONL bytes）</li>
 * </ul>
 *
 * <h3>📦 codex_event_json 格式</h3>
 * <p>每个事件是一个 JSON 对象，可能包含：</p>
 * <pre>
 * {"type": "message", "role": "assistant", "content": [...]}
 * {"type": "function_call", "name": "...", "arguments": "..."}
 * {"type": "reasoning", "summary": [...]}
 * </pre>
 */
@Slf4j
@Component
public class TaskResponseProtoConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 转换 RunTaskResponse (Proto) → TaskResponse (DTO)
     *
     * <p>新版 Proto 使用 oneof event 结构，需要根据事件类型分别处理</p>
     *
     * @param protoResponse Codex-Agent 返回的 Proto 响应
     * @return API 层的 TaskResponse DTO
     */
    public static TaskResponse toTaskResponse(RunTaskResponse protoResponse) {
        if (protoResponse == null) {
            return null;
        }

        TaskResponse dto = new TaskResponse();

        // 根据 oneof event 类型处理
        RunTaskResponse.EventCase eventCase = protoResponse.getEventCase();

        switch (eventCase) {
            case CODEX_EVENT_JSON -> {
                // 原始 Codex JSONL 事件
                String eventJson = protoResponse.getCodexEventJson();
                dto.setStatus("PROCESSING");
                parseCodexEventJson(eventJson, dto);
            }
            case ADAPTER_LOG -> {
                // 系统日志（通常用于调试）
                String logMessage = protoResponse.getAdapterLog();
                dto.setStatus("PROCESSING");
                // 可以选择忽略日志或放入特定字段
                log.debug("Adapter log: {}", logMessage);
            }
            case ERROR -> {
                // 错误信息
                String errorMessage = protoResponse.getError();
                dto.setStatus("ERROR");
                dto.setErrorMessage(errorMessage);
                log.error("Codex error: {}", errorMessage);
            }
            case UPDATED_ROLLOUT -> {
                // 最终会话状态数据 - 这是流结束的标志
                // 字节数据由调用方直接处理，这里仅标记状态
                dto.setStatus("FINISHED");
                dto.setUpdatedRollout(protoResponse.getUpdatedRollout().toByteArray());
                log.info("Received updated rollout: {} bytes", protoResponse.getUpdatedRollout().size());
            }
            case EVENT_NOT_SET -> {
                // 未设置事件类型
                log.warn("Received response with no event set");
            }
        }

        return dto;
    }

    /**
     * 解析 Codex 事件 JSON 并填充到 DTO
     *
     * <p>Codex 事件可能包含多种类型：</p>
     * <ul>
     *   <li>message - 消息（包含 text_delta 或完整 content）</li>
     *   <li>function_call - 工具调用</li>
     *   <li>reasoning - 推理过程</li>
     *   <li>等等</li>
     * </ul>
     */
    private static void parseCodexEventJson(String eventJson, TaskResponse dto) {
        if (eventJson == null || eventJson.isEmpty()) {
            return;
        }

        try {
            JsonNode event = objectMapper.readTree(eventJson);

            // 检测事件类型
            String type = event.has("type") ? event.get("type").asText() : null;

            if (type == null) {
                // 可能是增量事件格式
                if (event.has("delta")) {
                    JsonNode delta = event.get("delta");
                    if (delta.has("text")) {
                        dto.setTextDelta(delta.get("text").asText());
                    }
                    if (delta.has("reasoning")) {
                        dto.setReasoningDelta(delta.get("reasoning").asText());
                    }
                }
                return;
            }

            switch (type) {
                case "message" -> {
                    // 消息事件
                    String role = event.has("role") ? event.get("role").asText() : null;
                    if ("assistant".equals(role) && event.has("content")) {
                        // 提取文本内容
                        JsonNode content = event.get("content");
                        if (content.isArray()) {
                            StringBuilder text = new StringBuilder();
                            for (JsonNode item : content) {
                                if (item.has("text")) {
                                    text.append(item.get("text").asText());
                                }
                            }
                            if (!text.isEmpty()) {
                                dto.setFinalResponse(text.toString());
                            }
                        }
                    }
                }
                case "content_part" -> {
                    // 增量内容事件
                    if (event.has("text")) {
                        dto.setTextDelta(event.get("text").asText());
                    }
                }
                case "reasoning" -> {
                    // 推理事件
                    if (event.has("summary")) {
                        JsonNode summary = event.get("summary");
                        if (summary.isArray() && summary.size() > 0) {
                            StringBuilder reasoning = new StringBuilder();
                            for (JsonNode item : summary) {
                                if (item.has("text")) {
                                    reasoning.append(item.get("text").asText());
                                }
                            }
                            dto.setReasoningDelta(reasoning.toString());
                        }
                    }
                }
                case "function_call", "custom_tool_call" -> {
                    // 工具调用事件 - 保存原始 JSON 供上层处理
                    log.debug("Tool call event: {}", type);
                }
                case "function_call_output", "custom_tool_call_output" -> {
                    // 工具返回事件
                    log.debug("Tool output event: {}", type);
                }
                default -> {
                    log.debug("Unknown event type: {}", type);
                }
            }

        } catch (Exception e) {
            log.warn("Failed to parse codex event JSON: {}", e.getMessage());
            // 作为原始文本处理
            dto.setTextDelta(eventJson);
        }
    }

    /**
     * 检查响应是否为流结束事件
     *
     * @param protoResponse Proto 响应
     * @return 如果是 updated_rollout 或 error 则返回 true
     */
    public static boolean isStreamEnd(RunTaskResponse protoResponse) {
        if (protoResponse == null) {
            return false;
        }
        RunTaskResponse.EventCase eventCase = protoResponse.getEventCase();
        return eventCase == RunTaskResponse.EventCase.UPDATED_ROLLOUT
                || eventCase == RunTaskResponse.EventCase.ERROR;
    }

    /**
     * 提取更新后的会话状态数据
     *
     * @param protoResponse Proto 响应
     * @return 如果是 updated_rollout 事件则返回字节数组，否则返回 null
     */
    public static byte[] extractUpdatedRollout(RunTaskResponse protoResponse) {
        if (protoResponse == null) {
            return null;
        }
        if (protoResponse.getEventCase() == RunTaskResponse.EventCase.UPDATED_ROLLOUT) {
            return protoResponse.getUpdatedRollout().toByteArray();
        }
        return null;
    }
}
